package com.halovoid.bunori.data.artifact.generators

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.net.toUri
import com.halovoid.bunori.api.core.network.NetworkClient
import com.halovoid.bunori.data.artifact.ArtifactGenerator
import com.halovoid.bunori.data.repository.DownloadRepository
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.StorageRepository
import com.halovoid.bunori.data.scheduler.JobMetadata
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Novel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream


private sealed class ContentBlock {
    data class Text(val layout: StaticLayout) : ContentBlock()
    data class Image(val bitmap: Bitmap) : ContentBlock()
}

private data class TocEntry(val label: String, var pageNumber: Int = 0)

/**
 * Handles page creation, margins, and vertical-cursor bookkeeping for a paginated PDF.
 *
 * Every draw* method works in two modes, controlled by [dryRun]:
 *  - dryRun = true: no [PdfDocument] page is created and nothing is drawn; only [pageNumber]
 *    and the vertical cursor advance. Used to measure where content will land before
 *    we know it (e.g. so the Table of Contents can show correct page numbers).
 *  - dryRun = false: identical logic, but also creates real pages and draws onto their canvas.
 *
 * Calling the same sequence of draw* calls with the same content in both modes is guaranteed
 * to produce the same pagination, since layout is purely a function of content + page geometry.
 *
 * Code for PDF generation was completely written by an LLM model I have not yet looked how it works,
 * but it works so I guess won't touch it until something glitches out
 */
