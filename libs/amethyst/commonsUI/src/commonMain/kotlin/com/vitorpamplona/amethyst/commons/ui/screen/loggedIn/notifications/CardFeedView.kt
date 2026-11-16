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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.Card
import com.vitorpamplona.amethyst.commons.feeds.CardFeedState
import com.vitorpamplona.amethyst.commons.model.NoteLayoutType
import com.vitorpamplona.amethyst.commons.model.chats.formatHistoryReachDate
import com.vitorpamplona.amethyst.commons.notifications.BadgeCard
import com.vitorpamplona.amethyst.commons.notifications.CardFeedContentState
import com.vitorpamplona.amethyst.commons.notifications.ChannelInviteCard
import com.vitorpamplona.amethyst.commons.notifications.ConcordDirectInviteNotificationCard
import com.vitorpamplona.amethyst.commons.notifications.MessageSetCard
import com.vitorpamplona.amethyst.commons.notifications.MultiSetCard
import com.vitorpamplona.amethyst.commons.notifications.NoteCard
import com.vitorpamplona.amethyst.commons.notifications.NutzapUserSetCard
import com.vitorpamplona.amethyst.commons.notifications.OpenPollsState
import com.vitorpamplona.amethyst.commons.notifications.ZapUserSetCard
import com.vitorpamplona.amethyst.commons.notifications.containsEventId
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.notification_feed_is_empty
import com.vitorpamplona.amethyst.commons.resources.refresh
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedError
import com.vitorpamplona.amethyst.commons.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachCursor
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachDetailDialog
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachMarkers
import com.vitorpamplona.amethyst.commons.ui.feeds.StickToTopOnPrepend
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.BadgeCompose
import com.vitorpamplona.amethyst.commons.ui.note.CloseIcon
import com.vitorpamplona.amethyst.commons.ui.note.MessageSetCompose
import com.vitorpamplona.amethyst.commons.ui.note.MultiSetCompose
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.NutzapUserSetCompose
import com.vitorpamplona.amethyst.commons.ui.note.ZapUserSetCompose
import com.vitorpamplona.amethyst.commons.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.commons.ui.notifications.OpenPollsSectionHeader
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordDirectInviteCard
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications.donations.ShowDonationCard
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.util.logTime
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.delay

@Composable
fun RenderCardFeed(
    feedContent: CardFeedContentState,
    pollContent: OpenPollsState,
    accountViewModel: AccountViewModel,
    listState: LazyListState,
    nav: INav,
    routeForLastRead: String,
    scrollToEventId: String? = null,
    headerContent: (@Composable () -> Unit)? = null,
    // Whether THIS feed drives the shared account history pager. False for an off-screen split tab / second
    // pane, so only the feed the user is actually looking at pages the pager (see #2 in the audit).
    drivesPaging: Boolean = true,
) {
    // Notifications always use the Indented post layout, whatever the user picked for the other
    // feeds: the activity cards (likes, zaps, boosts, DMs, badges) are built around the author
    // column, and replies laid out full width beside them made the feed look like two designs.
    val displaySettings = LocalDisplaySettings.current
    val indented = remember(displaySettings) { displaySettings.copy(noteLayout = NoteLayoutType.AUTHOR_COLUMN) }
    CompositionLocalProvider(LocalDisplaySettings provides indented) {
        RenderCardFeedContent(
            feedContent = feedContent,
            pollContent = pollContent,
            accountViewModel = accountViewModel,
            listState = listState,
            nav = nav,
            routeForLastRead = routeForLastRead,
            scrollToEventId = scrollToEventId,
            headerContent = headerContent,
            drivesPaging = drivesPaging,
        )
    }
}

@Composable
private fun RenderCardFeedContent(
    feedContent: CardFeedContentState,
    pollContent: OpenPollsState,
    accountViewModel: AccountViewModel,
    listState: LazyListState,
    nav: INav,
    routeForLastRead: String,
    scrollToEventId: String?,
    headerContent: (@Composable () -> Unit)?,
    drivesPaging: Boolean,
) {
    val feedState by feedContent.feedContent.collectAsStateWithLifecycle()

    // A genuinely empty feed has no rows for the look-ahead buffer driver to measure, so step every relay
    // one page at a time to hunt for the first notifications. Once cards appear the buffer driver takes
    // over. Gated on Empty only (never the transient Loading navigation flashes through), and only for the
    // active feed so an off-screen tab doesn't hunt on the shared pager.
    val history = remember(accountViewModel) { accountViewModel.dataSources().account.notificationsHistory }
    if (drivesPaging) {
        BootstrapNotificationHistoryWhenEmpty(feedState is CardFeedState.Empty, history.loadingMore, history.status) { history.advanceAll() }
    }

    // Direct switch instead of CrossfadeIfEnabled: the crossfade's `currentlyVisible`
    // accumulator can leave a previous `Loaded` instance composed alongside the new
    // one when refreshes (e.g. double-tap on the Notifications tab) bounce the
    // state through Empty/Loading and back inside the animation window. Two
    // LazyColumns then share the same listState and only the top one scrolls.
    when (val state = feedState) {
        is CardFeedState.Empty -> {
            HeaderAbove(headerContent) { NotificationFeedEmpty(feedContent::invalidateData) }
        }

        is CardFeedState.FeedError -> {
            HeaderAbove(headerContent) { FeedError(state.errorMessage, feedContent::invalidateData) }
        }

        is CardFeedState.Loaded -> {
            FeedLoaded(
                loaded = state,
                polls = pollContent,
                listState = listState,
                routeForLastRead = routeForLastRead,
                accountViewModel = accountViewModel,
                nav = nav,
                scrollToEventId = scrollToEventId,
                headerContent = headerContent,
                drivesPaging = drivesPaging,
            )
        }

        CardFeedState.Loading -> {
            HeaderAbove(headerContent) { LoadingFeed() }
        }
    }
}

