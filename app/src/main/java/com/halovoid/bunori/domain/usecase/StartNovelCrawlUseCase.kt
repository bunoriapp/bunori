package com.halovoid.bunori.domain.usecase

import android.content.Context
import com.halovoid.bunori.data.factory.JobFactory
import com.halovoid.bunori.data.repository.BatchRepository
import com.halovoid.bunori.data.scheduler.services.SchedulerService

/**
 * Single business action for queuing a novel metadata request.
 */
class StartNovelCrawlUseCase(
    private val batchRepository: BatchRepository,
    private val jobFactory: JobFactory = JobFactory()
) {
    suspend operator fun invoke(context: Context, crawlerName: String, url: String, title: String) {
        val batch = jobFactory.createMetadataBatchFromUrl(crawlerName, url, title)
        val task = jobFactory.createMetadataTaskFromUrl(batch.id, crawlerName, url, title)
        batchRepository.insertBatch(batch)
        batchRepository.insertTask(task)
        SchedulerService.startService(context)
    }
}
