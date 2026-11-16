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
package com.vitorpamplona.amethyst.commons.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies how validated NIP-B1 BOLT12 zaps fold into a Note's aggregate zap
 * accounting — the model contract the LocalCache ingest path and the reaction-row
 * counter depend on.
 */
class NoteBolt12ZapTest {
    private fun note(id: String) = Note(id)

    @Test
    fun addsMillisatAmountsAsSatsToTheZapTotal() {
        val target = note("a".repeat(64))
        target.addBolt12Zap(note("b".repeat(64)), "hash1", amountMillisats = 21_000_000L, cryptoVerified = true)
        target.addBolt12Zap(note("c".repeat(64)), "hash2", amountMillisats = 1_000_000L, cryptoVerified = true)

        // 21_000_000 + 1_000_000 millisats = 22_000 sats.
        assertEquals(22_000L, target.zapsAmount.toLong())
        assertEquals(2, target.bolt12Zaps.size)
    }

    @Test
    fun deduplicatesByPaymentHash() {
        val target = note("a".repeat(64))
        target.addBolt12Zap(note("b".repeat(64)), "samehash", amountMillisats = 21_000_000L, cryptoVerified = true)
        // A relay echo (or a re-publish from another source) of the same settled payment.
        target.addBolt12Zap(note("c".repeat(64)), "samehash", amountMillisats = 21_000_000L, cryptoVerified = true)

        assertEquals(1, target.bolt12Zaps.size)
        assertEquals(21_000L, target.zapsAmount.toLong())
    }

    @Test
    fun keepsTheLowerAmountWhenTwoProofsShareAPaymentHash() {
        // NIP-B1: same proof identifier, differing amounts → count the LOWER, in either order.
        val a = note("a".repeat(64))
        a.addBolt12Zap(note("b".repeat(64)), "h", amountMillisats = 21_000_000L, cryptoVerified = true)
        a.addBolt12Zap(note("c".repeat(64)), "h", amountMillisats = 5_000_000L, cryptoVerified = true)
        assertEquals(1, a.bolt12Zaps.size)
        assertEquals(5_000L, a.zapsAmount.toLong())

        val b = note("a".repeat(64))
        b.addBolt12Zap(note("b".repeat(64)), "h", amountMillisats = 5_000_000L, cryptoVerified = true)
        b.addBolt12Zap(note("c".repeat(64)), "h", amountMillisats = 21_000_000L, cryptoVerified = true)
        assertEquals(5_000L, b.zapsAmount.toLong(), "order must not change the counted amount")
    }

    @Test
    fun aVerifiedEntryIsNotDowngradedByAnUnverifiedRepublish() {
        val target = note("a".repeat(64))
        target.addBolt12Zap(note("b".repeat(64)), "h", amountMillisats = 5_000_000L, cryptoVerified = true)
        target.addBolt12Zap(note("c".repeat(64)), "h", amountMillisats = 5_000_000L, cryptoVerified = false)

        assertEquals(1, target.bolt12Zaps.size)
        assertTrue(target.bolt12Zaps["h"]!!.cryptoVerified, "a verified entry must not be overwritten by an unverified one")
    }

    @Test
    fun aLowerUnverifiedRepublishCannotGriefAVerifiedTotal() {
        // A payer proof publishes its proof_preimage, so once a BOLT12 zap is public
        // anyone can replay the same payment hash in an UNVERIFIABLE (e.g. compressed)
        // proof carrying a self-chosen 1-msat amount. The lower-amount dedup MUST apply
        // only among validated proofs — an unverified duplicate can neither lower the
        // counted amount nor inherit the verified flag. Both arrival orders.
        val verifiedFirst = note("a".repeat(64))
        verifiedFirst.addBolt12Zap(note("b".repeat(64)), "h", amountMillisats = 5_000_000L, cryptoVerified = true)
        verifiedFirst.addBolt12Zap(note("c".repeat(64)), "h", amountMillisats = 1L, cryptoVerified = false)
        assertEquals(1, verifiedFirst.bolt12Zaps.size)
        assertTrue(verifiedFirst.bolt12Zaps["h"]!!.cryptoVerified)
        assertEquals(5_000L, verifiedFirst.zapsAmount.toLong(), "an unverified 1-msat replay must not grief the total down")

        val unverifiedFirst = note("a".repeat(64))
        unverifiedFirst.addBolt12Zap(note("c".repeat(64)), "h", amountMillisats = 1L, cryptoVerified = false)
        unverifiedFirst.addBolt12Zap(note("b".repeat(64)), "h", amountMillisats = 5_000_000L, cryptoVerified = true)
        assertEquals(1, unverifiedFirst.bolt12Zaps.size)
        assertTrue(unverifiedFirst.bolt12Zaps["h"]!!.cryptoVerified)
        assertEquals(5_000L, unverifiedFirst.zapsAmount.toLong(), "order must not matter")
    }

    @Test
    fun removingBySourceDropsTheEntryAndUpdatesTheTotal() {
        val target = note("a".repeat(64))
        val source = note("b".repeat(64))
        target.addBolt12Zap(source, "h", amountMillisats = 7_000_000L, cryptoVerified = true)
        assertEquals(7_000L, target.zapsAmount.toLong())

        target.removeBolt12ZapBySource(source)
        assertTrue(target.bolt12Zaps.isEmpty())
        assertEquals(0L, target.zapsAmount.toLong())
    }

    @Test
    fun clearChildLinksDropsBolt12ZapsAndReturnsTheirSources() {
        val target = note("a".repeat(64))
        val source = note("b".repeat(64))
        target.addBolt12Zap(source, "h", amountMillisats = 3_000_000L, cryptoVerified = true)
        assertTrue(target.hasZapsBoostsOrReactions())

        val removed = target.clearChildLinks()

        assertTrue(source in removed, "the source note must be returned so the cache can prune it")
        assertTrue(target.bolt12Zaps.isEmpty())
        assertEquals(0L, target.zapsAmount.toLong())
        assertFalse(target.hasZapsBoostsOrReactions())
    }

    @Test
    fun onlyCryptoVerifiedBolt12ZapsCountTowardTheTotal() {
        val target = note("a".repeat(64))
        target.addBolt12Zap(note("b".repeat(64)), "h1", amountMillisats = 2_000_000L, cryptoVerified = true)
        // An unverified (compressed / unbindable) proof is stored + shown but MUST NOT count —
        // its amount is self-chosen with no settled-payment guarantee.
        target.addBolt12Zap(note("c".repeat(64)), "h2", amountMillisats = 500_000L, cryptoVerified = false)

        assertEquals(2, target.bolt12Zaps.size, "both are stored (the unverified one still renders, dimmed)")
        assertEquals(2_000L, target.zapsAmount.toLong(), "only the verified 2000-sat zap counts")
    }

    @Test
    fun fractionalSatAmountsSurviveInTheTotal() {
        val target = note("a".repeat(64))
        // 1500 msat = 1.5 sat; two of them = 3 sat. Integer-dividing each first drops to 2.
        target.addBolt12Zap(note("b".repeat(64)), "h1", amountMillisats = 1_500L, cryptoVerified = true)
        target.addBolt12Zap(note("c".repeat(64)), "h2", amountMillisats = 1_500L, cryptoVerified = true)
        assertEquals(3L, target.zapsAmount.toLong(), "fractional sats must not be truncated per-entry")
    }
}
