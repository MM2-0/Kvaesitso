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

/**
 * The account-wide coordinator for every joined Concord community: it holds one
 * live [ConcordCommunitySession] per community id and fans inbound stream wraps
 * out to whichever session owns them. This is the read-path analog of
 * [ConcordChannelListState] on the write path — the list yields the joined
 * [ConcordCommunityListEntry] set, this expands each into a folding read-model.
 *
 * The app layer drives it from two directions:
 *  - [sync] whenever the joined list changes (from `liveCommunities`), which
 *    creates sessions for new communities and drops sessions for departed ones
 *    while **preserving** the already-folded state of the ones that remain.
 *  - [ingest] for every inbound kind-1059 wrap, which routes it to the matching
 *    session (control plane → re-fold; channel plane → re-project messages).
 *
 * [subscribeAddresses] returns the union of every session's control- and
 * channel-plane addresses — exactly the `authors` set a subscription must watch
 * for kind-1059 wraps. Thread-safe: the ingest path and UI share one instance.
 */
class ConcordSessionRegistry(
    private val onRumor: ConcordRumorSink = { _, _, _, _ -> },
) {
    private val lock = KmpLock()

    // communityId -> live folding session. Insertion-ordered for stable iteration.
    private val sessions = LinkedHashMap<HexKey, ConcordCommunitySession>()

    /**
     * Reconcile the held sessions with the current joined [entries]. Sessions for
     * communities still present are kept as-is (their folded state survives);
     * sessions for communities no longer joined are dropped; new communities get a
     * fresh session. Returns the set of community ids whose sessions were created.
     */
    fun sync(
        entries: List<ConcordCommunityListEntry>,
        myPubKey: HexKey,
    ): Set<HexKey> =
        lock.withLock {
            val wanted = entries.associateBy { it.id }
            // Drop sessions for communities we've left.
            sessions.keys.retainAll(wanted.keys)
            // Add sessions for newly-joined communities, and rebuild a session whose access
            // material changed under it — a Refounding rotates the community_root and bumps
            // the epoch (CORD-06), so the persisted entry now describes a different set of
            // planes; the session is a pure function of its entry, so we recreate it to
            // re-derive every address and re-fold under the new root.
            val created = mutableSetOf<HexKey>()
            for ((id, entry) in wanted) {
                val existing = sessions[id]
                if (existing == null || existing.entry.root != entry.root || existing.entry.rootEpoch != entry.rootEpoch) {
                    // CORD-08 §3: the disappearing messages the old session tracked are already in the
                    // store, and the new session never sees their wraps again — carry their deadlines
                    // over, or nothing would ever purge them.
                    sessions[id] = ConcordCommunitySession(entry, myPubKey, onRumor = onRumor).also { fresh -> existing?.let { fresh.carryExpiring(it.trackedExpiring()) } }
                    created += id
                } else {
                    // Same epoch, but the Control Plane write key may have just arrived — a
                    // staff-making Grant delivers it inside the fold itself (CORD-04 §3), long
                    // after this session was built. Adopt it in place: rebuilding would drop the
                    // buffered wraps and fold the community empty, and the plane's address is
                    // invariant under adoption anyway (CORD-02 §5).
                    existing.adoptControlMaterial(entry)
                    // Likewise a Private Channel key delivered on grant (CORD-03 §1): re-derive the
                    // channel planes in place so the channel becomes readable and writable.
                    existing.adoptPrivateChannels(entry)
                }
            }
            created
        }

    fun sessionFor(communityId: HexKey): ConcordCommunitySession? = lock.withLock { sessions[communityId] }

    fun sessions(): List<ConcordCommunitySession> = lock.withLock { sessions.values.toList() }

    /** The union of control- and channel-plane addresses across all sessions to subscribe to. */
    fun subscribeAddresses(): Set<HexKey> =
        lock.withLock {
            val out = HashSet<HexKey>()
            for (session in sessions.values) {
                out += session.controlPlaneAddress
                // Prior-epoch Control Planes too: folding them is what gives each entity its
                // anti-rollback floor across a CORD-06 Refounding.
                out += session.historicalControlPlaneAddresses()
                out += session.channelAddresses()
            }
            out
        }

    /**
     * The joined community whose planes [planeAddress] belongs to, or null when no session claims it.
     *
     * The NIP-42 auth path reads this to recognize an outbound plane wrap for what it is. A wrap is
     * signed by the plane's stream key and `p`-tagged to a throwaway pubkey, so on tag shape alone it
     * is indistinguishable from a NIP-17 gift wrap — a post into a community would otherwise be
     * explained to the user as a direct message to a stranger nobody can name.
     */
    fun communityIdForPlane(planeAddress: HexKey): HexKey? =
        lock.withLock {
            sessions.entries.firstOrNull { (_, session) -> session.ownsPlane(planeAddress) }?.key
        }

    /**
     * Routes an inbound stream [wrap] to whichever session recognizes it, returning that session's
     * [ConcordIngestOutcome] (or [ConcordIngestOutcome.NOT_MINE] if none claim it). A wrap belongs to
     * at most one plane, so the first accepting session wins.
     */
    fun ingest(
        wrap: Event,
        seenOnRelays: Set<NormalizedRelayUrl> = emptySet(),
    ): ConcordIngestOutcome {
        val snapshot = lock.withLock { sessions.values.toList() }
        for (session in snapshot) {
            val outcome = session.ingest(wrap, seenOnRelays)
            if (outcome != ConcordIngestOutcome.NOT_MINE) return outcome
        }
        return ConcordIngestOutcome.NOT_MINE
    }

    fun clear() = lock.withLock { sessions.clear() }
}
