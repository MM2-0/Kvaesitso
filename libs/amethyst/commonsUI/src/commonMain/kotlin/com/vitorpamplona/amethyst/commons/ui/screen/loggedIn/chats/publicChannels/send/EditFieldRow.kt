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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.send

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.chats.ui.ReplyModeToggle
import com.vitorpamplona.amethyst.commons.chats.ui.ThinSendButton
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzTypingState
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_editing_banner
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.chat_composer_hint
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.DisplayReplyingToNote
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPickerButton
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.FlowPreview

@OptIn(FlowPreview::class)
@Composable
fun EditFieldRow(
    channelScreenModel: ChannelNewMessageViewModel,
    accountViewModel: AccountViewModel,
    onSendNewMessage: suspend () -> Unit,
    nav: INav,
) {
    PlatformBackHandler {
        accountViewModel.launchSigner {
            channelScreenModel.sendDraftSync()
            onUiThread { channelScreenModel.cancel() }
        }
        nav.popBack()
    }

    StrippingFailureDialog(channelScreenModel.strippingFailureConfirmation)

    channelScreenModel.replyTo.value?.let {
        DisplayReplyingToNote(it, accountViewModel, nav) {
            channelScreenModel.clearReply()
        }
        ReplyModeToggle(
            mode = channelScreenModel.replyMode.value,
            onToggle = { channelScreenModel.toggleReplyMode() },
        )
    }

    // Buzz edit mode: a banner reminding the user the next send replaces an existing
    // message (a kind-40003 edit), with an X to abandon the edit and clear the field.
    channelScreenModel.editingBuzzMessage.value?.let {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringRes(Res.string.buzz_editing_banner),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = { channelScreenModel.clearBuzzEdit() }) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(Res.string.cancel),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    channelScreenModel.uploadState?.let { uploading ->
        uploading.multiOrchestrator?.let { _ ->
            ChannelFileUploadDialog(
                channelScreenModel = channelScreenModel,
                state = uploading,
                onUpload = onSendNewMessage,
                onCancel = uploading::reset,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }

    Column(
        modifier = EditFieldModifier,
    ) {
        channelScreenModel.userSuggestions?.let {
            ShowUserSuggestionList(
                it,
                channelScreenModel::autocompleteWithUser,
                accountViewModel,
                SuggestionListDefaultHeightChat,
            )
        }

        channelScreenModel.emojiSuggestions?.let {
            ShowEmojiSuggestionList(
                it,
                channelScreenModel::autocompleteWithEmoji,
                channelScreenModel::autocompleteWithEmojiUrl,
                SuggestionListDefaultHeightChat,
            )
        }

        // Client-side throttle for the Buzz kind-20002 typing heartbeat (below).
        val lastTypingSecs = remember { longArrayOf(0L) }

        ThinPaddingTextField(
            state = channelScreenModel.message,
            onTextChanged = {
                channelScreenModel.onMessageChanged()
                // Buzz typing indicator: fire a throttled heartbeat while composing in a
                // Buzz workspace channel, so other members see "… is typing". No-op on any
                // other chat (the kind is Buzz-only and the relay would ignore it anyway).
                val channel = channelScreenModel.channel
                if (channel is RelayGroupChannel &&
                    BuzzRelayDialect.isBuzz(channel.groupId.relayUrl) &&
                    channelScreenModel.message.text.isNotEmpty()
                ) {
                    val now = TimeUtils.now()
                    if (now - lastTypingSecs[0] >= BuzzTypingState.TYPING_HEARTBEAT_SECS) {
                        lastTypingSecs[0] = now
                        accountViewModel.sendBuzzTyping(channel)
                    }
                }
            },
            onContentReceived = { uri, mimeType ->
                channelScreenModel.pickedMedia(persistentListOf(SelectedMedia(uri, mimeType)))
            },
            inputTransformation = MentionPreservingInputTransformation,
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            shape = EditFieldBorder,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringRes(Res.string.chat_composer_hint),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
            trailingIcon = {
                ThinSendButton(
                    isActive =
                        channelScreenModel.message.text.isNotBlank() && !channelScreenModel.isUploadingImage,
                    modifier = EditFieldTrailingIconModifier,
                ) {
                    channelScreenModel.sendPost(onSendNewMessage)
                }
            },
            leadingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectFromGallery(
                        isUploading = channelScreenModel.isUploadingImage,
                        tint = MaterialTheme.colorScheme.placeholderText,
                        modifier = Modifier.height(32.dp).padding(start = 2.dp),
                        onImageChosen = channelScreenModel::pickedMedia,
                    )
                    EmojiPickerButton(channelScreenModel.emojiSuggestions?.emojiPacks, channelScreenModel.message, tint = MaterialTheme.colorScheme.placeholderText)
                }
            },
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            spellCheck = true,
            outputTransformation = rememberUrlUserTagOutputTransformation(),
        )
    }
}
