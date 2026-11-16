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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send

import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.drawing.ui.DrawingButton
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeToMessage
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drawing_open
import com.vitorpamplona.amethyst.commons.resources.messages_cant_upload_explainer
import com.vitorpamplona.amethyst.commons.resources.messages_cant_upload_title
import com.vitorpamplona.amethyst.commons.resources.messages_new_message_subject
import com.vitorpamplona.amethyst.commons.resources.messages_new_message_subject_caption
import com.vitorpamplona.amethyst.commons.resources.messages_new_message_to
import com.vitorpamplona.amethyst.commons.resources.messages_new_message_to_caption
import com.vitorpamplona.amethyst.commons.resources.private_message
import com.vitorpamplona.amethyst.commons.resources.record_a_video
import com.vitorpamplona.amethyst.commons.resources.take_a_picture
import com.vitorpamplona.amethyst.commons.resources.upload_media
import com.vitorpamplona.amethyst.commons.resources.write_a_message
import com.vitorpamplona.amethyst.commons.resources.zapraiser
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.EncryptedMediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.EncryptedMediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectFromFiles
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.TakePictureButton
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.TakeVideoButton
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.canCaptureFromCamera
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.rememberSharedMediaResolver
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SendingTopBar
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.creators.contentWarning.ContentSensitivityExplainer
import com.vitorpamplona.amethyst.commons.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.commons.ui.note.creators.expiration.ExpirationDatePicker
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.NewPostInvoiceRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.GeoHashPostSection
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.IMessageField
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.MessageField
import com.vitorpamplona.amethyst.commons.ui.note.creators.previews.PrepareLinkedImageMetadata
import com.vitorpamplona.amethyst.commons.ui.note.creators.previews.PreviewUrl
import com.vitorpamplona.amethyst.commons.ui.note.creators.secretEmoji.SecretEmojiRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerPostOptionTools
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerToolbar
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.composerBody
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapraiser.ZapRaiserRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.ForwardZapTo
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.ChatNewMessageViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.EncryptedUploadErrorDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.RecipientMissingRelaysWarning
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.upload.SuccessfulUploads
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Font14SP
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NewGroupDMScreen(
    message: String? = null,
    attachment: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: ChatNewMessageViewModel = rememberViewModel { ChatNewMessageViewModel() }
    postViewModel.init(accountViewModel)

    val mediaResolver = rememberSharedMediaResolver()

    LaunchedEffect(postViewModel, accountViewModel) {
        message?.ifBlank { null }?.let {
            postViewModel.message.setTextAndPlaceCursorAtEnd(it)
            postViewModel.onMessageChanged()
        }
        mediaResolver.resolve(attachment)?.let {
            postViewModel.pickedMedia(persistentListOf(it))
        }
    }

    WatchAndLoadMyEmojiList(accountViewModel)

    PlatformBackHandler {
        accountViewModel.launchSigner {
            postViewModel.sendDraftSync()
            onUiThread { postViewModel.cancel() }
        }
        nav.popBack()
    }

    Scaffold(
        topBar = {
            SendingTopBar(
                titleRes = Res.string.private_message,
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
                onPost = {
                    // uses the accountViewModel scope to avoid cancelling this
                    // function when the postViewModel is released
                    accountViewModel.launchSigner {
                        postViewModel.sendPostSync()
                        postViewModel.room.value?.let {
                            nav.nav(routeToMessage(it, null, null, null, null, accountViewModel))
                        }
                    }
                    nav.popBack()
                },
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
            GroupDMScreenContent(postViewModel, accountViewModel, nav)
        }
    }
}

@Composable
fun GroupDMScreenContent(
    postViewModel: ChatNewMessageViewModel,
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
                SendDirectMessageTo(postViewModel, accountViewModel)

                MessageFieldRow(
                    postViewModel,
                    accountViewModel,
                    onContentReceived = { uri, mimeType ->
                        postViewModel.pickedMedia(persistentListOf(SelectedMedia(uri, mimeType)))
                    },
                )

                DisplayPreviews(postViewModel, accountViewModel, nav)

                if (postViewModel.wantsToMarkAsSensitive) {
                    ContentSensitivityExplainer(
                        description = postViewModel.contentWarningDescription,
                        onDescriptionChange = { postViewModel.contentWarningDescription = it },
                    )
                }

                if (postViewModel.wantsExpirationDate) {
                    ExpirationDatePicker(postViewModel)
                }

                if (postViewModel.wantsToAddGeoHash) {
                    GeoHashPostSection(postViewModel)
                }

                if (postViewModel.wantsForwardZapTo) {
                    ForwardZapTo(postViewModel, accountViewModel)
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

                postViewModel.uploadState?.let { uploading ->
                    uploading.multiOrchestrator?.let { selectedFiles ->
                        ImageVideoDescription(
                            selectedFiles,
                            accountViewModel.account.settings.defaultFileServer,
                            isUploading = uploading.mediaUploadTracker.isUploading,
                            onAdd = { _, server, _, _, _, _, _ ->
                                postViewModel.uploadAndHold(
                                    accountViewModel.toastManager::toast,
                                    accountViewModel.mediaUploader,
                                    onceUploaded = { },
                                )
                                accountViewModel.account.settings.changeDefaultFileServer(server)
                            },
                            onDelete = { postViewModel.uploadState?.deleteMediaToUpload(it) },
                            onCancel = uploading::reset,
                            accountViewModel = accountViewModel,
                        )
                    }
                }

                postViewModel.encryptedUploadErrorTitle?.let { title ->
                    EncryptedUploadErrorDialog(
                        title = title,
                        message = postViewModel.encryptedUploadErrorMessage ?: "",
                        onDismiss = postViewModel::dismissEncryptedUploadError,
                        onRetryWithoutEncryption = postViewModel::retryWithoutEncryption,
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

        val missingRelays by postViewModel.recipientsMissingDmRelays.collectAsStateWithLifecycle()
        if (missingRelays.isNotEmpty()) {
            RecipientMissingRelaysWarning(missingRelays, accountViewModel, nav)
        }

        BottomRowActions(postViewModel, accountViewModel)
    }
}

@Composable
fun MessageFieldRow(
    postViewModel: IMessageField,
    accountViewModel: AccountViewModel,
    requestFocus: Boolean = false,
    onContentReceived: ((MediaUri, String?) -> Unit)? = null,
) {
    Row {
        BaseUserPicture(
            accountViewModel.userProfile(),
            Size35dp,
            accountViewModel,
        )
        MessageField(Res.string.write_a_message, postViewModel, requestFocus, onContentReceived)
    }
}

@Composable
fun DisplayPreviews(
    postViewModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    PrepareLinkedImageMetadata(postViewModel.urlPreviews, postViewModel.iMetaAttachments, accountViewModel)

    val urlPreviews by postViewModel.urlPreviews.results.collectAsStateWithLifecycle(emptyList())

    if (postViewModel.uploadsWaitingToBeSent.isNotEmpty() || urlPreviews.isNotEmpty()) {
        Row(modifier = Modifier.padding(vertical = Size5dp)) {
            LazyRow(modifier = Modifier.height(100.dp)) {
                items(postViewModel.uploadsWaitingToBeSent) {
                    Box(modifier = Modifier.aspectRatio(1f).clip(shape = QuoteBorder)) {
                        ShowImageUploadGallery(it, accountViewModel)
                    }
                }

                items(urlPreviews) {
                    Box(modifier = Modifier.aspectRatio(1f).clip(shape = QuoteBorder)) {
                        PreviewUrl(it, accountViewModel, nav)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomRowActions(
    postViewModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
) {
    ComposerToolbar { _ ->
        val room by postViewModel.room.collectAsStateWithLifecycle()

        if (room != null) {
            SelectFromGallery(
                isUploading = postViewModel.isUploadingImage,
                enabled = !postViewModel.isUploadingFile,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier,
            ) {
                postViewModel.pickedMedia(it)
            }

            SelectFromFiles(
                isUploading = postViewModel.isUploadingFile,
                enabled = !postViewModel.isUploadingImage,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier,
            ) {
                postViewModel.pickedMedia(it)
            }
        } else {
            IconButton(
                onClick = { accountViewModel.toastManager.toast(Res.string.messages_cant_upload_title, Res.string.messages_cant_upload_explainer) },
            ) {
                Icon(
                    symbol = MaterialSymbols.AddPhotoAlternate,
                    contentDescription = stringRes(id = Res.string.upload_media),
                    modifier = Modifier.height(25.dp),
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            }
        }

        if (room != null) {
            TakePictureButton(
                onPictureTaken = { postViewModel.pickedMedia(it) },
            )
        } else if (canCaptureFromCamera) {
            IconButton(
                onClick = {
                    accountViewModel.toastManager.toast(Res.string.messages_cant_upload_title, Res.string.messages_cant_upload_explainer)
                },
            ) {
                Icon(
                    symbol = MaterialSymbols.CameraAlt,
                    contentDescription = stringRes(id = Res.string.take_a_picture),
                    modifier = Modifier.height(22.dp),
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            }
        }

        if (room != null) {
            TakeVideoButton(
                onVideoTaken = { postViewModel.pickedMedia(it) },
            )
        } else if (canCaptureFromCamera) {
            IconButton(
                onClick = {
                    accountViewModel.toastManager.toast(Res.string.messages_cant_upload_title, Res.string.messages_cant_upload_explainer)
                },
            ) {
                Icon(
                    symbol = MaterialSymbols.Videocam,
                    contentDescription = stringRes(id = Res.string.record_a_video),
                    modifier = Modifier.height(22.dp),
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            }
        }

        // Like the camera, it waits for a room: a drawing goes out as a file message to its members.
        if (room != null) {
            DrawingButton(accountViewModel, enabled = !postViewModel.isUploadingImage && !postViewModel.isUploadingFile) { drawing, alt ->
                postViewModel.uploadDrawing(drawing, alt, accountViewModel.toastManager::toast)
            }
        } else {
            IconButton(
                onClick = {
                    accountViewModel.toastManager.toast(Res.string.messages_cant_upload_title, Res.string.messages_cant_upload_explainer)
                },
            ) {
                Icon(
                    symbol = MaterialSymbols.Draw,
                    contentDescription = stringRes(id = Res.string.drawing_open),
                    modifier = Modifier.height(22.dp),
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            }
        }

        ComposerPostOptionTools(postViewModel)
    }
}

@Composable
fun SendDirectMessageTo(
    postViewModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        launch {
            delay(200)
            focusRequester.requestFocus()
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

        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(Res.string.messages_new_message_subject),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            ThinPaddingTextField(
                state = postViewModel.subject,
                onTextChanged = { postViewModel.onSubjectChanged() },
                inputTransformation = MentionPreservingInputTransformation,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringRes(Res.string.messages_new_message_subject_caption),
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

@Composable
fun ShowImageUploadGallery(
    data: SuccessfulUploads,
    accountViewModel: AccountViewModel,
) {
    val isImage = data.result.mimeTypeBeforeEncryption?.startsWith("image/") == true || RichTextParser.isImageUrl(data.result.url)

    val cipher = data.cipher
    if (cipher != null) {
        accountViewModel.account.encryptionKeyCache.add(data.result.url, cipher, data.result.mimeTypeBeforeEncryption)
    }

    val content by remember(data) {
        mutableStateOf<BaseMediaContent>(
            if (cipher != null) {
                if (isImage) {
                    EncryptedMediaUrlImage(
                        url = data.result.url,
                        description = data.caption,
                        hash = data.result.hashBeforeEncryption,
                        blurhash =
                            data.result.fileHeader.blurHash
                                ?.blurhash,
                        dim = data.result.fileHeader.dim,
                        uri = null,
                        mimeType = data.result.mimeTypeBeforeEncryption,
                        encryptionAlgo = cipher.name(),
                        encryptionKey = cipher.keyBytes,
                        encryptionNonce = cipher.nonce,
                        thumbhash =
                            data.result.fileHeader.thumbHash
                                ?.thumbhash,
                        sizeBytes =
                            data.result.fileHeader.size
                                .toLong(),
                    )
                } else {
                    EncryptedMediaUrlVideo(
                        url = data.result.url,
                        description = data.caption,
                        hash = data.result.hashBeforeEncryption,
                        blurhash =
                            data.result.fileHeader.blurHash
                                ?.blurhash,
                        dim = data.result.fileHeader.dim,
                        uri = null,
                        mimeType = data.result.mimeTypeBeforeEncryption,
                        encryptionAlgo = cipher.name(),
                        encryptionKey = cipher.keyBytes,
                        encryptionNonce = cipher.nonce,
                        thumbhash =
                            data.result.fileHeader.thumbHash
                                ?.thumbhash,
                        sizeBytes =
                            data.result.fileHeader.size
                                .toLong(),
                    )
                }
            } else {
                if (isImage) {
                    MediaUrlImage(
                        url = data.result.url,
                        description = data.caption,
                        hash = data.result.fileHeader.hash,
                        blurhash =
                            data.result.fileHeader.blurHash
                                ?.blurhash,
                        dim = data.result.fileHeader.dim,
                        uri = null,
                        mimeType = data.result.mimeTypeBeforeEncryption,
                        thumbhash =
                            data.result.fileHeader.thumbHash
                                ?.thumbhash,
                    )
                } else {
                    MediaUrlVideo(
                        url = data.result.url,
                        description = data.caption,
                        hash = data.result.fileHeader.hash,
                        blurhash =
                            data.result.fileHeader.blurHash
                                ?.blurhash,
                        dim = data.result.fileHeader.dim,
                        uri = null,
                        mimeType = data.result.mimeTypeBeforeEncryption,
                        thumbhash =
                            data.result.fileHeader.thumbHash
                                ?.thumbhash,
                    )
                }
            },
        )
    }

    ZoomableContentView(
        content,
        persistentListOf(content),
        roundedCorner = false,
        contentScale = ContentScale.Crop,
        accountViewModel,
    )
}
