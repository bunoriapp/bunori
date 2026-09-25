package com.halovoid.bunori.api.core.network

import android.util.Log
import okhttp3.CookieJar
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.HttpUrl

class WebKitCookieJar: CookieJar {
    private val cookieManager: CookieManager
        get() = CookieManager.getInstance()

    companion object {
        private const val TAG = "WebKitCookieJar"
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val urlString = url.toString()
        val cookieHeader = cookieManager.getCookie(urlString)
        if (cookieHeader.isNullOrBlank()) {
            Log.d(TAG, "loadForRequest($url): CookieManager has NO cookies for this URL")
            return emptyList()
        }

        val cookies = cookieHeader.split(";").mapNotNull { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val parts = trimmed.split("=", limit = 2)
            val name = parts[0].trim()
            val value = parts.getOrNull(1)?.trim() ?: ""
            try {
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(url.host)
                    .path("/")
                    .build()
            } catch (_: Exception) {
                Cookie.parse(url, trimmed)
            }
        }
        Log.d(TAG, "loadForRequest($url): CookieManager raw='$cookieHeader' -> Parsed ${cookies.size} cookies: ${cookies.map { "${it.name}=${it.value}" }}")
        return cookies
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        Log.d(TAG, "saveFromResponse($url): Saving ${cookies.size} cookies: ${cookies.map { "${it.name}=${it.value}" }}")
        for (cookie in cookies) {
            cookieManager.setCookie(urlString, cookie.toString())
        }
        cookieManager.flush()
    }

    fun get(url: HttpUrl): List<Cookie> {
        return loadForRequest(url)
    }

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int {
        val urlString = url.toString()
        val cookies = cookieManager.getCookie(urlString) ?: return 0

        fun List<String>.filterNames(): List<String> {
            return if (cookieNames != null) {
                this.filter { it in cookieNames }
            } else {
                this
            }
        }

        val domain = url.host
        val baseDomain = url.topPrivateDomain()

        val matched = cookies.split(";")
            .map { it.substringBefore("=").trim() }
            .filterNames()

        for (name in matched) {
            val expireValue = "$name=; Max-Age=$maxAge; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
            cookieManager.setCookie(urlString, expireValue)
            cookieManager.setCookie("${url.scheme}://$domain", "$expireValue; Domain=$domain")
            cookieManager.setCookie("${url.scheme}://$domain", "$expireValue; Domain=.$domain")
            if (baseDomain != null && baseDomain != domain) {
                cookieManager.setCookie("${url.scheme}://$baseDomain", "$expireValue; Domain=.$baseDomain")
            }
        }
        cookieManager.flush()
        return matched.size
    }
}