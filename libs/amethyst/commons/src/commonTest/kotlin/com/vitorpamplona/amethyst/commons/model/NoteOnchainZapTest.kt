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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NoteOnchainZapTest {
    private fun freshNote(idHex: String = "f".repeat(64)) = Note(idHex)

    // Creates a sender-event Note whose `author.pubkeyHex` is set to [pubKey]. The Note's
    // own `idHex` is derived from the pubkey so it stays distinct from the target note.
    // `removeOnchainZapForSource` keys off `source.author?.pubkeyHex`, so the author
    // must be wired even in unit tests.
    private fun sourceNote(pubKey: HexKey): Note {
        val src = Note(pubKey)
        src.author = User(pubKey) { addr -> Note(addr.toValue()) }
        return src
    }

    @Test
    fun enumLevelOrderingIsMonotonic() {
        // Lock the documented status ordering. The upgrade guard in `innerAddOnchainZap`
        // depends on this — if someone reorders the enum without updating levels, the
        // guard silently breaks. This test fails before that happens.
        assertTrue(OnchainZapStatus.UNVERIFIED.level < OnchainZapStatus.PENDING.level)
        assertTrue(OnchainZapStatus.PENDING.level < OnchainZapStatus.CONFIRMED.level)
    }

    @Test
    fun unverifiedEntryIsStoredAndDoesNotAffectTotal() {
        val target = freshNote()
        val src = sourceNote("a".repeat(64))

        target.addOnchainZap(src, "tx1", claimedSats = 1000L, verifiedSats = 0L, status = OnchainZapStatus.UNVERIFIED)

        val entry = target.onchainZaps["tx1"]
        assertEquals(1, target.onchainZaps.size)
        assertSame(src, entry?.source)
        assertEquals(1000L, entry?.claimedSats)
        assertEquals(0L, entry?.verifiedSats)
        assertEquals(OnchainZapStatus.UNVERIFIED, entry?.status)
        assertEquals(BigDecimal(0), target.zapsAmount)
    }

    @Test
    fun pendingEntryIsStoredWithSourceAndDoesNotAffectTotal() {
        val target = freshNote()
        val src = sourceNote("b".repeat(64))

        target.addOnchainZap(src, "tx1", claimedSats = 1000L, verifiedSats = 1000L, status = OnchainZapStatus.PENDING)

        val entry = target.onchainZaps["tx1"]
        assertEquals(1, target.onchainZaps.size)
        assertSame(src, entry?.source)
        assertEquals(1000L, entry?.verifiedSats)
        assertEquals(OnchainZapStatus.PENDING, entry?.status)
        assertEquals(BigDecimal(0), target.zapsAmount)
    }

    @Test
    fun confirmedEntryIsStoredAndAddsToTotal() {
        val target = freshNote()
        val src = sourceNote("c".repeat(64))

        target.addOnchainZap(src, "tx1", claimedSats = 5000L, verifiedSats = 5000L, status = OnchainZapStatus.CONFIRMED)

        assertEquals(OnchainZapStatus.CONFIRMED, target.onchainZaps["tx1"]?.status)
        assertEquals(BigDecimal(5000L), target.zapsAmount)
    }

    @Test
    fun unverifiedThenPendingUpgradesSourceAmountAndStatus() {
        val target = freshNote()
        val firstSrc = sourceNote("d".repeat(64))
        val secondSrc = sourceNote("e".repeat(64))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 2500L, verifiedSats = 0L, status = OnchainZapStatus.UNVERIFIED)
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 2500L, verifiedSats = 2400L, status = OnchainZapStatus.PENDING)

        val entry = target.onchainZaps["tx1"]
        assertSame(secondSrc, entry?.source)
        assertEquals(OnchainZapStatus.PENDING, entry?.status)
        assertEquals(2400L, entry?.verifiedSats)
        assertEquals(BigDecimal(0), target.zapsAmount)
    }

    @Test
    fun pendingThenConfirmedUpgradesSourceAndUpdatesTotal() {
        val target = freshNote()
        val firstSrc = sourceNote("d".repeat(64))
        val secondSrc = sourceNote("e".repeat(64))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 2500L, verifiedSats = 2500L, status = OnchainZapStatus.PENDING)
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 2500L, verifiedSats = 2500L, status = OnchainZapStatus.CONFIRMED)

        val entry = target.onchainZaps["tx1"]
        assertSame(secondSrc, entry?.source)
        assertEquals(OnchainZapStatus.CONFIRMED, entry?.status)
        assertEquals(BigDecimal(2500L), target.zapsAmount)
    }

    @Test
    fun confirmedThenPendingIsIgnored() {
        val target = freshNote()
        val firstSrc = sourceNote("a1".repeat(32))
        val secondSrc = sourceNote("b2".repeat(32))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 7500L, verifiedSats = 7500L, status = OnchainZapStatus.CONFIRMED)
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 7500L, verifiedSats = 7500L, status = OnchainZapStatus.PENDING)

        val entry = target.onchainZaps["tx1"]
        assertSame(firstSrc, entry?.source)
        assertEquals(OnchainZapStatus.CONFIRMED, entry?.status)
        assertEquals(BigDecimal(7500L), target.zapsAmount)
    }

    @Test
    fun pendingThenUnverifiedIsIgnored() {
        val target = freshNote()
        val firstSrc = sourceNote("c3".repeat(32))
        val secondSrc = sourceNote("d4".repeat(32))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 100L, verifiedSats = 100L, status = OnchainZapStatus.PENDING)
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 100L, verifiedSats = 0L, status = OnchainZapStatus.UNVERIFIED)

        val entry = target.onchainZaps["tx1"]
        assertSame(firstSrc, entry?.source)
        assertEquals(OnchainZapStatus.PENDING, entry?.status)
        assertEquals(100L, entry?.verifiedSats)
    }

    @Test
    fun sameStatusHigherVerifiedSatsReplacesEntry() {
        // The indexer can revise its view of the recipient outputs upward across calls
        // (delayed mempool propagation, cached partial response). A larger verifiedSats
        // for the same status MUST replace the existing entry so the gallery doesn't
        // freeze on a stale low estimate.
        val target = freshNote()
        val firstSrc = sourceNote("e5".repeat(32))
        val secondSrc = sourceNote("f6".repeat(32))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 5000L, verifiedSats = 1000L, status = OnchainZapStatus.PENDING)
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 5000L, verifiedSats = 2000L, status = OnchainZapStatus.PENDING)

        val entry = target.onchainZaps["tx1"]
        assertSame(secondSrc, entry?.source)
        assertEquals(2000L, entry?.verifiedSats)
    }

    @Test
    fun sameStatusLowerVerifiedSatsIsIgnored() {
        val target = freshNote()
        val firstSrc = sourceNote("a7".repeat(32))
        val secondSrc = sourceNote("b8".repeat(32))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 999L, verifiedSats = 999L, status = OnchainZapStatus.CONFIRMED)
        // Strictly-lower verifiedSats: keep the original entry; don't downgrade.
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 999L, verifiedSats = 500L, status = OnchainZapStatus.CONFIRMED)

        val entry = target.onchainZaps["tx1"]
        assertSame(firstSrc, entry?.source)
        assertEquals(999L, entry?.verifiedSats)
        assertEquals(BigDecimal(999L), target.zapsAmount)
    }

    @Test
    fun sameStatusEqualVerifiedSatsDifferentSourceReplaces() {
        // Multi-signer / split-zap-rebroadcast scenario: a second legitimate kind:8333
        // with the same txid and identical verifiedSats arrives from a different signer.
        // The new entry should win so attribution isn't permanently locked to whichever
        // relay delivered first.
        val target = freshNote()
        val firstSrc = sourceNote("c9".repeat(32))
        val secondSrc = sourceNote("d0".repeat(32))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 1000L, verifiedSats = 1000L, status = OnchainZapStatus.CONFIRMED)
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 1000L, verifiedSats = 1000L, status = OnchainZapStatus.CONFIRMED)

        val entry = target.onchainZaps["tx1"]
        assertSame(secondSrc, entry?.source)
        assertEquals(BigDecimal(1000L), target.zapsAmount)
    }

    @Test
    fun structuralDuplicateIsIgnored() {
        // Exact structural duplicate: same source Note reference + same fields. This
        // is the typical relay-echo case — N relays deliver the same event id, so
        // `getOrCreateNote` returns the same Note instance for each. Skip the rewrite
        // to avoid spurious flowSet invalidations.
        val target = freshNote()
        val src = sourceNote("e1".repeat(32))

        target.addOnchainZap(src, "tx1", claimedSats = 100L, verifiedSats = 100L, status = OnchainZapStatus.CONFIRMED)
        val firstEntry = target.onchainZaps["tx1"]
        target.addOnchainZap(src, "tx1", claimedSats = 100L, verifiedSats = 100L, status = OnchainZapStatus.CONFIRMED)
        val secondEntry = target.onchainZaps["tx1"]

        assertSame(firstEntry, secondEntry)
        assertEquals(BigDecimal(100L), target.zapsAmount)
    }

    @Test
    fun removeOnchainZapForMatchingSourceDropsEntryAndAdjustsTotal() {
        // CONFIRMED entries are guarded against removal (see
        // `confirmedEntryIsNotRemovableOnTransientReject`), so use a PENDING entry
        // here to exercise the matching-source removal path.
        val target = freshNote()
        val srcKey = "c9".repeat(32)
        val src = sourceNote(srcKey)

        target.addOnchainZap(src, "tx1", claimedSats = 4200L, verifiedSats = 4200L, status = OnchainZapStatus.PENDING)
        // PENDING entries don't contribute to total per spec.
        assertEquals(BigDecimal(0), target.zapsAmount)
        assertNotNull(target.onchainZaps["tx1"])

        target.removeOnchainZapForSource("tx1", srcKey)

        assertNull(target.onchainZaps["tx1"])
        assertEquals(BigDecimal(0), target.zapsAmount)
    }

    @Test
    fun removeOnchainZapForMismatchedSourceKeepsLegitimateEntry() {
        // Regression test for the txid-collision attack: a spoofed kind:8333 with the
        // same txid but a different sender pubkey must not erase a legitimate entry.
        // `removeOnchainZapForSource` requires the existing entry's source.author
        // to match the rejecting sender.
        val target = freshNote()
        val legitSrcKey = "1a".repeat(32)
        val attackerKey = "2b".repeat(32)
        val legitSrc = sourceNote(legitSrcKey)

        target.addOnchainZap(legitSrc, "tx1", claimedSats = 4200L, verifiedSats = 4200L, status = OnchainZapStatus.PENDING)

        target.removeOnchainZapForSource("tx1", attackerKey)

        assertNotEquals(null, target.onchainZaps["tx1"])
    }

    @Test
    fun confirmedEntryIsNotRemovableOnTransientReject() {
        // Once chain-verified, a CONFIRMED entry must not be erased by a later
        // verifier reject (e.g. transient ZERO_VERIFIED_AMOUNT from a corrupted
        // Esplora response, or a multi-target event where a sibling target hadn't
        // confirmed yet). Only an explicit fresh CONFIRMED replacement should
        // change a confirmed entry.
        val target = freshNote()
        val srcKey = "f1".repeat(32)
        val src = sourceNote(srcKey)

        target.addOnchainZap(src, "tx1", claimedSats = 4200L, verifiedSats = 4200L, status = OnchainZapStatus.CONFIRMED)
        assertEquals(BigDecimal(4200L), target.zapsAmount)

        target.removeOnchainZapForSource("tx1", srcKey)

        assertNotNull(target.onchainZaps["tx1"])
        assertEquals(OnchainZapStatus.CONFIRMED, target.onchainZaps["tx1"]?.status)
        assertEquals(BigDecimal(4200L), target.zapsAmount)
    }

    @Test
    fun removeOnchainZapForUnknownTxidIsNoOp() {
        val target = freshNote()
        val srcKey = "3c".repeat(32)
        val src = sourceNote(srcKey)

        target.addOnchainZap(src, "tx1", claimedSats = 100L, verifiedSats = 100L, status = OnchainZapStatus.PENDING)
        target.removeOnchainZapForSource("tx-never-added", srcKey)

        // The known entry survives; the unknown txid op is a no-op.
        assertEquals(1, target.onchainZaps.size)
        assertNotNull(target.onchainZaps["tx1"])
    }

    @Test
    fun onchainZapResolvedFlagDefaultsFalseAndIsMutable() {
        // The resolved flag is the dedup gate `LocalCache.consume()` uses to skip
        // re-launching the verifier for terminally-resolved events. New notes start
        // unresolved; the verifier flips the flag on Confirmed / hard-Rejected.
        val src = sourceNote("9d".repeat(32))
        assertFalse(src.onchainZapResolved)

        src.onchainZapResolved = true
        assertTrue(src.onchainZapResolved)
    }

    @Test
    fun hasZapsBoostsOrReactionsReturnsTrueWhenOnlyOnchainZapPresent() {
        val target = freshNote()
        val src = sourceNote("4d".repeat(32))

        assertFalse(target.hasZapsBoostsOrReactions())

        target.addOnchainZap(src, "tx1", claimedSats = 6416L, verifiedSats = 6416L, status = OnchainZapStatus.CONFIRMED)

        assertTrue(target.hasZapsBoostsOrReactions())
    }

    @Test
    fun hasZapsBoostsOrReactionsReturnsTrueWhenOnlyUnverifiedOnchainZapPresent() {
        val target = freshNote()
        val src = sourceNote("5e".repeat(32))

        target.addOnchainZap(src, "tx1", claimedSats = 1000L, verifiedSats = 0L, status = OnchainZapStatus.UNVERIFIED)

        assertTrue(target.hasZapsBoostsOrReactions())
    }

    @Test
    fun updateZapTotalSumsConfirmedAcrossMultipleTxids() {
        val target = freshNote()
        target.addOnchainZap(sourceNote("60".repeat(32)), "tx1", claimedSats = 1000L, verifiedSats = 1000L, status = OnchainZapStatus.CONFIRMED)
        target.addOnchainZap(sourceNote("71".repeat(32)), "tx2", claimedSats = 2000L, verifiedSats = 2000L, status = OnchainZapStatus.CONFIRMED)
        target.addOnchainZap(sourceNote("82".repeat(32)), "tx3", claimedSats = 9999L, verifiedSats = 9999L, status = OnchainZapStatus.PENDING)
        target.addOnchainZap(sourceNote("93".repeat(32)), "tx4", claimedSats = 1234L, verifiedSats = 0L, status = OnchainZapStatus.UNVERIFIED)

        assertEquals(BigDecimal(3000L), target.zapsAmount)
        assertEquals(4, target.onchainZaps.size)
        assertTrue(target.onchainZaps.containsKey("tx3"))
        assertTrue(target.onchainZaps.containsKey("tx4"))
    }

    @Test
    fun clearChildLinksResetsOnchainZapResolvedFlag() {
        // `clearChildLinks()` runs on delete-event handling and during cache
        // pressure; the resolved flag must travel with the cleared state so a
        // re-arrival of the same event gets re-verified instead of being silently
        // skipped against stale state.
        val src = sourceNote("ee".repeat(32))
        src.onchainZapResolved = true

        src.clearChildLinks()

        assertFalse(src.onchainZapResolved)
    }

    @Test
    fun upgradeProducesNewEntryInstance() {
        // Sanity check: when an upgrade is accepted, the new immutable map entry is a
        // fresh OnchainZapEntry instance (not a mutation of the existing one). Compose
        // skipping/recomposition correctness depends on this.
        val target = freshNote()
        val firstSrc = sourceNote("11".repeat(32))
        val secondSrc = sourceNote("22".repeat(32))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 1000L, verifiedSats = 1000L, status = OnchainZapStatus.PENDING)
        val before = target.onchainZaps["tx1"]!!
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 1000L, verifiedSats = 1000L, status = OnchainZapStatus.CONFIRMED)
        val after = target.onchainZaps["tx1"]!!

        assertNotSame(before, after)
        assertEquals(OnchainZapStatus.CONFIRMED, after.status)
    }
}
