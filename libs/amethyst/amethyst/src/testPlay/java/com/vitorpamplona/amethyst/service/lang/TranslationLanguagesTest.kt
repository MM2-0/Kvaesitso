/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationLanguagesTest {
    @Test
    fun `old and new ISO codes map to the same translator code`() {
        assertEquals("he", TranslationLanguages.toTranslatorCode("iw"))
        assertEquals("he", TranslationLanguages.toTranslatorCode("he"))
        assertEquals("id", TranslationLanguages.toTranslatorCode("in"))
        assertEquals("id", TranslationLanguages.toTranslatorCode("id"))
        assertEquals("no", TranslationLanguages.toTranslatorCode("nb"))
        assertEquals("no", TranslationLanguages.toTranslatorCode("no"))
        assertEquals("tl", TranslationLanguages.toTranslatorCode("fil"))
    }

    @Test
    fun `tags without a model have no translator code`() {
        assertNull(TranslationLanguages.toTranslatorCode("und"))
        assertNull(TranslationLanguages.toTranslatorCode("zh-Latn"))
        assertNull(TranslationLanguages.toTranslatorCode("ja-Latn"))
        assertNull(TranslationLanguages.toTranslatorCode("hmn"))
    }

    @Test
    fun `a blocked language is skipped whichever code spells it`() {
        // Language identification says `iw`; the settings picker stored `he`.
        assertFalse(TranslationLanguages.shouldTranslate("iw", setOf("he"), "en"))
        assertFalse(TranslationLanguages.shouldTranslate("id", setOf("in"), "en"))
        // A Norwegian Bokmål device puts `nb` on the list by default.
        assertFalse(TranslationLanguages.shouldTranslate("no", setOf("nb"), "en"))
        assertFalse(TranslationLanguages.shouldTranslate("fil", setOf("tl"), "en"))
    }

    @Test
    fun `text already in the target language is skipped whichever code spells it`() {
        assertFalse(TranslationLanguages.shouldTranslate("iw", emptySet(), "he"))
        assertFalse(TranslationLanguages.shouldTranslate("id", emptySet(), "in"))
        assertFalse(TranslationLanguages.shouldTranslate("no", emptySet(), "nb"))
        assertFalse(TranslationLanguages.shouldTranslate("en", emptySet(), "EN"))
    }

    @Test
    fun `untranslatable detections are skipped`() {
        assertFalse(TranslationLanguages.shouldTranslate("und", emptySet(), "en"))
        assertFalse(TranslationLanguages.shouldTranslate("zh-Latn", emptySet(), "en"))
    }

    @Test
    fun `other languages are translated`() {
        assertTrue(TranslationLanguages.shouldTranslate("iw", setOf("pt", "nb"), "en"))
        assertTrue(TranslationLanguages.shouldTranslate("no", setOf("he"), "en"))
        assertTrue(TranslationLanguages.shouldTranslate("fil", emptySet(), "en"))
        assertTrue(TranslationLanguages.shouldTranslate("de", setOf("en"), "nb"))
    }

    @Test
    fun `a language list blocks a code in any spelling`() {
        assertTrue(TranslationLanguages.blocks(setOf("iw"), "he"))
        assertTrue(TranslationLanguages.blocks(setOf("nb"), "no"))
        assertFalse(TranslationLanguages.blocks(setOf("und", "zh-Latn"), "zh"))
        assertFalse(TranslationLanguages.blocks(emptySet(), "en"))
    }

    @Test
    fun `repeated lookups return the same codes`() {
        repeat(3) {
            assertEquals("he", TranslationLanguages.toTranslatorCode("iw"))
            assertNull(TranslationLanguages.toTranslatorCode("und"))
        }
    }
}
