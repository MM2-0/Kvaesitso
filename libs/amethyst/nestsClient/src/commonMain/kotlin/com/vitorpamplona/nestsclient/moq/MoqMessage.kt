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
 * Subset of the **IETF `draft-ietf-moq-transport-17`** control-plane
 * messages. Wire layout per the IETF draft: control messages on the
 * single bidi share
 *
 *   message_type (varint) | message_length (varint) | payload...
 *
 * Wire-protocol scope: this is the IETF MoQ-transport flavour. The
 * nostrnests reference relay speaks **moq-lite** (kixelated's variant)
 * which uses a wire-incompatible shape — no SETUP message, ControlType
 * discriminator on each fresh bidi stream, single-string broadcast +
 * track names rather than tuples. See
 * `nestsClient/plans/2026-04-26-moq-lite-gap.md` for the full spec and
 * the planned moq-lite parallel codec.
 */
sealed class MoqMessage {
    abstract val type: MoqMessageType
}

/**
 * Message type codes per draft-ietf-moq-transport-17. Held as enum so future
 * draft revisions can extend without breaking call sites.
 */
enum class MoqMessageType(
    val code: Long,
) {
    Subscribe(0x03),
    SubscribeOk(0x04),
    SubscribeError(0x05),
    Announce(0x06),
    AnnounceOk(0x07),
    AnnounceError(0x08),
    Unannounce(0x09),
    Unsubscribe(0x0A),
    SubscribeDone(0x0B),
    ClientSetup(0x40),
    ServerSetup(0x41),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Long): MoqMessageType? = byCode[code]
    }
}

/**
 * Wire-level setup parameter: key is a varint, value is an opaque byte string
 * (MoQ doesn't prescribe a single type for values — some draft revisions use
 * varints, others length-prefixed bytes; nests uses bytes in either case).
 */
data class SetupParameter(
    val key: Long,
    val value: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SetupParameter) return false
        return key == other.key && value.contentEquals(other.value)
    }

    override fun hashCode(): Int = 31 * key.hashCode() + value.contentHashCode()

    companion object {
        /** ROLE parameter (key 0x00), draft-specific values. Kept as bytes. */
        const val KEY_ROLE: Long = 0x00L

        /** PATH parameter (key 0x01). */
        const val KEY_PATH: Long = 0x01L

        /** MAX_SUBSCRIBE_ID (key 0x02 in most drafts). */
        const val KEY_MAX_SUBSCRIBE_ID: Long = 0x02L
    }
}

/**
 * CLIENT_SETUP (0x40): sent by the client immediately after the WebTransport
 * session opens, on the first bidi stream (the control stream).
 */
data class ClientSetup(
    val supportedVersions: List<Long>,
    val parameters: List<SetupParameter> = emptyList(),
) : MoqMessage() {
    override val type: MoqMessageType = MoqMessageType.ClientSetup

    init {
        require(supportedVersions.isNotEmpty()) { "must offer at least one version" }
    }
}

/**
 * SERVER_SETUP (0x41): response to CLIENT_SETUP. Contains the one version the
 * server selected from [ClientSetup.supportedVersions].
 */
data class ServerSetup(
    val selectedVersion: Long,
    val parameters: List<SetupParameter> = emptyList(),
) : MoqMessage() {
    override val type: MoqMessageType = MoqMessageType.ServerSetup
}

/**
 * MoQ track namespace is a tuple of byte strings (draft-ietf-moq-transport).
 * Clients talking to nests typically use a 1- or 2-element namespace; we
 * expose the tuple as a `List<ByteArray>` and provide a [text] helper for
 * the common case where every element is UTF-8.
 */
data class TrackNamespace(
    val tuple: List<ByteArray>,
) {
    fun text(): List<String> = tuple.map { it.decodeToString() }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackNamespace) return false
        if (tuple.size != other.tuple.size) return false
        for (i in tuple.indices) if (!tuple[i].contentEquals(other.tuple[i])) return false
        return true
    }

    override fun hashCode(): Int = tuple.fold(0) { acc, bytes -> 31 * acc + bytes.contentHashCode() }

    companion object {
        fun of(vararg segments: String): TrackNamespace = TrackNamespace(segments.map { it.encodeToByteArray() })
    }
}

/**
 * Subscribe filter types (draft-ietf-moq-transport). Values align with the
 * draft's enum; we model it as an enum so the public API reads clearly.
 */
enum class SubscribeFilter(
    val code: Long,
) {
    LatestGroup(0x01),
    LatestObject(0x02),
    AbsoluteStart(0x03),
    AbsoluteRange(0x04),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        fun fromCode(code: Long): SubscribeFilter? = byCode[code]
    }
}

/**
 * SUBSCRIBE (0x03): client asks a publisher to forward objects belonging to a
 * (namespace, track) pair. Today the codec supports only the LatestGroup /
 * LatestObject filters — absolute-range variants add extra wire fields the
 * codec will grow in a follow-up if nests ever needs them.
 */
