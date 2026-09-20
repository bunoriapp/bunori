package com.halovoid.bunori.ui.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.feature.reader.components.ReaderSettingsContent

@Composable
fun ReaderSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.readerSettings.collectAsStateWithLifecycle()
    val customFonts by viewModel.customFonts.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Reader",
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        ReaderSettingsContent(
            settings = settings,
            customFonts = customFonts,
            onUpdateTheme = viewModel::updateReaderTheme,
            onUpdateReadingMode = viewModel::updateReadingMode,
            onUpdateFontFamily = viewModel::updateFontFamily,
            onUpdateFontSize = viewModel::updateFontSize,
            onUpdateLineHeight = viewModel::updateLineHeight,
            onUpdatePadding = viewModel::updateHorizontalPadding,
            onUpdateTextAlign = viewModel::updateTextAlign,
            onUpdateVolumeKeyTurn = viewModel::updateVolumeKeyPageTurn,
            onUpdateKeepScreenAwake = viewModel::updateKeepScreenAwake,
            onUpdateDimImages = viewModel::updateDimImages,
            onUpdateShowTapZoneOverlay = viewModel::updateShowTapZoneOverlay,
            onUpdateCustomCode = viewModel::updateCustomCode,
            onAddCustomFont = viewModel::addCustomFont,
            onRemoveCustomFont = viewModel::removeCustomFont,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        )
    }
}
