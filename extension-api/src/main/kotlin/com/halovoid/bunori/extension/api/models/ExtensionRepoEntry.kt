package com.halovoid.bunori.extension.api.models

import com.halovoid.bunori.extension.api.ExtensionJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
@Serializable
data class ExtensionRepoEntry(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val apiVersion: Int = 1,
    val lang: String = "en",
    val baseUrl: String = "",
    val format: ExtensionFormat = ExtensionFormat.BEXT_WASM,
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
        format = format,
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
        fun parseIndex(jsonString: String): List<ExtensionRepoEntry> {
            val trimmed = jsonString.trim()
            if (trimmed.isEmpty()) return emptyList()

            if (trimmed.startsWith("{")) {
                return try {
                    ExtensionJson.json.decodeFromString<ExtensionRepoIndex>(trimmed).extensions.map { entry ->
                        val rawId = entry.id.removePrefix("bext.").removePrefix("lnreader.")
                        entry.copy(id = rawId)
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            return try {
                val root = ExtensionJson.json.parseToJsonElement(trimmed)
                val isLnReader = (root as? JsonArray)?.any { elem ->
                    val obj = elem as? JsonObject ?: return@any false
                    obj.containsKey("site") || obj["url"]?.jsonPrimitive?.contentOrNull?.endsWith(".js") == true
                } == true

                if (isLnReader) {
                    ExtensionJson.json.decodeFromString<List<LnReaderPluginRepoItem>>(trimmed).map { it.toRepoEntry() }
                } else {
                    ExtensionJson.json.decodeFromString<List<ExtensionRepoEntry>>(trimmed).map { entry ->
                        val rawId = entry.id.removePrefix("bext.").removePrefix("lnreader.")
                        entry.copy(id = rawId)
                    }
                }
            } catch (_: Exception) {
                runCatching {
                    ExtensionJson.json.decodeFromString<List<LnReaderPluginRepoItem>>(trimmed).map { it.toRepoEntry() }
                }.getOrElse {
                    runCatching {
                        ExtensionJson.json.decodeFromString<List<ExtensionRepoEntry>>(trimmed).map { entry ->
                            val rawId = entry.id.removePrefix("bext.").removePrefix("lnreader.")
                            entry.copy(id = rawId)
                        }
                    }.getOrDefault(emptyList())
                }
            }
        }
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
@Serializable
data class LnReaderPluginRepoItem(
    val id: String,
    val name: String,
    val site: String? = null,
    val lang: String = "en",
    val version: String = "1.0.0",
    val url: String,
    val iconUrl: String? = null
) {
    fun toRepoEntry(): ExtensionRepoEntry {
        val rawId = id.removePrefix("lnreader.").removePrefix("bext.")
        return ExtensionRepoEntry(
            id = rawId,
            name = name,
            version = version,
            lang = lang,
            baseUrl = site.orEmpty(),
            url = url,
            bextUrl = url,
            iconUrl = iconUrl,
            format = ExtensionFormat.LNREADER_JS
        )
    }
}
@Serializable
data class ExtensionRepoIndex(
    val repoName: String = "Extensions",
    val version: Int = 1,
    val extensions: List<ExtensionRepoEntry> = emptyList()
)
