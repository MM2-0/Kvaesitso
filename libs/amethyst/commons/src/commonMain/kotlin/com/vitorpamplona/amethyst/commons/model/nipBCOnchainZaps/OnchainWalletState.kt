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
package com.vitorpamplona.amethyst.commons.model.nipBCOnchainZaps

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipBCOnchainZaps.builder.OnchainZapBuilder
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.OnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * A snapshot of the account's NIP-BC on-chain wallet: what is sitting on the
 * account's Taproot address, and — the part the UI actually needs before
 * offering an on-chain amount — how much of it a zap can still pay out once the
 * miner fee is taken.
 *
 * @property confirmedSats Value of the confirmed UTXOs (what a zap may spend).
 * @property unconfirmedSats Value still in the mempool. Not spendable: the
 *           builder refuses to chain off an unconfirmed parent by default.
 * @property maxSpendableSats Largest single-recipient amount the builder could
 *           pay from these UTXOs at [feeRateSatPerVByte]. Fee-inclusive, so it
 *           is never the whole of [confirmedSats].
 * @property feeRateSatPerVByte The rate [maxSpendableSats] was computed at.
 */
@Immutable
data class OnchainFunds(
    val confirmedSats: Long,
    val unconfirmedSats: Long,
    val maxSpendableSats: Long,
    val feeRateSatPerVByte: Double,
) {
    /**
     * Everything sitting on the address, settled or not — the figure the wallet
     * card displays. Not what a zap can spend: see [maxSpendableSats].
     */
    val totalSats: Long get() = confirmedSats + unconfirmedSats

    /** Whether an on-chain zap of [amountSats] fits in the wallet, fee included. */
    fun canAfford(amountSats: Long) = amountSats <= maxSpendableSats
}

/**
 * Load state of [OnchainWalletState.funds], for surfaces that display the
 * balance itself rather than just gating on it.
 *
 *  - [UNAVAILABLE]: nothing to load — the pubkey yields no Taproot address, or
 *    no chain backend is configured.
 *  - [LOADING]: no snapshot yet; a fetch is pending or in flight.
 *  - [READY]: a snapshot is available. It may be slightly stale — a failed
 *    refresh keeps the last good balance rather than blanking the display.
 *  - [ERROR]: no snapshot, and the last fetch failed.
 */
enum class OnchainBalanceStatus { UNAVAILABLE, LOADING, READY, ERROR }

/**
 * Account-scoped cache of the sender's on-chain (NIP-BC) balance.
 *
 * Every account has exactly one Taproot address, derived from its Nostr pubkey,
 * and reading its balance means an Esplora round trip — far too expensive to do
 * per rendered zap chip. So the fetch happens once per [STALE_AFTER_MS] window,
 * the result is held here for the account's lifetime, and UI reads the cached
 * [funds] synchronously.
 *
 * The zap picker uses it to decide whether to offer the on-chain rail for a
 * given amount at all: [OnchainFunds.maxSpendableSats] comes from
 * [OnchainZapBuilder.maxSpendableSats], which mirrors the builder's own greedy
 * coin selection, so an amount that clears it is an amount the send path will
 * not reject with `InsufficientFundsException`.
 *
 * `null` [funds] means "not loaded yet / backend unreachable" — callers should
 * treat that as *unknown*, not as *empty*, and keep offering the rail so a flaky
 * explorer doesn't silently remove a payment option (the send path still
 * validates for real).
 */
