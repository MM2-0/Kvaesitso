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
package com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserAndFollowListEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/** Turns a resolved top-nav selection into the per-relay REQs for one feed kind. */
typealias TopNavFeedFilterMaker = (
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long?,
) -> List<RelayBasedFilter>

/**
 * The EOSE manager every top-nav-driven feed shares: one relay subscription per logged-in user +
 * selected list, re-issued when the selection changes, when its per-relay resolution changes, or
 * when a watched feed floor moves. Subclasses supply [updateFilter]; [floors], [onListChanged] and
 * [extraInvalidators] tune what re-issues the REQ.
 */
abstract class TopNavFeedSubAssembler<K : TopNavFeedQueryState>(
    client: INostrClient,
    allKeys: () -> Set<K>,
) : PerUserAndFollowListEoseManager<K, TopFilter>(client, allKeys) {
    override fun user(key: K) = key.account.userProfile()

    override fun list(key: K) = key.listName.value

    /** Runs when the key's top-nav selection changes, right before the filters are re-issued. */
    protected open fun onListChanged(key: K) {}

    /** How often (ms) a moving feed floor may re-issue the REQ; the video swipe feed wants a tighter cadence. */
    protected open val floorSampleMs: Long = 5000

    /** The feed floors that re-issue the REQ (sampled) when they move. Defaults to every feed on the key. */
    protected open fun floors(key: K): List<StateFlow<Long?>> = key.feeds.map { it.lastNoteCreatedAtWhenFullyLoaded }

    /** Additional flows whose every emission re-issues the REQ, unsampled. */
    protected open fun extraInvalidators(key: K): List<Flow<*>> = emptyList()

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: K): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            buildList {
                add(
                    key.scope.launch(Dispatchers.IO) {
                        key.listName.collectLatest {
                            onListChanged(key)
                            invalidateFilters()
                        }
                    },
                )
                add(
                    key.scope.launch(Dispatchers.IO) {
                        key.followsPerRelay.sample(500).collectLatest {
                            invalidateFilters()
                        }
                    },
                )
                val floors = floors(key)
                if (floors.isNotEmpty()) {
                    add(
                        key.scope.launch(Dispatchers.IO) {
                            combine(floors) { }.sample(floorSampleMs).collectLatest {
                                invalidateFilters()
                            }
                        },
                    )
                }
                extraInvalidators(key).forEach { flow ->
                    add(
                        key.scope.launch(Dispatchers.IO) {
                            flow.collectLatest {
                                invalidateFilters()
                            }
                        },
                    )
                }
            }

        return super.newSub(key)
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        userJobMap.remove(key)?.forEach { it.cancel() }
    }
}

/**
 * A top-nav feed whose REQ is one [makeFilter] dispatcher over the selection, bounded by the
 * key's (single) feed floor.
 *
 * @param resetEoseOnListChange drop the per-relay EOSE cursor when the user switches lists. Needed
 *   when the feed shows addressables held only weakly by the cache: while the user views list B the
 *   strong refs from list A's UI are gone and the GC may reclaim those notes; a cursor that still
 *   says "you have everything up to T" would then stop the relay from re-sending them.
 */
open class SingleTopNavFeedSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<TopNavFeedQueryState>,
    private val makeFilter: TopNavFeedFilterMaker,
    private val resetEoseOnListChange: Boolean = false,
    override val floorSampleMs: Long = 5000,
) : TopNavFeedSubAssembler<TopNavFeedQueryState>(client, allKeys) {
    override fun updateFilter(
        key: TopNavFeedQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> = makeFilter(key.followsPerRelay.value, since, key.feeds.firstOrNull()?.lastNoteCreatedAtIfFilled())

    override fun onListChanged(key: TopNavFeedQueryState) {
        if (resetEoseOnListChange) clearEoseFor(key)
    }
}
