package com.halovoid.bunori.api.loader

import com.halovoid.bunori.api.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

class AppUpdateManager {
    private val client = NetworkClient.okHttpClient

    data class AppReleaseInfo(
        val tagName: String,
        val releaseUrl: String,
        val apkDownloadUrl: String?,
        val body: String? = null,
        val publishedAt: String? = null
    )

    suspend fun fetchLatestAppRelease(enableBeta: Boolean = false): AppReleaseInfo = withContext(Dispatchers.IO) {
        val url = if (enableBeta) {
            "https://api.github.com/repos/bunoriapp/bunori/releases"
        } else {
            "https://api.github.com/repos/bunoriapp/bunori/releases/latest"
        }
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to fetch app release info: $response")
            
            val body = response.body?.string() ?: throw Exception("Empty response body")
            val json = if (enableBeta) {
                val array = org.json.JSONArray(body)
                if (array.length() == 0) throw Exception("No releases found")
                array.getJSONObject(0)
            } else {
                JSONObject(body)
            }
            
            val assets = json.optJSONArray("assets")
            var apkDownloadUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkDownloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }
            }

            AppReleaseInfo(
                tagName = json.getString("tag_name"),
                releaseUrl = json.getString("html_url"),
                apkDownloadUrl = apkDownloadUrl,
                body = json.optString("body"),
                publishedAt = json.optString("published_at")
            )
        }
    }

    companion object {
        fun isUpdateAvailable(current: String, latest: String): Boolean {
            return VersionUtils.isUpdateAvailable(current, latest)
        }
    }
}
