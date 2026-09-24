package com.halovoid.bunori.extension.api.models

import com.halovoid.bunori.extension.api.ExtensionJson
import kotlinx.serialization.Serializable

/**
 * Metadata entry representing an extension available in an online or local repository index.
 *
 * @property id Unique identifier of the extension (e.g. "novelbins").
 * @property name Display name of the extension source.
 * @property version Monotonically increasing release version of this specific extension.
 * @property apiVersion Target Bunori Extension API contract version (currently 1).
 * @property lang Supported language code (e.g. "en", "es").
 * @property baseUrl Canonical base URL of the website.
 * @property entryClass Fully-qualified class name implementing [com.halovoid.bunori.extension.api.IExtension].
 * @property iconPath Optional path to the icon asset inside the .bext package (e.g. "assets/icon.png").
 * @property bextUrl URL or relative path to the .bext archive file.
 * @property size File size in bytes.
 * @property sha256 Optional SHA-256 checksum of the .bext file.
 */
@Serializable
data class ExtensionRepoEntry(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val apiVersion: Int = 1,
    val lang: String = "en",
    val baseUrl: String = "",
    val authors: List<String> = emptyList(),
    val isDeprecated: Boolean = false,
    val deprecationReason: String? = null,
    val suggestedAlternative: String? = null,
    val latestChangelog: String? = null,
    val entryClass: String? = null,
    val iconPath: String? = null,
    val iconUrl: String? = null,
    val url: String = "",
    val bextUrl: String = "",
    val size: Long = 0L,
    val sha256: String? = null,
    val codeHash: String? = null,
    val artifacts: List<String> = emptyList(),
    val webviewNeeded: Boolean = false,
    val runnerConcurrency: Int = 3,
    val runnerCooldown: Long = 1000L,
    val maxAttempts: Int = 3
) {
    val downloadUrl: String
        get() = url.ifBlank { bextUrl }

    fun toManifest(): ExtensionManifest = ExtensionManifest(
        id = id,
        name = name,
        version = version,
        apiVersion = apiVersion,
        lang = lang,
        baseUrl = baseUrl,
        authors = authors,
        isDeprecated = isDeprecated,
        deprecationReason = deprecationReason,
        suggestedAlternative = suggestedAlternative,
        entryClass = entryClass,
        iconPath = iconPath,
        iconUrl = iconUrl,
        webviewNeeded = webviewNeeded,
        runnerConcurrency = runnerConcurrency,
        runnerCooldown = runnerCooldown,
        maxAttempts = maxAttempts
    )

    companion object {
        /**
         * Parses repository index JSON from standard Bunori `index.json`, array `[...]`,
         * or LNReader `plugins.min.json`.
         */
        fun parseIndex(jsonString: String): List<ExtensionRepoEntry> {
            val trimmed = jsonString.trim()
            return if (trimmed.startsWith("[")) {
                try {
                    ExtensionJson.json.decodeFromString<List<ExtensionRepoEntry>>(trimmed)
                } catch (_: Exception) {
                    try {
                        val lnItems = ExtensionJson.json.decodeFromString<List<LnReaderPluginRepoItem>>(trimmed)
                        lnItems.map { item ->
                            val rawId = item.id.removePrefix("lnreader.")
                            ExtensionRepoEntry(
                                id = "lnreader.$rawId",
                                name = item.name,
                                version = item.version,
                                lang = item.lang,
                                baseUrl = item.site.orEmpty(),
                                url = item.url,
                                bextUrl = item.url,
                                iconUrl = item.iconUrl
                            )
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            } else {
                try {
                    ExtensionJson.json.decodeFromString<ExtensionRepoIndex>(trimmed).extensions
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

        /**
         * Checks whether [candidateVersion] is strictly newer than [currentVersion]
         * using Semantic Versioning (e.g. "1.0.1" > "1.0.0", "1.1.0" > "1.0.9").
         */
        fun isVersionNewer(candidateVersion: String, currentVersion: String?): Boolean {
            if (currentVersion == null) return true
            val candidateParts = candidateVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(candidateParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val c = candidateParts.getOrElse(i) { 0 }
                val cur = currentParts.getOrElse(i) { 0 }
                if (c > cur) return true
                if (c < cur) return false
            }
            return false
        }
    }
}

/**
 * Metadata format representing an entry in LNReader `plugins.min.json`.
 */
@Serializable
data class LnReaderPluginRepoItem(
    val id: String,
    val name: String,
    val site: String? = null,
    val lang: String = "en",
    val version: String = "1.0.0",
    val url: String,
    val iconUrl: String? = null
)

/**
 * Top-level metadata wrapper for an extension repository index.
 */
@Serializable
data class ExtensionRepoIndex(
    val repoName: String = "Bunori Extensions",
    val version: Int = 1,
    val extensions: List<ExtensionRepoEntry> = emptyList()
)
