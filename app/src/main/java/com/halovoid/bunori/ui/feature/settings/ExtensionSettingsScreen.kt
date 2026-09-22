package com.halovoid.bunori.ui.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.data.repository.DEFAULT_EXTENSION_REPO_URL
import androidx.compose.foundation.shape.RoundedCornerShape
import com.halovoid.bunori.ui.core.components.AppDialog
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.theme.*
import kotlinx.coroutines.launch

@Composable
fun ExtensionSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val extensionRepoUrl by viewModel.extensionRepoUrl.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showRepoDialog by remember { mutableStateOf(false) }

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

    if (showRepoDialog) {
        RepoUrlDialog(
            initialUrl = extensionRepoUrl,
            onDismiss = { showRepoDialog = false },
            onSave = { newUrl ->
                viewModel.setExtensionRepoUrl(newUrl)
                showRepoDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Extensions",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader(text = "Repositories")

            SettingsRow(
                title = "Repository URL",
                subtitle = if (extensionRepoUrl.isBlank()) "None (Repository disabled)" else extensionRepoUrl,
//                icon = Icons.Outlined.Link,
                onClick = { showRepoDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader(text = "Local Extensions")

            SettingsRow(
                title = "Install from File",
                subtitle = "Load a local .bext extension package",
                icon = Icons.Outlined.FolderOpen,
                onClick = { filePickerLauncher.launch("*/*") }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun RepoUrlDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var urlText by remember { mutableStateOf(initialUrl) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Extension Repository",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter custom index.min.json repository URL for extensions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "https://...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandAccent,
                        unfocusedBorderColor = BorderColor.copy(alpha = 0.3f),
                        focusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.2f),
                        cursorColor = BrandAccent,
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { urlText = DEFAULT_EXTENSION_REPO_URL },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Reset to Default",
                        fontSize = 12.sp,
                        color = BrandAccent,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(urlText.trim()) }
            ) {
                Text(
                    text = "Save",
                    color = BrandAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PrimaryText)
            }
        }
    )
}
