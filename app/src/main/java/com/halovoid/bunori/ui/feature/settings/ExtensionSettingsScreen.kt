package com.halovoid.bunori.ui.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.theme.*
import kotlinx.coroutines.launch

private fun getLanguageDisplayName(code: String): String {
    return when (code.lowercase().trim()) {
        "all", "multi" -> "Multi-language"
        "en" -> "English"
        "es" -> "Spanish"
        "fr" -> "French"
        "de" -> "German"
        "it" -> "Italian"
        "pt", "pt-br" -> "Portuguese"
        "ru" -> "Russian"
        "zh" -> "Chinese"
        "ja" -> "Japanese"
        "ko" -> "Korean"
        "ar" -> "Arabic"
        "id" -> "Indonesian"
        "vi" -> "Vietnamese"
        "th" -> "Thai"
        "tr" -> "Turkish"
        else -> try {
            val loc = java.util.Locale.forLanguageTag(code)
            val name = loc.getDisplayName(loc)
            if (name.isNotBlank() && !name.equals(code, ignoreCase = true)) name.replaceFirstChar { it.uppercase() } else code.uppercase()
        } catch (_: Exception) {
            code.uppercase()
        }
    }
}

@Composable
fun ExtensionSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToRepoSettings: () -> Unit = {}
) {
    val extensionRepoUrls by viewModel.extensionRepoUrls.collectAsStateWithLifecycle()
    val disabledRepoUrls by viewModel.disabledExtensionRepoUrls.collectAsStateWithLifecycle()
    val showWasmSlowModeToast by viewModel.showWasmSlowModeToast.collectAsStateWithLifecycle()
    val enabledLanguages by viewModel.enabledExtensionLanguages.collectAsStateWithLifecycle()
    val availableLanguages by viewModel.availableExtensionLanguages.collectAsStateWithLifecycle()

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

            SectionHeader(text = "Extension Languages")

            if (availableLanguages.isNotEmpty()) {
                val enabledLower = enabledLanguages.map { it.lowercase() }.toSet()
                availableLanguages.forEach { lang ->
                    val isChecked = lang.lowercase() in enabledLower
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = getLanguageDisplayName(lang),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = PrimaryText
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = DarkSurfaceVariant
                                ) {
                                    Text(
                                        text = lang.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SecondaryText,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                viewModel.setExtensionLanguageEnabled(lang, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PrimaryText,
                                checkedTrackColor = BrandAccent,
                                uncheckedThumbColor = SecondaryText,
                                uncheckedTrackColor = DarkBackground
                            )
                        )
                    }
                }
            } else {
                Text(
                    text = "No languages available from configured repositories",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Runtime")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
