package com.halovoid.bunori.data.scheduler.workers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.halovoid.bunori.MainActivity
import com.halovoid.bunori.R
import com.halovoid.bunori.api.backup.BackupService
import com.halovoid.bunori.data.scheduler.notification.NotificationChannels
import com.halovoid.bunori.utils.Logger

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val NOTIFICATION_ID = 5002
    }

    override suspend fun doWork(): Result {
        NotificationChannels.createChannels(applicationContext)
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Show starting notification
        showOngoingNotification(notificationManager)

        return try {
            val backupService = BackupService(applicationContext)
            backupService.createBackup(
                backupDatabase = true,
                backupChapters = true,
                backupCovers = true
            )

            // Show completion notification
            showSuccessNotification(notificationManager)
            Logger.d("[BackupWorker] Automatic backup completed successfully")
            Result.success()
        } catch (e: Exception) {
            Logger.e("[BackupWorker] Automatic backup failed: ${e.message}", e)
            showFailureNotification(notificationManager, e.message ?: "Unknown error")
            Result.retry()
        }
    }

    private fun showOngoingNotification(notificationManager: NotificationManager) {
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_PROGRESS)
            .setContentTitle("Automatic Backup")
            .setContentText("Creating backup of library and downloaded content...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showSuccessNotification(notificationManager: NotificationManager) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_COMPLETE)
            .setContentTitle("Backup Completed")
            .setContentText("Your library and downloaded content have been backed up successfully.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showFailureNotification(notificationManager: NotificationManager, errorMsg: String) {
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_COMPLETE)
            .setContentTitle("Backup Failed")
            .setContentText("Automatic backup could not be created: $errorMsg")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
