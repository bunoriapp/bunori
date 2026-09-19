package com.halovoid.bunori.data.scheduler.services

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.halovoid.bunori.api.backup.BackupService
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.api.core.network.NetworkClient
import com.halovoid.bunori.api.core.scrapper.Scrapper
import com.halovoid.bunori.data.artifact.ArtifactGeneratorFactory
import com.halovoid.bunori.data.artifact.generators.EpubGenerator
import com.halovoid.bunori.data.artifact.generators.PdfGenerator
import com.halovoid.bunori.data.db.AppDatabase
import com.halovoid.bunori.data.db.dao.BatchDao
import com.halovoid.bunori.data.db.dao.TaskDao
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.data.handlers.ArtifactHandler
import com.halovoid.bunori.data.handlers.ChapterHandler
import com.halovoid.bunori.data.handlers.NovelMetadataHandler
import com.halovoid.bunori.data.handlers.RangeDownloadHandler
import com.halovoid.bunori.data.handlers.utility.crawlerName
import com.halovoid.bunori.data.repository.ArtifactRepository
import com.halovoid.bunori.data.repository.ChapterRepository
import com.halovoid.bunori.data.repository.DownloadRepositoryImpl
import com.halovoid.bunori.data.repository.NovelRepository
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.StorageRepositoryImpl
import com.halovoid.bunori.data.scheduler.jobs.JobHandlerRegistry
import com.halovoid.bunori.data.scheduler.jobs.JobScheduler
import com.halovoid.bunori.data.scheduler.notification.DownloadNotificationManager
import com.halovoid.bunori.extension.manager.ExtensionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class SchedulerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var scheduler: JobScheduler
    private lateinit var batchDao: BatchDao
    private lateinit var taskDao: TaskDao
    private lateinit var notificationManager: DownloadNotificationManager

    private val previousBatchStatuses = ConcurrentHashMap<String, JobStatus>()
    private val notifiedBlockedCrawlers = ConcurrentHashMap.newKeySet<String>()

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_CANCEL_JOB = "ACTION_CANCEL_JOB"
        const val ACTION_PAUSE_JOB = "ACTION_PAUSE_JOB"
        const val ACTION_RESUME_JOB = "ACTION_RESUME_JOB"
        const val ACTION_REPLAY_JOB = "ACTION_REPLAY_JOB"
        const val ACTION_UNBLOCK_CRAWLER = "ACTION_UNBLOCK_CRAWLER"

        const val EXTRA_JOB_ID = "EXTRA_JOB_ID"
        const val EXTRA_CRAWLER_NAME = "EXTRA_CRAWLER_NAME"

        fun startService(context: Context) {
            val intent = Intent(context, SchedulerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SchedulerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun cancelJob(context: Context, jobId: String) {
            val intent = Intent(context, SchedulerService::class.java).apply {
                action = ACTION_CANCEL_JOB
                putExtra(EXTRA_JOB_ID, jobId)
            }
            context.startService(intent)
        }

        fun pauseJob(context: Context, jobId: String) {
            val intent = Intent(context, SchedulerService::class.java).apply {
                action = ACTION_PAUSE_JOB
                putExtra(EXTRA_JOB_ID, jobId)
            }
            context.startService(intent)
        }

        fun resumeJob(context: Context, jobId: String) {
            val intent = Intent(context, SchedulerService::class.java).apply {
                action = ACTION_RESUME_JOB
                putExtra(EXTRA_JOB_ID, jobId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun unblockCrawler(context: Context, crawlerName: String) {
            val intent = Intent(context, SchedulerService::class.java).apply {
                action = ACTION_UNBLOCK_CRAWLER
                putExtra(EXTRA_CRAWLER_NAME, crawlerName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun replayJob(context: Context, jobId: String) {
            val intent = Intent(context, SchedulerService::class.java).apply {
                action = ACTION_REPLAY_JOB
                putExtra(EXTRA_JOB_ID, jobId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun createPausePendingIntent(context: Context, jobId: String): PendingIntent {
            val intent = Intent(context, SchedulerService::class.java).apply {
                action = ACTION_PAUSE_JOB
                putExtra(EXTRA_JOB_ID, jobId)
            }
            return PendingIntent.getService(
                context,
                (jobId.hashCode() and 0xFFFF) + 10,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun createResumePendingIntent(context: Context, jobId: String): PendingIntent {
            val intent = Intent(context, SchedulerService::class.java).apply {
                action = ACTION_RESUME_JOB
                putExtra(EXTRA_JOB_ID, jobId)
            }
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    (jobId.hashCode() and 0xFFFF) + 20,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getService(
                    context,
                    (jobId.hashCode() and 0xFFFF) + 20,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
        }

        fun createCancelPendingIntent(context: Context, jobId: String): PendingIntent {
            val intent = Intent(context, SchedulerService::class.java).apply {
                action = ACTION_CANCEL_JOB
                putExtra(EXTRA_JOB_ID, jobId)
            }
            return PendingIntent.getService(
                context,
                (jobId.hashCode() and 0xFFFF) + 30,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun createReplayPendingIntent(context: Context, jobId: String): PendingIntent {
            val intent = Intent(context, SchedulerService::class.java).apply {
                action = ACTION_REPLAY_JOB
                putExtra(EXTRA_JOB_ID, jobId)
            }
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    (jobId.hashCode() and 0xFFFF) + 40,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getService(
                    context,
                    (jobId.hashCode() and 0xFFFF) + 40,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(this)
        batchDao = db.batchDao()
        taskDao = db.taskDao()
        notificationManager = DownloadNotificationManager(this)

        val novelRepository = NovelRepository.getInstance(this)
        val chapterRepository = ChapterRepository.getInstance(this)
        val preferenceRepository = PreferenceRepository.getInstance(this)
        val storageRepository = StorageRepositoryImpl.getInstance(this)
        val artifactRepository = ArtifactRepository.getInstance(this)
        val downloadRepository = DownloadRepositoryImpl.getInstance(this)

        val epubGenerator = EpubGenerator(storageRepository, downloadRepository, preferenceRepository)
        val pdfGenerator = PdfGenerator(storageRepository, downloadRepository, preferenceRepository)
        val generatorFactory = ArtifactGeneratorFactory(listOf(epubGenerator, pdfGenerator))

        val registry = JobHandlerRegistry()
        val crawlerFactory = CrawlerFactory

        val scrapper = Scrapper(NetworkClient.okHttpClient)

        registry.register(JobType.CHAPTER, ChapterHandler(
            scrapper, chapterRepository, storageRepository, crawlerFactory,
            downloadRepository, novelRepository
        ))
        registry.register(JobType.NOVEL_METADATA, NovelMetadataHandler(
            crawlerFactory, novelRepository, chapterRepository, storageRepository, preferenceRepository
        ))
        registry.register(JobType.ARTIFACT, ArtifactHandler(
            novelRepository, chapterRepository, crawlerFactory, storageRepository, generatorFactory, artifactRepository, downloadRepository
        ))
        registry.register(JobType.BACKUP, BackupService(applicationContext))
        registry.register(JobType.RANGE_DOWNLOAD, RangeDownloadHandler(chapterRepository, taskDao))

        scheduler = JobScheduler(batchDao, taskDao, registry, preferenceRepository = preferenceRepository)
        scheduler.setOnEmptyListener {
            stopSelf()
        }

        serviceScope.launch {
            ExtensionManager.getInstance(this@SchedulerService)
                .loadInstalledExtensions()
        }

        observeProgress()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val jobId = intent?.getStringExtra(EXTRA_JOB_ID)
        when (intent?.action) {
            ACTION_START -> {
                ensureForeground()
                scheduler.start()
            }
            ACTION_STOP -> {
                scheduler.stop()
                stopSelf()
            }
            ACTION_CANCEL_JOB -> {
                if (jobId != null) {
                    scheduler.cancelActiveJob(jobId)
                }
            }
            ACTION_PAUSE_JOB -> {
                if (jobId != null) {
                    scheduler.pauseJob(jobId)
                }
            }
            ACTION_RESUME_JOB -> {
                ensureForeground()
                if (jobId != null) {
                    scheduler.resumeJob(jobId)
                } else {
                    scheduler.start()
                }
            }
            ACTION_REPLAY_JOB -> {
                ensureForeground()
                if (jobId != null) {
                    scheduler.replayJob(jobId)
                } else {
                    scheduler.start()
                }
            }
            ACTION_UNBLOCK_CRAWLER -> {
                ensureForeground()
                val crawlerName = intent?.getStringExtra(EXTRA_CRAWLER_NAME)
                if (crawlerName != null) {
                    notifiedBlockedCrawlers.remove(crawlerName)
                    notificationManager.dismissCloudflareAlert(crawlerName)
                    scheduler.unblockCrawlerAsync(crawlerName)
                } else {
                    scheduler.start()
                }
            }
        }
        return START_STICKY
    }

    private fun ensureForeground() {
        startForeground(
            DownloadNotificationManager.FOREGROUND_NOTIFICATION_ID,
            notificationManager.createInitialForegroundNotification()
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scheduler.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeProgress() {
        combine(
            batchDao.getBatchesWithStatsFlow(),
            taskDao.getRunningTasksFlow()
        ) { batches, runningTasks ->
            handleProgressUpdate(batches, runningTasks)
        }.launchIn(serviceScope)
    }

    private fun handleProgressUpdate(
        batches: List<com.halovoid.bunori.data.db.dao.BatchWithStats>,
        runningTasks: List<com.halovoid.bunori.data.db.entities.TaskEntity>
    ) {
        // 1. Process batch transitions (Blocked, Success, Failure)
        for (item in batches) {
            val batch = item.batch
            val prevStatus = previousBatchStatuses[batch.id]
            val currentStatus = batch.status

            if (prevStatus != currentStatus) {
                val crawler = batch.crawlerName.orEmpty()

                when (currentStatus) {
                    JobStatus.BLOCKED -> {
                        if (crawler.isNotBlank() && !notifiedBlockedCrawlers.contains(crawler)) {
                            notifiedBlockedCrawlers.add(crawler)
                            val targetUrl = runningTasks.firstOrNull { it.batchId == batch.id }?.url ?: batch.novelUrl
                            notificationManager.showCloudflareAlert(batch, crawler, targetUrl)
                        }
                    }

                    JobStatus.SUCCESS -> {
                        if (prevStatus == JobStatus.RUNNING || prevStatus == JobStatus.BLOCKED || prevStatus == JobStatus.PENDING) {
                            notificationManager.showCompletionNotification(batch, item.totalTasks)
                            if (crawler.isNotBlank()) {
                                notifiedBlockedCrawlers.remove(crawler)
                                notificationManager.dismissCloudflareAlert(crawler)
                            }
                        }
                    }

                    JobStatus.FAILED -> {
                        if (prevStatus == JobStatus.RUNNING || prevStatus == JobStatus.BLOCKED || prevStatus == JobStatus.PENDING) {
                            notificationManager.showFailureNotification(batch, batch.error)
                            if (crawler.isNotBlank()) {
                                notifiedBlockedCrawlers.remove(crawler)
                                notificationManager.dismissCloudflareAlert(crawler)
                            }
                        }
                    }

                    JobStatus.RUNNING -> {
                        if (crawler.isNotBlank() && notifiedBlockedCrawlers.contains(crawler)) {
                            notifiedBlockedCrawlers.remove(crawler)
                            notificationManager.dismissCloudflareAlert(crawler)
                        }
                    }

                    else -> {}
                }

                previousBatchStatuses[batch.id] = currentStatus
            }
        }

        // 2. Active tasks for foreground notification
        val active = batches.filter {
            it.batch.status == JobStatus.RUNNING ||
            it.batch.status == JobStatus.PAUSED ||
            it.batch.status == JobStatus.PENDING
        }.sortedByDescending { it.batch.updatedAt }

        if (active.isNotEmpty()) {
            val primary = active.first()
            val notification = notificationManager.createProgressNotification(
                primaryBatch = primary,
                runningTasks = runningTasks,
                activeBatchesCount = active.size
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(DownloadNotificationManager.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }
}
