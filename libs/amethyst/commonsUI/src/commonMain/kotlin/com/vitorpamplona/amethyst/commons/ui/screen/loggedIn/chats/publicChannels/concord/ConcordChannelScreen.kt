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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.publicChannels.concord.datasource.ConcordChannelHistorySubAssembler
import com.vitorpamplona.amethyst.commons.chats.publicChannels.dal.ChannelFeedViewModel
import com.vitorpamplona.amethyst.commons.chats.ui.ReplyModeToggle
import com.vitorpamplona.amethyst.commons.chats.ui.ThinSendButton
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.chats.formatHistoryReachDate
import com.vitorpamplona.amethyst.commons.model.concord.ConcordCommunitySession
import com.vitorpamplona.amethyst.commons.model.concord.ConcordMembership
import com.vitorpamplona.amethyst.commons.model.concordChannelLastReadRoute
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.chat_composer_hint
import com.vitorpamplona.amethyst.commons.resources.concord_banned_notice
import com.vitorpamplona.amethyst.commons.resources.concord_dissolved_read_only
import com.vitorpamplona.amethyst.commons.resources.concord_private_channel_cut
import com.vitorpamplona.amethyst.commons.resources.concord_private_channel_no_key
import com.vitorpamplona.amethyst.commons.resources.concord_send_image_title
import com.vitorpamplona.amethyst.commons.resources.concord_timer_active
import com.vitorpamplona.amethyst.commons.resources.concord_typing_many
import com.vitorpamplona.amethyst.commons.resources.concord_typing_one
import com.vitorpamplona.amethyst.commons.resources.concord_typing_two
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberLongNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.DmHistoryLoadingCard
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachCursor
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachMarkers
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachSentinels
import com.vitorpamplona.amethyst.commons.ui.feeds.RelayReachState
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.concordTimerText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.upload.ChatFileUploader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.upload.SuccessfulUploads
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordPinnedButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordPinnedMessagesSheet
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelHistorySubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.rememberConcordChannelPins
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.send.ConcordNewMessageViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.ChatFileUploadDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.DisplayReplyingToNote
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.EditingMessageBanner
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.toConcordImeta
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPickerButton
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeyring
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayPagingProgress
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The chat screen of one Concord Channel. Messages are real Notes in [LocalCache]
 * attached to the channel, so the feed reuses the shared [RefreshingChatroomFeedView]
 * (avatars, reactions, replies, zaps, OTS) and the composer reuses the same
 * @-mention / inline-mention / reply machinery as the other chats. Only the send
 * path is Concord-specific: it wraps the message on the channel plane.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcordChannelScreen(
    communityId: String,
    channelId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ConcordChannelSubscription(accountViewModel.dataSources().concordChannels, accountViewModel)
    // Load the user's custom-emoji packs so the `:shortcode:` composer autocomplete has entries.
    WatchAndLoadMyEmojiList(accountViewModel)
    ConcordChannelHistorySubscription(communityId, channelId, accountViewModel.dataSources().concordChannelHistory, accountViewModel)

    val account = accountViewModel.account
    val channel = remember(account, communityId, channelId) { LocalCache.getOrCreateConcordChannel(ConcordChannelId(communityId, channelId)) }

    val feedViewModel: ChannelFeedViewModel =
        rememberViewModel(
            key = channel.channelId.toKey() + "ConcordFeedViewModel",
            factory = ChannelFeedViewModel.Factory(channel, account),
        )
    WatchLifecycleAndUpdateModel(feedViewModel)

    // Backward history pager for this open channel (older wraps by until+limit, per relay), mirroring
    // the NIP-04 per-conversation history: markers drive paging while on screen, a status card sits at
    // the oldest end, and an empty channel bootstraps one page so there is something to scroll from.
    val history = remember(accountViewModel) { accountViewModel.dataSources().concordChannelHistory.history }
    val loadingHistory by history.loadingMore.collectAsStateWithLifecycle()
    val historyStatus by history.status.collectAsStateWithLifecycle()
    val limits =
        remember(historyStatus) {
            buildList {
                if (!historyStatus.exhausted) {
                    historyStatus.relayProgress.forEach { (relay, p) ->
                        add(RelayReachCursor("cord:${relay.url}", relayShortName(relay), p.reachedUntil, reachState(p), "Concord") { history.advance(relay) })
                    }
                }
            }
        }
    ConcordBackfillHistoryToWindow(feedViewModel.feedState, history)

    val newMessageModel: ConcordNewMessageViewModel = rememberViewModel(key = channel.channelId.toKey() + "ConcordNewMessageViewModel") { ConcordNewMessageViewModel() }
    newMessageModel.init(accountViewModel)
    newMessageModel.load(communityId, channelId)

    // CORD-04 §7 Pins: the header's entry point, the sheet it opens and the jump it requests. The
    // delayed duty writes a PIN_MESSAGES holder owes run from the account (scheduleConcordPinDuties).
    val pins by rememberConcordChannelPins(communityId, channelId, accountViewModel)
    var showPins by remember { mutableStateOf(false) }
    val jumpToNoteId = remember { mutableStateOf<String?>(null) }
    pins?.let { current ->
        if (showPins) {
            ConcordPinnedMessagesSheet(
                communityId = communityId,
                channelId = channelId,
                pins = current,
                accountViewModel = accountViewModel,
                nav = nav,
                onJumpToMessage = { jumpToNoteId.value = it },
                onDismiss = { showPins = false },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                actions = { ConcordPinnedButton(communityId, pins, accountViewModel) { showPins = true } },
                title = {
                    Column {
                        Text(channel.toBestDisplayName(), maxLines = 1)
                        channel.communityName?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    // Hidden in a list/detail screen's detail pane, where there is nothing to go back to.
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(Res.string.back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        // The composer must ride above the soft keyboard: the bare Material3 Scaffold's content
        // insets cover the system bars but not the IME. consumeWindowInsets(padding) before
        // imePadding() unions the two so the nav-bar inset already in `padding` is dropped while
        // the keyboard is up (WindowInsets.ime already spans that band) instead of stacking on top.
        Column(
            Modifier
                .fillMaxHeight()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePaddingSafe(),
        ) {
            Column(Modifier.fillMaxHeight().weight(1f, true)) {
                RefreshingChatroomFeedView(
                    feedContentState = feedViewModel.feedState,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    routeForLastRead = concordChannelLastReadRoute(communityId, channelId),
                    onWantsToReply = { newMessageModel.reply(it) },
                    onWantsToEditDraft = {},
                    onWantsToEditChatMessage = { newMessageModel.editConcordMessage(it) },
                    jumpToNoteId = jumpToNoteId,
                    onJumpHandled = { jumpToNoteId.value = null },
                    // A status card at the oldest end: shows what it's reaching for while it pages and
                    // crossfades to "All caught up" when every relay runs dry.
                    olderBoundary = {
                        DmHistoryLoadingCard(
                            "Concord",
                            "Concord",
                            loadingHistory,
                            historyStatus.exhausted,
                            historyStatus.relayCount,
                            historyStatus.stalledCount,
                            historyStatus.reachedBack,
                            historyStatus.relayProgress,
                            ::formatHistoryReachDate,
                        )
                    },
                    // Each relay's window-limit marker at its reached cursor (pure UI). Hidden when exhausted.
                    markersInGap =
                        if (limits.isEmpty()) {
                            null
                        } else {
                            { newer, older -> RelayReachMarkers(limits, newer, older) {} }
                        },
                    // Pulls each relay's next page while its marker is on screen, off viewport visibility.
                    sentinels =
                        if (limits.isEmpty()) {
                            null
                        } else {
                            { items, listState ->
                                RelayReachSentinels(limits, listState) { index -> items.getOrNull(index)?.event?.createdAt }
                            }
                        },
                )
            }

            ConcordTypingIndicator(communityId, channelId, accountViewModel)

            // canPost()/keyHeld are plain fields the revision tick rewrites; observing the channel's
            // metadata flow (invalidated on every such change) is what makes a key cut, a ban or a
            // dissolution landing while this screen is open swap the composer for its notice.
            val channelInfo by channel
                .flow()
                .metadata.stateFlow
                .collectAsStateWithLifecycle()
            val canPost = remember(channelInfo) { channel.canPost() }
            if (canPost) {
                ConcordTimerIndicator(communityId, accountViewModel)
                Spacer(modifier = DoubleVertSpacer)
                ConcordMessageComposer(
                    newMessageModel = newMessageModel,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onMessageSent = { feedViewModel.feedState.sendToTop() },
                )
            } else if (channel.dissolved) {
                // CORD-02 §9: an owner-signed tombstone seals the community read-only — the composer is
                // gone (canPost() is false) and this replaces it so the seal is explained, not silent.
                ConcordDissolvedNotice()
            } else if (channel.membership == ConcordMembership.BANNED) {
                // CORD-04 §4: every reader drops a banned author's messages, so the composer is gone;
                // say why instead of leaving the bottom of the screen silently empty.
                ConcordReadOnlyNotice(Res.string.concord_banned_notice)
            } else if (!channel.keyHeld) {
                // CORD-03 §1: a Private Channel is keyed independently; without its key there is no
                // plane only its members can read, so nothing may be posted (never to the root plane).
                // A rotation that left us out (CORD-06 §2): say we were removed, not that we never had access.
                val wasCut =
                    remember(channelInfo) {
                        account.concordSessions
                            .sessionFor(communityId)
                            ?.entry
                            ?.let { ConcordChannelKeyring.cutsOf(it).containsKey(channelId.lowercase()) } == true
                    }
                ConcordReadOnlyNotice(if (wasCut) Res.string.concord_private_channel_cut else Res.string.concord_private_channel_no_key)
            }
        }
    }
}

/**
 * The read-only notice shown where the composer would be once a community is dissolved (CORD-02 §9).
 * The tombstone seals the community: history stays readable, but no member may post again.
 */
@Composable
private fun ConcordDissolvedNotice() = ConcordReadOnlyNotice(Res.string.concord_dissolved_read_only)

/** A one-line explanation shown where the composer would be when this channel cannot be posted to. */
@Composable
private fun ConcordReadOnlyNotice(message: StringResource) {
    Text(
        text = stringRes(message),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.placeholderText,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/**
 * CORD-08: a slim "Messages disappear after 30 days" line above the composer while the community's
 * timer is on, so a member knows before sending that the message will not last.
 */
@Composable
private fun ConcordTimerIndicator(
    communityId: String,
    accountViewModel: AccountViewModel,
) {
    // Re-resolved on every session-set change: the session may not exist yet at first composition, and
    // a Refounding replaces it (a captured one would keep reading the dead epoch's fold).
    val sessions = accountViewModel.account.concordSessions
    val revision by sessions.revision.collectAsStateWithLifecycle()
    val session = remember(communityId, revision) { sessions.sessionFor(communityId) } ?: return
    val state by session.state.collectAsStateWithLifecycle()
    val secs = state?.metadata?.messageExpirationSecs() ?: return
    Text(
        text = stringRes(Res.string.concord_timer_active, concordTimerText(secs)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

/** The number of messages a freshly-opened channel eagerly backfills to before paging goes demand-driven. */
private const val CONCORD_HISTORY_TARGET = 50

/**
 * On open, eagerly backfill this channel's older history until the feed holds at least
 * [CONCORD_HISTORY_TARGET] messages (or the relays are exhausted) — mirroring Armada's multi-page
 * `backfillStore`. The live subscription only carries each plane's relay-capped recent tail, shared
 * across one merged REQ per relay for every channel; so without this, a channel that has plenty of
 * history opens showing just its last few messages until the user scrolls. Once the target is reached,
 * paging is purely demand-driven by the markers' visibility. A short startup delay skips the transient
 * empty feed that navigation flashes through. Supersedes the old empty-only bootstrap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun ConcordBackfillHistoryToWindow(
    feedContentState: FeedContentState,
    history: ConcordChannelHistorySubAssembler,
) {
    LaunchedEffect(feedContentState, history) {
        delay(1200L)
        // Reactive count of currently-loaded messages (0 while empty/loading).
        val loadedCount =
            feedContentState.feedContent.flatMapLatest { state ->
                when (state) {
                    is FeedState.Loaded -> state.feed.map { it.list.size }
                    else -> flowOf(0)
                }
            }
        // Pull another page whenever we're below target, no page is in flight, and relays aren't done.
        // Each landed page grows the count (or flips exhausted), so this re-fires page-by-page and then
        // latches off. The !loading gate prevents overlapping REQs / a tight loop.
        combine(loadedCount, history.loadingMore, history.status) { count, loading, status ->
            count < CONCORD_HISTORY_TARGET && !loading && !status.exhausted
        }.distinctUntilChanged()
            .filter { it }
            .collect { history.advanceAll() }
    }
}

/** Republish a typing heartbeat at most this often (seconds) while composing. */
private const val TYPING_HEARTBEAT_SECS = 4L

/**
 * A slim "X is typing…" line above the composer, driven by the session's ephemeral
 * typing heartbeats (kind 23311). A ~2s ticker re-applies the freshness window so a
 * typist who stops silently fades out even without a new ingest.
 */
@Composable
private fun ConcordTypingIndicator(
    communityId: String,
    channelId: String,
    accountViewModel: AccountViewModel,
) {
    val session = remember(communityId) { accountViewModel.account.concordSessions.sessionFor(communityId) } ?: return
    val typingMap by session.typing.collectAsStateWithLifecycle()

    var nowSecs by remember { mutableLongStateOf(TimeUtils.now()) }
    // Only tick while this channel actually has heartbeats, and stop once they've all aged out of
    // the freshness window — an idle channel must not wake a 2s recomposition loop forever. A new
    // heartbeat re-keys this effect (the map value changes) and restarts the fade.
    LaunchedEffect(session, channelId, typingMap[channelId]) {
        val perChannel = typingMap[channelId]
        if (perChannel.isNullOrEmpty()) return@LaunchedEffect
        while (true) {
            nowSecs = TimeUtils.now()
            if (perChannel.values.none { nowSecs - it <= ConcordCommunitySession.TYPING_STALE_SECS }) break
            delay(2000L)
        }
    }

    val typers =
        remember(typingMap, channelId, nowSecs) {
            (typingMap[channelId] ?: emptyMap())
                .filterValues { nowSecs - it <= ConcordCommunitySession.TYPING_STALE_SECS }
                .keys
                .sorted()
        }

    if (typers.isEmpty()) return

    val label =
        when (typers.size) {
            1 -> stringRes(Res.string.concord_typing_one, rememberTypistName(typers[0], accountViewModel))
            2 ->
                stringRes(
                    Res.string.concord_typing_two,
                    rememberTypistName(typers[0], accountViewModel),
                    rememberTypistName(typers[1], accountViewModel),
                )
            else -> stringRes(Res.string.concord_typing_many)
        }

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 1,
        )
    }
}

/** Resolves [hex] to its best display name, reactively, falling back to a short hex. */
@Composable
private fun rememberTypistName(
    hex: String,
    accountViewModel: AccountViewModel,
): String {
    val user = remember(hex) { LocalCache.checkGetOrCreateUser(hex) } ?: return remember(hex) { hex.take(8) }
    val info by observeUserInfo(user, accountViewModel)
    return info?.info?.bestName() ?: remember(user) { user.pubkeyDisplayHex() }
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

@Composable
private fun ConcordMessageComposer(
    newMessageModel: ConcordNewMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    onMessageSent: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val canPost by remember { derivedStateOf { newMessageModel.canPost() } }
    val shortNotice = rememberShortNotice()

    // Throttle typing heartbeats to at most one every few seconds while the field is non-empty.
    val lastTypingSecs = remember(newMessageModel.channelId) { longArrayOf(0L) }

    DisposableEffect(newMessageModel.channelId) {
        onDispose { newMessageModel.userSuggestions?.reset() }
    }

    // Encrypted image attachments: a picked image opens this dialog, which encrypts + uploads via the
    // shared NIP-17 pipeline and sends an Armada-shaped image message on the channel plane.
    newMessageModel.uploadState?.let { uploadState ->
        uploadState.multiOrchestrator?.let {
            ConcordFileUploadDialog(
                newMessageModel = newMessageModel,
                state = uploadState,
                accountViewModel = accountViewModel,
                nav = nav,
                onUpload = { onMessageSent() },
                onCancel = uploadState::reset,
            )
        }
    }

    newMessageModel.replyTo.value?.let {
        DisplayReplyingToNote(it, accountViewModel, nav) { newMessageModel.clearReply() }
        ReplyModeToggle(
            mode = newMessageModel.replyMode.value,
            onToggle = { newMessageModel.toggleReplyMode() },
        )
    }

    // Edit mode: a banner reminding the user the next send replaces this message (a kind-1010
    // edit on the channel plane), with an X to abandon the edit and clear the field.
    newMessageModel.editingMessage.value?.let {
        EditingMessageBanner(onCancel = { newMessageModel.cancelEdit() })
    }

    Column(modifier = EditFieldModifier) {
        newMessageModel.userSuggestions?.let {
            ShowUserSuggestionList(
                it,
                newMessageModel::autocompleteWithUser,
                accountViewModel,
                SuggestionListDefaultHeightChat,
            )
        }

        newMessageModel.emojiSuggestions?.let {
            ShowEmojiSuggestionList(
                it,
                newMessageModel::autocompleteWithEmoji,
                newMessageModel::autocompleteWithEmoji,
                SuggestionListDefaultHeightChat,
            )
        }

        ThinPaddingTextField(
            state = newMessageModel.message,
            onTextChanged = {
                newMessageModel.onMessageChanged()
                val community = newMessageModel.communityId
                val channel = newMessageModel.channelId
                val now = TimeUtils.now()
                if (community != null && channel != null &&
                    newMessageModel.message.text.isNotEmpty() &&
                    now - lastTypingSecs[0] >= TYPING_HEARTBEAT_SECS
                ) {
                    lastTypingSecs[0] = now
                    accountViewModel.sendConcordTyping(community, channel)
                }
            },
            onContentReceived = { uri, mimeType ->
                newMessageModel.pickedMedia(persistentListOf(SelectedMedia(uri, mimeType)))
            },
            inputTransformation = MentionPreservingInputTransformation,
            spellCheck = true,
            outputTransformation = rememberUrlUserTagOutputTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = EditFieldBorder,
            placeholder = {
                Text(
                    text = stringRes(Res.string.chat_composer_hint),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            leadingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                ) {
                    SelectFromGallery(
                        isUploading = false,
                        tint = MaterialTheme.colorScheme.placeholderText,
                        modifier = Modifier,
                        onImageChosen = newMessageModel::pickedMedia,
                    )
                    EmojiPickerButton(
                        newMessageModel.emojiSuggestions?.emojiPacks,
                        newMessageModel.message,
                        tint = MaterialTheme.colorScheme.placeholderText,
                    )
                }
            },
            trailingIcon = {
                ThinSendButton(
                    isActive = canPost,
                    modifier = EditFieldTrailingIconModifier,
                ) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            newMessageModel.sendPost()
                            onMessageSent()
                        } catch (e: Exception) {
                            currentCoroutineContext().ensureActive()
                            launch(Dispatchers.Main) {
                                shortNotice.show("Failed to send message: ${e.message}")
                            }
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

/**
 * The picked-image confirmation dialog for a Concord channel. Reuses the shared NIP-17 upload
 * pipeline: it always encrypts (no encryption toggle is shown, and [SuccessfulUploads.toConcordImeta]
 * fails closed if a cipher is somehow absent), uploads the ciphertext, then sends one Armada-shaped
 * image message ([Account.sendConcordChannelImageMessage]) carrying every attachment's `imeta`.
 */
@Composable
private fun ConcordFileUploadDialog(
    newMessageModel: ConcordNewMessageViewModel,
    state: ChatFileUploadState,
    accountViewModel: AccountViewModel,
    nav: INav,
    onUpload: suspend () -> Unit,
    onCancel: () -> Unit,
) {
    val longNotice = rememberLongNotice()
    val scope = rememberCoroutineScope()

    ChatFileUploadDialog(
        state = state,
        title = { Text(stringRes(Res.string.concord_send_image_title)) },
        upload = {
            scope.launch(Dispatchers.IO) {
                val community = newMessageModel.communityId
                val channel = newMessageModel.channelId
                if (community == null || channel == null) return@launch

                ChatFileUploader(accountViewModel.account).justUploadNIP17(
                    viewState = state,
                    onError = { title, message ->
                        scope.launch(Dispatchers.Main) { longNotice.show("$title: $message") }
                    },
                    onEncryptedUploadError = { title, message ->
                        scope.launch(Dispatchers.Main) { longNotice.show("$title: $message") }
                    },
                    uploader = accountViewModel.mediaUploader,
                    onceUploaded = { uploads ->
                        accountViewModel.reportSignerErrors {
                            val imetas = uploads.mapNotNull { it.toConcordImeta() }
                            if (imetas.isNotEmpty()) {
                                accountViewModel.account.concord.sendConcordChannelImageMessage(community, channel, "", imetas)
                            }
                            onUpload()
                        }
                    },
                )

                accountViewModel.account.settings.changeDefaultFileServer(state.selectedServer)
                accountViewModel.account.settings.changeStripLocationOnUpload(state.stripMetadata)
            }
        },
        onCancel = onCancel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}
