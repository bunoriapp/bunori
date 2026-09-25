package com.halovoid.bunori

import com.halovoid.bunori.data.book.EpubExtractor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `isEpub returns false for invalid file`() {
        val nonEpub = tempFolder.newFile("test.txt").apply {
            writeText("Just plain text file, not a zip archive.")
        }
        assertFalse(EpubExtractor.isEpub(nonEpub))
    }

    @Test
    fun `isEpub and extractChapters work on synthetic EPUB`() {
        val epubFile = createSyntheticEpub()
        assertTrue(EpubExtractor.isEpub(epubFile))

        val chapters = EpubExtractor.extractChapters(epubFile, "annas_archive/test_book")
        assertEquals(2, chapters.size)
        assertEquals("Chapter 1: The Beginning", chapters[0].title)
        assertEquals(1, chapters[0].index)
        assertEquals("Chapter 2: The Journey", chapters[1].title)
        assertEquals(2, chapters[1].index)

        // Test extracting HTML
        val html = EpubExtractor.extractChapterHtml(epubFile, chapters[0].url)
        assertTrue(html.contains("Hello world from chapter 1"))
        assertTrue(html.contains("data:image/png;base64,")) // Image was inlined as Base64

        // Test metadata extraction
        val metadata = EpubExtractor.extractMetadata(epubFile)
        assertEquals("Test Fantasy Novel", metadata.title)
        assertEquals("Author Person", metadata.author)
    }

    private fun createSyntheticEpub(): File {
        val file = tempFolder.newFile("book.epub")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            // 1. mimetype
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            // 2. container.xml
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("""
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent().toByteArray())
            zip.closeEntry()

            // 3. content.opf
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write("""
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Fantasy Novel</dc:title>
                    <dc:creator>Author Person</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="c1" href="chap1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="chap2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="img1" href="images/pic.png" media-type="image/png"/>
                  </manifest>
                  <spine toc="ncx">
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
            """.trimIndent().toByteArray())
            zip.closeEntry()

            // 4. toc.ncx
            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zip.write("""
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                  <navMap>
                    <navPoint id="p1" playOrder="1">
                      <navLabel><text>Chapter 1: The Beginning</text></navLabel>
                      <content src="chap1.xhtml"/>
                    </navPoint>
                    <navPoint id="p2" playOrder="2">
                      <navLabel><text>Chapter 2: The Journey</text></navLabel>
                      <content src="chap2.xhtml"/>
                    </navPoint>
                  </navMap>
                </ncx>
            """.trimIndent().toByteArray())
            zip.closeEntry()

            // 5. chap1.xhtml with an embedded image
            zip.putNextEntry(ZipEntry("OEBPS/chap1.xhtml"))
            zip.write("""
                <?xml version="1.0" encoding="utf-8"?>
                <html>
                  <body>
                    <h1>Chapter 1</h1>
                    <p>Hello world from chapter 1.</p>
                    <img src="images/pic.png" alt="Test image"/>
                  </body>
                </html>
            """.trimIndent().toByteArray())
            zip.closeEntry()

            // 6. chap2.xhtml
            zip.putNextEntry(ZipEntry("OEBPS/chap2.xhtml"))
            zip.write("""
                <?xml version="1.0" encoding="utf-8"?>
                <html>
                  <body>
                    <h1>Chapter 2</h1>
                    <p>Hello world from chapter 2.</p>
                  </body>
                </html>
            """.trimIndent().toByteArray())
            zip.closeEntry()

            // 7. fake image
            zip.putNextEntry(ZipEntry("OEBPS/images/pic.png"))
            zip.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            zip.closeEntry()
        }
        return file
    }
}
