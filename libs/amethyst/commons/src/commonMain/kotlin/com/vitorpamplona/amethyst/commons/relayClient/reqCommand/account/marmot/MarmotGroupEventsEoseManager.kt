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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.marmot

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * EOSE manager for Marmot GroupEvent (kind:445) subscriptions.
 *
 * Uses filters from [MarmotSubscriptionManager.buildFilters()] which
 * include per-group kind:445 filters. These filters are sent to the
 * group's relays on connect/reconnect and when groups are added/removed.
 */
class MarmotGroupEventsEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val manager = key.account.marmotManager ?: return emptyList()
        if (!key.account.isWriteable()) return emptyList()
        if (!key.account.chatFeedToggles.isEnabled(ChatFeedType.MARMOT)) return emptyList()

        val result = mutableListOf<RelayBasedFilter>()
        val fallbackRelays = key.account.homeRelays.flow.value

        // Per-group kind:445 filters — route each to the group's own relays
        val groupStates = manager.subscriptionManager.activeGroupIdsSnapshot()
        Log.d("MarmotDbg") {
            "MarmotGroupEventsEoseManager.updateFilter: ${groupStates.size} active group(s), fallbackRelays=${fallbackRelays.size}"
        }
        for (groupId in groupStates) {
            val filter =
                manager.subscriptionManager.let { sub ->
                    // Build the filter for this specific group
                    val groupFilters = sub.activeGroupFiltersSnapshot()
                    // activeGroupFilters() returns one filter per group; match by tag content
                    groupFilters.find { f ->
                        f.tags?.any { it.value.contains(groupId) } == true
                    }
                } ?: continue

            // Use group-specific relays from MLS metadata; fall back to home relays
            val metadata = manager.groupView(groupId)
            val groupRelays =
                metadata
                    ?.relays
                    ?.mapNotNull {
                        com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
                            .normalizeOrNull(it)
                    }?.toSet()
            val relaysForGroup = if (!groupRelays.isNullOrEmpty()) groupRelays else fallbackRelays
            Log.d("MarmotDbg") {
                "MarmotGroupEventsEoseManager.updateFilter: group=${groupId.take(8)}… → ${relaysForGroup.size} relay(s) " +
                    "(metadataRelays=${groupRelays?.size ?: 0}, usingFallback=${groupRelays.isNullOrEmpty()}): ${relaysForGroup.map { it.url }}"
            }
            for (relay in relaysForGroup) {
                result.add(RelayBasedFilter(relay = relay, filter = ExplainedFilter.of(filter, SubPurpose.ENCRYPTED_GROUPS, "MLS group messages", entityIds = listOf(groupId))))
            }
        }

        // Own KeyPackage filter (kind:30443) — use home relays
        if (fallbackRelays.isNotEmpty()) {
            val ownKeyPackageFilter = manager.subscriptionManager.ownKeyPackageFilter()
            for (relay in fallbackRelays) {
                result.add(RelayBasedFilter(relay = relay, filter = ExplainedFilter.of(ownKeyPackageFilter, SubPurpose.ENCRYPTED_GROUPS, "own MLS key package")))
            }
        }

        Log.d("MarmotDbg") {
            "MarmotGroupEventsEoseManager.updateFilter: emitting ${result.size} RelayBasedFilter(s)"
        }
        return result
    }

    override val watchedChatFeeds = setOf(ChatFeedType.MARMOT)

    val userJobMap = mutableMapOf<User, List<Job>>()

    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.homeRelays.flow.collect {
                        invalidateFilters()
                    }
                },
                // Critical: invalidate the kind:445 filter set whenever a
                // group is joined (via Welcome processing), created, or
                // left. Without this the per-group h-tag subscription is
                // never sent to relays after the initial connect, so an
                // invitee that just joined a group would never actually
                // receive any of its messages until the next app restart.
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.marmotGroupList.groupListChanges.collect { changedGroupId ->
                        Log.d("MarmotDbg") {
                            "MarmotGroupEventsEoseManager: groupListChanges → ${changedGroupId.take(8)}… invalidating filters"
                        }
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
