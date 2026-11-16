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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.threadview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.chats.ui.ThinSendButton
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeEditDraftTo
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeToMessage
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitThreadTimelineRow
import com.vitorpamplona.amethyst.commons.nip34Git.ui.LocalGitThreadRoot
import com.vitorpamplona.amethyst.commons.nip34Git.ui.isGitTimelineEvent
import com.vitorpamplona.amethyst.commons.nip34Git.ui.isHiddenInGitThread
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitRepositoryAnnouncements
import com.vitorpamplona.amethyst.commons.nip99Classifieds.listingStatus
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ui.ClassifiedsOpenMarketsDetails
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ui.ListingStatusBadge
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ui.RenderProductCollection
import com.vitorpamplona.amethyst.commons.nipsOnNostr.ui.RenderNipHeaderForThread
import com.vitorpamplona.amethyst.commons.nipsOnNostr.ui.datasource.WatchNipSubscription
import com.vitorpamplona.amethyst.commons.relayClient.event.observeCommunityApprovalNeedStatus
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.expand
import com.vitorpamplona.amethyst.commons.resources.hi_seller_is_this_still_available
import com.vitorpamplona.amethyst.commons.resources.hi_there_is_this_still_available
import com.vitorpamplona.amethyst.commons.resources.ic_dm
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.resources.reply_here
import com.vitorpamplona.amethyst.commons.resources.send_a_direct_message
import com.vitorpamplona.amethyst.commons.resources.send_the_seller_a_message
import com.vitorpamplona.amethyst.commons.resources.thread_collapsed_reply_count
import com.vitorpamplona.amethyst.commons.resources.thread_reply_outside_network
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.ui.components.AutoNonlazyGrid
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.ShowFullTextCache
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedTextField
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.markdown.LocalMarkdownAnchors
import com.vitorpamplona.amethyst.commons.ui.markdown.MarkdownAnchors
import com.vitorpamplona.amethyst.commons.ui.markdown.markdownAnchorViewport
import com.vitorpamplona.amethyst.commons.ui.markdown.rememberMarkdownAnchors
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.CheckAndDisplayEditStatus
import com.vitorpamplona.amethyst.commons.ui.note.CheckHiddenFeedWatchBlockAndReport
import com.vitorpamplona.amethyst.commons.ui.note.DisplayDraft
import com.vitorpamplona.amethyst.commons.ui.note.DisplayOtsIfInOriginal
import com.vitorpamplona.amethyst.commons.ui.note.ExpandMoreIcon
import com.vitorpamplona.amethyst.commons.ui.note.Expiration
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadDecryptedContent
import com.vitorpamplona.amethyst.commons.ui.note.LongPressToQuickAction
import com.vitorpamplona.amethyst.commons.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.commons.ui.note.ObserveDraftEvent
import com.vitorpamplona.amethyst.commons.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.commons.ui.note.RenderApproveButton
import com.vitorpamplona.amethyst.commons.ui.note.RenderCodeSnippetHeaderForThread
import com.vitorpamplona.amethyst.commons.ui.note.RenderDraft
import com.vitorpamplona.amethyst.commons.ui.note.RenderNoteRow
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.commons.ui.note.calculateBackgroundColor
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.DisplayZapSplits
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayFollowingCommunityInPost
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayFollowingHashtagsInPost
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayLocation
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayPoW
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayReward
import com.vitorpamplona.amethyst.commons.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.commons.ui.note.elements.Reward
import com.vitorpamplona.amethyst.commons.ui.note.elements.ShowForkInformation
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgoStyle
import com.vitorpamplona.amethyst.commons.ui.note.nip22Comments.DisplayCommentScope
import com.vitorpamplona.amethyst.commons.ui.note.observeEdits
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.note.types.BadgeDisplay
import com.vitorpamplona.amethyst.commons.ui.note.types.DisplayStarterPack
import com.vitorpamplona.amethyst.commons.ui.note.types.DrssEpisodeAudio
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.ui.note.types.LongFormPublishedAndReadingTime
import com.vitorpamplona.amethyst.commons.ui.note.types.LongFormTopicChips
import com.vitorpamplona.amethyst.commons.ui.note.types.MAX_THREAD_SECTIONS
import com.vitorpamplona.amethyst.commons.ui.note.types.PublicationHeader
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderChat
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderSoftwareRelease
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderTextEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.RenderDListItem
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.isDListItemPage
import com.vitorpamplona.amethyst.commons.ui.note.types.estimateReadingMinutes
import com.vitorpamplona.amethyst.commons.ui.note.types.observeZapSender
import com.vitorpamplona.amethyst.commons.ui.note.types.publishedAtWorthShowing
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.RenderFeedState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.PublicChatChannelHeader
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.PaddingHorizontal12Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size40dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.selectedNote
import com.vitorpamplona.amethyst.commons.ui.thread.drawReplyLevel
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.thread.LevelFeedViewModel
import com.vitorpamplona.amethyst.commons.wot.network.TrustVerdicts
import com.vitorpamplona.quartz.experimental.bounties.bountyBaseReward
import com.vitorpamplona.quartz.experimental.forks.IForkableEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.isNip82SoftwareRelease
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geoHashOrScope
import com.vitorpamplona.quartz.nip13Pow.strongPoWOrNull
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.review.isGitThreadResolution
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.hasZapSplitSetup
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.communityAddress
import com.vitorpamplona.quartz.nip72ModCommunities.isACommunityPost
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.collection.ProductCollectionEvent
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.parseBigDecimalOrNull
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Composable
fun ThreadFeedView(
    noteId: String,
    viewModel: LevelFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RefresheableBox(viewModel) {
        RenderFeedState(
            viewModel = viewModel,
            accountViewModel = accountViewModel,
            listState = viewModel.llState,
            nav = nav,
            routeForLastRead = null,
            onLoaded = {
                RenderThreadFeed(noteId, it, viewModel.llState, viewModel, accountViewModel, nav)
            },
        )
    }
}

