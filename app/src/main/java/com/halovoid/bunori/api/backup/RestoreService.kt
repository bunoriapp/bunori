package com.halovoid.bunori.api.backup

import android.content.Context
import android.net.Uri
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.StorageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class RestoreService(private val context: Context) {

    private val storageRepository by lazy { StorageRepository.getInstance(context) }

    suspend fun restoreBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext false
        val preferenceRepository = PreferenceRepository.getInstance(context)

        // Preserve current critical settings before DB/datastore overwrite
        val currentExportFolderUri = preferenceRepository.exportFolderUri.firstOrNull()
        val currentOnboardingCompleted = preferenceRepository.isOnboardingCompleted.firstOrNull()

        val restoredFilesMap = mutableMapOf<String, String>() // fileName -> relativePath
        val restoredNovelCoversMap = mutableMapOf<String, String>() // novelKey -> relativePath

        var tempDbFile: File? = null
        val datastoreDir = File(context.filesDir, "datastore").apply { mkdirs() }

        try {
            AppDatabase.closeAndResetDatabase()

            ZipInputStream(inputStream.buffered(65536)).use { zis ->
                val buffer = ByteArray(65536)
                var entry = zis.nextEntry

                while (entry != null) {
                    val entryName = entry.name

                    when {
                        entry.isDirectory -> { /* Skip directory entries */ }

                        entryName == "database.db" -> {
                            tempDbFile = File(context.cacheDir, "restore_temp_db.db").apply {
                                outputStream().buffered(65536).use { out -> zis.copyTo(out, 65536) }
                            }
                        }

                        entryName.startsWith("datastore/") -> {
                            val targetFile = File(context.filesDir, entryName)
                            targetFile.parentFile?.mkdirs()
                            targetFile.outputStream().buffered(65536).use { out -> zis.copyTo(out, 65536) }
                        }

                        entryName.startsWith("novels/") -> {
                            val relDir = entryName.substringBeforeLast('/')
                            val fileName = entryName.substringAfterLast('/')
                            val mimeType = when {
                                fileName.endsWith(".gz", true) -> "application/gzip"
                                fileName.endsWith(".png", true) -> "image/png"
                                fileName.endsWith(".webp", true) -> "image/webp"
                                fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
                                else -> "application/octet-stream"
                            }

                            val dirUri = ensureDirectory(relDir)
                            if (dirUri != null) {
                                val bytes = ByteArrayOutputStream().apply { zis.copyTo(this, 65536) }.toByteArray()
                                storageRepository.createFile(dirUri, fileName, mimeType, bytes)

                                restoredFilesMap[fileName] = entryName
                                if (relDir.contains("/covers")) {
                                    val novelKey = relDir.removePrefix("novels/").substringBefore('/')
                                    if (novelKey.isNotBlank()) {
                                        restoredNovelCoversMap[novelKey] = entryName
                                    }
                                }
                            }
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 1. Restore Database File
            tempDbFile?.let { srcFile ->
                val targetDbFile = context.getDatabasePath("bunori.db").apply { parentFile?.mkdirs() }
                srcFile.copyTo(targetDbFile, overwrite = true)
                File(targetDbFile.path + "-wal").delete()
                File(targetDbFile.path + "-shm").delete()
                File(targetDbFile.path + "-journal").delete()
                srcFile.delete()
            }

            // 2. Restore Preferences
            if (currentExportFolderUri != null) {
                preferenceRepository.setExportFolder(currentExportFolderUri)
            }
            if (currentOnboardingCompleted != null) {
                preferenceRepository.setOnboardingCompleted(currentOnboardingCompleted)
            }

            // 3. Reconcile Database Paths
            reconcileDatabaseUris(restoredFilesMap, restoredNovelCoversMap)
            AppDatabase.closeAndResetDatabase()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            tempDbFile?.delete()
            false
        }
    }

    private suspend fun reconcileDatabaseUris(
        restoredFilesMap: Map<String, String>,
        restoredNovelCoversMap: Map<String, String>
    ) {
        if (restoredFilesMap.isEmpty() && restoredNovelCoversMap.isEmpty()) return

        try {
            val db = AppDatabase.getDatabase(context)
            val downloadDao = db.downloadDao()
            val novelDao = db.novelDao()

            val allDownloads = downloadDao.getAllDownloads()
            val updatedDownloads = allDownloads.mapNotNull { download ->
                val fileName = download.fileLocation.substringAfterLast('/')
                val newRelPath = restoredFilesMap[fileName]
                if (newRelPath != null && download.fileLocation != newRelPath) {
                    download.copy(fileLocation = newRelPath)
                } else null
            }
            if (updatedDownloads.isNotEmpty()) {
                downloadDao.upsertDownloads(updatedDownloads)
            }

            val allNovels = novelDao.getAllNovelsOnce()
            val updatedNovels = allNovels.mapNotNull { novel ->
                val oldCover = novel.coverUrl ?: return@mapNotNull null
                val matchedKey = restoredNovelCoversMap.keys.firstOrNull { oldCover.contains(it) }
                val newRelPath = matchedKey?.let { restoredNovelCoversMap[it] }

                if (newRelPath != null && novel.coverUrl != newRelPath) {
                    novel.copy(coverUrl = newRelPath)
                } else null
            }
            if (updatedNovels.isNotEmpty()) {
                novelDao.upsertNovels(updatedNovels)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun ensureDirectory(relativePath: String): Uri? {
        val existing = storageRepository.relativeToUri(relativePath)
        if (existing != null) return existing

        val rootUri = storageRepository.relativeToUri("") ?: return null
        val segments = relativePath.trim('/').split('/').filter { it.isNotBlank() }

        var currentUri = rootUri
        var currentPath = ""

        for (segment in segments) {
            currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            val nextUri = storageRepository.relativeToUri(currentPath)
                ?: storageRepository.createDirectory(currentUri, segment)
                ?: return null
            currentUri = nextUri
        }

        return currentUri
    }
}
