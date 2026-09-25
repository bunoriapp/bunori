package com.halovoid.bunori.api.core.network

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.halovoid.bunori.data.repository.PreferenceRepository
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.Buffer
import java.io.File
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

object NetworkClient {
    private const val TAG = "NetworkClient"

    private var cache: Cache? = null

    val fastDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            return try {
                Dns.SYSTEM.lookup(hostname).sortedBy { if (it is Inet4Address) 0 else 1 }
            } catch (_: Exception) {
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    private var appContext: Context? = null
    val cookieJar = WebKitCookieJar()

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        val cacheSize = 5 * 1024 * 1024L // 5 MiB
        val cacheDirectory = File(appCtx.cacheDir, "http_cache")
        cache = Cache(cacheDirectory, cacheSize)
    }

    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:155.0) Gecko/20100101 Firefox/155.0"

    @Volatile
    var currentUserAgent: String = DEFAULT_USER_AGENT

    val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .cache(cache)
            .dns(fastDns)
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val request = originalRequest.newBuilder()
                    .header("User-Agent", currentUserAgent)
                    .build()
                chain.proceed(request)
            }

        appContext?.let { ctx ->
            builder.addInterceptor(com.halovoid.bunori.api.core.network.interceptor.CloudflareInterceptor(ctx, cookieJar) { currentUserAgent })
        }

        builder
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                val method = request.method
                val startNs = System.nanoTime()

                val cookieManagerCookies = try {
                    CookieManager.getInstance().getCookie(url)
                } catch (e: Exception) {
                    "Error getting cookie: ${e.message}"
                }

                val reqBodyStr = request.body?.let { body ->
                    try {
                        val buffer = Buffer()
                        body.writeTo(buffer)
                        buffer.readUtf8()
                    } catch (e: Exception) {
                        "[Error reading body: ${e.message}]"
                    }
                }

                val reqHeaders = (0 until request.headers.size).map { i ->
                    "${request.headers.name(i)}: ${request.headers.value(i)}"
                }

                Log.d(TAG, "--> [NETWORK REQ] $method $url")
                Log.d(TAG, "    [CookieManager for URL]: $cookieManagerCookies")
                reqHeaders.forEach { Log.d(TAG, "    [Header] $it") }
                if (reqBodyStr != null) {
                    Log.d(TAG, "    [Body] (${reqBodyStr.length} chars): ${reqBodyStr.take(1000)}")
                }

                val response: Response
                try {
                    response = chain.proceed(request)
                } catch (e: Exception) {
                    Log.e(TAG, "<-- [NETWORK FAIL] $method $url: ${e.message}", e)
                    throw e
                }

                val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
                val respHeaders = (0 until response.headers.size).map { i ->
                    "${response.headers.name(i)}: ${response.headers.value(i)}"
                }

                val peekBody = try {
                    response.peekBody(4096).string()
                } catch (e: Exception) {
                    "[Error reading peekBody: ${e.message}]"
                }

                Log.d(TAG, "<-- [NETWORK RESP] ${response.code} ${response.message} $method $url (${tookMs}ms)")
                respHeaders.forEach { Log.d(TAG, "    [Header] $it") }
                Log.d(TAG, "    [Body Preview] (${peekBody.length} chars): ${peekBody.take(1500)}")

                response
            }
            .build()
    }
}
