package com.halovoid.bunori.api.loader

import android.os.Build
import com.halovoid.bunori.api.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
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
                val array = JSONArray(body)
                if (array.length() == 0) throw Exception("No releases found")
                array.getJSONObject(0)
            } else {
                JSONObject(body)
            }
            
            val assets = json.optJSONArray("assets")
            val apkDownloadUrl = selectBestApkUrl(assets)

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

        fun selectBestApkUrl(assets: JSONArray?): String? {
            if (assets == null || assets.length() == 0) return null

            data class ApkAsset(val name: String, val url: String)
            val apkList = mutableListOf<ApkAsset>()

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotEmpty()) {
                    apkList.add(ApkAsset(name, url))
                }
            }

            if (apkList.isEmpty()) return null

            val supportedAbis = try {
                Build.SUPPORTED_ABIS ?: emptyArray()
            } catch (_: Throwable) {
                emptyArray()
            }

            for (abi in supportedAbis) {
                val match = apkList.firstOrNull { it.name.contains(abi, ignoreCase = true) }
                if (match != null) {
                    return match.url
                }
            }

            val universalMatch = apkList.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            if (universalMatch != null) {
                return universalMatch.url
            }

            return apkList.first().url
        }
    }
}

