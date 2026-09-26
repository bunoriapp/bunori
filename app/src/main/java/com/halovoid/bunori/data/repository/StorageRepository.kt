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
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
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

/**
 * Strongly typed representation of a storage location.
 */
sealed interface StorageLocation {
    data class Relative(val path: String) : StorageLocation
    data class Content(val uri: Uri) : StorageLocation
    data class LocalFile(val file: File) : StorageLocation
    data class Remote(val uri: Uri) : StorageLocation
    data object Empty : StorageLocation

    companion object {
        fun from(location: String?): StorageLocation {
            val trimmed = location?.trim().orEmpty()
            if (trimmed.isEmpty()) return Empty
            return when {
                trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ->
                    Remote(Uri.parse(trimmed))

                trimmed.startsWith("content://", ignoreCase = true) ->
                    Content(Uri.parse(trimmed))

                trimmed.startsWith("file://", ignoreCase = true) ->
                    LocalFile(File(trimmed.removePrefix("file://")))

                trimmed.startsWith("/") ->
                    LocalFile(File(trimmed))

                else ->
                    Relative(trimmed.trimStart('/'))
            }
        }
    }
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
    private var cachedRootDocId: String? = null

    // Cache: relative directory path -> SAF document ID (e.g. "novels/shadowslave" -> "primary:Bunori/novels/shadowslave")
    private val dirDocIdCache = ConcurrentHashMap<String, String>()
    // Cache: parentDocId -> Map of (displayName -> childDocId)
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

    private data class RootStorageInfo(
        val treeUri: Uri,
        val treeDocId: String
    )

