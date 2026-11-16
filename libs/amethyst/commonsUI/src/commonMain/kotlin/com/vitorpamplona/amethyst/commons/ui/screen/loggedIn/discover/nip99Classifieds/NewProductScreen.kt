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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip99Classifieds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.description
import com.vitorpamplona.amethyst.commons.resources.lightning_create_and_add_invoice
import com.vitorpamplona.amethyst.commons.resources.lightning_invoice
import com.vitorpamplona.amethyst.commons.resources.new_product
import com.vitorpamplona.amethyst.commons.resources.zapraiser
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.rememberSharedMediaResolver
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.PublishingTopBar
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.creators.contentWarning.ContentSensitivityExplainer
import com.vitorpamplona.amethyst.commons.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.commons.ui.note.creators.expiration.ExpirationDatePicker
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.InvoiceRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.GeoHashPostSection
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.MessageField
import com.vitorpamplona.amethyst.commons.ui.note.creators.previews.DisplayPreviews
import com.vitorpamplona.amethyst.commons.ui.note.creators.secretEmoji.SecretEmojiRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerMediaTools
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerPostOptionTools
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerToolbar
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerToolsFormStartPadding
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.composerBody
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapraiser.ZapRaiserRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.ForwardZapTo
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.FlowPreview

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NewProductScreen(
    message: String? = null,
    attachment: String? = null,
    quoteId: HexKey? = null,
    draftId: HexKey? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: NewProductViewModel = rememberViewModel { NewProductViewModel() }
    postViewModel.init(accountViewModel)
    val mediaResolver = rememberSharedMediaResolver()

    LaunchedEffect(postViewModel, accountViewModel) {
        postViewModel.reloadRelaySet()
        draftId?.let { LocalCache.getNoteIfExists(it) }?.let {
            postViewModel.editFromDraft(it)
        }
        quoteId?.let { LocalCache.getNoteIfExists(it) }?.let {
            postViewModel.quote(it)
        }
        message?.ifBlank { null }?.let {
            postViewModel.message.setTextAndPlaceCursorAtEnd(it)
            postViewModel.onMessageChanged()
        }
        mediaResolver.resolve(attachment)?.let {
            postViewModel.selectImage(persistentListOf(it))
        }
    }

    NewProductScreen(
        postViewModel,
        accountViewModel,
        nav,
    )
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NewProductScreen(
    postViewModel: NewProductViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchAndLoadMyEmojiList(accountViewModel)

    StrippingFailureDialog(postViewModel.strippingFailureConfirmation)

    PlatformBackHandler {
        accountViewModel.launchSigner {
            postViewModel.sendDraftSync()
            onUiThread { postViewModel.cancel() }
        }
        nav.popBack()
    }

    Scaffold(
        topBar = {
            PublishingTopBar(
                titleRes = Res.string.new_product,
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
                    accountViewModel.launchSigner {
                        postViewModel.sendPostSync()
                        nav.popBack()
                    }
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
            NewProductBody(
                postViewModel,
                accountViewModel,
                nav,
            )
        }
    }
}

@Composable
private fun NewProductBody(
    postViewModel: NewProductViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scrollState = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Size10dp)
                .then(composerBody())
                .verticalScroll(scrollState, reverseScrolling = true),
        ) {
            Row(
                verticalAlignment = CenterVertically,
                modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
            ) {
                SellProduct(postViewModel = postViewModel)
            }

            Row(
                modifier = Modifier.padding(vertical = Size10dp),
            ) {
                BaseUserPicture(
                    accountViewModel.userProfile(),
                    Size35dp,
                    accountViewModel = accountViewModel,
                )
                MessageField(
                    Res.string.description,
                    postViewModel,
                    onContentReceived = { uri, mimeType ->
                        postViewModel.selectImage(
                            persistentListOf(
                                SelectedMedia(uri, mimeType),
                            ),
                        )
                    },
                )
            }

            DisplayPreviews(postViewModel.urlPreviews, postViewModel.iMetaDescription, accountViewModel, nav)

            if (postViewModel.wantsToMarkAsSensitive) {
                Row(
                    verticalAlignment = CenterVertically,
                    modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                ) {
                    ContentSensitivityExplainer(
                        description = postViewModel.contentWarningDescription,
                        onDescriptionChange = { postViewModel.contentWarningDescription = it },
                    )
                }
            }

            if (postViewModel.wantsExpirationDate) {
                Row(
                    verticalAlignment = CenterVertically,
                    modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                ) {
                    ExpirationDatePicker(postViewModel)
                }
            }

            if (postViewModel.wantsToAddGeoHash) {
                Row(
                    verticalAlignment = CenterVertically,
                    modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                ) {
                    GeoHashPostSection(postViewModel)
                }
            }

            if (postViewModel.wantsForwardZapTo) {
                Row(
                    verticalAlignment = CenterVertically,
                    modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                ) {
                    ForwardZapTo(postViewModel, accountViewModel)
                }
            }

            postViewModel.multiOrchestrator?.let {
                Row(
                    verticalAlignment = CenterVertically,
                    modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                ) {
                    ImageVideoDescription(
                        uris = it,
                        defaultServer = accountViewModel.account.settings.defaultFileServer,
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
                postViewModel.lnAddress()?.let { lud16 ->
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        InvoiceRequest(
                            lud16,
                            accountViewModel.account.userProfile(),
                            accountViewModel,
                            stringRes(id = Res.string.lightning_invoice),
                            stringRes(id = Res.string.lightning_create_and_add_invoice),
                            onNewInvoice = {
                                postViewModel.insertAtCursor(it)
                                postViewModel.wantsInvoice = false
                            },
                            onError = { title, message -> accountViewModel.toastManager.toast(title, message) },
                        )
                    }
                }
            }

            if (postViewModel.wantsSecretEmoji) {
                Row(
                    verticalAlignment = CenterVertically,
                    modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        SecretEmojiRequest {
                            postViewModel.insertAtCursor(it)
                            postViewModel.wantsSecretEmoji = false
                        }
                    }
                }
            }

            if (postViewModel.wantsZapRaiser && postViewModel.hasLnAddress()) {
                Row(
                    verticalAlignment = CenterVertically,
                    modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                ) {
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
                modifier = SuggestionListDefaultHeightPage,
            )
        }

        postViewModel.emojiSuggestions?.let {
            ShowEmojiSuggestionList(
                it,
                postViewModel::autocompleteWithEmoji,
                postViewModel::autocompleteWithEmojiUrl,
                modifier = SuggestionListDefaultHeightPage,
            )
        }

        BottomRowActions(postViewModel)
    }
}

@Composable
private fun BottomRowActions(postViewModel: NewProductViewModel) {
    ComposerToolbar(startPadding = ComposerToolsFormStartPadding) { _ ->
        ComposerMediaTools(postViewModel)
        ComposerPostOptionTools(postViewModel)
    }
}
