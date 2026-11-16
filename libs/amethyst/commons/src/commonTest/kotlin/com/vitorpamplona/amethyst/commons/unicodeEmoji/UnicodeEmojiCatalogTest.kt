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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnicodeEmojiCatalogTest {
    private val sample =
        """
        # header comment
        @Smileys & Emotion
        😀	grinning face
        😂	face with tears of joy
        @People & Body
        👋	waving hand	👋🏻 👋🏼 👋🏽 👋🏾 👋🏿
        🫶	heart hands	only two
        @Empty Group
        @Animals & Nature
        🐶	dog face
        🐕	dog
        🌭	hot dog
        """.trimIndent()

    private val catalog = UnicodeEmojiCatalog.parse(sample)

    @Test
    fun parsesGroupsInOrderAndSkipsEmptyOnes() {
        assertEquals(listOf("Smileys & Emotion", "People & Body", "Animals & Nature"), catalog.groups.map { it.name })
        assertEquals(listOf("😀", "😂"), catalog.groups[0].emojis.map { it.emoji })
        assertEquals("face with tears of joy", catalog.groups[0].emojis[1].name)
    }

    @Test
    fun appliesSkinToneOnlyWhereAllFiveVariantsExist() {
        val (wave, heartHands) = catalog.groups[1].emojis
        assertEquals("👋", wave.withTone(SkinTone.NONE))
        assertEquals("👋🏻", wave.withTone(SkinTone.LIGHT))
        assertEquals("👋🏿", wave.withTone(SkinTone.DARK))

        assertNull(heartHands.skinTones)
        assertEquals("🫶", heartHands.withTone(SkinTone.MEDIUM))
    }

    @Test
    fun searchRanksNameStartsThenWordStartsThenContains() {
        assertEquals(listOf("🐕", "🐶", "🌭"), catalog.search("dog").map { it.emoji })
        assertEquals(listOf("🐶"), catalog.search("  FACE dog ").map { it.emoji })
        assertEquals(emptyList(), catalog.search("   "))
        assertEquals(emptyList(), catalog.search("unicorn"))
    }

    @Test
    fun findsAnEntryByItsEmojiOrAToneVariant() {
        assertEquals("waving hand", catalog.find("👋🏽")?.name)
        assertEquals("dog", catalog.find("🐕")?.name)
        assertNull(catalog.find("🦄"))
    }
}
