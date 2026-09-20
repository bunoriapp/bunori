package com.halovoid.bunori.ui.feature.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.components.ConfirmCancelDialog
import com.halovoid.bunori.ui.core.platform.rememberFolderPickerLauncher
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.settings.components.CacheClearFrequencyBottomSheet

@Composable
fun DownloadPreferencesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val maxJobs by viewModel.maxConcurrentJobs.collectAsStateWithLifecycle()
    val ignoreImg by viewModel.ignoreImages.collectAsStateWithLifecycle()
    val friendlyPath by viewModel.friendlyPath.collectAsStateWithLifecycle("")
    val cacheClearFreq by viewModel.cacheClearFrequency.collectAsStateWithLifecycle()
    
    var showResetDialog by remember { mutableStateOf(false) }
    var showCacheClearSheet by remember { mutableStateOf(false) }

    val launchFolderPicker = rememberFolderPickerLauncher { uri ->
        viewModel.setExportFolder(uri)
        Toast.makeText(context, "Download location updated", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Download Preferences",
                onBack = onBack
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader(text = "Downloads")
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Max Concurrent Downloads",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Text(
                        text = "$maxJobs jobs",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Slider(
                    value = maxJobs.toFloat(),
                    onValueChange = { viewModel.setMaxConcurrentJobs(it.toInt()) },
                    valueRange = 1f..5f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = BrandAccent,
                        activeTrackColor = BrandAccent,
                        inactiveTrackColor = BorderColor.copy(alpha = 0.2f),
                        activeTickColor = DarkBackground,
                        inactiveTickColor = BrandAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Limit parallel download tasks. A lower count reduces crawler network stress and prevents IP temp-bans.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryText
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Data Saver")
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setIgnoreImages(!ignoreImg) }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ignore Images",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Skip image downloading to save bandwidth and storage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
                Switch(
                    checked = ignoreImg,
                    onCheckedChange = { viewModel.setIgnoreImages(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryText,
                        checkedTrackColor = BrandAccent,
                        uncheckedThumbColor = SecondaryText,
                        uncheckedTrackColor = DarkBackground
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Storage")
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { launchFolderPicker() }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = BrandAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Storage Directory",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = friendlyPath.ifEmpty { "No folder selected" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Maintenance")
            
            // Cache Clear Frequency Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCacheClearSheet = true }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cache Clearing Frequency",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Auto-delete temporary chapter cache from online reading",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
                Text(
                    text = cacheClearFreq,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            // Clear Cache Now Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.clearCacheNow { count ->
                            Toast.makeText(
                                context,
                                if (count > 0) "Cleared $count cached items" else "Reading cache is already clean",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Cached,
                    contentDescription = null,
                    tint = BrandAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Clear Reading Cache Now",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Immediately delete temporary online reading cache",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showResetDialog = true }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = null,
                    tint = ErrorRed.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reset onboarding tour",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = ErrorRed
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Clears configuration tour progress, triggering setup next restart.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showCacheClearSheet) {
            CacheClearFrequencyBottomSheet(
                currentFrequency = cacheClearFreq,
                onFrequencySelected = { viewModel.setCacheClearFrequency(it) },
                onDismiss = { showCacheClearSheet = false }
            )
        }

        if (showResetDialog) {
            ConfirmCancelDialog(
                title = "Reset Onboarding?",
                message = "This will prompt the setup wizard next time the app launches. Your downloaded novels will not be deleted.",
                onConfirm = {
                    viewModel.resetOnboarding()
                    showResetDialog = false
                    Toast.makeText(context, "Onboarding reset completed", Toast.LENGTH_LONG).show()
                },
                onDismiss = { showResetDialog = false }
            )
        }
    }
}
