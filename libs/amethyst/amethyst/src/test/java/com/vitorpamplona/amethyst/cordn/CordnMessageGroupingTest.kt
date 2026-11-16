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
package com.vitorpamplona.amethyst.cordn

import com.vitorpamplona.amethyst.commons.chats.ui.ChatGroupPosition
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.cordnGroupPositionFor
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.sameDayAs
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Where a cordn bubble sits in a run of its sender's messages.
 *
 * The feed is reverse-laid-out, so the newer/older argument order is easy to invert and
 * impossible to notice from the code alone — an inverted version still produces
 * plausible-looking bubbles, just with the tail on the wrong end of every burst. These
 * assert the ends of a run by name.
 */
class CordnMessageGroupingTest {
    private val alice: HexKey = "aa".repeat(32)
    private val bob: HexKey = "bb".repeat(32)

    /** Local noon today, so a ±10 minute window never crosses midnight by accident. */
    private val noon =
        LocalDate
            .now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .plusHours(12)
            .toEpochSecond()

    private var cursor = 0L

    private fun msg(
        author: HexKey,
        at: Long,
    ) = CordnDeliveredMessage(
        envelope = CordnEnvelope.build(author, at, 9, emptyArray(), "hi"),
        cursor = ++cursor,
    )

    @Test
    fun `a lone message is SINGLE`() {
        assertEquals(ChatGroupPosition.SINGLE, cordnGroupPositionFor(null, msg(alice, noon), null))
    }

    @Test
    fun `a run of three from one sender is TOP MIDDLE BOTTOM oldest first`() {
        val oldest = msg(alice, noon)
        val middle = msg(alice, noon + 60)
        val newest = msg(alice, noon + 120)

        // The oldest of a run renders at the visual top of the burst and carries the
        // author line; the newest carries the bubble tail.
        assertEquals(ChatGroupPosition.TOP, cordnGroupPositionFor(middle, oldest, null))
        assertEquals(ChatGroupPosition.MIDDLE, cordnGroupPositionFor(newest, middle, oldest))
        assertEquals(ChatGroupPosition.BOTTOM, cordnGroupPositionFor(null, newest, middle))
    }

    @Test
    fun `a different sender between them breaks the run`() {
        val mine = msg(alice, noon)
        val theirs = msg(bob, noon + 60)
        val mineAgain = msg(alice, noon + 120)

        assertEquals(ChatGroupPosition.SINGLE, cordnGroupPositionFor(theirs, mine, null))
        assertEquals(ChatGroupPosition.SINGLE, cordnGroupPositionFor(mineAgain, theirs, mine))
        assertEquals(ChatGroupPosition.SINGLE, cordnGroupPositionFor(null, mineAgain, theirs))
    }

    @Test
    fun `the shared ten-minute window is what decides, not a cordn-local one`() {
        val first = msg(alice, noon)

        // Inside the window the run holds...
        val nine = msg(alice, noon + 9 * 60)
        assertEquals(ChatGroupPosition.BOTTOM, cordnGroupPositionFor(null, nine, first))

        // ...and just past it, it breaks. A cordn-local 5-minute window (what this
        // screen used before adopting the shared one) would already have broken at 9.
        val eleven = msg(alice, noon + 11 * 60)
        assertEquals(ChatGroupPosition.SINGLE, cordnGroupPositionFor(null, eleven, first))
    }

    @Test
    fun `a run does not cross local midnight even inside the time window`() {
        val midnight =
            LocalDate
                .now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault())
                .toEpochSecond()

        val lastNight = msg(alice, midnight - 120)
        val thisMorning = msg(alice, midnight + 120)

        // Four minutes apart, same sender — but a day separator lands between them, so
        // joining them into one burst would draw the separator inside a sealed bubble run.
        assertFalse(thisMorning.sameDayAs(lastNight))
        assertEquals(ChatGroupPosition.SINGLE, cordnGroupPositionFor(null, thisMorning, lastNight))
    }

    @Test
    fun `sameDayAs treats a missing neighbour as a new day`() {
        // Drives the day separator above the very first message in the list.
        assertFalse(msg(alice, noon).sameDayAs(null))
        assertTrue(msg(alice, noon).sameDayAs(msg(bob, noon + 3600)))
    }
}
