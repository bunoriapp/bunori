package com.halovoid.bunori.extension.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ExtensionFormat {
    @SerialName("bext")
    BEXT_WASM,

    @SerialName("lnreader")
    LNREADER_JS,

    @SerialName("custom_js")
    CUSTOM_JS;

    companion object {
        fun fromString(value: String?): ExtensionFormat {
            if (value.isNullOrBlank()) return BEXT_WASM
            val clean = value.lowercase().trim()
            return when {
                clean == "lnreader" || clean == "lnreader_js" || clean.contains("lnreader") -> LNREADER_JS
                clean == "bext" || clean == "bext_wasm" || clean.contains("bext") -> BEXT_WASM
                clean == "custom_js" -> CUSTOM_JS
                else -> BEXT_WASM
            }
        }
    }
}
