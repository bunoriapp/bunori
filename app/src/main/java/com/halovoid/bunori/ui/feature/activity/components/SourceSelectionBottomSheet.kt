package com.halovoid.bunori.ui.feature.activity.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.data.handlers.utility.crawlerName
import com.halovoid.bunori.domain.models.Batch
import com.halovoid.bunori.ui.core.components.AppBottomSheet
import com.halovoid.bunori.ui.core.components.AppBottomSheetDivider
import com.halovoid.bunori.ui.core.components.AppBottomSheetGroup
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText

fun Batch.getSourceDisplayName(): String {
    return crawlerName?.takeIf { it.isNotBlank() }
        ?: try {
            val uri = java.net.URI(novelUrl)
            uri.host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: novelUrl
        } catch (_: Exception) {
            novelUrl
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectionBottomSheet(
    batches: List<Batch>,
    onDismiss: () -> Unit,
    onSourceSelected: (String) -> Unit
) {
    val sourceMap = batches.groupBy { it.getSourceDisplayName() }
    val distinctSources = sourceMap.keys.toList()

    AppBottomSheet(
        onDismiss = onDismiss,
        title = "Select by Source",
        subtitle = "Choose a source to select all matching activities"
    ) {
        AppBottomSheetGroup {
            distinctSources.forEachIndexed { index, sourceName ->
                val count = sourceMap[sourceName]?.size ?: 0
                ListItem(
                    headlineContent = {
                        Text(
                            text = sourceName,
                            color = PrimaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingContent = {},
                    trailingContent = {
                        Text(
                            text = "$count",
                            color = SecondaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        onSourceSelected(sourceName)
                    }
                )
                if (index < distinctSources.lastIndex) {
                    AppBottomSheetDivider()
                }
            }
        }
    }
}
