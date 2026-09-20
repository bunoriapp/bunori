package com.halovoid.bunori.ui.feature.onboarding

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.ui.core.platform.rememberFolderPickerLauncher
import com.halovoid.bunori.ui.core.theme.*
import kotlinx.coroutines.launch

@Composable
fun FolderScreen(
    viewModel: FolderViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedFolder by viewModel.exportFolderUri.collectAsState(initial = null)
    val friendlyPath by viewModel.friendlyPath.collectAsState(initial = "")

    val launchFolderPicker = rememberFolderPickerLauncher { uri ->
        scope.launch {
            viewModel.setExportFolder(uri)
        }
    }

    val isStepValid = selectedFolder != null

    OnboardingStep(
        title = "Storage Location",
        subtitle = "Choose a directory to store offline novels, chapters, and backups.",
        stepNumber = 1,
        totalSteps = 4,
        onBack = onBack,
        onNext = onNext,
        isNextEnabled = isStepValid,
        nextButtonText = "Next"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Clickable Folder Selection Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        try {
                            launchFolderPicker()
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "No file manager found to select a folder", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = if (selectedFolder != null) PrimaryText else SecondaryText,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedFolder != null && friendlyPath.isNotBlank()) friendlyPath else "Tap to select storage folder",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selectedFolder != null) PrimaryText else SecondaryText,
                            fontWeight = if (selectedFolder != null) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (selectedFolder != null) "Tap to change folder" else "Folder selection is required to proceed",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Text(
                text = "Note: Storage works for Android SAF and has not been implemented for Waydroid yet.",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText,
                lineHeight = 18.sp
            )
        }
    }
}
