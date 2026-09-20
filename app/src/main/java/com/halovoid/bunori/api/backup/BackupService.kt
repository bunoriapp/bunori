package com.halovoid.bunori.api.backup

import android.content.Context
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.StorageRepositoryImpl
import com.halovoid.bunori.data.scheduler.jobs.JobHandler
import com.halovoid.bunori.data.scheduler.jobs.JobResult
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
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
        FileOutputStream(tempFile).use { fos ->
            writeBackupToStream(fos, backupDatabase, backupChapters, backupCovers, timestamp)
        }
        val bytes = tempFile.readBytes()
        tempFile.delete()

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
                storageRepository.saveFile("backup", fileName, "application/octet-stream", bytes)
                storageRepository.saveText("backup", "backup_metadata.json", "application/json", metadataJson)
                return null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback to local external/internal filesDir if exportUri is not set or failed
        val backupDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "backup").apply { mkdirs() }
        val fallbackFile = File(backupDir, fileName)
        fallbackFile.writeBytes(bytes)
        File(backupDir, "backup_metadata.json").writeText(metadataJson)

        return fallbackFile
    }

    private fun writeBackupToStream(
        outputStream: OutputStream,
        backupDatabase: Boolean,
        backupChapters: Boolean,
        backupCovers: Boolean,
        timestamp: Long
    ) {
        val dbFile = context.getDatabasePath("bunori.db")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val appVersion = packageInfo.versionName ?: "1.0"
        val databaseVersion = AppDatabase.getDatabase(context).openHelper.readableDatabase.version

        // Checkpoint SQLite WAL to ensure all tables (novels, chapters, downloads, batches, tasks, artifacts) are synced
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

        ZipOutputStream(outputStream).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestJson.toString(2).toByteArray())
            zos.closeEntry()

            if (backupDatabase && dbFile.exists()) {
                zos.putNextEntry(ZipEntry("database.db"))
                dbFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }

            if (backupChapters || backupCovers) {
                val novelsDir = File(context.filesDir, "novels")
                if (novelsDir.exists() && novelsDir.isDirectory) {
                    zipDirectory(novelsDir, "novels", zos)
                }
            }

            val datastoreDir = File(context.filesDir, "datastore")
            if (datastoreDir.exists() && datastoreDir.isDirectory) {
                zipDirectory(datastoreDir, "datastore", zos)
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
