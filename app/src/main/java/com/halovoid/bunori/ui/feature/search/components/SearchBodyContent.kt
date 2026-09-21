package com.halovoid.bunori.ui.feature.search.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.domain.models.SearchItem
import com.halovoid.bunori.ui.core.theme.SecondaryText
import com.halovoid.bunori.ui.feature.search.SearchState
import com.halovoid.bunori.ui.feature.search.SourceSearchStatus

@Composable
fun SearchBodyContent(
    searchState: SearchState,
    isCompactMode: Boolean,
    libraryUrls: Set<String>,
    onNavigateToRequest: () -> Unit,
    onItemClick: (SearchItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (searchState) {
            is SearchState.Idle -> {
                SearchIdleContent()
            }
            is SearchState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = searchState.message,
                        color = SecondaryText,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is SearchState.Searching -> {
                SearchResultsList(
                    state = searchState,
                    isCompactMode = isCompactMode,
                    libraryUrls = libraryUrls,
                    onNavigateToRequest = onNavigateToRequest,
                    onItemClick = onItemClick
                )
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    state: SearchState.Searching,
    isCompactMode: Boolean,
    libraryUrls: Set<String>,
    onNavigateToRequest: () -> Unit,
    onItemClick: (SearchItem) -> Unit
) {
    val allDone = state.sourceStates.all { it.value !is SourceSearchStatus.Loading }
    val allEmpty = state.sourceStates.all {
        val status = it.value
        status is SourceSearchStatus.Success && status.items.isEmpty()
    }

    if (allDone && allEmpty) {
        SearchEmptyContent(
            query = state.query,
            onRequestClick = onNavigateToRequest
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = if (isCompactMode) Arrangement.spacedBy(4.dp) else Arrangement.spacedBy(24.dp)
    ) {
        state.sourceStates.forEach { (source, status) ->
            sourceResultSection(
                source = source,
                status = status,
                isCompactMode = isCompactMode,
                libraryUrls = libraryUrls,
                onItemClick = onItemClick
            )
        }

        if (allDone) {
            item(key = "asking_request") {
                RequestNovelFooter(onRequestClick = onNavigateToRequest)
            }
        }
    }
}
