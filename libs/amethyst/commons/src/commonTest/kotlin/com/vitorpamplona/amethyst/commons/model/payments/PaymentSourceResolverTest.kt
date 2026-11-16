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
package com.vitorpamplona.amethyst.commons.model.payments

import com.vitorpamplona.amethyst.commons.model.clink.ClinkDebitWalletEntryNorm
import com.vitorpamplona.amethyst.commons.model.nip47WalletConnect.NwcWalletEntryNorm
import com.vitorpamplona.quartz.experimental.clink.pointers.NDebit
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentSourceResolverTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!
    private val pubKey = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"

    private fun nwc(id: String) = NwcWalletEntryNorm(id, "nwc-$id", Nip47WalletConnect.Nip47URINorm(pubKey, relay, secret = "ab".repeat(32)))

    private fun debit(id: String) = ClinkDebitWalletEntryNorm(id, "debit-$id", NDebit(pubKey, listOf(relay), "pointer-$id", null))

    @Test
    fun allListsNwcBeforeDebits() {
        val sources = PaymentSourceResolver.all(listOf(nwc("a")), listOf(debit("b")))
        assertTrue(sources[0] is PaymentSource.Nwc)
        assertTrue(sources[1] is PaymentSource.ClinkDebit)
    }

    @Test
    fun explicitDefaultSelectsAcrossEitherType() {
        val nwcWallets = listOf(nwc("a"))
        val debits = listOf(debit("b"))

        // a debit can be the unified default even when an NWC wallet exists
        val resolved = PaymentSourceResolver.resolveDefault(nwcWallets, debits, defaultId = "b")
        assertTrue(resolved is PaymentSource.ClinkDebit)
        assertEquals("b", resolved.id)
    }

    @Test
    fun fallsBackToFirstNwcWhenNoExplicitDefault() {
        val resolved = PaymentSourceResolver.resolveDefault(listOf(nwc("a")), listOf(debit("b")), defaultId = null)
        assertTrue(resolved is PaymentSource.Nwc)
        assertEquals("a", resolved.id)
    }

    @Test
    fun fallsBackToFirstDebitWhenNoNwc() {
        val resolved = PaymentSourceResolver.resolveDefault(emptyList(), listOf(debit("b"), debit("c")), defaultId = null)
        assertTrue(resolved is PaymentSource.ClinkDebit)
        assertEquals("b", resolved.id)
    }

    @Test
    fun staleDefaultIdFallsBackToFirst() {
        val resolved = PaymentSourceResolver.resolveDefault(listOf(nwc("a")), listOf(debit("b")), defaultId = "deleted")
        assertEquals("a", resolved?.id)
    }

    @Test
    fun noSourcesResolvesToNull() {
        assertNull(PaymentSourceResolver.resolveDefault(emptyList(), emptyList(), defaultId = null))
    }

    @Test
    fun debitSourceCannotShowBalance() {
        assertTrue(PaymentSource.Nwc(nwc("a")).canShowBalance)
        assertTrue(!PaymentSource.ClinkDebit(debit("b")).canShowBalance)
    }
}
