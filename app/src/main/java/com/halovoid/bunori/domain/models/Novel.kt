package com.halovoid.bunori.domain.models

data class Novel(
    val url: String,
    val title: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val status: String? = null,
    val chapters: List<Chapter> = emptyList(),
    val crawlerName: String,
    val alternativeNames: String? = null,
    val titleHash: Long? = null,
    val coverHttpsUrl: String? = null,
    val inLibrary: Boolean = false,
    val refreshExpiry: Long = 0L
)