package com.halovoid.bunori.data.book

import android.content.Context
import android.util.Log
import com.halovoid.bunori.extension.api.models.ChapterDto
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object EpubBookManager {
    private const val TAG = "EpubBookManager"
    private const val BOOKS_DIR = "books"

    private val mirrorsCache = ConcurrentHashMap<String, List<String>>()

    fun registerMirrors(bookId: String, mirrors: List<String>) {
        val valid = mirrors.filter { url ->
            val u = url.lowercase()
            !u.contains("/search?") && !u.contains("ipfs_cid:") && (u.startsWith("http://") || u.startsWith("https://"))
        }
        if (valid.isNotEmpty()) {
            mirrorsCache[bookId] = valid
            Log.d(TAG, "Registered ${valid.size} download mirrors for bookId $bookId")
        }
    }

    fun getMirrors(bookId: String): List<String> = mirrorsCache[bookId].orEmpty()

    fun getBookFile(context: Context, bookId: String): File {
        val safeId = bookId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dir = File(context.filesDir, BOOKS_DIR).apply { if (!exists()) mkdirs() }
        return File(dir, "$safeId.epub")
    }

    fun hasBook(context: Context, bookId: String): Boolean {
        val file = getBookFile(context, bookId)
        return file.exists() && EpubExtractor.isEpub(file)
    }

    suspend fun ensureBookDownloaded(
        context: Context,
        bookId: String,
        mirrors: List<String>? = null,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean {
        val file = getBookFile(context, bookId)
        if (file.exists() && EpubExtractor.isEpub(file)) return true

        val candidateMirrors = (mirrors?.takeIf { it.isNotEmpty() } ?: getMirrors(bookId))
            .filter { url ->
                val u = url.lowercase()
                !u.contains("/search?") && !u.contains("ipfs_cid:") && (u.startsWith("http://") || u.startsWith("https://"))
            }

        if (candidateMirrors.isEmpty()) {
            Log.w(TAG, "No valid download mirrors available for bookId $bookId")
            return false
        }

        Log.i(TAG, "Downloading EPUB for $bookId using ${candidateMirrors.size} mirrors...")
        return ResilientDownloader.downloadWithMirrors(candidateMirrors, file, onProgress)
    }

    fun getChapters(context: Context, bookId: String, novelUrl: String): List<ChapterDto> {
        val file = getBookFile(context, bookId)
        if (!file.exists()) {
            Log.w(TAG, "Cannot extract chapters: EPUB not found on disk for bookId $bookId")
            return emptyList()
        }
        return EpubExtractor.extractChapters(file, bookId)
    }

    suspend fun getChapterContent(context: Context, bookId: String, entryPath: String): String? {
        val file = getBookFile(context, bookId)
        if (!file.exists() || !EpubExtractor.isEpub(file)) {
            val success = ensureBookDownloaded(context, bookId)
            if (!success || !file.exists()) {
                Log.w(TAG, "Cannot load chapter content: EPUB download failed for bookId $bookId")
                return "<html><body><h3>Download Failed</h3><p>Could not download book from available mirrors. Please check your network connection or try another source.</p></body></html>"
            }
        }
        return EpubExtractor.extractChapterHtml(file, entryPath)
    }

    fun deleteBook(context: Context, bookId: String): Boolean {
        val file = getBookFile(context, bookId)
        return file.delete()
    }
}
