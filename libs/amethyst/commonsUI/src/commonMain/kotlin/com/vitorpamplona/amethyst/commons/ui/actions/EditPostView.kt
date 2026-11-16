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
package com.vitorpamplona.amethyst.commons.ui.actions

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.drawing.ui.DrawingButton
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_bitcoin_invoice
import com.vitorpamplona.amethyst.commons.resources.cancel_bitcoin_invoice
import com.vitorpamplona.amethyst.commons.resources.lightning_create_and_add_invoice
import com.vitorpamplona.amethyst.commons.resources.lightning_invoice
import com.vitorpamplona.amethyst.commons.resources.what_s_on_your_mind
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.ui.actions.UrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectFromFiles
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.commons.ui.components.BechLink
import com.vitorpamplona.amethyst.commons.ui.components.OutlinedThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.components.edgeToEdgeDialogProperties
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.AddLnInvoiceButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.InvoiceRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadUrlPreview
import com.vitorpamplona.amethyst.commons.ui.note.platform.VideoView
import com.vitorpamplona.amethyst.commons.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPostView(
    onClose: () -> Unit,
    edit: Note,
    versionLookingAt: Note?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: EditPostViewModel = viewModel()
    postViewModel.init(accountViewModel)

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        postViewModel.load(edit, versionLookingAt)
    }

    StrippingFailureDialog(postViewModel.strippingFailureConfirmation)

    Dialog(
        onDismissRequest = { onClose() },
        properties = edgeToEdgeDialogProperties(dismissOnClickOutside = false),
    ) {
        Scaffold(
            topBar = {
                PostingTopBar(
                    isActive = postViewModel::canPost,
                    onPost = {
                        postViewModel.sendPost()
                        scope.launch {
                            delay(100)
                            onClose()
                        }
                    },
                    onCancel = {
                        postViewModel.cancel()
                        scope.launch {
                            delay(100)
                            onClose()
                        }
                    },
                )
            },
        ) { pad ->
            Surface(
                modifier =
                    Modifier
                        .padding(
                            start = Size10dp,
                            top = pad.calculateTopPadding(),
                            end = Size10dp,
                            bottom = pad.calculateBottomPadding(),
                        ).fillMaxSize(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .imePaddingSafe()
                                .weight(1f),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(scrollState, reverseScrolling = true),
                            ) {
                                postViewModel.editedFromNote?.let {
                                    Row(Modifier.heightIn(max = 200.dp)) {
                                        NoteCompose(
                                            baseNote = it,
                                            modifier = MaterialTheme.colorScheme.replyModifier,
                                            isQuotedNote = true,
                                            unPackReply = ReplyRenderType.NONE,
                                            makeItShort = true,
                                            quotesLeft = 1,
                                            accountViewModel = accountViewModel,
                                            nav = nav,
                                        )
                                        Spacer(modifier = StdVertSpacer)
                                    }
                                }

                                MessageField(postViewModel)

                                val myUrlPreview = postViewModel.urlPreview
                                if (myUrlPreview != null) {
                                    Row(modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp)) {
                                        if (RichTextParser.isValidURL(myUrlPreview)) {
                                            when {
                                                RichTextParser.isImageUrl(myUrlPreview) -> {
                                                    AsyncImage(
                                                        model = myUrlPreview,
                                                        contentDescription = myUrlPreview,
                                                        contentScale = ContentScale.FillWidth,
                                                        modifier =
                                                            Modifier
                                                                .padding(top = 4.dp)
                                                                .fillMaxWidth()
                                                                .clip(shape = QuoteBorder)
                                                                .border(
                                                                    1.dp,
                                                                    MaterialTheme.colorScheme.subtleBorder,
                                                                    QuoteBorder,
                                                                ),
                                                    )
                                                }

                                                RichTextParser.isVideoUrl(myUrlPreview) -> {
                                                    VideoView(
                                                        myUrlPreview,
                                                        mimeType = null,
                                                        roundedCorner = true,
                                                        contentScale = ContentScale.FillWidth,
                                                        accountViewModel = accountViewModel,
                                                    )
                                                }

                                                else -> {
                                                    LoadUrlPreview(myUrlPreview, myUrlPreview, null, accountViewModel)
                                                }
                                            }
                                        } else if (RichTextParser.startsWithNIP19Scheme(myUrlPreview)) {
                                            val bgColor = MaterialTheme.colorScheme.background
                                            val backgroundColor = remember { mutableStateOf(bgColor) }

                                            BechLink(
                                                word = myUrlPreview,
                                                canPreview = true,
                                                quotesLeft = 1,
                                                backgroundColor = backgroundColor,
                                                accountViewModel = accountViewModel,
                                                nav = nav,
                                            )
                                        } else if (RichTextParser.isUrlWithoutScheme(myUrlPreview)) {
                                            LoadUrlPreview("https://$myUrlPreview", myUrlPreview, null, accountViewModel)
                                        }
                                    }
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
                                                postViewModel.upload(alt, sensitiveContent, mediaQuality, false, server, accountViewModel.toastManager::toast, accountViewModel.mediaUploader, useH265, stripMetadata, convertGifToMp4)
                                                accountViewModel.account.settings.changeDefaultFileServer(server)
                                            },
                                            onDelete = postViewModel::deleteMediaToUpload,
                                            onCancel = { postViewModel.multiOrchestrator = null },
                                            accountViewModel = accountViewModel,
                                        )
                                    }
                                }

                                if (postViewModel.wantsInvoice) {
                                    val user = postViewModel.account.userProfile()
                                    val info by observeUserInfo(user, accountViewModel)
                                    val lud16 = info?.info?.lnAddress()

                                    if (lud16 != null) {
                                        Row(
                                            verticalAlignment = CenterVertically,
                                            modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                                        ) {
                                            InvoiceRequest(
                                                lud16,
                                                user,
                                                accountViewModel,
                                                stringRes(id = Res.string.lightning_invoice),
                                                stringRes(id = Res.string.lightning_create_and_add_invoice),
                                                onNewInvoice = {
                                                    postViewModel.message.setTextAndPlaceCursorAtEnd(
                                                        postViewModel.message.text.toString() + "\n\n" + it,
                                                    )
                                                    postViewModel.wantsInvoice = false
                                                },
                                                onError = { title, message -> accountViewModel.toastManager.toast(title, message) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        postViewModel.userSuggestions?.let {
                            ShowUserSuggestionList(
                                it,
                                postViewModel::autocompleteWithUser,
                                accountViewModel,
                                modifier = SuggestionListDefaultHeightPage,
                            )
                        }

                        BottomRowActions(postViewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomRowActions(postViewModel: EditPostViewModel) {
    val scrollState = rememberScrollState()

    Row(
        modifier =
            Modifier
                .horizontalScroll(scrollState)
                .fillMaxWidth()
                .height(50.dp),
        verticalAlignment = CenterVertically,
    ) {
        SelectFromGallery(
            isUploading = postViewModel.isUploadingImage,
            enabled = !postViewModel.isUploadingFile,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier,
        ) {
            postViewModel.selectImage(it)
        }

        SelectFromFiles(
            isUploading = postViewModel.isUploadingFile,
            enabled = !postViewModel.isUploadingImage,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier,
        ) {
            postViewModel.selectImage(it)
        }

        DrawingButton(postViewModel.accountViewModel, enabled = !postViewModel.isUploadingImage && !postViewModel.isUploadingFile) { drawing, alt ->
            postViewModel.uploadDrawing(drawing, alt, postViewModel.accountViewModel.toastManager::toast)
        }

        if (postViewModel.canAddInvoice) {
            AddLnInvoiceButton(postViewModel.wantsInvoice) {
                postViewModel.wantsInvoice = !postViewModel.wantsInvoice
            }
        }
    }
}

@Composable
private fun MessageField(postViewModel: EditPostViewModel) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        launch {
            delay(200)
            focusRequester.requestFocus()
        }
    }

    OutlinedThinPaddingTextField(
        state = postViewModel.message,
        onTextChanged = { postViewModel.onMessageChanged() },
        inputTransformation = MentionPreservingInputTransformation,
        spellCheck = true,
        outputTransformation = rememberUrlUserTagOutputTransformation(),
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                ).focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        keyboardController?.show()
                    }
                },
        placeholder = {
            Text(
                text = stringRes(Res.string.what_s_on_your_mind),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
            ),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
    )
}

@Composable
private fun AddLnInvoiceButton(
    isLnInvoiceActive: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = { onClick() },
    ) {
        if (!isLnInvoiceActive) {
            Icon(
                symbol = MaterialSymbols.CurrencyBitcoin,
                contentDescription = stringRes(id = Res.string.add_bitcoin_invoice),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            Icon(
                symbol = MaterialSymbols.CurrencyBitcoin,
                contentDescription = stringRes(id = Res.string.cancel_bitcoin_invoice),
                modifier = Modifier.size(20.dp),
                tint = BitcoinOrange,
            )
        }
    }
}
