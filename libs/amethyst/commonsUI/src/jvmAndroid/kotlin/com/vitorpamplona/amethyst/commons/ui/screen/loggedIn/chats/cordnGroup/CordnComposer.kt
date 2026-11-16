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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.audio.RecordingResult
import com.vitorpamplona.amethyst.commons.chats.ui.ThinSendButton
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cordn_composer_hint
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.ui.actions.UrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.note.platform.VoiceMessagePreview
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.text.replaceCurrentWord
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta

/**
 * The cordn room's composer, on the same field every other Amethyst chat uses.
 *
 * [ThinPaddingTextField] + [ThinSendButton] inside [EditFieldModifier], with the shared
 * mention machinery on top: [ShowUserSuggestionList] for `@` autocomplete,
 * [MentionPreservingInputTransformation] so an IME cannot rewrite half a bech32, and
 * [UrlUserTagOutputTransformation] so a pasted or completed mention reads as a name
 * while you type it. Before this the room had a bare `OutlinedTextField` over a
 * `String`: cordn could *render* mentions and highlight a message that named you, but
 * the only way to enter one was to type `nostr:npub1…` by hand.
 *
 * Mentions are inserted as resolved `nostr:` URIs (`forceNostrUri`). See the parameter's
 * KDoc — cordn has no send-time tagger, so the short `@npub1…` form would ship as text.
 *
 * Looking users up to offer them is the same safe direction as everywhere else on this
 * screen: profiles are public relay data the cache already holds, and reading one puts
 * no part of this conversation into it.
 */
