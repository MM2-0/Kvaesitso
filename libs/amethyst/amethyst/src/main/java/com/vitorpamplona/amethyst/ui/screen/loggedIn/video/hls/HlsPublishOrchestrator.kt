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

import com.davotoula.lightcompressor.VideoCodec
import com.davotoula.lightcompressor.hls.HlsConfig
import com.davotoula.lightcompressor.hls.HlsContentTypes
import com.davotoula.lightcompressor.hls.HlsLadder
import com.davotoula.lightcompressor.hls.HlsListener
import com.davotoula.lightcompressor.hls.HlsUploadResult
import com.davotoula.lightcompressor.hls.HlsUploaded
import com.davotoula.lightcompressor.hls.Rendition
import com.davotoula.lightcompressor.hls.SimpleHlsListener
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.service.upload.HlsPublishState
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.service.uploads.hls.HlsBlobUploader
import com.vitorpamplona.amethyst.service.uploads.hls.HlsKind1SiblingBuilder
import com.vitorpamplona.amethyst.service.uploads.hls.HlsVideoEventBuilder
import com.vitorpamplona.amethyst.service.uploads.hls.HlsVideoEventTemplate
import com.vitorpamplona.amethyst.service.uploads.hls.HlsVideoPublishInput
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

private const val TAG = "HlsPublishOrchestrator"

data class HlsPublishRequest(
    val title: String,
    val description: String,
    val sensitiveContent: Boolean,
    val contentWarningReason: String,
    val codec: VideoCodec,
    val server: ServerName,
    val ladder: HlsLadder = HlsLadder.default(),
    val durationSeconds: Int? = null,
)

/**
 * Poster JPEG upload result. Carries the public URL plus blurhash/thumbhash derived from the
 * same poster pixels, so every imeta in the published NIP-71 event can render an instant
 * low-res placeholder. All three fields share a single source frame; per-rendition variants
 * would be redundant.
 */
data class HlsPosterUpload(
    val url: String,
    val blurhash: String? = null,
    val thumbhash: String? = null,
)

/**
 * Orchestrates the transcode → upload → build → publish pipeline for a single HLS video publish.
 * Delegates transcoding and segment/media-playlist upload plumbing to the library's
 * [com.davotoula.lightcompressor.hls.HlsUploadHelper] via the injected [runUpload] closure, then
 * uploads the rewritten master playlist itself, builds the NIP-71 event, signs, and publishes.
 * All Android/account-specific concerns are injected as suspending callbacks so the whole state
 * machine is unit-testable.
 *
 * State transitions: Idle → Transcoding (per rendition, driven by listener) → Uploading (per
 * segment/playlist upload, driven by the uploader lambda) → Publishing → Success, or → Failure
 * on any exception.
 */
