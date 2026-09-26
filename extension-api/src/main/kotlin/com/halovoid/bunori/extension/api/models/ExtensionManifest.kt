package com.halovoid.bunori.extension.api.models

import kotlinx.serialization.Serializable

/**
 * Metadata specification stored in manifest.json inside a .bext archive.
 *
 * @property id Unique identifier of the extension (e.g. "novelbins").
 * @property name Display name of the extension source.
 * @property version Monotonically increasing release version of this specific extension.
 * @property apiVersion Target Bunori Extension API contract version (currently 1).
 * @property lang Supported language code (e.g. "en", "es").
 * @property baseUrl Canonical base URL of the website.
 * @property entryClass Fully-qualified class name implementing [com.halovoid.bunori.extension.api.IExtension].
 * @property iconPath Optional path to the icon asset inside the .bext package (e.g. "assets/icon.png").
 */
@Serializable
data class ExtensionManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val apiVersion: Int = 1,
    val lang: String = "en",
    val baseUrl: String,
    val format: ExtensionFormat = ExtensionFormat.BEXT_WASM,
    val authors: List<String> = emptyList(),
    val isDeprecated: Boolean = false,
    val deprecationReason: String? = null,
    val suggestedAlternative: String? = null,
    val latestChangelog: String? = null,
    val entryClass: String? = null,
    val iconPath: String? = null,
    val iconUrl: String? = null,
    val webviewNeeded: Boolean = false,
    val runnerConcurrency: Int = 3,
    val runnerCooldown: Long = 1000L,
    val maxAttempts: Int = 3
) {
    fun toMetadata(iconUrl: String? = null): ExtensionMetadata {
        return ExtensionMetadata(
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
            iconUrl = iconUrl ?: this.iconUrl,
            webviewNeeded = webviewNeeded,
            runnerConcurrency = runnerConcurrency,
            runnerCooldown = runnerCooldown,
            maxAttempts = maxAttempts
        )
    }
}
