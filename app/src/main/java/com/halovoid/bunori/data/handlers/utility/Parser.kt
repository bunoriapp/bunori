package com.halovoid.bunori.data.handlers.utility

import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.data.db.entities.BatchEntity
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.scheduler.JobMetadata
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.domain.models.Task

private fun parseMetadataJson(rawJson: String?): JobMetadata {
    if (rawJson.isNullOrBlank()) return JobMetadata()
    return try {
        val crawler = Regex("\"crawlerName\"\\s*:\\s*\"([^\"]+)\"").find(rawJson)?.groupValues?.get(1)
        val format = Regex("\"format\"\\s*:\\s*\"([^\"]+)\"").find(rawJson)?.groupValues?.get(1)
            ?: Regex("\"artifactFormat\"\\s*:\\s*\"([^\"]+)\"").find(rawJson)?.groupValues?.get(1)
        val chapterId = Regex("\"chapterId\"\\s*:\\s*(\\d+)").find(rawJson)?.groupValues?.get(1)?.toIntOrNull()
        val startIndex = Regex("\"startIndex\"\\s*:\\s*(\\d+)").find(rawJson)?.groupValues?.get(1)?.toIntOrNull()
        val endIndex = Regex("\"endIndex\"\\s*:\\s*(\\d+)").find(rawJson)?.groupValues?.get(1)?.toIntOrNull()

        // graceful transition in case a job was still running
        var updatedCrawler = ""

        if (crawler?.contains(".") == true) {
            updatedCrawler = crawler
        } else {
            updatedCrawler = "bext.$crawler"
        }
        JobMetadata(
            crawlerName = crawler,
            artifactFormat = format,
            chapterId = chapterId,
            format = format,
            startIndex = startIndex,
            endIndex = endIndex
        )
    } catch (_: Exception) {
        JobMetadata()
    }
}

val Batch.parsedMetadata: JobMetadata get() = parseMetadataJson(this.metadata)
val Batch.crawlerName: String? get() = parsedMetadata.crawlerName?.takeIf { it.isNotBlank() } ?: CrawlerFactory.getCrawlerByUrl(novelUrl)?.name

val BatchEntity.parsedMetadata: JobMetadata get() = parseMetadataJson(this.metadata)
val BatchEntity.crawlerName: String? get() = parsedMetadata.crawlerName?.takeIf { it.isNotBlank() } ?: CrawlerFactory.getCrawlerByUrl(novelUrl)?.name

val TaskEntity.parsedMetadata: JobMetadata get() = parseMetadataJson(this.metadata)
val TaskEntity.crawlerName: String? get() = parsedMetadata.crawlerName?.takeIf { it.isNotBlank() }
    ?: url?.let { CrawlerFactory.getCrawlerByUrl(it)?.name }
    ?: CrawlerFactory.getCrawlerByUrl(novelUrl)?.name

val Task.parsedMetadata: JobMetadata get() = parseMetadataJson(this.metadata)
val Task.crawlerName: String? get() = parsedMetadata.crawlerName?.takeIf { it.isNotBlank() }
    ?: url?.let { CrawlerFactory.getCrawlerByUrl(it)?.name }
    ?: CrawlerFactory.getCrawlerByUrl(novelUrl)?.name