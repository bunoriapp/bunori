package com.halovoid.bunori.ui.feature.onboarding

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.settings.SettingsViewModel

@Composable
fun ThemeOnboardingScreen(
    viewModel: SettingsViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val selectedThemeId by viewModel.selectedThemeId.collectAsStateWithLifecycle()
    val isAmoledMode by viewModel.isAmoledMode.collectAsStateWithLifecycle()

    val isSystemDark = isSystemInDarkTheme()
    val isEffectiveDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val currentTheme = remember(selectedThemeId) {
        ThemeRegistry.getThemeById(selectedThemeId)
    }

    val themePairs = remember {
        ThemeRegistry.allThemes.chunked(2)
    }

    OnboardingStep(
        title = "Choose Your Style",
        subtitle = "Customize the interface to suit your personal aesthetic and reading comfort.",
        stepNumber = 2,
        totalSteps = 4,
        onBack = onBack,
        onNext = onNext
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Interactive App Preview Hero Card
            OnboardingLiveThemePreview(
                appTheme = currentTheme,
                isEffectiveDark = isEffectiveDark,
                isAmoled = isAmoledMode && isEffectiveDark,
                modifier = Modifier.fillMaxWidth()
            )

            // Theme Mode Selector
            Text(
                text = "Theme Mode",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )

            OnboardingThemeModeSelector(
                selectedMode = themeMode,
                onModeSelected = { viewModel.setThemeMode(it) },
                modifier = Modifier.fillMaxWidth()
            )

            // Color Palette Header
            Text(
                text = "Color Palette",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )

            // Color Palettes Grid
            themePairs.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OnboardingThemeCard(
                        appTheme = pair[0],
                        isSelected = selectedThemeId.equals(pair[0].id, ignoreCase = true),
                        isEffectiveDark = isEffectiveDark,
                        isAmoled = isAmoledMode && isEffectiveDark,
                        onClick = { viewModel.setSelectedThemeId(pair[0].id) },
                        modifier = Modifier.weight(1f)
                    )

                    if (pair.size > 1) {
                        OnboardingThemeCard(
                            appTheme = pair[1],
                            isSelected = selectedThemeId.equals(pair[1].id, ignoreCase = true),
                            isEffectiveDark = isEffectiveDark,
                            isAmoled = isAmoledMode && isEffectiveDark,
                            onClick = { viewModel.setSelectedThemeId(pair[1].id) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Pure AMOLED Black Option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isEffectiveDark) {
                        viewModel.setAmoledMode(!isAmoledMode)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pure AMOLED Black",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEffectiveDark) PrimaryText else SecondaryText.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isEffectiveDark) "True #000000 background for OLED screens" else "Enabled when dark mode is active",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText.copy(alpha = if (isEffectiveDark) 0.8f else 0.4f),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = isAmoledMode && isEffectiveDark,
                    onCheckedChange = { viewModel.setAmoledMode(it) },
                    enabled = isEffectiveDark,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BrandAccent,
                        uncheckedThumbColor = SecondaryText,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun OnboardingLiveThemePreview(
    appTheme: AppTheme,
    isEffectiveDark: Boolean,
    isAmoled: Boolean,
    modifier: Modifier = Modifier
) {
    val preview = if (isEffectiveDark) appTheme.previewDark else appTheme.previewLight
    val previewBg = if (isEffectiveDark && isAmoled) Color.Black else preview.background
    val previewSurface = if (isEffectiveDark && isAmoled) Color(0xFF0C0C0C) else preview.surface
    val previewAccent = preview.primary
    val previewSecondary = preview.secondary
    val previewText = preview.text

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = previewBg,
        border = BorderStroke(1.5.dp, BorderColor.copy(alpha = 0.4f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(previewAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bunori Preview",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = previewText
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = previewAccent.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = appTheme.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = previewAccent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = previewSurface
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(previewAccent.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(previewAccent)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shadow Slave",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = previewText,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Chapter 1,240 · Webnovel",
                            fontSize = 9.sp,
                            color = previewSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(previewText.copy(alpha = 0.15f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(previewAccent)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = previewAccent,
                        modifier = Modifier.height(24.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Read",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLightColor(previewAccent)) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingThemeModeSelector(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(
        Triple(ThemeMode.SYSTEM, "System", Icons.Outlined.BrightnessAuto),
        Triple(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
        Triple(ThemeMode.DARK, "Dark", Icons.Outlined.DarkMode)
    )

    Surface(
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            modes.forEach { (mode, label, icon) ->
                val isSelected = selectedMode == mode
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) BrandAccent.copy(alpha = 0.2f) else Color.Transparent,
                    animationSpec = tween(150),
                    label = "SegmentBackground"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) BrandAccent else SecondaryText,
                    animationSpec = tween(150),
                    label = "SegmentText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(backgroundColor)
                        .clickable { onModeSelected(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingThemeCard(
    appTheme: AppTheme,
    isSelected: Boolean,
    isEffectiveDark: Boolean,
    isAmoled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preview = if (isEffectiveDark) appTheme.previewDark else appTheme.previewLight
    val cardBg = if (isEffectiveDark && isAmoled && isSelected) Color.Black else preview.background
    val cardSurface = if (isEffectiveDark && isAmoled && isSelected) Color(0xFF0E0E0E) else preview.surface
    val isUnsupportedDynamic = appTheme.isDynamic && Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) preview.primary else BorderColor.copy(alpha = 0.35f)
        ),
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier.height(96.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = appTheme.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = preview.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(preview.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = if (isLightColor(preview.primary)) Color.Black else Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = cardSurface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(preview.text.copy(alpha = 0.7f))
                        )

                        Box(
                            modifier = Modifier
                                .size(width = 18.dp, height = 10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(preview.primary)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(preview.primary))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(preview.secondary))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(preview.surface).border(0.5.dp, Color.Gray.copy(alpha = 0.4f), CircleShape))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(preview.background).border(0.5.dp, Color.Gray.copy(alpha = 0.4f), CircleShape))
                }
            }

            if (isUnsupportedDynamic) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF262626)
                    ) {
                        Text(
                            text = "Android 12+",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun isLightColor(color: Color): Boolean {
    val r = color.red
    val g = color.green
    val b = color.blue
    return (0.299 * r + 0.587 * g + 0.114 * b) > 0.55
}
