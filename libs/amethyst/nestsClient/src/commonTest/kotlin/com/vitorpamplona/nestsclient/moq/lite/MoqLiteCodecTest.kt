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
import com.vitorpamplona.nestsclient.moq.MoqReader
import com.vitorpamplona.quic.Varint
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Encode/decode round-trip tests for every moq-lite (Lite-03) message
 * type the client uses. Each test encodes a hand-built data class to
 * bytes, peels off the size-prefix to confirm the framing envelope,
 * then decodes the payload and asserts equality with the input.
 *
 * Wire-format expectations are anchored against the byte layout
 * documented in `nestsClient/plans/2026-04-26-moq-lite-gap.md` (which
 * cites `kixelated/moq-rs/rs/moq-lite/src/`).
 */
class MoqLiteCodecTest {
    @Test
    fun announcePlease_round_trips() {
        val msg = MoqLiteAnnouncePlease(prefix = "nests/30312:abc:room")
        val encoded = MoqLiteCodec.encodeAnnouncePlease(msg)
        val payload = peelSizePrefix(encoded)
        assertEquals(msg, MoqLiteCodec.decodeAnnouncePlease(payload))
    }

    @Test
    fun announcePlease_normalizes_prefix_on_encode_and_decode() {
        val raw = MoqLiteAnnouncePlease(prefix = "/nests//30312:abc:room/")
        val payload = peelSizePrefix(MoqLiteCodec.encodeAnnouncePlease(raw))
        // The wire bytes are normalised — no leading/trailing/duplicate `/`.
        val r = MoqReader(payload)
        assertEquals("nests/30312:abc:room", r.readLengthPrefixedString())
        assertTrue(!r.hasMore())
    }

    @Test
    fun announce_active_round_trips() {
        val msg = MoqLiteAnnounce(status = MoqLiteAnnounceStatus.Active, suffix = "speakerPubkey", hops = emptyList())
        val payload = peelSizePrefix(MoqLiteCodec.encodeAnnounce(msg))
        // Byte 0 is the status byte (literal 1), then a 13-byte string,
        // then a varint hops count.
        assertEquals(1, payload[0].toInt())
        assertEquals(msg, MoqLiteCodec.decodeAnnounce(payload))
    }

    @Test
    fun announce_ended_with_relay_hops_round_trips() {
        // Lite-03 wire only carries a count; decoded list is filled
        // with UNKNOWN placeholders (origin id 0). Round-trip thus
        // requires the input also be a list of zeros.
        val msg = MoqLiteAnnounce(status = MoqLiteAnnounceStatus.Ended, suffix = "fff", hops = List(7) { 0L })
        val payload = peelSizePrefix(MoqLiteCodec.encodeAnnounce(msg))
        assertEquals(0, payload[0].toInt())
        assertEquals(msg, MoqLiteCodec.decodeAnnounce(payload))
    }

    @Test
    fun announce_rejects_unknown_status_byte() {
        // Hand-craft a payload with an invalid status byte. Wrap in the
        // size-prefix envelope just like a real message would arrive.
        val handCraft = byteArrayOf(0x05.toByte()) + lengthPrefixed("x") + Varint.encode(0L)
        assertFailsWith<MoqCodecException> { MoqLiteCodec.decodeAnnounce(handCraft) }
    }

    @Test
    fun subscribe_round_trips_with_all_groups_set() {
        val msg =
            MoqLiteSubscribe(
                id = 42L,
                broadcast = "nests/30312:abc:room",
                track = "audio/data",
                priority = 0x80,
                ordered = true,
                maxLatencyMillis = 250L,
                startGroup = 5L,
                endGroup = 9L,
            )
        val payload = peelSizePrefix(MoqLiteCodec.encodeSubscribe(msg))
        assertEquals(msg, MoqLiteCodec.decodeSubscribe(payload))
    }

    @Test
    fun subscribe_round_trips_with_null_group_bounds() {
        val msg =
            MoqLiteSubscribe(
                id = 1L,
                broadcast = "speakerPubkey",
                track = "catalog.json",
                priority = 0,
                ordered = false,
                maxLatencyMillis = 0L,
                startGroup = null,
                endGroup = null,
            )
        val payload = peelSizePrefix(MoqLiteCodec.encodeSubscribe(msg))
        assertEquals(msg, MoqLiteCodec.decodeSubscribe(payload))
    }