private class PagedPdfWriter(
    private val document: PdfDocument?,
    private val pageWidth: Int,
    private val pageHeight: Int,
    private val marginX: Float,
    private val marginTop: Float,
    marginBottom: Float,
    val dryRun: Boolean,
    private val onPageStarted: ((Canvas, Int) -> Unit)? = null
) {
    var pageNumber = 0
        private set
    var cursorY = 0f
        private set

    private var currentPdfPage: PdfDocument.Page? = null
    var canvas: Canvas? = null
        private set

    val printableWidth: Float = pageWidth - marginX * 2
    val printableHeight: Float = pageHeight - marginTop - marginBottom

    fun startNewPage() {
        finishCurrentPage()
        pageNumber++
        cursorY = 0f
        if (!dryRun) {
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val pdfPage = document!!.startPage(info)
            currentPdfPage = pdfPage
            canvas = pdfPage.canvas
            onPageStarted?.invoke(pdfPage.canvas, pageNumber)
        }
    }

    fun finishCurrentPage() {
        if (!dryRun) {
            currentPdfPage?.let { document!!.finishPage(it) }
        }
        currentPdfPage = null
        canvas = null
    }

    private fun ensurePage() {
        if (pageNumber == 0) startNewPage()
    }

    fun availableHeight(): Float = printableHeight - cursorY

    fun addVerticalSpace(space: Float) {
        ensurePage()
        if (availableHeight() < space) startNewPage() else cursorY += space
    }

    fun drawHorizontalRule(paint: Paint, spacingAfter: Float = 0f) {
        ensurePage()
        if (availableHeight() < 1f) startNewPage()
        if (!dryRun) {
            val y = marginTop + cursorY
            canvas!!.drawLine(marginX, y, marginX + printableWidth, y, paint)
        }
        cursorY += 1f + spacingAfter
    }

    fun drawCenteredText(text: String, paint: TextPaint, topPadding: Float = 0f, bottomPadding: Float = 0f) {
        if (text.isBlank()) return
        ensurePage()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, printableWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        val height = layout.height.toFloat()
        if (availableHeight() < height + topPadding) startNewPage()
        cursorY += topPadding
        if (!dryRun) {
            canvas!!.save()
            canvas!!.translate(marginX, marginTop + cursorY)
            layout.draw(canvas!!)
            canvas!!.restore()
        }
        cursorY += height + bottomPadding
    }

    fun drawImageBlock(bitmap: Bitmap, spacingAfter: Float, maxHeightFraction: Float = 0.9f) {
        ensurePage()
        val aspect = bitmap.height.toFloat() / bitmap.width.toFloat()
        var drawWidth = printableWidth
        var drawHeight = drawWidth * aspect
        val maxHeight = printableHeight * maxHeightFraction
        if (drawHeight > maxHeight) {
            drawHeight = maxHeight
            drawWidth = drawHeight / aspect
        }
        if (cursorY > 0f && availableHeight() < drawHeight) startNewPage()
        if (!dryRun) {
            val xOffset = marginX + (printableWidth - drawWidth) / 2f
            val rect = RectF(xOffset, marginTop + cursorY, xOffset + drawWidth, marginTop + cursorY + drawHeight)
            canvas!!.drawBitmap(bitmap, null, rect, null)
        }
        cursorY += drawHeight + spacingAfter
    }

    fun drawTextBlockPaginated(layout: StaticLayout, spacingAfter: Float = 0f) {
        ensurePage()
        val lineCount = layout.lineCount
        var lineIdx = 0
        while (lineIdx < lineCount) {
            if (availableHeight() <= 2f) {
                startNewPage()
                continue
            }
            val availablePx = availableHeight()
            val segmentTop = layout.getLineTop(lineIdx)
            var endLine = lineIdx - 1
            for (i in lineIdx until lineCount) {
                val bottom = layout.getLineBottom(i)
                if ((bottom - segmentTop) > availablePx) break
                endLine = i
            }
            if (endLine < lineIdx) {
                if (cursorY <= 0.01f) {
                    endLine = lineIdx
                } else {
                    startNewPage()
                    continue
                }
            }
            val clipTop = segmentTop
            val clipBottom = layout.getLineBottom(endLine)
            if (!dryRun) {
                canvas!!.save()
                canvas!!.translate(marginX, marginTop + cursorY - clipTop)
                canvas!!.clipRect(0, clipTop, printableWidth.toInt(), clipBottom)
                layout.draw(canvas!!)
                canvas!!.restore()
            }
            cursorY += (clipBottom - clipTop)
            lineIdx = endLine + 1
        }
        cursorY += spacingAfter
    }

    fun drawTocEntry(title: String, pageLabel: String, titlePaint: TextPaint, numberPaint: TextPaint, rowHeight: Float) {
        ensurePage()
        if (availableHeight() < rowHeight) startNewPage()
        if (!dryRun) {
            val baselineY = marginTop + cursorY + rowHeight - 9f
            val numberWidth = if (pageLabel.isEmpty()) 0f else numberPaint.measureText(pageLabel)
            val maxTitleWidth = (printableWidth - numberWidth - 16f).coerceAtLeast(20f)
            val ellipsized = TextUtils.ellipsize(title, titlePaint, maxTitleWidth, TextUtils.TruncateAt.END)
            canvas!!.drawText(ellipsized.toString(), marginX, baselineY, titlePaint)
            if (pageLabel.isNotEmpty()) {
                canvas!!.drawText(pageLabel, marginX + printableWidth, baselineY, numberPaint)
            }
        }
        cursorY += rowHeight
    }
}

