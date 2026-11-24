package com.halovoid.bunori.data.artifact.generators

import org.junit.Assert.assertEquals
import org.junit.Test

class BookLanguageTest {

    @Test
    fun arabicTextIsArabicRightToLeft() {
        val sample = "تنهد صني ثم نظر إلى السماء المظلمة وقال إن الطريق ما زال طويلاً."
        assertEquals(BookLanguage("ar", isRtl = true), detectBookLanguage(sample))
    }

    @Test
    fun markupDoesNotOutweighArabicText() {
        val paragraph = """<p style="margin: 0 0 0.9em 0; text-indent: 0;">هز رأسه.</p>"""
        val sample = """<div dir="rtl" lang="ar" style="direction: rtl;">""" +
            paragraph.repeat(20) + "</div>"
        assertEquals(BookLanguage("ar", isRtl = true), detectBookLanguage(sample))
    }

    @Test
    fun arabicWithAForeignNameStaysArabic() {
        val sample = "وصل بيتر إلى المدينة في الصباح الباكر، وكان اسمه الكامل بيتر گراي، " +
            "ثم جلس في المقهى ينتظر صديقه القديم ويتذكر أيام الدراسة."
        assertEquals(BookLanguage("ar", isRtl = true), detectBookLanguage(sample))
    }

    @Test
    fun persianTextIsPersian() {
        val sample = "این یک کتاب فارسی است و من آن را خیلی دوست دارم. گربه در خانه بازی می‌کند."
        assertEquals(BookLanguage("fa", isRtl = true), detectBookLanguage(sample))
    }

    @Test
    fun urduTextIsUrdu() {
        val sample = "یہ ایک اردو کتاب ہے اور مجھے یہ بہت پسند ہے۔ بچے باہر کھیل رہے ہیں۔"
        assertEquals(BookLanguage("ur", isRtl = true), detectBookLanguage(sample))
    }

    @Test
    fun hebrewTextIsHebrew() {
        val sample = "זהו ספר בעברית ואני אוהב לקרוא אותו בערב."
        assertEquals(BookLanguage("he", isRtl = true), detectBookLanguage(sample))
    }

    @Test
    fun englishTextKeepsDefault() {
        val sample = "<p>Sunny sighed and looked at the dark sky.</p>"
        assertEquals(BookLanguage.DEFAULT, detectBookLanguage(sample))
    }

    @Test
    fun englishWithAFewArabicWordsKeepsDefault() {
        val sample = "The sign above the door read مرحبا, which means welcome in Arabic."
        assertEquals(BookLanguage.DEFAULT, detectBookLanguage(sample))
    }

    @Test
    fun emptySampleKeepsDefault() {
        assertEquals(BookLanguage.DEFAULT, detectBookLanguage(""))
    }
}
