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
package com.vitorpamplona.amethyst.commons.model.zap

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks in how the unified zap chip classifies a cashu nutzap per amount.
 * This is the precise (free-to-read) gating the chip relies on, so the
 * boundaries between funded / reload / impossible matter.
 */
class RailCapabilityCashuStatusTest {
    private fun caps(
        hasCashu: Boolean = true,
        bestSingleMint: Long = 0L,
        totalWallet: Long = 0L,
    ) = RailCapability(
        hasCashu = hasCashu,
        hasLightning = false,
        hasOnchain = false,
        cashuBestSingleMintSats = bestSingleMint,
        cashuTotalWalletSats = totalWallet,
    )

    @Test
    fun unavailableWhenRecipientCannotReceiveCashu() {
        assertEquals(
            CashuRailStatus.UNAVAILABLE,
            caps(hasCashu = false, bestSingleMint = 10_000L, totalWallet = 10_000L).cashuStatus(100L),
        )
    }

    @Test
    fun fundedWhenSingleMintCoversAmount() {
        val c = caps(bestSingleMint = 1_000L, totalWallet = 5_000L)
        assertEquals(CashuRailStatus.FUNDED, c.cashuStatus(500L))
        // Exact boundary is funded.
        assertEquals(CashuRailStatus.FUNDED, c.cashuStatus(1_000L))
    }

    @Test
    fun needsReloadWhenOnlyTotalCoversAmount() {
        // Funds exist, but spread across mints so no single shared mint covers it.
        val c = caps(bestSingleMint = 1_000L, totalWallet = 5_000L)
        assertEquals(CashuRailStatus.NEEDS_RELOAD, c.cashuStatus(1_001L))
        assertEquals(CashuRailStatus.NEEDS_RELOAD, c.cashuStatus(5_000L))
    }

    @Test
    fun impossibleWhenTotalCannotCoverAmount() {
        val c = caps(bestSingleMint = 1_000L, totalWallet = 5_000L)
        assertEquals(CashuRailStatus.IMPOSSIBLE, c.cashuStatus(5_001L))
    }

    @Test
    fun emptyWalletWithReceivingRecipientIsImpossibleNotUnavailable() {
        // Recipient can receive (hasCashu) but we hold nothing anywhere.
        assertEquals(CashuRailStatus.IMPOSSIBLE, caps().cashuStatus(1L))
    }
}
