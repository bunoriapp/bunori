package com.halovoid.bunori.ui.feature.reader.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.halovoid.bunori.ui.core.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    title: String,
    subtitle: String?,
    isBlockedOrEmpty: Boolean = false,
    onBack: () -> Unit,
    onOpenToc: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenBlockedDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkBackground.copy(alpha = 0.95f),
        contentColor = PrimaryText,
        modifier = modifier.fillMaxWidth()
    ) {
        TopAppBar(
            modifier = Modifier.statusBarsPadding(),
            title = {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText,
                            maxLines = 1
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                IconButton(onClick = onOpenToc) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Table of contents"
                    )
                }
                if (isBlockedOrEmpty) {
                    IconButton(onClick = onOpenBlockedDialog) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Resolve Verification / WebView",
                            tint = BrandAccent
                        )
                    }
                } else {
                    IconButton(onClick = onToggleFullscreen) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen"
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = PrimaryText,
                navigationIconContentColor = PrimaryText,
                actionIconContentColor = PrimaryText
            )
        )
    }
}
