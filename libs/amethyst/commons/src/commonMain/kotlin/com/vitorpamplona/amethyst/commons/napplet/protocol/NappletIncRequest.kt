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
package com.vitorpamplona.amethyst.commons.napplet.protocol

/**
 * A NAP-INC (`inc.*`) request, decoded from the wire by [NappletProtocolJson.decodeIncRequest].
 * Inter-napplet messages never reach the broker: the host hands them to its
 * `NappletIncBus`, which routes them between the live
 * napplets. A payload is kept as its raw JSON text, `null` when the message had none: the bus never
 * reads it, it only passes it on.
 */
sealed interface NappletIncRequest {
    /** `inc.subscribe`: receive `inc.event` for [topic]. Answered with `inc.subscribe.result`. */
    data class Subscribe(
        val id: String,
        val topic: String,
    ) : NappletIncRequest

    /** `inc.unsubscribe`: stop receiving [topic]. Fire-and-forget. */
    data class Unsubscribe(
        val topic: String,
    ) : NappletIncRequest

    /** `inc.emit`: deliver [payload] to every other napplet subscribed to [topic]. Fire-and-forget. */
    data class Emit(
        val topic: String,
        val payload: String?,
    ) : NappletIncRequest

    /** `inc.channel.open`: a channel to the live endpoint [target]. Answered with `inc.channel.open.result`. */
    data class ChannelOpen(
        val id: String,
        val target: String,
    ) : NappletIncRequest

    /** `inc.channel.emit`: send [payload] to the peer of [channelId]. Fire-and-forget. */
    data class ChannelEmit(
        val channelId: String,
        val payload: String?,
    ) : NappletIncRequest

    /** `inc.channel.broadcast`: send [payload] to every peer the napplet has an open channel with. */
    data class ChannelBroadcast(
        val payload: String?,
    ) : NappletIncRequest

    /** `inc.channel.list`: the napplet's open channels. Answered with `inc.channel.list.result`. */
    data class ChannelList(
        val id: String,
    ) : NappletIncRequest

    /** `inc.channel.close`: tear down [channelId]; both ends get `inc.channel.closed`. */
    data class ChannelClose(
        val channelId: String,
    ) : NappletIncRequest
}
