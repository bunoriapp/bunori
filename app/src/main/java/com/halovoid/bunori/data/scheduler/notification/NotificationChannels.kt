package com.halovoid.bunori.data.scheduler.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val CHANNEL_PROGRESS = "downloader_progress_channel"
    const val CHANNEL_ALERTS = "downloader_alerts_channel"
    const val CHANNEL_COMPLETE = "downloader_complete_channel"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS,
                "Active Tasks & Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for novel downloads, metadata fetching, and artifact generation"
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Cloudflare & Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a source requires Cloudflare or human verification"
                enableVibration(true)
                setShowBadge(true)
            }

            val completeChannel = NotificationChannel(
                CHANNEL_COMPLETE,
                "Task Completions & Errors",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when background downloads or exports finish or fail"
                setShowBadge(true)
            }

            manager.createNotificationChannels(listOf(progressChannel, alertsChannel, completeChannel))
        }
    }
}
