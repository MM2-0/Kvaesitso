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
package com.vitorpamplona.nestsclient.audio

import android.media.MediaCodec
import android.media.MediaFormat
import com.vitorpamplona.quartz.utils.Log
import java.nio.ByteOrder

/**
 * [OpusEncoder] backed by Android's [MediaCodec] (`audio/opus`, encoder
 * variant available on API 29+ — the decoder works back to API 21 but the
 * encoder shipped later). One instance per outgoing track.
 *
 * Configuration:
 *   - 48 kHz PCM 16-bit input (matches [AudioFormat.SAMPLE_RATE_HZ]).
 *   - [channelCount] — 1 (mono) or 2 (stereo, L/R interleaved). Drives
 *     the MediaFormat channel count; the supplied PCM frame must hold
 *     `FRAME_SIZE_SAMPLES * channelCount` samples per call.
 *   - Target bitrate ~32 kbit/s VBR — high-quality wideband speech.
 *   - 20 ms frames (the encoder requires the input buffer to hold one frame
 *     at a time for low latency).
 *
 * Default is [AudioFormat.DEFAULT_CHANNELS] (mono) so existing call sites
 * that don't pass a channel count keep the prior behaviour. Pair with a
 * matching [com.vitorpamplona.nestsclient.AudioBroadcastConfig] on the
 * speaker so the published catalog declares the same channel count.
 */