    @Test
    fun subscribe_off_by_one_group_encoding_uses_zero_for_none() {
        val msg = subscribeWith(startGroup = null, endGroup = null)
        val payload = peelSizePrefix(MoqLiteCodec.encodeSubscribe(msg))
        // Skip past id (varint), broadcast/track strings, priority byte,
        // ordered byte, maxLatency varint — pull the last two varints.
        val r = MoqReader(payload)
        r.readVarint() // id
        r.readLengthPrefixedString() // broadcast
        r.readLengthPrefixedString() // track
        r.readByte() // priority
        r.readByte() // ordered
        r.readVarint() // maxLatency
        assertEquals(0L, r.readVarint(), "startGroup=None encodes as 0 on the wire")
        assertEquals(0L, r.readVarint(), "endGroup=None encodes as 0 on the wire")
    }

    @Test
    fun subscribe_off_by_one_some_zero_encodes_as_one() {
        val msg = subscribeWith(startGroup = 0L, endGroup = 0L)
        val payload = peelSizePrefix(MoqLiteCodec.encodeSubscribe(msg))
        val r = MoqReader(payload)
        r.readVarint()
        r.readLengthPrefixedString()
        r.readLengthPrefixedString()
        r.readByte()
        r.readByte()
        r.readVarint()
        assertEquals(1L, r.readVarint(), "Some(0) encodes as 1 (off-by-one trick)")
        assertEquals(1L, r.readVarint(), "Some(0) encodes as 1 (off-by-one trick)")
    }

    @Test
    fun subscribe_rejects_oversized_priority_at_construction() {
        assertFailsWith<IllegalArgumentException> {
            MoqLiteSubscribe(
                id = 0L,
                broadcast = "x",
                track = "y",
                priority = 256,
                ordered = false,
                maxLatencyMillis = 0L,
                startGroup = null,
                endGroup = null,
            )
        }
    }

    @Test
    fun subscribeOk_round_trips() {
        val msg =
            MoqLiteSubscribeOk(
                priority = 0x80,
                ordered = true,
                maxLatencyMillis = 100L,
                startGroup = null,
                endGroup = null,
            )
        // moq-lite-03 SubscribeResponse is `[type][size][body]` with the
        // type discriminator OUTSIDE the size prefix — the encoder no
        // longer emits an outer wrap, so feed the bytes through directly.
        val payload = MoqLiteCodec.encodeSubscribeOk(msg)
        val resp = MoqLiteCodec.decodeSubscribeResponse(payload)
        val ok = assertIs<MoqLiteCodec.SubscribeResponse.Ok>(resp)
        assertEquals(msg, ok.ok)
    }

    @Test
    fun subscribeDrop_round_trips() {
        val msg = MoqLiteSubscribeDrop(errorCode = 0x12L, reasonPhrase = "publisher gone")
        val payload = MoqLiteCodec.encodeSubscribeDrop(msg)
        val resp = MoqLiteCodec.decodeSubscribeResponse(payload)
        val drop = assertIs<MoqLiteCodec.SubscribeResponse.Dropped>(resp)
        assertEquals(msg, drop.drop)
    }

    @Test
    fun groupHeader_round_trips() {
        val msg = MoqLiteGroupHeader(subscribeId = 42L, sequence = 7L)
        val payload = peelSizePrefix(MoqLiteCodec.encodeGroupHeader(msg))
        assertEquals(msg, MoqLiteCodec.decodeGroupHeader(payload))
    }

    // ---------------- Lite-04 wire diffs (audit L1) ----------------

    @Test
    fun announcePlease_lite04_round_trips_excludeHop() {
        val msg = MoqLiteAnnouncePlease(prefix = "rooms/A", excludeHop = 7L)
        val payload =
            peelSizePrefix(
                MoqLiteCodec.encodeAnnouncePlease(msg, MoqLiteVersion.LITE_04),
            )
        // Wire after prefix: a single varint excludeHop.
        val r = MoqReader(payload)
        assertEquals("rooms/A", r.readLengthPrefixedString())
        assertEquals(7L, r.readVarint(), "Lite-04 excludeHop is a single varint after prefix")
        assertTrue(!r.hasMore())
        // Round-trip via decoder.
        assertEquals(
            msg,
            MoqLiteCodec.decodeAnnouncePlease(payload, MoqLiteVersion.LITE_04),
        )
    }

