package com.halovoid.bunori.ui.feature.crawler

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.crawler.components.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrawlerScreen(
    viewModel: CrawlerViewModel,
    onBack: () -> Unit = {},
    showHeader: Boolean = true,
    searchQuery: String = "",
    onNavigateToExtensionSettings: (() -> Unit)? = null,
    onNavigateToExtensionInfo: ((String) -> Unit)? = null
) {
    val extensionItems by viewModel.extensionItems.collectAsStateWithLifecycle()
    val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedItemForDetails by remember { mutableStateOf<ExtensionUiItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messageFlow.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (selectedItemForDetails != null) {
        val detailItem = selectedItemForDetails!!
        ExtensionDetailDialog(
            item = detailItem,
            onDismiss = { selectedItemForDetails = null },
            onUninstall = {
                selectedItemForDetails = null
                viewModel.uninstallExtension(detailItem.id)
            },
            onInstall = {
                selectedItemForDetails = null
                detailItem.repoEntry?.let { viewModel.installExtension(it) }
            }
        )
    }

    if (showHeader) {
        Scaffold(
            containerColor = DarkBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryText
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "Extensions",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Text(
                            text = "${extensionItems.count { it.isInstalled }} installed • ${extensionItems.size} total",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText
                        )
                    }

                    if (catalogState is CatalogState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(20.dp),
                            color = BrandAccent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.refreshCatalog(forceNetwork = true) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Catalog",
                                tint = BrandAccent
                            )
                        }
                    }

                    if (onNavigateToExtensionSettings != null) {
                        IconButton(onClick = onNavigateToExtensionSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Extension Settings",
                                tint = SecondaryText
                            )
                        }
                    }
                }

                ExtensionListContent(
                    extensionItems = extensionItems,
                    catalogState = catalogState,
                    searchQuery = searchQuery,
                    viewModel = viewModel,
                    onNavigateToExtensionSettings = onNavigateToExtensionSettings,
                    onNavigateToExtensionInfo = onNavigateToExtensionInfo,
                    onSelectItemForDetails = { selectedItemForDetails = it }
                )
            }
        }
    } else {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            ExtensionListContent(
                extensionItems = extensionItems,
                catalogState = catalogState,
                searchQuery = searchQuery,
                viewModel = viewModel,
                onNavigateToExtensionSettings = onNavigateToExtensionSettings,
                onNavigateToExtensionInfo = onNavigateToExtensionInfo,
                onSelectItemForDetails = { selectedItemForDetails = it },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
