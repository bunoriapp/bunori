package com.halovoid.bunori.ui.feature.browse.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.ui.core.components.ScreenHeader
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
    val isExpanded = isSearchActive && selectedTab == BrowseTab.EXTENSIONS
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            focusRequester.requestFocus()
        }
    }

    ScreenHeader(
        title = "Browse",
        modifier = modifier,
        isExpanded = isExpanded,
        expandedContent = {
            TextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        text = "Search extensions...",
                        fontSize = 14.sp,
                        color = SecondaryText
                    )
                },
                leadingIcon = {
                    IconButton(onClick = onCloseSearch) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryText
                        )
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = PrimaryText
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = PrimaryText,
                    unfocusedTextColor = PrimaryText,
                    cursorColor = BrandAccent
                ),
                singleLine = true
            )
        },
        actions = {
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
    )
}
