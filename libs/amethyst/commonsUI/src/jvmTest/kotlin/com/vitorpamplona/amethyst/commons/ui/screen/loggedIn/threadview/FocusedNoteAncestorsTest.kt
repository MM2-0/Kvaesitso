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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.threadview

import com.vitorpamplona.amethyst.commons.model.Note
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FocusedNoteAncestorsTest {
    private fun note(n: Int) = Note(n.toString(16).padStart(64, '0'))

    @Test
    fun keepsTheFocusedNoteAndEveryReplyAboveIt() {
        // Depth-first: root, A (1), A1 (2), A1a (3), B (1), B1 (2) — focus A1a.
        val root = note(0)
        val a = note(1)
        val a1 = note(2)
        val a1a = note(3)
        val b = note(4)
        val b1 = note(5)
        val thread = listOf(root, a, a1, a1a, b, b1)
        val levels = mapOf(root to 0, a to 1, a1 to 2, a1a to 3, b to 1, b1 to 2)

        assertEquals(setOf(a1a.idHex, a1.idHex, a.idHex, root.idHex), focusedNoteAndAncestors(thread, levels, a1a.idHex))
        // A sibling branch is not an ancestor.
        assertEquals(setOf(b1.idHex, b.idHex, root.idHex), focusedNoteAndAncestors(thread, levels, b1.idHex))
    }

    @Test
    fun knownReplyBelowLooksOnlyInsideEachSubtree() {
        // Depth-first: root, A (1), A1 (2), A1a (3), B (1), B1 (2), C (1). Only A1a and C are known.
        val root = note(0)
        val a = note(1)
        val a1 = note(2)
        val a1a = note(3)
        val b = note(4)
        val b1 = note(5)
        val c = note(6)
        val thread = listOf(root, a, a1, a1a, b, b1, c)
        val levels = mapOf(root to 0, a to 1, a1 to 2, a1a to 3, b to 1, b1 to 2, c to 1)
        val known = setOf(a1a, c)

        val below = knownReplyBelow(thread, levels) { it in known }
        assertTrue(below[0]) // root
        assertTrue(below[1]) // A: A1a is two levels down
        assertTrue(below[2]) // A1
        assertFalse(below[3]) // A1a: a leaf, itself known
        assertFalse(below[4]) // B: C is a sibling, not a reply
        assertFalse(below[5]) // B1
        assertFalse(below[6]) // C
    }
}
