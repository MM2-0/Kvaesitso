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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuzzHuddleLivenessStateTest {
    @AfterTest
    fun reset() = BuzzHuddleLivenessState.clearForTesting()

    @Test
    fun aReportedSessionIsLiveUntilItStopsBeingReported() {
        BuzzHuddleLivenessState.record("chan", "s1", seenAtSecs = 1_000)

        assertEquals(setOf("s1"), BuzzHuddleLivenessState.liveSessions("chan", nowSecs = 1_000))
        assertEquals(setOf("s1"), BuzzHuddleLivenessState.liveSessions("chan", nowSecs = 1_000 + BuzzHuddleLivenessState.LIVE_WINDOW_SECS))
        // Two missed 10s polls later it is gone: the relay only answers for live sessions.
        assertTrue(BuzzHuddleLivenessState.liveSessions("chan", nowSecs = 1_001 + BuzzHuddleLivenessState.LIVE_WINDOW_SECS).isEmpty())
    }

    @Test
    fun sessionsAreScopedToTheirChannelAndAnOlderReportIsIgnored() {
        BuzzHuddleLivenessState.record("chan", "s1", seenAtSecs = 1_000)
        BuzzHuddleLivenessState.record("chan", "s1", seenAtSecs = 900)

        assertTrue(BuzzHuddleLivenessState.liveSessions("other", nowSecs = 1_000).isEmpty())
        assertEquals(1_000L, BuzzHuddleLivenessState.flow.value["chan"]?.get("s1"))
    }
}
