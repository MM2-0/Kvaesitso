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

import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.UnicodeEmojiPalette
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reads the bundled palette through Compose Resources. In `jvmTest` for the reason
 * `NappletShellResourceTest` gives: the Android reader needs a `Context` a host test lacks.
 */
class UnicodeEmojiPaletteResourceTest {
    @Test
    fun bundledPaletteParsesIntoTheNineUnicodeGroups() =
        runTest {
            val catalog = UnicodeEmojiPalette.load()

            assertEquals(
                listOf(
                    "Smileys & Emotion",
                    "People & Body",
                    "Animals & Nature",
                    "Food & Drink",
                    "Travel & Places",
                    "Activities",
                    "Objects",
                    "Symbols",
                    "Flags",
                ),
                catalog.groups.map { it.name },
            )

            val all = catalog.groups.flatMap { it.emojis }
            assertTrue(all.size > 1500, "only ${all.size} emoji")
            assertEquals(all.size, all.map { it.emoji }.toSet().size, "the grid keys on the emoji, so each must be unique")
            assertTrue(all.none { it.name.contains("skin tone") }, "tone variants fold into their base emoji")

            val wave = all.first { it.name == "waving hand" }
            assertEquals("👋🏽", wave.withTone(SkinTone.MEDIUM))
            assertEquals("😂", catalog.search("tears of joy").first().emoji)
        }
}
