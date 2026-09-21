package com.halovoid.bunori.data.scheduler

data class JobMetadata(
    val crawlerName: String? = null,
    val artifactFormat: String? = null,
    val chapterId: Int? = null,
    val format: String? = null,
    val startIndex: Int? = null,
    val endIndex: Int? = null,
)