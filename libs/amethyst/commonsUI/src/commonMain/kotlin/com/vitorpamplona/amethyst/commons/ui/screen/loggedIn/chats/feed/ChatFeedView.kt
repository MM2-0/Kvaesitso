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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.ui.AutoScrollToNewest
import com.vitorpamplona.amethyst.commons.chats.ui.NewDateDivisor
import com.vitorpamplona.amethyst.commons.chats.ui.NewDateOrSubjectDivisor
import com.vitorpamplona.amethyst.commons.chats.ui.watchChatGroupPosition
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.ChatLayoutType
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.composer.DraftTagState
import com.vitorpamplona.amethyst.commons.model.privateChats.isSubjectOnlyChatMessage
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedEmpty
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedError
import com.vitorpamplona.amethyst.commons.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.SaveableFeedState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatroomMessageCompose
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import kotlinx.coroutines.launch

private const val SUBJECT_ONLY_CONTENT_TYPE = -2

/** A collapsed run of messages from outside the network: a pill, not a bubble. */
private const val OUTSIDE_RUN_CONTENT_TYPE = -3

/**
 * The lazy-list content type of a chat row: its event kind, except that a NIP-17 rename with no
 * text gets its own type. It renders as a system pill rather than a bubble, so letting it share
 * kind 14's slot with ordinary messages would make the list rebuild one layout into the other
 * whenever a recycled slot crossed between the two.
 */
private fun chatRowContentType(item: Note): Int {
    val event = item.event ?: return -1
    return if (event.isSubjectOnlyChatMessage()) SUBJECT_ONLY_CONTENT_TYPE else event.kind
}

/**
 * A caller's own rendering for feed rows that are not chat bubbles.
 *
 * Marmot's kind:1210 group system rows are the case this exists for: they sit
 * in the conversation in chronological order but are captions about group
 * state, not messages, and rendering one as a bubble would show a reader raw
 * JSON attributed to whoever committed the change.
 */
@Immutable
interface ChatFeedRowRenderer {
    /** True when this renderer takes the row instead of the normal bubble. */
    fun claims(note: Note): Boolean

    @Composable
    fun Render(note: Note)
}

@Composable
fun RefreshingChatroomFeedView(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
    routeForLastRead: String,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    avoidDraft: DraftTagState? = null,
    scrollStateKey: String? = null,
    // Opt-in hook handed the feed's scroll state, so a specific screen (e.g. private DMs) can
    // attach scroll-driven loading. No-op for the public-chat / channel callers that don't paginate.
    listStateObserver: @Composable (LazyListState) -> Unit = {},
    // Optional footer rendered at the oldest end of the thread (a "load more" / spinner affordance).
    // Null for callers that load their whole history at once (public chats / channels).
    olderBoundary: (@Composable () -> Unit)? = null,
    // Optional per-gap hook: invoked between each message and its next-older neighbour with their
    // createdAt bounds, so a caller (private DMs) can draw per-relay paging markers at the depth each
    // relay has reached. No-op for callers without per-relay progress.
    markersInGap: (@Composable (newerCreatedAt: Long?, olderCreatedAt: Long?) -> Unit)? = null,
    // Optional hoisted load driver: handed the loaded message list and its scroll state once (above the
    // LazyColumn), so a caller (private DMs) can drive demand-driven paging off viewport visibility
    // rather than per-row composition. No-op for callers that don't paginate.
    sentinels: (@Composable (items: List<Note>, listState: LazyListState) -> Unit)? = null,
    // Optional external jump request (e.g. a "pinned message" bar): when it holds a note id, the feed
    // scrolls to that message and highlights it, then calls [onJumpHandled] to clear it. Null for
    // callers with no external jump affordance.
    jumpToNoteId: State<String?>? = null,
    onJumpHandled: () -> Unit = {},
    onWantsToEditChatMessage: ((Note) -> Unit)? = null,
    // Optional per-row override for rows the caller renders itself rather than as
    // a chat bubble. Null for every surface whose feed is only messages.
    rowRenderer: ChatFeedRowRenderer? = null,
    /**
     * Set by public chats: collapses runs of messages from outside the Web of Trust network,
     * except from the people the room vouches for. Null leaves the chat alone (private chats).
     */
    collapseOutsideNetwork: ChatRoomVouches? = null,
) {
    SaveableFeedState(feedContentState, scrollStateKey) { listState ->
        listStateObserver(listState)
        RenderChatFeedView(
            feedContentState,
            accountViewModel,
            listState,
            nav,
            routeForLastRead,
            onWantsToReply,
            onWantsToEditDraft,
            avoidDraft,
            olderBoundary,
            markersInGap,
            sentinels,
            jumpToNoteId,
            onJumpHandled,
            onWantsToEditChatMessage,
            rowRenderer,
            collapseOutsideNetwork,
        )
    }
}

