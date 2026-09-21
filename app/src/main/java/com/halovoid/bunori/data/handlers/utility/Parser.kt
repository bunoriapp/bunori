package com.halovoid.bunori.data.handlers.utility

import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.data.db.entities.BatchEntity
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.data.scheduler.RequestMetadata
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.domain.models.Task

private fun parseMetadataJson(rawJson: String?): RequestMetadata {
    if (rawJson.isNullOrBlank()) return RequestMetadata()
    return try {
        val crawler = Regex("\"crawlerName\"\\s*:\\s*\"([^\"]+)\"").find(rawJson)?.groupValues?.get(1)
        val format = Regex("\"format\"\\s*:\\s*\"([^\"]+)\"").find(rawJson)?.groupValues?.get(1)
            ?: Regex("\"artifactFormat\"\\s*:\\s*\"([^\"]+)\"").find(rawJson)?.groupValues?.get(1)
        val chapterId = Regex("\"chapterId\"\\s*:\\s*(\\d+)").find(rawJson)?.groupValues?.get(1)?.toIntOrNull()
        val startIndex = Regex("\"startIndex\"\\s*:\\s*(\\d+)").find(rawJson)?.groupValues?.get(1)?.toIntOrNull()
        val endIndex = Regex("\"endIndex\"\\s*:\\s*(\\d+)").find(rawJson)?.groupValues?.get(1)?.toIntOrNull()

        RequestMetadata(
            crawlerName = crawler,
            artifactFormat = format,
            chapterId = chapterId,
            format = format,
            startIndex = startIndex,
            endIndex = endIndex
        )
    } catch (_: Exception) {
        RequestMetadata()
    }
}

val Batch.parsedMetadata: RequestMetadata get() = parseMetadataJson(this.metadata)
val Batch.crawlerName: String? get() = parsedMetadata.crawlerName?.takeIf { it.isNotBlank() } ?: CrawlerFactory.getCrawlerByUrl(novelUrl)?.name

val BatchEntity.parsedMetadata: RequestMetadata get() = parseMetadataJson(this.metadata)
val BatchEntity.crawlerName: String? get() = parsedMetadata.crawlerName?.takeIf { it.isNotBlank() } ?: CrawlerFactory.getCrawlerByUrl(novelUrl)?.name

val TaskEntity.parsedMetadata: RequestMetadata get() = parseMetadataJson(this.metadata)
val TaskEntity.crawlerName: String? get() = parsedMetadata.crawlerName?.takeIf { it.isNotBlank() }
    ?: url?.let { CrawlerFactory.getCrawlerByUrl(it)?.name }
    ?: CrawlerFactory.getCrawlerByUrl(novelUrl)?.name

val Task.parsedMetadata: RequestMetadata get() = parseMetadataJson(this.metadata)
val Task.crawlerName: String? get() = parsedMetadata.crawlerName?.takeIf { it.isNotBlank() }
    ?: url?.let { CrawlerFactory.getCrawlerByUrl(it)?.name }
    ?: CrawlerFactory.getCrawlerByUrl(novelUrl)?.name