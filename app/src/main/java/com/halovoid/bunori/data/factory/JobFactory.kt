package com.halovoid.bunori.data.factory

import com.halovoid.bunori.data.db.entities.BatchEntity
import com.halovoid.bunori.data.db.entities.JobStatus
import com.halovoid.bunori.data.db.entities.JobType
import com.halovoid.bunori.data.db.entities.TaskEntity
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.ui.feature.novel.components.artifact.ExportFormat
import org.json.JSONObject

class JobFactory {

    fun createMetadataBatch(novel: Novel): BatchEntity {
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
            priority = 10
        )
    }

    fun createMetadataTask(batchId: String, novel: Novel): TaskEntity {
        val metadata = JSONObject().apply {
            put("crawlerName", novel.crawlerName)
        }.toString()

        return TaskEntity(
            id = "${batchId}_task",
            batchId = batchId,
            name = "Metadata: ${novel.title}",
            url = novel.url,
            novelUrl = novel.url,
            type = JobType.NOVEL_METADATA,
            priority = 10,
            metadata = metadata,
            status = JobStatus.PENDING
        )
    }

    fun createMetadataBatchFromUrl(crawlerName: String, url: String, title: String): BatchEntity {
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

    fun createMetadataTaskFromUrl(batchId: String, crawlerName: String, url: String, title: String): TaskEntity {
        val metadata = JSONObject().apply {
            put("crawlerName", crawlerName)
        }.toString()

        return TaskEntity(
            id = "${batchId}_task",
            batchId = batchId,
            name = "Metadata: $title",
            url = url,
            novelUrl = url,
            type = JobType.NOVEL_METADATA,
            priority = 0,
            metadata = metadata,
            status = JobStatus.PENDING
        )
    }

    fun createRangeDownloadBatch(novel: Novel, start: Int, end: Int): BatchEntity {
        val metadata = JSONObject().apply {
            put("crawlerName", novel.crawlerName)
            put("startIndex", start)
            put("endIndex", end)
        }.toString()

        val rangeLabel = if (end == Int.MAX_VALUE) {
            if (start <= 1) "All Chapters" else "From $start"
        } else {
            "$start-$end"
        }

        return BatchEntity(
            id = "${novel.url}_download_${start}_${end}_${System.currentTimeMillis()}",
            type = JobType.RANGE_DOWNLOAD,
            novelUrl = novel.url,
            name = "Download: ${novel.title} ($rangeLabel)",
            metadata = metadata,
            status = JobStatus.PENDING
        )
    }

    fun createChapterBatch(novel: Novel, chapter: Chapter): BatchEntity {
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

    fun createChapterTasks(batchId: String, crawlerName: String, chapters: List<Chapter>, priority: Int = 0): List<TaskEntity> {
        return chapters.map { chapter ->
            val taskMetadata = JSONObject().apply {
                put("chapterId", chapter.id)
                put("crawlerName", crawlerName)
            }.toString()

            val effectiveUrl = chapter.sourceUrl?.takeIf { it.isNotBlank() } ?: chapter.url

            TaskEntity(
                id = "${batchId}_ch_${chapter.index}_${chapter.id}",
                batchId = batchId,
                name = chapter.title.ifBlank { "Chapter ${chapter.index}" },
                url = effectiveUrl,
                novelUrl = chapter.novelUrl,
                type = JobType.CHAPTER,
                priority = priority,
                metadata = taskMetadata,
                status = JobStatus.PENDING
            )
        }
    }

    private fun createExportMetadata(novel: Novel, format: ExportFormat, start: Int, end: Int, selectedSources: Set<String>?): String {
        return JSONObject().apply {
            put("format", format.toString())
            put("crawlerName", novel.crawlerName)
            put("startIndex", start)
            put("endIndex", end)
            if (!selectedSources.isNullOrEmpty()) {
                put("selectedSources", org.json.JSONArray(selectedSources.toList()))
            }
        }.toString()
    }

    fun createExportBatch(novel: Novel, format: ExportFormat, start: Int, end: Int, selectedSources: Set<String>? = null): BatchEntity {
        val metadata = createExportMetadata(novel, format, start, end, selectedSources)
        val rangeLabel = if (end == Int.MAX_VALUE) {
            if (start <= 1) "All Chapters" else "From $start"
        } else {
            "$start-$end"
        }

        return BatchEntity(
            id = "${novel.url}_export_${format}_${start}_${end}_${System.nanoTime()}",
            type = JobType.ARTIFACT,
            novelUrl = novel.url,
            name = "Export: ${novel.title} ($format) [$rangeLabel]",
            metadata = metadata,
            status = JobStatus.PENDING
        )
    }

    fun createExportTask(batchId: String, novel: Novel, format: ExportFormat, start: Int, end: Int, selectedSources: Set<String>? = null): TaskEntity {
        val metadata = createExportMetadata(novel, format, start, end, selectedSources)

        return TaskEntity(
            id = "${batchId}_task",
            batchId = batchId,
            name = "Export: ${novel.title} ($format)",
            url = null,
            novelUrl = novel.url,
            type = JobType.ARTIFACT,
            priority = 0,
            metadata = metadata,
            status = JobStatus.PENDING
        )
    }
}
