package com.halovoid.bunori.ui.feature.reader

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halovoid.bunori.domain.models.ReadingMode
import com.halovoid.bunori.ui.core.platform.SystemBarHandler
import com.halovoid.bunori.ui.core.theme.*
import com.halovoid.bunori.ui.feature.crawler.webview.WebViewActivity
import com.halovoid.bunori.ui.feature.reader.components.ReaderSettingsBottomSheet
import com.halovoid.bunori.ui.feature.reader.components.ReaderWebView
import com.halovoid.bunori.ui.feature.reader.components.TableOfContentsDialog
import com.halovoid.bunori.ui.feature.reader.components.TapZoneGuideOverlay
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * WebView Reader screen for Bunori.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var isTocVisible by remember { mutableStateOf(false) }
    var isSettingsVisible by remember { mutableStateOf(false) }
    var isGuideVisible by remember { mutableStateOf(false) }

    // Display visual tap zone helper for 2 seconds on initial open and whenever reading mode switches
    LaunchedEffect(readerSettings.readingMode) {
        isGuideVisible = true
        delay(2000L.milliseconds)
        isGuideVisible = false
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
            visible = isGuideVisible,
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
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(6.dp)
            ) {
                val pct = (readingProgress * 100).toInt().coerceIn(0, 100)
                Text(
                    text = "$currentChapterNumber/$totalChapters  $pct%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }
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
                onBack = onBack,
                onOpenToc = { isTocVisible = true },
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
                onOpenSettings = { isSettingsVisible = true },
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

        // 6. Dynamic Content / Cloudflare Fallback Dialog Card
        if (isBlockedOrEmpty && blockedChapter != null) {
            val chapter = blockedChapter!!
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        tint = BrandAccent,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Dynamic Content / Verification Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"${chapter.title}\" could not be scraped directly. The site may require Cloudflare verification or JavaScript DOM extraction.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryText,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.reloadChapter(chapter.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retry", color = PrimaryText)
                        }
                        Button(
                            onClick = {
                                val targetUrl = chapter.sourceUrl?.takeIf { it.isNotBlank() } ?: chapter.url
                                val intent = Intent(context, WebViewActivity::class.java).apply {
                                    putExtra("url", targetUrl)
                                    putExtra("host", targetUrl.toUri().host ?: "")
                                    putExtra("is_extraction_mode", true)
                                    putExtra("chapter_id", chapter.id)
                                    putExtra("chapter_index", chapter.index)
                                    putExtra("chapter_title", chapter.title)
                                    putExtra("novel_url", novelUrl)
                                    putExtra("chapter_url", chapter.url)
                                    putExtra("scanlation_source", chapter.scanlationSource)
                                }
                                extractorLauncher.launch(intent)
                            },
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                        ) {
                            Text("Open in WebView", color = Color.White)
                        }
                    }
                    TextButton(
                        onClick = { viewModel.dismissBlockedState() },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("Dismiss", color = SecondaryText)
                    }
                }
            }
        }
    }

    // Center Modal Dialog: Table of Contents with Search & Quick Jump
    if (isTocVisible) {
        TableOfContentsDialog(
            chapters = tocChapters,
            currentChapterId = currentChapter?.id,
            onChapterSelected = { chapterId ->
                viewModel.jumpToChapter(chapterId)
                isTocVisible = false
            },
            onDismiss = { isTocVisible = false }
        )
    }

    // Reader Settings Bottom Sheet
    if (isSettingsVisible) {
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
            onUpdateCustomCode = viewModel::updateCustomCode,
            onAddCustomFont = viewModel::addCustomFont,
            onRemoveCustomFont = viewModel::removeCustomFont,
            onDismiss = { isSettingsVisible = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onOpenToc: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    Surface(
        color = DarkBackground.copy(alpha = 0.95f),
        contentColor = PrimaryText,
        modifier = Modifier.fillMaxWidth()
    ) {
        TopAppBar(
            modifier = Modifier.statusBarsPadding(),
            title = {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText,
                            maxLines = 1
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                IconButton(onClick = onOpenToc) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Table of contents"
                    )
                }
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = PrimaryText,
                navigationIconContentColor = PrimaryText,
                actionIconContentColor = PrimaryText
            )
        )
    }
}

@Composable
private fun ReaderBottomBar(
    progress: Float,
    currentChapterNumber: Int,
    totalChapters: Int,
    onOpenSettings: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit
) {
    Surface(
        color = DarkBackground.copy(alpha = 0.95f),
        contentColor = PrimaryText,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Scrubber Progress Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                    color = BrandAccent,
                    trackColor = DarkSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                val pct = (progress * 100).toInt().coerceIn(0, 100)
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryText,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Navigation Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onPreviousChapter,
                    enabled = currentChapterNumber > 1
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Previous Chapter",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Prev")
                }

                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryText)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Reader settings",
                        modifier = Modifier.size(16.dp),
                        tint = PrimaryText
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Settings", style = MaterialTheme.typography.labelMedium)
                }

                TextButton(
                    onClick = onNextChapter,
                    enabled = currentChapterNumber < totalChapters
                ) {
                    Text("Next")
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Next Chapter",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}