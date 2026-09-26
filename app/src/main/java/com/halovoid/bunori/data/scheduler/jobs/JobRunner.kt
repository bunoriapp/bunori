package com.halovoid.bunori.data.scheduler.jobs

import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.data.config.SchedulerConfig
import com.halovoid.bunori.data.db.dao.BatchDao
import com.halovoid.bunori.data.db.dao.TaskDao
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.handlers.utility.crawlerName
import com.halovoid.bunori.data.scheduler.SourceRateLimiter
import kotlinx.coroutines.CancellationException

class JobRunner(
    private val batchDao: BatchDao,
    private val taskDao: TaskDao,
    private val handlerRegistry: JobHandlerRegistry,
    private val retryPolicy: RetryPolicy,
    private val config: SchedulerConfig,
    private val rateLimiter: SourceRateLimiter? = null,
    private val onCrawlerBlocked: (suspend (crawlerName: String, task: TaskEntity) -> Unit)? = null,
    private val onRetryScheduled: ((delayMs: Long) -> Unit)? = null
) {
    companion object {
        private const val DEFAULT_MAX_ATTEMPTS = 3
    }

    suspend fun run(task: TaskEntity, onComplete: suspend () -> Unit) {
        try {
            val preClaim = taskDao.getTaskById(task.id)
            if (preClaim == null || preClaim.status == JobStatus.CANCELLED || preClaim.status == JobStatus.PAUSED) {
                return
            }

            val claimedStatus = JobStateMachine.transition(task.status, JobEvent.CLAIMED)
            taskDao.updateStatus(task.id, claimedStatus)
            batchDao.updateStatus(task.batchId, JobStatus.RUNNING)

            val handler = handlerRegistry.getHandler(task.type)
            if (handler == null) {
                fail(task, "No handler found for ${task.type}", task.attemptCount)
                return
            }

            val maxAttempts = maxAttemptsFor(task)
            val isHighPriority = task.priority >= 5 || task.type == JobType.NOVEL_METADATA

            val crawlerName = task.crawlerName
            if (crawlerName != null && rateLimiter != null) {
                val crawler = CrawlerFactory.getCrawler(crawlerName)
                val cooldownMs = crawler?.config?.runnerCooldownMs ?: 1000L
                rateLimiter.acquire(crawlerName, cooldownMs, maxJitterMs = 250L, isHighPriority = isHighPriority)
            }

            val preExec = taskDao.getTaskById(task.id) ?: task
            if (preExec.status == JobStatus.CANCELLED || preExec.status == JobStatus.PAUSED) {
                return
            }

            val result = handler.handle(task)

            val latest = taskDao.getTaskById(task.id) ?: task
            if (latest.status == JobStatus.CANCELLED || latest.status == JobStatus.PAUSED) {
                return
            }

            when (result) {
                is JobResult.Success -> {
                    markSuccess(latest)
                    return
                }

                is JobResult.Cancelled -> {
                    markCancelled(latest)
                    return
                }

                is JobResult.Blocked -> {
                    markBlocked(latest)
                    return
                }

                is JobResult.Failure -> {
                    val attemptsSoFar = latest.attemptCount + 1
                    val canRetry = result.isRecoverable && attemptsSoFar < maxAttempts

                    if (!canRetry) {
                        fail(latest, result.error.message ?: "Execution Failed", attemptsSoFar)
                        return
                    }

                    val delayMs = retryPolicy.getNextDelay(attemptsSoFar)
                    val nextRunAt = System.currentTimeMillis() + delayMs

                    taskDao.markRetrying(latest.id, attemptsSoFar, result.error.message, nextRunAt)
                    onRetryScheduled?.invoke(delayMs)
                    return
                }
            }
        } catch (e: CancellationException) {
            runCatching {
                val latestTask = taskDao.getTaskById(task.id)
                val batch = batchDao.getBatchById(task.batchId)

                val targetStatus = if (batch?.status == JobStatus.PAUSED || latestTask?.status == JobStatus.PAUSED) {
                    JobStateMachine.transition(task.status, JobEvent.PAUSE_REQUESTED)
                } else if (batch?.status == JobStatus.CANCELLED || latestTask?.status == JobStatus.CANCELLED) {
                    JobStateMachine.transition(task.status, JobEvent.CANCEL_REQUESTED)
                } else {
                    JobStatus.PENDING
                }
                taskDao.updateStatus(task.id, targetStatus)
                syncBatchCompletion(task.batchId)
            }
            throw e
        } catch (e: Exception) {
            fail(task, e.message ?: "Unexpected error during execution", task.attemptCount + 1)
        } finally {
            onComplete()
        }
    }

    private fun maxAttemptsFor(task: TaskEntity): Int {
        val crawlerName = task.crawlerName
        val crawlerMax = crawlerName?.let { CrawlerFactory.getCrawler(it)?.config?.maxAttempts }
        return crawlerMax ?: task.maxAttempts.takeIf { it > 0 } ?: DEFAULT_MAX_ATTEMPTS
    }

    private suspend fun markSuccess(task: TaskEntity) {
        JobStateMachine.transition(task.status, JobEvent.HANDLER_SUCCESS)
        taskDao.markSuccess(task.id)
        syncBatchCompletion(task.batchId)
    }

    private suspend fun fail(task: TaskEntity, errorMessage: String, attempts: Int) {
        JobStateMachine.transition(task.status, JobEvent.HANDLER_FAILURE_FINAL)
        taskDao.markFailed(task.id, errorMessage, attempts)
        syncBatchCompletion(task.batchId)
    }

    private suspend fun markCancelled(task: TaskEntity) {
        val targetStatus = JobStateMachine.transition(task.status, JobEvent.CANCEL_REQUESTED)
        taskDao.updateStatus(task.id, targetStatus)
        syncBatchCompletion(task.batchId)
    }

    private suspend fun markBlocked(task: TaskEntity) {
        val targetStatus = JobStateMachine.transition(task.status, JobEvent.BLOCKED_BY_PROTECTION)
        taskDao.updateStatus(task.id, targetStatus)
        batchDao.updateStatus(task.batchId, targetStatus)
        val crawler = task.crawlerName
        if (crawler != null) {
            onCrawlerBlocked?.invoke(crawler, task)
        }
    }

    private suspend fun syncBatchCompletion(batchId: String) {
        val tasks = taskDao.getTasksByBatchId(batchId)
        if (tasks.isEmpty()) return

        val batch = batchDao.getBatchById(batchId) ?: return
        if (batch.status == JobStatus.CANCELLED || batch.status == JobStatus.PAUSED || batch.status == JobStatus.BLOCKED) {
            return
        }

        val allCompleted = tasks.all { 
            it.status == JobStatus.SUCCESS || 
            it.status == JobStatus.FAILED || 
            it.status == JobStatus.CANCELLED 
        }

        if (allCompleted) {
            val hasFailed = tasks.any { it.status == JobStatus.FAILED }
            val allCancelled = tasks.all { it.status == JobStatus.CANCELLED }

            val finalStatus = when {
                allCancelled -> JobStatus.CANCELLED
                hasFailed -> JobStatus.FAILED
                else -> JobStatus.SUCCESS
            }
            batchDao.markCompleted(batchId, finalStatus)
        } else {
            val anyRunning = tasks.any { it.status == JobStatus.RUNNING }
            if (anyRunning && batch.status != JobStatus.RUNNING) {
                batchDao.updateStatus(batchId, JobStatus.RUNNING)
            }
        }
    }
}
