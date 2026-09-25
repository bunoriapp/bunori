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
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Exception thrown when storage operations fail.
 */
class StorageException(message: String, cause: Throwable? = null) : IOException(message, cause)


data class StorageFileInfo(
    val relativePath: String,
    val uri: Uri,
    val size: Long
)

sealed interface CopyResult {
    data class Success(val destinationUri: Uri) : CopyResult
    data object SourceMissing : CopyResult
    data class Error(val throwable: Throwable? = null) : CopyResult
}

interface StorageRepository {

    /**
     * Input Stream to read from the files
     */
    suspend fun openInputStream(
        uri: Uri
    ): InputStream?

    suspend fun openInputStream(
        location: String
    ): InputStream?

    /**
     * Convenience Function
     */
    suspend fun readText(
        uri: Uri
    ): String?

    suspend fun readText(
        location: String
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

    suspend fun saveFile(
        relativePath: String,
        fileName: String,
        mimeType: String,
        sourceFile: File
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

    suspend fun delete(location: String)

    suspend fun exists(
        relativePath: String,
        fileName: String
    ): Boolean

    suspend fun exists(
        location: String
    ): Boolean

    suspend fun copyFile(
        sourceUri: Uri,
        destinationUri: Uri
    ): Uri?

    suspend fun copyLocationToUri(
        location: String,
        destinationUri: Uri
    ): CopyResult

    suspend fun uriExists(
        uri: Uri
    ): Boolean

    suspend fun resolveLocationUri(
        location: String
    ): Uri?

    suspend fun listFilesRecursively(
        relativePath: String
    ): List<StorageFileInfo>

    companion object {
        fun getInstance(context: Context): StorageRepository = StorageRepositoryImpl.getInstance(context)
    }
}

class StorageRepositoryImpl private constructor(
    private val context: Context,
    private val preferenceRepository: PreferenceRepository
) : StorageRepository {

    private val httpClient = NetworkClient.okHttpClient

    private var cachedRootUri: Uri? = null
    private val dirUriCache = ConcurrentHashMap<String, Uri>()
    private val dirChildrenCache = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

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

    override suspend fun saveFile(
        relativePath: String,
        fileName: String,
        mimeType: String,
        sourceFile: File
    ): Uri = withContext(Dispatchers.IO) {
        val rootUri = getRootUri()
        val targetDirUri = getDirectory(rootUri, relativePath, createIfMissing = true)
            ?: throw StorageException("Failed to navigate to or create path: $relativePath")

        val existingFileUri = findChildUri(rootUri, targetDirUri, fileName)
        val fileUri = existingFileUri ?: createDocument(targetDirUri, mimeType, fileName)

        try {
            sourceFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(fileUri, "wt")?.use { output ->
                    input.copyTo(output)
                } ?: throw StorageException("Failed to open output stream for $fileUri")
            }
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

    override suspend fun openInputStream(location: String): InputStream? = withContext(Dispatchers.IO) {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return@withContext null

        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            val file = File(trimmed)
            if (file.exists()) {
                return@withContext wrapDecompressionIfNeeded(FileInputStream(file))
            }
        }

        val uri = resolveLocationUri(trimmed) ?: return@withContext null
        openInputStream(uri)
    }

    override suspend fun readText(location: String): String? = withContext(Dispatchers.IO) {
        openInputStream(location)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    }

    override suspend fun delete(location: String) = withContext(Dispatchers.IO) {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return@withContext

        if (trimmed.startsWith("/") || trimmed.startsWith("file://")) {
            val path = trimmed.removePrefix("file://")
            File(path).delete()
            return@withContext
        }

        val uri = resolveLocationUri(trimmed)
        if (uri != null) {
            delete(uri)
        }
    }

    override suspend fun exists(location: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return@withContext false

        if (trimmed.startsWith("/") || trimmed.startsWith("file://")) {
            val path = trimmed.removePrefix("file://")
            return@withContext File(path).exists()
        }

        val uri = resolveLocationUri(trimmed) ?: return@withContext false
        uriExists(uri)
    }

    override suspend fun resolveLocationUri(location: String): Uri? = withContext(Dispatchers.IO) {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return@withContext null

        if (trimmed.startsWith("http://", ignoreCase = true) || 
            trimmed.startsWith("https://", ignoreCase = true) || 
            trimmed.startsWith("file://", ignoreCase = true)) {
            return@withContext Uri.parse(trimmed)
        }

        if (trimmed.startsWith("/")) {
            return@withContext Uri.fromFile(File(trimmed))
        }

        // Relative path e.g. "novels/shadowslave/chapters/0001_1.html.gz"
        getFileUri(trimmed)
    }

    private suspend fun getFileUri(relativePath: String): Uri? {
        val rootUri = try {
            getRootUri()
        } catch (_: Exception) {
            return null
        }

        val cleanPath = relativePath.trim().trimStart('/')
        val lastSlash = cleanPath.lastIndexOf('/')
        if (lastSlash == -1) {
            return findChildUri(rootUri, rootUri, cleanPath)
        }

        val dirPath = cleanPath.substring(0, lastSlash)
        val fileName = cleanPath.substring(lastSlash + 1)
        val targetDirUri = getDirectory(rootUri, dirPath, createIfMissing = false) ?: return null
        return findChildUri(rootUri, targetDirUri, fileName)
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

    override suspend fun copyFile(sourceUri: Uri, destinationUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    input.copyTo(output)
                }
            }
            destinationUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun copyLocationToUri(location: String, destinationUri: Uri): CopyResult = withContext(Dispatchers.IO) {
        val sourceUri = resolveLocationUri(location) ?: (try { Uri.parse(location) } catch (_: Exception) { null })
        if (sourceUri == null || !uriExists(sourceUri)) {
            return@withContext CopyResult.SourceMissing
        }
        val result = copyFile(sourceUri, destinationUri)
        if (result != null) {
            CopyResult.Success(destinationUri)
        } else {
            CopyResult.Error()
        }
    }

    override suspend fun uriExists(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun getRootUri(): Uri {
        val uri = preferenceRepository.exportFolderUri.firstOrNull()
            ?: throw StorageException("Root storage folder not selected")

        if (cachedRootUri != uri) {
            cachedRootUri = uri
            dirChildrenCache.clear()
            dirUriCache.clear()
        }

        val persistedPermissions = context.contentResolver.persistedUriPermissions
        val hasPermission = persistedPermissions.any { it.uri == uri && it.isWritePermission }
        if (!hasPermission) {
            throw StorageException("Missing write permission for folder: $uri")
        }

        return uri
    }

    private fun getDirectory(rootUri: Uri, relativePath: String, createIfMissing: Boolean): Uri? {
        val normalized = relativePath.trim().trim('/')
        if (normalized.isEmpty()) return rootUri

        if (!createIfMissing) {
            dirUriCache[normalized]?.let { return it }
        }

        val treeId = DocumentsContract.getTreeDocumentId(rootUri)
        var currentParentId = treeId
        var currentParentUri = rootUri

        val segments = normalized.split("/").filter { it.isNotEmpty() }
        var currentPath = ""
        for (segment in segments) {
            currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            val childId = findChildId(rootUri, currentParentId, segment)
            if (childId == null) {
                if (createIfMissing) {
                    val parentUri = currentParentUri
                    val newUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        parentUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        segment
                    ) ?: throw StorageException("Failed to create directory: $segment")
                    currentParentId = DocumentsContract.getDocumentId(newUri)
                    currentParentUri = newUri
                    dirChildrenCache[DocumentsContract.getDocumentId(parentUri)]?.put(segment, currentParentId)
                    dirUriCache[currentPath] = newUri
                } else {
                    return null
                }
            } else {
                currentParentId = childId
                val dirUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, currentParentId)
                currentParentUri = dirUri
                dirUriCache[currentPath] = dirUri
            }
        }
        val resultUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, currentParentId)
        dirUriCache[normalized] = resultUri
        return resultUri
    }

