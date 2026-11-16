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
package com.vitorpamplona.nestsclient.moq

import com.vitorpamplona.quic.Varint

/**
 * Append-only byte buffer used by the MoQ encoders. Doubles in capacity when
 * full. Kept intentionally minimal so this module has no buffer-library
 * dependency — helps keep the KMP surface thin.
 */
class MoqWriter(
    initialCapacity: Int = 64,
) {
    private var buf: ByteArray = ByteArray(initialCapacity)
    private var pos: Int = 0

    val size: Int get() = pos

    fun toByteArray(): ByteArray = buf.copyOf(pos)

    fun writeByte(value: Int) {
        ensure(1)
        buf[pos++] = value.toByte()
    }

    fun writeBytes(bytes: ByteArray) {
        ensure(bytes.size)
        bytes.copyInto(buf, pos)
        pos += bytes.size
    }

    fun writeVarint(value: Long) {
        ensure(Varint.size(value))
        pos += Varint.writeTo(value, buf, pos)
    }

    fun writeVarint(value: Int) = writeVarint(value.toLong())

    /** Write a length-prefixed byte array (varint length + contents). */
    fun writeLengthPrefixedBytes(bytes: ByteArray) {
        writeVarint(bytes.size.toLong())
        writeBytes(bytes)
    }

    /** Write a length-prefixed UTF-8 string. */
    fun writeLengthPrefixedString(s: String) = writeLengthPrefixedBytes(s.encodeToByteArray())

    private fun ensure(more: Int) {
        if (pos + more > buf.size) {
            var newSize = buf.size * 2
            while (newSize < pos + more) newSize *= 2
            buf = buf.copyOf(newSize)
        }
    }
}

/**
 * Read cursor over a fully-buffered frame payload. Throws [MoqCodecException]
 * on under-read so callers get a typed error surface instead of opaque
 * [IndexOutOfBoundsException]s.
 */
class MoqReader(
    private val src: ByteArray,
    private var pos: Int = 0,
    private val end: Int = src.size,
) {
    val remaining: Int get() = end - pos

    fun hasMore(): Boolean = pos < end

    fun readByte(): Int {
        require(1)
        return src[pos++].toInt() and 0xFF
    }

    fun readBytes(n: Int): ByteArray {
        require(n)
        val out = src.copyOfRange(pos, pos + n)
        pos += n
        return out
    }

    fun readVarint(): Long {
        val dec =
            Varint.decode(src, pos)
                ?: throw MoqCodecException("truncated varint at offset=$pos (remaining=$remaining)")
        require(dec.bytesConsumed)
        pos += dec.bytesConsumed
        return dec.value
    }

    fun readLengthPrefixedBytes(): ByteArray {
        val len = readVarint()
        if (len < 0 || len > Int.MAX_VALUE) throw MoqCodecException("absurd length prefix: $len")
        return readBytes(len.toInt())
    }

    fun readLengthPrefixedString(): String = readLengthPrefixedBytes().decodeToString()

    private fun require(n: Int) {
        if (pos + n > end) {
            throw MoqCodecException(
                "short read at offset=$pos: wanted $n bytes, have $remaining",
            )
        }
    }
}

/** Thrown when an encoded MoQ frame is malformed or truncated. */
open class MoqCodecException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Thrown when a MoQ control frame uses a type code we don't recognise but
 * is otherwise well-framed (length-prefixed, fully buffered). Carries the
 * full frame size so the caller's pump can skip just this message and
 * keep parsing the next one — versus a generic [MoqCodecException] which
 * means "this byte stream is corrupted, drop everything".
 */
class MoqUnknownTypeException(
    val typeCode: Long,
    val bytesConsumed: Int,
) : MoqCodecException("unknown MoQ message type: 0x${typeCode.toString(16)}")
