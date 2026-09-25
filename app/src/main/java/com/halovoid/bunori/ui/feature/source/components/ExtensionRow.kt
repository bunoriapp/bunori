package com.halovoid.bunori.ui.feature.source.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halovoid.bunori.ui.core.components.SourceIcon
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.source.ExtensionUiItem

@Composable
fun ExtensionRow(
    item: ExtensionUiItem,
    onActionClick: () -> Unit,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !item.isActionInProgress, onClick = onItemClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SourceIcon(
            model = item.iconModel,
            fallbackText = item.name,
            size = 42.dp,
            shape = RoundedCornerShape(10.dp),
            contentPadding = 0.dp,
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.isDeprecated) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = ErrorRed.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "DEPRECATED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            val langText = if (item.lang.equals("all", ignoreCase = true)) "Multi" else item.lang.uppercase()
            val versionText = when {
                item.hasUpdate -> "v${item.installedVersion} → v${item.repoVersion}"
                item.installedVersion != null -> "v${item.installedVersion}"
                item.repoVersion != null -> "v${item.repoVersion}"
                else -> "v1.0.0"
            }
            val is18Plus = item.name.contains("18+") || item.baseUrl.contains("18+")
            val ageRatingText = if (is18Plus) " • 18+" else ""
            val statusPrefix = if (item.isActionInProgress) {
                if (item.isInstalled) "Updating • " else "Installing • "
            } else ""
            val typeTag = item.extensionType.badgeText
            val metadata = if (item.isDeprecated && !item.deprecationReason.isNullOrBlank()) {
                "$typeTag • ${item.deprecationReason} • $langText • $versionText"
            } else {
                "$typeTag • $statusPrefix$langText • $versionText$ageRatingText"
            }

            Text(
                text = metadata,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.isDeprecated) SecondaryText.copy(alpha = 0.85f) else if (item.hasUpdate && !item.isActionInProgress) BrandAccent else SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (item.isActionInProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = SecondaryText
            )
        } else if (item.hasUpdate) {
            Button(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandAccent,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Update",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (item.isInstalled) {
            IconButton(
                onClick = onActionClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Extension Info",
                    tint = SecondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            IconButton(
                onClick = onActionClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = "Download Extension",
                    tint = SecondaryText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
