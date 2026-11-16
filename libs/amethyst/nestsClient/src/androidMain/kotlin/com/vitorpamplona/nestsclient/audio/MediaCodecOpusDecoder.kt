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
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * [OpusDecoder] backed by Android's [MediaCodec] (`audio/opus`, available on
 * API 21+). One instance per track — Opus carries forward predictor state
 * across packets, so sharing a decoder across speakers would cause clicks.
 *
 * Configuration:
 *   - 48 kHz signed 16-bit PCM output (Opus's internal sample rate;
 *     MediaCodec's Opus decoder always emits 48 kHz regardless of the
 *     OpusHead `inputSampleRate` field).
 *   - [channelCount] — 1 (mono) or 2 (stereo) interleaved L/R. Drives both
 *     the MediaFormat channel-count and the OpusHead CSD-0 channel byte.
 *     A web publisher (kixelated/hang) emits stereo when the user's
 *     AudioContext picks a stereo input; without matching channel
 *     configuration the decoder either downmixes with artifacts or
 *     refuses the packet.
 *   - CSD-0: Opus identification header per RFC 7845 §5.1, 19 bytes
 *     (mapping family 0; covers both mono and stereo with implicit
 *     L,R interleaving).
 *   - CSD-1 / CSD-2: pre-skip + seek pre-roll, both zero (we don't seek).
 *
 * Default is [AudioFormat.DEFAULT_CHANNELS] (mono) so existing call sites
 * that don't pass a channel count continue to behave exactly as before.
 */
class MediaCodecOpusDecoder(
    private val channelCount: Int = AudioFormat.DEFAULT_CHANNELS,
    /**
     * Source sample rate in Hz. Drives the OpusHead `inputSampleRate`
     * field and the MediaFormat `audio/opus` sample-rate hint.
     *
     * **Note on Opus's actual decode rate**: Opus always decodes at
     * 48 kHz internally regardless of [sampleRate] (the codec's
     * design — RFC 6716). Android's Codec2 `audio/opus` decoder
     * always emits 48 kHz PCM. So this parameter is informational at
     * the OpusHead level and doesn't affect the PCM rate the
     * downstream [AudioPlayer] sees. We thread it through anyway so
     * the decoder declaration matches what the catalog says, and so a
     * future codec or container variant that DOES respect input
     * sample rate (e.g. a non-Opus rendition) gets the correct
     * configuration.
     */
    private val sampleRate: Int = AudioFormat.SAMPLE_RATE_HZ,
) : OpusDecoder {
    init {
        require(channelCount in 1..2) {
            "MediaCodecOpusDecoder supports mono (1) or stereo (2) only, got $channelCount"
        }
        require(sampleRate > 0) {
            "MediaCodecOpusDecoder sampleRate must be positive, got $sampleRate"
        }
    }

    private val codec: MediaCodec =
        try {
            MediaCodec
                .createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
                .apply {
                    configure(buildFormat(channelCount, sampleRate), null, null, 0)
                    start()
                }.also {
                    Log.d("NestPlay") {
                        "MediaCodecOpusDecoder allocated codec='${it.name}' channelCount=$channelCount"
                    }
                }
        } catch (t: Throwable) {
            Log.w("NestPlay") {
                "MediaCodec audio/opus decoder allocation FAILED: ${t::class.simpleName}: ${t.message}"
            }
            throw AudioException(
                AudioException.Kind.DeviceUnavailable,
                "Failed to allocate MediaCodec audio/opus decoder",
                t,
            )
        }

    private val bufferInfo = MediaCodec.BufferInfo()
    private var presentationTimeUs: Long = 0L
    private var released = false

    override fun decode(opusPacket: ByteArray): ShortArray {
        check(!released) { "decoder released" }

        // Pre-sized output. Write decoded samples directly into the array
        // via ShortBuffer.get(dst, off, len) — the previous shape went
        // through ArrayList<Short>, which boxed every PCM sample
        // (~48 000 alloc/sec/speaker on the audio hot path).
        //
        // Stereo Opus emits L/R-interleaved samples, so a 20 ms / 960-
        // sample frame produces `FRAME_SIZE_SAMPLES * channelCount`
        // shorts — twice as many for stereo as for mono.
        val out = ShortArray(AudioFormat.FRAME_SIZE_SAMPLES * channelCount)
        var outPos = 0

        // 1. Acquire an input slot. On rare back-pressure (output buffers
        //    haven't been drained fast enough → all input slots are tied
        //    up), drain whatever output IS ready first to free slots,
        //    then retry input dequeue with a longer timeout. The previous
        //    shape returned `ShortArray(0)` immediately when the first
        //    10 ms dequeue missed, turning every transient stall (thermal
        //    throttle, GC pause) into a 20 ms audio gap.
        var inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (inputIndex < 0) {
            outPos = drainAvailableOutput(out, outPos)
            inputIndex = codec.dequeueInputBuffer(DEQUEUE_RETRY_TIMEOUT_US)
            if (inputIndex < 0) {
                // Genuine input starvation past the retry window. Drop
                // this packet (presentationTimeUs is NOT advanced — Opus
                // PLC will paper over the gap on the listener) and
                // return whatever we drained from output so the player
                // doesn't underrun on the back of one tight cycle.
                return if (outPos > 0) out.copyOf(outPos) else ShortArray(0)
            }
        }
        val inputBuffer =
            codec.getInputBuffer(inputIndex)
                ?: throw AudioException(
                    AudioException.Kind.DecoderError,
                    "MediaCodec returned null input buffer at index $inputIndex",
                )
        inputBuffer.clear()
        inputBuffer.put(opusPacket)
        codec.queueInputBuffer(inputIndex, 0, opusPacket.size, presentationTimeUs, 0)
        // Advance presentation time by one 20 ms frame.
        presentationTimeUs += FRAME_DURATION_US

        // 2. Drain whatever output is ready right now. A single Opus
        //    packet typically yields exactly one output buffer, but
        //    [drainAvailableOutput] tolerates an INFO_OUTPUT_FORMAT_CHANGED
        //    that some devices emit before the first PCM frame.
        outPos = drainAvailableOutput(out, outPos)
        return if (outPos == out.size) out else out.copyOf(outPos)
    }

    /**
     * Drain any output buffers MediaCodec has ready, copying samples into
     * [out] starting at [startPos]. Returns the new write position.
     * Stops on the first empty / EOS / TRY_AGAIN_LATER signal — never
     * blocks past the [DEQUEUE_TIMEOUT_US] dequeue probe.
     */
    private fun drainAvailableOutput(
        out: ShortArray,
        startPos: Int,
    ): Int {
        var outPos = startPos
        var formatChangeAbsorbed = false
        drain@ while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            when {
                outputIndex >= 0 -> {
                    val outputBuffer =
                        codec.getOutputBuffer(outputIndex)
                            ?: continue@drain
                    if (bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shorts = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val toCopy = minOf(shorts.remaining(), out.size - outPos)
                        if (toCopy > 0) {
                            shorts.get(out, outPos, toCopy)
                            outPos += toCopy
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    // No more buffered output for this packet.
                    if (bufferInfo.size == 0) break
                    if (outPos >= out.size) break
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // The format change carries no audio data; absorb it
                    // once and loop to read the actual PCM frame. Buggy
                    // decoders that re-fire format-change without
                    // producing output would otherwise busy-spin.
                    if (formatChangeAbsorbed) break
                    formatChangeAbsorbed = true
                    continue@drain
                }

                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    break
                }

                else -> {
                    break
                }
            }
        }
        return outPos
    }

    override fun release() {
        if (released) return
        released = true
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    companion object {
        private const val DEQUEUE_TIMEOUT_US = 10_000L // 10 ms

        /**
         * Fallback timeout after the first input dequeue misses + we
         * drain pending output to free slots. Deliberately well under
         * the 20 ms frame cadence so a tight back-pressure window is
         * absorbed without falling off the per-frame budget. The
         * previous code took a 0 ms retry (i.e. dropped the frame
         * outright on the first miss), which turned every transient
         * stall into ~20 ms of silence. 5 ms gives MediaCodec time to
         * free a slot after the output drain without blowing the
         * frame budget.
         */
        private const val DEQUEUE_RETRY_TIMEOUT_US = 5_000L // 5 ms

        private const val FRAME_DURATION_US = 20_000L // 20 ms

        private fun buildFormat(
            channelCount: Int,
            sampleRate: Int,
        ): MediaFormat {
            val format =
                MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_OPUS,
                    sampleRate,
                    channelCount,
                )
            format.setByteBuffer("csd-0", ByteBuffer.wrap(buildOpusIdHeader(channelCount, sampleRate)))
            // Pre-skip + seek pre-roll: both zero, encoded as little-endian
            // 64-bit nanoseconds per Android's MediaCodec contract.
            format.setByteBuffer("csd-1", ByteBuffer.wrap(zeroLongLe()))
            format.setByteBuffer("csd-2", ByteBuffer.wrap(zeroLongLe()))
            return format
        }

        private fun buildOpusIdHeader(
            channelCount: Int,
            sampleRate: Int,
        ): ByteArray {
            // RFC 7845 §5.1 — 19 bytes, mapping family 0 (covers mono and
            // stereo with implicit L,R interleaving; no per-channel
            // mapping table required).
            val buf = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("OpusHead".encodeToByteArray()) // 8 bytes magic
            buf.put(1.toByte()) // version
            buf.put(channelCount.toByte()) // channel count (1 or 2)
            buf.putShort(0) // pre-skip
            buf.putInt(sampleRate) // input sample rate
            buf.putShort(0) // output gain (Q7.8 dB)
            buf.put(0.toByte()) // mapping family 0
            return buf.array()
        }

        private fun zeroLongLe(): ByteArray =
            ByteBuffer
                .allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(0L)
                .array()
    }
}
