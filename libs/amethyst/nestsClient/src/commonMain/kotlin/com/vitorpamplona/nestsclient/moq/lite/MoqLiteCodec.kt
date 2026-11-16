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
import com.vitorpamplona.nestsclient.moq.MoqWriter

/**
 * Encode/decode moq-lite (Lite-03) messages. Every message on the wire
 * is **size-prefixed**: a varint length, then the payload bytes.
 *
 * This codec produces the size-prefixed envelope on encode and consumes
 * it on decode. The control-stream framing in `MoqLiteSession` reads
 * one size-prefix at a time, slices the payload, and dispatches.
 *
 * `MoqLitePath.normalize` is applied to every path string at the wire
 * boundary so a non-normalised input from the application layer can't
 * silently produce wire bytes that the relay won't match against
 * `claims.root` or against another peer's broadcast lookup.
 *
 * The off-by-one encoding for `startGroup` / `endGroup` (Subscribe
 * Lite-03 only) is `0 = None, n = Some(n − 1)`. The data-class fields
 * carry the `Some(value)` form (or null for None); the codec applies
 * the `+1` / `−1` shift here.
 *
 * Source: `kixelated/moq-rs/rs/moq-lite/src/lite/{announce,subscribe,
 * group}.rs`, the equivalent `@moq/lite/lite/` JS files, and varint
 * encoding per RFC 9000 §16.
 */
object MoqLiteCodec {
    // ---------------- AnnouncePlease ----------------

    fun encodeAnnouncePlease(
        msg: MoqLiteAnnouncePlease,
        version: MoqLiteVersion = MoqLiteVersion.LITE_03,
    ): ByteArray {
        val body = MoqWriter()
        body.writeLengthPrefixedString(MoqLitePath.normalize(msg.prefix))
        // Lite-04 appends `excludeHop` as a single varint after
        // prefix. Sentinel `0` = no exclusion. Lite-03 omits the
        // field entirely; ignore [msg.excludeHop] in that case.
        if (version == MoqLiteVersion.LITE_04) {
            body.writeVarint(msg.excludeHop)
        }
        return wrapSizePrefixed(body)
    }

    fun decodeAnnouncePlease(
        payload: ByteArray,
        version: MoqLiteVersion = MoqLiteVersion.LITE_03,
    ): MoqLiteAnnouncePlease {
        val r = MoqReader(payload)
        val prefix = MoqLitePath.normalize(r.readLengthPrefixedString())
        val excludeHop =
            if (version == MoqLiteVersion.LITE_04) r.readVarint() else 0L
        ensureFullyConsumed(r, "AnnouncePlease")
        return MoqLiteAnnouncePlease(prefix = prefix, excludeHop = excludeHop)
    }

    // ---------------- Announce ----------------

    fun encodeAnnounce(
        msg: MoqLiteAnnounce,
        version: MoqLiteVersion = MoqLiteVersion.LITE_03,
    ): ByteArray {
        val body = MoqWriter()
        body.writeByte(msg.status.code)
        body.writeLengthPrefixedString(MoqLitePath.normalize(msg.suffix))
        // Lite-03: just the count. Lite-04: count + each origin id.
        // Mirrors kixelated's `encode_hops` switch.
        body.writeVarint(msg.hops.size.toLong())
        if (version == MoqLiteVersion.LITE_04) {
            for (originId in msg.hops) {
                body.writeVarint(originId)
            }
        }
        return wrapSizePrefixed(body)
    }

    fun decodeAnnounce(
        payload: ByteArray,
        version: MoqLiteVersion = MoqLiteVersion.LITE_03,
    ): MoqLiteAnnounce {
        val r = MoqReader(payload)
        val statusByte = r.readByte()
        val status =
            MoqLiteAnnounceStatus.fromCode(statusByte)
                ?: throw MoqCodecException("unknown moq-lite Announce status byte: $statusByte")
        val suffix = MoqLitePath.normalize(r.readLengthPrefixedString())
        val count = r.readVarint()
        if (count < 0L || count > MoqLiteAnnounce.MAX_HOPS) {
            throw MoqCodecException("Announce hops count out of range [0, ${MoqLiteAnnounce.MAX_HOPS}]: $count")
        }
        val hops: List<Long> =
            when (version) {
                MoqLiteVersion.LITE_03 -> {
                    // Wire only carries a count; fill with UNKNOWN
                    // placeholders (origin id 0). Mirrors kixelated's
                    // `Origin::UNKNOWN` fill in announce.rs.
                    List(count.toInt()) { 0L }
                }

                MoqLiteVersion.LITE_04 -> {
                    val list = ArrayList<Long>(count.toInt())
                    repeat(count.toInt()) {
                        val id = r.readVarint()
                        require(id >= 0) { "decoded origin id must be non-negative" }
                        list.add(id)
                    }
                    list
                }
            }
        ensureFullyConsumed(r, "Announce")
        return MoqLiteAnnounce(status = status, suffix = suffix, hops = hops)
    }

