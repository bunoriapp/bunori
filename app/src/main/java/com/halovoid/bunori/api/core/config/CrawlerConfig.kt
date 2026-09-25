package com.halovoid.bunori.api.core.config

data class CrawlerConfig (
    val userFolderLocation: String,
    val maxAttempts: Int,
    val ignoreImages: Boolean = false,
    val runnerConcurrency: Int = 3,
    val runnerCooldown: Int = 1,
    val maxSessionPerExit: Int = 0,
    val runnerCooldownMs: Long = runnerCooldown * 1000L
)