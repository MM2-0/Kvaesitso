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
package com.vitorpamplona.amethyst.commons.model.nip03Timestamp

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsResolver
import com.vitorpamplona.quartz.nip03Timestamp.VerificationState
import com.vitorpamplona.quartz.nip40Expiration.isExpirationBefore
import com.vitorpamplona.quartz.utils.TimeUtils

/*
 * OTS (NIP-03) attestation resolution off a note's own [Note.timestamps] list. Every kind-1040
 * attestation targeting a note is anchored there as a hard-referenced child (like a reaction), so
 * finding a note's proofs is an in-memory fold — no LocalCache scan. Each attestation memoizes its
 * blockchain verdict in [Note.otsVerification], which shares the attestation note's lifecycle: it is
 * evicted when the target note is (a NIP-09 delete or a cache prune), so there is no separate,
 * id-keyed verification cache to keep in sync. This is the read side that replaces the old
 * VerificationStateCache + full-cache scan.
 */

/** The memoized OTS verdict for this attestation note, or null when it has never been verified. */
fun Note.justOtsVerification(): VerificationState? = otsVerification

/**
 * Returns the OTS verdict for this attestation note, verifying against the blockchain only when no
 * usable verdict exists yet (or a stale [VerificationState.NetworkError] is due for a retry). The
 * result is stored on the note so later reads are free. Must run off the main thread — verification
 * hits the network. No-op verdict ([VerificationState.Error]) when the note is not an OTS event.
 */
suspend fun Note.cacheVerifyOts(resolver: OtsResolver): VerificationState {
    val event = event as? OtsEvent ?: return VerificationState.Error("Not an OTS event")
    return when (val current = otsVerification) {
        is VerificationState.Verified -> current
        is VerificationState.Error -> current
        is VerificationState.NetworkError ->
            if (current.time < TimeUtils.fiveMinutesAgo()) verifyOts(event, resolver) else current
        // null, or a leftover non-terminal state from an interrupted run: (re)verify.
        else -> verifyOts(event, resolver)
    }
}

/**
 * Verifies the attestation and stores ONLY the terminal verdict. We deliberately never persist a
 * `Verifying` sentinel: this runs inside a cancellable `LoadOts` LaunchedEffect, so if the coroutine
 * were cancelled at the network suspension point after writing `Verifying` but before the verdict,
 * that sentinel would stick on the (long-lived) note forever — there is no LRU eviction to recover
 * it, unlike the old VerificationStateCache — and the OTS pill would silently vanish. Leaving the
 * field untouched until a real verdict lands means a cancelled run simply retries on the next read;
 * the cost is at worst two concurrent first-time verifications, which is harmless.
 */
private suspend fun Note.verifyOts(
    event: OtsEvent,
    resolver: OtsResolver,
): VerificationState = event.verifyState(resolver).also { otsVerification = it }

/**
 * The earliest blockchain-verified time (unix seconds) among this note's non-expired OTS
 * attestations, or null when none verify. Reads already-cached verdicts first — so a proof that was
 * verified on arrival shows without waiting on the network — then verifies any still-unresolved
 * proofs. Must run off the main thread.
 */
suspend fun Note.earliestOtsVerifiedTime(resolver: OtsResolver): Long? {
    val now = TimeUtils.now()
    var minTime: Long? = null

    fun consider(time: Long) {
        if (minTime.let { it == null || time < it }) minTime = time
    }

    val live =
        timestamps.filter {
            val e = it.event
            e is OtsEvent && !e.isExpirationBefore(now)
        }

    val unresolved =
        live.filter { proof ->
            val verified = (proof.justOtsVerification() as? VerificationState.Verified)?.verifiedTime
            if (verified != null) {
                consider(verified)
                false
            } else {
                true
            }
        }

    unresolved.forEach { proof ->
        (proof.cacheVerifyOts(resolver) as? VerificationState.Verified)?.verifiedTime?.let(::consider)
    }

    return minTime
}
