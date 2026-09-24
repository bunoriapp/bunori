package com.halovoid.bunori.lnreader

import android.util.Log
import com.halovoid.bunori.api.core.network.NetworkClient
import com.halovoid.bunori.extension.api.ExtensionJson
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.GzipSource
import okio.buffer

@Serializable
data class LnReaderHttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

@Serializable
data class LnReaderHttpResponse(
    val status: Int,
    val statusText: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String = ""
)

object LnReaderHttpBridge {

    private val TAG = "LnReaderHttpBridge"

    @Volatile
    var lastCloudflareBlockedUrl: String? = null

    fun consumeCloudflareBlocked(): Boolean {
        val blocked = lastCloudflareBlockedUrl != null
        lastCloudflareBlockedUrl = null
        return blocked
    }

    fun execute(url: String, initJson: String): String {
        return try {
            val req = if (initJson.isNotBlank() && initJson != "{}") {
                try {
                    ExtensionJson.json.decodeFromString<LnReaderHttpRequest>(initJson)
                } catch (_: Exception) {
                    LnReaderHttpRequest(url = url);
                }
            } else {
                LnReaderHttpRequest(url = url);
            }

            val reqBuilder = Request.Builder().url(req.url.ifBlank { url })
            // Strip manual Accept-Encoding so OkHttp can transparently handle gzip and decompression
            req.headers.forEach { (k, v) ->
                if (!k.equals("Accept-Encoding", ignoreCase = true)) {
                    reqBuilder.header(k, v)
                }
            }

            val method = req.method.uppercase()
            when (method) {
                "POST" -> {
                    val contentType = req.headers["Content-Type"] ?: req.headers["content-type"] ?: "application/x-www-form-urlencoded"
                    reqBuilder.post((req.body ?: "").toRequestBody(contentType.toMediaTypeOrNull()))
                }
                "PUT" -> {
                    val contentType = req.headers["Content-Type"] ?: req.headers["content-type"] ?: "application/json"
                    reqBuilder.put((req.body ?: "").toRequestBody(contentType.toMediaTypeOrNull()))
                }
                "PATCH" -> {
                    val contentType = req.headers["Content-Type"] ?: req.headers["content-type"] ?: "application/json"
                    reqBuilder.patch((req.body ?: "").toRequestBody(contentType.toMediaTypeOrNull()))
                }
                "DELETE" -> {
                    if (req.body != null) {
                        val contentType = req.headers["Content-Type"] ?: req.headers["content-type"] ?: "application/json"
                        reqBuilder.delete(req.body.toRequestBody(contentType.toMediaTypeOrNull()))
                    } else {
                        reqBuilder.delete()
                    }
                }
                "HEAD" -> reqBuilder.head()
                else -> reqBuilder.get()
            }

            NetworkClient.okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val respHeaders = mutableMapOf<String, String>()
                for (i in 0 until resp.headers.size) {
                    respHeaders[resp.headers.name(i)] = resp.headers.value(i)
                }

                val rawBytes = resp.body?.bytes() ?: ByteArray(0)
                val isGzip = resp.header("Content-Encoding")?.equals("gzip", ignoreCase = true) == true
                val bodyStr = if (isGzip) {
                    try {
                        GzipSource(okio.Buffer().write(rawBytes)).buffer().use { it.readUtf8() }
                    } catch (_: Exception) {
                        String(rawBytes, Charsets.UTF_8)
                    }
                } else {
                    String(rawBytes, Charsets.UTF_8)
                }

                if (isGzip) {
                    respHeaders.remove("content-encoding")
                    respHeaders.remove("Content-Encoding")
                    respHeaders.remove("content-length")
                    respHeaders.remove("Content-Length")
                }

                if (resp.code in listOf(403, 429) && (resp.header("cf-mitigated") == "challenge") || bodyStr.contains("<title>Just a moment...</title>")) {
                    lastCloudflareBlockedUrl = url;
                }

                val resObj = LnReaderHttpResponse(
                    status = resp.code,
                    statusText = resp.message,
                    headers = respHeaders,
                    body = bodyStr
                )

                ExtensionJson.json.encodeToString(LnReaderHttpResponse.serializer(), resObj)
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP req failed for $url: ${e.message}", e)
            val err = LnReaderHttpResponse(status = 500, statusText = e.message ?: "Network Error")
            ExtensionJson.json.encodeToString(LnReaderHttpResponse.serializer(), err)
        }
    }
}