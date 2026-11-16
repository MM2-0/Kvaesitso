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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.publicChannels.dal.ChannelFeedViewModel
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.datasource.RelayGroupOpenChatHistorySubAssembler
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.chats.formatHistoryReachDate
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.relayGroupChannelLastReadRoute
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_group_invite_only_to_post
import com.vitorpamplona.amethyst.commons.resources.relay_group_join_to_post
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.DmHistoryLoadingCard
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachCursor
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachMarkers
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachSentinels
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachState
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatRoomVouches
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.datasource.ChannelFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.LoadRelayGroupChannel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupOpenChatHistorySubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupOpenChatTailSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.send.ChannelNewMessageViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.send.EditFieldRow
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayPagingProgress
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Composable
fun RelayGroupChannelView(
    channelId: GroupId?,
    draft: Note? = null,
    replyTo: Note? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (channelId == null) return

    LoadRelayGroupChannel(channelId) { group ->
        PrepareChannelViewModels(
            baseChannel = group,
            draft = draft,
            replyTo = replyTo,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun PrepareChannelViewModels(
    baseChannel: RelayGroupChannel,
    draft: Note? = null,
    replyTo: Note? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedViewModel: ChannelFeedViewModel =
        rememberViewModel(
            key = baseChannel.groupId.toKey() + "ChannelFeedViewModel",
            factory =
                ChannelFeedViewModel.Factory(
                    baseChannel,
                    accountViewModel.account,
                ),
        )

    val channelScreenModel: ChannelNewMessageViewModel = rememberViewModel { ChannelNewMessageViewModel() }
    channelScreenModel.init(accountViewModel)
    channelScreenModel.load(baseChannel)

    if (draft != null) {
        LaunchedEffect(draft, channelScreenModel, accountViewModel) {
            channelScreenModel.editFromDraft(draft)
        }
    }

    if (replyTo != null) {
        LaunchedEffect(replyTo, channelScreenModel, accountViewModel) {
            channelScreenModel.reply(replyTo)
        }
    }

    ChannelView(
        channel = baseChannel,
        feedViewModel = feedViewModel,
        newPostModel = channelScreenModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
private fun ChannelView(
    channel: RelayGroupChannel,
    feedViewModel: ChannelFeedViewModel,
    newPostModel: ChannelNewMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedViewModel)
    // Metadata/pins live-watch (roster comes from the always-on state sub; this only keeps the pinned-id
    // back-fill firing when the pin list changes). The old fixed-window content path here is superseded
    // by the live tail + history pager below.
    ChannelFilterAssemblerSubscription(channel, accountViewModel.dataSources().channel, accountViewModel)

    // Recent chat (live) + on-demand backward history, the group analog of the NIP-04 per-conversation
    // stack. The tail also covers a non-joined group opened by link (not in the batched preview tail).
    RelayGroupOpenChatTailSubscription(channel.groupId, accountViewModel.dataSources().relayGroupOpenChatTail, accountViewModel)
    RelayGroupOpenChatHistorySubscription(channel.groupId, accountViewModel.dataSources().relayGroupOpenChatHistory, accountViewModel)

    val history = remember(accountViewModel) { accountViewModel.dataSources().relayGroupOpenChatHistory.history }
    val loadingHistory by history.loadingMore.collectAsStateWithLifecycle()
    val historyStatus by history.status.collectAsStateWithLifecycle()
    val limits =
        remember(historyStatus) {
            buildList {
                if (!historyStatus.exhausted) {
                    historyStatus.relayProgress.forEach { (relay, p) ->
                        add(RelayReachCursor("nip29:${relay.url}", relayShortName(relay), p.reachedUntil, reachState(p), "Group") { history.advance(relay) })
                    }
                }
            }
        }
    RelayGroupBackfillHistoryToWindow(feedViewModel.feedState, history)

    // Collect the metadata flow once for the whole screen: it drives both the pinned-message
    // bar (kind-39005 pins) and the composer gating (roster membership), and updates the moment
    // a pin lands or my join is accepted.
    val channelState by channel
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val liveChannel = channelState.channel as? RelayGroupChannel ?: channel

    // A pinned-bar tap requests an in-feed jump; the feed consumes it, scrolls + highlights,
    // then clears it back to null so the same pin can be tapped again later.
    val jumpToNoteId = remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxHeight()) {
        RelayGroupMigrationBar(
            channel = liveChannel,
            accountViewModel = accountViewModel,
            nav = nav,
        )

        RelayGroupPinnedBar(
            channel = liveChannel,
            accountViewModel = accountViewModel,
            onJumpToNote = { jumpToNoteId.value = it.idHex },
        )

        RelayGroupSubgroupsBar(
            channel = liveChannel,
            accountViewModel = accountViewModel,
            nav = nav,
        )

        Column(
            modifier =
                remember {
                    Modifier
                        .fillMaxHeight()
                        .padding(vertical = 0.dp)
                        .weight(1f, true)
                },
        ) {
            // Who the room vouches for, rebuilt when its metadata changes (a new speaker, member).
            val channelState by channel
                .flow()
                .metadata.stateFlow
                .collectAsStateWithLifecycle()
            val vouches = remember(channelState) { ChatRoomVouches(channel.memberKeys()) }
            RefreshingChatroomFeedView(
                feedContentState = feedViewModel.feedState,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = relayGroupChannelLastReadRoute(channel.groupId),
                avoidDraft = newPostModel.draftTag,
                onWantsToReply = newPostModel::reply,
                onWantsToEditDraft = newPostModel::editFromDraft,
                onWantsToEditChatMessage = newPostModel::editBuzzMessage,
                jumpToNoteId = jumpToNoteId,
                onJumpHandled = { jumpToNoteId.value = null },
                // A status card at the oldest end: what it's reaching for while paging, "All caught up" when dry.
                olderBoundary = {
                    DmHistoryLoadingCard(
                        "Group",
                        "Group",
                        loadingHistory,
                        historyStatus.exhausted,
                        historyStatus.relayCount,
                        historyStatus.stalledCount,
                        historyStatus.reachedBack,
                        historyStatus.relayProgress,
                        ::formatHistoryReachDate,
                    )
                },
                // The host relay's window-limit marker at its reached cursor (pure UI). Hidden when exhausted.
                markersInGap =
                    if (limits.isEmpty()) {
                        null
                    } else {
                        { newer, older -> RelayReachMarkers(limits, newer, older) {} }
                    },
                // Pulls the next page while its marker is on screen, off viewport visibility.
                sentinels =
                    if (limits.isEmpty()) {
                        null
                    } else {
                        { items, listState ->
                            RelayReachSentinels(limits, listState) { index -> items.getOrNull(index)?.event?.createdAt }
                        }
                    },
                collapseOutsideNetwork = vouches,
            )
        }

        // Buzz workspaces surface a live "… is typing" row just above the composer, fed by
        // ephemeral kind-20002 heartbeats. Only rendered on a Buzz-dialect relay (the kind is
        // Buzz-only); a no-op otherwise.
        if (BuzzRelayDialect.isBuzz(channel.groupId.relayUrl)) {
            BuzzTypingIndicator(
                channelId = channel.groupId.id,
                myPubkey = accountViewModel.userProfile().pubkeyHex,
                accountViewModel = accountViewModel,
            )
        }

        Spacer(modifier = DoubleVertSpacer)

        // Show the composer wherever the relay would actually accept my write: any roster
        // member/mod/admin, plus any authenticated member of an open Buzz channel (which accepts
        // writes without a per-channel join). Otherwise explain why. See [RelayGroupChannel.canPost].
        val canPost = liveChannel.canPost(accountViewModel.userProfile().pubkeyHex)

        if (canPost) {
            EditFieldRow(
                newPostModel,
                accountViewModel,
                onSendNewMessage = feedViewModel.feedState::sendToTop,
                nav,
            )
        } else {
            JoinToPostNotice(liveChannel)
        }
    }
}

/** The number of messages a freshly-opened group eagerly backfills to before paging goes demand-driven. */
private const val RELAY_GROUP_HISTORY_TARGET = 50

/**
 * On open, eagerly backfill this group's older history until the feed holds at least
 * [RELAY_GROUP_HISTORY_TARGET] messages (or the host relay is exhausted) — mirroring the Concord channel
 * backfill. The live tail only carries the recent window, so without this a group with plenty of history
 * opens showing just its last few messages until the user scrolls. Once the target is reached, paging is
 * purely demand-driven by the markers' visibility. A short startup delay skips the transient empty feed
 * navigation flashes through.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun RelayGroupBackfillHistoryToWindow(
    feedContentState: FeedContentState,
    history: RelayGroupOpenChatHistorySubAssembler,
) {
    LaunchedEffect(feedContentState, history) {
        delay(1200L)
        val loadedCount =
            feedContentState.feedContent.flatMapLatest { state ->
                when (state) {
                    is FeedState.Loaded -> state.feed.map { it.list.size }
                    else -> flowOf(0)
                }
            }
        combine(loadedCount, history.loadingMore, history.status) { count, loading, status ->
            count < RELAY_GROUP_HISTORY_TARGET && !loading && !status.exhausted
        }.distinctUntilChanged()
            .filter { it }
            .collect { history.advanceAll() }
    }
}

private fun reachState(p: RelayPagingProgress): RelayReachState =
    when {
        p.done -> RelayReachState.DONE
        p.stalled -> RelayReachState.STALLED
        else -> RelayReachState.REACHING
    }

private fun relayShortName(relay: NormalizedRelayUrl): String =
    relay.url
        .removePrefix("wss://")
        .removePrefix("ws://")
        .removeSuffix("/")

/**
 * Shown in place of the composer when I'm not (yet) a member: a relay group won't accept my kind-9
 * chat until its roster lists me, so typing would only earn a silent relay rejection. Points me at
 * the top-bar Join action (open groups) or explains the invite requirement (closed groups).
 */
@Composable
private fun JoinToPostNotice(channel: RelayGroupChannel) {
    val message =
        if (channel.isClosed()) {
            stringRes(Res.string.relay_group_invite_only_to_post)
        } else {
            stringRes(Res.string.relay_group_join_to_post)
        }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
