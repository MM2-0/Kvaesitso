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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.privateDM.dal.ChatroomFeedViewModel
import com.vitorpamplona.amethyst.commons.chats.privateDM.history.ChatHistoryGate
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.chats.formatHistoryReachDate
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.privateChatLastReadRoute
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsHistoryEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chat_composer_off_with_nip17
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_this_group
import com.vitorpamplona.amethyst.commons.resources.chats_history_proto_nip04
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.rememberSharedMediaResolver
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.ChatHistoryGateCard
import com.vitorpamplona.amethyst.commons.ui.feeds.DmHistoryLoadingCard
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachCursor
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachDetailDialog
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachMarkers
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachSentinels
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachState
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachVisibility
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.elements.ObserveRelayListForDMsAndDisplayIfNotFound
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.datasource.ChatroomFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.header.DmReportWarningCard
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.ChatNewMessageViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.PrivateMessageEditFieldRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.Nip17OffNotice
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayPagingProgress
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun ChatroomView(
    room: ChatroomKey,
    draftMessage: String?,
    attachmentUri: String? = null,
    replyToNote: HexKey? = null,
    editFromDraft: HexKey? = null,
    expiresDays: Int? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedViewModel: ChatroomFeedViewModel =
        rememberViewModel(
            key = room.hashCode().toString() + "ChatroomViewModels",
            factory =
                ChatroomFeedViewModel.Factory(
                    room,
                    accountViewModel.account,
                ),
        )

    val newPostModel: ChatNewMessageViewModel = rememberViewModel { ChatNewMessageViewModel() }
    newPostModel.init(accountViewModel)
    newPostModel.load(room)

    if (replyToNote != null) {
        LaunchedEffect(key1 = replyToNote, newPostModel, accountViewModel) {
            val replyNote = LocalCache.checkGetOrCreateNote(replyToNote)
            if (replyNote != null) {
                newPostModel.reply(replyNote)
            }
        }
    }
    if (editFromDraft != null) {
        LaunchedEffect(editFromDraft, newPostModel, accountViewModel) {
            val draftNote = LocalCache.checkGetOrCreateNote(editFromDraft)
            if (draftNote != null) {
                newPostModel.editFromDraft(draftNote)
            }
        }
    }
    if (expiresDays != null) {
        LaunchedEffect(expiresDays, newPostModel, accountViewModel) {
            newPostModel.loadExpiration(expiresDays)
        }
    }

    // Reactively check if recipients have DM relays for NIP-17 delivery
    for (userHex in room.users) {
        LoadAddressableNote(
            DmRelayListEvent.createAddress(userHex),
        ) { note ->
            if (note != null) {
                EventFinderFilterAssemblerSubscription(note, accountViewModel)
            }
        }
    }

    if (draftMessage != null) {
        LaunchedEffect(key1 = draftMessage) {
            newPostModel.message.setTextAndPlaceCursorAtEnd(draftMessage)
            newPostModel.onMessageChanged()
        }
    }
    val mediaResolver = rememberSharedMediaResolver()
    if (attachmentUri != null) {
        LaunchedEffect(key1 = attachmentUri) {
            mediaResolver.resolve(attachmentUri)?.let {
                newPostModel.pickedMedia(persistentListOf(it))
            }
        }
    }

    ChatroomViewUI(
        room = room,
        feedViewModel = feedViewModel,
        newPostModel = newPostModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/**
 * Bootstraps NIP-04 history when the conversation has no messages yet (the live tail came back empty for
 * a thread whose newest message is older than a week). There's nothing on screen to host the per-relay
 * window-limit markers that normally drive paging, so while the feed is empty we step every relay one
 * page at a time until messages appear — at which point the on-screen markers take over — or the
 * protocol is exhausted. NIP-04 pages are filtered to this conversation, so every page is about it.
 *
 * NIP-17 is not bootstrapped here: its pages are account-wide (a gift wrap names only its recipient), so
 * walking them until this chat shows a message could pull the whole inbox. [ChatHistoryGate] decides
 * those, and an empty chat waits for the reader's Continue.
 */
@Composable
private fun BootstrapHistoryWhenEmpty(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val nip04History = remember(accountViewModel) { accountViewModel.dataSources().chatroom.nip04History }
    val feedState by feedContentState.feedContent.collectAsStateWithLifecycle()
    // Empty only (never the transient Loading navigation flashes through), and debounced below, so
    // re-opening a conversation that has messages doesn't kick a hunt.
    val needsBootstrap = feedState is FeedState.Empty

    LaunchedEffect(needsBootstrap, nip04History) {
        if (!needsBootstrap) return@LaunchedEffect
        delay(BOOTSTRAP_DEBOUNCE_MS)
        combine(nip04History.loadingMore, nip04History.status) { loading, s -> !loading && !s.exhausted }
            .distinctUntilChanged()
            .filter { it }
            .collect { nip04History.advanceAll() }
    }
}

// Ignore the transient empty feed that navigation flashes through before messages re-appear.
private const val BOOTSTRAP_DEBOUNCE_MS = 1200L

@Composable
fun ChatroomViewUI(
    room: ChatroomKey,
    feedViewModel: ChatroomFeedViewModel,
    newPostModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedViewModel)
    ChatroomFilterAssemblerSubscription(room, accountViewModel.dataSources().chatroom, accountViewModel)

    DisposableEffect(room) {
        Log.d("DMPagination") { "convo: OPEN room=${room.hashCode()}" }
        onDispose { Log.d("DMPagination") { "convo: CLOSE room=${room.hashCode()}" } }
    }

    val giftWrapsHistory = remember(accountViewModel) { accountViewModel.dataSources().account.giftWrapsHistory }
    val nip04History = remember(accountViewModel) { accountViewModel.dataSources().chatroom.nip04History }
    val loadingNip04 by nip04History.loadingMore.collectAsStateWithLifecycle()
    // One atomic snapshot per protocol (exhausted + relays + reached + per-relay progress) instead of six
    // separate collectors — the status card and the per-relay markers read all of it together anyway.
    val giftWrapsStatus by giftWrapsHistory.status.collectAsStateWithLifecycle()
    val nip04Status by nip04History.status.collectAsStateWithLifecycle()
    val user = accountViewModel.userProfile()

    // Both protocols' per-relay window limits, placed in the stream as markers (see RelayReachMarkers).
    // A protocol drops out once exhausted.
    //
    // NIP-04 pages are filtered to this conversation, so its relays page while their marker is on screen
    // and keep paging while it stays there (RelayReachSentinels).
    //
    // NIP-17 pages are account-wide: a quiet chat would leave its marker in view and walk every gift wrap
    // the account ever received. Its markers only report visibility; [ChatHistoryGate] pages one round when
    // the reader scrolls up to them and asks before going further. They sit at the point every message
    // after which is in: a wrap is back-dated up to two days (NIP-59), so a relay that reached wrap time D
    // has delivered every message written after D + 2 days, not after D.
    val nip17Limits =
        remember(giftWrapsStatus) {
            if (giftWrapsStatus.exhausted) {
                emptyList()
            } else {
                giftWrapsStatus.relayProgress.map { (relay, p) ->
                    RelayReachCursor("17:${relay.url}", relayShortName(relay), giftWrapCompleteTo(p), reachState(p), "NIP-17") {}
                }
            }
        }
    val nip04Limits =
        remember(nip04Status, user) {
            if (nip04Status.exhausted) {
                emptyList()
            } else {
                nip04Status.relayProgress.map { (relay, p) ->
                    RelayReachCursor("04:${relay.url}", relayShortName(relay), p.reachedUntil, reachState(p), "NIP-04") { nip04History.advance(relay) }
                }
            }
        }
    val limits = remember(nip17Limits, nip04Limits) { nip17Limits + nip04Limits }

    val gate = remember(room, giftWrapsHistory) { ChatHistoryGate(advanceAll = giftWrapsHistory::advanceAll) }
    val gatePhase by gate.phase.collectAsStateWithLifecycle()
    val feedState by feedViewModel.feedState.feedContent.collectAsStateWithLifecycle()
    val feedIsEmpty = feedState is FeedState.Empty
    // Null until the list reports. An empty chat has no list to host the markers: the card is all there is.
    var nip17MarkersInView by remember(room) { mutableStateOf<Boolean?>(null) }
    DriveChatHistoryGate(gate, giftWrapsHistory, feedViewModel.feedState, if (feedIsEmpty) true else nip17MarkersInView)

    val chatName = chatNameOf(room, accountViewModel)
    val gateCard: @Composable () -> Unit = {
        ChatHistoryGateCard(
            phase = gatePhase,
            chatName = chatName,
            completeTo =
                giftWrapsStatus.relayProgress.values
                    .filter { !it.done && !it.stalled }
                    .maxOfOrNull(::giftWrapCompleteTo),
            stalledRelays =
                giftWrapsStatus.relayProgress
                    .filterValues { it.stalled }
                    .keys
                    .map(::relayShortName),
            relayProgress = giftWrapsStatus.relayProgress,
            onResume = gate::resume,
            onKeepLooking = gate::keepLooking,
            onStop = gate::stop,
        )
    }
    val nip04Name = stringRes(Res.string.chats_history_proto_nip04)

    // The relays behind a tapped in-stream "Relay sync" marker; non-null shows the detail popup.
    var syncDetail by remember { mutableStateOf<List<RelayReachCursor>?>(null) }
    syncDetail?.let { detail ->
        RelayReachDetailDialog(detail, ::formatHistoryReachDate) { syncDetail = null }
    }

    BootstrapHistoryWhenEmpty(feedViewModel.feedState, accountViewModel)

    Column(Modifier.fillMaxHeight()) {
        ObserveRelayListForDMsAndDisplayIfNotFound(accountViewModel, nav)

        DmReportWarningCard(room, accountViewModel, nav)

        // An empty feed renders no list, so no olderBoundary: the card goes above it.
        if (feedIsEmpty) gateCard()

        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(vertical = 0.dp)
                    .weight(1f, true),
        ) {
            RefreshingChatroomFeedView(
                feedContentState = feedViewModel.feedState,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = privateChatLastReadRoute(room),
                avoidDraft = newPostModel.draftTag,
                onWantsToReply = newPostModel::reply,
                onWantsToEditDraft = newPostModel::editFromDraft,
                // One status card per protocol at the oldest end. NIP-17's says where loading stopped and
                // offers Continue / Keep looking; NIP-04's shows what it's reaching for while it pages and
                // crossfades to "All caught up" when it runs dry.
                olderBoundary = {
                    Column {
                        gateCard()
                        DmHistoryLoadingCard(nip04Name, "NIP-04", loadingNip04, nip04Status.exhausted, nip04Status.relayCount, nip04Status.stalledCount, nip04Status.reachedBack, nip04Status.relayProgress, ::formatHistoryReachDate)
                    }
                },
                // Each relay's window-limit marker, placed at its reached cursor (pure UI). Hidden once
                // both protocols are exhausted.
                markersInGap =
                    if (limits.isEmpty()) {
                        null
                    } else {
                        { newer, older -> RelayReachMarkers(limits, newer, older) { syncDetail = it } }
                    },
                // Hoisted above the list, off viewport visibility so feed reorders don't re-page: NIP-04
                // relays pull their next page while their marker is on screen (RelayReachSentinels); NIP-17
                // markers only tell the gate whether the reader is looking at them.
                sentinels = { items, listState ->
                    val createdAtAt = { index: Int -> items.getOrNull(index)?.event?.createdAt }
                    RelayReachSentinels(nip04Limits, listState, createdAtAt)
                    RelayReachVisibility(nip17Limits, listState, createdAtAt) { nip17MarkersInView = it }
                },
            )
        }

        Spacer(modifier = DoubleVertSpacer)

        val scope = rememberCoroutineScope()

        // LAST ROW. The composer only sends NIP-17; with NIP-17 off a sent message would be dropped
        // on arrival, so say so instead of offering to send.
        val chatFeeds by accountViewModel.account.chatFeedToggles.applied
            .collectAsStateWithLifecycle()
        if (ChatFeedType.NIP17 in chatFeeds) {
            PrivateMessageEditFieldRow(
                newPostModel,
                accountViewModel,
                onSendNewMessage = {
                    scope.launch {
                        feedViewModel.feedState.sendToTop()
                    }
                },
                nav,
            )
        } else {
            Nip17OffNotice(
                text = stringRes(Res.string.chat_composer_off_with_nip17),
                onOpenMessagesSettings = { nav.nav(Route.MessagesSettings) },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
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
        .substringAfter("://")
        .trimEnd('/')
        .substringBefore('/')

/**
 * Feeds [gate] its inputs whenever any of them changes: the NIP-17 pager's progress, this chat's NIP-17
 * message count, and whether its markers are in view. Busy and open are read from the pager at that moment
 * rather than from the combined values, so a page the gate just started is never mistaken for one that
 * already settled empty.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun DriveChatHistoryGate(
    gate: ChatHistoryGate,
    giftWrapsHistory: AccountGiftWrapsHistoryEoseManager,
    feedContentState: FeedContentState,
    markersInView: Boolean?,
) {
    val visible by rememberUpdatedState(markersInView)
    LaunchedEffect(gate, giftWrapsHistory, feedContentState) {
        val nip17Count =
            feedContentState.feedContent.flatMapLatest { state ->
                if (state is FeedState.Loaded) {
                    state.feed.map { loaded -> loaded.list.count { it.event is BaseDMGroupEvent } }
                } else {
                    flowOf(0)
                }
            }
        combine(
            giftWrapsHistory.loadingMore,
            giftWrapsHistory.status,
            nip17Count,
            snapshotFlow { visible },
        ) { _, _, count, inView -> count to inView }
            .collect { (count, inView) ->
                gate.update(
                    markersVisible = inView,
                    busy = giftWrapsHistory.loadingMore.value,
                    open = !giftWrapsHistory.status.value.exhausted,
                    count = count,
                )
            }
    }
}

/** Every message written after this moment has arrived from this relay, as far as its pages reached. */
private fun giftWrapCompleteTo(p: RelayPagingProgress): Long = p.reachedUntil + TimeUtils.twoDays()

/** Who the chat is with, for the history card's sentences: the other person's name, or "this group". */
@Composable
private fun chatNameOf(
    room: ChatroomKey,
    accountViewModel: AccountViewModel,
): String {
    val other = room.users.singleOrNull() ?: return stringRes(Res.string.chats_history_gate_this_group)
    val user = remember(other) { accountViewModel.getOrCreateUser(other) }
    val name by observeUserName(user, accountViewModel)
    return name
}
