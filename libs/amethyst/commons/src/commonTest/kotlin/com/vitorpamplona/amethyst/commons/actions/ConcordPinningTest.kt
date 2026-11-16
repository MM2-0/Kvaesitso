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

import com.vitorpamplona.amethyst.commons.model.concord.ConcordCommunitySession
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.pins.ConcordPins
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CORD-04 §7 Pins end to end at the commons layer: build → publish → fold → verify. */
class ConcordPinningTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val alice = NostrSignerInternal(KeyPair())
    private val stranger = NostrSignerInternal(KeyPair())
    private val secretId = ByteArray(32) { 0x5C }
    private val secretIdHex = secretId.toHexKey()
    private val channelKey = ByteArray(32) { 0x3C }
    private val channelEpoch = 3L

    private fun entryFor(
        community: NewConcordCommunity,
        privateChannels: List<PrivateChannelKey> = emptyList(),
    ) = ConcordCommunityListEntry(
        id = community.communityIdHex,
        owner = community.ownerPubKey,
        ownerSalt = community.ownerSalt.toHexKey(),
        root = community.communityRoot.toHexKey(),
        rootEpoch = community.rootEpoch,
        controlPk = community.controlPkHex,
        controlRoot = community.controlRoot.toHexKey(),
        privateChannels = privateChannels,
        relays = listOf("wss://r.example"),
        name = "Nostrichs",
    )

    /** A session plus every rumor it emitted, which is the evidence (deletes, edits) a reader holds. */
    private class Harness(
        val community: NewConcordCommunity,
        entry: ConcordCommunityListEntry,
        me: HexKey,
        drained: Boolean = true,
    ) {
        val rumors = mutableListOf<Event>()
        val session = ConcordCommunitySession(entry, me) { _, _, rumor, _ -> rumors += rumor }.also { if (drained) it.markControlDrained() }

        fun pins(channelIdHex: HexKey) = ConcordPinEvidence(rumors).let { evidence -> assertNotNull(session.readPins(channelIdHex, evidence::isKilled, evidence::newestEdit)) }

        fun ctx(
            actor: NostrSigner,
            channelIdHex: HexKey,
            authorized: Boolean = true,
        ): ConcordPinContext {
            val state = session.state.value!!
            return ConcordPinContext(
                actor = actor,
                controlPlane = session.controlPlaneKeys(),
                communityId = community.communityId,
                owner = community.ownerPubKey,
                current = session.controlEditions(),
                channelIdHex = channelIdHex,
                channelIsPrivate = state.channels[channelIdHex]!!.definition.private,
                currentPlane = session.currentChannelPlane(channelIdHex),
                pins = pins(channelIdHex),
                authorized = authorized,
            )
        }

        suspend fun post(
            author: NostrSigner,
            channelIdHex: HexKey,
            text: String,
            createdAt: Long,
        ): Event {
            val plane = session.currentChannelPlane(channelIdHex)!!
            val wrap = ConcordActions.buildChannelMessage(author, plane.key, channelIdHex, plane.epoch, text, createdAt)
            session.ingest(wrap)
            return rumors.last()
        }

        suspend fun pin(
            actor: NostrSigner,
            channelIdHex: HexKey,
            rumor: Event,
            createdAt: Long,
        ): ConcordPinWrite {
            val write = ConcordPinning.pin(ctx(actor, channelIdHex), assertNotNull(session.pinSource(channelIdHex, rumor.id)), createdAt)
            write.wrap?.let { session.ingest(it) }
            return write
        }
    }

    private suspend fun harness(withPrivate: Boolean = false): Harness {
        val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
        val keys = if (withPrivate) listOf(PrivateChannelKey(secretIdHex, channelKey.toHexKey(), channelEpoch, "secret")) else emptyList()
        val h = Harness(community, entryFor(community, keys), owner.pubKey)
        community.genesisWraps.forEach { h.session.ingest(it) }
        if (withPrivate) {
            h.session.ingest(
                ConcordModeration.defineChannel(owner, community.controlPlane, community.communityId, secretId, ChannelEntity(name = "secret", private = true), h.session.controlEditions(), 2L, owner = community.ownerPubKey),
            )
        }
        return h
    }

    @Test
    fun noPinWriteIsBuiltBeforeTheControlPlaneHasDrained() =
        runTest {
            val h = harness()
            val general = h.community.generalChannelIdHex
            val message = h.post(alice, general, "ship it", 10L)
            assertEquals(ConcordPinOutcome.PUBLISHED, h.pin(owner, general, message, 11L).outcome)

            // A second device that has folded only part of the plane: here the genesis without the pin.
            val partial = Harness(h.community, entryFor(h.community), owner.pubKey, drained = false)
            h.community.genesisWraps.forEach { partial.session.ingest(it) }
            val read = partial.pins(general)
            assertFalse(read.complete, "no head yet reads as not-yet-served, not as an empty list")
            assertNull(read.head)

            // A replace-entire write from that read would erase the pin it never saw (§7).
            val refused = ConcordPinning.unpin(partial.ctx(owner, general), message.id, 12L)
            assertEquals(ConcordPinOutcome.NOT_FOLDED, refused.outcome)
            assertNull(refused.wrap)

            // Once the plane is drained (the pin landed), writes proceed from the full list.
            h.session.controlPlaneWraps().forEach { partial.session.ingest(it) }
            partial.session.markControlDrained()
            assertTrue(partial.pins(general).complete)
            assertEquals(ConcordPinOutcome.PUBLISHED, ConcordPinning.unpin(partial.ctx(owner, general), message.id, 12L).outcome)
        }

    @Test
    fun aPinThatLosesAConcurrentTieReappliesOnTopOfTheWinner() =
        runTest {
            val h = harness()
            val general = h.community.generalChannelIdHex
            val one = h.post(alice, general, "one", 10L)
            val two = h.post(alice, general, "two", 11L)

            // Two curators read the same (empty) head and each write v1 at once.
            val ctx = h.ctx(owner, general)
            val a = ConcordPinning.pin(ctx, h.session.pinSource(general, one.id)!!, 12L)
            val b = ConcordPinning.pin(ctx, h.session.pinSource(general, two.id)!!, 12L)
            h.session.ingest(a.wrap!!)
            h.session.ingest(b.wrap!!)
            val folded = h.pins(general)
            assertTrue(folded.isPinned(one.id) xor folded.isPinned(two.id), "one edition wins the tie, the other's pin is gone")
            val loser = if (folded.isPinned(one.id)) two else one

            // The re-heal: the loser runs its write again on the refolded head, chaining onto the winner.
            val heal = ConcordPinning.pin(h.ctx(owner, general), h.session.pinSource(general, loser.id)!!, 13L)
            assertEquals(ConcordPinOutcome.PUBLISHED, heal.outcome)
            h.session.ingest(heal.wrap!!)
            val healed = h.pins(general)
            assertTrue(healed.isPinned(one.id) && healed.isPinned(two.id))
            assertEquals(2L, healed.head!!.version)
        }

    @Test
    fun aPinRoundTripsThroughTheControlPlaneAndUnpinRemovesIt() =
        runTest {
            val h = harness()
            val general = h.community.generalChannelIdHex
            assertEquals(0, h.pins(general).count)
            assertNull(h.pins(general).head)

            val message = h.post(alice, general, "ship it", 10L)
            val write = h.pin(owner, general, message, 11L)
            assertEquals(ConcordPinOutcome.PUBLISHED, write.outcome)

            // The edition is a vsk-11 Pin List at pins_locator(community, channel), chained from genesis.
            val head = assertNotNull(h.session.pinHeads.value[general])
            assertEquals(ControlEntityKind.PIN_LIST, head.entityKind)
            assertEquals(ConcordKeyDerivation.pinsCoordinate(h.community.communityId, general.hexToByteArray()).toHexKey(), head.entityIdHex)
            assertEquals(1L, head.version)
            assertFalse(ConcordPins.isSealedForm(head.content), "a public channel's list is plaintext")

            val pins = h.pins(general)
            assertEquals(1, pins.count)
            assertEquals(message.id, pins.pins.single().rumorId)
            assertEquals(alice.pubKey, pins.pins.single().author)
            assertEquals("ship it", pins.pins.single().content)
            assertTrue(h.session.holdsRumor(message.id), "the wrap hint resolves locally, so the row can jump")

            assertEquals(ConcordPinOutcome.ALREADY_PINNED, ConcordPinning.pin(h.ctx(owner, general), h.session.pinSource(general, message.id)!!, 12L).outcome)

            val unpin = ConcordPinning.unpin(h.ctx(owner, general), message.id, 13L)
            assertEquals(ConcordPinOutcome.PUBLISHED, unpin.outcome)
            h.session.ingest(unpin.wrap!!)
            assertEquals(
                2L,
                h.session.pinHeads.value[general]!!
                    .version,
                "unpinning is the next edition, not a deletion",
            )
            assertEquals(0, h.pins(general).count)
            assertEquals(ConcordPinOutcome.NOT_PINNED, ConcordPinning.unpin(h.ctx(owner, general), message.id, 14L).outcome)
        }

    @Test
    fun aPinnedMessageThatExpiresLeavesThePinnedList() =
        runTest {
            // CORD-08 §3 meets CORD-04 §7: the proof stays valid, but the rumor's own expiration says it is gone.
            val h = harness()
            val general = h.community.generalChannelIdHex
            val plane = h.session.currentChannelPlane(general)!!
            val sent = TimeUtils.now()
            h.session.ingest(ConcordActions.buildChannelMessage(alice, plane.key, general, plane.epoch, "gone soon", sent, timerSecs = 3_600))
            val message = h.rumors.last()
            assertEquals(ConcordPinOutcome.PUBLISHED, h.pin(owner, general, message, sent + 1).outcome)

            val evidence = ConcordPinEvidence(h.rumors)
            assertEquals(1, h.session.readPins(general, evidence::isKilled, evidence::newestEdit, now = sent + 10)!!.count)
            assertEquals(0, h.session.readPins(general, evidence::isKilled, evidence::newestEdit, now = sent + 3_600)!!.count)
        }

    @Test
    fun theWrapHintPointsAtTheCarryingWrap() =
        runTest {
            val h = harness()
            val general = h.community.generalChannelIdHex
            val plane = h.session.currentChannelPlane(general)!!
            val wrap = ConcordActions.buildChannelMessage(alice, plane.key, general, plane.epoch, "hint", 10L)
            h.session.ingest(wrap)
            h.pin(owner, general, h.rumors.last(), 11L)
            assertEquals(
                wrap.id,
                h
                    .pins(general)
                    .pins
                    .single()
                    .pin.wrapHint,
            )
        }

    @Test
    fun aNonPinMessagesAuthorsEditionIsIgnored() =
        runTest {
            val h = harness()
            val general = h.community.generalChannelIdHex
            val message = h.post(alice, general, "legit", 10L)
            h.pin(owner, general, message, 11L)

            // A stranger who somehow holds the write key mints a newer edition emptying the list.
            val rogue = ConcordModeration.setPinList(stranger, h.session.controlPlaneKeys(), h.community.communityId, general.hexToByteArray(), h.session.pinHeads.value[general], ConcordPins.serializePublic(emptyList()), h.session.controlEditions(), 12L, owner = h.community.ownerPubKey)
            h.session.ingest(rogue)
            assertEquals(1, h.pins(general).count, "the fold gates Pin Lists on PIN_MESSAGES")
            assertEquals(
                owner.pubKey,
                h.session.pinHeads.value[general]!!
                    .author,
            )

            // And the verb refuses outright for an unauthorized actor.
            val refused = ConcordPinning.pin(h.ctx(stranger, general, authorized = false), h.session.pinSource(general, message.id)!!, 13L)
            assertEquals(ConcordPinOutcome.NOT_AUTHORIZED, refused.outcome)
            assertNull(refused.wrap)
        }

    @Test
    fun aPrivateChannelsListIsSealedAndUnavailableWithoutTheKey() =
        runTest {
            val h = harness(withPrivate = true)
            val message = h.post(alice, secretIdHex, "for members", 10L)
            assertEquals(ConcordPinOutcome.PUBLISHED, h.pin(owner, secretIdHex, message, 11L).outcome)
            val head = h.session.pinHeads.value[secretIdHex]!!
            assertTrue(ConcordPins.isSealedForm(head.content), "the writer uses the form of the channel's folded type")
            assertFalse(head.content.contains("for members"))
            assertEquals(
                "for members",
                h
                    .pins(secretIdHex)
                    .pins
                    .single()
                    .content,
            )

            // A client of this community without the channel key (here the owner's other device).
            val keylessHarness = Harness(h.community, entryFor(h.community), owner.pubKey)
            h.session.controlPlaneWraps().forEach { keylessHarness.session.ingest(it) }
            val dark = keylessHarness.pins(secretIdHex)
            assertTrue(dark.sealedUnavailable, "unreadable, not empty")
            assertEquals(0, dark.count)
            assertNotNull(dark.head)

            // MUST withhold the write: even an unpin of nothing would drop every sealed entry.
            val withheld = ConcordPinning.unpin(keylessHarness.ctx(owner, secretIdHex), message.id, 12L)
            assertEquals(ConcordPinOutcome.LIST_UNAVAILABLE, withheld.outcome)
            assertNull(withheld.wrap)
            assertEquals(ConcordPinOutcome.LIST_UNAVAILABLE, ConcordPinning.omit(keylessHarness.ctx(owner, secretIdHex), setOf(message.id), 12L).outcome)
        }

    @Test
    fun aPrivateToPublicSwitchNeverReformsTheSealedList() =
        runTest {
            val h = harness(withPrivate = true)
            val secretMessage = h.post(alice, secretIdHex, "private era", 10L)
            h.pin(owner, secretIdHex, secretMessage, 11L)

            // The channel turns public.
            h.session.ingest(
                ConcordModeration.defineChannel(owner, h.community.controlPlane, h.community.communityId, secretId, ChannelEntity(name = "secret", private = false), h.session.controlEditions(), 12L, owner = h.community.ownerPubKey),
            )
            assertFalse(
                h.session.state.value!!
                    .channels[secretIdHex]!!
                    .definition.private,
            )
            // Still readable (the key is held) — a reader accepts either form.
            assertEquals(1, h.pins(secretIdHex).count)

            val publicMessage = h.post(alice, secretIdHex, "public era", 13L)
            assertEquals(ConcordPinOutcome.PUBLISHED, h.pin(owner, secretIdHex, publicMessage, 14L).outcome)
            val head = h.session.pinHeads.value[secretIdHex]!!
            assertFalse(ConcordPins.isSealedForm(head.content))
            assertFalse(head.content.contains(secretMessage.id), "the private-era pin is not republished to everyone")
            assertEquals(listOf(publicMessage.id), h.pins(secretIdHex).pins.map { it.rumorId })
        }

    @Test
    fun capsRefuseBeforePublishing() =
        runTest {
            val h = harness()
            val general = h.community.generalChannelIdHex
            repeat(ConcordPins.MAX_ENTRIES) { i ->
                val m = h.post(alice, general, "pin number $i", 100L + i)
                assertEquals(ConcordPinOutcome.PUBLISHED, h.pin(owner, general, m, 200L + i).outcome, "pin $i")
            }
            assertEquals(ConcordPins.MAX_ENTRIES, h.pins(general).count)
            val overflow = h.post(alice, general, "one too many", 300L)
            val refused = ConcordPinning.pin(h.ctx(owner, general), h.session.pinSource(general, overflow.id)!!, 301L)
            assertEquals(ConcordPinOutcome.TOO_MANY_PINS, refused.outcome)
            assertNull(refused.wrap)
        }

    @Test
    fun aSealedListHitsTheByteCapBeforeTheEntryCap() =
        runTest {
            val h = harness(withPrivate = true)
            var outcome = ConcordPinOutcome.PUBLISHED
            var pinned = 0
            while (outcome == ConcordPinOutcome.PUBLISHED) {
                val m = h.post(alice, secretIdHex, "a typical pinned announcement of about a hundred and thirty five characters, give or take, number $pinned", 100L + pinned)
                outcome = h.pin(owner, secretIdHex, m, 200L + pinned).outcome
                if (outcome == ConcordPinOutcome.PUBLISHED) pinned++
            }
            assertEquals(ConcordPinOutcome.TOO_LARGE, outcome)
            assertTrue(pinned in 10 until ConcordPins.MAX_ENTRIES, "the byte cap governs a sealed list (pinned $pinned)")
            assertEquals(pinned, h.pins(secretIdHex).count, "the refused write published nothing")
        }

    @Test
    fun theAuthorsDeleteHidesThePinAndTheOmissionDropsIt() =
        runTest {
            val h = harness()
            val general = h.community.generalChannelIdHex
            val keep = h.post(alice, general, "keep", 10L)
            val oops = h.post(alice, general, "oops", 11L)
            h.pin(owner, general, keep, 12L)
            h.pin(owner, general, oops, 13L)

            // Someone else's delete of alice's message does nothing.
            val plane = h.session.currentChannelPlane(general)!!
            h.session.ingest(ConcordActions.buildChannelDelete(stranger, plane.key, general, plane.epoch, listOf(oops), 14L))
            assertEquals(2, h.pins(general).count)

            h.session.ingest(ConcordActions.buildChannelDelete(alice, plane.key, general, plane.epoch, listOf(oops), 15L))
            val read = h.pins(general)
            assertEquals(listOf(keep.id), read.pins.map { it.rumorId }, "a held delete hides the entry immediately")
            assertEquals(listOf(oops.id), read.killed.map { it.rumorId })
            assertTrue(read.owesRepublish)

            // The duty write drops it from the head; after that nothing is owed.
            val settled = ConcordPinning.settle(h.ctx(owner, general), { null }, 16L)
            assertEquals(ConcordPinOutcome.PUBLISHED, settled.outcome)
            h.session.ingest(settled.wrap!!)
            assertFalse(
                h.session.pinHeads.value[general]!!
                    .content
                    .contains(oops.id),
            )
            assertFalse(h.pins(general).owesRepublish)
            assertEquals(ConcordPinOutcome.NOTHING_TO_DO, ConcordPinning.settle(h.ctx(owner, general), { null }, 17L).outcome)
        }

    @Test
    fun thePinnersOmissionPublishesAtOnce() =
        runTest {
            val h = harness()
            val general = h.community.generalChannelIdHex
            val mine = h.post(owner, general, "my announcement", 10L)
            h.pin(owner, general, mine, 11L)
            val omitted = ConcordPinning.omit(h.ctx(owner, general), setOf(mine.id), 12L)
            assertEquals(ConcordPinOutcome.PUBLISHED, omitted.outcome)
            h.session.ingest(omitted.wrap!!)
            assertEquals(0, h.pins(general).count)
            assertEquals(ConcordPinOutcome.NOTHING_TO_DO, ConcordPinning.omit(h.ctx(owner, general), setOf(mine.id), 13L).outcome)
        }

    @Test
    fun aNewerHeldEditMarksThePinEditedAndTheRefreshAttachesItsProof() =
        runTest {
            val h = harness()
            val general = h.community.generalChannelIdHex
            val original = h.post(alice, general, "teh plan", 10L)
            h.pin(owner, general, original, 11L)
            assertFalse(
                h
                    .pins(general)
                    .pins
                    .single()
                    .edited,
            )

            val plane = h.session.currentChannelPlane(general)!!
            h.session.ingest(ConcordActions.buildChannelEdit(alice, plane.key, general, plane.epoch, original, "the plan", 12L))
            val edit = h.rumors.last()
            // A forged edit by someone else never counts.
            h.session.ingest(ConcordActions.buildChannelEdit(stranger, plane.key, general, plane.epoch, original, "pwned", 13L))

            val shown = h.pins(general).pins.single()
            assertTrue(shown.edited, "a client holding a newer Edit MUST mark the pin edited")
            assertEquals("the plan", shown.content)
            assertEquals(edit.id, shown.newerEdit?.rumorId)
            assertFalse(shown.pin.edited, "the proof itself still carries the original words")

            val refreshed = ConcordPinning.settle(h.ctx(owner, general), { h.session.pinSource(general, it.newerEdit!!.rumorId) }, 14L)
            assertEquals(ConcordPinOutcome.PUBLISHED, refreshed.outcome)
            h.session.ingest(refreshed.wrap!!)
            val after = h.pins(general).pins.single()
            assertTrue(after.pin.edited, "the proof now carries the Edit for keyless readers")
            assertEquals("the plan", after.pin.content)
            assertNull(after.newerEdit, "nothing newer is owed")
            assertFalse(h.pins(general).owesRepublish)
        }

    @Test
    fun verificationIsCachedByEntryIdentity() =
        runTest {
            val verifier = ConcordPinVerifier()
            val h = harness()
            val general = h.community.generalChannelIdHex
            h.pin(owner, general, h.post(alice, general, "one", 10L), 11L)
            h.pin(owner, general, h.post(alice, general, "two", 12L), 13L)
            val head = h.session.pinHeads.value[general]
            ConcordPinning.read(head, general, { null }, verifier)
            assertEquals(2, verifier.misses)
            ConcordPinning.read(head, general, { null }, verifier)
            assertEquals(2, verifier.misses, "a re-read redoes no signature, MAC or decryption")
        }
}
