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

/**
 * A single object in **IETF `draft-ietf-moq-transport-17`** — a
 * publisher-produced unit identified by `(track_alias, group_id,
 * object_id)` coordinates with a `publisher_priority` and a `status`
 * varint, carrying app bytes.
 *
 * Per the IETF draft, objects are delivered three ways:
 *
 *   1. OBJECT_DATAGRAM — one object per QUIC datagram. Lowest latency, no
 *      retransmits.
 *   2. STREAM_HEADER_SUBGROUP — multiple objects per uni stream, reliable.
 *   3. FETCH_HEADER — historical objects over a bidi stream.
 *
 * This shape is **not** what nostrnests's relay (moq-lite) uses on the
 * wire. moq-lite delivers media as *frames within groups within uni
 * streams*: one uni stream per group, each with `(subscribe_id, group_seq)`
 * header and then a sequence of `(size_varint, payload)` frames; no
 * datagram path, no `track_alias`, no `publisher_priority`/`status`
 * envelope. See `nestsClient/plans/2026-04-26-moq-lite-gap.md` §5 for
 * the moq-lite frame layout.
 */
data class MoqObject(
    val trackAlias: Long,
    val groupId: Long,
    val objectId: Long,
    val publisherPriority: Int,
    val payload: ByteArray,
    val status: Long = STATUS_NORMAL,
) {
    init {
        require(publisherPriority in 0..255) { "publisher_priority must fit in a byte" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MoqObject) return false
        return trackAlias == other.trackAlias &&
            groupId == other.groupId &&
            objectId == other.objectId &&
            publisherPriority == other.publisherPriority &&
            status == other.status &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = trackAlias.hashCode()
        result = 31 * result + groupId.hashCode()
        result = 31 * result + objectId.hashCode()
        result = 31 * result + publisherPriority
        result = 31 * result + status.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        /** Normal object with a payload. */
        const val STATUS_NORMAL: Long = 0x00L

        /**
         * Indicates the object intentionally carries no payload (e.g. a
         * keyframe boundary marker). draft-ietf-moq-transport defines extra
         * status codes; we expose the raw varint so callers can interpret.
         */
        const val STATUS_OBJECT_DOES_NOT_EXIST: Long = 0x01L
    }
}

/**
 * Encoder / decoder for the OBJECT_DATAGRAM wire format.
 *
 * Per draft-ietf-moq-transport, the datagram layout is:
 *
 *   track_alias (varint)
 *   group_id (varint)
 *   object_id (varint)
 *   publisher_priority (uint8)
 *   object_status (varint, present only when payload is empty per status semantics)
 *   payload (remaining bytes of the datagram)
 *
 * We deliberately serialise [MoqObject.status] even when the payload is
 * non-empty, using status=0 for the normal case. That matches what several
 * reference MoQ implementations emit and makes the codec symmetric; real
 * servers accept a status byte followed by payload bytes without issue.
 */
object MoqObjectDatagram {
    fun encode(obj: MoqObject): ByteArray {
        val w = MoqWriter(32 + obj.payload.size)
        w.writeVarint(obj.trackAlias)
        w.writeVarint(obj.groupId)
        w.writeVarint(obj.objectId)
        w.writeByte(obj.publisherPriority)
        w.writeVarint(obj.status)
        w.writeBytes(obj.payload)
        return w.toByteArray()
    }

    fun decode(datagram: ByteArray): MoqObject {
        val r = MoqReader(datagram)
        val trackAlias = r.readVarint()
        val groupId = r.readVarint()
        val objectId = r.readVarint()
        val publisherPriority = r.readByte()
        val status = r.readVarint()
        val payload = if (r.remaining > 0) r.readBytes(r.remaining) else ByteArray(0)
        return MoqObject(
            trackAlias = trackAlias,
            groupId = groupId,
            objectId = objectId,
            publisherPriority = publisherPriority,
            status = status,
            payload = payload,
        )
    }
}
