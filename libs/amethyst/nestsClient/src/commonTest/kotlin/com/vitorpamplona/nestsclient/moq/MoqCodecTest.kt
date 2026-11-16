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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class MoqCodecTest {
    @Test
    fun client_setup_round_trip_no_parameters() {
        val msg = ClientSetup(supportedVersions = listOf(MoqVersion.DRAFT_17))
        val encoded = MoqCodec.encode(msg)
        val decoded = MoqCodec.decode(encoded)!!
        assertEquals(encoded.size, decoded.bytesConsumed)
        assertEquals(msg, decoded.message)
    }

    @Test
    fun client_setup_round_trip_multi_version_and_parameters() {
        val msg =
            ClientSetup(
                supportedVersions = listOf(MoqVersion.DRAFT_11, MoqVersion.DRAFT_17),
                parameters =
                    listOf(
                        SetupParameter(SetupParameter.KEY_ROLE, byteArrayOf(0x03)),
                        SetupParameter(SetupParameter.KEY_PATH, "/moq".encodeToByteArray()),
                    ),
            )
        val encoded = MoqCodec.encode(msg)
        val decoded = MoqCodec.decode(encoded)!!
        val roundTripped = assertIs<ClientSetup>(decoded.message)
        assertEquals(msg.supportedVersions, roundTripped.supportedVersions)
        assertEquals(msg.parameters, roundTripped.parameters)
    }

    @Test
    fun server_setup_round_trip() {
        val msg =
            ServerSetup(
                selectedVersion = MoqVersion.DRAFT_17,
                parameters = listOf(SetupParameter(SetupParameter.KEY_MAX_SUBSCRIBE_ID, byteArrayOf(0x40, 0x10))),
            )
        val encoded = MoqCodec.encode(msg)
        val decoded = MoqCodec.decode(encoded)!!
        assertEquals(msg, decoded.message)
    }

    @Test
    fun encode_prefixes_with_type_and_length() {
        val encoded = MoqCodec.encode(ServerSetup(selectedVersion = 1L))
        // ServerSetup's type code is 0x41 (65). That's > 63 so it needs a
        // 2-byte varint: 0x40 0x41. Length of the payload (2 bytes: one byte
        // for varint(version=1), one byte for varint(0 params)) fits in a
        // single-byte varint → 0x02. Payload: 0x01, 0x00. Total 5 bytes.
        assertContentEquals(byteArrayOf(0x40, 0x41, 0x02, 0x01, 0x00), encoded)
    }

    @Test
    fun decode_returns_null_when_frame_is_truncated() {
        val full = MoqCodec.encode(ClientSetup(listOf(MoqVersion.DRAFT_17)))
        // Lose the last byte → caller should buffer more and retry.
        assertNull(MoqCodec.decode(full.copyOf(full.size - 1)))
    }

    @Test
    fun decode_with_offset_reads_from_middle_of_buffer() {
        val a = MoqCodec.encode(ClientSetup(listOf(MoqVersion.DRAFT_17)))
        val b = MoqCodec.encode(ServerSetup(selectedVersion = MoqVersion.DRAFT_17))
        val concatenated = a + b

        val first = MoqCodec.decode(concatenated, offset = 0)!!
        assertEquals(a.size, first.bytesConsumed)
        assertIs<ClientSetup>(first.message)

        val second = MoqCodec.decode(concatenated, offset = first.bytesConsumed)!!
        assertEquals(b.size, second.bytesConsumed)
        assertIs<ServerSetup>(second.message)
    }

    @Test
    fun unknown_message_type_throws_typed_exception_with_full_frame_size() {
        // Craft a frame with message type 0xFFFF (large varint), length 4,
        // four bytes of payload. The pump uses bytesConsumed to skip past
        // the unknown frame instead of dropping the whole buffer.
        val bogus = MoqWriter()
        bogus.writeVarint(0xFFFFL)
        bogus.writeVarint(4L)
        bogus.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        val encoded = bogus.toByteArray()
        val ex = assertFailsWith<MoqUnknownTypeException> { MoqCodec.decode(encoded) }
        assertEquals(0xFFFFL, ex.typeCode)
        assertEquals(encoded.size, ex.bytesConsumed)
    }

    @Test
    fun trailing_bytes_in_payload_are_rejected() {
        // Valid ServerSetup payload of (selectedVersion=1, 0 params) fits in 2 bytes
        // (varint 0x01 + varint 0x00). Craft a frame that declares a 4-byte
        // payload and pads with 2 junk bytes inside the declared window so the
        // decoder sees extra data after the last field.
        val w = MoqWriter()
        w.writeVarint(MoqMessageType.ServerSetup.code)
        w.writeVarint(4L)
        w.writeVarint(1L) // selected version (1 byte)
        w.writeVarint(0L) // 0 parameters (1 byte)
        w.writeByte(0xAA) // trailing junk inside declared payload
        w.writeByte(0xBB)
        assertFailsWith<MoqCodecException> { MoqCodec.decode(w.toByteArray()) }
    }

    @Test
    fun parameters_with_zero_length_value_are_allowed() {
        val msg = ClientSetup(listOf(1L), parameters = listOf(SetupParameter(0x10L, ByteArray(0))))
        val decoded = MoqCodec.decode(MoqCodec.encode(msg))!!
        assertEquals(msg, decoded.message)
    }
}
