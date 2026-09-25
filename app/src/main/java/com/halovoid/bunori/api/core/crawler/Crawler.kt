package com.halovoid.bunori.api.core.crawler

import com.halovoid.bunori.api.core.config.CrawlerConfig
import com.halovoid.bunori.api.core.scrapper.Scrapper
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.extension.api.models.ListingDto
import kotlin.math.max
abstract class Crawler {
    protected var _config: CrawlerConfig? = null
    open val config: CrawlerConfig
        get() = _config ?: CrawlerConfig(userFolderLocation = "", maxAttempts = 3)
    fun initialize(config: CrawlerConfig) {
        this._config = config
    }
    abstract val name: String
    open val id: String
        get() = name.lowercase().replace(" ", "").replace("-", "")
    abstract val baseUrl: String
    open val language: String = "en"
    open val iconUrl: String? = null
    open val iconFile: java.io.File? = null
    abstract val webviewNeeded: Boolean?
    protected val scrapper = Scrapper()
    abstract fun canHandle(url: String): Boolean
    open suspend fun getNovelMetadata(novelUrl: String): Novel {
        return getNovelDetails(novelUrl).copy(chapters = emptyList())
    }
    open suspend fun getChapterList(novelUrl: String): List<Chapter> {
        return getNovelDetails(novelUrl).chapters
    }
    abstract suspend fun getNovelDetails(novelUrl: String): Novel
    abstract suspend fun getChapterContent(chapterUrl: String): String?
    abstract suspend fun getSearchResults(query: String): List<Novel>
    open fun getListings(): List<ListingDto> = emptyList()
    open suspend fun getListingNovels(listingId: String, page: Int = 1): List<Novel> = emptyList()
    open suspend fun searchNovels(query: String, page: Int = 1): List<Novel> = getSearchResults(query)
    open suspend fun downloadCover(url: String) : ByteArray? {
        if (url.isBlank()) {
            throw Exception("No Download URL provided for Cover")
        }
        return scrapper.download(url)
    }
    open fun getNovelKey(url: String): String {
        val slug = url.trimEnd('/').split('/').last()
        return "${name.lowercase()}_$slug".filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    }
}