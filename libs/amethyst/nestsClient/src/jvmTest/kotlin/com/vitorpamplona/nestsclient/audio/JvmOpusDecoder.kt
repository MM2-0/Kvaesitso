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

import com.sun.jna.ptr.PointerByReference
import tomp2p.opuswrapper.Opus
import java.nio.IntBuffer
import java.nio.ShortBuffer

/**
 * [OpusDecoder] backed by libopus via JNA. Mirror of
 * [MediaCodecOpusDecoder] for JVM tests — same per-stream
 * statefulness rules apply.
 */
class JvmOpusDecoder(
    private val sampleRate: Int = AudioFormat.SAMPLE_RATE_HZ,
    private val channelCount: Int = AudioFormat.DEFAULT_CHANNELS,
) : OpusDecoder {
    private val handle: PointerByReference

    /** 120 ms at 48 kHz — Opus's worst-case decode frame size. */
    private val out = ShortBuffer.allocate(sampleRate / 1000 * 120 * channelCount)

    init {
        JvmOpusEncoder.ensureNativesLoaded()
        val err = IntBuffer.allocate(1)
        handle = Opus.INSTANCE.opus_decoder_create(sampleRate, channelCount, err)
        check(err.get(0) == 0) { "opus_decoder_create failed: error ${err.get(0)}" }
    }

    override fun decode(opusPacket: ByteArray): ShortArray {
        out.clear()
        val n =
            Opus.INSTANCE.opus_decode(
                handle,
                opusPacket,
                opusPacket.size,
                out,
                out.capacity() / channelCount,
                // 0 = no FEC — match what MediaCodecOpusDecoder does on
                // a normal-arrival packet.
                0,
            )
        check(n >= 0) { "opus_decode returned $n (negative is an error)" }
        val interleaved = n * channelCount
        val pcm = ShortArray(interleaved)
        out.position(0)
        out.get(pcm, 0, interleaved)
        return pcm
    }

    override fun release() {
        Opus.INSTANCE.opus_decoder_destroy(handle)
    }
}
