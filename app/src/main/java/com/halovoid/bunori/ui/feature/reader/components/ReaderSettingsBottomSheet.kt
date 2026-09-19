package com.halovoid.bunori.ui.feature.reader.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onUpdateKeepScreenAwake: (Boolean) -> Unit,
    onUpdateDimImages: (Boolean) -> Unit,
    onUpdateCustomCss: (String) -> Unit,
    onUpdateCustomJs: (String) -> Unit,
    onAddCustomFont: (CustomFont) -> Unit,
    onRemoveCustomFont: (CustomFont) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCustomCodeDialog by remember { mutableStateOf(false) }

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

    AppBottomSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
        title = "Reader Settings",
        showCloseButton = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Reading Mode Selection
            SectionHeader(title = "Reading Mode")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeSelectionCard(
                    title = "Continuous",
                    subtitle = "Vertical scroll",
                    icon = Icons.Filled.SwapVert,
                    isSelected = settings.readingMode == ReadingMode.CONTINUOUS,
                    onClick = { onUpdateReadingMode(ReadingMode.CONTINUOUS) },
                    modifier = Modifier.weight(1f)
                )
                ModeSelectionCard(
                    title = "Paged",
                    subtitle = "Book flip",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    isSelected = settings.readingMode == ReadingMode.PAGED,
                    onClick = { onUpdateReadingMode(ReadingMode.PAGED) },
                    modifier = Modifier.weight(1f)
                )
            }

            // 2. Color Themes
            SectionHeader(title = "Theme")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeChip(
                    name = "OLED",
                    bgColor = Color(0xFF000000),
                    textColor = Color(0xFFE4E4E7),
                    isSelected = settings.theme == ReaderTheme.OLED,
                    onClick = { onUpdateTheme(ReaderTheme.OLED) }
                )
                ThemeChip(
                    name = "Dark",
                    bgColor = Color(0xFF18181B),
                    textColor = Color(0xFFF4F4F5),
                    isSelected = settings.theme == ReaderTheme.DARK,
                    onClick = { onUpdateTheme(ReaderTheme.DARK) }
                )
            }

            // 3. Font Family
            SectionHeader(title = "Font Family")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bundled OFL Fonts
                BUNDLED_FONTS.forEach { fontName ->
                    val isSelected = settings.fontFamily.equals(fontName, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onUpdateFontFamily(fontName) },
                        label = { Text(text = fontName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandAccent,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                // Custom User Imported Fonts
                customFonts.forEach { customFont ->
                    val isSelected = settings.fontFamily.equals(customFont.name, ignoreCase = true)
                    InputChip(
                        selected = isSelected,
                        onClick = { onUpdateFontFamily(customFont.name) },
                        label = { Text(text = customFont.name) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove font",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onRemoveCustomFont(customFont) }
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = BrandAccent,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                // Add Custom Font Button
                OutlinedButton(
                    onClick = {
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp),
                    border = BorderStroke(1.dp, BrandAccent)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = BrandAccent
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Import Font", style = MaterialTheme.typography.labelMedium, color = BrandAccent)
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

            // 9. Custom CSS & Scripts
            OutlinedButton(
                onClick = { showCustomCodeDialog = true },
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
    }

    if (showCustomCodeDialog) {
        CustomCodeDialog(
            initialCss = settings.customCss,
            initialJs = settings.customJs,
            onSave = { css, js ->
                onUpdateCustomCss(css)
                onUpdateCustomJs(js)
                showCustomCodeDialog = false
            },
            onDismiss = { showCustomCodeDialog = false }
        )
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
private fun ModeSelectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) BrandAccent.copy(alpha = 0.15f) else DarkSurfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            1.5.dp,
            if (isSelected) BrandAccent else BorderColor.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) BrandAccent else SecondaryText,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) BrandAccent else PrimaryText
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText
                )
            }
        }
    }
}

@Composable
private fun ThemeChip(
    name: String,
    bgColor: Color,
    textColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(68.dp)
            .height(52.dp),
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) BrandAccent else Color(0xFF4B5563)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name,
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(8.dp)
                        .background(BrandAccent, CircleShape)
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
