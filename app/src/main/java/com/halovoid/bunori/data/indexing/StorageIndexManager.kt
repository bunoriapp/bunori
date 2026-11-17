package com.halovoid.bunori.data.indexing

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.db.dao.ChapterDao
import com.halovoid.bunori.data.db.dao.DownloadDao
import com.halovoid.bunori.data.db.dao.NovelDao
import com.halovoid.bunori.data.db.entities.DownloadEntity
import com.halovoid.bunori.data.repository.StorageRepository
import com.halovoid.bunori.data.repository.StorageTree
import com.halovoid.bunori.data.scheduler.jobs.MaintenanceActivity
import com.halovoid.bunori.data.scheduler.jobs.MaintenanceScheduler
import com.halovoid.bunori.domain.models.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/*
How metadata is stored:

root metadata (novels/metadata.json):
{
    "version": 1,
    "novels": [
        {
            "url": "some url",
            "name": "some name",
            "file": "novels/location"
        }
    ]
}

novel level metadata (novels/<folder>/metadata.json):
{
    "name": "novel name",
    "url": "novel url",
    "chapter_cnt": 12,
    "chapters": [
        {
            "index": 1,
            "url": "chapter url",
            "loc": "novels/folder/chapters/file.html.gz",
            "title": "Chapter 1",
            "source": "bext.novelarchive"
        }
    ]
}
*/

@Serializable
data class RootMetadata(
    val version: Int = 1,
    val novels: List<RootNovelEntry> = emptyList()
)

@Serializable
data class RootNovelEntry(
    val url: String = "",
    val name: String = "",
    val file: String = ""
)

@Serializable
data class NovelMetadata(
    val name: String = "",
    val url: String = "",
    @SerialName("chapter_cnt") val chapterCnt: Int = 0,
    val chapters: List<ChapterMetadata> = emptyList()
)

@Serializable
data class ChapterMetadata(
    val index: Int = 0,
    val url: String = "",
    val loc: String = "",
    val title: String = "",
    val source: String = "Not Provided"
)

