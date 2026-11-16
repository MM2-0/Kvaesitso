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

import com.vitorpamplona.nestsclient.moq.MoqCodec.encode
import com.vitorpamplona.quic.Varint

/**
 * Encode/decode IETF `draft-ietf-moq-transport-17` control-stream
 * messages.
 *
 * Wire format for every control message:
 *
 *   message_type (varint) | message_length (varint) | payload bytes
 *
 * The payload layout is per-message. Payload encoders here produce only the
 * payload; [encode] wraps it with type+length for on-the-wire use.
 *
 * Wire-protocol scope: IETF MoQ-transport, NOT moq-lite. nostrnests's
 * reference relay uses moq-lite — see
 * `nestsClient/plans/2026-04-26-moq-lite-gap.md`.
 */
object MoqCodec {
    /**
     * Upper bound on a control-message payload length. MoQ-transport control
     * messages are small (subscribes, announces, parameter blocks) — 16 MiB
     * is well above any legitimate body and below any value where `.toInt()`
     * would risk overflow. Anything bigger is treated as a malformed peer.
     */
    const val MAX_CONTROL_PAYLOAD_LEN: Long = 16L * 1024L * 1024L

    /**
     * Encode a full control-stream frame (type + length + payload).
     */
    fun encode(message: MoqMessage): ByteArray {
        val payload = encodePayload(message)
        val frame = MoqWriter(payload.size + 8)
        frame.writeVarint(message.type.code)
        frame.writeVarint(payload.size.toLong())
        frame.writeBytes(payload)
        return frame.toByteArray()
    }

    /**
     * Decode one control-stream message from [src] starting at [offset].
     *
     * Returns the message plus the number of bytes consumed, or null if [src]
     * doesn't yet contain a whole frame (caller should buffer more data from
     * the control stream and retry).
     */
    fun decode(
        src: ByteArray,
        offset: Int = 0,
    ): DecodeResult? {
        val typeDec = Varint.decode(src, offset) ?: return null
        val lenDec = Varint.decode(src, offset + typeDec.bytesConsumed) ?: return null
        val payloadStart = offset + typeDec.bytesConsumed + lenDec.bytesConsumed
        // Bound the payload length before .toInt() — varint can carry up to
        // 2^62, which silently overflows to a negative or absurd payloadEnd
        // and makes the (payloadEnd > src.size) check return null forever,
        // wedging the parser. Reject anything larger than the cap up front.
        if (lenDec.value < 0L || lenDec.value > MAX_CONTROL_PAYLOAD_LEN) {
            throw MoqCodecException(
                "MoQ control payload length out of bounds: ${lenDec.value} (max $MAX_CONTROL_PAYLOAD_LEN)",
            )
        }
        val payloadEnd = payloadStart + lenDec.value.toInt()
        if (payloadEnd > src.size) return null

        val type =
            MoqMessageType.fromCode(typeDec.value)
                ?: throw MoqUnknownTypeException(
                    typeCode = typeDec.value,
                    bytesConsumed = payloadEnd - offset,
                )

        val reader = MoqReader(src, payloadStart, payloadEnd)
        val message =
            when (type) {
                MoqMessageType.ClientSetup -> decodeClientSetup(reader)
                MoqMessageType.ServerSetup -> decodeServerSetup(reader)
                MoqMessageType.Subscribe -> decodeSubscribe(reader)
                MoqMessageType.SubscribeOk -> decodeSubscribeOk(reader)
                MoqMessageType.SubscribeError -> decodeSubscribeError(reader)
                MoqMessageType.Unsubscribe -> decodeUnsubscribe(reader)
                MoqMessageType.SubscribeDone -> decodeSubscribeDone(reader)
                MoqMessageType.Announce -> decodeAnnounce(reader)
                MoqMessageType.AnnounceOk -> decodeAnnounceOk(reader)
                MoqMessageType.AnnounceError -> decodeAnnounceError(reader)
                MoqMessageType.Unannounce -> decodeUnannounce(reader)
            }
        if (reader.hasMore()) {
            throw MoqCodecException(
                "trailing bytes in ${type.name} payload (consumed=${payloadEnd - payloadStart - reader.remaining}, total=${payloadEnd - payloadStart})",
            )
        }
        return DecodeResult(message, payloadEnd - offset)
    }

