package com.halovoid.bunori.domain.usecase

import android.net.Uri
import com.halovoid.bunori.data.factory.JobFactory
import com.halovoid.bunori.data.repository.DownloadRepository
import com.halovoid.bunori.data.repository.BatchRepository
import com.halovoid.bunori.data.repository.StorageRepository
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.ui.core.logging.AppLog

/**
 * Single business action for deleting an existing chapter download and re-queuing a fetch request.
 */
class ReplayChapterUseCase(
    private val downloadRepository: DownloadRepository,
    private val storageRepository: StorageRepository,
    private val batchRepository: BatchRepository,
    private val jobFactory: JobFactory = JobFactory()
) {
    suspend operator fun invoke(novel: Novel, chapter: Chapter) {
        val download = downloadRepository.getDownload(chapter.novelUrl, chapter.url)
        if (download != null) {
            try {
                storageRepository.delete(Uri.parse(download.fileLocation))
            } catch (e: Exception) {
                AppLog.w("ReplayChapterUseCase", "Failed to delete chapter file at ${download.fileLocation} on replay", e)
            }
            downloadRepository.deleteDownload(chapter.novelUrl, chapter.url)
        }
        val request = jobFactory.chapter(novel, chapter)
        batchRepository.insertBatchWithChapterTasks(request, listOf(chapter))
    }
}
