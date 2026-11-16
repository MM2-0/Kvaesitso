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
package com.vitorpamplona.amethyst.commons.routstr

import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.routstr.node.RoutstrNodeEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where a saved key's calls go, whatever the announcements say: an attacker's onion never gets one. */
class RoutstrRoutingTest {
    private val realPubKey = "1".repeat(64)
    private val attackerPubKey = "e".repeat(64)
    private val realOnion = "http://" + "r".repeat(56) + ".onion"
    private val attackerOnion = "http://" + "x".repeat(56) + ".onion"
    private val routstrUrl = "https://api.routstr.com"

    /** Routstr's real node, over https; every other call is recorded and unanswered. */
    private class FakeNodes(
        val answers: Map<String, RoutstrInfo>,
    ) : RoutstrApi {
        val calls = mutableListOf<String>()

        private fun unreachable(): Nothing = throw RoutstrException(RoutstrError(null, RoutstrFailure.UNREACHABLE))

        override suspend fun info(base: String): RoutstrInfo {
            calls += "info $base"
            return answers[base] ?: unreachable()
        }

        override suspend fun models(
            base: String,
            apiKey: String?,
        ): List<RoutstrModel> {
            calls += "models $base"
            unreachable()
        }

        override suspend fun balance(
            base: String,
            apiKey: String,
        ): RoutstrBalance {
            calls += "balance $base"
            return RoutstrBalance(1000, null, null, null, null)
        }

        override suspend fun createInvoice(
            base: String,
            amountSats: Long,
            purpose: RoutstrPurpose,
            apiKey: String?,
        ): RoutstrInvoice {
            calls += "invoice $base"
            unreachable()
        }

        override suspend fun invoiceStatus(
            base: String,
            invoiceId: String,
        ): RoutstrInvoiceStatus = unreachable()

        override suspend fun recover(
            base: String,
            bolt11: String,
        ): RoutstrInvoiceStatus = unreachable()

        override suspend fun createWithCashu(
            base: String,
            token: String,
        ): RoutstrCashuResult = unreachable()

        override suspend fun topupWithCashu(
            base: String,
            apiKey: String,
            token: String,
        ): RoutstrCashuResult = unreachable()
    }

    private fun info(
        npub: String,
        onion: String?,
    ) = RoutstrInfo("Routstr", null, "0.5.1", npub, listOf("https://mint.example"), routstrUrl, onion)

    private fun announcement(
        author: String,
        vararg urls: String,
    ) = RoutstrNodeEvent(
        id = "0".repeat(64),
        pubKey = author,
        createdAt = 1_000L,
        tags = arrayOf(arrayOf("d", "node")) + urls.map { arrayOf("u", it) },
        content = """{"name":"Routstr (official)"}""",
        sig = "0".repeat(128),
    )

    private val attacker = announcement(attackerPubKey, routstrUrl, attackerOnion)

    @Test
    fun anAttackerAnnouncingRoutstrsHostNeverGetsTheKey() =
        runTest {
            val api = FakeNodes(mapOf(routstrUrl to info(realPubKey, realOnion)))
            val routing = RoutstrRouting(api, torEnabled = { true })
            val key = AgentKeyTag("sk-real", "$routstrUrl/v1")

            // The key goes to its own node: here, the onion that node's https /v1/info names.
            assertEquals(RoutstrNodes.root(realOnion), routing.baseFor(key.url))
            // The attacker's announcement isn't the key's node, so it names nothing.
            assertNull(routing.nodeForKey(listOf(attacker), key.url))
            assertEquals("Routstr", routing.nodeName(listOf(attacker), key.url))
            // A purchase at the attacker's announcement goes to the real host, at its own onion.
            val target = routing.target(RoutstrNode.from(attacker).httpsUrl!!, "x")
            assertEquals(routstrUrl, target.root)
            assertEquals(RoutstrNodes.root(realOnion), target.onionRoot)
            assertEquals(RoutstrNodes.root(realOnion), target.callBase(torEnabled = true))
            // An announcement alone never supplies an onion.
            assertNull(RoutstrTarget.of(RoutstrNode.from(attacker))?.onionRoot)
            // ...and the announcement isn't verified.
            assertEquals(RoutstrVerification.Mismatch, routing.verify(RoutstrNode.from(attacker)))

            assertTrue(api.calls.none { "x".repeat(56) in it }, api.calls.toString())
            assertTrue(api.calls.all { it.startsWith("info https://") }, api.calls.toString())
        }

