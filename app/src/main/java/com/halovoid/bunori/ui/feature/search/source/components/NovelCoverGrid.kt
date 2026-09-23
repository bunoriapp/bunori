package com.halovoid.bunori.ui.feature.search.source.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.domain.models.SearchItem
import com.halovoid.bunori.ui.core.components.NovelCard
import com.halovoid.bunori.ui.core.theme.BrandAccent

@Composable
fun NovelCoverGrid(
    novels: List<SearchItem>,
    isCompactMode: Boolean,
    libraryUrls: Set<String>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onItemClick: (SearchItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isCompactMode) {
        val listState = rememberLazyListState()

        val shouldLoadMore by remember(novels.size, isLoadingMore, hasMore) {
            derivedStateOf {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                hasMore && !isLoadingMore && lastVisibleIndex >= novels.size - 4
            }
        }

        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) {
                onLoadMore()
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.fillMaxSize()
        ) {
            itemsIndexed(novels, key = { index, item -> "${item.url}_$index" }) { _, item ->
                val isInLibrary = libraryUrls.contains(item.url)
                NovelCard(
                    item = item,
                    isCompactMode = true,
                    isInLibrary = isInLibrary,
                    onClick = { onItemClick(item) }
                )
            }

            if (isLoadingMore) {
                item(key = "loading_more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = BrandAccent,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    } else {
        val gridState = rememberLazyGridState()

        val shouldLoadMore by remember(novels.size, isLoadingMore, hasMore) {
            derivedStateOf {
                val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                hasMore && !isLoadingMore && lastVisibleIndex >= novels.size - 6
            }
        }

        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) {
                onLoadMore()
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            state = gridState,
            contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize()
        ) {
            itemsIndexed(novels, key = { index, item -> "${item.url}_$index" }) { _, item ->
                val isInLibrary = libraryUrls.contains(item.url)
                NovelCard(
                    item = item,
                    isCompactMode = false,
                    isInLibrary = isInLibrary,
                    onClick = { onItemClick(item) }
                )
            }

            if (isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "grid_loading_more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = BrandAccent,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}
