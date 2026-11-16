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
package com.vitorpamplona.amethyst.commons.model.nip60Cashu

import com.vitorpamplona.amethyst.commons.cashu.ops.NutzapRedemption
import com.vitorpamplona.amethyst.commons.cashu.ops.normalizeMintUrl
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Decides which pending nutzaps an auto-redeem sweep tries, and remembers what the last sweeps
 * learned about each mint. Not thread-safe: [CashuWalletState] calls it under its sweep mutex.
 * Session state only; a restart starts fresh.
 */
class NutzapRedeemScheduler(
    private val nowMs: () -> Long = { TimeUtils.nowMillis() },
) {
    /**
     * Nutzaps worth no more than their mint's input fee, even swapped together with every other
     * pending nutzap at that mint (a 1-sat nutzap on a 100-ppk keyset). Not dead: the fee is
     * rounded up once per swap, so they become redeemable once enough new nutzaps arrive at the
     * same mint.
     */
    private val belowFee = HashSet<HexKey>()

    /** Per normalized mint: consecutive sweeps with transient failures there, and when to try it again. */
    private val backoff = HashMap<String, Backoff>()

    private class Backoff(
        val failures: Int,
        val retryAtMs: Long,
    )

    /**
     * The nutzaps of [pending] worth trying now:
     *  - only at one of [acceptedMints] (normalized). A nutzap names its own mint and anyone can
     *    send one, so any other mint is never contacted (NIP-61: the mints the recipient lists).
     *    Not remembered: adding the mint later makes them redeemable;
     *  - not at a mint backing off after transient failures;
     *  - not at a mint whose pending nutzaps all fell below its fee last time: they can't do
     *    better until a new nutzap arrives there, and then the whole mint is retried together.
     */
    fun select(
        pending: Collection<NutzapEvent>,
        acceptedMints: Set<String>,
    ): List<NutzapEvent> {
        val now = nowMs()
        return pending
            .groupBy { it.mintUrl()?.let(::normalizeMintUrl) }
            .filter { (mint, group) ->
                mint != null &&
                    mint in acceptedMints &&
                    (backoff[mint]?.retryAtMs ?: 0L) <= now &&
                    group.any { it.id !in belowFee }
            }.values
            .flatten()
    }

    /** What a sweep's [result] changes for the next ones. */
    class Outcome(
        /** Nutzaps that fell below the fee for the first time. */
        val newlyBelowFee: List<NutzapEvent>,
        /** Mints now backing off, with the delay in ms. */
        val backingOff: Map<String, Long>,
    )

    fun record(result: NutzapRedemption): Outcome {
        val now = nowMs()
        val newlyBelowFee = result.belowFee.filter { belowFee.add(it.id) }

        val failedMints = result.failed.keys.mapNotNullTo(HashSet()) { it.mintUrl()?.let(::normalizeMintUrl) }
        val backingOff = LinkedHashMap<String, Long>()
        failedMints.forEach { mint ->
            val failures = (backoff[mint]?.failures ?: 0) + 1
            val delay = backoffMs(failures)
            backoff[mint] = Backoff(failures, now + delay)
            backingOff[mint] = delay
        }
        result.redeemed
            .flatMap { it.nutzaps }
            .mapNotNullTo(HashSet()) { it.mintUrl()?.let(::normalizeMintUrl) }
            .filter { it !in failedMints }
            .forEach { backoff.remove(it) }

        return Outcome(newlyBelowFee, backingOff)
    }

    companion object {
        /** 1 min, doubling per consecutive failing sweep, capped at 1 h. */
        fun backoffMs(failures: Int): Long = minOf(60_000L shl (failures - 1).coerceIn(0, 6), 3_600_000L)
    }
}
