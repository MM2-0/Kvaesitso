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

/**
 * PCM audio format the audio pipeline produces and consumes.
 *
 * Sample rate + frame cadence are pipeline-wide invariants (48 kHz Opus,
 * 20 ms frames) and live as flat constants. Channel count is **per-stream**:
 * a microphone is mono, a stereo broadcast is two-channel, and a single
 * listener may simultaneously decode tracks of different shapes. Call sites
 * either inline `1` (the device is intrinsically mono — e.g. the production
 * mic) or take a `channelCount` parameter, plumbed in from a catalog (on
 * the listener side) or from an [com.vitorpamplona.nestsclient.AudioBroadcastConfig]
 * (on the speaker side). [DEFAULT_CHANNELS] is what call sites use as the
 * factory default when they want the historical mono behaviour.
 */
object AudioFormat {
    const val SAMPLE_RATE_HZ: Int = 48_000

    /**
     * Default channel count for call sites that don't yet thread a
     * per-stream override through. Mono — matches what the pipeline
     * shipped before stereo support landed. Use this only as a default
     * value; do NOT assume every audio track is mono just because this
     * exists.
     */
    const val DEFAULT_CHANNELS: Int = 1

    /** 20 ms at 48 kHz. */
    const val FRAME_SIZE_SAMPLES: Int = 960

    /**
     * Duration of one Opus frame in microseconds — derived from
     * [FRAME_SIZE_SAMPLES] / [SAMPLE_RATE_HZ]. Used by the publisher
     * to stamp each frame at frame-index × this value, giving hang.js's
     * watcher a perfect 20 ms cadence even when the broadcaster's send
     * loop suspends on transport backpressure.
     */
    const val FRAME_DURATION_US: Long = (FRAME_SIZE_SAMPLES.toLong() * 1_000_000L) / SAMPLE_RATE_HZ

    /** Bytes per PCM 16-bit sample. */
    const val BYTES_PER_SAMPLE: Int = 2
}

/**
 * Decoder for one Opus frame at a time.
 *
 * Implementations are stateful — Opus carries forward predictor state across
 * frames — so a single decoder must be used by a single track for the
 * lifetime of that subscription. Call [release] when the track ends.
 */
interface OpusDecoder {
    /**
     * Decode one Opus packet (the bytes of an OBJECT_DATAGRAM payload from
     * MoQ) into PCM 16-bit signed mono samples. Returns an empty array if
     * the decoder needs more input before producing output (some codec
     * pipelines have a ramp-up frame), but never throws on a well-formed
     * packet.
     */
    fun decode(opusPacket: ByteArray): ShortArray

    fun release()
}

/**
 * Encoder for one PCM frame at a time.
 *
 * Mirror of [OpusDecoder] for the publish direction. Like the decoder, Opus
 * encoder state is per-stream — one instance per outgoing track.
 */
interface OpusEncoder {
    /**
     * Encode one PCM frame (typically [AudioFormat.FRAME_SIZE_SAMPLES] samples
     * of signed 16-bit mono at [AudioFormat.SAMPLE_RATE_HZ]) into one Opus
     * packet. Returns an empty array if the encoder is still warming up
     * (some pipelines need a few frames before producing output).
     */
    fun encode(pcm: ShortArray): ByteArray

    fun release()
}

/**
 * Source for PCM audio capture. Implementations open the device's microphone
 * and produce one frame at a time via [readFrame].
 */
interface AudioCapture {
    /** Allocate the microphone resource and begin capturing. */
    fun start()

    /**
     * Read one PCM frame ([AudioFormat.FRAME_SIZE_SAMPLES] samples). Suspends
     * until enough samples are available. Returns null when [stop] is called.
     */
    suspend fun readFrame(): ShortArray?

    /** Stop capture and release the microphone. After this, [readFrame] returns null. */
    fun stop()
}

/**
 * Sink for PCM audio playback. Implementations buffer internally — [enqueue]
 * may suspend if the device's playback buffer is full.
 *
 * **Two-phase startup.** [start] allocates the underlying device + per-instance
 * resources but does NOT begin consuming samples; [beginPlayback] flips the
 * device into the playing state. Splitting the two lets [com.vitorpamplona.nestsclient.audio.NestPlayer]
 * pre-roll a few decoded frames into the device's buffer before playback
 * starts, so the AudioTrack has slack the moment the hardware begins pulling
 * samples. Calling [enqueue] between [start] and [beginPlayback] is allowed
 * and is the intended pattern. Implementations that don't need the
 * distinction (test fakes, software-only sinks) can leave the default
 * [beginPlayback] no-op alone.
 */
interface AudioPlayer {
    /**
     * Allocate underlying audio resources. The returned device is in a
     * "ready, not playing" state — [enqueue] is allowed but the hardware
     * doesn't consume samples until [beginPlayback] is called. Throws on
     * device-unavailable so callers (typically
     * [com.vitorpamplona.nestsclient.audio.NestPlayer.play]) get a
     * synchronous failure they can roll back the subscription on.
     */
    fun start()

    /**
     * Transition the allocated device into the playing state. The
     * hardware begins pulling samples from whatever's already been
     * [enqueue]d. Default no-op so test fakes / software-only sinks
     * don't have to grow a method they'll never use; production
     * Android implementations override to call `AudioTrack.play()`.
     */
    fun beginPlayback() {}

    /**
     * Feed one PCM frame (any length, but typically [AudioFormat.FRAME_SIZE_SAMPLES]
     * samples) into the playback queue.
     */
    suspend fun enqueue(pcm: ShortArray)

    /**
     * Toggle output silence without halting the underlying decode/network
     * pipeline. The producer keeps pushing PCM into [enqueue]; the device
     * just stops emitting sound. Default is unmuted.
     *
     * Used so the audio-room UI mute button is instant — nothing to
     * re-handshake on unmute. Setting before [start] is allowed; the value
     * is applied when the device opens.
     */
    fun setMuted(muted: Boolean)

    /**
     * Per-stream output gain, range `[0.0, 1.0]`. 1.0 is the device
     * default; 0.0 silences this stream while leaving any other
     * concurrent players untouched. Used to "hush" one speaker
     * locally without affecting the rest of the room.
     *
     * Independent of [setMuted]: muted streams are silent regardless of
     * gain, and an unmuted stream at gain 0 is still silent. The two
     * controls compose multiplicatively. Default gain is 1.0.
     *
     * Default implementation is a no-op so existing fakes / capture-only
     * actuals don't have to grow a method they'll never use.
     */
    fun setVolume(volume: Float) {}

    /** Stop playback and release resources. After this, the player is unusable. */
    fun stop()
}

/**
 * Audio-pipeline exception type that lets UI code distinguish between
 * recoverable codec/IO errors and fatal device-resource failures.
 */
class AudioException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    enum class Kind {
        /** Decoder rejected an Opus packet (corrupted bytes, unsupported config). */
        DecoderError,

        /** Encoder rejected a PCM frame. */
        EncoderError,

        /** Audio device resource (AudioTrack/AudioRecord) couldn't be allocated. */
        DeviceUnavailable,

        /** Underlying audio device threw mid-playback. */
        PlaybackFailed,
    }
}
