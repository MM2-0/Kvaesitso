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
package com.vitorpamplona.amethyst.commons.relays.subscriptions

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * "Why do I have this many subscriptions right now?"
 *
 * Pivots on **purpose**, not on relay. The relay-shaped view cannot answer the question: a probe
 * holding 670 filters across 168 relays looks like one unremarkable chip repeated on 168 rows,
 * whereas here it is a single line that reads `Notifications · 670 filters · 168 relays` and is
 * immediately obviously the largest thing running.
 *
 * Grouped by account first because several accounts are normally logged in and they do not share
 * relay sets — a total that mixes them cannot be acted on.
 *
 * Everything is a **snapshot**, polled rather than observed: `activeRequests` is a plain map read
 * off the relay pool with no change feed, and subscriptions churn constantly (every EOSE advances a
 * `since`). Polling on a visible screen is honest and cheap; a push feed would mean instrumenting
 * the pool for a diagnostic screen.
 */
@Immutable
data class SubscriptionEntityRow(
    /** Null when the filter named no entity — "the rest of this purpose", not a real entity. */
    val entityId: HexKey?,
    /**
     * The top-nav selection behind a filter that names no entity — what a discovery filter is
     * searching *within*. Null for filters that name a real entity, which speaks for itself.
     */
    val scope: IFeedTopNavPerRelayFilter?,
    val detail: String?,
    val relays: List<NormalizedRelayUrl>,
    /**
     * How many in-flight filters name this entity.
     *
     * **Not summable across entities.** One batched filter names every chat its relay serves, so it
     * counts once for each of them — summing these to get a purpose's total is how "6 chats on 6
     * relays" once read as 144 filters when 24 were on the wire. [SubscriptionPurposeRow.filterCount]
     * counts filters, and is the only number that belongs next to a total.
     */
    val namedInFilters: Int,
)

/**
 * What makes two filters the same row.
 *
 * [scopeKey] is the scope's **type**, not its contents: an author-based selection carries a
 * different slice of the follow list to every relay, so keying on contents would shatter "People you
 * follow" into one row per relay — the opposite of what this screen is for. Selections whose
 * contents are the same everywhere (a hashtag, a geohash) render theirs from the retained instance.
 */
private data class EntityKey(
    val entityId: HexKey?,
    val scopeKey: String?,
)

@Immutable
data class SubscriptionPurposeRow(
    val purpose: SubPurpose,
    /** Filters actually in flight for this purpose — the same unit as [ActiveSubscriptionsState.totalFilters]. */
    val filterCount: Int,
    val relays: List<NormalizedRelayUrl>,
    val entities: List<SubscriptionEntityRow>,
)

/**
 * One purpose's tally for one account, accumulated in a single pass.
 *
 * [filters] is incremented once per filter; [entities] records the same filter against every entity
 * it names. Keeping both means the card can report a real filter count while still breaking down
 * which chats or communities that filter is for.
 */
private class PurposeTally {
    var filters = 0
    val relays = mutableSetOf<NormalizedRelayUrl>()
    val entities = mutableMapOf<EntityKey, MutableList<NormalizedRelayUrl>>()
}

@Immutable
data class SubscriptionAccountRow(
    /** Null groups everything not yet attributed to an account. Shown last, never hidden. */
    val accountPubKey: HexKey?,
    val filterCount: Int,
    val relays: List<NormalizedRelayUrl>,
    val purposes: List<SubscriptionPurposeRow>,
)

@Immutable
data class ActiveSubscriptionsState(
    val accounts: List<SubscriptionAccountRow> = emptyList(),
    val totalFilters: Int = 0,
    val totalRelays: Int = 0,
    /** Filters in flight that carry no purpose — assemblers not yet migrated. Honesty, not a bug. */
    val untaggedFilters: Int = 0,
    /**
     * The sum of the per-account counts, which is what a card's share must be drawn against.
     *
     * It exceeds [totalFilters] when a filter serves several accounts at once — one merged
     * notifications REQ naming four pubkeys is one filter on the wire but four accounts' worth of
     * explanation. Dividing a per-account count by [totalFilters] would compare the two units and
     * overstate every card, which is the mistake the per-entity rows already taught once.
     */
    val attributedFilters: Int = 0,
)

class ActiveSubscriptionsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ActiveSubscriptionsState())
    val state: StateFlow<ActiveSubscriptionsState> = _state.asStateFlow()

    /**
     * Polls until the caller's scope is cancelled. Run it from the screen's effect, so leaving the
     * screen stops it; a loop in `viewModelScope` would outlive the screen, and each return to it
     * would stack another. [REFRESH_MS] is slow enough to be free, fast enough to feel live.
     */
    suspend fun pollWhileShown(client: INostrClient) {
        while (true) {
            _state.value = snapshot(client)
            delay(REFRESH_MS)
        }
    }

    private suspend fun snapshot(client: INostrClient): ActiveSubscriptionsState =
        withContext(Dispatchers.IO) {
            aggregateSubscriptions(
                client.connectedRelaysFlow().value.associateWith { relay ->
                    client.activeRequests(relay).values.flatten()
                },
            )
        }

    companion object {
        private const val REFRESH_MS = 2000L
    }
}

