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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Authors a CORD-02 §6 encrypted community image and returns the [ImagePointer] to seal in the
 * community metadata — the exact inverse of [rememberConcordImageModel]'s read path, and the same
 * scheme Armada's `encryptImageBlob` + Blossom upload uses in `concord-v2/lib/image.ts`.
 *
 * Rather than hand-roll the upload, this drives the **standard** [UploadOrchestrator.uploadEncrypted]
 * pipeline that DM/chat encrypted media uses, so a community icon gets the same treatment as any
 * other attachment: image compression ([CompressorQuality]), EXIF/metadata stripping, and the
 * account's configured Blossom server. The only Concord-specific parts are the fresh [AESGCM] cipher
 * (whose key/nonce we keep to build the pointer) and mapping the orchestrator's result — the
 * ciphertext `url` plus the *plaintext* `hashBeforeEncryption` — into the [ImagePointer] shape.
 */
class ConcordImageUploader(
    private val account: Account,
) {
    /**
     * Compresses, strips, AES-256-GCM-encrypts and uploads the picked [media], returning its pointer.
     *
     * Runs on [Dispatchers.IO]: compression, encryption and the upload all block, and callers launch
     * this from a Compose `rememberCoroutineScope()`, which is Main-dispatched.
     */
    suspend fun uploadEncrypted(
        media: SelectedMedia,
        uploader: MediaUploader,
    ): ImagePointer =
        withContext(Dispatchers.IO) {
            // Fresh random key + nonce per image; we hold onto them to build the pointer below since the
            // orchestrator only surfaces the ciphertext URL, not the cipher it was handed.
            val cipher = AESGCM()

            val finalState =
                UploadOrchestrator().uploadEncrypted(
                    uri = media.uri,
                    mimeType = media.mimeType,
                    alt = null,
                    contentWarningReason = null,
                    compressionQuality = CompressorQuality.MEDIUM,
                    encrypt = cipher,
                    server = resolveBlossomServer(),
                    account = account,
                    uploader = uploader,
                )

            val result =
                when (finalState) {
                    is UploadingState.Finished -> finalState.result
                    is UploadingState.Error -> throw IllegalStateException(loadStringRes(finalState.errorResource, *finalState.params))
                }

            val server =
                result as? UploadOrchestrator.OrchestratorResult.ServerResult
                    ?: throw IllegalStateException("Encrypted community image upload did not return a server URL")

            ImagePointer(
                url = server.url,
                key = cipher.keyBytes.toHexKey(),
                nonce = cipher.nonce.toHexKey(),
                // hash is over the *plaintext* (post-compression/strip) bytes — the read path verifies it
                // after decrypting, so it must match what was actually encrypted, not the original file.
                hash = server.hashBeforeEncryption ?: throw IllegalStateException("Upload pipeline did not report the plaintext hash"),
            )
        }

    /** The account's first configured Blossom server, wrapped as a [ServerName], else the default. */
    private fun resolveBlossomServer(): ServerName {
        val configured =
            account.blossomServers
                .getBlossomServersList()
                ?.servers()
                ?.firstOrNull()
        return if (configured != null) {
            ServerName(configured, configured, ServerType.Blossom)
        } else {
            DEFAULT_MEDIA_SERVERS.first { it.type == ServerType.Blossom }
        }
    }
}
