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
import com.halovoid.bunori.data.db.dao.TaskDao
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.ui.core.logging.AppLog
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
        val overallStartTime = System.currentTimeMillis()
        val metadata = task.parsedMetadata
        val format = metadata.format ?: return@withContext JobResult.Failure(Exception("No Format Provided"))

        AppLog.i(TAG, "Starting export task #${task.id} for novel: ${task.novelUrl}, format=$format")

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

            val downloadedChapters = resolveExportChapters(task, novel, crawlerName, startIndex, endIndex, selectedSources)
            if (downloadedChapters.isEmpty()) {
                AppLog.w(TAG, "No downloaded chapters found to export for novel: ${task.novelUrl}")
                return@withContext JobResult.Failure(Exception("No downloaded chapters found to export for the selected sources"))
            }

            // 2. Select generator and create temp file with live progress updates
            val generator = generatorFactory.getGenerator(format)
            var lastProgressTime = 0L
            val genStartTime = System.currentTimeMillis()
            AppLog.i(TAG, "Starting $format generation for ${downloadedChapters.size} chapters...")
            val tempFile = generator.generate(novel, downloadedChapters, metadata) { current, total, stage ->
                val now = System.currentTimeMillis()
                if (current == 1 || current == total || now - lastProgressTime >= 250) {
                    lastProgressTime = now
                    try {
                        taskDao?.updateTaskName(task.id, "Exporting ($current/$total): $stage")
                    } catch (_: Exception) { }
                }
            }
            AppLog.i(
                TAG,
                "$format generation finished in ${System.currentTimeMillis() - genStartTime}ms. Temp file size: ${tempFile.length() / 1024} KB"
            )
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

            val saveStartTime = System.currentTimeMillis()
            AppLog.i(TAG, "Saving artifact to storage destination: $relativePath")
            storageRepository.saveFile(
                relativePath = relativeDir,
                fileName = fileName,
                mimeType = mimeType,
                sourceFile = tempFile
            )
            AppLog.i(TAG, "Artifact saved to storage in ${System.currentTimeMillis() - saveStartTime}ms")

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

            try {
                taskDao?.updateTaskName(task.id, "Export completed: ${downloadedChapters.size} chapters ($format)")
            } catch (_: Exception) { }

            val totalDuration = System.currentTimeMillis() - overallStartTime
            AppLog.i(TAG, "Export task #${task.id} completed successfully in ${totalDuration}ms for novel '${novel.title}'")

            JobResult.Success
        } catch (e: Exception) {
            AppLog.e(TAG, "Export task #${task.id} failed after ${System.currentTimeMillis() - overallStartTime}ms: ${e.message}", e)
            JobResult.Failure(e)
        }
    }

    private suspend fun resolveExportChapters(
        task: TaskEntity,
        novel: Novel,
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

        AppLog.i(
            TAG,
            "Chapters resolved: total=${allChapters.size}, range=$startIndex..$endIndex (${rangeChapters.size}), " +
            "sources=${selectedSources ?: "all"} (${sourceFiltered.size}), downloaded=${downloadedChapters.size}"
        )

        return downloadedChapters
    }
}