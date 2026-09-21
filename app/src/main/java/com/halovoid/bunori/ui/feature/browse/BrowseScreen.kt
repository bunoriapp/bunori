package com.halovoid.bunori.ui.feature.browse

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.browse.components.*
import com.halovoid.bunori.ui.feature.source.SourceScreen
import com.halovoid.bunori.ui.feature.source.SourceViewModel
import com.halovoid.bunori.ui.feature.activity.components.JobActionHandler
import com.halovoid.bunori.ui.feature.source.components.SourcesTabContent

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
    var selectedTabOrdinal by rememberSaveable {
        mutableStateOf(sourceViewModel.selectedTabOrdinal)
    }
    val selectedTab = BrowseTab.entries.getOrElse(selectedTabOrdinal) { BrowseTab.SOURCES }

    fun selectTab(tab: BrowseTab) {
        selectedTabOrdinal = tab.ordinal
        sourceViewModel.selectedTabOrdinal = tab.ordinal
    }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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
                        selectTab(tab)
                        isSearchActive = false
                        searchQuery = ""
                    }
                )

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        BrowseTab.SOURCES -> {
                            SourcesTabContent(
                                installedSources = installedSources,
                                failedExtensions = failedExtensions,
                                onNavigateToSearch = onNavigateToSearch,
                                onNavigateToExtensions = { selectTab(BrowseTab.EXTENSIONS) }
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
