package com.halovoid.bunori.ui.feature.settings

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.BuildConfig
import com.halovoid.bunori.ui.core.theme.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDetailScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val betaModeApp by viewModel.betaModeApp.collectAsStateWithLifecycle()

    // Cache release info so version & download url stay visible across state transitions
    var cachedReleaseInfo by remember { mutableStateOf<AppUpdateState.UpdateAvailable?>(null) }
    LaunchedEffect(updateState) {
        val current = updateState
        if (current is AppUpdateState.UpdateAvailable) {
            cachedReleaseInfo = current
        }
    }

    val state = updateState
    val releaseInfo = cachedReleaseInfo ?: (state as? AppUpdateState.UpdateAvailable)

    val targetVersion = releaseInfo?.tagName?.removePrefix("v") ?: BuildConfig.VERSION_NAME
    val releaseUrl = releaseInfo?.releaseUrl
    val downloadUrl = releaseInfo?.apkDownloadUrl

    val publishedDate = remember(releaseInfo?.publishedAt) {
        releaseInfo?.publishedAt?.let {
            try {
                val zdt = ZonedDateTime.parse(it)
                zdt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH))
            } catch (_: Exception) {
                null
            }
        }
    }

    val primaryAbi = remember {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        when {
            abi.contains("arm64") -> "arm64"
            abi.contains("x86_64") -> "x86_64"
            abi.contains("v7a") -> "armv7"
            else -> abi
        }
    }

    val channelText = if (betaModeApp) "Beta" else "Release"

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "App Update",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryText
                        )
                    }
                },
                actions = {
                    if (!releaseUrl.isNullOrBlank()) {
                        IconButton(onClick = {
                            try {
                                uriHandler.openUri(releaseUrl)
                            } catch (_: Exception) {}
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "View on GitHub",
                                tint = PrimaryText
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = PrimaryText,
                    navigationIconContentColor = PrimaryText
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. App Title
            Text(
                text = "Bunori",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Version Transition Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DarkSurfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(0.5.dp, BorderColor.copy(alpha = 0.25f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = SecondaryText,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = BrandAccent,
                        modifier = Modifier.size(13.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "v$targetVersion",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (publishedDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Released on $publishedDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Specs / Build Info Bar (spans full width, vertical dividers)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Size",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "~17 MB",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                }

                VerticalDivider(
                    modifier = Modifier.height(34.dp),
                    thickness = 1.dp,
                    color = BorderColor.copy(alpha = 0.6f)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Architecture",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = primaryAbi,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                }

                VerticalDivider(
                    modifier = Modifier.height(34.dp),
                    thickness = 1.dp,
                    color = BorderColor.copy(alpha = 0.6f)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Channel",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryText
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = channelText,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(44.dp))

            // 4. The Hero Action Disc (Approach A: Icon is the button)
            val discSize = 84.dp
            val iconSize = 36.dp

            val isDownloading = state is AppUpdateState.Downloading
            val isInstalling = state is AppUpdateState.Installing
            val isReady = state is AppUpdateState.ReadyToInstall
            val isError = state is AppUpdateState.Error

            val actionIcon = when {
                isReady -> Icons.Outlined.SystemUpdate
                isError -> Icons.Outlined.Refresh
                state is AppUpdateState.UpToDate -> Icons.Outlined.CheckCircle
                else -> Icons.Outlined.FileDownload
            }

            val actionTitle = when {
                isDownloading -> "Downloading Update..."
                isInstalling -> "Installing Update..."
                isReady -> "Ready to Install"
                isError -> "Download Failed"
                state is AppUpdateState.UpToDate -> "Up to Date"
                else -> "Download Update"
            }

            val actionSubtitle = when {
                isDownloading -> "Please keep Bunori open"
                isInstalling -> "Opening system package installer"
                isReady -> "Tap to begin installation"
                isError -> "Tap to retry download"
                state is AppUpdateState.UpToDate -> "You are on the latest version"
                else -> "Tap the disc to start downloading"
            }

            val isClickable = !isDownloading && !isInstalling && state !is AppUpdateState.UpToDate

            Box(
                modifier = Modifier.size(discSize + 20.dp),
                contentAlignment = Alignment.Center
            ) {
                // Active progress ring when downloading or installing
                if (isDownloading || isInstalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(discSize + 12.dp),
                        color = BrandAccent,
                        strokeWidth = 2.5.dp
                    )
                }

                // Interactive Disc
                Surface(
                    onClick = {
                        when (state) {
                            is AppUpdateState.ReadyToInstall -> {
                                viewModel.installUpdate(context, state.uri)
                            }
                            is AppUpdateState.Error -> {
                                downloadUrl?.let { viewModel.startUpdateDownload(it) }
                                    ?: viewModel.checkForUpdates()
                            }
                            else -> {
                                downloadUrl?.let { viewModel.startUpdateDownload(it) }
                            }
                        }
                    },
                    enabled = isClickable,
                    shape = CircleShape,
                    color = DarkSurfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (isReady) BrandAccent else BrandAccent.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.size(discSize)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = actionTitle,
                            tint = BrandAccent,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = actionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isError) ErrorRed else PrimaryText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = actionSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText,
                textAlign = TextAlign.Center
            )

            // 5. GitHub Link at Bottom
            if (!releaseUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(28.dp))
                TextButton(
                    onClick = {
                        try {
                            uriHandler.openUri(releaseUrl)
                        } catch (_: Exception) {}
                    }
                ) {
                    Text(
                        text = "View Release on GitHub ↗",
                        fontSize = 13.sp,
                        color = SecondaryText,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
