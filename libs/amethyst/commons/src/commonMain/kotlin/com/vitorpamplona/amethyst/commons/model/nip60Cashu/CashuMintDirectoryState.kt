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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuMintDirectoryFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuMintDirectoryQueryState
import com.vitorpamplona.amethyst.commons.util.ConcurrentSet
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip87Ecash.cashu.CashuMintEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One mint as it appears in the NIP-87 directory.
 *
 * The same mint URL can be announced more than once (different operators,
 * different keysets, different metadata). Picker UI typically shows the most
 * recent announcement and aggregates recommendation counts across all
 * announcements for the same URL.
 */
@Immutable
data class CashuMintDirectoryEntry(
    val url: String,
    /** Most recent announcement we've seen for this URL, if any. */
    val announcement: CashuMintEvent?,
    /** Total kind:38000 recommendations targeting this mint's announcement. */
    val recommendationCount: Int,
    /** Recommendations specifically by [followedPubkeys] — surfaced first in the picker. */
    val followsRecommendationCount: Int,
    /**
     * Pubkeys of followed users who have recommended this mint, capped at
     * [MAX_FOLLOWS_RECOMMENDER_AVATARS] for cheap rendering. Length equals
     * `min(followsRecommendationCount, MAX_FOLLOWS_RECOMMENDER_AVATARS)`.
     * The remaining `followsRecommendationCount - size` followers are
     * implied by the count, not enumerated here.
     */
    val followsRecommenderPubkeys: List<HexKey>,
) {
    companion object {
        const val MAX_FOLLOWS_RECOMMENDER_AVATARS: Int = 6
    }
}

/**
 * Account-scoped index of cashu mint announcements + recommendations.
 *
 * Reactive: backfills from [LocalCache.notes] on first observer + listens to
 * [LocalCache.live.newEventBundles] for incremental updates. The relay-side
 * subscription is started/stopped via [open] / [close] so the directory only
 * costs network bandwidth while a picker is on screen.
 *
 * Recommendation counts are derived per refresh from the latest snapshot —
 * duplicate kind:38000 events from the same (pubkey, mint) pair are
 * deduplicated so a single recommender can't inflate counts by re-posting.
 */
