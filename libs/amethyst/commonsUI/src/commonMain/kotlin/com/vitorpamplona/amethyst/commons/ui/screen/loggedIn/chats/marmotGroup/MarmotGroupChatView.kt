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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.marmotGroup.MarmotGroupFeedViewModel
import com.vitorpamplona.amethyst.commons.chats.ui.ThinSendButton
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.marmotGroupLastReadRoute
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.chat_composer_hint
import com.vitorpamplona.amethyst.commons.resources.marmot_awaiting_reinvite
import com.vitorpamplona.amethyst.commons.resources.marmot_group_composer_disbanding
import com.vitorpamplona.amethyst.commons.resources.marmot_group_composer_leaving
import com.vitorpamplona.amethyst.commons.resources.marmot_group_composer_removed
import com.vitorpamplona.amethyst.commons.resources.marmot_group_default_name
import com.vitorpamplona.amethyst.commons.resources.marmot_not_a_member
import com.vitorpamplona.amethyst.commons.resources.marmot_out_of_sync_body
import com.vitorpamplona.amethyst.commons.resources.marmot_out_of_sync_confirm_body
import com.vitorpamplona.amethyst.commons.resources.marmot_out_of_sync_confirm_title
import com.vitorpamplona.amethyst.commons.resources.marmot_out_of_sync_reset
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_create
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberLongNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.LocalChatIsOneOnOne
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.send.CreateMarmotPollSheet
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.send.MarmotFileSender
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.send.MarmotFileUploader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.send.MarmotNewMessageViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.ChatFileUploadDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.DisplayReplyingToNote
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.EditingMessageBanner
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.protocolCore.LocalOutboundGate
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

@Composable
fun MarmotGroupChatView(
    nostrGroupId: HexKey,
    draftMessage: String? = null,
    replyToInnerNote: HexKey? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedViewModel: MarmotGroupFeedViewModel =
        rememberViewModel(
            key = nostrGroupId + "MarmotGroupFeedViewModel",
            factory =
                MarmotGroupFeedViewModel.Factory(
                    nostrGroupId,
                    accountViewModel.account,
                ),
        )

    WatchLifecycleAndUpdateModel(feedViewModel)

    val chatroom =
        remember(nostrGroupId) {
            accountViewModel.account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        }

    val newMessageModel: MarmotNewMessageViewModel = rememberViewModel(key = nostrGroupId + "MarmotNewMessageViewModel") { MarmotNewMessageViewModel() }
    newMessageModel.init(accountViewModel)
    newMessageModel.load(nostrGroupId)

    // Resolve the navigation-supplied replyId (e.g. tapping reply on an MLS
    // message in the Notifications screen) into the actual Note once it has
    // landed in LocalCache. checkGetOrCreateNote is a no-op for unknown ids.
    if (replyToInnerNote != null) {
        LaunchedEffect(replyToInnerNote) {
            val parent = LocalCache.checkGetOrCreateNote(replyToInnerNote)
            if (parent != null) {
                newMessageModel.reply(parent)
            }
        }
    }

    if (draftMessage != null) {
        LaunchedEffect(draftMessage) {
            newMessageModel.editFromDraft(draftMessage)
        }
    }

    // The live agent-preview watcher is NOT started here.
    //
    // Opening the chat used to build a [MarmotAgentStreamWatcher] and call
    // `watchLatest` on every feed change, which dials the QUIC brokers a
    // kind:1200 advertises. Nothing in the deployed network publishes those
    // streams, so that was a UDP connection attempt to a third-party endpoint
    // on behalf of a feature no one is using — a service we start, not a
    // capability we hold.
    //
    // The watcher, the transport and the banner all still exist and are still
    // tested; `amy marmot stream watch` drives the same code on demand. Wiring
    // it back is re-adding the watcher, the LaunchedEffect and the banner
    // below, once there is something to watch.

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(1f, true),
        ) {
            // Two members is a 1:1 chat: drop the other person's name and picture from every
            // bubble, as a NIP-17 conversation does. Zero means not loaded yet, so keep them.
            val memberCount by chatroom.memberCount.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalChatIsOneOnOne provides (memberCount in 1..2)) {
                RefreshingChatroomFeedView(
                    feedContentState = feedViewModel.feedState,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    routeForLastRead = marmotGroupLastReadRoute(nostrGroupId),
                    onWantsToReply = { note -> newMessageModel.reply(note) },
                    onWantsToEditDraft = { },
                    onWantsToEditChatMessage = { note -> newMessageModel.editMarmotMessage(note) },
                    // kind:1210 rows sit in the conversation in order but are
                    // group-state captions rather than messages, so they get their
                    // own centered style instead of a bubble.
                    rowRenderer = remember(accountViewModel) { MarmotSystemRowRenderer(accountViewModel) },
                )
            }
        }

        Spacer(modifier = DoubleVertSpacer)

        // A durable outbound gate means the group takes no new work: an
        // unresolved disband request, a SelfRemove already sent, a realized
        // removal. Sending would throw behind it, so the composer is replaced
        // by the reason rather than left there to fail on tap — the history
        // stays readable either way, which is the point of a gate that is not
        // a terminal state.
        val outboundGate by chatroom.outboundGate.collectAsStateWithLifecycle()
        val isOutOfSync by chatroom.isOutOfSync.collectAsStateWithLifecycle()
        val awaitingReinvite by chatroom.awaitingReinvite.collectAsStateWithLifecycle()
        val gate = outboundGate
        if (awaitingReinvite) {
            MarmotGroupNoticeRow(stringRes(Res.string.marmot_awaiting_reinvite))
        } else if (gate != null) {
            MarmotGroupClosedComposer(gate)
        } else {
            if (isOutOfSync) {
                MarmotOutOfSyncBanner(nostrGroupId, accountViewModel)
            }
            MarmotGroupMessageComposer(
                nostrGroupId = nostrGroupId,
                newMessageModel = newMessageModel,
                accountViewModel = accountViewModel,
                nav = nav,
                onMessageSent = {
                    feedViewModel.feedState.sendToTop()
                },
            )
        }
    }
}

