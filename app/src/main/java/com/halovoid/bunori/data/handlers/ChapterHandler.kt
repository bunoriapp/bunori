package com.halovoid.bunori.data.handlers

import android.net.Uri
import com.halovoid.bunori.api.core.crawler.Crawler
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.api.core.network.interceptor.CloudflareBypassException
import com.halovoid.bunori.api.core.scrapper.Scrapper
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
import java.io.File

import com.halovoid.bunori.ui.core.logging.AppLog
import com.halovoid.bunori.wasm.WamrHttpBridge

class ChapterHandler(
    private val scrapper: Scrapper,
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

        val crawlerName = metadata.crawlerName
            ?: chapter.scanlationSource.takeIf { it.isNotBlank() && it != "NotProvided" && it != "Not Provided" }
            ?: return JobResult.Failure(Exception("No Crawler Found"))

        val crawler = crawlerFactory.getCrawler(crawlerName)
            ?: crawlerFactory.getCrawler(chapter.scanlationSource)
            ?: return JobResult.Failure(Exception("No Crawler Found for name: $crawlerName"))

        val novel = novelRepository.getNovelDetails(chapter.novelUrl)
        val novelTitle = novel?.title ?: "Novel"

        // Cache Promotion: Check if valid cached version is already available locally
        val existingDownload = downloadRepository.getDownload(chapter.novelUrl, chapter.url)
        if (existingDownload != null) {
            if (!existingDownload.isCache) {
                return JobResult.Success
            }
            val now = System.currentTimeMillis()
            val isExpired = existingDownload.expirationTime?.let { it < now } ?: false
            if (!isExpired) {
                val cachedContent = try {
                    val fileLoc = existingDownload.fileLocation
                    val uri = if (fileLoc.startsWith("content://") || fileLoc.startsWith("file://") || fileLoc.startsWith("http")) {
                        Uri.parse(fileLoc)
                    } else {
                        Uri.fromFile(File(fileLoc))
                    }
                    storageRepository.readText(uri)
                } catch (_: Exception) {
                    null
                }

                if (!cachedContent.isNullOrBlank() && cachedContent.trim().length > 50) {
                    val novelKey = crawler.getNovelKey(chapter.novelUrl)
                    val fileName = "${chapter.index.toString().padStart(4, '0')}_${chapter.id}.html.gz"
                    val relativePath = "novels/$novelKey/chapters"

                    val permanentUri = storageRepository.saveCompressedText(
                        relativePath = relativePath,
                        fileName = fileName,
                        content = cachedContent
                    )

                    try {
                        if (!existingDownload.fileLocation.startsWith("content://")) {
                            File(existingDownload.fileLocation.removePrefix("file://")).delete()
                        }
                    } catch (_: Exception) {}

                    downloadRepository.saveDownload(
                        existingDownload.copy(
                            fileLocation = permanentUri.toString(),
                            isCache = false,
                            expirationTime = null,
                            downloadedAt = System.currentTimeMillis()
                        )
                    )

                    return JobResult.Success
                }
            }
        }

        val targetUrl = task.url?.takeIf { it.isNotBlank() }
            ?: chapter.sourceUrl?.takeIf { it.isNotBlank() }
            ?: chapter.url.takeIf { it.isNotBlank() }
            ?: return JobResult.Failure(Exception("No valid URL found for chapter ${chapter.title}"))

        // 1. Load the Chapter and Save it
        return try {
            val fileLocation = loadAndSaveFile(targetUrl, crawler, chapter)

            // 2. Save into the Download table
            downloadRepository.saveDownload(
                Download(
                    novelUrl = chapter.novelUrl,
                    chapterUrl = chapter.url,
                    fileLocation = fileLocation.toString(),
                    chapterIndex = chapter.index,
                    chapterTitle = chapter.title,
                    scanlationSource = chapter.scanlationSource,
                    novelTitle = novelTitle
                )
            )

            JobResult.Success
        } catch (e: Exception) {
            AppLog.e("ChapterHandler", "Failed to download chapter ${chapter.title} ($targetUrl)", e)
            val isCloudflare = crawler.webviewNeeded == true || 
                WamrHttpBridge.consumeCloudflareBlocked() ||
                e is CloudflareBypassException ||
                e.cause is CloudflareBypassException ||
                e.message?.contains("Cloudflare", ignoreCase = true) == true

            if (isCloudflare) {
                AppLog.w("ChapterHandler", "Detected Cloudflare block for chapter ${chapter.title} ($targetUrl). Setting JobResult.Blocked.")
                JobResult.Blocked
            } else {
                JobResult.Failure(e)
            }
        }
    }

    suspend fun loadAndSaveFile(url: String, crawler: Crawler, chapter: Chapter): Uri {
        if (url.startsWith("content://")) {
            return Uri.parse(url)
        }

        val chapterContent = crawler.getChapterContent(url)
        if (chapterContent.isNullOrBlank()) {
            throw IllegalStateException("Empty content returned from source for chapter ${chapter.title}")
        }
        if (chapterContent.trim().length <= 50) {
            throw IllegalStateException("Content too short (${chapterContent.trim().length} chars) for chapter ${chapter.title}")
        }

        val novelKey = crawler.getNovelKey(chapter.novelUrl)
        val fileName = "${chapter.index.toString().padStart(4, '0')}_${chapter.id}.html.gz"
        val relativePath = "novels/$novelKey/chapters"

        return storageRepository.saveCompressedText(
            relativePath = relativePath,
            fileName = fileName,
            content = chapterContent
        )
    }
}