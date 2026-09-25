package com.halovoid.bunori.ui.feature.settings

import android.content.ClipDescription
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.halovoid.bunori.data.repository.DEFAULT_LNREADER_REPO_URL
import com.halovoid.bunori.ui.core.components.AppDialog
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.components.ConfirmDeleteDialog
import com.halovoid.bunori.ui.core.theme.*
import kotlinx.coroutines.launch
import java.net.URI

sealed interface RepoSettingDialogState {
    data object AddRepo : RepoSettingDialogState
    data class ConfirmDelete(val url: String, val title: String) : RepoSettingDialogState
}

@Composable
fun RepoSettingScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val extensionRepoUrls by viewModel.extensionRepoUrls.collectAsStateWithLifecycle()
    val disabledRepoUrls by viewModel.disabledExtensionRepoUrls.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    var activeDialog by remember { mutableStateOf<RepoSettingDialogState?>(null) }

    when (val dialog = activeDialog) {
        is RepoSettingDialogState.AddRepo -> {
            AddRepoDialog(
                existingUrls = extensionRepoUrls,
                onDismiss = { activeDialog = null },
                onAdd = { newUrl ->
                    viewModel.addExtensionRepoUrl(newUrl)
                    activeDialog = null
                    scope.launch {
                        snackbarHostState.showSnackbar("Repository added successfully")
                    }
                }
            )
        }
        is RepoSettingDialogState.ConfirmDelete -> {
            ConfirmDeleteDialog(
                title = "Delete Repository",
                message = "Are you sure you want to remove \"${dialog.title}\"? Extensions already installed from this repository will remain installed.",
                onConfirm = {
                    val urlToRemove = dialog.url
                    activeDialog = null
                    viewModel.removeExtensionRepoUrl(urlToRemove)
                    scope.launch {
                        snackbarHostState.showSnackbar("Repository removed")
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
                onBack = onBack,
                actions = {
                    IconButton(onClick = { activeDialog = RepoSettingDialogState.AddRepo }) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Add Repository",
                            tint = PrimaryText
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { innerPadding ->
        if (extensionRepoUrls.isEmpty()) {
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
                                imageVector = Icons.Outlined.Public,
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
                        text = "Extension Repositories (${extensionRepoUrls.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }

                items(
                    items = extensionRepoUrls,
                    key = { it }
                ) { url ->
                    val repoTitle = getRepoTitle(url)
                    val websiteUrl = getRepoWebsiteUrl(url)
                    val isEnabled = url !in disabledRepoUrls

                    RepoCardItem(
                        title = repoTitle,
                        url = url,
                        isEnabled = isEnabled,
                        onToggle = { checked ->
                            viewModel.setExtensionRepoEnabled(url, checked)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (checked) "Repository enabled" else "Repository disabled"
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
                            clipboardManager.setText(AnnotatedString(url))
                            scope.launch {
                                snackbarHostState.showSnackbar("Repository URL copied to clipboard")
                            }
                        },
                        onDeleteClick = {
                            activeDialog = RepoSettingDialogState.ConfirmDelete(url, repoTitle)
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Section: Leading Icon, Title, URL, and Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isEnabled) BrandAccent.copy(alpha = 0.12f) else DarkSurfaceVariant,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = null,
                            tint = if (isEnabled) BrandAccent else SecondaryText.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled) PrimaryText else SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText.copy(alpha = if (isEnabled) 0.8f else 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

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
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.25f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons Row: Website, Copy, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Website Option
                TextButton(
                    onClick = onWebsiteClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Website",
                        tint = BrandAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Website",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = BrandAccent
                    )
                }

                // 2. Copy Option
                TextButton(
                    onClick = onCopyClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        tint = SecondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copy",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = SecondaryText
                    )
                }

                // 3. Delete Option
                TextButton(
                    onClick = onDeleteClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = ErrorRed
                    )
                }
            }
        }
    }
}

@Composable
fun AddRepoDialog(
    existingUrls: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
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
                    text = "Enter a Bunori index.min.json or LNReader plugins.min.json repository URL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText
                )
                Spacer(modifier = Modifier.height(14.dp))
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

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SecondaryText
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            urlText = DEFAULT_EXTENSION_REPO_URL
                            errorMessage = null
                        },
                        label = { Text("BEXT Official (.bext)", fontSize = 12.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                            labelColor = PrimaryText
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = BorderColor.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    SuggestionChip(
                        onClick = {
                            urlText = DEFAULT_LNREADER_REPO_URL
                            errorMessage = null
                        },
                        label = { Text("LNReader (.js)", fontSize = 12.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = DarkSurfaceVariant.copy(alpha = 0.5f),
                            labelColor = PrimaryText
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = BorderColor.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = urlText.trim()
                    when {
                        trimmed.isBlank() -> {
                            errorMessage = "URL cannot be empty"
                        }
                        !trimmed.startsWith("http://") && !trimmed.startsWith("https://") -> {
                            errorMessage = "URL must start with http:// or https://"
                        }
                        existingUrls.contains(trimmed) -> {
                            errorMessage = "Repository already exists"
                        }
                        else -> {
                            onAdd(trimmed)
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

private fun getRepoTitle(url: String): String {
    return when (url) {
        DEFAULT_EXTENSION_REPO_URL -> "BEXT Official (.bext)"
        DEFAULT_LNREADER_REPO_URL -> "LNReader Plugins (.js)"
        else -> {
            try {
                val host = URI(url).host ?: url
                when {
                    url.contains("bunori", ignoreCase = true) || url.contains("bext", ignoreCase = true) -> "BEXT Repository"
                    url.contains("lnreader", ignoreCase = true) -> "LNReader Repository"
                    host.isNotBlank() -> host
                    else -> "Custom Repository"
                }
            } catch (_: Exception) {
                "Custom Repository"
            }
        }
    }
}

private fun getRepoWebsiteUrl(url: String): String {
    return when {
        url == DEFAULT_EXTENSION_REPO_URL || url.contains("bunoriapp/bunori-extensions") -> {
            "https://github.com/bunoriapp/bunori-extensions"
        }
        url == DEFAULT_LNREADER_REPO_URL || url.contains("LNReader/lnreader-plugins") -> {
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