    @Test
    fun withoutAnOnionOfItsOwnTheKeyStaysOnHttps() =
        runTest {
            val api = FakeNodes(mapOf(routstrUrl to info(realPubKey, onion = null)))
            val routing = RoutstrRouting(api, torEnabled = { true })
            assertEquals(routstrUrl, routing.baseFor("$routstrUrl/v1"))
            assertTrue(api.calls.none { ".onion" in it })
        }

    @Test
    fun anAnnouncementNeverMakesAKeyARoutstrKey() =
        runTest {
            // The node doesn't answer: only the announcements claim the hosts.
            val api = FakeNodes(emptyMap())
            val routing = RoutstrRouting(api, torEnabled = { true })
            val openRouter = AgentKeyTag("sk-or-v1-real", "https://openrouter.ai/api/v1")
            val routstr = AgentKeyTag("sk-real", "$routstrUrl/v1")

            assertFalse(routing.isRoutstrKey(openRouter))
            assertFalse(routing.confirm(openRouter))
            assertFalse(routing.mayBeRoutstr(openRouter))
            assertFalse(routing.isRoutstrKey(routstr))
            assertFalse(routing.confirm(routstr))
            // OpenRouter is never asked anything.
            assertTrue(api.calls.none { "openrouter" in it }, api.calls.toString())
            // An unreachable node's key stays on its own https URL.
            assertEquals(routstrUrl, routing.baseFor(routstr.url))
        }

    @Test
    fun aKeyIsARoutstrKeyWhenItsOwnUrlSaysSo() =
        runTest {
            val api = FakeNodes(mapOf(routstrUrl to info(realPubKey, realOnion)))
            val routing = RoutstrRouting(api, torEnabled = { false })
            val key = AgentKeyTag("sk-real", "$routstrUrl/v1")
            assertTrue(routing.confirm(key))
            assertTrue(routing.isRoutstrKey(key))
            // Without Tor, its own https URL.
            assertEquals(routstrUrl, routing.baseFor(key.url))
        }

    @Test
    fun aKeyIsARoutstrKeyWhenItsOwnModelsArePricedInSats() =
        runTest {
            val plain = RoutstrInfo(null, null, null, null, null, null, null)
            val api = FakeNodes(mapOf("https://custom.example" to plain))
            val routing = RoutstrRouting(api, torEnabled = { false })
            val key = AgentKeyTag("sk-x", "https://custom.example/v1")
            assertFalse(routing.confirm(key))
            routing.notePricing(key.url, listOf(RoutstrModel("m", null, null, true, true, null, null, SatsPricing(1.0, 1.0, null, null), null)))
            assertTrue(routing.isRoutstrKey(key))
        }

    @Test
    fun verificationIsAskedOverHttpsOnly() =
        runTest {
            val api = FakeNodes(mapOf(routstrUrl to info(realPubKey, realOnion)))
            val routing = RoutstrRouting(api, torEnabled = { true })
            val real = RoutstrNode.from(announcement(realPubKey, routstrUrl, realOnion))
            assertEquals(RoutstrVerification.Verified, routing.verify(real))
            // An onion-only node is never verified: its onion's answer could be anyone's.
            val onionOnly = RoutstrNode.from(announcement(realPubKey, realOnion))
            assertEquals(RoutstrVerification.Unknown, routing.verify(onionOnly))
            assertEquals(listOf("info $routstrUrl"), api.calls)
            // Asking an onion for /v1/info is refused outright.
            assertNull(routing.info(realOnion))
            assertEquals(listOf("info $routstrUrl"), api.calls)
        }

    @Test
    fun aFakeOnionIsRefused() =
        runTest {
            assertNull(RoutstrNodes.onionRoot("http://x@evil.com/.onion"))
            assertNull(RoutstrNodes.onionRoot("http://test.onion"))
            assertNull(RoutstrNodes.onionRoot("http://evil.com/" + "a".repeat(56) + ".onion"))
            assertEquals(RoutstrNodes.root(realOnion), RoutstrNodes.onionRoot("$realOnion/v1"))

            // A node whose own /v1/info names a fake onion is called over https.
            val api = FakeNodes(mapOf(routstrUrl to info(realPubKey, "http://x@evil.com/.onion")))
            val routing = RoutstrRouting(api, torEnabled = { true })
            assertEquals(routstrUrl, routing.baseFor("$routstrUrl/v1"))
            assertNull(routing.onionOf(routstrUrl))
            // A stored record or a target holding one never calls it.
            assertEquals(routstrUrl, RoutstrTarget(routstrUrl, "http://x@evil.com/.onion").callBase(torEnabled = true))
        }
}
