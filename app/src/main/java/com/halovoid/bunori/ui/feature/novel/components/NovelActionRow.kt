package com.halovoid.bunori.ui.feature.novel.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.PrimaryText
import com.halovoid.bunori.ui.core.theme.SecondaryText

@Composable
fun NovelActionRow(
    inLibrary: Boolean,
    isActivityRunning: Boolean,
    artifactsExist: Boolean,
    downloadEnabled: Boolean = true,
    onFavoriteClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onArtifactsClick: () -> Unit,
    onWebViewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FavoriteActionItem(
            inLibrary = inLibrary,
            isActivityRunning = isActivityRunning,
            onClick = onFavoriteClick
        )
        ActionItem(
            icon = Icons.Outlined.FileDownload,
            label = "Download",
            onClick = onDownloadClick,
            enabled = downloadEnabled
        )
        ActionItem(
            icon = Icons.Outlined.FileUpload,
            label = "Artifacts",
            onClick = onArtifactsClick,
            enabled = artifactsExist
        )
        ActionItem(
            icon = Icons.Outlined.Language,
            label = "WebView",
            onClick = onWebViewClick
        )
    }
}

@Composable
private fun FavoriteActionItem(
    inLibrary: Boolean,
    isActivityRunning: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val heartbeatScale by if (isActivityRunning) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1400
                    1.0f at 0
                    1.20f at 200
                    1.05f at 380
                    1.16f at 560
                    1.0f at 800
                    1.0f at 1400
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "heartbeatScale"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val icon = if (inLibrary) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
    val iconTint = if (inLibrary) BrandAccent else PrimaryText

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.width(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (inLibrary) "Liked" else "Like",
                tint = iconTint,
                modifier = Modifier
                    .size(22.dp)
                    .scale(heartbeatScale)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (inLibrary) "Liked" else "Like",
            style = MaterialTheme.typography.labelSmall,
            color = if (inLibrary) BrandAccent else PrimaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isRunning: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.width(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) PrimaryText else SecondaryText.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )

            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    strokeWidth = 2.dp,
                    color = BrandAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) PrimaryText else SecondaryText.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
