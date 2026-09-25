package com.halovoid.bunori.extension.manager

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.extension.adapter.ExtensionCrawlerAdapter
import com.halovoid.bunori.extension.api.IExtension
import com.halovoid.bunori.extension.api.models.ExtensionFormat
import com.halovoid.bunori.extension.api.models.ExtensionMetadata
import com.halovoid.bunori.extension.api.models.ExtensionRepoEntry
import com.halovoid.bunori.extension.api.pkg.BextUtils
import com.halovoid.bunori.extension.loader.BextLoader
import com.halovoid.bunori.extension.loader.LnReaderLoader
import com.halovoid.bunori.extension.loader.LoadedExtension
import com.halovoid.bunori.lnreader.LnReaderRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Manages the lifecycle, storage, installation, catalog retrieval, and querying of Bunori extensions.
 */
class ExtensionManager private constructor(private val context: Context) {

    private val bextLoader = BextLoader(context)
    private val lnReaderLoader = LnReaderLoader(context)
    private val _installedExtensions = MutableStateFlow<Map<String, LoadedExtension>>(emptyMap())
    val installedExtensions: StateFlow<Map<String, LoadedExtension>> = _installedExtensions.asStateFlow()

    private val _failedExtensions = MutableStateFlow<List<String>>(emptyList())
    val failedExtensions: StateFlow<List<String>> = _failedExtensions.asStateFlow()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(com.halovoid.bunori.api.core.network.NetworkClient.fastDns)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val TAG = "ExtensionManager"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ExtensionManager? = null

