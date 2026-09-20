package com.halovoid.bunori.data.scheduler.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.halovoid.bunori.data.repository.NovelRepository
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.utils.Logger

class NovelPruningWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val novelRepository = NovelRepository.getInstance(applicationContext)
            val preferenceRepository = PreferenceRepository.getInstance(applicationContext)

            val prunableNovels = novelRepository.getPrunableNovels()
            if (prunableNovels.isNotEmpty()) {
                val urls = prunableNovels.map { it.url }
                novelRepository.deleteNovelsByUrl(urls)
                Logger.d("[NovelPruningWorker] Successfully pruned ${prunableNovels.size} unreferenced non-library novels.")
            } else {
                Logger.d("[NovelPruningWorker] No prunable novels found.")
            }

            preferenceRepository.setLastNovelPruneTime(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            Logger.e("[NovelPruningWorker] Error while pruning novels: ${e.message}", e)
            Result.retry()
        }
    }
}
