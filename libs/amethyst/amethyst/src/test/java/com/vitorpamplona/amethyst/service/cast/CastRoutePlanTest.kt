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
package com.vitorpamplona.amethyst.service.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class CastRoutePlanTest {
    private val tv = "route-tv"
    private val soundbar = "route-soundbar"

    @Test
    fun reusesTheSessionOnlyWhenItBelongsToTheTargetRoute() {
        assertEquals(
            CastRoutePlan.REUSE_SESSION,
            planRouteSelection(targetRouteId = tv, selectedRouteId = tv, hasConnectedSession = true),
        )
    }

    @Test
    fun switchingReceiversIsASwapNotAReuse() {
        // The regression: a session connected to the soundbar was treated as reusable for the TV.
        // cast() completed its start immediately, selectRoute() then tore that session down, and the
        // load was skipped against a dead session — the TV connected and showed nothing.
        assertEquals(
            CastRoutePlan.SWAP_RECEIVER,
            planRouteSelection(targetRouteId = tv, selectedRouteId = soundbar, hasConnectedSession = true),
        )
    }

    @Test
    fun aDisconnectedSessionOnTheTargetRouteStillNeedsAFreshStart() {
        assertEquals(
            CastRoutePlan.SELECT_FRESH,
            planRouteSelection(targetRouteId = tv, selectedRouteId = tv, hasConnectedSession = false),
        )
    }

    @Test
    fun theFirstCastOfTheSessionSelectsFresh() {
        assertEquals(
            CastRoutePlan.SELECT_FRESH,
            planRouteSelection(targetRouteId = tv, selectedRouteId = null, hasConnectedSession = false),
        )
    }

    @Test
    fun onlyASwapExpectsTheOldSessionToEnd() {
        // This is what stops the outgoing session's onSessionEnded from failing the incoming
        // attempt, which is why switching devices used to report "session start refused".
        assertEquals(true, CastRoutePlan.SWAP_RECEIVER.expectsPreviousSessionToEnd)
        assertEquals(false, CastRoutePlan.SELECT_FRESH.expectsPreviousSessionToEnd)
        assertEquals(false, CastRoutePlan.REUSE_SESSION.expectsPreviousSessionToEnd)
    }

    @Test
    fun onlyReuseSkipsTheRouteSelection() {
        assertEquals(false, CastRoutePlan.REUSE_SESSION.needsRouteSelection)
        assertEquals(true, CastRoutePlan.SWAP_RECEIVER.needsRouteSelection)
        assertEquals(true, CastRoutePlan.SELECT_FRESH.needsRouteSelection)
    }
}
