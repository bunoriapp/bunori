package com.halovoid.bunori.ui.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.BuildConfig
import com.halovoid.bunori.R
import com.halovoid.bunori.ui.core.theme.*

@Composable
fun MoreScreen(
    viewModel: SettingsViewModel,
    onNavigateToLayout: () -> Unit,
    onNavigateToDownloadsPref: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    onNavigateToSupportSettings: () -> Unit,
    onNavigateToBackupSettings: () -> Unit,
    onNavigateToUpdate: () -> Unit,
    onNavigateToThemeSettings: () -> Unit = {},
    onNavigateToExtensionSettings: () -> Unit = {},
    onNavigateToReaderSettings: () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_splash_logo),
                    contentDescription = "Bunori Logo",
                    colorFilter = ColorFilter.tint(BrandAccent),
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(modifier = Modifier.height(36.dp))

            HorizontalDivider(
                color = BorderColor.copy(alpha = 0.2f),
                thickness = 1.dp
            )

            if (updateState is AppUpdateState.UpdateAvailable || updateState is AppUpdateState.ReadyToInstall) {
                Spacer(modifier = Modifier.height(8.dp))
                UpdateProminentCard(
                    state = updateState,
                    onClick = {
                        if (updateState is AppUpdateState.UpdateAvailable) {
                            onNavigateToUpdate()
                        } else if (updateState is AppUpdateState.ReadyToInstall) {
                            viewModel.installUpdate(context, (updateState as AppUpdateState.ReadyToInstall).uri)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Settings")
            
            SettingsRow(
                title = "Theme",
                subtitle = "Theme mode, color palettes, pure AMOLED",
                icon = Icons.Outlined.Palette,
                onClick = onNavigateToThemeSettings
            )

            SettingsRow(
                title = "Reader",
                subtitle = "Reading mode, themes, fonts, volume keys, margins",
                icon = Icons.AutoMirrored.Outlined.ChromeReaderMode,
                onClick = onNavigateToReaderSettings
            )

            SettingsRow(
                title = "Layouts",
                subtitle = "Library layout, search view mode, novel detail defaults",
                icon = Icons.Outlined.CollectionsBookmark,
                onClick = onNavigateToLayout
            )

            SettingsRow(
                title = "General Preferences",
                subtitle = "Parallel downloads, data saver, folder path",
                icon = Icons.Outlined.Download,
                onClick = onNavigateToDownloadsPref
            )

            SettingsRow(
                title = "Extensions",
                subtitle = "Repository URL, install local extension packages",
                icon = Icons.Outlined.Explore,
                onClick = onNavigateToExtensionSettings
            )
            
            SettingsRow(
                title = "Advanced",
                subtitle = "Beta release channels, Data Pruning, Webviews",
                icon = Icons.Outlined.Code,
                onClick = onNavigateToAdvanced
            )

            SettingsRow(
                title = "Support Us",
                subtitle = "Contribute, star repository, donation",
                icon = Icons.Outlined.VolunteerActivism,
                onClick = onNavigateToSupportSettings
            )

            SettingsRow(
                title = "Backup & Restore",
                subtitle = "Manage app backups, database snapshots",
                icon = Icons.Outlined.Storage,
                onClick = onNavigateToBackupSettings
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader(text = "About")
            
            SettingsRow(
                title = "Version",
                subtitle = "v${BuildConfig.VERSION_NAME}"
            )
            
            SettingsRow(
                title = "Check for Updates",
                onClick = { viewModel.checkForUpdates() },
                trailingContent = {
                    AppUpdateStatus(
                        state = updateState,
                        onUpdateClick = { _ -> onNavigateToUpdate() },
                        onInstallClick = { uri -> viewModel.installUpdate(context, uri) },
                        onRetryClick = { viewModel.checkForUpdates() }
                    )
                }
            )
            
            SettingsRow(
                title = "What's New",
                subtitle = "View latest release notes",
                onClick = { uriHandler.openUri("https://github.com/LNCrawler/LNCrawler/releases") }
            )
            
            SettingsRow(
                title = "Open Source License",
                subtitle = "MIT License",
                onClick = { uriHandler.openUri("https://github.com/LNCrawler/LNCrawler/blob/main/LICENSE") }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader(text = "Help & Feedback")

            SettingsRow(
                title = "GitHub Issues",
                subtitle = "Report bugs or request new features",
                iconPainter = painterResource(id = R.drawable.ic_github),
                onClick = { uriHandler.openUri("https://github.com/LNCrawler/LNCrawler/issues") }
            )

            SettingsRow(
                title = "Discord Server",
                subtitle = "Join our community for chat & support",
                iconPainter = painterResource(id = R.drawable.ic_discord),
                onClick = { uriHandler.openUri("https://discord.gg/A6cY7pN6Y") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocialIcon(
                    painter = painterResource(id = R.drawable.ic_discord),
                    onClick = { uriHandler.openUri("https://discord.gg/A6cY7pN6Y") }
                )
                Spacer(modifier = Modifier.width(20.dp))
                SocialIcon(
                    painter = painterResource(id = R.drawable.ic_github),
                    onClick = { uriHandler.openUri("https://github.com/LNCrawler/LNCrawler") }
                )
                Spacer(modifier = Modifier.width(20.dp))
                SocialIcon(
                    painter = painterResource(id = R.drawable.ic_reddit),
                    onClick = { uriHandler.openUri("https://www.reddit.com/user/BrilliantLeopard3196/") }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = SecondaryText,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null || iconPainter != null) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BrandAccent,
                    modifier = Modifier.size(22.dp)
                )
            } else if (iconPainter != null) {
                Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = BrandAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = PrimaryText
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SecondaryText.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SocialIcon(
    painter: Painter,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .background(BrandAccent.copy(alpha = 0.08f), CircleShape)
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = BrandAccent,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun AppUpdateStatus(
    state: AppUpdateState,
    onUpdateClick: (AppUpdateState.UpdateAvailable) -> Unit,
    onInstallClick: (String) -> Unit,
    onRetryClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val (text, color, isLoading) = when (state) {
            is AppUpdateState.Loading, is AppUpdateState.Idle -> 
                Triple("Checking updates...", SecondaryText, true)
            is AppUpdateState.UpdateAvailable -> 
                Triple("Update available", BrandAccent, false)
            is AppUpdateState.Downloading ->
                Triple("Downloading...", BrandAccent, true)
            is AppUpdateState.ReadyToInstall ->
                Triple("Ready to install", SuccessGreen, false)
            is AppUpdateState.Installing ->
                Triple("Installing...", BrandAccent, true)
            is AppUpdateState.UpToDate -> 
                Triple("Up to date", SuccessGreen.copy(alpha = 0.7f), false)
            is AppUpdateState.Error -> 
                Triple("Check failed", ErrorRed, false)
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = color,
                strokeWidth = 1.5.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
        } else if (state !is AppUpdateState.UpToDate) {
            Icon(
                imageVector = if (state is AppUpdateState.Error) Icons.Default.Error else Icons.Outlined.Info,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = if (state is AppUpdateState.UpToDate) FontWeight.Normal else FontWeight.Bold,
            modifier = Modifier.clickable(enabled = state is AppUpdateState.UpdateAvailable || state is AppUpdateState.ReadyToInstall || state is AppUpdateState.Error) {
                when (state) {
                    is AppUpdateState.UpdateAvailable -> onUpdateClick(state)
                    is AppUpdateState.ReadyToInstall -> onInstallClick(state.uri)
                    is AppUpdateState.Error -> onRetryClick()
                    else -> {}
                }
            }
        )
    }
}

@Composable
fun UpdateProminentCard(
    state: AppUpdateState,
    onClick: () -> Unit
) {
    val isReady = state is AppUpdateState.ReadyToInstall
    val tagName = if (state is AppUpdateState.UpdateAvailable) state.tagName else "New Version"
    
    Surface(
        onClick = onClick,
        color = if (isReady) SuccessGreen.copy(alpha = 0.08f) else BrandAccent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (isReady) SuccessGreen.copy(alpha = 0.2f) else BrandAccent.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isReady) SuccessGreen.copy(alpha = 0.15f) else BrandAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isReady) Icons.Outlined.CheckCircle else Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    tint = if (isReady) SuccessGreen else BrandAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isReady) "Ready to Install" else "Update Available",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Text(
                    text = if (isReady) "Tap to finish installation" else "New version $tagName is out",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SecondaryText.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
