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

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.validate.LnurlEndpointResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A zap receipt is credited only after the recipient's LNURL provider vouches for its signer.
 * When the recipient's lightning address is malformed (its host can't be fetched), no provider
 * can vouch, so the receipt is shown but not credited — the same outcome as a failed lookup.
 */
class EventCacheZapLnurlTest {
    private val bob = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000002".hexToByteArray()))
    private val alice = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000003".hexToByteArray()))
    private val provider = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000004".hexToByteArray()))

    // Mainnet 100,000 sat invoice, from LnInvoiceUtilTest.
    private val invoice =
        "lnbc1m1pjt9u0qsp553q90pj5mafzv20w45eqavned9tgwhl4q99n9s5ppcw24nzw3zeqpp5002kd3ktym67du86kj665fgaev7ka8ys7j5yz5fg686lr5e2gfkshp5dkk27nnuax05az3pk2r6ytxtvwn5j4xzsq9ajprhc7crjkmgvr3qxqyjw5qcqpjrzjqtzxvfsuxe4l92pf97tt4rcgpy2xalkmlwexh899wqxf83l8nwv4xzh0gvqq89qqqqqqqqlgqqqqq0gqvs9qxpqysgqx5mz04wd7kqu5zhhel9enr036hjrp4gga0nz084p2asjl36a0zmrk6mhqa249zsgqref2rlvhffm73u7rxgr47gden6rugup4ksvpzsqvds4pz"

    private suspend fun zapBobsNote(
        cache: EventCache,
        bobsLud16: String?,
    ): Int {
        val content = if (bobsLud16 == null) "{\"name\":\"bob\"}" else "{\"name\":\"bob\",\"lud16\":\"$bobsLud16\"}"
        cache.justConsume(bob.sign<MetadataEvent>(1_000, MetadataEvent.KIND, emptyArray(), content), null, true)

        val note = bob.sign<TextNoteEvent>(1_000, TextNoteEvent.KIND, emptyArray(), "hi")
        cache.justConsume(note, null, true)

        val zapRequest =
            alice.sign<ZapRequestEvent>(
                1_100,
                ZapRequestEvent.KIND,
                arrayOf(arrayOf("e", note.id), arrayOf("p", bob.pubKey), arrayOf("amount", "100000000"), arrayOf("relays", "wss://relay.example.com/")),
                "",
            )
        val receipt =
            provider.sign<ZapReceiptEvent>(
                1_200,
                ZapReceiptEvent.KIND,
                arrayOf(arrayOf("e", note.id), arrayOf("p", bob.pubKey), arrayOf("bolt11", invoice), arrayOf("description", zapRequest.toJson())),
                "",
            )
        cache.justConsume(receipt, null, true)

        assertNotNull(cache.getNoteIfExists(receipt.id)?.event, "the receipt is still shown")
        // The zap request alone adds a pending entry (request -> null); a credited zap has its receipt.
        return cache
            .getNoteIfExists(note.id)!!
            .zaps.values
            .count { it != null }
    }

    @Test
    fun malformedLightningAddressIsNotCredited() =
        runTest {
            val cache = EventCache()
            var lookups = 0
            cache.lnurlEndpointResolver =
                LnurlEndpointResolver {
                    lookups++
                    null
                }

            assertEquals(0, zapBobsNote(cache, "bob@stardroid.app)⭐"))
            assertEquals(0, lookups)
        }

    @Test
    fun noLightningAddressKeepsTheLegacySignatureOnlyCredit() =
        runTest {
            val cache = EventCache()
            cache.lnurlEndpointResolver = LnurlEndpointResolver { null }

            assertTrue(zapBobsNote(cache, null) == 1)
        }
}
