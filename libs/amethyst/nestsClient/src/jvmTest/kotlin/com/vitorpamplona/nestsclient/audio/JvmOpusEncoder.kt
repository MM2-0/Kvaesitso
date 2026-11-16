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

import club.minnced.opus.util.OpusLibrary
import com.sun.jna.ptr.PointerByReference
import tomp2p.opuswrapper.Opus
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.ShortBuffer

/**
 * [OpusEncoder] backed by libopus via JNA (`club.minnced:opus-java`).
 * Test-only — JVM tests need a host-side codec and `MediaCodec` is
 * Android-only. The natives are bundled in the jar (linux-x86-64,
 * linux-aarch64, darwin, win32, win32-x86-64), unpacked from
 * [OpusLibrary.loadFromJar] on first use.
 *
 * Mirror of [MediaCodecOpusEncoder]'s contract: 48 kHz mono /
 * stereo PCM 16-bit input → Opus packet bytes. Stateful
 * (libopus carries forward predictor state); use one instance per
 * outgoing track.
 */
class JvmOpusEncoder(
    private val sampleRate: Int = AudioFormat.SAMPLE_RATE_HZ,
    private val channelCount: Int = AudioFormat.DEFAULT_CHANNELS,
    targetBitrate: Int = DEFAULT_BITRATE_BPS,
) : OpusEncoder {
    private val handle: PointerByReference

    /** Sized for libopus's worst-case output for one 20 ms frame. */
    private val out = ByteBuffer.allocateDirect(MAX_OPUS_PACKET_BYTES).order(ByteOrder.nativeOrder())

    init {
        ensureNativesLoaded()
        val err = IntBuffer.allocate(1)
        handle =
            Opus.INSTANCE.opus_encoder_create(
                sampleRate,
                channelCount,
                Opus.OPUS_APPLICATION_AUDIO,
                err,
            )
        check(err.get(0) == 0) { "opus_encoder_create failed: error ${err.get(0)}" }
        Opus.INSTANCE.opus_encoder_ctl(handle, Opus.OPUS_SET_BITRATE_REQUEST, targetBitrate)
    }

    override fun encode(pcm: ShortArray): ByteArray {
        // libopus wants exactly one frame at a time; for 48 kHz mono
        // that's `FRAME_SIZE_SAMPLES` samples. The interface contract
        // doesn't enforce length, so we pass the caller's array as-is
        // and let libopus's frame-size validator reject mis-sizes.
        val frameSize = pcm.size / channelCount
        val pcmBuffer = ShortBuffer.wrap(pcm)
        out.clear()
        val n = Opus.INSTANCE.opus_encode(handle, pcmBuffer, frameSize, out, out.capacity())
        check(n > 0) { "opus_encode returned $n (negative is an error)" }
        // JNA writes to the native buffer but doesn't advance the JVM
        // position; reset to 0 and absolute-read `n` bytes out.
        val packet = ByteArray(n)
        out.position(0)
        out.get(packet, 0, n)
        return packet
    }

    override fun release() {
        Opus.INSTANCE.opus_encoder_destroy(handle)
    }

    companion object {
        const val DEFAULT_BITRATE_BPS: Int = 32_000

        /** libopus's worst-case packet size; spec says ≤ 1275 per channel × 3 frames. */
        private const val MAX_OPUS_PACKET_BYTES: Int = 4_000

        @Volatile private var nativesLoaded: Boolean = false
        private val loadLock = Any()

        internal fun ensureNativesLoaded() {
            if (nativesLoaded) return
            synchronized(loadLock) {
                if (nativesLoaded) return
                if (OpusLibrary.isSupportedPlatform()) {
                    check(OpusLibrary.loadFromJar()) { "OpusLibrary.loadFromJar() returned false" }
                } else {
                    // opus-java 1.1.1's bundled `natives/darwin/libopus.dylib`
                    // is x86_64-only, so `isSupportedPlatform()` returns
                    // false on Apple Silicon (and on any host the jar
                    // doesn't ship a binary for). Fall back to a
                    // system-installed libopus: probe a small set of
                    // canonical locations and `System.load` it directly
                    // so its symbols are in the process. JNA's lazy
                    // [Opus.INSTANCE] init falls back to RTLD_DEFAULT
                    // for symbol lookup when `jna.library.path` doesn't
                    // resolve the bare name, so preloading by absolute
                    // path is the most reliable cross-host approach
                    // (`brew install opus` on macOS, `apt install libopus0`
                    // / `yum install opus` on linux). After the load, touch
                    // [Opus.INSTANCE] so any remaining linkage issue
                    // surfaces here rather than at the first encode call.
                    val candidates =
                        listOf(
                            "/opt/homebrew/opt/opus/lib/libopus.dylib",
                            "/usr/local/opt/opus/lib/libopus.dylib",
                            "/usr/lib/x86_64-linux-gnu/libopus.so.0",
                            "/usr/lib/aarch64-linux-gnu/libopus.so.0",
                            "/usr/lib64/libopus.so.0",
                        )
                    val found = candidates.firstOrNull { java.io.File(it).exists() }
                    checkNotNull(found) {
                        "club.minnced:opus-java natives not available for this platform " +
                            "and no system libopus found at any of: $candidates"
                    }
                    System.load(found)
                    Opus.INSTANCE
                }
                nativesLoaded = true
            }
        }
    }
}
