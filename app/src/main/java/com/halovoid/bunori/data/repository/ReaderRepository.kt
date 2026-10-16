package com.halovoid.bunori.data.repository

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.net.toUri
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Download
import com.halovoid.bunori.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

interface ReaderRepository {
    suspend fun getChapterContent(chapter: Chapter, crawlerName: String): String
    suspend fun saveExtractedChapter(
        novelUrl: String,
        chapterUrl: String,
        chapterId: Int,
        chapterIndex: Int,
        chapterTitle: String,
        scanlationSource: String,
        html: String
    ): Boolean
    companion object {
        fun getInstance(context: Context): ReaderRepository = ReaderRepositoryImpl.getInstance(context)
    }
}

class ReaderRepositoryImpl private constructor(
    private val context: Context,
    private val downloadRepository: DownloadRepository = DownloadRepositoryImpl.getInstance(context),
    private val novelRepository: NovelRepository = NovelRepository.getInstance(context),
    private val storageRepository: StorageRepository = StorageRepositoryImpl.getInstance(context)
) : ReaderRepository {
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: ReaderRepository? = null

        fun getInstance(context: Context): ReaderRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReaderRepositoryImpl(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override suspend fun getChapterContent(chapter: Chapter, crawlerName: String): String =
        withContext(Dispatchers.IO) {
            val download = downloadRepository.getDownload(chapter.novelUrl, chapter.url)
            val now = System.currentTimeMillis()
            val html = if (download != null) {
                if (download.isCache && download.expirationTime != null && download.expirationTime < now) {
                    deleteCacheFile(download.fileLocation)
                    downloadRepository.deleteDownload(download.novelUrl, download.chapterUrl)
                    fetchAndCache(chapter, crawlerName)
                } else {
                    readDownloaded(download) ?: fetchAndCache(chapter, crawlerName)
                }
            } else {
                fetchAndCache(chapter, crawlerName)
            }
            html ?: "<p>Couldn't load this chapter. Check your connection and try again</p>"
        }

    override suspend fun saveExtractedChapter(
        novelUrl: String,
        chapterUrl: String,
        chapterId: Int,
        chapterIndex: Int,
        chapterTitle: String,
        scanlationSource: String,
        html: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (html.isBlank()) return@withContext false
        try {
            val cacheDir = File(context.cacheDir, "chapter_cache").apply { mkdirs() }
            val safeFileName = "ch_${novelUrl.hashCode()}_${chapterId}_${System.currentTimeMillis()}.html.gz"
            val cacheFile = File(cacheDir, safeFileName)
            writeCompressed(cacheFile, html)

            val novel = novelRepository.getNovelByUrl(novelUrl)
            val novelTitle = novel?.title ?: "Novel"

            val cachedDownload = Download(
                novelUrl = novelUrl,
                chapterUrl = chapterUrl,
                fileLocation = cacheFile.absolutePath,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                scanlationSource = scanlationSource,
                novelTitle = novelTitle,
                sizeBytes = cacheFile.length(),
                downloadedAt = System.currentTimeMillis(),
                isCache = true,
                expirationTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
            )
            downloadRepository.saveDownload(cachedDownload)
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun readDownloaded(download: Download): String? {
        Logger.e("READDOWNLOAD: ${download.fileLocation} trying to open")
        val fileLocation = download.fileLocation
        if (fileLocation.isBlank()) return null

        if (download.isCache) {
            Logger.e("READDOWNLOAD: ${download.fileLocation} found in cache")
            return storageRepository.read(fileLocation.toUri())
        }

        val locToUri = storageRepository.relativeToUri(fileLocation) ?: return null;
        return storageRepository.read(locToUri);
    }

    private suspend fun fetchAndCache(chapter: Chapter, crawlerName: String): String? {
        val crawler = CrawlerFactory.getCrawler(crawlerName) ?: return null
        val url = chapter.sourceUrl?.takeIf { it.isNotBlank() } ?: chapter.url
        val html = crawler.getChapterContent(url) ?: return null
        if (html.isNotBlank() && html.trim().length > 50) {
            try {
                val cacheDir = File(context.cacheDir, "chapter_cache").apply { mkdirs() }
                val safeFileName = "ch_${chapter.novelUrl.hashCode()}_${chapter.id}_${System.currentTimeMillis()}.html.gz"
                val cacheFile = File(cacheDir, safeFileName)
                writeCompressed(cacheFile, html)

                val novel = novelRepository.getNovelByUrl(chapter.novelUrl)
                val novelTitle = novel?.title ?: "Novel"

                val cachedDownload = Download(
                    novelUrl = chapter.novelUrl,
                    chapterUrl = chapter.url,
                    fileLocation = cacheFile.absolutePath,
                    chapterIndex = chapter.index,
                    chapterTitle = chapter.title,
                    scanlationSource = chapter.scanlationSource,
                    novelTitle = novelTitle,
                    sizeBytes = cacheFile.length(),
                    downloadedAt = System.currentTimeMillis(),
                    isCache = true,
                    expirationTime = System.currentTimeMillis() + 2 * 60 * 60 * 1000L // 2 hours
                )
                downloadRepository.saveDownload(cachedDownload)
            } catch (_: Exception) {
                // Ignore caching errors so chapter display is uninterrupted
            }
        }
        return html
    }

    private fun writeCompressed(file: File, text: String) {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        file.writeBytes(bos.toByteArray())
    }

    private fun deleteCacheFile(fileLocation: String) {
        try {
            File(fileLocation).delete()
        } catch (_: Exception) {}
    }
}
