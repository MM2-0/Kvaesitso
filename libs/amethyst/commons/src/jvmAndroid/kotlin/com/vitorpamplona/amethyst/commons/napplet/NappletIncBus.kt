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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletIncRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * NAP-INC, the shell side: topics and point-to-point channels between the napplets running in this
 * app. Each host keeps one bus for all its surfaces (the Android broker service, the desktop
 * runtime) and hands it every `inc.*` request with the surface [K] it came from and the function
 * that pushes to that surface.
 *
 * Every surface gets an endpoint identifier the first time it talks to the bus: 128 random bits, so
 * one is never handed to another endpoint, and never the manifest's `d` tag (NAP-INC binds the identifier to the live endpoint, so
 * two copies of one napplet, or a root and a named napplet of one author, are different peers).
 * `sender` and `peer` always come from that binding; nothing a napplet sends can set them.
 *
 * Every endpoint belongs to a partition, the account its surface was launched as: napplets only
 * reach napplets of the same account, as every other per-surface resource is bound to it. Topics
 * match by exact string equality, and an emit never reaches its own sender. A channel is authorized
 * once, on open: the target must be a live endpoint of the same partition other than the opener. Messages are
 * pushed after the lock is released, in the order they were decided, so a target's
 * `inc.channel.opened` is queued before [handle] returns the opener's `inc.channel.open.result`.
 *
 * A surface going away ([remove]) closes its channels, telling each peer `peer destroyed`.
 */
