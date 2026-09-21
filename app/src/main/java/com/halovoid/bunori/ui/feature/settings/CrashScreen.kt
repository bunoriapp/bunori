package com.halovoid.bunori.ui.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.crash.utils.CrashLogUtil
import com.halovoid.bunori.data.repository.StorageRepository

@Composable
fun CrashScreen(
    exception: Throwable?,
    storageRepository: StorageRepository,
    onRestartClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    InfoScreen(
        icon = Icons.Outlined.BugReport,
        headingText = "Crash Occurred",
        subtitleText = "The app has crashed. Please copy the logs and report the issue on Discord or Github.",
        rejectText = "Close App",
        onRejectClick = onRestartClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val log = CrashLogUtil(context, storageRepository).getFullLog(exception)
                    clipboardManager.setText(AnnotatedString(log))
                    android.widget.Toast.makeText(context, "Log copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy Log", fontSize = 13.sp)
            }

            Button(
                onClick = {
                    uriHandler.openUri("https://discord.gg/WzpTJuS4JT")
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2))
            ) {
                Icon(Icons.Outlined.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Report on Discord", fontSize = 13.sp)
            }
        }

        Surface(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .heightIn(max = 400.dp)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = exception?.stackTraceToString() ?: "Unknown error",
                modifier = Modifier
                    .padding(all = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
