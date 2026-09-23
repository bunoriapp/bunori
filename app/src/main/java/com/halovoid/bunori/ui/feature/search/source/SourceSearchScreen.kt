package com.halovoid.bunori.ui.feature.search.source

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.domain.models.SearchItem
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.DarkBackground
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText
import com.halovoid.bunori.ui.feature.browse.BrowseViewModel
import com.halovoid.bunori.ui.feature.search.source.components.ListingTagRow
import com.halovoid.bunori.ui.feature.search.source.components.NovelCoverGrid
import com.halovoid.bunori.ui.feature.search.source.components.SourceSearchTopBar

@Composable
fun SourceSearchScreen(
    sourceName: String,
    initialQuery: String? = null,
    viewModel: SourceSearchViewModel,
    browseViewModel: BrowseViewModel,
    onBack: () -> Unit,
    onNavigateToDetail: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember(initialQuery) { mutableStateOf(initialQuery ?: "") }
    var isSearchMode by remember(initialQuery) { mutableStateOf(!initialQuery.isNullOrBlank()) }
    val isCompactMode by viewModel.searchCompactView.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val libraryUrls by browseViewModel.libraryUrls.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(sourceName, initialQuery) {
        viewModel.initialize(sourceName, initialQuery)
    }

    LaunchedEffect(isSearchMode) {
        if (isSearchMode) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    BackHandler {
        if (isSearchMode) {
            isSearchMode = false
        } else {
            onBack()
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            SourceSearchTopBar(
                sourceName = sourceName,
                isSearchMode = isSearchMode,
                onOpenSearch = {
                    isSearchMode = true
                },
                onCloseSearch = {
                    isSearchMode = false
                },
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = {
                    if (searchQuery.isNotBlank()) {
                        viewModel.search(searchQuery.trim())
                        keyboardController?.hide()
                    }
                },
                onClearQuery = {
                    searchQuery = ""
                    viewModel.clearSearch()
                    isSearchMode = false
                },
                onBack = {
                    if (isSearchMode) {
                        isSearchMode = false
                    } else {
                        onBack()
                    }
                },
                isCompactMode = isCompactMode,
                onToggleCompactMode = viewModel::setSearchCompactView,
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
            when (val state = uiState) {
                is SourceExploreState.Idle,
                is SourceExploreState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = BrandAccent,
                            strokeWidth = 2.5.dp
                        )
                    }
                }

                is SourceExploreState.Content -> {
                    // Listing chips (includes dynamic Search chip when searching)
                    ListingTagRow(
                        listings = state.listings,
                        selectedListingId = state.selectedListingId,
                        onSelectListing = { listingId ->
                            if (listingId == SourceSearchViewModel.SEARCH_LISTING_ID) {
                                isSearchMode = true
                            } else {
                                isSearchMode = false
                            }
                            viewModel.selectListing(listingId)
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (state.isLoadingContent) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = BrandAccent,
                                strokeWidth = 2.5.dp
                            )
                        }
                    } else if (state.errorMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "Unable to load content",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = state.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryText
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.selectListing(state.selectedListingId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    } else if (state.novels.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (state.selectedListingId == SourceSearchViewModel.SEARCH_LISTING_ID) {
                                        "No results found for \"$searchQuery\""
                                    } else if (state.listings.isEmpty()) {
                                        "Search in $sourceName to explore novels"
                                    } else {
                                        "No novels found in this category"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = PrimaryText
                                )
                                if (state.selectedListingId == SourceSearchViewModel.SEARCH_LISTING_ID) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Try searching with different keywords",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondaryText
                                    )
                                }
                            }
                        }
                    } else {
                        NovelCoverGrid(
                            novels = state.novels,
                            isCompactMode = isCompactMode,
                            libraryUrls = libraryUrls,
                            isLoadingMore = state.isLoadingMore,
                            hasMore = state.hasMore,
                            onLoadMore = viewModel::loadNextPage,
                            onItemClick = { item ->
                                handleNovelClick(
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

                is SourceExploreState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "Unable to load source",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryText
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (searchQuery.isNotBlank()) {
                                        viewModel.search(searchQuery.trim())
                                    } else {
                                        viewModel.loadListings()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun handleNovelClick(
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
