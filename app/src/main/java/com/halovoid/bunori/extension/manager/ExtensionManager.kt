package com.halovoid.bunori.extension.manager

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.api.core.network.NetworkClient
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.extension.adapter.ExtensionCrawlerAdapter
import com.halovoid.bunori.extension.api.IExtension
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ExtensionManager private constructor(private val context: Context) {

    private val bextLoader = BextLoader(context)
    private val lnReaderLoader = LnReaderLoader(context)
    private val httpClient: OkHttpClient = NetworkClient.okHttpClient

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

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ExtensionManager? = null

        fun getInstance(context: Context): ExtensionManager {
            return instance ?: synchronized(this) {
                instance ?: ExtensionManager(context.applicationContext).also { instance = it }
            }
        }
    }

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
                        val loadedExt = bextLoader.loadFromBextFile(bextFile, extensionId = dir.name)
                        loaded[loadedExt.manifest.id] = loadedExt
                        Log.i(TAG, "Loaded BEXT extension: ${loadedExt.manifest.name} (${loadedExt.manifest.id})")
                    }
                    jsFile.exists() && jsFile.length() > 0 -> {
                        val loadedExt = lnReaderLoader.loadFromDirectory(dir)
                        loaded[loadedExt.manifest.id] = loadedExt
                        Log.i(TAG, "Loaded LNReader extension: ${loadedExt.manifest.name} (${loadedExt.manifest.id})")
                    }
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
            val file = getCatalogFile(repo.stableKey)
            if (file.exists()) file.delete()
            val legacy = getCatalogFile(repo.name)
            if (legacy.exists()) legacy.delete()
            _catalogUpdatedTrigger.value = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting catalog cache for ${repo.name}: ${e.message}")
        }
    }

    fun getCachedRepoCatalog(repo: ExtensionRepo): List<ExtensionRepoEntry>? {
        return try {
            val file = getCatalogFile(repo.stableKey).takeIf { it.exists() && it.length() > 0L }
                ?: getCatalogFile(repo.name).takeIf { it.exists() && it.length() > 0L }
                ?: return null

            val jsonString = file.readText(Charsets.UTF_8)
            val entries = ExtensionRepoEntry.parseIndex(jsonString, repoName = repo.stableKey)
            entries.map { entry ->
                val resolvedUrl = resolveUrl(repo.url, entry.downloadUrl)
                entry.copy(url = resolvedUrl, bextUrl = resolvedUrl)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading cached catalog for ${repo.name}: ${e.message}")
            null
        }
    }

    fun getAllCachedRepoCatalogs(repos: List<ExtensionRepo>? = null): List<ExtensionRepoEntry> {
        val files = catalogCacheDir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        val allEntries = mutableListOf<ExtensionRepoEntry>()

        for (file in files) {
            try {
                val fileKey = file.nameWithoutExtension.removePrefix("catalog_")
                val matchingRepo = repos?.firstOrNull {
                    it.stableKey.equals(fileKey, ignoreCase = true) || it.name.equals(fileKey, ignoreCase = true)
                }

                if (repos != null) {
                    if (matchingRepo == null) {
                        // Orphan cache file from removed repo
                        file.delete()
                        continue
                    }
                    if (!matchingRepo.enabled) continue
                }

                val jsonString = file.readText(Charsets.UTF_8)
                val effectiveRepoKey = matchingRepo?.stableKey ?: fileKey
                val entries = ExtensionRepoEntry.parseIndex(jsonString, repoName = effectiveRepoKey)
                val baseUrl = matchingRepo?.url ?: ""
                val resolved = if (baseUrl.isNotBlank()) {
                    entries.map { e ->
                        val res = resolveUrl(baseUrl, e.downloadUrl)
                        e.copy(url = res, bextUrl = res)
                    }
                } else entries
                allEntries.addAll(resolved)
            } catch (e: Exception) {
                Log.w(TAG, "Error reading catalog cache file ${file.name}: ${e.message}")
            }
        }
        return allEntries
    }

    suspend fun fetchRepoCatalog(
        repo: ExtensionRepo,
        forceNetwork: Boolean = false
    ): Result<List<ExtensionRepoEntry>> = withContext(Dispatchers.IO) {
        try {
            if (!forceNetwork) {
                val cached = getCachedRepoCatalog(repo)
                if (!cached.isNullOrEmpty()) {
                    return@withContext Result.success(cached)
                }
            }

            val jsonString = if (repo.url.startsWith("file://")) {
                val localPath = repo.url.removePrefix("file://")
                File(localPath).readText(Charsets.UTF_8)
            } else {
                val request = Request.Builder()
                    .url(repo.url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code} fetching ${repo.url}"))
                }
                response.body?.string() ?: return@withContext Result.failure(IOException("Empty response body"))
            }

            try {
                getCatalogFile(repo.stableKey).writeText(jsonString, Charsets.UTF_8)
                _catalogUpdatedTrigger.value = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.w(TAG, "Failed caching catalog for ${repo.name}: ${e.message}")
            }

            val entries = ExtensionRepoEntry.parseIndex(jsonString, repoName = repo.stableKey)
            val resolvedEntries = entries.map { entry ->
                val resolvedUrl = resolveUrl(repo.url, entry.downloadUrl)
                entry.copy(url = resolvedUrl, bextUrl = resolvedUrl)
            }
            Result.success(resolvedEntries)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching catalog for ${repo.name} (${repo.url})", e)
            val fallback = getCachedRepoCatalog(repo)
            if (!fallback.isNullOrEmpty()) {
                Result.success(fallback)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun fetchAllRepoCatalogs(
        repos: List<ExtensionRepo>,
        forceNetwork: Boolean = false
    ): Result<List<ExtensionRepoEntry>> = withContext(Dispatchers.IO) {
        val enabledRepos = repos.filter { it.enabled }
        if (enabledRepos.isEmpty()) return@withContext Result.success(emptyList())

        val combined = coroutineScope {
            enabledRepos.map { repo ->
                async { fetchRepoCatalog(repo, forceNetwork).getOrDefault(emptyList()) }
            }.awaitAll()
        }.flatten()

        Result.success(combined)
    }

    suspend fun checkForUpdates(repos: List<ExtensionRepo>): List<ExtensionRepoEntry> = withContext(Dispatchers.IO) {
        LnReaderRuntime.updateIfAvailable(context)

        val result = fetchAllRepoCatalogs(repos, forceNetwork = true)
        val catalog = result.getOrNull() ?: return@withContext emptyList()
        val installed = _installedExtensions.value

        catalog.filter { entry ->
            val installedExt = installed[entry.id]
            installedExt != null && ExtensionRepoEntry.isVersionNewer(entry.version, installedExt.manifest.version)
        }
    }

    suspend fun checkForUpdates(repoUrl: String): List<ExtensionRepoEntry> =
        checkForUpdates(listOf(ExtensionRepo(name = "repo", url = repoUrl, enabled = true)))

    suspend fun downloadAndInstall(entry: ExtensionRepoEntry): Result<LoadedExtension> = withContext(Dispatchers.IO) {
        val isJsPlugin = entry.format == ExtensionFormat.LNREADER_JS || entry.downloadUrl.endsWith(".js")
        val downloadUrl = entry.downloadUrl

        try {
            val fileBytes = if (downloadUrl.startsWith("file://")) {
                File(downloadUrl.removePrefix("file://")).readBytes()
            } else {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code} downloading extension"))
                }
                response.body?.bytes() ?: return@withContext Result.failure(IOException("Empty download response"))
            }

            if (isJsPlugin) {
                var iconBytes: ByteArray? = null
                val iconUrl = entry.iconUrl
                if (!iconUrl.isNullOrBlank() && (iconUrl.startsWith("http://") || iconUrl.startsWith("https://"))) {
                    try {
                        val iconRes = httpClient.newCall(Request.Builder().url(iconUrl).build()).execute()
                        if (iconRes.isSuccessful) iconBytes = iconRes.body?.bytes()
                    } catch (_: Exception) {}
                }

                val loaded = lnReaderLoader.install(entry.toManifest(), fileBytes, iconBytes)
                val updated = _installedExtensions.value.toMutableMap().apply { put(loaded.manifest.id, loaded) }
                _installedExtensions.value = updated
                syncWithCrawlerFactory()
                ensureExtensionLanguageEnabled(loaded.manifest.lang)
                Result.success(loaded)
            } else {
                val tempFile = File(context.cacheDir, "download_${entry.id}_${System.currentTimeMillis()}.bext")
                FileOutputStream(tempFile).use { fos -> fos.write(fileBytes) }
                val result = installFromFile(tempFile, extensionId = entry.id)
                tempFile.delete()
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed installing extension ${entry.id}", e)
            Result.failure(e)
        }
    }

    suspend fun installFromFile(sourceFile: File, extensionId: String? = null): Result<LoadedExtension> = withContext(Dispatchers.IO) {
        try {
            val isJs = sourceFile.name.endsWith(".js", ignoreCase = true) || sourceFile.name.startsWith("plugin", ignoreCase = true)
            if (isJs) {
                val jsContent = sourceFile.readText(Charsets.UTF_8)
                val id = Regex("id\\s*:\\s*[\"']([^\"']+)[\"']").find(jsContent)?.groupValues?.get(1) ?: sourceFile.nameWithoutExtension
                val name = Regex("name\\s*:\\s*[\"']([^\"']+)[\"']").find(jsContent)?.groupValues?.get(1) ?: sourceFile.nameWithoutExtension
                val lang = Regex("lang\\s*:\\s*[\"']([^\"']+)[\"']").find(jsContent)?.groupValues?.get(1) ?: "en"
                val site = Regex("site\\s*:\\s*[\"']([^\"']+)[\"']").find(jsContent)?.groupValues?.get(1) ?: ""
                val version = Regex("version\\s*:\\s*[\"']([^\"']+)[\"']").find(jsContent)?.groupValues?.get(1) ?: "1.0.0"

                val manifest = ExtensionManifest(
                    id = extensionId ?: id,
                    name = name,
                    version = version,
                    lang = lang,
                    baseUrl = site,
                    format = ExtensionFormat.LNREADER_JS
                )
                val loaded = lnReaderLoader.install(manifest, jsContent.toByteArray(Charsets.UTF_8))
                val updated = _installedExtensions.value.toMutableMap().apply { put(loaded.manifest.id, loaded) }
                _installedExtensions.value = updated
                syncWithCrawlerFactory()
                ensureExtensionLanguageEnabled(loaded.manifest.lang)
                Log.i(TAG, "Installed LNReader JS extension: ${loaded.manifest.name} (${loaded.manifest.id})")
                return@withContext Result.success(loaded)
            }

            val pkg = sourceFile.inputStream().use { stream ->
                BextUtils.readPackage(stream, validateApiVersion = true)
            }

            val targetId = extensionId ?: pkg.manifest.id
            val targetDir = File(extensionsDir, targetId).apply { if (!exists()) mkdirs() }
            val targetBext = File(targetDir, "package.bext")
            sourceFile.copyTo(targetBext, overwrite = true)

            val loaded = bextLoader.loadPackage(pkg, targetBext, extensionId = targetId)
            val updated = _installedExtensions.value.toMutableMap().apply { put(loaded.manifest.id, loaded) }
            _installedExtensions.value = updated
            syncWithCrawlerFactory()
            ensureExtensionLanguageEnabled(loaded.manifest.lang)

            Log.i(TAG, "Installed extension: ${loaded.manifest.name} (${loaded.manifest.id})")
            Result.success(loaded)
        } catch (e: Exception) {
            Log.e(TAG, "Failed installing from file ${sourceFile.name}", e)
            Result.failure(e)
        }
    }

    private suspend fun ensureExtensionLanguageEnabled(lang: String) {
        val clean = lang.lowercase().trim()
        if (clean.isNotBlank() && clean != "all" && clean != "multi") {
            try {
                val prefRepo = PreferenceRepository.getInstance(context)
                val currentLangs = prefRepo.enabledExtensionLanguages.first()
                if (clean !in currentLangs.map { it.lowercase().trim() }) {
                    prefRepo.setExtensionLanguageEnabled(clean, true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to auto-enable language '$clean': ${e.message}")
            }
        }
    }

    suspend fun installFromUri(uri: Uri): Result<LoadedExtension> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.bext")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(IllegalArgumentException("Cannot open URI: $uri"))

            val result = installFromFile(tempFile)
            tempFile.delete()
            result
        } catch (e: Exception) {
            tempFile.delete()
            Result.failure(e)
        }
    }

    suspend fun uninstall(extensionId: String): Boolean = withContext(Dispatchers.IO) {
        val current = _installedExtensions.value.toMutableMap()
        current.remove(extensionId)
        val altKeys = current.keys.filter { it.endsWith(".$extensionId") || extensionId.endsWith(".$it") }
        altKeys.forEach { current.remove(it) }

        _installedExtensions.value = current
        _failedExtensions.value -= extensionId
        syncWithCrawlerFactory()

        val candidateDirs = listOf(
            File(extensionsDir, extensionId),
            File(extensionsDir, extensionId.substringAfter('.'))
        ).distinct()

        for (targetDir in candidateDirs) {
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
        }

        Log.i(TAG, "Uninstalled extension: $extensionId")
        true
    }

    fun getExtension(id: String): IExtension? {
        return _installedExtensions.value[id]?.extension
    }

    suspend fun syncWithCrawlerFactory() = withContext(Dispatchers.IO) {
        try {
            val adapters = _installedExtensions.value.values.map {
                ExtensionCrawlerAdapter(it.extension, it.iconFile)
            }
            CrawlerFactory.registerCrawlers(adapters)
            Log.i(TAG, "Registered ${adapters.size} crawlers in CrawlerFactory (out of ${_installedExtensions.value.size} installed)")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing with CrawlerFactory", e)
        }
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
