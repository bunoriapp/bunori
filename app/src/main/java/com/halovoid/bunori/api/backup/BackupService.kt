package com.halovoid.bunori.api.backup

import android.content.Context
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.repository.StorageRepository
import com.halovoid.bunori.data.repository.StorageTree
import com.halovoid.bunori.data.scheduler.jobs.MaintenanceActivity
import com.halovoid.bunori.data.scheduler.jobs.MaintenanceScheduler
import com.halovoid.bunori.data.scheduler.jobs.JobResult
import com.halovoid.bunori.data.scheduler.notification.IndexNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupService(
    private val context: Context,
    private val storRepo: StorageRepository = StorageRepository.getInstance(context)
) {
    companion object {
        @Volatile
        private var isRunning = false
    }

    suspend fun handle(
        backupDatabase: Boolean = true,
        backupChapters: Boolean = true,
        backupCovers: Boolean = true,
        onProgress: (current: Int, total: Int, step: String) -> Unit = { _, _, _ -> }
    ): JobResult = withContext(Dispatchers.IO) {
        if (!MaintenanceScheduler.acquire(MaintenanceActivity.BACKUP)) {
            return@withContext JobResult.Failure(Exception("Another maintenance activity is currently in progress (${MaintenanceScheduler.currentActivity.value})"))
        }

        val notifManager = IndexNotificationManager(context)
        notifManager.showProgress("Creating Backup", "Preparing files...", isIndeterminate = true)

        try {
            val timestamp = System.currentTimeMillis()
            val fileName = "backup.lnbak"

            val backupDirUri = storRepo.ensureDirectory("backup")
                ?: run {
                    notifManager.showCompletion("Backup Failed", "Failed to access backup folder")
                    return@withContext JobResult.Failure(Exception("Failed to ensure backup directory"))
                }

            // 1. Fetch trees for novels and artifacts
            val novelsUri = storRepo.relativeToUri("novels")
            val novelsTree = if (novelsUri != null) storRepo.tree(novelsUri) else null

            val artifactsUri = storRepo.relativeToUri("artifacts")
            val artifactsTree = if (artifactsUri != null) storRepo.tree(artifactsUri) else null

            val datastoreDir = File(context.filesDir, "datastore")
            val datastoreFiles = mutableListOf<File>()
            if (datastoreDir.exists()) {
                val files = datastoreDir.walkTopDown().filter { it.isFile }
                for (f in files) {
                    datastoreFiles.add(f)
                }
            }

            // 2. Calculate total items for accurate progress reporting
            var totalItems = 1 // manifest.json
            if (backupDatabase) totalItems += 1
            if (novelsTree != null) {
                totalItems += countTreeFiles(novelsTree, isNovel = true, backupChapters, backupCovers)
            }
            if (artifactsTree != null) {
                totalItems += countTreeFiles(artifactsTree, isNovel = false, backupChapters, backupCovers)
            }
            totalItems += datastoreFiles.size

            var currentItem = 0
            fun report(step: String) {
                currentItem++
                onProgress(currentItem, totalItems, step)
                if (currentItem % 5 == 0 || currentItem == totalItems) {
                    notifManager.showProgress("Creating Backup", step, current = currentItem, max = totalItems)
                }
            }

            // 3. Create destination file on storage
            val destUri = storRepo.createFile(backupDirUri, fileName, "application/octet-stream", ByteArray(0))
                ?: run {
                    notifManager.showCompletion("Backup Failed", "Failed to create backup file")
                    return@withContext JobResult.Failure(Exception("Failed to create backup file"))
                }

            val outputStream = storRepo.openOutputStream(destUri)
                ?: run {
                    notifManager.showCompletion("Backup Failed", "Failed to open output stream")
                    return@withContext JobResult.Failure(Exception("Failed to open output stream"))
                }

            outputStream.buffered(65536).use { bufferedStream ->
                ZipOutputStream(bufferedStream).use { zos ->
                    zos.setLevel(Deflater.BEST_SPEED)

                    // Write manifest.json
                    report("Creating backup manifest...")
                    val backupItems = JSONArray()
                    if (backupDatabase) backupItems.put("database")
                    if (novelsTree != null) backupItems.put("novels")
                    if (artifactsTree != null) backupItems.put("artifacts")

                    val manifest = JSONObject()
                    manifest.put("formatVersion", 1)
                    manifest.put("createdAt", timestamp)
                    manifest.put("database", backupDatabase)
                    manifest.put("chapters", backupChapters)
                    manifest.put("covers", backupCovers)
                    manifest.put("backup", backupItems)

                    zos.putNextEntry(ZipEntry("manifest.json"))
                    zos.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // Write database.db
                    if (backupDatabase) {
                        report("Backing up database...")
                        try {
                            AppDatabase.getDatabase(context).openHelper.writableDatabase
                                .query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
                        } catch (_: Exception) {}

                        val dbFile = context.getDatabasePath("bunori.db")
                        if (dbFile.exists()) {
                            zos.putNextEntry(ZipEntry("database.db"))
                            dbFile.inputStream().buffered(65536).use { input ->
                                input.copyTo(zos, 65536)
                            }
                            zos.closeEntry()
                        }
                    }

                    // Write novels directory
                    if (novelsTree != null) {
                        tree_backup(
                            tree = novelsTree,
                            currPath = "novels",
                            zos = zos,
                            isNovel = true,
                            backupChapters = backupChapters,
                            backupCovers = backupCovers
                        ) { path ->
                            report("Backing up: $path")
                        }
                    }

                    // Write artifacts directory
                    if (artifactsTree != null) {
                        tree_backup(
                            tree = artifactsTree,
                            currPath = "artifacts",
                            zos = zos,
                            isNovel = false,
                            backupChapters = true,
                            backupCovers = true
                        ) { path ->
                            report("Backing up: $path")
                        }
                    }

                    // Write settings datastore
                    for (file in datastoreFiles) {
                        val relPath = "datastore/${file.relativeTo(datastoreDir).path}"
                        report("Backing up settings: ${file.name}")
                        zos.putNextEntry(ZipEntry(relPath))
                        file.inputStream().buffered(65536).use { input ->
                            input.copyTo(zos, 65536)
                        }
                        zos.closeEntry()
                    }
                }
            }

            // Write metadata summary for settings screen
            val timeStr = android.text.format.DateFormat.format("MMM dd, yyyy, h:mm a", timestamp).toString()
            val summaryParts = mutableListOf<String>()
            if (backupDatabase) summaryParts.add("Database")
            if (novelsTree != null) {
                val novelCount = novelsTree.directories().size
                summaryParts.add("$novelCount Novels")
            }
            if (artifactsTree != null) {
                val artifactCount = artifactsTree.directories().size
                summaryParts.add("$artifactCount Artifacts")
            }
            val summaryStr = summaryParts.joinToString(", ")

            val metaJson = JSONObject()
            metaJson.put("lastBackupTime", timeStr)
            metaJson.put("contentsSummary", summaryStr)
            try {
                val metaFile = File(context.filesDir, "backup_metadata.json")
                metaFile.writeText(metaJson.toString())
            } catch (_: Exception) {}

            notifManager.showCompletion("Backup Completed", "Backup saved to backup/backup.lnbak")
            JobResult.Success
        } catch (e: Exception) {
            notifManager.showCompletion("Backup Failed", e.message ?: "Unknown error")
            JobResult.Failure(e)
        } finally {
            notifManager.dismissProgress()
            MaintenanceScheduler.release(MaintenanceActivity.BACKUP)
        }
    }

    private fun countTreeFiles(
        tree: StorageTree,
        isNovel: Boolean,
        backupChapters: Boolean,
        backupCovers: Boolean
    ): Int {
        var count = 0
        for (child in tree.children) {
            if (child.isDirectory) {
                if (isNovel && child.name == "chapters" && !backupChapters) continue
                if (isNovel && child.name == "covers" && !backupCovers) continue
                count += countTreeFiles(child, isNovel, backupChapters, backupCovers)
            } else {
                count++
            }
        }
        return count
    }

    private suspend fun tree_backup(
        tree: StorageTree,
        currPath: String,
        zos: ZipOutputStream,
        isNovel: Boolean,
        backupChapters: Boolean,
        backupCovers: Boolean,
        onFile: (String) -> Unit
    ) {
        for (child in tree.children) {
            val entryPath = "$currPath/${child.name}"

            if (child.isDirectory) {
                if (isNovel && child.name == "chapters" && !backupChapters) continue
                if (isNovel && child.name == "covers" && !backupCovers) continue

                tree_backup(
                    tree = child,
                    currPath = entryPath,
                    zos = zos,
                    isNovel = isNovel,
                    backupChapters = backupChapters,
                    backupCovers = backupCovers,
                    onFile = onFile
                )
            } else {
                onFile(entryPath)
                val isGz = child.name.endsWith(".gz", ignoreCase = true)
                if (isGz) {
                    zos.setLevel(Deflater.NO_COMPRESSION)
                } else {
                    zos.setLevel(Deflater.BEST_SPEED)
                }

                zos.putNextEntry(ZipEntry(entryPath))
                val stream = storRepo.openInputStream(child.uri)
                if (stream != null) {
                    stream.use { input ->
                        input.copyTo(zos, 65536)
                    }
                } else {
                    throw IOException("Unable to read ${child.name}")
                }
                zos.closeEntry()
            }
        }
    }
}
