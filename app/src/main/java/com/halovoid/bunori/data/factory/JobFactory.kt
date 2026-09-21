package com.halovoid.bunori.data.factory

import com.halovoid.bunori.data.db.entities.BatchEntity
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.ui.feature.novel.components.artifact.ExportFormat
import org.json.JSONObject

class JobFactory {

    fun metadata(novel: Novel): BatchEntity {
        val metadata = JSONObject().apply {
            put("crawlerName", novel.crawlerName)
        }.toString()

        return BatchEntity(
            id = "${novel.url}_metadata",
            type = JobType.NOVEL_METADATA,
            novelUrl = novel.url,
            name = "Metadata: ${novel.title}",
            metadata = metadata,
            status = JobStatus.PENDING,
            priority = 0
        )
    }

    fun metadataFromUrl(crawlerName: String, url: String, title: String): BatchEntity {
        val metadata = JSONObject().apply {
            put("crawlerName", crawlerName)
        }.toString()

        return BatchEntity(
            id = "${url}_metadata",
            type = JobType.NOVEL_METADATA,
            novelUrl = url,
            name = "Metadata: $title",
            metadata = metadata,
            status = JobStatus.PENDING,
            priority = 0
        )
    }

    fun rangeDownload(novel: Novel, start: Int, end: Int, chapterCount: Int): BatchEntity {
        val metadata = JSONObject().apply {
            put("crawlerName", novel.crawlerName)
            put("startIndex", start)
            put("endIndex", end)
        }.toString()

        return BatchEntity(
            id = "${novel.url}_download_${start}_${end}_${System.currentTimeMillis()}",
            type = JobType.RANGE_DOWNLOAD,
            novelUrl = novel.url,
            name = "Download: ${novel.title} ($start-$end)",
            metadata = metadata,
            status = JobStatus.PENDING
        )
    }

    fun chapter(novel: Novel, chapter: Chapter): BatchEntity {
        val metadata = JSONObject().apply {
            put("chapterId", chapter.id)
            put("crawlerName", novel.crawlerName)
        }.toString()

        return BatchEntity(
            id = "${novel.url}_chapter_${chapter.index}_${chapter.id}",
            type = JobType.CHAPTER,
            priority = 10,
            name = "Chapter: ${chapter.title}",
            status = JobStatus.PENDING,
            metadata = metadata,
            novelUrl = novel.url
        )
    }

    fun export(novel: Novel, format: ExportFormat, start: Int, end: Int, selectedSources: Set<String>? = null): BatchEntity {
        val metadata = JSONObject().apply {
            put("format", format.toString())
            put("crawlerName", novel.crawlerName)
            put("startIndex", start)
            put("endIndex", end)
            if (!selectedSources.isNullOrEmpty()) {
                put("selectedSources", org.json.JSONArray(selectedSources.toList()))
            }
        }.toString()

        return BatchEntity(
            id = "${novel.url}_export_${format}_${start}_${end}_${System.nanoTime()}",
            type = JobType.ARTIFACT,
            novelUrl = novel.url,
            name = "Export: ${novel.title} ($format) [$start-$end]",
            metadata = metadata,
            status = JobStatus.PENDING
        )
    }
}
