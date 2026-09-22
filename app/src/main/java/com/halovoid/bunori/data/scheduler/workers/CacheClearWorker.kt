package com.halovoid.bunori.data.scheduler.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.halovoid.bunori.data.repository.DownloadRepository
import com.halovoid.bunori.data.repository.DownloadRepositoryImpl
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.utils.Logger
import java.io.File

class CacheClearWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val downloadRepository: DownloadRepository = DownloadRepositoryImpl.getInstance(applicationContext)
            val preferenceRepository = PreferenceRepository.getInstance(applicationContext)

            val cachedDownloads = downloadRepository.getAllCachedDownloads()
            var deletedFilesCount = 0

            // Delete individual cached download files
            cachedDownloads.forEach { download ->
                val location = download.fileLocation
                if (location.isNotBlank()) {
                    val file = File(location)
                    if (file.exists() && file.delete()) {
                        deletedFilesCount++
                    }
                }
            }

            // Also clean up any lingering files inside the chapter_cache directory
            val cacheDir = File(applicationContext.cacheDir, "chapter_cache")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.delete()) {
                        deletedFilesCount++
                    }
                }
            }

            // Remove cache rows from downloads table
            downloadRepository.deleteAllCachedDownloads()

            preferenceRepository.setLastCacheClearTime(System.currentTimeMillis())
            Logger.d("[CacheClearWorker] Cleared ${cachedDownloads.size} cached download records and $deletedFilesCount cache files.")

            Result.success()
        } catch (e: Exception) {
            Logger.e("[CacheClearWorker] Error while clearing download cache: ${e.message}", e)
            Result.retry()
        }
    }
}
