package com.halovoid.bunori.extension.api.models

import kotlinx.serialization.Serializable

/**
 * Represents a configured extension repository source.
 *
 * @property name User-facing unique repository name (e.g. "bext", "lnreader", "custom").
 * @property url Remote repository index endpoint URL (e.g. index.min.json or plugins.min.json).
 * @property enabled Whether this repository is currently enabled for catalog discovery and updates.
 */
@Serializable
data class ExtensionRepo(
    val name: String,
    val url: String,
    val enabled: Boolean = true
) {
    /**
     * Stable, immutable repository identifier derived deterministically from the URL.
     * This key never changes even if the user updates the repo's display name.
     */
    val stableKey: String
        get() = generateStableKey(url)

    /**
     * Normalized slug for backward compatibility.
     */
    val slug: String
        get() = stableKey

    companion object {
        fun generateStableKey(url: String): String {
            val clean = url.trim().lowercase().removeSuffix("/")
            if (clean.contains("bunori-extensions") || clean.endsWith(".bext")) return "bext"
            if (clean.contains("lnreader-plugins") || clean.endsWith(".js")) return "lnreader"

            val md5 = try {
                java.security.MessageDigest.getInstance("MD5")
                    .digest(clean.toByteArray(Charsets.UTF_8))
                    .take(4)
                    .joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                clean.hashCode().toUInt().toString(16)
            }

            val host = try {
                java.net.URI(clean).host?.substringBefore('.')?.filter { it.isLetterOrDigit() }?.take(6) ?: "repo"
            } catch (_: Exception) {
                "repo"
            }

            return "${host.ifBlank { "repo" }}_$md5"
        }
    }
}