class StorageIndexManager private constructor(
    private val context: Context,
    private val storRepo: StorageRepository = StorageRepository.getInstance(context),
    private val downloadDao: DownloadDao = AppDatabase.getDatabase(context).downloadDao()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @SuppressLint("StaticFieldLeak")
    companion object {
        private const val ROOT_METADATA_PATH = "novels/metadata.json"
        private const val ROOT_NOVELS_DIR = "novels"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: StorageIndexManager? = null

        fun getInstance(context: Context): StorageIndexManager {
            val current = instance
            if (current != null) return current

            synchronized(this) {
                if (instance == null) {
                    instance = StorageIndexManager(context.applicationContext)
                }
                return instance!!
            }
        }
    }

    suspend fun getReg(path: String): String? = withContext(Dispatchers.IO) {
        val uri = storRepo.relativeToUri(path) ?: return@withContext null
        try {
            val stream = storRepo.openInputStream(uri) ?: return@withContext null
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        } catch (e: Exception) {
            Log.e("StorageIndexManager", "Failed to read $path", e)
            null
        }
    }

    suspend fun saveReg(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val bytes = content.toByteArray(Charsets.UTF_8)
            val uri = storRepo.relativeToUri(path)
            if (uri != null) {
                return@withContext storRepo.write(uri, bytes)
            }
            val parentPath = path.substringBeforeLast('/', "")
            val fileName = path.substringAfterLast('/')
            val parentUri = storRepo.ensureDirectory(parentPath) ?: return@withContext false
            val created = storRepo.createFile(parentUri, fileName, "application/json", bytes)
            created != null
        } catch (e: Exception) {
            Log.e("StorageIndexManager", "Failed to save $path", e)
            false
        }
    }

    suspend fun createRegBak(path: String): Boolean = withContext(Dispatchers.IO) {
        val content = getReg(path) ?: return@withContext false
        saveReg("$path.bak", content)
    }

    suspend fun forceRebuildIndex(
        onProgress: (current: Int, total: Int, name: String) -> Unit = { _, _, _ -> }
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        if (!MaintenanceScheduler.acquire(MaintenanceActivity.INDEXING)) {
            return@withContext Pair(0, 0)
        }
        try {
            val novelTree = storRepo.tree(storRepo.relativeToUri("novels"))
            if (novelTree === null) return@withContext Pair(0, 0)
            val rootMeta: MutableList<RootNovelEntry> = mutableListOf()
            val downloads = downloadDao.getAllDownloads()
            val novelFolders = novelTree.children.filter { it.isDirectory }
            val totalFolders = novelFolders.size

            for ((foldIdx, item) in novelFolders.withIndex()) {
                val folderName = item.info.name

                val chapterTree = item.children.find { it.name == "chapters" && it.isDirectory }
                if (chapterTree === null) continue

                val entry = downloads.find { it.fileLocation.contains(folderName) }
                // if there is no entry regarding the same the header can never be rebuilt for the directory
                if (entry === null) continue
                var novelMeta = NovelMetadata(
                    url = entry.novelUrl,
                    name = entry.novelTitle,
                    chapterCnt = 0,
                    chapters = emptyList()
                )
                rootMeta.add(
                    element = RootNovelEntry(
                        url = novelMeta.url,
                        name = novelMeta.name,
                        file = "novels/$folderName"
                    )
                )

                val chapterFiles = chapterTree.children.filter { !it.isDirectory && it.name.endsWith(".gz") }
                val totalChapterDocs = chapterFiles.size

                for ((chIdx, chapter) in chapterFiles.withIndex()) {
                    val chapterFile = chapter.info.name
                    if (chIdx % 5 == 0 || chIdx == totalChapterDocs - 1) {
                        onProgress(foldIdx + 1, totalFolders, "${novelMeta.name} (${chIdx + 1}/$totalChapterDocs)")
                    }

                    val stream = storRepo.openInputStream(chapter.uri) ?: continue
                    val readResult = try {
                        stream.use { raw ->
                            GZIPInputStream(raw).bufferedReader(Charsets.UTF_8).use { it.readLine() }
                        }
                    } catch (_: Exception) {
                        null
                    }
                    var line = readResult?.trim() ?: ""

                    val prefix = "<!--bunori:"
                    val suffix = "-->"
                    if (!line.startsWith(prefix) || !line.endsWith(suffix)) {
                        // case where header is missing from the file and header is inserted back into the file
                        val dlEntry = downloads.find { it.fileLocation.contains(chapterFile) }
                        if (dlEntry === null) {
                            continue
                        }
                        val headerData = ChapterMetadata(
                            url = dlEntry.chapterUrl,
                            index = dlEntry.chapterIndex,
                            title = dlEntry.chapterTitle,
                            source = dlEntry.scanlationSource,
                            loc = "novels/$folderName/chapters/$chapterFile"
                        )
                        val contentStream = storRepo.openInputStream(chapter.info.uri) ?: continue
                        val content = try {
                            contentStream.use { s ->
                                GZIPInputStream(s).bufferedReader(Charsets.UTF_8).use { it.readText() }
                            }
                        } catch (_: Exception) {
                            null
                        } ?: continue

                        val header = "<!--bunori:${json.encodeToString(headerData)}-->\n"
                        val byteOutput = ByteArrayOutputStream()
                        GZIPOutputStream(byteOutput).use { gz -> gz.write((header + content).toByteArray(Charsets.UTF_8)) }
                        line = header.trim() // trim remove the \n from the line
                        storRepo.write(chapter.uri, byteOutput.toByteArray())
                    }
                    val jsonString = line.removePrefix(prefix).removeSuffix(suffix)
                    val headerData = try {
                        json.decodeFromString<ChapterMetadata>(jsonString).copy(
                            loc = "novels/$folderName/chapters/$chapterFile" // in case the chapter file is changed this updates the loc so that reindexing can work properly
                        )
                    } catch (_: Exception) {
                        null
                    } ?: continue

                    novelMeta = novelMeta.copy(
                        chapterCnt = novelMeta.chapterCnt + 1,
                        chapters = novelMeta.chapters + headerData
                    )
                }

                // delete and update the metadata.json
                storRepo.deleteFile(storRepo.relativeToUri("novels/$folderName/metadata.json"))
                saveReg("novels/$folderName/metadata.json", json.encodeToString(novelMeta))
            }

            // delete and update the root metdata.json
            val rootReg = RootMetadata(version = 1, novels = rootMeta)
            storRepo.deleteFile(storRepo.relativeToUri("novels/metadata.json"))
            saveReg("novels/metadata.json", json.encodeToString(rootReg))

            return@withContext reIndex()
        } catch (e: Exception) {
            Log.e("StorageIndexManager", "Failed to force rebuild index", e)
            return@withContext Pair(0, 0)
        } finally {
            MaintenanceScheduler.release(MaintenanceActivity.INDEXING)
        }
    }

    suspend fun checkIndex(
        onProgress: (current: Int, total: Int, status: String) -> Unit = { _, _, _ -> }
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        if (!MaintenanceScheduler.acquire(MaintenanceActivity.INDEXING)) {
            return@withContext Pair(0, 0)
        }
        try {
            val metadata = getReg(ROOT_METADATA_PATH) ?: return@withContext Pair(0, 0)
            val jmetadata = try {
                json.decodeFromString<RootMetadata>(metadata)
            } catch (e: Exception) {
                return@withContext Pair(0, 0)
            }

            val rootUri = storRepo.relativeToUri(ROOT_NOVELS_DIR)
            val tree = if (rootUri != null) storRepo.tree(rootUri) else null

            // Collect all existing relative paths into an in-memory set for O(1) existence checks
            val existingFiles = mutableSetOf<String>()
            fun collectPaths(node: StorageTree, currentPath: String) {
                for (child in node.children) {
                    val childPath = "$currentPath/${child.name}"
                    if (child.isDirectory) {
                        collectPaths(child, childPath)
                    } else {
                        existingFiles.add(childPath)
                    }
                }
            }
            if (tree != null) {
                collectPaths(tree, ROOT_NOVELS_DIR)
            }

            var failLocs = 0
            var succLocs = 0
            val total = jmetadata.novels.size

            for (idx in 0 until jmetadata.novels.size) {
                val novel = jmetadata.novels[idx]
                val index = "${novel.file}/metadata.json"
                val content = getReg(index)
                if (content == null) {
                    failLocs++
                    continue
                }
                val jcontent = try {
                    json.decodeFromString<NovelMetadata>(content)
                } catch (_: Exception) {
                    failLocs++
                    continue
                }

                for (chapter in jcontent.chapters) {
                    val exists = if (tree != null) {
                        chapter.loc in existingFiles
                    } else {
                        val fileUri = storRepo.relativeToUri(chapter.loc)
                        fileUri != null && storRepo.exists(fileUri)
                    }

                    if (exists) {
                        succLocs++
                    } else {
                        failLocs++
                    }
                }
                onProgress(idx + 1, total, "${novel.name}: $succLocs found, $failLocs missing")
            }
            Pair(succLocs, failLocs)
        } finally {
            MaintenanceScheduler.release(MaintenanceActivity.INDEXING)
        }
    }

    suspend fun reIndex(
        onProgress: (current: Int, total: Int, novelName: String) -> Unit = { _, _, _ -> }
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val isAlreadyLocked = MaintenanceScheduler.currentActivity.value != MaintenanceActivity.NONE
        val acquired = if (!isAlreadyLocked) MaintenanceScheduler.acquire(MaintenanceActivity.INDEXING) else true
        if (!acquired) {
            return@withContext Pair(0, 0)
        }
        try {
            val metadata = getReg(ROOT_METADATA_PATH) ?: return@withContext Pair(0, 0)
            val jmetadata = try {
                json.decodeFromString<RootMetadata>(metadata)
            } catch (e: Exception) {
                return@withContext Pair(0, 0)
            }

            var totalNovels = 0
            var totalChapters = 0
            val total = jmetadata.novels.size
            val validNovels = mutableListOf<RootNovelEntry>()
            val validChapterLocations = mutableSetOf<String>()

            for (idx in 0 until jmetadata.novels.size) {
                val novel = jmetadata.novels[idx]
                val index = "${novel.file}/metadata.json"
                val content = getReg(index)
                if (content == null) {
                    continue
                }
                val jcontent = try {
                    json.decodeFromString<NovelMetadata>(content)
                } catch (_: Exception) {
                    continue
                }

                validNovels.add(novel)

                val novelUrl = if (jcontent.url.isNotBlank()) jcontent.url else novel.url
                val novelName = if (jcontent.name.isNotBlank()) jcontent.name else novel.name
                onProgress(idx + 1, total, novelName)

                val downloadEntities = mutableListOf<DownloadEntity>()
                for (chapter in jcontent.chapters) {
                    if (chapter.url.isBlank()) continue
                    validChapterLocations.add(chapter.loc)
                    downloadEntities.add(
                        DownloadEntity(
                            id = 0,
                            novelUrl = novelUrl,
                            chapterUrl = chapter.url,
                            fileLocation = chapter.loc,
                            chapterIndex = chapter.index,
                            chapterTitle = if (chapter.title.isNotBlank()) chapter.title else "Chapter ${chapter.index}",
                            scanlationSource = chapter.source,
                            novelTitle = novelName,
                            isCache = false
                        )
                    )
                }

                if (downloadEntities.isNotEmpty()) {
                    downloadDao.upsertDownloads(downloadEntities)
                    totalChapters += downloadEntities.size
                    totalNovels++
                }
            }

            if (validNovels.size != jmetadata.novels.size) {
                saveReg(ROOT_METADATA_PATH, json.encodeToString(RootMetadata(version = jmetadata.version, novels = validNovels)))
            }

            try {
                val currentDbDownloads = downloadDao.getAllDownloads()
                val validNovelUrls = mutableSetOf<String>()
                for (v in validNovels) {
                    if (v.url.isNotBlank()) validNovelUrls.add(v.url)
                }

                for (item in currentDbDownloads) {
                    if (!item.isCache) {
                        if (item.novelUrl !in validNovelUrls || item.fileLocation !in validChapterLocations) {
                            val fileUri = storRepo.relativeToUri(item.fileLocation)
                            if (fileUri == null || !storRepo.exists(fileUri)) {
                                downloadDao.deleteDownloadById(item.id)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            Pair(totalNovels, totalChapters)
        } finally {
            if (!isAlreadyLocked) {
                MaintenanceScheduler.release(MaintenanceActivity.INDEXING)
            }
        }
    }

    suspend fun recordDownload(
        novelUrl: String,
        novelTitle: String,
        crawlerName: String,
        chapter: Chapter,
        fileLocation: String
    ) = withContext(Dispatchers.IO) {
        try {
            val dirName = fileLocation.removePrefix("novels/").substringBefore('/')
            if (dirName.isBlank()) return@withContext

            val path = "novels/$dirName/metadata.json"
            val existingContent = getReg(path)

            val chapters = mutableListOf<ChapterMetadata>()
            if (existingContent != null) {
                try {
                    val manifest = json.decodeFromString<NovelMetadata>(existingContent)
                    chapters.addAll(manifest.chapters)
                } catch (_: Exception) {}
            }

            val chapterItem = ChapterMetadata(
                index = chapter.index,
                url = chapter.url,
                loc = fileLocation,
                title = chapter.title,
                source = if (chapter.scanlationSource.isNotBlank()) chapter.scanlationSource else crawlerName
            )

            var foundIndex = -1
            for (i in 0 until chapters.size) {
                if (chapters[i].url == chapter.url) {
                    foundIndex = i
                    break
                }
            }

            if (foundIndex >= 0) {
                chapters[foundIndex] = chapterItem
            } else {
                chapters.add(chapterItem)
            }

            val updated = NovelMetadata(
                name = novelTitle,
                url = novelUrl,
                chapterCnt = chapters.size,
                chapters = chapters
            )
            saveReg(path, json.encodeToString(updated))

            val novelsList = mutableListOf<RootNovelEntry>()
            val rootContent = getReg(ROOT_METADATA_PATH)
            if (rootContent != null) {
                try {
                    val rootMeta = json.decodeFromString<RootMetadata>(rootContent)
                    novelsList.addAll(rootMeta.novels)
                } catch (_: Exception) {}
            }

            var alreadyRegistered = false
            for (entry in novelsList) {
                if (entry.file == "novels/$dirName" || entry.url == novelUrl) {
                    alreadyRegistered = true
                    break
                }
            }

            if (!alreadyRegistered) {
                novelsList.add(RootNovelEntry(url = novelUrl, name = novelTitle, file = "novels/$dirName"))
                saveReg(ROOT_METADATA_PATH, json.encodeToString(RootMetadata(version = 1, novels = novelsList)))
            }
        } catch (e: Exception) {
            Log.e("StorageIndexManager", "Failed to record download", e)
        }
    }

    suspend fun reindexNovel(novelUrl: String): Int = withContext(Dispatchers.IO) {
        val rootContent = getReg(ROOT_METADATA_PATH) ?: return@withContext 0
        val rootMeta = try {
            json.decodeFromString<RootMetadata>(rootContent)
        } catch (_: Exception) {
            return@withContext 0
        }

        var novelEntry: RootNovelEntry? = null
        for (entry in rootMeta.novels) {
            if (entry.url == novelUrl) {
                novelEntry = entry
                break
            }
        }
        if (novelEntry == null) return@withContext 0

        val novelContent = getReg("${novelEntry.file}/metadata.json") ?: return@withContext 0
        val novelMeta = try {
            json.decodeFromString<NovelMetadata>(novelContent)
        } catch (_: Exception) {
            return@withContext 0
        }

        val downloads = mutableListOf<DownloadEntity>()
        for (ch in novelMeta.chapters) {
            if (ch.url.isBlank()) continue
            downloads.add(
                DownloadEntity(
                    id = 0,
                    novelUrl = novelUrl,
                    chapterUrl = ch.url,
                    fileLocation = ch.loc,
                    chapterIndex = ch.index,
                    chapterTitle = if (ch.title.isNotBlank()) ch.title else "Chapter ${ch.index}",
                    scanlationSource = ch.source,
                    novelTitle = novelMeta.name,
                    isCache = false
                )
            )
        }

        if (downloads.isNotEmpty()) {
            downloadDao.upsertDownloads(downloads)
        }
        downloads.size
    }
}
