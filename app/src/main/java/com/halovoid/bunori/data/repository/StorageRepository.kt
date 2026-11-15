package com.halovoid.bunori.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.halovoid.bunori.api.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.Request
import okio.IOException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream

data class StorageFileInfo(
    val name: String,
    val uri: Uri,
    val size: Long,
    val isDirectory: Boolean
)

data class StorageTree(
    val info: StorageFileInfo,
    val children: List<StorageTree> = emptyList()
) {
    val isDirectory: Boolean get() = info.isDirectory
    val name: String get() = info.name
    val uri: Uri get() = info.uri

    fun findChild(childName: String): StorageTree? {
        for (child in children) {
            if (child.name == childName) return child
        }
        return null
    }

    fun files(): List<StorageTree> {
        val result = mutableListOf<StorageTree>()
        for (child in children) {
            if (!child.isDirectory) result.add(child)
        }
        return result
    }

    fun directories(): List<StorageTree> {
        val result = mutableListOf<StorageTree>()
        for (child in children) {
            if (child.isDirectory) result.add(child)
        }
        return result
    }
}

/**
 * Storage Repository consists of basic file operation functions
 * why only relativeToUri but not the other way around?
 * the code can always for a relative path and get its actual URI
 * it does not seem worth it to actually go the other way around
 * and also URI to Relative path lookup is slow as it involves a
 * complete DFS Tree lookup, or I would have had to cache the data
 * to make it fast.
 */
interface StorageRepository {
    suspend fun read(uri: Uri): String?;
    suspend fun read(url: String): String?;
    suspend fun write(uri: Uri, data: ByteArray): Boolean;
    suspend fun deleteFile(uri: Uri): Boolean;
    suspend fun openInputStream(uri: Uri): InputStream?;
    suspend fun openInputStream(url: String): InputStream?;
    suspend fun openOutputStream(uri: Uri): OutputStream?;
    suspend fun exists(uri: Uri): Boolean;
    suspend fun createDirectory(uri: Uri, name: String): Uri?;
    suspend fun createFile(parentUri: Uri, name: String, mime: String, data: ByteArray): Uri?;
    suspend fun relativeToUri(path: String): Uri?;
    suspend fun ensureDirectory(relativePath: String): Uri?
    suspend fun tree(path: Uri): StorageTree?
    fun getCacheDir(): File;
    companion object {
        fun getInstance(context: Context): StorageRepository = StorageRepositoryImpl.getInstance(context)
    }
}

class StorageRepositoryImpl private constructor(
    private val context: Context,
    private val preferenceRepository: PreferenceRepository
) : StorageRepository {

    private val httpClient = NetworkClient.okHttpClient

    suspend fun rootUri(): Uri? = preferenceRepository.exportFolderUri.firstOrNull()

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

    override fun getCacheDir(): File = context.cacheDir;

    override suspend fun relativeToUri(path: String): Uri? = withContext(Dispatchers.IO) {
        val treeUri = rootUri() ?: return@withContext null;
        val rootId = DocumentsContract.getTreeDocumentId(treeUri);
        val isUri = DocumentsContract.isDocumentUri(context, path.toUri());
        if (isUri) {
            return@withContext path.toUri() // case where the path of cache might be given
        }

        val segments = path.trim('/').split('/').filter { it.isNotBlank() }

        // fast lookup by just adding the relative path when I am sure this is an android SAF filesystem
        if (treeUri.authority == "com.android.externalstorage.documents") {
            val docId = if (segments.isEmpty()) {
                rootId
            } else {
                val prefix =
                    if (rootId.endsWith("/") || rootId.endsWith(":")) rootId else "$rootId/"
                prefix + segments.joinToString("/")
            }
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
            val exists = exists(docUri);
            return@withContext if (exists) docUri else null
        }

        // slow lookup for other filesystem (Google Drive, Cloud etc...)
        var currentId = rootId;
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        try {
            for (segment in segments) {
                val childUri =
                    DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentId);

                currentId = context.contentResolver.query(childUri, projection, null, null, null)
                    ?.use { cursor ->
                        val idIndex =
                            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                        val nameIndex =
                            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);

                        while (cursor.moveToNext()) {
                            if (cursor.getString(nameIndex) == segment) return@use cursor.getString(
                                idIndex
                            )
                        }
                        null
                    } ?: return@withContext null
            }
        } catch (_: SecurityException) {
            return@withContext null
        }

        DocumentsContract.buildDocumentUriUsingTree(treeUri, currentId);
    }

    override suspend fun ensureDirectory(relativePath: String): Uri? {
        val existing = relativeToUri(relativePath);
        if (existing != null) return existing;

        val rootUri = relativeToUri("") ?: return null;
        val segments = relativePath.trim('/').split('/').filter { it.isNotBlank() };

        var currentUri = rootUri;
        var currentPath = "";

        for (segment in segments) {
            currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment";
            val nextUri =
                relativeToUri(currentPath) ?: createDirectory(currentUri, segment) ?: return null;
            currentUri = nextUri;
        }

        return currentUri
    }

    override suspend fun openInputStream(url: String): InputStream? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()
            } else {
                response.close()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("Recycle")
    override suspend fun openInputStream(uri: Uri): InputStream? = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == null || uri.scheme == "file") {
                val path = uri.path ?: return@withContext null
                File(path).inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("Recycle")
    override suspend fun openOutputStream(uri: Uri): OutputStream? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        openInputStream(uri).use { input ->
            GZIPInputStream(input)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }
    }

    override suspend fun read(url: String): String? = withContext(Dispatchers.IO) {
        openInputStream(url)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    }

    override suspend fun deleteFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun write(uri: Uri, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(data);
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun createFile(
        parentUri: Uri,
        name: String,
        mime: String,
        data: ByteArray
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val newUri =
                DocumentsContract.createDocument(context.contentResolver, parentUri, mime, name)
                    ?: return@withContext null;
            write(newUri, data)
            newUri
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun exists(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null
            )?.use { it.moveToFirst() } ?: false
        } catch (_: Exception) {
            return@withContext false
        }
    }

    override suspend fun createDirectory(uri: Uri, name: String): Uri? =
        withContext(Dispatchers.IO) {
            try {
                DocumentsContract.createDocument(
                    context.contentResolver,
                    uri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    name
                )
            } catch (_: Exception) {
                return@withContext null
            }
        }

    @SuppressLint("Range")
    override suspend fun tree(path: Uri): StorageTree? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        fun buildTree(uri: Uri, name: String): StorageTree {
            val docId = DocumentsContract.getDocumentId(uri)
            val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)
            val children = mutableListOf<StorageTree>()

            resolver.query(
                childUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val childName = cursor.getString(1)
                    val size = if (cursor.isNull(2)) 0L else cursor.getLong(2)
                    val mime = cursor.getString(3)

                    val itemUri = DocumentsContract.buildDocumentUriUsingTree(uri, id)
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val info = StorageFileInfo(
                        name = childName,
                        uri = itemUri,
                        size = size,
                        isDirectory = isDir
                    )

                    children.add(
                        if (isDir) {
                            buildTree(itemUri, childName)
                        } else {
                            StorageTree(info, emptyList())
                        }
                    )
                }
            }

            return StorageTree(
                info = StorageFileInfo(name = name, uri = uri, size = 0L, isDirectory = true),
                children = children
            )
        }

        try {
            val rootName = try {
                DocumentsContract.getDocumentId(path).substringAfterLast('/').substringAfterLast(':')
            } catch (_: Exception) {
                path.lastPathSegment ?: "novels"
            }
            buildTree(path, rootName)
        } catch (e: Exception) {
            null
        }
    }
};
