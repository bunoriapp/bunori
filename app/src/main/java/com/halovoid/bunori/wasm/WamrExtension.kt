package com.halovoid.bunori.wasm

import android.util.Log
import com.halovoid.bunori.extension.api.ExtensionJson
import com.halovoid.bunori.extension.api.IExtension
import com.halovoid.bunori.extension.api.models.ExtensionManifest
import com.halovoid.bunori.extension.api.models.ExtensionMetadata
import com.halovoid.bunori.extension.api.models.ListingDto
import com.halovoid.bunori.extension.api.models.NovelDto
import com.halovoid.bunori.extension.api.models.SearchResultDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class WamrExtension(
    val manifest: ExtensionManifest,
    val binaryBytes: ByteArray
) : IExtension, AutoCloseable {

    companion object {
        private const val TAG = "WamrExtension"
    }

    override val metadata: ExtensionMetadata = manifest.toMetadata()

    private var modulePtr: Long = 0
    private var instPtr: Long = 0
    private val mutex = Mutex()

    constructor(manifest: ExtensionManifest, file: File) : this(manifest, file.readBytes())

    init {
        val loadStart = System.currentTimeMillis()
        modulePtr = WamrBridge.nativeLoad(binaryBytes)
        if (modulePtr == 0L) {
            throw IllegalStateException("Failed to load WAMR module for: ${manifest.name}")
        }

        instPtr = WamrBridge.nativeInstantiate(modulePtr)
        if (instPtr == 0L) {
            WamrBridge.nativeDestroy(0L, modulePtr)
            modulePtr = 0L
            throw IllegalStateException("Failed to instantiate WAMR module for: ${manifest.name}")
        }
        Log.i(TAG, "Initialized WAMR Native Runtime in ${System.currentTimeMillis() - loadStart}ms for ${manifest.name} (id: ${manifest.id})")
    }

    override suspend fun search(query: String, page: Int): List<SearchResultDto> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        Log.i(TAG, "[${metadata.name}] [${metadata.id}] Starting search for '$query' (page $page)...")
        val json = mutex.withLock {
            WamrBridge.nativeCallString(instPtr, "search", query, page)
        } ?: run {
            Log.e(TAG, "[${metadata.name}] [${metadata.id}] nativeCallString('search', '$query', $page) returned NULL! Search failed.")
            throw IOException("Search failed in extension ${metadata.name}")
        }
        val duration = System.currentTimeMillis() - start
        val results = if (json.isBlank()) emptyList()
        else ExtensionJson.json.decodeFromString<List<SearchResultDto>>(json)
        Log.i(TAG, "[${metadata.name}] [${metadata.id}] [WAMR search] total=${duration}ms (results=${results.size})")
        results
    }

    override suspend fun getNovelDetails(novelUrl: String): NovelDto = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val json = mutex.withLock {
            WamrBridge.nativeCallString(instPtr, "get_novel_details", novelUrl, -1)
        } ?: throw IllegalStateException("Empty response from get_novel_details for $novelUrl")
        val duration = System.currentTimeMillis() - start
        val details = ExtensionJson.json.decodeFromString<NovelDto>(json)
        Log.i(TAG, "[${metadata.id}] [WAMR getNovelDetails] total=${duration}ms (chapters=${details.chapters.size})")
        details
    }

    override suspend fun getChapterContent(chapterUrl: String): String? = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val content = mutex.withLock {
            WamrBridge.nativeCallString(instPtr, "get_chapter_content", chapterUrl, -1)
        }?.takeIf { it.isNotBlank() }
        Log.i(TAG, "[${metadata.id}] [WAMR getChapterContent] total=${System.currentTimeMillis() - start}ms (length=${content?.length ?: 0})")
        content
    }

    override fun getListings(): List<ListingDto> {
        val json = WamrBridge.nativeCallString(instPtr, "get_listings", null, -1)
        return if (json.isNullOrBlank()) emptyList()
        else try {
            ExtensionJson.json.decodeFromString(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getListingNovels(listingId: String, page: Int): List<SearchResultDto> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val json = mutex.withLock {
            WamrBridge.nativeCallString(instPtr, "get_listing_novels", listingId, page)
        } ?: throw IOException("Failed to fetch listing novels in extension ${metadata.name}")
        val duration = System.currentTimeMillis() - start
        val results = if (json.isBlank()) emptyList()
        else ExtensionJson.json.decodeFromString<List<SearchResultDto>>(json)
        Log.i(TAG, "[${metadata.id}] [WAMR getListingNovels] total=${duration}ms (results=${results.size})")
        results
    }

    override fun close() {
        val iPtr = instPtr
        val mPtr = modulePtr
        if (iPtr != 0L || mPtr != 0L) {
            instPtr = 0L
            modulePtr = 0L
            WamrBridge.nativeDestroy(iPtr, mPtr)
            Log.i(TAG, "Destroyed WAMR instance for: ${manifest.name}")
        }
    }

    protected fun finalize() {
        close()
    }
}
