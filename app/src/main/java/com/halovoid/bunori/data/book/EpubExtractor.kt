package com.halovoid.bunori.data.book

import android.util.Log
import com.halovoid.bunori.extension.api.models.ChapterDto
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class EpubBookMetadata(
    val title: String? = null,
    val author: String? = null,
    val description: String? = null,
    val coverBytes: ByteArray? = null,
    val coverMimeType: String? = null
)

object EpubExtractor {
    private const val TAG = "EpubExtractor"

    /**
     * Checks if the given file is a valid, readable EPUB archive.
     */
    fun isEpub(file: File): Boolean {
        if (!file.exists() || file.length() < 100) return false
        return try {
            ZipFile(file).use { zip ->
                zip.getEntry("META-INF/container.xml") != null || zip.size() > 0
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extracts the sequential list of chapters from an EPUB file.
     * Uses the EPUB Table of Contents (toc.ncx or nav.xhtml), falling back
     * to the OPF spine reading order.
     */
    fun extractChapters(epubFile: File, novelUrl: String): List<ChapterDto> {
        val chapters = mutableListOf<ChapterDto>()
        try {
            ZipFile(epubFile).use { zip ->
                val opfPath = getOpfPath(zip) ?: return emptyList()
                val opfDir = opfPath.substringBeforeLast('/', "")
                val opfDoc = parseXml(zip.getInputStream(zip.getEntry(opfPath)))

                // 1. Build manifest: id -> href
                val manifestItems = mutableMapOf<String, String>()
                val itemNodes = opfDoc.getElementsByTagName("item")
                for (i in 0 until itemNodes.length) {
                    val el = itemNodes.item(i) as? Element ?: continue
                    val id = el.getAttribute("id")
                    val href = el.getAttribute("href")
                    if (id.isNotBlank() && href.isNotBlank()) {
                        manifestItems[id] = resolveZipPath(opfDir, href)
                    }
                }

                // 2. Try parsing toc.ncx if referenced
                var ncxHref: String? = null
                val spineNode = opfDoc.getElementsByTagName("spine").item(0) as? Element
                val tocId = spineNode?.getAttribute("toc")
                if (!tocId.isNullOrBlank()) {
                    ncxHref = manifestItems[tocId]
                }
                if (ncxHref == null) {
                    ncxHref = manifestItems.values.firstOrNull { it.endsWith("toc.ncx", ignoreCase = true) }
                }

                if (ncxHref != null && zip.getEntry(ncxHref) != null) {
                    val ncxDir = ncxHref.substringBeforeLast('/', "")
                    val ncxDoc = parseXml(zip.getInputStream(zip.getEntry(ncxHref)))
                    val navPoints = ncxDoc.getElementsByTagName("navPoint")
                    for (i in 0 until navPoints.length) {
                        val np = navPoints.item(i) as? Element ?: continue
                        val textNode = np.getElementsByTagName("text").item(0)
                        val title = textNode?.textContent?.trim() ?: "Chapter ${i + 1}"
                        val contentEl = np.getElementsByTagName("content").item(0) as? Element
                        val rawSrc = contentEl?.getAttribute("src") ?: continue
                        val cleanSrc = rawSrc.substringBefore('#')
                        val resolvedEntryPath = resolveZipPath(ncxDir, cleanSrc)

                        chapters.add(
                            ChapterDto(
                                url = "epub://${novelUrl.trimEnd('/')}/$resolvedEntryPath",
                                title = title,
                                index = i + 1
                            )
                        )
                    }
                }

                // 3. Fallback to spine reading order if TOC was empty
                if (chapters.isEmpty() && spineNode != null) {
                    val itemrefs = spineNode.getElementsByTagName("itemref")
                    var idx = 1
                    for (i in 0 until itemrefs.length) {
                        val ir = itemrefs.item(i) as? Element ?: continue
                        val idref = ir.getAttribute("idref")
                        val path = manifestItems[idref] ?: continue
                        if (path.endsWith(".xhtml", ignoreCase = true) || path.endsWith(".html", ignoreCase = true)) {
                            chapters.add(
                                ChapterDto(
                                    url = "epub://${novelUrl.trimEnd('/')}/$path",
                                    title = "Section $idx",
                                    index = idx
                                )
                            )
                            idx++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract chapters from ${epubFile.name}", e)
        }
        return chapters
    }

    /**
     * Extracts and renders the chapter HTML for the given entry path within the EPUB.
     * Automatically resolves and inlines embedded images as Base64 data URIs so
     * images render self-contained in ReaderWebView.
     */
    fun extractChapterHtml(epubFile: File, entryPath: String): String {
        return try {
            ZipFile(epubFile).use { zip ->
                val cleanPath = entryPath.removePrefix("epub://")
                var entry: ZipEntry? = zip.getEntry(cleanPath)
                if (entry == null && cleanPath.contains('/')) {
                    val stripped = cleanPath.substringAfter('/')
                    entry = zip.getEntry(stripped)
                        ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(stripped.substringAfterLast('/')) }
                }
                if (entry == null) {
                    entry = zip.entries().asSequence().firstOrNull { it.name.endsWith(cleanPath.substringAfterLast('/')) }
                }

                if (entry == null || cleanPath == "book" || cleanPath.endsWith("/book")) {
                    val opfPath = getOpfPath(zip)
                    if (opfPath != null) {
                        val opfDoc = parseXml(zip.getInputStream(zip.getEntry(opfPath)))
                        val spineNode = opfDoc.getElementsByTagName("spine").item(0) as? Element
                        val itemrefs = spineNode?.getElementsByTagName("itemref")
                        val firstItem = itemrefs?.item(0) as? Element
                        val idref = firstItem?.getAttribute("idref")
                        val itemNodes = opfDoc.getElementsByTagName("item")
                        var foundHref: String? = null
                        for (i in 0 until itemNodes.length) {
                            val el = itemNodes.item(i) as? Element ?: continue
                            if (el.getAttribute("id") == idref) {
                                foundHref = el.getAttribute("href")
                                break
                            }
                        }
                        if (foundHref != null) {
                            val opfDir = opfPath.substringBeforeLast('/', "")
                            entry = zip.getEntry(resolveZipPath(opfDir, foundHref))
                        }
                    }
                }

                if (entry == null) {
                    return "<html><body><p>Chapter content not found in EPUB: $cleanPath</p></body></html>"
                }

                val entryDir = entry.name.substringBeforeLast('/', "")
                var rawHtml = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }

                // Inline images: find src="..." or href="..."
                val imgRegex = Regex("""(<(?:img|image)[^>]+(?:src|xlink:href)=["'])([^"']+)(["'][^>]*>)""", RegexOption.IGNORE_CASE)
                rawHtml = imgRegex.replace(rawHtml) { match ->
                    val prefix = match.groupValues[1]
                    val imgSrc = match.groupValues[2]
                    val suffix = match.groupValues[3]

                    if (imgSrc.startsWith("data:") || imgSrc.startsWith("http://") || imgSrc.startsWith("https://")) {
                        match.value
                    } else {
                        val imgEntryPath = resolveZipPath(entryDir, imgSrc.substringBefore('#'))
                        val imgEntry = zip.getEntry(imgEntryPath)
                        if (imgEntry != null) {
                            val imgBytes = zip.getInputStream(imgEntry).use { it.readBytes() }
                            val mime = getMimeType(imgEntryPath)
                            val b64 = java.util.Base64.getEncoder().encodeToString(imgBytes)
                            val dataUri = "data:$mime;base64,$b64"
                            "$prefix$dataUri$suffix"
                        } else {
                            match.value
                        }
                    }
                }

                rawHtml
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read chapter HTML for $entryPath in ${epubFile.name}", e)
            "<html><body><p>Error loading chapter: ${e.message}</p></body></html>"
        }
    }

    /**
     * Extracts metadata (title, author, description, and cover image) from the EPUB.
     */
    fun extractMetadata(epubFile: File): EpubBookMetadata {
        return try {
            ZipFile(epubFile).use { zip ->
                val opfPath = getOpfPath(zip) ?: return EpubBookMetadata()
                val opfDir = opfPath.substringBeforeLast('/', "")
                val opfDoc = parseXml(zip.getInputStream(zip.getEntry(opfPath)))

                val title = opfDoc.getElementsByTagName("dc:title").item(0)?.textContent?.trim()
                val author = opfDoc.getElementsByTagName("dc:creator").item(0)?.textContent?.trim()
                val desc = opfDoc.getElementsByTagName("dc:description").item(0)?.textContent?.trim()

                // Cover extraction: look for meta name="cover" or item properties="cover-image"
                var coverHref: String? = null
                val metaNodes = opfDoc.getElementsByTagName("meta")
                for (i in 0 until metaNodes.length) {
                    val m = metaNodes.item(i) as? Element ?: continue
                    if (m.getAttribute("name").equals("cover", ignoreCase = true)) {
                        val coverId = m.getAttribute("content")
                        val itemNodes = opfDoc.getElementsByTagName("item")
                        for (j in 0 until itemNodes.length) {
                            val itm = itemNodes.item(j) as? Element ?: continue
                            if (itm.getAttribute("id") == coverId) {
                                coverHref = itm.getAttribute("href")
                                break
                            }
                        }
                        break
                    }
                }

                if (coverHref == null) {
                    val itemNodes = opfDoc.getElementsByTagName("item")
                    for (i in 0 until itemNodes.length) {
                        val itm = itemNodes.item(i) as? Element ?: continue
                        val props = itm.getAttribute("properties")
                        if (props.contains("cover-image", ignoreCase = true)) {
                            coverHref = itm.getAttribute("href")
                            break
                        }
                    }
                }

                var coverBytes: ByteArray? = null
                var coverMime: String? = null
                if (coverHref != null) {
                    val coverZipPath = resolveZipPath(opfDir, coverHref)
                    val coverEntry = zip.getEntry(coverZipPath)
                    if (coverEntry != null) {
                        coverBytes = zip.getInputStream(coverEntry).use { it.readBytes() }
                        coverMime = getMimeType(coverZipPath)
                    }
                }

                EpubBookMetadata(
                    title = title,
                    author = author,
                    description = desc,
                    coverBytes = coverBytes,
                    coverMimeType = coverMime
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract metadata from ${epubFile.name}: ${e.message}")
            EpubBookMetadata()
        }
    }

    private fun getOpfPath(zip: ZipFile): String? {
        val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
        val doc = parseXml(zip.getInputStream(containerEntry))
        val rootfiles = doc.getElementsByTagName("rootfile")
        if (rootfiles.length > 0) {
            val rootfile = rootfiles.item(0) as? Element
            return rootfile?.getAttribute("full-path")
        }
        return null
    }

    private fun parseXml(inputStream: InputStream): org.w3c.dom.Document {
        return inputStream.use { stream ->
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isValidating = false
                try {
                    setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                } catch (_: Exception) {}
            }
            factory.newDocumentBuilder().parse(stream)
        }
    }

    private fun resolveZipPath(baseDir: String, relativePath: String): String {
        val cleanRel = relativePath.removePrefix("/")
        if (baseDir.isBlank()) return cleanRel
        return try {
            val baseUri = URI(if (baseDir.endsWith('/')) baseDir else "$baseDir/")
            baseUri.resolve(cleanRel).path.removePrefix("/")
        } catch (_: Exception) {
            if (baseDir.endsWith('/')) "$baseDir$cleanRel" else "$baseDir/$cleanRel"
        }
    }

    private fun getMimeType(path: String): String {
        return when (path.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }
}
