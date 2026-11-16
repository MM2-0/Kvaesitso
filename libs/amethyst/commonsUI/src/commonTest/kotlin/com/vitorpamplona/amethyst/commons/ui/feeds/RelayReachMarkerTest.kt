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
package com.vitorpamplona.amethyst.commons.ui.feeds

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The gap predicate places the per-relay reach marker AND gates its paging sentinel, so an off-by-one
 * here would either render a marker in the wrong gap or fire (or never fire) paging. The boundaries are
 * deliberately asymmetric — newer side strictly `>`, older side `<=` — so each edge is pinned here.
 */
class RelayReachMarkerTest {
    @Test
    fun cursorStrictlyBetweenTwoMessagesIsInTheGap() {
        // gap is (older=80, newer=100]; a cursor reached down to 90 sits in it.
        assertTrue(reachedFallsInGap(reachedUntil = 90, newerCreatedAt = 100, olderCreatedAt = 80))
    }

    @Test
    fun cursorAtTheOldestEndWithNoOlderNeighbourIsInTheGap() {
        // Past the oldest loaded row (olderCreatedAt null): any cursor below the last message sits here.
        assertTrue(reachedFallsInGap(reachedUntil = 50, newerCreatedAt = 100, olderCreatedAt = null))
    }

    @Test
    fun noNewerRowMeansNotInThisGap() {
        // newerCreatedAt null = nothing on the newer side (e.g. a non-message row) → never placed here.
        assertFalse(reachedFallsInGap(reachedUntil = 50, newerCreatedAt = null, olderCreatedAt = 20))
    }

    @Test
    fun newerSideIsStrictlyNewer_equalDoesNotCount() {
        // The marker belongs in the gap *below* the message it reached, not at the message itself.
        assertFalse(reachedFallsInGap(reachedUntil = 100, newerCreatedAt = 100, olderCreatedAt = 50))
    }

    @Test
    fun olderSideIsInclusive_equalCounts() {
        // older == reached: the cursor sits exactly at the older message → still this gap (`<=`).
        assertTrue(reachedFallsInGap(reachedUntil = 80, newerCreatedAt = 100, olderCreatedAt = 80))
    }

    @Test
    fun gapWhoseOlderNeighbourIsStillNewerThanTheCursorIsNotIt() {
        // older=90 is newer than the cursor (70): the cursor lives in a deeper gap, not this one.
        assertFalse(reachedFallsInGap(reachedUntil = 70, newerCreatedAt = 100, olderCreatedAt = 90))
    }

    @Test
    fun cursorNewerThanTheNewerRowIsNotInThisGap() {
        assertFalse(reachedFallsInGap(reachedUntil = 150, newerCreatedAt = 100, olderCreatedAt = 80))
    }
}
