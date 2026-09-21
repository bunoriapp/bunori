package com.halovoid.bunori.domain.models

import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType

data class Task(
    val id: String,
    val batchId: String,
    val name: String,
    val url: String?,
    val novelUrl: String,
    val type: JobType,
    val priority: Int = 0,
    val status: JobStatus = JobStatus.PENDING,
    val attemptCount: Int = 0,
    val maxAttempts: Int = 3,
    val error: String? = null,
    val metadata: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