    private fun encodePayload(message: MoqMessage): ByteArray =
        when (message) {
            is ClientSetup -> encodeClientSetup(message)
            is ServerSetup -> encodeServerSetup(message)
            is Subscribe -> encodeSubscribe(message)
            is SubscribeOk -> encodeSubscribeOk(message)
            is SubscribeError -> encodeSubscribeError(message)
            is Unsubscribe -> encodeUnsubscribe(message)
            is SubscribeDone -> encodeSubscribeDone(message)
            is Announce -> encodeAnnounce(message)
            is AnnounceOk -> encodeAnnounceOk(message)
            is AnnounceError -> encodeAnnounceError(message)
            is Unannounce -> encodeUnannounce(message)
        }

    private fun encodeClientSetup(message: ClientSetup): ByteArray {
        val w = MoqWriter()
        w.writeVarint(message.supportedVersions.size.toLong())
        for (v in message.supportedVersions) w.writeVarint(v)
        encodeParameters(w, message.parameters)
        return w.toByteArray()
    }

    private fun decodeClientSetup(r: MoqReader): ClientSetup {
        val nVersions = r.readVarint().toInt()
        if (nVersions < 0) throw MoqCodecException("negative version count: $nVersions")
        val versions = ArrayList<Long>(nVersions)
        repeat(nVersions) { versions.add(r.readVarint()) }
        val params = decodeParameters(r)
        return ClientSetup(versions, params)
    }

    private fun encodeServerSetup(message: ServerSetup): ByteArray {
        val w = MoqWriter()
        w.writeVarint(message.selectedVersion)
        encodeParameters(w, message.parameters)
        return w.toByteArray()
    }

    private fun decodeServerSetup(r: MoqReader): ServerSetup {
        val version = r.readVarint()
        val params = decodeParameters(r)
        return ServerSetup(version, params)
    }

    private fun encodeParameters(
        w: MoqWriter,
        params: List<SetupParameter>,
    ) {
        w.writeVarint(params.size.toLong())
        for (p in params) {
            w.writeVarint(p.key)
            w.writeLengthPrefixedBytes(p.value)
        }
    }

    private fun decodeParameters(r: MoqReader): List<SetupParameter> {
        val n = r.readVarint().toInt()
        if (n < 0) throw MoqCodecException("negative parameter count: $n")
        val out = ArrayList<SetupParameter>(n)
        repeat(n) {
            val key = r.readVarint()
            val value = r.readLengthPrefixedBytes()
            out.add(SetupParameter(key, value))
        }
        return out
    }

    private fun encodeNamespace(
        w: MoqWriter,
        ns: TrackNamespace,
    ) {
        w.writeVarint(ns.tuple.size.toLong())
        for (segment in ns.tuple) w.writeLengthPrefixedBytes(segment)
    }

    private fun decodeNamespace(r: MoqReader): TrackNamespace {
        val count = r.readVarint().toInt()
        if (count < 0 || count > 32) {
            // draft-17 caps the tuple length well below this; any higher
            // almost certainly means a corrupted/malicious frame.
            throw MoqCodecException("absurd track namespace tuple length: $count")
        }
        val segments = ArrayList<ByteArray>(count)
        repeat(count) { segments.add(r.readLengthPrefixedBytes()) }
        return TrackNamespace(segments)
    }

    private fun encodeSubscribe(m: Subscribe): ByteArray {
        val w = MoqWriter()
        w.writeVarint(m.subscribeId)
        w.writeVarint(m.trackAlias)
        encodeNamespace(w, m.namespace)
        w.writeLengthPrefixedBytes(m.trackName)
        w.writeByte(m.subscriberPriority)
        w.writeByte(m.groupOrder)
        w.writeVarint(m.filter.code)
        // LatestGroup / LatestObject have no extra fields.
        encodeParameters(w, m.parameters)
        return w.toByteArray()
    }

    private fun decodeSubscribe(r: MoqReader): Subscribe {
        val subscribeId = r.readVarint()
        val trackAlias = r.readVarint()
        val namespace = decodeNamespace(r)
        val trackName = r.readLengthPrefixedBytes()
        val subscriberPriority = r.readByte()
        val groupOrder = r.readByte()
        val filterCode = r.readVarint()
        val filter =
            SubscribeFilter.fromCode(filterCode)
                ?: throw MoqCodecException("unknown subscribe filter: 0x${filterCode.toString(16)}")
        if (filter != SubscribeFilter.LatestGroup && filter != SubscribeFilter.LatestObject) {
            throw MoqCodecException("subscribe filter $filter not yet supported")
        }
        val parameters = decodeParameters(r)
        return Subscribe(
            subscribeId = subscribeId,
            trackAlias = trackAlias,
            namespace = namespace,
            trackName = trackName,
            subscriberPriority = subscriberPriority,
            groupOrder = groupOrder,
            filter = filter,
            parameters = parameters,
        )
    }

