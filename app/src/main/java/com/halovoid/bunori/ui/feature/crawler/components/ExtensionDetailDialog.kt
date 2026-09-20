package com.halovoid.bunori.ui.feature.crawler.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.halovoid.bunori.ui.core.components.SourceIcon
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.crawler.ExtensionUiItem

@Composable
fun ExtensionDetailDialog(
    item: ExtensionUiItem,
    onDismiss: () -> Unit,
    onUninstall: () -> Unit,
    onInstall: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val manifest = item.loadedExtension?.manifest
    val metadata = item.loadedExtension?.extension?.metadata
    val repoEntry = item.repoEntry

    val concurrency = metadata?.runnerConcurrency ?: repoEntry?.runnerConcurrency ?: manifest?.runnerConcurrency ?: 3
    val cooldownMs = metadata?.runnerCooldown ?: repoEntry?.runnerCooldown ?: manifest?.runnerCooldown ?: 1000L
    val maxAttempts = metadata?.maxAttempts ?: repoEntry?.maxAttempts ?: manifest?.maxAttempts ?: 3
    val webviewNeeded = metadata?.webviewNeeded ?: repoEntry?.webviewNeeded ?: manifest?.webviewNeeded ?: false

    val packageSizeBytes = item.loadedExtension?.bextFile?.takeIf { it.exists() }?.length()
        ?: repoEntry?.size
        ?: 0L
    val packageSizeFormatted = if (packageSizeBytes > 0) {
        String.format("%.1f KB", packageSizeBytes / 1024.0)
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SourceIcon(
                    model = item.iconModel,
                    fallbackText = item.name,
                    size = 46.dp,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = 0.dp,
                    contentScale = ContentScale.Crop
                )
                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    val ver = item.installedVersion ?: item.repoVersion ?: "1.0.0"
                    Text(
                        text = "v$ver • ${item.lang.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (item.baseUrl.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, item.baseUrl.toUri())
                                context.startActivity(intent)
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Website",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryText
                            )
                            Text(
                                text = item.baseUrl,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BrandAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open Website",
                            tint = SecondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Concurrency", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    Text("$concurrency workers", style = MaterialTheme.typography.bodySmall, color = PrimaryText, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Rate Limit Delay", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    Text("${cooldownMs}ms", style = MaterialTheme.typography.bodySmall, color = PrimaryText, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Max Retries", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    Text("$maxAttempts", style = MaterialTheme.typography.bodySmall, color = PrimaryText, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("WebView Bypass", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                    Text(if (webviewNeeded) "Required" else "Not required", style = MaterialTheme.typography.bodySmall, color = PrimaryText, fontWeight = FontWeight.Medium)
                }

                if (packageSizeFormatted != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Package Size", style = MaterialTheme.typography.bodySmall, color = SecondaryText)
                        Text(packageSizeFormatted, style = MaterialTheme.typography.bodySmall, color = PrimaryText, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {
            if (item.isInstalled) {
                OutlinedButton(
                    onClick = onUninstall,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Uninstall", fontWeight = FontWeight.Bold)
                }
            } else if (onInstall != null) {
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Install", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PrimaryText)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
