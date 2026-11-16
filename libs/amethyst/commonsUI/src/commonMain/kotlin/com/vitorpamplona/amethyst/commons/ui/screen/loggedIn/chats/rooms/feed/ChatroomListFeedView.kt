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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.feed

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.chats.formatHistoryReachDate
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.relayClient.paging.PagingStatus
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chats_history_proto_nip04
import com.vitorpamplona.amethyst.commons.resources.chats_history_proto_nip17
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.feeds.DmHistoryLoadingCard
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedError
import com.vitorpamplona.amethyst.commons.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachCursor
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachDetailDialog
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachMarkers
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachSentinels
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachState
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.ChatroomHeaderCompose
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ChatListPadding
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.util.JavaSerializable
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayPagingProgress
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun ChatroomListFeedView(
    feedContentState: FeedContentState,
    scrollStateKey: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisposableEffect(Unit) {
        Log.d("DMPagination") { "rooms.list: OPEN" }
        onDispose { Log.d("DMPagination") { "rooms.list: CLOSE" } }
    }
    RefresheableBox(feedContentState, true) {
        SaveableFeedContentState(feedContentState, scrollStateKey) { listState ->
            CrossFadeState(feedContentState, listState, accountViewModel, nav)
        }
    }
}

@Composable
private fun CrossFadeState(
    feedContentState: FeedContentState,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedState by feedContentState.feedContent.collectAsStateWithLifecycle()

    // History is exhausted only once BOTH DM protocols have paged to their end (each stops when a
    // round of until+limit pages brings nothing back). Until then an empty feed means "still filling",
    // not "no conversations" — keep the spinner up rather than flash empty.
    val giftWrapsHistory = remember(accountViewModel) { accountViewModel.dataSources().account.giftWrapsHistory }
    val nip04History = remember(accountViewModel) { accountViewModel.dataSources().chatroomList.nip04History }
    val giftWrapsStatus by giftWrapsHistory.status.collectAsStateWithLifecycle()
    val nip04Status by nip04History.status.collectAsStateWithLifecycle()
    val historyExhausted = giftWrapsStatus.exhausted && nip04Status.exhausted

    // A *genuinely* empty list has no rows to host the per-relay window-limit markers, so we step every
    // relay one page at a time to hunt for the first rooms. Gated on FeedState.Empty only (never the
    // transient Loading that navigation flashes through) and debounced, so re-opening Messages with rooms
    // already loaded does NOT kick a hunt. Once rooms appear the markers take over, demand-driven.
    val user = accountViewModel.userProfile()
    val bootstrap = feedState is FeedState.Empty
    BootstrapHistoryWhenEmpty(bootstrap, giftWrapsHistory.loadingMore, giftWrapsHistory.status) { giftWrapsHistory.advanceAll() }
    BootstrapHistoryWhenEmpty(bootstrap, nip04History.loadingMore, nip04History.status) { nip04History.advanceAll() }

    CrossfadeIfEnabled(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
    ) { state ->
        when (state) {
            is FeedState.Empty -> {
                Column(Modifier.padding(rememberFeedContentPadding(FeedPadding))) {
                    if (historyExhausted) {
                        FeedEmpty { feedContentState.invalidateData() }
                    } else {
                        LoadingFeed()
                    }
                }
            }

            is FeedState.FeedError -> {
                FeedError(state.errorMessage) { feedContentState.invalidateData() }
            }

            is FeedState.Loaded -> {
                FeedLoaded(state, listState, accountViewModel, nav)
            }

            FeedState.Loading -> {
                LoadingFeed()
            }
        }
    }
}

