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

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.attributedTo
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.groupByRelay
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * This query type creates only ONE relay subscription and does not
 * store any EOSE cache. The EOSE is irrelevant for these types of
 * filters
 */
abstract class SingleSubNoEoseCacheEoseManager<T>(
    client: INostrClient,
    allKeys: () -> Set<T>,
    val invalidateAfterEose: Boolean = false,
) : BaseEoseManager<T>(client, allKeys) {
    val sub =
        requestNewSubscription(
            object : SubscriptionListener {
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (invalidateAfterEose) {
                        invalidateFilters()
                    }
                }

                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (isLive && invalidateAfterEose) {
                        invalidateFilters()
                    }
                }
            },
        )

    override fun updateSubscriptions(keys: Set<T>) {
        val uniqueSubscribedAccounts = keys.distinctBy { distinct(it) }
        val newFilters =
            updateFilter(uniqueSubscribedAccounts)
                ?.ifEmpty { null }
                // Attribute to the account that owns this subscription, once, here — rather than
                // threading a pubkey through every filter builder underneath. Builders that already
                // know their account keep what they set.
                ?.let { f -> accountPubKeyOf(uniqueSubscribedAccounts.firstOrNull())?.let { pk -> f.attributedTo(pk) } ?: f }
        sub.updateFilters(newFilters?.groupByRelay())
    }

    abstract fun updateFilter(keys: List<T>): List<RelayBasedFilter>?

    abstract fun distinct(key: T): Any

    /**
     * The account behind [key], when the key is account-scoped. Null for keys about other users.
     *
     * Account-agnostic in commons: front ends that want single-account attribution override this
     * (see the amethyst `AccountScopedSingleSubNoEoseCacheEoseManager`, which reads
     * `(key as? AccountScopedQuery)?.account?.userProfile()?.pubkeyHex`). The default returns null,
     * so pooled / cross-account subscriptions are filed as "not attributed".
     */
    open fun accountPubKeyOf(key: Any?): String? = null
}
