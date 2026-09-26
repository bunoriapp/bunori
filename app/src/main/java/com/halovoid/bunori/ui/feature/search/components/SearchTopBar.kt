package com.halovoid.bunori.ui.feature.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.api.core.crawler.Crawler
import com.halovoid.bunori.ui.core.components.SourceIcon
import com.halovoid.bunori.ui.core.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    selectedSource: String?,
    onSelectSource: (String?) -> Unit,
    installedCrawlers: List<Crawler>,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onClearQuery: () -> Unit,
    focusRequester: FocusRequester,
    extensionRepos: List<com.halovoid.bunori.extension.api.models.ExtensionRepo> = emptyList(),
    modifier: Modifier = Modifier
) {
    var showSourceDropdown by remember { mutableStateOf(false) }

    fun formatCrawlerDisplayName(crawler: Crawler): String {
        val repoKey = if (crawler.id.contains('.')) crawler.id.substringBefore('.') else null
        val repoName = repoKey?.let { key ->
            extensionRepos.find { it.stableKey.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true) }?.name
        } ?: repoKey?.uppercase()
        return if (repoName != null) "$repoName • ${crawler.name}" else crawler.name
    }

    val selectedCrawler = remember(selectedSource, installedCrawlers) {
        selectedSource?.let { src ->
            installedCrawlers.find { it.id.equals(src, ignoreCase = true) || it.name.equals(src, ignoreCase = true) }
        }
    }

    val selectedSourceIconModel = remember(selectedCrawler) {
        selectedCrawler?.let { crawler ->
            when {
                crawler.iconFile != null && crawler.iconFile?.exists() == true -> crawler.iconFile
                !crawler.iconUrl.isNullOrBlank() -> crawler.iconUrl
                crawler.baseUrl.isNotBlank() -> crawler.baseUrl
                else -> null
            }
        }
    }

    TopAppBar(
        modifier = modifier,
        title = {
            TextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = {
                    val displaySelected = selectedCrawler?.let { formatCrawlerDisplayName(it) } ?: selectedSource
                    Text(
                        text = if (displaySelected != null) "Search in $displaySelected..." else "Search for novels...",
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 16.sp,
                        color = SecondaryText.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = PrimaryText,
                    fontSize = 16.sp
                ),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    focusedTextColor = PrimaryText,
                    unfocusedTextColor = PrimaryText,
                    cursorColor = BrandAccent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearch() }
                ),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = onClearQuery) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = SecondaryText
                            )
                        }
                    }
                }
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PrimaryText
                )
            }
        },
        actions = {
            Box {
                IconButton(onClick = { showSourceDropdown = true }) {
                    if (selectedSource != null) {
                        SourceIcon(
                            model = selectedSourceIconModel,
                            fallbackText = selectedSource,
                            size = 22.dp,
                            shape = RoundedCornerShape(6.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.TravelExplore,
                            contentDescription = "Select Source",
                            tint = if (searchQuery.isNotBlank()) BrandAccent else SecondaryText
                        )
                    }
                }

                DropdownMenu(
                    expanded = showSourceDropdown,
                    onDismissRequest = { showSourceDropdown = false },
                    modifier = Modifier.background(DarkSurfaceVariant)
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TravelExplore,
                                    contentDescription = null,
                                    tint = if (selectedSource == null) BrandAccent else PrimaryText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "All Sources",
                                    color = if (selectedSource == null) BrandAccent else PrimaryText,
                                    fontWeight = if (selectedSource == null) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        },
                        onClick = {
                            showSourceDropdown = false
                            onSelectSource(null)
                        }
                    )

                    if (installedCrawlers.isNotEmpty()) {
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))

                        installedCrawlers.forEach { crawler ->
                            val isSelected = selectedSource.equals(crawler.id, ignoreCase = true) || selectedSource.equals(crawler.name, ignoreCase = true)
                            val crawlerIconModel = remember(crawler) {
                                when {
                                    crawler.iconFile != null && crawler.iconFile?.exists() == true -> crawler.iconFile
                                    !crawler.iconUrl.isNullOrBlank() -> crawler.iconUrl
                                    crawler.baseUrl.isNotBlank() -> crawler.baseUrl
                                    else -> null
                                }
                            }
                            val displayName = formatCrawlerDisplayName(crawler)

                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        SourceIcon(
                                            model = crawlerIconModel,
                                            fallbackText = crawler.name,
                                            size = 20.dp,
                                            shape = RoundedCornerShape(5.dp)
                                        )
                                        Text(
                                            text = displayName,
                                            color = if (isSelected) BrandAccent else PrimaryText,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                onClick = {
                                    showSourceDropdown = false
                                    onSelectSource(crawler.id)
                                }
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DarkBackground,
            titleContentColor = PrimaryText,
            navigationIconContentColor = PrimaryText,
            actionIconContentColor = PrimaryText
        )
    )
}

