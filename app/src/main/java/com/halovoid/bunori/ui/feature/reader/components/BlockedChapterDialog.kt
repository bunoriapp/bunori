package com.halovoid.bunori.ui.feature.reader.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.source.webview.WebViewActivity

@Composable
fun BlockedChapterDialog(
    chapter: Chapter,
    novelUrl: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenWebView: (Intent) -> Unit,
    context: Context,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, BorderColor),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = null,
                tint = BrandAccent,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Dynamic Content / Verification Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "\"${chapter.title}\" could not be scraped directly. The site may require Cloudflare verification or JavaScript DOM extraction.",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Retry", color = PrimaryText)
                }
                Button(
                    onClick = {
                        val targetUrl = chapter.sourceUrl?.takeIf { it.isNotBlank() } ?: chapter.url
                        val intent = Intent(context, WebViewActivity::class.java).apply {
                            putExtra("url", targetUrl)
                            putExtra("host", targetUrl.toUri().host ?: "")
                            putExtra("is_extraction_mode", true)
                            putExtra("chapter_id", chapter.id)
                            putExtra("chapter_index", chapter.index)
                            putExtra("chapter_title", chapter.title)
                            putExtra("novel_url", novelUrl)
                            putExtra("chapter_url", chapter.url)
                            putExtra("scanlation_source", chapter.scanlationSource)
                        }
                        onOpenWebView(intent)
                    },
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                ) {
                    Text("Open in WebView", color = Color.White)
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text("Dismiss", color = SecondaryText)
            }
        }
    }
}
