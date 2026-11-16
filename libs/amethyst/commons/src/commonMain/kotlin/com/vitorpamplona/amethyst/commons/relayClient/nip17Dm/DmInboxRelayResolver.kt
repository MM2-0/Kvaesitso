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
package com.vitorpamplona.amethyst.commons.relayClient.nip17Dm

import com.vitorpamplona.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Resolves a recipient's NIP-17 inbox relays (kind:10050) for DM delivery.
 *
 * Lookup order:
 *
 * 1. **LocalCache hit** — the caller has already seen the user's kind:10050
 *    via the normal feed subscription pipeline. Cheapest; no I/O.
 * 2. **In-memory LRU cache** — a prior resolve() succeeded for this pubkey
 *    within the TTL. Avoids re-querying when the user opens a conversation
 *    list and clicks several recipients in sequence.
 * 3. **Network fan-out** ([resolve] does this in two phases) — query the
 *    curated indexers **plus** the recipient's known write relays for
 *    kind:10050/10002, and, if the indexers didn't have the 10050, read it
 *    directly from the recipient's NIP-65 write (outbox) relays — the
 *    canonical location per NIP-65. The client passed in here MUST be an
 *    **unauthenticated** instance (no [RelayAuthenticator] attached) —
 *    otherwise a relay's AUTH challenge would extract an identity-key
 *    signature from the user, turning the metadata leak "some relay learns
 *    who we want to DM" into "that relay learns user U wants to DM pubkey X".
 *
 * Filters to **kind:10050 only**. Per NIP-17 §Publishing, gift wraps MUST
 * land on relays in the recipient's kind:10050; this resolver never
 * substitutes the NIP-65 read marker as a fallback, because doing so leaks
 * DMs to relays the recipient did not explicitly designate for DMs.
 *
 * Empty result is the canonical "we don't know where to send" signal — the
 * caller should refuse to publish rather than fall back to its own relays
 * (see [com.vitorpamplona.amethyst.desktop.model.DesktopIAccount.resolveDmInboxRelaysStrict]).
 *
 * @property unauthenticatedClient NostrClient WITHOUT a RelayAuthenticator
 *   attached. Use a dedicated instance — do NOT pass the app's primary
 *   client.
 * @property indexerRelays Curated indexer set. Typically
 *   [com.vitorpamplona.amethyst.commons.defaults.DefaultDmIndexerRelays].
 * @property localLookup Callback the resolver invokes first to check the
 *   LocalCache — returns the user's current kind:10050 list or null if
 *   unknown. Allows commons/headless callers to plug in a CLI-safe lookup.
 * @property outboxLookup Callback returning the recipient's NIP-65 write
 *   (outbox) relays from the recipient's cached kind:10002, or null/empty when
 *   unknown. Per the NIP-65 outbox model a user's replaceable events — kind:10050
 *   included — live on their own write relays, so those are queried directly (in
 *   addition to the curated indexers) rather than trusting the indexers to have
 *   mirrored the 10050. Returns null by default (indexer-only, e.g. the CLI).
 * @property cacheTtlMs LRU cache TTL. 1h matches the brainstorm's open
 *   question; configurable here for tests.
 * @property cacheSize LRU bound. 100 entries × ~200 bytes each is trivial
 *   memory; matches typical active-conversation count for power users.
 */
class DmInboxRelayResolver(
    private val unauthenticatedClient: INostrClient,
    private val indexerRelays: Set<NormalizedRelayUrl>,
    private val localLookup: (HexKey) -> List<NormalizedRelayUrl>?,
    private val outboxLookup: (HexKey) -> List<NormalizedRelayUrl>? = { null },
    private val cacheTtlMs: Long = 60 * 60 * 1_000L,
    private val cacheSize: Int = 100,
    private val nowMs: () -> Long = {
        kotlin.time.Clock.System
            .now()
            .toEpochMilliseconds()
    },
) {
    private data class Entry(
        val relays: List<NormalizedRelayUrl>,
        val expiresAtMs: Long,
    )

    private val cache = linkedMapOf<HexKey, Entry>()
    private val mutex = Mutex()

    /**
     * Resolve `pubkey`'s NIP-17 inbox relays. Returns empty list if neither the
     * LocalCache, the recipient's outbox relays, nor the indexer fan-out yielded
     * a kind:10050.
     *
     * Two network phases against the unauthenticated client:
     *  1. Query the curated indexers **plus** the recipient's known write relays
     *     (from a cached kind:10002) for kinds 10050/10002 in one shot.
     *  2. If that still produced no kind:10050 but we now know the recipient's
     *     write relays (from the kind:10002 the indexers returned, or the cache),
     *     read kind:10050 directly from those write relays — the canonical
     *     NIP-65 outbox location, which the curated indexers may not mirror.
     */
    suspend fun resolve(pubkey: HexKey): List<NormalizedRelayUrl> {
        localLookup(pubkey)?.takeIf { it.isNotEmpty() }?.let { return it }

        val now = nowMs()
        mutex.withLock {
            cache[pubkey]?.let { entry ->
                if (entry.expiresAtMs > now) {
                    // Refresh LRU order on hit
                    cache.remove(pubkey)
                    cache[pubkey] = entry
                    return entry.relays
                } else {
                    cache.remove(pubkey)
                }
            }
        }

        val cachedOutbox = outboxLookup(pubkey)?.toSet().orEmpty()
        val phase1Seed = indexerRelays + cachedOutbox
        if (phase1Seed.isEmpty()) return emptyList()

        // Phase 1: indexers ∪ known outbox relays.
        val lists = RecipientRelayFetcher.fetchRelayLists(unauthenticatedClient, pubkey, phase1Seed)
        // Strict: kind:10050 ONLY. No NIP-65 fallback. Empty = canonical
        // "unreachable" signal; caller refuses to publish.
        var relays = lists.dmInbox

        // Phase 2: the recipient's own write relays are where their kind:10050
        // lives per NIP-65. If phase 1 (mostly the curated indexers) didn't
        // surface it, read straight from the write relays the indexers'
        // kind:10002 just pointed us at — minus what phase 1 already queried
        // (which already includes any cached outbox relays). Bounded by a
        // shorter timeout so a cold send doesn't stack two full fan-out waits.
        if (relays.isEmpty()) {
            val writeRelays = lists.nip65Write().toSet() - phase1Seed
            if (writeRelays.isNotEmpty()) {
                relays =
                    RecipientRelayFetcher
                        .fetchRelayLists(unauthenticatedClient, pubkey, writeRelays, idleTimeoutMs = 5_000L)
                        .dmInbox
            }
        }

        mutex.withLock {
            cache[pubkey] = Entry(relays, now + cacheTtlMs)
            while (cache.size > cacheSize) {
                cache.remove(cache.keys.iterator().next())
            }
        }
        return relays
    }

    /** Evict a specific entry — e.g. when LocalCache observes a fresh kind:10050. */
    suspend fun invalidate(pubkey: HexKey) {
        mutex.withLock { cache.remove(pubkey) }
    }

    /** Wipe the entire cache — e.g. on account switch. */
    suspend fun clear() {
        mutex.withLock { cache.clear() }
    }
}
