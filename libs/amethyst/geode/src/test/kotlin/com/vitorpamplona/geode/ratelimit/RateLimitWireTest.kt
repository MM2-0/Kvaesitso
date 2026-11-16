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
package com.vitorpamplona.geode.ratelimit

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RateLimitPolicy
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Geode's per-client rate limits as a client sees them on the wire (nostr-protocol/nips#2498):
 * the refusal is NIP-01 `rate-limited:` and carries its back-off in milliseconds as the trailing
 * `params` element, and that back-off is honest — waiting it out is enough, and nothing less is.
 */
class RateLimitWireTest {
    private val http = OkHttpClient.Builder().build()
    private val notes = Notes()
    private val opened = mutableListOf<AutoCloseable>()

    private fun <T : AutoCloseable> T.closing() = also { opened += it }

    private fun geode(toml: String) = RateLimitedGeode(toml).closing()

    private fun socket(
        relay: RateLimitedGeode,
        headers: Map<String, String> = emptyMap(),
    ) = RawSocket(relay.server.url, http, headers).closing()

    @AfterTest
    fun tearDown() = opened.asReversed().forEach { it.close() }

    private fun RawSocket.publish(event: Event = notes.next()): OkMessage {
        send(notes.eventFrame(event))
        return nextMessage<OkMessage>("OK").also { assertEquals(event.id, it.eventId) }
    }

    private fun RawSocket.req(subId: String): String {
        send("""["REQ","$subId",{"kinds":[1],"limit":1}]""")
        return next("EOSE", "CLOSED")
    }

    @Test
    fun eventsPastTheBurstAreRefusedWithTheBackOffAsTheFifthElement() {
        val relay = geode(rateLimitsToml(eventsPerMinute = 60, eventBurst = 3))
        val ws = socket(relay)

        repeat(3) { assertTrue(ws.publish().success, "event #$it is inside the burst") }

        val event = notes.next()
        ws.send(notes.eventFrame(event))
        val raw = ws.next("OK")
        val wire = RawSocket.wire(raw)
        assertEquals(5, wire.size, "the refusal carries params: $raw")
        assertEquals("false", wire[2].toString())
        assertTrue(wire[3].jsonPrimitive.content.startsWith("rate-limited: "), raw)
        assertTrue(wire[4].jsonPrimitive.isString, "params is a string, not a number: $raw")

        val ok = parse<OkMessage>(raw)
        assertEquals(MachineReadablePrefix.RATE_LIMITED, MachineReadablePrefix.parse(ok.message))
        val backOff = assertNotNull(ok.retryAfterMs)
        assertTrue(backOff in 1..1_000, "one token a second, so at most a second: $backOff")
        assertEquals(ok.params, backOff.toString(), "a plain decimal integer, no leading zeros")
    }

    @Test
    fun waitingOutTheStatedBackOffIsEnoughAndNothingLessIs() {
        val relay = geode(rateLimitsToml(eventsPerMinute = 600, eventBurst = 1))
        val ws = socket(relay)

        assertTrue(ws.publish().success)
        val refused = ws.publish()
        val backOff = assertNotNull(refused.retryAfterMs, refused.message)

        // Asking again at once is refused again, with a back-off no longer than the first.
        val again = ws.publish()
        assertEquals(false, again.success)
        assertTrue(assertNotNull(again.retryAfterMs) <= backOff)

        Thread.sleep(backOff + 30)
        assertTrue(ws.publish().success, "the stated back-off was not enough")
    }

    @Test
    fun reqsPastTheBurstAreClosedWithTheBackOffAsTheFourthElement() {
        val relay = geode(rateLimitsToml(reqsPerMinute = 60, reqBurst = 2))
        val ws = socket(relay)

        assertTrue(ws.req("a").contains("\"EOSE\""))
        assertTrue(ws.req("b").contains("\"EOSE\""))

        val raw = ws.req("c")
        val wire = RawSocket.wire(raw)
        assertEquals(4, wire.size, raw)
        val closed = parse<ClosedMessage>(raw)
        assertEquals("c", closed.subId)
        assertTrue(closed.message.startsWith("rate-limited: "), raw)
        assertTrue(assertNotNull(closed.retryAfterMs) in 1..1_000, raw)
    }

    @Test
    fun countsShareTheReqAllowanceAndAreClosedTheSameWay() {
        val relay = geode(rateLimitsToml(reqsPerMinute = 60, reqBurst = 1))
        val ws = socket(relay)
        assertTrue(ws.req("a").contains("\"EOSE\""))

        ws.send("""["COUNT","q",{"kinds":[1]}]""")
        val closed = ws.nextMessage<ClosedMessage>("CLOSED", "COUNT")
        assertEquals("q", closed.subId)
        assertNotNull(closed.retryAfterMs)
    }

    @Test
    fun aNegOpenIsCountedAsAReqAndRefusedWithNegErr() {
        val relay = geode(rateLimitsToml(reqsPerMinute = 60, reqBurst = 1))
        val ws = socket(relay)
        assertTrue(ws.req("a").contains("\"EOSE\""))

        ws.send("""["NEG-OPEN","n",{"kinds":[1]},"6100"]""")
        val err = ws.nextMessage<NegErrMessage>("NEG-ERR", "NEG-MSG")
        assertTrue(err.reason.startsWith("rate-limited: "), err.reason)
    }

