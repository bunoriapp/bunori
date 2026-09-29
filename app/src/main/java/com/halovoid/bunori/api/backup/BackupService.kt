package com.halovoid.bunori.api.backup

import android.content.Context
import android.net.Uri
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.StorageFileInfo
import com.halovoid.bunori.data.repository.StorageRepository
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

class BackupService(private val context: Context) {
    private val storageRepository by lazy { StorageRepository.getInstance(context) }
    suspend fun handle(): JobResult {
        return try {
            val timestamp = System.currentTimeMillis()
            val fileName = "backup.bbak"

            val backupDirUri = storageRepository.ensureDirectory("backup")
                ?: return JobResult.Failure(Exception("Failed to ensure backup directory"))
            var destinationUri: Uri?;

            val outputStream: OutputStream = run {
                destinationUri = storageRepository.createFile(backupDirUri, fileName, "application/octet-stream", ByteArray(0)) ?: return JobResult.Failure(Exception("Failed to create destination URI"))
                storageRepository.openOutputStream(destinationUri)
            } ?: return JobResult.Failure(Exception("Failed to open a output stream to desired path"))

            outputStream.buffered(65536).use { stream ->
                ZipOutputStream(stream).use { zos ->
                    zos.setLevel(Deflater.BEST_SPEED);

                    val manifest = JSONObject().apply {
                        put("formatVersion", 1)
                        put("createdAt", timestamp)
                        put("database", true) // these three are hardcoded to yes even though the user is given the option to choose what he wants to back up
                        put("chapters", true) // will fix later on
                        put("covers", true) // this becomes a todo
                    }

                    zos.putNextEntry(ZipEntry("manifest.json"))
                    zos.write(manifest.toString(2).toByteArray())
                    zos.closeEntry()

                    try {
                        AppDatabase.getDatabase(context).openHelper.writableDatabase
                            .query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
                    } catch (_: Exception) {}
                    val dbFile = context.getDatabasePath("bunori.db");
                    if (dbFile.exists()) {
                        zos.putNextEntry(ZipEntry("database.db"));
                        dbFile.inputStream().buffered(65536).use { it.copyTo(zos, 65536) }
                        zos.closeEntry()
                    }

                    val novelsUri = storageRepository.relativeToUri("novels")
                    if (novelsUri != null) {
                        backupStorageDirectory(novelsUri, "novels", zos,
                            backupChapters = true,
                            backupCovers = true
                        )
                    }

                    val datastoreDir = File(context.filesDir, "datastore")
                    if (datastoreDir.exists()) {
                        datastoreDir.walkTopDown().filter { it.isFile }.forEach { file ->
                            val relative = "datastore/${file.relativeTo(datastoreDir).path}"
                            zos.putNextEntry(ZipEntry(relative))
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
            JobResult.Success
        } catch (e: Exception) {
            JobResult.Failure(e, isRecoverable = true)
        }
    }

    private suspend fun backupStorageDirectory(
        dirUri: Uri,
        currentPath: String,
        zos: ZipOutputStream,
        backupChapters: Boolean,
        backupCovers: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        val files: List<StorageFileInfo> = storageRepository.list(dirUri)
        for (file in files) {
            val entryPath = "$currentPath/${file.name}"
            if (file.isDirectory) {
                backupStorageDirectory(file.uri, entryPath, zos, backupChapters, backupCovers)
            } else {
                val isChapter = entryPath.contains("/chapters/")
                val isCover = entryPath.contains("/covers/")
                if ((isChapter && !backupChapters) || (isCover && !backupCovers)) continue

                zos.setLevel(if (file.name.endsWith(".gz", ignoreCase = true)) Deflater.NO_COMPRESSION else Deflater.BEST_SPEED)
                zos.putNextEntry(ZipEntry(entryPath))
                storageRepository.openInputStream(file.uri)?.buffered(65536)?.use { input ->
                    input.copyTo(zos, 65536)
                }
                zos.closeEntry()
            }
        }
    }
}
