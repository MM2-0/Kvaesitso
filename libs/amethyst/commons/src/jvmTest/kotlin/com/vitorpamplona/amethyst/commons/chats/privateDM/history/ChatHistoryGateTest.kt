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
package com.vitorpamplona.amethyst.commons.chats.privateDM.history

import com.vitorpamplona.amethyst.commons.chats.privateDM.history.ChatHistoryPhase.AUTO
import com.vitorpamplona.amethyst.commons.chats.privateDM.history.ChatHistoryPhase.BUTTON
import com.vitorpamplona.amethyst.commons.chats.privateDM.history.ChatHistoryPhase.END
import com.vitorpamplona.amethyst.commons.chats.privateDM.history.ChatHistoryPhase.IDLE
import com.vitorpamplona.amethyst.commons.chats.privateDM.history.ChatHistoryPhase.PAUSED
import com.vitorpamplona.amethyst.commons.chats.privateDM.history.ChatHistoryPhase.SEARCH
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ChatHistoryGate] driven by hand: a fake pager whose `advanceAll` counts pages, and the inputs a chat
 * screen would report as the reader scrolls and pages land.
 */
class ChatHistoryGateTest {
    private var pages = 0
    private var canAdvance = true
    private val gate = ChatHistoryGate { canAdvance.also { if (it) pages++ } }

    private fun ChatHistoryGate.at(
        visible: Boolean?,
        busy: Boolean = false,
        open: Boolean = true,
        count: Int = 0,
    ) = update(visible, busy, open, count)

    @Test
    fun chatOpeningWithMarkersInViewPausesWithoutFetching() {
        gate.at(visible = true, count = 3)
        assertEquals(PAUSED, gate.phase.value)
        assertEquals(0, pages)

        // Re-reports on a static screen don't fetch either.
        gate.at(visible = true, count = 3)
        assertEquals(0, pages)
    }

    @Test
    fun continueLoadsOnePage() {
        gate.at(visible = true, count = 3)
        gate.resume()
        assertEquals(AUTO, gate.phase.value)
        assertEquals(1, pages)
    }

    @Test
    fun scrollingUpToTheMarkersLoadsExactlyOnePage() {
        gate.at(visible = false, count = 40)
        gate.at(visible = true, count = 40)
        assertEquals(AUTO, gate.phase.value)
        assertEquals(1, pages)

        // While it's in flight, nothing else is asked for.
        gate.at(visible = true, busy = true, count = 40)
        gate.at(visible = true, busy = true, count = 40)
        assertEquals(1, pages)
    }

    @Test
    fun aPageWithNothingForThisChatStopsAndOffersKeepLooking() {
        gate.at(visible = false, count = 40)
        gate.at(visible = true, count = 40)
        gate.at(visible = true, busy = true, count = 40)
        gate.at(visible = true, busy = false, count = 40)
        assertEquals(BUTTON, gate.phase.value)

        // The markers staying in view does not walk the rest of the inbox.
        repeat(5) { gate.at(visible = true, count = 40) }
        assertEquals(1, pages)
    }

    @Test
    fun aPageThatBringsMessagesKeepsPagingAsTheReaderScrolls() {
        gate.at(visible = false, count = 40)
        gate.at(visible = true, count = 40)
        gate.at(visible = true, busy = true, count = 40)
        gate.at(visible = false, busy = false, count = 55)
        assertEquals(IDLE, gate.phase.value)

        gate.at(visible = true, count = 55)
        assertEquals(AUTO, gate.phase.value)
        assertEquals(2, pages)
    }

    @Test
    fun lateDecryptedMessagesWithdrawTheKeepLookingOffer() {
        gate.at(visible = false, count = 40)
        gate.at(visible = true, count = 40)
        gate.at(visible = true, busy = true, count = 40)
        gate.at(visible = true, count = 40)
        assertEquals(BUTTON, gate.phase.value)

        // The markers are still in view, so there's nothing to scroll to: it asks instead of paging.
        gate.at(visible = true, count = 42)
        assertEquals(PAUSED, gate.phase.value)
        assertEquals(1, pages)
    }

    @Test
    fun keepLookingPagesRoundAfterRoundUntilAMessageTurnsUp() {
        gate.at(visible = false, count = 40)
        gate.at(visible = true, count = 40)
        gate.at(visible = true, busy = true, count = 40)
        gate.at(visible = true, count = 40)
        assertEquals(1, pages)

        gate.keepLooking()
        assertEquals(SEARCH, gate.phase.value)
        assertEquals(2, pages)

        gate.at(visible = true, busy = true, count = 40)
        gate.at(visible = true, busy = false, count = 40)
        assertEquals(3, pages)
        gate.at(visible = true, busy = true, count = 40)
        gate.at(visible = true, busy = false, count = 40)
        assertEquals(4, pages)

        gate.at(visible = true, busy = true, count = 41)
        assertEquals(IDLE, gate.phase.value)

        // The rest of the round lands; the found message didn't push the markers off screen.
        gate.at(visible = true, busy = false, count = 41)
        assertEquals(PAUSED, gate.phase.value)
        assertEquals(4, pages)
    }

