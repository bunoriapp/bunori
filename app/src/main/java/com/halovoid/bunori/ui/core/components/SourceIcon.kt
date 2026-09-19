package com.halovoid.bunori.ui.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.halovoid.bunori.ui.core.theme.BrandAccent
import com.halovoid.bunori.ui.core.theme.DarkSurfaceVariant
import java.net.URI

/**
 * Standard icon component for extension sources and crawlers across Bunori.
 * Renders local icon file, network iconUrl, or favicon service with a fallback
 * to a styled letter avatar.
 */
@Composable
fun SourceIcon(
    model: Any?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    backgroundColor: Color = Color.Transparent,
    contentPadding: Dp = 0.dp,
    contentScale: ContentScale = ContentScale.Crop,
    border: BorderStroke? = null
) {
    var errorCount by remember(model) { mutableStateOf(0) }

    // Resolve current data to load with progressive fallback
    val currentData = remember(model, errorCount) {
        when (errorCount) {
            0 -> model
            1 -> {
                // If primary model failed and baseUrl can be derived, try Google Favicon service
                val baseUrlStr = when (model) {
                    is String -> model
                    else -> null
                }
                baseUrlStr?.let { getFaviconUrl(it) }
            }
            else -> null
        }
    }

    val isImageAvailable = currentData != null && errorCount < 2
    val containerColor = if (isImageAvailable) {
        backgroundColor
    } else {
        if (backgroundColor == Color.Transparent) DarkSurfaceVariant else backgroundColor
    }

    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = containerColor,
        border = border
    ) {
        if (isImageAvailable) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(currentData)
                    .crossfade(true)
                    .build(),
                contentDescription = fallbackText,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                onError = {
                    errorCount++
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = fallbackText.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandAccent
                )
            }
        }
    }
}

/**
 * Derives a Google Favicon 128px PNG URL from a web URL or domain.
 */
fun getFaviconUrl(urlOrDomain: String): String {
    val domain = try {
        val clean = if (!urlOrDomain.startsWith("http://") && !urlOrDomain.startsWith("https://")) {
            "https://$urlOrDomain"
        } else {
            urlOrDomain
        }
        URI(clean).host ?: urlOrDomain.removePrefix("https://").removePrefix("http://").substringBefore('/')
    } catch (_: Exception) {
        urlOrDomain.removePrefix("https://").removePrefix("http://").substringBefore('/')
    }
    return "https://www.google.com/s2/favicons?domain=$domain&sz=128"
}
