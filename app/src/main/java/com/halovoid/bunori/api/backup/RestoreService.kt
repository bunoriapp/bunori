package com.halovoid.bunori.api.backup

import android.content.Context
import android.net.Uri
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.db.entities.NovelEntity
import com.halovoid.bunori.data.indexing.StorageIndexManager
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.StorageRepository
import com.halovoid.bunori.data.scheduler.jobs.MaintenanceActivity
import com.halovoid.bunori.data.scheduler.jobs.MaintenanceScheduler
import com.halovoid.bunori.data.scheduler.notification.IndexNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class RestoreService(
    private val context: Context,
    private val storRepo: StorageRepository = StorageRepository.getInstance(context)
) {
    suspend fun restoreBackup(
        uri: Uri,
        onProgress: (current: Int, total: Int, step: String) -> Unit = { _, _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (!MaintenanceScheduler.acquire(MaintenanceActivity.RESTORE)) {
            return@withContext false
        }

        val notifManager = IndexNotificationManager(context)
        notifManager.showProgress("Restoring Backup", "Scanning archive...", isIndeterminate = true)

        val prefRepo = PreferenceRepository.getInstance(context)

        // Preserve export folder and onboarding settings
        val currentExportFolderUri = prefRepo.exportFolderUri.firstOrNull()
        val currentOnboardingCompleted = prefRepo.isOnboardingCompleted.firstOrNull()

        val restoredFilesMap = mutableMapOf<String, String>()
        val restoredNovelCoversMap = mutableMapOf<String, String>()

        var tempDbFile: File? = null
        val datastoreDir = File(context.filesDir, "datastore")
        datastoreDir.mkdirs()

        // 1. First pass: count total entries for the progress bar
        var totalEntries = 0
        try {
            val countStream = context.contentResolver.openInputStream(uri) ?: run {
                notifManager.showCompletion("Restore Failed", "Cannot open backup file")
                return@withContext false
            }
            ZipInputStream(countStream.buffered(65536)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        totalEntries++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {
            totalEntries = 100 // fallback estimate
        }

        var currentEntry = 0
        fun report(step: String) {
            onProgress(currentEntry, totalEntries, step)
            if (currentEntry % 5 == 0 || currentEntry == totalEntries) {
                notifManager.showProgress("Restoring Backup", step, current = currentEntry, max = totalEntries)
            }
        }

        // 2. Second pass: extract files
        try {
            AppDatabase.closeAndResetDatabase()

            val inputStream = context.contentResolver.openInputStream(uri) ?: run {
                notifManager.showCompletion("Restore Failed", "Cannot open backup file")
                return@withContext false
            }
            ZipInputStream(inputStream.buffered(65536)).use { zis ->
                var entry = zis.nextEntry

                while (entry != null) {
                    val entryName = entry.name
                    if (entry.isDirectory) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    currentEntry++

                    if (entryName == "database.db") {
                        report("Restoring database...")
                        tempDbFile = File(context.cacheDir, "restore_temp_db.db")
                        tempDbFile.outputStream().buffered(65536).use { out ->
                            zis.copyTo(out, 65536)
                        }
                    } else if (entryName.startsWith("datastore/")) {
                        val fileName = entryName.substringAfterLast('/')
                        report("Restoring settings: $fileName")
                        val targetFile = File(context.filesDir, entryName)
                        val parent = targetFile.parentFile
                        if (parent != null) parent.mkdirs()
                        targetFile.outputStream().buffered(65536).use { out ->
                            zis.copyTo(out, 65536)
                        }
                    } else if (entryName.startsWith("novels/") || entryName.startsWith("artifacts/")) {
                        val relDir = entryName.substringBeforeLast('/', "")
                        val fileName = entryName.substringAfterLast('/')
                        report("Restoring file: $fileName")

                        val mimeType = when {
                            fileName.endsWith(".gz", true) -> "application/gzip"
                            fileName.endsWith(".json", true) -> "application/json"
                            fileName.endsWith(".png", true) -> "image/png"
                            fileName.endsWith(".webp", true) -> "image/webp"
                            fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
                            else -> "application/octet-stream"
                        }

                        val dirUri = storRepo.ensureDirectory(relDir)
                        if (dirUri != null) {
                            val byteOut = ByteArrayOutputStream()
                            zis.copyTo(byteOut, 65536)
                            val bytes = byteOut.toByteArray()
                            storRepo.createFile(dirUri, fileName, mimeType, bytes)

                            restoredFilesMap[fileName] = entryName
                            if (relDir.contains("/covers")) {
                                val novelKey = relDir.removePrefix("novels/").substringBefore('/')
                                if (novelKey.isNotBlank()) {
                                    restoredNovelCoversMap[novelKey] = entryName
                                }
                            }
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 3. Restore Database File
            if (tempDbFile != null && tempDbFile.exists()) {
                report("Applying restored database...")
                val targetDbFile = context.getDatabasePath("bunori.db")
                val parent = targetDbFile.parentFile
                if (parent != null) parent.mkdirs()

                tempDbFile.copyTo(targetDbFile, overwrite = true)
                File(targetDbFile.path + "-wal").delete()
                File(targetDbFile.path + "-shm").delete()
                File(targetDbFile.path + "-journal").delete()
                tempDbFile.delete()
            }

            // 4. Restore preferences
            if (currentExportFolderUri != null) {
                prefRepo.setExportFolder(currentExportFolderUri)
            }
            if (currentOnboardingCompleted != null) {
                prefRepo.setOnboardingCompleted(currentOnboardingCompleted)
            }

            // 5. Reconcile Database novel covers
            try {
                val db = AppDatabase.getDatabase(context)
                val novelDao = db.novelDao()
                val allNovels = novelDao.getAllNovelsOnce()
                val updatedNovels = mutableListOf<NovelEntity>()

                for (novel in allNovels) {
                    val oldCover = novel.coverUrl ?: continue
                    var matchedPath: String? = null
                    for ((key, path) in restoredNovelCoversMap) {
                        if (oldCover.contains(key)) {
                            matchedPath = path
                            break
                        }
                    }
                    if (matchedPath != null && novel.coverUrl != matchedPath) {
                        updatedNovels.add(novel.copy(coverUrl = matchedPath))
                    }
                }

                if (updatedNovels.isNotEmpty()) {
                    novelDao.upsertNovels(updatedNovels)
                }
            } catch (_: Exception) {}

            // 6. Synchronize Room downloads table from metadata manifests
            report("Syncing database with restored downloads...")
            StorageIndexManager.getInstance(context).reIndex { current, total, novelName ->
                onProgress(currentEntry, totalEntries, "Restoring to DB: $novelName ($current/$total)")
                notifManager.showProgress("Restoring Backup", "Restoring to DB: $novelName ($current/$total)", current = current, max = total)
            }

            AppDatabase.closeAndResetDatabase()
            notifManager.showCompletion("Restore Completed", "Backup restored successfully. Please restart the app.")
            true
        } catch (e: Exception) {
            notifManager.showCompletion("Restore Failed", "Failed to restore backup.")
            tempDbFile?.delete()
            false
        } finally {
            notifManager.dismissProgress()
            MaintenanceScheduler.release(MaintenanceActivity.RESTORE)
        }
    }
}
