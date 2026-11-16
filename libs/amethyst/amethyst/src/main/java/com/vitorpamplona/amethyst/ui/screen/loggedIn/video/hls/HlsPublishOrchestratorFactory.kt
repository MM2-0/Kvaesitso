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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video.hls

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.davotoula.lightcompressor.hls.HlsContentTypes
import com.davotoula.lightcompressor.hls.HlsUploadHelper
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.service.upload.HlsPublishState
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.service.uploads.PreviewMetadataCalculator
import com.vitorpamplona.amethyst.service.uploads.getThumbnail
import com.vitorpamplona.amethyst.service.uploads.hls.HlsBlobUploader
import com.vitorpamplona.amethyst.service.uploads.hls.HlsBlobUploaderFactory
import com.vitorpamplona.amethyst.service.uploads.hls.HlsVideoEventTemplate
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "HlsPublishOrchestratorFactory"

private const val POSTER_JPEG_QUALITY = 85
private const val POSTER_CONTENT_TYPE = "image/jpeg"

/**
 * Production wiring for [HlsPublishOrchestrator]. Binds the upload closure to
 * [HlsUploadHelper.run], the uploader factory to [HlsBlobUploaderFactory], and the
 * signAndPublish closure to the account's signer + outbox publish path.
 *
 * The Uri is read via [uriProvider] on each publish invocation so the orchestrator can be built
 * once (at VM load) before the user actually picks a video.
 */
fun createProductionHlsPublishOrchestrator(
    state: MutableStateFlow<HlsPublishState>,
    account: Account,
    context: Context,
    uriProvider: () -> Uri?,
): HlsPublishOrchestrator =
    HlsPublishOrchestrator(
        _state = state,
        runUpload = { config, listener, uploadFile ->
            val uri = uriProvider() ?: error("No video picked")
            HlsUploadHelper.run<MediaUploadResult>(
                context = context,
                uri = uri,
                config = config,
                listener = listener,
                uploader = uploadFile,
            )
        },
        buildUploader = { server ->
            HlsBlobUploaderFactory.create(server, account, context)
        },
        uploadMaster = { uploader, masterPlaylist ->
            val masterFile = File.createTempFile("hls-master-", ".m3u8", context.cacheDir)
            try {
                masterFile.writeText(masterPlaylist)
                // Master playlist is ~1-5 KB and uploads in milliseconds, so we don't
                // bother piping byte progress for it — the file counter bar flipping from
                // "N-1 of N" → "N of N" is enough signal.
                uploader.upload(masterFile, HlsContentTypes.HLS_PLAYLIST) { _, _ -> }
            } finally {
                if (!masterFile.delete()) {
                    Log.w(TAG) { "uploadMaster: failed to delete temp file ${masterFile.absolutePath}" }
                }
            }
        },
        signAndPublish = { template, sibling ->
            val inner =
                when (template) {
                    is HlsVideoEventTemplate.Horizontal -> template.template
                    is HlsVideoEventTemplate.Vertical -> template.template
                }
            val signed = account.signer.sign(inner)
            account.sendAutomatic(signed)

            // Sibling-publish failure is a soft warning: the NIP-71 event already landed, so a
            // partial success is more useful than a hard fail.
            try {
                val signedSibling = account.signer.sign(sibling)
                account.sendAutomatic(signedSibling)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG) { "kind:1 sibling sign/publish failed: ${e.message}" }
            }
            signed.id
        },
        uploadPoster = { uploader ->
            val uri = uriProvider()
            if (uri == null) {
                null
            } else {
                generateAndUploadPoster(context, uri, uploader)
            }
        },
    )

/**
 * Extracts a still frame from the source video at [uri], encodes it as JPEG, computes blurhash +
 * thumbhash from the same JPEG bytes, and uploads it via [uploader]. Returns an [HlsPosterUpload]
 * carrying the public URL plus the two hashes on success, or null if any step fails (unsupported
 * source, no readable frame, encode/upload error). The orchestrator treats null as "publish
 * without a poster" — failure here must never abort the publish.
 */
private suspend fun generateAndUploadPoster(
    context: Context,
    uri: Uri,
    uploader: HlsBlobUploader,
): HlsPosterUpload? {
    val posterFile = extractPosterToTempFile(context, uri) ?: return null
    return try {
        // Read once: hash from the same bytes we upload, so the hashes describe the exact
        // JPEG receiving clients will fetch via the imeta `image` URL.
        val posterBytes = posterFile.readBytes()
        val hashes =
            try {
                PreviewMetadataCalculator.computeFromBytes(posterBytes, POSTER_CONTENT_TYPE, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG) { "uploadPoster: blurhash/thumbhash compute failed: ${e.message}" }
                null
            }
        val result = uploader.upload(posterFile, POSTER_CONTENT_TYPE) { _, _ -> }
        val url = result.url ?: return null
        HlsPosterUpload(
            url = url,
            blurhash = hashes?.blurhash?.blurhash,
            thumbhash = hashes?.thumbhash?.thumbhash,
        )
    } finally {
        if (!posterFile.delete()) {
            Log.w(TAG) { "uploadPoster: failed to delete temp file ${posterFile.absolutePath}" }
        }
    }
}

private suspend fun extractPosterToTempFile(
    context: Context,
    uri: Uri,
): File? =
    withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var bitmap: Bitmap? = null
        try {
            retriever.setDataSource(context, uri)
            bitmap = retriever.getThumbnail()
            if (bitmap == null) {
                Log.w(TAG) { "extractPosterToTempFile: getThumbnail returned null for $uri" }
                return@withContext null
            }
            val outFile = File.createTempFile("hls-poster-", ".jpg", context.cacheDir)
            try {
                FileOutputStream(outFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, POSTER_JPEG_QUALITY, stream)
                }
                outFile
            } catch (e: Exception) {
                if (!outFile.delete()) {
                    Log.w(TAG) { "extractPosterToTempFile: failed to delete temp file ${outFile.absolutePath}" }
                }
                throw e
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG) { "extractPosterToTempFile: failed for $uri — ${e.message}" }
            null
        } finally {
            bitmap?.recycle()
            try {
                retriever.release()
            } catch (_: Exception) {
                // release() can throw RuntimeException on some devices; swallow — the temp
                // file (if any) is already cleaned up and the bitmap recycled.
            }
        }
    }
