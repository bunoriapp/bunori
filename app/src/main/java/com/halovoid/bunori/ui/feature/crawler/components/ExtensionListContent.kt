package com.halovoid.bunori.ui.feature.crawler.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.crawler.CatalogState
import com.halovoid.bunori.ui.feature.crawler.CrawlerViewModel
import com.halovoid.bunori.ui.feature.crawler.ExtensionUiItem

@Composable
fun ExtensionListContent(
    extensionItems: List<ExtensionUiItem>,
    catalogState: CatalogState,
    searchQuery: String,
    viewModel: CrawlerViewModel,
    onNavigateToExtensionSettings: (() -> Unit)?,
    onNavigateToExtensionInfo: ((String) -> Unit)?,
    onSelectItemForDetails: (ExtensionUiItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredItems = remember(extensionItems, searchQuery) {
        if (searchQuery.isBlank()) {
            extensionItems
        } else {
            val q = searchQuery.trim().lowercase()
            extensionItems.filter {
                it.name.lowercase().contains(q) ||
                it.lang.lowercase().contains(q) ||
                it.baseUrl.lowercase().contains(q)
            }
        }
    }

    val installing = remember(filteredItems) {
        filteredItems.filter { it.isActionInProgress }.sortedBy { it.name.lowercase() }
    }
    val updates = remember(filteredItems) {
        filteredItems.filter { it.hasUpdate && !it.isActionInProgress }.sortedBy { it.name.lowercase() }
    }
    val installed = remember(filteredItems) {
        filteredItems.filter { it.isInstalled && !it.hasUpdate && !it.isActionInProgress }.sortedBy { it.name.lowercase() }
    }
    val available = remember(filteredItems) {
        filteredItems.filter { !it.isInstalled && !it.hasUpdate && !it.isActionInProgress }.sortedBy { it.name.lowercase() }
    }

    val availableGroups = remember(available) {
        if (available.isEmpty()) emptyList()
        else listOf("Available" to available)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (extensionItems.isEmpty() && catalogState is CatalogState.Loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandAccent)
            }
        } else if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        color = DarkSurfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Extension,
                            contentDescription = null,
                            modifier = Modifier.padding(16.dp),
                            tint = SecondaryText
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No results found" else "No extensions found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty())
                            "Try searching with a different keyword"
                        else
                            "Add the source from the extension settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isEmpty() && onNavigateToExtensionSettings != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToExtensionSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Extension settings", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (installing.isNotEmpty()) {
                    item(key = "section_header_installing") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Installing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            Surface(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${installing.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    items(installing, key = { "installing_${it.id}" }) { item ->
                        ExtensionRow(
                            item = item,
                            onActionClick = {},
                            onItemClick = {}
                        )
                    }
                }

                if (updates.isNotEmpty()) {
                    item(key = "section_header_updates") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Updates pending",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandAccent
                                )
                                Surface(
                                    color = BrandAccent.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "${updates.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            TextButton(
                                onClick = { viewModel.updateAll() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Update all",
                                    color = BrandAccent,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    items(updates, key = { "update_${it.id}" }) { item ->
                        ExtensionRow(
                            item = item,
                            onActionClick = { item.repoEntry?.let { viewModel.installExtension(it) } },
                            onItemClick = {
                                if (onNavigateToExtensionInfo != null) {
                                    onNavigateToExtensionInfo(item.id)
                                } else {
                                    onSelectItemForDetails(item)
                                }
                            }
                        )
                    }
                }

                if (installed.isNotEmpty()) {
                    item(key = "section_header_installed") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Installed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            Surface(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${installed.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    items(installed, key = { "installed_${it.id}" }) { item ->
                        ExtensionRow(
                            item = item,
                            onActionClick = {
                                if (onNavigateToExtensionInfo != null) {
                                    onNavigateToExtensionInfo(item.id)
                                } else {
                                    onSelectItemForDetails(item)
                                }
                            },
                            onItemClick = {
                                if (onNavigateToExtensionInfo != null) {
                                    onNavigateToExtensionInfo(item.id)
                                } else {
                                    onSelectItemForDetails(item)
                                }
                            }
                        )
                    }
                }

                if (availableGroups.isNotEmpty()) {
                    availableGroups.forEach { (groupTitle, groupItems) ->
                        item(key = "section_header_avail_$groupTitle") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = groupTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryText
                                )
                                Surface(
                                    color = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "${groupItems.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SecondaryText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        items(groupItems, key = { "avail_${it.id}" }) { item ->
                            ExtensionRow(
                                item = item,
                                onActionClick = { item.repoEntry?.let { viewModel.installExtension(it) } },
                                onItemClick = { onSelectItemForDetails(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}
