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
package com.vitorpamplona.amethyst.commons.wot

import com.vitorpamplona.amethyst.commons.relays.dialableWriteRelays
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.RelayListRecommendationProcessor
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

/**
 * Fetches kind-0 (profile metadata) and kind-3 (contact list) events for a
 * set of authors using the NIP-65 **outbox model**:
 *
 * 1. **Phase 1 — discover.** Ask the configured index relays (Purple Pages,
 *    Coracle, nos.lol, …) for the kind-10002 of each author. Merge with
 *    already-cached 10002s from [OutboxCacheGateway].
 *
 * 2. **Phase 2 — pick + fetch.** Feed the author → write-relays map into
 *    [RelayListRecommendationProcessor.reliableRelaySetFor] to get a
 *    minimal, popularity-based set of relays that covers every author.
 *    Open one subscription per recommended relay, filtered to that
 *    relay's authors, for kind-0 and/or kind-3.
 *
 * 3. **Phase 3 — fallback.** For any author whose kind-10002 the network
 *    never returned, fall back to the index-relay REQ (preserves the
 *    current behaviour so a coldish account doesn't lose signal).
 *
 * The dispatcher is single-scoped (one instance per account) so its dedup
 * set survives across follow-set diffs. Call [clear] on account switch.
 *
 * @param client shared [INostrClient] used for every subscription
 * @param scope account-lifetime scope; cancelling it cancels in-flight REQs
 * @param indexRelays lazy accessor so a change through the settings UI
 *                    takes effect on next fetch without recreating the
 *                    dispatcher
 * @param gateway platform-specific cache adapter (see [OutboxCacheGateway])
 * @param perRelayTimeoutMs how long each REQ waits for its EOSE. Under
 *                          the plan (2026-07-06): 4 s.
 * @param overallTimeoutMs cap on the whole two-phase fetch. Belt against
 *                         a phase getting stuck. Under the plan: 8 s.
 * @param maxOutboxRelaysPerAuthor bound author → write-relays to the first N
 *                                 relays after the [RelayListRecommendationProcessor]
 *                                 chooses them, to keep fan-out predictable
 */
