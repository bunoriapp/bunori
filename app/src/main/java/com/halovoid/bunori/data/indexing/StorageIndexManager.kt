package com.halovoid.bunori.data.indexing

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.db.dao.ChapterDao
import com.halovoid.bunori.data.db.dao.DownloadDao
import com.halovoid.bunori.data.db.dao.NovelDao
import com.halovoid.bunori.data.db.entities.ChapterEntity
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private val novelDao: NovelDao = AppDatabase.getDatabase(context).novelDao(),
    private val chapterDao: ChapterDao = AppDatabase.getDatabase(context).chapterDao(),
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

    private suspend fun readFirstLineOfGzip(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val stream = storRepo.openInputStream(uri) ?: return@withContext null
            stream.use { rawStream ->
                GZIPInputStream(rawStream).bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readLine()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun forceRebuildIndex(
        onProgress: (current: Int, total: Int, name: String) -> Unit = { _, _, _ -> }
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        if (!MaintenanceScheduler.acquire(MaintenanceActivity.INDEXING)) {
            return@withContext Pair(0, 0)
        }
        try {
            val rootUri = storRepo.relativeToUri(ROOT_NOVELS_DIR) ?: return@withContext Pair(0, 0)
            val tree = storRepo.tree(rootUri) ?: return@withContext Pair(0, 0)
            val folders = tree.directories()
            val rootNovels = mutableListOf<RootNovelEntry>()
            var totalChapters = 0

            var cachedDownloads: List<DownloadEntity>? = null

            for (foldIdx in 0 until folders.size) {
                val fold = folders[foldIdx]
                val foldPath = "$ROOT_NOVELS_DIR/${fold.name}"
                val chaptersDir = fold.findChild("chapters") ?: continue

                var novelUrl = ""
                var novelName = ""
                val chapterList = mutableListOf<ChapterMetadata>()

                val docs = mutableListOf<StorageTree>()
                for (f in chaptersDir.files()) {
                    if (f.name.endsWith(".gz")) {
                        docs.add(f)
                    }
                }
                val totalDocs = docs.size

                for (docIdx in 0 until totalDocs) {
                    val doc = docs[docIdx]
                    if (docIdx % 5 == 0 || docIdx == totalDocs - 1) {
                        val displayName = if (novelName.isNotBlank()) novelName else fold.name
                        onProgress(foldIdx + 1, folders.size, "$displayName (${docIdx + 1}/$totalDocs)")
                    }

                    val loc = "$foldPath/chapters/${doc.name}"
                    val firstLine = readFirstLineOfGzip(doc.uri) ?: ""
                    var chUrl = ""
                    var chIdx = 0
                    var chSource = "Not Provided"

                    if (firstLine.startsWith("<!--bunori:") && firstLine.contains("-->")) {
                        try {
                            val jsonStr = firstLine.substringAfter("<!--bunori:").substringBefore("-->").trim()
                            val obj = json.parseToJsonElement(jsonStr).jsonObject
                            chUrl = obj["url"]?.jsonPrimitive?.content ?: ""
                            chIdx = obj["idx"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                            chSource = obj["source"]?.jsonPrimitive?.content ?: "Not Provided"

                            val headerNovelUrl = obj["novelUrl"]?.jsonPrimitive?.content
                            if (!headerNovelUrl.isNullOrBlank() && novelUrl.isBlank()) {
                                novelUrl = headerNovelUrl
                            }
                            val headerNovelName = obj["novelName"]?.jsonPrimitive?.content
                            if (!headerNovelName.isNullOrBlank() && novelName.isBlank()) {
                                novelName = headerNovelName
                            }
                        } catch (_: Exception) {}
                    }

                    // Fallback: only if header is missing, query database to resolve and update the file
                    if (chUrl.isBlank()) {
                        if (cachedDownloads == null) {
                            cachedDownloads = downloadDao.getAllDownloads()
                        }
                        val dl = cachedDownloads.find {
                            it.fileLocation == loc || it.fileLocation.endsWith("/${doc.name}") || it.fileLocation == doc.name
                        }
                        if (dl != null) {
                            chUrl = dl.chapterUrl
                            chIdx = dl.chapterIndex
                            chSource = dl.scanlationSource
                            if (novelUrl.isBlank()) novelUrl = dl.novelUrl
                            if (novelName.isBlank()) novelName = dl.novelTitle
                        } else if (novelUrl.isNotBlank()) {
                            val dbChapters = chapterDao.getChapterFromNovel(novelUrl)
                            val fileChapterId = doc.name.substringAfter('_').substringBefore('.').toIntOrNull()
                            val fileIdx = doc.name.substringBefore('_').toIntOrNull()
                            val matched = dbChapters.find { it.id == fileChapterId } ?: dbChapters.find { it.index == fileIdx }
                            if (matched != null) {
                                chUrl = matched.url
                                chIdx = matched.index
                                chSource = matched.scanlationSource
                            }
                        }

                        if (chIdx == 0) {
                            chIdx = doc.name.substringBefore('_').toIntOrNull() ?: (docIdx + 1)
                        }
                        if (chUrl.isBlank()) {
                            chUrl = if (novelUrl.isNotBlank()) "$novelUrl#chapter-$chIdx" else "chapter-$chIdx"
                        }

                        // Write header into the legacy file
                        try {
                            val stream = storRepo.openInputStream(doc.uri)
                            val content = stream?.use { s ->
                                GZIPInputStream(s).bufferedReader(Charsets.UTF_8).use { it.readText() }
                            }
                            if (content != null) {
                                val escUrl = chUrl.replace("\"", "\\\"")
                                val escNovelUrl = novelUrl.replace("\"", "\\\"")
                                val escSource = chSource.replace("\"", "\\\"")
                                val header = "<!--bunori:{\"url\":\"$escUrl\",\"idx\":$chIdx,\"novelUrl\":\"$escNovelUrl\",\"source\":\"$escSource\"}-->\n"
                                val byteOut = ByteArrayOutputStream()
                                GZIPOutputStream(byteOut).use { gz ->
                                    gz.write((header + content).toByteArray(Charsets.UTF_8))
                                }
                                storRepo.write(doc.uri, byteOut.toByteArray())
                            }
                        } catch (e: Exception) {
                            Log.e("StorageIndexManager", "Failed to update header for ${doc.name}", e)
                        }
                    }

                    chapterList.add(
                        ChapterMetadata(
                            index = chIdx,
                            url = chUrl,
                            loc = loc,
                            title = "Chapter $chIdx",
                            source = chSource
                        )
                    )
                }

                // If novel name was not in headers, resolve from DB or existing metadata
                if (novelName.isBlank() && novelUrl.isNotBlank()) {
                    val dbNovel = novelDao.getNovelByUrl(novelUrl)
                    if (dbNovel != null && dbNovel.title.isNotBlank()) {
                        novelName = dbNovel.title
                    }
                }
                if (novelName.isBlank()) {
                    val existing = getReg("$foldPath/metadata.json")
                    if (existing != null) {
                        try {
                            val meta = json.decodeFromString<NovelMetadata>(existing)
                            if (meta.name.isNotBlank()) novelName = meta.name
                            if (novelUrl.isBlank() && meta.url.isNotBlank()) novelUrl = meta.url
                        } catch (_: Exception) {}
                    }
                }
                if (novelName.isBlank()) {
                    novelName = fold.name
                }

                // Ensure chapter URLs have novelUrl if chapter was missing it
                if (novelUrl.isNotBlank()) {
                    for (i in 0 until chapterList.size) {
                        val ch = chapterList[i]
                        if (ch.url.startsWith("chapter-")) {
                            chapterList[i] = ch.copy(url = "$novelUrl#${ch.url}")
                        }
                    }
                }

                chapterList.sortBy { it.index }

                createRegBak("$foldPath/metadata.json")
                val novelMeta = NovelMetadata(
                    name = novelName,
                    url = novelUrl,
                    chapterCnt = chapterList.size,
                    chapters = chapterList
                )
                saveReg("$foldPath/metadata.json", json.encodeToString(novelMeta))

                rootNovels.add(
                    RootNovelEntry(
                        url = novelUrl,
                        name = novelName,
                        file = foldPath
                    )
                )
                totalChapters += chapterList.size
            }

            createRegBak(ROOT_METADATA_PATH)
            val rootMeta = RootMetadata(
                version = 1,
                novels = rootNovels
            )
            saveReg(ROOT_METADATA_PATH, json.encodeToString(rootMeta))

            reIndex()

            Pair(rootNovels.size, totalChapters)
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
