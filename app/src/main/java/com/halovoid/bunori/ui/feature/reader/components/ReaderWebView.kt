package com.halovoid.bunori.ui.feature.reader.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewAssetLoader
import com.halovoid.bunori.domain.models.ChapterPayload
import com.halovoid.bunori.domain.models.CustomFont
import com.halovoid.bunori.domain.models.ReaderSettings
import com.halovoid.bunori.ui.feature.reader.ReaderCommand
import com.halovoid.bunori.ui.feature.reader.ReaderViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Modern high-performance WebView reader component for Bunori.
 * Supports continuous bidirectional infinite scroll, multi-column paged mode,
 * 120 FPS dynamic theming, custom fonts, and rich HTML fidelity.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderWebView(
    viewModel: ReaderViewModel,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenExternalUrl: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val readerSettings by viewModel.readerSettings.collectAsStateWithLifecycle()
    val customFonts by viewModel.customFonts.collectAsStateWithLifecycle()

    var isPageReady by remember { mutableStateOf(false) }
    var pendingCommand by remember { mutableStateOf<ReaderCommand?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val currentOnToggleControls by rememberUpdatedState(onToggleControls)
    val currentOnOpenExternalUrl by rememberUpdatedState(onOpenExternalUrl)

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryHex = remember(primaryColor) {
        String.format("#%06X", 0xFFFFFF and primaryColor.toArgb())
    }

    // Keep screen awake setting
    DisposableEffect(readerSettings.keepScreenAwake) {
        val window = (context as? Activity)?.window
        if (readerSettings.keepScreenAwake) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Asset loader for secure offline rendering of assets & internal custom fonts
    val fontsDir = remember(context) { File(context.filesDir, "fonts").apply { mkdirs() } }
    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/fonts/", WebViewAssetLoader.InternalStoragePathHandler(context, fontsDir))
            .build()
    }

    // Listen for incoming commands from ReaderViewModel
    LaunchedEffect(isPageReady) {
        viewModel.commands.collect { command ->
            val webView = webViewInstance
            if (isPageReady && webView != null) {
                executeReaderCommand(webView, command)
            } else {
                pendingCommand = command
            }
        }
    }

    // Live reader settings updates (CSS variables, themes, reading mode)
    LaunchedEffect(readerSettings, primaryHex, isPageReady) {
        val webView = webViewInstance
        if (isPageReady && webView != null) {
            val json = readerSettings.toJsSettingsJson(primaryHex)
            webView.evaluateJavascript("window.applyReaderSettings($json);", null)
        }
    }

    // Dynamic @font-face injection for imported custom fonts
    LaunchedEffect(customFonts, isPageReady) {
        val webView = webViewInstance
        if (isPageReady && webView != null && customFonts.isNotEmpty()) {
            val fontCss = buildCustomFontsCss(customFonts)
            val escapedCss = fontCss.replace("\n", " ").replace("'", "\\'")
            webView.evaluateJavascript(
                """
                (function() {
                    let el = document.getElementById('custom-fonts-style');
                    if (!el) {
                        el = document.createElement('style');
                        el.id = 'custom-fonts-style';
                        document.head.appendChild(el);
                    }
                    el.textContent = '$escapedCss';
                })();
                """.trimIndent(),
                null
            )
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Prevent white flash by keeping canvas transparent until themed
                setBackgroundColor(0x00000000)
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    textZoom = 100 // Prevent accessibility scaling from breaking layout
                    cacheMode = WebSettings.LOAD_DEFAULT
                    useWideViewPort = false
                    loadWithOverviewMode = false
                }

                val bridge = BunoriReaderBridge(
                    onCenterTapCallback = {
                        Log.d("BunoriReader", "ReaderWebView -> onCenterTapCallback: invoking currentOnToggleControls()")
                        currentOnToggleControls()
                    },
                    onPageTurnCallback = { dir ->
                        Log.d("BunoriReader", "ReaderWebView -> onPageTurnCallback: dir=$dir")
                    },
                    onProgressUpdateCallback = { id, pct -> viewModel.onProgressUpdate(id, pct) },
                    onChapterCompletedCallback = { id -> viewModel.onChapterCompleted(id) },
                    onActiveChapterChangedCallback = { id, title, idx -> viewModel.onActiveChapterChanged(id, title, idx) },
                    onRequestNextChapterCallback = { id -> viewModel.loadNextChapter(id) },
                    onRequestPreviousChapterCallback = { id -> viewModel.loadPreviousChapter(id) },
                    onLinkClickCallback = { url ->
                        currentOnOpenExternalUrl(url)
                        openExternalLink(ctx, url)
                    }
                )

                addJavascriptInterface(bridge, "BunoriBridge")

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d("BunoriReaderJS", "${consoleMessage?.message()} -- line ${consoleMessage?.lineNumber()}")
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(request.url)
                            ?: super.shouldInterceptRequest(view, request)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val url = request.url.toString()
                        if (url.startsWith("https://appassets.androidplatform.net")) {
                            return false
                        }
                        if (url.startsWith("#")) {
                            return false
                        }
                        openExternalLink(ctx, url)
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        isPageReady = true

                        // Apply current settings and font faces
                        val json = readerSettings.toJsSettingsJson(primaryHex)
                        view.evaluateJavascript("window.applyReaderSettings($json);", null)

                        if (customFonts.isNotEmpty()) {
                            val fontCss = buildCustomFontsCss(customFonts)
                            val escapedCss = fontCss.replace("\n", " ").replace("'", "\\'")
                            view.evaluateJavascript(
                                """
                                (function() {
                                    let el = document.getElementById('custom-fonts-style');
                                    if (!el) {
                                        el = document.createElement('style');
                                        el.id = 'custom-fonts-style';
                                        document.head.appendChild(el);
                                    }
                                    el.textContent = '$escapedCss';
                                })();
                                """.trimIndent(),
                                null
                            )
                        }

                        // Flush pending command if one arrived before page loaded
                        pendingCommand?.let { cmd ->
                            executeReaderCommand(view, cmd)
                            pendingCommand = null
                        }
                    }
                }

                // Volume key page turning if enabled
                setOnKeyListener { _, keyCode, event ->
                    if (readerSettings.volumeKeyPageTurn && event.action == KeyEvent.ACTION_DOWN) {
                        when (keyCode) {
                            KeyEvent.KEYCODE_VOLUME_UP -> {
                                evaluateJavascript(
                                    "window.pageTurn ? window.pageTurn(-1) : window.scrollBy({ top: -window.innerHeight * 0.8, behavior: 'smooth' });",
                                    null
                                )
                                true
                            }
                            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                                evaluateJavascript(
                                    "window.pageTurn ? window.pageTurn(1) : window.scrollBy({ top: window.innerHeight * 0.8, behavior: 'smooth' });",
                                    null
                                )
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }

                webViewInstance = this
                loadUrl("https://appassets.androidplatform.net/assets/reader/reader.html")
            }
        },
        update = { webView ->
            webViewInstance = webView
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.apply {
                removeJavascriptInterface("BunoriBridge")
                stopLoading()
                destroy()
            }
            webViewInstance = null
        }
    }
}