@Composable
private fun FeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    val myPubKey = accountViewModel.userProfile().pubkeyHex

    val giftWrapsHistory = remember(accountViewModel) { accountViewModel.dataSources().account.giftWrapsHistory }
    val nip04History = remember(accountViewModel) { accountViewModel.dataSources().chatroomList.nip04History }
    val loadingGiftWraps by giftWrapsHistory.loadingMore.collectAsStateWithLifecycle()
    val loadingNip04 by nip04History.loadingMore.collectAsStateWithLifecycle()
    // One atomic snapshot per protocol (exhausted + relays + reached + per-relay progress) instead of six
    // separate collectors — the status card and the per-relay markers read all of it together anyway.
    val giftWrapsStatus by giftWrapsHistory.status.collectAsStateWithLifecycle()
    val nip04Status by nip04History.status.collectAsStateWithLifecycle()
    val user = accountViewModel.userProfile()
    val nip17Name = stringRes(Res.string.chats_history_proto_nip17)
    val nip04Name = stringRes(Res.string.chats_history_proto_nip04)
    val oldestNip17Index = items.list.indexOfLast { it.event is ChatroomKeyable && it.event !is EncryptedDmEvent }
    val oldestNip04Index = items.list.indexOfLast { it.event is EncryptedDmEvent }

    // Each relay's window limit, carrying the advance() that pulls its OWN next page. Placed in the list
    // at its reached depth as a sentinel (see RelayReachMarkers): a relay pages only while its
    // marker is on screen and keeps paging while it stays there, so a spam-dense relay never floods —
    // you have to scroll through its messages to pull more. A protocol drops out once exhausted.
    val limits =
        remember(giftWrapsStatus, nip04Status, user) {
            buildList {
                if (!giftWrapsStatus.exhausted) {
                    giftWrapsStatus.relayProgress.forEach { (relay, p) ->
                        add(RelayReachCursor("17:${relay.url}", relayShortName(relay), p.reachedUntil, reachState(p), "NIP-17") { giftWrapsHistory.advance(relay) })
                    }
                }
                if (!nip04Status.exhausted) {
                    nip04Status.relayProgress.forEach { (relay, p) ->
                        add(RelayReachCursor("04:${relay.url}", relayShortName(relay), p.reachedUntil, reachState(p), "NIP-04") { nip04History.advance(relay) })
                    }
                }
            }
        }

    // Hoisted load driver: pulls each relay's next page off viewport visibility, so feed reorders
    // (a live DM bumping a room) no longer re-fire paging. The markers below are pure UI.
    RelayReachSentinels(limits, listState) { index -> items.list.getOrNull(index)?.createdAt() }

    // The relays behind a tapped in-stream "Relay sync" marker; non-null shows the per-relay popup so the
    // terse divider isn't a dead end — every count/name is one tap from the full breakdown (which relays,
    // protocol, how far back each paged).
    var syncDetail by remember { mutableStateOf<List<RelayReachCursor>?>(null) }
    syncDetail?.let { detail ->
        RelayReachDetailDialog(detail, ::formatHistoryReachDate) { syncDetail = null }
    }

    LazyColumn(
        contentPadding = rememberFeedContentPadding(ChatListPadding),
        state = listState,
    ) {
        itemsIndexed(
            items.list,
            key = { _, item -> chatroomLazyKey(item, myPubKey) },
        ) { index, item ->
            Row(Modifier.fillMaxWidth()) {
                ChatroomHeaderCompose(
                    item,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            // No divider between rows: the row padding already separates them, and a hairline under
            // every row added a line of noise per conversation.

            // Rendered unconditionally at the protocol's oldest room so the card can run its own
            // "All caught up" crossfade-and-collapse when that protocol exhausts.
            if (index == oldestNip17Index) {
                DmHistoryLoadingCard(nip17Name, "NIP-17", loadingGiftWraps, giftWrapsStatus.exhausted, giftWrapsStatus.relayCount, giftWrapsStatus.stalledCount, giftWrapsStatus.reachedBack, giftWrapsStatus.relayProgress, ::formatHistoryReachDate)
            }
            if (index == oldestNip04Index) {
                DmHistoryLoadingCard(nip04Name, "NIP-04", loadingNip04, nip04Status.exhausted, nip04Status.relayCount, nip04Status.stalledCount, nip04Status.reachedBack, nip04Status.relayProgress, ::formatHistoryReachDate)
            }

            // Per-relay window-limit markers/sentinels belonging in the gap toward the next-older room:
            // each pulls its relay's next page while it's on screen. olderCreatedAt is null past the
            // oldest loaded room, so relays that have reached the bottom of the list sit there.
            RelayReachMarkers(
                limits,
                item.createdAt(),
                items.list.getOrNull(index + 1)?.createdAt(),
            ) { syncDetail = it }
        }
    }
}

/**
 * Bootstraps history while the rooms list is genuinely empty: steps every relay one page at a time,
 * gated only on its own loader, until rooms appear or the protocol exhausts. Once rooms load this stops
 * and the per-relay window-limit markers drive paging on demand.
 *
 * Leads with a debounce so the brief Empty/Loading flash that navigation passes through does NOT trigger
 * a hunt; if [active] drops before it elapses (rooms loaded) the effect cancels and nothing pages.
 */
@Composable
private fun BootstrapHistoryWhenEmpty(
    active: Boolean,
    loadingMore: StateFlow<Boolean>,
    status: StateFlow<PagingStatus>,
    advanceAll: () -> Unit,
) {
    LaunchedEffect(active, loadingMore, status) {
        if (!active) return@LaunchedEffect
        delay(BOOTSTRAP_DEBOUNCE_MS)
        combine(loadingMore, status) { loading, s -> !loading && !s.exhausted }
            .distinctUntilChanged()
            .filter { it }
            .collect { advanceAll() }
    }
}

// Ignore the transient empty feed that navigation flashes through before the rooms re-appear.
private const val BOOTSTRAP_DEBOUNCE_MS = 1200L

private fun reachState(p: RelayPagingProgress) =
    when {
        p.done -> RelayReachState.DONE
        p.stalled -> RelayReachState.STALLED
        else -> RelayReachState.REACHING
    }

private fun relayShortName(relay: NormalizedRelayUrl): String =
    relay.url
        .substringAfter("://")
        .trimEnd('/')
        .substringBefore('/')

// Stable per-chatroom key — derived from chatroom identity, not the latest
// message id, so reorders move the row instead of recreating it. Compose
// stores LazyColumn item keys in a SaveableStateHolder, which on Android
// requires Bundle-storable types, so each variant is Serializable and only
// holds primitives.
private sealed interface ChatroomLazyKey : JavaSerializable

private data class MarmotChatroomLazyKey(
    val groupId: HexKey,
) : ChatroomLazyKey

private data class PublicChannelLazyKey(
    val channelId: HexKey,
) : ChatroomLazyKey

private data class EphemeralChannelLazyKey(
    val id: String,
    val relayUrl: String,
) : ChatroomLazyKey

private data class PrivateChatLazyKey(
    val users: HashSet<HexKey>,
) : ChatroomLazyKey

private data class FallbackChatroomLazyKey(
    val noteIdHex: HexKey,
) : ChatroomLazyKey

private fun chatroomLazyKey(
    item: Note,
    myPubKey: HexKey,
): ChatroomLazyKey {
    item.inGatherers
        ?.firstNotNullOfOrNull { it as? MarmotGroupChatroom }
        ?.let { return MarmotChatroomLazyKey(it.nostrGroupId) }

    return when (val event = item.event) {
        is ChannelMessageEvent -> {
            PublicChannelLazyKey(event.channelId() ?: item.idHex)
        }

        is ChannelMetadataEvent -> {
            PublicChannelLazyKey(event.channelId() ?: item.idHex)
        }

        is ChannelCreateEvent -> {
            PublicChannelLazyKey(event.id)
        }

        is EphemeralChatEvent -> {
            event.roomId()?.let { EphemeralChannelLazyKey(it.id, it.relayUrl.url) }
                ?: FallbackChatroomLazyKey(item.idHex)
        }

        is ChatroomKeyable -> {
            // ChatroomKey.users may be a kotlinx PersistentOrderedSet, which
            // is not Serializable. Copy into a HashSet so the key survives
            // Bundle round-trips; Set equality stays order-independent.
            PrivateChatLazyKey(HashSet(event.chatroomKey(myPubKey).users))
        }

        else -> {
            FallbackChatroomLazyKey(item.idHex)
        }
    }
}