    @Test
    fun announcePlease_lite03_omits_excludeHop_on_wire() {
        // Lite-03 encode MUST NOT write excludeHop, regardless of the
        // value carried in the data class. Confirms a Lite-04-aware
        // app accidentally talking Lite-03 doesn't leak the field
        // and desync the peer.
        val msg = MoqLiteAnnouncePlease(prefix = "rooms/A", excludeHop = 99L)
        val payload =
            peelSizePrefix(
                MoqLiteCodec.encodeAnnouncePlease(msg, MoqLiteVersion.LITE_03),
            )
        val r = MoqReader(payload)
        assertEquals("rooms/A", r.readLengthPrefixedString())
        assertTrue(!r.hasMore(), "Lite-03 wire MUST end after prefix — no excludeHop")
        // Decoded msg drops back to excludeHop=0 (Lite-03 default).
        assertEquals(
            MoqLiteAnnouncePlease(prefix = "rooms/A", excludeHop = 0L),
            MoqLiteCodec.decodeAnnouncePlease(payload, MoqLiteVersion.LITE_03),
        )
    }

    @Test
    fun announce_lite04_round_trips_full_origin_list() {
        val msg =
            MoqLiteAnnounce(
                status = MoqLiteAnnounceStatus.Active,
                suffix = "spk",
                hops = listOf(1L, 5L, 12L),
            )
        val payload =
            peelSizePrefix(
                MoqLiteCodec.encodeAnnounce(msg, MoqLiteVersion.LITE_04),
            )
        // Wire: status u8, suffix string, count varint, then count
        // origin id varints.
        val r = MoqReader(payload)
        assertEquals(1, r.readByte()) // Active
        assertEquals("spk", r.readLengthPrefixedString())
        assertEquals(3L, r.readVarint(), "count")
        assertEquals(1L, r.readVarint(), "origin[0]")
        assertEquals(5L, r.readVarint(), "origin[1]")
        assertEquals(12L, r.readVarint(), "origin[2]")
        assertTrue(!r.hasMore())
        assertEquals(msg, MoqLiteCodec.decodeAnnounce(payload, MoqLiteVersion.LITE_04))
    }

    @Test
    fun announce_lite03_drops_origin_ids_keeps_count() {
        // Lite-03 wire only carries the count; the IDs are discarded
        // on encode and reconstructed as UNKNOWN (0) placeholders on
        // decode. Confirms cross-version round-trip is lossy in this
        // direction (matches kixelated's Lite-03 hop-count semantic).
        val sent =
            MoqLiteAnnounce(
                status = MoqLiteAnnounceStatus.Active,
                suffix = "spk",
                hops = listOf(1L, 5L, 12L),
            )
        val payload = peelSizePrefix(MoqLiteCodec.encodeAnnounce(sent, MoqLiteVersion.LITE_03))
        val decoded = MoqLiteCodec.decodeAnnounce(payload, MoqLiteVersion.LITE_03)
        // Same count, IDs replaced with UNKNOWN.
        assertEquals(3, decoded.hops.size)
        assertEquals(listOf(0L, 0L, 0L), decoded.hops)
    }

    @Test
    fun announce_decoder_rejects_oversize_hop_count() {
        // MAX_HOPS = 32 per kixelated's Origin::MAX_HOPS. A wire
        // payload with count = 33 must be rejected.
        val out =
            com.vitorpamplona.nestsclient.moq
                .MoqWriter()
        out.writeByte(1) // Active
        out.writeLengthPrefixedString("spk")
        out.writeVarint(33L)
        // Lite-03 doesn't write IDs so the body ends here.
        val payload = out.toByteArray()
        assertFailsWith<MoqCodecException> {
            MoqLiteCodec.decodeAnnounce(payload, MoqLiteVersion.LITE_03)
        }
    }

    @Test
    fun probe_lite04_round_trips_rtt() {
        val msg = MoqLiteProbe(bitrate = 32_000L, rtt = 25L)
        val payload =
            peelSizePrefix(
                MoqLiteCodec.encodeProbe(msg, MoqLiteVersion.LITE_04),
            )
        val r = MoqReader(payload)
        assertEquals(32_000L, r.readVarint())
        assertEquals(25L, r.readVarint())
        assertTrue(!r.hasMore())
        assertEquals(msg, MoqLiteCodec.decodeProbe(payload, MoqLiteVersion.LITE_04))
    }

