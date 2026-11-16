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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.quic.Varint
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Unit tests for [MoqLiteNestsListener.Companion.stripLegacyTimestampPrefix].
 *
 * This function is the entire receive-side wire-format converter for
 * audio frames — every web speaker's Opus packet flows through it. A
 * regression here is invisible to users (silent decode failure of every
 * web speaker) and to interop tests (both sides agree on the same bug).
 * Pin the four QUIC varint-length tiers, the malformed paths, and a
 * round-trip against `NestMoqLiteBroadcaster`'s wire format.
 *
 * Varint length tags (RFC 9000 §16, top 2 bits of the first byte):
 *   00 → 1 byte total (6-bit value, 0..63)
 *   01 → 2 bytes total (14-bit, 0..16383)
 *   10 → 4 bytes total (30-bit, 0..2^30-1)
 *   11 → 8 bytes total (62-bit, 0..2^62-1)
 */
class StripLegacyTimestampPrefixTest {
    @Test
    fun strips_one_byte_varint_for_small_timestamp() {
        // `Varint.encode(0L)` returns a single byte with tag 00.
        val frame = Varint.encode(0L) + OPUS
        val stripped = MoqLiteNestsListener.stripLegacyTimestampPrefix(frame)
        assertContentEquals(OPUS, stripped)
    }

    @Test
    fun strips_one_byte_varint_at_max_6bit_value() {
        // 63 (0x3F) is the largest value still encoded in 1 byte.
        val frame = Varint.encode(63L) + OPUS
        assertContentEquals(byteArrayOf(0x3F) + OPUS, frame, "varint(63) is a single 0x3F byte")
        val stripped = MoqLiteNestsListener.stripLegacyTimestampPrefix(frame)
        assertContentEquals(OPUS, stripped)
    }

    @Test
    fun strips_two_byte_varint() {
        // 64 (just past the 1-byte boundary) → tag 01, 2 bytes.
        val frame = Varint.encode(64L) + OPUS
        val tag = (frame[0].toInt() ushr 6) and 0x3
        assertTrue(tag == 0x1, "varint(64) should use 2-byte form, got tag=$tag")
        val stripped = MoqLiteNestsListener.stripLegacyTimestampPrefix(frame)
        assertContentEquals(OPUS, stripped)
    }

    @Test
    fun strips_four_byte_varint() {
        // ~4.2 million µs ≈ 4.2 s — comfortably past the 2-byte 16383 µs
        // limit and well within any real broadcast lifetime.
        val frame = Varint.encode(4_200_000L) + OPUS
        val tag = (frame[0].toInt() ushr 6) and 0x3
        assertTrue(tag == 0x2, "varint(4_200_000) should use 4-byte form, got tag=$tag")
        val stripped = MoqLiteNestsListener.stripLegacyTimestampPrefix(frame)
        assertContentEquals(OPUS, stripped)
    }

    @Test
    fun strips_eight_byte_varint() {
        // > 2^30 µs (~17.9 minutes) forces the 8-byte form. Models a
        // broadcast that's been running long enough for the timestamp
        // counter to overflow into the widest tier — perfectly valid
        // Opus stream timestamps but not exercised by the smaller
        // tiers.
        val frame = Varint.encode(2_000_000_000L) + OPUS
        val tag = (frame[0].toInt() ushr 6) and 0x3
        assertTrue(tag == 0x3, "varint(2_000_000_000) should use 8-byte form, got tag=$tag")
        val stripped = MoqLiteNestsListener.stripLegacyTimestampPrefix(frame)
        assertContentEquals(OPUS, stripped)
    }

    @Test
    fun empty_payload_returns_empty() {
        val empty = ByteArray(0)
        // Same instance — no allocation on the empty path.
        assertSame(empty, MoqLiteNestsListener.stripLegacyTimestampPrefix(empty))
    }

    @Test
    fun malformed_payload_smaller_than_varint_returns_payload_unchanged() {
        // A frame body that's just the high tag byte of an 8-byte
        // varint with no follow-up bytes is malformed. The current
        // contract is "return the payload unchanged so upstream
        // surfaces the corruption rather than silently masking it."
        // This pins that contract — if it ever changes (e.g. to
        // throw or return empty), this test fails and forces an
        // explicit decision.
        val malformed = byteArrayOf(0xC0.toByte()) // tag=11, expects 8 bytes total
        val result = MoqLiteNestsListener.stripLegacyTimestampPrefix(malformed)
        assertContentEquals(malformed, result, "malformed frame returned unchanged")
        assertSame(malformed, result, "no allocation on the unchanged-malformed path")
    }

    @Test
    fun round_trip_against_broadcaster_wire_shape() {
        // Mirror the byte sequence
        // [com.vitorpamplona.nestsclient.audio.NestMoqLiteBroadcaster] writes:
        //   varint(timestamp_us) + raw_opus_packet
        // Stripping MUST recover the exact opus packet byte-for-byte
        // for every frame the watcher receives. Any drift here means
        // every decoded frame loses or gains bytes at the front.
        val timestamps = longArrayOf(0L, 20_000L, 40_000L, 1_000_000L, 2_000_000_000L)
        for (ts in timestamps) {
            val tsLen = Varint.size(ts)
            val frame = ByteArray(tsLen + OPUS.size)
            Varint.writeTo(ts, frame, 0)
            OPUS.copyInto(frame, tsLen)
            val stripped = MoqLiteNestsListener.stripLegacyTimestampPrefix(frame)
            assertContentEquals(OPUS, stripped, "round-trip failed for timestamp $ts")
        }
    }

    private companion object {
        // 8 bytes of plausible-looking Opus packet bytes — the function
        // doesn't care about the codec content, only the length /
        // content equality after the strip.
        private val OPUS = byteArrayOf(0x78, 0x12, 0x34, 0x56, 0x78.toByte(), 0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte())
    }
}