    @Test
    fun keepLookingStopsAfterItsRoundsAndAsksAgain() {
        gate.at(visible = false, count = 40)
        gate.at(visible = true, count = 40)
        gate.at(visible = true, busy = true, count = 40)
        gate.at(visible = true, count = 40)
        assertEquals(1, pages)

        gate.keepLooking()
        repeat(ChatHistoryGate.DEFAULT_SEARCH_ROUNDS) {
            gate.at(visible = true, busy = true, count = 40)
            gate.at(visible = true, busy = false, count = 40)
        }
        assertEquals(BUTTON, gate.phase.value)
        assertEquals(1 + ChatHistoryGate.DEFAULT_SEARCH_ROUNDS, pages)

        // Sitting there doesn't continue the search.
        repeat(5) { gate.at(visible = true, count = 40) }
        assertEquals(1 + ChatHistoryGate.DEFAULT_SEARCH_ROUNDS, pages)

        // Another tap is another set of rounds.
        gate.keepLooking()
        assertEquals(SEARCH, gate.phase.value)
        assertEquals(2 + ChatHistoryGate.DEFAULT_SEARCH_ROUNDS, pages)
    }

    @Test
    fun keepLookingPressedMidPageStartsItsRoundsWhenThatPageSettles() {
        gate.at(visible = false, count = 40)
        gate.at(visible = true, count = 40)
        gate.at(visible = true, busy = true, count = 40)
        gate.at(visible = true, count = 40)
        assertEquals(BUTTON, gate.phase.value)

        // A page is in flight (a stalled relay answering late) when the reader taps Keep looking.
        gate.at(visible = true, busy = true, count = 40)
        gate.keepLooking()
        assertEquals(1, pages)

        gate.at(visible = true, busy = false, count = 40)
        assertEquals(SEARCH, gate.phase.value)
        assertEquals(2, pages)

        // That started round one of three, not a free extra round.
        repeat(ChatHistoryGate.DEFAULT_SEARCH_ROUNDS) {
            gate.at(visible = true, busy = true, count = 40)
            gate.at(visible = true, busy = false, count = 40)
        }
        assertEquals(BUTTON, gate.phase.value)
        assertEquals(1 + ChatHistoryGate.DEFAULT_SEARCH_ROUNDS, pages)
    }

    @Test
    fun aProductivePageThatLeavesTheMarkersInViewWaitsForContinue() {
        gate.at(visible = false, count = 40)
        gate.at(visible = true, count = 40)
        gate.at(visible = true, busy = true, count = 40)
        gate.at(visible = true, busy = false, count = 41)
        assertEquals(PAUSED, gate.phase.value)

        // More messages finishing decryption, a status tick: none of it is the reader asking.
        gate.at(visible = true, count = 43)
        gate.at(visible = true, count = 43)
        assertEquals(PAUSED, gate.phase.value)
        assertEquals(1, pages)

        gate.resume()
        assertEquals(AUTO, gate.phase.value)
        assertEquals(2, pages)
    }

    @Test
    fun aProductivePageNeedsTheReaderToScrollBackToTheMarkers() {
        gate.at(visible = false, count = 40)
        gate.at(visible = true, count = 40)
        gate.at(visible = true, busy = true, count = 40)
        // The new messages push the markers off screen.
        gate.at(visible = false, busy = false, count = 60)
        assertEquals(IDLE, gate.phase.value)
        gate.at(visible = false, count = 62)
        assertEquals(1, pages)

        // Scrolling up through them to the markers is the request.
        gate.at(visible = true, count = 62)
        assertEquals(AUTO, gate.phase.value)
        assertEquals(2, pages)
    }

    @Test
    fun markersAlreadyOffScreenWhenThePageLandsStillCountAsArmed() {
        gate.at(visible = false, count = 40)
        gate.at(visible = true, count = 40)
        // The reader scrolls back down while the page is in flight.
        gate.at(visible = false, busy = true, count = 40)
        gate.at(visible = false, busy = false, count = 45)
        assertEquals(IDLE, gate.phase.value)

        // No further "not visible" report comes, yet scrolling back up must still be a request.
        gate.at(visible = true, count = 45)
        assertEquals(AUTO, gate.phase.value)
        assertEquals(2, pages)
    }

    @Test
    fun stopEndsTheSearch() {
        gate.at(visible = false, count = 40)
        gate.at(visible = true, count = 40)
        gate.at(visible = true, count = 40)
        gate.keepLooking()
        gate.stop()
        assertEquals(BUTTON, gate.phase.value)

        gate.at(visible = true, busy = false, count = 40)
        assertEquals(2, pages)
    }

    @Test
    fun searchEndsWhenNothingMoreIsReachable() {
        gate.at(visible = false, count = 40)
        gate.at(visible = true, count = 40)
        gate.at(visible = true, count = 40)
        gate.keepLooking()
        gate.at(visible = true, busy = true, open = true, count = 40)
        gate.at(visible = true, busy = false, open = false, count = 40)
        assertEquals(END, gate.phase.value)
    }

    @Test
    fun exhaustedHistoryEndsWithoutFetching() {
        gate.at(visible = true, open = false, count = 3)
        assertEquals(END, gate.phase.value)
        assertEquals(0, pages)
    }

    @Test
    fun aRelayReopeningAfterTheEndOffersKeepLookingAgain() {
        gate.at(visible = true, open = false, count = 3)
        gate.at(visible = true, open = true, count = 3)
        assertEquals(BUTTON, gate.phase.value)
    }

    @Test
    fun scrollingAwayFromAPausedChatThenBackIsARequest() {
        gate.at(visible = true, count = 3)
        assertEquals(PAUSED, gate.phase.value)
        gate.at(visible = false, count = 3)
        assertEquals(IDLE, gate.phase.value)
        gate.at(visible = true, count = 3)
        assertEquals(AUTO, gate.phase.value)
        assertEquals(1, pages)
    }
}
