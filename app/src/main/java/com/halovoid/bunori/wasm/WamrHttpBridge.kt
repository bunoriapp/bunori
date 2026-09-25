package com.halovoid.bunori.wasm

import android.util.Log
import com.halovoid.bunori.api.core.network.NetworkClient
import com.halovoid.bunori.api.core.network.interceptor.CloudflareBypassException
import com.halovoid.bunori.extension.api.ExtensionJson
import com.halovoid.bunori.extension.api.wasm.WasmHttpRequest
import com.halovoid.bunori.extension.api.wasm.WasmHttpResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object WamrHttpBridge {
    private const val TAG = "WamrHttpBridge"

    @Volatile
    var lastCloudflareBlockedUrl: String? = null

    fun consumeCloudflareBlocked(): Boolean {
        val blocked = lastCloudflareBlockedUrl != null
        lastCloudflareBlockedUrl = null
        return blocked
    }

    @JvmStatic
    fun execute(requestJson: String): ByteArray {
        var currentUrl: String? = null
        return try {
            val req = ExtensionJson.json.decodeFromString<WasmHttpRequest>(requestJson)
            currentUrl = req.url
            Log.i(TAG, "--> [WASM HTTP REQ] ${req.method} ${req.url} (headers: ${req.headers}, body: ${req.body?.take(300)})")

            val requestBuilder = Request.Builder()
                .url(req.url)
                .cacheControl(okhttp3.CacheControl.FORCE_NETWORK)

            req.headers.forEach { (key, value) ->
                requestBuilder.header(key, value)
            }

            if (req.method.equals("POST", ignoreCase = true)) {
                val contentType = req.headers["Content-Type"]
                    ?: req.headers["content-type"]
                    ?: "application/x-www-form-urlencoded"
                val body = (req.body ?: "").toRequestBody(contentType.toMediaTypeOrNull())
                requestBuilder.post(body)
            } else {
                requestBuilder.get()
            }

            NetworkClient.okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val responseHeaders = mutableMapOf<String, String>()
                for (i in 0 until response.headers.size) {
                    responseHeaders[response.headers.name(i)] = response.headers.value(i)
                }

                val bodyStr = response.body?.string() ?: ""
                Log.i(TAG, "<-- [WASM HTTP RESP] status=${response.code} for ${req.url} (body length: ${bodyStr.length} chars, preview: ${bodyStr.take(300)})")

                if (response.code in listOf(403, 429) && (
                    response.header("cf-mitigated") == "challenge" ||
                    response.header("Server")?.contains("ddos-guard", ignoreCase = true) == true ||
                    bodyStr.contains("<title>Just a moment...</title>") ||
                    bodyStr.contains("<title>DDoS-Guard</title>")
                )) {
                    lastCloudflareBlockedUrl = req.url
                }

                val httpResponse = WasmHttpResponse(
                    statusCode = response.code,
                    headers = responseHeaders,
                    body = bodyStr
                )
                ExtensionJson.json.encodeToString(WasmHttpResponse.serializer(), httpResponse).toByteArray(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing HTTP request in WamrHttpBridge: ${e.message}", e)
            if (e is CloudflareBypassException ||
                e.cause is CloudflareBypassException ||
                e.message?.contains("Cloudflare", ignoreCase = true) == true ||
                e.message?.contains("ddos", ignoreCase = true) == true
            ) {
                lastCloudflareBlockedUrl = currentUrl
            }
            val errorResponse = WasmHttpResponse(
                statusCode = 500,
                headers = emptyMap(),
                body = "Host HTTP bridge error: ${e.message}"
            )
            ExtensionJson.json.encodeToString(WasmHttpResponse.serializer(), errorResponse).toByteArray(Charsets.UTF_8)
        }
    }
}