    @Test
    fun probe_lite04_clamps_some_zero_to_one_to_avoid_unknown_sentinel() {
        // Per kixelated `encode_msg`, an outgoing rtt of `Some(0)` is
        // clamped to `Some(1)` because 0 is the unknown-sentinel.
        // Mirroring that exactly avoids a peer round-trip from
        // Some(0) to None.
        val msg = MoqLiteProbe(bitrate = 100_000L, rtt = 0L)
        val payload =
            peelSizePrefix(
                MoqLiteCodec.encodeProbe(msg, MoqLiteVersion.LITE_04),
            )
        val r = MoqReader(payload)
        r.readVarint() // bitrate
        assertEquals(1L, r.readVarint(), "rtt=0 must be clamped to 1 on the wire")
    }

    @Test
    fun probe_lite04_decodes_zero_rtt_as_null() {
        // Symmetric: an incoming `0` MUST decode as null (None).
        val out =
            com.vitorpamplona.nestsclient.moq
                .MoqWriter()
        out.writeVarint(64_000L)
        out.writeVarint(0L) // rtt = unknown
        val payload = out.toByteArray()
        val decoded = MoqLiteCodec.decodeProbe(payload, MoqLiteVersion.LITE_04)
        assertEquals(64_000L, decoded.bitrate)
        assertEquals(null, decoded.rtt, "rtt=0 decodes as null (unknown sentinel)")
    }

    @Test
    fun probe_lite03_wire_omits_rtt() {
        // Lite-03 encode MUST NOT write rtt regardless of the data
        // class value.
        val msg = MoqLiteProbe(bitrate = 32_000L, rtt = 99L)
        val payload =
            peelSizePrefix(
                MoqLiteCodec.encodeProbe(msg, MoqLiteVersion.LITE_03),
            )
        val r = MoqReader(payload)
        assertEquals(32_000L, r.readVarint())
        assertTrue(!r.hasMore(), "Lite-03 probe must end after bitrate — no rtt")
        // Decoded msg drops rtt to null (Lite-03 default).
        assertEquals(
            MoqLiteProbe(bitrate = 32_000L, rtt = null),
            MoqLiteCodec.decodeProbe(payload, MoqLiteVersion.LITE_03),
        )
    }

    @Test
    fun decoders_reject_trailing_garbage() {
        // Build a valid AnnouncePlease payload then append a stray byte.
        val payload = peelSizePrefix(MoqLiteCodec.encodeAnnouncePlease(MoqLiteAnnouncePlease("foo")))
        val tampered = payload + byteArrayOf(0)
        assertFailsWith<MoqCodecException> { MoqLiteCodec.decodeAnnouncePlease(tampered) }
    }

    @Test
    fun decoders_reject_invalid_ordered_byte() {
        val msg = subscribeWith(startGroup = null, endGroup = null)
        val payload = peelSizePrefix(MoqLiteCodec.encodeSubscribe(msg)).copyOf()
        // Patch the ordered byte. Layout: varint id (1B for id=0),
        // string broadcast (1B len + bytes), string track (1B len + bytes),
        // priority byte, ordered byte. So index = 1 + 1 + b.length + 1 + t.length + 1.
        val idx = 1 + 1 + msg.broadcast.length + 1 + msg.track.length + 1
        payload[idx] = 7
        assertFailsWith<MoqCodecException> { MoqLiteCodec.decodeSubscribe(payload) }
    }

    private fun subscribeWith(
        startGroup: Long?,
        endGroup: Long?,
    ) = MoqLiteSubscribe(
        id = 0L,
        broadcast = "b",
        track = "t",
        priority = 0,
        ordered = false,
        maxLatencyMillis = 0L,
        startGroup = startGroup,
        endGroup = endGroup,
    )

    private fun lengthPrefixed(s: String): ByteArray {
        val bytes = s.encodeToByteArray()
        return Varint.encode(bytes.size.toLong()) + bytes
    }

    /**
     * Peel the varint size-prefix off an encoded message. Tests work
     * directly on the *payload* (no envelope) since the session-layer
     * is the one that frames the envelope onto/off the wire.
     */
    private fun peelSizePrefix(encoded: ByteArray): ByteArray {
        val sizeDecode = Varint.decode(encoded) ?: error("envelope missing size prefix")
        val payload = encoded.copyOfRange(sizeDecode.bytesConsumed, encoded.size)
        assertEquals(
            sizeDecode.value.toInt(),
            payload.size,
            "size prefix must equal payload length",
        )
        return payload
    }

    @Suppress("unused")
    private fun byteHex(bytes: ByteArray): String = bytes.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun checks_byteHex_helper_compiles() {
        assertContentEquals(byteArrayOf(0x10, 0x20), byteArrayOf(0x10, 0x20))
    }
}
