package com.halovoid.bunori.ui.feature.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.browse.components.*
import com.halovoid.bunori.ui.feature.source.SourceScreen
import com.halovoid.bunori.ui.feature.source.SourceViewModel
import com.halovoid.bunori.ui.feature.activity.components.JobActionHandler
import com.halovoid.bunori.ui.feature.source.components.SourcesTabContent
import kotlinx.coroutines.launch

enum class BrowseTab(val title: String) {
    SOURCES("Sources"),
    EXTENSIONS("Extensions")
}

/**
 * Modern BrowseScreen adhering to Bunori's dark aesthetic guidelines:
 * - Minimalist Top App Bar with large left-aligned "Browse" title, Search, Refresh, and Settings
 * - 2 full-width tabs: Sources, Extensions (with update badge)
 * - Clean content layout fitting seamlessly into Bunori's themes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onNavigateToSearch: (String?) -> Unit = {},
    onNavigateToExtensionSettings: () -> Unit = {},
    onNavigateToExtensionInfo: ((String) -> Unit)? = null,
    viewModel: BrowseViewModel,
    sourceViewModel: SourceViewModel
) {
    val pagerState = rememberPagerState(
        initialPage = sourceViewModel.selectedTabOrdinal.coerceIn(0, BrowseTab.entries.size - 1),
        pageCount = { BrowseTab.entries.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = BrowseTab.entries.getOrElse(pagerState.currentPage) { BrowseTab.SOURCES }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(pagerState.currentPage) {
        sourceViewModel.selectedTabOrdinal = pagerState.currentPage
        if (selectedTab == BrowseTab.SOURCES && isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        }
    }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    val updatesCount by sourceViewModel.updatesCount.collectAsStateWithLifecycle()
    val failedExtensions by sourceViewModel.failedExtensions.collectAsStateWithLifecycle()
    val extensionItems by sourceViewModel.extensionItems.collectAsStateWithLifecycle()
    val installedSources = remember(extensionItems) {
        extensionItems.filter { it.isInstalled }.sortedBy { it.name.lowercase() }
    }

    JobActionHandler(
        onResolveWebview = { id, url -> viewModel.resolveWebView(id, url) }
    ) {
        Scaffold(
            containerColor = DarkBackground
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                BrowseTopBar(
                    selectedTab = selectedTab,
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onCloseSearch = {
                        isSearchActive = false
                        searchQuery = ""
                    },
                    onOpenSearch = { isSearchActive = true },
                    onNavigateToSearch = onNavigateToSearch,
                    onRefreshCatalog = { sourceViewModel.refreshCatalog(forceNetwork = true) },
                    onNavigateToExtensionSettings = onNavigateToExtensionSettings
                )

                BrowseTabRow(
                    selectedTab = selectedTab,
                    updatesCount = updatesCount,
                    onTabSelected = { tab ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(tab.ordinal)
                        }
                    }
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    when (BrowseTab.entries.getOrElse(page) { BrowseTab.SOURCES }) {
                        BrowseTab.SOURCES -> {
                            SourcesTabContent(
                                installedSources = installedSources,
                                failedExtensions = failedExtensions,
                                onNavigateToSearch = onNavigateToSearch,
                                onNavigateToExtensions = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(BrowseTab.EXTENSIONS.ordinal)
                                    }
                                }
                            )
                        }
                        BrowseTab.EXTENSIONS -> {
                            SourceScreen(
                                viewModel = sourceViewModel,
                                showHeader = false,
                                searchQuery = searchQuery,
                                onNavigateToExtensionSettings = onNavigateToExtensionSettings,
                                onNavigateToExtensionInfo = onNavigateToExtensionInfo
                            )
                        }
                    }
                }
            }
        }
    }
}