/**
 * Folds the in-flight filters into the screen's rows.
 *
 * Pure and separate from the ViewModel so the invariant it exists to keep — every tagged filter
 * counted **exactly once**, so a purpose's count shares a unit with the total it is drawn against —
 * is testable without a relay pool. It did not hold before: purposes summed their per-entity rows,
 * and a batched filter naming six chats counted six times.
 */
fun aggregateSubscriptions(filtersByRelay: Map<NormalizedRelayUrl, List<Filter>>): ActiveSubscriptionsState {
    // account -> purpose -> tally (real filter count + relays + per-entity breakdown)
    val byAccount = mutableMapOf<HexKey?, MutableMap<SubPurpose, PurposeTally>>()
    val detailOf = mutableMapOf<Pair<SubPurpose, EntityKey>, String?>()
    val scopeOf = mutableMapOf<Pair<SubPurpose, EntityKey>, IFeedTopNavPerRelayFilter>()
    var total = 0
    var untagged = 0
    val allRelays = mutableSetOf<NormalizedRelayUrl>()

    filtersByRelay.forEach { (relay, filters) ->
        filters.forEach { filter ->
            total++
            val explained = filter as? ExplainedFilter
            if (explained == null) {
                untagged++
                return@forEach
            }
            allRelays.add(relay)
            // Discovery filters name no entity — they go looking for things rather than
            // serving known ones — but they do carry the selection they search within, which
            // is what keeps them from collapsing into one nameless row per purpose.
            val scopeKey = explained.scope?.let { it::class.simpleName }

            // A merged filter serves several accounts at once — notifications are `#p`-scoped, so
            // every account reading a relay is asked for in one filter naming all of them. It shows
            // under each of those accounts, because "why is this relay busy for me" has to be
            // answerable per account. That makes the per-account counts a breakdown of a shared
            // filter rather than a partition of the total, which is what [attributedFilters] exists
            // to keep straight.
            val accounts: List<HexKey?> = explained.accountPubKeys?.takeIf { it.isNotEmpty() } ?: listOf(null)
            accounts.forEach { account ->
                val tally =
                    byAccount
                        .getOrPut(account) { mutableMapOf() }
                        .getOrPut(explained.purpose) { PurposeTally() }

                // Once per account this filter serves, never once per entity it names.
                tally.filters++
                tally.relays.add(relay)

                // A batched filter serves several entities at once — relay-group state is one #d
                // filter per host relay carrying every joined group on it — so it contributes a
                // row to each of them rather than collapsing to "All". These rows are a
                // breakdown, never a total: see [SubscriptionEntityRow.namedInFilters].
                val entities: List<HexKey?> = explained.entityIds?.takeIf { it.isNotEmpty() } ?: listOf(null)
                entities.forEach { entityId ->
                    val key = EntityKey(entityId, scopeKey)
                    tally.entities.getOrPut(key) { mutableListOf() }.add(relay)
                    detailOf[explained.purpose to key] = explained.purposeDetail
                    explained.scope?.let { scopeOf.getOrPut(explained.purpose to key) { it } }
                }
            }
        }
    }

    val accounts =
        byAccount
            .map { (account, purposes) ->
                val purposeRows =
                    purposes
                        .map { (purpose, tally) ->
                            val entityRows =
                                tally.entities
                                    .map { (key, relays) ->
                                        SubscriptionEntityRow(
                                            entityId = key.entityId,
                                            scope = scopeOf[purpose to key],
                                            detail = detailOf[purpose to key],
                                            relays = relays.distinct().sortedBy { it.url },
                                            namedInFilters = relays.size,
                                        )
                                    }.sortedByDescending { it.namedInFilters }
                            SubscriptionPurposeRow(
                                purpose = purpose,
                                // The tally, NOT the sum of the entity rows — a batched filter
                                // appears in one row per entity it names.
                                filterCount = tally.filters,
                                relays = tally.relays.sortedBy { it.url },
                                entities = entityRows,
                            )
                        }.sortedByDescending { it.filterCount }
                SubscriptionAccountRow(
                    accountPubKey = account,
                    filterCount = purposeRows.sumOf { it.filterCount },
                    relays = purposeRows.flatMap { it.relays }.distinct(),
                    purposes = purposeRows,
                )
            }
            // unattributed group last, so it reads as a remainder rather than a headline
            .sortedWith(compareBy<SubscriptionAccountRow> { it.accountPubKey == null }.thenByDescending { it.filterCount })

    return ActiveSubscriptionsState(
        accounts = accounts,
        totalFilters = total,
        totalRelays = allRelays.size,
        untaggedFilters = untagged,
        attributedFilters = accounts.sumOf { it.filterCount },
    )
}
