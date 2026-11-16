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
package com.vitorpamplona.amethyst.service.resourceusage

import com.vitorpamplona.amethyst.commons.service.resourceusage.RefCountedSession
import org.junit.Assert.assertEquals
import org.junit.Test

class RefCountedSessionTest {
    @Test
    fun overlappingHoldersKeepTheSessionOpenAndReportOnlyTransitions() {
        val calls = mutableListOf<Boolean>()
        val session = RefCountedSession { calls.add(it) }

        session.setActive(true) // holders 1 — inactive -> active
        session.setActive(true) // holders 2 — a second listener joins, no transition
        session.setActive(false) // holders 1 — the first one leaves, still active

        assertEquals("only the 0 -> 1 edge is a transition", listOf(true), calls)

        session.setActive(false) // holders 0 — the last one leaves

        assertEquals(listOf(true, false), calls)
    }

    @Test
    fun unmatchedReleaseDoesNotDriveTheCountNegative() {
        val calls = mutableListOf<Boolean>()
        val session = RefCountedSession { calls.add(it) }

        session.setActive(false)
        session.setActive(false)

        assertEquals("releasing an idle session is a no-op", emptyList<Boolean>(), calls)

        // If the count had gone to -2, one acquire would leave it at -1 and
        // report inactive. It must open the session instead.
        session.setActive(true)

        assertEquals(listOf(true), calls)
    }

    @Test
    fun aSingleHolderOpensAndClosesTheSession() {
        val calls = mutableListOf<Boolean>()
        val session = RefCountedSession { calls.add(it) }

        session.setActive(true)
        session.setActive(false)

        assertEquals(listOf(true, false), calls)
    }
}
