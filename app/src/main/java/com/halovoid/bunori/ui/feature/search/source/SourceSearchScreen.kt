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
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.halovoid.bunori.api.core.crawler.Crawler
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.domain.models.SearchItem
import com.halovoid.bunori.ui.core.theme.BorderColor
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.DarkBackground
import com.halovoid.bunori.ui.core.theme.DarkSurface
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText
import com.halovoid.bunori.ui.feature.browse.BrowseViewModel
import com.halovoid.bunori.ui.feature.search.source.components.ListingTagRow
import com.halovoid.bunori.ui.feature.search.source.components.NovelCoverGrid
import com.halovoid.bunori.ui.feature.search.source.components.SourceSearchTopBar
import com.halovoid.bunori.ui.feature.source.webview.WebViewActivity
import com.halovoid.bunori.wasm.WamrHttpBridge

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
    val context = LocalContext.current
    val crawler = remember(sourceName) { CrawlerFactory.getCrawler(sourceName) }

    val openWebView: (() -> Unit)? = remember(crawler, context) {
        if (crawler != null && crawler.baseUrl.isNotBlank()) {
            {
                val targetUrl = WamrHttpBridge.lastCloudflareBlockedUrl ?: crawler.baseUrl
                val intent = Intent(context, WebViewActivity::class.java).apply {
                    putExtra("url", targetUrl)
                    putExtra("host", targetUrl.toUri().host ?: "")
                    putExtra("crawler_name", sourceName)
                }
                context.startActivity(intent)
            }
        } else null
    }

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
                focusRequester = focusRequester,
                onOpenWebView = openWebView
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

                    if (state.errorMessage != null && state.novels.isNotEmpty()) {
                        val isProtectionChallenge = checkIsProtectionChallenge(crawler, state.errorMessage)
                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isProtectionChallenge) Icons.Default.Shield else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (isProtectionChallenge) MaterialTheme.colorScheme.primary else SecondaryText.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isProtectionChallenge) "Cloudflare / DDoS-Guard challenge detected" else state.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryText,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (openWebView != null) {
                                    Text(
                                        text = "Open in WebView",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { openWebView() }
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

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
                    } else if (state.errorMessage != null && state.novels.isEmpty()) {
                        val isProtectionChallenge = checkIsProtectionChallenge(crawler, state.errorMessage)
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
                                Icon(
                                    imageVector = if (isProtectionChallenge) Icons.Default.Shield else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (isProtectionChallenge) MaterialTheme.colorScheme.primary else SecondaryText,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (isProtectionChallenge) "Verification Required" else "Unable to load content",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryText
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isProtectionChallenge) {
                                        "$sourceName requires passing a Cloudflare or DDoS-Guard browser challenge."
                                    } else {
                                        state.errorMessage
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryText
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (openWebView != null) {
                                        Button(
                                            onClick = openWebView,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Public,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open in WebView")
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Button(
                                        onClick = {
                                            if (state.selectedListingId == SourceSearchViewModel.SEARCH_LISTING_ID) {
                                                searchQuery.takeIf { it.isNotBlank() }?.let { viewModel.search(it.trim()) }
                                            } else {
                                                viewModel.selectListing(state.selectedListingId)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Retry")
                                    }
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
                    val isProtectionChallenge = checkIsProtectionChallenge(crawler, state.message)
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
                            Icon(
                                imageVector = if (isProtectionChallenge) Icons.Default.Shield else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isProtectionChallenge) MaterialTheme.colorScheme.primary else SecondaryText,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isProtectionChallenge) "Verification Required" else "Unable to load source",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isProtectionChallenge) {
                                    "$sourceName requires passing a Cloudflare or DDoS-Guard browser challenge."
                                } else {
                                    state.message
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryText
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (openWebView != null) {
                                    Button(
                                        onClick = openWebView,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Public,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open in WebView")
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Button(
                                    onClick = {
                                        if (searchQuery.isNotBlank()) {
                                            viewModel.search(searchQuery.trim())
                                        } else {
                                            viewModel.loadListings()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                                    shape = RoundedCornerShape(8.dp)
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
}

private fun checkIsProtectionChallenge(crawler: Crawler?, message: String?): Boolean {
    return crawler?.webviewNeeded == true ||
        WamrHttpBridge.lastCloudflareBlockedUrl != null ||
        message?.contains("cloudflare", ignoreCase = true) == true ||
        message?.contains("challenge", ignoreCase = true) == true ||
        message?.contains("ddos", ignoreCase = true) == true ||
        message?.contains("403", ignoreCase = true) == true ||
        message?.contains("blocked", ignoreCase = true) == true
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
