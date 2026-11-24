package com.halovoid.bunori.data.artifact.generators

/**
 * Language and text direction written into an exported EPUB. Readers use the
 * language for fonts and hyphenation, and the direction to lay out text and
 * turn pages right to left.
 */
internal data class BookLanguage(val code: String, val isRtl: Boolean) {
    companion object {
        val DEFAULT = BookLanguage("en", isRtl = false)
    }
}

private val htmlTagRegex = Regex("<[^>]*>")

/**
 * Guesses the book's language from a sample of its text (plain text or HTML).
 * Only right-to-left scripts are detected (Arabic, Persian, Urdu, Hebrew);
 * anything else keeps the previous default, English left-to-right.
 */
internal fun detectBookLanguage(sample: String): BookLanguage {
    var letters = 0
    var arabicScript = 0
    var hebrew = 0
    var urduLetters = 0
    var persianLetters = 0

    for (ch in sample.replace(htmlTagRegex, " ")) {
        if (!ch.isLetter()) continue
        letters++
        when (ch) {
            in '֐'..'׿', in 'יִ'..'ﭏ' -> hebrew++
            in '؀'..'ۿ', in 'ݐ'..'ݿ', in 'ࢠ'..'ࣿ',
            in 'ﭐ'..'﷿', in 'ﹰ'..'﻿' -> {
                arabicScript++
                when (ch) {
                    // ٹ ڈ ڑ ں ھ ہ ے
                    'ٹ', 'ڈ', 'ڑ', 'ں', 'ھ', 'ہ', 'ے' -> urduLetters++
                    // پ چ ژ ک گ ی
                    'پ', 'چ', 'ژ', 'ک', 'گ', 'ی' -> persianLetters++
                }
            }
        }
    }

    return when {
        letters == 0 -> BookLanguage.DEFAULT
        hebrew * 2 > letters -> BookLanguage("he", isRtl = true)
        arabicScript * 2 > letters -> {
            // Urdu also uses the Persian letters, so it is checked first. Arabic text
            // only has these letters in the odd foreign name.
            val code = when {
                urduLetters * 50 > arabicScript -> "ur"
                persianLetters * 20 > arabicScript -> "fa"
                else -> "ar"
            }
            BookLanguage(code, isRtl = true)
        }
        else -> BookLanguage.DEFAULT
    }
}