    private suspend fun getRootInfo(): RootStorageInfo {
        val uri = preferenceRepository.exportFolderUri.firstOrNull()
            ?: throw StorageException("Root storage folder not selected")

        if (cachedRootUri != uri || cachedRootDocId == null) {
            val persistedPermissions = context.contentResolver.persistedUriPermissions
            val hasPermission = persistedPermissions.any { it.uri == uri && it.isWritePermission }
            if (!hasPermission) {
                throw StorageException("Missing write permission for folder: $uri")
            }
            cachedRootUri = uri
            cachedRootDocId = DocumentsContract.getTreeDocumentId(uri)
            dirDocIdCache.clear()
            dirChildrenCache.clear()
        }

        return RootStorageInfo(uri, cachedRootDocId!!)
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

    override suspend fun openInputStream(location: String): InputStream? = withContext(Dispatchers.IO) {
        when (val loc = StorageLocation.from(location)) {
            is StorageLocation.Remote -> openHttpInputStream(loc.uri)
            is StorageLocation.Content -> openInputStream(loc.uri)
            is StorageLocation.LocalFile -> {
                if (loc.file.exists()) {
                    wrapDecompressionIfNeeded(FileInputStream(loc.file))
                } else {
                    null
                }
            }
            is StorageLocation.Relative -> {
                val uri = getFileUri(loc.path) ?: return@withContext null
                openInputStream(uri)
            }
            is StorageLocation.Empty -> null
        }
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

    override suspend fun readText(location: String): String? = withContext(Dispatchers.IO) {
        openInputStream(location)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
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
        val rootInfo = getRootInfo()
        val targetDirDocId = getDirectoryDocId(rootInfo, relativePath, createIfMissing = true)
            ?: throw StorageException("Failed to navigate to or create path: $relativePath")

        val existingDocId = findChildDocId(rootInfo.treeUri, targetDirDocId, fileName)
        val fileUri = if (existingDocId != null) {
            DocumentsContract.buildDocumentUriUsingTree(rootInfo.treeUri, existingDocId)
        } else {
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(rootInfo.treeUri, targetDirDocId)
            val createdUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                mimeType,
                fileName
            ) ?: throw StorageException("Failed to create document: $fileName")
            val newDocId = DocumentsContract.getDocumentId(createdUri)
            dirChildrenCache.getOrPut(targetDirDocId) { ConcurrentHashMap() }[fileName] = newDocId
            createdUri
        }

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
        val rootInfo = getRootInfo()
        val targetDirDocId = getDirectoryDocId(rootInfo, relativePath, createIfMissing = true)
            ?: throw StorageException("Failed to navigate to or create path: $relativePath")

        val existingDocId = findChildDocId(rootInfo.treeUri, targetDirDocId, fileName)
        val fileUri = if (existingDocId != null) {
            DocumentsContract.buildDocumentUriUsingTree(rootInfo.treeUri, existingDocId)
        } else {
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(rootInfo.treeUri, targetDirDocId)
            val createdUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                mimeType,
                fileName
            ) ?: throw StorageException("Failed to create document: $fileName")
            val newDocId = DocumentsContract.getDocumentId(createdUri)
            dirChildrenCache.getOrPut(targetDirDocId) { ConcurrentHashMap() }[fileName] = newDocId
            createdUri
        }

        try {
            sourceFile.inputStream().buffered(65536).use { input ->
                context.contentResolver.openOutputStream(fileUri, "wt")?.buffered(65536)?.use { output ->
                    input.copyTo(output, 65536)
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

    override suspend fun delete(uri: Uri): Unit = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme?.lowercase() == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            throw StorageException("Failed to delete document: $uri", e)
        }
    }

    override suspend fun delete(location: String): Unit = withContext(Dispatchers.IO) {
        when (val loc = StorageLocation.from(location)) {
            is StorageLocation.Remote -> {}
            is StorageLocation.Content -> delete(loc.uri)
            is StorageLocation.LocalFile -> {
                loc.file.delete()
            }
            is StorageLocation.Relative -> {
                val uri = getFileUri(loc.path)
                if (uri != null) delete(uri)
            }
            is StorageLocation.Empty -> {}
        }
    }

    override suspend fun exists(relativePath: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val rootInfo = getRootInfo()
            val targetDirDocId = getDirectoryDocId(rootInfo, relativePath, createIfMissing = false) ?: return@withContext false
            findChildDocId(rootInfo.treeUri, targetDirDocId, fileName) != null
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun exists(location: String): Boolean = withContext(Dispatchers.IO) {
        when (val loc = StorageLocation.from(location)) {
            is StorageLocation.Remote -> true
            is StorageLocation.Content -> uriExists(loc.uri)
            is StorageLocation.LocalFile -> loc.file.exists()
            is StorageLocation.Relative -> getFileUri(loc.path) != null
            is StorageLocation.Empty -> false
        }
    }

    override suspend fun resolveLocationUri(location: String): Uri? = withContext(Dispatchers.IO) {
        when (val loc = StorageLocation.from(location)) {
            is StorageLocation.Remote -> loc.uri
            is StorageLocation.Content -> loc.uri
            is StorageLocation.LocalFile -> Uri.fromFile(loc.file)
            is StorageLocation.Relative -> getFileUri(loc.path)
            is StorageLocation.Empty -> null
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
        val sourceUri = resolveLocationUri(location)
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

    private suspend fun getFileUri(relativePath: String): Uri? {
        val rootInfo = try {
            getRootInfo()
        } catch (_: Exception) {
            return null
        }

        val cleanPath = relativePath.trim().trimStart('/')
        val lastSlash = cleanPath.lastIndexOf('/')
        val (dirPath, fileName) = if (lastSlash == -1) {
            "" to cleanPath
        } else {
            cleanPath.substring(0, lastSlash) to cleanPath.substring(lastSlash + 1)
        }

        val dirDocId = getDirectoryDocId(rootInfo, dirPath, createIfMissing = false) ?: return null
        val fileDocId = findChildDocId(rootInfo.treeUri, dirDocId, fileName) ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(rootInfo.treeUri, fileDocId)
    }

    /**
     * Traverses or creates directories using Document IDs.
     * Guaranteed never to pass a Tree URI to DocumentsContract.getDocumentId().
     */
    private fun getDirectoryDocId(
        rootInfo: RootStorageInfo,
        relativePath: String,
        createIfMissing: Boolean
    ): String? {
        val normalized = relativePath.trim().trim('/')
        if (normalized.isEmpty()) return rootInfo.treeDocId

        if (!createIfMissing) {
            dirDocIdCache[normalized]?.let { return it }
        }

        var currentParentDocId = rootInfo.treeDocId
        val segments = normalized.split("/").filter { it.isNotEmpty() }
        var currentPath = ""

        for (segment in segments) {
            currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            val cachedDocId = dirDocIdCache[currentPath]
            if (cachedDocId != null) {
                currentParentDocId = cachedDocId
                continue
            }

            val childDocId = findChildDocId(rootInfo.treeUri, currentParentDocId, segment)
            if (childDocId == null) {
                if (createIfMissing) {
                    val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(rootInfo.treeUri, currentParentDocId)
                    val newDirUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        parentDocUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        segment
                    ) ?: throw StorageException("Failed to create directory: $segment")
                    val newDocId = DocumentsContract.getDocumentId(newDirUri)
                    dirChildrenCache.getOrPut(currentParentDocId) { ConcurrentHashMap() }[segment] = newDocId
                    dirDocIdCache[currentPath] = newDocId
                    currentParentDocId = newDocId
                } else {
                    return null
                }
            } else {
                dirDocIdCache[currentPath] = childDocId
                currentParentDocId = childDocId
            }
        }

        dirDocIdCache[normalized] = currentParentDocId
        return currentParentDocId
    }

    private fun findChildDocId(treeUri: Uri, parentDocId: String, displayName: String): String? {
        val cached = dirChildrenCache[parentDocId]
        if (cached != null) {
            return cached[displayName]
        }
        val map = refreshChildrenMap(treeUri, parentDocId)
        return map[displayName]
    }

    private fun refreshChildrenMap(treeUri: Uri, parentDocId: String): ConcurrentHashMap<String, String> {
        val map = dirChildrenCache.getOrPut(parentDocId) { ConcurrentHashMap() }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
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

    override suspend fun listFilesRecursively(relativePath: String): List<StorageFileInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<StorageFileInfo>()
        val rootInfo = try {
            getRootInfo()
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        val baseDirDocId = getDirectoryDocId(rootInfo, relativePath, createIfMissing = false) ?: return@withContext emptyList()

        fun traverse(docId: String, currentPath: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootInfo.treeUri, docId)
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
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(rootInfo.treeUri, childDocId)
                            results.add(StorageFileInfo(relativePath = childRelPath, uri = fileUri, size = size))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        traverse(baseDirDocId, relativePath.trimEnd('/'))
        results
    }
}
