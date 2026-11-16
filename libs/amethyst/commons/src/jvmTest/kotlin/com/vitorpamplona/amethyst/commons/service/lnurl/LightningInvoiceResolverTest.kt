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
package com.vitorpamplona.amethyst.commons.service.lnurl

import com.vitorpamplona.amethyst.commons.notices.InvoiceNotice
import com.vitorpamplona.amethyst.commons.notices.LnurlFailureDetail
import com.vitorpamplona.amethyst.commons.service.lnurl.LightningInvoiceResolver
import com.vitorpamplona.amethyst.commons.service.lnurl.LnurlHttpResponse
import com.vitorpamplona.amethyst.commons.service.lnurl.LnurlHttpTransport
import com.vitorpamplona.amethyst.commons.service.lnurl.roundHalfUpToSats
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * [LightningInvoiceResolver] over a scripted transport. The replies use the loose typing real
 * LNURL servers send (a boolean as text, a number for a flag), which the resolver was written to
 * read through Jackson's lenient readers.
 */
class LightningInvoiceResolverTest {
    // A 1 mBTC (100,000 sat) BOLT-11.
    private val invoice100kSats = "lnbc1m1pjt9u0qsp553q90pj5mafzv20w45eqavned9tgwhl4q99n9s5ppcw24nzw3zeqpp5002kd3ktym67du86kj665fgaev7ka8ys7j5yz5fg686lr5e2gfkshp5dkk27nnuax05az3pk2r6ytxtvwn5j4xzsq9ajprhc7crjkmgvr3qxqyjw5qcqpjrzjqtzxvfsuxe4l92pf97tt4rcgpy2xalkmlwexh899wqxf83l8nwv4xzh0gvqq89qqqqqqqqlgqqqqq0gqvs9qxpqysgqx5mz04wd7kqu5zhhel9enr036hjrp4gga0nz084p2asjl36a0zmrk6mhqa249zsgqref2rlvhffm73u7rxgr47gden6rugup4ksvpzsqvds4pz"

    private class ScriptedTransport(
        private val replies: Map<String, String>,
    ) : LnurlHttpTransport {
        val requested = mutableListOf<String>()

        override suspend fun get(url: String): LnurlHttpResponse {
            requested += url
            val key = replies.keys.first { url.startsWith(it) }
            return LnurlHttpResponse(200, "OK", replies.getValue(key))
        }
    }

    private fun resolverFor(
        lnurlp: String,
        invoiceReply: String = """{"pr":"$invoice100kSats","routes":[]}""",
    ): Pair<LightningInvoiceResolver, ScriptedTransport> {
        val transport =
            ScriptedTransport(
                mapOf(
                    "https://example.com/.well-known/lnurlp/alice" to lnurlp,
                    "https://example.com/callback" to invoiceReply,
                ),
            )
        return LightningInvoiceResolver(transport) to transport
    }

    @Test
    fun resolvesAnInvoiceAndEncodesTheComment() =
        runBlocking {
            val (resolver, transport) = resolverFor("""{"callback":"https://example.com/callback","allowsNostr":true,"nostrPubkey":"ab"}""")
            var progress = 0f

            val pr = resolver.lnAddressInvoice("alice@example.com", 100_000_000L, "gm & ☕", onProgress = { progress = it })

            assertEquals(invoice100kSats, pr)
            assertEquals(0.7f, progress)
            assertEquals("https://example.com/callback?amount=100000000&comment=gm+%26+%E2%98%95", transport.requested[1])
        }

    @Test
    fun readsLooselyTypedFlagsLikeJackson() =
        runBlocking {
            val zapRequest = ZapRequestEvent("00".repeat(32), "11".repeat(32), 1L, emptyArray(), "", "22".repeat(64))
            // The text "true" and a non-zero number count as true, as Jackson's asBoolean() read them.
            listOf("\"true\"" to true, "true" to true, "1" to true, "0" to false, "false" to false, "\"yes\"" to false).forEach { (flag, allows) ->
                val (resolver, transport) = resolverFor("""{"callback":"https://example.com/callback","allowsNostr":$flag}""")
                var sent: ZapRequestEvent? = null

                resolver.lnAddressInvoice("alice@example.com", 100_000_000L, "", zapRequest, onProgress = {}, onZapRequestSent = { sent = it })

                assertEquals(allows, sent != null, "allowsNostr=$flag")
                assertEquals(allows, transport.requested[1].contains("&nostr="), "allowsNostr=$flag")
            }
        }

    @Test
    fun appendsParametersToACallbackThatAlreadyHasAQuery() =
        runBlocking {
            val (resolver, transport) = resolverFor("""{"callback":"https://example.com/callback?id=7"}""")

            resolver.lnAddressInvoice("alice@example.com", 100_000_000L, "hi", onProgress = {})

            assertEquals("https://example.com/callback?id=7&amount=100000000&comment=hi", transport.requested[1])
        }

    @Test
    fun roundsHalfASatUp() {
        assertEquals(0L, roundHalfUpToSats(499))
        assertEquals(1L, roundHalfUpToSats(500))
        assertEquals(100_000L, roundHalfUpToSats(100_000_000))
        assertEquals(-1L, roundHalfUpToSats(-500))
    }

    private class FailingTransport(
        private val status: Int,
        private val reason: String,
        private val body: String,
    ) : LnurlHttpTransport {
        override suspend fun get(url: String) = LnurlHttpResponse(status, reason, body)
    }

    @Test
    fun aMissingCallbackIsReportedAsSuch() =
        runBlocking {
            val (resolver, _) = resolverFor("""{"allowsNostr":true}""")

            val error = assertFailsWith<LightningInvoiceResolver.LightningAddressError> { resolver.lnAddressInvoice("alice@example.com", 1000L, "", onProgress = {}) }

            val notice = assertIs<InvoiceNotice.CallbackNotFound>(error.notice)
            assertEquals("alice@example.com", notice.lnAddress)
        }

    @Test
    fun aRefusingServerIsReportedAsUnavailableWithItsMessage() =
        runBlocking {
            val resolver = LightningInvoiceResolver(FailingTransport(503, "Service Unavailable", """{"status":"error","message":"down for maintenance"}"""))

            val error = assertFailsWith<LightningInvoiceResolver.LightningAddressError> { resolver.lnAddressInvoice("alice@example.com", 1000L, "", onProgress = {}) }

            // Not re-wrapped as "could not resolve": the server answered, and said why.
            val notice = assertIs<InvoiceNotice.ServiceUnavailable>(error.notice)
            assertEquals("https://example.com/.well-known/lnurlp/alice", notice.url)
            assertEquals("down for maintenance", assertIs<LnurlFailureDetail.ServerMessage>(notice.detail).text)
        }

    @Test
    fun aBareErrorStatusKeepsTheStatus() =
        runBlocking {
            val resolver = LightningInvoiceResolver(FailingTransport(404, "Not Found", ""))

            val error = assertFailsWith<LightningInvoiceResolver.LightningAddressError> { resolver.lnAddressInvoice("alice@example.com", 1000L, "", onProgress = {}) }

            val detail = assertIs<LnurlFailureDetail.HttpStatus>(assertIs<InvoiceNotice.ServiceUnavailable>(error.notice).detail)
            assertEquals(404, detail.code)
            assertEquals("Not Found", detail.reason)
        }
}
