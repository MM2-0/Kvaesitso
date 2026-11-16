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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoqObjectDatagramTest {
    @Test
    fun encodes_and_decodes_opus_sized_payload() {
        val opusFrame = ByteArray(80) { (it and 0xFF).toByte() } // 20 ms @ 32 kbit/s Opus ≈ 80 bytes
        val obj =
            MoqObject(
                trackAlias = 7,
                groupId = 12_345,
                objectId = 42,
                publisherPriority = 0x80,
                payload = opusFrame,
            )
        val datagram = MoqObjectDatagram.encode(obj)
        val decoded = MoqObjectDatagram.decode(datagram)
        assertEquals(obj, decoded)
    }

    @Test
    fun zero_length_payload_round_trips() {
        val obj =
            MoqObject(
                trackAlias = 1,
                groupId = 0,
                objectId = 0,
                publisherPriority = 0,
                payload = ByteArray(0),
                status = MoqObject.STATUS_OBJECT_DOES_NOT_EXIST,
            )
        val decoded = MoqObjectDatagram.decode(MoqObjectDatagram.encode(obj))
        assertEquals(obj, decoded)
    }

    @Test
    fun varint_fields_respect_quic_boundaries() {
        // A large trackAlias forces an 8-byte varint, exercising the path
        // in Varint / MoqWriter / MoqReader without ambiguity.
        val obj =
            MoqObject(
                trackAlias = 1L shl 32,
                groupId = 1L shl 16,
                objectId = 1L shl 8,
                publisherPriority = 0x40,
                payload = byteArrayOf(0x01, 0x02, 0x03),
            )
        assertEquals(obj, MoqObjectDatagram.decode(MoqObjectDatagram.encode(obj)))
    }

    @Test
    fun truncated_datagram_is_rejected() {
        val obj =
            MoqObject(
                trackAlias = 1,
                groupId = 2,
                objectId = 3,
                publisherPriority = 0x80,
                payload = byteArrayOf(0x00, 0x01, 0x02, 0x03),
            )
        val full = MoqObjectDatagram.encode(obj)
        // Lose the bytes that carry publisher_priority + status. The remaining
        // bytes end mid-header so the reader must throw.
        val truncated = full.copyOf(3)
        assertFailsWith<MoqCodecException> { MoqObjectDatagram.decode(truncated) }
    }

    @Test
    fun priority_out_of_range_is_rejected_at_construction() {
        assertFailsWith<IllegalArgumentException> {
            MoqObject(
                trackAlias = 1,
                groupId = 0,
                objectId = 0,
                publisherPriority = 256,
                payload = ByteArray(0),
            )
        }
    }
}
