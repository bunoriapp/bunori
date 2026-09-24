package com.halovoid.bunori.data.db.mappers

import com.halovoid.bunori.data.db.dao.BatchWithStats
import com.halovoid.bunori.data.db.entities.ArtifactEntity
import com.halovoid.bunori.data.db.entities.BatchEntity
import com.halovoid.bunori.data.db.entities.ChapterEntity
import com.halovoid.bunori.data.db.entities.DownloadEntity
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.NovelEntity
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.domain.models.Artifact
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Download
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.domain.models.Task

// Novel Mappings
fun NovelEntity.toDomain(): Novel = Novel(
    url = url,
    title = title,
    author = author,
    coverUrl = coverUrl,
    description = description,
    status = status,
    crawlerName = crawlerName,
    alternativeNames = alternativeNames,
    chapters = emptyList(), // Chapters are usually loaded separately
    titleHash = titleHash,
    coverHttpsUrl = coverHttpsUrl,
    inLibrary = inLibrary,
    refreshExpiry = refreshExpiry
)


fun Novel.toEntity() = NovelEntity(
    url = url,
    title = title,
    author = author,
    coverUrl = coverUrl,
    description = description,
    status = status,
    crawlerName = crawlerName,
    alternativeNames = alternativeNames,
    titleHash = titleHash,
    coverHttpsUrl = coverHttpsUrl,
    inLibrary = inLibrary,
    refreshExpiry = refreshExpiry
)

// Chapter Mappings

fun ChapterEntity.toDomain(): Chapter = Chapter(
    id = id,
    url = url,
    title = title,
    index = index,
    novelUrl = novelUrl
).apply {
    sourceUrl = this@toDomain.sourceUrl
    scanlationSource = this@toDomain.scanlationSource
    read = this@toDomain.read
}

fun Chapter.toEntity(): ChapterEntity = ChapterEntity(
    id = id,
    url = url,
    sourceUrl = sourceUrl,
    scanlationSource = scanlationSource,
    title = title,
    index = index,
    novelUrl = novelUrl,
    read = read
)

// Artifact Mappings
fun ArtifactEntity.toDomain() : Artifact = Artifact(
    id = id,
    novelUrl = novelUrl,
    requestId = requestId,
    artifactDestination = artifactDestination,
    artifactName = artifactName
)

fun Artifact.toEntity() : ArtifactEntity = ArtifactEntity(
    id = id,
    novelUrl = novelUrl,
    requestId = requestId,
    artifactDestination = artifactDestination,
    artifactName = artifactName
)

// Batch and Task

fun BatchWithStats.toDomain(): Batch {
    val effectiveStatus = when {
        batch.status == JobStatus.PAUSED -> JobStatus.PAUSED
        batch.status == JobStatus.CANCELLED -> JobStatus.CANCELLED
        blockedTasks > 0 && runningTasks == 0 -> JobStatus.BLOCKED
        runningTasks > 0 -> JobStatus.RUNNING
        pendingTasks > 0 -> if (batch.status == JobStatus.RUNNING) JobStatus.RUNNING else JobStatus.PENDING
        totalTasks > 0 && (completedTasks + failedTasks + cancelledTasks >= totalTasks) -> {
            when {
                cancelledTasks == totalTasks -> JobStatus.CANCELLED
                failedTasks > 0 -> JobStatus.FAILED
                else -> JobStatus.SUCCESS
            }
        }
        else -> batch.status
    }

    return Batch(
        id = batch.id,
        name = batch.name,
        novelUrl = batch.novelUrl,
        priority = batch.priority,
        type = batch.type,
        createdAt = batch.createdAt,
        updatedAt = batch.updatedAt,
        completedAt = batch.completedAt,
        progressTotal = totalTasks,
        progressSuccess = completedTasks,
        progressFailed = failedTasks,
        progressCancelled = cancelledTasks,
        status = effectiveStatus,
        metadata = batch.metadata,
        error = batch.error
    )
}

fun BatchEntity.toDomain(): Batch = Batch(
    id = id,
    name = name,
    novelUrl = novelUrl,
    priority = priority,
    type = type,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    progressTotal = 0,
    progressSuccess = 0,
    progressFailed = 0,
    progressCancelled = 0,
    status = status,
    metadata = metadata,
    error = error
)

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    batchId = batchId,
    name = name,
    url = url,
    novelUrl = novelUrl,
    priority = priority,
    type = type,
    status = status,
    attemptCount = attemptCount,
    maxAttempts = maxAttempts,
    error = error,
    metadata = metadata,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    batchId = batchId,
    name = name,
    url = url,
    novelUrl = novelUrl,
    priority = priority,
    type = type,
    status = status,
    attemptCount = attemptCount,
    maxAttempts = maxAttempts,
    error = error,
    metadata = metadata,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt
)

// Download
fun DownloadEntity.toDomain(): Download = Download(
    id = id,
    novelUrl = novelUrl,
    chapterUrl = chapterUrl,
    fileLocation = fileLocation,
    chapterIndex = chapterIndex,
    chapterTitle = chapterTitle,
    scanlationSource = scanlationSource,
    novelTitle = novelTitle,
    sizeBytes = sizeBytes,
    downloadedAt = downloadedAt,
    isCache = isCache,
    expirationTime = expirationTime
)

fun Download.toEntity(): DownloadEntity = DownloadEntity(
    id = id,
    novelUrl = novelUrl,
    chapterUrl = chapterUrl,
    fileLocation = fileLocation,
    chapterIndex = chapterIndex,
    chapterTitle = chapterTitle,
    scanlationSource = scanlationSource,
    novelTitle = novelTitle,
    sizeBytes = sizeBytes,
    downloadedAt = downloadedAt,
    isCache = isCache,
    expirationTime = expirationTime
)