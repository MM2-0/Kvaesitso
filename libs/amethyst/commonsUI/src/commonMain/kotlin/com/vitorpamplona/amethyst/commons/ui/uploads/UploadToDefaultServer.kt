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
package com.vitorpamplona.amethyst.commons.ui.uploads

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_upload
import com.vitorpamplona.amethyst.commons.resources.metadata_strip_failed_title
import com.vitorpamplona.amethyst.commons.resources.metadata_strip_failed_upload_cancelled
import com.vitorpamplona.amethyst.commons.resources.server_did_not_provide_a_url_after_uploading
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.UploadError
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import kotlinx.coroutines.CancellationException

/**
 * Compresses, strips (when [stripMetadata] and the account ask for it) and uploads one picked file
 * to the account's default media server, returning the hosted URL. Every failure, a
 * metadata-stripping one included, is reported through [onError] with a user-facing title and
 * message, and yields null.
 *
 * For single-image fields (avatars, banners, pack and cover images) that only need a URL back.
 */
suspend fun uploadToDefaultServer(
    media: SelectedMedia,
    account: Account,
    uploader: MediaUploader,
    onError: (title: String, message: String) -> Unit,
    quality: CompressorQuality = CompressorQuality.MEDIUM,
    stripMetadata: Boolean = true,
): String? = uploadToDefaultServerWithMetadata(media, account, uploader, onError, quality, stripMetadata)?.url

/**
 * Same as [uploadToDefaultServer], but returns the whole upload result, whose file header
 * (hash, size, dimensions, blurhash) fills a NIP-92 `imeta` for the uploaded URL.
 */
suspend fun uploadToDefaultServerWithMetadata(
    media: SelectedMedia,
    account: Account,
    uploader: MediaUploader,
    onError: (title: String, message: String) -> Unit,
    quality: CompressorQuality = CompressorQuality.MEDIUM,
    stripMetadata: Boolean = true,
): UploadOrchestrator.OrchestratorResult.ServerResult? {
    val state =
        try {
            UploadOrchestrator().upload(
                uri = media.uri,
                mimeType = media.mimeType,
                alt = null,
                contentWarningReason = null,
                compressionQuality = quality,
                server = account.settings.defaultFileServer,
                account = account,
                uploader = uploader,
                stripMetadata = stripMetadata && account.settings.stripLocationOnUpload,
                onStrippingFailed = { false },
            )
        } catch (_: SignerExceptions.ReadOnlyException) {
            onError(loadStringRes(Res.string.failed_to_upload_media_no_details), loadStringRes(Res.string.login_with_a_private_key_to_be_able_to_upload))
            return null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            onError(loadStringRes(Res.string.failed_to_upload_media_no_details), e.message ?: e::class.simpleName ?: "")
            return null
        }

    return when (state) {
        is UploadingState.Finished -> {
            val uploaded = state.result
            if (uploaded is UploadOrchestrator.OrchestratorResult.ServerResult) {
                uploaded
            } else {
                onError(loadStringRes(Res.string.failed_to_upload_media_no_details), loadStringRes(Res.string.server_did_not_provide_a_url_after_uploading))
                null
            }
        }

        is UploadingState.Error -> {
            when (state.error) {
                // The only cancel on this path is a metadata strip that failed: say so, since the
                // user turned stripping on and would otherwise not know why nothing uploaded.
                UploadError.UPLOAD_CANCELLED ->
                    onError(loadStringRes(Res.string.metadata_strip_failed_title), loadStringRes(Res.string.metadata_strip_failed_upload_cancelled))
                UploadError.AVIF_METADATA_STRIP_FAILED ->
                    onError(loadStringRes(Res.string.metadata_strip_failed_title), loadStringRes(state.errorResource, *state.params))
                else ->
                    onError(loadStringRes(Res.string.failed_to_upload_media_no_details), loadStringRes(state.errorResource, *state.params))
            }
            null
        }
    }
}
