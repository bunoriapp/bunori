package com.halovoid.bunori.ui.feature.reader.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.ui.core.components.AppDialog
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
    AppDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = BrandAccent,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Chapter Warning",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        },
        text = {
            Text(
                text = "\"${chapter.title}\" could not be loaded directly or may contain protected / dynamic content. You can retry loading or open the webview to bypass verification or view it directly.",
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText
            )
        },
        confirmButton = {
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandAccent,
                    contentColor = Color.White
                )
            ) {
                Text("Open in WebView")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onRetry) {
                Text("Retry", color = PrimaryText)
            }
        }
    )
}

