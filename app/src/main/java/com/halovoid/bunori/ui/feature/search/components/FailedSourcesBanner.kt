package com.halovoid.bunori.ui.feature.search.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.crawler.webview.WebViewActivity

@Composable
fun FailedSourcesBanner(
    allFailedSources: List<String>,
    isSearching: Boolean,
    onResolveSheet: () -> Unit,
    onRetryFailed: () -> Unit,
    onRetrySingleSource: (String) -> Unit,
    context: Context,
    modifier: Modifier = Modifier
) {
    if (allFailedSources.isEmpty()) return

    val singleFailedSource = if (allFailedSources.size == 1) allFailedSources.first() else null
    val singleCrawler = remember(singleFailedSource) {
        singleFailedSource?.let { src ->
            CrawlerFactory.getCrawlers().find { it.name.equals(src, ignoreCase = true) }
        }
    }

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (singleCrawler?.webviewNeeded == true) Icons.Default.Shield else Icons.Default.Info,
                contentDescription = null,
                tint = if (singleCrawler?.webviewNeeded == true) MaterialTheme.colorScheme.primary else SecondaryText.copy(alpha = 0.7f),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (singleFailedSource != null) {
                    "$singleFailedSource could not be reached"
                } else {
                    "${allFailedSources.size} sources could not be reached"
                },
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )

            if (singleCrawler != null && singleCrawler.baseUrl.isNotBlank()) {
                Text(
                    text = if (singleCrawler.webviewNeeded == true) "Open in WebView" else "WebView",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val targetUrl = singleCrawler.baseUrl
                            val intent = Intent(context, WebViewActivity::class.java).apply {
                                putExtra("url", targetUrl)
                                putExtra("host", singleCrawler.baseUrl.toUri().host ?: "")
                            }
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            } else if (allFailedSources.size > 1) {
                Text(
                    text = "Resolve",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onResolveSheet() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            if (isSearching) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (singleFailedSource != null) {
                                onRetrySingleSource(singleFailedSource)
                            } else {
                                onRetryFailed()
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}
