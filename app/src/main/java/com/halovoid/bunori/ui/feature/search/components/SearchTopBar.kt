package com.halovoid.bunori.ui.feature.search.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.api.core.crawler.Crawler
import com.halovoid.bunori.ui.core.components.SourceIcon
import com.halovoid.bunori.ui.core.theme.*

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
    modifier: Modifier = Modifier
) {
    var isSearchFocused by remember { mutableStateOf(false) }
    var showSourceDropdown by remember { mutableStateOf(false) }

    val selectedCrawler = remember(selectedSource, installedCrawlers) {
        selectedSource?.let { src ->
            installedCrawlers.find { it.name.equals(src, ignoreCase = true) }
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

    Surface(
        modifier = modifier
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
                onClick = onBack,
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
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = PrimaryText,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(BrandAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearch() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { isSearchFocused = it.isFocused }
                )
            }

            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = onClearQuery,
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

            Box {
                IconButton(
                    onClick = { showSourceDropdown = true },
                    modifier = Modifier.size(38.dp)
                ) {
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
                            tint = if (searchQuery.isNotBlank()) BrandAccent else SecondaryText,
                            modifier = Modifier.size(20.dp)
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
                            val isSelected = selectedSource.equals(crawler.name, ignoreCase = true)
                            val crawlerIconModel = remember(crawler) {
                                when {
                                    crawler.iconFile != null && crawler.iconFile?.exists() == true -> crawler.iconFile
                                    !crawler.iconUrl.isNullOrBlank() -> crawler.iconUrl
                                    crawler.baseUrl.isNotBlank() -> crawler.baseUrl
                                    else -> null
                                }
                            }

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
                                            text = crawler.name,
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
                                    onSelectSource(crawler.name)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
