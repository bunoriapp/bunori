package com.halovoid.bunori.ui.feature.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.api.backup.BackupService
import com.halovoid.bunori.api.backup.RestoreService
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.ui.core.components.AppDialog
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.components.AsymptoticProgressRing
import com.halovoid.bunori.ui.core.platform.rememberFileOpenLauncher
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.settings.components.BackupFrequencyBottomSheet
import com.halovoid.bunori.ui.feature.settings.components.CreateBackupBottomSheet
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class BackupMetadata(
    val lastBackupTime: String,
    val contentsSummary: String
)

fun getLatestBackupMetadata(context: Context): BackupMetadata {
    val metadataFile = File(context.filesDir, "backup_metadata.json")
    if (metadataFile.exists()) {
        try {
            val json = JSONObject(metadataFile.readText())
            return BackupMetadata(
                lastBackupTime = json.optString("lastBackupTime", "No backup created yet"),
                contentsSummary = json.optString("contentsSummary", "")
            )
        } catch (_: Exception) {}
    }

    val backupDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "backup")
    val backupMetaFile = File(backupDir, "backup_metadata.json")
    if (backupMetaFile.exists()) {
        try {
            val json = JSONObject(backupMetaFile.readText())
            return BackupMetadata(
                lastBackupTime = json.optString("lastBackupTime", "No backup created yet"),
                contentsSummary = json.optString("contentsSummary", "")
            )
        } catch (_: Exception) {}
    }

    val backupFile = File(backupDir, "backup.bbak").takeIf { it.exists() }
        ?: File(backupDir, "backup.lnbak").takeIf { it.exists() }
    if (backupFile != null) {
        val timeStr = android.text.format.DateFormat.format("MMM dd, yyyy, h:mm a", backupFile.lastModified()).toString()
        return BackupMetadata(timeStr, "")
    }

    return BackupMetadata("No backup created yet", "")
}

sealed interface BackupDialogState {
    data object CreateBackupSheet : BackupDialogState
    data object FrequencySheet : BackupDialogState
    data object RestartDialog : BackupDialogState
}

@Composable
fun BackupSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportUri by PreferenceRepository.getInstance(context).exportFolderUri.collectAsStateWithLifecycle(initialValue = null)
    val storageLocation = exportUri?.toString() ?: File(context.getExternalFilesDir(null) ?: context.filesDir, "backup").absolutePath

    var metadata by remember { mutableStateOf(getLatestBackupMetadata(context)) }

    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    val isOperating = isBackingUp || isRestoring

    val backupFrequency by viewModel.backupFrequency.collectAsStateWithLifecycle()
    var activeDialog by remember { mutableStateOf<BackupDialogState?>(null) }

    val launchRestorePicker = rememberFileOpenLauncher(mimeTypes = arrayOf("*/*")) { uri ->
        isRestoring = true
        Toast.makeText(context, "Restoring backup...", Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    RestoreService(context).restoreBackup(uri)
                }
                if (success) {
                    metadata = getLatestBackupMetadata(context)
                    val message = "Backup restored successfully! Please restart the app."
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    snackbarHostState.showSnackbar(message)
                    activeDialog = BackupDialogState.RestartDialog
                } else {
                    val message = "Failed to restore backup. Please ensure the file is valid."
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    snackbarHostState.showSnackbar(message)
                }
            } catch (e: Exception) {
                val message = "Error restoring backup: ${e.message}"
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                snackbarHostState.showSnackbar(message)
            } finally {
                isRestoring = false
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Backup & Restore",
                onBack = onBack,
                actions = {
                    if (isOperating) {
                        AsymptoticProgressRing(
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                }
            )
        },
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SectionHeader(text = "Backup Actions")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isOperating) { activeDialog = BackupDialogState.CreateBackupSheet }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Backup,
                    contentDescription = null,
                    tint = if (isOperating) SecondaryText.copy(alpha = 0.5f) else BrandAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Create Backup",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (isOperating) SecondaryText else PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isBackingUp) "Creating backup in progress..." else "Create a backup of your library and downloaded content",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isOperating) { launchRestorePicker() }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Restore,
                    contentDescription = null,
                    tint = if (isOperating) SecondaryText.copy(alpha = 0.5f) else BrandAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Restore Backup",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (isOperating) SecondaryText else PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRestoring) "Restoring backup in progress..." else "Restore your library from an existing backup",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Backup Status")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(text = "Last Backup", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = PrimaryText)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = metadata.lastBackupTime, style = MaterialTheme.typography.bodyMedium, color = SecondaryText)
                    if (metadata.contentsSummary.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = metadata.contentsSummary, style = MaterialTheme.typography.bodySmall, color = BrandAccent)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Automatic Backup")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeDialog = BackupDialogState.FrequencySheet }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Backup Frequency",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Automatic backup schedule",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
                Text(
                    text = backupFrequency,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Note: Automatic backup happens at 5:30 PM based on the schedule you choose.",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryText.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Storage")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = BrandAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Backup Location",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = storageLocation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        when (activeDialog) {
            is BackupDialogState.CreateBackupSheet -> {
                CreateBackupBottomSheet(
                    onDismiss = { activeDialog = null },
                    onCreateBackup = { db, ch, cov ->
                        activeDialog = null
                        isBackingUp = true
                        Toast.makeText(context, "Creating backup...", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    BackupService(context).createBackup(
                                        backupDatabase = db,
                                        backupChapters = ch,
                                        backupCovers = cov
                                    )
                                }
                                metadata = getLatestBackupMetadata(context)
                                Toast.makeText(context, "Backup created successfully!", Toast.LENGTH_SHORT).show()
                                snackbarHostState.showSnackbar("Backup created successfully!")
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to create backup: ${e.message}", Toast.LENGTH_LONG).show()
                                snackbarHostState.showSnackbar("Failed to create backup")
                            } finally {
                                isBackingUp = false
                            }
                        }
                    }
                )
            }
            is BackupDialogState.FrequencySheet -> {
                BackupFrequencyBottomSheet(
                    currentFrequency = backupFrequency,
                    onFrequencySelected = { viewModel.setBackupFrequency(it) },
                    onDismiss = { activeDialog = null }
                )
            }
            is BackupDialogState.RestartDialog -> {
                AppDialog(
                    onDismissRequest = { activeDialog = null },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Restore,
                            contentDescription = null,
                            tint = BrandAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Restore Completed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                    },
                    text = {
                        Text(
                            text = "Backup restored successfully! Please restart the app to ensure all restored database connections, settings, and library items load cleanly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                activeDialog = null
                                val packageManager = context.packageManager
                                val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                                val componentName = intent?.component
                                val mainIntent = Intent.makeRestartActivityTask(componentName)
                                context.startActivity(mainIntent)
                                Runtime.getRuntime().exit(0)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandAccent,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Restart App")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { activeDialog = null }) {
                            Text("Later", color = SecondaryText)
                        }
                    }
                )
            }
            null -> Unit
        }
    }
}
