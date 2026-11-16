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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.upload

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.avif_metadata_strip_failed
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_encrypted_media_message
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_encrypted_media_title
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.crypto.aes.AESGCM

class ChatFileUploader(
    val account: Account,
) {
    // ------
    // NIP 17
    // ------

    suspend fun justUploadNIP17(
        viewState: ChatFileUploadState,
        onError: (title: String, message: String) -> Unit,
        onEncryptedUploadError: (title: String, message: String) -> Unit,
        uploader: MediaUploader,
        onStrippingFailed: suspend () -> Boolean = { true },
        onceUploaded: suspend (List<SuccessfulUploads>) -> Unit,
    ) {
        val orchestrator = viewState.multiOrchestrator ?: return
        viewState.mediaUploadTracker.startUpload(orchestrator.hasNonMedia())

        if (viewState.encryptFiles) {
            val results =
                orchestrator.uploadEncrypted(
                    viewState.caption,
                    viewState.contentWarningReason,
                    CompressorQuality.fromSlider(viewState.mediaQualitySlider),
                    // A fresh key and nonce per file: an AESGCM seals only one message.
                    { AESGCM() },
                    viewState.selectedServer,
                    account,
                    uploader,
                    stripMetadata = viewState.stripMetadata,
                    onStrippingFailed = onStrippingFailed,
                )

            if (results.allGood) {
                val list =
                    results.successful.mapNotNull { upload ->
                        val uploaded = upload.finished.result
                        if (uploaded is UploadOrchestrator.OrchestratorResult.ServerResult) {
                            SuccessfulUploads(uploaded, viewState.caption, viewState.contentWarningReason, upload.cipher)
                        } else {
                            null
                        }
                    }

                onceUploaded(list)
                viewState.reset()
            } else {
                val errorMessages = results.errors.map { loadStringRes(it.errorResource, *it.params) }.distinct()
                val allAvifMetadata = results.errors.isNotEmpty() && results.errors.all { it.errorResource == Res.string.avif_metadata_strip_failed }

                if (allAvifMetadata) {
                    // AVIF strip refusal is a pre-encryption check — "retry without encryption" wouldn't help.
                    onError(
                        loadStringRes(Res.string.failed_to_upload_media_no_details),
                        errorMessages.joinToString(".\n"),
                    )
                } else {
                    onEncryptedUploadError(
                        loadStringRes(Res.string.failed_to_upload_encrypted_media_title),
                        loadStringRes(Res.string.failed_to_upload_encrypted_media_message) + "\n\n" + errorMessages.joinToString(".\n"),
                    )
                }
            }
        } else {
            justUploadNIP17Unencrypted(viewState, onError, uploader, onStrippingFailed, onceUploaded)
        }

        viewState.mediaUploadTracker.finishUpload()
    }

    /**
     * Uploads the picked files in plaintext. Files that already went up encrypted (an encrypted
     * attempt that partly failed, then a retry without encryption) are not sent again: they are
     * handed to [onceUploaded] with their ciphers, and only the rest go up in plaintext. Only a
     * NIP-17 chat can encrypt, so a plaintext-only chat never has any.
     */
    suspend fun justUploadNIP17Unencrypted(
        viewState: ChatFileUploadState,
        onError: (title: String, message: String) -> Unit,
        uploader: MediaUploader,
        onStrippingFailed: suspend () -> Boolean = { true },
        onceUploaded: suspend (List<SuccessfulUploads>) -> Unit,
    ) {
        val orchestrator = viewState.multiOrchestrator ?: return
        viewState.mediaUploadTracker.startUpload(orchestrator.hasNonMedia())

        val results =
            orchestrator.upload(
                viewState.caption,
                viewState.contentWarningReason,
                CompressorQuality.fromSlider(viewState.mediaQualitySlider),
                viewState.selectedServer,
                account,
                uploader,
                stripMetadata = viewState.stripMetadata,
                onStrippingFailed = onStrippingFailed,
                keepEncrypted = true,
            )

        if (results.allGood) {
            val sealed =
                results.keptEncrypted.mapNotNull { upload ->
                    val uploaded = upload.finished.result
                    if (uploaded is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        SuccessfulUploads(uploaded, viewState.caption, viewState.contentWarningReason, upload.cipher)
                    } else {
                        null
                    }
                }
            val list =
                results.successful.mapNotNull { state ->
                    val uploaded = state.result
                    if (uploaded is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        SuccessfulUploads(uploaded, viewState.caption, viewState.contentWarningReason, null)
                    } else {
                        null
                    }
                }

            onceUploaded(sealed + list)
            viewState.reset()
        } else {
            val errorMessages = results.errors.map { loadStringRes(it.errorResource, *it.params) }.distinct()

            onError(loadStringRes(Res.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
        }

        viewState.mediaUploadTracker.finishUpload()
    }

    // ------
    // NIP 04
    // ------

    suspend fun justUploadNIP04(
        viewState: ChatFileUploadState,
        onError: (title: String, message: String) -> Unit,
        uploader: MediaUploader,
        onStrippingFailed: suspend () -> Boolean = { true },
        onceUploaded: suspend (List<SuccessfulUploads>) -> Unit,
    ) {
        val orchestrator = viewState.multiOrchestrator ?: return
        viewState.mediaUploadTracker.startUpload(orchestrator.hasNonMedia())

        val results =
            orchestrator.upload(
                viewState.caption,
                viewState.contentWarningReason,
                CompressorQuality.fromSlider(viewState.mediaQualitySlider),
                viewState.selectedServer,
                account,
                uploader,
                stripMetadata = viewState.stripMetadata,
                onStrippingFailed = onStrippingFailed,
            )

        if (results.allGood) {
            val list =
                results.successful.mapNotNull { state ->
                    val uploaded = state.result
                    if (uploaded is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        SuccessfulUploads(uploaded, viewState.caption, viewState.contentWarningReason, null)
                    } else {
                        null
                    }
                }

            onceUploaded(list)
            viewState.reset()
        } else {
            val errorMessages = results.errors.map { loadStringRes(it.errorResource, *it.params) }.distinct()

            onError(loadStringRes(Res.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
        }

        viewState.mediaUploadTracker.finishUpload()
    }
}
