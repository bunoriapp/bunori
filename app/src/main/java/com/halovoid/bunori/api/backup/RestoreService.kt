package com.halovoid.bunori.api.backup

import android.content.Context
import android.net.Uri
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.StorageRepositoryImpl
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
import java.io.BufferedOutputStream
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

            ZipInputStream(inputStream.buffered(65536)).use { zis ->
                var entry = zis.nextEntry
                val buffer = ByteArray(65536)
                while (entry != null) {
                    val file = File(tempDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(file), 65536).use { fos ->
                            var read: Int
                            while (zis.read(buffer).also { read = it } != -1) {
                                fos.write(buffer, 0, read)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val preferenceRepository = PreferenceRepository.getInstance(context)
            val currentExportFolderUri = preferenceRepository.exportFolderUri.firstOrNull()
            val currentOnboardingCompleted = preferenceRepository.isOnboardingCompleted.firstOrNull()

            AppDatabase.closeAndResetDatabase()

            val dbFile = File(tempDir, "database.db").takeIf { it.exists() }
                ?: tempDir.walkTopDown().firstOrNull { it.name == "database.db" }
            if (dbFile != null && dbFile.exists()) {
                val targetDbFile = context.getDatabasePath("bunori.db")
                targetDbFile.parentFile?.mkdirs()
                dbFile.copyTo(targetDbFile, overwrite = true)
                File(targetDbFile.path + "-wal").delete()
                File(targetDbFile.path + "-shm").delete()
                File(targetDbFile.path + "-journal").delete()
            }

            val datastoreDir = File(tempDir, "datastore").takeIf { it.exists() && it.isDirectory }
                ?: tempDir.walkTopDown().firstOrNull { it.isDirectory && it.name == "datastore" }
            if (datastoreDir != null && datastoreDir.exists() && datastoreDir.isDirectory) {
                val targetDatastoreDir = File(context.filesDir, "datastore")
                targetDatastoreDir.mkdirs()
                datastoreDir.copyRecursively(targetDatastoreDir, overwrite = true)

                if (currentExportFolderUri != null) {
                    preferenceRepository.setExportFolder(currentExportFolderUri)
                }
                if (currentOnboardingCompleted != null) {
                    preferenceRepository.setOnboardingCompleted(currentOnboardingCompleted)
                }
            }

            val novelsDir = File(tempDir, "novels").takeIf { it.exists() && it.isDirectory }
                ?: tempDir.walkTopDown().firstOrNull { it.isDirectory && it.name == "novels" }
            val restoredFilesMap = mutableMapOf<String, Uri>()
            val restoredNovelCoversMap = mutableMapOf<String, Uri>()

            if (novelsDir != null && novelsDir.exists() && novelsDir.isDirectory) {
                val storageRepository = StorageRepositoryImpl.getInstance(context)
                novelsDir.walkTopDown().filter { it.isFile }.forEach { file ->
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
                            sourceFile = file
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

            reconcileDatabaseUris(restoredFilesMap, restoredNovelCoversMap)
            AppDatabase.closeAndResetDatabase()

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

            val allDownloads = downloadDao.getAllDownloads()
            val updatedDownloads = mutableListOf<com.halovoid.bunori.data.db.entities.DownloadEntity>()

            for (download in allDownloads) {
                val oldLocation = download.fileLocation
                val fileName = oldLocation.substringAfterLast('/')

                if (restoredFilesMap.containsKey(fileName)) {
                    val decoded = Uri.decode(oldLocation)
                    val novelIdx = decoded.lastIndexOf("novels/", ignoreCase = true)
                    val relPath = if (novelIdx != -1) {
                        decoded.substring(novelIdx)
                    } else {
                        "novels/${download.novelUrl.hashCode()}/chapters/$fileName"
                    }
                    if (download.fileLocation != relPath) {
                        updatedDownloads.add(download.copy(fileLocation = relPath))
                    }
                }
            }

            if (updatedDownloads.isNotEmpty()) {
                downloadDao.upsertDownloads(updatedDownloads)
            }

            val allNovels = novelDao.getAllNovelsOnce()
            val updatedNovels = mutableListOf<com.halovoid.bunori.data.db.entities.NovelEntity>()

            for (novel in allNovels) {
                val oldCover = novel.coverUrl
                if (oldCover != null) {
                    val matchedCoverEntry = restoredNovelCoversMap.entries.firstOrNull { (novelKey, _) ->
                        oldCover.contains(novelKey)
                    }

                    if (matchedCoverEntry != null) {
                        val decoded = Uri.decode(oldCover)
                        val novelIdx = decoded.lastIndexOf("novels/", ignoreCase = true)
                        val relPath = if (novelIdx != -1) {
                            decoded.substring(novelIdx)
                        } else {
                            "novels/${matchedCoverEntry.key}/covers/cover.jpg"
                        }
                        if (novel.coverUrl != relPath) {
                            updatedNovels.add(novel.copy(coverUrl = relPath))
                        }
                    }
                }
            }

            if (updatedNovels.isNotEmpty()) {
                novelDao.upsertNovels(updatedNovels)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
