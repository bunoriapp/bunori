package com.halovoid.bunori.ui.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.data.repository.DEFAULT_EXTENSION_REPO_URL
import com.halovoid.bunori.data.repository.DEFAULT_LNREADER_REPO_URL
import com.halovoid.bunori.ui.core.components.AppDialog
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.theme.*
import kotlinx.coroutines.launch

sealed interface ExtensionSettingsDialogState {
    data object AddRepo : ExtensionSettingsDialogState
}

@Composable
fun ExtensionSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val extensionRepoUrls by viewModel.extensionRepoUrls.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var activeDialog by remember { mutableStateOf<ExtensionSettingsDialogState?>(null) }

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

    when (activeDialog) {
        is ExtensionSettingsDialogState.AddRepo -> {
            AddRepoDialog(
                onDismiss = { activeDialog = null },
                onAdd = { newUrl ->
                    viewModel.addExtensionRepoUrl(newUrl)
                    activeDialog = null
                    scope.launch {
                        snackbarHostState.showSnackbar("Repository added")
                    }
                }
            )
        }
        null -> Unit
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

            SectionHeader(text = "Extension Repositories")

            extensionRepoUrls.forEach { url ->
                val repoTitle = when (url) {
                    DEFAULT_EXTENSION_REPO_URL -> "Bunori Official (.bext)"
                    DEFAULT_LNREADER_REPO_URL -> "LNReader Plugins (.js)"
                    else -> "Custom Repository"
                }

                RepoItemRow(
                    title = repoTitle,
                    url = url,
                    canDelete = extensionRepoUrls.size > 1,
                    onDelete = {
                        viewModel.removeExtensionRepoUrl(url)
                        scope.launch {
                            snackbarHostState.showSnackbar("Repository removed")
                        }
                    }
                )
            }

            SettingsRow(
                title = "Add Repository",
                subtitle = "Add a Bunori (.json) or LNReader (plugins.min.json) repository",
                icon = Icons.Outlined.Add,
                onClick = { activeDialog = ExtensionSettingsDialogState.AddRepo }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader(text = "Local Extensions")

            SettingsRow(
                title = "Install from File",
                subtitle = "Load a local .bext or .js extension package",
                icon = Icons.Outlined.FolderOpen,
                onClick = { filePickerLauncher.launch("*/*") }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun RepoItemRow(
    title: String,
    url: String,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Public,
                contentDescription = null,
                tint = BrandAccent,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete repository",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun AddRepoDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Repository",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter a Bunori index.min.json or LNReader plugins.min.json URL.",
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

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelMedium,
                    color = SecondaryText
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = { urlText = DEFAULT_EXTENSION_REPO_URL },
                        label = { Text("Bunori (.bext)", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { urlText = DEFAULT_LNREADER_REPO_URL },
                        label = { Text("LNReader (.js)", fontSize = 11.sp) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (urlText.isNotBlank()) onAdd(urlText.trim()) }
            ) {
                Text(
                    text = "Add",
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
