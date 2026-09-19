package com.halovoid.bunori.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class ReaderTheme(val id: String, val displayName: String) {
    OLED("oled", "OLED Pitch Black"),
    DARK("dark", "Dark Slate")
}

@Serializable
enum class ReadingMode(val id: String, val displayName: String) {
    CONTINUOUS("CONTINUOUS", "Continuous Vertical"),
    PAGED("PAGED", "Paged Mode")
}

@Serializable
enum class ReaderTextAlign(val id: String, val cssValue: String, val displayName: String) {
    LEFT("left", "left", "Left"),
    JUSTIFY("justify", "justify", "Justified"),
    CENTER("center", "center", "Center")
}

@Serializable
data class CustomFont(
    val name: String,
    val filePath: String
) {
    fun toStorageString(): String = "$name|$filePath"

    companion object {
        fun fromStorageString(str: String): CustomFont? {
            val parts = str.split("|", limit = 2)
            if (parts.size != 2) return null
            return CustomFont(name = parts[0], filePath = parts[1])
        }
    }
}

@Serializable
data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.DARK,
    val readingMode: ReadingMode = ReadingMode.CONTINUOUS,
    val fontFamily: String = "Lora",
    val fontSizeSp: Int = 19,
    val lineHeight: Float = 1.65f,
    val letterSpacing: Float = 0.01f,
    val paragraphSpacingEm: Float = 1.25f,
    val horizontalPaddingDp: Int = 18,
    val textAlign: ReaderTextAlign = ReaderTextAlign.LEFT,
    val volumeKeyPageTurn: Boolean = false,
    val keepScreenAwake: Boolean = false,
    val dimImagesInDarkMode: Boolean = true,
    val customCss: String = "",
    val customJs: String = ""
)

/**
 * Encapsulates the chapter payload passed to the WebView runtime.
 */
@Serializable
data class ChapterPayload(
    val id: Int,
    val title: String,
    val index: Int,
    val htmlContent: String,
    val scanlator: String = ""
)

