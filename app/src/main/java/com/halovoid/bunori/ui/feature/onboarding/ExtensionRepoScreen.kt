package com.halovoid.bunori.ui.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.data.repository.DEFAULT_EXTENSION_REPO_URL
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.settings.SettingsViewModel

@Composable
fun ExtensionRepoScreen(
    viewModel: SettingsViewModel,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val currentRepoUrl by viewModel.extensionRepoUrl.collectAsStateWithLifecycle()
    var inputUrl by remember(currentRepoUrl) { mutableStateOf(currentRepoUrl) }
    val clipboardManager = LocalClipboardManager.current

    OnboardingStep(
        title = "Extension Repository",
        subtitle = "Set up extension sources to browse and install novel crawlers.",
        stepNumber = 4,
        totalSteps = 4,
        onBack = onBack,
        onNext = {
            viewModel.setExtensionRepoUrl(inputUrl.trim())
            onComplete()
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DarkSurfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = BrandAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Bunori supports both native .bext extension catalogs and LNReader plugin repositories.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryText,
                        lineHeight = 20.sp
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Repository URL",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    singleLine = true,
                    placeholder = { Text("https://...", color = SecondaryText.copy(alpha = 0.45f)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            tint = SecondaryText.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (inputUrl.isNotBlank()) {
                            IconButton(onClick = { inputUrl = "" }) {
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
                                        inputUrl = clipData.text.trim()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPaste,
                                    contentDescription = "Paste from clipboard",
                                    tint = BrandAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandAccent,
                        unfocusedBorderColor = BorderColor.copy(alpha = 0.4f),
                        focusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = DarkSurfaceVariant.copy(alpha = 0.2f),
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText,
                        cursorColor = BrandAccent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            inputUrl = DEFAULT_EXTENSION_REPO_URL
                            viewModel.setExtensionRepoUrl(DEFAULT_EXTENSION_REPO_URL)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Reset to Official Default",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
