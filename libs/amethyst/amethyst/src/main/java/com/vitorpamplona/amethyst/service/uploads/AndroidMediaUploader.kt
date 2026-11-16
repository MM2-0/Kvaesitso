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
package com.vitorpamplona.amethyst.service.uploads

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.model.mediaServers.blossomUploadOrder
import com.vitorpamplona.amethyst.commons.notices.UploadNotice
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.BlossomIdenticalMediaException
import com.vitorpamplona.amethyst.commons.service.upload.BlossomPaymentException
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCompressionPreview
import com.vitorpamplona.amethyst.commons.service.uploads.ImageDownloader
import com.vitorpamplona.amethyst.commons.service.uploads.ImageFileStats
import com.vitorpamplona.amethyst.commons.service.uploads.MediaCompressorResult
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.StripDecision
import com.vitorpamplona.amethyst.commons.service.uploads.UploadError
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState.UploadingFinalState
import com.vitorpamplona.amethyst.commons.service.uploads.VideoCompressionSupport
import com.vitorpamplona.amethyst.commons.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoHdrInfo
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoInfo
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.nip96.upload
import com.vitorpamplona.crypto.SymmetricCipher
import com.vitorpamplona.quartz.buzz.media.BuzzMediaSanitizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.File
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android's [MediaUploader]: compresses with LightCompressor/Compressor, strips metadata, encrypts,
 * and uploads over the app's upload-role OkHttp clients. Reads files through the application
 * [appContext]'s content resolver, so it needs no Activity. Every entry point moves to
 * [Dispatchers.IO] itself: hashing, bitmap decoding and stripping block, and shared callers may
 * launch from a Main-bound scope.
 */
