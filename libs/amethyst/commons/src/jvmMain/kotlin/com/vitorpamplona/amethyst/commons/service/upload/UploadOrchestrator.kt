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
package com.vitorpamplona.amethyst.commons.service.upload

import com.vitorpamplona.amethyst.commons.notices.UploadNotice
import com.vitorpamplona.amethyst.commons.service.upload.ImageReencoder.ReencodeResult
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

data class UploadResult(
    val blossom: BlossomUploadResult,
    val metadata: MediaMetadata,
    /** Where the upload went when it was not simply the chosen server taking these bytes. */
    val notices: List<UploadNotice> = emptyList(),
)

data class EncryptedUploadResult(
    val blossom: BlossomUploadResult,
    val metadata: MediaMetadata,
    val encryptedHash: String,
    val encryptedSize: Int,
    /** Where the upload went when it was not the chosen server. */
    val notices: List<UploadNotice> = emptyList(),
)

class UploadOrchestrator(
    private val client: BlossomClient = BlossomClient(),
    /**
     * The client for a server: a fallback server goes out on its own route (the app may send one
     * server through Tor and another not), not on the selected server's. Defaults to [client].
     */
    private val clientFor: (serverBaseUrl: String) -> BlossomClient = { client },
) {
    /**
     * Upload an image file to a Blossom server, optionally re-encoding
     * + downscaling first, optionally stripping EXIF.
     *
     * Flow:
     * 1. [ImageReencoder] decides:
     *    - `Reencoded(temp)` → upload the temp; EXIF is naturally
     *      absent because the re-encode wiped it.
     *    - `PassThrough` (animated GIF / WebP / SVG) → upload the
     *      *original*. If [stripExif] is on and source is JPEG, run
     *      [MediaCompressor.stripExif] first.
     * 2. [MediaMetadataReader] computes the upload hash on the final
     *    bytes (whatever bytes actually leave the machine).
     * 3. Blossom auth + upload (unchanged).
     * 4. All temp files created along the way are deleted in a
     *    finally block under [NonCancellable] so cleanup survives
     *    coroutine cancellation.
     *
     * @throws CompressionException when the reencoder refuses or fails
     *   (caller surfaces the fail-loud confirm dialog).
     */
    suspend fun upload(
        file: File,
        alt: String?,
        serverBaseUrl: String,
        signer: NostrSigner,
        stripExif: Boolean = true,
        quality: ImageCompressionTarget? = null,
        bypassReencode: Boolean = false,
        preCompressed: File? = null,
        // Tried in order after [serverBaseUrl] fails; see [uploadToFirstAccepting].
        fallbackServerBaseUrls: List<String> = emptyList(),
        // The bytes sent, per attempt: a retry or the next server starts again from zero.
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    ): UploadResult {
        var reencodedTemp: File? = null
        var strippedTemp: File? = null
        try {
            // 1. Re-encode (or pass-through). Four branches:
            //    - preCompressed != null: the preview dialog already
            //      ran ImageReencoder and is handing off ownership of
            //      its temp file. Skip reencode, skip stripExif (the
            //      reencode already wiped EXIF), just upload + clean.
            //    - bypassReencode == true: user opted to send the
            //      original after a reencode failure or refusal.
            //    - quality == null: caller did not opt into the
            //      desktop image-compression feature. Treat the file
            //      as a pass-through — preserves old orchestrator
            //      semantics for the CLI, Android, and any non-
            //      image upload (voice memos, video, DM files).
            //    - else: run the normal reencode path with the
            //      caller-requested preset.
            val reencode =
                when {
                    preCompressed != null -> ReencodeResult.Reencoded(preCompressed)
                    bypassReencode || quality == null ->
                        ReencodeResult.PassThrough(ImageReencoder.PassReason.BypassByUser)
                    else -> ImageReencoder.reencode(file, quality)
                }
            val afterReencode =
                when (reencode) {
                    is ReencodeResult.Reencoded -> reencode.file.also { reencodedTemp = it }
                    is ReencodeResult.PassThrough -> file
                }

            // 2. Strip EXIF only when we're shipping the original
            //    (re-encode already wipes EXIF naturally).
            val finalFile =
                if (stripExif && reencode is ReencodeResult.PassThrough) {
                    val stripped = MediaCompressor.stripExif(afterReencode)
                    if (stripped != afterReencode) strippedTemp = stripped
                    stripped
                } else {
                    afterReencode
                }

            // 3. Compute metadata on the bytes that will actually
            //    leave this machine.
            val metadata = MediaMetadataReader.compute(finalFile)

            // 4. Create auth header.
            val authHeader =
                BlossomAuth.createUploadAuth(
                    hash = metadata.sha256,
                    size = metadata.size,
                    alt = alt ?: "Uploading ${file.name}",
                    signer = signer,
                )

            // 5. Upload. A server holding an identical image names it instead of taking this one:
            //    the note links to that copy, so its header describes that copy's bytes.
            var identicalCopy: MediaMetadata? = null
            val accepted =
                uploadToFirstAccepting(serverBaseUrl, fallbackServerBaseUrls) { server ->
                    val blossom = clientFor(server)
                    try {
                        blossom.uploadOrHeld(metadata.sha256, metadata.size, metadata.mimeType, server) {
                            blossom.upload(
                                file = finalFile,
                                contentType = metadata.mimeType,
                                serverBaseUrl = server,
                                authHeader = authHeader,
                                onProgress = onProgress,
                            )
                        }
                    } catch (e: BlossomIdenticalMediaException) {
                        val copy = identicalCopy(blossom, e, metadata) ?: throw e
                        identicalCopy = copy
                        BlossomClient.heldDescriptor(copy.sha256, copy.size, copy.mimeType, server, BlossomClient.extensionFor(copy.mimeType))
                    }
                }

            return UploadResult(blossom = accepted.result, metadata = identicalCopy ?: metadata, notices = listOfNotNull(accepted.notice(serverBaseUrl)))
        } finally {
            // Eager cleanup of every intermediate. NonCancellable so a
            // user-cancelled upload still cleans up its temps.
            withContext(NonCancellable) {
                strippedTemp?.deleteOrWarn("UploadOrchestrator", "stripped temp")
                reencodedTemp?.deleteOrWarn("UploadOrchestrator", "reencoded temp")
            }
        }
    }

    /**
     * Upload a file encrypted with AES-GCM for NIP-17 DM file sharing.
     * Computes pre-encryption metadata (dimensions, blurhash), encrypts bytes,
     * then uploads the encrypted blob to Blossom.
     */
    suspend fun uploadEncrypted(
        file: File,
        cipher: AESGCM,
        serverBaseUrl: String,
        signer: NostrSigner,
        // Defaults preserve the prior behaviour exactly (no reencode, no EXIF
        // strip → encrypt raw bytes) for existing Android/CLI callers. Only
        // callers that opt in (e.g. Desktop DMs) change what leaves the machine.
        stripExif: Boolean = false,
        quality: ImageCompressionTarget? = null,
        // When false (default) the encrypted blob is uploaded as opaque
        // application/octet-stream so the server can't learn the media type.
        // When true it's uploaded with the real media type — less private, but
        // required by strict Blossom servers that reject octet-stream (HTTP 415).
        declareRealMimeType: Boolean = false,
        // Tried in order after [serverBaseUrl] fails; see [uploadToFirstAccepting].
        fallbackServerBaseUrls: List<String> = emptyList(),
        // The bytes sent, per attempt: a retry or the next server starts again from zero.
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    ): EncryptedUploadResult {
        var reencodedTemp: File? = null
        var strippedTemp: File? = null
        try {
            // 1. Re-encode (or pass-through) BEFORE encrypting, mirroring [upload].
            //    quality == null → pass-through (preserves prior DM behaviour, and
            //    the CLI/Android callers). ImageReencoder itself passes animated
            //    GIFs through byte-identical, so GIF DMs stay animated.
            val reencode =
                when {
                    quality == null -> ReencodeResult.PassThrough(ImageReencoder.PassReason.BypassByUser)
                    else -> ImageReencoder.reencode(file, quality)
                }
            val afterReencode =
                when (reencode) {
                    is ReencodeResult.Reencoded -> reencode.file.also { reencodedTemp = it }
                    is ReencodeResult.PassThrough -> file
                }

            // 2. Strip EXIF only when shipping the original (re-encode wipes it).
            val finalFile =
                if (stripExif && reencode is ReencodeResult.PassThrough) {
                    val stripped = MediaCompressor.stripExif(afterReencode)
                    if (stripped != afterReencode) strippedTemp = stripped
                    stripped
                } else {
                    afterReencode
                }

            // 3. Compute pre-encryption metadata on the bytes that get encrypted
            //    (dimensions, blurhash, mime, originalHash of the final plaintext).
            val metadata = MediaMetadataReader.compute(finalFile)

            // 4. Read final bytes and encrypt
            val plaintext = finalFile.readBytes()
            val encrypted = cipher.encrypt(plaintext)

            // 5. Compute SHA256 of ENCRYPTED blob (not plaintext)
            val encryptedHash = sha256(encrypted).toHexKey()
            val encryptedSize = encrypted.size

            // 6. Create Blossom auth with encrypted hash and size
            val authHeader =
                BlossomAuth.createUploadAuth(
                    hash = encryptedHash,
                    size = encryptedSize.toLong(),
                    alt = "Encrypted upload",
                    signer = signer,
                )

            // 7. Upload the encrypted blob. Default: declare
            //    application/octet-stream so the server can't learn whether a
            //    private attachment is an image, video, voice note, etc. — this
            //    needs a server that accepts opaque blobs (e.g. nostr.download).
            //    Callers may opt into [declareRealMimeType] to declare the real
            //    type instead, so strict servers that reject octet-stream (HTTP
            //    415) also work, at the cost of leaking the media category.
            val uploadContentType =
                if (declareRealMimeType) metadata.mimeType else "application/octet-stream"
            // The same ciphertext goes to every server tried, so the cipher and the
            // encrypted hash stay valid wherever it lands.
            val accepted =
                uploadToFirstAccepting(serverBaseUrl, fallbackServerBaseUrls) { server ->
                    val blossom = clientFor(server)
                    blossom.uploadOrHeld(encryptedHash, encryptedSize.toLong(), uploadContentType, server) {
                        blossom.upload(
                            bytes = encrypted,
                            contentType = uploadContentType,
                            serverBaseUrl = server,
                            authHeader = authHeader,
                            onProgress = onProgress,
                        )
                    }
                }

            return EncryptedUploadResult(
                blossom = accepted.result,
                metadata = metadata,
                encryptedHash = encryptedHash,
                encryptedSize = encryptedSize,
                notices = listOfNotNull(accepted.notice(serverBaseUrl)),
            )
        } finally {
            withContext(NonCancellable) {
                strippedTemp?.deleteOrWarn("UploadOrchestrator", "encrypted stripped temp")
                reencodedTemp?.deleteOrWarn("UploadOrchestrator", "encrypted reencoded temp")
            }
        }
    }

    /**
     * [serverBaseUrl] first, then each of [fallbacks] (skipping duplicates by domain) until
     * one accepts. Servers differ in what they take -- several answer an opaque encrypted
     * blob with 415, paid ones with 402 -- and the user can't see that in advance. The auth
     * header is not server-scoped, so the same one serves every attempt. When all fail, the
     * first server's error is thrown: that is the server the user chose.
     */
    private suspend fun uploadToFirstAccepting(
        serverBaseUrl: String,
        fallbacks: List<String>,
        upload: suspend (String) -> BlossomUploadResult,
    ): Accepted {
        val seen = mutableSetOf<String>()
        val order = (listOf(serverBaseUrl) + fallbacks).filter { seen.add(BlossomServerUrl.domain(it)) }
        var firstError: Exception? = null
        for (server in order) {
            try {
                return Accepted(upload(server), server, firstError)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (firstError == null) firstError = e
                Log.w("UploadOrchestrator") { "Upload to $server failed (${e.message}), trying the next server" }
            }
        }
        throw firstError!!
    }

    /** The server that took the upload, and the chosen server's refusal when it was not that one. */
    private class Accepted(
        val result: BlossomUploadResult,
        val server: String,
        val refusal: Exception?,
    ) {
        /** What to tell the user when [server] is not [chosen]: where it went, and why. */
        fun notice(chosen: String): UploadNotice? =
            refusal?.let {
                UploadNotice.UploadedToAnotherServer(
                    chosenServer = BlossomServerUrl.domain(chosen),
                    usedServer = BlossomServerUrl.domain(server),
                    reason = it.message,
                )
            }
    }

    /**
     * The metadata of the copy [refusal] names, when [blossom] serves it, it hashes to that name and
     * it has [local]'s dimensions, or null. The server says it is the same picture; the hash proves
     * the copy is a fixed file, and the size check catches a server naming some other image.
     */
    private suspend fun identicalCopy(
        blossom: BlossomClient,
        refusal: BlossomIdenticalMediaException,
        local: MediaMetadata,
    ): MediaMetadata? {
        val bytes = blossom.download(BlossomServerUrl.blob(refusal.server, refusal.existingHash)) ?: return null
        if (!sha256(bytes).toHexKey().equals(refusal.existingHash, ignoreCase = true)) {
            Log.w("UploadOrchestrator") { "${refusal.server} serves other bytes than its identical copy ${refusal.existingHash}" }
            return null
        }
        val copy = MediaMetadataReader.compute(bytes, local.mimeType)
        if (copy.width != local.width || copy.height != local.height) {
            Log.w("UploadOrchestrator") { "${refusal.server}'s identical copy is ${copy.width}x${copy.height}, not ${local.width}x${local.height}" }
            return null
        }
        return copy
    }

    companion object {
        /**
         * The bytes [upload] would send, for servers this class doesn't speak to (NIP-96):
         * re-encoded at [quality], or, when passed through, stripped of EXIF if [stripExif]. [block]
         * gets that file and its metadata; every temp is deleted afterwards, even on cancellation.
         */
        suspend fun <T> withPreparedFile(
            file: File,
            stripExif: Boolean,
            quality: ImageCompressionTarget?,
            block: suspend (prepared: File, metadata: MediaMetadata) -> T,
        ): T {
            var reencodedTemp: File? = null
            var strippedTemp: File? = null
            try {
                val reencode =
                    when {
                        quality == null -> ReencodeResult.PassThrough(ImageReencoder.PassReason.BypassByUser)
                        else -> ImageReencoder.reencode(file, quality)
                    }
                val afterReencode =
                    when (reencode) {
                        is ReencodeResult.Reencoded -> reencode.file.also { reencodedTemp = it }
                        is ReencodeResult.PassThrough -> file
                    }
                val finalFile =
                    if (stripExif && reencode is ReencodeResult.PassThrough) {
                        val stripped = MediaCompressor.stripExif(afterReencode)
                        if (stripped != afterReencode) strippedTemp = stripped
                        stripped
                    } else {
                        afterReencode
                    }
                return block(finalFile, MediaMetadataReader.compute(finalFile))
            } finally {
                withContext(NonCancellable) {
                    strippedTemp?.deleteOrWarn("UploadOrchestrator", "prepared stripped temp")
                    reencodedTemp?.deleteOrWarn("UploadOrchestrator", "prepared reencoded temp")
                }
            }
        }
    }
}
