package com.halovoid.bunori.ui.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.settings.components.CacheClearFrequencyBottomSheet
import com.halovoid.bunori.ui.feature.settings.components.NovelPruneFrequencyBottomSheet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import kotlinx.coroutines.launch

sealed interface AdvancedDialogState {
    data object NovelPruneSheet : AdvancedDialogState
    data object CacheClearSheet : AdvancedDialogState
}

@Composable
fun AdvancedSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToWebView: () -> Unit = {}
) {
    val appBeta by viewModel.betaModeApp.collectAsStateWithLifecycle()
    val showWasmSlowModeToast by viewModel.showWasmSlowModeToast.collectAsStateWithLifecycle()
    val novelPruneFreq by viewModel.novelPruneFrequency.collectAsStateWithLifecycle()
    val cacheClearFreq by viewModel.cacheClearFrequency.collectAsStateWithLifecycle()

    var activeDialog by remember { mutableStateOf<AdvancedDialogState?>(null) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Advanced Settings",
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader(text = "Release Channels")
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setBetaModeApp(!appBeta) }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Beta App Releases",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Get notified of pre-release application builds (early features/unstable).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
                Switch(
                    checked = appBeta,
                    onCheckedChange = { viewModel.setBetaModeApp(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryText,
                        checkedTrackColor = BrandAccent,
                        uncheckedThumbColor = SecondaryText,
                        uncheckedTrackColor = DarkBackground
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Extensions")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setShowWasmSlowModeToast(!showWasmSlowModeToast) }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Slow Mode Notifications",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Show toast notifications when an extension falls back to portable WASM bytecode mode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
                Switch(
                    checked = showWasmSlowModeToast,
                    onCheckedChange = { viewModel.setShowWasmSlowModeToast(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryText,
                        checkedTrackColor = BrandAccent,
                        uncheckedThumbColor = SecondaryText,
                        uncheckedTrackColor = DarkBackground
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Maintenance & Automation")

            // Novel Prune Frequency Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeDialog = AdvancedDialogState.NovelPruneSheet }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Novel Pruning Frequency",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Auto-delete novels not in library and with no active tasks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
                Text(
                    text = novelPruneFreq,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            // Prune Novels Now Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.pruneNovelsNow { count ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (count > 0) "Pruned $count unused novels" else "No unreferenced novels to prune"
                                )
                            }
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prune Unused Novels Now",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Immediately remove novel details not saved in your library",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cache Clear Frequency Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeDialog = AdvancedDialogState.CacheClearSheet }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cache Clearing Frequency",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Auto-delete temporary chapter cache from online reading",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
                Text(
                    text = cacheClearFreq,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            // Clear Cache Now Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.clearCacheNow { count ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (count > 0) "Cleared $count cached items" else "Reading cache is already clean"
                                )
                            }
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Clear Reading Cache Now",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Immediately delete temporary online reading cache",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Web & Network")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToWebView() }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WebView",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "User agent, cookies, and webview data",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = SecondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        when (activeDialog) {
            is AdvancedDialogState.NovelPruneSheet -> {
                NovelPruneFrequencyBottomSheet(
                    currentFrequency = novelPruneFreq,
                    onFrequencySelected = { viewModel.setNovelPruneFrequency(it) },
                    onDismiss = { activeDialog = null }
                )
            }
            is AdvancedDialogState.CacheClearSheet -> {
                CacheClearFrequencyBottomSheet(
                    currentFrequency = cacheClearFreq,
                    onFrequencySelected = { viewModel.setCacheClearFrequency(it) },
                    onDismiss = { activeDialog = null }
                )
            }
            null -> Unit
        }
    }
}