class CashuMintDirectoryState(
    private val cache: LocalCache,
    private val scope: CoroutineScope,
    private val assembler: CashuMintDirectoryFilterAssembler,
    private val followsFlow: StateFlow<Set<HexKey>>,
) {
    /** Mint announcements keyed by the announcement event id. */
    private val announcements = ConcurrentMap<HexKey, CashuMintEvent>()

    /** Recommendations keyed by event id — value is the parsed event. */
    private val recommendations = ConcurrentMap<HexKey, MintRecommendationEvent>()

    private val _entries = MutableStateFlow<List<CashuMintDirectoryEntry>>(emptyList())

    /**
     * Mint directory sorted by:
     *   1. Number of recommendations from users we follow (descending)
     *   2. Total recommendation count (descending)
     *   3. Mint URL (ascending — stable tiebreaker)
     */
    val entries: StateFlow<List<CashuMintDirectoryEntry>> =
        combine(_entries, followsFlow) { all, follows -> rank(all, follows) }
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val size: StateFlow<Int> =
        entries.map { it.size }.stateIn(scope, SharingStarted.Eagerly, 0)

    // ============================================================
    // Subscription lifecycle (open while a picker is on screen)
    // ============================================================
    private var currentSubscription: CashuMintDirectoryQueryState? = null
    private val openers = ConcurrentSet<Any>()
    private val liveJobLock = KmpLock()
    private var liveJob: Job? = null

    /**
     * Begin observing new mint events. Idempotent — multiple openers can
     * share one subscription, and the relay subscription is dropped when the
     * last opener calls [close]. [opener] is any unique object the caller
     * holds (typically the ViewModel instance) so we can ref-count cleanly.
     */
    fun open(
        opener: Any,
        relays: Set<NormalizedRelayUrl>,
    ) {
        if (openers.size == 0) {
            // First opener — start the live indexer + relay subscription.
            startLiveIndex()
            backfillFromCacheAsync()
        }
        openers.add(opener)
        syncSubscription(relays)
    }

    fun close(opener: Any) {
        openers.remove(opener)
        if (openers.size == 0) {
            currentSubscription?.let { runCatching { assembler.unsubscribe(it) } }
            currentSubscription = null
            liveJobLock.withLock {
                liveJob?.cancel()
                liveJob = null
            }
        }
    }

    private fun syncSubscription(relays: Set<NormalizedRelayUrl>) {
        val previous = currentSubscription
        if (relays.isEmpty()) {
            previous?.let { runCatching { assembler.unsubscribe(it) } }
            currentSubscription = null
            return
        }
        if (previous != null && previous.relays == relays) return

        previous?.let { runCatching { assembler.unsubscribe(it) } }
        val next = CashuMintDirectoryQueryState(relays)
        currentSubscription = next
        assembler.subscribe(next)
    }

    private fun startLiveIndex() {
        liveJobLock.withLock {
            if (liveJob != null) return
            liveJob =
                scope.launch(Dispatchers.IO) {
                    cache.live.newEventBundles.collect { notes ->
                        val touched =
                            notes.mapNotNull { note ->
                                when (val e = note.event) {
                                    is CashuMintEvent -> {
                                        announcements[e.id] = e
                                        true
                                    }
                                    is MintRecommendationEvent -> {
                                        if (e.isCashuRecommendation()) {
                                            recommendations[e.id] = e
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    else -> false
                                }
                            }
                        if (touched.isNotEmpty()) rebuildEntries()
                    }
                }
        }
    }

    private fun backfillFromCacheAsync() {
        scope.launch(Dispatchers.IO) {
            // NIP-87 kinds are addressable: the current version lives in `addressables` (its
            // per-id note is weakly held and pruned once superseded). Both maps are keyed by id.
            val visit = { note: Note ->
                when (val e = note.event) {
                    is CashuMintEvent -> announcements[e.id] = e
                    is MintRecommendationEvent -> if (e.isCashuRecommendation()) recommendations[e.id] = e
                    else -> Unit
                }
            }
            cache.notes.forEach { _, note -> visit(note) }
            cache.addressables.forEach { _, note -> visit(note) }
            rebuildEntries()
        }
    }

    // ============================================================
    // Indexing
    // ============================================================
    private fun rebuildEntries() {
        // 1. Group announcements by mint URL — most recent wins for display.
        // One copy for both passes, so byUrl and urlByDTag describe the same set of announcements.
        val announced = announcements.snapshot().values
        val byUrl: MutableMap<String, CashuMintEvent> = HashMap()
        announced.forEach { e ->
            val url = e.mintUrl() ?: return@forEach
            val existing = byUrl[url]
            if (existing == null || e.createdAt > existing.createdAt) byUrl[url] = e
        }

        // 2. Build a per-URL set of announcement event ids so a recommendation
        //    aTag → that mint's bucket. We use the d-tag (mint pubkey) per
        //    NIP-87 to match recommendations to announcements — but many
        //    clients also store the mint URL in the `u` tag of the
        //    recommendation, so we accept both.
        val urlByDTag: MutableMap<String, String> = HashMap()
        announced.forEach { e ->
            val d = e.dTag()
            val url = e.mintUrl()
            // dTag() is "" when the tag is missing: such announcements can't be matched by d-tag.
            if (d.isNotEmpty() && url != null) urlByDTag[d] = url
        }

        // 3. Count recommendations per URL, deduplicating by (recommender, mint URL).
        val perUrlAll: MutableMap<String, MutableSet<HexKey>> = HashMap()
        val perUrlFollows: MutableMap<String, MutableSet<HexKey>> = HashMap()
        val followSet = currentFollowSnapshot()

        recommendations.asMap().values.forEach { rec ->
            // Mints recommended via `u` tag(s) — directly carry the URL.
            val urlsFromU = rec.mintUrls()
            // Mints recommended via `a` tag(s) — look up the URL by mint d-tag.
            val urlsFromA =
                rec
                    .mintEventAddresses()
                    .mapNotNull { aTag ->
                        // a-tag is "kind:pubkey:dTag" — we want the dTag.
                        aTag.split(":").getOrNull(2)?.let(urlByDTag::get)
                    }
            (urlsFromU + urlsFromA).distinct().forEach { url ->
                perUrlAll.getOrPut(url) { mutableSetOf() }.add(rec.pubKey)
                if (rec.pubKey in followSet) {
                    perUrlFollows.getOrPut(url) { mutableSetOf() }.add(rec.pubKey)
                }
            }
        }

        // 4. Build the final entries list — every URL with either an
        //    announcement or a recommendation gets surfaced.
        val allUrls = byUrl.keys + perUrlAll.keys
        _entries.value =
            allUrls.map { url ->
                val followsRecs = perUrlFollows[url].orEmpty()
                CashuMintDirectoryEntry(
                    url = url,
                    announcement = byUrl[url],
                    recommendationCount = perUrlAll[url]?.size ?: 0,
                    followsRecommendationCount = followsRecs.size,
                    // Cap the per-row pubkey list — the row only renders
                    // a small avatar gallery; the full count is preserved
                    // in followsRecommendationCount for the "+N more" suffix.
                    followsRecommenderPubkeys =
                        followsRecs.take(CashuMintDirectoryEntry.MAX_FOLLOWS_RECOMMENDER_AVATARS).toList(),
                )
            }
    }

    private fun currentFollowSnapshot(): Set<HexKey> = followsFlow.value

    private fun rank(
        all: List<CashuMintDirectoryEntry>,
        @Suppress("UNUSED_PARAMETER") follows: Set<HexKey>,
    ): List<CashuMintDirectoryEntry> =
        all.sortedWith(
            compareByDescending<CashuMintDirectoryEntry> { it.followsRecommendationCount }
                .thenByDescending { it.recommendationCount }
                .thenBy { it.url },
        )

    // ============================================================
    // Convenience accessors
    // ============================================================

    /** Find a directory entry by exact URL match (used to surface metadata for an already-known mint). */
    fun lookup(url: String): CashuMintDirectoryEntry? = entries.value.firstOrNull { it.url == url }

    /**
     * Substring search ranked by the same comparator as [entries] —
     * follows recommendations first, then total recommendations, then
     * URL. Empty [query] returns the top mints overall, which is what
     * the autocomplete dropdown shows before the user starts typing.
     *
     * Backs both the add-cashu-wallet autocomplete and the wallet
     * settings "Add recommendation" flow; merging them onto one row
     * composable lets both surfaces show the recommender avatar
     * gallery and the +N suffix without each rolling their own join.
     */
    fun search(
        query: String,
        limit: Int = 6,
    ): List<CashuMintDirectoryEntry> {
        val needle = query.trim().lowercase()
        val all = entries.value
        val filtered =
            if (needle.isEmpty()) {
                all
            } else {
                all.filter {
                    it.url.lowercase().contains(needle) ||
                        (it.announcement?.content ?: "").lowercase().contains(needle) ||
                        (it.announcement?.dTag() ?: "").lowercase().contains(needle)
                }
            }
        return filtered.take(limit)
    }
}
