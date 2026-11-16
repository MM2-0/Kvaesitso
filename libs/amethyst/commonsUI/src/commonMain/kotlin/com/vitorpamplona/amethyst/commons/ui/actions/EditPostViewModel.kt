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

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.drawing.RenderedDrawing
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.service.upload.MediaUploadTracker
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.UserSuggestionAnchor
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.text.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadDrawing
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Stable
open class EditPostViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    var editedFromNote: Note? = null

    var subject by mutableStateOf(TextFieldValue(""))

    val message = TextFieldState()
    var urlPreview by mutableStateOf<String?>(null)
    val mediaUploadTracker = MediaUploadTracker()
    val isUploadingImage: Boolean get() = mediaUploadTracker.isUploadingImage
    val isUploadingFile: Boolean get() = mediaUploadTracker.isUploadingFile

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

    // Stripping failure dialog
    val strippingFailureConfirmation = SuspendableConfirmation()

    // Invoices
    var canAddInvoice by mutableStateOf(false)
    var wantsInvoice by mutableStateOf(false)

    open fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    open fun load(
        edit: Note,
        versionLookingAt: Note?,
    ) {
        canAddInvoice = accountViewModel.userProfile().lnAddress() != null
        multiOrchestrator = null

        message.setTextAndPlaceCursorAtEnd(versionLookingAt?.event?.content ?: edit.event?.content ?: "")
        urlPreview = findUrlInMessage()

        this.editedFromNote = edit

        this.userSuggestions?.reset()
        this.userSuggestions = UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
    }

    fun sendPost() {
        accountViewModel.launchSigner(::innerSendPost)
    }

    suspend fun innerSendPost() {
        val notify =
            if (editedFromNote?.author?.pubkeyHex == account.userProfile().pubkeyHex) {
                null
            } else {
                // notifies if it is not the logged in user
                editedFromNote?.author?.pubkeyHex
            }

        account.sendEdit(
            message = message.text.toString(),
            originalNote = editedFromNote!!,
            notify = notify,
            summary = subject.text.ifBlank { null },
        )

        onUiThread { cancel() }
    }

    open fun updateSubject(it: TextFieldValue) {
        subject = it
    }

    /**
     * Uploads a hand drawing ([uploadDrawing]) and puts its GIF in the edited text. An edit carries
     * no tags (they are never replaced), so there is no `imeta` for the drawing's still preview.
     */
    fun uploadDrawing(
        drawing: RenderedDrawing,
        alt: String?,
        onError: (title: String, message: String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val uploaded = uploadDrawing(drawing, alt, account, accountViewModel.mediaUploader, mediaUploadTracker, uploadStill = false, onError) ?: return@launch
            onUiThread { message.insertUrlAtCursor(uploaded.link) }
            urlPreview = findUrlInMessage()
        }
    }

    fun upload(
        alt: String?,
        sensitiveContent: Boolean,
        mediaQuality: Int,
        isPrivate: Boolean = false,
        server: ServerName,
        onError: (String, String) -> Unit,
        uploader: MediaUploader,
        useH265: Boolean,
        stripMetadata: Boolean,
        convertGifToMp4: Boolean,
    ) {
        uploadUnsafe(alt, sensitiveContent, mediaQuality, isPrivate, server, onError, uploader, useH265, stripMetadata, convertGifToMp4)
    }

    fun uploadUnsafe(
        alt: String?,
        sensitiveContent: Boolean,
        mediaQuality: Int,
        isPrivate: Boolean = false,
        server: ServerName,
        onError: (String, String) -> Unit,
        uploader: MediaUploader,
        useH265: Boolean,
        stripMetadata: Boolean,
        convertGifToMp4: Boolean,
    ) {
        accountViewModel.launchSigner {
            val myAccount = account
            val myMultiOrchestrator = multiOrchestrator ?: return@launchSigner

            mediaUploadTracker.startUpload(myMultiOrchestrator.hasNonMedia())

            try {
                val results =
                    myMultiOrchestrator.upload(
                        alt,
                        if (sensitiveContent) "" else null,
                        CompressorQuality.fromSlider(mediaQuality),
                        server,
                        myAccount,
                        uploader,
                        useH265,
                        stripMetadata,
                        onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                        convertGifToMp4 = convertGifToMp4,
                    )

                if (results.allGood) {
                    val urls =
                        // An edit carries no tags (they are never replaced), so only the links go in.
                        results.successful.mapNotNull { (it.result as? UploadOrchestrator.OrchestratorResult.ServerResult)?.url }

                    onUiThread { message.insertUrlAtCursor(urls.joinToString(" ")) }
                    urlPreview = findUrlInMessage()

                    this@EditPostViewModel.multiOrchestrator = null
                } else {
                    val errorMessages = results.errors.map { loadStringRes(it.errorResource, *it.params) }.distinct()

                    onError(loadStringRes(Res.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
                }
            } finally {
                mediaUploadTracker.finishUpload()
            }
        }
    }

    open fun cancel() {
        message.setTextAndPlaceCursorAtEnd("")
        subject = TextFieldValue("")

        editedFromNote = null

        multiOrchestrator = null
        urlPreview = null
        mediaUploadTracker.finishUpload()

        wantsInvoice = false

        userSuggestions?.reset()
        userSuggestionsMainMessage = null
    }

    open fun findUrlInMessage(): String? =
        message.text.toString().split('\n').firstNotNullOfOrNull { paragraph ->
            paragraph.split(' ').firstOrNull { word: String ->
                RichTextParser.isValidURL(word) || RichTextParser.isUrlWithoutScheme(word)
            }
        }

    open fun onMessageChanged() {
        urlPreview = findUrlInMessage()

        if (message.selection.collapsed) {
            val lastWord = message.currentWord()
            if (lastWord.startsWith("@")) {
                userSuggestionsMainMessage = UserSuggestionAnchor.MAIN_MESSAGE
                userSuggestions?.processCurrentWord(lastWord)
            } else {
                userSuggestionsMainMessage = null
                userSuggestions?.reset()
            }
        }
    }

    open fun autocompleteWithUser(item: User) {
        userSuggestions?.let { userSuggestions ->
            val lastWord = message.currentWord()
            userSuggestions.replaceCurrentWord(message, lastWord, item)

            userSuggestionsMainMessage = null
            userSuggestions.reset()
        }
    }

    fun canPost() = message.text.isNotBlank() && !mediaUploadTracker.isUploading && !wantsInvoice && multiOrchestrator == null

    fun selectImage(uris: ImmutableList<SelectedMedia>) {
        multiOrchestrator = MultiOrchestrator(uris)
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        val orchestrator = multiOrchestrator ?: return
        orchestrator.remove(selected)
        // An empty orchestrator still renders the gallery, which reads its first item.
        if (orchestrator.size() == 0) multiOrchestrator = null
    }
}
