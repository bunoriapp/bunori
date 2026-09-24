package com.halovoid.bunori.lnreader

import android.util.Log
import com.halovoid.bunori.api.core.network.NetworkClient
import com.halovoid.bunori.extension.api.ExtensionJson
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
    var lastCloudflareBlockedUrl: String? = null;

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
            req.headers.forEach { (k, v) -> reqBuilder.header(k, v) }

            if (req.method.equals("POST", ignoreCase = true)) {
                val contentType = req.headers["Content-Type"] ?: req.headers["content-type"] ?: "application/x-www-form-urlencoded"
                reqBuilder.post((req.body ?: "").toRequestBody(contentType.toMediaTypeOrNull()))
            } else {
                reqBuilder.get()
            }

            NetworkClient.okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val respHeaders = mutableMapOf<String, String>()
                for (i in 0 until resp.headers.size) {
                    respHeaders[resp.headers.name(i)] = resp.headers.value(i)
                }

                val bodyStr = resp.body?.string().orEmpty();

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
            Log.e(TAG, "HTTP req failed for $url")
            val err = LnReaderHttpResponse(status = 500, statusText =e.message ?: "Network Error")
            ExtensionJson.json.encodeToString(LnReaderHttpResponse.serializer(), err)
        }
    }
}