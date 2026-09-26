package com.halovoid.bunori.ui.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.settings.SettingsViewModel

@Composable
fun PermissionScreen(
    viewModel: SettingsViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(isIgnoringBatteryOptimizations(context))
    }

    var canInstallPackages by remember {
        mutableStateOf(context.packageManager.canRequestPackageInstalls())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    OnboardingStep(
        title = "App Permissions",
        subtitle = "Enable system permissions for seamless background chapter downloads, updates, and maintenance.",
        stepNumber = 3,
        totalSteps = 3,
        onBack = onBack,
        onNext = onNext
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PermissionToggleRow(
                title = "Notifications",
                description = "Show download progress notifications, batch crawler status, and background maintenance alerts.",
                isChecked = hasNotificationPermission,
                onToggle = { enabled ->
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        }
                        context.startActivity(intent)
                    }
                }
            )

            HorizontalDivider(color = BorderColor.copy(alpha = 0.25f), thickness = 0.5.dp)

            PermissionToggleRow(
                title = "Background Activity",
                description = "Disable battery optimization so long chapter downloads and background crawlers are not interrupted.",
                isChecked = isIgnoringBatteryOptimizations,
                onToggle = { enabled ->
                    if (enabled) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } else {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        }
                        context.startActivity(intent)
                    }
                }
            )

            HorizontalDivider(color = BorderColor.copy(alpha = 0.25f), thickness = 0.5.dp)

            PermissionToggleRow(
                title = "In-App Updates",
                description = "Allow Bunori to install direct app updates without leaving the application.",
                isChecked = canInstallPackages,
                onToggle = {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )
        }
        
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
                canInstallPackages = context.packageManager.canRequestPackageInstalls()
            }
        }
    }
}

@Composable
private fun PermissionToggleRow(
    title: String,
    description: String,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isChecked) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = PrimaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText,
                lineHeight = 18.sp,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = isChecked,
            onCheckedChange = { onToggle(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandAccent,
                uncheckedThumbColor = SecondaryText,
                uncheckedTrackColor = DarkSurfaceVariant
            )
        )
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}
