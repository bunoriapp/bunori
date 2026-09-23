package com.halovoid.bunori.ui.feature.reader

import android.app.Activity
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.domain.models.Chapter
import com.halovoid.bunori.domain.models.ReadingMode
import com.halovoid.bunori.ui.core.platform.SystemBarHandler
import com.halovoid.bunori.ui.core.platform.VolumeKeyEventManager
import com.halovoid.bunori.ui.feature.reader.components.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

sealed interface ReaderDialogState {
    data object TableOfContents : ReaderDialogState
    data object SettingsSheet : ReaderDialogState
    data class BlockedChapter(val chapter: Chapter) : ReaderDialogState
}

/**
 * WebView Reader screen.
 */
@Composable
fun ReaderScreen(
    novelUrl: String,
    initialChapterId: Int,
    onBack: () -> Unit,
    viewModel: ReaderViewModel
) {
    val context = LocalContext.current

    val currentChapter by viewModel.currentChapter.collectAsStateWithLifecycle()
    val currentChapterNumber by viewModel.currentChapterNumber.collectAsStateWithLifecycle()
    val totalChapters by viewModel.totalChapters.collectAsStateWithLifecycle()
    val tocChapters by viewModel.tocChapters.collectAsStateWithLifecycle()
    val readingProgress by viewModel.readingProgress.collectAsStateWithLifecycle()
    val isBlockedOrEmpty by viewModel.isBlockedOrEmpty.collectAsStateWithLifecycle()
    val blockedChapter by viewModel.blockedChapter.collectAsStateWithLifecycle()
    val readerSettings by viewModel.readerSettings.collectAsStateWithLifecycle()
    val customFonts by viewModel.customFonts.collectAsStateWithLifecycle()

    var isControlsVisible by remember { mutableStateOf(true) }
    var activeDialog by remember { mutableStateOf<ReaderDialogState?>(null) }
    var isGuideVisible by remember { mutableStateOf(false) }

    // Display visual tap zone helper for 2 seconds on initial open and whenever reading mode switches (if enabled)
    LaunchedEffect(readerSettings.readingMode, readerSettings.showTapZoneOverlay) {
        if (readerSettings.showTapZoneOverlay) {
            isGuideVisible = true
            delay(2000L.milliseconds)
            isGuideVisible = false
        } else {
            isGuideVisible = false
        }
    }

    val extractorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val chId = blockedChapter?.id
            if (chId != null) {
                viewModel.reloadChapter(chId)
            }
        }
    }

    // Controls system status & navigation bar visibility (Mihon pattern)
    SystemBarHandler(isSystemBarsVisible = isControlsVisible)

    // Volume key page turning / navigation listener
    DisposableEffect(readerSettings.volumeKeyPageTurn) {
        if (readerSettings.volumeKeyPageTurn) {
            VolumeKeyEventManager.setListener { keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_VOLUME_UP -> {
                            viewModel.turnPage(-1)
                            true
                        }
                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            viewModel.turnPage(1)
                            true
                        }
                        else -> false
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                } else {
                    false
                }
            }
        } else {
            VolumeKeyEventManager.setListener(null)
        }
        onDispose {
            VolumeKeyEventManager.setListener(null)
        }
    }

    LaunchedEffect(novelUrl, initialChapterId) {
        viewModel.start(novelUrl, initialChapterId)
    }

    val containerBgColor = when (readerSettings.theme.id) {
        "oled" -> Color.Black
        else -> Color(0xFF18181B)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(containerBgColor)
    ) {
        ReaderWebView(
            viewModel = viewModel,
            onToggleControls = {
                Log.d("BunoriReader", "ReaderScreen -> onToggleControls: toggling isControlsVisible from $isControlsVisible to ${!isControlsVisible}")
                isControlsVisible = !isControlsVisible
            },
            modifier = Modifier.fillMaxSize()
        )

        TapZoneGuideOverlay(
            visible = isGuideVisible && readerSettings.showTapZoneOverlay,
            readingMode = readerSettings.readingMode,
            onDismiss = { isGuideVisible = false },
            onLeftTap = {
                if (readerSettings.readingMode == ReadingMode.PAGED) {
                    viewModel.turnPage(-1)
                }
            },
            onCenterTap = {
                isControlsVisible = !isControlsVisible
            },
            onRightTap = {
                if (readerSettings.readingMode == ReadingMode.PAGED) {
                    viewModel.turnPage(1)
                }
            }
        )

        // 3. Floating Minimal Progress Pill (visible when controls are hidden)
        AnimatedVisibility(
            visible = !isControlsVisible && totalChapters > 0 && currentChapterNumber > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            ReaderProgressPill(
                currentChapterNumber = currentChapterNumber,
                totalChapters = totalChapters,
                progress = readingProgress
            )
        }

        // 4. Floating Top Bar Overlay
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                title = currentChapter?.title ?: "Reader",
                subtitle = if (totalChapters > 0) "Chapter $currentChapterNumber of $totalChapters" else null,
                isBlockedOrEmpty = isBlockedOrEmpty && blockedChapter != null,
                onBack = onBack,
                onOpenToc = { activeDialog = ReaderDialogState.TableOfContents },
                onOpenBlockedDialog = {
                    val ch = blockedChapter
                    if (ch != null) {
                        activeDialog = ReaderDialogState.BlockedChapter(ch)
                    }
                },
                onToggleFullscreen = {
                    Log.d("BunoriReader", "ReaderScreen -> TopBar fullscreen button clicked, hiding controls")
                    isControlsVisible = false
                }
            )
        }

        // 5. Floating Bottom Bar Overlay
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                progress = readingProgress,
                currentChapterNumber = currentChapterNumber,
                totalChapters = totalChapters,
                onOpenSettings = { activeDialog = ReaderDialogState.SettingsSheet },
                onPreviousChapter = {
                    if (currentChapterNumber > 1 && tocChapters.isNotEmpty()) {
                        val prevChapter = tocChapters.getOrNull(currentChapterNumber - 2)
                        if (prevChapter != null) {
                            viewModel.jumpToChapter(prevChapter.id, startAtEnd = true)
                        }
                    }
                },
                onNextChapter = {
                    if (currentChapterNumber < totalChapters && tocChapters.isNotEmpty()) {
                        val nextChapter = tocChapters.getOrNull(currentChapterNumber)
                        if (nextChapter != null) viewModel.jumpToChapter(nextChapter.id)
                    }
                }
            )
        }
    }

    // Modal Dialogs and Bottom Sheets
    when (val dialog = activeDialog) {
        is ReaderDialogState.BlockedChapter -> {
            BlockedChapterDialog(
                chapter = dialog.chapter,
                novelUrl = novelUrl,
                onRetry = {
                    activeDialog = null
                    viewModel.reloadChapter(dialog.chapter.id)
                },
                onDismiss = {
                    activeDialog = null
                },
                onOpenWebView = { intent ->
                    activeDialog = null
                    extractorLauncher.launch(intent)
                },
                context = context
            )
        }
        is ReaderDialogState.TableOfContents -> {
            TableOfContentsDialog(
                chapters = tocChapters,
                currentChapterId = currentChapter?.id,
                onChapterSelected = { chapterId ->
                    viewModel.jumpToChapter(chapterId)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null }
            )
        }
        is ReaderDialogState.SettingsSheet -> {
            ReaderSettingsBottomSheet(
                settings = readerSettings,
                customFonts = customFonts,
                onUpdateTheme = viewModel::updateTheme,
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
                onDismiss = { activeDialog = null }
            )
        }
        null -> Unit
    }
}
