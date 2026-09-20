package com.halovoid.bunori.ui.feature.activity.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.DarkSurface
import com.halovoid.bunori.ui.core.theme.DarkSurfaceVariant
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText

@Composable
fun DownloadRangeDialog(
    initialRange: ClosedFloatingPointRange<Float>,
    minChapterIndex: Float = 1f,
    maxChapterIndex: Float,
    sources: List<String> = emptyList(),
    onConfirm: (ClosedFloatingPointRange<Float>) -> Unit,
    onDismiss: () -> Unit
) {
    var currentRange by remember { mutableStateOf(initialRange) }
    val rangeSpan = (maxChapterIndex - minChapterIndex).toInt()
    val steps = if (rangeSpan > 1) rangeSpan - 1 else 0
    val selectedCount = (currentRange.endInclusive - currentRange.start).toInt() + 1

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Select Download Range",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = "Select the chapter range to download:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                RangeSlider(
                    value = currentRange,
                    onValueChange = { currentRange = it },
                    valueRange = minChapterIndex..maxChapterIndex,
                    steps = steps,
                    colors = SliderDefaults.colors(
                        thumbColor = BrandAccent,
                        activeTrackColor = BrandAccent,
                        inactiveTrackColor = BrandAccent.copy(alpha = 0.20f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chapter ${currentRange.start.toInt()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryText,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BrandAccent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "$selectedCount ${if (selectedCount == 1) "chapter" else "chapters"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandAccent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "Chapter ${currentRange.endInclusive.toInt()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryText,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (sources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = DarkSurfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = BrandAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Downloading from ${sources.size} ${if (sources.size == 1) "source" else "sources"}:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryText
                                )
                                Text(
                                    text = if (sources.size <= 2) sources.joinToString(", ") else "${sources.take(2).joinToString(", ")}, ...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PrimaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(currentRange) }
            ) {
                Text(
                    text = "Download",
                    color = BrandAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = SecondaryText,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}
