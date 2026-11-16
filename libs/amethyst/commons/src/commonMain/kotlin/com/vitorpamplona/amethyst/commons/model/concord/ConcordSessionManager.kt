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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The account-scoped owner of the live Concord read-path. It keeps the
 * [ConcordSessionRegistry] in step with the joined-communities list and turns
 * each community's folds into a single observable tick the app layer watches to
 * (re)derive subscription filters and refresh its channel index.
 *
 * Wiring, per account:
 *  - Construct once with the account's `liveCommunities` flow, its pubkey, and a
 *    long-lived [scope]. It self-starts a collector that [ConcordSessionRegistry.sync]s
 *    on every list change and, for each **new** session, watches its
 *    [ConcordCommunitySession.state] so a fold bumps [revision].
 *  - Feed every inbound kind-1059 wrap through [ingest]; a Concord plane wrap is
 *    applied (control → re-fold, channel → re-project) and returns true, so the
 *    caller can stop treating it as a NIP-59 DM.
 *  - Read [subscribeAddresses] to build the `authors` set for the kind-1059
 *    subscription; re-read it whenever [revision] advances (a fold reveals new
 *    channel planes to watch).
 *
 * Everything platform-specific (the LocalCache channel index, the actual REQ
 * mounting) stays in the app layer, which reacts to [revision]; this class holds
 * no Android/UI dependency so it stays unit-testable.
 */
