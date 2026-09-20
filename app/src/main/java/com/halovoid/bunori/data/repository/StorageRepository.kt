package com.halovoid.bunori.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.halovoid.bunori.api.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Exception thrown when storage operations fail.
 */
class StorageException(message: String, cause: Throwable? = null) : IOException(message, cause)


interface StorageRepository {

    /**
     * Input Stream to read from the files
     */
    suspend fun openInputStream(
        uri: Uri
    ): InputStream?

    /**
     * Convenience Function
     */
    suspend fun readText(
        uri: Uri
    ): String?

    /**
     * Temporary Space generated for generating artifacts
     */
    suspend fun getCacheDir(): File

    suspend fun saveFile(
        relativePath: String,
        fileName: String,
        mimeType: String,
        data: ByteArray
    ): Uri

    suspend fun saveText(
        relativePath: String,
        fileName: String,
        mimeType: String,
        content: String
    ): Uri

    suspend fun saveCompressedText(
        relativePath: String,
        fileName: String,
        content: String
    ): Uri

    suspend fun delete(uri: Uri)

    suspend fun exists(
        relativePath: String,
        fileName: String
    ): Boolean
}

class StorageRepositoryImpl private constructor(
    private val context: Context,
    private val preferenceRepository: PreferenceRepository
) : StorageRepository {

    private val httpClient = NetworkClient.okHttpClient

    companion object {
        @Volatile
        private var INSTANCE: StorageRepository? = null

        fun getInstance(context: Context): StorageRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StorageRepositoryImpl(
                    context.applicationContext,
                    PreferenceRepository.getInstance(context)
                ).also { INSTANCE = it }
            }
        }
    }

    private fun openHttpInputStream(uri: Uri): InputStream? {
        val request = Request.Builder()
            .url(uri.toString())
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            response.close()
            return null
        }

        val body = response.body ?: run {
            response.close()
            return null
        }

        return object : FilterInputStream(body.byteStream()) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    response.close()
                }
            }
        }
    }

    @SuppressLint("Recycle")
    override suspend fun openInputStream(uri: Uri): InputStream? = withContext(Dispatchers.IO) {
        val rawStream = when (uri.scheme?.lowercase()) {
            "content" ->
                context.contentResolver.openInputStream(uri)

            "file" ->
                uri.path?.let { runCatching { FileInputStream(File(it)) }.getOrNull() }
                    ?: context.contentResolver.openInputStream(uri)

            "http", "https" ->
                openHttpInputStream(uri)

            else ->
                uri.path?.let { runCatching { FileInputStream(File(it)) }.getOrNull() }
        } ?: return@withContext null

        wrapDecompressionIfNeeded(rawStream)
    }

    private fun wrapDecompressionIfNeeded(rawStream: InputStream): InputStream {
        val buffered = if (rawStream.markSupported()) rawStream else BufferedInputStream(rawStream)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) {
            GZIPInputStream(buffered)
        } else {
            buffered
        }
    }

    override suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    }

    override suspend fun getCacheDir(): File {
        return context.cacheDir
    }
    override suspend fun saveFile(
        relativePath: String,
        fileName: String,
        mimeType: String,
        data: ByteArray
    ): Uri = withContext(Dispatchers.IO) {
        val rootUri = getRootUri()
        val targetDirUri = getDirectory(rootUri, relativePath, createIfMissing = true)
            ?: throw StorageException("Failed to navigate to or create path: $relativePath")

        val existingFileUri = findChildUri(rootUri, targetDirUri, fileName)
        val fileUri = existingFileUri ?: createDocument(targetDirUri, mimeType, fileName)

        try {
            // "wt" mode opens the file for writing and truncates any existing content.
            context.contentResolver.openOutputStream(fileUri, "wt")?.use {
                it.write(data)
            } ?: throw StorageException("Failed to open output stream for $fileUri")
        } catch (e: Exception) {
            throw StorageException("Error writing to file: $fileName", e)
        }

        fileUri
    }

    override suspend fun saveText(
        relativePath: String,
        fileName: String,
        mimeType: String,
        content: String
    ): Uri = saveFile(relativePath, fileName, mimeType, content.toByteArray())

    override suspend fun saveCompressedText(
        relativePath: String,
        fileName: String,
        content: String
    ): Uri = withContext(Dispatchers.IO) {
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { it.write(content.toByteArray(Charsets.UTF_8)) }
        saveFile(relativePath, fileName, "application/gzip", byteStream.toByteArray())
    }

    override suspend fun delete(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } catch (e: Exception) {
                throw StorageException("Failed to delete document: $uri", e)
            }
        }
    }

    override suspend fun exists(relativePath: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val rootUri = getRootUri()
            val targetDirUri = getDirectory(rootUri, relativePath, createIfMissing = false) ?: return@withContext false
            findChildUri(rootUri, targetDirUri, fileName) != null
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun getRootUri(): Uri {
        val uri = preferenceRepository.exportFolderUri.firstOrNull()
            ?: throw StorageException("Root storage folder not selected")

        val persistedPermissions = context.contentResolver.persistedUriPermissions
        val hasPermission = persistedPermissions.any { it.uri == uri && it.isWritePermission }
        if (!hasPermission) {
            throw StorageException("Missing write permission for folder: $uri")
        }

        return uri
    }

    private fun getDirectory(rootUri: Uri, relativePath: String, createIfMissing: Boolean): Uri? {
        val treeId = DocumentsContract.getTreeDocumentId(rootUri)
        var currentParentId = treeId

        val segments = relativePath.split("/").filter { it.isNotEmpty() }
        for (segment in segments) {
            val childId = findChildId(rootUri, currentParentId, segment)
            if (childId == null) {
                if (createIfMissing) {
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, currentParentId)
                    val newUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        parentUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        segment
                    ) ?: throw StorageException("Failed to create directory: $segment")
                    currentParentId = DocumentsContract.getDocumentId(newUri)
                } else {
                    return null
                }
            } else {
                currentParentId = childId
            }
        }
        return DocumentsContract.buildDocumentUriUsingTree(rootUri, currentParentId)
    }

    private fun findChildId(treeUri: Uri, parentDocumentId: String, displayName: String): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == displayName) {
                    return cursor.getString(0)
                }
            }
        }
        return null
    }

    private fun findChildUri(treeUri: Uri, parentUri: Uri, displayName: String): Uri? {
        val parentId = DocumentsContract.getDocumentId(parentUri)
        val childId = findChildId(treeUri, parentId, displayName)
        return if (childId != null) {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
        } else {
            null
        }
    }

    fun createDocument(parentUri: Uri, mimeType: String, displayName: String): Uri {
        return DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            mimeType,
            displayName
        ) ?: throw StorageException("Failed to create document: $displayName")
    }
}
