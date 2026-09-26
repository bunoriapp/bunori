package com.halovoid.bunori.extension.manager

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.api.core.network.NetworkClient
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.extension.adapter.ExtensionCrawlerAdapter
import com.halovoid.bunori.extension.api.models.ExtensionFormat
import com.halovoid.bunori.extension.api.models.ExtensionManifest
import com.halovoid.bunori.extension.api.models.ExtensionRepo
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Central manager for installing, loading, updating, and uninstalling extensions.
 *
 * All extension IDs follow the multicatalog convention: `repoKey.rawId`
 * (e.g. `bunori_7cf2a3b1.novelbins`). This class never hardcodes any
 * specific repo — ID prefixing is handled by [ExtensionRepoEntry.parseIndex].
 */
class ExtensionManager private constructor(private val context: Context) {

    private val bextLoader = BextLoader(context)
    private val lnReaderLoader = LnReaderLoader(context)
    private val httpClient = NetworkClient.okHttpClient

    private val _installedExtensions = MutableStateFlow<Map<String, LoadedExtension>>(emptyMap())
    val installedExtensions: StateFlow<Map<String, LoadedExtension>> = _installedExtensions.asStateFlow()

    private val _failedExtensions = MutableStateFlow<List<String>>(emptyList())
    val failedExtensions: StateFlow<List<String>> = _failedExtensions.asStateFlow()

    private val _catalogUpdatedTrigger = MutableStateFlow(System.currentTimeMillis())
    val catalogUpdatedTrigger: StateFlow<Long> = _catalogUpdatedTrigger.asStateFlow()

    private val extensionsDir: File
        get() = File(context.filesDir, "installed_extensions").also { if (!it.exists()) it.mkdirs() }

    private val catalogCacheDir: File
        get() = File(context.filesDir, "catalog_cache").also { if (!it.exists()) it.mkdirs() }

    companion object {
        private const val TAG = "ExtensionManager"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ExtensionManager? = null

        fun getInstance(context: Context): ExtensionManager =
            instance ?: synchronized(this) {
                instance ?: ExtensionManager(context.applicationContext).also { instance = it }
            }
    }