class MediaCodecOpusEncoder(
    private val channelCount: Int = AudioFormat.DEFAULT_CHANNELS,
    private val targetBitrate: Int = DEFAULT_BITRATE_BPS,
) : OpusEncoder {
    init {
        require(channelCount in 1..2) {
            "MediaCodecOpusEncoder supports mono (1) or stereo (2) only, got $channelCount"
        }
    }

    private val codec: MediaCodec =
        try {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply {
                configure(buildFormat(channelCount, targetBitrate), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
        } catch (t: Throwable) {
            throw AudioException(
                AudioException.Kind.DeviceUnavailable,
                "Failed to allocate MediaCodec audio/opus encoder",
                t,
            )
        }

    private val bufferInfo = MediaCodec.BufferInfo()
    private var presentationTimeUs: Long = 0L
    private var released = false

    /**
     * Latch so a single conspicuous log fires the FIRST time we drop a
     * [MediaCodec.BUFFER_FLAG_CODEC_CONFIG] output buffer. Without
     * this latch the same encoder can produce 2-3 CSD buffers in a row
     * (OpusHead + OpusTags on some Android Codec2 stacks) and we'd
     * spam the log; with it, we record the fact once per encoder
     * lifetime and stay quiet thereafter.
     */
    private var loggedCsdSkip = false

    override fun encode(pcm: ShortArray): ByteArray {
        check(!released) { "encoder released" }
        require(pcm.isNotEmpty()) { "PCM frame must not be empty" }

        val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (inputIndex < 0) return ByteArray(0)
        val inputBuffer =
            codec.getInputBuffer(inputIndex)
                ?: throw AudioException(
                    AudioException.Kind.EncoderError,
                    "MediaCodec returned null input buffer at index $inputIndex",
                )
        inputBuffer.clear()
        inputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer().put(pcm)
        val byteCount = pcm.size * AudioFormat.BYTES_PER_SAMPLE
        codec.queueInputBuffer(inputIndex, 0, byteCount, presentationTimeUs, 0)
        presentationTimeUs += FRAME_DURATION_US

        // One PCM frame produces one Opus packet (sometimes after one warmup
        // round). Drain the output queue once. The format-change signal
        // fires at most once on encoder startup; absorb it then re-poll,
        // but never loop on it (some buggy encoders re-emit FORMAT_CHANGED
        // without producing output, which would otherwise busy-spin at
        // 100 Hz against the 10 ms dequeue timeout).
        var formatChangeAbsorbed = false
        // CSD-skip iteration cap. Codec2 stacks have been observed to
        // emit OpusHead AND OpusTags as separate CSD buffers; some
        // future stack might emit more. Cap the per-call CSD-skip
        // count so a buggy encoder that emits CSD on every dequeue
        // can't busy-loop the per-frame budget. 4 is generous: the
        // expected case is 1 (OpusHead) or 2 (OpusHead + OpusTags).
        var csdSkipsThisCall = 0
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            when {
                outputIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputIndex) ?: continue
                    // CODEC_CONFIG buffers carry codec-specific data
                    // (CSD): on the Android `audio/opus` encoder these
                    // are the 19-byte OpusHead identification header
                    // and (on some Codec2 stacks) the OpusTags
                    // comment header. They are NOT Opus packets — they
                    // are decoder-config blobs that should be supplied
                    // out-of-band on the receive side (we already do
                    // this in `MediaCodecOpusDecoder.buildOpusIdHeader`).
                    // Sending them to the wire as audio frames means
                    // the watcher's WebCodecs `AudioDecoder.decode`
                    // sees garbage in the first frame, burning a
                    // warmup slot and producing a click on group
                    // rollover after every JWT-refresh hot-swap.
                    val isCodecConfig =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (isCodecConfig) {
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (!loggedCsdSkip) {
                            loggedCsdSkip = true
                            Log.d("NestTx") {
                                "MediaCodecOpusEncoder skipped ${bufferInfo.size}-byte CODEC_CONFIG (OpusHead/OpusTags) — not an audio frame"
                            }
                        }
                        csdSkipsThisCall += 1
                        if (csdSkipsThisCall >= MAX_CSD_SKIPS_PER_CALL) {
                            // A buggy encoder is emitting CSD on every
                            // dequeue; bail this call rather than
                            // busy-looping the per-frame budget.
                            // Returning empty is the existing "warmup"
                            // contract — broadcaster's
                            // `if (opus.isEmpty()) continue` handles it
                            // and the next encode call retries.
                            Log.w("NestTx") {
                                "MediaCodecOpusEncoder hit MAX_CSD_SKIPS_PER_CALL=$MAX_CSD_SKIPS_PER_CALL; bailing this encode call (encoder may be misbehaving)"
                            }
                            return ByteArray(0)
                        }
                        // Don't return; loop to find the next output
                        // buffer (the next dequeue may be the actual
                        // first audio frame).
                        continue
                    }
                    val opus = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    outputBuffer.get(opus)
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (opus.isNotEmpty()) return opus
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (formatChangeAbsorbed) return ByteArray(0)
                    formatChangeAbsorbed = true
                    continue
                }

                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    return ByteArray(0)
                }

                else -> {
                    return ByteArray(0)
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        return ByteArray(0)
    }

    override fun release() {
        if (released) return
        released = true
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    companion object {
        const val DEFAULT_BITRATE_BPS: Int = 32_000
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val FRAME_DURATION_US = 20_000L

        /**
         * Per-encode-call cap on consecutive
         * [MediaCodec.BUFFER_FLAG_CODEC_CONFIG] outputs we'll skip
         * before bailing the call. Expected steady state is 0; the
         * one-time startup cost is 1 (OpusHead) or 2 (OpusHead +
         * OpusTags on Codec2). 4 leaves headroom for a future stack
         * that emits more without uncapping the loop entirely.
         */
        private const val MAX_CSD_SKIPS_PER_CALL: Int = 4

        private fun buildFormat(
            channelCount: Int,
            bitrate: Int,
        ): MediaFormat =
            MediaFormat
                .createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_OPUS,
                    AudioFormat.SAMPLE_RATE_HZ,
                    channelCount,
                ).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                    setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                    // KEY_AAC_PROFILE on an audio/opus encoder is meaningless
                    // (Opus has no AAC profile); historically it was silently
                    // ignored, but stricter Codec2 stacks on Android 13+
                    // reject the configure() call with IllegalArgumentException
                    // and surface as DeviceUnavailable.
                }
    }
}
