package com.halovoid.bunori.ui.feature.reader.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.ui.core.theme.*

@Composable
fun ReaderBottomBar(
    progress: Float,
    currentChapterNumber: Int,
    totalChapters: Int,
    onOpenSettings: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkBackground.copy(alpha = 0.95f),
        contentColor = PrimaryText,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Scrubber Progress Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                    color = BrandAccent,
                    trackColor = DarkSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                val pct = (progress * 100).toInt().coerceIn(0, 100)
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Navigation Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onPreviousChapter,
                    enabled = currentChapterNumber > 1
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Previous Chapter",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Prev")
                }

                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, BorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Reader settings",
                        modifier = Modifier.size(16.dp),
                        tint = PrimaryText
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Settings", style = MaterialTheme.typography.labelMedium)
                }

                TextButton(
                    onClick = onNextChapter,
                    enabled = currentChapterNumber < totalChapters
                ) {
                    Text("Next")
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Next Chapter",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
