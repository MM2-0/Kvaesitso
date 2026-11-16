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

import com.vitorpamplona.nestsclient.moq.MoqWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoqLiteFrameBufferTest {
    @Test
    fun reads_size_prefixed_payload_assembled_from_multiple_chunks() {
        val payload = ByteArray(7) { (0x10 + it).toByte() }
        val full =
            MoqWriter()
                .also { it.writeLengthPrefixedBytes(payload) }
                .toByteArray()

        val buf = MoqLiteFrameBuffer()
        // Drip-feed the payload one byte at a time so the buffer has to
        // hold partial state across many push() calls — the "needs more
        // bytes" branch must NOT advance pos when the payload is short.
        for (i in full.indices) {
            assertNull(buf.readSizePrefixed(), "must wait until payload complete")
            buf.push(byteArrayOf(full[i]))
        }
        val readBack = buf.readSizePrefixed()
        assertContentEquals(payload, readBack)
    }

    @Test
    fun back_to_back_payloads_share_one_buffer() {
        val w = MoqWriter()
        w.writeLengthPrefixedBytes(byteArrayOf(0x01, 0x02))
        w.writeLengthPrefixedBytes(byteArrayOf(0x03, 0x04, 0x05))
        w.writeLengthPrefixedBytes(byteArrayOf(0x06))

        val buf = MoqLiteFrameBuffer()
        buf.push(w.toByteArray())
        assertContentEquals(byteArrayOf(0x01, 0x02), buf.readSizePrefixed())
        assertContentEquals(byteArrayOf(0x03, 0x04, 0x05), buf.readSizePrefixed())
        assertContentEquals(byteArrayOf(0x06), buf.readSizePrefixed())
        assertNull(buf.readSizePrefixed(), "no more frames")
    }

    @Test
    fun growth_capacity_amortises_under_many_small_pushes() {
        // Push 10 KB one byte at a time. The earlier shape allocated a
        // fresh ByteArray per push; the size-tracking shape should
        // double-and-keep, so total allocations are O(log N) — and the
        // resulting buffer correctly reads back as one giant payload.
        val payload = ByteArray(10_000) { (it and 0xFF).toByte() }
        val framed =
            MoqWriter()
                .also { it.writeLengthPrefixedBytes(payload) }
                .toByteArray()

        val buf = MoqLiteFrameBuffer()
        for (b in framed) buf.push(byteArrayOf(b))
        assertContentEquals(payload, buf.readSizePrefixed())
    }

    @Test
    fun compact_runs_after_consuming_half_then_pushing_more() {
        // 1) Push frame A and read it (advances pos, leaves 0 live bytes).
        // 2) Push frame B — compact() should fire, dropping the dead
        //    prefix without copying garbage.
        val w = MoqWriter()
        w.writeLengthPrefixedBytes(ByteArray(100) { 0xAA.toByte() })
        val buf = MoqLiteFrameBuffer()
        buf.push(w.toByteArray())
        assertContentEquals(ByteArray(100) { 0xAA.toByte() }, buf.readSizePrefixed())

        val w2 = MoqWriter()
        w2.writeLengthPrefixedBytes(byteArrayOf(0x55, 0x66))
        buf.push(w2.toByteArray())
        assertContentEquals(byteArrayOf(0x55, 0x66), buf.readSizePrefixed())
        assertEquals(0, buf.remaining)
    }

    @Test
    fun varint_must_not_read_into_uninitialised_capacity() {
        // Push 1 byte that's a 2-byte-varint prefix. The buffer's
        // underlying capacity is grown to 64+, but only 1 byte is live.
        // readVarint must NOT consume the second byte of slack capacity
        // as if it were data.
        val buf = MoqLiteFrameBuffer()
        // 0x40 → top 2 bits "01" → varint is 2 bytes long.
        buf.push(byteArrayOf(0x40))
        assertNull(buf.readVarint(), "incomplete varint must return null")
    }
}
