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
package com.vitorpamplona.amethyst.commons.service.nwc

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcResponseEvent
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

/**
 * Tracks pending NIP-47 (Nostr Wallet Connect) payment requests awaiting responses.
 *
 * Shared between Android and Desktop to provide consistent payment tracking behavior.
 * Platform-specific caches (LocalCache, DesktopLocalCache) delegate to this tracker
 * for the core request/response matching logic.
 *
 * Flow:
 * 1. When sending payment request: [registerRequest] stores callback and the
 *    expected wallet-service pubkey
 * 2. When response arrives: [onResponseReceived] retrieves the pending request
 *    only if the response author matches the expected wallet-service pubkey
 * 3. Caller invokes callback and links notes via Note.addZapPayment()
 *
 * Author verification is required because the relay subscription filters by
 * request id only (matching Primal's filter shape). An attacker who observes
 * the request on the relay could otherwise forge a response with their own
 * keypair and trick the client into displaying attacker-controlled data.
 */
@OptIn(ExperimentalAtomicApi::class)
class NwcPaymentTracker {
    /**
     * Data for a pending payment request.
     *
     * @property expectedServicePubkey Pubkey of the wallet service we sent
     *   the request to. Only a response signed by this key is accepted.
     * @property zappedNote The note being zapped, if payment is for a zap
     * @property onResponse Callback to invoke when wallet responds
     */
    class PendingRequest(
        val expectedServicePubkey: HexKey,
        val zappedNote: Note?,
        val onResponse: suspend (NwcResponseEvent) -> Unit,
    ) {
        // Number of kind-23195 events that targeted this request id with the
        // right `e` tag but the wrong author. Surfaced to the UI when a
        // request times out so an actual attack can be distinguished from
        // an unresponsive wallet.
        val spoofAttempts = AtomicInt(0)
    }

    private val awaitingRequests = ConcurrentMap<HexKey, PendingRequest>()

    /**
     * Registers a pending payment request.
     *
     * @param requestId Event ID of the NwcRequestEvent
     * @param expectedServicePubkey Wallet service pubkey from the request's `p` tag
     * @param zappedNote The note being zapped (null if not a zap payment)
     * @param onResponse Callback invoked when response arrives
     */
    fun registerRequest(
        requestId: HexKey,
        expectedServicePubkey: HexKey,
        zappedNote: Note?,
        onResponse: suspend (NwcResponseEvent) -> Unit,
    ) {
        awaitingRequests[requestId] = PendingRequest(expectedServicePubkey, zappedNote, onResponse)
    }

    /** Outcome of matching a response event against the pending-requests table. */
    sealed interface MatchResult {
        /** No pending request for this request id (or the id was null). */
        data object NoMatch : MatchResult

        /** A pending request exists but the response author does not match. */
        data class WrongAuthor(
            val expected: HexKey,
            val actual: HexKey,
        ) : MatchResult

        /** Response is authentic; the pending request has been removed. */
        data class Matched(
            val pending: PendingRequest,
        ) : MatchResult
    }

    /**
     * Looks up the pending request for the given response. Only consumes (and
     * returns [MatchResult.Matched]) when the response author matches the
     * stored [PendingRequest.expectedServicePubkey] — a forged response is
     * left in the map so the legitimate one can still resolve it.
     *
     * @param requestId The `e` tag from the response, pointing to original request
     * @param responseAuthor The `pubkey` field of the response event
     */
    fun onResponseReceived(
        requestId: HexKey?,
        responseAuthor: HexKey,
    ): MatchResult {
        if (requestId == null) return MatchResult.NoMatch
        val pending = awaitingRequests[requestId] ?: return MatchResult.NoMatch
        if (pending.expectedServicePubkey != responseAuthor) {
            pending.spoofAttempts.fetchAndIncrement()
            return MatchResult.WrongAuthor(pending.expectedServicePubkey, responseAuthor)
        }
        // Author matches — atomically remove and return.
        val removed = awaitingRequests.remove(requestId) ?: return MatchResult.NoMatch
        return MatchResult.Matched(removed)
    }

    /**
     * Number of spoofed (wrong-author) replies that arrived for this request.
     * Returns 0 if the request is unknown or has already been resolved.
     */
    fun spoofAttemptsFor(requestId: HexKey?): Int {
        if (requestId == null) return 0
        return awaitingRequests[requestId]?.spoofAttempts?.load() ?: 0
    }

    /**
     * Checks if there's a pending request for the given ID.
     */
    fun hasPendingRequest(requestId: HexKey): Boolean = awaitingRequests[requestId] != null

    /**
     * Manually removes a pending request (e.g., on timeout).
     *
     * Returns true when this call is the one that removed it. Callers racing a
     * response use that to decide who reports the outcome: the response path
     * consumes the entry through [onResponseReceived], so a timeout that gets
     * false must stay quiet rather than overwrite a real wallet answer.
     */
    fun cleanup(requestId: HexKey): Boolean = awaitingRequests.remove(requestId) != null

    /**
     * Returns count of pending requests (for debugging/monitoring).
     */
    fun pendingCount(): Int = awaitingRequests.size()
}
