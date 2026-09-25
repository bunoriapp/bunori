package com.halovoid.bunori.data.artifact

import com.halovoid.bunori.data.scheduler.JobMetadata
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Novel
import java.io.File

interface ArtifactGenerator {
    val format: String

    suspend fun generate(
        novel: Novel,
        chapters: List<Chapter>,
        metadata: JobMetadata,
        onProgress: (suspend (current: Int, total: Int, stage: String) -> Unit)? = null
    ): File
}