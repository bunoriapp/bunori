package com.halovoid.bunori.api.core.crawler

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central registry of all crawlers (both static built-in and dynamic extension-based).
 *
 * Crawler IDs follow the multicatalog convention: `repoKey.rawId`
 * (e.g. `bunori_7cf2a3b1.novelbins`). The lookup methods handle both
 * fully-qualified and raw IDs transparently so callers never need to
 * know which repo an extension came from.
 */
object CrawlerFactory {
    private val staticCrawlers = listOf<Crawler>()
    private var dynamicCrawlers = mutableListOf<Crawler>()
    private val _crawlersFlow = MutableStateFlow<List<Crawler>>(emptyList())
    val crawlersFlow: StateFlow<List<Crawler>> = _crawlersFlow.asStateFlow()

    init {
        _crawlersFlow.value = getCrawlers()
    }

    fun getCrawlers(): List<Crawler> = staticCrawlers + dynamicCrawlers

    /**
     * Resolves a crawler by [identifier], which may be a fully-qualified
     * multicatalog ID (`repoKey.rawId`), a bare raw ID, or an extension name.
     *
     * Resolution order:
     * 1. Exact match on `Crawler.id`
     * 2. Exact match on `Crawler.name`
     * 3. Strip the repo prefix from [identifier] and match the raw portion
     *    against registered crawler IDs (both full and raw)
     * 4. Match the raw portion against crawler names
     */
    fun getCrawler(identifier: String): Crawler? {
        if (identifier.isBlank()) return null
        val crawlers = getCrawlers()
        if (crawlers.isEmpty()) return null

        // Tier 1: Exact match on ID
        crawlers.find { it.id.equals(identifier, ignoreCase = true) }?.let { return it }

        // Tier 2: Exact match on name
        crawlers.find { it.name.equals(identifier, ignoreCase = true) }?.let { return it }

        // Tier 3: Strip repo prefix → match raw ID
        // "bunori_7cf.novelbins" → rawId = "novelbins"
        val rawId = identifier.substringAfterLast('.')
        if (rawId.isNotBlank()) {
            // Direct raw ID match
            crawlers.find { it.id.equals(rawId, ignoreCase = true) }?.let { return it }

            // Cross-prefix match: crawler registered as "otherRepo.novelbins"
            crawlers.find {
                it.id.substringAfterLast('.').equals(rawId, ignoreCase = true)
            }?.let { return it }

            // Raw ID matches a crawler name
            crawlers.find { it.name.equals(rawId, ignoreCase = true) }?.let { return it }
        }

        return null
    }

    fun getCrawlerByUrl(url: String): Crawler? = getCrawlers().find { it.canHandle(url) }

    fun registerCrawlers(newCrawlers: List<Crawler>) {
        val filteredCrawlers = newCrawlers
            .filter { dynamic -> staticCrawlers.none { static -> static.id == dynamic.id } }
            .distinctBy { it.id }

        dynamicCrawlers.clear()
        dynamicCrawlers.addAll(filteredCrawlers)
        _crawlersFlow.value = getCrawlers()
    }
}