@Composable
fun RenderChatFeedView(
    feed: FeedContentState,
    accountViewModel: AccountViewModel,
    listState: LazyListState,
    nav: INav,
    routeForLastRead: String,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    avoidDraft: DraftTagState? = null,
    olderBoundary: (@Composable () -> Unit)? = null,
    markersInGap: (@Composable (newerCreatedAt: Long?, olderCreatedAt: Long?) -> Unit)? = null,
    sentinels: (@Composable (items: List<Note>, listState: LazyListState) -> Unit)? = null,
    jumpToNoteId: State<String?>? = null,
    onJumpHandled: () -> Unit = {},
    onWantsToEditChatMessage: ((Note) -> Unit)? = null,
    rowRenderer: ChatFeedRowRenderer? = null,
    /**
     * Set by public chats: collapses runs of messages from outside the Web of Trust network,
     * except from the people the room vouches for. Null leaves the chat alone (private chats).
     */
    collapseOutsideNetwork: ChatRoomVouches? = null,
) {
    val feedState by feed.feedContent.collectAsStateWithLifecycle()

    CrossfadeIfEnabled(targetState = feedState, animationSpec = tween(durationMillis = 100)) { state ->
        when (state) {
            is FeedState.Loading -> {
                LoadingFeed()
            }

            is FeedState.Empty -> {
                FeedEmpty { feed.invalidateData() }
            }

            is FeedState.FeedError -> {
                FeedError(state.errorMessage) { feed.invalidateData() }
            }

            is FeedState.Loaded -> {
                ChatFeedLoaded(
                    state,
                    accountViewModel,
                    listState,
                    nav,
                    routeForLastRead,
                    onWantsToReply,
                    onWantsToEditDraft,
                    avoidDraft,
                    olderBoundary,
                    markersInGap,
                    sentinels,
                    jumpToNoteId,
                    onJumpHandled,
                    onWantsToEditChatMessage,
                    rowRenderer,
                    collapseOutsideNetwork,
                )
            }
        }
    }
}

