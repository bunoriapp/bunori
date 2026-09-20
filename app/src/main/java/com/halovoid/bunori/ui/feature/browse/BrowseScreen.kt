package com.halovoid.bunori.ui.feature.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.ui.core.components.SourceIcon
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.crawler.CrawlerScreen
import com.halovoid.bunori.ui.feature.crawler.CrawlerViewModel
import com.halovoid.bunori.ui.feature.crawler.ExtensionUiItem
import com.halovoid.bunori.ui.feature.activity.components.JobActionHandler

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
    crawlerViewModel: CrawlerViewModel
) {
    var selectedTabOrdinal by rememberSaveable {
        mutableStateOf(crawlerViewModel.selectedTabOrdinal)
    }
    val selectedTab = BrowseTab.values().getOrElse(selectedTabOrdinal) { BrowseTab.SOURCES }

    fun selectTab(tab: BrowseTab) {
        selectedTabOrdinal = tab.ordinal
        crawlerViewModel.selectedTabOrdinal = tab.ordinal
    }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val updatesCount by crawlerViewModel.updatesCount.collectAsStateWithLifecycle()
    val failedExtensions by crawlerViewModel.failedExtensions.collectAsStateWithLifecycle()
    val extensionItems by crawlerViewModel.extensionItems.collectAsStateWithLifecycle()
    val installedSources = remember(extensionItems) {
        extensionItems.filter { it.isInstalled }.sortedBy { it.name.lowercase() }
    }

    JobActionHandler (
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
                // 1. TOP APP BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSearchActive && selectedTab == BrowseTab.EXTENSIONS) {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close search",
                                tint = PrimaryText
                            )
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search extensions...",
                                    color = SecondaryText,
                                    fontSize = 15.sp
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                cursorColor = BrandAccent,
                                focusedTextColor = PrimaryText,
                                unfocusedTextColor = PrimaryText
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = SecondaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        )
                    } else {
                        Text(
                            text = "Browse",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = {
                            if (selectedTab == BrowseTab.SOURCES) {
                                onNavigateToSearch(null)
                            } else {
                                isSearchActive = true
                            }
                        }) {
                            Icon(
                                imageVector = if (selectedTab == BrowseTab.SOURCES) Icons.Default.TravelExplore else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = PrimaryText
                            )
                        }

                        IconButton(onClick = {
                            crawlerViewModel.refreshCatalog(forceNetwork = true)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = PrimaryText
                            )
                        }

                        IconButton(onClick = onNavigateToExtensionSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Extension Settings",
                                tint = PrimaryText
                            )
                        }
                    }
                }

                // 2. TOP TAB NAVIGATION
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = BrandAccent,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                            color = BrandAccent,
                            height = 3.dp
                        )
                    },
                    divider = {
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.4f), thickness = 1.dp)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BrowseTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        Tab(
                            selected = isSelected,
                            onClick = {
                                selectTab(tab)
                                isSearchActive = false
                                searchQuery = ""
                            },
                            text = {
                                if (tab == BrowseTab.EXTENSIONS) {
                                    BadgedBox(
                                        badge = {
                                            if (updatesCount > 0) {
                                                Badge(
                                                    containerColor = BrandAccent,
                                                    contentColor = Color.White
                                                ) {
                                                    Text("$updatesCount", fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = tab.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) BrandAccent else SecondaryText
                                        )
                                    }
                                } else {
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) BrandAccent else SecondaryText
                                    )
                                }
                            }
                        )
                    }
                }

                // 3. TAB CONTENT
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
                            CrawlerScreen(
                                viewModel = crawlerViewModel,
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

// Alias for compatibility
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestScreen(
    onNavigateToSearch: (String?) -> Unit = {},
    onNavigateToExtensionSettings: () -> Unit = {},
    onNavigateToExtensionInfo: ((String) -> Unit)? = null,
    viewModel: BrowseViewModel,
    crawlerViewModel: CrawlerViewModel
) = BrowseScreen(
    onNavigateToSearch = onNavigateToSearch,
    onNavigateToExtensionSettings = onNavigateToExtensionSettings,
    onNavigateToExtensionInfo = onNavigateToExtensionInfo,
    viewModel = viewModel,
    crawlerViewModel = crawlerViewModel
)

@Composable
private fun SourcesTabContent(
    installedSources: List<ExtensionUiItem>,
    failedExtensions: List<String>,
    onNavigateToSearch: (String?) -> Unit,
    onNavigateToExtensions: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (installedSources.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = DarkSurfaceVariant
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        modifier = Modifier.padding(16.dp),
                        tint = BrandAccent
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No sources installed",
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryText,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please install sources from extensions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onNavigateToExtensions,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text("Explore Extensions", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            if (failedExtensions.isNotEmpty()) {
                item(key = "failed_note") {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = SecondaryText.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (failedExtensions.size == 1) {
                                    "${failedExtensions.first()} failed to load"
                                } else {
                                    "${failedExtensions.size} sources failed to load"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item(key = "header_installed") {
                Text(
                    text = "Installed Sources (${installedSources.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(installedSources, key = { "source_${it.id}" }) { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToSearch(source.name) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SourceIcon(
                        model = source.iconModel,
                        fallbackText = source.name,
                        size = 42.dp,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = 0.dp,
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val langDisplay = if (source.lang.equals("all", ignoreCase = true)) "Multi" else source.lang.uppercase()
                        val hostDisplay = try {
                            java.net.URI(source.baseUrl).host ?: source.baseUrl
                        } catch (e: Exception) {
                            source.baseUrl
                        }
                        Text(
                            text = "$langDisplay • $hostDisplay",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = SecondaryText.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                HorizontalDivider(
                    color = BorderColor.copy(alpha = 0.25f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}