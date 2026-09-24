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

    fun getCrawler(name: String): Crawler? = getCrawlers().find { it.id.equals(name, ignoreCase =true) }

    // fallback mechanism if metadata for the crawler name does not appear
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