class HlsPublishOrchestrator(
    private val _state: MutableStateFlow<HlsPublishState>,
    private val runUpload: suspend (
        config: HlsConfig,
        listener: HlsListener,
        uploadFile: suspend (File, String) -> HlsUploaded<MediaUploadResult>,
    ) -> HlsUploadResult<MediaUploadResult>,
    private val buildUploader: (ServerName) -> HlsBlobUploader,
    private val uploadMaster: suspend (HlsBlobUploader, String) -> MediaUploadResult,
    // Signs and broadcasts the NIP-71 video event AND the kind:1 sibling note. Returns the
    // NIP-71 event id.
    private val signAndPublish: suspend (
        primary: HlsVideoEventTemplate,
        sibling: EventTemplate<TextNoteEvent>,
    ) -> String,
    // Generates a poster JPEG from the picked source video and uploads it via the supplied
    // uploader, returning the public URL. Returns null if poster generation isn't possible
    // (unsupported source, decode failure, no readable frame). Failures here must NOT fail
    // the whole publish — the orchestrator catches and continues without a poster.
    private val uploadPoster: suspend (HlsBlobUploader) -> HlsPosterUpload? = { _ -> null },
) {
    val state: StateFlow<HlsPublishState> = _state

    suspend fun publish(request: HlsPublishRequest) {
        try {
            _state.value = HlsPublishState.Transcoding(currentLabel = "", percent = 0)

            val uploader = buildUploader(request.server)
            val config =
                HlsConfig(
                    codec = request.codec,
                    ladder = request.ladder,
                )

            val totalUploads = request.ladder.renditions.size * 2 + 1
            var uploadsDone = 0

            // Dedup Transcoding state emissions so onProgress (which fires many times per
            // integer percent) doesn't flood the StateFlow with identical values.
            var lastTranscodingLabel: String? = null
            var lastTranscodingPercent = -1

            val listener =
                object : SimpleHlsListener() {
                    override fun onRenditionStart(rendition: Rendition) {
                        val label = rendition.resolution.label
                        if (lastTranscodingLabel != label || lastTranscodingPercent != 0) {
                            lastTranscodingLabel = label
                            lastTranscodingPercent = 0
                            _state.value = HlsPublishState.Transcoding(label, 0)
                        }
                    }

                    override fun onProgress(
                        rendition: Rendition,
                        percent: Float,
                    ) {
                        val label = rendition.resolution.label
                        val p = percent.toInt()
                        if (lastTranscodingLabel != label || lastTranscodingPercent != p) {
                            lastTranscodingLabel = label
                            lastTranscodingPercent = p
                            _state.value = HlsPublishState.Transcoding(label, p)
                        }
                    }
                }

            // Throttle byte-progress state writes so the uploader's thousand-calls-per-second
            // cadence doesn't flood StateFlow. At most one update per ~100ms or per 2% change,
            // whichever happens first. Reset at the start of each file. The throttle state is
            // held in a small wrapper so its fields can carry @Volatile — the onBytesProgress
            // callback runs on OkHttp's worker thread while reset happens on the coroutine's
            // IO thread, and we want explicit memory-model guarantees rather than relying on
            // implicit happens-before edges from OkHttp + kotlinx.coroutines resumption.
            val throttle = ProgressThrottleState()
            val onBytesProgress: (Long, Long) -> Unit = { written, total ->
                if (total > 0) {
                    val fraction = (written.toFloat() / total).coerceIn(0f, 1f)
                    val now = System.currentTimeMillis()
                    val deltaMs = now - throttle.lastTick
                    val deltaFraction = fraction - throttle.lastFraction
                    if (deltaMs >= PROGRESS_THROTTLE_MS || deltaFraction >= PROGRESS_THROTTLE_FRACTION || fraction >= 1f) {
                        throttle.lastTick = now
                        throttle.lastFraction = fraction
                        // Atomic check-then-set: if the state has already flipped to Transcoding
                        // or Publishing by the time we land here, keep it — don't clobber a
                        // more recent transition with a stale Uploading snapshot.
                        _state.update { current ->
                            if (current is HlsPublishState.Uploading) {
                                current.copy(currentFileFraction = fraction)
                            } else {
                                current
                            }
                        }
                    }
                }
            }

            val uploadResult =
                runUpload(config, listener) { file, suggestedFilename ->
                    // Pre-increment: `done` tracks the in-flight index, not the finished
                    // count. StateFlow conflation would otherwise eat the post-upload tick.
                    uploadsDone++
                    throttle.reset()
                    _state.value =
                        HlsPublishState.Uploading(
                            done = uploadsDone,
                            total = totalUploads,
                            currentLabel = suggestedFilename,
                            currentFileFraction = 0f,
                        )
                    val contentType =
                        if (suggestedFilename.endsWith(".m3u8")) {
                            HlsContentTypes.HLS_PLAYLIST
                        } else {
                            HlsContentTypes.FMP4_SEGMENT
                        }
                    val result = uploader.upload(file, contentType, onBytesProgress)
                    HlsUploaded(
                        url =
                            result.url
                                ?: error("Uploader returned null URL for $suggestedFilename"),
                        metadata = result,
                    )
                }

            uploadsDone++
            throttle.reset()
            _state.value =
                HlsPublishState.Uploading(
                    done = uploadsDone,
                    total = totalUploads,
                    currentLabel = "master.m3u8",
                    // Master upload isn't instrumented for byte progress (~1-5 KB, sub-second).
                    // Show a full bar for its brief visible window so the row reads as "done"
                    // rather than "empty + vanished" when state flips to Publishing.
                    currentFileFraction = 1f,
                )
            val masterUpload = uploadMaster(uploader, uploadResult.masterPlaylist)
            val masterUrl =
                masterUpload.url ?: error("Uploader returned null URL for master playlist")

            // Generate + upload a poster JPEG so HLS-unaware UI surfaces (gallery thumbnails,
            // previews) have a still to render. Tolerate failure: skip the poster rather than
            // failing the entire publish, since the user has already paid the cost of the long
            // segment uploads.
            val posterResult =
                try {
                    uploadPoster(uploader)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG) { "Poster generation/upload failed: ${e.message}" }
                    null
                }

            _state.value = HlsPublishState.Publishing
            val built =
                HlsVideoEventBuilder.build(
                    HlsVideoPublishInput(
                        renditions = uploadResult.renditions,
                        uploads = uploadResult.uploads,
                        masterUrl = masterUrl,
                        masterSha256 = masterUpload.sha256,
                        title = request.title,
                        description = request.description,
                        durationSeconds = request.durationSeconds,
                        contentWarning = contentWarningOrNull(request),
                        posterUrl = posterResult?.url,
                        blurhash = posterResult?.blurhash,
                        thumbhash = posterResult?.thumbhash,
                    ),
                )
            val sibling =
                HlsKind1SiblingBuilder.build(
                    title = request.title,
                    description = request.description,
                    masterUrl = masterUrl,
                    masterSha256 = masterUpload.sha256,
                    masterDimension = built.masterDimension,
                    posterUrl = posterResult?.url,
                    blurhash = posterResult?.blurhash,
                    thumbhash = posterResult?.thumbhash,
                )
            val eventId = signAndPublish(built.template, sibling)

            _state.value =
                HlsPublishState.Success(
                    eventId = eventId,
                    masterUrl = masterUrl,
                )
        } catch (e: CancellationException) {
            // Cancellation is not a failure. Let the rethrow propagate up the coroutine
            // scope; NewHlsVideoViewModel.cancel() calls reset() to put state back to Idle.
            throw e
        } catch (e: Throwable) {
            _state.value = HlsPublishState.Failure(message = e.message ?: e.javaClass.simpleName.orEmpty())
        }
    }

    fun reset() {
        _state.value = HlsPublishState.Idle
    }

    private fun contentWarningOrNull(request: HlsPublishRequest): String? = if (request.sensitiveContent) request.contentWarningReason else null

    // Per-publish throttle bookkeeping for byte-progress emissions. Wrapped in a tiny class
    // so its fields can carry @Volatile — the onBytesProgress callback runs on OkHttp's
    // worker thread while `throttle.reset()` runs on the coroutine's IO thread, and we want
    // an explicit memory-model guarantee rather than relying on implicit happens-before
    // edges from OkHttp + kotlinx.coroutines resumption.
    private class ProgressThrottleState {
        @Volatile var lastTick: Long = 0L

        @Volatile var lastFraction: Float = -1f

        fun reset() {
            lastTick = 0L
            lastFraction = -1f
        }
    }

    private companion object {
        private const val PROGRESS_THROTTLE_MS = 100L
        private const val PROGRESS_THROTTLE_FRACTION = 0.02f
    }
}
