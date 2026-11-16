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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Stream zaps show in the live chat when they pay the stream's host. zap.stream (and Shosho,
 * primalstream, …) sign the kind-30311 with the service's key and name the streamer in a
 * `p … host` tag, so the zap's `p` is the host while the `a` carries the service's key.
 */
class EventCacheLiveStreamZapTest {
    private val service = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000002".hexToByteArray()))
    private val host = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000003".hexToByteArray()))
    private val guest = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000004".hexToByteArray()))
    private val viewer = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000005".hexToByteArray()))
    private val provider = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000006".hexToByteArray()))

    private fun newCache() = EventCache().also { it.lnurlEndpointResolver = LnurlEndpointResolver { null } }

    private suspend fun stream(
        signer: NostrSignerInternal,
        hostTag: NostrSignerInternal?,
    ): LiveActivitiesEvent {
        val tags =
            listOfNotNull(
                arrayOf("d", "stream-1"),
                arrayOf("status", "live"),
                hostTag?.let { arrayOf("p", it.pubKey, "", "host") },
            ).toTypedArray()
        return signer.sign(1_000, LiveActivitiesEvent.KIND, tags, "")
    }

    private suspend fun zap(
        streamAddress: Address,
        receiver: NostrSignerInternal,
    ): ZapReceiptEvent {
        val a = streamAddress.toValue()
        val request =
            viewer.sign<ZapRequestEvent>(
                1_100,
                ZapRequestEvent.KIND,
                arrayOf(arrayOf("a", a), arrayOf("p", receiver.pubKey), arrayOf("amount", "21000"), arrayOf("relays", "wss://relay.example.com/")),
                "",
            )
        return provider.sign(
            1_200,
            ZapReceiptEvent.KIND,
            arrayOf(arrayOf("a", a), arrayOf("p", receiver.pubKey), arrayOf("description", request.toJson())),
            "",
        )
    }

    private fun EventCache.streamHas(
        address: Address,
        receipt: ZapReceiptEvent,
    ) = getLiveActivityChannelIfExists(address)?.notes?.containsKey(receipt.id) == true

    @Test
    fun zapToTheHostOfAServiceSignedStreamShowsInTheChat() =
        runTest {
            val cache = newCache()
            val stream = stream(service, hostTag = host)
            cache.justConsume(stream, null, true)

            val receipt = zap(stream.address(), host)
            cache.justConsume(receipt, null, true)

            assertTrue(cache.streamHas(stream.address(), receipt))
        }

    @Test
    fun zapToTheAuthorOfASelfHostedStreamShowsInTheChat() =
        runTest {
            val cache = newCache()
            val stream = stream(host, hostTag = null)
            cache.justConsume(stream, null, true)

            val receipt = zap(stream.address(), host)
            cache.justConsume(receipt, null, true)

            assertTrue(cache.streamHas(stream.address(), receipt))
        }

    @Test
    fun zapThatArrivesBeforeTheStreamEventShowsInTheChat() =
        runTest {
            val cache = newCache()
            val stream = stream(service, hostTag = host)

            val receipt = zap(stream.address(), host)
            cache.justConsume(receipt, null, true)
            cache.justConsume(stream, null, true)

            assertTrue(cache.streamHas(stream.address(), receipt))
        }

    @Test
    fun zapToSomeoneWhoIsNotAHostStaysOutOfTheChat() =
        runTest {
            val cache = newCache()
            val stream = stream(service, hostTag = host)
            cache.justConsume(stream, null, true)

            val receipt = zap(stream.address(), guest)
            cache.justConsume(receipt, null, true)

            assertFalse(cache.streamHas(stream.address(), receipt))
        }
}
