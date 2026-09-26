package com.halovoid.bunori.ui.feature.source

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.halovoid.bunori.api.core.crawler.Crawler
import com.halovoid.bunori.api.core.crawler.CrawlerFactory
import com.halovoid.bunori.data.repository.DEFAULT_EXTENSION_REPO_URL
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.data.repository.UpdateRepository
import com.halovoid.bunori.extension.api.models.ExtensionFormat
import com.halovoid.bunori.extension.api.models.ExtensionRepoEntry
import com.halovoid.bunori.extension.loader.LoadedExtension
import com.halovoid.bunori.extension.manager.ExtensionManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class SyncState {
    object Idle : SyncState()
    object Loading : SyncState()
    data class Success(val message: String) : SyncState()
    data class Incompatible(val minVersion: String) : SyncState()
    data class Error(val error: String) : SyncState()
}

sealed class CatalogState {
    object Idle : CatalogState()
    object Loading : CatalogState()
    data class Success(val count: Int) : CatalogState()
    data class Error(val message: String) : CatalogState()
}

enum class ExtensionType(
    val label: String,
    val badgeText: String,
    val fileExtension: String
) {
    BEXT("BEXT", "BEXT", ".bext"),
    LNREADER("LNReader", "LNREADER", ".js")
}

data class ExtensionUiItem(
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val installedVersion: String?,
    val repoVersion: String?,
    val isInstalled: Boolean,
    val hasUpdate: Boolean,
    val isActionInProgress: Boolean = false,
    val repoDisplayName: String? = null,
    val repoEntry: ExtensionRepoEntry? = null,
    val loadedExtension: LoadedExtension? = null
) {
    val extensionType: ExtensionType
        get() {
            if (loadedExtension?.manifest?.format == ExtensionFormat.LNREADER_JS || repoEntry?.format == ExtensionFormat.LNREADER_JS || repoEntry?.downloadUrl?.endsWith(".js") == true) {
                return ExtensionType.LNREADER
            }
            return ExtensionType.BEXT
        }
    val iconModel: Any?
        get() {
            if (loadedExtension?.iconFile != null && loadedExtension.iconFile.exists()) {
                return loadedExtension.iconFile
            }
            val extIconUrl = loadedExtension?.extension?.metadata?.iconUrl
            if (!extIconUrl.isNullOrBlank()) {
                return extIconUrl
            }
            val repoIconUrl = repoEntry?.iconUrl
            if (!repoIconUrl.isNullOrBlank()) {
                return repoIconUrl
            }
            val targetBaseUrl = baseUrl.ifBlank { repoEntry?.baseUrl ?: "" }
            if (targetBaseUrl.isNotBlank()) {
                return targetBaseUrl
            }
            return null
        }

    val isDeprecated: Boolean
        get() = loadedExtension?.manifest?.isDeprecated == true || repoEntry?.isDeprecated == true

    val deprecationReason: String?
        get() = loadedExtension?.manifest?.deprecationReason
            ?: repoEntry?.deprecationReason

    val suggestedAlternative: String?
        get() = loadedExtension?.manifest?.suggestedAlternative
            ?: repoEntry?.suggestedAlternative

    val authors: List<String>
        get() = loadedExtension?.manifest?.authors?.takeIf { it.isNotEmpty() }
            ?: repoEntry?.authors
            ?: emptyList()

    val latestChangelog: String?
        get() = loadedExtension?.manifest?.latestChangelog ?: repoEntry?.latestChangelog
}

