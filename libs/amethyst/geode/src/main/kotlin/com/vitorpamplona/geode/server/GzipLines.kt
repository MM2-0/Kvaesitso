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
package com.vitorpamplona.geode.server

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

/**
 * A gzip body written line by line onto [out], for NIP-FE's streamed answers. Every [flush] is a
 * gzip sync flush: the deflater gives up everything it holds, byte-aligned, so the client can
 * inflate every line written so far. Without it the compressor sits on the first lines until its
 * window fills, and streaming delivers nothing until the answer is nearly done.
 */
internal class GzipLines(
    private val out: ByteWriteChannel,
) {
    private val compressed = Pending()

    // Level 1: on Nostr events it compresses to ~42% of raw against ~39% at the JDK's default 6, for
    // about 2.5x less CPU; ids, keys and signatures are hex and barely compress at any level.
    private val gzip =
        object : GZIPOutputStream(compressed, BUFFER, true) {
            init {
                def.setLevel(Deflater.BEST_SPEED)
            }
        }

    /** Compresses [line] and its newline. Moves compressed bytes to the socket once enough piled up, so a long burst still meets backpressure. */
    suspend fun line(line: String) {
        gzip.write(line.encodeToByteArray())
        gzip.write(NEWLINE)
        if (compressed.size() >= BUFFER) drain()
    }

    /** A sync flush, then everything onto the socket. */
    suspend fun flush() {
        gzip.flush()
        drain()
        out.flush()
    }

    /** Writes the gzip trailer; the body is complete after this. */
    suspend fun finish() {
        gzip.finish()
        drain()
        out.flush()
    }

    /** Frees the deflater, finished or not. */
    fun close() = gzip.close()

    private suspend fun drain() {
        if (compressed.size() == 0) return
        compressed.writeTo(out)
        compressed.reset()
    }

    /** The compressed bytes not yet on the socket, written from its own array rather than a copy. */
    private class Pending : ByteArrayOutputStream(BUFFER) {
        suspend fun writeTo(out: ByteWriteChannel) = out.writeFully(buf, 0, count)
    }

    companion object {
        private const val BUFFER = 16 * 1024
        private val NEWLINE = byteArrayOf('\n'.code.toByte())
    }
}