    // ---------------- Subscribe ----------------

    fun encodeSubscribe(msg: MoqLiteSubscribe): ByteArray {
        val body = MoqWriter()
        body.writeVarint(msg.id)
        body.writeLengthPrefixedString(MoqLitePath.normalize(msg.broadcast))
        body.writeLengthPrefixedString(msg.track) // tracks are opaque, no normalize
        body.writeByte(msg.priority)
        body.writeByte(if (msg.ordered) 1 else 0)
        body.writeVarint(msg.maxLatencyMillis)
        body.writeVarint(encodeOptionalGroup(msg.startGroup))
        body.writeVarint(encodeOptionalGroup(msg.endGroup))
        return wrapSizePrefixed(body)
    }

    fun decodeSubscribe(payload: ByteArray): MoqLiteSubscribe {
        val r = MoqReader(payload)
        val id = r.readVarint()
        val broadcast = MoqLitePath.normalize(r.readLengthPrefixedString())
        val track = r.readLengthPrefixedString()
        val priority = r.readByte()
        val ordered = decodeOrderedByte(r.readByte())
        val maxLatencyMillis = r.readVarint()
        val startGroup = decodeOptionalGroup(r.readVarint())
        val endGroup = decodeOptionalGroup(r.readVarint())
        ensureFullyConsumed(r, "Subscribe")
        return MoqLiteSubscribe(
            id = id,
            broadcast = broadcast,
            track = track,
            priority = priority,
            ordered = ordered,
            maxLatencyMillis = maxLatencyMillis,
            startGroup = startGroup,
            endGroup = endGroup,
        )
    }

    // ---------------- Subscribe response ----------------

    fun encodeSubscribeOk(msg: MoqLiteSubscribeOk): ByteArray {
        val body = MoqWriter()
        body.writeByte(msg.priority)
        body.writeByte(if (msg.ordered) 1 else 0)
        body.writeVarint(msg.maxLatencyMillis)
        body.writeVarint(encodeOptionalGroup(msg.startGroup))
        body.writeVarint(encodeOptionalGroup(msg.endGroup))
        return prefixWithType(MoqLiteSubscribeResponseType.Ok.code, body)
    }

    fun encodeSubscribeDrop(msg: MoqLiteSubscribeDrop): ByteArray {
        val body = MoqWriter()
        body.writeVarint(msg.errorCode)
        body.writeLengthPrefixedString(msg.reasonPhrase)
        return prefixWithType(MoqLiteSubscribeResponseType.Drop.code, body)
    }

    /**
     * moq-lite-03 SubscribeResponse framing: a top-level type
     * discriminator varint, then a size-prefixed body. The type sits
     * OUTSIDE the size prefix — see
     * `rs/moq-lite/src/lite/subscribe.rs::SubscribeResponse::encode`
     * (the `_` arm covers Lite03+). Earlier drafts size-prefixed the
     * whole thing, but Lite03 uses this two-piece framing.
     */
    private fun prefixWithType(
        typeCode: Long,
        body: MoqWriter,
    ): ByteArray {
        // Inline the size-prefix wrap so we don't allocate body→ByteArray
        // and a separate wrapper buffer just to copy them into `out`. The
        // earlier shape (`out.writeBytes(wrapSizePrefixed(body))`) made
        // three ByteArrays per response — varintless small frames don't
        // matter, but this is on the publisher's reply path and the
        // pattern is cheap to fix.
        val payload = body.toByteArray()
        val out = MoqWriter(payload.size + 16)
        out.writeVarint(typeCode)
        out.writeLengthPrefixedBytes(payload)
        return out.toByteArray()
    }

    /**
     * Decode either an [MoqLiteSubscribeOk] or [MoqLiteSubscribeDrop]
     * — the response stream tells which by its leading varint type.
     */
    sealed class SubscribeResponse {
        data class Ok(
            val ok: MoqLiteSubscribeOk,
        ) : SubscribeResponse()

        data class Dropped(
            val drop: MoqLiteSubscribeDrop,
        ) : SubscribeResponse()
    }

