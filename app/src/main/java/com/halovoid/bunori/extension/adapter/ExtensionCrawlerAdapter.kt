package com.halovoid.bunori.extension.adapter

import com.halovoid.bunori.api.core.config.CrawlerConfig
import com.halovoid.bunori.api.core.crawler.Crawler
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.extension.api.IExtension
import com.halovoid.bunori.extension.api.models.ListingDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adapter allowing dynamic [IExtension] implementations to function as legacy [Crawler]
 * instances across the Bunori application (Search, Novel Detail, Reader, Downloads, etc.).
 */
class ExtensionCrawlerAdapter(
    val extension: IExtension,
    override val iconFile: java.io.File? = null
) : Crawler() {

    override val id: String = extension.metadata.id
    override val name: String = extension.metadata.name
    override val baseUrl: String = extension.metadata.baseUrl
    override val language: String = extension.metadata.lang
    override val webviewNeeded: Boolean = extension.metadata.webviewNeeded
    override val iconUrl: String
        get() = extension.metadata.iconUrl ?: run {
            val host = try { java.net.URI(baseUrl).host ?: baseUrl } catch (_: Exception) { baseUrl }
            "https://www.google.com/s2/favicons?domain=$host&sz=128"
        }

    override val config: CrawlerConfig
        get() = CrawlerConfig(
            userFolderLocation = "",
            maxAttempts = extension.metadata.maxAttempts,
            runnerConcurrency = extension.metadata.runnerConcurrency,
            runnerCooldown = (extension.metadata.runnerCooldown / 1000).toInt(),
            runnerCooldownMs = extension.metadata.runnerCooldown
        )

    override fun canHandle(url: String): Boolean {
        val cleanUrl = url.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
        val domain = cleanUrl.substringBefore('/')
        val extDomain = extension.metadata.baseUrl.lowercase()
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .substringBefore('/')
        return domain.contains(extDomain) || extDomain.contains(domain)
    }

    override suspend fun getSearchResults(query: String): List<Novel> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val results = extension.search(query, page = 1)
        val mapped = results.map { dto ->
            Novel(
                url = dto.url,
                title = dto.title,
                author = dto.author,
                coverUrl = dto.coverUrl,
                description = null,
                chapters = emptyList(),
                crawlerName = extension.metadata.id,
                coverHttpsUrl = dto.coverUrl
            )
        }
        android.util.Log.i("ExtensionCrawlerAdapter", "[${extension.metadata.id}] [TIMING getSearchResults] ${System.currentTimeMillis() - start}ms (${mapped.size} novels)")
        mapped
    }

    override fun getListings(): List<ListingDto> = extension.getListings()

    override suspend fun getListingNovels(listingId: String, page: Int): List<Novel> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val results = extension.getListingNovels(listingId, page)
        val mapped = results.map { dto ->
            Novel(
                url = dto.url,
                title = dto.title,
                author = dto.author,
                coverUrl = dto.coverUrl,
                description = null,
                chapters = emptyList(),
                crawlerName = extension.metadata.id,
                coverHttpsUrl = dto.coverUrl
            )
        }
        android.util.Log.i("ExtensionCrawlerAdapter", "[${extension.metadata.id}] [TIMING getListingNovels '$listingId' p$page] ${System.currentTimeMillis() - start}ms (${mapped.size} novels)")
        mapped
    }

    override suspend fun searchNovels(query: String, page: Int): List<Novel> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val results = extension.search(query, page)
        val mapped = results.map { dto ->
            Novel(
                url = dto.url,
                title = dto.title,
                author = dto.author,
                coverUrl = dto.coverUrl,
                description = null,
                chapters = emptyList(),
                crawlerName = extension.metadata.id,
                coverHttpsUrl = dto.coverUrl
            )
        }
        android.util.Log.i("ExtensionCrawlerAdapter", "[${extension.metadata.id}] [TIMING searchNovels '$query' p$page] ${System.currentTimeMillis() - start}ms (${mapped.size} novels)")
        mapped
    }

    override suspend fun getNovelMetadata(novelUrl: String): Novel = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val novel = getNovelDetails(novelUrl).copy(chapters = emptyList())
        android.util.Log.i("ExtensionCrawlerAdapter", "[${extension.metadata.id}] [TIMING getNovelMetadata] ${System.currentTimeMillis() - start}ms")
        novel
    }

    override suspend fun getNovelDetails(novelUrl: String): Novel = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val novelDto = extension.getNovelDetails(novelUrl)
        val fetchDuration = System.currentTimeMillis() - start
        val mapStart = System.currentTimeMillis()
        val novel = Novel(
            url = novelDto.url,
            title = novelDto.title,
            author = novelDto.author,
            coverUrl = novelDto.coverUrl,
            description = novelDto.description,
            status = novelDto.status,
            chapters = novelDto.chapters.mapIndexed { idx, chDto ->
                Chapter(
                    id = 0,
                    url = chDto.url,
                    title = chDto.title,
                    index = if (chDto.index >= 0) chDto.index else idx + 1,
                    novelUrl = novelDto.url
                ).apply {
                    scanlationSource = chDto.scanlation?.takeIf { it.isNotBlank() } ?: extension.metadata.name
                }
            },
            crawlerName = extension.metadata.id,
            coverHttpsUrl = novelDto.coverUrl
        )
        val mapDuration = System.currentTimeMillis() - mapStart
        android.util.Log.i("ExtensionCrawlerAdapter", "[${extension.metadata.id}] [TIMING getNovelDetails] total=${System.currentTimeMillis() - start}ms (ext_fetch=${fetchDuration}ms, map=${mapDuration}ms, chapters=${novel.chapters.size})")
        novel
    }

    override suspend fun getChapterList(novelUrl: String): List<Chapter> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val chapters = getNovelDetails(novelUrl).chapters
        android.util.Log.i("ExtensionCrawlerAdapter", "[${extension.metadata.id}] [TIMING getChapterList] ${System.currentTimeMillis() - start}ms (${chapters.size} chapters)")
        chapters
    }

    override suspend fun getChapterContent(chapterUrl: String): String? = withContext(Dispatchers.IO) {
        extension.getChapterContent(chapterUrl)
    }
}
