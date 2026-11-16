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
package com.vitorpamplona.amethyst.commons.napplet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NappletHeldRequestsTest {
    private var now = 0L
    private val held = NappletHeldRequests<String>(clock = { now }, cap = 3, maxAgeMs = 1_000)

    @Test
    fun heldRequestsAreSentWhenTheUserIsBack() {
        assertNull(held.hold("a"))
        assertNull(held.hold("b"))
        val drained = held.drain()
        assertEquals(listOf("a", "b"), drained.send)
        assertTrue(drained.fail.isEmpty())
        assertEquals(0, held.size)
    }

    @Test
    fun pastTheCapANewRequestIsHandedBack() {
        held.hold("a")
        held.hold("b")
        held.hold("c")
        assertEquals("d", held.hold("d"))
        assertEquals(3, held.size)
    }

    @Test
    fun staleRequestsAreFailedNotSent() {
        held.hold("old")
        now = 600
        held.hold("new")
        now = 1_200
        val drained = held.drain()
        assertEquals(listOf("new"), drained.send)
        assertEquals(listOf("old"), drained.fail)
    }

    @Test
    fun expireRemovesOnlyTheStaleOnes() {
        held.hold("old")
        now = 600
        held.hold("new")
        now = 1_000
        assertEquals(listOf("old"), held.expire())
        assertEquals(1, held.size)
        now = 1_600
        assertEquals(listOf("new"), held.expire())
        assertEquals(0, held.size)
    }

    @Test
    fun clearReturnsEverything() {
        held.hold("a")
        held.hold("b")
        assertEquals(listOf("a", "b"), held.clear())
        assertEquals(0, held.size)
    }
}
