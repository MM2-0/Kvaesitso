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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvites
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmChannels
import com.vitorpamplona.amethyst.commons.model.buzz.ChannelClassification
import com.vitorpamplona.amethyst.commons.model.buzz.buzzChannelTypes
import com.vitorpamplona.amethyst.commons.model.buzz.classifyBuzzChannel
import com.vitorpamplona.amethyst.commons.model.buzz.membershipNoticeFilter
import com.vitorpamplona.amethyst.commons.model.buzz.membershipNotices
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.RELAY_GROUP_METADATA_KINDS
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Always-on discovery of the viewer's Buzz **DM channels** across every joined workspace relay, mounted
 * once high in the logged-in tree ([com.vitorpamplona.amethyst.commons.ui.app.AmethystApp]).
 *
 * The deployed relay does not expose a queryable DM list; it addresses each member a kind-44100
 * member-added notification (`#p` = me). Those arrive through the ordinary subscription pipeline —
 * [com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.buzz.BuzzMembershipEoseManager]
 * owns the one `#p=me` REQ per workspace relay — so this reads them back out of [LocalCache], fetches
 * each discovered channel's 39000-39003 directory (so its `t`=dm marker + participants land), and
 * records the ones that turn out to be DMs into [BuzzDmChannels]. The companion
 * [com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.BuzzDmJoinedChatTailPreload]
 * then keeps those channels' messages warm app-wide — which is what lets a Buzz DM show on the
 * Notifications tab and in push without the viewer opening the conversation first.
 *
 * Everything else somebody added the viewer to is a named channel; it must NOT be silently subscribed,
 * so it stays out of [BuzzDmChannels] and surfaces as a prompt instead — see
 * [com.vitorpamplona.amethyst.commons.model.buzz.ChannelInvitesState], which projects the
 * same cached notices.
 *
 * ### Recompute, don't accumulate
 *
 * Each pass derives the whole membership picture from the cache and *declares* the result
 * ([BuzzDmChannels.replace]). The previous incremental version — record every 44100, then delete the
 * ones classification rejected — had no memory of its own rejections, so the next delivery of the same
 * event re-added them and the two steps fought each other in a loop. A recomputation cannot fight
 * itself: the same events always produce the same set.
 */
@Composable
fun BuzzDmDiscoveryPreload(accountViewModel: AccountViewModel) {
    val account = accountViewModel.account

    LaunchedEffect(account) {
        runBuzzDmDiscovery(account)
    }
}

/**
 * Recompute the viewer's DM set from the cached membership notices, fetching any directory still
 * missing, and keep doing it for the lifetime of the caller's scope.
 */
private suspend fun runBuzzDmDiscovery(account: Account) {
    val me = account.userProfile().pubkeyHex

    combine(
        LocalCache.observeNotes(membershipNoticeFilter(me)),
        // A channel's type is only decidable once its kind-39000 is in the cache, and that lands
        // *after* the notice that revealed the channel — so the directory arriving has to re-run the
        // classification. The emission carries the types themselves rather than a count of notes,
        // because `LocalCache.consume(GroupMetadataEvent)` wakes this observer before it copies the
        // event into the channel: classifying off the channel at this instant reads one that is still
        // empty and answers UNKNOWN, and nothing emits again to correct it. See [buzzChannelTypes].
        LocalCache
            .observeNotes(Filter(kinds = listOf(GroupMetadataEvent.KIND)))
            .map { buzzChannelTypes(it) }
            .distinctUntilChanged(),
    ) { _, knownTypes ->
        // Read the joined set per pass, not once: which relay vouched for a notice depends on it,
        // and restore-from-disk can land after the cache already holds notices.
        val workspaces = account.buzzWorkspaces.flow.value
        BuzzChannelInvites.currentMemberships(LocalCache.membershipNotices(me, workspaces)) to knownTypes
    }.collectLatest { (memberships, knownTypes) ->
        fetchMissingDirectories(account, memberships, knownTypes)
        BuzzDmChannels.replace(
            me,
            memberships.filter { (id, relay) ->
                classifyBuzzChannel(LocalCache, id, relay, knownTypes) == ChannelClassification.DM
            },
        )
    }
}

/**
 * Fetch the NIP-29 directory (39000-39003) of every channel whose type we don't know yet, so its `t`=dm
 * marker and roster load.
 *
 * Only the unclassified ones: a channel keeps its metadata in the cache for the session, so re-asking
 * for it on every pass would put a burst of `#d` reads on the relay each time a single new notice
 * arrives. This converges — the fetch lands the 39000, which re-runs the pass, which now finds nothing
 * missing.
 */
private suspend fun fetchMissingDirectories(
    account: Account,
    memberships: Map<String, NormalizedRelayUrl>,
    knownTypes: Map<String, ChannelClassification>,
) {
    val byRelay =
        memberships
            .filterKeys { id -> memberships[id]?.let { classifyBuzzChannel(LocalCache, id, it, knownTypes) } == ChannelClassification.UNKNOWN }
            .entries
            .groupBy({ it.value }, { it.key })
            .mapValues { (_, ids) -> listOf(ExplainedFilter(kinds = RELAY_GROUP_METADATA_KINDS, tags = mapOf("d" to ids), purpose = SubPurpose.RELAY_GROUPS)) }
    if (byRelay.isEmpty()) return
    account.client.fetchAllWithHooks(filters = byRelay, idleTimeoutMs = 8_000, pendingOnAuthRequired = true) { _, _ -> false }
}