    private fun downloadBytes(url: String): ByteArray {
        if (url.startsWith("file://")) return File(url.removePrefix("file://")).readBytes()

        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} from $url")
        return response.body?.bytes() ?: throw IOException("Empty response from $url")
    }

    private fun downloadText(url: String): String {
        if (url.startsWith("file://")) return File(url.removePrefix("file://")).readText(Charsets.UTF_8)

        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} from $url")
        return response.body?.string() ?: throw IOException("Empty response from $url")
    }

    private suspend fun registerExtension(loaded: LoadedExtension) {
        _installedExtensions.value = _installedExtensions.value + (loaded.manifest.id to loaded)
        syncWithCrawlerFactory()
        _catalogUpdatedTrigger.value = System.currentTimeMillis()
        ensureExtensionLanguageEnabled(loaded.manifest.lang)
    }

    private suspend fun ensureExtensionLanguageEnabled(lang: String) {
        val clean = lang.lowercase().trim()
        if (clean.isBlank() || clean == "all" || clean == "multi") return
        try {
            val prefRepo = PreferenceRepository.getInstance(context)
            val current = prefRepo.enabledExtensionLanguages.first().map { it.lowercase().trim() }
            if (clean !in current) prefRepo.setExtensionLanguageEnabled(clean, true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to auto-enable language '$clean': ${e.message}")
        }
    }

    private fun parseJsManifest(jsContent: String, fallbackId: String): ExtensionManifest {
        fun extract(key: String, default: String = ""): String =
            Regex("""$key\s*:\s*["']([^"']+)["']""").find(jsContent)?.groupValues?.get(1) ?: default

        return ExtensionManifest(
            id = extract("id", fallbackId),
            name = extract("name", fallbackId),
            version = extract("version", "1.0.0"),
            lang = extract("lang", "en"),
            baseUrl = extract("site"),
            format = ExtensionFormat.LNREADER_JS
        )
    }

    private fun resolveUrl(base: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://") || relative.startsWith("file://")) {
            return relative
        }
        if (base.startsWith("file://")) {
            val parent = File(base.removePrefix("file://")).parentFile
            return "file://" + File(parent, relative).absolutePath
        }
        val lastSlash = base.lastIndexOf('/')
        return if (lastSlash != -1) base.substring(0, lastSlash + 1) + relative else "$base/$relative"
    }

    private fun parseCatalogEntries(jsonString: String, repo: ExtensionRepo): List<ExtensionRepoEntry> {
        return ExtensionRepoEntry.parseIndex(jsonString, repoName = repo.stableKey).map { entry ->
            val resolved = resolveUrl(repo.url, entry.downloadUrl)
            entry.copy(url = resolved, bextUrl = resolved)
        }
    }

    suspend fun loadInstalledExtensions() = withContext(Dispatchers.IO) {
        val loaded = mutableMapOf<String, LoadedExtension>()
        val failed = mutableListOf<String>()

        val dirs = extensionsDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        for (dir in dirs) {
            try {
                val bextFile = File(dir, "package.bext")
                val jsFile = File(dir, "plugin.js")
                val ext = when {
                    bextFile.exists() && bextFile.length() > 0 ->
                        bextLoader.loadFromBextFile(bextFile, extensionId = dir.name)
                    jsFile.exists() && jsFile.length() > 0 ->
                        lnReaderLoader.loadFromDirectory(dir)
                    else -> null
                }
                if (ext != null) {
                    loaded[ext.manifest.id] = ext
                    Log.i(TAG, "Loaded: ${ext.manifest.name} (${ext.manifest.id})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading extension from ${dir.name}", e)
                failed.add(dir.name)
            }
        }

        _failedExtensions.value = failed
        _installedExtensions.value = loaded
        syncWithCrawlerFactory()
    }

    private fun getCatalogFile(repoKey: String): File {
        val safeKey = repoKey.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        return File(catalogCacheDir, "catalog_$safeKey.json")
    }

    fun deleteRepoCatalogCache(repo: ExtensionRepo) {
        try {
            getCatalogFile(repo.stableKey).takeIf { it.exists() }?.delete()
            getCatalogFile(repo.name).takeIf { it.exists() }?.delete()
            _catalogUpdatedTrigger.value = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting catalog cache for ${repo.name}: ${e.message}")
        }
    }

    fun getCachedRepoCatalog(repo: ExtensionRepo): List<ExtensionRepoEntry>? {
        val file = getCatalogFile(repo.stableKey).takeIf { it.exists() && it.length() > 0L }
            ?: getCatalogFile(repo.name).takeIf { it.exists() && it.length() > 0L }
            ?: return null
        return try {
            parseCatalogEntries(file.readText(Charsets.UTF_8), repo)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading cached catalog for ${repo.name}: ${e.message}")
            null
        }
    }

    fun getAllCachedRepoCatalogs(repos: List<ExtensionRepo>? = null): List<ExtensionRepoEntry> {
        val files = catalogCacheDir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                val fileKey = file.nameWithoutExtension.removePrefix("catalog_")
                val matchingRepo = repos?.firstOrNull {
                    it.stableKey.equals(fileKey, ignoreCase = true) ||
                            it.name.equals(fileKey, ignoreCase = true)
                }

                // When filtering by repos: skip catalogs from unknown or disabled repos.
                // We don't delete unknown ones, a newly added repo's catalog may arrive
                // before the DataStore flow emits the updated repo list.
                if (repos != null && (matchingRepo == null || !matchingRepo.enabled)) return@mapNotNull null

                val jsonString = file.readText(Charsets.UTF_8)
                val repoKey = matchingRepo?.stableKey ?: fileKey
                val baseUrl = matchingRepo?.url ?: ""
                val entries = ExtensionRepoEntry.parseIndex(jsonString, repoName = repoKey)

                if (baseUrl.isNotBlank()) {
                    entries.map { e ->
                        val resolved = resolveUrl(baseUrl, e.downloadUrl)
                        e.copy(url = resolved, bextUrl = resolved)
                    }
                } else entries
            } catch (e: Exception) {
                Log.w(TAG, "Error reading catalog cache ${file.name}: ${e.message}")
                null
            }
        }.flatten()
    }

    suspend fun fetchRepoCatalog(
        repo: ExtensionRepo,
        forceNetwork: Boolean = false
    ): Result<List<ExtensionRepoEntry>> = withContext(Dispatchers.IO) {
        try {
            if (!forceNetwork) {
                val cached = getCachedRepoCatalog(repo)
                if (!cached.isNullOrEmpty()) return@withContext Result.success(cached)
            }

            val jsonString = downloadText(repo.url)

            runCatching {
                getCatalogFile(repo.stableKey).writeText(jsonString, Charsets.UTF_8)
                _catalogUpdatedTrigger.value = System.currentTimeMillis()
            }.onFailure { Log.w(TAG, "Failed caching catalog for ${repo.name}: ${it.message}") }

            Result.success(parseCatalogEntries(jsonString, repo))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching catalog for ${repo.name} (${repo.url})", e)
            val fallback = getCachedRepoCatalog(repo)
            if (!fallback.isNullOrEmpty()) Result.success(fallback) else Result.failure(e)
        }
    }

    suspend fun fetchAllRepoCatalogs(
        repos: List<ExtensionRepo>,
        forceNetwork: Boolean = false
    ): Result<List<ExtensionRepoEntry>> = withContext(Dispatchers.IO) {
        val enabled = repos.filter { it.enabled }
        if (enabled.isEmpty()) return@withContext Result.success(emptyList())
        val combined = coroutineScope {
            enabled.map { repo -> async { fetchRepoCatalog(repo, forceNetwork).getOrDefault(emptyList()) } }.awaitAll()
        }.flatten()
        Result.success(combined)
    }

    suspend fun checkForUpdates(repos: List<ExtensionRepo>): List<ExtensionRepoEntry> = withContext(Dispatchers.IO) {
        LnReaderRuntime.updateIfAvailable(context)
        val catalog = fetchAllRepoCatalogs(repos, forceNetwork = true).getOrNull() ?: return@withContext emptyList()
        val installed = _installedExtensions.value
        catalog.filter { entry ->
            val ext = installed[entry.id]
            ext != null && ExtensionRepoEntry.isVersionNewer(entry.version, ext.manifest.version)
        }
    }
    suspend fun downloadAndInstall(entry: ExtensionRepoEntry): Result<LoadedExtension> = withContext(Dispatchers.IO) {
        try {
            val fileBytes = downloadBytes(entry.downloadUrl)
            val isJs = entry.format == ExtensionFormat.LNREADER_JS || entry.downloadUrl.endsWith(".js")

            if (isJs) {
                val iconBytes = entry.iconUrl
                    ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?.let { runCatching { downloadBytes(it) }.getOrNull() }
                val loaded = lnReaderLoader.install(entry.toManifest(), fileBytes, iconBytes)
                registerExtension(loaded)
                Result.success(loaded)
            } else {
                val tempFile = File(context.cacheDir, "download_${entry.id}.bext")
                try {
                    tempFile.writeBytes(fileBytes)
                    installFromFile(tempFile, extensionId = entry.id)
                } finally {
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed installing extension ${entry.id}", e)
            Result.failure(e)
        }
    }
    suspend fun installFromFile(sourceFile: File, extensionId: String? = null): Result<LoadedExtension> = withContext(Dispatchers.IO) {
        try {
            val isJs = sourceFile.name.endsWith(".js", ignoreCase = true) ||
                    sourceFile.name.startsWith("plugin", ignoreCase = true)

            val loaded = if (isJs) {
                val jsContent = sourceFile.readText(Charsets.UTF_8)
                val manifest = parseJsManifest(jsContent, extensionId ?: sourceFile.nameWithoutExtension)
                lnReaderLoader.install(manifest, jsContent.toByteArray(Charsets.UTF_8))
            } else {
                val pkg = sourceFile.inputStream().use { BextUtils.readPackage(it, validateApiVersion = true) }
                val targetId = extensionId ?: pkg.manifest.id
                val targetBext = File(File(extensionsDir, targetId).apply { mkdirs() }, "package.bext")
                sourceFile.copyTo(targetBext, overwrite = true)
                bextLoader.loadPackage(pkg, targetBext, extensionId = targetId)
            }

            registerExtension(loaded)
            Log.i(TAG, "Installed: ${loaded.manifest.name} (${loaded.manifest.id})")
            Result.success(loaded)
        } catch (e: Exception) {
            Log.e(TAG, "Failed installing from file ${sourceFile.name}", e)
            Result.failure(e)
        }
    }

    suspend fun installFromUri(uri: Uri): Result<LoadedExtension> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.bext")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(IllegalArgumentException("Cannot open URI: $uri"))
            installFromFile(tempFile).also { tempFile.delete() }
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    suspend fun uninstall(extensionId: String): Boolean = withContext(Dispatchers.IO) {
        val current = _installedExtensions.value.toMutableMap()
        current.remove(extensionId)
        // Also remove cross-prefix aliases (e.g. "repo.ext" vs "ext")
        current.keys.filter { it.endsWith(".$extensionId") || extensionId.endsWith(".$it") }
            .forEach { current.remove(it) }

        _installedExtensions.value = current
        _failedExtensions.value -= extensionId
        syncWithCrawlerFactory()
        _catalogUpdatedTrigger.value = System.currentTimeMillis()

        listOf(File(extensionsDir, extensionId), File(extensionsDir, extensionId.substringAfter('.')))
            .distinct().filter { it.exists() }.forEach { it.deleteRecursively() }

        Log.i(TAG, "Uninstalled extension: $extensionId")
        true
    }


    fun getExtension(id: String): com.halovoid.bunori.extension.api.IExtension? =
        _installedExtensions.value[id]?.extension

    suspend fun syncWithCrawlerFactory() = withContext(Dispatchers.IO) {
        try {
            val adapters = _installedExtensions.value.values.map {
                ExtensionCrawlerAdapter(it.extension, it.iconFile)
            }
            CrawlerFactory.registerCrawlers(adapters)
            Log.i(TAG, "Synced ${adapters.size} crawlers to CrawlerFactory")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing with CrawlerFactory", e)
        }
    }
}
