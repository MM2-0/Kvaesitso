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

import com.vitorpamplona.amethyst.commons.util.parseJsonObjectOrNull
import com.vitorpamplona.amethyst.commons.util.stringOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NappletBridgeDocumentsTest {
    private class Proxy(
        val name: String,
    )

    private val docs = NappletBridgeDocuments<Proxy>()
    private val a = Proxy("a.com")
    private val b = Proxy("b.com")

    @Test
    fun replyReachesTheDocumentThatAsked() {
        assertFalse(docs.onMessage(a))
        val id = docs.brokerIdFor("r0")
        val (pageId, proxy) = docs.resolve(id)!!
        assertEquals("r0", pageId)
        assertSame(a, proxy)
    }

    @Test
    fun sameDocumentKeepsItsRequests() {
        docs.onMessage(a)
        val id = docs.brokerIdFor("r0")
        assertFalse(docs.onMessage(a))
        assertSame(a, docs.resolve(id)!!.second)
    }

    @Test
    fun replyForANavigatedAwayDocumentIsDropped() {
        docs.onMessage(a)
        val aRequest = docs.brokerIdFor("r0")
        assertTrue(docs.onMessage(b))
        // b.com numbers its own requests from r0 too; a.com's late reply must not resolve it.
        val bRequest = docs.brokerIdFor("r0")
        assertNull(docs.resolve(aRequest))
        assertSame(b, docs.resolve(bRequest)!!.second)
    }

    @Test
    fun returningToAnEarlierProxyIsStillANewDocument() {
        docs.onMessage(a)
        val first = docs.brokerIdFor("r0")
        docs.onMessage(b)
        docs.onMessage(a)
        assertNull(docs.resolve(first))
    }

    @Test
    fun pageIdsMayContainTheSeparator() {
        docs.onMessage(a)
        assertEquals("fire:7", docs.resolve(docs.brokerIdFor("fire:7"))!!.first)
    }

    @Test
    fun clearedSurfaceResolvesNothing() {
        docs.onMessage(a)
        val id = docs.brokerIdFor("r0")
        docs.clear()
        assertNull(docs.resolve(id))
        assertNull(docs.currentProxy)
    }

    @Test
    fun unstampedIdsAreRejected() {
        docs.onMessage(a)
        assertNull(docs.resolve("r0"))
        assertNull(docs.resolve(":r0"))
    }

    private fun json(raw: String) = parseJsonObjectOrNull(raw)!!

    @Test
    fun subscriptionPushesReachTheDocumentThatSubscribed() {
        docs.onMessage(a)
        val stamped = docs.stampSubscription(json("""{"type":"relay.subscribe","id":"r0","subId":"s0"}"""))!!
        val brokerSubId = stamped.stringOrNull("subId")!!
        val push = docs.resolvePush(json("""{"type":"relay.event","subId":"$brokerSubId"}"""))!!
        assertEquals("s0", push.stringOrNull("subId"))
    }

    @Test
    fun subscriptionPushesForANavigatedAwayDocumentAreDropped() {
        docs.onMessage(a)
        val brokerSubId = docs.stampSubscription(json("""{"type":"relay.subscribe","subId":"s0"}"""))!!.stringOrNull("subId")!!
        docs.onMessage(b)
        // b.com names its own subscription s0 too: a.com's decrypted events must not reach it.
        docs.stampSubscription(json("""{"type":"relay.subscribe","subId":"s0"}"""))
        assertNull(docs.resolvePush(json("""{"type":"relay.event","subId":"$brokerSubId"}""")))
    }

    @Test
    fun closeIsStampedLikeTheSubscribeItEnds() {
        docs.onMessage(a)
        val open = docs.stampSubscription(json("""{"type":"relay.subscribe","subId":"s0"}"""))!!
        val close = docs.stampSubscription(json("""{"type":"relay.close","subId":"s0"}"""))!!
        assertEquals(open.stringOrNull("subId"), close.stringOrNull("subId"))
    }

    @Test
    fun pushesWithoutASubscriptionGoToThePageOnScreen() {
        assertNull(docs.resolvePush(json("""{"type":"identity.changed"}""")))
        docs.onMessage(a)
        assertEquals("identity.changed", docs.resolvePush(json("""{"type":"identity.changed"}"""))!!.stringOrNull("type"))
        assertNull(docs.stampSubscription(json("""{"type":"nostr.signEvent","id":"r0"}""")))
    }

    @Test
    fun unstampedSubscriptionPushesAreDropped() {
        docs.onMessage(a)
        assertNull(docs.resolvePush(json("""{"type":"relay.event","subId":"s0"}""")))
    }

    @Test
    fun navigationEndsTheDocumentEvenIfTheNextNeverTalks() {
        docs.onMessage(a)
        val request = docs.brokerIdFor("r0")
        assertTrue(docs.onNavigation())
        assertNull(docs.resolve(request))
        assertNull(docs.resolvePush(json("""{"type":"identity.changed"}""")))
        // Nothing on screen talked yet: a second navigation has nothing to release.
        assertFalse(docs.onNavigation())
        // The next page's first message is not a "replacement": its predecessor was already released.
        assertFalse(docs.onMessage(b))
    }
}