/**
 * Draws [headerContent] above a non-loaded feed state.
 *
 * The header is not part of the feed's contents — it carries standing prompts (a pending channel
 * invite, "you have no inbox relay") that are true regardless of whether any notification has loaded.
 * Drawing it only in the `Loaded` branch made it blink out and back on every visit, because arriving on
 * the screen re-runs `checkKeysInvalidateDataAndSendToTop` and any refresh that momentarily computes an
 * empty list flips the state through `Empty`/`Loading` (see the `CardFeedState` comment above). Worse
 * for the relay prompt specifically: a missing inbox relay is the most likely *reason* the feed is
 * empty, so the one state that hid it was the one that needed it.
 *
 * The padding is applied here because only the `Loaded` branch has a `LazyColumn` to carry the
 * scaffold's inset as content padding; without it the header would draw under the disappearing top bar.
 * Same shape [com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.feed.ChatroomListFeedView] uses.
 */
@Composable
private fun HeaderAbove(
    headerContent: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    if (headerContent == null) {
        content()
    } else {
        Column(Modifier.fillMaxSize().padding(rememberFeedContentPadding(FeedPadding))) {
            headerContent()
            content()
        }
    }
}

@Composable
fun NotificationFeedEmpty(onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringRes(Res.string.notification_feed_is_empty))
        Spacer(modifier = StdVertSpacer)
        OutlinedButton(onClick = onRefresh) { Text(text = stringRes(Res.string.refresh)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedLoaded(
    loaded: CardFeedState.Loaded,
    polls: OpenPollsState,
    listState: LazyListState,
    routeForLastRead: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    scrollToEventId: String? = null,
    headerContent: (@Composable () -> Unit)? = null,
    drivesPaging: Boolean = true,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    val openPolls by polls.flow.collectAsStateWithLifecycle()

    // Infinite-scroll backward pagination of the notifications history. All the driving (look-ahead buffer,
    // auto-retry, per-relay sentinels) lives in [rememberNotificationHistoryPaging]; here we just get back
    // the per-relay cursors to draw as frontier markers, and keep [history] for the detail dialog's retry.
    val history = remember(accountViewModel) { accountViewModel.dataSources().account.notificationsHistory }
    // Count of items above the notification cards in the LazyColumn (scaffold header + donation card + open
    // polls), so the hoisted sentinel can map a visible LazyColumn index back to a card.
    val leadingItemCount = (if (headerContent != null) 1 else 0) + 1 + openPolls.size
    val limits =
        rememberNotificationHistoryPaging(history, listState, drivesPaging) { index ->
            items.list.getOrNull(index - leadingItemCount)?.createdAt()
        }

    // The relays behind a tapped in-stream marker; non-null shows the per-relay breakdown popup.
    var syncDetail by remember { mutableStateOf<List<RelayReachCursor>?>(null) }
    syncDetail?.let { detail ->
        // Tap-through offers a Try Again on stalled relays, so a user who sees a bad relay can act on it.
        RelayReachDetailDialog(detail, ::formatHistoryReachDate, onRetry = { history.advanceAll() }) { syncDetail = null }
    }

    StickToTopOnPrepend(listState, items.list.firstOrNull()?.id())

    // Track which card is highlighted (will auto-clear after animation)
    var highlightedCardId by remember { mutableStateOf<String?>(null) }

    // Scroll to the card containing the target event ID
    if (scrollToEventId != null) {
        LaunchedEffect(scrollToEventId, items) {
            val position = items.list.indexOfFirst { it.containsEventId(scrollToEventId) }
            if (position >= 0) {
                // +1 offset for the donation card header item
                val scrollIndex = position + 1 + openPolls.size
                listState.animateScrollToItem(scrollIndex)
                highlightedCardId = items.list[position].id()
                delay(2000)
                highlightedCardId = null
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        if (headerContent != null) {
            item(key = "scaffold-header") { headerContent() }
        }
        item {
            ShowDonationCard(accountViewModel, nav)
        }

        if (openPolls.isNotEmpty()) {
            itemsIndexed(
                items = openPolls,
                key = { _, item -> "open-poll-${item.idHex}" },
                contentType = { _, _ -> "OpenPoll" },
            ) { _, note ->
                Row(modifier = Modifier.padding(start = Size10dp, end = Size10dp, bottom = Size10dp)) {
                    Card(
                        modifier = MaterialTheme.colorScheme.imageModifier,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OpenPollsSectionHeader()
                            IconButton(
                                modifier = Modifier.padding(end = Size10dp),
                                onClick = { accountViewModel.dismissPollNotification(note.idHex) },
                            ) {
                                CloseIcon()
                            }
                        }
                        Row(Modifier.fillMaxWidth().animateItem()) {
                            NoteCompose(
                                baseNote = note,
                                modifier = Modifier.fillMaxWidth(),
                                routeForLastRead = routeForLastRead,
                                isBoostedNote = false,
                                isQuotedNote = false,
                                quotesLeft = 3,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                        }
                        HorizontalDivider(
                            thickness = DividerThickness,
                        )
                    }
                }
            }
        }

        itemsIndexed(
            items = items.list,
            key = { _, item -> item.id() },
            contentType = { _, item -> item::class },
        ) { index, item ->
            val isHighlighted = highlightedCardId == item.id()
            val highlightColor by animateColorAsState(
                targetValue = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                animationSpec = tween(durationMillis = if (isHighlighted) 300 else 1000),
                label = "highlightAnimation",
            )

            Row(Modifier.fillMaxWidth().background(highlightColor).animateItem()) {
                logTime(
                    debugMessage = { "CardFeedView $item" },
                ) {
                    RenderCardItem(
                        item,
                        routeForLastRead,
                        showHidden = items.showHidden,
                        accountViewModel,
                        nav,
                    )
                }
            }
            HorizontalDivider(
                thickness = DividerThickness,
            )

            // Per-relay markers in the gap toward the next-older card, at the depth each relay has paged to.
            // The bulk load is buffer-driven (above); these mark each relay's frontier and drive the
            // stalled-relay retry when scrolled into view (see the sentinel above). olderCreatedAt is null
            // past the oldest loaded card, so relays that reached the bottom sit there as "fully loaded".
            if (limits.isNotEmpty()) {
                RelayReachMarkers(
                    limits,
                    item.createdAt(),
                    items.list.getOrNull(index + 1)?.createdAt(),
                ) { syncDetail = it }
            }
        }
    }
}

@Composable
private fun RenderCardItem(
    item: Card,
    routeForLastRead: String,
    showHidden: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (item) {
        is NoteCard -> {
            NoteCardCompose(
                item,
                routeForLastRead = routeForLastRead,
                isBoostedNote = false,
                showHidden = showHidden,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        is ZapUserSetCard -> {
            ZapUserSetCompose(
                item,
                isInnerNote = false,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = routeForLastRead,
            )
        }

        is NutzapUserSetCard -> {
            NutzapUserSetCompose(
                item,
                isInnerNote = false,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = routeForLastRead,
            )
        }

        is MultiSetCard -> {
            MultiSetCompose(
                item,
                accountViewModel = accountViewModel,
                showHidden = showHidden,
                nav = nav,
                routeForLastRead = routeForLastRead,
            )
        }

        is BadgeCard -> {
            BadgeCompose(
                item,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = routeForLastRead,
            )
        }

        is ConcordDirectInviteNotificationCard -> {
            // The same decision card as the Concord hub: the invite has no real event to hang a
            // NoteCompose on (its gift wrap is authored by a throwaway key).
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                ConcordDirectInviteCard(item.note.invite, accountViewModel, nav)
            }
        }

        is ChannelInviteCard -> {
            // The same NoteCompose every other row uses. The invite-specific part is only the body,
            // dispatched by kind in RenderNoteRow — so the author header, 3-dot menu, reactions row,
            // last-read background and click-through are the shared ones rather than re-implemented.
            NoteCompose(
                baseNote = item.note,
                modifier = Modifier.fillMaxWidth(),
                routeForLastRead = routeForLastRead,
                isBoostedNote = false,
                isQuotedNote = false,
                isHiddenFeed = showHidden,
                quotesLeft = 3,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        is MessageSetCard -> {
            MessageSetCompose(
                messageSetCard = item,
                routeForLastRead = routeForLastRead,
                showHidden = showHidden,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun NoteCardCompose(
    baseNote: NoteCard,
    modifier: Modifier = Modifier,
    routeForLastRead: String? = null,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: ReplyRenderType = ReplyRenderType.FULL,
    makeItShort: Boolean = false,
    showHidden: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NoteCompose(
        baseNote = baseNote.note,
        modifier = modifier.fillMaxWidth(),
        routeForLastRead = routeForLastRead,
        isBoostedNote = isBoostedNote,
        isQuotedNote = isQuotedNote,
        unPackReply = unPackReply,
        makeItShort = makeItShort,
        isHiddenFeed = showHidden,
        quotesLeft = 3,
        parentBackgroundColor = parentBackgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}
