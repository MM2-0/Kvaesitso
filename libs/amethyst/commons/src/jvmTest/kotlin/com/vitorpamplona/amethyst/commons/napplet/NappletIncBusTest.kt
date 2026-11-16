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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** NAP-INC's shell rules, against the bus both hosts route through. */
class NappletIncBusTest {
    private var ids = 0
    private val bus = NappletIncBus<String>(newEndpointId = { "endpoint-${++ids}" }, newChannelId = { "channel-${++ids}" }, maxChannelsPerEndpoint = 2)

    // Everything each surface was pushed, in order.
    private val inbox = mutableMapOf<String, MutableList<JsonObject>>()

    private fun pushesTo(surface: String) = inbox.getOrPut(surface) { mutableListOf() }

    // Surfaces are launched as account "alice" unless the test says otherwise.
    private fun send(
        surface: String,
        request: NappletIncRequest,
        account: String = "alice",
    ): JsonObject? = bus.handle(surface, account, { pushesTo(surface) += Json.parseToJsonElement(it).jsonObject }, request)?.let { Json.parseToJsonElement(it).jsonObject }

    private fun idOf(
        surface: String,
        account: String = "alice",
    ) = bus.endpointId(surface, account) { pushesTo(surface) += Json.parseToJsonElement(it).jsonObject }

    private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.content

    @Test
    fun emitReachesOtherSubscribersOfTheExactTopicOnly() {
        assertEquals("inc.subscribe.result", send("feed", NappletIncRequest.Subscribe("a1", "napplet:profile/open"))?.text("type"))
        send("other", NappletIncRequest.Subscribe("a2", "napplet:profile/open/"))
        send("viewer", NappletIncRequest.Subscribe("a3", "napplet:profile/open"))

        send("viewer", NappletIncRequest.Emit("napplet:profile/open", """{"pubkey":"abc"}"""))

        val event = pushesTo("feed").single()
        assertEquals("inc.event", event.text("type"))
        assertEquals("napplet:profile/open", event.text("topic"))
        assertEquals(idOf("viewer"), event.text("sender"))
        assertEquals("abc", event["payload"]?.jsonObject?.text("pubkey"))
        // No echo to the sender, and no prefix match.
        assertTrue(pushesTo("viewer").isEmpty())
        assertTrue(pushesTo("other").isEmpty())
    }

    @Test
    fun anEventWithoutAPayloadHasNone() {
        send("a", NappletIncRequest.Subscribe("1", "t"))
        send("b", NappletIncRequest.Emit("t", null))
        assertFalse("payload" in pushesTo("a").single())
    }

    @Test
    fun unsubscribeStopsDelivery() {
        send("a", NappletIncRequest.Subscribe("1", "t"))
        send("a", NappletIncRequest.Unsubscribe("t"))
        send("b", NappletIncRequest.Emit("t", "1"))
        assertTrue(pushesTo("a").isEmpty())
    }

    @Test
    fun everySurfaceIsItsOwnEndpoint() {
        assertNotEquals(idOf("a"), idOf("b"))
        assertEquals(idOf("a"), idOf("a"))
        val before = idOf("a")
        bus.remove("a")
        assertNotEquals(before, idOf("a"))
    }

    @Test
    fun aChannelIsAnnouncedToTheTargetBeforeTheOpenerHearsBack() {
        val target = idOf("player")
        val result = send("controller", NappletIncRequest.ChannelOpen("ch1", target))!!
        assertEquals("inc.channel.open.result", result.text("type"))
        assertNull(result.text("error"))
        val channelId = result.text("channelId")!!
        assertEquals(target, result.text("peer"))

        val opened = pushesTo("player").single()
        assertEquals("inc.channel.opened", opened.text("type"))
        assertEquals(channelId, opened.text("channelId"))
        assertEquals(idOf("controller"), opened.text("peer"))

        send("controller", NappletIncRequest.ChannelEmit(channelId, """{"command":"play"}"""))
        val event = pushesTo("player").last()
        assertEquals("inc.channel.event", event.text("type"))
        assertEquals(idOf("controller"), event.text("sender"))
        send("player", NappletIncRequest.ChannelEmit(channelId, """{"status":"playing"}"""))
        assertEquals(target, pushesTo("controller").last().text("sender"))
    }

