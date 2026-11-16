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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.audio.RecordingResult
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.chats.ui.AutoScrollToNewest
import com.vitorpamplona.amethyst.commons.cordn.CordnMediaService
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.ChatLayoutType
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.cordn_action_editing
import com.vitorpamplona.amethyst.commons.resources.cordn_action_unpin
import com.vitorpamplona.amethyst.commons.resources.cordn_chat_empty_description
import com.vitorpamplona.amethyst.commons.resources.cordn_chat_empty_title
import com.vitorpamplona.amethyst.commons.resources.cordn_group_info
import com.vitorpamplona.amethyst.commons.resources.cordn_group_unavailable
import com.vitorpamplona.amethyst.commons.resources.cordn_group_untitled
import com.vitorpamplona.amethyst.commons.resources.cordn_media_download_failed
import com.vitorpamplona.amethyst.commons.resources.cordn_media_no_server
import com.vitorpamplona.amethyst.commons.resources.cordn_media_unreadable
import com.vitorpamplona.amethyst.commons.resources.cordn_media_upload_failed
import com.vitorpamplona.amethyst.commons.resources.cordn_member_count
import com.vitorpamplona.amethyst.commons.resources.cordn_message_deleted
import com.vitorpamplona.amethyst.commons.resources.cordn_pinned_by
import com.vitorpamplona.amethyst.commons.resources.cordn_pinned_count
import com.vitorpamplona.amethyst.commons.resources.cordn_pinned_next
import com.vitorpamplona.amethyst.commons.resources.cordn_pinned_previous
import com.vitorpamplona.amethyst.commons.resources.cordn_pinned_show_all
import com.vitorpamplona.amethyst.commons.resources.cordn_pinned_title
import com.vitorpamplona.amethyst.commons.resources.cordn_send_failed
import com.vitorpamplona.amethyst.commons.resources.cordn_send_no_session
import com.vitorpamplona.amethyst.commons.resources.cordn_voice_record
import com.vitorpamplona.amethyst.commons.resources.cordn_voice_stop
import com.vitorpamplona.amethyst.commons.richtext.EncryptedMediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.EncryptedMediaUrlVideo
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.lastPathSegmentOrNull
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NonClickableUserPictures
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.platform.RecordAudioBox
import com.vitorpamplona.amethyst.commons.ui.note.platform.RenderAudioPlayer
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.CordnComposer
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.CordnMessageRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.CordnQuotedMessage
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.DaySeparator
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.UnreadDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.cordnGroupPositionFor
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.rememberToday
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.cordnGroup.sameDayAs
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatFeedDayIndicator
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.LocalChatDayShownByFeed
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.ChatFileUploadDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.cordn.appEncryptedMedia.CordnBlobUpload
import com.vitorpamplona.cordn.appEncryptedMedia.CordnMediaAttachment
import com.vitorpamplona.cordn.appEncryptedMedia.CordnMediaCipher
import com.vitorpamplona.cordn.appEncryptedMedia.CordnMediaEncryption
import com.vitorpamplona.cordn.appEncryptedMedia.CordnMediaTag
import com.vitorpamplona.cordn.client.CordnGroupManager
import com.vitorpamplona.cordn.spec02Envelopes.CordnAnnotationIndex
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageReferences
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One cordn room.
 *
 * Every kind `spec/02.md` defines can now be both sent and read here: text,
 * thread replies, reactions, edits, deletions and pins, plus encrypted
 * attachments and voice notes. The rule that got us here is worth keeping —
 * a composer must never be able to send a kind this room cannot render, or
 * the sender's own messages appear as blanks to them.
 *
 * It renders the room's own envelopes rather than `Note`s, so nothing on this
 * screen goes through `LocalCache`. That is the point — a cordn message is not
 * a Nostr event that happens to be encrypted, it is an MLS payload that never
 * touched a relay, and giving it a Note would make it searchable and
 * notifiable alongside things that were actually published.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CordnGroupChatScreen(
    coordinatorPubKey: HexKey,
    gid: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime
    val room = remember(coordinatorPubKey, gid) { runtime?.groups?.get(coordinatorPubKey, gid) }

    if (room == null) {
        // No room means no session for this coordinator, which is a real state
        // (it was forgotten, or never opened) and not an error to throw at the
        // user as a blank screen.
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(stringRes(Res.string.cordn_group_unavailable), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    CordnGroupChat(room, accountViewModel, nav)
}

@Composable
private fun CordnGroupChat(
    room: CordnGroupChatroom,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val messages by room.messages.collectAsStateWithLifecycle()
    val annotations by room.annotations.collectAsStateWithLifecycle()
    val name by room.name.collectAsStateWithLifecycle()
    val members by room.members.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val me = accountViewModel.account.signer.pubKey

    val draft by room.draft.collectAsStateWithLifecycle()
    var replyingTo by remember { mutableStateOf<CordnDeliveredMessage?>(null) }
    var editing by remember { mutableStateOf<CordnDeliveredMessage?>(null) }
    var attaching by remember { mutableStateOf(false) }
    // The same upload state every other chat's dialog is built on — caption, media
    // quality, metadata stripping and the server to put it on.
    val uploadState =
        remember(room.gid) {
            ChatFileUploadState(
                accountViewModel.account.settings.defaultFileServer,
                accountViewModel.account.settings.stripLocationOnUpload,
            )
        }
    var pendingVoice by remember { mutableStateOf<RecordingResult?>(null) }

    DisposableEffect(room.gid) {
        onDispose {
            // The recorder writes plaintext audio to cacheDir and sendVoiceNote deletes
            // it after sending. A note recorded and then abandoned — the screen closed,
            // the room switched — never reached that, so it stayed on disk: an
            // unencrypted copy of a message that was never even sent.
            pendingVoice?.file?.toFile()?.delete()
        }
    }
    var attachError by remember { mutableStateOf<String?>(null) }

    // Why sending says anything at all when it fails: `manager()` is null-safe
    // all the way down, so a room whose coordinator has no open session
    // swallowed every send, reaction, edit, delete and pin without a word —
    // and the composer had already cleared the draft, so the text went with it.
    // From the outside that is indistinguishable from a message that was sent
    // and simply never arrived.
    var sendError by remember { mutableStateOf<String?>(null) }
    val uploader = accountViewModel.mediaUploader
    val uploadFailed = stringRes(Res.string.cordn_media_upload_failed)
    val sendFailed = stringRes(Res.string.cordn_send_failed)
    val noSession = stringRes(Res.string.cordn_send_no_session)

    fun manager() =
        accountViewModel.account.cordnRuntime
            ?.sessionOrNull(room.coordinatorPubKey)
            ?.manager

    // One place for every outbound action, so none of them can go quiet again.
    // Returns false when it failed, which is what lets the composer put the
    // draft back rather than eat it.
    suspend fun trySend(block: suspend (CordnGroupManager) -> CordnDeliveredMessage?): Boolean {
        val manager = manager()
        if (manager == null) {
            sendError = noSession
            return false
        }
        return try {
            // Shown the moment the coordinator takes it, rather than when the
            // echo comes back — which, for your own traffic, it never does as
            // a message: the sync loop recognises it by cursor and reports it
            // as Delivery.Echo, whose branch adds nothing to the room. So a
            // sent message used to leave no trace in the room that sent it.
            //
            // add() is keyed on the envelope id and idempotent, so a later
            // re-sync that does hand the message back cannot double it.
            block(manager)?.let { room.add(it) }
            sendError = null
            true
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.w("CordnGroupChat", "send failed in ${room.gid}: ${e.message}", e)
            sendError = e.message ?: sendFailed
            false
        }
    }

    val runtime = accountViewModel.account.cordnRuntime

    // Opening a room is what marks it read, and the read position and draft
    // are written when leaving it. Saving on every keystroke would rewrite an
    // encrypted file per character; saving on dispose loses nothing a process
    // death would not have lost anyway.
    DisposableEffect(room) {
        onDispose {
            room.markRead()
            runtime?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    it.saveRoomState(room.coordinatorPubKey, room.gid)
                }
            }
        }
    }

    // Where the divider goes, taken once per visit. markRead() below moves the live
    // cursor to the newest message, so reading it per frame would erase the line at
    // exactly the moment it starts being useful. Restored state first, because the
    // cursor this reads is the one that was persisted.
    var unreadFrom by remember(room.gid) { mutableStateOf<Long?>(null) }

    LaunchedEffect(room) {
        runtime?.restoreRoomState(room.coordinatorPubKey, room.gid)
        unreadFrom = room.lastReadCursor.value.takeIf { room.unreadCount.value > 0 }
        room.markRead()
    }

    // Tapping a reply's quote jumps to the message it answers and flashes it; the
    // bubble clears this itself once the flash is done.
    var highlighted by remember(room.gid) { mutableStateOf<HexKey?>(null) }

    // The same scaffold every other chat screen uses. A bare Scaffold gave this room
    // neither of the two things it provides: the bars' scroll behaviour, and the IME
    // inset — without which the composer sat *under* the soft keyboard.
    DisappearingScaffold(
        isInvertedLayout = true,
        topBar = {
            CordnChatTopBar(
                title = name?.takeIf { it.isNotBlank() } ?: stringRes(Res.string.cordn_group_untitled, room.gid.take(8)),
                members = members,
                accountViewModel = accountViewModel,
                // Hidden in a list/detail screen's detail pane, where there is nothing to go back to.
                onBack = if (nav.canPop()) ({ nav.popBack() }) else null,
                onInfo = { nav.nav(Route.CordnGroupInfo(room.coordinatorPubKey, room.gid)) },
            )
        },
        accountViewModel = accountViewModel,
        allowBarHide = false,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // One clock for every divider in the room, so they cannot disagree.
            val today = rememberToday()

            // Reversed once, into a val: `asReversed()` is a view, and indexing it
            // per item to find the neighbour is how a list like this quietly becomes
            // quadratic. Hoisted out of the LazyColumn because the jump below needs to
            // find a message's row by id.
            val rows = remember(messages) { messages.asReversed() }

            // The oldest message this visit had not seen. Own traffic is excluded for
            // the same reason unreadCount excludes it: a message of yours coming back
            // as an echo is not news, and a divider above it would say it was.
            val firstUnreadId =
                remember(rows, unreadFrom, me) {
                    unreadFrom?.let { readUpTo ->
                        rows.lastOrNull { it.cursor > readUpTo && it.envelope.pubKey != me }?.envelope?.id
                    }
                }

            val jumpTo: (HexKey) -> Unit = { id ->
                val index = rows.indexOfFirst { it.envelope.id == id }
                // Not found means the quoted message is older than what is loaded.
                // Flashing nothing is better than scrolling somewhere arbitrary.
                if (index >= 0) {
                    highlighted = id
                    scope.launch { listState.animateScrollToItem(index) }
                }
            }

            // Pinned messages sit above the conversation rather than inside it.
            // A pin is a claim about a message's importance, not a message, and
            // leaving it only in place means the thing someone pinned scrolls
            // away exactly like everything else.
            PinnedRibbon(
                annotations = annotations,
                scope = scope,
                accountViewModel = accountViewModel,
                nav = nav,
                onJumpTo = jumpTo,
            ) { message ->
                trySend {
                    it.post(
                        gid = room.gid,
                        pinTo = message.target(),
                        pinOp = CordnMessageReferences.PinOp.REMOVE,
                    )
                }
            }

            // The same rule every other chat follows: your own message always
            // pulls the view onto it, someone else's only while you are already
            // at the bottom, and history you scrolled up to read is left alone.
            val newest = rows.firstOrNull()?.envelope
            AutoScrollToNewest(listState, newest?.id, mine = newest?.pubKey == me)

            // A room with nothing in it said nothing at all, where every other chat
            // crossfades a real empty state. There is no error or loading branch to
            // match: the session opens behind `restoreRoomState` and a room that cannot
            // reach its coordinator says so through the send banner below, on the
            // action that actually needed it.
            if (rows.isEmpty()) {
                EmptyState(
                    title = stringRes(Res.string.cordn_chat_empty_title),
                    description = stringRes(Res.string.cordn_chat_empty_description),
                    modifier = Modifier.weight(1f),
                )
            }

            // The day of what is at the top, pinned the way the shared chat feed pins it:
            // a pill over bubbles, a docked bar over compact lines, nothing in the open
            // layout, whose stamps name the day themselves. Under a pinned day the stamps
            // are the time of day (LocalChatDayShownByFeed, read by ChatTimestamp).
            val chatLayout = LocalDisplaySettings.current.chatLayout
            val pinsDay = chatLayout != ChatLayoutType.OPEN

            CompositionLocalProvider(LocalChatDayShownByFeed provides pinsDay) {
                Box(
                    // No weight while the empty state holds the space, or the two would
                    // split the screen and the placeholder would sit in half of it. The
                    // side padding is the Box's, so the docked bar lines up with the
                    // dividers in the list.
                    modifier =
                        if (rows.isEmpty()) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier.weight(1f).fillMaxWidth()
                        }.padding(horizontal = 12.dp),
                ) {
                    LazyColumn(
                        state = listState,
                        // Anchored at the bottom like every other chat: a room opens on
                        // its newest message. `messages` is oldest-first, so the rows
                        // are reversed to match.
                        reverseLayout = true,
                        contentPadding = FeedPadding,
                        modifier = if (rows.isEmpty()) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(rows, key = { _, it -> it.envelope.id }) { index, message ->
                            // `rows` runs newest-first and the list is reverse-laid-out,
                            // so the next index is the older message and renders above.
                            val older = rows.getOrNull(index + 1)
                            val newer = rows.getOrNull(index - 1)

                            // Send/arrival motion, as in the shared feed: a new row fades
                            // in and the ones above slide to make room, so a sent message
                            // enters instead of appearing.
                            val itemModifier =
                                if (accountViewModel.settings.isPerformanceMode()) {
                                    Modifier
                                } else {
                                    Modifier.animateItem()
                                }

                            // One Column, so the item is a single placeable. `reverseLayout`
                            // mirrors each of an item's placeables about the main axis, which
                            // inverts the order of siblings emitted side by side — but it does
                            // not reach inside a layout, so this Column still reads top to
                            // bottom. Both headers are therefore composed BEFORE the bubble
                            // they introduce: the date above the whole day, then the unread
                            // line immediately against the message.
                            Column(modifier = itemModifier) {
                                // Starts a new day when it differs from the older row, which
                                // under reverseLayout is the one rendered above.
                                if (!message.sameDayAs(older)) {
                                    DaySeparator(message.envelope.createdAt, today)
                                }

                                if (message.envelope.id == firstUnreadId) {
                                    UnreadDivider()
                                }

                                CordnMessageRow(
                                    message = message,
                                    room = room,
                                    annotations = annotations,
                                    me = me,
                                    // Grouped when the same person keeps talking inside the
                                    // shared chat window: one avatar and name per burst
                                    // instead of per line, and the bubbles of a burst square
                                    // off against each other — which is most of what makes a
                                    // wall of messages readable.
                                    groupPosition =
                                        remember(newer?.envelope?.id, message.envelope.id, older?.envelope?.id) {
                                            cordnGroupPositionFor(newer, message, older)
                                        },
                                    // The edit if there is one, and nothing at all if the
                                    // message was withdrawn: rendering the original text of
                                    // a deleted message would defeat the deletion.
                                    text = if (annotations.isDeleted(message.envelope.id)) null else annotations.contentOf(message.envelope.id),
                                    isEdited = annotations.isEdited(message.envelope.id),
                                    shouldHighlight = highlighted == message.envelope.id,
                                    accountViewModel = accountViewModel,
                                    nav = nav,
                                    onHighlightFinished = { highlighted = null },
                                    onScrollToMessage = jumpTo,
                                    onReply = {
                                        replyingTo = message
                                        editing = null
                                    },
                                    onEdit = {
                                        editing = message
                                        replyingTo = null
                                        room.draft.value = annotations.contentOf(message.envelope.id).orEmpty()
                                    },
                                    onDelete = {
                                        // Through trySend like the rest: a deletion is an
                                        // annotation, and an annotation of your own comes back
                                        // as an Echo too, so deleting your own message used to
                                        // look like nothing had happened until someone else's
                                        // traffic refreshed the fold.
                                        scope.launch { trySend { it.post(room.gid, deleteTo = message.target()) } }
                                    },
                                    onTogglePin = {
                                        val pinned = annotations.isPinned(message.envelope.id)
                                        scope.launch {
                                            trySend {
                                                it.post(
                                                    gid = room.gid,
                                                    pinTo = message.target(),
                                                    pinOp = if (pinned) CordnMessageReferences.PinOp.REMOVE else CordnMessageReferences.PinOp.ADD,
                                                )
                                            }
                                        }
                                    },
                                    onReact = { emoji ->
                                        scope.launch { trySend { it.post(room.gid, emoji, reactionTo = message.target()) } }
                                    },
                                )
                            }
                        }
                    }

                    // Every row that opens a day carries its DaySeparator at its top, the oldest
                    // included (sameDayAs is false without an older row), which is what the
                    // indicator reads the list's geometry against.
                    if (pinsDay && rows.isNotEmpty()) {
                        ChatFeedDayIndicator(listState, rows.size, docked = chatLayout == ChatLayoutType.COMPACT) { index ->
                            rows.getOrNull(index)?.envelope?.createdAt
                        }
                    }
                }
            }

            (attachError ?: sendError)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            // The app's upload dialog, as Marmot, Concord, the DMs, minichat and Nests
            // all use it. cordn keeps its own uploader behind it — encrypted blobs to
            // Blossom, not a nostr media post — which is exactly how Marmot uses it too.
            if (uploadState.multiOrchestrator != null) {
                ChatFileUploadDialog(
                    state = uploadState,
                    title = { Text(name?.takeIf { it.isNotBlank() } ?: stringRes(Res.string.cordn_group_untitled, room.gid.take(8))) },
                    upload = {
                        scope.launch {
                            attaching = true
                            attachError = null
                            try {
                                sendAttachment(uploader, accountViewModel, room, uploadState)
                                // Only on success: a failed upload leaves the dialog up
                                // with what you picked still in it, so retrying is one
                                // tap rather than the picker again.
                                uploadState.reset()
                            } catch (e: Exception) {
                                currentCoroutineContext().ensureActive()
                                // A failed attachment left no trace anywhere; the
                                // banner tells the person, this tells whoever has
                                // to work out why.
                                Log.w("CordnGroupChat", "attachment failed in ${room.gid}: ${e.message}", e)
                                attachError = e.message ?: uploadFailed
                            } finally {
                                attaching = false
                            }
                        }
                    },
                    onCancel = uploadState::reset,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    // cordn tells its blob host a fixed set of constants on purpose and
                    // its imeta tag has no field for a warning, so the switch would be
                    // a control with nowhere to put the answer.
                    showContentWarning = false,
                )
            }

            val replyPreview = replyingTo
            if (replyPreview != null) {
                // The same quote block the bubbles use, so what you are answering looks
                // the same while you write it as it does once it is sent.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        CordnQuotedMessage(
                            parent = replyPreview,
                            annotations = annotations,
                            me = me,
                            accountViewModel = accountViewModel,
                            nav = nav,
                            onClick = { jumpTo(replyPreview.envelope.id) },
                        )
                    }
                    IconButton(onClick = { replyingTo = null }) {
                        Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.cancel))
                    }
                }
            }
            if (editing != null) {
                ComposerBanner(
                    label = stringRes(Res.string.cordn_action_editing),
                    onCancel = {
                        editing = null
                        room.draft.value = ""
                    },
                )
            }

            CordnComposer(
                room = room,
                accountViewModel = accountViewModel,
                // Picking a file no longer sends it. A cordn attachment is encrypted,
                // uploaded and announced to the room in one irreversible action, so it
                // gets the same confirm-first treatment as anything else that cannot be
                // taken back.
                onAttach = { uri, mimeType ->
                    uploadState.load(persistentListOf(SelectedMedia(uri, mimeType ?: uploader.mimeType(uri))))
                },
                // Recording no longer sends. It goes to the preview above the field,
                // where it can be played back, re-recorded or dropped first.
                pendingVoice = pendingVoice,
                onVoiceNote = { recording ->
                    // Re-recording replaces what was there; the old file is a temp
                    // file this screen owns, so it goes now rather than being leaked.
                    pendingVoice?.file?.toFile()?.delete()
                    pendingVoice = recording
                },
                onRemoveVoice = {
                    pendingVoice?.file?.toFile()?.delete()
                    pendingVoice = null
                },
                attaching = attaching,
                // The field hands its own text over rather than the screen reading
                // `room.draft`: the two are kept in step by a snapshot collector, which
                // settles a frame later, and a send must use what is on screen now.
                onSend = { typed ->
                    val text = typed.trim()

                    val voice = pendingVoice
                    if (voice != null) {
                        pendingVoice = null
                        room.draft.value = ""
                        scope.launch {
                            attaching = true
                            attachError = null
                            try {
                                sendVoiceNote(accountViewModel, room, voice, text)
                            } catch (e: Exception) {
                                currentCoroutineContext().ensureActive()
                                Log.w("CordnGroupChat", "voice note failed in ${room.gid}: ${e.message}", e)
                                attachError = e.message ?: uploadFailed
                            } finally {
                                attaching = false
                            }
                        }
                        return@CordnComposer
                    }

                    if (text.isEmpty()) return@CordnComposer

                    val reply = replyingTo
                    val edit = editing
                    room.draft.value = ""
                    replyingTo = null
                    editing = null

                    scope.launch {
                        val sent =
                            trySend {
                                it.post(
                                    gid = room.gid,
                                    content = text,
                                    replyTo = reply?.target(),
                                    editTo = edit?.target(),
                                )
                            }
                        if (!sent) {
                            // Hand the message back rather than lose it, and put
                            // the reply or edit it belonged to back with it, so
                            // trying again means pressing send and nothing else.
                            room.draft.value = text
                            replyingTo = reply
                            editing = edit
                        }
                    }
                },
            )
        }
    }
}

