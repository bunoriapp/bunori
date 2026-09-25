package com.halovoid.bunori.data.scheduler.jobs

import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

enum class JobEvent {
    CLAIMED,
    HANDLER_SUCCESS,
    HANDLER_FAILURE_RETRYABLE,
    HANDLER_FAILURE_FINAL,
    CANCEL_REQUESTED,
    PAUSE_REQUESTED,
    RESUME_REQUESTED,
    BLOCKED_BY_PROTECTION
}

sealed class JobResult {
    object Success : JobResult()

    data class Failure(
        val error: Throwable,
        val isRecoverable: Boolean = true
    ) : JobResult()

    object Cancelled : JobResult()

    object Blocked : JobResult()
}

object JobStateMachine {
    fun transition(current: JobStatus, event: JobEvent): JobStatus =
        when (current to event) {
            JobStatus.PENDING to JobEvent.CLAIMED -> JobStatus.RUNNING
            JobStatus.PENDING to JobEvent.CANCEL_REQUESTED -> JobStatus.CANCELLED
            JobStatus.PENDING to JobEvent.PAUSE_REQUESTED -> JobStatus.PAUSED

            JobStatus.RUNNING to JobEvent.HANDLER_SUCCESS -> JobStatus.SUCCESS
            JobStatus.RUNNING to JobEvent.HANDLER_FAILURE_RETRYABLE -> JobStatus.PENDING // job now goes to pending if it fails once leaving the space for other jobs
            JobStatus.RUNNING to JobEvent.HANDLER_FAILURE_FINAL -> JobStatus.FAILED
            JobStatus.RUNNING to JobEvent.CANCEL_REQUESTED -> JobStatus.CANCELLED
            JobStatus.RUNNING to JobEvent.PAUSE_REQUESTED -> JobStatus.PAUSED
            JobStatus.RUNNING to JobEvent.BLOCKED_BY_PROTECTION -> JobStatus.BLOCKED

            JobStatus.PAUSED to JobEvent.CLAIMED -> JobStatus.RUNNING
            JobStatus.PAUSED to JobEvent.RESUME_REQUESTED -> JobStatus.PENDING
            JobStatus.PAUSED to JobEvent.CANCEL_REQUESTED -> JobStatus.CANCELLED
            JobStatus.PAUSED to JobEvent.PAUSE_REQUESTED -> JobStatus.PAUSED

            JobStatus.PENDING to JobEvent.RESUME_REQUESTED -> JobStatus.PENDING
            JobStatus.RUNNING to JobEvent.RESUME_REQUESTED -> JobStatus.RUNNING

            else -> current
        }
}

interface JobHandler {
    suspend fun handle(task: TaskEntity): JobResult
}

class JobHandlerRegistry {
    private val handlers = ConcurrentHashMap<JobType, JobHandler>()

    fun register(type: JobType, handler: JobHandler) {
        handlers[type] = handler
    }

    fun getHandler(type: JobType): JobHandler? = handlers[type]
}

interface RetryPolicy {
    fun getNextDelay(retryCount: Int): Long
}

class ExponentialBackoffPolicy(
    private val initialDelay: Long = 1000,
    private val factor: Double = 2.0,
    private val maxDelay: Long = 60000
) : RetryPolicy {
    override fun getNextDelay(retryCount: Int): Long {
        return (initialDelay * factor.pow(retryCount.toDouble())).toLong().coerceAtMost(maxDelay)
    }
}
