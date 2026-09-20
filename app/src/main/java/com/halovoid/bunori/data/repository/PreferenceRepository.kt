package com.halovoid.bunori.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import com.halovoid.bunori.domain.models.CustomFont
import com.halovoid.bunori.domain.models.ReaderSettings
import com.halovoid.bunori.domain.models.ReaderTextAlign
import com.halovoid.bunori.domain.models.ReaderTheme
import com.halovoid.bunori.domain.models.ReadingMode
import kotlinx.coroutines.flow.Flow
import com.halovoid.bunori.data.preferences.appDataStore
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
private val DEFAULT_CHAPTER_DOWNLOAD_FILTER = stringPreferencesKey("default_chapter_download_filter")
private val DEFAULT_CHAPTER_SORT_TYPE = stringPreferencesKey("default_chapter_sort_type")
private val DEFAULT_CHAPTER_SORT_ORDER = stringPreferencesKey("default_chapter_sort_order")
private val DEFAULT_SOURCE_FILTER = stringPreferencesKey("default_source_filter")
private val BACKUP_FREQUENCY = stringPreferencesKey("backup_frequency")
private val THEME_MODE = stringPreferencesKey("theme_mode")
private val SELECTED_THEME_ID = stringPreferencesKey("selected_theme_id")
private val IS_AMOLED_MODE = booleanPreferencesKey("is_amoled_mode")
private val EXTENSION_REPO_URL = stringPreferencesKey("extension_repo_url")
private val CUSTOM_USER_AGENT = stringPreferencesKey("custom_user_agent")
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
private val READER_CUSTOM_CSS = stringPreferencesKey("reader_custom_css")
private val READER_CUSTOM_JS = stringPreferencesKey("reader_custom_js")
private val READER_CUSTOM_FONTS = stringSetPreferencesKey("reader_custom_fonts")
private val CUSTOM_DOMAIN_SELECTORS = stringSetPreferencesKey("custom_domain_selectors")

