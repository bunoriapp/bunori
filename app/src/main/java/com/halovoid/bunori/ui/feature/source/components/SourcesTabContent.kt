package com.halovoid.bunori.ui.feature.source.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.source.ExtensionUiItem

@Composable
fun SourcesTabContent(
    installedSources: List<ExtensionUiItem>,
    failedExtensions: List<String>,
    onNavigateToSearch: (String?) -> Unit,
    onNavigateToExtensions: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (installedSources.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = DarkSurfaceVariant
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        modifier = Modifier.padding(16.dp),
                        tint = BrandAccent
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No sources installed",
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryText,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please install sources from extensions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onNavigateToExtensions,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text("Explore Extensions", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            if (failedExtensions.isNotEmpty()) {
                item(key = "failed_note") {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = SecondaryText.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (failedExtensions.size == 1) {
                                    "${failedExtensions.first()} failed to load"
                                } else {
                                    "${failedExtensions.size} sources failed to load"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item(key = "header_installed") {
                Text(
                    text = "Installed Sources (${installedSources.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(installedSources, key = { "source_${it.id}" }) { source ->
                SourceItemRow(
                    source = source,
                    onClick = { onNavigateToSearch(source.name) }
                )
            }
        }
    }
}
