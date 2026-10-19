package com.halovoid.bunori.data.handlers

import androidx.core.net.toUri
import com.halovoid.bunori.api.core.crawler.Crawler
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.api.core.network.interceptor.CloudflareBypassException
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.handlers.utility.parsedMetadata
import com.halovoid.bunori.data.repository.ChapterRepository
import com.halovoid.bunori.data.repository.DownloadRepository
import com.halovoid.bunori.data.repository.NovelRepository
import com.halovoid.bunori.data.repository.StorageRepository
import com.halovoid.bunori.data.scheduler.jobs.JobHandler
import com.halovoid.bunori.data.scheduler.jobs.JobResult
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Download
import com.halovoid.bunori.utils.Logger
import com.halovoid.bunori.wasm.WamrHttpBridge
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class ChapterHandler(
    private val chapterRepository: ChapterRepository,
    private val storageRepository: StorageRepository,
    private val crawlerFactory: CrawlerFactory,
    private val downloadRepository: DownloadRepository,
    private val novelRepository: NovelRepository
) : JobHandler {
    override suspend fun handle(task: TaskEntity): JobResult {
        val metadata = task.parsedMetadata
        val chapterId = metadata.chapterId
            ?: return JobResult.Failure(Exception("Failure to complete request: missing chapter ID"))

        val chapter = try {
            chapterRepository.getChapterById(chapterId)
        } catch (e: Exception) {
            return JobResult.Failure(Exception("Chapter not found for ID: $chapterId", e))
        }

        val chapterSource = chapter.scanlationSource.takeIf {
            it.isNotBlank() && it != "NotProvided" && it != "Not Provided"
        }
        val crawlerName = chapterSource
            ?: metadata.crawlerName
            ?: return JobResult.Failure(Exception("No Crawler Found"))

        val crawler = crawlerFactory.getCrawler(crawlerName)
            ?: metadata.crawlerName?.let { crawlerFactory.getCrawler(it) }
            ?: return JobResult.Failure(Exception("No Crawler Found for name: $crawlerName"))

        val novel = novelRepository.getNovelByUrl(chapter.novelUrl)
        val novelTitle = novel?.title ?: "Novel"

        // Cache Promotion: Check if valid cached version is already available locally
        val existingDownload = downloadRepository.getDownload(chapter.novelUrl, chapter.url)
        if (existingDownload != null) {
            if (!existingDownload.isCache) {
                // it's like saying the thing is in the download still I am redownloading
                // I don't know how and since its there and is not a cache I consider it downloaded
                return JobResult.Success
            }
            val now = System.currentTimeMillis()
            val isExpired = existingDownload.expirationTime?.let { it < now } ?: false
            if (!isExpired) {
                val cachedContent = try {
                    storageRepository.read(existingDownload.fileLocation.toUri()) // cached file location is stored as direct String URI no need to convert
                } catch (_: Exception) {
                    null
                }

                if (!cachedContent.isNullOrBlank() && cachedContent.trim().length > 50) {
                    val novelKey = crawler.getNovelKey(chapter.novelUrl)
                    val fileName = "${chapter.index.toString().padStart(4, '0')}_${chapter.id}.html.gz"
                    val relativeDir = "novels/$novelKey/chapters"

                    val relativeLocation = "$relativeDir/$fileName"

                    val targetDirUri = storageRepository.ensureDirectory(relativeDir) ?: return JobResult.Failure(Exception("Failed to ensure target directory $relativeDir"))
                    storageRepository.createFile(
                        parentUri = targetDirUri,
                        name = fileName,
                        mime = "application/gzip",
                        data = cachedContent.gzip()
                    ) ?: return JobResult.Failure(Exception("Failed to write cache file"))

                    downloadRepository.saveDownload(
                        existingDownload.copy(
                            fileLocation = relativeLocation,
                            isCache = false,
                            expirationTime = null,
                            downloadedAt = System.currentTimeMillis()
                        )
                    )

                    android.util.Log.e("ChapterHandler", "Cache promotion SUCCESS: saved to $relativeLocation")
                    return JobResult.Success
                }
            }
        }

        val targetUrl = task.url?.takeIf { it.isNotBlank() } ?: return JobResult.Failure(Exception("No valid URL found for chapter ${chapter.title}"))

        return try {
            val fileLocation = loadAndSaveFile(targetUrl, crawler, chapter)

            downloadRepository.saveDownload(
                Download(
                    novelUrl = chapter.novelUrl,
                    chapterUrl = chapter.url,
                    fileLocation = fileLocation,
                    chapterIndex = chapter.index,
                    chapterTitle = chapter.title,
                    scanlationSource = chapter.scanlationSource,
                    novelTitle = novelTitle
                )
            )

            JobResult.Success
        } catch (e: Exception) {
            val isCloudflare = crawler.webviewNeeded == true || 
                WamrHttpBridge.consumeCloudflareBlocked() ||
                e is CloudflareBypassException ||
                e.cause is CloudflareBypassException ||
                e.message?.contains("Cloudflare", ignoreCase = true) == true

            if (isCloudflare) {
                JobResult.Blocked
            } else {
                JobResult.Failure(e)
            }
        }
    }

    suspend fun loadAndSaveFile(url: String, crawler: Crawler, chapter: Chapter): String {
        if (url.startsWith("novels/")) {
            return url
        }

        val chapterContent = crawler.getChapterContent(url)
        if (chapterContent.isNullOrBlank()) {
            throw IllegalStateException("Empty content returned from source for chapter ${chapter.title}")
        }
        if (chapterContent.trim().length <= 10) {
            throw IllegalStateException("Content too short (${chapterContent.trim().length} chars) for chapter ${chapter.title}")
        }

        val novelKey = crawler.getNovelKey(chapter.novelUrl)
        val fileName = "${chapter.index.toString().padStart(4, '0')}_${chapter.id}.html.gz"
        val relativeDir = "novels/$novelKey/chapters"

        val targetDirUri = storageRepository.ensureDirectory(relativeDir) ?: throw IllegalStateException("Failed to ensure directory : $relativeDir");

        storageRepository.createFile(
            parentUri = targetDirUri,
            name = fileName,
            mime = "application/gzip",
            data = chapterContent.gzip()
        ) ?: throw IllegalStateException("Failed to create file: $fileName")

        return "$relativeDir/$fileName"
    }
}

fun String.gzip(): ByteArray {
    val byteStream = ByteArrayOutputStream()
    GZIPOutputStream(byteStream).use { gzip ->
        gzip.write(this.toByteArray(Charsets.UTF_8))
    }
    return byteStream.toByteArray();
}