    @Test
    fun aChannelNeedsALiveTargetOtherThanItself() {
        assertEquals("target not found", send("a", NappletIncRequest.ChannelOpen("1", "nobody"))?.text("error"))
        assertTrue(send("a", NappletIncRequest.ChannelOpen("2", idOf("a")))?.text("error") != null)
        val gone = idOf("b")
        bus.remove("b")
        assertEquals("target not found", send("a", NappletIncRequest.ChannelOpen("3", gone))?.text("error"))
    }

    @Test
    fun onlyTheTwoEndsCanUseAChannel() {
        val channelId = send("a", NappletIncRequest.ChannelOpen("1", idOf("b")))!!.text("channelId")!!
        send("intruder", NappletIncRequest.ChannelEmit(channelId, "1"))
        send("intruder", NappletIncRequest.ChannelClose(channelId))
        assertEquals(listOf("inc.channel.opened"), pushesTo("b").map { it.text("type") })
        assertTrue(pushesTo("a").isEmpty())
    }

    @Test
    fun closingTellsBothEnds() {
        val channelId = send("a", NappletIncRequest.ChannelOpen("1", idOf("b")))!!.text("channelId")!!
        send("b", NappletIncRequest.ChannelClose(channelId))
        assertEquals("inc.channel.closed", pushesTo("a").single().text("type"))
        assertEquals("inc.channel.closed", pushesTo("b").last().text("type"))
        // A closed channel carries nothing more.
        send("a", NappletIncRequest.ChannelEmit(channelId, "1"))
        assertEquals(2, pushesTo("b").size)
    }

    @Test
    fun aSurfaceGoingAwayClosesItsChannelsForThePeer() {
        val channelId = send("a", NappletIncRequest.ChannelOpen("1", idOf("b")))!!.text("channelId")!!
        bus.remove("b")
        val closed = pushesTo("a").single()
        assertEquals("inc.channel.closed", closed.text("type"))
        assertEquals(channelId, closed.text("channelId"))
        assertEquals(NappletIncBus.PEER_DESTROYED, closed.text("reason"))
        assertEquals(0, send("a", NappletIncRequest.ChannelList("l"))!!["channels"]!!.jsonArray.size)
    }

    @Test
    fun broadcastAndListCoverEveryOpenChannel() {
        val toB = send("a", NappletIncRequest.ChannelOpen("1", idOf("b")))!!.text("channelId")
        val fromC = send("c", NappletIncRequest.ChannelOpen("2", idOf("a")))!!.text("channelId")

        val channels = send("a", NappletIncRequest.ChannelList("l"))!!["channels"]!!.jsonArray.map { it.jsonObject }
        assertEquals(setOf(toB to idOf("b"), fromC to idOf("c")), channels.map { it.text("id") to it.text("peer") }.toSet())

        send("a", NappletIncRequest.ChannelBroadcast("""{"announcement":"bye"}"""))
        assertEquals("inc.channel.event", pushesTo("b").last().text("type"))
        assertEquals("inc.channel.event", pushesTo("c").last().text("type"))
        // The sender hears nothing back: all it got was c's channel opening.
        assertEquals(listOf("inc.channel.opened"), pushesTo("a").map { it.text("type") })
    }

    @Test
    fun channelsPerEndpointAreBounded() {
        send("a", NappletIncRequest.ChannelOpen("1", idOf("b")))
        send("a", NappletIncRequest.ChannelOpen("2", idOf("c")))
        assertEquals("too many channels", send("a", NappletIncRequest.ChannelOpen("3", idOf("d")))?.text("error"))
        assertTrue(pushesTo("d").isEmpty())
    }

    @Test
    fun nappletsOfAnotherAccountAreNotThere() {
        send("mine", NappletIncRequest.Subscribe("1", "t"))
        send("theirs", NappletIncRequest.Subscribe("2", "t"), account = "bob")
        send("theirs", NappletIncRequest.Emit("t", "1"), account = "bob")
        assertTrue(pushesTo("mine").isEmpty(), "an emit stays inside its account")

        val theirs = idOf("theirs", account = "bob")
        assertEquals("target not found", send("mine", NappletIncRequest.ChannelOpen("3", theirs))?.text("error"))
        assertTrue(pushesTo("theirs").isEmpty())
    }
}
