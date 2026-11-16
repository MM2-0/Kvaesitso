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
package com.vitorpamplona.nestsclient.interop

import com.vitorpamplona.nestsclient.audio.AudioCapture
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import kotlinx.coroutines.channels.Channel

/**
 * Capture seam driven directly by the test. Each [push] hands one
 * single-sample PCM frame into the broadcaster's pump; [stop] closes
 * the channel so the pump exits cleanly during teardown.
 *
 * Tests pair this with [InteropStubEncoder] to round-trip a
 * deterministic, byte-for-byte verifiable payload through the relay.
 */
class InteropDriverCapture : AudioCapture {
    private val frames = Channel<ShortArray>(capacity = Channel.UNLIMITED)

    @Volatile private var started: Boolean = false

    override fun start() {
        started = true
    }

    override suspend fun readFrame(): ShortArray? {
        if (!started) return null
        return frames.receiveCatching().getOrNull()
    }

    override fun stop() {
        frames.close()
    }

    /** Convenience for the common `push one PCM byte` shape. */
    fun push(byteValue: Int) {
        frames.trySend(shortArrayOf(byteValue.toShort()))
    }

    fun push(pcm: ShortArray) {
        frames.trySend(pcm)
    }
}

/**
 * Encoder that emits `<prefix><lo-byte-of-first-pcm-sample>` for each
 * frame. Lets tests assert byte-for-byte payload integrity through the
 * relay without pulling a real Opus encoder onto the JVM build.
 */
class InteropStubEncoder(
    private val prefix: ByteArray,
) : OpusEncoder {
    override fun encode(pcm: ShortArray): ByteArray = prefix + byteArrayOf(pcm.first().toByte())

    override fun release() = Unit
}
