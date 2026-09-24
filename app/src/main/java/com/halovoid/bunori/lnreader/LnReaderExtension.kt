package com.halovoid.bunori.lnreader

import android.util.Log
import com.halovoid.bunori.extension.api.ExtensionJson
import com.halovoid.bunori.extension.api.IExtension
import com.halovoid.bunori.extension.api.models.ExtensionManifest
import com.halovoid.bunori.extension.api.models.ExtensionMetadata
import com.halovoid.bunori.extension.api.models.ListingDto
import com.halovoid.bunori.extension.api.models.NovelDto
import com.halovoid.bunori.extension.api.models.SearchResultDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

class LnReaderExtension(
    val manifest: ExtensionManifest,
    val pluginJs: String,
    val runtimeJs: String
) : IExtension, AutoCloseable {

    companion object {
        private const val TAG = "LnReaderExtension"
    }

    override val metadata: ExtensionMetadata = manifest.toMetadata()
    private val mutex = Mutex()
    private val bridge = LnReaderBridge(manifest.name)

    init {
        runBlocking {
            bridge.initialize(runtimeJs, pluginJs)
        }
        Log.i(TAG, "Initialized LNReader Extension for ${manifest.name} (${manifest.id})")
    }

    override suspend fun search(query: String, page: Int): List<SearchResultDto> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val queryJson = ExtensionJson.json.encodeToString(query)
        val json = mutex.withLock {
            val script = "JSON.stringify(await __bunori_bridge.search($queryJson, $page))"
            bridge.evaluate(script)
        } ?: throw IOException("Search returned null in ${metadata.name}")

        val results = if (json.isBlank()) emptyList()
        else ExtensionJson.json.decodeFromString<List<SearchResultDto>>(json)
        Log.i(TAG, "[${metadata.id}] [JS search] total=${System.currentTimeMillis() - start}ms (results=${results.size})")
        results
    }

    override suspend fun getNovelDetails(novelUrl: String): NovelDto = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val urlJson = ExtensionJson.json.encodeToString(novelUrl)
        val json = mutex.withLock {
            val script = "JSON.stringify(await __bunori_bridge.getNovelDetails($urlJson))"
            bridge.evaluate(script)
        } ?: throw IllegalStateException("Empty response from getNovelDetails for $novelUrl")

        val details = ExtensionJson.json.decodeFromString<NovelDto>(json)
        Log.i(TAG, "[${metadata.id}] [JS getNovelDetails] total=${System.currentTimeMillis() - start}ms (chapters=${details.chapters.size})")
        details
    }

    override suspend fun getChapterContent(chapterUrl: String): String? = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val urlJson = ExtensionJson.json.encodeToString(chapterUrl)
        val content = mutex.withLock {
            val script = "await __bunori_bridge.getChapterContent($urlJson)"
            bridge.evaluate(script)
        }?.takeIf { it.isNotBlank() }

        Log.i(TAG, "[${metadata.id}] [JS getChapterContent] total=${System.currentTimeMillis() - start}ms")
        content
    }

    override fun getListings(): List<ListingDto> = listOf(
        ListingDto(id = "popular", name = "Popular"),
        ListingDto(id = "latest", name = "Latest Updates")
    )

    override suspend fun getListingNovels(listingId: String, page: Int): List<SearchResultDto> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val json = mutex.withLock {
            val script = "JSON.stringify(await __bunori_bridge.getListingNovels(\"$listingId\", $page))"
            bridge.evaluate(script)
        } ?: return@withContext emptyList()

        val results = if (json.isBlank()) emptyList()
        else ExtensionJson.json.decodeFromString<List<SearchResultDto>>(json)
        Log.i(TAG, "[${metadata.id}] [JS getListingNovels] total=${System.currentTimeMillis() - start}ms (results=${results.size})")
        results
    }

    override fun close() {
        bridge.close()
    }
}
