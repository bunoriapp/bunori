package com.halovoid.bunori.domain.models

import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType

data class Batch(
    val id: String,
    val name: String,
    val novelUrl: String,
    val priority: Int = 0,
    val type: JobType,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val progressTotal: Int = 0,
    val progressSuccess: Int = 0,
    val progressFailed: Int = 0,
    val progressCancelled: Int = 0,
    val status: JobStatus = JobStatus.PENDING,
    val metadata: String? = null,
    val error: String? = null
)
