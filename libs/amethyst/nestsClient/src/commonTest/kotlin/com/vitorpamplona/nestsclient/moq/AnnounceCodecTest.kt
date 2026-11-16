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
import kotlin.test.assertIs

class AnnounceCodecTest {
    @Test
    fun announce_round_trip_with_parameters() {
        val msg =
            Announce(
                namespace = TrackNamespace.of("nests", "test-room"),
                parameters = listOf(SetupParameter(0x10L, byteArrayOf(0x01, 0x02))),
            )
        val decoded = MoqCodec.decode(MoqCodec.encode(msg))!!.message
        assertEquals(msg, assertIs<Announce>(decoded))
    }

    @Test
    fun announce_ok_round_trip() {
        val msg = AnnounceOk(namespace = TrackNamespace.of("ns"))
        val decoded = MoqCodec.decode(MoqCodec.encode(msg))!!.message
        assertEquals(msg, assertIs<AnnounceOk>(decoded))
    }

    @Test
    fun announce_error_round_trip() {
        val msg =
            AnnounceError(
                namespace = TrackNamespace.of("nests", "denied-room"),
                errorCode = 403,
                reasonPhrase = "namespace already taken",
            )
        val decoded = MoqCodec.decode(MoqCodec.encode(msg))!!.message
        assertEquals(msg, assertIs<AnnounceError>(decoded))
    }

    @Test
    fun unannounce_round_trip() {
        val msg = Unannounce(namespace = TrackNamespace.of("nests", "test-room"))
        val decoded = MoqCodec.decode(MoqCodec.encode(msg))!!.message
        assertEquals(msg, assertIs<Unannounce>(decoded))
    }

    @Test
    fun subscribe_done_round_trip() {
        val msg =
            SubscribeDone(
                subscribeId = 42,
                statusCode = 0,
                streamCount = 17,
                reasonPhrase = "subscription expired",
            )
        val decoded = MoqCodec.decode(MoqCodec.encode(msg))!!.message
        assertEquals(msg, assertIs<SubscribeDone>(decoded))
    }

    @Test
    fun announce_then_announce_ok_decode_in_sequence() {
        val ns = TrackNamespace.of("nests", "abc")
        val ann = Announce(ns)
        val ok = AnnounceOk(ns)
        val bytes = MoqCodec.encode(ann) + MoqCodec.encode(ok)

        val first = MoqCodec.decode(bytes, offset = 0)!!
        val second = MoqCodec.decode(bytes, offset = first.bytesConsumed)!!

        assertIs<Announce>(first.message)
        assertIs<AnnounceOk>(second.message)
        assertEquals(bytes.size, first.bytesConsumed + second.bytesConsumed)
    }

    @Test
    fun every_publisher_message_type_has_a_known_code() {
        // Guards against accidentally reordering MoqMessageType enum entries.
        assertEquals(0x06L, MoqMessageType.Announce.code)
        assertEquals(0x07L, MoqMessageType.AnnounceOk.code)
        assertEquals(0x08L, MoqMessageType.AnnounceError.code)
        assertEquals(0x09L, MoqMessageType.Unannounce.code)
        assertEquals(0x0BL, MoqMessageType.SubscribeDone.code)
    }
}
