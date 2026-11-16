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
package com.vitorpamplona.amethyst.model.nipBCOnchainZaps

import com.vitorpamplona.amethyst.commons.model.nipBCOnchainZaps.OnchainBalanceStatus
import com.vitorpamplona.amethyst.commons.model.nipBCOnchainZaps.OnchainWalletState
import com.vitorpamplona.quartz.nipBCOnchainZaps.builder.OnchainZapBuilder
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.BitcoinAddressTx
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.BitcoinTx
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.FeeEstimates
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.OnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.Utxo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The zap picker gates the on-chain rail on this cache, so what matters is that
 * it reports the *spendable* figure (fee taken out, unconfirmed coins excluded),
 * that it collapses repeat reads into one explorer round trip, and that a failed
 * fetch leaves the balance unknown rather than looking like an empty wallet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnchainWalletStateTest {
    private val pubKey = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"

    private class FakeBackend(
        var utxos: List<Utxo> = emptyList(),
        var fees: FeeEstimates? = FeeEstimates(fastSatPerVbyte = 20.0, normalSatPerVbyte = 5.0, slowSatPerVbyte = 1.0),
    ) : OnchainBackend {
        var utxoCalls = 0
        var failUtxos = false

        override suspend fun getUtxosForAddress(address: String): List<Utxo> {
            utxoCalls++
            if (failUtxos) throw RuntimeException("explorer down")
            return utxos
        }

        override suspend fun feeEstimates(): FeeEstimates = fees ?: throw RuntimeException("no fees")

        override suspend fun getTx(txid: String): BitcoinTx? = null

        override suspend fun getTxsForAddress(
            address: String,
            afterTxid: String?,
        ): List<BitcoinAddressTx> = emptyList()

        override suspend fun broadcast(rawTxHex: String): String = ""

        override suspend fun tipHeight(): Long = 0L
    }

    private fun utxo(
        valueSats: Long,
        index: Int,
        confirmations: Int = 6,
    ) = Utxo(txid = index.toString().padStart(64, '0'), vout = 0, valueSats = valueSats, confirmations = confirmations)

    private fun TestScope.stateFor(backend: FakeBackend) =
        OnchainWalletState(
            pubKey = pubKey,
            scope = this,
            backend = { backend },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

    @Test
    fun reportsSpendableBalanceNetOfTheMinerFee() =
        runTest(StandardTestDispatcher()) {
            val backend = FakeBackend(utxos = listOf(utxo(100_000L, 1), utxo(50_000L, 2)))
            val state = stateFor(backend)

            state.refresh()
            advanceUntilIdle()

            val funds = state.funds.value!!
            assertEquals(150_000L, funds.confirmedSats)
            assertEquals(0L, funds.unconfirmedSats)
            assertEquals(5.0, funds.feeRateSatPerVByte, 0.0)
            assertEquals(
                OnchainZapBuilder.maxSpendableSats(backend.utxos, feeRateSatPerVByte = 5.0),
                funds.maxSpendableSats,
            )
            // A fee always comes off the top, so the full balance is never payable.
            assertTrue(funds.maxSpendableSats < funds.confirmedSats)
            assertTrue(funds.canAfford(funds.maxSpendableSats))
            assertTrue(!funds.canAfford(funds.maxSpendableSats + 1))
        }

    @Test
    fun mempoolCoinsAreReportedButNotSpendable() =
        runTest(StandardTestDispatcher()) {
            val backend = FakeBackend(utxos = listOf(utxo(100_000L, 1, confirmations = 0)))
            val state = stateFor(backend)

            state.refresh()
            advanceUntilIdle()

            val funds = state.funds.value!!
            assertEquals(0L, funds.confirmedSats)
            assertEquals(100_000L, funds.unconfirmedSats)
            // The builder won't chain off an unconfirmed parent, so nothing is payable.
            assertEquals(0L, funds.maxSpendableSats)
            assertTrue(!funds.canAfford(1L))
        }

    @Test
    fun repeatedReadsInsideTheWindowShareOneRoundTrip() =
        runTest(StandardTestDispatcher()) {
            val backend = FakeBackend(utxos = listOf(utxo(100_000L, 1)))
            val state = stateFor(backend)

            repeat(5) { state.refresh() }
            advanceUntilIdle()
            assertEquals(1, backend.utxoCalls)

            // A spend invalidates the snapshot; the next read must hit the chain.
            state.invalidate()
            state.refresh()
            advanceUntilIdle()
            assertEquals(2, backend.utxoCalls)
        }

    @Test
    fun forcedRefreshBypassesTheWindow() =
        runTest(StandardTestDispatcher()) {
            val backend = FakeBackend(utxos = listOf(utxo(100_000L, 1)))
            val state = stateFor(backend)

            state.refresh()
            advanceUntilIdle()
            state.refresh(force = true)
            advanceUntilIdle()

            assertEquals(2, backend.utxoCalls)
        }

    @Test
    fun aFailedFetchLeavesTheBalanceUnknownNotEmpty() =
        runTest(StandardTestDispatcher()) {
            val backend = FakeBackend(utxos = listOf(utxo(100_000L, 1)))
            backend.failUtxos = true
            val state = stateFor(backend)

            state.refresh()
            advanceUntilIdle()

            // null, not a zero balance — the picker keeps offering the rail.
            assertNull(state.funds.value)
            // And it backs off instead of retrying on every picker open.
            state.refresh()
            advanceUntilIdle()
            assertEquals(1, backend.utxoCalls)
        }

    @Test
    fun aFailedFetchKeepsThePreviousSnapshot() =
        runTest(StandardTestDispatcher()) {
            val backend = FakeBackend(utxos = listOf(utxo(100_000L, 1)))
            val state = stateFor(backend)

            state.refresh()
            advanceUntilIdle()
            val good = state.funds.value

            backend.failUtxos = true
            state.refresh(force = true)
            advanceUntilIdle()

            assertEquals(good, state.funds.value)
        }

    @Test
    fun missingFeeEstimatesFallBackToAConservativeRate() =
        runTest(StandardTestDispatcher()) {
            val backend = FakeBackend(utxos = listOf(utxo(100_000L, 1)), fees = null)
            val state = stateFor(backend)

            state.refresh()
            advanceUntilIdle()

            val funds = state.funds.value!!
            assertEquals(OnchainWalletState.FALLBACK_FEE_RATE_SAT_PER_VBYTE, funds.feeRateSatPerVByte, 0.0)
            // A high assumed rate understates what we can spend — it hides a
            // marginal amount instead of offering an unpayable one.
            assertTrue(
                funds.maxSpendableSats <
                    OnchainZapBuilder.maxSpendableSats(backend.utxos, feeRateSatPerVByte = 5.0),
            )
        }

    @Test
    fun noBackendMeansNoFetchAndNoBalance() =
        runTest(StandardTestDispatcher()) {
            val state =
                OnchainWalletState(
                    pubKey = pubKey,
                    scope = this,
                    backend = { null },
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                )

            state.refresh(force = true)
            advanceUntilIdle()

            assertNull(state.funds.value)
        }

    @Test
    fun statusWalksLoadingToReady() =
        runTest(StandardTestDispatcher()) {
            val backend = FakeBackend(utxos = listOf(utxo(100_000L, 1)))
            val state = stateFor(backend)

            assertEquals(OnchainBalanceStatus.LOADING, state.status.value)

            state.refresh()
            advanceUntilIdle()

            assertEquals(OnchainBalanceStatus.READY, state.status.value)
            // The wallet card shows everything on the address, settled or not.
            assertEquals(100_000L, state.funds.value!!.totalSats)
        }

    @Test
    fun statusIsErrorOnlyWhenThereIsNothingToShow() =
        runTest(StandardTestDispatcher()) {
            val backend = FakeBackend(utxos = listOf(utxo(100_000L, 1)))
            backend.failUtxos = true
            val state = stateFor(backend)

            state.refresh()
            advanceUntilIdle()
            assertEquals(OnchainBalanceStatus.ERROR, state.status.value)

            // Once a balance is known, a later failure keeps the last good number
            // on screen rather than blanking it to an error.
            backend.failUtxos = false
            state.refresh(force = true)
            advanceUntilIdle()
            assertEquals(OnchainBalanceStatus.READY, state.status.value)

            backend.failUtxos = true
            state.refresh(force = true)
            advanceUntilIdle()
            assertEquals(OnchainBalanceStatus.READY, state.status.value)
            assertEquals(100_000L, state.funds.value!!.totalSats)
        }

    @Test
    fun statusIsUnavailableWithoutABackend() =
        runTest(StandardTestDispatcher()) {
            var backend: OnchainBackend? = null
            val state =
                OnchainWalletState(
                    pubKey = pubKey,
                    scope = this,
                    backend = { backend },
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                )

            state.refresh()
            advanceUntilIdle()
            assertEquals(OnchainBalanceStatus.UNAVAILABLE, state.status.value)

            // AppModules wires the backend up after the account is built, so a
            // later refresh has to climb back out of UNAVAILABLE.
            backend = FakeBackend(utxos = listOf(utxo(100_000L, 1)))
            state.refresh()
            advanceUntilIdle()
            assertEquals(OnchainBalanceStatus.READY, state.status.value)
        }

    @Test
    fun statusIsUnavailableWithoutADerivableAddress() {
        val state =
            OnchainWalletState(
                pubKey = "not a pubkey",
                scope = TestScope(),
                backend = { null },
            )

        assertNull(state.address)
        assertEquals(OnchainBalanceStatus.UNAVAILABLE, state.status.value)
    }
}
