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
package com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis

import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmojiImportTest {
    @Test
    fun shortcodeFromFileName() {
        assertEquals("party_parrot", EmojiImport.shortcodeFromFileName("party parrot.gif"))
        assertEquals("blobcat-heart", EmojiImport.shortcodeFromFileName("emojis/blobcat-heart.png"))
        assertEquals("caf", EmojiImport.shortcodeFromFileName("C:\\packs\\café.webp"))
        assertEquals("a_b", EmojiImport.shortcodeFromFileName("a...b.png"))
        assertEquals("noext", EmojiImport.shortcodeFromFileName("noext"))
        assertNull(EmojiImport.shortcodeFromFileName("日本.png"))
        assertNull(EmojiImport.shortcodeFromFileName(".png"))
    }

    @Test
    fun assignShortcodesNumbersRepeats() {
        assertEquals(
            listOf("smile", "smile_2", null, "smile_3", "wink"),
            EmojiImport.assignShortcodes(listOf("smile.png", "smile.gif", "😀.png", "dir/smile.webp", "wink.png")),
        )
    }

    @Test
    fun imageDetection() {
        assertTrue(EmojiImport.isImageFileName("A.PNG"))
        assertTrue(EmojiImport.isImageFileName("folder/x.gif"))
        assertFalse(EmojiImport.isImageFileName("readme.txt"))
        assertTrue(EmojiImport.isZipFileName("pack.zip"))
        assertEquals("image/jpeg", EmojiImport.imageMimeTypeOf("x.JPG"))
        assertEquals("image/svg+xml", EmojiImport.imageMimeTypeOf("x.svg"))
        assertNull(EmojiImport.imageMimeTypeOf("x.zip"))
    }

    @Test
    fun parseListAcceptsCommonForms() {
        val parsed =
            EmojiImport.parseList(
                """
                # my pack
                a https://e.com/a.png
                :b: https://e.com/b.png
                c,https://e.com/c.png
                d	https://e.com/d.png

                bad code https://e.com/x.png
                e not-a-url
                """.trimIndent(),
            )
        assertEquals(
            listOf("a", "b", "c", "d").map { EmojiUrlTag(it, "https://e.com/$it.png") },
            parsed.emojis,
        )
        assertEquals(listOf(7, 8), parsed.badLines)
    }

    @Test
    fun planSkipsExistingUnlessReplacing() {
        val names = listOf("smile.png", "wave.png", "日本.png", "smile.gif")
        val plan = EmojiImport.plan(names, existingCodes = setOf("wave"))
        assertEquals(listOf(0 to "smile", 3 to "smile_2"), plan.toUpload)
        assertEquals(listOf("wave"), plan.skipped)
        assertEquals(listOf("日本.png"), plan.unnamed)

        val replacing = EmojiImport.plan(names, existingCodes = setOf("wave"), replace = true)
        assertEquals(listOf(0 to "smile", 1 to "wave", 3 to "smile_2"), replacing.toUpload)
        assertTrue(replacing.skipped.isEmpty())
    }
}
