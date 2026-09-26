package com.halovoid.bunori.extension.api.models

import kotlinx.serialization.Serializable

@Serializable
data class ExtensionRepo(
    val name: String,
    val url: String,
    val enabled: Boolean = true
) {
    val stableKey: String
        get() = generateStableKey(url)

    val slug: String
        get() = stableKey

    companion object {
        fun generateStableKey(url: String): String {
            val clean = url.trim().lowercase().removeSuffix("/")

            val md5 = try {
                java.security.MessageDigest.getInstance("MD5")
                    .digest(clean.toByteArray(Charsets.UTF_8))
                    .take(4)
                    .joinToString("") { "%02x".format(it) }
            } catch (_: Exception) {
                clean.hashCode().toUInt().toString(16).take(8)
            }

            val rawHost = try {
                java.net.URI(clean).host?.removePrefix("www.") ?: "repo"
            } catch (_: Exception) {
                "repo"
            }

            val host = when {
                clean.contains("bunori") -> "bunori"
                clean.contains("lnreader") -> "lnreader"
                else -> rawHost.substringBefore('.').filter { it.isLetterOrDigit() }.take(8)
            }.ifBlank { "repo" }

            return "${host}_$md5"
        }
    }
}