class PreferenceRepository private constructor(
    private val context: Context
) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: PreferenceRepository? = null

        fun getInstance(context: Context): PreferenceRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferenceRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val exportFolderUri: Flow<Uri?> =
        context.appDataStore.data.map { preferences ->
            preferences[EXPORT_FOLDER_URI]?.let(Uri::parse)
        }

    val isOnboardingCompleted: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[ONBOARDING_COMPLETED]?.toBoolean() ?: false
        }

    val currentDexTag: Flow<String?> =
        context.appDataStore.data.map { preferences ->
            preferences[CURRENT_DEX_TAG]
        }

    val betaModeApp: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[BETA_MODE_APP] ?: false
        }

    val betaModeCrawlers: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[BETA_MODE_CRAWLERS] ?: false
        }

    val ignoreImages: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[IGNORE_IMAGES] ?: false
        }

    val maxConcurrentJobs: Flow<Int> =
        context.appDataStore.data.map { preferences ->
            preferences[MAX_CONCURRENT_JOBS] ?: 3
        }

    val searchCompactView: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[SEARCH_COMPACT_VIEW] ?: false
        }

    val libraryCompactView: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[LIBRARY_COMPACT_VIEW] ?: false
        }

    val defaultChapterDownloadFilter: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[DEFAULT_CHAPTER_DOWNLOAD_FILTER] ?: "ALL"
        }

    val defaultChapterSortType: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[DEFAULT_CHAPTER_SORT_TYPE] ?: "CHAPTER_NUMBER"
        }

    val defaultChapterSortOrder: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[DEFAULT_CHAPTER_SORT_ORDER] ?: "ASCENDING"
        }

    val defaultSourceFilter: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[DEFAULT_SOURCE_FILTER] ?: "ALL"
        }

    val backupFrequency: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[BACKUP_FREQUENCY] ?: "Off"
        }

    val themeMode: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[THEME_MODE] ?: "SYSTEM"
        }

    val selectedThemeId: Flow<String> =
        context.appDataStore.data.map { preferences ->
            preferences[SELECTED_THEME_ID] ?: "DEFAULT"
        }

    val isAmoledMode: Flow<Boolean> =
        context.appDataStore.data.map { preferences ->
            preferences[IS_AMOLED_MODE] ?: false
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed.toString()
        }
    }

    suspend fun setExportFolder(uri: Uri) {
        context.appDataStore.edit { preferences ->
            preferences[EXPORT_FOLDER_URI] = uri.toString()
        }
    }

    suspend fun clearExportFolder() {
        context.appDataStore.edit { preferences ->
            preferences.remove(EXPORT_FOLDER_URI)
        }
    }

    suspend fun setCurrentDexTag(tag: String) {
        context.appDataStore.edit { preferences ->
            preferences[CURRENT_DEX_TAG] = tag
        }
    }

    suspend fun setBetaModeApp(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[BETA_MODE_APP] = enabled
        }
    }

    suspend fun setBetaModeCrawlers(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[BETA_MODE_CRAWLERS] = enabled
        }
    }

    suspend fun setIgnoreImages(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[IGNORE_IMAGES] = enabled
        }
    }

    suspend fun setMaxConcurrentJobs(jobs: Int) {
        context.appDataStore.edit { preferences ->
            preferences[MAX_CONCURRENT_JOBS] = jobs
        }
    }

    suspend fun setSearchCompactView(compact: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[SEARCH_COMPACT_VIEW] = compact
        }
    }

    suspend fun setLibraryCompactView(compact: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[LIBRARY_COMPACT_VIEW] = compact
        }
    }

    suspend fun setDefaultChapterDownloadFilter(filter: String) {
        context.appDataStore.edit { preferences ->
            preferences[DEFAULT_CHAPTER_DOWNLOAD_FILTER] = filter
        }
    }

    suspend fun setDefaultChapterSortType(type: String) {
        context.appDataStore.edit { preferences ->
            preferences[DEFAULT_CHAPTER_SORT_TYPE] = type
        }
    }

    suspend fun setDefaultChapterSortOrder(order: String) {
        context.appDataStore.edit { preferences ->
            preferences[DEFAULT_CHAPTER_SORT_ORDER] = order
        }
    }

    suspend fun setDefaultSourceFilter(filter: String) {
        context.appDataStore.edit { preferences ->
            preferences[DEFAULT_SOURCE_FILTER] = filter
        }
    }

    suspend fun setBackupFrequency(frequency: String) {
        context.appDataStore.edit { preferences ->
            preferences[BACKUP_FREQUENCY] = frequency
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.appDataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setSelectedThemeId(themeId: String) {
        context.appDataStore.edit { preferences ->
            preferences[SELECTED_THEME_ID] = themeId
        }
    }

    suspend fun setAmoledMode(enabled: Boolean) {
        context.appDataStore.edit { preferences ->
            preferences[IS_AMOLED_MODE] = enabled
        }
    }

    val extensionRepoUrl: Flow<String> =
        context.appDataStore.data.map { preferences ->
            val stored = preferences[EXTENSION_REPO_URL]
            if (stored.isNullOrBlank() || isDeprecatedRepoUrl(stored)) {
                DEFAULT_EXTENSION_REPO_URL
            } else {
                stored
            }
        }

    suspend fun setExtensionRepoUrl(url: String) {
        context.appDataStore.edit { preferences ->
            val trimmed = url.trim()
            if (trimmed.isEmpty() || trimmed == DEFAULT_EXTENSION_REPO_URL || isDeprecatedRepoUrl(trimmed)) {
                preferences.remove(EXTENSION_REPO_URL)
            } else {
                preferences[EXTENSION_REPO_URL] = trimmed
            }
        }
    }

    fun getSavedSourcesForNovel(novelUrl: String): Flow<Set<String>?> {
        val key = stringSetPreferencesKey("novel_sources_${novelUrl.hashCode()}")
        return context.appDataStore.data.map { preferences ->
            preferences[key]
        }
    }

    suspend fun saveSourcesForNovel(novelUrl: String, sources: Set<String>) {
        val key = stringSetPreferencesKey("novel_sources_${novelUrl.hashCode()}")
        context.appDataStore.edit { preferences ->
            if (sources.isEmpty()) {
                preferences.remove(key)
            } else {
                preferences[key] = sources
            }
        }
    }

    val customUserAgent: Flow<String?> =
        context.appDataStore.data.map { preferences ->
            preferences[CUSTOM_USER_AGENT]
        }

    suspend fun setCustomUserAgent(userAgent: String?) {
        context.appDataStore.edit { preferences ->
            if (userAgent.isNullOrBlank()) {
                preferences.remove(CUSTOM_USER_AGENT)
            } else {
                preferences[CUSTOM_USER_AGENT] = userAgent.trim()
            }
        }
    }

    val readerSettings: Flow<ReaderSettings> =
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
                customCss = prefs[READER_CUSTOM_CSS] ?: "",
                customJs = prefs[READER_CUSTOM_JS] ?: ""
            )
        }

    suspend fun updateReaderSettings(settings: ReaderSettings) {
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
            prefs[READER_CUSTOM_CSS] = settings.customCss
            prefs[READER_CUSTOM_JS] = settings.customJs
        }
    }

    suspend fun updateReaderTheme(theme: ReaderTheme) {
        context.appDataStore.edit { it[READER_THEME] = theme.name }
    }

    suspend fun updateReadingMode(mode: ReadingMode) {
        context.appDataStore.edit { it[READER_READING_MODE] = mode.name }
    }

    suspend fun updateReaderFont(fontFamily: String) {
        context.appDataStore.edit { it[READER_FONT_FAMILY] = fontFamily }
    }

    suspend fun updateReaderFontSize(sizeSp: Int) {
        context.appDataStore.edit { it[READER_FONT_SIZE] = sizeSp }
    }

    suspend fun updateReaderLineHeight(lineHeight: Float) {
        context.appDataStore.edit { it[READER_LINE_HEIGHT] = lineHeight }
    }

    suspend fun updateReaderPadding(paddingDp: Int) {
        context.appDataStore.edit { it[READER_PADDING_H] = paddingDp }
    }

    suspend fun updateReaderTextAlign(align: ReaderTextAlign) {
        context.appDataStore.edit { it[READER_TEXT_ALIGN] = align.name }
    }

    suspend fun updateVolumeKeyPageTurn(enabled: Boolean) {
        context.appDataStore.edit { it[READER_VOLUME_KEY_PAGE_TURN] = enabled }
    }

    suspend fun updateKeepScreenAwake(enabled: Boolean) {
        context.appDataStore.edit { it[READER_KEEP_SCREEN_AWAKE] = enabled }
    }

    suspend fun updateDimImages(enabled: Boolean) {
        context.appDataStore.edit { it[READER_DIM_IMAGES] = enabled }
    }

    suspend fun updateCustomCss(css: String) {
        context.appDataStore.edit { it[READER_CUSTOM_CSS] = css }
    }

    suspend fun updateCustomJs(js: String) {
        context.appDataStore.edit { it[READER_CUSTOM_JS] = js }
    }

    suspend fun updateCustomCode(css: String, js: String) {
        context.appDataStore.edit {
            it[READER_CUSTOM_CSS] = css
            it[READER_CUSTOM_JS] = js
        }
    }

    val customFonts: Flow<List<CustomFont>> =
        context.appDataStore.data.map { prefs ->
            val set = prefs[READER_CUSTOM_FONTS] ?: emptySet()
            set.mapNotNull { CustomFont.fromStorageString(it) }
        }

    suspend fun addCustomFont(font: CustomFont) {
        context.appDataStore.edit { prefs ->
            val current = prefs[READER_CUSTOM_FONTS] ?: emptySet()
            prefs[READER_CUSTOM_FONTS] = current + font.toStorageString()
        }
    }

    suspend fun removeCustomFont(font: CustomFont) {
        context.appDataStore.edit { prefs ->
            val current = prefs[READER_CUSTOM_FONTS] ?: emptySet()
            prefs[READER_CUSTOM_FONTS] = current - font.toStorageString()
        }
    }

    fun getDomainSelector(domain: String): Flow<String?> =
        context.appDataStore.data.map { prefs ->
            val set = prefs[CUSTOM_DOMAIN_SELECTORS] ?: emptySet()
            set.firstOrNull { it.startsWith("$domain|") }?.substringAfter('|')
        }

    suspend fun saveDomainSelector(domain: String, selector: String) {
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

