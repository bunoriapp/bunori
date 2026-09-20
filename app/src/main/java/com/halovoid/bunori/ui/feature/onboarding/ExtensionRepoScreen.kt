package com.halovoid.bunori.ui.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    OnboardingStep(
        title = "Extension Repository",
        subtitle = "Set up extension sources to browse and install novel crawlers.",
        stepNumber = 4,
        totalSteps = 4,
        onBack = onBack,
        buttonText = "Enter Bunori",
        onNext = {
            if (inputUrl.isNotBlank()) {
                viewModel.setExtensionRepoUrl(inputUrl.trim())
            }
            onComplete()
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "The default repository is currently the only repository supporting the .bext architecture that the app is built upon.",
                style = MaterialTheme.typography.bodyMedium,
                color = PrimaryText,
                lineHeight = 22.sp
            )

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
                    placeholder = { Text("https://...", color = SecondaryText.copy(alpha = 0.5f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandAccent,
                        unfocusedBorderColor = BorderColor.copy(alpha = 0.5f),
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText,
                        cursorColor = BrandAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
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
                            text = "Reset to Default",
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
