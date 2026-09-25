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
import com.halovoid.bunori.data.db.dao.TaskDao
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.utils.Logger
import org.json.JSONObject

class ArtifactHandler(
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository,
    private val crawlerFactory: CrawlerFactory,
    private val storageRepository: StorageRepository,
    private val generatorFactory: ArtifactGeneratorFactory,
    private val artifactRepository: ArtifactRepository,
    private val downloadRepository: DownloadRepository,
    private val taskDao: TaskDao? = null
) : JobHandler {

    private companion object {
        const val TAG = "ArtifactExport"
    }

    override suspend fun handle(task: TaskEntity): JobResult = withContext(Dispatchers.IO) {
        val metadata = task.parsedMetadata
        val format = metadata.format ?: return@withContext JobResult.Failure(Exception("No Format Provided"))

        Logger.i("$TAG : Starting export task #${task.id} for novel: ${task.novelUrl}, format=$format")

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

            val downloadedChapters = resolveExportChapters(task, crawlerName, startIndex, endIndex, selectedSources)
            if (downloadedChapters.isEmpty()) {
                return@withContext JobResult.Failure(Exception("No downloaded chapters found to export for the selected sources"))
            }

            val generator = generatorFactory.getGenerator(format)
            var lastProgressTime = 0L
            val tempFile = generator.generate(novel, downloadedChapters, metadata) { current, total, stage ->
                val now = System.currentTimeMillis()
                if (current == 1 || current == total || now - lastProgressTime >= 250) {
                    lastProgressTime = now
                    try {
                        taskDao?.updateTaskName(task.id, "Exporting ($current/$total): $stage")
                    } catch (_: Exception) { }
                }
            }
            val crawler = crawlerFactory.getCrawler(crawlerName)

            val existingArtifacts = artifactRepository.getArtifactForBatch(task.batchId)
            existingArtifacts.forEach { existing ->
                try {
                    storageRepository.delete(existing.artifactDestination)
                } catch (_: Exception) { }
                artifactRepository.removeArtifact(existing)
            }

            try {
                taskDao?.updateTaskName(task.id, "Saving $format to storage...")
            } catch (_: Exception) { }

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

            val artifact = Artifact(
                id = 0,
                novelUrl = novel.url,
                requestId = task.batchId,
                artifactDestination = relativePath,
                artifactName = fileName
            )
            artifactRepository.insertArtifact(artifact)

            tempFile.delete()

            try {
                taskDao?.updateTaskName(task.id, "Export completed: ${downloadedChapters.size} chapters ($format)")
            } catch (_: Exception) { }

            JobResult.Success
        } catch (e: Exception) {
            JobResult.Failure(e)
        }
    }

    private suspend fun resolveExportChapters(
        task: TaskEntity,
        crawlerName: String,
        startIndex: Int,
        endIndex: Int,
        selectedSources: Set<String>?
    ): List<Chapter> {
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

        val rangeChapters = allChapters.filter { it.index in startIndex..endIndex }

        val sourceFiltered = if (!selectedSources.isNullOrEmpty()) {
            rangeChapters.filter { chapter ->
                val src = chapter.scanlationSource
                val effective = if (src.isBlank() || src == "NotProvided" || src == "Not Provided") crawlerName else src
                selectedSources.contains(src) || selectedSources.contains(effective)
            }
        } else {
            rangeChapters
        }

        val downloadedUrls = downloadRepository.getDownloadedChapterUrls(task.novelUrl).toSet()
        val downloadedChapters = sourceFiltered.filter { downloadedUrls.contains(it.url) }

        return downloadedChapters
    }
}