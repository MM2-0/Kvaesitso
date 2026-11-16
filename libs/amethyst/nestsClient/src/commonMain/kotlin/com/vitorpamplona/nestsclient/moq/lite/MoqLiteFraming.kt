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
package com.vitorpamplona.nestsclient.moq.lite

import com.vitorpamplona.nestsclient.moq.MoqCodecException
import com.vitorpamplona.quic.Varint
import kotlinx.coroutines.CancellationException

/**
 * Streaming frame buffer for moq-lite. Every wire datum on a control
 * stream is a varint length followed by `length` bytes; every uni
 * stream starts with a one-varint type byte then a size-prefixed
 * group header then a sequence of size-prefixed frames until QUIC FIN.
 *
 * Network reads arrive as opaque chunks, so this class holds a rolling
 * buffer and exposes:
 *   - [readVarint] — pull one varint when enough bytes have arrived
 *   - [readSizePrefixed] — pull one size-prefixed payload (varint
 *     length + payload bytes) when complete
 *
 * Both return null when more bytes are needed; the caller buffers
 * additional chunks via [push] and retries.
 *
 * Not thread-safe — used from a single coroutine per stream.
 */
class MoqLiteFrameBuffer {
    // [buf] is the capacity-sized backing array; [size] tracks the
    // high-water mark of valid data inside it. Decoupling capacity from
    // length is what makes power-of-two growth actually amortise — the
    // earlier shape (`buf = grown.copyOf(needed)`) immediately truncated
    // back to `needed`, defeating the doubling and forcing a fresh
    // allocation on every push.
    private var buf: ByteArray = ByteArray(0)
    private var size: Int = 0
    private var pos: Int = 0

    /** Append [chunk] to the buffer, growing/compacting as needed. */
    fun push(chunk: ByteArray) {
        if (chunk.isEmpty()) return
        compact()
        val needed = size + chunk.size
        if (needed > buf.size) {
            // Power-of-two doubling matches MoqWriter's growth policy and
            // avoids quadratic copies under bursty arrivals.
            var newCap = if (buf.size == 0) 64 else buf.size
            while (newCap < needed) newCap *= 2
            val grown = ByteArray(newCap)
            buf.copyInto(grown, 0, 0, size)
            buf = grown
        }
        chunk.copyInto(buf, size, 0, chunk.size)
        size = needed
    }

    /** Try to read one varint. Returns null if not enough bytes. */
    fun readVarint(): Long? {
        if (pos >= size) return null
        val dec = Varint.decode(buf, pos) ?: return null
        // Reject a varint whose declared length runs past the live region
        // — Varint.decode only checks against [buf]'s capacity, but slack
        // capacity is unfilled garbage, not real bytes from the peer.
        if (pos + dec.bytesConsumed > size) return null
        pos += dec.bytesConsumed
        return dec.value
    }

    /**
     * Try to read one size-prefixed payload. Returns the payload bytes
     * (no length prefix) or null if either the length varint or the
     * payload itself isn't fully buffered yet. On invalid lengths
     * (negative, > Int.MAX_VALUE) throws.
     */
    fun readSizePrefixed(): ByteArray? {
        val savedPos = pos
        val len = readVarint() ?: return null
        if (len < 0 || len > Int.MAX_VALUE) {
            throw MoqCodecException("absurd moq-lite size prefix: $len")
        }
        if (pos + len.toInt() > size) {
            // Roll the cursor back so the next call sees the same
            // varint and only commits when the whole payload arrives.
            pos = savedPos
            return null
        }
        val payload = buf.copyOfRange(pos, pos + len.toInt())
        pos += len.toInt()
        return payload
    }

    /** Bytes still buffered after [pos] — exposed for diagnostic / EOF detection. */
    val remaining: Int get() = size - pos

    /** Drop the consumed prefix when half the buffer is dead weight. */
    private fun compact() {
        if (pos == 0) return
        if (pos < size / 2) return
        val live = size - pos
        if (live > 0) buf.copyInto(buf, 0, pos, size)
        size = live
        pos = 0
    }
}

/**
 * Pull *one* size-prefixed message off a chunk channel. Drains chunks
 * one at a time into [buffer] until [MoqLiteFrameBuffer.readSizePrefixed]
 * returns a full payload. Returns null if [chunks] closes before a
 * complete message arrives.
 *
 * Channel-based rather than Flow-based — `collect { throw EarlyExit }`
 * is fragile under virtual-time test dispatchers and doesn't compose
 * well with multiple sequential reads on the same channel. The caller
 * owns the channel and is responsible for keeping it pumped (typically
 * a single launched collector that forwards `incoming` chunks into the
 * channel).
 */
internal suspend fun readOneSizePrefixedFrom(
    chunks: kotlinx.coroutines.channels.ReceiveChannel<ByteArray>,
    buffer: MoqLiteFrameBuffer,
): ByteArray? {
    while (true) {
        buffer.readSizePrefixed()?.let { return it }
        val next =
            try {
                chunks.receive()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                return null
            }
        buffer.push(next)
    }
}
