package com.halovoid.bunori.api.core.crawler

import android.net.Uri
import com.halovoid.bunori.api.core.config.CrawlerConfig
import com.halovoid.bunori.api.core.scrapper.Scrapper
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Novel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.RequestBody
import org.jsoup.nodes.Document
import kotlin.math.max

/**
 * Base abstract class for all source crawlers in the Data layer.
 *
 * Provides utility methods for fetching HTML, resolving absolute URLs,
 * and defines the interface for site-specific implementations using Jsoup for scraping.
 */
abstract class Crawler {
    protected var _config: CrawlerConfig? = null

    open val config: CrawlerConfig
        get() = _config ?: CrawlerConfig(userFolderLocation = "", maxAttempts = 3)

    fun initialize(config: CrawlerConfig) {
        this._config = config
    }
    private val version = 1
    abstract val name: String

    open val id: String
        get() = name.lowercase().replace(" ", "").replace("-", "")

    abstract val baseUrl: String

    open val language: String = "en"

    open val iconUrl: String? = null
    open val iconFile: java.io.File? = null

    abstract val webviewNeeded: Boolean?

    /** Generic HTTP and scraping utility */
    protected val scrapper = Scrapper()

    protected fun maxWorkers(): Int {
        return max(1, config.maxSessionPerExit) + 1
    }

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