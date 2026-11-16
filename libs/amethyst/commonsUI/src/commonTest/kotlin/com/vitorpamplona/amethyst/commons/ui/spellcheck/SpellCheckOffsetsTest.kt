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
package com.vitorpamplona.amethyst.commons.ui.spellcheck

import com.vitorpamplona.amethyst.commons.service.spellcheck.Misspelling
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpellCheckOffsetsTest {
    private val checked = CheckedText("teh cat sta dgo", listOf(Misspelling(0, 3, "teh"), Misspelling(8, 11, "sta"), Misspelling(12, 15, "dgo")))

    @Test
    fun anEditKeepsWordsBeforeItAndShiftsWordsAfterIt() {
        // "cat" -> "kitty": "teh" stays, "sta" and "dgo" move by 2.
        val followed = checked.followEdit("teh kitty sta dgo")
        assertEquals(listOf(Misspelling(0, 3, "teh"), Misspelling(10, 13, "sta"), Misspelling(14, 17, "dgo")), followed.misspellings)
    }

    @Test
    fun anEditInsideAWordDropsIt() {
        val followed = checked.followEdit("teh cat star dgo")
        assertEquals(listOf("teh", "dgo"), followed.misspellings.map { it.word })
        assertEquals(13, followed.misspellings[1].start)
    }

    @Test
    fun typingRightAfterAWordDropsIt() {
        val followed = checked.followEdit("teh cat sta dgos")
        assertEquals(listOf("teh", "sta"), followed.misspellings.map { it.word })
    }

    @Test
    fun editRegionCoversTheChange() {
        val edit = EditRegion.between("hello world", "hello brave world")
        assertEquals(6, edit.start)
        assertEquals(6, edit.oldEnd)
        assertEquals(12, edit.newEnd)
    }

    @Test
    fun offsetsShiftPastReplacedMentions() {
        // "nostr:npub1abc" (14 chars) is drawn as "@Al" (3 chars).
        val replacements = listOf(TextReplacement(3, 17, "@Al"))
        assertEquals(0, replacements.toVisual(0))
        assertEquals(7, replacements.toVisual(18))
        assertNull(replacements.toVisual(5))
    }
}
