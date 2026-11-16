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

import com.vitorpamplona.amethyst.commons.model.MIN_ONCHAIN_ZAP_SATS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in when the unified zap chip offers the on-chain rail: the recipient
 * must be payable on-chain, the amount must clear the on-chain minimum, and our
 * own Taproot wallet must be able to cover it.
 */
class RailCapabilityOnchainGateTest {
    private fun caps(
        hasOnchain: Boolean = true,
        maxSpendable: Long? = null,
    ) = RailCapability(
        hasCashu = false,
        hasLightning = false,
        hasOnchain = hasOnchain,
        onchainMaxSpendableSats = maxSpendable,
    )

    @Test
    fun notOfferedWhenRecipientCannotBePaidOnchain() {
        assertFalse(caps(hasOnchain = false, maxSpendable = 1_000_000L).canPayOnchain(50_000L))
    }

    @Test
    fun notOfferedBelowTheOnchainMinimum() {
        val c = caps(maxSpendable = 1_000_000L)
        assertFalse(c.canPayOnchain(MIN_ONCHAIN_ZAP_SATS - 1))
        assertTrue(c.canPayOnchain(MIN_ONCHAIN_ZAP_SATS))
    }

    @Test
    fun notOfferedWhenOurWalletCannotCoverIt() {
        val c = caps(maxSpendable = 50_000L)
        assertTrue(c.canPayOnchain(50_000L))
        assertFalse(c.canPayOnchain(50_001L))
    }

    @Test
    fun emptyWalletOffersNothingOnchain() {
        val c = caps(maxSpendable = 0L)
        assertFalse(c.canPayOnchain(MIN_ONCHAIN_ZAP_SATS))
        assertFalse(c.canPayOnchain(1_000_000L))
    }

    @Test
    fun unknownBalanceStaysOptimistic() {
        // Balance not loaded yet / explorer unreachable: keep offering the rail
        // rather than silently dropping a payment option. The send path is still
        // the authority on funds.
        val c = caps(maxSpendable = null)
        assertTrue(c.canPayOnchain(MIN_ONCHAIN_ZAP_SATS))
        assertTrue(c.canPayOnchain(100_000_000L))
    }
}
