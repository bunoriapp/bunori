package com.halovoid.bunori.data.scheduler.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.halovoid.bunori.MainActivity
import com.halovoid.bunori.R
import com.halovoid.bunori.data.db.dao.BatchWithStats
import com.halovoid.bunori.data.db.entities.BatchEntity
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.handlers.utility.crawlerName
import com.halovoid.bunori.data.scheduler.services.SchedulerService
import com.halovoid.bunori.ui.feature.crawler.webview.WebViewActivity
import com.halovoid.bunori.ui.navigation.AppNavigationManager
import com.halovoid.bunori.ui.navigation.Screen

class DownloadNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID_BASE = 2000
        private const val COMPLETE_NOTIFICATION_ID_BASE = 3000
        private const val FAILED_NOTIFICATION_ID_BASE = 4000
    }

    init {
        NotificationChannels.createChannels(context)
    }

    fun createInitialForegroundNotification(): Notification {
        val openDownloadsPendingIntent = createOpenRoutePendingIntent(
            context = context,
            route = Screen.Activity.route,
            requestCode = 100
        )

        return NotificationCompat.Builder(context, NotificationChannels.CHANNEL_PROGRESS)
            .setContentTitle("Bunori Task Scheduler")
            .setContentText("Initializing background tasks...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentIntent(openDownloadsPendingIntent)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    fun createProgressNotification(
        primaryBatch: BatchWithStats,
        runningTasks: List<TaskEntity>,
        activeBatchesCount: Int
    ): Notification {
        val batch = primaryBatch.batch
        val total = primaryBatch.totalTasks
        val completed = primaryBatch.completedTasks
        val isIndeterminate = total <= 0
        val percent = if (total > 0) (completed * 100) / total else 0

        val typeTitle = when (batch.type) {
            JobType.RANGE_DOWNLOAD -> "Downloading"
            JobType.ARTIFACT -> "Exporting"
            JobType.NOVEL_METADATA -> "Refreshing"
            JobType.BACKUP -> "Backup"
            JobType.CHAPTER -> "Fetching"
        }

        val title = "$typeTitle: ${batch.name}"

        val activeTaskName = runningTasks.firstOrNull { it.batchId == batch.id }?.name
        val contentText = when {
            activeTaskName != null -> activeTaskName
            total > 0 -> "$completed of $total items finished ($percent%)"
            else -> "Processing..."
        }

        val subText = if (activeBatchesCount > 1) {
            "$percent% • +${activeBatchesCount - 1} more"
        } else {
            "$percent%"
        }

        val openDetailPendingIntent = createOpenRoutePendingIntent(
            context = context,
            route = Screen.JobDetail.createRoute(batch.id),
            requestCode = (batch.id.hashCode() and 0xFFFF) + 200
        )

        val builder = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_PROGRESS)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(subText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentIntent(openDetailPendingIntent)
            .setProgress(total, completed, isIndeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        // Action Buttons
        if (batch.status == JobStatus.PAUSED) {
            val resumeIntent = SchedulerService.createResumePendingIntent(context, batch.id)
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                resumeIntent
            )
        } else {
            val pauseIntent = SchedulerService.createPausePendingIntent(context, batch.id)
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                pauseIntent
            )
        }

        val cancelIntent = SchedulerService.createCancelPendingIntent(context, batch.id)
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Cancel",
            cancelIntent
        )

        return builder.build()
    }

    fun showCloudflareAlert(
        batch: BatchEntity,
        crawlerName: String,
        targetUrl: String?
    ) {
        val alertId = ALERT_NOTIFICATION_ID_BASE + (crawlerName.hashCode() and 0x7FFF)
        val url = targetUrl?.takeIf { it.isNotBlank() } ?: batch.novelUrl
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: crawlerName

        val solveIntent = Intent(context, WebViewActivity::class.java).apply {
            putExtra("url", url)
            putExtra("host", host)
            putExtra("crawler_name", crawlerName)
            putExtra("batch_id", batch.id)
            putExtra("dismiss_notification_id", alertId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val solvePendingIntent = PendingIntent.getActivity(
            context,
            alertId,
            solveIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = SchedulerService.createCancelPendingIntent(context, batch.id)

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_ALERTS)
            .setContentTitle("Security Check: $crawlerName")
            .setContentText("Download paused for \"${batch.name}\". Tap to complete Cloudflare verification.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Cloudflare verification is required for source \"$crawlerName\". Tap 'Solve Now' to complete the challenge and continue downloading \"${batch.name}\".")
            )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentIntent(solvePendingIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Solve Now",
                solvePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel Task",
                cancelIntent
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(alertId, notification)
    }

    fun dismissCloudflareAlert(crawlerName: String) {
        val alertId = ALERT_NOTIFICATION_ID_BASE + (crawlerName.hashCode() and 0x7FFF)
        notificationManager.cancel(alertId)
    }

    fun showCompletionNotification(batch: BatchEntity, totalTasks: Int) {
        val notificationId = COMPLETE_NOTIFICATION_ID_BASE + (batch.id.hashCode() and 0x7FFF)
        val crawler = batch.crawlerName

        val contentIntent = if (!crawler.isNullOrBlank() && batch.novelUrl.isNotBlank()) {
            createOpenRoutePendingIntent(
                context = context,
                route = Screen.Novel.createRoute(crawler, batch.novelUrl),
                requestCode = notificationId
            )
        } else {
            createOpenRoutePendingIntent(
                context = context,
                route = Screen.Activity.route,
                requestCode = notificationId
            )
        }

        val typeLabel = when (batch.type) {
            JobType.RANGE_DOWNLOAD -> "Download Completed"
            JobType.ARTIFACT -> "Export Finished"
            JobType.NOVEL_METADATA -> "Novel Updated"
            else -> "Task Completed"
        }

        val summaryText = if (totalTasks > 0) {
            "Successfully finished $totalTasks items for \"${batch.name}\"."
        } else {
            "Completed \"${batch.name}\"."
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_COMPLETE)
            .setContentTitle(typeLabel)
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)

        if (!crawler.isNullOrBlank() && batch.novelUrl.isNotBlank()) {
            builder.addAction(
                android.R.drawable.ic_menu_view,
                "View Novel",
                contentIntent
            )
        }

        notificationManager.notify(notificationId, builder.build())
    }

    fun showFailureNotification(batch: BatchEntity, errorMessage: String?) {
        val notificationId = FAILED_NOTIFICATION_ID_BASE + (batch.id.hashCode() and 0x7FFF)
        val detailIntent = createOpenRoutePendingIntent(
            context = context,
            route = Screen.JobDetail.createRoute(batch.id),
            requestCode = notificationId
        )

        val retryIntent = SchedulerService.createReplayPendingIntent(context, batch.id)

        val err = errorMessage?.takeIf { it.isNotBlank() } ?: "An unexpected error occurred"
        val text = "Failed \"${batch.name}\": $err"

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_COMPLETE)
            .setContentTitle("Download Failed")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentIntent(detailIntent)
            .addAction(
                android.R.drawable.ic_popup_sync,
                "Retry",
                retryIntent
            )
            .addAction(
                android.R.drawable.ic_menu_info_details,
                "Details",
                detailIntent
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun createOpenRoutePendingIntent(
        context: Context,
        route: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(AppNavigationManager.EXTRA_NAV_ROUTE, route)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
