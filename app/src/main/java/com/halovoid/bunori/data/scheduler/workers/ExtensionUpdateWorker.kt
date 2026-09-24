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
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.scheduler.notification.NotificationChannels
import com.halovoid.bunori.extension.manager.ExtensionManager
import com.halovoid.bunori.lnreader.LnReaderRuntime
import com.halovoid.bunori.utils.Logger
import kotlinx.coroutines.flow.first

class ExtensionUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val NOTIFICATION_ID = 5001
    }

    override suspend fun doWork(): Result {
        return try {
            val extensionManager = ExtensionManager.getInstance(applicationContext)
            val preferenceRepository = PreferenceRepository.getInstance(applicationContext)

            val repoUrl = preferenceRepository.extensionRepoUrl.first()
            if (repoUrl.isBlank()) {
                return Result.success()
            }

            extensionManager.loadInstalledExtensions()

            LnReaderRuntime.updateIfAvailable(applicationContext)

            val updates = extensionManager.checkForUpdates(repoUrl)

            if (updates.isNotEmpty()) {
                Logger.d("[ExtensionUpdateWorker] Found ${updates.size} extension updates: ${updates.map { it.name }}")
                sendUpdateNotification(updates.map { it.name })
            } else {
                Logger.d("[ExtensionUpdateWorker] Extension index refreshed, no new updates found.")
            }

            Result.success()
        } catch (e: Exception) {
            Logger.e("[ExtensionUpdateWorker] Error checking for extension updates: ${e.message}", e)
            Result.retry()
        }
    }

    private fun sendUpdateNotification(updatedExtensionNames: List<String>) {
        NotificationChannels.createChannels(applicationContext)
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Extension Updates Available"
        val count = updatedExtensionNames.size
        val namesSummary = updatedExtensionNames.joinToString(", ")
        val contentText = if (count == 1) {
            "Update available for $namesSummary"
        } else {
            "$count updates available: $namesSummary"
        }

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_COMPLETE)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