@Composable
fun ChatFeedLoaded(
    loaded: FeedState.Loaded,
    accountViewModel: AccountViewModel,
    listState: LazyListState,
    nav: INav,
    routeForLastRead: String,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    avoidDraft: DraftTagState? = null,
    olderBoundary: (@Composable () -> Unit)? = null,
    markersInGap: (@Composable (newerCreatedAt: Long?, olderCreatedAt: Long?) -> Unit)? = null,
    sentinels: (@Composable (items: List<Note>, listState: LazyListState) -> Unit)? = null,
    jumpToNoteId: State<String?>? = null,
    onJumpHandled: () -> Unit = {},
    onWantsToEditChatMessage: ((Note) -> Unit)? = null,
    rowRenderer: ChatFeedRowRenderer? = null,
    /**
     * Set by public chats: collapses runs of messages from outside the Web of Trust network,
     * except from the people the room vouches for. Null leaves the chat alone (private chats).
     */
    collapseOutsideNetwork: ChatRoomVouches? = null,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    // Public chats collapse runs of messages from outside the Web of Trust network into one row.
    // Saveable: a run shown before opening a profile stays shown on the way back.
    var revealed by rememberSaveable(saver = RevealedSaver) { mutableStateOf(emptySet<String>()) }
    val outsideNetwork = rememberOutsideNetworkRuns(items.list, revealed, collapseOutsideNetwork, accountViewModel)
    // What the list draws: a collapsed run is one row (its oldest message), not one empty item
    // per message, which a long run of them would compose all at once.
    val rows =
        remember(items, outsideNetwork) {
            if (outsideNetwork.byId.isEmpty()) {
                items.list
            } else {
                items.list.filter { note -> outsideNetwork.byId[note.idHex]?.let { it.head.idHex == note.idHex } ?: true }
            }
        }

    // Hoisted load driver (above the LazyColumn): pages each relay off viewport visibility, so feed
    // reorders no longer re-fire paging. The per-gap markers below are pure UI. Given the rows the
    // list draws, since it maps visible item indexes into them.
    sentinels?.invoke(rows, listState)

    val newest = items.list.firstOrNull()
    AutoScrollToNewest(listState, newest, mine = accountViewModel.isLoggedUser(newest?.author?.pubkeyHex))

    val scope = rememberCoroutineScope()
    val highlightedNoteId = remember { mutableStateOf<String?>(null) }

    // A target inside a collapsed run is not a row: reveal the run, then scroll once it is drawn.
    var pendingScrollId by remember { mutableStateOf<String?>(null) }
    val scrollToNote: (String) -> Unit = { id ->
        // A run first: its head is one of the rows, but drawn as the collapsed run.
        val run = outsideNetwork.byId[id]
        if (run != null) {
            revealed = revealed + run.members.map { it.idHex }
            pendingScrollId = id
        } else {
            val index = rows.indexOfFirst { it.idHex == id }
            if (index >= 0) {
                scope.launch {
                    listState.animateScrollToItem(index)
                    highlightedNoteId.value = id
                }
            }
        }
    }
    LaunchedEffect(rows, outsideNetwork, pendingScrollId) {
        val id = pendingScrollId ?: return@LaunchedEffect
        // A run's head is a row even while collapsed: wait until the reveal has drawn it.
        if (outsideNetwork.byId.containsKey(id)) return@LaunchedEffect
        val index = rows.indexOfFirst { it.idHex == id }
        if (index >= 0) {
            pendingScrollId = null
            listState.animateScrollToItem(index)
            highlightedNoteId.value = id
        }
    }
    // One stable callback for every row: scrollToNote is rebuilt on each feed emission (it reads the
    // current rows), and handing the rows a new lambda made every visible bubble recompose per message.
    val latestScrollToNote by rememberUpdatedState(scrollToNote)
    val onScrollToNote: (Note) -> Unit = remember { { note -> latestScrollToNote(note.idHex) } }

    // External jump request (pinned-message bar). Keyed on the id alone, so a message arriving mid-jump
    // can't cancel the scroll animation or restart the effect. Always clears the request after one
    // attempt — even when the target isn't loaded — so it never sticks and a repeat tap fires again.
    val jumpId = jumpToNoteId?.value
    LaunchedEffect(jumpId) {
        if (jumpId != null) {
            scrollToNote(jumpId)
            onJumpHandled()
        }
    }

    // Bubbles and compact pin the day of what is at the top (a pill, a docked bar), so their stamps
    // can be just the time of day. Open has none: its stamps say the day themselves.
    val chatLayout = LocalDisplaySettings.current.chatLayout
    val pinsDay = chatLayout != ChatLayoutType.OPEN

    CompositionLocalProvider(LocalChatDayShownByFeed provides pinsDay) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = FeedPadding,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                state = listState,
            ) {
                itemsIndexed(
                    rows,
                    key = { _, item -> item.idHex },
                    contentType = { _, item -> if (outsideNetwork.byId.containsKey(item.idHex)) OUTSIDE_RUN_CONTENT_TYPE else chatRowContentType(item) },
                ) { index, item ->
                    val noteEvent = item.event
                    if (avoidDraft == null || noteEvent !is DraftWrapEvent || noteEvent.dTag() !in avoidDraft.usedDraftTags) {
                        // Reverse layout: index - 1 is the newer message (visually below),
                        // index + 1 the older one (visually above).
                        val newer = rows.getOrNull(index - 1)
                        val older = rows.getOrNull(index + 1)

                        // Send/arrival motion: new items fade in and existing ones slide to
                        // make room, so a sent message enters instead of appearing.
                        val itemModifier =
                            if (accountViewModel.settings.isPerformanceMode()) {
                                Modifier
                            } else {
                                Modifier.animateItem()
                            }

                        val outsideRun = outsideNetwork.byId[item.idHex]

                        Column(modifier = itemModifier) {
                            if (outsideRun != null) {
                                // The run's row, at its oldest message. The day header only: a subject change
                                // would print the author's name and the new subject.
                                NewDateDivisor(older, item)
                                markersInGap?.invoke(item.event?.createdAt, older?.event?.createdAt)
                                val newestInRun = outsideRun.members.first()
                                LaunchedEffect(routeForLastRead, newestInRun.idHex) {
                                    accountViewModel.loadAndMarkAsRead(routeForLastRead, newestInRun.createdAt(), dismissNotificationId = newestInRun.idHex)
                                }
                                OutsideNetworkChatRow(outsideRun.members.size) {
                                    revealed = revealed + outsideRun.members.map { it.idHex }
                                }
                            } else {
                                // A day/subject header belongs ABOVE the message it introduces. `reverseLayout`
                                // flips the order of the lazy list's items, but NOT the content inside one item:
                                // this Column still lays out top-to-bottom, so the divisor must be composed
                                // before the bubble. Composing it after put the header below its own message —
                                // i.e. visually heading the NEXT (newer) message while showing this one's date,
                                // which is why a "Jul 1, 2025" header sat on top of a Sep 23 bubble.
                                NewDateOrSubjectDivisor(older, item, accountViewModel)

                                // Per-relay paging markers for the gap toward the next-older message. Older items sit
                                // ABOVE newer ones under `reverseLayout`, so that gap is the space above this bubble —
                                // which means these belong before it, for the same reason the divisor does. Composed
                                // after the bubble they rendered in the gap toward the NEWER message, contradicting the
                                // bounds they are handed.
                                markersInGap?.invoke(
                                    item.event?.createdAt,
                                    older?.event?.createdAt,
                                )

                                // A claimed row is rendered by the caller instead of as a
                                // bubble. The date divisor above still applies — a system
                                // row belongs under the day it happened on like anything
                                // else — which is why the claim is checked here and not
                                // around the whole item.
                                val claimed = rowRenderer?.takeIf { it.claims(item) }
                                if (claimed != null) {
                                    claimed.Render(item)
                                } else {
                                    ChatroomMessageCompose(
                                        baseNote = item,
                                        routeForLastRead = routeForLastRead,
                                        accountViewModel = accountViewModel,
                                        nav = nav,
                                        onWantsToReply = onWantsToReply,
                                        onWantsToEditDraft = onWantsToEditDraft,
                                        onScrollToNote = onScrollToNote,
                                        shouldHighlight = highlightedNoteId.value == item.idHex,
                                        onHighlightFinished = { highlightedNoteId.value = null },
                                        groupPosition = watchChatGroupPosition(newer, item, older),
                                        previousNoteId = older?.idHex,
                                        onWantsToEditChatMessage = onWantsToEditChatMessage,
                                    )
                                }
                            }
                        }
                    }
                }

                // Reverse layout: a trailing item sits at the highest index, i.e. the visual TOP (oldest end).
                // That's where the caller's "load more" affordance / spinner lives.
                if (olderBoundary != null) {
                    item(key = "olderBoundary") { olderBoundary() }
                }
            }

            if (pinsDay) {
                ChatFeedDayIndicator(listState, rows, docked = chatLayout == ChatLayoutType.COMPACT)
            }
        }
    }
}

/** The revealed message ids, as a list a saved state can hold. */
private val RevealedSaver =
    Saver<MutableState<Set<String>>, List<String>>(
        save = { it.value.toList() },
        restore = { mutableStateOf(it.toSet()) },
    )
