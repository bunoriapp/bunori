package com.halovoid.bunori.data.scheduler.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.util.Calendar
import java.util.concurrent.TimeUnit

object BackupScheduler {
    fun scheduleBackupWork(context: Context, frequency: String) {
        BackgroundMaintenanceScheduler.scheduleBackupWork(context, frequency)
    }
}