class SourceViewModel(
    application: Application,
    private val preferenceRepository: PreferenceRepository
) : AndroidViewModel(application) {

    private val extensionManager = ExtensionManager.getInstance(application)

    var selectedTabOrdinal: Int = 0

    val failedExtensions: StateFlow<List<String>> = extensionManager.failedExtensions

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)

    private val _catalogState = MutableStateFlow<CatalogState>(CatalogState.Idle)
    val catalogState: StateFlow<CatalogState> = _catalogState.asStateFlow()

    private val _catalogEntries = MutableStateFlow<List<ExtensionRepoEntry>>(emptyList())

    private val _inProgressIds = MutableStateFlow<Set<String>>(emptySet())
    val inProgressIds: StateFlow<Set<String>> = _inProgressIds.asStateFlow()

    private val _messageFlow = MutableSharedFlow<String>()
    val messageFlow: SharedFlow<String> = _messageFlow.asSharedFlow()

    val extensionItems: StateFlow<List<ExtensionUiItem>> = combine(
        extensionManager.installedExtensions,
        _catalogEntries,
        _inProgressIds,
        preferenceRepository.enabledExtensionLanguages,
        preferenceRepository.extensionRepos
    ) { installed, catalog, inProgress, enabledLangs, repos ->
        val items = mutableListOf<ExtensionUiItem>()
        val catalogMap = catalog.associateBy { it.id }

        val activeLangs = enabledLangs.map { it.lowercase().trim() }.toSet()
        val enabledRepoKeys = repos.filter { it.enabled }.map { it.stableKey.lowercase() }.toSet()

        fun isRepoEnabled(extId: String): Boolean {
            val key = if (extId.contains('.')) extId.substringBefore('.').lowercase() else return true
            if (key in enabledRepoKeys) return true
            val matching = repos.find { it.stableKey.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true) }
            return matching?.enabled ?: true
        }

        fun isLangEnabled(lang: String): Boolean {
            if (activeLangs.isEmpty() || "all" in activeLangs) return true
            return lang.lowercase().trim() in activeLangs
        }

        fun resolveRepoName(extId: String): String? {
            val key = if (extId.contains('.')) extId.substringBefore('.') else return null
            return repos.find { it.stableKey.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true) }?.name
                ?: key.uppercase()
        }

        for (entry in catalog) {
            if (!isRepoEnabled(entry.id)) continue
            if (!isLangEnabled(entry.lang)) continue

            val inst = installed[entry.id]
            val installedVer = inst?.manifest?.version
            val hasUpdate = inst != null && ExtensionRepoEntry.isVersionNewer(entry.version, installedVer)

            items.add(
                ExtensionUiItem(
                    id = entry.id,
                    name = entry.name,
                    lang = entry.lang,
                    baseUrl = entry.baseUrl,
                    installedVersion = installedVer,
                    repoVersion = entry.version,
                    isInstalled = inst != null,
                    hasUpdate = hasUpdate,
                    isActionInProgress = inProgress.contains(entry.id),
                    repoDisplayName = resolveRepoName(entry.id),
                    repoEntry = entry,
                    loadedExtension = inst
                )
            )
        }

        for ((id, inst) in installed) {
            if (!catalogMap.containsKey(id)) {
                if (!isRepoEnabled(id)) continue
                if (!isLangEnabled(inst.manifest.lang)) continue

                items.add(
                    ExtensionUiItem(
                        id = id,
                        name = inst.manifest.name,
                        lang = inst.manifest.lang,
                        baseUrl = inst.manifest.baseUrl,
                        installedVersion = inst.manifest.version,
                        repoVersion = null,
                        isInstalled = true,
                        hasUpdate = false,
                        isActionInProgress = inProgress.contains(id),
                        repoDisplayName = resolveRepoName(id),
                        repoEntry = null,
                        loadedExtension = inst
                    )
                )
            }
        }

        items.sortedWith(
            compareBy(
                { !it.hasUpdate },
                { !it.isInstalled },
                { it.name.lowercase() }
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val updatesCount: StateFlow<Int> = extensionItems.combine(extensionItems) { items, _ ->
        items.count { it.hasUpdate }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            val repos = preferenceRepository.extensionRepos.first()
            val initialCached = extensionManager.getAllCachedRepoCatalogs(repos)
            if (initialCached.isNotEmpty()) {
                _catalogEntries.value = initialCached
                _catalogState.value = CatalogState.Success(initialCached.size)
            }
            extensionManager.loadInstalledExtensions()
            refreshCatalog(forceNetwork = false)
        }
        checkForUpdates()
    }

    fun refreshCatalog(forceNetwork: Boolean = false) {
        viewModelScope.launch {
            _catalogState.value = CatalogState.Loading
            try {
                val repos = preferenceRepository.extensionRepos.first().filter { it.enabled }
                if (repos.isEmpty()) {
                    _catalogEntries.value = emptyList()
                    _catalogState.value = CatalogState.Idle
                    return@launch
                }
                val result = extensionManager.fetchAllRepoCatalogs(repos, forceNetwork = forceNetwork)
                result.onSuccess { entries ->
                    _catalogEntries.value = entries
                    _catalogState.value = CatalogState.Success(entries.size)
                }.onFailure { err ->
                    _catalogState.value = CatalogState.Error(err.message ?: "Failed to fetch repository index")
                    Log.w("CrawlerViewModel", "Failed to fetch repository catalog: ${err.message}")
                }
            } catch (e: Exception) {
                _catalogState.value = CatalogState.Error(e.message ?: "Unknown error")
                Log.e("CrawlerViewModel", "Error in refreshCatalog", e)
            }
        }
    }

    fun installExtension(entry: ExtensionRepoEntry) {
        viewModelScope.launch {
            _inProgressIds.value += entry.id
            val result = extensionManager.downloadAndInstall(entry)
            _inProgressIds.value -= entry.id
            result.onSuccess { loaded ->
                _messageFlow.emit("Installed ${loaded.manifest.name}")
            }.onFailure { err ->
                _messageFlow.emit("Failed to install ${entry.name}: ${err.message}")
            }
        }
    }

    fun uninstallExtension(extensionId: String) {
        viewModelScope.launch {
            _inProgressIds.value += extensionId
            val name = extensionManager.getExtension(extensionId)?.metadata?.name ?: extensionId
            val success = extensionManager.uninstall(extensionId)
            _inProgressIds.value -= extensionId
            if (success) {
                _messageFlow.emit("Uninstalled $name")
            }
        }
    }

    fun updateAll() {
        viewModelScope.launch {
            val toUpdate = extensionItems.value.filter { it.hasUpdate && it.repoEntry != null }
            for (item in toUpdate) {
                item.repoEntry?.let { entry ->
                    installExtension(entry)
                }
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            try {
                UpdateRepository.getInstance(getApplication()).checkForUpdates()
            } catch (e: Exception) {
                Log.e("CrawlerViewModel", "Failed to check for updates: ${e.message}", e)
            }
        }
    }
}
