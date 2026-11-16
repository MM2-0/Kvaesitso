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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.notifications.publicMessages

import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.messages_new_message_to
import com.vitorpamplona.amethyst.commons.resources.messages_new_message_to_caption
import com.vitorpamplona.amethyst.commons.resources.public_message
import com.vitorpamplona.amethyst.commons.resources.zapraiser
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.submitOnCtrlEnter
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.creators.contentWarning.ContentSensitivityExplainer
import com.vitorpamplona.amethyst.commons.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.commons.ui.note.creators.expiration.ExpirationDatePicker
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.NewPostInvoiceRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.GeoHashPostSection
import com.vitorpamplona.amethyst.commons.ui.note.creators.previews.DisplayPreviews
import com.vitorpamplona.amethyst.commons.ui.note.creators.secretEmoji.SecretEmojiRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerMediaTools
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerPostOptionTools
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerToolbar
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.composerBody
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapraiser.ZapRaiserRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.ForwardZapTo
import com.vitorpamplona.amethyst.commons.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.MessageFieldRow
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Font14SP
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NewPublicMessageScreen(
    to: Set<HexKey>? = null,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: NewPublicMessageViewModel = rememberViewModel { NewPublicMessageViewModel() }
    postViewModel.init(accountViewModel)

    LaunchedEffect(postViewModel, accountViewModel) {
        withContext(Dispatchers.IO) {
            to?.let {
                postViewModel.load(it)
            }
            replyId?.let { LocalCache.getNoteIfExists(it) }?.let {
                postViewModel.reply(it)
            }
            draftId?.let { LocalCache.getNoteIfExists(it) }?.let {
                postViewModel.editFromDraft(it)
            }
        }
    }

    WatchAndLoadMyEmojiList(accountViewModel)

    StrippingFailureDialog(postViewModel.strippingFailureConfirmation)

    PlatformBackHandler {
        accountViewModel.launchSigner {
            postViewModel.sendDraftSync()
            onUiThread { postViewModel.cancel() }
        }
        nav.popBack()
    }

    val onPost: () -> Unit = {
        // uses the accountViewModel scope to avoid cancelling this
        // function when the postViewModel is released
        accountViewModel.launchSigner {
            postViewModel.sendPostSync()
            nav.popBack()
        }
    }

    Scaffold(
        modifier = Modifier.submitOnCtrlEnter(isActive = postViewModel::canPost, onSubmit = onPost),
        topBar = {
            PostingTopBar(
                titleRes = Res.string.public_message,
                isActive = postViewModel::canPost,
                onCancel = {
                    // uses the accountViewModel scope to avoid cancelling this
                    // function when the postViewModel is released
                    accountViewModel.launchSigner {
                        postViewModel.sendDraftSync()
                        onUiThread { postViewModel.cancel() }
                    }
                    nav.popBack()
                },
                onPost = onPost,
            )
        },
    ) { pad ->
        Surface(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePaddingSafe(),
        ) {
            PublicMessageScreenContent(postViewModel, accountViewModel, nav)
        }
    }
}

