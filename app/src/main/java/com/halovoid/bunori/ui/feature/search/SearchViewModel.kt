package com.halovoid.bunori.ui.feature.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.domain.models.SearchItem
import com.halovoid.bunori.ui.core.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SearchState {
    object Idle : SearchState()
    data class Searching(
        val query: String,
        val sourceStates: Map<String, SourceSearchStatus>,
        val isComplete: Boolean = false,
        val isEmpty: Boolean = false
    ) : SearchState()
    data class Error(val message: String) : SearchState()
}

sealed class SourceSearchStatus {
    object Loading : SourceSearchStatus()
    data class Success(val items: List<SearchItem>) : SourceSearchStatus()
    data class Error(val message: String) : SourceSearchStatus()
}

class SearchViewModel(
    application: Application,
    private val preferenceRepository: PreferenceRepository = PreferenceRepository.getInstance(application)
) : AndroidViewModel(application) {

    val searchCompactView: StateFlow<Boolean> = preferenceRepository.searchCompactView
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun setSearchCompactView(compact: Boolean) {
        viewModelScope.launch {
            preferenceRepository.setSearchCompactView(compact)
        }
    }

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _selectedSource = MutableStateFlow<String?>(null)
    val selectedSource: StateFlow<String?> = _selectedSource.asStateFlow()

    fun setSelectedSource(source: String?) {
        _selectedSource.value = source
    }

    val failedExtensions: StateFlow<List<String>> =
        com.halovoid.bunori.extension.manager.ExtensionManager.getInstance(application).failedExtensions

    fun search(query: String, targetSource: String? = _selectedSource.value) {
        if (query.isBlank()) return

        viewModelScope.launch {
            val allCrawlers = try {
                CrawlerFactory.getCrawlers()
            } catch (e: Exception) {
                _searchState.value = SearchState.Error(e.message ?: "Failed to retrieve crawlers")
                return@launch
            }

            val crawlers = if (!targetSource.isNullOrBlank()) {
                allCrawlers.filter { it.name.equals(targetSource, ignoreCase = true) }
            } else {
                allCrawlers
            }

            if (crawlers.isEmpty()) {
                _searchState.value = SearchState.Error(
                    if (!targetSource.isNullOrBlank()) "Source '$targetSource' not found" else "No sources available"
                )
                return@launch
            }

            val initialStates = crawlers.associate { it.name to SourceSearchStatus.Loading }
            _searchState.value = SearchState.Searching(query, initialStates)

            crawlers.forEach { crawler ->
                viewModelScope.launch {
                    try {
                        val results = withContext(Dispatchers.IO) {
                            crawler.getSearchResults(query)
                        }
                        AppLog.d(
                            "SearchViewModel",
                            "Crawler '${crawler.name}' returned ${results.size} results for query '$query': ${results.map { "${it.title} (${it.url})" }}"
                        )
                        val searchItems = results.map { novel ->
                            SearchItem(
                                title = novel.title,
                                source = novel.crawlerName,
                                url = novel.url,
                                description = novel.description ?: "",
                                score = 0.0,
                                imageUrl = novel.coverHttpsUrl ?: novel.coverUrl
                            )
                        }
                        updateSourceState(crawler.name, SourceSearchStatus.Success(searchItems))
                    } catch (e: Exception) {
                        AppLog.e("SearchViewModel", "Error searching ${crawler.name}: ${e.message}", e)
                        updateSourceState(crawler.name, SourceSearchStatus.Error(e.message ?: "Unknown error occurred"))
                    }
                }
            }
        }
    }

    private fun updateSourceState(sourceName: String, status: SourceSearchStatus) {
        val currentState = _searchState.value
        if (currentState is SearchState.Searching) {
            val updatedMap = currentState.sourceStates.toMutableMap().apply {
                put(sourceName, status)
            }

            val allDone = updatedMap.all { it.value !is SourceSearchStatus.Loading }
            val allEmpty = updatedMap.all {
                val stat = it.value
                stat is SourceSearchStatus.Success && stat.items.isEmpty()
            }

            _searchState.value = currentState.copy(
                sourceStates = updatedMap,
                isComplete = allDone,
                isEmpty = allEmpty
            )
        }
    }

    fun retryFailed() {
        val currentState = _searchState.value as? SearchState.Searching ?: return
        val failedSources = currentState.sourceStates
            .filterValues { it is SourceSearchStatus.Error }
            .keys

        if (failedSources.isEmpty()) return

        val query = currentState.query
        val crawlers = try {
            CrawlerFactory.getCrawlers().filter { crawler ->
                failedSources.any { it.equals(crawler.name, ignoreCase = true) }
            }
        } catch (_: Exception) {
            emptyList()
        }

        if (crawlers.isEmpty()) return

        val updatedMap = currentState.sourceStates.toMutableMap().apply {
            crawlers.forEach { put(it.name, SourceSearchStatus.Loading) }
        }
        _searchState.value = currentState.copy(
            sourceStates = updatedMap,
            isComplete = false
        )

        crawlers.forEach { crawler ->
            viewModelScope.launch {
                try {
                    val results = withContext(Dispatchers.IO) {
                        crawler.getSearchResults(query)
                    }
                    val searchItems = results.map { novel ->
                        SearchItem(
                            title = novel.title,
                            source = novel.crawlerName,
                            url = novel.url,
                            description = novel.description ?: "",
                            score = 0.0,
                            imageUrl = novel.coverHttpsUrl ?: novel.coverUrl
                        )
                    }
                    updateSourceState(crawler.name, SourceSearchStatus.Success(searchItems))
                } catch (e: Exception) {
                    AppLog.e("SearchViewModel", "Error searching ${crawler.name}: ${e.message}", e)
                    updateSourceState(crawler.name, SourceSearchStatus.Error(e.message ?: "Unknown error occurred"))
                }
            }
        }
    }

    fun retrySource(sourceName: String) {
        val currentState = _searchState.value as? SearchState.Searching ?: return
        val query = currentState.query
        val crawler = try {
            CrawlerFactory.getCrawlers().find { it.name.equals(sourceName, ignoreCase = true) }
        } catch (_: Exception) {
            null
        } ?: return

        val updatedMap = currentState.sourceStates.toMutableMap().apply {
            put(crawler.name, SourceSearchStatus.Loading)
        }
        _searchState.value = currentState.copy(
            sourceStates = updatedMap,
            isComplete = false
        )

        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    crawler.getSearchResults(query)
                }
                val searchItems = results.map { novel ->
                    SearchItem(
                        title = novel.title,
                        source = novel.crawlerName,
                        url = novel.url,
                        description = novel.description ?: "",
                        score = 0.0,
                        imageUrl = novel.coverHttpsUrl ?: novel.coverUrl
                    )
                }
                updateSourceState(crawler.name, SourceSearchStatus.Success(searchItems))
            } catch (e: Exception) {
                AppLog.e("SearchViewModel", "Error retrying ${crawler.name}: ${e.message}", e)
                updateSourceState(crawler.name, SourceSearchStatus.Error(e.message ?: "Unknown error occurred"))
            }
        }
    }

    fun resetState() {
        _searchState.value = SearchState.Idle
    }
}
