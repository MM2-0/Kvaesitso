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
import kotlin.test.assertIs

class SubscribeCodecTest {
    @Test
    fun subscribe_round_trip_with_multi_segment_namespace_and_parameters() {
        val msg =
            Subscribe(
                subscribeId = 42,
                trackAlias = 7,
                namespace = TrackNamespace.of("nests", "room-abc"),
                trackName = "speaker-pubkey-hex".encodeToByteArray(),
                subscriberPriority = 0x40,
                groupOrder = 0x02,
                filter = SubscribeFilter.LatestObject,
                parameters = listOf(SetupParameter(0x10L, byteArrayOf(0x01))),
            )
        val decoded = MoqCodec.decode(MoqCodec.encode(msg))!!.message
        assertEquals(msg, assertIs<Subscribe>(decoded))
    }

    @Test
    fun subscribe_rejects_unsupported_filter_at_construction() {
        assertFailsWith<IllegalArgumentException> {
            Subscribe(
                subscribeId = 1,
                trackAlias = 1,
                namespace = TrackNamespace.of("ns"),
                trackName = "t".encodeToByteArray(),
                filter = SubscribeFilter.AbsoluteStart,
            )
        }
    }

    @Test
    fun subscribe_ok_no_content() {
        val msg =
            SubscribeOk(
                subscribeId = 42,
                expiresMs = 60_000,
                groupOrder = 2,
                contentExists = false,
            )
        val decoded = MoqCodec.decode(MoqCodec.encode(msg))!!.message
        assertEquals(msg, assertIs<SubscribeOk>(decoded))
    }

    @Test
    fun subscribe_ok_with_content_existing() {
        val msg =
            SubscribeOk(
                subscribeId = 3,
                expiresMs = 30_000,
                groupOrder = 1,
                contentExists = true,
                largestGroupId = 1_000,
                largestObjectId = 9,
                parameters = listOf(SetupParameter(0x20, byteArrayOf(0x01, 0x02))),
            )
        val decoded = MoqCodec.decode(MoqCodec.encode(msg))!!.message
        assertEquals(msg, assertIs<SubscribeOk>(decoded))
    }

    @Test
    fun subscribe_ok_construction_requires_matching_content_exists_and_largest_ids() {
        assertFailsWith<IllegalArgumentException> {
            SubscribeOk(1, 0, 0, contentExists = true)
        }
        assertFailsWith<IllegalArgumentException> {
            SubscribeOk(1, 0, 0, contentExists = false, largestGroupId = 5, largestObjectId = 1)
        }
    }

    @Test
    fun subscribe_error_round_trip() {
        val msg =
            SubscribeError(
                subscribeId = 9,
                errorCode = 404,
                reasonPhrase = "track not found",
                trackAlias = 9,
            )
        val decoded = MoqCodec.decode(MoqCodec.encode(msg))!!.message
        assertEquals(msg, assertIs<SubscribeError>(decoded))
    }

    @Test
    fun unsubscribe_round_trip() {
        val msg = Unsubscribe(subscribeId = 9_001)
        val decoded = MoqCodec.decode(MoqCodec.encode(msg))!!.message
        assertEquals(msg, assertIs<Unsubscribe>(decoded))
    }

    @Test
    fun concatenated_subscribe_then_subscribe_ok_decode_in_sequence() {
        val sub =
            Subscribe(
                subscribeId = 1,
                trackAlias = 1,
                namespace = TrackNamespace.of("ns"),
                trackName = "t".encodeToByteArray(),
            )
        val ok = SubscribeOk(1, 5_000, 0, contentExists = false)
        val bytes = MoqCodec.encode(sub) + MoqCodec.encode(ok)

        val first = MoqCodec.decode(bytes, offset = 0)!!
        val second = MoqCodec.decode(bytes, offset = first.bytesConsumed)!!

        assertIs<Subscribe>(first.message)
        assertIs<SubscribeOk>(second.message)
        assertEquals(bytes.size, first.bytesConsumed + second.bytesConsumed)
    }

    @Test
    fun unknown_filter_code_on_wire_is_rejected_on_decode() {
        // Build a SUBSCRIBE frame with filter_type = 0x99 (not in the enum).
        val payload = MoqWriter()
        payload.writeVarint(1L) // subscribe_id
        payload.writeVarint(1L) // track_alias
        payload.writeVarint(1L) // namespace tuple size
        payload.writeLengthPrefixedString("ns")
        payload.writeLengthPrefixedString("track")
        payload.writeByte(0x80)
        payload.writeByte(0x00)
        payload.writeVarint(0x99L) // unknown filter
        payload.writeVarint(0L) // zero parameters

        val frame = MoqWriter()
        frame.writeVarint(MoqMessageType.Subscribe.code)
        frame.writeVarint(payload.size.toLong())
        frame.writeBytes(payload.toByteArray())

        assertFailsWith<MoqCodecException> { MoqCodec.decode(frame.toByteArray()) }
    }
}