@Composable
fun PublicMessageScreenContent(
    postViewModel: NewPublicMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scrollState = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Size10dp).then(composerBody())) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(scrollState, reverseScrolling = true),
                verticalArrangement = spacedBy(Size10dp),
            ) {
                val replyTo = postViewModel.replyingTo

                if (replyTo == null) {
                    SendDirectMessageTo(postViewModel, accountViewModel)
                } else {
                    Row {
                        NoteCompose(
                            baseNote = replyTo,
                            modifier = MaterialTheme.colorScheme.imageModifier,
                            isQuotedNote = true,
                            unPackReply = ReplyRenderType.NONE,
                            makeItShort = true,
                            quotesLeft = 1,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }

                MessageFieldRow(
                    postViewModel,
                    accountViewModel,
                    postViewModel.toUsers.text.isNotBlank(),
                    onContentReceived = { uri, mimeType ->
                        postViewModel.selectImage(persistentListOf(SelectedMedia(uri, mimeType)))
                    },
                )

                DisplayPreviews(postViewModel.urlPreviews, postViewModel.iMetaAttachments, accountViewModel, nav)

                if (postViewModel.wantsToMarkAsSensitive) {
                    ContentSensitivityExplainer(
                        description = postViewModel.contentWarningDescription,
                        onDescriptionChange = { postViewModel.contentWarningDescription = it },
                    )
                }

                if (postViewModel.wantsExpirationDate) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                    ) {
                        ExpirationDatePicker(postViewModel)
                    }
                }

                if (postViewModel.wantsToAddGeoHash) {
                    GeoHashPostSection(postViewModel)
                }

                if (postViewModel.wantsForwardZapTo) {
                    ForwardZapTo(postViewModel, accountViewModel)
                }

                postViewModel.multiOrchestrator?.let {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                    ) {
                        ImageVideoDescription(
                            it,
                            accountViewModel.account.settings.defaultFileServer,
                            isUploading = postViewModel.mediaUploadTracker.isUploading,
                            onAdd = { alt, server, sensitiveContent, mediaQuality, useH265, stripMetadata, convertGifToMp4 ->
                                postViewModel.upload(alt, if (sensitiveContent) "" else null, mediaQuality, server, accountViewModel.toastManager::toast, accountViewModel.mediaUploader, useH265, stripMetadata, convertGifToMp4)
                                accountViewModel.account.settings.changeDefaultFileServer(server)
                            },
                            onDelete = postViewModel::deleteMediaToUpload,
                            onCancel = { postViewModel.multiOrchestrator = null },
                            accountViewModel = accountViewModel,
                        )
                    }
                }

                if (postViewModel.wantsInvoice) {
                    NewPostInvoiceRequest(
                        onSuccess = {
                            postViewModel.insertAtCursor(it)
                            postViewModel.wantsInvoice = false
                        },
                        accountViewModel,
                    )
                }

                if (postViewModel.wantsSecretEmoji) {
                    SecretEmojiRequest {
                        postViewModel.insertAtCursor(it)
                        postViewModel.wantsSecretEmoji = false
                    }
                }

                if (postViewModel.wantsZapRaiser && postViewModel.hasLnAddress()) {
                    ZapRaiserRequest(
                        stringRes(id = Res.string.zapraiser),
                        postViewModel,
                    )
                }
            }
        }

        postViewModel.userSuggestions?.let {
            ShowUserSuggestionList(
                it,
                postViewModel::autocompleteWithUser,
                accountViewModel,
                SuggestionListDefaultHeightPage,
            )
        }

        postViewModel.emojiSuggestions?.let {
            ShowEmojiSuggestionList(
                it,
                postViewModel::autocompleteWithEmoji,
                postViewModel::autocompleteWithEmojiUrl,
                SuggestionListDefaultHeightPage,
            )
        }

        BottomRowActions(postViewModel, accountViewModel)
    }
}

@Composable
private fun BottomRowActions(
    postViewModel: NewPublicMessageViewModel,
    accountViewModel: AccountViewModel,
) {
    ComposerToolbar { _ ->
        ComposerMediaTools(postViewModel)
        ComposerPostOptionTools(postViewModel)
    }
}

@Composable
fun SendDirectMessageTo(
    postViewModel: NewPublicMessageViewModel,
    accountViewModel: AccountViewModel,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (postViewModel.toUsers.text.isBlank()) {
            launch {
                delay(200)
                focusRequester.requestFocus()
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(Res.string.messages_new_message_to),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            ThinPaddingTextField(
                state = postViewModel.toUsers,
                onTextChanged = postViewModel::onToUsersChanged,
                inputTransformation = MentionPreservingInputTransformation,
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                keyboardController?.show()
                            }
                        },
                placeholder = {
                    Text(
                        text = stringRes(Res.string.messages_new_message_to_caption),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                spellCheck = true,
                outputTransformation =
                    rememberUrlUserTagOutputTransformation(),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
            )
        }

        HorizontalDivider(thickness = DividerThickness)
    }
}