class ConcordSessionManager(
    private val communities: StateFlow<List<ConcordCommunityListEntry>>,
    private val myPubKey: HexKey,
    private val scope: CoroutineScope,
    private val onRumor: ConcordRumorSink = { _, _, _, _ -> },
) {
    val registry = ConcordSessionRegistry(onRumor)

    private val _revision = MutableStateFlow(0)

    /** Monotonic counter bumped whenever the joined set or any community's fold changes. */
    val revision: StateFlow<Int> = _revision

    // Declared before `init`: the communities collector may run synchronously on an immediate dispatcher.
    private val _nextExpiry = MutableStateFlow<Long?>(null)

    /**
     * The earliest disappearing-message deadline (unix seconds) across every joined community, or
     * null when nothing expires (CORD-08 §3). The account schedules its [sweepExpired] on this, so
     * communities without a timer cost nothing.
     */
    val nextExpiry: StateFlow<Long?> = _nextExpiry

    // Guards the compute-and-assign of [nextExpiry]. Declared before `init` for the same reason.
    private val expiryLock = KmpLock()

    private val lock = KmpLock()
    private val stateWatchers = HashMap<HexKey, Job>() // communityId -> state collector

    init {
        scope.launch {
            communities.collect { entries -> onCommunitiesChanged(entries) }
        }
    }

    private fun onCommunitiesChanged(entries: List<ConcordCommunityListEntry>) {
        val created = registry.sync(entries, myPubKey)
        val wantedIds = entries.mapTo(HashSet()) { it.id }

        lock.withLock {
            // Cancel watchers for communities we've left.
            val departed = stateWatchers.keys.filterNot { it in wantedIds }
            for (id in departed) stateWatchers.remove(id)?.cancel()

            // Watch each newly-created session so its folds bump the revision. A Refounding
            // rebuilds a still-joined community's session in place (same id, new root/epoch),
            // so it comes back in `created` while its old watcher is still running — cancel
            // that stale collector before replacing the map entry, or every Refounding leaks
            // a coroutine holding a dead session and bumping the revision forever.
            for (id in created) {
                val session = registry.sessionFor(id) ?: continue
                stateWatchers.remove(id)?.cancel()
                stateWatchers[id] =
                    scope.launch {
                        // CORD-08: a rumor with an expiration moves the account-wide sweep deadline.
                        launch { session.nextExpiry.collect { recomputeNextExpiry() } }
                        session.state.collect { bumpRevision() }
                    }
            }
        }
        recomputeNextExpiry()
        bumpRevision()
    }

    private fun recomputeNextExpiry() {
        // Computed and assigned under one lock: two watchers racing could otherwise let a slower,
        // staler computation overwrite an earlier deadline, and the sweep would sleep past it.
        expiryLock.withLock {
            _nextExpiry.value = registry.sessions().mapNotNull { it.nextExpiry.value }.minOrNull()
        }
    }

    /**
     * Sweeps every session for rumors expired at [now] (CORD-08 §3): drops their wraps from the
     * session buffers and returns them, per community, for the caller to purge from its store.
     */
    fun sweepExpired(now: Long): Map<HexKey, List<ExpiredConcordRumor>> {
        val out = HashMap<HexKey, List<ExpiredConcordRumor>>()
        for (session in registry.sessions()) {
            val expired = session.sweepExpired(now)
            if (expired.isNotEmpty()) out[session.entry.id] = expired
        }
        recomputeNextExpiry()
        return out
    }

    private fun bumpRevision() {
        // Called from the communities collector, every per-session state watcher, and the
        // ingest path — different coroutines/dispatchers — so the increment must be atomic
        // or concurrent bumps are lost.
        _revision.update { it + 1 }
    }

    /** The `authors` set (control + known channel planes) for the kind-1059 subscription. */
    fun subscribeAddresses(): Set<HexKey> = registry.subscribeAddresses()

    /**
     * The stream secret keys that must answer a NIP-42 AUTH challenge from [relay]:
     * every plane of every joined community whose relays include [relay] — the Control
     * Plane + folded channels ([ConcordCommunitySession.streamKeys]) plus the Guestbook
     * and next-epoch base-rekey planes ([ConcordCommunitySession.auxStreamKeys]). Concord
     * relays serve a plane's kind-1059 wraps only to a connection authenticated as that
     * stream key, so the relay-auth layer signs a kind-22242 with each of these (locally,
     * never the user's signer) — without them the plane REQ is refused. Since the relay
     * re-authenticates on an `auth-required` CLOSED, a key revealed only after the Control
     * Plane folds (a channel) is picked up on the retry; the aux keys derive from the entry
     * alone, so they authenticate on the initial connection.
     */
    fun streamAuthSecretsFor(relay: NormalizedRelayUrl): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        for (session in registry.sessions()) {
            val relays = session.entry.relays.mapNotNullTo(HashSet()) { RelayUrlNormalizer.normalizeOrNull(it) }
            if (relay in relays) {
                session.streamKeys().forEach { out.add(it.secretKey) }
                session.auxStreamKeys().forEach { out.add(it.secretKey) }
            }
        }
        return out
    }

    /**
     * Route an inbound stream wrap; true if it was a Concord plane wrap we applied. Only a wrap that
     * changed community *structure* (a fold, a membership/rekey change — not a plain chat message)
     * bumps the revision: bumping per message re-derives every plane subscription per message and
     * gets the client rate-limited off the relays (which then close the plane subs mid-load). Chat
     * messages still reach the feed via the rumor sink → LocalCache, independent of the revision.
     */
    fun ingest(
        wrap: Event,
        seenOnRelays: Set<NormalizedRelayUrl> = emptySet(),
    ): Boolean {
        val outcome = registry.ingest(wrap, seenOnRelays)
        // Only the planes that change structure *without* republishing `state`. A control-plane
        // fold returns STRUCTURAL_FOLD and is bumped by the per-session state watcher instead, which
        // (since the folded state compares by value) fires only when the fold genuinely changed.
        if (outcome == ConcordIngestOutcome.STRUCTURAL) bumpRevision()
        return outcome.claimed
    }

    /**
     * The joined community [planeAddress] streams for, or null when it isn't one of ours. Lets the
     * NIP-42 auth path name a pending plane wrap as a post into that community instead of reading its
     * throwaway `p` tag as a DM recipient (see [ConcordSessionRegistry.communityIdForPlane]).
     */
    fun communityIdForPlane(planeAddress: HexKey): HexKey? = registry.communityIdForPlane(planeAddress)

    fun sessions() = registry.sessions()

    fun sessionFor(communityId: HexKey) = registry.sessionFor(communityId)

    fun destroy() {
        lock.withLock {
            stateWatchers.values.forEach { it.cancel() }
            stateWatchers.clear()
        }
        registry.clear()
    }
}
