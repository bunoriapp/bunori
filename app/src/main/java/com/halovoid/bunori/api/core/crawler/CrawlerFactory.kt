package com.halovoid.bunori.api.core.crawler

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CrawlerFactory {
    private val staticCrawlers = listOf<Crawler>(

    )

    private var dynamicCrawlers = mutableListOf<Crawler>()
    
    private val _crawlersFlow = MutableStateFlow<List<Crawler>>(emptyList())
    val crawlersFlow: StateFlow<List<Crawler>> = _crawlersFlow.asStateFlow()

    init {
        _crawlersFlow.value = getCrawlers()
    }

    fun getCrawlers(): List<Crawler> = staticCrawlers + dynamicCrawlers

    fun getCrawler(identifier: String): Crawler? {
        if (identifier.isBlank()) return null
        val crawlers = getCrawlers()

        // 1. Match on full id (e.g. "lnreader.agit.xyz", "bext.novelfull")
        crawlers.find { it.id.equals(identifier, ignoreCase = true) }?.let { return it }

        // 2. Match on display name (e.g. "Agitoon", "NovelFull")
        crawlers.find { it.name.equals(identifier, ignoreCase = true) }?.let { return it }

        // 3. Match without prefix (e.g. "agit.xyz", "novelfull")
        val clean = identifier.substringAfter(".")
        crawlers.find { 
            it.id.substringAfter(".").equals(clean, ignoreCase = true) ||
            it.name.equals(clean, ignoreCase = true)
        }?.let { return it }

        return null
    }

    fun getCrawlerByUrl(url: String): Crawler? = getCrawlers().find { it.canHandle(url) }

    fun registerCrawlers(newCrawlers: List<Crawler>) {
        val filteredCrawlers = newCrawlers.filter { dynamic ->
            staticCrawlers.none { static -> static.id == dynamic.id }
        }.distinctBy {it.id}
        
        dynamicCrawlers.clear()
        dynamicCrawlers.addAll(filteredCrawlers)
        _crawlersFlow.value = getCrawlers()
    }
}
