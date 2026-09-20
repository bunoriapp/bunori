package com.halovoid.bunori.ui.feature.crawler

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.ui.core.components.SourceIcon
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.crawler.components.ConfigDivider
import com.halovoid.bunori.ui.feature.crawler.components.ConfigItemRow
import java.net.URI

@Composable
fun ExtensionInfoScreen(
    extensionId: String,
    viewModel: CrawlerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extensionItems by viewModel.extensionItems.collectAsStateWithLifecycle()
    val inProgressIds by viewModel.inProgressIds.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showUninstallConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messageFlow.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val item = remember(extensionItems, extensionId) {
        extensionItems.firstOrNull { it.id == extensionId }
    }

    // Auto-navigate back if extension was uninstalled
    LaunchedEffect(item) {
        if (item != null && !item.isInstalled && !inProgressIds.contains(extensionId)) {
            onBack()
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = DarkBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryText
                        )
                    }
                    Text(
                        text = "Extension Info",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        if (item == null) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandAccent)
            }
            return@Scaffold
        }

        val scrollState = rememberScrollState()
        val isActionInProgress = inProgressIds.contains(item.id)

        // Configuration details derived from loaded extension or repository metadata
        val manifest = item.loadedExtension?.manifest
        val metadata = item.loadedExtension?.extension?.metadata
        val repoEntry = item.repoEntry

        val concurrency = metadata?.runnerConcurrency ?: repoEntry?.runnerConcurrency ?: manifest?.runnerConcurrency ?: 3
        val cooldownMs = metadata?.runnerCooldown ?: repoEntry?.runnerCooldown ?: manifest?.runnerCooldown ?: 1000L
        val maxAttempts = metadata?.maxAttempts ?: repoEntry?.maxAttempts ?: manifest?.maxAttempts ?: 3
        val webviewNeeded = metadata?.webviewNeeded ?: repoEntry?.webviewNeeded ?: manifest?.webviewNeeded ?: false
        val apiVersion = metadata?.apiVersion ?: repoEntry?.apiVersion ?: manifest?.apiVersion ?: 1
        val entryClass = manifest?.entryClass ?: repoEntry?.entryClass ?: "Not specified"

        val packageSizeBytes = item.loadedExtension?.bextFile?.takeIf { it.exists() }?.length()
            ?: repoEntry?.size
            ?: 0L
        val packageSizeFormatted = if (packageSizeBytes > 0) {
            String.format("%.1f KB", packageSizeBytes / 1024.0)
        } else {
            "Installed"
        }

        val hostName = remember(item.baseUrl) {
            try {
                URI(item.baseUrl).host ?: item.baseUrl
            } catch (_: Exception) {
                item.baseUrl.removePrefix("https://").removePrefix("http://").substringBefore('/')
            }
        }

        val languageDisplayName = remember(item.lang) {
            when (item.lang.lowercase()) {
                "en" -> "English (EN)"
                "all", "multi" -> "Multi-language"
                "es" -> "Spanish (ES)"
                "fr" -> "French (FR)"
                "ja" -> "Japanese (JA)"
                "zh" -> "Chinese (ZH)"
                "ko" -> "Korean (KO)"
                "ru" -> "Russian (RU)"
                else -> item.lang.uppercase()
            }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. TOP CENTER: Extension Icon (scaled to fill space)
            SourceIcon(
                model = item.iconModel,
                fallbackText = item.name,
                size = 84.dp,
                shape = RoundedCornerShape(18.dp),
                contentPadding = 0.dp,
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. JUST BELOW: Name
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 3. BELOW IT: Website Name (clickable to open in browser)
            if (item.baseUrl.isNotBlank()) {
                Surface(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, item.baseUrl.toUri())
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = hostName,
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandAccent,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open Website",
                            tint = BrandAccent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            // 4. VERSION & LANGUAGE (Side-by-side with separator, no card background)
            val versionText = when {
                item.hasUpdate -> "v${item.installedVersion} → v${item.repoVersion}"
                item.installedVersion != null -> "v${item.installedVersion}"
                item.repoVersion != null -> "v${item.repoVersion}"
                else -> "v1.0.0"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            ) {
                Text(
                    text = versionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.hasUpdate) BrandAccent else PrimaryText,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Text(
                    text = languageDisplayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText
                )
            }

            // 5. UNINSTALL AND UPDATE ACTIONS (Integrated native feel)
            if (item.hasUpdate) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showUninstallConfirmation = true },
                        enabled = !isActionInProgress,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Uninstall",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Button(
                        onClick = {
                            item.repoEntry?.let { entry -> viewModel.installExtension(entry) }
                        },
                        enabled = !isActionInProgress,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandAccent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        if (isActionInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Updating...",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Update,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Update",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(DarkSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = BrandAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Up to date",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = SecondaryText
                        )
                    }

                    OutlinedButton(
                        onClick = { showUninstallConfirmation = true },
                        enabled = !isActionInProgress,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Uninstall",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. SOURCE CONFIGURATION SECTION (List presentation without card background)
            Text(
                text = "Source Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                ConfigItemRow(
                    icon = Icons.Outlined.Speed,
                    title = "Rate Limit (Cooldown)",
                    subtitle = "Pause delay between consecutive network requests",
                    value = "${cooldownMs} ms (${cooldownMs / 1000.0}s)"
                )

                ConfigDivider()

                ConfigItemRow(
                    icon = Icons.Outlined.AltRoute,
                    title = "Max Concurrency",
                    subtitle = "Simultaneous background worker threads",
                    value = "$concurrency workers"
                )

                ConfigDivider()

                ConfigItemRow(
                    icon = Icons.Outlined.Replay,
                    title = "Max Attempts",
                    subtitle = "Automatic retry attempts on failure",
                    value = "$maxAttempts retries"
                )

                ConfigDivider()

                ConfigItemRow(
                    icon = Icons.Outlined.Security,
                    title = "WebView Bypass",
                    subtitle = "Bypasses Cloudflare or bot protection with browser session",
                    value = if (webviewNeeded) "Required" else "Not required"
                )

                ConfigDivider()

                ConfigItemRow(
                    icon = Icons.Outlined.Code,
                    title = "API Contract Version",
                    subtitle = "Target Bunori extension interface specification",
                    value = "v$apiVersion"
                )

                ConfigDivider()

                ConfigItemRow(
                    icon = Icons.Outlined.Folder,
                    title = "Package Size",
                    subtitle = "Storage footprint of the extension bytecode package",
                    value = packageSizeFormatted
                )

                ConfigDivider()

                ConfigItemRow(
                    icon = Icons.Outlined.DataObject,
                    title = "Entry Class",
                    subtitle = entryClass,
                    value = "Active"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Uninstall Confirmation Dialog
    if (showUninstallConfirmation) {
        AlertDialog(
            onDismissRequest = { showUninstallConfirmation = false },
            title = {
                Text(
                    text = "Uninstall ${item?.name ?: "Extension"}?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
            },
            text = {
                Text(
                    text = "This will remove the extension package and delete all local crawler data associated with it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUninstallConfirmation = false
                        viewModel.uninstallExtension(extensionId)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Uninstall", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallConfirmation = false }) {
                    Text("Cancel", color = PrimaryText)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
