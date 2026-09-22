package com.halovoid.bunori.ui.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.halovoid.bunori.data.repository.StorageRepository
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.halovoid.bunori.ui.core.theme.DarkSurface
import com.halovoid.bunori.ui.core.theme.DarkSurfaceVariant
import com.halovoid.bunori.ui.core.theme.SecondaryText
import kotlin.math.abs

/**
 * Deterministically generates a complementary 2-color gradient based on the novel title.
 */
private fun generateCoverGradient(title: String): Brush {
    val hash = abs(title.hashCode())
    val palettes = listOf(
        listOf(Color(0xFF1E293B), Color(0xFF0F172A)), // Slate
        listOf(Color(0xFF2E1065), Color(0xFF0F172A)), // Deep Purple
        listOf(Color(0xFF14532D), Color(0xFF0F172A)), // Deep Emerald
        listOf(Color(0xFF701A75), Color(0xFF0F172A)), // Deep Fuchsia
        listOf(Color(0xFF1C1917), Color(0xFF292524)), // Stone / Charcoal
        listOf(Color(0xFF0C4A6E), Color(0xFF082F49)), // Deep Ocean Blue
        listOf(Color(0xFF831843), Color(0xFF4C0519)), // Rose / Wine
        listOf(Color(0xFF7C2D12), Color(0xFF451A03))  // Terracotta / Rust
    )
    val chosen = palettes[hash % palettes.size]
    return Brush.verticalGradient(
        colors = listOf(chosen[0], chosen[1])
    )
}

/**
 * Resilient Novel Cover Image component.
 * - Tries primary `coverUrl` (local/cached file or web URL).
 * - Falls back to `coverHttpsUrl` if `coverUrl` fails.
 * - Displays a stylized book cover fallback with dynamic gradient, book icon, and title if all images fail or are null.
 */
@Composable
fun NovelCoverImage(
    coverUrl: String?,
    coverHttpsUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RoundedCornerShape(8.dp),
    showTitleInFallback: Boolean = true
) {
    val context = LocalContext.current
    var currentModel by remember(coverUrl, coverHttpsUrl) {
        mutableStateOf<Any?>(coverUrl ?: coverHttpsUrl)
    }

    LaunchedEffect(coverUrl, coverHttpsUrl) {
        if (!coverUrl.isNullOrBlank()) {
            val storageRepo = StorageRepository.getInstance(context)
            val resolved = storageRepo.resolveLocationUri(coverUrl)
            currentModel = resolved ?: coverUrl
        } else {
            currentModel = coverHttpsUrl
        }
    }

    var isFailed by remember(currentModel) {
        mutableStateOf(currentModel == null)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(DarkSurface),
        contentAlignment = Alignment.Center
    ) {
        if (!isFailed && currentModel != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(currentModel)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onError = {
                    if (currentModel != coverHttpsUrl && !coverHttpsUrl.isNullOrBlank()) {
                        currentModel = coverHttpsUrl
                    } else {
                        isFailed = true
                    }
                }
            )
        } else {
            // Stylized Fallback Book Cover
            NovelCoverFallback(
                title = title,
                showTitle = showTitleInFallback,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun NovelCoverFallback(
    title: String,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true
) {
    val gradient = remember(title) { generateCoverGradient(title) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = SecondaryText.copy(alpha = 0.6f),
                modifier = Modifier.size(28.dp)
            )
            if (showTitle && title.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 13.sp
                    ),
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