@Composable
fun RenderThreadFeed(
    noteId: String,
    loaded: FeedState.Loaded,
    listState: LazyListState,
    viewModel: LevelFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    val levels by viewModel.levelCacheFlow.collectAsStateWithLifecycle()

    // Replies whose author is outside the Web of Trust network start collapsed. Empty while no
    // network is active, so threads look exactly as before for users without one.
    val verdicts by accountViewModel.account.trustVerdicts
        .collectAsStateWithLifecycle()
    val outsideNetwork =
        remember(items, levels, verdicts, noteId) {
            if (!verdicts.isActive) emptySet() else outOfNetworkReplies(items.list, levels, noteId, verdicts)
        }

    // A review thread an authorised member marked resolved (NIP-22 reply with `["l","resolved"]`)
    // also starts collapsed, as gitworkshop folds it.
    val rootEvent = items.list.firstOrNull()?.event
    val gitAnnouncements = observeGitRepositoryAnnouncements(rootEvent)
    val resolvedThreads =
        remember(items, rootEvent, gitAnnouncements) {
            if (rootEvent !is GitIssueEvent && rootEvent !is GitPatchEvent && rootEvent !is GitPullRequestEvent) {
                emptySet()
            } else {
                items.list.mapNotNullTo(HashSet()) { note ->
                    val event = note.event as? CommentEvent
                    if (event != null && event.isGitThreadResolution() && LocalCache.gitAuthority.isMemberAt(event.pubKey, rootEvent, event.createdAt)) note.replyTo?.lastOrNull()?.idHex else null
                }
            }
        }
    val collapsedByDefault = remember(outsideNetwork, resolvedThreads) { if (resolvedThreads.isEmpty()) outsideNetwork else outsideNetwork + resolvedThreads }

    // Hides every descendant of a collapsed reply and counts how many were hidden. The feed is
    // ordered depth-first, so a note's descendants are the contiguous items that follow it with a
    // strictly deeper reply level.
    val visible by
        remember(items, levels, collapsedByDefault) {
            derivedStateOf {
                val full = items.list
                if (viewModel.collapsedReplies.isEmpty() && collapsedByDefault.isEmpty()) {
                    VisibleThread(full, emptyMap())
                } else {
                    val result = ArrayList<Note>(full.size)
                    val hiddenCounts = HashMap<String, Int>()
                    var hideDeeperThan = Int.MAX_VALUE
                    var collapsedAncestorId: String? = null
                    full.forEach { note ->
                        val level = levels[note] ?: 0
                        if (level > hideDeeperThan) {
                            collapsedAncestorId?.let { hiddenCounts[it] = (hiddenCounts[it] ?: 0) + 1 }
                            return@forEach
                        }

                        hideDeeperThan = Int.MAX_VALUE
                        collapsedAncestorId = null
                        result.add(note)

                        if (viewModel.isCollapsed(note.idHex, collapsedByDefault)) {
                            hideDeeperThan = level
                            collapsedAncestorId = note.idHex
                        }
                    }
                    VisibleThread(result, hiddenCounts)
                }
            }
        }

    // A NIP-34 status or PR update whose issue / patch / PR is in this thread draws as a timeline
    // row: the item it acts on is already on screen, so its feed card (which quotes that item)
    // would only repeat it. Cover notes on that item are versions of its summary, reached through
    // the item's edit pencil, so they are left out. The note this thread was opened on is neither:
    // tapping a patch row opens that patch to read its diff, and a cover note opened from a
    // notification or a quote is what the user came to see.
    val threadIds = remember(items) { items.list.mapTo(HashSet()) { it.idHex } }
    val visibleItems =
        remember(visible, threadIds, noteId) {
            visible.items.filterNot { it.idHex != noteId && isHiddenInGitThread(it.event) && it.replyTo?.lastOrNull()?.idHex in threadIds }
        }
    val position = visibleItems.indexOfFirst { it.idHex == noteId }

    LaunchedEffect(noteId, position) {
        // hack to allow multiple scrolls to Item while posts on the screen load.
        // This is important when clicking on a reply of an older thread in Notifications
        // In that case, this screen will open with 0-1 items, and the scrollToItem below
        // will not change the state of the screen (too few items, scroll is not available)
        // as the app loads the reaming of the thread the position of the reply changes
        // and because there wasn't a possibility to scroll before and now there is one,
        // the screen stays at the top. Once the thread has enough replies, the lazy column
        // updates with new items correctly. It just needs a few items to start the scroll.
        //
        // This hack allows the list 1 second to fill up with more
        // records before setting up the position on the feed.
        //
        // It jumps around, but it is the best we can do.

        if (position >= 0 && !viewModel.hasDragged.value) {
            val offset =
                if (position > visibleItems.size - 3) {
                    0
                } else {
                    -200
                }

            listState.scrollToItem(position, offset)
        }
    }

    val contentPadding = rememberFeedContentPadding(FeedPadding)
    val density = LocalDensity.current
    // A NIP read here jumps to its sections; the top padding is the part a top bar covers.
    val anchors = rememberMarkdownAnchors(listState) { with(density) { contentPadding.calculateTopPadding().toPx() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().markdownAnchorViewport(anchors),
        contentPadding = contentPadding,
        state = listState,
    ) {
        itemsIndexed(
            visibleItems,
            key = { _, item -> item.idHex },
            contentType = { index, item ->
                when {
                    index == 0 -> "master"
                    viewModel.isCollapsed(item.idHex, collapsedByDefault) -> "collapsed"
                    else -> "reply"
                }
            },
        ) { index, item ->
            // Remembered per item: a fresh flow on every recomposition restarted the collector each
            // time, and starting from 0 drew every reply un-indented for a frame.
            val levelFlow = remember(item) { viewModel.levelFlowForItem(item) }
            val level = levelFlow.collectAsStateWithLifecycle(viewModel.levelCacheFlow.value[item] ?: 0)

            val modifier =
                Modifier
                    .drawReplyLevel(
                        level = level,
                        color = MaterialTheme.colorScheme.placeholderText,
                        selected =
                            if (item.idHex == noteId) {
                                MaterialTheme.colorScheme.lessImportantLink
                            } else {
                                MaterialTheme.colorScheme.placeholderText
                            },
                    )

            if (index == 0) {
                ProvideTextStyle(TextStyle(fontSize = 18.sp, lineHeight = 1.20.em)) {
                    // A git item's tools (diff, clone, status buttons) belong to its own thread.
                    CompositionLocalProvider(LocalGitThreadRoot provides item.idHex, LocalThreadReaderAnchors provides anchors) {
                        NoteMaster(
                            baseNote = item,
                            modifier = modifier,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            } else if (item.idHex != noteId && isGitTimelineEvent(item.event) && item.replyTo?.lastOrNull()?.idHex in threadIds) {
                // Before the out-of-network collapse: a maintainer outside the viewer's network
                // still changed the item, and a stranger's change already reads as "Proposed".
                GitThreadTimelineRow(
                    baseNote = item,
                    modifier = modifier,
                    background = if (item.idHex == noteId) MaterialTheme.colorScheme.selectedNote else Color.Transparent,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            } else if (viewModel.isCollapsed(item.idHex, collapsedByDefault)) {
                CollapsedNoteCompose(
                    baseNote = item,
                    modifier = modifier,
                    hiddenReplyCount = visible.hiddenCounts[item.idHex] ?: 0,
                    outsideNetwork = item.idHex in outsideNetwork,
                    onExpand = { viewModel.toggleCollapsed(item.idHex, collapsedByDefault) },
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            } else {
                val selectedNoteColor = MaterialTheme.colorScheme.selectedNote
                val background =
                    remember {
                        if (item.idHex == noteId) mutableStateOf(selectedNoteColor) else null
                    }

                // Tapping a reply collapses/expands its children, except for the user's own
                // drafts: those open the edit-draft screen so the post can be resumed, matching
                // the behavior everywhere else a draft is tapped.
                val onClick =
                    remember(item, collapsedByDefault) {
                        {
                            if (item.isDraft()) {
                                nav.nav {
                                    withContext(Dispatchers.IO) {
                                        routeEditDraftTo(item, accountViewModel.account)
                                    }
                                }
                            } else {
                                viewModel.toggleCollapsed(item.idHex, collapsedByDefault)
                            }
                        }
                    }

                NoteCompose(
                    baseNote = item,
                    modifier = modifier,
                    isBoostedNote = false,
                    unPackReply = ReplyRenderType.NONE,
                    quotesLeft = 3,
                    parentBackgroundColor = background,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onClick = onClick,
                )
            }

            HorizontalDivider(
                thickness = DividerThickness,
            )
        }
    }
}

/**
 * Replies (by id) whose author is outside the Web of Trust network: they start collapsed. The
 * thread's root, its author, the focused note and the replies above it are never included (a
 * collapsed reply hides everything under it, which would hide the note the user opened), nor
 * is a reply that the root's author or someone in the network answered; neither are follows or
 * the user (see [TrustVerdicts]).
 */
private fun outOfNetworkReplies(
    thread: List<Note>,
    levels: Map<Note, Int>,
    focusedNoteId: String,
    verdicts: TrustVerdicts,
): Set<String> {
    val root = thread.firstOrNull() ?: return emptySet()
    val rootAuthor = root.author?.pubkeyHex
    val keepOpen = focusedNoteAndAncestors(thread, levels, focusedNoteId)
    val answered =
        knownReplyBelow(thread, levels) { reply ->
            val author = reply.author?.pubkeyHex
            author != null && (author == rootAuthor || verdicts.explain(author).isKnown == true)
        }
    val result = HashSet<String>()
    for (i in 1 until thread.size) {
        val note = thread[i]
        if (note.idHex in keepOpen) continue
        // Levels are computed off the main thread and can trail the list: a reply with none yet
        // cannot be placed (it may be above the focused note), so it is judged once it has one.
        if (levels[note] == null) continue
        val author = note.author?.pubkeyHex ?: continue
        if (author == rootAuthor) continue
        if (!answered[i] && verdicts.isOutside(author)) result.add(note.idHex)
    }
    return result
}

/**
 * For each item of [thread], whether a [known] reply is anywhere under it. Collapsing a
 * reply hides everything under it, which would hide their part of the conversation. The thread
 * is depth-first: the replies under a note are the items after it at a deeper level. One
 * backwards pass: the stack holds the subtrees already seen, nearest on top, each with whether
 * a known author is in it; a note owns the ones deeper than itself.
 */
internal fun knownReplyBelow(
    thread: List<Note>,
    levels: Map<Note, Int>,
    known: (Note) -> Boolean,
): BooleanArray {
    val result = BooleanArray(thread.size)
    val stackLevels = IntArray(thread.size)
    val stackKnown = BooleanArray(thread.size)
    var top = 0
    for (i in thread.indices.reversed()) {
        val level = levels[thread[i]] ?: continue
        var below = false
        while (top > 0 && stackLevels[top - 1] > level) {
            top--
            below = below || stackKnown[top]
        }
        result[i] = below
        stackLevels[top] = level
        stackKnown[top] = below || known(thread[i])
        top++
    }
    return result
}

/**
 * The focused note and every reply above it. The thread is depth-first, so its ancestors are
 * the earlier items each at a shallower level than the last one found.
 */
internal fun focusedNoteAndAncestors(
    thread: List<Note>,
    levels: Map<Note, Int>,
    focusedNoteId: String,
): Set<String> {
    val focused = thread.indexOfFirst { it.idHex == focusedNoteId }
    if (focused < 0) return setOf(focusedNoteId)
    val result = HashSet<String>()
    result.add(focusedNoteId)
    var level = levels[thread[focused]] ?: return result
    for (i in focused - 1 downTo 0) {
        val candidate = levels[thread[i]] ?: continue
        if (candidate < level) {
            result.add(thread[i].idHex)
            level = candidate
            if (level <= 0) break
        }
    }
    return result
}

/**
 * Holds the thread items currently visible after collapsing, plus, for each collapsed reply id,
 * the number of descendant replies that were hidden underneath it.
 */
private class VisibleThread(
    val items: List<Note>,
    val hiddenCounts: Map<String, Int>,
)

/**
 * Compact rendering of a thread reply the user has collapsed: avatar, author name, and the
 * first two lines of its content. The right side shows how many replies are hidden underneath it
 * and an ExpandMore indicator. Tapping anywhere on the row expands the note (and reveals its
 * hidden children) again.
 */
@Composable
private fun CollapsedNoteCompose(
    baseNote: Note,
    modifier: Modifier,
    hiddenReplyCount: Int,
    outsideNetwork: Boolean = false,
    onExpand: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchNoteEvent(baseNote, accountViewModel, nav) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    // Match NoteComposeLayout: 12dp leading/trailing padding, 10dp top.
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoteAuthorPicture(baseNote = baseNote, size = Size40dp, accountViewModel = accountViewModel, nav = nav)

            // 10dp gap between the avatar and the name, same as NoteComposeLayout's authorContentGap.
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                NoteUsernameDisplay(baseNote, accountViewModel = accountViewModel, nav = nav)

                if (outsideNetwork) {
                    // No preview: a reply collapsed for being outside the network is often spam.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            symbol = MaterialSymbols.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.placeholderText,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringRes(Res.string.thread_reply_outside_network),
                            color = MaterialTheme.colorScheme.placeholderText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    LoadDecryptedContent(baseNote, accountViewModel) { body ->
                        Text(
                            text = body,
                            color = MaterialTheme.colorScheme.placeholderText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(modifier = StdHorzSpacer)

            // The label carries an explicit '\n' so the count and the word always stack on two
            // lines (e.g. "+1\nreply"), keeping the horizontal footprint small and consistent.
            // Always shown — even "+0 replies" — so every collapsed card looks the same.
            Text(
                text = pluralStringRes(Res.plurals.thread_collapsed_reply_count, hiddenReplyCount, hiddenReplyCount),
                color = MaterialTheme.colorScheme.placeholderText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
            )

            Spacer(modifier = StdHorzSpacer)

            ExpandMoreIcon(modifier = Modifier.size(Size20dp), contentDescriptor = Res.string.expand)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteMaster(
    baseNote: Note,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchNoteEvent(
        baseNote = baseNote,
        accountViewModel = accountViewModel,
        nav,
        modifier,
    ) {
        CheckHiddenFeedWatchBlockAndReport(
            note = baseNote,
            modifier = modifier,
            ignoreAllBlocksAndReports = false,
            showHiddenWarning = true,
            accountViewModel = accountViewModel,
            nav = nav,
        ) { canPreview ->
            LongPressToQuickAction(baseNote, accountViewModel, nav) { showPopup ->
                FullBleedNoteCompose(
                    baseNote,
                    modifier
                        .combinedClickable(
                            onClick = {},
                            onLongClick = showPopup,
                        ),
                    canPreview,
                    parentBackgroundColor = parentBackgroundColor,
                    accountViewModel,
                    nav,
                )
            }
        }
    }
}

/**
 * The thread list's section anchors. Only a NIP's own body is split into sections (see
 * [FullBleedNoteCompose]), so replies with headings render as before.
 */
private val LocalThreadReaderAnchors = staticCompositionLocalOf<MarkdownAnchors?> { null }

@Composable
private fun FullBleedNoteCompose(
    baseNote: Note,
    modifier: Modifier,
    canPreview: Boolean,
    parentBackgroundColor: MutableState<Color>?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event ?: return

    val isDraft = baseNote.isDraft()
    val backgroundColor =
        calculateBackgroundColor(
            baseNote.createdAt(),
            null,
            parentBackgroundColor,
            accountViewModel,
        )

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        val editState = observeEdits(baseNote = baseNote, accountViewModel = accountViewModel)

        // Zap receipts are signed by the recipient's lightning provider; show the
        // sender from the embedded zap request instead of the service key.
        val zapSender =
            if (noteEvent is ZapReceiptEvent) {
                observeZapSender(baseNote, accountViewModel).value
            } else {
                null
            }

        Row(
            modifier =
                Modifier
                    .padding(start = 12.dp, end = 12.dp)
                    .clickable(onClick = { (zapSender ?: baseNote.author)?.let { nav.nav(routeFor(it)) } }),
        ) {
            if (zapSender != null) {
                UserPicture(
                    user = zapSender,
                    size = Size55dp,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            } else {
                NoteAuthorPicture(
                    baseNote = baseNote,
                    size = Size55dp,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            Column(modifier = Modifier.padding(start = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Size5dp),
                ) {
                    if (zapSender != null) {
                        UsernameDisplay(zapSender, Modifier.weight(1f), accountViewModel = accountViewModel)
                    } else {
                        NoteUsernameDisplay(baseNote, Modifier.weight(1f), accountViewModel = accountViewModel, nav = nav)
                    }

                    if (isDraft) {
                        ObserveDraftEvent(baseNote, accountViewModel) { draftNote ->
                            val isCommunityPost by remember(draftNote) {
                                derivedStateOf { draftNote.event?.isACommunityPost() == true }
                            }

                            if (isCommunityPost) {
                                DisplayFollowingCommunityInPost(draftNote, accountViewModel, nav)
                            } else {
                                DisplayFollowingHashtagsInPost(draftNote, accountViewModel, nav)
                            }
                        }
                    } else {
                        val isCommunityPost by remember(baseNote) {
                            derivedStateOf { baseNote.event?.isACommunityPost() == true }
                        }

                        if (isCommunityPost) {
                            DisplayFollowingCommunityInPost(baseNote, accountViewModel, nav)
                        } else {
                            DisplayFollowingHashtagsInPost(baseNote, accountViewModel, nav)
                        }
                    }

                    CheckAndDisplayEditStatus(editState)

                    Expiration(baseNote, accountViewModel)

                    // The dotted TimeAgo carries its own " • " separator and the
                    // options button has slack around its icon: keep the pair unspaced.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TimeAgo(note = baseNote, style = TimeAgoStyle.DottedTight)

                        MoreOptionsButton(baseNote, editState, accountViewModel, nav)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Size5dp),
                ) {
                    Column(
                        Modifier.weight(1f),
                    ) {
                        if (noteEvent is IForkableEvent && noteEvent.isAFork()) {
                            ShowForkInformation(noteEvent, Modifier, accountViewModel, nav)
                        } else {
                            ObserveDisplayNip05Status(
                                baseNote,
                                accountViewModel,
                                nav,
                            )
                        }
                    }

                    val geo = remember { noteEvent.geoHashOrScope() }
                    if (geo != null) {
                        DisplayLocation(geo, accountViewModel, nav)
                    }

                    val baseReward = remember { noteEvent.bountyBaseReward()?.let { Reward(it) } }
                    if (baseReward != null) {
                        DisplayReward(baseReward, baseNote, accountViewModel)
                    }

                    val pow = remember(noteEvent) { noteEvent.strongPoWOrNull() }
                    if (pow != null) {
                        DisplayPoW(pow, accountViewModel)
                    }

                    if (isDraft) {
                        DisplayDraft()
                    }

                    DisplayOtsIfInOriginal(baseNote, editState, accountViewModel)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Keep in sync with hasThreadHeader(): these kinds get a thread-only header here and a
        // plain text body below instead of their feed card.
        when (noteEvent) {
            is BadgeDefinitionEvent -> BadgeDisplay(baseNote = baseNote, accountViewModel)
            is LongFormContentEvent -> RenderLongFormHeaderForThread(noteEvent, baseNote, accountViewModel, nav)
            is ThreadEvent -> RenderThreadHeaderForThread(noteEvent)
            is WikiArticleEvent -> RenderWikiHeaderForThread(noteEvent, accountViewModel, nav)
            is NipTextEvent -> {
                // A NIP is opened to be read: its body starts expanded, not cut at "Show More".
                remember(baseNote.idHex) { ShowFullTextCache.cache.put(baseNote.idHex, true) }
                // While it is read, a new version of the NIP arrives and replaces this one in place.
                if (baseNote is AddressableNote) WatchNipSubscription(baseNote, accountViewModel)
                RenderNipHeaderForThread(noteEvent, nav, LocalThreadReaderAnchors.current)
            }
            is ClassifiedsEvent -> RenderClassifiedsReaderForThread(noteEvent, baseNote, accountViewModel, nav)
            is CodeSnippetEvent -> RenderCodeSnippetHeaderForThread(noteEvent)
        }

        Row(
            modifier = PaddingHorizontal12Modifier,
        ) {
            Column {
                // Only what the thread view draws differently from the feed is listed here. Every
                // other kind goes through the feed's own per-kind dispatch (RenderNoteRow), so a
                // kind that gets a card there gets it here too instead of falling back to text.
                if (noteEvent is ChannelCreateEvent) {
                    PublicChatChannelHeader(
                        channelHex = noteEvent.id,
                        sendToChannel = true,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                } else if (noteEvent is ChannelMetadataEvent) {
                    noteEvent.channelId()?.let {
                        PublicChatChannelHeader(
                            channelHex = it,
                            sendToChannel = true,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                } else if (noteEvent is StarterPackEvent) {
                    // The full pack here; the feed shows the short card.
                    DisplayStarterPack(baseNote, false, accountViewModel, nav)
                } else if (noteEvent is ReleaseArtifactSetEvent && noteEvent.isNip82SoftwareRelease()) {
                    // A plain NIP-51 release set has nothing extra to show here, so it takes the feed's list card.
                    RenderSoftwareRelease(baseNote, backgroundColor, accountViewModel, nav, expanded = true)
                } else if (noteEvent is ProductCollectionEvent) {
                    // The collection's page: more of its products and shipping than a feed card.
                    RenderProductCollection(baseNote, false, accountViewModel, nav, isPage = true)
                } else if (isDListItemPage(noteEvent)) {
                    // The item's own page: a concept with a view of its own draws it (tier three).
                    RenderDListItem(baseNote, 3, backgroundColor, accountViewModel, nav, isPage = true)
                } else if (noteEvent is DraftWrapEvent) {
                    RenderDraft(baseNote, 3, ReplyRenderType.FULL, backgroundColor, accountViewModel, nav)
                } else if (noteEvent is PublicationIndexEvent) {
                    // Uncapped here: the thread view is where a publication is actually read,
                    // so the whole table of contents is listed rather than a feed-sized taste.
                    PublicationHeader(noteEvent, baseNote, false, canPreview, quotesLeft = 3, backgroundColor, accountViewModel, nav, MAX_THREAD_SECTIONS)
                } else if (noteEvent is ChatEvent) {
                    RenderChat(
                        note = baseNote,
                        makeItShort = false,
                        canPreview = canPreview,
                        quotesLeft = 3,
                        unPackReply = ReplyRenderType.NONE,
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                } else if (noteEvent is CommentEvent) {
                    // A comment that answers a scope rather than a note has no in-cache parent, so
                    // it surfaces here as the thread's own "master" note. Show that scope for
                    // context, same as the reply-context branch in RenderTextEvent does for
                    // non-root occurrences of the same comment.
                    DisplayCommentScope(noteEvent, accountViewModel, nav)
                    RenderTextEvent(
                        baseNote,
                        false,
                        canPreview,
                        quotesLeft = 3,
                        unPackReply = ReplyRenderType.NONE,
                        backgroundColor,
                        editState,
                        accountViewModel,
                        nav,
                    )
                } else if (hasThreadHeader(noteEvent)) {
                    // The header above already drew the card's title/cover/summary; the body is the
                    // full text, not the feed card, which would repeat that header. A NIP's body is
                    // split at its headings so its table of contents and `#section` links can reach them.
                    val anchors = if (noteEvent is NipTextEvent) LocalThreadReaderAnchors.current else null
                    CompositionLocalProvider(LocalMarkdownAnchors provides anchors) {
                        RenderTextEvent(
                            baseNote,
                            false,
                            canPreview,
                            quotesLeft = 3,
                            unPackReply = ReplyRenderType.NONE,
                            backgroundColor,
                            editState,
                            accountViewModel,
                            nav,
                        )
                    }
                } else {
                    RenderNoteRow(
                        baseNote = baseNote,
                        backgroundColor = backgroundColor,
                        makeItShort = false,
                        canPreview = canPreview,
                        quotesLeft = 3,
                        unPackReply = ReplyRenderType.NONE,
                        editState = editState,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }

        val zapSplits = remember(noteEvent) { noteEvent.hasZapSplitSetup() }
        if (zapSplits) {
            Spacer(modifier = DoubleVertSpacer)
            Row(PaddingHorizontal12Modifier) {
                DisplayZapSplits(noteEvent, false, accountViewModel, nav)
            }
        }

        RenderApprovalIfNeeded(baseNote, accountViewModel, nav)

        ReactionsRow(baseNote, showReactionDetail = true, addPadding = true, editState = editState, accountViewModel = accountViewModel, nav = nav)
    }
}

/**
 * The kinds the master note gives a thread-only header (the `when` above its body). Their body is
 * the plain text renderer, not their feed card, which would draw that header a second time.
 */
private fun hasThreadHeader(event: Event) =
    event is BadgeDefinitionEvent ||
        event is LongFormContentEvent ||
        event is ThreadEvent ||
        event is WikiArticleEvent ||
        event is NipTextEvent ||
        event is ClassifiedsEvent ||
        event is CodeSnippetEvent

@Composable
private fun RenderApprovalIfNeeded(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (baseNote.isNewThread()) {
        val communityAddress =
            remember(baseNote) {
                baseNote.event?.communityAddress()
            }
        communityAddress?.let {
            LoadAddressableNote(it) { community ->
                if (community != null) {
                    val showApproveButton by observeCommunityApprovalNeedStatus(baseNote, community, accountViewModel)
                    if (showApproveButton == true) {
                        Row(PaddingHorizontal12Modifier) {
                            RenderApproveButton(baseNote, community, accountViewModel)
                        }
                    }
                }
            }
        }
    }
}

@Suppress("StateFlowValueCalledInComposition")
@Composable
private fun RenderClassifiedsReaderForThread(
    noteEvent: ClassifiedsEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val imageSet =
        noteEvent.imageMetas().ifEmpty { null }?.map {
            MediaUrlImage(
                url = it.url,
                description = it.alt,
                hash = it.hash,
                blurhash = it.blurhash,
                dim = it.dimension,
                uri = note.toNostrUri(),
                mimeType = it.mimeType,
                thumbhash = it.thumbhash,
            )
        }

    val title = remember(noteEvent) { noteEvent.title() }
    val summary =
        remember(noteEvent) {
            val sum = noteEvent.summary()
            if (sum != noteEvent.content) {
                sum
            } else {
                null
            }
        }
    val price = remember(noteEvent) { noteEvent.price() }
    val location = remember(noteEvent) { noteEvent.location() }
    val status = remember(noteEvent) { noteEvent.listingStatus() }

    Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Column {
            if (!imageSet.isNullOrEmpty()) {
                AutoNonlazyGrid(imageSet.size) {
                    ZoomableContentView(
                        content = imageSet[it],
                        images = imageSet.toImmutableList(),
                        roundedCorner = false,
                        contentScale = ContentScale.Crop,
                        accountViewModel = accountViewModel,
                    )
                }
            } else {
                DefaultImageHeader(note, accountViewModel)
            }

            Row(
                Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                }
                status?.let {
                    Spacer(Modifier.width(5.dp))
                    ListingStatusBadge(it)
                }
            }

            price?.let {
                Row(
                    Modifier.padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val newAmount = parseBigDecimalOrNull(price.amount)?.let { showAmount(it) } ?: price.amount

                    val priceTag =
                        remember(noteEvent) {
                            if (price.frequency != null && price.currency != null) {
                                "$newAmount ${price.currency}/${price.frequency}"
                            } else if (price.currency != null) {
                                "$newAmount ${price.currency}"
                            } else {
                                newAmount
                            }
                        }

                    Text(
                        text = priceTag,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )

                    location?.let {
                        Text(
                            text = it,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            summary?.let {
                Row(
                    Modifier.padding(top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        color = Color.Gray,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            ClassifiedsOpenMarketsDetails(noteEvent, accountViewModel, nav)

            Row(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, bottom = 5.dp, top = 15.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterRes(Res.drawable.ic_dm, 5),
                    stringRes(Res.string.send_a_direct_message),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = StdHorzSpacer)

                Text(stringRes(id = Res.string.send_the_seller_a_message))
            }

            Row(
                modifier =
                    Modifier
                        .padding(start = 10.dp, end = 10.dp, bottom = 5.dp, top = 5.dp)
                        .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val sellerName =
                    note.author
                        ?.metadataOrNull()
                        ?.flow
                        ?.value
                        ?.info
                        ?.bestName()

                val msg =
                    if (sellerName != null) {
                        stringRes(
                            id = Res.string.hi_seller_is_this_still_available,
                            sellerName,
                        )
                    } else {
                        stringRes(id = Res.string.hi_there_is_this_still_available)
                    }

                var message by remember { mutableStateOf(TextFieldValue(msg)) }

                SpellCheckedTextField(
                    value = message,
                    onValueChange = { message = it },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    shape = EditFieldBorder,
                    modifier = Modifier.weight(1f, true),
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.reply_here),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    trailingIcon = {
                        ThinSendButton(
                            isActive = message.text.isNotBlank(),
                            modifier = EditFieldTrailingIconModifier,
                        ) {
                            note.author?.let {
                                nav.nav {
                                    routeToMessage(
                                        it,
                                        draftMessage = note.toNostrUri() + "\n\n" + msg,
                                        accountViewModel = accountViewModel,
                                    )
                                }
                            }
                        }
                    },
                    colors =
                        TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                )
            }
        }
    }
}

@Composable
private fun RenderLongFormHeaderForThread(
    noteEvent: LongFormContentEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The author row above already shows created_at; repeat a date only when published_at differs.
    val publishedAt = remember(noteEvent) { publishedAtWorthShowing(noteEvent.publishedAt(), noteEvent.createdAt) }
    val readingMinutes = remember(noteEvent) { estimateReadingMinutes(noteEvent.content) }
    val topics = remember(noteEvent) { noteEvent.topics().distinct().toImmutableList() }

    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        noteEvent.image()?.let {
            MyAsyncImage(
                imageUrl = it,
                contentDescription =
                    stringRes(
                        Res.string.preview_card_image_for,
                        it,
                    ),
                contentScale = ContentScale.FillWidth,
                mainImageModifier = Modifier,
                loadedImageModifier = MaterialTheme.colorScheme.imageModifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { DefaultImageHeaderBackground(note, accountViewModel) },
                onError = { DefaultImageHeader(note, accountViewModel) },
            )
        } ?: run {
            DefaultImageHeader(note, accountViewModel)
        }

        noteEvent.title()?.let {
            Spacer(modifier = DoubleVertSpacer)
            Text(
                text = it,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        noteEvent
            .summary()
            ?.ifBlank { null }
            ?.let {
                Spacer(modifier = StdVertSpacer)
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }

        Spacer(modifier = StdVertSpacer)
        LongFormPublishedAndReadingTime(publishedAt, readingMinutes)

        // A podcast episode plays here from where the user left it, and keeps their place.
        if (note is AddressableNote) {
            Spacer(modifier = StdVertSpacer)
            DrssEpisodeAudio(note, noteEvent, accountViewModel)
        }

        if (topics.isNotEmpty()) {
            Spacer(modifier = StdVertSpacer)
            LongFormTopicChips(topics, nav)
        }
    }
}

@Composable
private fun RenderThreadHeaderForThread(noteEvent: ThreadEvent) {
    noteEvent.title()?.let {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            Text(
                text = it,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun RenderWikiHeaderForThreadPreview() {
    val event = Event.fromJson("{\"id\":\"277f982a4cd3f67cc47ad9282176acabee1713848f547d6021e0c155572078e1\",\"pubkey\":\"460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c\",\"created_at\":1708695717,\"kind\":30818,\"tags\":[[\"d\",\"amethyst\"],[\"a\",\"30818:f03e7c5262648e0b7823dfb49f8f17309cfec9cb14711413dcabdf3d7fc6369a:amethyst\",\"wss://relay.nostr.band\",\"fork\"],[\"e\",\"ceabc60c8022c472c727aa25ae7691885964366386ce265c47e5a78be6cb00be\",\"wss://relay.nostr.band\",\"fork\"],[\"title\",\"Amethyst\"],[\"published_at\",\"1708707133\"]],\"content\":\"An Android-only app written in Kotlin with support for over 90 event kinds. \\n\\n![](https://play-lh.googleusercontent.com/lvZlAm9dBrpHeOo7sIPKCsiKOLYLhR2b0FiOT4tyiwWO2dvsR2gDS0xk9tOOr9U-6uM=w240-h480-rw)\\n\",\"sig\":\"6748126a909a20dbdb67947a09d64e41d7140a79335a4ad675c6173d7dd5dbcab9c360dec617bd67bbbc20dfad416b15056eda2e20716cd6c425a84301a125a0\"}") as WikiArticleEvent
    val accountViewModel = mockAccountViewModel()

    val editState =
        remember {
            mutableStateOf(GenericLoadable.Empty<EditState>())
        }

    runBlocking {
        withContext(Dispatchers.IO) {
            LocalCache.justConsume(event, null, false)
        }
    }

    val nav = EmptyNav()

    LoadNote(baseNoteHex = "277f982a4cd3f67cc47ad9282176acabee1713848f547d6021e0c155572078e1") { baseNote ->
        ThemeComparisonColumn {
            val bg = MaterialTheme.colorScheme.background
            val backgroundColor =
                remember {
                    mutableStateOf(bg)
                }

            Column {
                RenderWikiHeaderForThread(noteEvent = event, accountViewModel = accountViewModel, nav)
                RenderTextEvent(
                    baseNote!!,
                    makeItShort = false,
                    canPreview = true,
                    quotesLeft = 3,
                    unPackReply = ReplyRenderType.NONE,
                    backgroundColor = backgroundColor,
                    editState = editState,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
private fun RenderWikiHeaderForThread(
    noteEvent: WikiArticleEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Column {
            noteEvent.image()?.let {
                AsyncImage(
                    model = it,
                    contentDescription =
                        stringRes(
                            Res.string.preview_card_image_for,
                            it,
                        ),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            noteEvent.title()?.let {
                Spacer(modifier = DoubleVertSpacer)
                Text(
                    text = it,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            noteEvent
                .summary()
                ?.ifBlank { null }
                ?.let {
                    Spacer(modifier = DoubleVertSpacer)
                    Text(
                        text = it,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Gray,
                    )
                }
        }
    }
}
