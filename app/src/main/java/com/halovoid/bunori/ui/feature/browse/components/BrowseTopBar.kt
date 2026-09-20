package com.halovoid.bunori.ui.feature.browse.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.browse.BrowseTab

@Composable
fun BrowseTopBar(
    selectedTab: BrowseTab,
    isSearchActive: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onOpenSearch: () -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    onRefreshCatalog: () -> Unit,
    onNavigateToExtensionSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSearchActive && selectedTab == BrowseTab.EXTENSIONS) {
            IconButton(onClick = onCloseSearch) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close search",
                    tint = PrimaryText
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
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
                        IconButton(onClick = { onQueryChange("") }) {
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
                    onOpenSearch()
                }
            }) {
                Icon(
                    imageVector = if (selectedTab == BrowseTab.SOURCES) Icons.Default.TravelExplore else Icons.Default.Search,
                    contentDescription = "Search",
                    tint = PrimaryText
                )
            }

            IconButton(onClick = onRefreshCatalog) {
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
}
