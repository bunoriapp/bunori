package com.halovoid.bunori.ui.feature.reader.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.halovoid.bunori.domain.models.CustomFont
import com.halovoid.bunori.domain.models.ReaderSettings
import com.halovoid.bunori.domain.models.ReaderTextAlign
import com.halovoid.bunori.domain.models.ReaderTheme
import com.halovoid.bunori.domain.models.ReadingMode
import com.halovoid.bunori.ui.core.components.AppBottomSheet
import com.halovoid.bunori.ui.core.theme.*
import java.io.File

private val BUNDLED_FONTS = listOf(
    "Lora",
    "PT Serif",
    "Domine",
    "Arbutus Slab",
    "Lato",
    "Nunito",
    "OpenDyslexic"
)

/**
 * Bottom sheet containing typography, theme, reading mode, and layout preferences.
 * Updates settings reactively with 120 FPS live feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsBottomSheet(
    settings: ReaderSettings,
    customFonts: List<CustomFont>,
    onUpdateTheme: (ReaderTheme) -> Unit,
    onUpdateReadingMode: (ReadingMode) -> Unit,
    onUpdateFontFamily: (String) -> Unit,
    onUpdateFontSize: (Int) -> Unit,
    onUpdateLineHeight: (Float) -> Unit,
    onUpdatePadding: (Int) -> Unit,
    onUpdateTextAlign: (ReaderTextAlign) -> Unit,
    onUpdateVolumeKeyTurn: (Boolean) -> Unit,
    onUpdateKeepScreenAwake: (Boolean) -> Unit = {},
    onUpdateDimImages: (Boolean) -> Unit = {},
    onUpdateShowTapZoneOverlay: (Boolean) -> Unit = {},
    onUpdateCustomCode: (String, String) -> Unit = { _, _ -> },
    onAddCustomFont: (CustomFont) -> Unit = {},
    onRemoveCustomFont: (CustomFont) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    AppBottomSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
        title = "Reader Settings",
        showCloseButton = true
    ) {
        ReaderSettingsContent(
            settings = settings,
            customFonts = customFonts,
            onUpdateTheme = onUpdateTheme,
            onUpdateReadingMode = onUpdateReadingMode,
            onUpdateFontFamily = onUpdateFontFamily,
            onUpdateFontSize = onUpdateFontSize,
            onUpdateLineHeight = onUpdateLineHeight,
            onUpdatePadding = onUpdatePadding,
            onUpdateTextAlign = onUpdateTextAlign,
            onUpdateVolumeKeyTurn = onUpdateVolumeKeyTurn,
            onUpdateKeepScreenAwake = onUpdateKeepScreenAwake,
            onUpdateDimImages = onUpdateDimImages,
            onUpdateShowTapZoneOverlay = onUpdateShowTapZoneOverlay,
            onUpdateCustomCode = onUpdateCustomCode,
            onAddCustomFont = onAddCustomFont,
            onRemoveCustomFont = onRemoveCustomFont
        )
    }
}

sealed interface ReaderSettingsDialogState {
    data object CustomCode : ReaderSettingsDialogState
    data object FontSelection : ReaderSettingsDialogState
}

@Composable
fun ReaderSettingsContent(
    settings: ReaderSettings,
    customFonts: List<CustomFont>,
    onUpdateTheme: (ReaderTheme) -> Unit = {},
    onUpdateReadingMode: (ReadingMode) -> Unit = {},
    onUpdateFontFamily: (String) -> Unit = {},
    onUpdateFontSize: (Int) -> Unit = {},
    onUpdateLineHeight: (Float) -> Unit = {},
    onUpdatePadding: (Int) -> Unit = {},
    onUpdateTextAlign: (ReaderTextAlign) -> Unit = {},
    onUpdateVolumeKeyTurn: (Boolean) -> Unit = {},
    onUpdateKeepScreenAwake: (Boolean) -> Unit = {},
    onUpdateDimImages: (Boolean) -> Unit = {},
    onUpdateShowTapZoneOverlay: (Boolean) -> Unit = {},
    onUpdateCustomCode: (String, String) -> Unit = { _, _ -> },
    onAddCustomFont: (CustomFont) -> Unit = {},
    onRemoveCustomFont: (CustomFont) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeDialog by remember { mutableStateOf<ReaderSettingsDialogState?>(null) }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val fileName = queryFileName(context, uri) ?: "Font_${System.currentTimeMillis()}.ttf"
                val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
                val cleanName = fileName.substringBeforeLast('.').trim().ifBlank { "CustomFont" }
                val ext = fileName.substringAfterLast('.', "ttf")
                val destFile = File(fontsDir, "$cleanName.$ext")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val customFont = CustomFont(name = cleanName, filePath = destFile.absolutePath)
                onAddCustomFont(customFont)
                onUpdateFontFamily(cleanName)
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
            // 1. Reading Mode Selection (Selectable label options, no palettes)
            SectionHeader(title = "Reading Mode")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionSelectableChip(
                        label = "Continuous",
                        isSelected = settings.readingMode == ReadingMode.CONTINUOUS,
                        onClick = { onUpdateReadingMode(ReadingMode.CONTINUOUS) },
                        modifier = Modifier.weight(1f)
                    )
                    OptionSelectableChip(
                        label = "Paged (left to right)",
                        isSelected = settings.readingMode == ReadingMode.PAGED,
                        onClick = { onUpdateReadingMode(ReadingMode.PAGED) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionSelectableChip(
                        label = "Paged (right to left)",
                        isSelected = settings.readingMode == ReadingMode.PAGED_RTL,
                        onClick = { onUpdateReadingMode(ReadingMode.PAGED_RTL) },
                        modifier = Modifier.weight(1f)
                    )
                    OptionSelectableChip(
                        label = "Paged (vertical)",
                        isSelected = settings.readingMode == ReadingMode.VERTICAL_TAP,
                        onClick = { onUpdateReadingMode(ReadingMode.VERTICAL_TAP) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 2. Color Themes (Selectable label options, no palettes)
            SectionHeader(title = "Theme")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OptionSelectableChip(
                    label = "OLED",
                    isSelected = settings.theme == ReaderTheme.OLED,
                    onClick = { onUpdateTheme(ReaderTheme.OLED) },
                    modifier = Modifier.weight(1f)
                )
                OptionSelectableChip(
                    label = "Dark Slate",
                    isSelected = settings.theme == ReaderTheme.DARK,
                    onClick = { onUpdateTheme(ReaderTheme.DARK) },
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Font Family (Clean card that opens font selection modal)
            SectionHeader(title = "Font Family")
            Surface(
                onClick = { activeDialog = ReaderSettingsDialogState.FontSelection },
                shape = RoundedCornerShape(12.dp),
                color = DarkSurfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = settings.fontFamily,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val isCustom = customFonts.any { it.name.equals(settings.fontFamily, ignoreCase = true) }
                        Text(
                            text = if (isCustom) "Custom font • Tap to change or import" else "Bundled font • Tap to change or import",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Select font",
                        tint = SecondaryText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 4. Font Size Stepper & Slider
            SliderControlRow(
                title = "Font Size",
                valueDisplay = "${settings.fontSizeSp} sp",
                currentValue = settings.fontSizeSp.toFloat(),
                valueRange = 12f..36f,
                steps = 23,
                onValueChange = { onUpdateFontSize(it.toInt()) },
                onStepMinus = { onUpdateFontSize((settings.fontSizeSp - 1).coerceAtLeast(12)) },
                onStepPlus = { onUpdateFontSize((settings.fontSizeSp + 1).coerceAtMost(36)) }
            )

            // 5. Line Height Stepper & Slider
            SliderControlRow(
                title = "Line Spacing",
                valueDisplay = "%.2fx".format(settings.lineHeight),
                currentValue = settings.lineHeight,
                valueRange = 1.2f..2.4f,
                steps = 23,
                onValueChange = { onUpdateLineHeight((it * 20).toInt() / 20f) },
                onStepMinus = { onUpdateLineHeight((settings.lineHeight - 0.05f).coerceAtLeast(1.2f)) },
                onStepPlus = { onUpdateLineHeight((settings.lineHeight + 0.05f).coerceAtMost(2.4f)) }
            )

            // 6. Horizontal Margins
            SliderControlRow(
                title = "Page Margins",
                valueDisplay = "${settings.horizontalPaddingDp} dp",
                currentValue = settings.horizontalPaddingDp.toFloat(),
                valueRange = 8f..36f,
                steps = 13,
                onValueChange = { onUpdatePadding(it.toInt()) },
                onStepMinus = { onUpdatePadding((settings.horizontalPaddingDp - 2).coerceAtLeast(8)) },
                onStepPlus = { onUpdatePadding((settings.horizontalPaddingDp + 2).coerceAtMost(36)) }
            )

            // 7. Text Alignment
            SectionHeader(title = "Alignment")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AlignChip(
                    icon = Icons.AutoMirrored.Filled.FormatAlignLeft,
                    label = "Left",
                    isSelected = settings.textAlign == ReaderTextAlign.LEFT,
                    onClick = { onUpdateTextAlign(ReaderTextAlign.LEFT) },
                    modifier = Modifier.weight(1f)
                )
                AlignChip(
                    icon = Icons.Filled.FormatAlignJustify,
                    label = "Justified",
                    isSelected = settings.textAlign == ReaderTextAlign.JUSTIFY,
                    onClick = { onUpdateTextAlign(ReaderTextAlign.JUSTIFY) },
                    modifier = Modifier.weight(1f)
                )
                AlignChip(
                    icon = Icons.Filled.FormatAlignCenter,
                    label = "Center",
                    isSelected = settings.textAlign == ReaderTextAlign.CENTER,
                    onClick = { onUpdateTextAlign(ReaderTextAlign.CENTER) },
                    modifier = Modifier.weight(1f)
                )
            }

            // 8. Toggles
            SectionHeader(title = "Reading Preferences")
            PreferenceToggle(
                title = "Volume Key Navigation",
                subtitle = "Turn pages or scroll using volume keys",
                checked = settings.volumeKeyPageTurn,
                onCheckedChange = onUpdateVolumeKeyTurn
            )
            PreferenceToggle(
                title = "Keep Screen Awake",
                subtitle = "Prevent screen timeout while reading",
                checked = settings.keepScreenAwake,
                onCheckedChange = onUpdateKeepScreenAwake
            )
            PreferenceToggle(
                title = "Dim Images in Dark Mode",
                subtitle = "Reduce image contrast in OLED and Dark themes",
                checked = settings.dimImagesInDarkMode,
                onCheckedChange = onUpdateDimImages
            )
            PreferenceToggle(
                title = "Show Tap Zone Overlay",
                subtitle = "Display visual tap zones guide when opening reader or changing reading mode",
                checked = settings.showTapZoneOverlay,
                onCheckedChange = onUpdateShowTapZoneOverlay
            )

            // 9. Custom CSS & Scripts
            OutlinedButton(
                onClick = { activeDialog = ReaderSettingsDialogState.CustomCode },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Icon(
                    imageVector = Icons.Filled.Code,
                    contentDescription = null,
                    tint = PrimaryText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Custom CSS & Scripts", color = PrimaryText)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        when (activeDialog) {
            is ReaderSettingsDialogState.CustomCode -> {
                CustomCodeDialog(
                    initialCss = settings.customCss,
                    initialJs = settings.customJs,
                    onSave = { css, js ->
                        onUpdateCustomCode(css, js)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null }
                )
            }
            is ReaderSettingsDialogState.FontSelection -> {
                FontSelectionDialog(
                    currentFont = settings.fontFamily,
                    customFonts = customFonts,
                    onSelectFont = { selectedFont ->
                        onUpdateFontFamily(selectedFont)
                        activeDialog = null
                    },
                    onImportFont = {
                        fontPickerLauncher.launch(
                            arrayOf(
                                "font/ttf",
                                "font/otf",
                                "application/x-font-ttf",
                                "application/x-font-otf",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    onRemoveCustomFont = onRemoveCustomFont,
                    onDismiss = { activeDialog = null }
                )
            }
            null -> Unit
        }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = SecondaryText,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun OptionSelectableChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) BrandAccent.copy(alpha = 0.16f) else DarkSurfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) BrandAccent else BorderColor.copy(alpha = 0.45f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) BrandAccent else PrimaryText,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FontSelectionDialog(
    currentFont: String,
    customFonts: List<CustomFont>,
    onSelectFont: (String) -> Unit,
    onImportFont: () -> Unit,
    onRemoveCustomFont: (CustomFont) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredBundled = remember(searchQuery) {
        if (searchQuery.isBlank()) BUNDLED_FONTS
        else BUNDLED_FONTS.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    val filteredCustom = remember(searchQuery, customFonts) {
        if (searchQuery.isBlank()) customFonts
        else customFonts.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f),
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Font Family",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SecondaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search fonts...", color = SecondaryText) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = SecondaryText)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SecondaryText)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Import Font Button
                OutlinedButton(
                    onClick = onImportFont,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BrandAccent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandAccent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = BrandAccent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import New Font (.ttf / .otf)", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Font List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredBundled.isNotEmpty()) {
                        Text(
                            text = "BUNDLED FONTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                        filteredBundled.forEach { fontName ->
                            val isSelected = fontName.equals(currentFont, ignoreCase = true)
                            FontRowItem(
                                name = fontName,
                                subtitle = "Bundled OFL Font",
                                isSelected = isSelected,
                                onSelect = { onSelectFont(fontName) },
                                onDelete = null
                            )
                        }
                    }

                    if (filteredCustom.isNotEmpty()) {
                        Text(
                            text = "CUSTOM FONTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                        filteredCustom.forEach { customFont ->
                            val isSelected = customFont.name.equals(currentFont, ignoreCase = true)
                            FontRowItem(
                                name = customFont.name,
                                subtitle = "User Imported Font",
                                isSelected = isSelected,
                                onSelect = { onSelectFont(customFont.name) },
                                onDelete = { onRemoveCustomFont(customFont) }
                            )
                        }
                    }

                    if (filteredBundled.isEmpty() && filteredCustom.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No fonts found matching \"$searchQuery\"", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FontRowItem(
    name: String,
    subtitle: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) BrandAccent.copy(alpha = 0.12f) else DarkSurfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 0.5.dp,
            if (isSelected) BrandAccent else BorderColor.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) BrandAccent else PrimaryText
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove font",
                            tint = SecondaryText.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = BrandAccent,
                        unselectedColor = SecondaryText.copy(alpha = 0.4f)
                    )
                )
            }
        }
    }
}

@Composable
private fun SliderControlRow(
    title: String,
    valueDisplay: String,
    currentValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onStepMinus: () -> Unit,
    onStepPlus: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = SecondaryText)
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onStepMinus, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = SecondaryText)
            }
            Slider(
                value = currentValue,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = BrandAccent,
                    activeTrackColor = BrandAccent,
                    inactiveTrackColor = DarkSurfaceVariant
                )
            )
            IconButton(onClick = onStepPlus, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Increase", tint = SecondaryText)
            }
        }
    }
}

@Composable
private fun AlignChip(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) BrandAccent.copy(alpha = 0.15f) else DarkSurfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, if (isSelected) BrandAccent else BorderColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) BrandAccent else SecondaryText,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) BrandAccent else PrimaryText
            )
        }
    }
}

@Composable
private fun PreferenceToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = PrimaryText)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = SecondaryText)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandAccent,
                uncheckedTrackColor = DarkSurfaceVariant
            )
        )
    }
}

@Composable
private fun CustomCodeDialog(
    initialCss: String,
    initialJs: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var css by remember { mutableStateOf(initialCss) }
    var js by remember { mutableStateOf(initialJs) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Custom CSS & JavaScript",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text(text = "Custom CSS (injected into <style>):", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = css,
                    onValueChange = { css = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    placeholder = { Text(".chapter-body p { text-indent: 1.5em; }", color = SecondaryText.copy(alpha = 0.5f)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "Custom JavaScript (runs on page load):", style = MaterialTheme.typography.labelSmall, color = SecondaryText)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = js,
                    onValueChange = { js = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    placeholder = { Text("// custom code", color = SecondaryText.copy(alpha = 0.5f)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "Cancel", color = SecondaryText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(css, js) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                    ) {
                        Text(text = "Save & Apply", color = Color.White)
                    }
                }
            }
        }
    }
}

private fun queryFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path?.substringAfterLast('/')
    }
    return result
}
