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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reproduces the "public chat message not sent to the channel-declared relay" report.
 *
 * Walks the exact protocol path the app uses:
 *  1. build a kind-40 ChannelCreate carrying a declared relay in its content,
 *  2. feed it to a PublicChatChannel via updateChannelInfo (what LocalCache does),
 *  3. assert channel.relays() returns the declared relay (what both the send path
 *     and computeRelaysForChannels/broadcast read),
 *  4. build a kind-42 message for that channel and assert channelId() resolves back
 *     to the channel id (what getAnyChannel uses to find the channel at broadcast).
 */
class PublicChatChannelRelayTest {
    private val authorKey = "e".repeat(64)
    private val channelId = "a".repeat(64)
    private val msgId = "b".repeat(64)
    private val sig = "f".repeat(128)

    private val declaredRelay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    private fun createEvent(relays: List<NormalizedRelayUrl>?): ChannelCreateEvent {
        val template = ChannelCreateEvent.build("My Channel", "about", null, relays)
        return ChannelCreateEvent(channelId, authorKey, template.createdAt, template.tags, template.content, sig)
    }

    @Test
    fun channelRelaysContainsDeclaredRelay() {
        val create = createEvent(listOf(declaredRelay))

        // sanity: the relay survives the content round-trip
        assertEquals(listOf(declaredRelay), create.channelInfo().relays)

        val channel = PublicChatChannel(channelId)
        channel.info = create.channelInfo() // exactly what updateChannelInfo does

        assertTrue(channel.relays().contains(declaredRelay), "channel.relays() must contain the declared relay")
    }

    @Test
    fun messageResolvesBackToChannel() {
        val create = createEvent(listOf(declaredRelay))

        val template = ChannelMessageEvent.message("hello", EventHintBundle(create, declaredRelay))
        val message = ChannelMessageEvent(msgId, authorKey, template.createdAt, template.tags, template.content, sig)

        assertEquals(channelId, message.channelId(), "message must resolve to the channel id (getAnyChannel)")
    }

    @Test
    fun emptyDeclaredRelayListFallsThroughToObservedRelays() {
        // Regression: info.relays = [] (empty, not null) must NOT short-circuit to an
        // empty set. It has to fall back to the relays the channel was observed on,
        // otherwise messages are published to nowhere.
        val create = createEvent(emptyList())

        val channel = PublicChatChannel(channelId)
        channel.addRelaySync(declaredRelay) // observed relay
        channel.info = create.channelInfo() // exactly what updateChannelInfo does

        assertTrue(channel.info.relays!!.isEmpty(), "precondition: declared relay list is empty")
        assertTrue(
            channel.relays().contains(declaredRelay),
            "relays() must fall back to observed relays when the declared list is empty",
        )
    }
}
