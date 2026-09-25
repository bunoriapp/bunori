package com.halovoid.bunori.ui.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.theme.*
import kotlinx.coroutines.launch

@Composable
fun ExtensionSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToRepoSettings: () -> Unit = {}
) {
    val extensionRepoUrls by viewModel.extensionRepoUrls.collectAsStateWithLifecycle()
    val disabledRepoUrls by viewModel.disabledExtensionRepoUrls.collectAsStateWithLifecycle()
    val showWasmSlowModeToast by viewModel.showWasmSlowModeToast.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.installExtensionFromUri(it) { _, message ->
                scope.launch {
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Extension Settings",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader(text = "Repositories")

            val activeCount = extensionRepoUrls.size - disabledRepoUrls.count { it in extensionRepoUrls }
            val repoSubtitle = if (extensionRepoUrls.isEmpty()) {
                "No repositories configured"
            } else {
                "${extensionRepoUrls.size} configured • $activeCount enabled"
            }

            SettingsRow(
                title = "Extension Repositories",
                subtitle = repoSubtitle,
                icon = Icons.Outlined.Public,
                onClick = onNavigateToRepoSettings
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Local Extensions")

            SettingsRow(
                title = "Install from File",
                subtitle = "Load a local .bext or .js extension package",
                icon = Icons.Outlined.FolderOpen,
                onClick = { filePickerLauncher.launch("*/*") }
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Runtime")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Slow Mode Notifications",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Show notification when WASM runtime enters interpreted mode",
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

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
