package com.halovoid.bunori.ui.feature.settings

import android.net.Uri
import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halovoid.bunori.ui.core.components.AppTopBar
import com.halovoid.bunori.ui.core.theme.*

private data class CookieItem(
    val name: String,
    val value: String
)

@Composable
fun ManualCookieScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var siteUrl by remember { mutableStateOf("https://www.novelupdates.com") }
    var rawCookies by remember { mutableStateOf("") }
    var activeCookies by remember { mutableStateOf<List<CookieItem>>(emptyList()) }
    var expandedCookieName by remember { mutableStateOf<String?>(null) }

    fun refreshActiveCookies(url: String) {
        val target = normalizeUrl(url)
        if (target == null) {
            activeCookies = emptyList()
            return
        }
        val cookieManager = CookieManager.getInstance()
        val raw = cookieManager.getCookie(target)
        activeCookies = parseCookieItems(raw)
    }

    LaunchedEffect(siteUrl) {
        refreshActiveCookies(siteUrl)
    }

    val detectedInputCookies = remember(rawCookies) {
        parseCookieItems(rawCookies)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Cookie Manager",
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Target Domain Section
            Text(
                text = "Target Website",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = siteUrl,
                onValueChange = { siteUrl = it },
                placeholder = { Text("https://example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandAccent,
                    focusedLabelColor = BrandAccent
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Inject Cookies Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Inject Cookies",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )

                        TextButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    rawCookies = clip
                                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Paste Clipboard", color = BrandAccent, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = rawCookies,
                        onValueChange = { rawCookies = it },
                        placeholder = { Text("cf_clearance=...; _ga=...; (or one cookie per line)") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandAccent,
                            focusedLabelColor = BrandAccent
                        )
                    )

                    AnimatedVisibility(visible = detectedInputCookies.isNotEmpty()) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BrandAccent.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "${detectedInputCookies.size} cookie(s) detected: ${detectedInputCookies.take(3).joinToString { it.name }}${if (detectedInputCookies.size > 3) "..." else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandAccent,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val target = normalizeUrl(siteUrl)
                            if (target == null) {
                                Toast.makeText(context, "Please enter a valid website URL", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val count = injectCookies(target, rawCookies)
                            if (count > 0) {
                                Toast.makeText(context, "Injected $count cookie(s) successfully!", Toast.LENGTH_SHORT).show()
                                rawCookies = ""
                                refreshActiveCookies(siteUrl)
                            } else {
                                Toast.makeText(context, "No valid cookies found (must be name=value)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandAccent)
                    ) {
                        Text("Apply to Browser", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Active Cookies Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Active Cookies",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )
                    if (activeCookies.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BrandAccent.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = activeCookies.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { refreshActiveCookies(siteUrl) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Refresh", color = SecondaryText, fontSize = 12.sp)
                    }

                    if (activeCookies.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val target = normalizeUrl(siteUrl) ?: return@TextButton
                                clearCookiesForUrl(target)
                                refreshActiveCookies(siteUrl)
                                Toast.makeText(context, "Cleared cookies for this site", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Clear All", color = ErrorRed, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (activeCookies.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active cookies stored for this site",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryText
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeCookies.forEach { cookie ->
                        val isExpanded = expandedCookieName == cookie.name
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cookie.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandAccent
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString("${cookie.name}=${cookie.value}"))
                                                Toast.makeText(context, "Copied ${cookie.name}", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text("Copy", fontSize = 11.sp, color = SecondaryText)
                                        }

                                        TextButton(
                                            onClick = {
                                                val target = normalizeUrl(siteUrl) ?: return@TextButton
                                                deleteSingleCookie(target, cookie.name)
                                                refreshActiveCookies(siteUrl)
                                                Toast.makeText(context, "Removed ${cookie.name}", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text("Delete", fontSize = 11.sp, color = ErrorRed)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = cookie.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = PrimaryText,
                                    fontSize = 11.sp,
                                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedCookieName = if (isExpanded) null else cookie.name
                                        }
                                )

                                if (cookie.value.length > 80) {
                                    Text(
                                        text = if (isExpanded) "Show less" else "Show more",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandAccent.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .clickable {
                                                expandedCookieName = if (isExpanded) null else cookie.name
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun parseCookieItems(raw: String?): List<CookieItem> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(";", "\n")
        .map { it.trim() }
        .filter { it.contains("=") }
        .mapNotNull { item ->
            val split = item.split("=", limit = 2)
            if (split.size == 2 && split[0].trim().isNotBlank()) {
                CookieItem(split[0].trim(), split[1].trim())
            } else null
        }
}

private fun normalizeUrl(raw: String): String? {
    var trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        trimmed = "https://$trimmed"
    }
    val uri = try { Uri.parse(trimmed) } catch (_: Exception) { return null }
    val host = uri.host ?: return null
    return "${uri.scheme}://$host"
}

private fun injectCookies(baseUrl: String, rawCookies: String): Int {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)

    val host = Uri.parse(baseUrl).host ?: return 0
    val rootDomain = host.removePrefix("www.")

    var count = 0
    val items = rawCookies.split(";", "\n").map { it.trim() }.filter { it.contains("=") }

    for (item in items) {
        val split = item.split("=", limit = 2)
        if (split.size == 2) {
            val name = split[0].trim()
            val value = split[1].trim()
            if (name.isNotBlank()) {
                val pair = "$name=$value"
                cookieManager.setCookie(baseUrl, "$pair; Path=/")
                cookieManager.setCookie(baseUrl, "$pair; Domain=$host; Path=/")
                if (rootDomain != host) {
                    cookieManager.setCookie(baseUrl, "$pair; Domain=.$rootDomain; Path=/")
                }
                count++
            }
        }
    }
    cookieManager.flush()
    return count
}

private fun deleteSingleCookie(baseUrl: String, cookieName: String) {
    val cookieManager = CookieManager.getInstance()
    val host = Uri.parse(baseUrl).host ?: ""
    val rootDomain = host.removePrefix("www.")

    cookieManager.setCookie(baseUrl, "$cookieName=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
    if (host.isNotBlank()) {
        cookieManager.setCookie(baseUrl, "$cookieName=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Domain=$host; Path=/")
        if (rootDomain != host) {
            cookieManager.setCookie(baseUrl, "$cookieName=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Domain=.$rootDomain; Path=/")
        }
    }
    cookieManager.flush()
}

private fun clearCookiesForUrl(baseUrl: String) {
    val cookieManager = CookieManager.getInstance()
    val cookies = cookieManager.getCookie(baseUrl) ?: return
    val host = Uri.parse(baseUrl).host ?: ""
    val rootDomain = host.removePrefix("www.")

    val items = cookies.split(";").map { it.trim() }.filter { it.contains("=") }
    for (item in items) {
        val name = item.substringBefore("=").trim()
        if (name.isNotBlank()) {
            cookieManager.setCookie(baseUrl, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
            if (host.isNotBlank()) {
                cookieManager.setCookie(baseUrl, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Domain=$host; Path=/")
                if (rootDomain != host) {
                    cookieManager.setCookie(baseUrl, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Domain=.$rootDomain; Path=/")
                }
            }
        }
    }
    cookieManager.flush()
}
