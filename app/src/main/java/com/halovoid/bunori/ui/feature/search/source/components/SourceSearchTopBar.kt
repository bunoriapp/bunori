package com.halovoid.bunori.ui.feature.search.source.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.DarkBackground
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSearchTopBar(
    sourceName: String,
    isSearchMode: Boolean,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearQuery: () -> Unit,
    onBack: () -> Unit,
    isCompactMode: Boolean,
    onToggleCompactMode: (Boolean) -> Unit,
    focusRequester: FocusRequester
) {
    if (isSearchMode) {
        TopAppBar(
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            text = "Search in $sourceName...",
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
                IconButton(onClick = onCloseSearch) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close search",
                        tint = PrimaryText
                    )
                }
            },
            actions = {
                IconButton(onClick = { onToggleCompactMode(!isCompactMode) }) {
                    Icon(
                        imageVector = if (isCompactMode) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = "Toggle view",
                        tint = SecondaryText
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkBackground,
                titleContentColor = PrimaryText,
                navigationIconContentColor = PrimaryText,
                actionIconContentColor = PrimaryText
            )
        )
    } else {
        AppTopBar(
            title = sourceName,
            onBack = onBack,
            actions = {
                IconButton(onClick = { onToggleCompactMode(!isCompactMode) }) {
                    Icon(
                        imageVector = if (isCompactMode) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = "Toggle view",
                        tint = SecondaryText
                    )
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search in $sourceName",
                        tint = if (searchQuery.isNotBlank()) BrandAccent else PrimaryText
                    )
                }
            }
        )
    }
}
