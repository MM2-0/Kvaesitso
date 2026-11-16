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

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcResponseEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Guards the cache-pruning invariant: when a Note is removed from `LocalCache`,
 * every other Note that referenced it must drop that strong reference. Onchain
 * zaps (NIP-BC) and nutzaps (NIP-61) were added to [Note] after the original
 * removal/migration routines were written, so these tests pin that
 * [Note.removeNote], [Note.clearChildLinks], and [Note.moveAllReferencesTo]
 * all account for them — otherwise a pruned source Note leaks through the
 * target's `onchainZaps` / `nutzaps` map and a duplicate Note with the same id
 * gets minted on the next relay echo.
 */
class NotePruningReferenceTest {
    private fun note(idHex: String) = Note(idHex)

    private fun userFor(pubKey: HexKey) = User(pubKey) { addr -> Note(addr.toValue()) }

    // A source-event Note with a wired author, matching the production shape where
    // `source.author` is the zap sender.
    private fun sourceNote(pubKey: HexKey): Note = note(pubKey).apply { author = userFor(pubKey) }

    private fun eventWith(idHex: HexKey): Event =
        Event(
            id = idHex,
            pubKey = "ab".repeat(32),
            createdAt = 1L,
            kind = 9321,
            tags = emptyArray(),
            content = "",
            sig = "sig",
        )

    private fun typed(
        idHex: HexKey,
        kind: Int,
    ): Note = note(idHex).apply { event = EventFactory.create<Event>(idHex, "ab".repeat(32), 1L, kind, emptyArray(), "", "sig") }

    // ── removeNote and the zap maps (its no-op path skips the lock and the scan) ──

    @Test
    fun removeNoteDetachesAZapReceipt() {
        val target = note("d".repeat(64))
        val request = typed("01".repeat(32), ZapRequestEvent.KIND)
        val receipt = typed("02".repeat(32), ZapReceiptEvent.KIND)
        target.addZap(request, receipt)

        target.removeNote(receipt)

        assertTrue(target.zaps.isEmpty(), "a pruned receipt must leave the zap map")
    }

    @Test
    fun removeNoteDetachesAZapRequest() {
        val target = note("e".repeat(64))
        val request = typed("03".repeat(32), ZapRequestEvent.KIND)
        val receipt = typed("04".repeat(32), ZapReceiptEvent.KIND)
        target.addZap(request, receipt)

        target.removeNote(request)

        assertTrue(target.zaps.isEmpty(), "a pruned request must leave the zap map")
    }

    @Test
    fun removeNoteOfAReplyLeavesTheZapsAlone() {
        val target = note("f".repeat(64))
        val request = typed("05".repeat(32), ZapRequestEvent.KIND)
        val receipt = typed("06".repeat(32), ZapReceiptEvent.KIND)
        target.addZap(request, receipt)

        target.removeNote(typed("07".repeat(32), 1))

        assertEquals(mapOf(request to receipt), target.zaps)
    }

    @Test
    fun removeNoteDetachesAReceiptWhoseEventIsNotLoaded() {
        val target = note("1".repeat(64))
        val request = typed("08".repeat(32), ZapRequestEvent.KIND)
        val receipt = note("09".repeat(32))
        target.addZap(request, receipt)

        target.removeNote(receipt)

        assertTrue(target.zaps.isEmpty(), "with no event to tell its kind, the full check still finds it")
    }

    @Test
    fun removeNoteDetachesAnNwcResponse() {
        val target = note("2".repeat(64))
        val request = typed("0a".repeat(32), NwcRequestEvent.KIND)
        val response = typed("0b".repeat(32), NwcResponseEvent.KIND)
        target.addZapPayment(request, response)

        target.removeNote(response)

        assertTrue(target.zapPayments.isEmpty(), "a pruned NWC response must leave the payment map")
    }

    // ── Fix 1: removeNote drops onchain-zap and nutzap sources ──────────────

    @Test
    fun removeNoteDetachesOnchainZapSource() {
        val target = note("a".repeat(64))
        val src = sourceNote("11".repeat(32))

        target.addOnchainZap(src, "tx1", claimedSats = 1000L, verifiedSats = 1000L, status = OnchainZapStatus.CONFIRMED)
        assertTrue(target.onchainZaps.containsKey("tx1"))

        target.removeNote(src)

        assertTrue(target.onchainZaps.isEmpty(), "onchain zap source must be detached on removeNote")
    }

    @Test
    fun removeNoteDetachesNutzapSource() {
        val target = note("b".repeat(64))
        val src = sourceNote("22".repeat(32)).apply { event = eventWith("cc".repeat(32)) }

        target.addNutzap(src, claimedSats = 500L)
        assertTrue(target.nutzaps.isNotEmpty())

        target.removeNote(src)

        assertTrue(target.nutzaps.isEmpty(), "nutzap source must be detached on removeNote")
    }