        fun getInstance(context: Context): ExtensionManager {
            return instance ?: synchronized(this) {
                instance ?: ExtensionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val extensionsDir: File
        get() = File(context.filesDir, "installed_extensions").also {
            if (!it.exists()) it.mkdirs()
        }

    /**
     * Scans app storage, initializes all previously installed extensions,
     * and synchronizes them with [CrawlerFactory].
     */
    suspend fun loadInstalledExtensions() = withContext(Dispatchers.IO) {
        val loaded = mutableMapOf<String, LoadedExtension>()
        val failed = mutableListOf<String>()
        val dirs = extensionsDir.listFiles { file -> file.isDirectory } ?: emptyArray()

        for (dir in dirs) {
            val bextFile = File(dir, "package.bext")
            val jsFile = File(dir, "plugin.js")

            try {
                when {
                    bextFile.exists() && bextFile.length() > 0 -> {
                        val loadedExt = bextLoader.loadFromBextFile(bextFile)
                        loaded[loadedExt.manifest.id] = loadedExt
                        Log.i(TAG, "Loaded BEXT extension on startup: ${loadedExt.manifest.name}")
                    }
                    jsFile.exists() && jsFile.length() > 0 -> {
                        val loadedExt = lnReaderLoader.loadFromDirectory(dir)
                        loaded[loadedExt.manifest.id] = loadedExt
                        Log.i(TAG, "Loaded LNReader extension on startup: ${loadedExt.manifest.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load extension from ${dir.absolutePath}", e)
                failed.add(dir.name)
            }
        }

        _failedExtensions.value = failed
        _installedExtensions.value = loaded
        syncWithCrawlerFactory(loaded)
    }

    private fun getCatalogCacheFile(repoUrl: String): File {
        val cacheDir = File(context.filesDir, "catalog_cache").apply { mkdirs() }
        val hash = try {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(repoUrl.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            repoUrl.hashCode().toString()
        }
        return File(cacheDir, "catalog_$hash.json")
    }

    /**
     * Returns locally cached extension repository catalog entries if available.
     * If [repoUrl] is provided, loads the cache specifically for that repository.
     * Otherwise, aggregates all cached repository files.
     */
    fun getCachedRepoCatalog(repoUrl: String? = null): List<ExtensionRepoEntry>? {
        return try {
            if (repoUrl != null) {
                val file = getCatalogCacheFile(repoUrl)
                if (!file.exists() || file.length() == 0L) {
                    val legacyFile = File(context.filesDir, "extension_catalog_cache.json")
                    if (legacyFile.exists() && legacyFile.length() > 0L) {
                        val jsonString = legacyFile.readText(Charsets.UTF_8)
                        val entries = ExtensionRepoEntry.parseIndex(jsonString)
                        return entries.map { entry ->
                            val resolvedUrl = resolveUrl(repoUrl, entry.downloadUrl)
                            entry.copy(url = resolvedUrl, bextUrl = resolvedUrl)
                        }
                    }
                    return null
                }
                val jsonString = file.readText(Charsets.UTF_8)
                val entries = ExtensionRepoEntry.parseIndex(jsonString)
                entries.map { entry ->
                    val resolvedUrl = resolveUrl(repoUrl, entry.downloadUrl)
                    entry.copy(url = resolvedUrl, bextUrl = resolvedUrl)
                }
            } else {
                val cacheDir = File(context.filesDir, "catalog_cache")
                val files = cacheDir.listFiles { f -> f.extension == "json" }
                val allEntries = mutableListOf<ExtensionRepoEntry>()
                if (files != null && files.isNotEmpty()) {
                    for (file in files) {
                        try {
                            val jsonString = file.readText(Charsets.UTF_8)
                            allEntries.addAll(ExtensionRepoEntry.parseIndex(jsonString))
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed reading catalog cache ${file.name}: ${e.message}")
                        }
                    }
                }
                if (allEntries.isEmpty()) {
                    val legacyFile = File(context.filesDir, "extension_catalog_cache.json")
                    if (legacyFile.exists() && legacyFile.length() > 0L) {
                        try {
                            allEntries.addAll(ExtensionRepoEntry.parseIndex(legacyFile.readText(Charsets.UTF_8)))
                        } catch (_: Exception) {}
                    }
                }
                allEntries.ifEmpty { null }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cached repository catalog: ${e.message}")
            null
        }
    }

    /**
     * Fetches and parses an extension catalog/repository index from [repoUrl].
     * If [forceNetwork] is false and a local cache is present, returns the cached version.
     * Resolves any relative package URLs to absolute URLs.
     */
    suspend fun fetchRepoCatalog(
        repoUrl: String,
        forceNetwork: Boolean = false
    ): Result<List<ExtensionRepoEntry>> = withContext(Dispatchers.IO) {
        try {
            if (!forceNetwork) {
                val cached = getCachedRepoCatalog(repoUrl)
                if (cached != null && cached.isNotEmpty()) {
                    Log.i(TAG, "Loaded extension catalog from local cache (${cached.size} entries) for $repoUrl")
                    return@withContext Result.success(cached)
                }
            }

            val jsonString = if (repoUrl.startsWith("file://")) {
                val localPath = repoUrl.removePrefix("file://")
                File(localPath).readText(Charsets.UTF_8)
            } else {
                val request = Request.Builder()
                    .url(repoUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Failed to fetch repository index: HTTP ${response.code}")
                    )
                }
                response.body?.string() ?: return@withContext Result.failure(
                    IOException("Empty response body from repository")
                )
            }

            // Save raw json to repo-specific cache file
            try {
                val cacheFile = getCatalogCacheFile(repoUrl)
                cacheFile.writeText(jsonString, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write extension catalog cache for $repoUrl: ${e.message}")
            }

            val entries = ExtensionRepoEntry.parseIndex(jsonString)

            // Resolve relative package URLs against repoUrl
            val resolvedEntries = entries.map { entry ->
                val resolvedUrl = resolveUrl(repoUrl, entry.downloadUrl)
                entry.copy(url = resolvedUrl, bextUrl = resolvedUrl)
            }

            Result.success(resolvedEntries)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching repository catalog from $repoUrl", e)
            // Fallback to cache if network request failed
            val cached = getCachedRepoCatalog(repoUrl)
            if (cached != null && cached.isNotEmpty()) {
                Log.i(TAG, "Falling back to cached extension catalog after network failure for $repoUrl")
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Fetches and aggregates catalogs from multiple repository URLs concurrently.
     * When duplicate IDs exist across repositories, the one with the newest version is retained.
     */
    suspend fun fetchAllRepoCatalogs(
        repoUrls: List<String>,
        forceNetwork: Boolean = false
    ): Result<List<ExtensionRepoEntry>> = withContext(Dispatchers.IO) {
        if (repoUrls.isEmpty()) return@withContext Result.success(emptyList())
        val combined = coroutineScope {
            repoUrls.map { url ->
                async { fetchRepoCatalog(url, forceNetwork).getOrDefault(emptyList()) }
            }.awaitAll()
        }.flatten()
            .groupBy { "${it.format.name}:${it.id}" }
            .values
            .map { entries ->
                entries.maxWithOrNull { a, b ->
                    if (ExtensionRepoEntry.isVersionNewer(a.version, b.version)) 1
                    else if (ExtensionRepoEntry.isVersionNewer(b.version, a.version)) -1
                    else 0
                } ?: entries.first()
            }

        Result.success(combined)
    }

    /**
     * Checks if any installed extensions have updates across the provided [repoUrls].
     */
    suspend fun checkForUpdates(repoUrls: List<String>): List<ExtensionRepoEntry> = withContext(Dispatchers.IO) {
        LnReaderRuntime.updateIfAvailable(context)

        val result = fetchAllRepoCatalogs(repoUrls, forceNetwork = true)
        val catalog = result.getOrNull() ?: return@withContext emptyList()
        val installed = _installedExtensions.value

        catalog.filter { entry ->
            val installedExt = installed.values.find { it.manifest.id == entry.id && it.manifest.format == entry.format }
            installedExt != null && ExtensionRepoEntry.isVersionNewer(entry.version, installedExt.manifest.version)
        }
    }

    suspend fun checkForUpdates(repoUrl: String): List<ExtensionRepoEntry> = checkForUpdates(listOf(repoUrl))

    /**
     * Downloads and installs an extension from a repository entry (either .bext or .js).
     */
    suspend fun downloadAndInstall(entry: ExtensionRepoEntry): Result<LoadedExtension> = withContext(Dispatchers.IO) {
        val isJsPlugin = entry.format == ExtensionFormat.LNREADER_JS || entry.downloadUrl.endsWith(".js")
        val downloadUrl = entry.downloadUrl

        try {
            val fileBytes = if (downloadUrl.startsWith("file://")) {
                val localPath = downloadUrl.removePrefix("file://")
                File(localPath).readBytes()
            } else {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Failed to download extension: HTTP ${response.code}")
                    )
                }
                response.body?.bytes() ?: return@withContext Result.failure(
                    IOException("Empty body downloading extension")
                )
            }

            if (isJsPlugin) {
                // Download icon if present
                var iconBytes: ByteArray? = null
                val iconUrl = entry.iconUrl
                if (!iconUrl.isNullOrBlank() && (iconUrl.startsWith("http://") || iconUrl.startsWith("https://"))) {
                    try {
                        val iconReq = Request.Builder().url(iconUrl).build()
                        val iconRes = httpClient.newCall(iconReq).execute()
                        if (iconRes.isSuccessful) {
                            iconBytes = iconRes.body?.bytes()
                        }
                    } catch (_: Exception) {}
                }

                val loaded = lnReaderLoader.install(entry.toManifest(), fileBytes, iconBytes)
                val updated = _installedExtensions.value.toMutableMap()
                updated[loaded.manifest.id] = loaded
                _installedExtensions.value = updated
                syncWithCrawlerFactory(updated)
                Result.success(loaded)
            } else {
                val tempFile = File(context.cacheDir, "download_${entry.id}_${System.currentTimeMillis()}.bext")
                FileOutputStream(tempFile).use { fos -> fos.write(fileBytes) }
                val result = installFromFile(tempFile)
                tempFile.delete()
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download and install extension: ${entry.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Installs or updates an extension from a local .bext file.
     *
     * @param sourceFile The .bext archive.
     * @return [Result] containing [LoadedExtension] on success.
     */
    suspend fun installFromFile(sourceFile: File): Result<LoadedExtension> = withContext(Dispatchers.IO) {
        try {
            // 1. Verify and read package
            val pkg = sourceFile.inputStream().use { stream ->
                BextUtils.readPackage(stream, validateApiVersion = true)
            }

            val targetDir = File(extensionsDir, pkg.manifest.id)
            if (!targetDir.exists()) targetDir.mkdirs()

            val targetBext = File(targetDir, "package.bext")
            if (targetBext.exists()) {
                targetBext.delete()
            }
            sourceFile.copyTo(targetBext, overwrite = true)

            // 2. Load into memory
            val loaded = bextLoader.loadPackage(pkg, targetBext)

            // 3. Update registry and sync CrawlerFactory
            val current = _installedExtensions.value.toMutableMap()
            current[loaded.manifest.id] = loaded
            _installedExtensions.value = current
            syncWithCrawlerFactory(current)

            Log.i(TAG, "Successfully installed extension: ${loaded.manifest.name} (id: ${loaded.manifest.id})")
            Result.success(loaded)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install extension from ${sourceFile.absolutePath}", e)
            Result.failure(e)
        }
    }

    /**
     * Installs or updates an extension selected via Android file picker [Uri].
     */
    suspend fun installFromUri(uri: Uri): Result<LoadedExtension> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.bext")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalArgumentException("Could not open URI: $uri"))

            val result = installFromFile(tempFile)
            tempFile.delete()
            result
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Uninstalls and removes an extension by its identifier.
     */
    suspend fun uninstall(extensionId: String): Boolean = withContext(Dispatchers.IO) {
        val current = _installedExtensions.value.toMutableMap()

        _installedExtensions.value = current
        _failedExtensions.value -= extensionId
        syncWithCrawlerFactory(current)

        val rawId = extensionId.removePrefix("bext.").removePrefix("lnreader.")
        val candidateDirs = listOf(
            File(extensionsDir, extensionId),
            File(extensionsDir, rawId),
            File(extensionsDir, "lnreader.$rawId"),
            File(extensionsDir, "bext.$rawId")
        ).distinct()

        for (targetDir in candidateDirs) {
            if (targetDir.exists()) {
                try {
                    targetDir.walkBottomUp().forEach { file ->
                        file.setWritable(true)
                        file.delete()
                    }
                    targetDir.deleteRecursively()
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning directory for ${targetDir.name}: ${e.message}")
                }
            }
        }

        Log.i(TAG, "Uninstalled extension: $extensionId")
        true
    }

    /**
     * Returns whether an extension with [id] is installed.
     */
    fun isInstalled(id: String): Boolean {
        return _installedExtensions.value.containsKey(id)
    }

    /**
     * Returns installed release version for extension [id], or null if not installed.
     */
    fun getInstalledVersion(id: String): String? {
        return _installedExtensions.value[id]?.manifest?.version
    }

    /**
     * Retrieves an active [IExtension] by source id.
     */
    fun getExtension(id: String): IExtension? {
        return _installedExtensions.value[id]?.extension
    }

    private fun syncWithCrawlerFactory(extensions: Map<String, LoadedExtension>) {
        val adapters = extensions.values.map { ExtensionCrawlerAdapter(it.extension, it.iconFile) }
        CrawlerFactory.registerCrawlers(adapters)
    }

    private fun resolveUrl(base: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://") || relative.startsWith("file://")) {
            return relative
        }
        return if (base.startsWith("file://")) {
            val parent = File(base.removePrefix("file://")).parentFile
            "file://" + File(parent, relative).absolutePath
        } else {
            val lastSlash = base.lastIndexOf('/')
            if (lastSlash != -1) {
                base.substring(0, lastSlash + 1) + relative
            } else {
                "$base/$relative"
            }
        }
    }
}
