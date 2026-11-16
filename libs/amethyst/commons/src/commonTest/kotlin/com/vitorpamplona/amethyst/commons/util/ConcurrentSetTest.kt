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
package com.vitorpamplona.amethyst.commons.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConcurrentSetTest {
    @Test
    fun `add returns true only the first time`() {
        val set = ConcurrentSet<String>()
        assertTrue(set.add("a"))
        assertFalse(set.add("a"))
        assertEquals(1, set.size)
    }

    @Test
    fun `contains reflects membership`() {
        val set = ConcurrentSet<String>()
        assertFalse(set.contains("a"))
        set.add("a")
        assertTrue(set.contains("a"))
    }

    @Test
    fun `remove returns true only when present`() {
        val set = ConcurrentSet<String>()
        set.add("a")
        assertTrue(set.remove("a"))
        assertFalse(set.remove("a"))
        assertFalse(set.contains("a"))
    }

    @Test
    fun `clear empties the set`() {
        val set = ConcurrentSet<String>()
        set.add("a")
        set.add("b")
        set.clear()
        assertEquals(0, set.size)
        assertFalse(set.contains("a"))
    }

    @Test
    fun `snapshot is a copy that later writes do not touch`() {
        val set = ConcurrentSet<String>()
        assertTrue(set.isEmpty())
        set.add("a")
        val copy = set.snapshot()
        set.add("b")
        set.remove("a")
        assertEquals(setOf("a"), copy)
        assertTrue("b" in set)
        assertFalse(set.isEmpty())
    }
}
