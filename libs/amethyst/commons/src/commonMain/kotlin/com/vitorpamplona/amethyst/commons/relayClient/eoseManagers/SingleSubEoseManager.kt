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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.relays.EOSERelayList
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.groupByRelay
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * This query type creates only ONE relay subscription. It filters duplicates
 * by DistinctById.uniqueId() but it doesn't create a new subscription for each.
 *
 * All filters are passed as a single sub.
 *
 * It is ideal for temporary filters, including event, user finding that must be
 * disabled after the user is found.
 *
 * This class keeps EOSEs for as long as possible and
 * shares all EOSEs among all users.
 */
abstract class SingleSubEoseManager<T>(
    client: INostrClient,
    allKeys: () -> Set<T>,
    val invalidateAfterEose: Boolean = false,
) : BaseEoseManager<T>(client, allKeys) {
    // long term EOSE cache
    private val latestEOSEs = EOSERelayList()

    fun since() = latestEOSEs.since()

    /**
     * Drops one relay's EOSE cursor so the next assembly asks it from scratch.
     *
     * This manager keeps a single cursor per relay for the whole key set, which is right while that
     * set is stable and wrong the moment it grows: a key that joins later inherits a cursor earned by
     * keys that were already there, and everything older than it is never requested. Subclasses whose
     * filters merge keys together call this when a relay's set of keys gains a member.
     */
    protected fun clearEoseFor(relay: NormalizedRelayUrl) {
        latestEOSEs.remove(relay)
    }

    /** What [sinceDroppingGrownRelays] remembers each relay's filters last covered. */
    private val keysPerRelay = MergedAuthorTracker()

    /**
     * [clearEoseFor] on every relay whose set of keys in [current] gained a member since the last
     * assembly, and [since] without those relays, for the filters being assembled right now — the
     * map handed in is a copy-on-write snapshot, so clearing the cursor alone would not reach them.
     *
     * For subclasses that serve several entities (profiles, communities, repositories, …) from this
     * one subscription: the second one opened on a relay would otherwise be asked only for what is
     * newer than the first one's EOSE, and its history would never be requested at all.
     */
    protected fun sinceDroppingGrownRelays(
        current: Map<NormalizedRelayUrl, Collection<String>>,
        since: SincePerRelayMap?,
    ): SincePerRelayMap? {
        // Every relay goes through gainedAuthors, so each one's set is recorded, grown or not.
        val grown = current.filter { (relay, keys) -> keysPerRelay.gainedAuthors(relay, keys) }.keys
        if (grown.isEmpty()) return since
        grown.forEach(::clearEoseFor)
        return since?.filterKeys { it !in grown }?.toMutableMap()
    }

    open fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>? = null,
    ) {
        latestEOSEs.newEose(relay, time)
        if (invalidateAfterEose) {
            invalidateFilters()
        }
    }

    val sub =
        requestNewSubscription(
            object : SubscriptionListener {
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    newEose(relay, TimeUtils.now(), forFilters)
                }

                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (isLive) {
                        newEose(relay, TimeUtils.now(), forFilters)
                    }
                }
            },
        )

    override fun updateSubscriptions(keys: Set<T>) {
        val uniqueSubscribedAccounts = keys.distinctBy { distinct(it) }
        val newFilters = updateFilter(uniqueSubscribedAccounts, since())?.ifEmpty { null }

        sub.updateFilters(newFilters?.groupByRelay())
    }

    abstract fun updateFilter(
        keys: List<T>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>?

    abstract fun distinct(key: T): Any
}
