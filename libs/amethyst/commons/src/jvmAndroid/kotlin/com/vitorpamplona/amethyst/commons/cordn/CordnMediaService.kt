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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.cordn.appEncryptedMedia.CordnBlobUpload
import com.vitorpamplona.cordn.appEncryptedMedia.CordnEncryptedMedia
import com.vitorpamplona.cordn.appEncryptedMedia.CordnMediaEncryption
import com.vitorpamplona.cordn.appEncryptedMedia.CordnMediaTag
import com.vitorpamplona.mls.group.MlsGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sending cordn attachments. Fetching one is the media layer's (`EncryptedBlobInterceptor`).
 *
 * ## What each party sees
 *
 * | | sees |
 * | --- | --- |
 * | the blob host | opaque bytes, their size, and who uploaded them |
 * | the coordinator | a sealed payload; not the URL, not the type, not the name |
 * | the group | everything, because the key rides in the sealed descriptor |
 *
 * Getting that split right is most of this file. The `imeta` descriptor —
 * URL, MIME type, filename, plaintext hash, nonce — rides **inside** the MLS
 * envelope, so it is as private as the message it belongs to.
 *
 * ## Three deliberate choices on the upload
 *
 * 1. **`application/octet-stream`, always.** The real MIME type goes in the
 *    encrypted descriptor. Declaring `image/jpeg` to the host would tell it
 *    what kind of file this is for no benefit to anyone.
 * 2. **No `alt` text, no content warning.** Both are plaintext on a Blossom
 *    upload. An alt string describing a private photo is the photo's caption,
 *    handed to a server that was supposed to see nothing.
 * 3. **`/upload`, never `/media`.** The `/media` endpoint asks the server to
 *    re-encode. Re-encoding ciphertext destroys it, and an account with
 *    "optimize uploads" on would otherwise silently break every attachment.
 *
 * Blossom addresses a blob by the SHA-256 of the bytes it stores — the
 * ciphertext. The `imeta` descriptor carries the hash of the **plaintext**.
 * Two different hashes on purpose: the host needs one to name the blob, the
 * group needs the other to know it got the file that was sent, and neither
 * can be derived from the other.
 */
class CordnMediaService(
    private val account: Account,
    private val httpClients: IRoleBasedHttpClientBuilder,
) {
    /**
     * Encrypts [bytes] under a fresh per-file key and uploads the ciphertext.
     *
     * @return the `imeta` tag to put on the message, or null when the account
     *   has no Blossom server configured — there is nowhere to put a file and
     *   saying so beats a failure deeper in.
     */
    suspend fun upload(
        group: MlsGroup,
        bytes: ByteArray,
        mimeType: String,
        filename: String,
        /** The host to put it on. Defaults to the account's, which is what the
         *  upload dialog's server spinner starts on. */
        serverBaseUrl: String = account.settings.defaultFileServer.baseUrl,
        /**
         * Display hints for the `imeta`. All optional and none authenticated:
         * they are the spec's "passed through unchanged" fields, so they cost
         * nothing to omit and are worth a great deal to include — without
         * [dimensions] the bubble has no aspect ratio to reserve and the list
         * jumps when the picture lands, and without [waveform] a voice note the
         * recorder already measured comes back as bare bars.
         */
        dimensions: String? = null,
        blurhash: String? = null,
        alt: String? = null,
        waveform: List<Float>? = null,
    ): Array<String>? =
        withContext(Dispatchers.IO) {
            val server = serverBaseUrl.ifBlank { return@withContext null }

            // Under the group's current epoch key, derived from its exporter and never sent
            // (encrypted-media.md §3.1): cordn-kmp's rule, so any cordn client opens it.
            val sealed = CordnMediaEncryption.sealFor(group, bytes, mimeType, filename)
            CordnMediaTag.build(
                media = sealed,
                url = put(sealed, server),
                dimensions = dimensions,
                blurhash = blurhash,
                alt = alt,
                waveform = waveform,
            )
        }

    /**
     * Uploads the ciphertext and returns the URL the host gave it.
     *
     * Every privacy decision is in [CordnBlobUpload]; this forwards it. Read
     * that KDoc before changing an argument here — the wrong constant does not
     * fail, it just tells a server something.
     */
    private suspend fun put(
        sealed: CordnEncryptedMedia,
        server: String,
    ): String {
        val blob = CordnBlobUpload.of(sealed)
        // BlossomClient.upload is the BUD-02 `/upload` endpoint, never `/media`.
        check(!blob.useMediaEndpoint) { "cordn blobs are ciphertext and must never be re-encoded" }

        // The auth event is public too, so it names the blob by its hash, as the app's uploader
        // always has: "Uploading <hash>".
        val auth = account.createBlossomUploadAuth(blob.hash, blob.length, "Uploading ${blob.baseFileName}").toAuthorizationHeader()
        val result =
            BlossomClient(httpClients.okHttpClientForUploads(server))
                .upload(bytes = blob.bytes, contentType = blob.contentType, serverBaseUrl = server, authHeader = auth)

        return result.url ?: throw IllegalStateException("the blob server returned no URL")
    }
}
