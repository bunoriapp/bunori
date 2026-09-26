package com.halovoid.bunori.ui.feature.settings

import android.content.ClipDescription
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.data.repository.DEFAULT_EXTENSION_REPO_URL
import com.halovoid.bunori.extension.api.models.ExtensionRepo
import com.halovoid.bunori.ui.core.components.AppDialog
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.components.ConfirmDeleteDialog
import com.halovoid.bunori.ui.core.theme.*
import kotlinx.coroutines.launch
import java.net.URI

sealed interface RepoSettingDialogState {
    data object AddRepo : RepoSettingDialogState
    data class ConfirmDelete(val repo: ExtensionRepo) : RepoSettingDialogState
}

@Composable
fun RepoSettingScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val extensionRepos by viewModel.extensionRepos.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    var activeDialog by remember { mutableStateOf<RepoSettingDialogState?>(null) }

    when (val dialog = activeDialog) {
        is RepoSettingDialogState.AddRepo -> {
            AddRepoDialog(
                existingRepos = extensionRepos,
                onDismiss = { activeDialog = null },
                onAdd = { newRepo ->
                    viewModel.addExtensionRepo(newRepo)
                    activeDialog = null
                    scope.launch {
                        snackbarHostState.showSnackbar("Repository \"${newRepo.name}\" added successfully")
                    }
                }
            )
        }
        is RepoSettingDialogState.ConfirmDelete -> {
            ConfirmDeleteDialog(
                title = "Delete Repository",
                message = "Are you sure you want to remove \"${dialog.repo.name}\"? Extensions already installed from this repository will remain installed.",
                onConfirm = {
                    val repoToRemove = dialog.repo
                    activeDialog = null
                    viewModel.removeExtensionRepo(repoToRemove.name)
                    scope.launch {
                        snackbarHostState.showSnackbar("Repository \"${repoToRemove.name}\" removed")
                    }
                },
                onDismiss = { activeDialog = null }
            )
        }
        null -> Unit
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Repositories",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (extensionRepos.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { activeDialog = RepoSettingDialogState.AddRepo },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = {
                        Text(
                            text = "Add Repository",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    containerColor = BrandAccent,
                    contentColor = DarkBackground,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        if (extensionRepos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = DarkSurfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = BrandAccent
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "No Repositories",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add an extension repository to discover and install novel sources.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { activeDialog = RepoSettingDialogState.AddRepo },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandAccent,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Repository",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item(key = "repo_header") {
                    Text(
                        text = "Extension Repositories (${extensionRepos.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }

                items(
                    items = extensionRepos,
                    key = { it.name }
                ) { repo ->
                    val websiteUrl = getRepoWebsiteUrl(repo.url)

                    RepoCardItem(
                        title = repo.name,
                        url = repo.url,
                        isEnabled = repo.enabled,
                        onToggle = { checked ->
                            viewModel.setExtensionRepoEnabledByName(repo.name, checked)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (checked) "Repository \"${repo.name}\" enabled" else "Repository \"${repo.name}\" disabled"
                                )
                            }
                        },
                        onWebsiteClick = {
                            try {
                                uriHandler.openUri(websiteUrl)
                            } catch (_: Exception) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Unable to open repository website")
                                }
                            }
                        },
                        onCopyClick = {
                            clipboardManager.setText(AnnotatedString(repo.url))
                            scope.launch {
                                snackbarHostState.showSnackbar("Repository URL copied to clipboard")
                            }
                        },
                        onDeleteClick = {
                            activeDialog = RepoSettingDialogState.ConfirmDelete(repo)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RepoCardItem(
    title: String,
    url: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onWebsiteClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val websiteUrl = remember(url) { getRepoWebsiteUrl(url) }

    val subtitleText = remember(url) {
        try {
            val uri = URI(url)
            val host = uri.host?.removePrefix("www.") ?: url
            val pathSegments = uri.path?.split("/")?.filter { it.isNotBlank() } ?: emptyList()
            val filename = pathSegments.lastOrNull()
            if (!filename.isNullOrBlank()) "$host • $filename" else host
        } catch (_: Exception) {
            url
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) PrimaryText else SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText.copy(alpha = if (isEnabled) 0.75f else 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PrimaryText,
                    checkedTrackColor = BrandAccent,
                    uncheckedThumbColor = SecondaryText,
                    uncheckedTrackColor = DarkBackground
                )
            )

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Repository Options",
                        tint = SecondaryText.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(DarkSurfaceVariant)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Copy URL",
                                color = PrimaryText,
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = PrimaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onCopyClick()
                        }
                    )

                    if (websiteUrl.isNotBlank()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Open Website",
                                    color = PrimaryText,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = PrimaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onWebsiteClick()
                            }
                        )
                    }

                    HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Delete",
                                color = ErrorRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AddRepoDialog(
    existingRepos: List<ExtensionRepo>,
    onDismiss: () -> Unit,
    onAdd: (ExtensionRepo) -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    var urlText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

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
                    text = "Provide a unique name and URL for the extension repository (e.g. Bunori index.min.json or LNReader plugins.min.json).",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Repository Name",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = nameText,
                    onValueChange = {
                        nameText = it
                        errorMessage = null
                    },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "e.g. bext, lnreader, custom",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandAccent,
                        unfocusedBorderColor = BorderColor.copy(alpha = 0.4f),
                        focusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.2f),
                        cursorColor = BrandAccent,
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Repository URL",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        errorMessage = null
                    },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "https://...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText.copy(alpha = 0.6f)
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (urlText.isNotEmpty()) {
                                IconButton(onClick = { urlText = "" }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = "Clear",
                                        tint = SecondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        val clipData = clipboardManager.getText()
                                        if (clipData != null && clipData.text.isNotBlank()) {
                                            urlText = clipData.text.trim()
                                            errorMessage = null
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentPaste,
                                        contentDescription = "Paste from clipboard",
                                        tint = SecondaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { msg ->
                        {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandAccent,
                        unfocusedBorderColor = BorderColor.copy(alpha = 0.4f),
                        focusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.2f),
                        cursorColor = BrandAccent,
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedName = nameText.trim()
                    val trimmedUrl = urlText.trim()
                    when {
                        trimmedName.isBlank() -> {
                            errorMessage = "Repository name cannot be empty"
                        }
                        trimmedName.contains("/") || trimmedName.contains("\\") -> {
                            errorMessage = "Repository name cannot contain slashes"
                        }
                        existingRepos.any { it.name.trim().equals(trimmedName, ignoreCase = true) } -> {
                            errorMessage = "A repository named \"$trimmedName\" already exists"
                        }
                        trimmedUrl.isBlank() -> {
                            errorMessage = "Repository URL cannot be empty"
                        }
                        !trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://") && !trimmedUrl.startsWith("file://") -> {
                            errorMessage = "URL must start with http:// or https://"
                        }
                        existingRepos.any { it.url.trim().equals(trimmedUrl, ignoreCase = true) } -> {
                            errorMessage = "Repository URL already exists"
                        }
                        else -> {
                            onAdd(ExtensionRepo(name = trimmedName, url = trimmedUrl, enabled = true))
                        }
                    }
                }
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
                Text("Cancel", color = SecondaryText)
            }
        }
    )
}

private fun getRepoWebsiteUrl(url: String): String {
    return when {
        url == DEFAULT_EXTENSION_REPO_URL || url.contains("bunoriapp/bunori-extensions") -> {
            "https://github.com/bunoriapp/bunori-extensions"
        }
        url.contains("lnreader", ignoreCase = true) || url.contains("lnreader-plugins", ignoreCase = true) -> {
            "https://github.com/LNReader/lnreader-plugins"
        }
        url.startsWith("https://raw.githubusercontent.com/") -> {
            val parts = url.removePrefix("https://raw.githubusercontent.com/").split("/")
            if (parts.size >= 2) {
                "https://github.com/${parts[0]}/${parts[1]}"
            } else {
                url
            }
        }
        else -> url
    }
}
