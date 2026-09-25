package com.halovoid.bunori.data.handlers

import com.halovoid.bunori.data.artifact.ArtifactGeneratorFactory
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.handlers.utility.parsedMetadata
import com.halovoid.bunori.data.repository.ArtifactRepository
import com.halovoid.bunori.data.repository.ChapterRepository
import com.halovoid.bunori.data.repository.DownloadRepository
import com.halovoid.bunori.data.repository.NovelRepository
import com.halovoid.bunori.data.repository.StorageRepository
import com.halovoid.bunori.data.scheduler.jobs.JobHandler
import com.halovoid.bunori.data.scheduler.jobs.JobResult
import com.halovoid.bunori.domain.models.Artifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import com.halovoid.bunori.domain.models.Chapter
import org.json.JSONObject

class ArtifactHandler(
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository,
    private val crawlerFactory: CrawlerFactory,
    private val storageRepository: StorageRepository,
    private val generatorFactory: ArtifactGeneratorFactory,
    private val artifactRepository: ArtifactRepository,
    private val downloadRepository: DownloadRepository
) : JobHandler {
    override suspend fun handle(task: TaskEntity): JobResult = withContext(Dispatchers.IO) {
        val metadata = task.parsedMetadata
        val format = metadata.format ?: return@withContext JobResult.Failure(Exception("No Format Provided"))

        try {
            // 1. Fetch All Necessary data
            val novel = novelRepository.getNovelByUrl(task.novelUrl)
                ?: return@withContext JobResult.Failure(Exception("Novel not found in database"))

            val crawlerName = metadata.crawlerName?.ifBlank { null }
                ?: novel.crawlerName.takeIf { it.isNotBlank() }
                ?: "local"

            val startIndex = metadata.startIndex ?: 1
            val endIndex = metadata.endIndex ?: Int.MAX_VALUE

            val json = try { JSONObject(task.metadata ?: "{}") } catch (_: Exception) { JSONObject() }
            val selectedSources = json.optJSONArray("selectedSources")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            }

            val allChapters = chapterRepository.getChaptersByNovelUrl(task.novelUrl).ifEmpty {
                val downloads = downloadRepository.getDownloadsForNovel(task.novelUrl)
                downloads.mapIndexed { idx, dl ->
                    Chapter(
                        id = if (dl.id > 0) dl.id.toInt() else (idx + 1),
                        url = dl.chapterUrl,
                        title = dl.chapterTitle.ifBlank { "Chapter ${dl.chapterIndex}" },
                        index = dl.chapterIndex,
                        novelUrl = dl.novelUrl,
                        isDownloaded = true,
                        read = false,
                        scanlationSource = dl.scanlationSource
                    )
                }
            }

            // Filter by range
            val rangeChapters = allChapters.filter { it.index in startIndex..endIndex }

            // Filter by selected sources
            val sourceFiltered = if (!selectedSources.isNullOrEmpty()) {
                rangeChapters.filter { chapter ->
                    val src = chapter.scanlationSource
                    val effective = if (src.isBlank() || src == "NotProvided" || src == "Not Provided") crawlerName else src
                    selectedSources.contains(src) || selectedSources.contains(effective)
                }
            } else {
                rangeChapters
            }

            // Filter ONLY those chapters that are actually downloaded!
            val downloadedUrls = downloadRepository.getDownloadedChapterUrls(task.novelUrl).toSet()
            val downloadedChapters = sourceFiltered.filter { downloadedUrls.contains(it.url) }

            if (downloadedChapters.isEmpty()) {
                return@withContext JobResult.Failure(Exception("No downloaded chapters found to export for the selected sources"))
            }

            // 2. Select generator and create temp file
            val generator = generatorFactory.getGenerator(format)
            val tempFile = generator.generate(novel, downloadedChapters, metadata)
            val crawler = crawlerFactory.getCrawler(crawlerName)

            // 3. Cleanup existing artifacts for this batch to prevent duplicates
            val existingArtifacts = artifactRepository.getArtifactForBatch(task.batchId)
            existingArtifacts.forEach { existing ->
                try {
                    storageRepository.delete(existing.artifactDestination)
                } catch (_: Exception) { }
                artifactRepository.removeArtifact(existing)
            }

            // 4. Save Permanently to the user's selected storage
            val novelKey = crawler?.getNovelKey(novel.title) ?: run {
                val slug = novel.title.trimEnd('/').split('/').last()
                "${crawlerName.lowercase()}_$slug".filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            }.ifBlank { "novel_${System.currentTimeMillis()}" }

            val fileName = "${novelKey}_${System.currentTimeMillis()}.$format"
            val relativeDir = "artifacts/$novelKey"
            val relativePath = "$relativeDir/$fileName"
            val mimeType = if (format.equals("pdf", ignoreCase = true)) "application/pdf" else "application/epub+zip"
            storageRepository.saveFile(
                relativePath = relativeDir,
                fileName = fileName,
                mimeType = mimeType,
                sourceFile = tempFile
            )

            // 5. Insert New Artifact to Database
            val artifact = Artifact(
                id = 0,
                novelUrl = novel.url,
                requestId = task.batchId,
                artifactDestination = relativePath,
                artifactName = fileName
            )
            artifactRepository.insertArtifact(artifact)

            // 6. Cleanup Temp File
            tempFile.delete()

            JobResult.Success
        } catch (e: Exception) {
            JobResult.Failure(e)
        }
    }
}