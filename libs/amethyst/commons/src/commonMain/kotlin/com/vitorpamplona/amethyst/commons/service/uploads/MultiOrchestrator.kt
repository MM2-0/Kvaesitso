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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

@Stable
class MultiOrchestrator(
    uris: List<SelectedMedia>,
) {
    // Snapshot state so a removal recomposes the gallery that draws it; as a plain var the
    // Remove on a failed upload dropped the item from the upload but left it on screen.
    private var list: List<SelectedMediaProcessing> by mutableStateOf(uris.map { SelectedMediaProcessing(it) })

    /** [keptEncrypted] are the files [upload] did not send again because [uploadEncrypted] already had. */
    @Stable
    class Result(
        val allGood: Boolean,
        val successful: List<UploadingState.Finished>,
        val errors: List<UploadingState.Error>,
        val keptEncrypted: List<EncryptedUpload> = emptyList(),
    )

    fun first() = list.first()

    fun hasVideo() = list.any { it.isVideo() }

    fun hasGif() = list.any { it.media.isGif() }

    fun hasCompressible() = list.any { it.media.isCompressible() }

    /** A still image the platform re-encodes; [hasCompressible] also counts videos. */
    fun hasCompressibleImage() = list.any { it.media.isImage() == true && it.media.isCompressible() }

    /** The video the slider label describes. */
    fun firstVideo(): SelectedMedia? = list.firstOrNull { it.isVideo() }?.media

    /**
     * Every picked video, in order, for the HDR hint. A new list each call, of the same
     * [SelectedMedia] instances, so a caller can key on it and only a real add or removal counts.
     */
    fun videos(): List<SelectedMedia> = list.filter { it.isVideo() }.map { it.media }

    private fun SelectedMediaProcessing.isVideo() = media.mimeType?.startsWith("video", ignoreCase = true) == true

    fun hasNonMedia() = list.any { it.media.isNotMedia() }

    /**
     * Uploads every file in plaintext. With [keepEncrypted], a file the last [uploadEncrypted] sent,
     * under the same settings, is not sent again: it comes back in [Result.keptEncrypted], still
     * sealed, so a retry without encryption uploads only the files that failed.
     */
    suspend fun upload(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: CompressorQuality,
        server: ServerName,
        account: Account,
        uploader: MediaUploader,
        useH265: Boolean = false,
        stripMetadata: Boolean = true,
        onStrippingFailed: suspend () -> Boolean = { true },
        convertGifToMp4: Boolean = false,
        forcedSigner: NostrSigner? = null,
        keepEncrypted: Boolean = false,
    ): Result {
        val settings = UploadSettings(mediaQuality, server, useH265, stripMetadata, convertGifToMp4)
        val kept = if (keepEncrypted) list.mapNotNull { item -> item.reusableEncrypted(settings)?.let { item to it } }.toMap() else emptyMap()
        val toSend = list.filter { it !in kept }
        coroutineScope {
            val jobs =
                toSend.map { item ->
                    // Sent again in plaintext, so its encrypted blob is no longer the one in use.
                    item.sentEncrypted = null
                    launch(Dispatchers.IO) {
                        item.orchestrator.upload(
                            item.media.uri,
                            item.media.mimeType,
                            alt,
                            contentWarningReason,
                            mediaQuality,
                            server,
                            account,
                            uploader,
                            useH265,
                            stripMetadata,
                            onStrippingFailed,
                            convertGifToMp4 = convertGifToMp4,
                            forcedSigner = forcedSigner,
                        )
                    }
                }

            jobs.joinAll()
        }

        // A file removed from the gallery while the upload ran is left out.
        val sent = computeFinalResults(toSend.filter { it in list })
        return Result(sent.allGood, sent.successful, sent.errors, list.mapNotNull { kept[it] })
    }

    /** A file [uploadEncrypted] sent, with the cipher that sealed it: the receiver needs that cipher's key and nonce. */
    @Stable
    class EncryptedUpload(
        val finished: UploadingState.Finished,
        val cipher: AESGCM,
    )

    @Stable
    class EncryptedResult(
        val allGood: Boolean,
        val successful: List<EncryptedUpload>,
        val errors: List<UploadingState.Error>,
    )

    /**
     * What shapes the blob a file becomes; a file sent under other settings is sent again. The alt
     * text and content warning are not here: a chat applies them to the message, not the blob.
     */
    internal data class UploadSettings(
        val mediaQuality: CompressorQuality,
        val server: ServerName,
        val useH265: Boolean,
        val stripMetadata: Boolean,
        val convertGifToMp4: Boolean,
    )

    internal class SentEncrypted(
        val settings: UploadSettings,
        val upload: EncryptedUpload,
    )

    /** What [uploadEncrypted] sent for this file under [settings], shown as finished again if something reset its bar. */
    private fun SelectedMediaProcessing.reusableEncrypted(settings: UploadSettings): EncryptedUpload? {
        val sent = sentEncrypted?.takeIf { it.settings == settings } ?: return null
        if (orchestrator.progressState.value !== sent.upload.finished) orchestrator.updateState(1.0, sent.upload.finished)
        return sent.upload
    }

    /**
     * Uploads every file encrypted under its own cipher from [newCipher]. One cipher cannot seal two
     * files: its nonce is fixed, so a second file under the same key and nonce would leak the XOR of
     * both plaintexts (and AESGCM throws on its second encrypt). A file an earlier call already
     * sent under the same settings is not sent again, so a retry uploads only the files that failed
     * and leaves no orphaned blobs behind.
     */
    suspend fun uploadEncrypted(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: CompressorQuality,
        newCipher: () -> AESGCM,
        server: ServerName,
        account: Account,
        uploader: MediaUploader,
        useH265: Boolean = false,
        stripMetadata: Boolean = true,
        onStrippingFailed: suspend () -> Boolean = { true },
        convertGifToMp4: Boolean = false,
        forcedSigner: NostrSigner? = null,
    ): EncryptedResult {
        val settings = UploadSettings(mediaQuality, server, useH265, stripMetadata, convertGifToMp4)
        val items = list
        val reused = items.mapNotNull { item -> item.reusableEncrypted(settings)?.let { item to it } }.toMap()
        // Made before anything goes up, so a cipher that cannot be made fails the batch up front.
        val pending = items.filter { it !in reused }.map { it to newCipher() }
        val sent =
            coroutineScope {
                pending
                    .map { (item, cipher) ->
                        async(Dispatchers.IO) {
                            item.orchestrator.uploadEncrypted(
                                item.media.uri,
                                item.media.mimeType,
                                alt,
                                contentWarningReason,
                                mediaQuality,
                                cipher,
                                server,
                                account,
                                uploader,
                                useH265,
                                stripMetadata,
                                onStrippingFailed,
                                convertGifToMp4 = convertGifToMp4,
                                forcedSigner = forcedSigner,
                            )
                        }
                    }.awaitAll()
            }

        val uploads = HashMap(reused)
        val errors = HashMap<SelectedMediaProcessing, UploadingState.Error>()
        pending.forEachIndexed { index, (item, cipher) ->
            when (val state = sent[index]) {
                is UploadingState.Finished -> {
                    uploads[item] = EncryptedUpload(state, cipher).also { item.sentEncrypted = SentEncrypted(settings, it) }
                }

                is UploadingState.Error -> {
                    errors[item] = state
                    item.sentEncrypted = null
                }
            }
        }

        // A file removed from the gallery while the upload ran is left out.
        val remaining = items.filter { it in list }
        val successful = remaining.mapNotNull { uploads[it] }

        return EncryptedResult(successful.size == remaining.size, successful, remaining.mapNotNull { errors[it] })
    }

    private fun computeFinalResults(items: List<SelectedMediaProcessing>): Result {
        val resultsByState =
            items.map {
                it.orchestrator.progressState.value
            }

        val finished = resultsByState.filterIsInstance<UploadingState.Finished>()
        val errors = resultsByState.filterIsInstance<UploadingState.Error>()

        return Result(finished.size == items.size, finished, errors)
    }

    fun remove(selected: SelectedMediaProcessing) {
        list = list.filter { it != selected }
    }

    fun size() = list.size

    fun get(index: Int) = list[index]
}