data class Subscribe(
    val subscribeId: Long,
    val trackAlias: Long,
    val namespace: TrackNamespace,
    val trackName: ByteArray,
    val subscriberPriority: Int = 0x80,
    val groupOrder: Int = 0x00,
    val filter: SubscribeFilter = SubscribeFilter.LatestGroup,
    val parameters: List<SetupParameter> = emptyList(),
) : MoqMessage() {
    override val type: MoqMessageType = MoqMessageType.Subscribe

    init {
        require(filter == SubscribeFilter.LatestGroup || filter == SubscribeFilter.LatestObject) {
            "only LatestGroup / LatestObject filters supported, got $filter"
        }
        require(subscriberPriority in 0..255) { "subscriber_priority must fit in a byte" }
        require(groupOrder in 0..255) { "group_order must fit in a byte" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Subscribe) return false
        return subscribeId == other.subscribeId &&
            trackAlias == other.trackAlias &&
            namespace == other.namespace &&
            trackName.contentEquals(other.trackName) &&
            subscriberPriority == other.subscriberPriority &&
            groupOrder == other.groupOrder &&
            filter == other.filter &&
            parameters == other.parameters
    }

    override fun hashCode(): Int {
        var result = subscribeId.hashCode()
        result = 31 * result + trackAlias.hashCode()
        result = 31 * result + namespace.hashCode()
        result = 31 * result + trackName.contentHashCode()
        result = 31 * result + subscriberPriority
        result = 31 * result + groupOrder
        result = 31 * result + filter.hashCode()
        result = 31 * result + parameters.hashCode()
        return result
    }
}

/**
 * SUBSCRIBE_OK (0x04): publisher confirms a subscribe request. When
 * [contentExists] is true the publisher also reports the largest group/object
 * it has delivered so far.
 */
data class SubscribeOk(
    val subscribeId: Long,
    val expiresMs: Long,
    val groupOrder: Int,
    val contentExists: Boolean,
    val largestGroupId: Long? = null,
    val largestObjectId: Long? = null,
    val parameters: List<SetupParameter> = emptyList(),
) : MoqMessage() {
    override val type: MoqMessageType = MoqMessageType.SubscribeOk

    init {
        require(groupOrder in 0..255) { "group_order must fit in a byte" }
        if (contentExists) {
            requireNotNull(largestGroupId) { "largestGroupId required when contentExists=true" }
            requireNotNull(largestObjectId) { "largestObjectId required when contentExists=true" }
        } else {
            require(largestGroupId == null && largestObjectId == null) {
                "largestGroupId/largestObjectId must be null when contentExists=false"
            }
        }
    }
}

/** SUBSCRIBE_ERROR (0x05): publisher rejects a subscribe request. */
data class SubscribeError(
    val subscribeId: Long,
    val errorCode: Long,
    val reasonPhrase: String,
    val trackAlias: Long,
) : MoqMessage() {
    override val type: MoqMessageType = MoqMessageType.SubscribeError
}

/** UNSUBSCRIBE (0x0A): subscriber asks the publisher to stop a subscription. */
data class Unsubscribe(
    val subscribeId: Long,
) : MoqMessage() {
    override val type: MoqMessageType = MoqMessageType.Unsubscribe
}

/**
 * SUBSCRIBE_DONE (0x0B): publisher tells the subscriber that no more objects
 * are coming for this subscription, optionally indicating the last group/object
 * boundary. Sent on subscription expiry, publisher-side track closure, or
 * after an UNSUBSCRIBE was acknowledged.
 */
data class SubscribeDone(
    val subscribeId: Long,
    val statusCode: Long,
    val streamCount: Long,
    val reasonPhrase: String,
) : MoqMessage() {
    override val type: MoqMessageType = MoqMessageType.SubscribeDone
}

/**
 * ANNOUNCE (0x06): publisher offers a track namespace. nests publishers send
 * one ANNOUNCE per audio-room they host so subscribers know which namespace
 * to subscribe under.
 */
data class Announce(
    val namespace: TrackNamespace,
    val parameters: List<SetupParameter> = emptyList(),
) : MoqMessage() {
    override val type: MoqMessageType = MoqMessageType.Announce
}

/** ANNOUNCE_OK (0x07): subscriber acknowledges an ANNOUNCE. */
data class AnnounceOk(
    val namespace: TrackNamespace,
) : MoqMessage() {
    override val type: MoqMessageType = MoqMessageType.AnnounceOk
}

/** ANNOUNCE_ERROR (0x08): subscriber rejects an ANNOUNCE. */
data class AnnounceError(
    val namespace: TrackNamespace,
    val errorCode: Long,
    val reasonPhrase: String,
) : MoqMessage() {
    override val type: MoqMessageType = MoqMessageType.AnnounceError
}

/** UNANNOUNCE (0x09): publisher withdraws a previously-announced namespace. */
data class Unannounce(
    val namespace: TrackNamespace,
) : MoqMessage() {
    override val type: MoqMessageType = MoqMessageType.Unannounce
}