private fun executeReaderCommand(webView: WebView, command: ReaderCommand) {
    when (command) {
        is ReaderCommand.SetInitialChapter -> {
            val b64Html = encodeB64(command.payload.htmlContent)
            val b64Title = encodeB64(command.payload.title)
            val b64Scanlator = encodeB64(command.payload.scanlator)
            val script = """
                window.setInitialChapterB64(${command.payload.id}, "$b64Title", ${command.payload.index}, "$b64Html", ${command.startAtEnd}, "$b64Scanlator");
                window.setHasMorePrevious(${command.hasPrevious});
                window.setHasMoreNext(${command.hasNext});
            """.trimIndent()
            webView.evaluateJavascript(script) {
                webView.post {
                    webView.requestLayout()
                    webView.invalidate()
                }
            }
        }
        is ReaderCommand.AppendChapter -> {
            val b64Html = encodeB64(command.payload.htmlContent)
            val b64Title = encodeB64(command.payload.title)
            val b64Scanlator = encodeB64(command.payload.scanlator)
            val script = """
                window.appendChapterB64(${command.payload.id}, "$b64Title", ${command.payload.index}, "$b64Html", "$b64Scanlator");
                window.setHasMoreNext(${command.hasNext});
            """.trimIndent()
            webView.evaluateJavascript(script) {
                webView.post {
                    webView.requestLayout()
                    webView.invalidate()
                }
            }
        }
        is ReaderCommand.PrependChapter -> {
            val b64Html = encodeB64(command.payload.htmlContent)
            val b64Title = encodeB64(command.payload.title)
            val b64Scanlator = encodeB64(command.payload.scanlator)
            val script = """
                window.prependChapterB64(${command.payload.id}, "$b64Title", ${command.payload.index}, "$b64Html", "$b64Scanlator");
                window.setHasMorePrevious(${command.hasPrevious});
            """.trimIndent()
            webView.evaluateJavascript(script) {
                webView.post {
                    webView.requestLayout()
                    webView.invalidate()
                }
            }
        }
        is ReaderCommand.SetHasMore -> {
            val script = """
                window.setHasMorePrevious(${command.hasPrevious});
                window.setHasMoreNext(${command.hasNext});
            """.trimIndent()
            webView.evaluateJavascript(script, null)
        }
        is ReaderCommand.ScrollToChapter -> {
            webView.evaluateJavascript(
                """
                const el = document.getElementById('chapter-${command.chapterId}');
                if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
                """.trimIndent(),
                null
            )
        }
        is ReaderCommand.PageTurn -> {
            webView.evaluateJavascript("window.pageTurn && window.pageTurn(${command.direction});", null)
        }
    }
}

private fun encodeB64(text: String): String {
    return Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}

private fun ReaderSettings.toJsSettingsJson(accentHex: String = "#7C3AED"): String {
    val jsonObject = buildJsonObject {
        put("fontSize", fontSizeSp)
        put("lineHeight", lineHeight)
        put("fontFamily", fontFamily)
        put("paddingH", horizontalPaddingDp)
        put("textAlign", textAlign.cssValue)
        put("theme", theme.id)
        put("readingMode", readingMode.id)
        put("dimImages", dimImagesInDarkMode)
        put("accentColor", accentHex)
        put("customCss", customCss)
        put("customJs", customJs)
    }
    return jsonObject.toString()
}

private fun buildCustomFontsCss(customFonts: List<CustomFont>): String {
    val sb = StringBuilder()
    for (font in customFonts) {
        val fileName = File(font.filePath).name
        sb.append(
            """
            @font-face {
              font-family: '${font.name}';
              src: url('https://appassets.androidplatform.net/fonts/$fileName');
              font-display: swap;
            }
            """.trimIndent()
        ).append("\n")
    }
    return sb.toString()
}

private fun openExternalLink(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}
