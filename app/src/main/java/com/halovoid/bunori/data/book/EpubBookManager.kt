package com.halovoid.bunori.data.book

import android.content.Context
import android.util.Log
import com.halovoid.bunori.extension.api.models.ChapterDto
import java.io.File

object EpubBookManager {
    private const val TAG = "EpubBookManager"
    private const val BOOKS_DIR = "books"

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
        mirrors: List<String>,
        onProgress: ((Long, Long) -> Unit)? = null
    ): Boolean {
        val file = getBookFile(context, bookId)
        if (file.exists() && EpubExtractor.isEpub(file)) return true
        return ResilientDownloader.downloadWithMirrors(mirrors, file, onProgress)
    }

    fun getChapters(context: Context, bookId: String, novelUrl: String): List<ChapterDto> {
        val file = getBookFile(context, bookId)
        if (!file.exists()) {
            Log.w(TAG, "Cannot extract chapters: EPUB not found on disk for bookId $bookId")
            return emptyList()
        }
        return EpubExtractor.extractChapters(file, novelUrl)
    }

    fun getChapterContent(context: Context, bookId: String, entryPath: String): String? {
        val file = getBookFile(context, bookId)
        if (!file.exists()) {
            Log.w(TAG, "Cannot load chapter content: EPUB not found on disk for bookId $bookId")
            return null
        }
        return EpubExtractor.extractChapterHtml(file, entryPath)
    }

    fun deleteBook(context: Context, bookId: String): Boolean {
        val file = getBookFile(context, bookId)
        return file.delete()
    }
}
