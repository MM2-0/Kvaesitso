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
package com.vitorpamplona.amethyst.commons.model.nip47WalletConnect

import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionRowLabelsTest {
    private val recipientHex = "ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b"

    /**
     * The bug this class exists for. Wallets send `"description": ""` for a payment
     * with no memo; the old row did `tx.description ?: fallback`, which only catches
     * null, so it rendered an empty Text — a line with the height of a real one and
     * nothing in it. Outgoing rows looked like a bare arrow and a date.
     */
    @Test
    fun aDescriptionWithNothingInItFallsBackToTheDirection() {
        // Empty and whitespace are what wallets actually send for a payment with no
        // memo; absent is the spec-clean form. All three must reach the fallback.
        listOf("", "   ", null).forEach { description ->
            val labels = TransactionRowLabels.resolve(NwcTransaction(type = "outgoing", description = description), "Sent")

            assertEquals("description=<$description>", TransactionRowLabels.Title.Literal("Sent"), labels.title)
            assertNull("description=<$description>", labels.subtitle)
        }
    }

    @Test
    fun aRealDescriptionIsTheTitle() {
        val labels = TransactionRowLabels.resolve(NwcTransaction(type = "outgoing", description = "Coffee"), "Sent")
        assertEquals(TransactionRowLabels.Title.Literal("Coffee"), labels.title)
        assertNull(labels.subtitle)
    }

    /** An outgoing zap resolves the payee from the zap request's `p` tag. */
    @Test
    fun anOutgoingZapNamesThePayee() {
        val labels =
            TransactionRowLabels.resolve(
                NwcTransaction(
                    type = "outgoing",
                    description = "",
                    metadata =
                        mapOf(
                            "recipient_data" to mapOf("identifier" to "user@domain.com"),
                            "nostr" to
                                mapOf(
                                    "pubkey" to "f512822a89d2369a386bfeb1e687ccd26ceb6bb33e73b98417499bb9054bff1f",
                                    "content" to "great post",
                                    "tags" to listOf(listOf("p", recipientHex)),
                                ),
                        ),
                ),
                "Sent",
            )

        assertEquals(TransactionRowLabels.Title.User(recipientHex, "user@domain.com"), labels.title)
        assertEquals("great post", labels.subtitle)
    }

    /** With no zap request, the lightning address alone still labels the row. */
    @Test
    fun theLeanPairAloneStillNamesThePayee() {
        val labels =
            TransactionRowLabels.resolve(
                NwcTransaction(
                    type = "outgoing",
                    description = "",
                    metadata = mapOf("recipient_data" to mapOf("identifier" to "user@domain.com")),
                ),
                "Sent",
            )

        assertEquals(TransactionRowLabels.Title.Literal("user@domain.com"), labels.title)
        // Named but undescribed: the second line says what the row was.
        assertEquals("Sent", labels.subtitle)
    }

    /** Incoming rows keep working off the payer, which is what they did before. */
    @Test
    fun anIncomingZapStillNamesTheSender() {
        val labels =
            TransactionRowLabels.resolve(
                NwcTransaction(
                    type = "incoming",
                    description = "Test",
                    metadata = mapOf("nostr" to mapOf("pubkey" to recipientHex, "content" to "Test")),
                ),
                "Received",
            )

        assertTrue(labels.title is TransactionRowLabels.Title.User)
        // The comment merely repeats the description, so it is not shown twice.
        assertEquals("Test", labels.subtitle)
    }

    /** A note zap's `e` tag reaches the row, so it can open the zapped note. */
    @Test
    fun aNoteZapCarriesTheNoteId() {
        val noteHex = "d".repeat(64)
        val labels =
            TransactionRowLabels.resolve(
                NwcTransaction(
                    type = "outgoing",
                    metadata = mapOf("nostr" to mapOf("tags" to listOf(listOf("e", noteHex), listOf("p", recipientHex)))),
                ),
                "Sent",
            )
        assertEquals(noteHex, labels.zappedNoteId)
    }

    @Test
    fun aProfileZapCarriesNoNoteId() {
        val labels =
            TransactionRowLabels.resolve(
                NwcTransaction(type = "outgoing", metadata = mapOf("nostr" to mapOf("tags" to listOf(listOf("p", recipientHex))))),
                "Sent",
            )
        assertNull(labels.zappedNoteId)
    }
}
