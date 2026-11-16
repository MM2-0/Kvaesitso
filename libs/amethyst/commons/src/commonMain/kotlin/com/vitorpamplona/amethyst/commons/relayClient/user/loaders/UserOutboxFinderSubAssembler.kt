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
package com.vitorpamplona.amethyst.commons.relayClient.user.loaders

import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.BaseEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.forEachChunk
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderQueryState
import com.vitorpamplona.amethyst.commons.relayClient.user.pickRelaysToLoadUsers
import com.vitorpamplona.amethyst.commons.relays.EOSEAccountFast
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.groupByRelay
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.TimeUtils

class UserOutboxFinderSubAssembler(
    client: INostrClient,
    val cache: ICacheProvider,
    val failureTracker: RelayOfflineTracker,
    allKeys: () -> Set<UserFinderQueryState>,
) : BaseEoseManager<UserFinderQueryState>(client, allKeys) {
    val relayListKinds = listOf(MetadataEvent.KIND, AdvertisedRelayListEvent.KIND)

    /**
     * This assembler saves the EOSE per user key. That EOSE includes their metadata, etc
     * and reports, but only from trusted accounts (follows of all logged in users).
     *
     * Fitted to the users still missing a relay list on every pass ([EOSEAccountFast.fitTo]): a feed
     * hands over whole lists (see [UserFinderQueryState.isFeedAuthor]), not just the users on screen.
     */
    var hasTried: EOSEAccountFast<User> = EOSEAccountFast(200)

    fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>? = null,
    ) {
        filters?.forEach {
            it.authors?.forEach {
                cache.getUserIfExists(it)?.let { user ->
                    hasTried.newEose(user, relay, time)
                }
            }
        }
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
            },
        )

    override fun updateSubscriptions(keys: Set<UserFinderQueryState>) {
        val uniqueSubscribedAccounts = keys.distinctBy { it.user }
        val newFilters = updateFilter(uniqueSubscribedAccounts)?.ifEmpty { null }
        sub.updateFilters(newFilters?.groupByRelay())
    }

    fun updateFilter(keys: List<UserFinderQueryState>): List<RelayBasedFilter>? {
        val noOutboxList = mutableSetOf<User>()

        keys.forEach {
            if (it.user.authorRelayList() == null) {
                noOutboxList.add(it.user)
            }
        }

        hasTried.fitTo(noOutboxList.size)

        if (noOutboxList.isEmpty()) return null

        val accounts = keys.mapTo(mutableSetOf()) { it.account }
        val connectedRelays = client.connectedRelaysFlow().value

        // Attributed only when one account is asking. Unlike the reports sweep, the relay choice here
        // is made from the *union* of every logged-in account's tiers (`pickRelaysToLoadUsers` below),
        // and the users being resolved are whoever is on screen rather than anyone's follow list — so
        // with several accounts active there is no single honest owner for a given filter, and
        // splitting the sweep per account would re-issue the same lookups once per account.
        // Deduped by pubkey, not by account identity: two objects for the same logged-in user would
        // look like two accounts and suppress attribution entirely.
        val soleAccountPubKey =
            accounts
                .mapTo(mutableSetOf()) { it.userFinderPubkeyHex }
                .singleOrNull()

        // Union of every asking account's relay tiers. The UserFinderAccount getters already apply the
        // platform default fallbacks (index/search), matching the prior outer pickRelaysToLoadUsers.
        val cannotConnect = failureTracker.cannotConnectRelays
        val indexRelays = mutableSetOf<NormalizedRelayUrl>()
        val homeRelays = mutableSetOf<NormalizedRelayUrl>()
        val searchRelays = mutableSetOf<NormalizedRelayUrl>()
        val commonRelays = mutableSetOf<NormalizedRelayUrl>()
        accounts.forEach { account ->
            indexRelays.addAll(account.indexRelays())
            homeRelays.addAll(account.outboxHomeRelays())
            searchRelays.addAll(account.searchRelays())
            commonRelays.addAll(account.commonRelays())
        }

        val perRelayKeysBoth =
            pickRelaysToLoadUsers(
                noOutboxList,
                cache.relayHints,
                indexRelays - cannotConnect,
                homeRelays - cannotConnect,
                searchRelays - cannotConnect,
                connectedRelays,
                commonRelays - cannotConnect,
                cannotConnect,
                hasTried,
            )

        // Chunked like the metadata watcher: a feed hands over whole lists, and a relay that clamps
        // `limit` answers only part of an oversized batch, silently.
        val activeFilters = mutableListOf<RelayBasedFilter>()
        perRelayKeysBoth.forEach { (relay, pubkeys) ->
            forEachChunk(pubkeys.sorted()) { chunk ->
                activeFilters.add(
                    RelayBasedFilter(
                        relay = relay,
                        filter =
                            ExplainedFilter(
                                kinds = relayListKinds,
                                authors = chunk,
                                purpose = SubPurpose.RELAY_LISTS,
                                accountPubKeys = listOfNotNull(soleAccountPubKey),
                            ),
                    ),
                )
            }
        }

        // Users that pickRelaysToLoadUsers could not place anywhere this pass:
        // every candidate relay tier (outbox/hints/index/search/connected/common)
        // is already in hasTried. Without a fallback they'd be silently dropped
        // and a late-published kind 10002 would never reach the UI.
        val placedPubkeys =
            perRelayKeysBoth.values
                .asSequence()
                .flatten()
                .toSet()
        //
        // Only for users on screen: a feed's authors are dropped after one round instead. A list can
        // hold thousands of members that never published a relay list (a mute list of spam bots),
        // and keeping all of them in a standing REQ on every default relay costs more than it finds.
        val onScreenUsers = keys.mapNotNullTo(mutableSetOf()) { key -> key.user.takeUnless { key.isFeedAuthor } }
        val abandonedPubkeys =
            noOutboxList.mapNotNullTo(mutableSetOf<HexKey>()) { user ->
                user.pubkeyHex.takeIf { it !in placedPubkeys && user in onScreenUsers }
            }

        if (abandonedPubkeys.isEmpty()) return activeFilters

        val sortedAbandoned = abandonedPubkeys.sorted()
        val fallbackRelays =
            (DefaultIndexerRelayList + DefaultSearchRelayList) - failureTracker.cannotConnectRelays
        fallbackRelays.forEach { relay ->
            forEachChunk(sortedAbandoned) { chunk ->
                activeFilters.add(
                    RelayBasedFilter(
                        relay = relay,
                        filter =
                            ExplainedFilter(
                                kinds = relayListKinds,
                                authors = chunk,
                                purpose = SubPurpose.RELAY_LISTS,
                                purposeDetail = "outbox discovery for users whose relay list we lost",
                                accountPubKeys = listOfNotNull(soleAccountPubKey),
                            ),
                    ),
                )
            }
        }

        return activeFilters
    }
}