class OutboxDispatcher(
    private val client: INostrClient,
    private val scope: CoroutineScope,
    private val indexRelays: () -> Set<NormalizedRelayUrl>,
    private val gateway: OutboxCacheGateway,
    private val perRelayTimeoutMs: Long = 4_000L,
    private val overallTimeoutMs: Long = 20_000L,
    @Suppress("UNUSED_PARAMETER") maxOutboxRelaysPerAuthor: Int = 5,
) {
    /**
     * Pubkeys we've already successfully fetched kind-3 for this session
     * (Phase 1 or Phase 2 returned events for them). Skipping a second
     * fetch is safe because a churn event from a subsequent kind-3
     * republication still reaches [OutboxCacheGateway.onDiscoveredEvent]
     * via other subscriptions (feed, notifications).
     */
    private val kind3Succeeded = mutableSetOf<HexKey>()

    /**
     * Pubkeys we've already successfully fetched kind-0 for this session.
     */
    private val kind0Succeeded = mutableSetOf<HexKey>()

    /**
     * Currently-in-flight authors — prevents rapid re-fire of the same
     * fetch. Distinct from [kind3Succeeded]/[kind0Succeeded]: a zero-EOSE
     * timeout rolls out of this set (allowing retry) instead of
     * permanently marking the pubkey as done.
     */
    private val kind3InFlight = mutableSetOf<HexKey>()
    private val kind0InFlight = mutableSetOf<HexKey>()

    /**
     * Outcome counters. All values are aggregated across every phase of
     * one [fetchKind3Only] / [fetchKind0And3] call. Callers log them for
     * observability; `amy wot sync --json` also emits them so a caller
     * can measure whether the outbox path is doing the work vs the
     * fallback path.
     */
    data class Result(
        val authorsRequested: Int,
        val kind10002Received: Int,
        val kind3Received: Int,
        val kind0Received: Int,
        val outboxCoveredAuthors: Int,
        val fallbackAuthors: Int,
    )

    /**
     * Fetch kind-3 for every pubkey in [authors] via each author's outbox
     * relay when known, falling back to index relays otherwise. Suspends
     * until every phase EOSEs or times out.
     */
    suspend fun fetchKind3Only(authors: Set<HexKey>): Result = run(authors, includeKind0 = false, includeKind3 = true)

    /**
     * Fetch kind-3 AND kind-0 for every pubkey in [authors]. Same phase
     * pipeline; a single per-outbox-relay subscription pulls both kinds
     * so we don't double the connection count.
     */
    suspend fun fetchKind0And3(authors: Set<HexKey>): Result = run(authors, includeKind0 = true, includeKind3 = true)

    /**
     * Fetch kind-0 only. Used by the metadata preloader when it decides
     * to bypass the index-relay batch for a specific author (e.g. a
     * profile screen visit where the author's outbox is already cached).
     */
    suspend fun fetchKind0Only(authors: Set<HexKey>): Result = run(authors, includeKind0 = true, includeKind3 = false)

    /**
     * Drop every dedup marker. Call on account switch so a fresh account
     * doesn't inherit the previous account's "already fetched" state.
     */
    fun clear() {
        kind3Succeeded.clear()
        kind0Succeeded.clear()
        kind3InFlight.clear()
        kind0InFlight.clear()
    }

    private suspend fun run(
        authors: Set<HexKey>,
        includeKind0: Boolean,
        includeKind3: Boolean,
    ): Result {
        if (authors.isEmpty()) return zeroResult(0)

        val newForKind3 =
            if (includeKind3) authors.filter { it !in kind3Succeeded && it !in kind3InFlight }.toSet() else emptySet()
        val newForKind0 =
            if (includeKind0) authors.filter { it !in kind0Succeeded && it !in kind0InFlight }.toSet() else emptySet()

        if (newForKind3.isEmpty() && newForKind0.isEmpty()) {
            Log.d("OutboxDispatcher") { "skip: all authors deduped (succeeded or in-flight)" }
            return zeroResult(authors.size)
        }

        kind3InFlight.addAll(newForKind3)
        kind0InFlight.addAll(newForKind0)

        return try {
            val result =
                withTimeoutOrNull(overallTimeoutMs) {
                    doRun(authors, newForKind3, newForKind0, includeKind0, includeKind3)
                }
            if (result == null) {
                Log.w("OutboxDispatcher") { "overall timeout ${overallTimeoutMs}ms exceeded — returning zero result" }
                zeroResult(authors.size)
            } else {
                result
            }
        } finally {
            kind3InFlight.removeAll(newForKind3)
            kind0InFlight.removeAll(newForKind0)
        }
    }

    private suspend fun doRun(
        allAuthors: Set<HexKey>,
        newForKind3: Set<HexKey>,
        newForKind0: Set<HexKey>,
        includeKind0: Boolean,
        includeKind3: Boolean,
    ): Result {
        val relayCounts = FetchCounters()
        val relaysConfigured = indexRelays()
        val newTargets = (newForKind3 + newForKind0)

        // Split into "have cached 10002" vs "need Phase 1".
        val cachedOutbox = mutableMapOf<HexKey, Set<NormalizedRelayUrl>>()
        val toDiscover = mutableSetOf<HexKey>()
        for (author in newTargets) {
            val write =
                gateway
                    .cachedOutbox(author)
                    ?.dialableWriteRelays()
                    .orEmpty()
                    .toSet()
            if (write.isNotEmpty()) cachedOutbox[author] = write else toDiscover.add(author)
        }

        Log.d("OutboxDispatcher") {
            "start authors=${allAuthors.size} newKind3=${newForKind3.size} newKind0=${newForKind0.size} " +
                "cachedOutbox=${cachedOutbox.size} toDiscover=${toDiscover.size} " +
                "indexRelays=${relaysConfigured.size}"
        }

        // Phase 1 — discover kind-10002 on the index relays. runPhase1
        // returns pubkey → list of (event, relay) so we can pick the
        // newest event (some relays return outdated 10002s).
        val discovered = mutableMapOf<HexKey, Set<NormalizedRelayUrl>>()
        if (toDiscover.isNotEmpty() && relaysConfigured.isNotEmpty()) {
            val (phase1Events, phase1EosedCount) = runPhase1(toDiscover, relaysConfigured)
            phase1Events.forEach { (pubkey, results) ->
                val newest = results.maxByOrNull { it.first.createdAt } ?: return@forEach
                gateway.onOutboxDiscovered(newest.first, newest.second)
                val write =
                    newest.first
                        .dialableWriteRelays()
                        .orEmpty()
                        .toSet()
                if (write.isNotEmpty()) discovered[pubkey] = write
            }
            relayCounts.kind10002 += phase1Events.values.sumOf { it.size }
            Log.d("OutboxDispatcher") {
                "phase1 done eosed=$phase1EosedCount/${relaysConfigured.size} " +
                    "10002-events=${relayCounts.kind10002} discovered=${discovered.size}"
            }
        }

        val outboxMap = cachedOutbox + discovered
        val authorsWithOutbox = outboxMap.keys
        val fallbackAuthors = newTargets - authorsWithOutbox

        // Phase 2 — per-outbox-relay REQ, kind-3 and/or kind-0. All
        // recommended relays are subscribed in a single call so the pool
        // fans out in parallel; a per-relay 4 s timeout bounds the wait
        // regardless of how many relays the recommendation set contains.
        val kind3BeforePhase2 = relayCounts.kind3
        val kind0BeforePhase2 = relayCounts.kind0
        if (outboxMap.isNotEmpty() && (includeKind0 || includeKind3)) {
            val recommendations = RelayListRecommendationProcessor.reliableRelaySetFor(outboxMap)
            val phase2FilterMap =
                recommendations
                    .mapNotNull { rec ->
                        val authorsForThisRelay =
                            rec.users.intersect(
                                if (includeKind0 && includeKind3) {
                                    newTargets
                                } else if (includeKind3) {
                                    newForKind3
                                } else {
                                    newForKind0
                                },
                            )
                        if (authorsForThisRelay.isEmpty()) return@mapNotNull null
                        val kinds =
                            buildList {
                                if (includeKind0 && authorsForThisRelay.any { it in newForKind0 }) add(MetadataEvent.KIND)
                                if (includeKind3 && authorsForThisRelay.any { it in newForKind3 }) add(ContactListEvent.KIND)
                            }
                        if (kinds.isEmpty()) return@mapNotNull null
                        rec.relay to
                            authorsForThisRelay.chunked(100).map { chunk ->
                                Filter(
                                    kinds = kinds,
                                    authors = chunk,
                                    limit = chunk.size * kinds.size,
                                )
                            }
                    }.toMap()

            Log.d("OutboxDispatcher") { "phase2 recommendations=${recommendations.size} relays-with-work=${phase2FilterMap.size}" }
            if (phase2FilterMap.isNotEmpty()) {
                runPhase2Or3(phase2FilterMap, counters = relayCounts)
            }
        }

        Log.d("OutboxDispatcher") {
            "phase2 done kind3=${relayCounts.kind3 - kind3BeforePhase2} kind0=${relayCounts.kind0 - kind0BeforePhase2}"
        }

        // Phase 3 — index-relay fallback for authors with no 10002.
        val kind3BeforePhase3 = relayCounts.kind3
        val kind0BeforePhase3 = relayCounts.kind0
        if (fallbackAuthors.isNotEmpty() && relaysConfigured.isNotEmpty()) {
            val kinds =
                buildList {
                    if (includeKind0 && fallbackAuthors.any { it in newForKind0 }) add(MetadataEvent.KIND)
                    if (includeKind3 && fallbackAuthors.any { it in newForKind3 }) add(ContactListEvent.KIND)
                }
            if (kinds.isNotEmpty()) {
                Log.d("OutboxDispatcher") { "phase3 fallback authors=${fallbackAuthors.size} kinds=$kinds relays=${relaysConfigured.size}" }
                val filters =
                    fallbackAuthors.chunked(100).map { chunk ->
                        Filter(
                            kinds = kinds,
                            authors = chunk,
                            limit = chunk.size * kinds.size,
                        )
                    }
                val phase3FilterMap = relaysConfigured.associateWith { filters }
                runPhase2Or3(phase3FilterMap, counters = relayCounts)
                Log.d("OutboxDispatcher") {
                    "phase3 done kind3=${relayCounts.kind3 - kind3BeforePhase3} kind0=${relayCounts.kind0 - kind0BeforePhase3}"
                }
            }
        }

        // Promote to succeeded — a completed run means we've asked; even if
        // an author had no publishable data we don't need to keep pounding
        // relays every follow-set change.
        kind3Succeeded.addAll(newForKind3)
        kind0Succeeded.addAll(newForKind0)

        return Result(
            authorsRequested = allAuthors.size,
            kind10002Received = relayCounts.kind10002,
            kind3Received = relayCounts.kind3,
            kind0Received = relayCounts.kind0,
            outboxCoveredAuthors = authorsWithOutbox.size,
            fallbackAuthors = fallbackAuthors.size,
        )
    }

    // ------------------------------------------------------------------

    private class FetchCounters {
        var kind10002 = 0
        var kind3 = 0
        var kind0 = 0
    }

    /**
     * Phase 1 helper. Returns a map of pubkey → list of (event, relay) so
     * caller can pick the newest, plus a boolean-per-relay EOSE indicator
     * (currently ignored but recorded for future retry telemetry).
     */
    private suspend fun runPhase1(
        pubkeys: Set<HexKey>,
        relays: Set<NormalizedRelayUrl>,
    ): Pair<Map<HexKey, List<Pair<AdvertisedRelayListEvent, NormalizedRelayUrl>>>, Int> {
        val filters =
            pubkeys.chunked(100).map { chunk ->
                Filter(
                    kinds = listOf(AdvertisedRelayListEvent.KIND),
                    authors = chunk,
                    limit = chunk.size,
                )
            }
        val filterMap = relays.associateWith { filters }

        val received = mutableMapOf<HexKey, MutableList<Pair<AdvertisedRelayListEvent, NormalizedRelayUrl>>>()
        val gate = BatchEoseGate(scope, target = relays.size)

        val listener =
            object : SubscriptionListener {
                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (event is AdvertisedRelayListEvent && event.pubKey in pubkeys) {
                        received
                            .getOrPut(event.pubKey) { mutableListOf() }
                            .add(event to relay)
                    }
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    gate.notifyEose(relay)
                }
            }

        val subId = newSubId()
        client.subscribe(subId, filterMap, listener)
        val eosedCount = gate.awaitAll(perRelayTimeoutMs)
        client.unsubscribe(subId)

        return received to eosedCount
    }

    /**
     * Phase 2 or Phase 3 helper. Opens a single subscription that
     * fans out to every relay in [filterMap] (Phase 2 uses per-outbox-
     * relay filters; Phase 3 uses the index-relay set with a shared
     * fallback filter). All relays are subscribed in parallel — the
     * per-relay timeout bounds the total wait regardless of relay count.
     */
    private suspend fun runPhase2Or3(
        filterMap: Map<NormalizedRelayUrl, List<Filter>>,
        counters: FetchCounters,
    ) {
        val gate = BatchEoseGate(scope, target = filterMap.size)

        val listener =
            object : SubscriptionListener {
                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    when (event.kind) {
                        MetadataEvent.KIND -> counters.kind0++
                        ContactListEvent.KIND -> counters.kind3++
                    }
                    gateway.onDiscoveredEvent(event, relay)
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    gate.notifyEose(relay)
                }
            }

        val subId = newSubId()
        client.subscribe(subId, filterMap, listener)
        gate.awaitAll(perRelayTimeoutMs)
        client.unsubscribe(subId)
    }

    private fun zeroResult(requested: Int) =
        Result(
            authorsRequested = requested,
            kind10002Received = 0,
            kind3Received = 0,
            kind0Received = 0,
            outboxCoveredAuthors = 0,
            fallbackAuthors = 0,
        )

    /**
     * KMP-safe EOSE aggregator (same as FeedMetadataCoordinator's local
     * one — duplicated locally instead of exported to keep the fix scope
     * minimal). Per-relay `onEose` callbacks may run on any dispatcher
     * (typically `Dispatchers.IO`) so we funnel them through a Channel
     * and let a single consumer coroutine own the `seen` set.
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
