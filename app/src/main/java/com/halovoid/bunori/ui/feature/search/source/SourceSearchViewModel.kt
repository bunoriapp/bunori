package com.halovoid.bunori.ui.feature.search.source

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halovoid.bunori.api.core.crawler.Crawler
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.domain.models.SearchItem
import com.halovoid.bunori.extension.api.models.ListingDto
import com.halovoid.bunori.ui.core.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SourceExploreState {
    object Idle : SourceExploreState()
    object Loading : SourceExploreState()
    data class Content(
        val listings: List<ListingDto>,
        val selectedListingId: String,
        val novels: List<SearchItem>,
        val isLoadingContent: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val errorMessage: String? = null
    ) : SourceExploreState()
    data class Error(val message: String) : SourceExploreState()
}

class SourceSearchViewModel(
    application: Application,
    private val preferenceRepository: PreferenceRepository = PreferenceRepository.getInstance(application)
) : AndroidViewModel(application) {

    companion object {
        const val SEARCH_LISTING_ID = "__search__"
    }

    private val _uiState = MutableStateFlow<SourceExploreState>(SourceExploreState.Idle)
    val uiState: StateFlow<SourceExploreState> = _uiState.asStateFlow()

    private val _currentSource = MutableStateFlow<String?>(null)
    val currentSource: StateFlow<String?> = _currentSource.asStateFlow()

    private var crawler: Crawler? = null

    // Cache of novels per listing tag
    private val listingCache = mutableMapOf<String, MutableList<SearchItem>>()
    private val listingPageMap = mutableMapOf<String, Int>()
    private val listingHasMoreMap = mutableMapOf<String, Boolean>()
    private var availableListings: List<ListingDto> = emptyList()

    // Search state
    private var activeSearchQuery: String? = null
    private val searchCache = mutableListOf<SearchItem>()
    private var searchPage = 1
    private var searchHasMore = true

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

    fun initialize(sourceName: String, initialQuery: String? = null) {
        if (_currentSource.value == sourceName && initialQuery.isNullOrBlank()) {
            return
        }
        _currentSource.value = sourceName
        crawler = CrawlerFactory.getCrawler(sourceName)

        if (crawler == null) {
            _uiState.value = SourceExploreState.Error("Source '$sourceName' not found or not installed")
            return
        }

        listingCache.clear()
        listingPageMap.clear()
        listingHasMoreMap.clear()
        searchCache.clear()
        searchPage = 1
        searchHasMore = true
        activeSearchQuery = null
        availableListings = emptyList()

        if (!initialQuery.isNullOrBlank()) {
            search(initialQuery.trim())
        } else {
            loadListings()
        }
    }

    private fun getCombinedListings(): List<ListingDto> {
        return if (!activeSearchQuery.isNullOrBlank()) {
            listOf(ListingDto(id = SEARCH_LISTING_ID, name = "Search")) + availableListings
        } else {
            availableListings
        }
    }

    fun loadListings(forcedListingId: String? = null) {
        val cr = crawler ?: return
        viewModelScope.launch {
            try {
                if (availableListings.isEmpty()) {
                    _uiState.value = SourceExploreState.Loading
                    availableListings = withContext(Dispatchers.IO) {
                        cr.getListings()
                    }
                }

                if (availableListings.isEmpty()) {
                    availableListings = listOf(ListingDto(id = "popular", name = "Popular"))
                }

                val activeId = forcedListingId ?: availableListings.first().id
                val cached = listingCache[activeId]

                if (cached != null) {
                    _uiState.value = SourceExploreState.Content(
                        listings = getCombinedListings(),
                        selectedListingId = activeId,
                        novels = cached.toList(),
                        isLoadingContent = false,
                        hasMore = listingHasMoreMap[activeId] ?: true
                    )
                } else {
                    _uiState.value = SourceExploreState.Content(
                        listings = getCombinedListings(),
                        selectedListingId = activeId,
                        novels = emptyList(),
                        isLoadingContent = true,
                        hasMore = true
                    )
                    fetchListingPage(activeId, page = 1)
                }
            } catch (e: Exception) {
                AppLog.e("SourceSearchViewModel", "Failed to load listings for ${cr.name}: ${e.message}", e)
                _uiState.value = SourceExploreState.Error(e.message ?: "Failed to load listings")
            }
        }
    }

    fun selectListing(listingId: String) {
        if (listingId == SEARCH_LISTING_ID) {
            val query = activeSearchQuery
            if (!query.isNullOrBlank()) {
                if (searchCache.isNotEmpty()) {
                    _uiState.value = SourceExploreState.Content(
                        listings = getCombinedListings(),
                        selectedListingId = SEARCH_LISTING_ID,
                        novels = searchCache.toList(),
                        isLoadingContent = false,
                        hasMore = searchHasMore
                    )
                } else {
                    search(query)
                }
            }
            return
        }

        val cached = listingCache[listingId]
        if (cached != null) {
            _uiState.value = SourceExploreState.Content(
                listings = getCombinedListings(),
                selectedListingId = listingId,
                novels = cached.toList(),
                isLoadingContent = false,
                hasMore = listingHasMoreMap[listingId] ?: true
            )
        } else {
            _uiState.value = SourceExploreState.Content(
                listings = getCombinedListings(),
                selectedListingId = listingId,
                novels = emptyList(),
                isLoadingContent = true,
                hasMore = true
            )
            fetchListingPage(listingId, page = 1)
        }
    }

    private fun fetchListingPage(listingId: String, page: Int) {
        val cr = crawler ?: return
        viewModelScope.launch {
            try {
                val novels = withContext(Dispatchers.IO) {
                    cr.getListingNovels(listingId, page)
                }
                val items = novels.map { novel ->
                    SearchItem(
                        title = novel.title,
                        source = novel.crawlerName,
                        url = novel.url,
                        description = novel.description ?: "",
                        score = 0.0,
                        imageUrl = novel.coverHttpsUrl ?: novel.coverUrl
                    )
                }

                val currentList = listingCache.getOrPut(listingId) { mutableListOf() }
                if (page == 1) {
                    currentList.clear()
                }
                currentList.addAll(items)
                listingPageMap[listingId] = page
                val hasMore = items.isNotEmpty()
                listingHasMoreMap[listingId] = hasMore

                _uiState.value = SourceExploreState.Content(
                    listings = getCombinedListings(),
                    selectedListingId = listingId,
                    novels = currentList.toList(),
                    isLoadingContent = false,
                    isLoadingMore = false,
                    hasMore = hasMore
                )
            } catch (e: Exception) {
                AppLog.e("SourceSearchViewModel", "Error fetching listing '$listingId' p$page: ${e.message}", e)
                _uiState.value = SourceExploreState.Content(
                    listings = getCombinedListings(),
                    selectedListingId = listingId,
                    novels = listingCache[listingId]?.toList() ?: emptyList(),
                    isLoadingContent = false,
                    isLoadingMore = false,
                    hasMore = false,
                    errorMessage = e.message ?: "Failed to load category content"
                )
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            clearSearch()
            return
        }

        val cr = crawler ?: return
        activeSearchQuery = query
        searchPage = 1
        searchCache.clear()

        viewModelScope.launch {
            try {
                if (availableListings.isEmpty()) {
                    availableListings = withContext(Dispatchers.IO) {
                        cr.getListings()
                    }
                    if (availableListings.isEmpty()) {
                        availableListings = listOf(ListingDto(id = "popular", name = "Popular"))
                    }
                }

                _uiState.value = SourceExploreState.Content(
                    listings = getCombinedListings(),
                    selectedListingId = SEARCH_LISTING_ID,
                    novels = emptyList(),
                    isLoadingContent = true,
                    hasMore = true
                )

                val results = withContext(Dispatchers.IO) {
                    cr.searchNovels(query, page = 1)
                }
                val items = results.map { novel ->
                    SearchItem(
                        title = novel.title,
                        source = novel.crawlerName,
                        url = novel.url,
                        description = novel.description ?: "",
                        score = 0.0,
                        imageUrl = novel.coverHttpsUrl ?: novel.coverUrl
                    )
                }

                searchCache.addAll(items)
                searchHasMore = items.isNotEmpty()

                _uiState.value = SourceExploreState.Content(
                    listings = getCombinedListings(),
                    selectedListingId = SEARCH_LISTING_ID,
                    novels = items,
                    isLoadingContent = false,
                    hasMore = searchHasMore
                )
            } catch (e: Exception) {
                AppLog.e("SourceSearchViewModel", "Error searching '$query': ${e.message}", e)
                _uiState.value = SourceExploreState.Content(
                    listings = getCombinedListings(),
                    selectedListingId = SEARCH_LISTING_ID,
                    novels = emptyList(),
                    isLoadingContent = false,
                    hasMore = false,
                    errorMessage = e.message ?: "Search failed"
                )
            }
        }
    }

    fun loadNextPage() {
        val currentState = _uiState.value as? SourceExploreState.Content ?: return
        if (currentState.isLoadingContent || currentState.isLoadingMore || !currentState.hasMore) return

        if (currentState.selectedListingId == SEARCH_LISTING_ID) {
            loadNextSearchPage()
        } else {
            loadNextListingPage()
        }
    }

    private fun loadNextListingPage() {
        val currentState = _uiState.value as? SourceExploreState.Content ?: return
        val listingId = currentState.selectedListingId
        val nextPage = (listingPageMap[listingId] ?: 1) + 1
        val cr = crawler ?: return

        _uiState.value = currentState.copy(isLoadingMore = true)

        viewModelScope.launch {
            try {
                val novels = withContext(Dispatchers.IO) {
                    cr.getListingNovels(listingId, nextPage)
                }
                val items = novels.map { novel ->
                    SearchItem(
                        title = novel.title,
                        source = novel.crawlerName,
                        url = novel.url,
                        description = novel.description ?: "",
                        score = 0.0,
                        imageUrl = novel.coverHttpsUrl ?: novel.coverUrl
                    )
                }

                val currentList = listingCache.getOrPut(listingId) { mutableListOf() }
                currentList.addAll(items)
                listingPageMap[listingId] = nextPage
                val hasMore = items.isNotEmpty()
                listingHasMoreMap[listingId] = hasMore

                _uiState.value = currentState.copy(
                    novels = currentList.toList(),
                    isLoadingMore = false,
                    hasMore = hasMore
                )
            } catch (e: Exception) {
                AppLog.e("SourceSearchViewModel", "Failed to load next listing page: ${e.message}", e)
                _uiState.value = currentState.copy(isLoadingMore = false)
            }
        }
    }

    private fun loadNextSearchPage() {
        val currentState = _uiState.value as? SourceExploreState.Content ?: return
        val query = activeSearchQuery ?: return
        val nextPage = searchPage + 1
        val cr = crawler ?: return

        _uiState.value = currentState.copy(isLoadingMore = true)

        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    cr.searchNovels(query, page = nextPage)
                }
                val items = results.map { novel ->
                    SearchItem(
                        title = novel.title,
                        source = novel.crawlerName,
                        url = novel.url,
                        description = novel.description ?: "",
                        score = 0.0,
                        imageUrl = novel.coverHttpsUrl ?: novel.coverUrl
                    )
                }

                searchPage = nextPage
                searchCache.addAll(items)
                val hasMore = items.isNotEmpty()
                searchHasMore = hasMore

                _uiState.value = currentState.copy(
                    novels = searchCache.toList(),
                    isLoadingMore = false,
                    hasMore = hasMore
                )
            } catch (e: Exception) {
                AppLog.e("SourceSearchViewModel", "Failed to load next search page: ${e.message}", e)
                _uiState.value = currentState.copy(isLoadingMore = false)
            }
        }
    }

    fun clearSearch() {
        activeSearchQuery = null
        searchCache.clear()
        searchPage = 1
        searchHasMore = true
        loadListings()
    }
}