    private fun refreshChildrenMap(treeUri: Uri, parentDocumentId: String): ConcurrentHashMap<String, String> {
        val map = dirChildrenCache.getOrPut(parentDocumentId) { ConcurrentHashMap() }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = if (idIdx != -1) cursor.getString(idIdx) else null
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                    if (id != null && name != null) {
                        map[name] = id
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private fun findChildId(treeUri: Uri, parentDocumentId: String, displayName: String): String? {
        val cached = dirChildrenCache[parentDocumentId]
        if (cached != null) {
            val docId = cached[displayName]
            if (docId != null) return docId
        }
        val map = refreshChildrenMap(treeUri, parentDocumentId)
        return map[displayName]
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

    override suspend fun listFilesRecursively(relativePath: String): List<StorageFileInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<StorageFileInfo>()
        val rootUri = try {
            getRootUri()
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        val baseDirUri = getDirectory(rootUri, relativePath, createIfMissing = false) ?: return@withContext emptyList()
        val baseDocId = DocumentsContract.getDocumentId(baseDirUri)

        fun traverse(docId: String, currentPath: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )

            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                    while (cursor.moveToNext()) {
                        val childDocId = if (idIdx != -1) cursor.getString(idIdx) else null ?: continue
                        val displayName = if (nameIdx != -1) cursor.getString(nameIdx) else null ?: continue
                        val mimeType = if (mimeIdx != -1) cursor.getString(mimeIdx) else ""
                        val size = if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                        val childRelPath = if (currentPath.isEmpty()) displayName else "$currentPath/$displayName"

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            traverse(childDocId, childRelPath)
                        } else {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, childDocId)
                            results.add(StorageFileInfo(relativePath = childRelPath, uri = fileUri, size = size))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        traverse(baseDocId, relativePath.trimEnd('/'))
        results
    }

    fun createDocument(parentUri: Uri, mimeType: String, displayName: String): Uri {
        val newUri = DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            mimeType,
            displayName
        ) ?: throw StorageException("Failed to create document: $displayName")
        try {
            val parentId = DocumentsContract.getDocumentId(parentUri)
            val docId = DocumentsContract.getDocumentId(newUri)
            dirChildrenCache[parentId]?.put(displayName, docId)
        } catch (_: Exception) {}
        return newUri
    }
}