@Composable
fun MarmotGroupMessageComposer(
    nostrGroupId: HexKey,
    newMessageModel: MarmotNewMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    onMessageSent: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val canPost by remember { derivedStateOf { newMessageModel.canPost() } }
    val shortNotice = rememberShortNotice()

    var isUploading by remember { mutableStateOf(false) }
    var showPollSheet by remember { mutableStateOf(false) }

    if (showPollSheet) {
        CreateMarmotPollSheet(nostrGroupId, accountViewModel) { showPollSheet = false }
    }

    DisposableEffect(nostrGroupId) {
        onDispose {
            newMessageModel.userSuggestions?.reset()
            newMessageModel.emojiSuggestions?.reset()
        }
    }

    // Upload dialog
    newMessageModel.uploadState?.let { uploadState ->
        uploadState.multiOrchestrator?.let {
            MarmotGroupFileUploadDialog(
                nostrGroupId = nostrGroupId,
                state = uploadState,
                accountViewModel = accountViewModel,
                nav = nav,
                onUpload = { onMessageSent() },
                onCancel = uploadState::reset,
            )
        }
    }

    newMessageModel.replyTo.value?.let {
        DisplayReplyingToNote(it, accountViewModel, nav) {
            newMessageModel.clearReply()
        }
    }

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
            onTextChanged = { newMessageModel.onMessageChanged() },
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
                MarmotGalleryLeadingIcon(
                    isUploading = isUploading,
                    onImageChosen = newMessageModel::pickedMedia,
                    onCreatePoll = { showPollSheet = true },
                )
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

@Composable
private fun MarmotGalleryLeadingIcon(
    isUploading: Boolean,
    onImageChosen: (ImmutableList<SelectedMedia>) -> Unit,
    onCreatePoll: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp),
    ) {
        SelectFromGallery(
            isUploading = isUploading,
            tint = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier,
            onImageChosen = onImageChosen,
        )
        // Encrypted NIP-88 poll. Peers on MDK 0.11+ send them and allow them in
        // direct conversations too (0.13), so the composer offers one everywhere.
        IconButton(onClick = onCreatePoll) {
            Icon(
                symbol = MaterialSymbols.Poll,
                contentDescription = stringRes(Res.string.marmot_poll_create),
                modifier = Modifier.height(22.dp),
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }
}

@Composable
private fun MarmotGroupFileUploadDialog(
    nostrGroupId: HexKey,
    state: ChatFileUploadState,
    accountViewModel: AccountViewModel,
    nav: INav,
    onUpload: suspend () -> Unit,
    onCancel: () -> Unit,
) {
    val shortNotice = rememberShortNotice()
    val longNotice = rememberLongNotice()
    val scope = rememberCoroutineScope()

    ChatFileUploadDialog(
        state = state,
        title = {
            val chatroom =
                remember(nostrGroupId) {
                    accountViewModel.account.marmotGroupList.getOrCreateGroup(nostrGroupId)
                }
            Text(chatroom.displayName.value ?: stringRes(Res.string.marmot_group_default_name))
        },
        upload = {
            scope.launch(Dispatchers.IO) {
                val key = accountViewModel.marmotMediaUploadKey(nostrGroupId)
                if (key == null) {
                    launch(Dispatchers.Main) {
                        shortNotice.show(loadStringRes(Res.string.marmot_not_a_member))
                    }
                    return@launch
                }

                MarmotFileUploader(accountViewModel.account).uploadMip04(
                    viewState = state,
                    exporterSecret = key.mediaSecret,
                    onError = { title, message ->
                        scope.launch(Dispatchers.Main) {
                            longNotice.show("$title: $message")
                        }
                    },
                    uploader = accountViewModel.mediaUploader,
                    onceUploaded = { uploads ->
                        MarmotFileSender(nostrGroupId, key.epoch, accountViewModel).send(uploads)
                        onUpload()
                    },
                )

                accountViewModel.account.settings.changeDefaultFileServer(state.selectedServer)
                accountViewModel.account.settings.changeStripLocationOnUpload(state.stripMetadata)
            }
        },
        onCancel = onCancel,
        accountViewModel = accountViewModel,
        nav = nav,
        isNip17 = false,
    )
}

/**
 * This device has fallen off the group's epoch chain (MarmotDesyncDetector): nothing the
 * other members send decrypts here, and it will not heal on its own. Offers the one way
 * back that exists, dropping the local copy so an admin can add this member again.
 */
@Composable
private fun MarmotOutOfSyncBanner(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
) {
    var confirming by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            text = stringRes(Res.string.marmot_out_of_sync_body),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { confirming = true }, modifier = Modifier.align(Alignment.End)) {
            Text(stringRes(Res.string.marmot_out_of_sync_reset))
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringRes(Res.string.marmot_out_of_sync_confirm_title)) },
            text = { Text(stringRes(Res.string.marmot_out_of_sync_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        accountViewModel.resetOutOfSyncMarmotGroup(nostrGroupId)
                    },
                ) { Text(stringRes(Res.string.marmot_out_of_sync_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringRes(Res.string.cancel)) }
            },
        )
    }
}

/**
 * Stands in for the composer when an outbound gate is up.
 *
 * Deliberately a statement rather than a disabled text field: a greyed-out
 * input still invites typing, and the three reasons are not the same — one is
 * waiting on the group, one on a commit, and one is over. The group's history
 * stays on screen above it.
 */
@Composable
private fun MarmotGroupClosedComposer(gate: LocalOutboundGate) {
    val message =
        when (gate) {
            LocalOutboundGate.DISBANDING -> stringRes(Res.string.marmot_group_composer_disbanding)
            LocalOutboundGate.LEAVING -> stringRes(Res.string.marmot_group_composer_leaving)
            LocalOutboundGate.REMOVED -> stringRes(Res.string.marmot_group_composer_removed)
        }
    MarmotGroupNoticeRow(message)
}

@Composable
private fun MarmotGroupNoticeRow(message: String) {
    Row(
        modifier = EditFieldModifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.placeholderText,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}
