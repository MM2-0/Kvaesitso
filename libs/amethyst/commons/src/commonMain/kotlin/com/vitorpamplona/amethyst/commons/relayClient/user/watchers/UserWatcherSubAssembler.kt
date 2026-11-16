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
package com.vitorpamplona.amethyst.commons.relayClient.user.watchers

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.BaseEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderQueryState
import com.vitorpamplona.amethyst.commons.relays.EOSEAccountFast
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.groupByRelay
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils

class UserWatcherSubAssembler(
    client: INostrClient,
    val cache: ICacheProvider,
    val failureTracker: RelayOfflineTracker,
    allKeys: () -> Set<UserFinderQueryState>,
) : BaseEoseManager<UserFinderQueryState>(client, allKeys) {
    /**
     * This assembler saves the EOSE per user key. That EOSE includes their metadata, etc
     * and reports, but only from trusted accounts (follows of all logged in users).
     *
     * Fitted to the watched users on every pass ([EOSEAccountFast.fitTo]): a feed hands over whole
     * lists (see [UserFinderQueryState.isFeedAuthor]), and an evicted user loses its `since` and is
     * downloaded again in full.
     */
    var latestEOSEs: EOSEAccountFast<User> = EOSEAccountFast(200)

    fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>? = null,
    ) {
        filters?.forEach {
            it.authors?.forEach {
                cache.getUserIfExists(it)?.let { user ->
                    latestEOSEs.newEose(user, relay, time)
                }
            }
        }
        // Re-evaluate filters: filterUserMetadataForKey may widen to indexer
        // relays once a user's outbox is exhausted without yielding metadata.
        invalidateFilters()
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

    override fun updateSubscriptions(keys: Set<UserFinderQueryState>) {
        val users = keys.mapTo(mutableSetOf()) { it.user }
        latestEOSEs.fitTo(users.size)

        if (users.isEmpty()) {
            sub.updateFilters(null)
            return
        }

        // assembles all index relays from all accounts
        // Attributed only when one account is looking: the index relays below are pooled across every
        // account and the users are whoever is on screen, so with several askers none of them owns it.
        val soleAccountPubKey =
            keys
                .mapTo(mutableSetOf()) { it.account.userFinderPubkeyHex }
                .singleOrNull()

        val indexRelays = mutableSetOf<NormalizedRelayUrl>()
        keys.mapTo(mutableSetOf()) { it.account }.forEach {
            indexRelays.addAll(it.indexRelays())
        }

        val newFilters =
            filterUserMetadataForKey(
                users,
                cache.relayHints,
                indexRelays,
                failureTracker.cannotConnectRelays,
                latestEOSEs,
                soleAccountPubKey,
            ).ifEmpty { null }

        sub.updateFilters(newFilters?.groupByRelay())
    }
}
