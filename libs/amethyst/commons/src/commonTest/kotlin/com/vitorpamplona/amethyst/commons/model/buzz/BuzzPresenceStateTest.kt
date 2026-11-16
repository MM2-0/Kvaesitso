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
package com.vitorpamplona.amethyst.commons.model.buzz

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BuzzPresenceStateTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    @BeforeTest fun setup() = BuzzPresenceState.clearForTesting()

    @AfterTest fun teardown() = BuzzPresenceState.clearForTesting()

    @Test
    fun recordsAndReadsLatestStatus() {
        BuzzPresenceState.record(alice, "online", 1_000L)
        assertEquals("online", BuzzPresenceState.statusFor(alice))
        assertNull(BuzzPresenceState.statusFor(bob))
    }

    @Test
    fun aNewerUpdateWins() {
        BuzzPresenceState.record(alice, "online", 1_000L)
        BuzzPresenceState.record(alice, "away", 2_000L)
        assertEquals("away", BuzzPresenceState.statusFor(alice))
    }

    @Test
    fun anOlderUpdateIsIgnored() {
        BuzzPresenceState.record(alice, "away", 2_000L)
        BuzzPresenceState.record(alice, "online", 1_000L)
        assertEquals("away", BuzzPresenceState.statusFor(alice))
    }

    @Test
    fun presenceIsPerUser() {
        BuzzPresenceState.record(alice, "online", 1_000L)
        BuzzPresenceState.record(bob, "offline", 1_000L)
        assertEquals(mapOf(alice to "online", bob to "offline"), BuzzPresenceState.flow.value)
    }
}
