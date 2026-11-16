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
package com.vitorpamplona.amethyst.commons.service.uploads

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.notices.UploadNotice
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.crypto.SymmetricCipher
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * One file's upload, as the composer sees it: its progress and final state. The work itself is
 * the [MediaUploader]'s, which reports each stage back through [updateState].
 */
class UploadOrchestrator {
    val progress = MutableStateFlow(0.0)
    val progressState = MutableStateFlow<UploadingState>(UploadingState.Ready)

    val isUploading =
        progressState.map {
            progressState.value !is UploadingState.Ready && progressState.value !is UploadingState.Error && progressState.value !is UploadingState.Finished
        }

    fun error(
        error: UploadError,
        vararg params: String,
    ) = UploadingState.Error(error, params).also { updateState(0.0, it) }

    fun finish(result: OrchestratorResult) =
        UploadingState
            .Finished(result)
            .also { updateState(1.0, it) }

    fun updateState(
        newProgress: Double,
        newState: UploadingState,
    ) {
        progress.value = newProgress
        progressState.value = newState
    }

    /**
     * Maps the bytes an upload body has sent onto [from]..[to] of the progress bar, reporting
     * only whole-percent changes. A retried request starts again from zero, and so does the bar.
     */
    fun uploadProgress(
        from: Double,
        to: Double,
    ): (bytesWritten: Long, totalBytes: Long) -> Unit {
        var lastPercent = -1L
        return { written, total ->
            val percent = if (total > 0) written * 100 / total else 0
            if (percent != lastPercent) {
                lastPercent = percent
                updateState(from + (to - from) * percent / 100, UploadingState.Uploading)
            }
        }
    }

    suspend fun compressIfNeeded(
        uri: MediaUri,
        mimeType: String?,
        compressionQuality: CompressorQuality,
        uploader: MediaUploader,
        useH265: Boolean = false,
        convertGifToMp4: Boolean = false,
    ): MediaCompressorResult = uploader.compressIfNeeded(this, uri, mimeType, compressionQuality, useH265, convertGifToMp4)

    suspend fun upload(
        uri: MediaUri,
        mimeType: String?,
        alt: String?,
        contentWarningReason: String?,
        compressionQuality: CompressorQuality,
        server: ServerName,
        account: Account,
        uploader: MediaUploader,
        useH265: Boolean = false,
        stripMetadata: Boolean = true,
        onStrippingFailed: suspend () -> Boolean = { true },
        convertGifToMp4: Boolean = false,
        forcedSigner: NostrSigner? = null,
    ): UploadingState.UploadingFinalState = settle(uploader.upload(this, uri, mimeType, alt, contentWarningReason, compressionQuality, server, account, useH265, stripMetadata, onStrippingFailed, convertGifToMp4, forcedSigner))

    suspend fun uploadEncrypted(
        uri: MediaUri,
        mimeType: String?,
        alt: String?,
        contentWarningReason: String?,
        compressionQuality: CompressorQuality,
        encrypt: SymmetricCipher,
        server: ServerName,
        account: Account,
        uploader: MediaUploader,
        useH265: Boolean = false,
        stripMetadata: Boolean = true,
        onStrippingFailed: suspend () -> Boolean = { true },
        convertGifToMp4: Boolean = false,
        forcedSigner: NostrSigner? = null,
    ): UploadingState.UploadingFinalState = settle(uploader.uploadEncrypted(this, uri, mimeType, alt, contentWarningReason, compressionQuality, encrypt, server, account, useH265, stripMetadata, onStrippingFailed, convertGifToMp4, forcedSigner))

    /**
     * Shows [state] in [progressState] when the uploader returned it without reporting it (Android's
     * Blossom path does, for a failure it catches), so the bar does not stay on Uploading and
     * [MultiOrchestrator] sees the error.
     */
    private fun settle(state: UploadingState.UploadingFinalState): UploadingState.UploadingFinalState {
        if (progressState.value !== state) updateState(if (state is UploadingState.Finished) 1.0 else 0.0, state)
        return state
    }

    sealed class OrchestratorResult {
        class ServerResult(
            val fileHeader: FileHeader,
            val url: String,
            val magnet: String?,
            val uploadedHash: String?,
            val mimeTypeBeforeEncryption: String?,
            val hashBeforeEncryption: String?,
            /** What the user should know about where this upload went (see [UploadNotice]). */
            val notices: List<UploadNotice> = emptyList(),
        ) : OrchestratorResult() {
            fun withNotice(notice: UploadNotice) = ServerResult(fileHeader, url, magnet, uploadedHash, mimeTypeBeforeEncryption, hashBeforeEncryption, notices + notice)
        }
    }
}
