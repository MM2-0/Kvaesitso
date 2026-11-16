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
package com.vitorpamplona.amethyst.commons.service.upload.video

import com.vitorpamplona.amethyst.commons.notices.UploadNotice
import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.amethyst.commons.service.upload.AmethystTempDir
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.video.CompressionRule
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoCompressionRules
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoInfo
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Transcodes with a system `ffmpeg` using Android's resolution/bitrate table, trying the
 * encoders of [FfmpegEncoders] in turn. Stays on the caller's dispatcher: only [runner] and
 * the probe block on a child process, which keeps cancellation and temp-file behaviour
 * testable under virtual time.
 */
class FfmpegVideoCompressor(
    private val locator: FfmpegLocator = FfmpegLocator.default,
    private val encoders: suspend (ffmpeg: String) -> Set<String> = { FfmpegEncoders.listAvailable(it) },
    private val probe: suspend (source: File) -> ProbeResult? = { FfmpegProbe(locator.ffprobe).probe(it) },
    private val stallTimeoutMs: Long = 60_000,
    private val runner: ProcessRunner = FfmpegProcesses,
    private val gate: Semaphore = FfmpegProcesses.encodeGate,
    private val tempFile: () -> File = { AmethystTempDir.createTempFile("amethyst_video_", ".mp4") },
) : VideoCompressor {
    override val available: Boolean get() = locator.available

    override suspend fun probe(source: File): VideoInfo? = probed(source)?.info

    override suspend fun compress(
        source: File,
        quality: CompressorQuality,
        useH265: Boolean,
        onProgress: (Double) -> Unit,
    ): VideoCompressionResult {
        val ffmpeg = locator.ffmpeg ?: return VideoCompressionResult.Original(null)
        val plan = plan(source, quality)

        val candidates = FfmpegEncoders.candidates(encoders(ffmpeg), useH265)
        if (candidates.isEmpty()) return failed(source, plan.notices, "no encoder", remux = true)

        val outcome = encodeWith(ffmpeg, source, candidates, plan.shortSide, plan, onProgress)
        val encoded = outcome as? EncodeOutcome.Encoded ?: return failed(source, plan.notices, (outcome as EncodeOutcome.Failed).lastStderr, remux = true)
        val outSize = encoded.file.length()
        val sourceSize = source.length()
        if (outSize >= sourceSize) {
            Log.i(TAG) { "Encoded ${source.name} is not smaller ($outSize ≥ $sourceSize); keeping the original" }
            encoded.file.deleteOrWarn(TAG, "oversized encode")
            return VideoCompressionResult.Original(strip(source), plan.notices + UploadNotice.CompressionNotSmaller)
        }
        val hevcMissed = useH265 && !FfmpegEncoders.isHevc(encoded.encoder)
        return VideoCompressionResult.Compressed(encoded.file, if (hevcMissed) plan.notices + UploadNotice.HevcUnavailable else plan.notices)
    }

    override suspend fun convertGif(
        source: File,
        onProgress: (Double) -> Unit,
    ): VideoCompressionResult {
        val ffmpeg = locator.ffmpeg ?: return VideoCompressionResult.Original(null)
        // The MEDIUM row's bitrate for the GIF's standard, at the GIF's own size, as Android's converter.
        val plan = plan(source, CompressorQuality.MEDIUM)
        val candidates = FfmpegEncoders.candidates(encoders(ffmpeg), useH265 = false)
        if (candidates.isEmpty()) return failed(source, plan.notices, "no encoder", remux = false)
        // A GIF is never remuxed: on failure it goes up untouched.
        return when (val outcome = encodeWith(ffmpeg, source, candidates, shortSide = null, plan, onProgress)) {
            is EncodeOutcome.Encoded -> VideoCompressionResult.Compressed(outcome.file, plan.notices)
            is EncodeOutcome.Failed -> failed(source, plan.notices, outcome.lastStderr, remux = false)
        }
    }

    // Under the gate like an encode, so one ffmpeg runs at a time; never called while a permit is held.
    override suspend fun strip(source: File): File? {
        val ffmpeg = locator.ffmpeg ?: return null
        return gate.withPermit {
            withTempOutput("remux of ${source.name}") { out ->
                val outcome = runner.run(FfmpegCommandBuilder.remux(ffmpeg, source, out), {}, stallTimeoutMs)
                if (outcome.exitCode == 0) {
                    out
                } else {
                    Log.w(TAG) { "Remux of ${source.name} exited ${outcome.exitCode}: ${outcome.stderrTail.trim()}" }
                    null
                }
            }
        }
    }

    /** What the probe says about [source], read once per file even when the slider asked first. */
    private suspend fun probed(source: File): ProbeResult? {
        val key = Triple(source.absolutePath, source.length(), source.lastModified())
        lastProbe?.let { (k, v) -> if (k == key) return v }
        // A failed probe (stalled or exited non-zero) is not remembered: the next ask tries again.
        return probe.invoke(source)?.also { lastProbe = key to it }
    }

    @Volatile
    private var lastProbe: Pair<Triple<String, Long, Long>, ProbeResult>? = null

    /** The table row, frame rate, duration and notices one encode of [source] at [quality] works from. */
    private class EncodePlan(
        private val rule: CompressionRule?,
        private val framerate: Float,
        val durationSeconds: Double?,
        val notices: List<UserNotice>,
    ) {
        val shortSide: Int? get() = rule?.shortSide

        /** The rule's bitrate for the codec, or the probe-failure fallback when nothing could be probed. */
        fun bitrate(hevc: Boolean): Int = rule?.getBitrateBps(framerate, hevc) ?: VideoCompressionRules.PROBE_FAILED_BITRATE_BPS
    }

    private suspend fun plan(
        source: File,
        quality: CompressorQuality,
    ): EncodePlan {
        val probed = probed(source)
        return EncodePlan(
            rule = probed?.let { VideoCompressionRules.ruleFor(quality, it.info.resolution.getStandard()) },
            framerate = probed?.info?.framerate ?: 30f,
            durationSeconds = probed?.durationSeconds,
            notices = if (probed?.isHdr == true) listOf(UploadNotice.HdrSource) else emptyList(),
        )
    }

    private sealed interface EncodeOutcome {
        class Encoded(
            val file: File,
            val encoder: String,
        ) : EncodeOutcome

        class Failed(
            val lastStderr: String?,
        ) : EncodeOutcome
    }

    /** Tries each candidate in turn under the gate; the bitrate and the HEVC tag follow the candidate. */
    private suspend fun encodeWith(
        ffmpeg: String,
        source: File,
        candidates: List<String>,
        shortSide: Int?,
        plan: EncodePlan,
        onProgress: (Double) -> Unit,
    ): EncodeOutcome =
        gate.withPermit {
            var lastStderr: String? = null
            for (candidate in candidates) {
                val bitrate = plan.bitrate(FfmpegEncoders.isHevc(candidate))
                val encoded =
                    withTempOutput("$candidate on ${source.name}") { out ->
                        val parser = FfmpegProgressParser(plan.durationSeconds)
                        val command = FfmpegCommandBuilder.encode(ffmpeg, source, out, candidate, bitrate, shortSide)
                        Log.d(TAG) { "Encoding ${source.name} with $candidate at $bitrate bps, short side ${shortSide ?: "kept"}" }
                        val outcome = runner.run(command, { line -> parser.onLine(line)?.let(onProgress) }, stallTimeoutMs)
                        if (outcome.exitCode == 0) {
                            out
                        } else {
                            lastStderr = outcome.stderrTail.trim().ifEmpty { "exit ${outcome.exitCode}" }
                            Log.w(TAG) { "$candidate exited ${outcome.exitCode} on ${source.name}: $lastStderr" }
                            null
                        }
                    }
                if (encoded != null) return@withPermit EncodeOutcome.Encoded(encoded, candidate)
            }
            EncodeOutcome.Failed(lastStderr)
        }

    /**
     * Runs [block] with a fresh temp file and hands the file back only when [block] returns it:
     * a null, an exception or a cancellation deletes it. The temp is created inside the try so a
     * factory that throws leaves nothing behind.
     */
    private suspend fun withTempOutput(
        what: String,
        block: suspend (out: File) -> File?,
    ): File? {
        var out: File? = null
        try {
            out = tempFile()
            return block(out) ?: run {
                out.deleteOrWarn(TAG, "failed $what")
                null
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { out?.deleteOrWarn(TAG, "cancelled $what") }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what failed", e)
            out?.deleteOrWarn(TAG, "failed $what")
            return null
        }
    }

    /** Every candidate failed: the original goes up, remuxed when [remux] is allowed, with the notice. */
    private suspend fun failed(
        source: File,
        notices: List<UserNotice>,
        reason: String?,
        remux: Boolean,
    ): VideoCompressionResult = VideoCompressionResult.Original(if (remux) strip(source) else null, notices + UploadNotice.VideoCompressionFailed(reason))

    private companion object {
        const val TAG = "FfmpegVideoCompressor"
    }
}
