package com.halovoid.bunori.data.artifact.generators

import com.halovoid.bunori.data.artifact.ArtifactGenerator
import com.halovoid.bunori.data.repository.StorageRepository
import com.halovoid.bunori.data.scheduler.JobMetadata
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Novel
import androidx.core.net.toUri
import com.halovoid.bunori.data.repository.DownloadRepository
import com.halovoid.bunori.data.repository.PreferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

private data class EpubItem(
    val fileName: String,
    val content: ByteArray,
    val mediaType: String,
    val id: String,
    val title: String = id.replace("_", " ")
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EpubItem

        if (fileName != other.fileName) return false
        if (!content.contentEquals(other.content)) return false
        if (mediaType != other.mediaType) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + mediaType.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }
}

class EpubGenerator(
    private val storageRepository: StorageRepository,
    private val downloadRepository: DownloadRepository? = null,
    private val preferenceRepository: PreferenceRepository? = null
) : ArtifactGenerator {
    override val format: String = "EPUB"

    // Constants and CSS for EPUB
    private companion object {
        const val styleFileName = "style.css"
        const val projectUrl = "https://github.com/bunoriapp/bunori"

        val epubStyleCSS = """
            |body { font-family: sans-serif; padding: 1em; line-height: 1.5; }
            |h1 { text-align: center; margin-top: 2em; }
            |h3 { text-align: center; font-weight: normal; }
            |.synopsis { margin-top: 2em; font-style: italic; }
            |.footer { margin-top: 4em; font-size: 0.8em; border-top: 1px solid #ccc; padding-top: 1em; }
            |#chapter h4 { opacity: 0.75; margin-bottom: 0; }
            |img { max-width: 100%; height: auto; }
        """.trimMargin()
    }

    // Matches <img ... src="..." ...> tags (case-insensitive, single or double quotes)
    private val imgTagRegex = Regex("""<img[^>]*\ssrc\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)

    private fun extensionForUrl(url: String): String {
        val lower = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            lower.endsWith(".png") -> "png"
            lower.endsWith(".webp") -> "webp"
            lower.endsWith(".gif") -> "gif"
            lower.endsWith(".jpeg") -> "jpg"
            else -> "jpg"
        }
    }

    private fun mediaTypeForExtension(ext: String): String = when (ext) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }

    // Downloads image bytes from either a remote URL or a local content/file URI.
    private suspend fun downloadImageBytes(src: String): ByteArray? {
        return try {
            if (src.startsWith("http://", ignoreCase = true) || src.startsWith("https://", ignoreCase = true)) {
                val request = okhttp3.Request.Builder().url(src).build()
                com.halovoid.bunori.api.core.network.NetworkClient.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.bytes() else null
                }
            } else {
                storageRepository.openInputStream(src.toUri())?.use { it.readBytes() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Finds every <img src="..."> in a chapter's HTML, downloads any image not already
    // embedded, adds it to the epub package via addItem, and rewrites the src to point
    // at the local packaged file so the image works fully offline.
    private suspend fun embedChapterImages(
        chapterId: String,
        html: String,
        imageCache: MutableMap<String, String>,
        addItem: (EpubItem) -> Unit,
        ignoreImages: Boolean = false
    ): String {
        if (ignoreImages) {
            return html.replace(imgTagRegex, "")
        }
        val sources = imgTagRegex.findAll(html).map { it.groupValues[1] }.distinct().toList()
        if (sources.isEmpty()) return html

        for (src in sources) {
            if (imageCache.containsKey(src)) continue
            val bytes = downloadImageBytes(src) ?: continue
            val ext = extensionForUrl(src)
            val fileName = "img_${chapterId}_${imageCache.size}.$ext"
            imageCache[src] = fileName
            addItem(EpubItem("images/$fileName", bytes, mediaTypeForExtension(ext), "image_${fileName.substringBefore(".")}"))
        }

        var result = html
        for (src in sources) {
            val fileName = imageCache[src] ?: continue
            result = result.replace(src, "images/$fileName")
        }
        return result
    }

    // XHTML Wrapper
    private fun wrapXHTML(title: String, body: String): String {
        return """
            |<?xml version="1.0" encoding="utf-8"?>
            |<!DOCTYPE html>
            |<html xmlns="http://www.w3.org/1999/xhtml" lang="en">
            |<head>
            |    <meta charset="UTF-8" />
            |    <title>$title</title>
            |    <link rel="stylesheet" href="$styleFileName" type="text/css"/>
            |</head>
            |<body>
            |    $body
            |</body>
            |</html>
        """.trimMargin()
    }

    private fun buildCoverPage(imageFileName: String): String {
        val content = """
            |<div id="cover" style="text-align: center; margin: 0; padding: 0;">
            |    <img src="$imageFileName" alt="Cover Image" style="max-width: 100%; height: auto;" />
            |</div>
        """.trimMargin()
        return wrapXHTML("Cover", content)
    }

    // Page Templates
    private fun buildIntroPage(novel: Novel): String {
        val content = """
            |<div id="intro">
            |    <h1>${novel.title}</h1>
            |    <h3>${novel.author ?: "Unknown Author"}</h3>
            |    <div class="synopsis">
            |        ${novel.description ?: ""}
            |    </div>
            |    <div class="footer">
            |        <b>Source:</b> <a href="${novel.url}">${novel.url}</a>
            |        <br/>
            |        <i>Generated by <b>
            |        <a href="$projectUrl">Bunori</a></b></i>
            |    </div>
            |</div>
        """.trimMargin()
        return  wrapXHTML("Intro Page", content)
    }

    private fun buildChapterPage(chapter: Chapter, htmlContent: String): String {
        val displayTitle = chapter.title.ifBlank { "Chapter ${chapter.index}" }
        val content = """
            |<div id="chapter">
            |    <h4 aria-hidden="true">${chapter.index}</h4>
            |    <h1>${displayTitle}</h1>
            |    $htmlContent
            |</div>
        """.trimMargin()
        return wrapXHTML(displayTitle, content)
    }

    // Content to be written inside content.opf
    private fun generateContainerXML(): String {
        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
            |    <rootfiles>
            |        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
            |    </rootfiles>
            |</container>
        """.trimMargin()
    }

    private fun generateOpf(novel: Novel, items: List<EpubItem>): String {
        val manifest = items.joinToString("\n") {
            val navAttr = if (it.fileName == "nav.xhtml") " properties=\"nav\"" else ""
            val coverAttr = if (it.id == "cover-image") "properties=\"cover-image\"" else ""
            "|      <item id=\"${it.id}\" href=\"${it.fileName}\" media-type=\"${it.mediaType}\" $navAttr$coverAttr/>"
        }

        // Determined reading order
        val spine = items.filter { it.mediaType == "application/xhtml+xml" }
            .joinToString("\n") { "|        <itemref idref=\"${it.id}\"/>" }

        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="pub-id" version="3.0">
            |    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            |        <dc:identifier id="pub-id">${novel.url.hashCode()}</dc:identifier>
            |        <dc:title>${novel.title}</dc:title>
            |        <dc:creator>${novel.author ?: "Unknown"}</dc:creator>
            |        <dc:language>en</dc:language>
            |        <dc:description>${novel.description ?: ""}</dc:description>
            |        <meta property="dcterms:modified">${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())}</meta>
            |    </metadata>
            |    <manifest>
            |        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
            $manifest
            |    </manifest>
            |    <spine toc="ncx">
            $spine
            |    </spine>
            |</package>
        """.trimMargin()
    }

    private fun generateNcx(novel: Novel, items: List<EpubItem>): String {
        val navPoints = items.filter { it.mediaType == "application/xhtml+xml" }
            .mapIndexed { index, item ->
                """
                |<navPoint id="navPoint-${index + 1}" playOrder="${index + 1}">
                |    <navLabel><text>${item.title}</text></navLabel>
                |    <content src="${item.fileName}"/>
                |</navPoint>
            """.trimMargin()
            }.joinToString("\n")

        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
            |    <head>
            |        <meta name="dtb:uid" content="pub-${abs(novel.url.hashCode())}"/>
            |        <meta name="dtb:depth" content="1"/>
            |        <meta name="dtb:totalPageCount" content="0"/>
            |        <meta name="dtb:maxPageNumber" content="0"/>
            |    </head>
            |    <docTitle><text>${novel.title}</text></docTitle>
            |    <navMap>
            |        $navPoints
            |    </navMap>
            |</ncx>
        """.trimMargin()
    }

    private fun generateNav(novel: Novel, items: List<EpubItem>): String {
        val listItems = items.filter { it.mediaType == "application/xhtml+xml" }
            .joinToString("\n") {
                """<li><a href="${it.fileName}">${it.title}</a></li>"""
            }
        val content = """
            |<nav xmlns:epub="http://www.idpf.org/2007/ops" epub:type="toc" id="toc">
            |    <h1>Table of Contents</h1>
            |    <ol>
            |        $listItems
            |    </ol>
            |</nav>
        """.trimMargin()
        return wrapXHTML("Table of Contents", content)
    }


    override suspend fun generate(
        novel: Novel,
        chapters: List<Chapter>,
        metadata: JobMetadata
    ): File = withContext(Dispatchers.IO) {
        val items = mutableListOf<EpubItem>()
        val addedFileNames = mutableSetOf<String>()
        // Maps a chapter image's original src -> the local filename it was packaged as,
        // so the same remote image referenced twice isn't downloaded/embedded twice.
        val chapterImageCache = mutableMapOf<String, String>()

        fun addItem(item: EpubItem) {
            if (addedFileNames.add(item.fileName)) {
                items.add(item)
            }
        }

        val ignoreImages = preferenceRepository?.ignoreImages?.first() ?: false

// 0. Add Cover Image and Page
        if (!ignoreImages) {
            val coverUrl = novel.coverUrl
            val coverHttpsUrl = novel.coverHttpsUrl
            var coverBytes: ByteArray? = null
            var resolvedUrl: String? = null

            // Try reading from local coverUrl first
            if (!coverUrl.isNullOrBlank()) {
                try {
                    storageRepository.openInputStream(coverUrl)?.use { input ->
                        coverBytes = input.readBytes()
                        resolvedUrl = coverUrl
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // If local cover failed/missing, fall back to coverHttpsUrl
            if (coverBytes == null && !coverHttpsUrl.isNullOrBlank()) {
                try {
                    val request = okhttp3.Request.Builder().url(coverHttpsUrl).build()
                    com.halovoid.bunori.api.core.network.NetworkClient.okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            coverBytes = response.body?.bytes()
                            resolvedUrl = coverHttpsUrl
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            coverBytes?.let { bytes ->
                val extension = if (resolvedUrl?.contains(".png", ignoreCase = true) == true) "png" else "jpg"
                val imageFileName = "cover.$extension"
                val mediaType = if (extension == "png") "image/png" else "image/jpeg"

                addItem(EpubItem(imageFileName, bytes, mediaType, "cover-image"))
                addItem(EpubItem("cover.xhtml", buildCoverPage(imageFileName).toByteArray(), "application/xhtml+xml", "cover"))
            }
        }

        // 1. Add static assets
        addItem(EpubItem(styleFileName, epubStyleCSS.toByteArray(), "text/css", "style"))
        addItem(EpubItem("intro.xhtml", buildIntroPage(novel).toByteArray(), "application/xhtml+xml", "intro"))

        // 2. Build Chapters
        chapters.sortedBy { it.index }.forEach { chapter ->
            ensureActive()
            val download = downloadRepository?.getDownload(chapter.novelUrl, chapter.url)
            val rawContent = download?.fileLocation?.let { loc ->
                storageRepository.readText(loc)
            } ?: "<p><em>Content not available</em></p>"
            val content = embedChapterImages(chapter.id.toString(), rawContent, chapterImageCache, ::addItem, ignoreImages)

            val displayTitle = chapter.title.ifBlank { "Chapter ${chapter.index}" }
            addItem(EpubItem(
                "chapter_${chapter.id}_${chapter.index.toString().padStart(5, '0')}.xhtml",
                buildChapterPage(chapter, content).toByteArray(),
                "application/xhtml+xml",
                "chapter_${chapter.id}_${chapter.index}",
                displayTitle
            ))
        }

        // 3. Generate nav item
        val navItem = EpubItem(
            fileName = "nav.xhtml",
            content = generateNav(novel, items).toByteArray(),
            mediaType = "application/xhtml+xml",
            id = "nav"
        )
        // nav.xhtml is added to orderedItems specifically, but it should also be in the items list for OPF/NCX generation
        // But we must be careful not to add it twice to the zip.
        // Actually, let's just make a clean ordered list.

        val orderedItems = mutableListOf<EpubItem>()
        items.find { it.id == "style" }?.let { orderedItems.add(it) }
        items.find { it.id == "cover-image" }?.let { orderedItems.add(it) }
        items.find { it.id == "cover" }?.let { orderedItems.add(it) }
        items.find { it.id == "intro" }?.let { orderedItems.add(it) }
        orderedItems.add(navItem)
        items.filter { it.id.startsWith("chapter_") || it.id.startsWith("image_") }
            .forEach { orderedItems.add(it) }

        // Final items for OPF and NCX should be the ordered ones
        val opf = generateOpf(novel, orderedItems)
        val ncx = generateNcx(novel, orderedItems)

        // Package into ZIP File
        val tempFile = File(storageRepository.getCacheDir(), "${novel.title.replace(" ", "_")}.epub")
        ZipOutputStream(FileOutputStream(tempFile)).use { zip ->

            val mimeBytes = "application/epub+zip".toByteArray()
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                crc = CRC32().apply { update(mimeBytes) }.value
            }
            zip.putNextEntry(mimeEntry)
            zip.write(mimeBytes)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(generateContainerXML().toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(opf.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zip.write(ncx.toByteArray())
            zip.closeEntry()

            orderedItems.forEach { item ->
                zip.putNextEntry(ZipEntry("OEBPS/${item.fileName}"))
                zip.write(item.content)
                zip.closeEntry()
            }
        }

        tempFile
    }
}