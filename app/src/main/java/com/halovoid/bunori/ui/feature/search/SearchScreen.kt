package com.halovoid.bunori.ui.feature.search

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.domain.models.Novel
import com.halovoid.bunori.domain.models.SearchItem
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.crawler.webview.WebViewActivity
import com.halovoid.bunori.ui.feature.browse.BrowseViewModel
import com.halovoid.bunori.ui.feature.browse.components.CompactSearchResultCard
import com.halovoid.bunori.ui.feature.browse.components.SearchResultCard
import com.halovoid.bunori.ui.feature.browse.components.SourceHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    browseViewModel: BrowseViewModel,
    onBack: () -> Unit,
    onNavigateToRequest: () -> Unit = {},
    onNavigateToDetail: (String, String) -> Unit,
    initialSource: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showFailedSourcesSheet by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember(initialSource) { mutableStateOf(initialSource) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val isCompactMode by viewModel.searchCompactView.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val libraryUrls by browseViewModel.libraryUrls.collectAsStateWithLifecycle()
    val failedExtensions by viewModel.failedExtensions.collectAsStateWithLifecycle()

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
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .padding(horizontal = 14.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Search Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = DarkSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSearchFocused) BrandAccent.copy(alpha = 0.5f) else BorderColor.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            viewModel.resetState()
                            onBack()
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = if (selectedSource != null) "Search in $selectedSource..." else "Search for novels...",
                                color = SecondaryText.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                color = PrimaryText,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(BrandAccent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        viewModel.search(searchQuery.trim(), selectedSource)
                                        keyboardController?.hide()
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { isSearchFocused = it.isFocused }
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                viewModel.resetState()
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = SecondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                viewModel.search(searchQuery.trim(), selectedSource)
                                keyboardController?.hide()
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TravelExplore,
                            contentDescription = "Search",
                            tint = if (searchQuery.isNotBlank()) BrandAccent else SecondaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Filter chip row below the search input
            selectedSource?.let { source ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, BrandAccent.copy(alpha = 0.7f)),
                        modifier = Modifier.clickable {
                            selectedSource = null
                            viewModel.setSelectedSource(null)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = source,
                                style = MaterialTheme.typography.labelMedium,
                                color = PrimaryText,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear source filter",
                                tint = SecondaryText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // 2. Small Note at top if any source failed to load (NOT IN RED)
            if (allFailedSources.isNotEmpty()) {
                val singleFailedSource = if (allFailedSources.size == 1) allFailedSources.first() else null
                val singleCrawler = remember(singleFailedSource) {
                    singleFailedSource?.let { src ->
                        CrawlerFactory.getCrawlers().find { it.name.equals(src, ignoreCase = true) }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (singleCrawler?.webviewNeeded == true) Icons.Default.Shield else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (singleCrawler?.webviewNeeded == true) MaterialTheme.colorScheme.primary else SecondaryText.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (singleFailedSource != null) {
                                "$singleFailedSource could not be reached"
                            } else {
                                "${allFailedSources.size} sources could not be reached"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )

                        if (singleCrawler != null && singleCrawler.baseUrl.isNotBlank()) {
                            Text(
                                text = if (singleCrawler.webviewNeeded == true) "Open in WebView" else "WebView",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val targetUrl = singleCrawler.baseUrl
                                        val intent = Intent(context, WebViewActivity::class.java).apply {
                                            putExtra("url", targetUrl)
                                            putExtra("host", singleCrawler.baseUrl.toUri().host ?: "")
                                        }
                                        context.startActivity(intent)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        } else if (allFailedSources.size > 1) {
                            Text(
                                text = "Resolve",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showFailedSourcesSheet = true }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        if (searchState is SearchState.Searching) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Retry",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { viewModel.retryFailed() }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // 3. Search Results Sub-Header & Controls Bar (Grid / List view toggle)
            if (searchState is SearchState.Searching) {
                val state = searchState as SearchState.Searching
                val allDone = state.sourceStates.all { it.value !is SourceSearchStatus.Loading }
                val totalNovels = state.sourceStates.values.sumOf {
                    (it as? SourceSearchStatus.Success)?.items?.size ?: 0
                }
                val totalSources = state.sourceStates.size
                val completedSources = state.sourceStates.values.count { it !is SourceSearchStatus.Loading }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (!allDone) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = BrandAccent,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = if (selectedSource != null) "Searching $selectedSource..." else "Searching ($completedSources/$totalSources sources)...",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = if (totalNovels > 0) {
                                    if (selectedSource != null) "$totalNovels results in $selectedSource" else "$totalNovels results across $totalSources sources"
                                } else {
                                    "Search completed"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryText,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.setSearchCompactView(!isCompactMode) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isCompactMode) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = if (isCompactMode) "Switch to Comfortable View" else "Switch to Compact View",
                            tint = SecondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Search Content
            Box(modifier = Modifier.weight(1f)) {
                when (val state = searchState) {
                    is SearchState.Idle -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = SecondaryText.copy(alpha = 0.25f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Search across all sources",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = PrimaryText.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Enter a title or keyword to find novels in real-time",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    is SearchState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(state.message, color = SecondaryText, modifier = Modifier.padding(16.dp))
                        }
                    }
                    is SearchState.Searching -> {
                        val allDone = state.sourceStates.all { it.value !is SourceSearchStatus.Loading }
                        val allEmpty = state.sourceStates.all {
                            val status = it.value
                            status is SourceSearchStatus.Success && status.items.isEmpty()
                        }

                        if (allDone && allEmpty) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = SecondaryText.copy(alpha = 0.3f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No results found for \"${state.query}\"", color = SecondaryText)
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Did not find your novel? Try ", color = SecondaryText, fontSize = 14.sp)
                                    TextButton(
                                        onClick = onNavigateToRequest,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Requesting", color = BrandAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                verticalArrangement = if (isCompactMode) Arrangement.spacedBy(4.dp) else Arrangement.spacedBy(24.dp)
                            ) {
                                state.sourceStates.forEach { (source, status) ->
                                    val count = when (status) {
                                        is SourceSearchStatus.Success -> status.items.size
                                        else -> 0
                                    }

                                    item(key = "header_$source") {
                                        SourceHeader(source = source, count = count)
                                    }

                                    when (status) {
                                        is SourceSearchStatus.Loading -> {
                                            item(key = "loading_$source") {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(24.dp),
                                                        color = BrandAccent,
                                                        strokeWidth = 2.dp
                                                    )
                                                }
                                            }
                                        }
                                        is SourceSearchStatus.Error -> {
                                            item(key = "error_$source") {
                                                Text(
                                                    text = "Could not reach source",
                                                    color = SecondaryText.copy(alpha = 0.6f),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            }
                                        }
                                        is SourceSearchStatus.Success -> {
                                            if (status.items.isEmpty()) {
                                                item(key = "empty_$source") {
                                                    Text(
                                                        text = "No results found",
                                                        color = SecondaryText.copy(alpha = 0.5f),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.padding(vertical = 8.dp)
                                                    )
                                                }
                                            } else {
                                                if (isCompactMode) {
                                                    itemsIndexed(status.items, key = { index, item -> "${source}_${item.url}_$index" }) { index, item ->
                                                        val isInLibrary = libraryUrls.contains(item.url)
                                                        CompactSearchResultCard(
                                                            item = item,
                                                            isInLibrary = isInLibrary,
                                                            onClick = {
                                                                handleSearchResultClick(
                                                                    item = item,
                                                                    isInLibrary = isInLibrary,
                                                                    onNavigateToDetail = onNavigateToDetail,
                                                                    browseViewModel = browseViewModel
                                                                )
                                                            }
                                                        )
                                                    }
                                                } else {
                                                    item(key = "row_$source") {
                                                        LazyRow(
                                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                            contentPadding = PaddingValues(bottom = 8.dp)
                                                        ) {
                                                            itemsIndexed(status.items, key = { index, item -> "${source}_${item.url}_$index" }) { index, item ->
                                                                val isInLibrary = libraryUrls.contains(item.url)
                                                                SearchResultCard(
                                                                    item = item,
                                                                    isInLibrary = isInLibrary,
                                                                    onClick = {
                                                                        handleSearchResultClick(
                                                                            item = item,
                                                                            isInLibrary = isInLibrary,
                                                                            onNavigateToDetail = onNavigateToDetail,
                                                                            browseViewModel = browseViewModel
                                                                        )
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (allDone && !allEmpty) {
                                    item(key = "asking_request") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 24.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Did not find your novel? Try ", color = SecondaryText, fontSize = 14.sp)
                                            TextButton(
                                                onClick = onNavigateToRequest,
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("Requesting", color = BrandAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFailedSourcesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFailedSourcesSheet = false },
            containerColor = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Sources Could Not Be Reached",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Some sources may require Cloudflare security clearance in WebView.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText
                )
                Spacer(modifier = Modifier.height(16.dp))

                allFailedSources.forEach { sourceName ->
                    val crawler = remember(sourceName) {
                        CrawlerFactory.getCrawlers().find { it.name.equals(sourceName, ignoreCase = true) }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sourceName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = PrimaryText
                            )
                            if (crawler?.webviewNeeded == true) {
                                Text(
                                    text = "Security check required",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        if (crawler != null && crawler.baseUrl.isNotBlank()) {
                            Button(
                                onClick = {
                                    val targetUrl = crawler.baseUrl
                                    val intent = Intent(context, WebViewActivity::class.java).apply {
                                        putExtra("url", targetUrl)
                                        putExtra("host", crawler.baseUrl.toUri().host ?: "")
                                    }
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Open in WebView", fontSize = 12.sp)
                            }
                        }
                    }
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.2f))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        showFailedSourcesSheet = false
                        viewModel.retryFailed()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Retry Failed Searches")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
