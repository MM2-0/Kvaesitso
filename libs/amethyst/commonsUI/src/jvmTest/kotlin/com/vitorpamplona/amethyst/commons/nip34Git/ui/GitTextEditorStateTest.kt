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
package com.vitorpamplona.amethyst.commons.nip34Git.ui

import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitTextEditorStateTest {
    @Test
    fun anUntouchedFileComesBackByteForByte() {
        listOf("a\nb\n", "a\nb", "a\r\nb\r\n", "a\r\n\tb", "", "\n", "single").forEach { original ->
            val state = GitTextEditorState(original)
            state.open(0)
            state.commit()
            assertEquals(original, state.text(), "round trip of ${original.replace("\r", "\\r").replace("\n", "\\n")}")
            assertFalse(state.isChanged)
        }
    }

    @Test
    fun anEditSplicesItsBlockBackAndKeepsLineEndings() {
        val original = (1..100).joinToString("\r\n") { "line $it" } + "\r\n"
        val state = GitTextEditorState(original)
        state.open(50)
        // The block holds lines 45..65 (5 before, 15 after).
        assertEquals(45..65, state.block)
        state.blockField!!.edit { insert(0, "new line\n") }
        assertTrue(state.isChanged)
        val text = state.text()
        val lines = text.split("\r\n")
        assertEquals("new line", lines[45])
        assertEquals("line 46", lines[46])
        assertEquals(102, lines.size, "one more line, and the final newline kept")
        assertTrue(text.endsWith("line 100\r\n"))
        assertFalse(text.contains(Regex("[^\r]\n")), "every line break is CRLF")
    }

    @Test
    fun openingAnotherBlockKeepsTheFirstEdit() {
        val state = GitTextEditorState((1..60).joinToString("\n") { "l$it" })
        state.open(0)
        state.blockField!!.setTextAndPlaceCursorAtEnd(
            state.blockField!!
                .text
                .toString()
                .replace("l1\n", "first\n"),
        )
        state.open(50)
        assertEquals("first", state.lines[0])
        assertTrue(state.text().startsWith("first\nl2"))
    }

    @Test
    fun aLineBelowABlockThatChangedSizeOpensWhereItNowIs() {
        val state = GitTextEditorState((0 until 100).joinToString("\n") { "l$it" })
        state.open(0)
        // The block holds lines 0..15; take out lines 1..15, keeping only the first.
        state.blockField!!.setTextAndPlaceCursorAtEnd("l0")
        // Line 99 as it was drawn below the block: after the commit it is line 84.
        state.open(99)
        assertEquals(85, state.lines.size)
        assertTrue(84 in state.block!!, "the tapped line is in the block: ${state.block}")
        assertTrue(
            state.blockField!!
                .text
                .toString()
                .endsWith("l99"),
        )
    }
}
