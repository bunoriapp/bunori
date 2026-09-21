package com.halovoid.bunori.data.db.mappers

import com.halovoid.bunori.data.db.dao.BatchWithStats
import com.halovoid.bunori.data.db.entities.ArtifactEntity
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

// Novel Mappings
fun NovelEntity.toDomain(): Novel = Novel(
    url = url,
    title = title,
    author = author,
    coverUrl = coverUrl,
    description = description,
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
        parentNovel = batch.novelUrl,
        url = null,
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
        rstatus = effectiveStatus,
        metadata = batch.metadata,
        error = batch.error
    )
}

fun TaskEntity.toDomain(): Batch = Batch(
    id = id,
    name = name,
    parentNovel = novelUrl,
    dependsOn = batchId,
    url = url,
    novelUrl = novelUrl,
    priority = priority,
    type = type,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    progressTotal = 1,
    progressSuccess = if (status == JobStatus.SUCCESS) 1 else 0,
    progressFailed = if (status == JobStatus.FAILED) 1 else 0,
    progressCancelled = if (status == JobStatus.CANCELLED) 1 else 0,
    status = status,
    rstatus = status,
    metadata = metadata,
    error = error
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