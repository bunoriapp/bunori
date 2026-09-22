package com.halovoid.bunori.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.halovoid.bunori.data.preferences.appDataStore
import com.halovoid.bunori.domain.models.CustomFont
import com.halovoid.bunori.domain.models.ReaderSettings
import com.halovoid.bunori.domain.models.ReaderTextAlign
import com.halovoid.bunori.domain.models.ReaderTheme
import com.halovoid.bunori.domain.models.ReadingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val EXPORT_FOLDER_URI = stringPreferencesKey("export_folder_uri")
private val ONBOARDING_COMPLETED = stringPreferencesKey("onboarding_completed")
private val LAST_SYNC_TIME = stringPreferencesKey("last_sync_time")
private val CURRENT_DEX_TAG = stringPreferencesKey("current_dex_tag")
private val BETA_MODE_APP = booleanPreferencesKey("beta_mode_app")
private val BETA_MODE_CRAWLERS = booleanPreferencesKey("beta_mode_crawlers")
private val IGNORE_IMAGES = booleanPreferencesKey("ignore_images")
private val MAX_CONCURRENT_JOBS = intPreferencesKey("max_concurrent_jobs")
private val SEARCH_COMPACT_VIEW = booleanPreferencesKey("search_compact_view")
private val LIBRARY_COMPACT_VIEW = booleanPreferencesKey("library_compact_view")
private val ACTIVITY_COMPACT_VIEW = booleanPreferencesKey("activity_compact_view")
private val DEFAULT_CHAPTER_DOWNLOAD_FILTER = stringPreferencesKey("default_chapter_download_filter")
private val DEFAULT_CHAPTER_SORT_TYPE = stringPreferencesKey("default_chapter_sort_type")
private val DEFAULT_CHAPTER_SORT_ORDER = stringPreferencesKey("default_chapter_sort_order")
private val DEFAULT_SOURCE_FILTER = stringPreferencesKey("default_source_filter")
private val BACKUP_FREQUENCY = stringPreferencesKey("backup_frequency")
private val NOVEL_PRUNE_FREQUENCY = stringPreferencesKey("novel_prune_frequency")
private val CACHE_CLEAR_FREQUENCY = stringPreferencesKey("cache_clear_frequency")
private val LAST_NOVEL_PRUNE_TIME = androidx.datastore.preferences.core.longPreferencesKey("last_novel_prune_time")
private val LAST_CACHE_CLEAR_TIME = androidx.datastore.preferences.core.longPreferencesKey("last_cache_clear_time")
private val THEME_MODE = stringPreferencesKey("theme_mode")
private val SELECTED_THEME_ID = stringPreferencesKey("selected_theme_id")
private val IS_AMOLED_MODE = booleanPreferencesKey("is_amoled_mode")
private val IS_OFFLINE_MODE = booleanPreferencesKey("is_offline_mode")
private val SHOW_ALL_SAVED_NOVELS = booleanPreferencesKey("show_all_saved_novels")
private val EXTENSION_REPO_URL = stringPreferencesKey("extension_repo_url")
private val CUSTOM_USER_AGENT = stringPreferencesKey("custom_user_agent")
private val SHOW_WASM_SLOW_MODE_TOAST = booleanPreferencesKey("show_wasm_slow_mode_toast")
const val DEFAULT_EXTENSION_REPO_URL = "https://bunoriapp.github.io/extensions/index.min.json"

// Reader Preferences
private val READER_THEME = stringPreferencesKey("reader_theme")
private val READER_READING_MODE = stringPreferencesKey("reader_reading_mode")
private val READER_FONT_FAMILY = stringPreferencesKey("reader_font_family")
private val READER_FONT_SIZE = intPreferencesKey("reader_font_size")
private val READER_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
private val READER_LETTER_SPACING = floatPreferencesKey("reader_letter_spacing")
private val READER_PARAGRAPH_SPACING = floatPreferencesKey("reader_paragraph_spacing")
private val READER_PADDING_H = intPreferencesKey("reader_padding_h")
private val READER_TEXT_ALIGN = stringPreferencesKey("reader_text_align")
private val READER_VOLUME_KEY_PAGE_TURN = booleanPreferencesKey("reader_volume_key_page_turn")
private val READER_KEEP_SCREEN_AWAKE = booleanPreferencesKey("reader_keep_screen_awake")
private val READER_DIM_IMAGES = booleanPreferencesKey("reader_dim_images")
private val READER_SHOW_TAP_ZONE_OVERLAY = booleanPreferencesKey("reader_show_tap_zone_overlay")
private val READER_CUSTOM_CSS = stringPreferencesKey("reader_custom_css")
private val READER_CUSTOM_JS = stringPreferencesKey("reader_custom_js")
private val READER_CUSTOM_FONTS = stringSetPreferencesKey("reader_custom_fonts")
private val CUSTOM_DOMAIN_SELECTORS = stringSetPreferencesKey("custom_domain_selectors")