@Composable
fun CordnComposer(
    room: CordnGroupChatroom,
    attaching: Boolean,
    pendingVoice: RecordingResult?,
    accountViewModel: AccountViewModel,
    onAttach: (MediaUri, String?) -> Unit,
    onVoiceNote: (RecordingResult) -> Unit,
    onRemoveVoice: () -> Unit,
    onSend: (String) -> Unit,
) {
    // `room.draft` is the persisted String — commons holds the draft and may not depend
    // on Compose UI, so it cannot hold a TextFieldState. The field owns text *and*
    // selection; these two effects keep them equal. Both writes are guarded on
    // inequality, so neither direction can bounce off the other.
    val draftState = remember(room.gid) { TextFieldState(room.draft.value) }

    val suggestions =
        remember(room.gid, accountViewModel) {
            UserSuggestionState(
                accountViewModel.account,
                accountViewModel.nip05ClientBuilder(),
                // Members of this room rank above the rest of the address book: in a
                // group, the person you are about to name is almost always in it.
                priorityPubkeys = { room.members.value.toSet() },
            )
        }

    // `:shortcode:` completion, as the DM and Concord composers have. Cordn inserts the
    // emoji's URL rather than its shortcode: a shortcode only resolves for a reader who
    // also got the NIP-30 `emoji` tags, and a cordn message carries none — so everyone
    // else would have read a literal ":shrug:". Same reasoning as forcing `nostr:` for
    // user mentions, and the URL renders as the image for every reader.
    val emojiSuggestions = remember(accountViewModel) { EmojiSuggestionState(accountViewModel.account.emoji) }

    WatchAndLoadMyEmojiList(accountViewModel)

    DisposableEffect(suggestions, emojiSuggestions) {
        onDispose {
            suggestions.reset()
            emojiSuggestions.reset()
        }
    }

    LaunchedEffect(draftState, room) {
        snapshotFlow { draftState.text.toString() }.collect {
            if (room.draft.value != it) room.draft.value = it
        }
    }

    LaunchedEffect(draftState, room) {
        room.draft.collect { external ->
            // The screen writes the draft from outside on three paths: clearing it on
            // send, restoring it when a send fails, and loading a message's text into
            // it to edit. Cursor to the end, as editFromDraft does elsewhere.
            if (external != draftState.text.toString()) {
                if (external.isEmpty()) draftState.clearText() else draftState.setTextAndPlaceCursorAtEnd(external)
                // onTextChanged only fires for typing, so a list left open by a
                // half-typed "@na" survived the field being cleared on send.
                suggestions.reset()
                emojiSuggestions.reset()
            }
        }
    }

    // A recorded voice note is something to send even with nothing typed; the text
    // beside it rides along as its caption.
    val canPost by remember(pendingVoice) { derivedStateOf { draftState.text.isNotBlank() || pendingVoice != null } }

    Column(modifier = EditFieldModifier) {
        // The same preview the post screens use: listen back, re-record, or drop it.
        // Recording used to send the moment you released the button, which is a hard
        // thing to get right first time and impossible to take back afterwards.
        pendingVoice?.let { recording ->
            val meta =
                remember(recording) {
                    AudioMeta(
                        // Empty: nothing is uploaded yet, so the preview plays the
                        // local file instead.
                        url = "",
                        mimeType = recording.mimeType,
                        duration = recording.duration,
                        waveform = recording.amplitudes,
                    )
                }

            VoiceMessagePreview(
                voiceMetadata = meta,
                localFile = recording.file,
                onRemove = onRemoveVoice,
                onReRecord = onVoiceNote,
                isUploading = attaching,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ShowEmojiSuggestionList(
            emojiSuggestions,
            onSelect = { insertEmojiUrl(draftState, emojiSuggestions, it) },
            onFullSize = { insertEmojiUrl(draftState, emojiSuggestions, it) },
            modifier = SuggestionListDefaultHeightChat,
        )

        ShowUserSuggestionList(
            suggestions,
            onSelect = { user ->
                suggestions.replaceCurrentWord(draftState, draftState.currentWord(), user, forceNostrUri = true)
                suggestions.reset()
            },
            accountViewModel = accountViewModel,
            modifier = SuggestionListDefaultHeightChat,
        )

        ThinPaddingTextField(
            state = draftState,
            onTextChanged = {
                // Only while the caret is a point: during a range selection the "current
                // word" is whatever is highlighted, which is not something being typed.
                if (draftState.selection.collapsed) {
                    val lastWord = draftState.currentWord()
                    when {
                        lastWord.startsWith("@") -> {
                            suggestions.processCurrentWord(lastWord)
                            emojiSuggestions.reset()
                        }

                        lastWord.startsWith(":") -> {
                            emojiSuggestions.processCurrentWord(lastWord)
                            suggestions.reset()
                        }

                        else -> {
                            suggestions.reset()
                            emojiSuggestions.reset()
                        }
                    }
                }
            },
            // Anything the keyboard or a paste hands over as content — a GIF, a shared
            // image — takes the same encrypted-upload path as the file picker.
            onContentReceived = { uri, mimeType -> onAttach(uri, mimeType) },
            inputTransformation = MentionPreservingInputTransformation,
            outputTransformation = rememberUrlUserTagOutputTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = EditFieldBorder,
            placeholder = {
                Text(
                    text = stringRes(Res.string.cordn_composer_hint),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            leadingIcon = {
                // Same frame every other chat composer uses for its leading
                // icons (`MarmotGalleryLeadingIcon`): 4.dp either side and the
                // placeholder tint. Without it these two sat further from the
                // edge and further apart than the rest of the app's composers,
                // because a bare IconButton keeps its full 48.dp touch target
                // and nothing was pulling the row back in.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                ) {
                    // The app's own gallery picker, so this composer offers the same
                    // affordance and the same multi-select as the DM and Marmot ones
                    // rather than a bare file dialog. Each pick is its own encrypted
                    // upload and its own message, which is what the send path does.
                    SelectFromGallery(
                        isUploading = attaching,
                        enabled = !attaching,
                        tint = MaterialTheme.colorScheme.placeholderText,
                        modifier = Modifier,
                        onImageChosen = { picked -> picked.forEach { onAttach(it.uri, it.mimeType) } },
                    )
                    VoiceNoteButton(enabled = !attaching, onRecorded = onVoiceNote)
                }
            },
            trailingIcon = {
                ThinSendButton(
                    isActive = canPost,
                    modifier = EditFieldTrailingIconModifier,
                ) {
                    onSend(draftState.text.toString())
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
 * Puts [item]'s URL where the half-typed `:shortcode:` was, and closes the list.
 *
 * [EmojiSuggestionState.autocompleteInto] inserts the shortcode, which is right for a
 * composer whose send path attaches the matching `emoji` tags. Cordn's does not, so the
 * URL goes in instead — see the state's comment above.
 */
private fun insertEmojiUrl(
    field: TextFieldState,
    state: EmojiSuggestionState,
    item: EmojiPackState.EmojiMedia,
) {
    field.replaceCurrentWord(item.link + " ")
    state.reset()
}
