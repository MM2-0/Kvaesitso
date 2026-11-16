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

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.OnchainZapStatus
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.util.ConcurrentSet
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipBCOnchainZaps.verify.OnchainZapVerifier
import com.vitorpamplona.quartz.nipBCOnchainZaps.verify.VerifiedOnchainZap
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Coordinates NIP-BC on-chain zap verification on top of the chain backend.
 *
 * `LocalCache.consume(OnchainZapEvent)` owns the event dispatch and optimistic
 * gallery attachment; this class owns the asynchronous chain-verification side:
 *
 * - Launching a verifier coroutine when a new event arrives (with per-event
 *   in-flight de-duplication so simultaneous relay echoes don't double-fetch).
 * - Re-running verification for visible notes when the chain tip advances or
 *   when a screen first comes into view.
 * - Owning the shared chain-tip poller flow that UI subscribes to.
 *
 * Stateless from the caller's perspective — the per-event resolution state lives
 * on the source [Note] itself (`onchainZapResolved`), so it travels with the Note
 * across cache eviction and doesn't accumulate here.
 */
class OnchainZapResolver(
    private val cache: EventCache,
) {
    /**
     * Caps the parallelism of [reverifyOnchainZapsForNote] so a thread with many
     * pending entries doesn't blast public Esplora endpoints with a burst of
     * simultaneous requests.
     */
    private val reverifySemaphore = Semaphore(permits = 8)

    /**
     * In-flight set of event ids currently being verified. Prevents two `consume()`
     * calls (or a `consume` plus a reverify) from issuing parallel Esplora fetches
     * for the same event. [ConcurrentSet] gives lock-free atomic `add`
     * returning `true` only for the inserting caller.
     */
    private val verifyingEventIds = ConcurrentSet<HexKey>()

    /**
     * In-flight set of note id strings currently being reverified. Lets the on-chain
     * zap gallery driver be called from many visible composables without the same
     * note's reverify pass running concurrently. The per-event gate above is the
     * backstop; this one short-circuits earlier and avoids creating async coroutines
     * that would just no-op.
     */
    private val reverifyingNoteIds = ConcurrentSet<HexKey>()

    /**
     * Shared poller for the current bitcoin chain tip height. Each gallery that
     * holds non-CONFIRMED on-chain zaps subscribes to this flow and re-verifies its
     * entries whenever the tip advances. Lazy + `WhileSubscribed` so the HTTP call
     * only fires when at least one UI surface needs it.
     *
     * Lazy so the scope is read from the cache's host after the app shell has installed
     * it, rather than at construction time — the cache is a singleton and is built first.
     */
    val onchainTipHeightFlow: StateFlow<Long?> by lazy {
        val scope = cache.appHost.scope

        flow {
            while (true) {
                val tip =
                    cache.onchainBackend?.let { backend ->
                        // Explicit try/catch instead of `runCatching` because the latter
                        // would swallow CancellationException too — when the upstream
                        // scope cancels, we must let it propagate so the flow tears down
                        // promptly instead of looping through one more delay().
                        try {
                            backend.tipHeight()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            null
                        }
                    }
                emit(tip)
                delay(TIP_POLL_INTERVAL_MS)
            }
        }.stateIn(
            scope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = null,
        )
    }

    /**
     * Launches an asynchronous chain verification for [event] unless one is already
     * in flight or [source] has already reached a terminal verdict. Returns
     * immediately. Apply-to-Note updates happen on the application IO scope.
     *
     * [repliesTo] is the pre-computed list of notes the event references so consume()
     * doesn't pay the cost of resolving it twice.
     */
    fun launchVerification(
        event: OnchainZapEvent,
        source: Note,
        repliesTo: List<Note>,
    ) {
        val backend = cache.onchainBackend ?: return
        if (source.onchainZapResolved) return
        if (!verifyingEventIds.add(event.id)) return

        val verifier = OnchainZapVerifier(backend)

        cache.appHost.scope.launch {
            try {
                verifyAndUpgradeOnchainZap(event, source, repliesTo, verifier)
            } finally {
                verifyingEventIds.remove(event.id)
            }
        }
    }

    /**
     * Re-run on-chain zap verification for every non-CONFIRMED entry attached to
     * [note]. Safe to call from a screen-visibility hook or a chain-tip change
     * observer; each verifier call is bounded by the chain backend's cache TTLs.
     *
     * - Per-note in-flight gate ([reverifyingNoteIds]) — if another caller is
     *   already reverifying this note (e.g. multiple visible galleries fired in
     *   the same tip tick) we skip the dup.
     * - Per-event in-flight gate ([verifyingEventIds]) — coordinates with
     *   [launchVerification] so the same event isn't verified twice concurrently.
     * - [supervisorScope] — a single verifier failure won't cancel sibling
     *   verifiers for other entries on the same note.
     * - Bounded parallelism via [reverifySemaphore] so a thread with many pending
     *   entries doesn't blast Esplora.
     */
    suspend fun reverifyOnchainZapsForNote(note: Note) {
        val backend = cache.onchainBackend ?: return
        if (!reverifyingNoteIds.add(note.idHex)) return
        try {
            val pendingEntries =
                note.onchainZaps.values.filter { it.status != OnchainZapStatus.CONFIRMED }
            if (pendingEntries.isEmpty()) return

            val verifier = OnchainZapVerifier(backend)
            supervisorScope {
                pendingEntries
                    .mapNotNull { entry ->
                        val sourceEvent = entry.source.event as? OnchainZapEvent ?: return@mapNotNull null
                        val source = entry.source
                        if (source.onchainZapResolved) return@mapNotNull null
                        if (!verifyingEventIds.add(sourceEvent.id)) return@mapNotNull null
                        async {
                            try {
                                reverifySemaphore.withPermit {
                                    val repliesTo = cache.computeReplyTo(sourceEvent)
                                    verifyAndUpgradeOnchainZap(sourceEvent, source, repliesTo, verifier)
                                }
                            } finally {
                                verifyingEventIds.remove(sourceEvent.id)
                            }
                        }
                    }.awaitAll()
            }
        } finally {
            reverifyingNoteIds.remove(note.idHex)
        }
    }

    /**
     * Run the chain verifier for a single on-chain zap event and apply the result to
     * every target note. Designed to be safe to call repeatedly — the [Note] entries
     * upgrade monotonically (UNVERIFIED → PENDING → CONFIRMED) and won't move
     * backwards, and source-scoped removal prevents one event's rejection from
     * erasing another sender's legitimate entry that happens to share a txid.
     *
     * Callers must wrap the call site with the [verifyingEventIds] gate; this
     * function does not itself protect against duplicate concurrent runs.
     */
    private suspend fun verifyAndUpgradeOnchainZap(
        event: OnchainZapEvent,
        source: Note,
        repliesTo: List<Note>,
        verifier: OnchainZapVerifier,
    ) {
        // Clamp claimedSats to non-negative — `amount` tag parses via toLongOrNull()
        // with no sign check, so a malicious sender could otherwise put "-1" in the
        // gallery as a negative-sats badge.
        val claimedSats = (event.claimedAmountInSats() ?: 0L).coerceAtLeast(0L)
        try {
            when (val result = verifier.verify(event)) {
                is VerifiedOnchainZap.Confirmed -> {
                    repliesTo.forEach {
                        it.addOnchainZap(source, result.txid, claimedSats, result.verifiedSats, OnchainZapStatus.CONFIRMED)
                    }
                    // Terminal — the chain has confirmed. Future relay echoes of this
                    // event id skip the verifier entirely via the `onchainZapResolved`
                    // gate in `launchVerification`.
                    source.onchainZapResolved = true
                }

                is VerifiedOnchainZap.Pending -> {
                    repliesTo.forEach {
                        it.addOnchainZap(source, result.txid, claimedSats, result.verifiedSats, OnchainZapStatus.PENDING)
                    }
                    // Not terminal — the tx is in the mempool. A future tip-poll or
                    // reverify call can upgrade it to CONFIRMED.
                }

                is VerifiedOnchainZap.Rejected -> {
                    if (result.reason == VerifiedOnchainZap.Rejected.Reason.TX_NOT_FOUND) {
                        // Transient — the tx may not have propagated to the backend's
                        // indexer yet. Leave the entry as UNVERIFIED so a later
                        // reverifyOnchainZapsForNote() call (e.g. on chain tip change)
                        // can promote it.
                        Log.d("OnchainZap") { "tx not yet indexed for ${event.id} (${result.txid}); will retry" }
                    } else if (result.txid.isNotEmpty()) {
                        // Hard rejection — e.g. ZERO_VERIFIED_AMOUNT means this sender's
                        // tx did not pay the recipient. Drop only entries whose source
                        // matches THIS event AND that aren't CONFIRMED (the per-target
                        // CONFIRMED check inside `removeOnchainZapForSource` prevents a
                        // transient backend hiccup from wiping a previously-verified
                        // entry on a sibling target).
                        Log.d("OnchainZap") { "rejected ${result.txid}: ${result.reason}" }
                        repliesTo.forEach { it.removeOnchainZapForSource(result.txid, event.pubKey) }
                        // Terminal — don't re-verify on future relay echoes of this
                        // event id. (Different event ids with the same txid still go
                        // through their own verifier pass.)
                        source.onchainZapResolved = true
                    } else {
                        // MISSING_TXID etc. — log so we have observability when a
                        // malformed event reaches the backend. Mark terminal so we
                        // don't re-verify the same broken event on every echo.
                        Log.d("OnchainZap") { "rejected ${event.id}: ${result.reason} (no txid)" }
                        source.onchainZapResolved = true
                    }
                }
            }
        } catch (t: Throwable) {
            // Never swallow cancellation — it must propagate so screen-scoped callers
            // (the gallery's reverification driver) can tear down cleanly.
            if (t is CancellationException) throw t
            Log.w("OnchainZap", "verification failed for ${event.id}", t)
        }
    }

    companion object {
        /**
         * Polling interval for the shared on-chain chain-tip flow. Aligned with
         * `CachingOnchainBackend.tipHeightTtlSeconds` (60s) — polling faster would
         * just hit the cache and do no useful work.
         */
        private const val TIP_POLL_INTERVAL_MS = 60_000L
    }
}
