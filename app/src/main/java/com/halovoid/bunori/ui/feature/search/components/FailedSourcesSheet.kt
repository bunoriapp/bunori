package com.halovoid.bunori.ui.feature.search.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.source.webview.WebViewActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FailedSourcesSheet(
    allFailedSources: List<String>,
    onRetrySingleSource: (String) -> Unit,
    onRetryAllFailed: () -> Unit,
    onDismiss: () -> Unit,
    context: Context
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Sources Could Not Be Reached",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Some sources may require Cloudflare security clearance in WebView.",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText
            )
            Spacer(modifier = Modifier.height(16.dp))

            allFailedSources.forEach { sourceName ->
                val crawler = remember(sourceName) {
                    CrawlerFactory.getCrawlers().find { it.name.equals(sourceName, ignoreCase = true) }
                }
                val isCloudflare = crawler?.webviewNeeded == true

                Surface(
                    color = DarkSurfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isCloudflare) WarningAmber.copy(alpha = 0.3f) else BorderColor.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = sourceName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            Text(
                                text = if (isCloudflare) "Cloudflare clearance needed" else "Connection failed",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCloudflare) WarningAmber else SecondaryText,
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (crawler != null && crawler.baseUrl.isNotBlank()) {
                                Button(
                                    onClick = {
                                        val targetUrl = crawler.baseUrl
                                        val intent = Intent(context, WebViewActivity::class.java).apply {
                                            putExtra("url", targetUrl)
                                            putExtra("host", crawler.baseUrl.toUri().host ?: "")
                                        }
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isCloudflare) WarningAmber.copy(alpha = 0.2f) else DarkSurface,
                                        contentColor = if (isCloudflare) WarningAmber else PrimaryText
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("WebView", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            OutlinedButton(
                                onClick = { onRetrySingleSource(sourceName) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    onDismiss()
                    onRetryAllFailed()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
            ) {
                Text("Retry All Failed Searches (${allFailedSources.size})", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
