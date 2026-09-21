package com.halovoid.bunori.domain.usecase

import android.content.Context
import com.halovoid.bunori.data.factory.JobFactory
import com.halovoid.bunori.data.repository.BatchRepository
import com.halovoid.bunori.data.repository.ChapterRepository
import com.halovoid.bunori.data.scheduler.services.SchedulerService
import com.halovoid.bunori.domain.models.Novel

/**
 * Single business action for queueing a range of chapters for download.
 */
class DownloadRangeUseCase(
    private val chapterRepository: ChapterRepository,
    private val batchRepository: BatchRepository,
    private val jobFactory: JobFactory = JobFactory()
) {
    suspend operator fun invoke(context: Context, novel: Novel, startIndex: Int, endIndex: Int) {
        val allChapters = chapterRepository.getChaptersByNovelUrl(novel.url)
        val rangeChapters = allChapters.filter { it.index in startIndex..endIndex }
        if (rangeChapters.isEmpty()) return

        val batch = jobFactory.createRangeDownloadBatch(novel, startIndex, endIndex)
        val tasks = jobFactory.createChapterTasks(batch.id, novel.crawlerName, rangeChapters, batch.priority)

        batchRepository.insertBatch(batch)
        batchRepository.insertTasks(tasks)
        SchedulerService.startService(context)
    }
}
