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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.chats.ui.ThinSendButton
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chat_composer_hint
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.DisplayReplyingToNote
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.commons.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPickerButton
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.FlowPreview

/**
 * Mirror of `EditFieldRow` for the nest-room composer. Uses
 * [NestNewMessageViewModel] which is scoped to a single
 * [com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent]
 * and only emits kind-1311 chat messages.
 *
 * Renders, top-to-bottom: stripping-failure dialog, reply preview,
 * file-upload dialog, @-mention picker, emoji suggestions, and the
 * text field with gallery picker + send button.
 *
 * Differences from the channel `EditFieldRow`:
 *  - No `BackHandler`: the nest chat panel lives inside an Activity
 *    that owns its own back behaviour (leave room / enter PiP). The
 *    composer's draft auto-save runs on every text change via
 *    [NestNewMessageViewModel.draftTag], so there is nothing to flush
 *    on back press.
 */
@OptIn(FlowPreview::class)
@Composable
fun NestEditFieldRow(
    nestScreenModel: NestNewMessageViewModel,
    accountViewModel: AccountViewModel,
    onSendNewMessage: suspend () -> Unit,
    nav: INav,
) {
    StrippingFailureDialog(nestScreenModel.strippingFailureConfirmation)

    nestScreenModel.replyTo.value?.let {
        DisplayReplyingToNote(it, accountViewModel, nav) {
            nestScreenModel.clearReply()
        }
    }

    nestScreenModel.uploadState?.let { uploading ->
        uploading.multiOrchestrator?.let {
            NestFileUploadDialog(
                nestScreenModel = nestScreenModel,
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
        nestScreenModel.userSuggestions?.let {
            ShowUserSuggestionList(
                it,
                nestScreenModel::autocompleteWithUser,
                accountViewModel,
                SuggestionListDefaultHeightChat,
            )
        }

        nestScreenModel.emojiSuggestions?.let {
            ShowEmojiSuggestionList(
                it,
                nestScreenModel::autocompleteWithEmoji,
                nestScreenModel::autocompleteWithEmojiUrl,
                SuggestionListDefaultHeightChat,
            )
        }

        ThinPaddingTextField(
            state = nestScreenModel.message,
            onTextChanged = { nestScreenModel.onMessageChanged() },
            onContentReceived = { uri, mimeType ->
                nestScreenModel.pickedMedia(persistentListOf(SelectedMedia(uri, mimeType)))
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
                        nestScreenModel.message.text.isNotBlank() && !nestScreenModel.isUploadingImage,
                    modifier = EditFieldTrailingIconModifier,
                ) {
                    nestScreenModel.sendPost(onSendNewMessage)
                }
            },
            leadingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectFromGallery(
                        isUploading = nestScreenModel.isUploadingImage,
                        tint = MaterialTheme.colorScheme.placeholderText,
                        modifier = Modifier.height(32.dp).padding(start = 2.dp),
                        onImageChosen = nestScreenModel::pickedMedia,
                    )
                    EmojiPickerButton(
                        nestScreenModel.emojiSuggestions?.emojiPacks,
                        nestScreenModel.message,
                        tint = MaterialTheme.colorScheme.placeholderText,
                    )
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