    /**
     * Decode `[type_varint][body_size_varint][body]` as produced by
     * [encodeSubscribeOk] / [encodeSubscribeDrop]. The body itself is
     * size-prefixed; the type discriminator sits OUTSIDE that size
     * prefix — see [prefixWithType] for the wire-format rationale.
     */
    fun decodeSubscribeResponse(payload: ByteArray): SubscribeResponse {
        val r = MoqReader(payload)
        val typeCode = r.readVarint()
        val type =
            MoqLiteSubscribeResponseType.fromCode(typeCode)
                ?: throw MoqCodecException("unknown moq-lite SubscribeResponse type: $typeCode")
        val body = r.readLengthPrefixedBytes()
        ensureFullyConsumed(r, "SubscribeResponse(type=$type)")
        val br = MoqReader(body)
        return when (type) {
            MoqLiteSubscribeResponseType.Ok -> {
                val priority = br.readByte()
                val ordered = decodeOrderedByte(br.readByte())
                val maxLatencyMillis = br.readVarint()
                val startGroup = decodeOptionalGroup(br.readVarint())
                val endGroup = decodeOptionalGroup(br.readVarint())
                ensureFullyConsumed(br, "SubscribeOk")
                SubscribeResponse.Ok(
                    MoqLiteSubscribeOk(
                        priority = priority,
                        ordered = ordered,
                        maxLatencyMillis = maxLatencyMillis,
                        startGroup = startGroup,
                        endGroup = endGroup,
                    ),
                )
            }

            MoqLiteSubscribeResponseType.Drop -> {
                val errorCode = br.readVarint()
                val reason = br.readLengthPrefixedString()
                ensureFullyConsumed(br, "SubscribeDrop")
                SubscribeResponse.Dropped(
                    MoqLiteSubscribeDrop(errorCode = errorCode, reasonPhrase = reason),
                )
            }
        }
    }

    // ---------------- Group header ----------------

    fun encodeGroupHeader(msg: MoqLiteGroupHeader): ByteArray {
        val body = MoqWriter()
        body.writeVarint(msg.subscribeId)
        body.writeVarint(msg.sequence)
        return wrapSizePrefixed(body)
    }

    fun decodeGroupHeader(payload: ByteArray): MoqLiteGroupHeader {
        val r = MoqReader(payload)
        val subscribeId = r.readVarint()
        val sequence = r.readVarint()
        ensureFullyConsumed(r, "GroupHeader")
        return MoqLiteGroupHeader(subscribeId = subscribeId, sequence = sequence)
    }

    // ---------------- Probe ----------------

    fun decodeProbe(
        payload: ByteArray,
        version: MoqLiteVersion = MoqLiteVersion.LITE_03,
    ): MoqLiteProbe {
        val r = MoqReader(payload)
        val bitrate = r.readVarint()
        // Lite-04 appends `rtt` as a single varint after bitrate.
        // Sentinel `0` = unknown (decoded as null).
        val rtt: Long? =
            if (version == MoqLiteVersion.LITE_04) {
                val raw = r.readVarint()
                if (raw == 0L) null else raw
            } else {
                null
            }
        ensureFullyConsumed(r, "Probe")
        return MoqLiteProbe(bitrate = bitrate, rtt = rtt)
    }

    /**
     * Encode a Probe message body. Lite-03 writes only `bitrate`;
     * Lite-04 appends `rtt` (sentinel `0` = unknown; outgoing
     * `Some(0)` is clamped to `Some(1)` to avoid colliding with the
     * sentinel — matches kixelated's `encode_msg` clamp). The
     * publisher writes these size-prefixed onto a Probe bidi the
     * subscriber opened, advertising the publisher's expected
     * bandwidth. Wrapping (size prefix) is the caller's
     * responsibility, matching [encodeAnnouncePlease] / [encodeAnnounce].
     */
    fun encodeProbe(
        probe: MoqLiteProbe,
        version: MoqLiteVersion = MoqLiteVersion.LITE_03,
    ): ByteArray {
        val body = MoqWriter()
        body.writeVarint(probe.bitrate)
        if (version == MoqLiteVersion.LITE_04) {
            // null → 0 (unknown sentinel). Some(0) → 1 (clamp).
            // Otherwise pass through.
            val wire =
                when (val r = probe.rtt) {
                    null -> 0L
                    0L -> 1L
                    else -> r
                }
            body.writeVarint(wire)
        }
        return wrapSizePrefixed(body)
    }

    // ---------------- internals ----------------

    /**
     * Wrap a body buffer in a varint size prefix. Every moq-lite
     * control-stream message uses this envelope (see e.g.
     * `lite/announce.rs:64-81`, `lite/subscribe.rs:25-72`).
     */
    private fun wrapSizePrefixed(body: MoqWriter): ByteArray {
        val payload = body.toByteArray()
        val out = MoqWriter(payload.size + 8)
        out.writeLengthPrefixedBytes(payload)
        return out.toByteArray()
    }

    /**
     * `0 = None, n = Some(n − 1)`. The encoding lets the wire format
     * collapse "no bound" into a single zero byte.
     */
    private fun encodeOptionalGroup(value: Long?): Long = if (value == null) 0L else value + 1

    private fun decodeOptionalGroup(raw: Long): Long? = if (raw == 0L) null else raw - 1

    private fun decodeOrderedByte(b: Int): Boolean =
        when (b) {
            0 -> false
            1 -> true
            else -> throw MoqCodecException("moq-lite ordered byte must be 0/1, got $b")
        }

    private fun ensureFullyConsumed(
        r: MoqReader,
        msg: String,
    ) {
        if (r.hasMore()) {
            throw MoqCodecException(
                "trailing $msg payload bytes (${r.remaining} left) — wire format mismatch",
            )
        }
    }
}
