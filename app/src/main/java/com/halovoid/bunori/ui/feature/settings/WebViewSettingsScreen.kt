package com.halovoid.bunori.ui.feature.settings

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.api.core.network.NetworkClient
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.components.ConfirmCancelDialog
import com.halovoid.bunori.ui.core.theme.*
import java.io.File

sealed interface WebViewDialogState {
    data object EditUserAgent : WebViewDialogState
    data object ClearCookies : WebViewDialogState
    data object ClearData : WebViewDialogState
}

@Composable
fun WebViewSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToManualCookies: () -> Unit = {}
) {
    val context = LocalContext.current
    val customUa by viewModel.customUserAgent.collectAsStateWithLifecycle()
    val activeUa = customUa?.takeIf { it.isNotBlank() } ?: NetworkClient.DEFAULT_USER_AGENT

    var activeDialog by remember { mutableStateOf<WebViewDialogState?>(null) }
    var editUaText by remember { mutableStateOf(activeUa) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "WebView Settings",
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // -------------------------------------------------------------
            // Section 1: User Agent
            // -------------------------------------------------------------
            SectionHeader(text = "User Agent")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        editUaText = activeUa
                        activeDialog = WebViewDialogState.EditUserAgent
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Browser User Agent",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryText
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (customUa.isNullOrBlank()) BrandAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (customUa.isNullOrBlank()) "Default" else "Custom",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (customUa.isNullOrBlank()) BrandAccent else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeUa,
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // -------------------------------------------------------------
            // Section 2: Storage & Cache Cleaning
            // -------------------------------------------------------------
            SectionHeader(text = "Storage & Cache")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeDialog = WebViewDialogState.ClearCookies }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Clear Cookies",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Delete all stored website cookies and Cloudflare clearance tokens.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeDialog = WebViewDialogState.ClearData }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Clear WebView Data",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Clear browser cache, DOM storage (localStorage, IndexedDB), and temporary web files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionHeader(text = "Cookie Management")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToManualCookies() }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Manually Add Cookies",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Paste cookies directly from desktop DevTools to bypass Cloudflare.",
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
            is WebViewDialogState.EditUserAgent -> {
                AlertDialog(
                    onDismissRequest = { activeDialog = null },
                    title = { Text("Edit User Agent", color = PrimaryText) },
                    text = {
                        Column {
                            Text(
                                text = "Changing the User Agent affects both WebView and background network requests.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryText
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = editUaText,
                                onValueChange = { editUaText = it },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BrandAccent,
                                    focusedLabelColor = BrandAccent
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    editUaText = NetworkClient.DEFAULT_USER_AGENT
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Reset to Default (Firefox Desktop)", color = BrandAccent)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val trimmed = editUaText.trim()
                                if (trimmed == NetworkClient.DEFAULT_USER_AGENT || trimmed.isEmpty()) {
                                    viewModel.setCustomUserAgent(null)
                                } else {
                                    viewModel.setCustomUserAgent(trimmed)
                                }
                                activeDialog = null
                                Toast.makeText(context, "User Agent updated", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { activeDialog = null }) {
                            Text("Cancel", color = SecondaryText)
                        }
                    },
                    containerColor = DarkSurface
                )
            }

            is WebViewDialogState.ClearCookies -> {
                ConfirmCancelDialog(
                    title = "Clear Cookies?",
                    message = "This will remove all stored website session cookies and Cloudflare clearance tokens. You may need to verify security again.",
                    onConfirm = {
                        CookieManager.getInstance().removeAllCookies {
                            CookieManager.getInstance().flush()
                        }
                        activeDialog = null
                        Toast.makeText(context, "All cookies cleared", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { activeDialog = null }
                )
            }

            is WebViewDialogState.ClearData -> {
                ConfirmCancelDialog(
                    title = "Clear WebView Data?",
                    message = "This will delete all cached web pages, HTML5 localStorage, and Chromium engine data. It will not delete your downloaded novels.",
                    onConfirm = {
                        try {
                            WebView(context).apply {
                                clearCache(true)
                                clearFormData()
                                clearHistory()
                                clearSslPreferences()
                                destroy()
                            }
                            WebStorage.getInstance().deleteAllData()
                            val webviewDir = File(context.applicationInfo.dataDir, "app_webview")
                            if (webviewDir.exists()) {
                                webviewDir.deleteRecursively()
                            }
                            activeDialog = null
                            Toast.makeText(context, "WebView data deleted", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to clear WebView data: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismiss = { activeDialog = null }
                )
            }
            null -> Unit
        }
    }
}