class AndroidMediaUploader(
    private val appContext: Context,
) : MediaUploader {
    override val videoCompression: VideoCompressionSupport get() = VideoCompressionSupport.Supported

    override suspend fun upload(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        alt: String?,
        contentWarningReason: String?,
        compressionQuality: CompressorQuality,
        server: ServerName,
        account: Account,
        useH265: Boolean,
        stripMetadata: Boolean,
        onStrippingFailed: suspend () -> Boolean,
        convertGifToMp4: Boolean,
        forcedSigner: NostrSigner?,
    ): UploadingFinalState = withContext(Dispatchers.IO) { Job(progress).upload(uri, mimeType, alt, contentWarningReason, compressionQuality, server, account, appContext, useH265, stripMetadata, onStrippingFailed, convertGifToMp4, forcedSigner) }

    override suspend fun uploadEncrypted(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        alt: String?,
        contentWarningReason: String?,
        compressionQuality: CompressorQuality,
        encrypt: SymmetricCipher,
        server: ServerName,
        account: Account,
        useH265: Boolean,
        stripMetadata: Boolean,
        onStrippingFailed: suspend () -> Boolean,
        convertGifToMp4: Boolean,
        forcedSigner: NostrSigner?,
    ): UploadingFinalState = withContext(Dispatchers.IO) { Job(progress).uploadEncrypted(uri, mimeType, alt, contentWarningReason, compressionQuality, encrypt, server, account, appContext, useH265, stripMetadata, onStrippingFailed, convertGifToMp4, forcedSigner) }

    override suspend fun compressIfNeeded(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        compressionQuality: CompressorQuality,
        useH265: Boolean,
        convertGifToMp4: Boolean,
    ): MediaCompressorResult = withContext(Dispatchers.IO) { Job(progress).compressIfNeeded(uri, mimeType, compressionQuality, appContext, useH265, convertGifToMp4) }

    /** One upload's run: the pipeline, reporting into [progress]. */
    private class Job(
        val progress: UploadOrchestrator,
    ) {
        fun error(
            error: UploadError,
            vararg params: String,
        ) = progress.error(error, *params)

        fun finish(result: UploadOrchestrator.OrchestratorResult) = progress.finish(result)

        fun updateState(
            newProgress: Double,
            newState: UploadingState,
        ) = progress.updateState(newProgress, newState)

        fun uploadProgress(
            from: Double,
            to: Double,
        ) = progress.uploadProgress(from, to)

        private suspend fun uploadNIP96(
            fileUri: Uri,
            contentType: String?,
            size: Long?,
            alt: String?,
            contentWarningReason: String?,
            serverBaseUrl: String,
            contentTypeForResult: String?,
            originalHash: String?,
            account: Account,
            forcedSigner: NostrSigner?,
            context: Context,
        ): UploadingFinalState {
            updateState(0.2, UploadingState.Uploading)
            return try {
                val result =
                    Nip96Uploader().upload(
                        uri = fileUri,
                        contentType = contentType,
                        size = size,
                        alt = alt,
                        sensitiveContent = contentWarningReason,
                        serverBaseUrl = serverBaseUrl,
                        okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                        onProgress = { percent: Float ->
                            updateState(0.4 + (0.2 * percent), UploadingState.Uploading)
                        },
                        onUploadProgress = uploadProgress(from = 0.2, to = 0.4),
                        httpAuth =
                            if (forcedSigner != null) {
                                { url, method, body -> forcedSigner.sign(HTTPAuthorizationEvent.build(url, method, body)) }
                            } else {
                                account::createHTTPAuthorization
                            },
                        context = context,
                    )

                verifyHeader(
                    uploadResult = result,
                    localContentType = contentType,
                    originalContentType = contentTypeForResult,
                    originalHash = originalHash,
                    okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                )
            } catch (_: SignerExceptions.ReadOnlyException) {
                error(UploadError.LOGIN_WITH_PRIVATE_KEY)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error(UploadError.FAILED_TO_UPLOAD_MEDIA, e.message ?: e.javaClass.simpleName)
            }
        }

        /** The pixel size of the image at [url], read from its header only, or null when it can't be read. */
        private suspend fun servedDimensions(
            url: String,
            okHttpClient: (String) -> OkHttpClient,
        ): Pair<Int, Int>? =
            try {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build()
                okHttpClient(url).newCall(request).executeAsync().use { response ->
                    withContext(Dispatchers.IO) {
                        if (!response.isSuccessful) return@withContext null
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        response.body.byteStream().use { BitmapFactory.decodeStream(it, null, bounds) }
                        if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth to bounds.outHeight else null
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("UploadOrchestrator", "Could not read the size of $url", e)
                null
            }

        /** [state] with [notice] added to its result, set as the progress state. */
        private fun withNotice(
            state: UploadingState.Finished,
            notice: UploadNotice,
        ): UploadingFinalState {
            val result = state.result as? UploadOrchestrator.OrchestratorResult.ServerResult ?: return state
            return finish(result.withNotice(notice))
        }

        /**
         * Tries [selected], then the rest of the servers the picker offered (see
         * [blossomUploadOrder]) until one stores the blob. Every server gets the same blob, so they
         * share one hash, one set of preview hashes and one signed upload token: the file is read
         * and the signer is asked once, and a signer that refused, timed out or is read-only stops
         * the fallback instead of prompting again per server. Attempts
         * don't publish their errors; when every server fails, the error shown is the selected
         * server's, the one the user chose and will recognize.
         */
        private suspend fun uploadBlossomWithFallback(
            selected: ServerName,
            account: Account,
            uploadTo: suspend (serverBaseUrl: String, shared: SharedUpload) -> UploadingFinalState,
        ): UploadingFinalState {
            val order = blossomUploadOrder(selected, account.blossomServers.hostNameFlow.value)
            val shared = SharedUpload()
            var firstError: UploadingState.Error? = null
            for (server in order) {
                when (val result = uploadTo(server.baseUrl, shared)) {
                    is UploadingState.Finished -> {
                        val refused = firstError ?: return result
                        // Not the server the user picked: say where it went, and why.
                        val notice =
                            UploadNotice.UploadedToAnotherServer(
                                chosenServer = BlossomServerUrl.domain(selected.baseUrl),
                                usedServer = BlossomServerUrl.domain(server.baseUrl),
                                reason = refused.params.firstOrNull()?.toString(),
                            )
                        return withNotice(result, notice)
                    }
                    is UploadingState.Error -> {
                        if (firstError == null) firstError = result
                        if (shared.signerFailed) return result.also { updateState(0.0, it) }
                        Log.w("UploadOrchestrator", "Upload to ${server.baseUrl} failed, trying the next server")
                    }
                }
            }
            return firstError!!.also { updateState(0.0, it) }
        }

        private class SanitizedBlob(
            val file: File,
            val uri: Uri,
            val size: Long,
        )

        /**
         * A copy of [uri] without the metadata a Buzz workspace refuses (HTTP 422: EXIF/XMP/ICC
         * segments, PNG text chunks, trailing bytes), or null when there was nothing to remove or
         * the format isn't one [BuzzMediaSanitizer] rewrites. The caller deletes the file.
         */
        private fun sanitizeForBuzzWorkspace(
            uri: Uri,
            contentType: String?,
            context: Context,
        ): SanitizedBlob? {
            if (!BuzzMediaSanitizer.handles(contentType)) return null
            val original = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val clean = BuzzMediaSanitizer.sanitize(original, contentType)
            if (clean.contentEquals(original)) return null
            val file = File.createTempFile("buzz-upload-", ".bin", context.cacheDir)
            file.writeBytes(clean)
            return SanitizedBlob(file, Uri.fromFile(file), clean.size.toLong())
        }

        /**
         * What every server tried for one blob shares: its [BlossomUploader.PreparedBlob], read
         * once, and the upload token, signed at most once. A signer that returned no token is asked
         * again on the next server; only a token or a signer failure is kept.
         */
        private class SharedUpload {
            private var outcome: Result<BlossomAuthorizationEvent>? = null
            private var prepared: BlossomUploader.PreparedBlob? = null

            fun blob(prepare: () -> BlossomUploader.PreparedBlob): BlossomUploader.PreparedBlob = prepared ?: prepare().also { prepared = it }

            var signerFailed = false
                private set

            suspend fun get(sign: suspend () -> BlossomAuthorizationEvent?): BlossomAuthorizationEvent? {
                outcome?.let { return it.getOrThrow() }
                return try {
                    sign()?.also { outcome = Result.success(it) }
                } catch (e: SignerExceptions) {
                    signerFailed = true
                    outcome = Result.failure(e)
                    throw e
                }
            }
        }

        private suspend fun uploadBlossom(
            fileUri: Uri,
            contentType: String?,
            size: Long?,
            alt: String?,
            contentWarningReason: String?,
            serverBaseUrl: String,
            contentTypeForResult: String?,
            originalHash: String?,
            account: Account,
            forcedSigner: NostrSigner?,
            context: Context,
            shared: SharedUpload,
            buzzWorkspace: Boolean = false,
        ): UploadingFinalState {
            updateState(0.2, UploadingState.Uploading)
            // BUD-05: route through /media (optimize) when the user opted in. The forced-signer
            // path (e.g. NIP-46 draft signing) always uses the bit-exact /upload, and so does a
            // Buzz workspace, whose relay checks the imeta `x` against the blob it stored.
            val useMedia = !buzzWorkspace && forcedSigner == null && account.settings.optimizeMediaOnUpload.value
            return try {
                val blob = shared.blob { BlossomUploader().prepare(fileUri, contentType, context) }
                // Set when the server already held an identical image and refused this one.
                var identicalCopy: BlossomIdenticalMediaException? = null
                val result =
                    try {
                        BlossomUploader()
                            .upload(
                                uri = fileUri,
                                contentType = contentType,
                                alt = alt,
                                sensitiveContent = contentWarningReason,
                                serverBaseUrl = serverBaseUrl,
                                okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                                // Use a t=media token when optimizing via /media, otherwise a plain
                                // t=upload token. Tokens are intentionally NOT server-scoped on the
                                // upload path: some servers reject an upload whose auth carries a
                                // `server` tag, and upload-token replay is not the threat scoping
                                // guards against (delete tokens are — those stay scoped).
                                httpAuth = { hash, size, alt ->
                                    shared.get {
                                        when {
                                            // Buzz's strict mode demands exactly one `server` tag naming the
                                            // workspace and a token that expires within 60s of signing.
                                            buzzWorkspace -> {
                                                val now = TimeUtils.now()
                                                BlossomAuthorizationEvent.createUploadAuth(
                                                    hash = hash,
                                                    size = size,
                                                    alt = alt,
                                                    signer = forcedSigner ?: account.signer,
                                                    servers = listOf(serverBaseUrl),
                                                    createdAt = now,
                                                    expiration = now + BUZZ_UPLOAD_TOKEN_SECS,
                                                )
                                            }
                                            forcedSigner != null -> BlossomAuthorizationEvent.createUploadAuth(hash, size, alt, forcedSigner)
                                            useMedia -> account.createBlossomMediaAuth(hash, size, alt)
                                            else -> account.createBlossomUploadAuth(hash, size, alt)
                                        }
                                    }
                                },
                                context = context,
                                useMediaEndpoint = useMedia,
                                onProgress = uploadProgress(from = 0.2, to = 0.6),
                                prepared = blob,
                            )
                    } catch (e: BlossomIdenticalMediaException) {
                        // nostr.download keeps one copy of a picture: it refuses this file because it
                        // holds one it finds identical, and names it. Link to that copy rather than
                        // move to a server the user did not pick; verifyHeader downloads it and checks
                        // it hashes to the name. A workspace or a /media upload never takes this path.
                        if (buzzWorkspace || useMedia) throw e
                        identicalCopy = e
                        BlossomUploader().convertToMediaResult(BlossomClient.heldDescriptor(e.existingHash, blob.size, contentType, serverBaseUrl, BlossomClient.extensionFor(contentType))).mergeLocalMetadata(blob.localMetadata)
                    }

                val finalState =
                    if (buzzWorkspace) {
                        // The workspace already bound the stored blob to our X-SHA-256 and the token's
                        // `x`, and its /media reads need a signed GET this client doesn't send — so take
                        // its descriptor rather than downloading the blob back to hash it again.
                        trustWorkspaceDescriptor(result, contentType, size, contentTypeForResult, originalHash)
                    } else {
                        verifyHeader(
                            uploadResult = result,
                            localContentType = contentType,
                            okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                            originalHash = originalHash,
                            originalContentType = contentTypeForResult,
                            // A Blossom URL names its blob by hash, so the server must serve exactly
                            // the blob its descriptor reports.
                            expectedHash = result.sha256,
                            // /upload stores the exact bytes: a descriptor naming the hash we sent
                            // confirms them, so only that the server serves them is left to check.
                            // /media may have transformed them, so that one is downloaded.
                            sent = blob.takeIf { !useMedia && result.sha256.equals(it.hash, ignoreCase = true) },
                        )
                    }

                // The server's word is all that makes its copy this picture: one of another size is not.
                val copyDim = identicalCopy?.let { servedDimensions(BlossomServerUrl.blob(serverBaseUrl, it.existingHash), Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads) }
                val localDim = blob.localMetadata?.dim
                if (identicalCopy != null && finalState is UploadingState.Finished && copyDim != null && localDim != null && copyDim != localDim.width to localDim.height) {
                    Log.w("UploadOrchestrator") { "$serverBaseUrl's identical copy is ${copyDim.first}x${copyDim.second}, not ${localDim.width}x${localDim.height}" }
                    return UploadingState.Error(UploadError.FAILED_TO_UPLOAD_MEDIA, arrayOf(identicalCopy.message ?: serverBaseUrl))
                }

                // Nothing to tell the user: the picture they chose is on the server they chose.
                if (identicalCopy != null && finalState is UploadingState.Finished) {
                    Log.d("UploadOrchestrator") { "$serverBaseUrl refused the upload as identical to ${identicalCopy.existingHash}; using that copy" }
                }

                // BUD-04: replicate the blob to the user's other Blossom servers for redundancy.
                // Fire-and-forget on the account scope AFTER the upload is finished: mirroring is
                // pure background redundancy, so it must never delay, alter, or fail the upload the
                // user already completed, and must not touch the on-screen progress state.
                if (finalState is UploadingState.Finished && !buzzWorkspace && forcedSigner == null && account.settings.mirrorUploadsToAllServers.value) {
                    account.scope.launch(Dispatchers.IO) {
                        try {
                            mirrorToOtherServers(result, serverBaseUrl, account)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w("UploadOrchestrator", "Background mirror failed", e)
                        }
                    }
                }

                finalState
            } catch (_: SignerExceptions.ReadOnlyException) {
                UploadingState.Error(UploadError.LOGIN_WITH_PRIVATE_KEY, emptyArray())
            } catch (e: BlossomPaymentException) {
                // BUD-07: the server wants payment before it will store the blob.
                UploadingState.Error(UploadError.BLOSSOM_PAYMENT_REQUIRED, arrayOf(e.payment.reason ?: serverBaseUrl))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                UploadingState.Error(UploadError.FAILED_TO_UPLOAD_MEDIA, arrayOf(e.message?.ifBlank { null } ?: e.javaClass.simpleName))
            }
        }

        /**
         * BUD-04 mirror fan-out: asks every *other* Blossom server in the account's
         * kind-10063 list to pull the freshly-uploaded blob from [result]'s URL. Runs
         * in the background after the upload is finished (see caller), so it never
         * delays the user's post or touches the upload progress UI; per-server failures
         * are swallowed. Requires the blob's sha256 so server B can verify the download.
         */
        private suspend fun mirrorToOtherServers(
            result: MediaUploadResult,
            primaryServerBaseUrl: String,
            account: Account,
        ) {
            val sourceUrl = result.url ?: return
            val hash = result.sha256 ?: sourceUrl.substringAfterLast('/').substringBefore('.')
            if (hash.length != 64) return

            // Only the user's *explicitly configured* kind-10063 servers (flow), NOT the
            // DEFAULT_MEDIA_SERVERS fallback that hostNameFlow injects — we must never fan
            // uploads out to public defaults the user never opted into.
            val primaryDomain = BlossomServerUrl.domain(primaryServerBaseUrl)
            val targets =
                account.blossomServers.flow.value
                    .filter { BlossomServerUrl.domain(it) != primaryDomain }
                    .distinct()

            if (targets.isEmpty()) return

            val contentType = result.type ?: "application/octet-stream"
            // One token serves every target: upload tokens are not server-scoped (see uploadBlossom)
            // and last an hour, so a remote signer is asked once instead of once per server.
            val auth = account.createBlossomUploadAuth(hash, result.size ?: 0L, "Mirror $hash").toAuthorizationHeader()
            targets.forEach { target ->
                try {
                    BlossomClient(Amethyst.instance.roleBasedHttpClientBuilder.okHttpClientForUploads(target))
                        .mirrorOrUpload(sourceUrl, hash, contentType, target, auth)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("UploadOrchestrator", "Failed to mirror $hash to $target", e)
                }
            }
        }

        private fun trustWorkspaceDescriptor(
            uploadResult: MediaUploadResult,
            localContentType: String?,
            localSize: Long?,
            originalContentType: String?,
            originalHash: String?,
        ): UploadingFinalState {
            val url = uploadResult.url
            val hash = uploadResult.sha256
            if (url.isNullOrBlank() || hash.isNullOrBlank()) return error(UploadError.SERVER_DID_NOT_PROVIDE_URL)
            val fileHeader =
                FileHeader(
                    mimeType = uploadResult.type ?: localContentType,
                    hash = hash,
                    size = (uploadResult.size ?: localSize)?.toInt() ?: 0,
                    dim = uploadResult.dimension,
                    blurHash = uploadResult.blurHash,
                )
            return finish(UploadOrchestrator.OrchestratorResult.ServerResult(fileHeader, url, uploadResult.magnet, hash, originalContentType, originalHash))
        }

        /**
         * Confirms the server serves the upload and builds its header from what it serves.
         *
         * When the server reported storing exactly the bytes sent ([sent]: a Blossom `/upload`
         * whose descriptor names the hash computed before sending), a `HEAD` request is enough:
         * the hash and size are already known, and downloading the whole file back would double the
         * data the upload costs. Otherwise, or when the server has no `HEAD`, the file is downloaded
         * and hashed, and must hash to [expectedHash] when one is given.
         */
        private suspend fun verifyHeader(
            uploadResult: MediaUploadResult,
            localContentType: String?,
            originalContentType: String?,
            originalHash: String?,
            okHttpClient: (String) -> OkHttpClient,
            expectedHash: String? = null,
            sent: BlossomUploader.PreparedBlob? = null,
        ): UploadingFinalState {
            val url = uploadResult.url
            if (url.isNullOrBlank()) {
                return error(UploadError.SERVER_DID_NOT_PROVIDE_URL)
            }

            updateState(0.6, UploadingState.Downloading)

            if (sent != null) {
                when (val check = ImageDownloader().waitUntilServed(url, okHttpClient)) {
                    null -> {
                        return error(UploadError.COULD_NOT_DOWNLOAD_FROM_SERVER)
                    }

                    is ImageDownloader.ServedCheck.Served -> {
                        if (check.size != null && check.size != sent.size) {
                            Log.w("UploadOrchestrator") { "$url serves ${check.size} bytes, not the uploaded ${sent.size}" }
                            return error(UploadError.SERVER_RETURNED_DIFFERENT_FILE, BlossomServerUrl.domain(url))
                        }
                        return finishVerified(uploadResult, url, sent.hash, sent.size, localContentType ?: check.contentType, originalContentType, originalHash)
                    }

                    ImageDownloader.ServedCheck.HeadNotSupported -> {}
                }
            }

            // Use streaming verification for memory efficiency with large files
            val verification =
                ImageDownloader().waitAndVerifyStream(url, okHttpClient)
                    ?: return error(UploadError.COULD_NOT_DOWNLOAD_FROM_SERVER)

            updateState(0.8, UploadingState.Hashing)

            if (expectedHash != null && !verification.hash.equals(expectedHash, ignoreCase = true)) {
                Log.w("UploadOrchestrator") { "$url serves ${verification.hash}, not the uploaded $expectedHash" }
                return error(UploadError.SERVER_RETURNED_DIFFERENT_FILE, BlossomServerUrl.domain(url))
            }

            return finishVerified(uploadResult, url, verification.hash, verification.size, localContentType ?: verification.contentType, originalContentType, originalHash)
        }

        private fun finishVerified(
            uploadResult: MediaUploadResult,
            url: String,
            hash: String,
            size: Long,
            localContentType: String?,
            originalContentType: String?,
            originalHash: String?,
        ): UploadingFinalState {
            // Blurhash and dimensions come from the upload result, which already merged the local ones.
            val fileHeader =
                FileHeader(
                    mimeType = uploadResult.type ?: localContentType,
                    hash = hash,
                    size = size.toInt(),
                    dim = uploadResult.dimension,
                    blurHash = uploadResult.blurHash,
                )

            return finish(
                UploadOrchestrator.OrchestratorResult.ServerResult(
                    fileHeader,
                    url,
                    uploadResult.magnet,
                    uploadResult.sha256,
                    originalContentType,
                    originalHash,
                ),
            )
        }

        suspend fun compressIfNeeded(
            uri: Uri,
            mimeType: String?,
            compressionQuality: CompressorQuality,
            context: Context,
            useH265: Boolean = false,
            convertGifToMp4: Boolean = false,
        ) = if (compressionQuality != CompressorQuality.UNCOMPRESSED || convertGifToMp4) {
            updateState(0.02, UploadingState.Compressing)
            MediaCompressor().compress(uri, mimeType, compressionQuality, context.applicationContext, useH265, convertGifToMp4)
        } else {
            MediaCompressorResult(uri, mimeType, null)
        }

        private suspend fun stripAfterCompression(
            originalUri: Uri,
            compressed: MediaCompressorResult,
            mimeType: String?,
            compressionQuality: CompressorQuality,
            stripMetadata: Boolean,
            onStrippingFailed: suspend () -> Boolean,
            context: Context,
        ): Uri? {
            val effectiveMimeType = compressed.contentType ?: mimeType
            val isVideo = effectiveMimeType?.startsWith("video/", ignoreCase = true) == true
            val compressionRequested = compressionQuality != CompressorQuality.UNCOMPRESSED
            val compressionApplied = compressionRequested && compressed.uri != originalUri

            // A fallback to the original (timeout, not smaller, encoder failure) is not compressionApplied,
            // so it reaches the stripper below.
            if (!StripDecision.shouldStrip(stripMetadata, isVideo, compressionApplied)) return compressed.uri

            // AvifMetadataNotVerifiableException is allowed to propagate so the caller
            // can emit a specific error rather than the generic "Upload cancelled".
            val strippingResult = MetadataStripper.strip(compressed.uri, effectiveMimeType, context.applicationContext)

            if (!strippingResult.stripped && !onStrippingFailed()) return null

            return strippingResult.uri
        }

        /**
         * Deletes a temporary file created during the upload pipeline if its URI
         * differs from the original (meaning it's an intermediate temp file, not the user's content).
         */
        internal fun deleteTempUri(
            tempUri: Uri,
            originalUri: Uri,
        ) {
            if (tempUri == originalUri) return
            try {
                val path = tempUri.path ?: return
                val file = File(path)
                if (file.delete()) {
                    Log.d("UploadOrchestrator") { "Deleted temp file: $path" }
                }
            } catch (e: Exception) {
                Log.w("UploadOrchestrator", "Failed to delete temp file: ${tempUri.path}", e)
            }
        }

        suspend fun upload(
            uri: Uri,
            mimeType: String?,
            alt: String?,
            contentWarningReason: String?,
            compressionQuality: CompressorQuality,
            server: ServerName,
            account: Account,
            context: Context,
            useH265: Boolean = false,
            stripMetadata: Boolean = true,
            onStrippingFailed: suspend () -> Boolean = { true },
            convertGifToMp4: Boolean = false,
            forcedSigner: NostrSigner? = null,
        ): UploadingFinalState {
            val compressed = compressIfNeeded(uri, mimeType, compressionQuality, context, useH265, convertGifToMp4)

            val finalUri =
                try {
                    stripAfterCompression(uri, compressed, mimeType, compressionQuality, stripMetadata, onStrippingFailed, context)
                } catch (e: AvifMetadataNotVerifiableException) {
                    return error(UploadError.AVIF_METADATA_STRIP_FAILED, e.message ?: e.javaClass.simpleName).also {
                        deleteTempUri(compressed.uri, uri)
                    }
                } catch (e: CancellationException) {
                    // Cancelled mid-strip or at the strip-failed prompt: the compressed temp file goes too.
                    deleteTempUri(compressed.uri, uri)
                    throw e
                } ?: return error(UploadError.UPLOAD_CANCELLED).also {
                    deleteTempUri(compressed.uri, uri)
                }

            if (compressed.uri != finalUri) deleteTempUri(compressed.uri, uri)

            try {
                return when (server.type) {
                    ServerType.NIP96 -> uploadNIP96(finalUri, compressed.contentType, compressed.size, alt, contentWarningReason, server.baseUrl, null, null, account, forcedSigner, context)
                    ServerType.Blossom ->
                        uploadBlossomWithFallback(server, account) { baseUrl, shared ->
                            uploadBlossom(finalUri, compressed.contentType, compressed.size, alt, contentWarningReason, baseUrl, null, null, account, forcedSigner, context, shared)
                        }
                    // Private to the workspace: never retried on, or mirrored to, a public server.
                    ServerType.BuzzWorkspace -> {
                        val clean = sanitizeForBuzzWorkspace(finalUri, compressed.contentType, context)
                        try {
                            uploadBlossom(clean?.uri ?: finalUri, compressed.contentType, clean?.size ?: compressed.size, alt, contentWarningReason, server.baseUrl, null, null, account, forcedSigner, context, SharedUpload(), buzzWorkspace = true)
                                .also { if (it is UploadingState.Error) updateState(0.0, it) }
                        } finally {
                            clean?.file?.let { file ->
                                if (!file.delete()) Log.w("UploadOrchestrator") { "Failed to delete sanitized temp file: ${file.path}" }
                            }
                        }
                    }
                }
            } finally {
                deleteTempUri(finalUri, uri)
            }
        }

        suspend fun uploadEncrypted(
            uri: Uri,
            mimeType: String?,
            alt: String?,
            contentWarningReason: String?,
            compressionQuality: CompressorQuality,
            encrypt: SymmetricCipher,
            server: ServerName,
            account: Account,
            context: Context,
            useH265: Boolean = false,
            stripMetadata: Boolean = true,
            onStrippingFailed: suspend () -> Boolean = { true },
            convertGifToMp4: Boolean = false,
            forcedSigner: NostrSigner? = null,
        ): UploadingFinalState {
            val compressed = compressIfNeeded(uri, mimeType, compressionQuality, context, useH265, convertGifToMp4)

            val finalUri =
                try {
                    stripAfterCompression(uri, compressed, mimeType, compressionQuality, stripMetadata, onStrippingFailed, context)
                } catch (e: AvifMetadataNotVerifiableException) {
                    return error(UploadError.AVIF_METADATA_STRIP_FAILED, e.message ?: e.javaClass.simpleName).also {
                        deleteTempUri(compressed.uri, uri)
                    }
                } catch (e: CancellationException) {
                    // Cancelled mid-strip or at the strip-failed prompt: the compressed temp file goes too.
                    deleteTempUri(compressed.uri, uri)
                    throw e
                } ?: return error(UploadError.UPLOAD_CANCELLED).also {
                    deleteTempUri(compressed.uri, uri)
                }

            if (compressed.uri != finalUri) deleteTempUri(compressed.uri, uri)

            val encrypted =
                try {
                    EncryptFiles().encryptFile(context, finalUri, encrypt)
                } catch (_: OutOfMemoryError) {
                    // The cipher takes the whole file as one array, next to its ciphertext. A file too
                    // big for that fails one large allocation, which is freed as this unwinds: report
                    // it instead of crashing the app.
                    return error(UploadError.FILE_TOO_LARGE_TO_ENCRYPT)
                } finally {
                    deleteTempUri(finalUri, uri)
                }

            try {
                return when (server.type) {
                    ServerType.NIP96 -> uploadNIP96(encrypted.uri, encrypted.contentType, encrypted.size, alt, contentWarningReason, server.baseUrl, compressed.contentType, encrypted.originalHash, account, forcedSigner, context)
                    // The same encrypted file goes to every server tried, so its key, nonce and
                    // hash stay valid whichever one ends up holding it.
                    ServerType.Blossom ->
                        uploadBlossomWithFallback(server, account) { baseUrl, shared ->
                            uploadBlossom(encrypted.uri, encrypted.contentType, encrypted.size, alt, contentWarningReason, baseUrl, compressed.contentType, encrypted.originalHash, account, forcedSigner, context, shared)
                        }

                    ServerType.BuzzWorkspace ->
                        uploadBlossom(encrypted.uri, encrypted.contentType, encrypted.size, alt, contentWarningReason, server.baseUrl, compressed.contentType, encrypted.originalHash, account, forcedSigner, context, SharedUpload(), buzzWorkspace = true)
                            .also { if (it is UploadingState.Error) updateState(0.0, it) }
                }
            } finally {
                deleteTempUri(encrypted.uri, uri)
            }
        }
    }

    override suspend fun previewImageCompression(
        uri: MediaUri,
        mimeType: String?,
        compressionQuality: CompressorQuality,
    ): ImageCompressionPreview? {
        // The caller cancels a preview the slider moved past; its file must not outlive it, even
        // when the cancellation lands as this returns.
        var written: Uri? = null
        return try {
            withContext(Dispatchers.IO) {
                val type = mimeType ?: mimeType(uri)
                if (compressionQuality.imageMaxDimension == null || !MediaCompressor.isReencodableImage(type)) return@withContext null
                val result = MediaCompressor().compress(uri, type, compressionQuality, appContext)
                // The compressor hands the original back when it fails.
                if (result.uri == uri) return@withContext null
                written = result.uri
                ImageCompressionPreview(imageStats(uri), imageStats(result.uri), result.uri)
            }
        } catch (e: CancellationException) {
            written?.let { discardTempFile(it) }
            throw e
        }
    }

    /** Pixel size as displayed (EXIF rotation applied) and byte length, without decoding the pixels. */
    private fun imageStats(uri: Uri): ImageFileStats {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        val width = bounds.outWidth.takeIf { it > 0 }
        val height = bounds.outHeight.takeIf { it > 0 }
        val sideways = runCatching { resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees % 180 != 0 } }.getOrNull() == true
        val bytes =
            runCatching {
                if (uri.scheme == "file") {
                    uri.path?.let { File(it).length() }
                } else {
                    resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
                }
            }.getOrNull()
        return if (sideways) ImageFileStats(height, width, bytes) else ImageFileStats(width, height, bytes)
    }

    override suspend fun remoteFileHeader(
        url: String,
        httpClients: IRoleBasedHttpClientBuilder,
    ): FileHeader? =
        withContext(Dispatchers.IO) {
            val fileExtension: String = MimeTypeMap.getFileExtensionFromUrl(url)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase(Locale.getDefault()))
            val blob = ImageDownloader().fetch(url, httpClients.okHttpClientForImage(url)) ?: return@withContext null
            FileHeader.prepare(blob.bytes, blob.imageMimeType() ?: mimeType ?: blob.mimeType(), null).getOrNull()
        }

    override suspend fun probeVideo(uri: MediaUri): VideoInfo? = withContext(Dispatchers.IO) { VideoCompressionHelper.probe(appContext, uri) }

    override suspend fun probeHdr(uri: MediaUri): VideoHdrInfo? = withContext(Dispatchers.IO) { VideoCompressionHelper.probeHdr(appContext, uri) }

    override fun displayName(uri: MediaUri): String? =
        appContext.contentResolver.query(uri, null, null, null, null)?.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && it.moveToFirst()) it.getString(idx) else null
        }

    override fun discardTempFile(uri: MediaUri) {
        try {
            uri.path?.let { path -> File(path).takeIf { it.exists() }?.delete() }
        } catch (e: Exception) {
            Log.w("AndroidMediaUploader", "Failed to delete temp file", e)
        }
    }

    override fun mimeType(uri: MediaUri): String? = appContext.contentResolver.getType(uri)

    override fun fileSize(uri: MediaUri): Long? =
        try {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                val idx = it.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && it.moveToFirst() && !it.isNull(idx)) it.getLong(idx) else null
            }
        } catch (e: Exception) {
            Log.w("AndroidMediaUploader", "Failed to read the size of $uri", e)
            null
        }

    override suspend fun stripMetadata(
        uri: MediaUri,
        mimeType: String?,
    ): MediaUri = withContext(Dispatchers.IO) { MetadataStripper.strip(uri, mimeType, appContext).uri }

    override suspend fun readBytes(uri: MediaUri): ByteArray? = withContext(Dispatchers.IO) { appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
}

/** Lifetime of a Buzz workspace upload token; Buzz rejects one that outlives 60s from signing. */
private const val BUZZ_UPLOAD_TOKEN_SECS = 55L