class PdfGenerator(
    private val storageRepository: StorageRepository,
    private val downloadRepository: DownloadRepository? = null,
    private val preferenceRepository: PreferenceRepository? = null
) : ArtifactGenerator {
    override val format: String = "PDF"

    private companion object {
        const val TAG = "PdfGenerator"
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN_X = 46f
        const val MARGIN_TOP = 58f
        const val MARGIN_BOTTOM = 56f
        const val PROJECT_NAME = "Bunori"
    }

    private val imgTagRegex = Regex("""<img[^>]*\ssrc\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)

    private val imageHttpClient: okhttp3.OkHttpClient by lazy {
        NetworkClient.okHttpClient.newBuilder()
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private suspend fun downloadBytes(src: String): ByteArray? {
        return try {
            if (src.startsWith("http://", ignoreCase = true) || src.startsWith("https://", ignoreCase = true)) {
                val request = okhttp3.Request.Builder().url(src).build()
                imageHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.bytes() else null
                }
            } else if (src.startsWith("content://", ignoreCase = true) || src.startsWith("file://", ignoreCase = true) || src.startsWith("/")) {
                storageRepository.openInputStream(src.toUri())?.use { it.readBytes() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledBitmap(bytes: ByteArray, reqWidthPx: Int): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
            var sampleSize = 1
            val halfWidth = boundsOptions.outWidth / 2
            while (halfWidth / sampleSize >= reqWidthPx) sampleSize *= 2
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun downloadBitmap(src: String, reqWidthPx: Int): Bitmap? {
        val bytes = downloadBytes(src) ?: return null
        val bitmap = decodeSampledBitmap(bytes, reqWidthPx)
        return bitmap
    }

    private suspend fun loadCoverBitmap(novel: Novel, reqWidthPx: Int): Bitmap? {
        novel.coverUrl?.takeIf { it.isNotBlank() }?.let { url ->
            try {
                storageRepository.openInputStream(url)?.use { input ->
                    val bytes = input.readBytes()
                    decodeSampledBitmap(bytes, reqWidthPx)?.let { return it }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        novel.coverHttpsUrl?.takeIf { it.isNotBlank() }?.let { url ->
            downloadBitmap(url, reqWidthPx)?.let { return it }
        }
        return null
    }

    private fun buildTextBlockOrNull(text: String, paint: TextPaint, widthPx: Int): ContentBlock.Text? {
        val clean = text.replace(Regex("<[^>]*>"), "").trim()
        if (clean.isBlank()) return null
        val layout = StaticLayout.Builder.obtain(clean, 0, clean.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1.25f)
            .build()
        return ContentBlock.Text(layout)
    }

    private suspend fun buildChapterBlocks(
        html: String,
        bodyPaint: TextPaint,
        printableWidthPx: Int,
        imageCache: MutableMap<String, Bitmap?>,
        ignoreImages: Boolean = false
    ): List<ContentBlock> {
        if (ignoreImages) {
            val cleanHtml = html.replace(imgTagRegex, "")
            val block = buildTextBlockOrNull(cleanHtml, bodyPaint, printableWidthPx)
            return if (block != null) listOf(block) else emptyList()
        }
        val blocks = mutableListOf<ContentBlock>()
        var lastIndex = 0
        for (match in imgTagRegex.findAll(html)) {
            val textSegment = html.substring(lastIndex, match.range.first)
            buildTextBlockOrNull(textSegment, bodyPaint, printableWidthPx)?.let { blocks.add(it) }

            val src = match.groupValues[1]
            val bitmap = if (imageCache.containsKey(src)) {
                imageCache[src]
            } else {
                downloadBitmap(src, printableWidthPx * 2).also { imageCache[src] = it }
            }
            if (bitmap != null) blocks.add(ContentBlock.Image(bitmap))

            lastIndex = match.range.last + 1
        }
        buildTextBlockOrNull(html.substring(lastIndex), bodyPaint, printableWidthPx)?.let { blocks.add(it) }
        return blocks
    }

    override suspend fun generate(
        novel: Novel,
        chapters: List<Chapter>,
        metadata: JobMetadata,
        onProgress: (suspend (current: Int, total: Int, stage: String) -> Unit)?
    ): File = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val ignoreImages = preferenceRepository?.ignoreImages?.first() ?: false

        val coverTitlePaint = TextPaint().apply { isAntiAlias = true; textSize = 26f; color = Color.BLACK; isFakeBoldText = true }
        val coverAuthorPaint = TextPaint().apply { isAntiAlias = true; textSize = 15f; color = Color.DKGRAY }
        val infoAuthorPaint = TextPaint().apply { isAntiAlias = true; textSize = 14f; color = Color.DKGRAY }
        val synopsisPaint = TextPaint().apply { isAntiAlias = true; textSize = 12f; color = Color.rgb(50, 50, 50) }
        val sourcePaint = TextPaint().apply { isAntiAlias = true; textSize = 10f; color = Color.GRAY }
        val creditPaint = TextPaint().apply { isAntiAlias = true; textSize = 10f; color = Color.LTGRAY; }
        val tocTitlePaint = TextPaint().apply { isAntiAlias = true; textSize = 21f; color = Color.BLACK; isFakeBoldText = true }
        val tocEntryPaint = TextPaint().apply { isAntiAlias = true; textSize = 12.5f; color = Color.rgb(40, 40, 40) }
        val tocPageNumPaint = TextPaint(tocEntryPaint).apply { color = Color.GRAY; textAlign = Paint.Align.RIGHT }
        val chapterHeadingPaint = TextPaint().apply { isAntiAlias = true; textSize = 18f; color = Color.BLACK; isFakeBoldText = true }
        val bodyPaint = TextPaint().apply { isAntiAlias = true; textSize = 11.5f; color = Color.rgb(25, 25, 25) }
        val footerPaint = TextPaint().apply { isAntiAlias = true; textSize = 9.5f; color = Color.GRAY; textAlign = Paint.Align.CENTER }
        val headerPaint = TextPaint().apply { isAntiAlias = true; textSize = 9f; color = Color.LTGRAY }
        val rulePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        val printableWidthPx = (PAGE_WIDTH - MARGIN_X * 2).toInt()

        val sortedChapters = chapters.sortedBy { it.index }
        val totalChapters = sortedChapters.size

        val imageCache = mutableMapOf<String, Bitmap?>()
        val chapterBlocks = mutableMapOf<Any, List<ContentBlock>>()
        val downloadsByUrl = downloadRepository?.getDownloadsForNovel(novel.url)?.associateBy { it.chapterUrl } ?: emptyMap()
        for ((index, chapter) in sortedChapters.withIndex()) {
            ensureActive()
            val displayTitle = chapter.title.ifBlank { "Chapter ${chapter.index}" }
            onProgress?.invoke(index + 1, totalChapters, displayTitle)

            val download = downloadsByUrl[chapter.url] ?: downloadRepository?.getDownload(chapter.novelUrl, chapter.url)
            val rawContent = download?.fileLocation?.let { loc -> storageRepository.readText(loc) }
                ?: "<p><em>Content not available</em></p>"
            chapterBlocks[chapter.id] = buildChapterBlocks(rawContent, bodyPaint, printableWidthPx, imageCache, ignoreImages)
        }
        val coverBitmap = if (!ignoreImages) loadCoverBitmap(novel, printableWidthPx * 2) else null

        val tocEntries = sortedChapters.map { chapter ->
            TocEntry(chapter.title.ifBlank { "Chapter ${chapter.index}" })
        }
        val tocRowHeight = tocEntryPaint.fontSpacing + 8f

        fun renderCoverPage(writer: PagedPdfWriter) {
            writer.startNewPage()
            if (coverBitmap != null) {
                writer.drawImageBlock(coverBitmap, spacingAfter = 22f, maxHeightFraction = 0.62f)
                writer.drawCenteredText(novel.title, coverTitlePaint, bottomPadding = 6f)
                novel.author?.let { writer.drawCenteredText(it, coverAuthorPaint) }
            } else {
                writer.addVerticalSpace(160f)
                writer.drawCenteredText(novel.title, coverTitlePaint, bottomPadding = 12f)
                writer.drawHorizontalRule(rulePaint, spacingAfter = 12f)
                novel.author?.let { writer.drawCenteredText(it, coverAuthorPaint) }
            }
        }

        fun renderInfoPage(writer: PagedPdfWriter) {
            writer.startNewPage()
            novel.author?.let { writer.drawCenteredText("by $it", infoAuthorPaint, bottomPadding = 22f) }
            novel.description?.let { desc ->
                val clean = desc.replace(Regex("<[^>]*>"), "").trim()
                if (clean.isNotBlank()) {
                    val layout = StaticLayout.Builder.obtain(clean, 0, clean.length, synopsisPaint, printableWidthPx)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(2f, 1.25f)
                        .build()
                    writer.drawTextBlockPaginated(layout, spacingAfter = 26f)
                }
            }
            writer.drawHorizontalRule(rulePaint, spacingAfter = 10f)
            writer.drawCenteredText("Source: ${novel.url}", sourcePaint, bottomPadding = 4f)
            writer.drawCenteredText("Generated by $PROJECT_NAME", creditPaint)
        }

        fun renderToc(writer: PagedPdfWriter, entries: List<TocEntry>) {
            writer.startNewPage()
            writer.drawCenteredText("Table of Contents", tocTitlePaint, bottomPadding = 20f)
            entries.forEach { entry ->
                writer.drawTocEntry(entry.label, entry.pageNumber.toString(), tocEntryPaint, tocPageNumPaint, tocRowHeight)
            }
        }

        fun renderChapter(writer: PagedPdfWriter, chapter: Chapter) {
            writer.startNewPage()
            val displayTitle = chapter.title.ifBlank { "Chapter ${chapter.index}" }
            writer.drawCenteredText(displayTitle, chapterHeadingPaint, bottomPadding = 20f)
            chapterBlocks[chapter.id]?.forEach { block ->
                when (block) {
                    is ContentBlock.Text -> writer.drawTextBlockPaginated(block.layout, spacingAfter = 10f)
                    is ContentBlock.Image -> writer.drawImageBlock(block.bitmap, spacingAfter = 10f)
                }
            }
        }

        onProgress?.invoke(totalChapters, totalChapters, "Calculating page layout...")
        val dryWriter = PagedPdfWriter(null, PAGE_WIDTH, PAGE_HEIGHT, MARGIN_X, MARGIN_TOP, MARGIN_BOTTOM, dryRun = true)
        renderCoverPage(dryWriter)
        renderInfoPage(dryWriter)
        renderToc(dryWriter, tocEntries) // placeholder numbers; only used to consume the right page count
        sortedChapters.forEachIndexed { index, chapter ->
            ensureActive()
            renderChapter(dryWriter, chapter)
            tocEntries[index].pageNumber = dryWriter.pageNumber
        }
        val totalPages = dryWriter.pageNumber
        dryWriter.finishCurrentPage()

        onProgress?.invoke(totalChapters, totalChapters, "Rendering $totalPages PDF pages...")
        val document = PdfDocument()
        val onPageStarted: (Canvas, Int) -> Unit = { canvas, pageNum ->
            if (pageNum > 1) {
                canvas.drawText(novel.title, MARGIN_X, MARGIN_TOP - 22f, headerPaint)
                val ruleY = PAGE_HEIGHT - MARGIN_BOTTOM + 14f
                canvas.drawLine(MARGIN_X, ruleY, PAGE_WIDTH - MARGIN_X, ruleY, rulePaint)
                canvas.drawText("$pageNum / $totalPages", PAGE_WIDTH / 2f, ruleY + 18f, footerPaint)
            }
        }
        val writer = PagedPdfWriter(document, PAGE_WIDTH, PAGE_HEIGHT, MARGIN_X, MARGIN_TOP, MARGIN_BOTTOM, dryRun = false, onPageStarted = onPageStarted)

        renderCoverPage(writer)
        renderInfoPage(writer)
        renderToc(writer, tocEntries)
        sortedChapters.forEach { chapter ->
            ensureActive()
            renderChapter(writer, chapter)
        }
        writer.finishCurrentPage()

        val tempFile = File(storageRepository.getCacheDir(), "${novel.title.replace(" ", "_")}.pdf")
        FileOutputStream(tempFile).use { out -> document.writeTo(out) }
        document.close()

        tempFile
    }
}