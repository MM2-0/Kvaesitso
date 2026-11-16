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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12Bech32
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12Offer
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.Bolt12Values
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.TlvRecord
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.bolt12.TlvStream
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locks the shared BOLT12 assembly surface amy drives: offer canonicalization,
 * offer decoding, and the kind:10058 offer-list round-trip.
 */
class Bolt12ZapActionsTest {
    // A minimal, well-formed offer: amount(8) + description(10), TLV-ascending.
    private fun sampleOffer(amountMsat: Long): String {
        val records =
            listOf(
                TlvRecord(Bolt12Offer.TYPE_AMOUNT, Bolt12Values.tu64ToBytes(amountMsat)),
                TlvRecord(Bolt12Offer.TYPE_DESCRIPTION, "coffee".encodeToByteArray()),
            )
        return Bolt12Bech32.encode(Bolt12Bech32.OFFER_HRP, TlvStream(records).encode())
    }

    @Test
    fun canonicalOfferAcceptsAValidOfferAndRejectsJunk() {
        val offer = sampleOffer(21_000L)
        assertEquals(offer.lowercase(), Bolt12ZapActions.canonicalOfferOrNull(offer))
        assertNull(Bolt12ZapActions.canonicalOfferOrNull("not-an-offer"))
        assertNull(Bolt12ZapActions.canonicalOfferOrNull("lnbc10n1xxx")) // bolt11, not bolt12
    }

    @Test
    fun decodeOfferReadsAmountAndDescription() {
        val fields = Bolt12ZapActions.decodeOffer(sampleOffer(21_000L))
        assertTrue(fields != null)
        assertEquals(21_000L, fields["amount_msat"])
        assertEquals("coffee", fields["description"])
        assertEquals(false, fields["has_paths"])
    }

    @Test
    fun decodeOfferReturnsNullForAProof() {
        assertNull(Bolt12ZapActions.decodeOffer("lnp1garbage"))
    }

    @Test
    fun decodeOfferOmitsAMalformedAmountWithoutThrowing() {
        // The TLV stream parses (ascending type, valid length), but the amount value is
        // 9 bytes — too long for a tu64. Reading it must not throw: the offer still
        // decodes, just without an `amount_msat`.
        val bad =
            Bolt12Bech32.encode(
                Bolt12Bech32.OFFER_HRP,
                TlvStream(listOf(TlvRecord(Bolt12Offer.TYPE_AMOUNT, ByteArray(9) { 1 }))).encode(),
            )
        val fields = Bolt12ZapActions.decodeOffer(bad)
        assertTrue(fields != null)
        assertNull(fields["amount_msat"])
    }

    @Test
    fun offerListRoundTrips() =
        runTest {
            val signer = NostrSignerInternal(KeyPair())
            val offers = listOf(sampleOffer(21_000L).lowercase(), sampleOffer(5_000L).lowercase())
            val event = Bolt12ZapActions.buildOfferList(signer, offers)

            assertEquals(10058, event.kind)
            assertEquals(offers, event.offers())
            assertEquals(signer.pubKey, event.pubKey)
        }
}