class NappletIncBus<K : Any>(
    private val newEndpointId: () -> String = { RandomInstance.bytes(16).toHexKey() },
    private val newChannelId: () -> String = { RandomInstance.bytes(16).toHexKey() },
    private val maxTopicsPerEndpoint: Int = MAX_TOPICS_PER_ENDPOINT,
    private val maxChannelsPerEndpoint: Int = MAX_CHANNELS_PER_ENDPOINT,
) {
    private class Endpoint<K>(
        val key: K,
        val partition: String,
        val id: String,
        val deliver: (String) -> Unit,
    ) {
        val topics = HashSet<String>()
        val channels = LinkedHashSet<String>()
    }

    private class Channel<K>(
        val id: String,
        val opener: Endpoint<K>,
        val target: Endpoint<K>,
    ) {
        fun peerOf(endpoint: Endpoint<K>) = if (endpoint === opener) target else opener
    }

    private class Delivery(
        val to: (String) -> Unit,
        val payload: String,
    )

    private val lock = Any()
    private val endpoints = HashMap<K, Endpoint<K>>()
    private val endpointsById = HashMap<String, Endpoint<K>>()
    private val subscribers = HashMap<String, LinkedHashSet<Endpoint<K>>>()
    private val channels = HashMap<String, Channel<K>>()

    /** The endpoint identifier of [key], assigned now if it has none. For tests: hosts only [handle]. */
    internal fun endpointId(
        key: K,
        partition: String,
        deliver: (String) -> Unit,
    ): String = synchronized(lock) { endpointOf(key, partition, deliver).id }

    /**
     * Acts on [request] from the surface [key], launched as the account [partition] and whose pushes
     * go to [deliver], and returns the reply the host sends back with the request's id, or null for a
     * fire-and-forget message.
     */
    fun handle(
        key: K,
        partition: String,
        deliver: (String) -> Unit,
        request: NappletIncRequest,
    ): String? {
        val out = ArrayList<Delivery>()
        val reply = synchronized(lock) { decide(endpointOf(key, partition, deliver), request, out) }
        out.forEach { it.to(it.payload) }
        return reply
    }

    /** Forgets the surface [key]: its topics go, and every channel it had closes for its peer. */
    fun remove(key: K) = removeAll { it == key }

    /** [remove] for every surface [matches] picks. */
    fun removeAll(matches: (K) -> Boolean) {
        val out = ArrayList<Delivery>()
        synchronized(lock) {
            endpoints.values.filter { matches(it.key) }.forEach { endpoint ->
                endpoint.channels.toList().forEach { channelId ->
                    val channel = channels.remove(channelId) ?: return@forEach
                    val peer = channel.peerOf(endpoint)
                    peer.channels.remove(channelId)
                    out += Delivery(peer.deliver, NappletProtocolJson.encodeIncChannelClosed(channelId, PEER_DESTROYED))
                }
                endpoint.topics.forEach { topic -> unsubscribe(endpoint, topic) }
                endpoints.remove(endpoint.key)
                endpointsById.remove(endpoint.id)
            }
        }
        out.forEach { it.to(it.payload) }
    }

    private fun endpointOf(
        key: K,
        partition: String,
        deliver: (String) -> Unit,
    ): Endpoint<K> =
        endpoints.getOrPut(key) {
            var id = newEndpointId()
            while (id in endpointsById) id = newEndpointId()
            Endpoint(key, partition, id, deliver).also { endpointsById[id] = it }
        }

    private fun decide(
        from: Endpoint<K>,
        request: NappletIncRequest,
        out: MutableList<Delivery>,
    ): String? =
        when (request) {
            is NappletIncRequest.Subscribe -> {
                if (request.topic !in from.topics && from.topics.size >= maxTopicsPerEndpoint) {
                    NappletProtocolJson.encodeIncSubscribeResult("too many topics")
                } else {
                    from.topics += request.topic
                    subscribers.getOrPut(request.topic) { LinkedHashSet() } += from
                    NappletProtocolJson.encodeIncSubscribeResult()
                }
            }

            is NappletIncRequest.Unsubscribe -> {
                if (from.topics.remove(request.topic)) unsubscribe(from, request.topic)
                null
            }

            is NappletIncRequest.Emit -> {
                // Every subscriber gets the same envelope: built once, and only if someone gets it.
                var event: String? = null
                subscribers[request.topic]?.forEach { subscriber ->
                    if (subscriber !== from && subscriber.partition == from.partition) {
                        val envelope = event ?: NappletProtocolJson.encodeIncEvent(request.topic, request.payload, from.id).also { event = it }
                        out += Delivery(subscriber.deliver, envelope)
                    }
                }
                null
            }

            is NappletIncRequest.ChannelOpen -> open(from, request.target, out)

            is NappletIncRequest.ChannelEmit -> {
                val channel = channels[request.channelId]
                if (channel != null && request.channelId in from.channels) {
                    val peer = channel.peerOf(from)
                    out += Delivery(peer.deliver, NappletProtocolJson.encodeIncChannelEvent(channel.id, from.id, request.payload))
                }
                null
            }

            is NappletIncRequest.ChannelBroadcast -> {
                // Each channel has its own id, so each peer's envelope differs.
                from.channels.forEach { channelId ->
                    val channel = channels[channelId] ?: return@forEach
                    out += Delivery(channel.peerOf(from).deliver, NappletProtocolJson.encodeIncChannelEvent(channelId, from.id, request.payload))
                }
                null
            }

            is NappletIncRequest.ChannelList ->
                NappletProtocolJson.encodeIncChannelListResult(
                    from.channels.mapNotNull { channelId -> channels[channelId]?.let { channelId to it.peerOf(from).id } },
                )

            is NappletIncRequest.ChannelClose -> {
                val channel = channels[request.channelId]
                if (channel != null && request.channelId in from.channels) {
                    channels.remove(channel.id)
                    channel.opener.channels.remove(channel.id)
                    channel.target.channels.remove(channel.id)
                    val closed = NappletProtocolJson.encodeIncChannelClosed(channel.id)
                    out += Delivery(from.deliver, closed)
                    out += Delivery(channel.peerOf(from).deliver, closed)
                }
                null
            }
        }

    private fun open(
        from: Endpoint<K>,
        target: String,
        out: MutableList<Delivery>,
    ): String {
        // Another account's napplet is not there for this one, so it can't learn that it exists.
        val peer = endpointsById[target]?.takeIf { it.partition == from.partition }
        val error =
            when {
                peer == null -> "target not found"
                peer === from -> "cannot open a channel to itself"
                from.channels.size >= maxChannelsPerEndpoint || peer.channels.size >= maxChannelsPerEndpoint -> "too many channels"
                else -> null
            }
        if (error != null || peer == null) return NappletProtocolJson.encodeIncChannelOpenResult(null, null, error)

        var channelId = newChannelId()
        while (channelId in channels) channelId = newChannelId()
        channels[channelId] = Channel(channelId, from, peer)
        from.channels += channelId
        peer.channels += channelId
        out += Delivery(peer.deliver, NappletProtocolJson.encodeIncChannelOpened(channelId, from.id))
        return NappletProtocolJson.encodeIncChannelOpenResult(channelId, peer.id)
    }

    private fun unsubscribe(
        endpoint: Endpoint<K>,
        topic: String,
    ) {
        val set = subscribers[topic] ?: return
        set.remove(endpoint)
        if (set.isEmpty()) subscribers.remove(topic)
    }

    companion object {
        /** NAP-INC's reason when the other end of a channel went away. */
        const val PEER_DESTROYED = "peer destroyed"

        /** Topics one napplet may listen on at once. */
        const val MAX_TOPICS_PER_ENDPOINT = 256

        /** Channels one napplet may hold open at once (NAP-INC lets a shell bound them). */
        const val MAX_CHANNELS_PER_ENDPOINT = 64
    }
}
