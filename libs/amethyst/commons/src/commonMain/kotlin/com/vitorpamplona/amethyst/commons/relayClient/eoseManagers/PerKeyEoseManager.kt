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

import com.vitorpamplona.amethyst.commons.relays.EOSECache
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.groupByRelay
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Generic per-key EOSE manager that creates a subscription for each unique key.
 *
 * This query type creates a new relay subscription for every distinct key K
 * extracted from query state T. It is ideal for screens that need separate
 * subscriptions per entity (user, thread, etc.).
 *
 * @param T The query state type (e.g., ThreadQueryState)
 * @param K The key type used to deduplicate subscriptions (e.g., String noteId)
 *
 * This class keeps EOSEs for each key for as long as possible and can be
 * shared among multiple query states that map to the same key.
 */
abstract class PerKeyEoseManager<T, K : Any>(
    client: INostrClient,
    allKeys: () -> Set<T>,
    val invalidateAfterEose: Boolean = false,
    cacheSize: Int = 200,
) : BaseEoseManager<T>(client, allKeys) {
    // EOSE cache keyed by K
    private val latestEOSEs = EOSECache<K>(cacheSize)

    // Map from key K to subscription ID
    private val keySubscriptionMap = mutableMapOf<K, String>()

    /**
     * Get the since map for a query state's key.
     */
    fun since(queryState: T): SincePerRelayMap? = latestEOSEs.since(extractKey(queryState))

    /**
     * Record a new EOSE for a query state.
     */
    open fun newEose(
        queryState: T,
        relayUrl: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>? = null,
    ) {
        latestEOSEs.newEose(extractKey(queryState), relayUrl, time)
        if (invalidateAfterEose) {
            invalidateFilters()
        }
    }

    /**
     * Create a new subscription for a query state.
     */
    open fun newSub(queryState: T): Subscription =
        requestNewSubscription(
            object : SubscriptionListener {
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    newEose(queryState, relay, TimeUtils.now(), forFilters)
                }

                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (isLive) {
                        newEose(queryState, relay, TimeUtils.now(), forFilters)
                    }
                }
            },
        )

    /**
     * End a subscription for a key.
     */
    open fun endSub(
        key: K,
        subId: String,
    ) {
        dismissSubscription(subId)
        keySubscriptionMap.remove(key)
    }

    /**
     * Find or create a subscription for a query state.
     */
    fun findOrCreateSubFor(queryState: T): Subscription {
        val key = extractKey(queryState)
        val subId = keySubscriptionMap[key]
        return if (subId == null) {
            newSub(queryState).also { keySubscriptionMap[key] = it.id }
        } else {
            getSubscription(subId) ?: newSub(queryState).also { keySubscriptionMap[key] = it.id }
        }
    }

    override fun updateSubscriptions(keys: Set<T>) {
        val uniqueByKey = keys.distinctBy { extractKey(it) }

        val updatedKeys = mutableSetOf<K>()

        uniqueByKey.forEach { queryState ->
            val key = extractKey(queryState)
            val newFilters = updateFilter(queryState, since(queryState))?.ifEmpty { null }
            findOrCreateSubFor(queryState).updateFilters(newFilters?.groupByRelay())
            updatedKeys.add(key)
        }

        // Clean up subscriptions for keys no longer active
        keySubscriptionMap.filter { it.key !in updatedKeys }.forEach {
            endSub(it.key, it.value)
        }
    }

    /**
     * Build filters for a query state.
     * @return List of relay-based filters, or null to clear the subscription
     */
    abstract fun updateFilter(
        queryState: T,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>?

    /**
     * Extract the deduplication key from a query state.
     * Subscriptions are shared among query states with the same key.
     */
    abstract fun extractKey(queryState: T): K
}
