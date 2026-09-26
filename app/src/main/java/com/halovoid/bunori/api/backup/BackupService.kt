package com.halovoid.bunori.api.backup

import android.content.Context
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.StorageRepositoryImpl
import com.halovoid.bunori.data.scheduler.jobs.JobHandler
import com.halovoid.bunori.data.scheduler.jobs.JobResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class BackupService(private val context: Context): JobHandler {
    override suspend fun handle(task: TaskEntity): JobResult {
        return try {
            createBackup()
            JobResult.Success
        } catch (e: Exception) {
            JobResult.Failure(e, isRecoverable = true)
        }
    }

    suspend fun createBackup(
        backupDatabase: Boolean = true,
        backupChapters: Boolean = true,
        backupCovers: Boolean = true
    ): File? {
        val timestamp = System.currentTimeMillis()
        val fileName = "backup.bbak"

        val tempFile = File(context.cacheDir, fileName)
        withContext(Dispatchers.IO) {
            BufferedOutputStream(FileOutputStream(tempFile), 65536).use { fos ->
                writeBackupToStream(fos, backupDatabase, backupChapters, backupCovers, timestamp)
            }
        }

        val included = mutableListOf<String>()
        if (backupDatabase) included.add("Database")
        if (backupChapters) included.add("Chapters")
        if (backupCovers) included.add("Covers")
        val summary = included.joinToString(" · ")
        val timeStr = android.text.format.DateFormat.format("MMM dd, yyyy, h:mm a", timestamp).toString()

        val metadataJson = JSONObject().apply {
            put("lastBackupTime", timeStr)
            put("contentsSummary", summary)
        }.toString()

        val metadataFile = File(context.filesDir, "backup_metadata.json")
        metadataFile.writeText(metadataJson)

        val exportUri = PreferenceRepository.getInstance(context).exportFolderUri.firstOrNull()
        if (exportUri != null) {
            try {
                val storageRepository = StorageRepositoryImpl.getInstance(context)
                storageRepository.saveFile("backup", fileName, "application/octet-stream", tempFile)
                storageRepository.saveText("backup", "backup_metadata.json", "application/json", metadataJson)
                tempFile.delete()
                return null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val backupDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "backup").apply { mkdirs() }
        val fallbackFile = File(backupDir, fileName)
        tempFile.copyTo(fallbackFile, overwrite = true)
        tempFile.delete()
        File(backupDir, "backup_metadata.json").writeText(metadataJson)

        return fallbackFile
    }

    private suspend fun writeBackupToStream(
        outputStream: OutputStream,
        backupDatabase: Boolean,
        backupChapters: Boolean,
        backupCovers: Boolean,
        timestamp: Long
    ) {
        val dbFile = context.getDatabasePath("bunori.db")
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
        val appVersion = packageInfo?.versionName ?: "1.0"
        val databaseVersion = try {
            AppDatabase.getDatabase(context).openHelper.readableDatabase.version
        } catch (_: Exception) {
            1
        }

        try {
            val db = AppDatabase.getDatabase(context).openHelper.writableDatabase
            db.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val manifestJson = JSONObject().apply {
            put("formatVersion", 1)
            put("appVersion", appVersion)
            put("databaseVersion", databaseVersion)
            put("createdAt", timestamp)
            put("contents", JSONObject().apply {
                put("database", backupDatabase)
                put("chapters", backupChapters)
                put("covers", backupCovers)
                put("downloads", backupDatabase)
                put("preferences", true)
            })
        }

        ZipOutputStream(outputStream.buffered(65536)).use { zos ->
            zos.setLevel(Deflater.BEST_SPEED)
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestJson.toString(2).toByteArray())
            zos.closeEntry()

            if (backupDatabase && dbFile.exists()) {
                zos.setLevel(Deflater.BEST_SPEED)
                zos.putNextEntry(ZipEntry("database.db"))
                dbFile.inputStream().buffered(65536).use { it.copyTo(zos, 65536) }
                zos.closeEntry()
            }

            if (backupChapters || backupCovers) {
                val addedEntries = mutableSetOf<String>()

                try {
                    val storageRepository = StorageRepositoryImpl.getInstance(context)
                    val storageFiles = storageRepository.listFilesRecursively("novels")
                    for (fileInfo in storageFiles) {
                        val relPath = fileInfo.relativePath
                        val isChapter = relPath.contains("/chapters/")
                        val isCover = relPath.contains("/covers/")

                        if (isChapter && !backupChapters) continue
                        if (isCover && !backupCovers) continue

                        if (addedEntries.add(relPath)) {
                            try {
                                if (relPath.endsWith(".gz", ignoreCase = true)) {
                                    zos.setLevel(Deflater.NO_COMPRESSION)
                                } else {
                                    zos.setLevel(Deflater.BEST_SPEED)
                                }
                                zos.putNextEntry(ZipEntry(relPath))
                                context.contentResolver.openInputStream(fileInfo.uri)?.buffered(65536)?.use {
                                    it.copyTo(zos, 65536)
                                }
                                zos.closeEntry()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val datastoreDir = File(context.filesDir, "datastore")
            if (datastoreDir.exists() && datastoreDir.isDirectory) {
                zipDirectory(datastoreDir, "datastore", zos)
            }
        }
    }

    private fun zipDirectoryFiltered(
        dir: File,
        baseName: String,
        zos: ZipOutputStream,
        backupChapters: Boolean,
        backupCovers: Boolean,
        addedEntries: MutableSet<String>
    ) {
        dir.listFiles()?.forEach { file ->
            val entryName = "$baseName/${file.name}"
            if (file.isDirectory) {
                zipDirectoryFiltered(file, entryName, zos, backupChapters, backupCovers, addedEntries)
            } else {
                val isChapter = entryName.contains("/chapters/")
                val isCover = entryName.contains("/covers/")
                if ((isChapter && !backupChapters) || (isCover && !backupCovers)) {
                    return@forEach
                }
                if (addedEntries.add(entryName)) {
                    try {
                        zos.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun zipDirectory(dir: File, baseName: String, zos: ZipOutputStream) {
        dir.listFiles()?.forEach { file ->
            val entryName = "$baseName/${file.name}"
            if (file.isDirectory) {
                zipDirectory(file, entryName, zos)
            } else {
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }
}