    @Test
    fun removeOnchainZapBySourceIgnoresUnrelatedSource() {
        val target = note("d".repeat(64))
        val src = sourceNote("33".repeat(32))
        val other = sourceNote("44".repeat(32))

        target.addOnchainZap(src, "tx1", claimedSats = 1000L, verifiedSats = 1000L, status = OnchainZapStatus.CONFIRMED)

        target.removeNote(other)

        assertTrue(target.onchainZaps.containsKey("tx1"), "removing an unrelated note must not drop the entry")
    }

    // ── Fix 2: clearChildLinks returns onchain-zap sources ──────────────

    @Test
    fun clearChildLinksReturnsAndClearsOnchainZapSources() {
        val target = note("e".repeat(64))
        val src = sourceNote("55".repeat(32))

        target.addOnchainZap(src, "tx1", claimedSats = 1000L, verifiedSats = 1000L, status = OnchainZapStatus.CONFIRMED)

        val removed = target.clearChildLinks()

        assertTrue(src in removed, "onchain zap source must be returned for removal from the cache map")
        assertTrue(target.onchainZaps.isEmpty())
    }

    // ── Fix 3: moveAllReferencesTo migrates onchain zaps, zap payments, labels ──

    @Test
    fun moveAllReferencesToMigratesOnchainZaps() {
        val old = note("f0".repeat(32))
        val newer = AddressableNote(Address(30023, "ab".repeat(32), "slug"))
        val src = sourceNote("66".repeat(32)).apply { replyTo = listOf(old) }

        old.addOnchainZap(src, "tx1", claimedSats = 1000L, verifiedSats = 1000L, status = OnchainZapStatus.CONFIRMED)

        old.moveAllReferencesTo(newer)

        assertTrue(old.onchainZaps.isEmpty(), "old version must release its onchain zaps")
        assertEquals(1, newer.onchainZaps.size, "onchain zap must move to the newer version")
        assertSame(src, newer.onchainZaps["tx1"]?.source)
        assertSame(newer, src.replyTo?.single(), "source replyTo must repoint to the newer version")
    }

    @Test
    fun moveAllReferencesToMigratesZapPayments() {
        val old = note("f1".repeat(32))
        val newer = AddressableNote(Address(30023, "ab".repeat(32), "slug2"))
        val request = note("77".repeat(32)).apply { replyTo = listOf(old) }
        val response = note("88".repeat(32))

        old.addZapPayment(request, response)

        old.moveAllReferencesTo(newer)

        assertTrue(old.zapPayments.isEmpty(), "old version must release its zap payments")
        assertTrue(newer.zapPayments.containsKey(request), "zap payment must move to the newer version")
        assertSame(newer, request.replyTo?.single())
    }

    @Test
    fun moveAllReferencesToMigratesLabels() {
        val old = note("f2".repeat(32))
        val newer = AddressableNote(Address(30023, "ab".repeat(32), "slug3"))
        val labelNote = note("99".repeat(32)).apply { replyTo = listOf(old) }

        old.addLabel("nostr", labelNote)

        old.moveAllReferencesTo(newer)

        assertTrue(old.labels.isEmpty(), "old version must release its labels")
        assertTrue(newer.labels["nostr"]?.contains(labelNote) == true, "label must move to the newer version")
        assertSame(newer, labelNote.replyTo?.single())
    }

    // ── Fix 4: deleteNote severs child back-references (no partial deletion) ──

    @Test
    fun detachFromChildrenSeversReplyToAndClearsCollections() {
        val parent = note("a1".repeat(32))
        val reply = note("b1".repeat(32)).apply { replyTo = listOf(parent) }
        parent.addReply(reply)

        val detached = parent.detachFromChildren()

        assertTrue(reply in detached, "the child must be returned as detached")
        assertTrue(parent.replies.isEmpty(), "parent must release its forward child references")
        assertTrue(
            reply.replyTo?.contains(parent) != true,
            "child must no longer point back at the removed parent",
        )
    }

    @Test
    fun detachFromChildrenKeepsOtherParents() {
        val deleted = note("a2".repeat(32))
        val survivor = note("c2".repeat(32))
        val reply = note("b2".repeat(32)).apply { replyTo = listOf(deleted, survivor) }
        deleted.addReply(reply)
        survivor.addReply(reply)

        deleted.detachFromChildren()

        assertEquals(listOf(survivor), reply.replyTo, "only the removed parent must be dropped from replyTo")
    }

    @Test
    fun detachFromChildrenSeversReactionAndZapSources() {
        val parent = note("a3".repeat(32))
        val reaction = sourceNote("31".repeat(32)).apply { replyTo = listOf(parent) }
        val zapSource = sourceNote("32".repeat(32)).apply { replyTo = listOf(parent) }

        parent.addOnchainZap(zapSource, "tx1", claimedSats = 1L, verifiedSats = 1L, status = OnchainZapStatus.CONFIRMED)
        parent.addBoost(reaction)

        parent.detachFromChildren()

        assertTrue(parent.boosts.isEmpty())
        assertTrue(parent.onchainZaps.isEmpty())
        assertTrue(reaction.replyTo?.contains(parent) != true)
        assertTrue(zapSource.replyTo?.contains(parent) != true)
    }
}
