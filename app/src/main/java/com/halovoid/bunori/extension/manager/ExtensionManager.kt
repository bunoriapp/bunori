package com.halovoid.bunori.extension.manager

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.extension.adapter.ExtensionCrawlerAdapter
import com.halovoid.bunori.extension.api.IExtension
import com.halovoid.bunori.extension.api.models.ExtensionMetadata
import com.halovoid.bunori.extension.api.models.ExtensionRepoEntry
import com.halovoid.bunori.extension.api.pkg.BextUtils
import com.halovoid.bunori.extension.loader.BextLoader
import com.halovoid.bunori.extension.loader.LoadedExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Manages the lifecycle, storage, installation, catalog retrieval, and querying of Bunori extensions.
 */
class ExtensionManager private constructor(private val context: Context) {

    private val bextLoader = BextLoader(context)
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
            if (bextFile.exists() && bextFile.length() > 0) {
                try {
                    val loadedExt = bextLoader.loadFromBextFile(bextFile)
                    loaded[loadedExt.manifest.id] = loadedExt
                    Log.i(TAG, "Loaded extension on startup: ${loadedExt.manifest.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load extension from ${bextFile.absolutePath}", e)
                    failed.add(dir.name)
                }
            }
        }

        _failedExtensions.value = failed
        _installedExtensions.value = loaded
        syncWithCrawlerFactory(loaded)
    }

    private val catalogCacheFile: File
        get() = File(context.filesDir, "extension_catalog_cache.json")

    /**
     * Returns locally cached extension repository catalog entries if available.
     */
    fun getCachedRepoCatalog(repoUrl: String? = null): List<ExtensionRepoEntry>? {
        if (!catalogCacheFile.exists() || catalogCacheFile.length() == 0L) return null
        return try {
            val jsonString = catalogCacheFile.readText(Charsets.UTF_8)
            val entries = ExtensionRepoEntry.parseIndex(jsonString)
            if (repoUrl != null) {
                entries.map { entry ->
                    entry.copy(bextUrl = resolveUrl(repoUrl, entry.bextUrl))
                }
            } else {
                entries
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
                    Log.i(TAG, "Loaded extension catalog from local cache (${cached.size} entries)")
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

            // Save raw json to cache file
            try {
                catalogCacheFile.writeText(jsonString, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write extension catalog cache: ${e.message}")
            }

            val entries = ExtensionRepoEntry.parseIndex(jsonString)

            // Resolve relative bextUrl against repoUrl
            val resolvedEntries = entries.map { entry ->
                val resolvedUrl = resolveUrl(repoUrl, entry.bextUrl)
                entry.copy(bextUrl = resolvedUrl)
            }

            Result.success(resolvedEntries)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching repository catalog from $repoUrl", e)
            // Fallback to cache if network request failed
            val cached = getCachedRepoCatalog(repoUrl)
            if (cached != null && cached.isNotEmpty()) {
                Log.i(TAG, "Falling back to cached extension catalog after network failure")
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Checks if any installed extensions have updates in [repoUrl].
     */
    suspend fun checkForUpdates(repoUrl: String): List<ExtensionRepoEntry> = withContext(Dispatchers.IO) {
        val result = fetchRepoCatalog(repoUrl, forceNetwork = true)
        val catalog = result.getOrNull() ?: return@withContext emptyList()
        val installed = _installedExtensions.value

        catalog.filter { entry ->
            val installedExt = installed[entry.id]
            installedExt != null && isNewerVersion(entry.version, installedExt.manifest.version)
        }
    }

    private fun isNewerVersion(remote: String, installed: String): Boolean {
        if (remote == installed) return false
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val installedParts = installed.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, installedParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val ins = installedParts.getOrElse(i) { 0 }
            if (r > ins) return true
            if (r < ins) return false
        }
        return remote != installed
    }

    /**
     * Downloads and installs an extension from a repository entry.
     */
    suspend fun downloadAndInstall(entry: ExtensionRepoEntry): Result<LoadedExtension> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "download_${entry.id}_${System.currentTimeMillis()}.bext")
        try {
            if (entry.bextUrl.startsWith("file://")) {
                val localPath = entry.bextUrl.removePrefix("file://")
                File(localPath).copyTo(tempFile, overwrite = true)
            } else {
                val request = Request.Builder()
                    .url(entry.bextUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Failed to download extension: HTTP ${response.code}")
                    )
                }
                val body = response.body ?: return@withContext Result.failure(
                    IOException("Empty body downloading extension")
                )

                FileOutputStream(tempFile).use { fos ->
                    body.byteStream().copyTo(fos)
                }
            }

            val result = installFromFile(tempFile)
            tempFile.delete()
            result
        } catch (e: Exception) {
            tempFile.delete()
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
        val loaded = current.remove(extensionId)

        _installedExtensions.value = current
        _failedExtensions.value = _failedExtensions.value - extensionId
        syncWithCrawlerFactory(current)

        // Delete installed directory (including read-only dex files and package.bext)
        val targetDir = File(extensionsDir, extensionId)
        if (targetDir.exists()) {
            try {
                targetDir.walkBottomUp().forEach { file ->
                    file.setWritable(true)
                    file.delete()
                }
                targetDir.deleteRecursively()
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning directory for $extensionId: ${e.message}")
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

    /**
     * Returns all active [IExtension] implementations.
     */
    fun getAllExtensions(): List<IExtension> {
        return _installedExtensions.value.values.map { it.extension }
    }

    /**
     * Finds an extension capable of handling [url] by domain/baseUrl matching.
     */
    fun findExtensionForUrl(url: String): IExtension? {
        val cleanUrl = url.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
        val domain = cleanUrl.substringBefore('/')

        return _installedExtensions.value.values.map { it.extension }.firstOrNull { ext ->
            val extDomain = ext.metadata.baseUrl.lowercase()
                .removePrefix("https://").removePrefix("http://").removePrefix("www.")
                .substringBefore('/')
            domain.contains(extDomain) || extDomain.contains(domain)
        }
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
