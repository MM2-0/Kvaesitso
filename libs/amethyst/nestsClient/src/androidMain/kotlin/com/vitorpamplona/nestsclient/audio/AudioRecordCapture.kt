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

import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.media.AudioFormat as AndroidAudioFormat

/**
 * [AudioCapture] backed by Android's [AudioRecord] from the
 * VOICE_COMMUNICATION input source — same source LiveKit, WebRTC, and most
 * voice-chat libraries use, so it gets the platform's echo-cancellation and
 * noise-suppression filters when available.
 *
 * **Audio effects (AEC / NS / AGC).** On most modern Android devices the
 * `VOICE_COMMUNICATION` source is enough to engage the platform's echo
 * canceller automatically. On a small set of older / OEM-customised
 * devices it isn't — the AEC engine only attaches when the device is in
 * `MODE_IN_COMMUNICATION`, which an audio-room app deliberately avoids
 * driving (it reroutes everything through the call audio path and shows a
 * "phone call" notification icon). To cover those devices without
 * touching `AudioManager.mode` we explicitly attach the standalone
 * [AcousticEchoCanceler] / [NoiseSuppressor] / [AutomaticGainControl]
 * effects to the AudioRecord's session id. On devices where the source
 * already engages them, attaching a second effect is a no-op (the
 * platform deduplicates by session id) — so this is purely additive.
 *
 * **Permission:** the caller is responsible for holding `RECORD_AUDIO` before
 * calling [start]; this class will throw [AudioException.Kind.DeviceUnavailable]
 * if the OS denies the resource.
 */
class AudioRecordCapture(
    private val source: Int = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
) : AudioCapture {
    private var record: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var stopped = false

    override fun start() {
        check(!stopped) { "capture already stopped" }
        if (record != null) return

        // Microphone capture is mono. The platform mic is a single-channel
        // device (or a fixed multi-mic array routed through a mono mixdown
        // by the AEC stack), so this isn't a per-stream configurable —
        // there's no "stereo mic" path to take. Stereo broadcasts
        // synthesise their second channel elsewhere (e.g. a stereo audio
        // capture for music playback) and bypass [AudioRecordCapture]
        // entirely.
        val channelMask = AndroidAudioFormat.CHANNEL_IN_MONO

        val minBuffer =
            AudioRecord.getMinBufferSize(
                AudioFormat.SAMPLE_RATE_HZ,
                channelMask,
                AndroidAudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBuffer <= 0) {
            throw AudioException(
                AudioException.Kind.DeviceUnavailable,
                "AudioRecord.getMinBufferSize returned $minBuffer for ${AudioFormat.SAMPLE_RATE_HZ} Hz",
            )
        }
        val bufferBytes =
            maxOf(minBuffer, AudioFormat.FRAME_SIZE_SAMPLES * AudioFormat.BYTES_PER_SAMPLE * 4)

        val rec =
            try {
                @Suppress("MissingPermission")
                AudioRecord(source, AudioFormat.SAMPLE_RATE_HZ, channelMask, AndroidAudioFormat.ENCODING_PCM_16BIT, bufferBytes)
            } catch (t: Throwable) {
                throw AudioException(
                    AudioException.Kind.DeviceUnavailable,
                    "Failed to construct AudioRecord (RECORD_AUDIO permission?)",
                    t,
                )
            }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { rec.release() }
            throw AudioException(
                AudioException.Kind.DeviceUnavailable,
                "AudioRecord state=${rec.state} after construction (expected INITIALIZED)",
            )
        }
        try {
            rec.startRecording()
        } catch (t: Throwable) {
            runCatching { rec.release() }
            throw AudioException(
                AudioException.Kind.DeviceUnavailable,
                "AudioRecord.startRecording() failed",
                t,
            )
        }
        record = rec
        // Attach standalone audio effects to the session. This covers
        // devices where the VOICE_COMMUNICATION source alone doesn't
        // engage AEC (typically because they only attach AEC under
        // MODE_IN_COMMUNICATION). All three are best-effort — a device
        // that doesn't support an effect leaves it null without
        // affecting capture.
        attachAudioEffects(rec.audioSessionId)
    }

    private fun attachAudioEffects(sessionId: Int) {
        if (AcousticEchoCanceler.isAvailable()) {
            aec =
                runCatching { AcousticEchoCanceler.create(sessionId)?.apply { enabled = true } }
                    .onFailure { Log.w("NestTx") { "AcousticEchoCanceler.create failed: ${it.message}" } }
                    .getOrNull()
        }
        if (NoiseSuppressor.isAvailable()) {
            ns =
                runCatching { NoiseSuppressor.create(sessionId)?.apply { enabled = true } }
                    .onFailure { Log.w("NestTx") { "NoiseSuppressor.create failed: ${it.message}" } }
                    .getOrNull()
        }
        if (AutomaticGainControl.isAvailable()) {
            agc =
                runCatching { AutomaticGainControl.create(sessionId)?.apply { enabled = true } }
                    .onFailure { Log.w("NestTx") { "AutomaticGainControl.create failed: ${it.message}" } }
                    .getOrNull()
        }
    }

    override suspend fun readFrame(): ShortArray? {
        val rec = record ?: return null
        val frame = ShortArray(AudioFormat.FRAME_SIZE_SAMPLES)
        return withContext(Dispatchers.IO) {
            var read = 0
            while (read < frame.size) {
                if (stopped) return@withContext null
                val n = rec.read(frame, read, frame.size - read)
                if (n < 0) {
                    throw AudioException(
                        AudioException.Kind.PlaybackFailed,
                        "AudioRecord.read returned error code $n",
                    )
                }
                if (n == 0) {
                    // Underrun — wait briefly and retry. This avoids busy-waiting
                    // on a slow producer.
                    kotlinx.coroutines.delay(2)
                    continue
                }
                read += n
            }
            frame
        }
    }

    override fun stop() {
        if (stopped) return
        stopped = true
        // Release the audio effects BEFORE the AudioRecord — they hold
        // a session-id reference and the platform expects effect
        // teardown to precede the AudioRecord.release() that frees
        // the session.
        runCatching { aec?.release() }
        runCatching { ns?.release() }
        runCatching { agc?.release() }
        aec = null
        ns = null
        agc = null
        val rec = record ?: return
        record = null
        runCatching { rec.stop() }
        runCatching { rec.release() }
    }
}
