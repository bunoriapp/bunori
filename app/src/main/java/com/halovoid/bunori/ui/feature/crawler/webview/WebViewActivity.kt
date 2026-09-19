package com.halovoid.bunori.ui.feature.crawler.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.halovoid.bunori.api.core.network.NetworkClient
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.ReaderRepository
import com.halovoid.bunori.ui.core.theme.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class WebViewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: "https://google.com"
        val host = intent.getStringExtra("host") ?: ""
        val isExtractionMode = intent.getBooleanExtra("is_extraction_mode", false)
        val chapterId = intent.getIntExtra("chapter_id", -1)
        val chapterIndex = intent.getIntExtra("chapter_index", 0)
        val chapterTitle = intent.getStringExtra("chapter_title") ?: "Chapter"
        val novelUrl = intent.getStringExtra("novel_url") ?: ""
        val chapterUrl = intent.getStringExtra("chapter_url") ?: url
        val scanlationSource = intent.getStringExtra("scanlation_source") ?: "Default"

        setContent {
            BunoriTheme {
                WebViewScreen(
                    initialUrl = url,
                    host = host,
                    isExtractionMode = isExtractionMode,
                    chapterId = chapterId,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapterTitle,
                    novelUrl = novelUrl,
                    chapterUrl = chapterUrl,
                    scanlationSource = scanlationSource,
                    onFinished = { success, userAgent ->
                        if (success && userAgent != null && host.isNotEmpty()) {
                            WebViewResolverImpl.getInstance().saveUserAgent(host, userAgent)
                        }
                        WebViewResolverImpl.onResolutionResult(host, success)
                        setResult(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        super.onDestroy()
        CookieManager.getInstance().flush()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    initialUrl: String,
    host: String,
    isExtractionMode: Boolean,
    chapterId: Int = -1,
    chapterIndex: Int = 0,
    chapterTitle: String = "Chapter",
    novelUrl: String = "",
    chapterUrl: String = initialUrl,
    scanlationSource: String = "Default",
    onFinished: (Boolean, String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferenceRepository = remember { PreferenceRepository.getInstance(context) }
    val readerRepository = remember { ReaderRepository.getInstance(context) }

    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Extraction state
    var detectedSelector by remember { mutableStateOf<String?>(null) }
    var detectedWordCount by remember { mutableIntStateOf(0) }
    var detectedPreview by remember { mutableStateOf<String?>(null) }
    var detectedHtml by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var showSelectorDialog by remember { mutableStateOf(false) }
    var savedDomainSelector by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(host) {
        if (host.isNotBlank()) {
            savedDomainSelector = preferenceRepository.getDomainSelector(host).firstOrNull()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        CookieManager.getInstance().flush()
                        onFinished(false, NetworkClient.currentUserAgent)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = if (isExtractionMode) chapterTitle else pageTitle.ifBlank { "WebView" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = host.ifBlank { currentUrl },
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (isExtractionMode) {
                        IconButton(onClick = { showSelectorDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "Selector Settings",
                                tint = BrandAccent
                            )
                        }
                    }
                    Button(
                        onClick = {
                            CookieManager.getInstance().flush()
                            onFinished(true, NetworkClient.currentUserAgent)
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DONE")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = PrimaryText,
                    navigationIconContentColor = PrimaryText
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = NetworkClient.currentUserAgent
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                Log.d("WebViewActivityJS", "${consoleMessage?.message()} (line ${consoleMessage?.lineNumber()})")
                                return true
                            }
                        }

                        if (isExtractionMode) {
                            val bridge = ChapterExtractorBridge { selector, wordCount, preview, html ->
                                detectedSelector = selector
                                detectedWordCount = wordCount
                                detectedPreview = preview
                                detectedHtml = html
                            }
                            addJavascriptInterface(bridge, "BunoriExtractorBridge")
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                url?.let { currentUrl = it }
                                val title = view?.title.orEmpty()
                                pageTitle = title

                                CookieManager.getInstance().flush()

                                if (isExtractionMode && view != null) {
                                    val isChallenge = title.contains("Just a moment", ignoreCase = true) ||
                                            title.contains("Attention Required", ignoreCase = true) ||
                                            title.contains("Security Check", ignoreCase = true) ||
                                            title.contains("Cloudflare", ignoreCase = true)
                                    if (!isChallenge) {
                                        val domainSel = savedDomainSelector ?: ""
                                        injectExtractorScript(view, domainSel)
                                    } else {
                                        Log.i("WebViewActivity", "Challenge page '$title' detected. Extractor dormant until verification completed.")
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                return false
                            }
                        }
                        loadUrl(initialUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = BrandAccent
                )
            }

            // Interactive Extractor Floating Bottom Bar
            if (isExtractionMode) {
                AnimatedVisibility(
                    visible = detectedHtml != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.5.dp, BrandAccent),
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(SuccessGreen.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Detected ~$detectedWordCount words",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryText
                                    )
                                    Text(
                                        text = "Via ${detectedSelector ?: "DOM analysis"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SecondaryText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    val htmlToSave = detectedHtml
                                    if (htmlToSave != null && !isSaving) {
                                        isSaving = true
                                        coroutineScope.launch {
                                            readerRepository.saveExtractedChapter(
                                                novelUrl = novelUrl,
                                                chapterUrl = chapterUrl,
                                                chapterId = chapterId,
                                                chapterIndex = chapterIndex,
                                                chapterTitle = chapterTitle,
                                                scanlationSource = scanlationSource,
                                                html = htmlToSave
                                            )
                                            // Persist selector for this host if it's not a density fallback
                                            val sel = detectedSelector
                                            if (!sel.isNullOrBlank() && !sel.startsWith("heuristic") && host.isNotBlank()) {
                                                preferenceRepository.saveDomainSelector(host, sel)
                                            }

                                            CookieManager.getInstance().flush()
                                            onFinished(true, NetworkClient.currentUserAgent)
                                        }
                                    }
                                },
                                enabled = !isSaving,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                            ) {
                                Text("EXTRACT & READ", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSelectorDialog) {
        SelectorDialog(
            host = host,
            activeSelector = detectedSelector.orEmpty(),
            savedSelector = savedDomainSelector.orEmpty(),
            onTestHighlight = { selector ->
                webViewRef?.evaluateJavascript("window.highlightElement ? window.highlightElement('$selector') : 0;", null)
            },
            onSaveSelector = { selector ->
                coroutineScope.launch {
                    preferenceRepository.saveDomainSelector(host, selector)
                    savedDomainSelector = selector
                    webViewRef?.evaluateJavascript("window.runExtractor ? window.runExtractor('$selector') : 0;", null)
                    showSelectorDialog = false
                }
            },
            onDismiss = { showSelectorDialog = false }
        )
    }
}

@Composable
private fun SelectorDialog(
    host: String,
    activeSelector: String,
    savedSelector: String,
    onTestHighlight: (String) -> Unit,
    onSaveSelector: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectorText by remember { mutableStateOf(savedSelector.ifBlank { activeSelector }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Domain CSS Selector",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Configure a custom CSS selector to extract novel chapter text for $host.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryText
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = selectorText,
                    onValueChange = { selectorText = it },
                    label = { Text("CSS Selector") },
                    placeholder = { Text(".entry-content, #chapter-content") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onTestHighlight(selectorText) }
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Highlight in Page")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveSelector(selectorText) },
                colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
            ) {
                Text("Save for Domain", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SecondaryText)
            }
        }
    )
}

/**
 * Injects the 3-tier chapter extraction engine into the live page.
 * Tier 1: Custom domain selector
 * Tier 2: Universal candidate selectors (21 specified selectors)
 * Tier 3: Paragraph density fallback
 */
private fun injectExtractorScript(webView: WebView, customDomainSelector: String) {
    val escapedDomainSelector = customDomainSelector.replace("'", "\\'")
    val script = """
    (function() {
        if (window._bunoriExtractorInstalled) {
            if (window.runExtractor) window.runExtractor('$escapedDomainSelector');
            return;
        }
        window._bunoriExtractorInstalled = true;

        const candidateSelectors = [
            ".chapter__content",
            ".entry-content",
            ".text_story",
            ".post-content",
            ".contenta",
            ".single_post",
            ".main-content",
            ".reader-content",
            "#chapter-content",
            ".chapter-text",
            ".content-wrapper",
            "#content",
            "#the-content",
            "article.post",
            ".chp_raw",
            ".post-body",
            ".content-post",
            ".halChap--kontenInner",
            "[data-tag='post-card']",
            "[data-reader-article='true']",
            "[data-reader-article]"
        ];

        function cleanHtml(el) {
            const clone = el.cloneNode(true);
            const toRemove = clone.querySelectorAll('script, style, noscript, iframe, button, input, form, nav, header, footer, aside, .ad, .ads, .advertisement, [id*="ad-"], [class*="ad-"]');
            toRemove.forEach(n => n.remove());
            return clone.innerHTML;
        }

        function countWords(str) {
            if (!str) return 0;
            return str.trim().split(/\s+/).filter(Boolean).length;
        }

        function findContentByDensity() {
            const candidates = document.querySelectorAll('article, section, main, div');
            let bestEl = null;
            let maxScore = 0;

            candidates.forEach(el => {
                if (el.closest('header, footer, nav, aside')) return;
                const pCount = el.querySelectorAll('p').length;
                const textLength = (el.innerText || '').trim().length;
                if (pCount >= 2 && textLength > 150) {
                    const score = pCount * 100 + textLength;
                    if (score > maxScore) {
                        maxScore = score;
                        bestEl = el;
                    }
                }
            });
            return bestEl;
        }

        window.highlightElement = function(selector) {
            document.querySelectorAll('.bunori-highlight').forEach(el => {
                el.classList.remove('bunori-highlight');
                el.style.outline = '';
            });
            try {
                const els = document.querySelectorAll(selector);
                els.forEach(el => {
                    el.classList.add('bunori-highlight');
                    el.style.outline = '4px solid #F59E0B';
                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                });
                return els.length;
            } catch(e) {
                return 0;
            }
        };

        function isChallengePage() {
            const title = (document.title || '').toLowerCase();
            if (title.includes('just a moment') || title.includes('attention required') || title.includes('security check') || title.includes('cloudflare')) {
                return true;
            }
            if (document.querySelector('#challenge-running, #challenge-stage, #cf-wrapper, .cf-browser-verification, iframe[src*="cloudflare"], iframe[src*="turnstile"]')) {
                return true;
            }
            return false;
        }

        window.runExtractor = function(customSelector) {
            if (isChallengePage()) {
                console.log('[BunoriExtractor] Verification challenge in progress; skipping extraction to allow clean human verification.');
                return;
            }

            let matchedEl = null;
            let matchedSelector = null;

            // Tier 1: Custom domain selector
            if (customSelector) {
                try {
                    const el = document.querySelector(customSelector);
                    if (el && countWords(el.innerText) > 30) {
                        matchedEl = el;
                        matchedSelector = customSelector;
                    }
                } catch(e) {}
            }

            // Tier 2: 21 Candidate selectors
            if (!matchedEl) {
                for (const sel of candidateSelectors) {
                    try {
                        const el = document.querySelector(sel);
                        if (el && countWords(el.innerText) > 40) {
                            matchedEl = el;
                            matchedSelector = sel;
                            break;
                        }
                    } catch(e) {}
                }
            }

            // Tier 3: Paragraph density fallback
            if (!matchedEl) {
                const densityEl = findContentByDensity();
                if (densityEl) {
                    matchedEl = densityEl;
                    matchedSelector = "heuristic:density";
                }
            }

            if (matchedEl && window.BunoriExtractorBridge) {
                const wordCount = countWords(matchedEl.innerText);
                const preview = (matchedEl.innerText || '').trim().substring(0, 150);
                const html = cleanHtml(matchedEl);
                if (!matchedSelector.startsWith('heuristic')) {
                    window.highlightElement(matchedSelector);
                }
                window.BunoriExtractorBridge.onExtractionDetected(matchedSelector, wordCount, preview, html);
            }
        };

        setTimeout(() => window.runExtractor('$escapedDomainSelector'), 600);

        // Next.js/React hydration observer
        const observer = new MutationObserver(() => {
            window.runExtractor('$escapedDomainSelector');
        });
        if (document.body) {
            observer.observe(document.body, { childList: true, subtree: true });
        }
    })();
    """.trimIndent()

    webView.evaluateJavascript(script, null)
}