class OnchainWalletState(
    private val pubKey: HexKey,
    private val scope: CoroutineScope,
    /**
     * Read lazily on every fetch rather than captured: `LocalCache.onchainBackend`
     * is wired by `AppModules` and may still be null when the account is built.
     */
    private val backend: () -> OnchainBackend?,
    /** Where the explorer round trip runs. Overridden in tests. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        /** How long a balance snapshot is reused before a refresh re-fetches it. */
        const val STALE_AFTER_MS = 60_000L

        /**
         * Fee rate used when the explorer answers with UTXOs but no fee
         * estimate. Deliberately on the high side: overestimating the fee
         * *understates* what we can spend, which hides a marginal amount rather
         * than offering one the send path would then refuse.
         */
        const val FALLBACK_FEE_RATE_SAT_PER_VBYTE = 20.0
    }

    /** The account's Taproot address, or null if the pubkey can't be tweaked. */
    val address: String? = runCatching { TaprootAddress.fromPubKey(pubKey) }.getOrNull()

    private val _funds = MutableStateFlow<OnchainFunds?>(null)

    /** Latest snapshot, or null while unknown. See the class doc on `null`. */
    val funds: StateFlow<OnchainFunds?> = _funds.asStateFlow()

    private val _status =
        MutableStateFlow(
            if (address == null) OnchainBalanceStatus.UNAVAILABLE else OnchainBalanceStatus.LOADING,
        )

    /** Why [funds] is what it is — for the wallet card, which shows the number. */
    val status: StateFlow<OnchainBalanceStatus> = _status.asStateFlow()

    /** Serializes fetches so N callers asking at once produce one round trip. */
    private val loading = Mutex()

    /**
     * When the last fetch was *attempted*, not when it last succeeded — a
     * failure has to back off too, otherwise an unreachable explorer gets a
     * fresh request every time the zap picker opens.
     */
    @Volatile
    private var lastAttemptMs = 0L

    private fun isStale() = TimeUtils.nowMillis() - lastAttemptMs > STALE_AFTER_MS

    /**
     * Refreshes the snapshot in the background if it is older than
     * [STALE_AFTER_MS] (or always, when [force]). Safe to call from a
     * `LaunchedEffect` on every surface that shows on-chain amounts — repeated
     * calls inside the window are a no-op.
     */
    fun refresh(force: Boolean = false) {
        if (address == null) return
        if (backend() == null) {
            _status.value = OnchainBalanceStatus.UNAVAILABLE
            return
        }
        // The backend may have been wired up since the last attempt; move off
        // UNAVAILABLE/ERROR so the card shows a spinner rather than a stale "—".
        if (_funds.value == null) _status.value = OnchainBalanceStatus.LOADING
        if (!force && !isStale()) return
        scope.launch(ioDispatcher) { load(force) }
    }

    /**
     * Drops the cached snapshot's freshness so the next [refresh] re-fetches.
     * Call after spending on-chain — the UTXO set just changed under us.
     */
    fun invalidate() {
        lastAttemptMs = 0L
    }

    private suspend fun load(force: Boolean) {
        val addr = address ?: return
        val chain = backend() ?: return

        loading.withLock {
            // Re-check under the lock: while we queued, another caller may have
            // already refreshed the snapshot.
            if (!force && !isStale()) return
            lastAttemptMs = TimeUtils.nowMillis()

            val utxos =
                try {
                    chain.getUtxosForAddress(addr)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    // Keep the previous snapshot rather than dropping to null: a
                    // slightly stale balance is a better gate than no gate, and
                    // null would re-open every amount as "unknown". Which is also
                    // why this only reports ERROR when there is nothing to show.
                    Log.w("OnchainWalletState", "Could not load the on-chain balance", t)
                    if (_funds.value == null) _status.value = OnchainBalanceStatus.ERROR
                    return
                }

            val feeRate = currentFeeRate(chain)

            _funds.value =
                OnchainFunds(
                    confirmedSats = utxos.sumOf { if (it.confirmations > 0) it.valueSats else 0L },
                    unconfirmedSats = utxos.sumOf { if (it.confirmations > 0) 0L else it.valueSats },
                    // One recipient output: the common zap. A split zap pays a
                    // few sats more in fees per extra output, which only matters
                    // within a rounding error of the whole balance — the send
                    // path is still the authority.
                    maxSpendableSats = OnchainZapBuilder.maxSpendableSats(utxos, feeRate),
                    feeRateSatPerVByte = feeRate,
                )
            _status.value = OnchainBalanceStatus.READY
        }
    }

    /**
     * The rate the zap dialog would default to (its NORMAL tier), so the offered
     * amounts and the amount the dialog can actually send agree.
     */
    private suspend fun currentFeeRate(chain: OnchainBackend): Double {
        val fetched =
            try {
                chain.feeEstimates().normalSatPerVbyte
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w("OnchainWalletState", "Could not load fee estimates", t)
                null
            }

        return fetched?.takeIf { it > 0 }
            ?: _funds.value?.feeRateSatPerVByte
            ?: FALLBACK_FEE_RATE_SAT_PER_VBYTE
    }
}