/**
 * Main repository interface for managing user preferences and DataStore state.
 */
interface PreferenceRepository {
    val exportFolderUri: Flow<Uri?>
    val isOnboardingCompleted: Flow<Boolean>
    val currentDexTag: Flow<String?>
    val betaModeApp: Flow<Boolean>
    val betaModeCrawlers: Flow<Boolean>
    val showWasmSlowModeToast: Flow<Boolean>
    val ignoreImages: Flow<Boolean>
    val maxConcurrentJobs: Flow<Int>
    val searchCompactView: Flow<Boolean>
    val libraryCompactView: Flow<Boolean>
    val activityCompactView: Flow<Boolean>
    val defaultChapterDownloadFilter: Flow<String>
    val defaultChapterSortType: Flow<String>
    val defaultChapterSortOrder: Flow<String>
    val defaultSourceFilter: Flow<String>
    val backupFrequency: Flow<String>
    val novelPruneFrequency: Flow<String>
    val cacheClearFrequency: Flow<String>
    val lastNovelPruneTime: Flow<Long>
    val lastCacheClearTime: Flow<Long>
    val themeMode: Flow<String>
    val selectedThemeId: Flow<String>
    val isAmoledMode: Flow<Boolean>
    val isOfflineMode: Flow<Boolean>
    val showAllSavedNovels: Flow<Boolean>
    val extensionRepoUrl: Flow<String>
    val customUserAgent: Flow<String?>
    val readerSettings: Flow<ReaderSettings>
    val customFonts: Flow<List<CustomFont>>

    suspend fun setShowAllSavedNovels(show: Boolean)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setExportFolder(uri: Uri)
    suspend fun clearExportFolder()
    suspend fun setCurrentDexTag(tag: String)
    suspend fun setBetaModeApp(enabled: Boolean)
    suspend fun setBetaModeCrawlers(enabled: Boolean)
    suspend fun setShowWasmSlowModeToast(enabled: Boolean)
    suspend fun setIgnoreImages(enabled: Boolean)
    suspend fun setMaxConcurrentJobs(jobs: Int)
    suspend fun setSearchCompactView(compact: Boolean)
    suspend fun setLibraryCompactView(compact: Boolean)
    suspend fun setActivityCompactView(compact: Boolean)
    suspend fun setDefaultChapterDownloadFilter(filter: String)
    suspend fun setDefaultChapterSortType(type: String)
    suspend fun setDefaultChapterSortOrder(order: String)
    suspend fun setDefaultSourceFilter(filter: String)
    suspend fun setBackupFrequency(frequency: String)
    suspend fun setNovelPruneFrequency(frequency: String)
    suspend fun setCacheClearFrequency(frequency: String)
    suspend fun setLastNovelPruneTime(timeMs: Long)
    suspend fun setLastCacheClearTime(timeMs: Long)
    suspend fun setThemeMode(mode: String)
    suspend fun setSelectedThemeId(themeId: String)
    suspend fun setAmoledMode(enabled: Boolean)
    suspend fun setOfflineMode(enabled: Boolean)
    suspend fun setExtensionRepoUrl(url: String)
    fun getSavedSourcesForNovel(novelUrl: String): Flow<Set<String>?>
    suspend fun saveSourcesForNovel(novelUrl: String, sources: Set<String>)
    suspend fun setCustomUserAgent(userAgent: String?)
    suspend fun updateReaderSettings(settings: ReaderSettings)
    suspend fun updateReaderTheme(theme: ReaderTheme)
    suspend fun updateReadingMode(mode: ReadingMode)
    suspend fun updateReaderFont(fontFamily: String)
    suspend fun updateReaderFontSize(sizeSp: Int)
    suspend fun updateReaderLineHeight(lineHeight: Float)
    suspend fun updateReaderPadding(paddingDp: Int)
    suspend fun updateReaderTextAlign(align: ReaderTextAlign)
    suspend fun updateVolumeKeyPageTurn(enabled: Boolean)
    suspend fun updateKeepScreenAwake(enabled: Boolean)
    suspend fun updateDimImages(enabled: Boolean)
    suspend fun updateShowTapZoneOverlay(enabled: Boolean)
    suspend fun updateCustomCss(css: String)
    suspend fun updateCustomJs(js: String)
    suspend fun updateCustomCode(css: String, js: String)
    suspend fun addCustomFont(font: CustomFont)
    suspend fun removeCustomFont(font: CustomFont)
    fun getDomainSelector(domain: String): Flow<String?>
    suspend fun saveDomainSelector(domain: String, selector: String)

