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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.send

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.client.MarmotGroupIconUpload
import com.vitorpamplona.marmot.client.MarmotGroupImageUploadPlan
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal

/**
 * Encrypts a picked image in the scheme its group's avatar carrier uses and uploads
 * the ciphertext to Blossom, signing the upload with that scheme's upload key (so any
 * admin holding it can later replace or delete the blob). A current-profile group
 * uses `marmot.group.blossom.image.v1` (raw keys, media type bound into the AAD); a
 * legacy group keeps MIP-01 v2. [MarmotGroupImageUploadPlan] picks; mixing them up
 * makes an avatar no other client can open.
 *
 * Reuses [UploadOrchestrator.uploadEncrypted] for compression, metadata stripping,
 * upload, and re-download verification — the same pipeline as MIP-04 message media.
 */
class MarmotGroupIconUploader(
    val account: Account,
) {
    suspend fun upload(
        nostrGroupId: HexKey,
        uri: MediaUri,
        mimeType: String?,
        server: ServerName,
        uploader: MediaUploader,
    ): MarmotGroupIconUpload {
        // Compress/downscale up front — avatars don't need full resolution, and a smaller
        // blob is cheaper for every member to fetch. The current-profile scheme binds the
        // media type into the AEAD, so the plan is made for the type AFTER compression.
        val compressed = UploadOrchestrator().compressIfNeeded(uri, mimeType, CompressorQuality.MEDIUM, uploader)
        val uploadMime = compressed.contentType ?: mimeType ?: DEFAULT_MIME

        // Inside the try, so a failed plan (not a member any more) still discards the compressed file.
        try {
            val manager = account.marmotManager ?: throw IllegalStateException("Marmot is not initialized")
            val plan = manager.planGroupImageUpload(nostrGroupId, uploadMime)
            val uploadSigner = NostrSignerInternal(KeyPair(privKey = plan.uploadSecretKey))

            val state =
                UploadOrchestrator().uploadEncrypted(
                    uri = compressed.uri,
                    mimeType = uploadMime,
                    alt = null,
                    contentWarningReason = null,
                    compressionQuality = CompressorQuality.UNCOMPRESSED,
                    encrypt = plan.cipher,
                    server = server,
                    account = account,
                    uploader = uploader,
                    stripMetadata = true,
                    forcedSigner = uploadSigner,
                )

            val serverResult = (state as? UploadingState.Finished)?.result as? UploadOrchestrator.OrchestratorResult.ServerResult
            if (serverResult != null) {
                val hash =
                    serverResult.uploadedHash
                        ?: throw IllegalStateException("Blossom server did not return a content hash for the group icon")
                return plan.uploadFor(hash)
            }

            val message =
                if (state is UploadingState.Error) {
                    loadStringRes(state.errorResource, *state.params)
                } else {
                    "Group icon upload failed"
                }
            throw IllegalStateException(message)
        } finally {
            // Delete the intermediate compressed temp file (compress returns the original
            // URI unchanged when it skips compression, so only delete a distinct temp).
            if (compressed.uri != uri) uploader.discardTempFile(compressed.uri)
        }
    }

    companion object {
        private const val DEFAULT_MIME = "image/jpeg"
    }
}

/**
 * Encrypts and uploads a picked image as a group avatar (canonical `marmot-group-image-v1`
 * scheme). The returned handle is later committed through
 * [AccountViewModel.updateMarmotGroupMetadata] as `MarmotGroupIconChange.Set`; uploading is
 * separate from that commit so the slow Blossom upload can show its own progress first.
 */
suspend fun AccountViewModel.uploadMarmotGroupIcon(
    nostrGroupId: HexKey,
    uri: MediaUri,
    mimeType: String?,
    uploader: MediaUploader,
): MarmotGroupIconUpload = MarmotGroupIconUploader(account).upload(nostrGroupId, uri, mimeType, account.settings.defaultFileServer, uploader)
