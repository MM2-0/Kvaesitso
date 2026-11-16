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
package com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.filterRelayGroupsByAuthors
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.filterRelayGroupsDiscovery
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.relayGroupChannelsByRelay
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserAndFollowListEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.scopedTo
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class RelayGroupsDiscoverySubAssembler(
    client: INostrClient,
    allKeys: () -> Set<RelayGroupsDiscoveryQueryState>,
) : PerUserAndFollowListEoseManager<RelayGroupsDiscoveryQueryState, TopFilter>(client, allKeys) {
    override fun updateFilter(
        key: RelayGroupsDiscoveryQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val feedSettings = key.followsPerRelay()
        val defaultSince = key.feedStates.relayGroupsDiscoveryFeed.lastNoteCreatedAtIfFilled()

        val base = filterRelayGroupsDiscovery(LocalCache, feedSettings, since, defaultSince)

        // The follow-list filter sets resolve their relays via the outbox model (a follow's own
        // publish relays), but a NIP-29 roster (39001/39002) lives ONLY on the group's host relay.
        // So a follow who is an admin/member of a group never surfaces unless we also ask that host
        // relay for `#p:<follows>` rosters. Without this, All-Follows shows nothing until the user
        // bounces through Global (which pulls the whole directory) — the reported bug.
        val (follows, alreadyQueried) =
            when (feedSettings) {
                is AllFollowsTopNavPerRelayFilterSet ->
                    feedSettings.set.values.flatMapTo(HashSet<HexKey>()) { it.authors.orEmpty() } to feedSettings.set.keys
                is AuthorsTopNavPerRelayFilterSet ->
                    feedSettings.set.values.flatMapTo(HashSet<HexKey>()) { it.authors } to feedSettings.set.keys
                else -> return base
            }
        if (follows.isEmpty()) return base

        // Query the follows as `#p` against every group-host relay we already know about (joined via
        // kind-10009 + favorited via kind-10012), minus the relays the outbox filter already covers.
        val hostRelays = key.groupHostRelays() - alreadyQueried
        if (hostRelays.isEmpty()) return base

        val channelsByRelay = relayGroupChannelsByRelay(LocalCache)
        val extra =
            hostRelays.flatMap { relay ->
                filterRelayGroupsByAuthors(
                    relay = relay,
                    authors = follows,
                    since = since?.get(relay)?.time ?: defaultSince,
                    cachedChannels = channelsByRelay[relay].orEmpty(),
                )
            }

        // `base` is scoped by its own builder; these host-relay rosters are built here, so they get
        // the same selection stamped on them rather than showing up as an unexplained extra.
        return base + extra.scopedTo(feedSettings)
    }

    /** Relays that host NIP-29 groups the user is connected to: joined (kind-10009) + favorited (kind-10012). */
    private fun RelayGroupsDiscoveryQueryState.groupHostRelays(): Set<NormalizedRelayUrl> =
        buildSet {
            account.relayGroupList.liveRelayGroupServers.value.forEach { server ->
                RelayUrlNormalizer.normalizeOrNull(server)?.let(::add)
            }
            addAll(account.favoriteRelayList.flow.value)
        }

    override fun user(key: RelayGroupsDiscoveryQueryState) = key.account.userProfile()

    override fun list(key: RelayGroupsDiscoveryQueryState) = key.listName()

    fun RelayGroupsDiscoveryQueryState.listNameFlow() = account.settings.defaultRelayGroupsDiscoveryFollowList

    fun RelayGroupsDiscoveryQueryState.listName() = listNameFlow().value

    fun RelayGroupsDiscoveryQueryState.followsPerRelayFlow() = account.liveRelayGroupsDiscoveryFollowListsPerRelay

    fun RelayGroupsDiscoveryQueryState.followsPerRelay() = followsPerRelayFlow().value

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: RelayGroupsDiscoveryQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.scope.launch(Dispatchers.IO) {
                    key.listNameFlow().collectLatest {
                        invalidateFilters()
                    }
                },
                key.scope.launch(Dispatchers.IO) {
                    key.followsPerRelayFlow().sample(500).collectLatest {
                        invalidateFilters()
                    }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    key.feedStates.relayGroupsDiscoveryFeed.lastNoteCreatedAtWhenFullyLoaded.sample(5000).collectLatest {
                        invalidateFilters()
                    }
                },
                // A #p roster hit (39001/39002) discovers a group but carries no 39000, so re-run
                // the assembly when rosters land — that fires the #d metadata backfill for groups
                // where a follow is an admin/member but whose metadata isn't cached yet.
                key.account.scope.launch(Dispatchers.IO) {
                    LocalCache.live.newEventBundles.sample(2000).collectLatest { bundle ->
                        if (bundle.any { it.event is GroupAdminsEvent || it.event is GroupMembersEvent }) {
                            invalidateFilters()
                        }
                    }
                },
                // Joining/leaving a group or favoriting a relay changes the host-relay set we probe
                // for follow rosters above, so re-assemble when either list moves.
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.relayGroupList.liveRelayGroupServers.sample(500).collectLatest {
                        invalidateFilters()
                    }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.favoriteRelayList.flow.sample(500).collectLatest {
                        invalidateFilters()
                    }
                },
            )

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