/** The target fields `CordnMessageReferences` needs, straight off a delivery. */
private fun CordnDeliveredMessage.target() = CordnMessageReferences.Target.of(envelope)

/**
 * The pinned messages, one line tall however many there are.
 *
 * Stacking them was the obvious shape and the wrong one: the ribbon sits above a
 * `weight(1f)` conversation, so an unweighted Column of one row per pin is
 * measured at its full height first and takes that space off the conversation --
 * eight pins and there is little chat left, with no way to scroll or collapse
 * the strip. cordn-web's shape fixes the height by construction instead: one pin
 * shown, a position counter, and arrows to step through the rest.
 *
 * Stepping wraps, and the index is clamped on read rather than corrected in an
 * effect -- the list shrinks under it whenever anybody unpins, and a clamp that
 * happens at read time cannot lag behind the data the way a correction does.
 */
@Composable
private fun PinnedRibbon(
    annotations: CordnAnnotationIndex,
    scope: CoroutineScope,
    accountViewModel: AccountViewModel,
    nav: INav,
    onJumpTo: (HexKey) -> Unit,
    // Hoisted rather than handed a manager: unpinning has to go through the
    // caller's trySend so it reports a failure and lands in the room, and a
    // ribbon that posted for itself could do neither.
    onUnpin: suspend (CordnDeliveredMessage) -> Unit,
) {
    val pinned = annotations.pinnedIds().mapNotNull { annotations.byId[it] }
    if (pinned.isEmpty()) return

    var index by remember { mutableIntStateOf(0) }
    var showingAll by remember { mutableStateOf(false) }

    val at = index.coerceIn(0, pinned.lastIndex)
    val current = pinned[at]

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.PushPin,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PinnedLine(
                message = current,
                annotations = annotations,
                accountViewModel = accountViewModel,
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable { onJumpTo(current.envelope.id) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )

            // Only when there is somewhere to step to.
            if (pinned.size > 1) {
                IconButton(onClick = { index = (at - 1 + pinned.size) % pinned.size }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        symbol = MaterialSymbols.AutoMirrored.KeyboardArrowLeft,
                        contentDescription = stringRes(Res.string.cordn_pinned_previous),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = "${at + 1}/${pinned.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { index = (at + 1) % pinned.size }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        symbol = MaterialSymbols.AutoMirrored.KeyboardArrowRight,
                        contentDescription = stringRes(Res.string.cordn_pinned_next),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            IconButton(onClick = { showingAll = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    symbol = MaterialSymbols.AutoMirrored.List,
                    contentDescription = stringRes(Res.string.cordn_pinned_show_all),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        HorizontalDivider()
    }

    if (showingAll) {
        AllPinnedDialog(
            pinned = pinned,
            annotations = annotations,
            accountViewModel = accountViewModel,
            nav = nav,
            onDismiss = { showingAll = false },
            onJumpTo = {
                showingAll = false
                onJumpTo(it)
            },
            onUnpin = { scope.launch { onUnpin(it) } },
        )
    }
}

/**
 * Who said it and what it said, on one line.
 *
 * The author is half of what makes a pin worth reading -- a line of text with no
 * name on it says nothing about why it was kept.
 */
@Composable
private fun PinnedLine(
    message: CordnDeliveredMessage,
    annotations: CordnAnnotationIndex,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = observeUserNameByHex(message.envelope.pubKey, accountViewModel),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
        Text(
            // A deleted message that is still pinned shows as deleted, not as
            // its old text: a pin must not outlive the withdrawal of what it
            // points at.
            text =
                if (annotations.isDeleted(message.envelope.id)) {
                    stringRes(Res.string.cordn_message_deleted)
                } else {
                    annotations.contentOf(message.envelope.id).orEmpty()
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * Every pinned message, for when the strip's one line is not enough.
 *
 * The only place `pinnedBy` is shown. The fold has always carried it and
 * nothing ever displayed it, yet in a group where any member may pin (spec/01.md
 * section 5.1) who did the pinning is often the point.
 */
@Composable
private fun AllPinnedDialog(
    pinned: List<CordnDeliveredMessage>,
    annotations: CordnAnnotationIndex,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
    onJumpTo: (HexKey) -> Unit,
    onUnpin: (CordnDeliveredMessage) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MaterialSymbols.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringRes(Res.string.cordn_pinned_title),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
        text = {
            Column {
                Text(
                    text = pluralStringRes(Res.plurals.cordn_pinned_count, pinned.size, pinned.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Bounded and scrollable: this list is as long as the group made
                // it, and a dialog that grows past the screen cannot be dismissed.
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(pinned, key = { it.envelope.id }) { message ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onJumpTo(message.envelope.id) }
                                .padding(vertical = 8.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                UserPicture(
                                    userHex = message.envelope.pubKey,
                                    size = 24.dp,
                                    accountViewModel = accountViewModel,
                                    nav = nav,
                                )
                                Text(
                                    text = observeUserNameByHex(message.envelope.pubKey, accountViewModel),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                )
                                TextButton(onClick = { onUnpin(message) }) {
                                    Text(stringRes(Res.string.cordn_action_unpin), style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            annotations.pins[message.envelope.id]?.pinnedBy?.let { pinnedBy ->
                                Text(
                                    text =
                                        stringRes(
                                            Res.string.cordn_pinned_by,
                                            observeUserNameByHex(pinnedBy, accountViewModel),
                                        ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Text(
                                text =
                                    if (annotations.isDeleted(message.envelope.id)) {
                                        stringRes(Res.string.cordn_message_deleted)
                                    } else {
                                        annotations.contentOf(message.envelope.id).orEmpty()
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) }
        },
    )
}

@Composable
private fun ComposerBanner(
    label: String,
    onCancel: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onCancel) { Text(stringRes(Res.string.cancel), style = MaterialTheme.typography.labelSmall) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CordnChatTopBar(
    title: String,
    members: List<HexKey>,
    accountViewModel: AccountViewModel,
    onBack: (() -> Unit)?,
    onInfo: () -> Unit,
) {
    TopAppBar(
        title = {
            // The whole title is the way in to group info, as it is in every other
            // group chat — the faces say who is in the room before you open it.
            Row(
                modifier = Modifier.clickable(onClick = onInfo),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (members.isNotEmpty()) {
                    NonClickableUserPictures(
                        userHexList = members,
                        size = 36.dp,
                        accountViewModel = accountViewModel,
                    )
                }
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (members.isNotEmpty()) {
                        Text(
                            text = pluralStringRes(Res.plurals.cordn_member_count, members.size, members.size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                        contentDescription = stringRes(Res.string.back),
                    )
                }
            }
        },
        actions = {
            // GroupAdd rather than Info, as in Marmot: the same screen, and the thing
            // people come to it for is adding someone.
            IconButton(onClick = onInfo) {
                Icon(MaterialSymbols.GroupAdd, contentDescription = stringRes(Res.string.cordn_group_info))
            }
        },
    )
}

/** A reason an attachment did not go, in words meant for the person who tried. */
private class CordnAttachmentException(
    message: String,
) : Exception(message)

/**
 * Encrypts what the upload dialog is holding and sends it as an attachment.
 *
 * cordn does not go through [com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator]
 * like the nostr surfaces do: its `imeta` tag carries the hash of the *plaintext* and a
 * per-file key, which the orchestrator neither produces nor surfaces. So the dialog's
 * two byte-level choices are applied here by hand, against the same helpers the
 * orchestrator uses, and the encryption and upload stay in [CordnMediaService].
 */
private suspend fun sendAttachment(
    uploader: MediaUploader,
    accountViewModel: AccountViewModel,
    room: CordnGroupChatroom,
    state: ChatFileUploadState,
) {
    // Every step here used to `?: return`, which reads as "nothing to do" and
    // behaves as "the attach button does nothing at all": the picker closed,
    // the spinner ended, and no message and no error appeared. Each one is now
    // a reason a person can act on.
    val session =
        accountViewModel.account.cordnRuntime?.sessionOrNull(room.coordinatorPubKey)
            ?: throw CordnAttachmentException(loadStringRes(Res.string.cordn_send_no_session))
    val group =
        session.manager.group(room.gid)
            ?: throw CordnAttachmentException(loadStringRes(Res.string.cordn_send_no_session))

    // The gallery inside the dialog can delete what was picked, which leaves an empty
    // orchestrator rather than a null one. canPost() now refuses that, but indexing it
    // blindly here crashed, so it is checked where the index happens too.
    val orchestrator = state.multiOrchestrator
    if (orchestrator == null || orchestrator.size() == 0) return

    val item = orchestrator.get(0)
    val uri = item.media.uri
    val declaredMime = item.media.mimeType ?: uploader.mimeType(uri) ?: CordnBlobUpload.OPAQUE

    // Marks the dialog busy: its Send button reads canPost(), which is false while a
    // tracker says an upload is running. Without this the button stayed live for the
    // whole upload and a second tap encrypted, uploaded and posted the file twice.
    state.mediaUploadTracker.startUpload(orchestrator.hasNonMedia())

    try {
        // The media-quality slider.
        //
        // Off the main thread, like the strip and the read below it:
        // `sendAttachment` is called from the composition's scope, so it
        // inherits Main, and compression blocks. The voice path posts its
        // recording already encoded and skips compression entirely.
        val compressed =
            withContext(Dispatchers.IO) {
                item.orchestrator.compressIfNeeded(
                    uri = uri,
                    mimeType = declaredMime,
                    compressionQuality = CompressorQuality.fromSlider(state.mediaQualitySlider),
                    uploader = uploader,
                )
            }
        val mime = compressed.contentType ?: declaredMime

        // The strip-metadata switch. A file type the stripper does not handle comes back
        // untouched and says so, which is not a failure — there was nothing to strip.
        val finalUri =
            if (state.stripMetadata) {
                uploader.stripMetadata(compressed.uri, mime)
            } else {
                compressed.uri
            }

        try {
            // The name the picker reported (Android's DISPLAY_NAME): `uri.lastPathSegment` is a
            // document id on a content:// URI, not a filename. The query is a disk read, so it
            // goes off the main thread with the rest — StrictMode flags it otherwise.
            val name = withContext(Dispatchers.IO) { uploader.displayName(uri) } ?: uri.lastPathSegmentOrNull() ?: "file"
            val bytes =
                uploader.readBytes(finalUri)
                    ?: throw CordnAttachmentException(loadStringRes(Res.string.cordn_media_unreadable))

            // Null means the chosen host has no base URL, which is a setting the person can
            // change — the one failure here that is entirely actionable.
            val tag =
                CordnMediaService(accountViewModel.account, accountViewModel.httpClientBuilder)
                    .upload(group, bytes, mime, name, state.selectedServer.baseUrl)
                    ?: throw CordnAttachmentException(loadStringRes(Res.string.cordn_media_no_server))

            // Into the room as well, for the same reason every other send is: an
            // attachment of your own echoes back as an Echo and would otherwise be
            // invisible to the person who sent it.
            //
            // The dialog's description is the message's own content, so an attachment with
            // something written about it is one message rather than two.
            room.add(session.manager.send(room.gid, content = state.caption.trim(), tags = arrayOf(tag)))
        } finally {
            // Compressing and stripping each write a new file, and both hold the
            // attachment in the clear. Leaving them in the cache would keep plaintext
            // copies of a message that is end-to-end encrypted everywhere else — the
            // same reason the voice path deletes its recording. Both calls no-op on the
            // user's own file.
            if (finalUri != uri) uploader.discardTempFile(finalUri)
            if (compressed.uri != finalUri && compressed.uri != uri) uploader.discardTempFile(compressed.uri)
        }
    } finally {
        // Always, not just on success: a failure leaves the dialog up to retry from,
        // and a tracker stuck "uploading" would keep its Send button dead forever.
        state.mediaUploadTracker.finishUpload()
    }
}

/**
 * One attachment, drawn by the pipeline every other chat's media goes through.
 *
 * Registering a [CordnMediaCipher] against the blob URL lets
 * `EncryptedBlobInterceptor` decrypt the download in flight, so the bytes reach
 * [ZoomableContentView] already in the clear and cordn gets the app's real
 * image, video and voice-note rendering — zoom, the pager, the content-warning
 * gate, thumbhash backdrops — instead of its own.
 *
 * It used to fetch and decrypt by hand and then draw a bare `Image`, an
 * OutlinedButton reading "Open <filename>", and its own audio player, on the
 * argument that a blob fetch tells the host that a particular person opened a
 * particular message and so should wait for a tap. The concern is real; a
 * cordn-only tap gate was the wrong place to answer it. Auto-loading media is
 * an app-wide setting that the shared renderer already honours, and every other
 * encrypted chat — Marmot included — routes through it, so the one chat that
 * opted out was also the one whose media did not look like the app.
 */
@Composable
fun CordnAttachment(
    attachment: CordnMediaAttachment,
    room: CordnGroupChatroom,
    accountViewModel: AccountViewModel,
) {
    // Derived, not carried: spec/applications/encrypted-media.md §3.1. Null
    // means this device cannot open the group at all, which is the one case
    // there is nothing to draw for.
    val mediaKey =
        remember(room.gid, attachment.url) {
            accountViewModel.account.cordnRuntime
                ?.sessionOrNull(room.coordinatorPubKey)
                ?.manager
                ?.group(room.gid)
                ?.let { CordnMediaEncryption.mediaKey(it) }
        }

    if (mediaKey == null) {
        Text(
            text = stringRes(Res.string.cordn_media_download_failed),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    val cipher = remember(attachment, mediaKey) { CordnMediaCipher(mediaKey, attachment) }
    accountViewModel.account.encryptionKeyCache.add(attachment.url, cipher, attachment.mimeType)

    // A voice note is audio, and audio has no picture: sent down the video
    // branch below it plays on a blank video surface. The Note-free player is
    // the same one the other chats reach, which cordn cannot get to through a
    // Note because its messages never enter LocalCache.
    if (attachment.isAudio) {
        // Present when the sender wrote the hint; the player falls back to a
        // placeholder when nobody did, so the bars are never simply missing.
        val bars = remember(attachment) { attachment.waveform?.let { WaveformData(it) } }
        Box(Modifier.padding(top = 6.dp)) {
            RenderAudioPlayer(
                mediaUrl = attachment.url,
                title = attachment.filename,
                mimeType = attachment.mimeType,
                waveform = bars,
                accountViewModel = accountViewModel,
            )
        }
        return
    }

    val content =
        remember(attachment, mediaKey) {
            val dim = attachment.dimensions?.let { DimensionTag.parse(it) }
            if (attachment.isImage) {
                EncryptedMediaUrlImage(
                    url = attachment.url,
                    description = attachment.filename,
                    hash = attachment.plaintextHash,
                    blurhash = attachment.blurhash,
                    dim = dim,
                    mimeType = attachment.mimeType,
                    encryptionAlgo = CordnMediaTag.VERSION_V1,
                    encryptionKey = mediaKey,
                    encryptionNonce = attachment.nonceBytes,
                )
            } else {
                EncryptedMediaUrlVideo(
                    url = attachment.url,
                    description = attachment.filename,
                    hash = attachment.plaintextHash,
                    blurhash = attachment.blurhash,
                    dim = dim,
                    mimeType = attachment.mimeType,
                    encryptionAlgo = CordnMediaTag.VERSION_V1,
                    encryptionKey = mediaKey,
                    encryptionNonce = attachment.nonceBytes,
                )
            }
        }

    Box(Modifier.padding(top = 6.dp)) {
        ZoomableContentView(
            content = content,
            roundedCorner = true,
            contentScale = ContentScale.FillWidth,
            accountViewModel = accountViewModel,
        )
    }
}

/**
 * Tap to record, tap again to stop; the recording goes to the composer's preview, not
 * straight to the room.
 *
 * The platform's recorder asks for the microphone on the first press rather than when the
 * room opens: opening a chat is not consent to use the microphone, and a dialog that appears
 * before anyone reached for it trains people to dismiss it. It also drops a press too short to
 * be a message, so an accidental tap never sends a zero-second voice note to a group. Where
 * the platform has no recorder the button is not drawn at all.
 */
@Composable
fun VoiceNoteButton(
    enabled: Boolean,
    onRecorded: (RecordingResult) -> Unit,
) {
    if (!enabled) {
        IconButton(enabled = false, onClick = {}) {
            Icon(
                symbol = MaterialSymbols.Mic,
                contentDescription = stringRes(Res.string.cordn_voice_record),
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }
        return
    }

    RecordAudioBox(
        modifier = Modifier.size(48.dp),
        onRecordTaken = onRecorded,
        maxDurationSeconds = null,
    ) { recording, _, _ ->
        Icon(
            symbol = if (recording) MaterialSymbols.Stop else MaterialSymbols.Mic,
            contentDescription = stringRes(if (recording) Res.string.cordn_voice_stop else Res.string.cordn_voice_record),
            // Matches the attach icon beside it; red only while recording,
            // which is the one state worth pulling the eye.
            tint = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.placeholderText,
        )
    }
}

/**
 * Sends a recording through exactly the same encrypted path as any other file.
 *
 * A voice note is a file: same codec, same Blossom upload, same `imeta`
 * descriptor inside the envelope. The only cordn-specific part is deleting
 * the cache file afterwards — the recorder writes plaintext audio to
 * `cacheDir`, and leaving it there would keep an unencrypted copy of a
 * message that was end-to-end encrypted everywhere else.
 */
private suspend fun sendVoiceNote(
    accountViewModel: AccountViewModel,
    room: CordnGroupChatroom,
    recording: RecordingResult,
    caption: String,
) {
    // Reported rather than returned, for the reason sendAttachment is: a voice
    // note that goes nowhere and says nothing is the same bug twice.
    val session =
        accountViewModel.account.cordnRuntime?.sessionOrNull(room.coordinatorPubKey)
            ?: throw CordnAttachmentException(loadStringRes(Res.string.cordn_send_no_session))
    val group =
        session.manager.group(room.gid)
            ?: throw CordnAttachmentException(loadStringRes(Res.string.cordn_send_no_session))

    try {
        val bytes = withContext(Dispatchers.IO) { recording.file.toFile().readBytes() }
        val tag =
            CordnMediaService(accountViewModel.account, accountViewModel.httpClientBuilder)
                .upload(
                    group = group,
                    bytes = bytes,
                    mimeType = recording.mimeType,
                    filename = recording.file.name,
                    // The recorder measured these while it was recording; the
                    // composer's own preview already draws them. Carrying them
                    // is what makes the bubble match the preview.
                    waveform = recording.amplitudes,
                )
                ?: throw CordnAttachmentException(loadStringRes(Res.string.cordn_media_no_server))
        // Into the room as well, for the same reason every other send is: an
        // attachment of your own echoes back as an Echo and would otherwise be
        // invisible to the person who sent it.
        room.add(session.manager.send(room.gid, content = caption.trim(), tags = arrayOf(tag)))
    } finally {
        withContext(Dispatchers.IO) { recording.file.toFile().delete() }
    }
}
