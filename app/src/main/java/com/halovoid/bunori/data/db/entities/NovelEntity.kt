package com.halovoid.bunori.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey val url: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val description: String?,
    val status: String? = null,
    val crawlerName: String,
    val alternativeNames: String? = null,
    val titleHash: Long? = null,
    val coverHttpsUrl: String? = null,
    val inLibrary: Boolean = false,
    val refreshExpiry: Long = 0L
)
