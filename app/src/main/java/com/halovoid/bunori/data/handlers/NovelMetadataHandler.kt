package com.halovoid.bunori.data.handlers

import android.net.Uri
import com.halovoid.bunori.api.core.crawler.Crawler
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.api.core.network.interceptor.CloudflareBypassException
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.handlers.utility.parsedMetadata
import com.halovoid.bunori.data.repository.ChapterRepository
import com.halovoid.bunori.data.repository.NovelRepository
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.StorageRepository
import com.halovoid.bunori.data.scheduler.jobs.JobHandler
import com.halovoid.bunori.data.scheduler.jobs.JobResult
import com.halovoid.bunori.wasm.WamrHttpBridge
import kotlinx.coroutines.flow.first

/**
 * Handler for [JobType.NOVEL_METADATA] requests.
 * Responsible for refreshing novel metadata and chapter lists from the source.
 */
class NovelMetadataHandler(
    private val crawlerFactory: CrawlerFactory,
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository,
    private val storageRepository: StorageRepository,
    private val preferenceRepository: PreferenceRepository
) : JobHandler {

    override suspend fun handle(task: TaskEntity): JobResult {
        val metadata = task.parsedMetadata
        val crawlerName = metadata.crawlerName
            ?: return JobResult.Failure(Exception("No Crawler Provided"))

        val crawler = crawlerFactory.getCrawler(crawlerName)
            ?: return JobResult.Failure(Exception("No Crawler Found for name: $crawlerName"))

        return try {
            android.util.Log.i("NovelMetadataHandler", "Fetching details for ${task.novelUrl} using crawler $crawlerName")
            // Fetch latest details from the source
            val novel = crawler.getNovelDetails(task.novelUrl)
            android.util.Log.i("NovelMetadataHandler", "Fetched details for ${task.novelUrl}: title='${novel.title}', chapters=${novel.chapters.size}")

            // Refresh cover image if available and not ignored
            val shouldIgnoreImages = preferenceRepository.ignoreImages.first()
            val coverRelPath = if (!shouldIgnoreImages) {
                downloadAndSaveCover(novel.coverUrl, crawler, novel.url)
            } else {
                null
            }

            // Prepare the updated novel domain model (formats titles)
            val updatedNovel = novel.let {
                val coverLocalUrl = coverRelPath ?: if (shouldIgnoreImages) null else it.coverUrl
                it.copy(
                    coverUrl = coverLocalUrl,
                    coverHttpsUrl = novel.coverUrl
                )
            }

            // Fetch existing chapters to preserve local state (like downloaded fileLocation)
            val existingChapters = chapterRepository.getChaptersByNovelUrl(task.novelUrl)
            val existingChapterMap = existingChapters.associateBy { it.url }

            val mergedChapters = updatedNovel.chapters.map { chapter ->
                val existing = existingChapterMap[chapter.url]
                val effectiveScanlation = chapter.scanlationSource.takeIf {
                    it.isNotBlank() && it != "NotProvided" && it != "Not Provided"
                } ?: existing?.scanlationSource?.takeIf {
                    it.isNotBlank() && it != "NotProvided" && it != "Not Provided"
                } ?: updatedNovel.crawlerName

                if (existing != null) {
                    chapter.copy(
                        id = existing.id,
                        novelUrl = task.novelUrl
                    ).apply {
                        sourceUrl = existing.sourceUrl ?: chapter.url
                        scanlationSource = effectiveScanlation
                        read = existing.read
                    }
                } else {
                    chapter.copy(
                        id = 0,
                        novelUrl = task.novelUrl
                    ).apply {
                        sourceUrl = sourceUrl ?: url
                        scanlationSource = effectiveScanlation
                    }
                }
            }

            // 5. Persist the updated data to the database
            val existingNovel = novelRepository.getNovelByUrl(task.novelUrl)
            val novelToSave = updatedNovel.copy(
                url = task.novelUrl,
                chapters = mergedChapters,
                inLibrary = existingNovel?.inLibrary ?: false,
                titleHash = existingNovel?.titleHash,
                crawlerName = existingNovel?.crawlerName ?: crawlerName,
                refreshExpiry = System.currentTimeMillis() + 2 * 24 * 60 * 60 * 1000L
            )
            novelRepository.saveNovel(novelToSave)
            if (novelToSave.chapters.isNotEmpty()) {
                chapterRepository.upsertChapters(novelToSave.chapters)
            }

            // Metadata for totalProgressUpdate is not changed in this request
            // Currently user would need to manually do a full novel fetch
            JobResult.Success
        } catch (e: Exception) {
            android.util.Log.e("NovelMetadataHandler", "Failed to handle novel metadata for ${task.novelUrl}: ${e.message}", e)
            val isCloudflare = crawler.webviewNeeded == true || 
                WamrHttpBridge.consumeCloudflareBlocked() ||
                com.halovoid.bunori.lnreader.LnReaderHttpBridge.consumeCloudflareBlocked() ||
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

    private suspend fun downloadAndSaveCover(url: String?, crawler: Crawler, novelUrl: String): String? {
        if (url.isNullOrBlank()) return null

        return try {
            val bytes = crawler.downloadCover(url) ?: return null
            val extension = if (url.contains(".png", ignoreCase = true)) "png" else "jpg"
            val novelKey = crawler.getNovelKey(novelUrl)
            val fileName = "cover.$extension"
            val relativeDir = "novels/$novelKey/covers"

            storageRepository.saveFile(
                relativePath = relativeDir,
                fileName = fileName,
                mimeType = "image/$extension",
                data = bytes
            )
            "$relativeDir/$fileName"
        } catch (e: Exception) {
            null
        }
    }
}
