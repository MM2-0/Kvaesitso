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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.minichat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.minichat.MinichatFeedViewModel
import com.vitorpamplona.amethyst.commons.chats.publicChannels.concord.datasource.ConcordChannelHistorySubAssembler
import com.vitorpamplona.amethyst.commons.chats.ui.ThinSendButton
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzTypingState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.mediaServers.buzzWorkspaceServer
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.chat_minichat_title
import com.vitorpamplona.amethyst.commons.resources.chat_send_image_title
import com.vitorpamplona.amethyst.commons.resources.concord_open_channel
import com.vitorpamplona.amethyst.commons.resources.reply_here
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberLongNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatroomMessageCompose
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.LocalSuppressReplyToNoteId
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.upload.ChatFileUploader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelHistorySubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.ChatFileUploadDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.toConcordImeta
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.toPlainImetas
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.threading.buzzThreadRootForReplyTo
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The minichat ("chat within a chat") of a single chat message: the message pinned at
 * top, then its kind-1111 thread replies as chat bubbles, with a composer that always
 * posts a kind-1111 rooted at that message (flat — replying inside a minichat doesn't
 * spawn sub-threads). Opened from the "N replies" chip on any chat message.
 *
 * Self-sufficient regardless of where it's opened from: it mounts its own datasource so
 * replies keep flowing (the Concord plane subscription when the root is a Concord message;
 * a relay reply subscription for public chats), and drives the list from a reactive
 * [MinichatFeedViewModel] so new replies appear (and auto-scroll into view) live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinichatScreen(
    rootId: String,
    concordCommunityId: String? = null,
    concordChannelId: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val rootNote = remember(rootId) { LocalCache.getOrCreateNote(rootId) }

    // Resolve the Concord channel from the (loaded) root note's gatherer, else from the ids threaded
    // in by the reply that opened this minichat. The latter is what lets a reply-to-an-unloaded-parent
    // still pick the plane + relays: the reply arrived over the channel plane, so its channel id is known.
    val concordChannel = remember(rootNote) { rootNote.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } }
    val communityId = concordCommunityId ?: concordChannel?.channelId?.communityId
    val channelId = concordChannelId ?: concordChannel?.channelId?.channelId
    val isConcord = communityId != null && channelId != null

    // Datasource: keep the thread replies flowing no matter the entry point. Concord replies arrive
    // over the channel plane, so mount the plane subscription plus this channel's backward-history
    // pager and page it until the parent message loads (see [ConcordMinichatBackfillUntilRoot]);
    // public-chat (NIP-28/NIP-29) replies come over a relay REQ for this message's kind-1111 children.
    if (isConcord) {
        ConcordChannelSubscription(accountViewModel.dataSources().concordChannels, accountViewModel)
        ConcordChannelHistorySubscription(communityId, channelId, accountViewModel.dataSources().concordChannelHistory, accountViewModel)
        ConcordMinichatBackfillUntilRoot(rootNote, accountViewModel.dataSources().concordChannelHistory.history)
    }
    EventFinderFilterAssemblerSubscription(rootNote, accountViewModel)

    val feedViewModel: MinichatFeedViewModel =
        rememberViewModel(
            key = rootId + "MinichatFeedViewModel",
            factory = MinichatFeedViewModel.Factory(rootNote, accountViewModel.account),
        )
    val replies by feedViewModel.replies.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    // Auto-scroll to the newest reply as they arrive (root is item 0, replies follow).
    LaunchedEffect(replies.size) {
        if (replies.isNotEmpty()) listState.animateScrollToItem(replies.size)
    }

    val composer = remember { TextFieldState() }
    val scope = rememberCoroutineScope()
    val shortNotice = rememberShortNotice()
    val longNotice = rememberLongNotice()
    val canPost by remember { derivedStateOf { composer.text.isNotBlank() } }
    // A thread in a Buzz workspace channel: its media must live on the workspace's own server
    // (the relay refuses any other image host) and its typing signal is scoped to the thread.
    // Reactive on the dialect: a relay is marked Buzz only once its first verified Buzz event lands,
    // which a cold start can reach after this screen opens.
    val buzzRelays by BuzzRelayDialect.flow.collectAsStateWithLifecycle()
    val relayGroup = remember(rootNote) { rootNote.inGatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel } }
    val buzzGroup = relayGroup?.takeIf { it.groupId.relayUrl in buzzRelays }
    val uploadState =
        remember(buzzGroup) {
            ChatFileUploadState(
                accountViewModel.account.settings.defaultFileServer,
                accountViewModel.account.settings.stripLocationOnUpload,
            ).apply { lockServer(buzzGroup?.let { buzzWorkspaceServer(it.groupId.relayUrl.url) }) }
        }
    // Client-side throttle for the Buzz kind-20002 typing heartbeat.
    val lastTypingSecs = remember { longArrayOf(0L) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.chat_minichat_title), maxLines = 1) },
                navigationIcon = {
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(Res.string.back))
                        }
                    }
                },
                actions = {
                    // For a Concord thread, always offer a jump to the full channel — the "chat room
                    // itself", where the whole timeline loads and (if you aren't a member yet) you can
                    // join. This is the way out when the pinned root is still backfilling or unavailable.
                    if (isConcord) {
                        IconButton(onClick = { nav.nav(Route.Concord(communityId, channelId)) }) {
                            SymbolIcon(symbol = MaterialSymbols.Forum, contentDescription = stringRes(Res.string.concord_open_channel))
                        }
                    }
                },
            )
        },
    ) { padding ->
        // The reply composer must ride above the soft keyboard — the bare Material3 Scaffold's
        // content insets cover the system bars but not the IME. consumeWindowInsets(padding) before
        // imePadding() unions the two so the nav-bar inset already in `padding` is dropped while the
        // keyboard is up instead of stacking on top of it.
        Column(
            Modifier
                .fillMaxHeight()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePaddingSafe(),
        ) {
            // Every reply here is rooted at [rootNote], which is already pinned at the top — so
            // suppress the redundant reply-to-root preview each reply would otherwise render.
            CompositionLocalProvider(LocalSuppressReplyToNoteId provides rootId) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f, true)) {
                    item("root") {
                        ChatroomMessageCompose(
                            baseNote = rootNote,
                            routeForLastRead = null,
                            accountViewModel = accountViewModel,
                            nav = nav,
                            onWantsToReply = {},
                            onWantsToEditDraft = {},
                        )
                        HorizontalDivider()
                    }
                    items(replies, key = { it.idHex }) { reply ->
                        ChatroomMessageCompose(
                            baseNote = reply,
                            routeForLastRead = null,
                            accountViewModel = accountViewModel,
                            nav = nav,
                            onWantsToReply = {},
                            onWantsToEditDraft = {},
                        )
                    }
                }
            }

            // Picture attachments: the picked media opens this dialog, which uploads via the shared
            // pipeline and sends the result as a thread reply. Concord minichats always encrypt (the
            // channel is end-to-end); public-chat (NIP-28/NIP-29) minichats upload in plaintext and carry
            // the URL + `imeta` on the kind-1111 comment. [Account.sendMinichatReply] routes by backend.
            uploadState.multiOrchestrator?.let {
                ChatFileUploadDialog(
                    state = uploadState,
                    title = { Text(stringRes(Res.string.chat_send_image_title)) },
                    upload = {
                        scope.launch(Dispatchers.IO) {
                            ChatFileUploader(accountViewModel.account).justUploadNIP17(
                                viewState = uploadState,
                                onError = { title, message ->
                                    launch(Dispatchers.Main) { longNotice.show("$title: $message") }
                                },
                                onEncryptedUploadError = { title, message ->
                                    launch(Dispatchers.Main) { longNotice.show("$title: $message") }
                                },
                                uploader = accountViewModel.mediaUploader,
                                onceUploaded = { uploads ->
                                    accountViewModel.reportSignerErrors {
                                        val caption = uploadState.caption
                                        val imetas = if (isConcord) uploads.mapNotNull { it.toConcordImeta() } else uploads.toPlainImetas()
                                        if (imetas.isNotEmpty()) {
                                            accountViewModel.account.sendMinichatReply(rootNote, caption, imetas)
                                        }
                                    }
                                },
                            )
                            if (uploadState.lockedServer == null) {
                                accountViewModel.account.settings.changeDefaultFileServer(uploadState.selectedServer)
                            }
                            accountViewModel.account.settings.changeStripLocationOnUpload(uploadState.stripMetadata)
                        }
                    },
                    onCancel = uploadState::reset,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            Column(modifier = EditFieldModifier) {
                ThinPaddingTextField(
                    state = composer,
                    spellCheck = true,
                    onTextChanged = {
                        val group = buzzGroup
                        val now = TimeUtils.now()
                        if (group != null && composer.text.isNotEmpty() && now - lastTypingSecs[0] >= BuzzTypingState.TYPING_HEARTBEAT_SECS) {
                            lastTypingSecs[0] = now
                            val rootEvent = rootNote.event
                            accountViewModel.sendBuzzTyping(
                                group,
                                threadRootId = rootEvent?.tags?.buzzThreadRootForReplyTo(rootEvent.id) ?: rootNote.idHex,
                                replyToId = rootNote.idHex,
                            )
                        }
                    },
                    onContentReceived = { uri, mimeType ->
                        uploadState.load(persistentListOf(SelectedMedia(uri, mimeType)))
                        // Same encryption choice the gallery button makes below.
                        uploadState.encryptFiles = isConcord
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = EditFieldBorder,
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.reply_here),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    leadingIcon = {
                        SelectFromGallery(
                            isUploading = uploadState.isUploadingImage,
                            tint = MaterialTheme.colorScheme.placeholderText,
                            modifier = Modifier.height(32.dp).padding(start = 2.dp),
                            onImageChosen = { media ->
                                uploadState.load(media)
                                // Encrypt only where the backend is end-to-end (Concord); public chats
                                // send plaintext blobs, matching how their main composer uploads.
                                uploadState.encryptFiles = isConcord
                            },
                        )
                    },
                    trailingIcon = {
                        ThinSendButton(
                            isActive = canPost,
                            modifier = EditFieldTrailingIconModifier,
                        ) {
                            val text = composer.text.toString().trim()
                            if (text.isNotEmpty()) {
                                composer.clearText()
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        accountViewModel.account.sendMinichatReply(rootNote, text)
                                    } catch (e: Exception) {
                                        currentCoroutineContext().ensureActive()
                                        launch(Dispatchers.Main) {
                                            shortNotice.show("Failed to send message: ${e.message}")
                                        }
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
    }
}

/**
 * Pages the Concord channel's backward history until the minichat's parent message loads (or the
 * relays are exhausted). Reaching a minichat from a reply notification can land on a parent that
 * isn't in the live tail; the reply itself pinned the channel context, so we can walk the plane
 * history back to fetch the parent — after which its whole kind-1111 thread projects normally. The
 * `!loading` gate serializes pages; once the root's event is present, or nothing is left, it latches off.
 */
@Composable
private fun ConcordMinichatBackfillUntilRoot(
    rootNote: Note,
    history: ConcordChannelHistorySubAssembler,
) {
    LaunchedEffect(rootNote, history) {
        combine(history.loadingMore, history.status) { loading, status ->
            rootNote.event == null && !loading && !status.exhausted
        }.distinctUntilChanged()
            .filter { it }
            .collect { history.advanceAll() }
    }
}
