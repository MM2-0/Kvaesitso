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
package com.vitorpamplona.amethyst.commons.relayClient.assemblers

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.preload.MetadataPreloader
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.PrioritizedSubscriptionQueue
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubscriptionPriority
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

/**
 * Coordinates metadata and reactions loading for feed items.
 * Ensures metadata (display names, avatars) loads before reactions.
 *
 * Priority order:
 * 1. METADATA - Display names, avatars (highest priority)
 * 2. REACTIONS - Likes, zaps, reposts (second priority)
 *
 * Usage:
 * ```
 * val coordinator = FeedMetadataCoordinator(client, scope, indexRelays, preloader)
 * coordinator.start()
 *
 * // When feed loads new notes
 * LaunchedEffect(notes) {
 *     coordinator.loadMetadataForNotes(notes)
 * }
 * ```
 */
class FeedMetadataCoordinator(
    private val client: INostrClient,
    private val scope: CoroutineScope,
    private val indexRelays: Set<NormalizedRelayUrl>,
    private val preloader: MetadataPreloader? = null,
    private val onEvent: ((Event, NormalizedRelayUrl) -> Unit)? = null,
) {
    private val priorityQueue = PrioritizedSubscriptionQueue(scope)

    // Track what we've already queued to avoid duplicates
    private val queuedPubkeys = mutableSetOf<HexKey>()
    private val queuedNoteIds = mutableSetOf<HexKey>()
    private val queuedBoostedIds = mutableSetOf<HexKey>()
    private val queuedKind3Pubkeys = mutableSetOf<HexKey>()

    // Batched paths only — pubkeys currently in-flight in a batched REQ.
    // Prevents rapid re-fire of the same batch. Distinct from queuedPubkeys
    // and queuedKind3Pubkeys (which record "asked and at least one relay
    // returned EOSE") so a batch that times out with zero events can be
    // retried on the next call — see PR #3483 review finding 5.
    private val inFlightBatchedMetadata = mutableSetOf<HexKey>()
    private val inFlightBatchedKind3 = mutableSetOf<HexKey>()

    // The six sets above are touched from the caller's thread (Compose scroll
    // callbacks) and from the batched REQ coroutines on IO; a plain HashSet
    // corrupts under that interleaving. Every read-filter-then-add goes
    // through [claim] or an explicit lock.
    private val queueLock = KmpLock()

    /**
     * Atomically returns the members of [candidates] not yet in [set] and marks
     * them, so two callers racing on the same pubkeys cannot both claim them.
     */
    private fun claim(
        set: MutableSet<HexKey>,
        candidates: Collection<HexKey>,
    ): List<HexKey> =
        queueLock.withLock {
            val fresh = candidates.filter { it !in set }.distinct()
            set.addAll(fresh)
            fresh
        }

    /**
     * Start processing the subscription queue.
     * Call once when coordinator is created.
     */
    fun start() {
        priorityQueue.start { filter ->
            // Convert filter to relay-based map for all index relays
            val filterMap = indexRelays.associateWith { listOf(filter) }

            // Create listener to pass events to the callback
            val listener =
                if (onEvent != null) {
                    object : SubscriptionListener {
                        override suspend fun onEvent(
                            event: Event,
                            isLive: Boolean,
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            onEvent.invoke(event, relay)
                        }
                    }
                } else {
                    null
                }

            client.subscribe(
                subId = newSubId(),
                filters = filterMap,
                listener = listener,
            )
        }
    }

    /**
     * Load metadata and reactions for a list of notes.
     * Metadata loads first (priority 1), then reactions (priority 2).
     *
     * @param notes The notes to load metadata/reactions for
     */
    fun loadMetadataForNotes(notes: List<Note>) {
        if (notes.isEmpty()) return

        // Fetch referenced note content: reposts (via replyTo) + quoted notes (via e-tags)
        val repostBoostedIds =
            notes
                .filter { it.event is RepostEvent || it.event is GenericRepostEvent }
                .mapNotNull { it.replyTo?.lastOrNull() }
                .filter { it.event == null }
                .map { it.idHex }

        val quotedNoteIds =
            notes
                .mapNotNull { it.event }
                .flatMap { event -> event.tags.mapNotNull { ETag.parseId(it) } }

        val allReferencedIds = claim(queuedBoostedIds, repostBoostedIds + quotedNoteIds)

        if (allReferencedIds.isNotEmpty()) {
            val referencedFilter =
                Filter(
                    ids = allReferencedIds,
                )
            priorityQueue.enqueue(
                SubscriptionPriority.METADATA,
                referencedFilter,
                tag = "feed-referenced-notes",
            )
        }

        // Extract unique authors that we haven't already queued
        val authors = claim(queuedPubkeys, notes.mapNotNull { it.author?.pubkeyHex })

        // Extract unique note IDs that we haven't already queued
        val noteIds = claim(queuedNoteIds, notes.map { it.idHex })

        // Queue metadata first (highest priority)
        if (authors.isNotEmpty()) {
            // Use preloader if available for rate-limited loading
            if (preloader != null) {
                notes.mapNotNull { it.author }.forEach { user ->
                    preloader.preloadForUser(user)
                }
            } else {
                // Direct queue without rate limiting
                val metadataFilter =
                    Filter(
                        kinds = listOf(MetadataEvent.KIND),
                        authors = authors,
                        limit = authors.size,
                    )
                priorityQueue.enqueue(
                    SubscriptionPriority.METADATA,
                    metadataFilter,
                    tag = "feed-metadata",
                )
            }
        }

        // Queue reactions second (lower priority)
        if (noteIds.isNotEmpty()) {
            val reactionsFilter =
                Filter(
                    kinds = listOf(ReactionEvent.KIND),
                    tags = mapOf("e" to noteIds),
                )
            priorityQueue.enqueue(
                SubscriptionPriority.REACTIONS,
                reactionsFilter,
                tag = "feed-reactions",
            )
        }
    }

    /**
     * Load metadata for specific pubkeys.
     * Useful for loading follower/following metadata.
     */
    fun loadMetadataForPubkeys(pubkeys: List<HexKey>) {
        val newPubkeys = claim(queuedPubkeys, pubkeys)
        if (newPubkeys.isEmpty()) return

        val filter =
            Filter(
                kinds = listOf(MetadataEvent.KIND),
                authors = newPubkeys,
                limit = newPubkeys.size,
            )
        priorityQueue.enqueue(
            SubscriptionPriority.METADATA,
            filter,
            tag = "pubkey-metadata",
        )
    }

    /**
     * Load reactions for specific note IDs.
     */
    fun loadReactionsForNotes(noteIds: List<HexKey>) {
        val newNoteIds = claim(queuedNoteIds, noteIds)
        if (newNoteIds.isEmpty()) return

        val filter =
            Filter(
                kinds = listOf(ReactionEvent.KIND),
                tags = mapOf("e" to newNoteIds),
            )
        priorityQueue.enqueue(
            SubscriptionPriority.REACTIONS,
            filter,
            tag = "note-reactions",
        )
    }

    /**
     * Fast-path: batched metadata subscription for visible-viewport authors.
     * Bypasses rate limiter. Single filter with all authors. Closes after EOSE.
     *
     * Pubkeys are moved into [queuedPubkeys] (dedup) only after at least one
     * relay EOSE'd. On timeout with zero EOSE (index relays all unreachable)
     * they roll out of [inFlightBatchedMetadata] so a subsequent call can
     * retry — see PR #3483 review finding 5.
     */
    fun loadMetadataBatched(
        pubkeys: List<HexKey>,
        timeoutMs: Long = 5_000L,
    ) {
        val newPubkeys =
            queueLock.withLock {
                pubkeys
                    .asSequence()
                    .filter { it !in queuedPubkeys && it !in inFlightBatchedMetadata }
                    .distinct()
                    .toList()
                    .also { inFlightBatchedMetadata.addAll(it) }
            }
        if (newPubkeys.isEmpty()) return

        scope.launch {
            // One filter per 100 authors, like loadKind3Batched: a single
            // `take(100)` filter used to mark the whole list as asked-for while
            // only ever requesting the first hundred.
            val filters =
                newPubkeys.chunked(100).map { chunk ->
                    Filter(
                        kinds = listOf(MetadataEvent.KIND),
                        authors = chunk,
                        limit = chunk.size,
                    )
                }
            val filterMap = indexRelays.associateWith { filters }
            val subId = newSubId()
            val gate = BatchEoseGate(scope, target = indexRelays.size)

            val listener =
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        onEvent?.invoke(event, relay)
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        gate.notifyEose(relay)
                    }
                }

            client.subscribe(subId, filterMap, listener)
            val eosedRelays = gate.awaitAll(timeoutMs)
            client.unsubscribe(subId)

            queueLock.withLock {
                if (eosedRelays > 0) {
                    queuedPubkeys.addAll(newPubkeys)
                }
                inFlightBatchedMetadata.removeAll(newPubkeys.toSet())
            }
        }
    }

    /**
     * Batched kind-3 (follow list) subscription. Used by the WoT service
     * to fetch the follow lists of every account the active user follows,
     * so friends-of-friends counts can be computed.
     *
     * Chunks authors into ≤100 per Filter within a single subscription
     * so relays with per-filter author caps (nostr-rs-relay defaults to
     * ~100) don't silently truncate the batch. Aggregates EOSE across
     * chunks and calls [onEose] once (or after [timeoutMs]).
     *
     * Pubkeys are moved into [queuedKind3Pubkeys] (dedup) only after at
     * least one relay EOSE'd. On timeout with zero EOSE (index relays all
     * unreachable — common on flaky mobile networks) they roll out of
     * [inFlightBatchedKind3] so the next `loadKind3Batched` call retries
     * — see PR #3483 review finding 5.
     */
    fun loadKind3Batched(
        pubkeys: Collection<HexKey>,
        timeoutMs: Long = 5_000L,
        onEose: () -> Unit = {},
    ) {
        val newPubkeys =
            queueLock.withLock {
                pubkeys
                    .asSequence()
                    .filter { it !in queuedKind3Pubkeys && it !in inFlightBatchedKind3 }
                    .distinct()
                    .toList()
                    .also { inFlightBatchedKind3.addAll(it) }
            }
        if (newPubkeys.isEmpty()) {
            onEose()
            return
        }

        scope.launch {
            val filters =
                newPubkeys.chunked(100).map { chunk ->
                    Filter(
                        kinds = listOf(ContactListEvent.KIND),
                        authors = chunk,
                        limit = chunk.size,
                    )
                }
            val filterMap = indexRelays.associateWith { filters }
            val subId = newSubId()
            val gate = BatchEoseGate(scope, target = indexRelays.size)

            val listener =
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        this@FeedMetadataCoordinator.onEvent?.invoke(event, relay)
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        gate.notifyEose(relay)
                    }
                }

            client.subscribe(subId, filterMap, listener)
            val eosedRelays = gate.awaitAll(timeoutMs)
            client.unsubscribe(subId)

            queueLock.withLock {
                if (eosedRelays > 0) {
                    queuedKind3Pubkeys.addAll(newPubkeys)
                }
                inFlightBatchedKind3.removeAll(newPubkeys.toSet())
            }

            onEose()
        }
    }

    /**
     * Clear queued items. Call when switching feeds.
     */
    fun clear() {
        priorityQueue.clear()
        queueLock.withLock {
            queuedPubkeys.clear()
            queuedNoteIds.clear()
            queuedBoostedIds.clear()
            queuedKind3Pubkeys.clear()
            inFlightBatchedMetadata.clear()
            inFlightBatchedKind3.clear()
        }
    }

    /**
     * Aggregates EOSE notifications from per-relay `onEose` callbacks
     * (which the client may dispatch on `Dispatchers.IO`) via a
     * [Channel]. The consumer coroutine is the sole reader/writer of the
     * `seen` set, eliminating the race the previous `mutableSetOf` +
     * shared-state check had — see PR #3483 review finding 6.
     *
     * [awaitAll] blocks up to [timeoutMs] and returns the number of
     * relays that EOSE'd (may be less than [target] on timeout). The
     * count feeds the retry decision in the batched loaders.
     */
    private class BatchEoseGate(
        private val scope: CoroutineScope,
        private val target: Int,
    ) {
        private val incoming = Channel<NormalizedRelayUrl>(Channel.UNLIMITED)
        private val done = CompletableDeferred<Unit>()

        @Volatile private var lastCount = 0

        fun notifyEose(relay: NormalizedRelayUrl) {
            incoming.trySend(relay)
        }

        suspend fun awaitAll(timeoutMs: Long): Int {
            if (target <= 0) return 0
            val consumer =
                scope.launch {
                    val seen = mutableSetOf<NormalizedRelayUrl>()
                    for (relay in incoming) {
                        if (seen.add(relay)) {
                            lastCount = seen.size
                            if (seen.size >= target && !done.isCompleted) {
                                done.complete(Unit)
                            }
                        }
                    }
                }
            withTimeoutOrNull(timeoutMs) { done.await() }
            incoming.close()
            consumer.join()
            return lastCount
        }
    }
}
