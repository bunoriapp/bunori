package com.halovoid.bunori.api.backup

import android.content.Context
import android.net.Uri
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.StorageRepositoryImpl
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class RestoreService(private val context: Context) {

    suspend fun restoreBackup(uri: Uri): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return false
            val tempDir = File(context.cacheDir, "restore_temp").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }

            // Unzip archive
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val file = File(tempDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Validate manifest
            val manifestFile = File(tempDir, "mainfest.json").takeIf { it.exists() } 
                ?: File(tempDir, "manifest.json").takeIf { it.exists() } 
                ?: return false

            val manifestText = manifestFile.readText()
            val manifest = JSONObject(manifestText)
            val contents = manifest.optJSONObject("contents")

            // Preserve current device-specific preferences before replacing datastore
            val preferenceRepository = PreferenceRepository.getInstance(context)
            val currentExportFolderUri = preferenceRepository.exportFolderUri.firstOrNull()
            val currentOnboardingCompleted = preferenceRepository.isOnboardingCompleted.firstOrNull()

            // Close DB connections before replacing
            AppDatabase.getDatabase(context).close()

            // Restore database.db
            val dbFile = File(tempDir, "database.db")
            if (dbFile.exists()) {
                val targetDbFile = context.getDatabasePath("bunori.db")
                targetDbFile.parentFile?.mkdirs()
                dbFile.copyTo(targetDbFile, overwrite = true)
                // Delete journal/wal/shm files so the new db file is cleanly opened
                File(targetDbFile.path + "-wal").delete()
                File(targetDbFile.path + "-shm").delete()
                File(targetDbFile.path + "-journal").delete()
            }

            // Restore datastore/
            val datastoreDir = File(tempDir, "datastore")
            if (datastoreDir.exists() && datastoreDir.isDirectory) {
                val targetDatastoreDir = File(context.filesDir, "datastore")
                targetDatastoreDir.mkdirs()
                datastoreDir.copyRecursively(targetDatastoreDir, overwrite = true)

                // Re-apply preserved storage folder location and onboarding status
                if (currentExportFolderUri != null) {
                    preferenceRepository.setExportFolder(currentExportFolderUri)
                }
                if (currentOnboardingCompleted != null) {
                    preferenceRepository.setOnboardingCompleted(currentOnboardingCompleted)
                }
            }

            // Restore novels/ to active StorageRepository (SAF)
            val novelsDir = File(tempDir, "novels")
            val restoredFilesMap = mutableMapOf<String, Uri>()
            val restoredNovelCoversMap = mutableMapOf<String, Uri>()

            if (novelsDir.exists() && novelsDir.isDirectory) {
                val storageRepository = StorageRepositoryImpl.getInstance(context)
                novelsDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relativePathToFile = file.relativeTo(novelsDir).path
                    val parentRelDir = "novels/" + (file.parentFile?.relativeTo(novelsDir)?.path ?: "")
                    val fileName = file.name
                    val mimeType = when {
                        fileName.endsWith(".gz") -> "application/gzip"
                        fileName.endsWith(".png") -> "image/png"
                        fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") -> "image/jpeg"
                        fileName.endsWith(".webp") -> "image/webp"
                        else -> "application/octet-stream"
                    }

                    try {
                        val newUri = storageRepository.saveFile(
                            relativePath = parentRelDir.trimEnd('/'),
                            fileName = fileName,
                            mimeType = mimeType,
                            data = file.readBytes()
                        )
                        restoredFilesMap[fileName] = newUri
                        if (parentRelDir.contains("/covers")) {
                            val novelKey = file.parentFile?.parentFile?.name ?: ""
                            if (novelKey.isNotEmpty()) {
                                restoredNovelCoversMap[novelKey] = newUri
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Reconcile database URIs with the newly restored files in SAF storage
            reconcileDatabaseUris(restoredFilesMap, restoredNovelCoversMap)

            tempDir.deleteRecursively()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun reconcileDatabaseUris(
        restoredFilesMap: Map<String, Uri>,
        restoredNovelCoversMap: Map<String, Uri>
    ) {
        if (restoredFilesMap.isEmpty() && restoredNovelCoversMap.isEmpty()) return

        try {
            val db = AppDatabase.getDatabase(context)
            val downloadDao = db.downloadDao()
            val novelDao = db.novelDao()

            // 1. Reconcile Downloads
            val allDownloads = downloadDao.getAllDownloads()
            for (download in allDownloads) {
                val oldLocation = download.fileLocation
                val matchingEntry = restoredFilesMap.entries.firstOrNull { (fileName, _) ->
                    oldLocation.endsWith(fileName) || oldLocation.contains(fileName)
                }

                if (matchingEntry != null) {
                    val newUri = matchingEntry.value
                    if (download.fileLocation != newUri.toString()) {
                        downloadDao.upsertDownload(
                            download.copy(fileLocation = newUri.toString())
                        )
                    }
                }
            }

            // 2. Reconcile Novel Covers
            val allNovels = novelDao.getAllNovelsOnce()
            for (novel in allNovels) {
                val oldCover = novel.coverUrl
                if (oldCover != null && (oldCover.startsWith("content://") || oldCover.startsWith("file://"))) {
                    val matchedCoverUri = restoredNovelCoversMap.entries.firstOrNull { (novelKey, _) ->
                        oldCover.contains(novelKey)
                    }?.value

                    if (matchedCoverUri != null && oldCover != matchedCoverUri.toString()) {
                        novelDao.upsertNovel(
                            novel.copy(coverUrl = matchedCoverUri.toString())
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
