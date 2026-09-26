package com.halovoid.bunori.data.scheduler.jobs

import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.data.config.SchedulerConfig
import com.halovoid.bunori.data.db.dao.BatchDao
import com.halovoid.bunori.data.db.dao.TaskDao
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.handlers.utility.crawlerName
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.scheduler.SourceRateLimiter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class JobScheduler(
    private val batchDao: BatchDao,
    private val taskDao: TaskDao,
    private val handlerRegistry: JobHandlerRegistry,
    private val config: SchedulerConfig = SchedulerConfig(),
    private val retryPolicy: RetryPolicy = ExponentialBackoffPolicy(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val preferenceRepository: PreferenceRepository? = null,
    private val rateLimiter: SourceRateLimiter? = SourceRateLimiter()
) {
    private val trigger = Channel<Unit>(Channel.CONFLATED)
    private var pollingJob: Job? = null
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val crawlerPools = ConcurrentHashMap<String, WorkerPool>()
    private val blockedCrawlers = ConcurrentHashMap.newKeySet<String>()
    private var currentGlobalLimit = config.maxConcurrentJobs
    private var globalPool = WorkerPool(currentGlobalLimit)
    private val leaseMonitor = LeaseMonitor(config.abandonedTimeoutMs)
    private var onEmptyListener: (() -> Unit)? = null

    init {
        preferenceRepository?.maxConcurrentJobs?.onEach {
            notifyWakeup()
        }?.launchIn(scope)
    }

    fun isCrawlerBlocked(crawlerName: String): Boolean = blockedCrawlers.contains(crawlerName)

    fun getBlockedCrawlers(): Set<String> = blockedCrawlers.toSet()

    suspend fun blockCrawler(crawlerName: String) {
        blockedCrawlers.add(crawlerName)
        val activeBatches = batchDao.getActiveBatches()
        for (batch in activeBatches) {
            if (batch.crawlerName == crawlerName) {
                batchDao.updateStatus(batch.id, JobStatus.BLOCKED)
            }
        }
        notifyWakeup()
    }

    fun blockCrawlerAsync(crawlerName: String) {
        scope.launch {
            blockCrawler(crawlerName)
        }
    }

    suspend fun unblockCrawler(crawlerName: String) {
        blockedCrawlers.remove(crawlerName)
        val blockedBatches = batchDao.getBlockedBatches()
        for (batch in blockedBatches) {
            if (batch.crawlerName == crawlerName) {
                batchDao.updateStatusWithError(batch.id, JobStatus.RUNNING, null)
                taskDao.resumeTasksForBatch(batch.id)
            }
        }
        start()
    }

    fun unblockCrawlerAsync(crawlerName: String) {
        scope.launch {
            unblockCrawler(crawlerName)
        }
    }

    fun setOnEmptyListener(listener: () -> Unit) {
        this.onEmptyListener = listener
    }

    fun notifyWakeup() {
        trigger.trySend(Unit)
    }

    fun start() {
        notifyWakeup()
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    schedule()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                withTimeoutOrNull(config.pollingIntervalMs.milliseconds) {
                    trigger.receive()
                }
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun pauseJob(batchId: String) {
        scope.launch {
            batchDao.updateStatus(batchId, JobStatus.PAUSED)
            taskDao.updateUnfinishedStatusForBatch(batchId, JobStatus.PAUSED)
            val tasks = taskDao.getTasksByBatchId(batchId)
            tasks.forEach { activeJobs[it.id]?.cancel() }
            notifyWakeup()
        }
    }

    fun resumeJob(batchId: String) {
        scope.launch {
            val batch = batchDao.getBatchById(batchId)
            val crawlerName = batch?.crawlerName

            if (crawlerName != null && blockedCrawlers.contains(crawlerName)) {
                unblockCrawler(crawlerName)
            } else {
                batchDao.updateStatusWithError(batchId, JobStatus.RUNNING, null)
                taskDao.resumeTasksForBatch(batchId)
                start()
            }
        }
    }

    fun replayJob(batchId: String) {
        scope.launch {
            batchDao.updateStatusWithError(batchId, JobStatus.PENDING, null)
            taskDao.resetAllTasksForBatch(batchId)
            start()
        }
    }

    fun cancelActiveJob(batchId: String) {
        scope.launch {
            batchDao.updateStatus(batchId, JobStatus.CANCELLED)
            taskDao.updateUnfinishedStatusForBatch(batchId, JobStatus.CANCELLED)
            val tasks = taskDao.getTasksByBatchId(batchId)
            tasks.forEach { activeJobs[it.id]?.cancel() }
            notifyWakeup()
        }
    }

    private suspend fun schedule() {
        val prefMaxJobs = preferenceRepository?.maxConcurrentJobs?.first() ?: config.maxConcurrentJobs
        if (prefMaxJobs != currentGlobalLimit) {
            currentGlobalLimit = prefMaxJobs
            globalPool = WorkerPool(prefMaxJobs)
            crawlerPools.clear()
        }

        // 1. Recover abandoned / crashed tasks (e.g. app force closed while tasks were running)
        recoverAbandoned()

        // 2. Sync blocked crawlers with currently BLOCKED batches from DB
        val blockedBatches = batchDao.getBlockedBatches()
        val activeBlockedCrawlerNames = blockedBatches.mapNotNull { it.crawlerName }.toSet()
        blockedCrawlers.clear()
        blockedCrawlers.addAll(activeBlockedCrawlerNames)

        // 3. Fetch runnable tasks
        val runnableTasks = taskDao.getRunnableTasks(System.currentTimeMillis())

        val readyQueue = ReadyQueue()
        readyQueue.pushAll(runnableTasks)

        if (activeJobs.isEmpty() && readyQueue.isEmpty()) {
            val hasActive = batchDao.hasActiveOrPendingBatches()
            if (!hasActive) {
                onEmptyListener?.invoke()
            }
            return
        }

        launchReadyJobs(readyQueue)
    }

    private fun launchReadyJobs(readyQueue: ReadyQueue) {
        val saturatedCrawlers = mutableSetOf<String>()
        saturatedCrawlers.addAll(blockedCrawlers)

        while (true) {
            val task = readyQueue.pop(saturatedCrawlers) ?: break
            if (activeJobs.containsKey(task.id)) continue

            val isHighPriority = task.priority >= 5 || task.type == com.halovoid.bunori.data.db.entities.JobType.NOVEL_METADATA
            val crawlerName = task.crawlerName
            val pool = if (crawlerName != null) {
                crawlerPools.getOrPut(crawlerName) {
                    val crawler = CrawlerFactory.getCrawler(crawlerName)
                    val limit = crawler?.config?.runnerConcurrency ?: currentGlobalLimit
                    // Ensure the global pool doesn't undercut per-source concurrency
                    if (limit > globalPool.baseLimit) {
                        globalPool = WorkerPool(limit)
                    }
                    WorkerPool(limit)
                }
            } else {
                globalPool
            }

            if (!globalPool.tryAcquire(isHighPriority)) {
                readyQueue.pushFirst(task)
                break
            }

            if (pool != globalPool && !pool.tryAcquire(isHighPriority)) {
                globalPool.release()
                val key = crawlerName ?: "__global__"
                if (!isHighPriority) {
                    saturatedCrawlers.add(key)
                }
                readyQueue.pushFirst(task)
                continue
            }

            val job = scope.launch {
                try {
                    val runner = JobRunner(
                        batchDao,
                        taskDao,
                        handlerRegistry,
                        retryPolicy,
                        config,
                        rateLimiter,
                        onCrawlerBlocked = { blockedName, _ -> blockCrawler(blockedName) },
                        onRetryScheduled = { delayMs ->
                            scope.launch {
                                delay(delayMs.milliseconds)
                                notifyWakeup()
                            }
                        }
                    )
                    runner.run(task) { activeJobs.remove(task.id) }
                } catch (e: Exception) {
                    activeJobs.remove(task.id)
                } finally {
                    pool.release()
                    if (pool != globalPool) globalPool.release()
                    notifyWakeup()
                }
            }
            activeJobs[task.id] = job
        }
    }

    private suspend fun recoverAbandoned() {
        val now = System.currentTimeMillis()
        val runningTasks = taskDao.getRunningTasks()

        for (task in runningTasks) {
            if (!activeJobs.containsKey(task.id) && leaseMonitor.isExpired(task, now)) {
                val batch = batchDao.getBatchById(task.batchId)
                when (batch?.status) {
                    JobStatus.CANCELLED -> taskDao.updateStatus(task.id, JobStatus.CANCELLED)
                    JobStatus.PAUSED -> taskDao.updateStatus(task.id, JobStatus.PAUSED)
                    JobStatus.BLOCKED -> taskDao.updateStatus(task.id, JobStatus.BLOCKED)
                    else -> taskDao.updateStatus(task.id, JobStatus.PENDING)
                }
            }
        }
    }
}

internal class WorkerPool(val baseLimit: Int, val reservedHighPrioritySlots: Int = 1) {
    private val lock = Any()
    private var activeCount = 0

    fun tryAcquire(isHighPriority: Boolean = false): Boolean {
        synchronized(lock) {
            val maxAllowed = if (isHighPriority) baseLimit + reservedHighPrioritySlots else baseLimit
            if (activeCount < maxAllowed) {
                activeCount++
                return true
            }
            return false
        }
    }

    fun release() {
        synchronized(lock) {
            if (activeCount > 0) activeCount--
        }
    }
}

internal class LeaseMonitor(private val leaseDurationMs: Long) {
    fun isExpired(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean =
        task.status == JobStatus.RUNNING && (now - task.updatedAt) > leaseDurationMs
}

internal class ReadyQueue {
    private val buckets = java.util.TreeMap<Int, LinkedHashMap<String, ArrayDeque<TaskEntity>>>(reverseOrder())

    fun pushAll(jobs: Collection<TaskEntity>) {
        for (job in jobs) {
            val novelMap = buckets.getOrPut(job.priority) { LinkedHashMap() }
            val queue = novelMap.getOrPut(job.novelUrl ?: "") { ArrayDeque() }
            queue.add(job)
        }
    }

    fun pushFirst(task: TaskEntity) {
        val novelMap = buckets.getOrPut(task.priority) { LinkedHashMap() }
        val queue = novelMap.getOrPut(task.novelUrl ?: "") { ArrayDeque() }
        queue.addFirst(task)
    }

    fun pop(saturatedCrawlers: Set<String> = emptySet()): TaskEntity? {
        val bucketIterator = buckets.iterator()
        while (bucketIterator.hasNext()) {
            val (_, novelMap) = bucketIterator.next()
            if (novelMap.isEmpty()) {
                bucketIterator.remove()
                continue
            }

            val novelIterator = novelMap.entries.iterator()
            var selectedNovelUrl: String? = null
            var selectedQueue: ArrayDeque<TaskEntity>? = null

            while (novelIterator.hasNext()) {
                val entry = novelIterator.next()
                val candidate = entry.value.firstOrNull() ?: continue
                val crawlerName = candidate.crawlerName ?: "__global__"
                if (saturatedCrawlers.contains(crawlerName)) {
                    continue
                }
                selectedNovelUrl = entry.key
                selectedQueue = entry.value
                break
            }

            if (selectedNovelUrl != null && selectedQueue != null) {
                val task = selectedQueue.removeFirstOrNull()

                novelMap.remove(selectedNovelUrl)
                if (selectedQueue.isNotEmpty()) {
                    novelMap[selectedNovelUrl] = selectedQueue
                }

                if (novelMap.isEmpty()) {
                    bucketIterator.remove()
                }

                if (task != null) return task
            }
        }
        return null
    }

    fun isEmpty(): Boolean = buckets.isEmpty() || buckets.values.all { novelMap -> novelMap.values.all { it.isEmpty() } }
}
