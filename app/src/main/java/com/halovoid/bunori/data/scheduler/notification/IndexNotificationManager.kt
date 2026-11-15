package com.halovoid.bunori.data.scheduler.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.halovoid.bunori.MainActivity
import com.halovoid.bunori.R

class IndexNotificationManager(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val INDEX_PROGRESS_NOTIFICATION_ID = 5500
        const val INDEX_COMPLETE_NOTIFICATION_ID = 5501
    }

    init {
        NotificationChannels.createChannels(context)
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, INDEX_PROGRESS_NOTIFICATION_ID, intent, flags)
    }

    fun showProgress(
        title: String,
        message: String,
        current: Int = 0,
        max: Int = 0,
        isIndeterminate: Boolean = false
    ) {
        try {
            val builder = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_PROGRESS)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
                .setContentIntent(createContentIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)

            if (isIndeterminate || max <= 0) {
                builder.setProgress(0, 0, true)
            } else {
                val percent = if (max > 0) (current * 100) / max else 0
                builder.setProgress(max, current, false)
                builder.setSubText("$percent%")
            }

            notificationManager.notify(INDEX_PROGRESS_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    fun showCompletion(title: String, message: String) {
        try {
            notificationManager.cancel(INDEX_PROGRESS_NOTIFICATION_ID)

            val builder = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_COMPLETE)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
                .setContentIntent(createContentIntent())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)

            notificationManager.notify(INDEX_COMPLETE_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    fun dismissProgress() {
        try {
            notificationManager.cancel(INDEX_PROGRESS_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }
}