    companion object {
        fun getInstance(context: Context): PreferenceRepository = PreferenceRepositoryImpl.getInstance(context)
    }
}

class PreferenceRepositoryImpl private constructor(
    private val context: Context
) : PreferenceRepository {
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: PreferenceRepository? = null

        fun getInstance(context: Context): PreferenceRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferenceRepositoryImpl(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override val exportFolderUri: Flow<Uri?> =
        context.appDataStore.data.map { preferences ->
            preferences[EXPORT_FOLDER_URI]?.let(Uri::parse)
        }

    override val isOnboardingCompleted: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[ONBOARDING_COMPLETED]?.toBoolean() ?: false
        }

    override val currentDexTag: Flow<String?> =
        context.appDataStore.data.map { preferences ->
            preferences[CURRENT_DEX_TAG]
        }

    override val betaModeApp: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[BETA_MODE_APP] ?: false
        }

    override val betaModeCrawlers: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[BETA_MODE_CRAWLERS] ?: false
        }

    override val showWasmSlowModeToast: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[SHOW_WASM_SLOW_MODE_TOAST] ?: true
        }

    override val ignoreImages: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[IGNORE_IMAGES] ?: false
        }

    override val maxConcurrentJobs: Flow<Int> =
        context.appDataStore.data.map { preferences ->
            preferences[MAX_CONCURRENT_JOBS] ?: 3
        }

    override val searchCompactView: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[SEARCH_COMPACT_VIEW] ?: false
        }

    override val libraryCompactView: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[LIBRARY_COMPACT_VIEW] ?: false
        }

    override val activityCompactView: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[ACTIVITY_COMPACT_VIEW] ?: false
        }

    override val defaultChapterDownloadFilter: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[DEFAULT_CHAPTER_DOWNLOAD_FILTER] ?: "ALL"
        }

    override val defaultChapterSortType: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[DEFAULT_CHAPTER_SORT_TYPE] ?: "CHAPTER_NUMBER"
        }

    override val defaultChapterSortOrder: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[DEFAULT_CHAPTER_SORT_ORDER] ?: "ASCENDING"
        }

    override val defaultSourceFilter: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[DEFAULT_SOURCE_FILTER] ?: "ALL"
        }

    override val backupFrequency: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[BACKUP_FREQUENCY] ?: "Off"
        }

    override val novelPruneFrequency: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[NOVEL_PRUNE_FREQUENCY] ?: "Every 10 Days"
        }

    override val cacheClearFrequency: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[CACHE_CLEAR_FREQUENCY] ?: "Every 4 Days"
        }

    override val lastNovelPruneTime: Flow<Long> =
        context.appDataStore.data.map { preferences ->
            preferences[LAST_NOVEL_PRUNE_TIME] ?: 0L
        }

    override val lastCacheClearTime: Flow<Long> =
        context.appDataStore.data.map { preferences ->
            preferences[LAST_CACHE_CLEAR_TIME] ?: 0L
        }

    override val themeMode: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[THEME_MODE] ?: "SYSTEM"
        }

    override val selectedThemeId: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[SELECTED_THEME_ID] ?: "DEFAULT"
        }

    override val isAmoledMode: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[IS_AMOLED_MODE] ?: false
        }

    override val isOfflineMode: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[IS_OFFLINE_MODE] ?: false
        }

    override val showAllSavedNovels: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[SHOW_ALL_SAVED_NOVELS] ?: false
        }

    override suspend fun setShowAllSavedNovels(show: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[SHOW_ALL_SAVED_NOVELS] = show
        }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed.toString()
        }
    }

    override suspend fun setExportFolder(uri: Uri) {
        context.appDataStore.edit { preferences ->
            preferences[EXPORT_FOLDER_URI] = uri.toString()
        }
    }

    override suspend fun clearExportFolder() {
        context.appDataStore.edit { preferences ->
            preferences.remove(EXPORT_FOLDER_URI)
        }
    }

    override suspend fun setCurrentDexTag(tag: String) {
        context.appDataStore.edit { preferences ->
            preferences[CURRENT_DEX_TAG] = tag
        }
    }

    override suspend fun setBetaModeApp(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[BETA_MODE_APP] = enabled
        }
    }

    override suspend fun setBetaModeCrawlers(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[BETA_MODE_CRAWLERS] = enabled
        }
    }

    override suspend fun setShowWasmSlowModeToast(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[SHOW_WASM_SLOW_MODE_TOAST] = enabled
        }
    }

    override suspend fun setIgnoreImages(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[IGNORE_IMAGES] = enabled
        }
    }

    override suspend fun setMaxConcurrentJobs(jobs: Int) {
        context.appDataStore.edit { preferences ->
            preferences[MAX_CONCURRENT_JOBS] = jobs
        }
    }

    override suspend fun setSearchCompactView(compact: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[SEARCH_COMPACT_VIEW] = compact
        }
    }

    override suspend fun setLibraryCompactView(compact: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[LIBRARY_COMPACT_VIEW] = compact
        }
    }

    override suspend fun setActivityCompactView(compact: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[ACTIVITY_COMPACT_VIEW] = compact
        }
    }

    override suspend fun setDefaultChapterDownloadFilter(filter: String) {
        context.appDataStore.edit { preferences ->
            preferences[DEFAULT_CHAPTER_DOWNLOAD_FILTER] = filter
        }
    }

    override suspend fun setDefaultChapterSortType(type: String) {
        context.appDataStore.edit { preferences ->
            preferences[DEFAULT_CHAPTER_SORT_TYPE] = type
        }
    }

    override suspend fun setDefaultChapterSortOrder(order: String) {
        context.appDataStore.edit { preferences ->
            preferences[DEFAULT_CHAPTER_SORT_ORDER] = order
        }
    }

    override suspend fun setDefaultSourceFilter(filter: String) {
        context.appDataStore.edit { preferences ->
            preferences[DEFAULT_SOURCE_FILTER] = filter
        }
    }

    override suspend fun setBackupFrequency(frequency: String) {
        context.appDataStore.edit { preferences ->
            preferences[BACKUP_FREQUENCY] = frequency
        }
    }

    override suspend fun setNovelPruneFrequency(frequency: String) {
        context.appDataStore.edit { preferences ->
            preferences[NOVEL_PRUNE_FREQUENCY] = frequency
        }
    }

    override suspend fun setCacheClearFrequency(frequency: String) {
        context.appDataStore.edit { preferences ->
            preferences[CACHE_CLEAR_FREQUENCY] = frequency
        }
    }

    override suspend fun setLastNovelPruneTime(timeMs: Long) {
        context.appDataStore.edit { preferences ->
            preferences[LAST_NOVEL_PRUNE_TIME] = timeMs
        }
    }

    override suspend fun setLastCacheClearTime(timeMs: Long) {
        context.appDataStore.edit { preferences ->
            preferences[LAST_CACHE_CLEAR_TIME] = timeMs
        }
    }

    override suspend fun setThemeMode(mode: String) {
        context.appDataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    override suspend fun setSelectedThemeId(themeId: String) {
        context.appDataStore.edit { preferences ->
            preferences[SELECTED_THEME_ID] = themeId
        }
    }

    override suspend fun setAmoledMode(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[IS_AMOLED_MODE] = enabled
        }
    }

    override suspend fun setOfflineMode(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[IS_OFFLINE_MODE] = enabled
        }
    }

    override val extensionRepoUrl: Flow<String> =
        context.appDataStore.data.map { preferences ->
            val stored = preferences[EXTENSION_REPO_URL]
            when {
                stored == null -> DEFAULT_EXTENSION_REPO_URL
                stored.isNotBlank() && isDeprecatedRepoUrl(stored) -> DEFAULT_EXTENSION_REPO_URL
                else -> stored
            }
        }

    override suspend fun setExtensionRepoUrl(url: String) {
        context.appDataStore.edit { preferences ->
            val trimmed = url.trim()
            if (trimmed == DEFAULT_EXTENSION_REPO_URL) {
                preferences.remove(EXTENSION_REPO_URL)
            } else {
                preferences[EXTENSION_REPO_URL] = trimmed
            }
        }
    }

    override fun getSavedSourcesForNovel(novelUrl: String): Flow<Set<String>?> {
        val key = stringSetPreferencesKey("novel_sources_${novelUrl.hashCode()}")
        return context.appDataStore.data.map { preferences ->
            preferences[key]
        }
    }

    override suspend fun saveSourcesForNovel(novelUrl: String, sources: Set<String>) {
        val key = stringSetPreferencesKey("novel_sources_${novelUrl.hashCode()}")
        context.appDataStore.edit { preferences ->
            if (sources.isEmpty()) {
                preferences.remove(key)
            } else {
                preferences[key] = sources
            }
        }
    }

    override val customUserAgent: Flow<String?> =
        context.appDataStore.data.map { preferences ->
            preferences[CUSTOM_USER_AGENT]
        }

    override suspend fun setCustomUserAgent(userAgent: String?) {
        context.appDataStore.edit { preferences ->
            if (userAgent.isNullOrBlank()) {
                preferences.remove(CUSTOM_USER_AGENT)
            } else {
                preferences[CUSTOM_USER_AGENT] = userAgent.trim()
            }
        }
    }

    override val readerSettings: Flow<ReaderSettings> =
        context.appDataStore.data.map { prefs ->
            val themeStr = prefs[READER_THEME] ?: ReaderTheme.DARK.name
            val theme = runCatching { ReaderTheme.valueOf(themeStr) }.getOrDefault(ReaderTheme.DARK)

            val modeStr = prefs[READER_READING_MODE] ?: ReadingMode.CONTINUOUS.name
            val mode = runCatching { ReadingMode.valueOf(modeStr) }.getOrDefault(ReadingMode.CONTINUOUS)

            val alignStr = prefs[READER_TEXT_ALIGN] ?: ReaderTextAlign.LEFT.name
            val align = runCatching { ReaderTextAlign.valueOf(alignStr) }.getOrDefault(ReaderTextAlign.LEFT)

            ReaderSettings(
                theme = theme,
                readingMode = mode,
                fontFamily = prefs[READER_FONT_FAMILY] ?: "Lora",
                fontSizeSp = prefs[READER_FONT_SIZE] ?: 19,
                lineHeight = prefs[READER_LINE_HEIGHT] ?: 1.65f,
                letterSpacing = prefs[READER_LETTER_SPACING] ?: 0.01f,
                paragraphSpacingEm = prefs[READER_PARAGRAPH_SPACING] ?: 1.25f,
                horizontalPaddingDp = prefs[READER_PADDING_H] ?: 18,
                textAlign = align,
                volumeKeyPageTurn = prefs[READER_VOLUME_KEY_PAGE_TURN] ?: false,
                keepScreenAwake = prefs[READER_KEEP_SCREEN_AWAKE] ?: false,
                dimImagesInDarkMode = prefs[READER_DIM_IMAGES] ?: true,
                showTapZoneOverlay = prefs[READER_SHOW_TAP_ZONE_OVERLAY] ?: true,
                customCss = prefs[READER_CUSTOM_CSS] ?: "",
                customJs = prefs[READER_CUSTOM_JS] ?: ""
            )
        }

    override suspend fun updateReaderSettings(settings: ReaderSettings) {
        context.appDataStore.edit { prefs ->
            prefs[READER_THEME] = settings.theme.name
            prefs[READER_READING_MODE] = settings.readingMode.name
            prefs[READER_FONT_FAMILY] = settings.fontFamily
            prefs[READER_FONT_SIZE] = settings.fontSizeSp
            prefs[READER_LINE_HEIGHT] = settings.lineHeight
            prefs[READER_LETTER_SPACING] = settings.letterSpacing
            prefs[READER_PARAGRAPH_SPACING] = settings.paragraphSpacingEm
            prefs[READER_PADDING_H] = settings.horizontalPaddingDp
            prefs[READER_TEXT_ALIGN] = settings.textAlign.name
            prefs[READER_VOLUME_KEY_PAGE_TURN] = settings.volumeKeyPageTurn
            prefs[READER_KEEP_SCREEN_AWAKE] = settings.keepScreenAwake
            prefs[READER_DIM_IMAGES] = settings.dimImagesInDarkMode
            prefs[READER_SHOW_TAP_ZONE_OVERLAY] = settings.showTapZoneOverlay
            prefs[READER_CUSTOM_CSS] = settings.customCss
            prefs[READER_CUSTOM_JS] = settings.customJs
        }
    }

    override suspend fun updateReaderTheme(theme: ReaderTheme) {
        context.appDataStore.edit { it[READER_THEME] = theme.name }
    }

    override suspend fun updateReadingMode(mode: ReadingMode) {
        context.appDataStore.edit { it[READER_READING_MODE] = mode.name }
    }

    override suspend fun updateReaderFont(fontFamily: String) {
        context.appDataStore.edit { it[READER_FONT_FAMILY] = fontFamily }
    }

    override suspend fun updateReaderFontSize(sizeSp: Int) {
        context.appDataStore.edit { it[READER_FONT_SIZE] = sizeSp }
    }

    override suspend fun updateReaderLineHeight(lineHeight: Float) {
        context.appDataStore.edit { it[READER_LINE_HEIGHT] = lineHeight }
    }

    override suspend fun updateReaderPadding(paddingDp: Int) {
        context.appDataStore.edit { it[READER_PADDING_H] = paddingDp }
    }

    override suspend fun updateReaderTextAlign(align: ReaderTextAlign) {
        context.appDataStore.edit { it[READER_TEXT_ALIGN] = align.name }
    }

    override suspend fun updateVolumeKeyPageTurn(enabled: Boolean) {
        context.appDataStore.edit { it[READER_VOLUME_KEY_PAGE_TURN] = enabled }
    }

    override suspend fun updateKeepScreenAwake(enabled: Boolean) {
        context.appDataStore.edit { it[READER_KEEP_SCREEN_AWAKE] = enabled }
    }

    override suspend fun updateDimImages(enabled: Boolean) {
        context.appDataStore.edit { it[READER_DIM_IMAGES] = enabled }
    }

    override suspend fun updateShowTapZoneOverlay(enabled: Boolean) {
        context.appDataStore.edit { it[READER_SHOW_TAP_ZONE_OVERLAY] = enabled }
    }

    override suspend fun updateCustomCss(css: String) {
        context.appDataStore.edit { it[READER_CUSTOM_CSS] = css }
    }

    override suspend fun updateCustomJs(js: String) {
        context.appDataStore.edit { it[READER_CUSTOM_JS] = js }
    }

    override suspend fun updateCustomCode(css: String, js: String) {
        context.appDataStore.edit {
            it[READER_CUSTOM_CSS] = css
            it[READER_CUSTOM_JS] = js
        }
    }

    override val customFonts: Flow<List<CustomFont>> =
        context.appDataStore.data.map { prefs ->
            val set = prefs[READER_CUSTOM_FONTS] ?: emptySet()
            set.mapNotNull { CustomFont.fromStorageString(it) }
        }

    override suspend fun addCustomFont(font: CustomFont) {
        context.appDataStore.edit { prefs ->
            val current = prefs[READER_CUSTOM_FONTS] ?: emptySet()
            prefs[READER_CUSTOM_FONTS] = current + font.toStorageString()
        }
    }

    override suspend fun removeCustomFont(font: CustomFont) {
        context.appDataStore.edit { prefs ->
            val current = prefs[READER_CUSTOM_FONTS] ?: emptySet()
            prefs[READER_CUSTOM_FONTS] = current - font.toStorageString()
        }
    }

    override fun getDomainSelector(domain: String): Flow<String?> =
        context.appDataStore.data.map { prefs ->
            val set = prefs[CUSTOM_DOMAIN_SELECTORS] ?: emptySet()
            set.firstOrNull { it.startsWith("$domain|") }?.substringAfter('|')
        }

    override suspend fun saveDomainSelector(domain: String, selector: String) {
        context.appDataStore.edit { prefs ->
            val current = prefs[CUSTOM_DOMAIN_SELECTORS] ?: emptySet()
            val filtered = current.filterNot { it.startsWith("$domain|") }.toSet()
            prefs[CUSTOM_DOMAIN_SELECTORS] = filtered + "$domain|$selector"
        }
    }
}

private fun isDeprecatedRepoUrl(url: String): Boolean {
    return url.contains("Binit06") ||
           url.contains("/releases/download/") ||
           url.endsWith("/repo/index.json") ||
           url.endsWith("/repo/index.min.json")
}
