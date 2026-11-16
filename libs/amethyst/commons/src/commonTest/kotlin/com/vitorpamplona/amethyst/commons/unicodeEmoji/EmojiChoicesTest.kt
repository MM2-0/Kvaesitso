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
package com.vitorpamplona.amethyst.commons.unicodeEmoji

import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmojiChoicesTest {
    private val keycap = "1\uFE0F\u20E3"
    private val england = "🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F"
    private val heart = "❤\uFE0F"
    private val thumbs = UnicodeEmoji("👍", "thumbs up", listOf("👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"))
    private val catalog =
        UnicodeEmojiCatalog(
            listOf(
                UnicodeEmojiGroup("People & Body", listOf(thumbs)),
                UnicodeEmojiGroup("Symbols", listOf(UnicodeEmoji(heart, "red heart"), UnicodeEmoji(keycap, "keycap: 1"))),
                UnicodeEmojiGroup("Flags", listOf(UnicodeEmoji(england, "flag: England"))),
            ),
        )

    @Test
    fun aTapAddsThenRemoves() {
        val added = listOf("+", "🚀").toggleEmoji("🔥")
        assertEquals(listOf("+", "🚀", "🔥"), added)
        assertEquals("🔥", EmojiChoices.of(added).savedForm("🔥"))
        assertEquals(listOf("+", "🚀"), added.toggleEmoji("🔥"))
        assertEquals(listOf("🚀"), listOf("🚀").toggleEmoji("  "))
    }

    @Test
    fun aTapRemovesEveryCopyOfADuplicatedChoice() {
        // Older versions of the screen and other clients could save the same emoji twice.
        assertEquals(listOf("🚀"), listOf("🔥", "🚀", "🔥").toggleEmoji("🔥"))
    }

    @Test
    fun aTypedEmojiMatchesAChoiceSavedInAnotherTone() {
        val choices = EmojiChoices.of(listOf("👍🏽"))
        assertEquals("👍🏽", choices.savedForm("👍", catalog.find("👍")))
        assertNull(choices.savedForm("👎", null))
    }

    @Test
    fun multiCharacterEmojisAreSavedWhole() {
        assertEquals(listOf(keycap, england), emptyList<String>().toggleEmoji(keycap).toggleEmoji(england))
    }

    @Test
    fun aChoiceSavedInAnotherToneIsFoundAndRemoved() {
        val choices = EmojiChoices.of(listOf("👍"))
        assertEquals("👍", choices.savedForm(thumbs, SkinTone.MEDIUM))

        val toned = EmojiChoices.of(listOf("👍🏿"))
        assertEquals("👍🏿", toned.savedForm(thumbs, SkinTone.NONE))
        assertNull(EmojiChoices.of(emptyList()).savedForm(thumbs, SkinTone.MEDIUM))
    }

    @Test
    fun theCurrentToneWinsWhenSeveralAreSaved() {
        assertEquals("👍🏽", EmojiChoices.of(listOf("👍", "👍🏽")).savedForm(thumbs, SkinTone.MEDIUM))
    }

    @Test
    fun aChoiceSavedWithoutTheVariationSelectorMatches() {
        assertEquals("❤", EmojiChoices.of(listOf("❤")).savedForm(heart))
    }

    @Test
    fun customEmojisMatchByImageOrByBareCode() {
        val sameImage = EmojiMedia("newcode", "https://x.com/a.png")
        val saved = listOf(":oldcode:https://x.com/a.png")
        assertTrue(EmojiChoices.of(saved).contains(sameImage))
        assertEquals(emptyList(), saved.toggleEmoji(sameImage))

        val party = EmojiMedia("party", "https://x.com/p.png")
        val bare = listOf(":party:", "🚀")
        assertTrue(EmojiChoices.of(bare).contains(party))
        assertEquals(listOf("🚀"), bare.toggleEmoji(party))

        assertFalse(EmojiChoices.of(emptyList()).contains(party))
        assertEquals(listOf(":party:https://x.com/p.png"), emptyList<String>().toggleEmoji(party))
    }

    @Test
    fun aBareShortcodeIsNotAUnicodeChoice() {
        assertNull(EmojiChoices.of(listOf(":party:")).savedForm(":party:"))
    }

    @Test
    fun typedTextIsAnEmojiOnlyWhenItIsNotAName() {
        assertEquals("👍🏽", typedEmoji("👍🏽 and more", catalog))
        assertEquals(keycap, typedEmoji(keycap, catalog))
        assertEquals(england, typedEmoji(england, catalog))
        assertEquals("+", typedEmoji("+", catalog))
        assertNull(typedEmoji("fire", catalog))
        assertNull(typedEmoji("1", catalog))
        assertNull(typedEmoji(":party", catalog))
    }
}
