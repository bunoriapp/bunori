package com.halovoid.bunori.extension.api.models

import kotlinx.serialization.Serializable

/**
 * Metadata defining a Bunori extension.
 *
 * @property id Unique lowercase identifier string (e.g. "novelfull", "novelbins").
 * @property name User-facing title of the source.
 * @property version Monotonically increasing version number for this specific source script.
 * @property apiVersion Target Bunori Extension API contract version (currently 1).
 * @property lang Language code supported by the source (e.g. "en", "es").
 * @property baseUrl The canonical base URL for the site.
 * @property iconUrl Optional URL for the source icon.
 */
@Serializable
data class ExtensionMetadata(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val apiVersion: Int = 1,
    val lang: String = "en",
    val baseUrl: String,
    val authors: List<String> = emptyList(),
    val isDeprecated: Boolean = false,
    val deprecationReason: String? = null,
    val suggestedAlternative: String? = null,
    val iconUrl: String? = null,
    val webviewNeeded: Boolean = false,
    val runnerConcurrency: Int = 3,
    val runnerCooldown: Long = 1000L,
    val maxAttempts: Int = 3
)