    private fun encodeSubscribeOk(m: SubscribeOk): ByteArray {
        val w = MoqWriter()
        w.writeVarint(m.subscribeId)
        w.writeVarint(m.expiresMs)
        w.writeByte(m.groupOrder)
        w.writeByte(if (m.contentExists) 1 else 0)
        if (m.contentExists) {
            w.writeVarint(m.largestGroupId!!)
            w.writeVarint(m.largestObjectId!!)
        }
        encodeParameters(w, m.parameters)
        return w.toByteArray()
    }

    private fun decodeSubscribeOk(r: MoqReader): SubscribeOk {
        val subscribeId = r.readVarint()
        val expiresMs = r.readVarint()
        val groupOrder = r.readByte()
        val contentExistsByte = r.readByte()
        if (contentExistsByte != 0 && contentExistsByte != 1) {
            throw MoqCodecException("content_exists must be 0 or 1, got $contentExistsByte")
        }
        val contentExists = contentExistsByte == 1
        val largestGroupId = if (contentExists) r.readVarint() else null
        val largestObjectId = if (contentExists) r.readVarint() else null
        val parameters = decodeParameters(r)
        return SubscribeOk(
            subscribeId = subscribeId,
            expiresMs = expiresMs,
            groupOrder = groupOrder,
            contentExists = contentExists,
            largestGroupId = largestGroupId,
            largestObjectId = largestObjectId,
            parameters = parameters,
        )
    }

    private fun encodeSubscribeError(m: SubscribeError): ByteArray {
        val w = MoqWriter()
        w.writeVarint(m.subscribeId)
        w.writeVarint(m.errorCode)
        w.writeLengthPrefixedString(m.reasonPhrase)
        w.writeVarint(m.trackAlias)
        return w.toByteArray()
    }

    private fun decodeSubscribeError(r: MoqReader): SubscribeError =
        SubscribeError(
            subscribeId = r.readVarint(),
            errorCode = r.readVarint(),
            reasonPhrase = r.readLengthPrefixedString(),
            trackAlias = r.readVarint(),
        )

    private fun encodeUnsubscribe(m: Unsubscribe): ByteArray {
        val w = MoqWriter()
        w.writeVarint(m.subscribeId)
        return w.toByteArray()
    }

    private fun decodeUnsubscribe(r: MoqReader): Unsubscribe = Unsubscribe(r.readVarint())

    private fun encodeSubscribeDone(m: SubscribeDone): ByteArray {
        val w = MoqWriter()
        w.writeVarint(m.subscribeId)
        w.writeVarint(m.statusCode)
        w.writeVarint(m.streamCount)
        w.writeLengthPrefixedString(m.reasonPhrase)
        return w.toByteArray()
    }

    private fun decodeSubscribeDone(r: MoqReader): SubscribeDone =
        SubscribeDone(
            subscribeId = r.readVarint(),
            statusCode = r.readVarint(),
            streamCount = r.readVarint(),
            reasonPhrase = r.readLengthPrefixedString(),
        )

    private fun encodeAnnounce(m: Announce): ByteArray {
        val w = MoqWriter()
        encodeNamespace(w, m.namespace)
        encodeParameters(w, m.parameters)
        return w.toByteArray()
    }

    private fun decodeAnnounce(r: MoqReader): Announce = Announce(namespace = decodeNamespace(r), parameters = decodeParameters(r))

    private fun encodeAnnounceOk(m: AnnounceOk): ByteArray {
        val w = MoqWriter()
        encodeNamespace(w, m.namespace)
        return w.toByteArray()
    }

    private fun decodeAnnounceOk(r: MoqReader): AnnounceOk = AnnounceOk(decodeNamespace(r))

    private fun encodeAnnounceError(m: AnnounceError): ByteArray {
        val w = MoqWriter()
        encodeNamespace(w, m.namespace)
        w.writeVarint(m.errorCode)
        w.writeLengthPrefixedString(m.reasonPhrase)
        return w.toByteArray()
    }

    private fun decodeAnnounceError(r: MoqReader): AnnounceError =
        AnnounceError(
            namespace = decodeNamespace(r),
            errorCode = r.readVarint(),
            reasonPhrase = r.readLengthPrefixedString(),
        )

    private fun encodeUnannounce(m: Unannounce): ByteArray {
        val w = MoqWriter()
        encodeNamespace(w, m.namespace)
        return w.toByteArray()
    }

    private fun decodeUnannounce(r: MoqReader): Unannounce = Unannounce(decodeNamespace(r))

    data class DecodeResult(
        val message: MoqMessage,
        val bytesConsumed: Int,
    )
}
