package com.halovoid.bunori.ui.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.domain.models.SearchItem
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.browse.BrowseViewModel
import com.halovoid.bunori.ui.feature.search.components.*

sealed interface SearchDialogState {
    data object FailedSourcesSheet : SearchDialogState
}

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    browseViewModel: BrowseViewModel,
    onBack: () -> Unit,
    onNavigateToRequest: () -> Unit = {},
    onNavigateToSourceSearch: (String, String?) -> Unit = { _, _ -> },
    onNavigateToDetail: (String, String) -> Unit,
    initialSource: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeDialog by remember { mutableStateOf<SearchDialogState?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember(initialSource) { mutableStateOf(initialSource) }
    val isCompactMode by viewModel.searchCompactView.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val libraryUrls by browseViewModel.libraryUrls.collectAsStateWithLifecycle()
    val failedExtensions by viewModel.failedExtensions.collectAsStateWithLifecycle()
    val installedCrawlers by CrawlerFactory.crawlersFlow.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(initialSource) {
        if (initialSource != null) {
            viewModel.setSelectedSource(initialSource)
        }
    }

    val failedSearchSources = remember(searchState) {
        (searchState as? SearchState.Searching)?.sourceStates
            ?.filterValues { it is SourceSearchStatus.Error }
            ?.keys?.toList() ?: emptyList()
    }
    val allFailedSources = remember(failedExtensions, failedSearchSources) {
        (failedExtensions + failedSearchSources).distinct()
    }

    BackHandler {
        searchQuery = ""
        viewModel.resetState()
        onBack()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            SearchTopBar(
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
                selectedSource = selectedSource,
                onSelectSource = { source ->
                    selectedSource = source
                    viewModel.setSelectedSource(source)
                    if (searchQuery.isNotBlank()) {
                        viewModel.search(searchQuery.trim(), source)
                    }
                },
                installedCrawlers = installedCrawlers,
                onSearch = {
                    if (searchQuery.isNotBlank()) {
                        viewModel.search(searchQuery.trim(), selectedSource)
                        keyboardController?.hide()
                    }
                },
                onBack = {
                    searchQuery = ""
                    viewModel.resetState()
                    onBack()
                },
                onClearQuery = {
                    searchQuery = ""
                    viewModel.resetState()
                },
                focusRequester = focusRequester
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
        ) {

            // 2. Failed Sources Banner Notice
            FailedSourcesBanner(
                allFailedSources = allFailedSources,
                isSearching = searchState is SearchState.Searching,
                onResolveSheet = { activeDialog = SearchDialogState.FailedSourcesSheet },
                onRetryFailed = viewModel::retryFailed,
                onRetrySingleSource = viewModel::retrySource,
                context = context
            )

            // 3. Search Results Sub-Header Controls
            if (searchState is SearchState.Searching) {
                val state = searchState as SearchState.Searching
                SearchControlsHeader(
                    sourceStates = state.sourceStates,
                    selectedSource = selectedSource,
                    isCompactMode = isCompactMode,
                    onToggleCompactMode = viewModel::setSearchCompactView
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 4. Search Content Body
            SearchBodyContent(
                searchState = searchState,
                isCompactMode = isCompactMode,
                libraryUrls = libraryUrls,
                onNavigateToRequest = onNavigateToRequest,
                onDrillDown = { source ->
                    onNavigateToSourceSearch(source, searchQuery.takeIf { it.isNotBlank() })
                },
                onItemClick = { item ->
                    handleSearchResultClick(
                        item = item,
                        isInLibrary = libraryUrls.contains(item.url),
                        onNavigateToDetail = onNavigateToDetail,
                        browseViewModel = browseViewModel
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    // 5. Failed Sources Sheet
    when (activeDialog) {
        is SearchDialogState.FailedSourcesSheet -> {
            FailedSourcesSheet(
                allFailedSources = allFailedSources,
                onRetrySingleSource = viewModel::retrySource,
                onRetryAllFailed = viewModel::retryFailed,
                onDismiss = { activeDialog = null },
                context = context
            )
        }
        null -> Unit
    }
}

private fun handleSearchResultClick(
    item: SearchItem,
    isInLibrary: Boolean,
    onNavigateToDetail: (String, String) -> Unit,
    browseViewModel: BrowseViewModel
) {
    if (isInLibrary) {
        onNavigateToDetail(item.source, item.url)
    } else {
        val novel = Novel(
            url = item.url,
            title = item.title,
            description = item.description,
            coverUrl = item.imageUrl,
            coverHttpsUrl = item.imageUrl,
            crawlerName = item.source,
            inLibrary = false,
            refreshExpiry = 0L
        )
        browseViewModel.saveNovelStub(novel) {
            onNavigateToDetail(item.source, item.url)
        }
    }
}
