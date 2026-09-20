package com.halovoid.bunori.data.scheduler.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.utils.Logger
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.util.Calendar
import java.util.concurrent.TimeUnit

object BackgroundMaintenanceScheduler {
    private const val TAG = "BgMaintenanceScheduler"
    private const val BACKUP_WORK_NAME = "bunori_backup_work"
    private const val NOVEL_PRUNE_WORK_NAME = "bunori_novel_prune_work"
    private const val CACHE_CLEAR_WORK_NAME = "bunori_cache_clear_work"
    private const val EXTENSION_UPDATE_WORK_NAME = "bunori_extension_update_work"

    suspend fun syncAll(context: Context) {
        val prefRepo = PreferenceRepository.getInstance(context)
        val backupFreq = prefRepo.backupFrequency.first()
        val pruneFreq = prefRepo.novelPruneFrequency.first()
        val cacheFreq = prefRepo.cacheClearFrequency.first()

        Logger.d("[$TAG] Syncing background maintenance workers: Backup=$backupFreq, NovelPrune=$pruneFreq, CacheClear=$cacheFreq")

        scheduleBackupWork(context, backupFreq)
        scheduleNovelPruningWork(context, pruneFreq)
        scheduleCacheClearWork(context, cacheFreq)
        scheduleExtensionUpdateWork(context)
    }

    fun scheduleExtensionUpdateWork(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<ExtensionUpdateWorker>(Duration.ofHours(12))
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            EXTENSION_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )
        Logger.d("[$TAG] Scheduled background extension update worker (every 12 hours)")
    }

    fun scheduleBackupWork(context: Context, frequency: String) {
        val workManager = WorkManager.getInstance(context)
        if (frequency.equals("Off", ignoreCase = true)) {
            workManager.cancelUniqueWork(BACKUP_WORK_NAME)
            Logger.d("[$TAG] Cancelled backup periodic work")
            return
        }

        val repeatInterval = when (frequency) {
            "Daily" -> Duration.ofDays(1)
            "Weekly" -> Duration.ofDays(7)
            "Monthly" -> Duration.ofDays(30)
            else -> {
                workManager.cancelUniqueWork(BACKUP_WORK_NAME)
                return
            }
        }

        val initialDelay = calculateInitialDelayTo530PM()
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<BackupWorker>(repeatInterval)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            BACKUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )
        Logger.d("[$TAG] Scheduled backup periodic work ($frequency)")
    }

    fun scheduleNovelPruningWork(context: Context, frequency: String) {
        val workManager = WorkManager.getInstance(context)
        if (frequency.equals("Off", ignoreCase = true)) {
            workManager.cancelUniqueWork(NOVEL_PRUNE_WORK_NAME)
            Logger.d("[$TAG] Cancelled novel pruning periodic work")
            return
        }

        val repeatInterval = when {
            frequency.contains("Daily", ignoreCase = true) || frequency.startsWith("1 ") -> Duration.ofDays(1)
            frequency.contains("3", ignoreCase = true) -> Duration.ofDays(3)
            frequency.contains("7", ignoreCase = true) || frequency.contains("Weekly", ignoreCase = true) -> Duration.ofDays(7)
            frequency.contains("10", ignoreCase = true) -> Duration.ofDays(10)
            frequency.contains("30", ignoreCase = true) || frequency.contains("Monthly", ignoreCase = true) -> Duration.ofDays(30)
            else -> Duration.ofDays(10)
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<NovelPruningWorker>(repeatInterval)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            NOVEL_PRUNE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )
        Logger.d("[$TAG] Scheduled novel pruning periodic work ($frequency, repeat: $repeatInterval)")
    }

    fun scheduleCacheClearWork(context: Context, frequency: String) {
        val workManager = WorkManager.getInstance(context)
        if (frequency.equals("Off", ignoreCase = true)) {
            workManager.cancelUniqueWork(CACHE_CLEAR_WORK_NAME)
            Logger.d("[$TAG] Cancelled cache clear periodic work")
            return
        }

        val repeatInterval = when {
            frequency.contains("Daily", ignoreCase = true) || frequency.startsWith("1 ") -> Duration.ofDays(1)
            frequency.contains("2", ignoreCase = true) -> Duration.ofDays(2)
            frequency.contains("4", ignoreCase = true) -> Duration.ofDays(4)
            frequency.contains("7", ignoreCase = true) || frequency.contains("Weekly", ignoreCase = true) -> Duration.ofDays(7)
            frequency.contains("14", ignoreCase = true) -> Duration.ofDays(14)
            else -> Duration.ofDays(4)
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<CacheClearWorker>(repeatInterval)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            CACHE_CLEAR_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )
        Logger.d("[$TAG] Scheduled cache clear periodic work ($frequency, repeat: $repeatInterval)")
    }

    fun runNovelPruningNow(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val oneTimeWork = OneTimeWorkRequestBuilder<NovelPruningWorker>()
            .build()
        workManager.enqueueUniqueWork("bunori_novel_prune_now", ExistingWorkPolicy.REPLACE, oneTimeWork)
        Logger.d("[$TAG] Triggered one-time novel pruning")
    }

    fun runCacheClearNow(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val oneTimeWork = OneTimeWorkRequestBuilder<CacheClearWorker>()
            .build()
        workManager.enqueueUniqueWork("bunori_cache_clear_now", ExistingWorkPolicy.REPLACE, oneTimeWork)
        Logger.d("[$TAG] Triggered one-time cache clear")
    }

    private fun calculateInitialDelayTo530PM(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 17)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (now.after(target)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
