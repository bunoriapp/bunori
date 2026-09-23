package com.halovoid.bunori.ui.feature.search.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.domain.models.SearchItem
import com.halovoid.bunori.ui.core.components.NovelCard
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText
import com.halovoid.bunori.ui.feature.search.SourceSearchStatus

fun LazyListScope.sourceResultSection(
    source: String,
    status: SourceSearchStatus,
    isCompactMode: Boolean,
    libraryUrls: Set<String>,
    onDrillDown: ((String) -> Unit)? = null,
    onItemClick: (SearchItem) -> Unit
) {
    val count = when (status) {
        is SourceSearchStatus.Success -> status.items.size
        else -> 0
    }

    item(key = "header_$source") {
        SourceHeader(
            source = source,
            count = count,
            onDrillDown = if (onDrillDown != null) { { onDrillDown(source) } } else null
        )
    }

    when (status) {
        is SourceSearchStatus.Loading -> {
            item(key = "loading_$source") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
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
        is SourceSearchStatus.Error -> {
            item(key = "error_$source") {
                val crawler = remember(source) {
                    CrawlerFactory.getCrawlers().find { it.name.equals(source, ignoreCase = true) }
                }
                val isCloudflare = crawler?.webviewNeeded == true ||
                    status.message.contains("cloudflare", ignoreCase = true) ||
                    status.message.contains("blocked", ignoreCase = true) ||
                    status.message.contains("security", ignoreCase = true)

                Text(
                    text = if (isCloudflare) "Cloudflare verification required" else "Could not reach source",
                    color = PrimaryText.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
        is SourceSearchStatus.Success -> {
            if (status.items.isEmpty()) {
                item(key = "empty_$source") {
                    Text(
                        text = "No results found",
                        color = SecondaryText.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                if (isCompactMode) {
                    itemsIndexed(status.items, key = { index, item -> "${source}_${item.url}_$index" }) { _, item ->
                        val isInLibrary = libraryUrls.contains(item.url)
                        NovelCard(
                            item = item,
                            isCompactMode = true,
                            isInLibrary = isInLibrary,
                            onClick = { onItemClick(item) }
                        )
                    }
                } else {
                    item(key = "row_$source") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            itemsIndexed(status.items, key = { index, item -> "${source}_${item.url}_$index" }) { _, item ->
                                val isInLibrary = libraryUrls.contains(item.url)
                                Box(modifier = Modifier.width(125.dp)) {
                                    NovelCard(
                                        item = item,
                                        isCompactMode = false,
                                        isInLibrary = isInLibrary,
                                        onClick = { onItemClick(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