    @Test
    fun eventsAndReqsHaveSeparateAllowances() {
        val relay = geode(rateLimitsToml(eventsPerMinute = 60, eventBurst = 1, reqsPerMinute = 60, reqBurst = 1))
        val ws = socket(relay)

        assertTrue(ws.publish().success)
        assertEquals(false, ws.publish().success)
        assertTrue(ws.req("a").contains("\"EOSE\""), "spending the event allowance must not touch the REQ one")
    }

    @Test
    fun closeAndAuthAreNeverLimited() {
        val relay = geode(rateLimitsToml(eventsPerMinute = 60, eventBurst = 1, reqsPerMinute = 60, reqBurst = 1))
        val ws = socket(relay)
        assertTrue(ws.req("live").contains("\"EOSE\""))
        assertTrue(ws.publish().success)
        assertEquals(false, ws.publish().success)

        // CLOSE of a live sub has no answer: a refusal would come back as CLOSED.
        ws.send("""["CLOSE","live"]""")
        val auth = NostrSignerSync(KeyPair()).sign(RelayAuthEvent.build(relay.url, "challenge"))
        repeat(3) {
            ws.send("""["AUTH",${auth.toJson()}]""")
            val ok = ws.nextMessage<OkMessage>("OK", "CLOSED")
            assertEquals(auth.id, ok.eventId)
            assertTrue(ok.retryAfterMs == null && !ok.message.startsWith("rate-limited"), ok.message)
        }
    }

    @Test
    fun aSecondSocketFromTheSameClientSharesTheAllowance() {
        val relay = geode(rateLimitsToml(eventsPerMinute = 60, eventBurst = 2))
        val first = socket(relay)
        repeat(2) { assertTrue(first.publish().success) }

        val second = socket(relay)
        val refused = second.publish()
        assertEquals(false, refused.success, "a new socket must not reset the client's allowance")
        assertNotNull(refused.retryAfterMs)
    }

    @Test
    fun clientsBehindATrustedProxyAreCountedApart() {
        val relay =
            geode(
                rateLimitsToml(
                    eventsPerMinute = 60,
                    eventBurst = 1,
                    extra = "[http]\ntrusted_proxies = [\"127.0.0.1\"]",
                ),
            )
        val alice = socket(relay, mapOf("X-Forwarded-For" to "203.0.113.7"))
        val bob = socket(relay, mapOf("X-Forwarded-For" to "198.51.100.9"))

        assertTrue(alice.publish().success)
        assertEquals(false, alice.publish().success)
        assertTrue(bob.publish().success, "another client behind the proxy has its own allowance")

        // Only the last hop is believed: a client cannot claim a fresh address in front of it.
        val aliceSpoofing = socket(relay, mapOf("X-Forwarded-For" to "192.0.2.1, 203.0.113.7"))
        assertEquals(false, aliceSpoofing.publish().success)
    }

    @Test
    fun ipv6ClientsShareTheAllowanceOfTheirSlash64() {
        val relay =
            geode(
                rateLimitsToml(
                    eventsPerMinute = 60,
                    eventBurst = 1,
                    extra = "[http]\ntrusted_proxies = [\"127.0.0.1\"]",
                ),
            )
        val first = socket(relay, mapOf("X-Forwarded-For" to "2001:db8:0:1::10"))
        assertTrue(first.publish().success)
        // Another address in the same /64: the same host, rotating.
        val rotated = socket(relay, mapOf("X-Forwarded-For" to "2001:db8:0:1:dead:beef:0:1"))
        assertEquals(false, rotated.publish().success)
        val neighbour = socket(relay, mapOf("X-Forwarded-For" to "2001:db8:0:2::10"))
        assertTrue(neighbour.publish().success)
    }

    @Test
    fun anUntrustedPeersForwardingHeaderIsIgnored() {
        val relay = geode(rateLimitsToml(eventsPerMinute = 60, eventBurst = 1))
        val first = socket(relay, mapOf("X-Forwarded-For" to "203.0.113.7"))
        assertTrue(first.publish().success)
        val second = socket(relay, mapOf("X-Forwarded-For" to "198.51.100.9"))
        assertEquals(false, second.publish().success, "a header from an untrusted peer must not split the count")
    }

    @Test
    fun withoutRateLimitsNothingIsRefused() {
        val relay = geode("")
        val ws = socket(relay)
        repeat(50) { assertTrue(ws.publish().success) }
        repeat(30) { assertTrue(ws.req("s$it").contains("\"EOSE\"")) }
    }

    @Test
    fun theRefusalReasonsAreTheDocumentedOnes() {
        val relay = geode(rateLimitsToml(eventsPerMinute = 60, eventBurst = 1, reqsPerMinute = 60, reqBurst = 1))
        val ws = socket(relay)
        ws.publish()
        assertEquals("rate-limited: ${RateLimitPolicy.EVENTS_REFUSED}", ws.publish().message)
        ws.req("a")
        assertEquals("rate-limited: ${RateLimitPolicy.REQS_REFUSED}", parse<ClosedMessage>(ws.req("b")).message)
    }
}

private inline fun <reified T : Message> parse(raw: String): T = Message.fromJson(raw) as T
