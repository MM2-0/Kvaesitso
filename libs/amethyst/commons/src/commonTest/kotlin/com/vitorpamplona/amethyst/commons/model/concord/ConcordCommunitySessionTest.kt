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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordDissolution
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConcordCommunitySessionTest {
    private val owner = NostrSignerInternal(KeyPair())

    @Test
    fun ingestsPriorEpochWrapsFromAHeldRoot() =
        runTest {
            // A community whose access root has been rotated once (CORD-06 Refounding): the current
            // entry is epoch 0/rootA, but the account still holds a prior epoch's root. The prior
            // epoch's channel plane is a DIFFERENT stream key; historical backfill must subscribe,
            // AUTH, and decrypt it so pre-Refounding messages surface.
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val priorEpoch = 7L
            val priorRoot = KeyPair().pubKey // any 32-byte value is a valid root ikm
            val entry =
                ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    controlPk = community.controlPkHex,
                    controlRoot = community.controlRoot.toHexKey(),
                    heldRoots = listOf(HeldRoot(priorEpoch, priorRoot.toHexKey())),
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )

            val captured = mutableListOf<com.vitorpamplona.quartz.nip01Core.core.Event>()
            val session = ConcordCommunitySession(entry, owner.pubKey) { _, _, rumor, _ -> captured += rumor }

            // Fold genesis so #general is known — historical planes are derived off the folded channels.
            community.genesisWraps.forEach { session.ingest(it) }

            // The #general channel plane at the PRIOR epoch (derived from the held root) is now a known
            // address AND a stream key to AUTH as.
            val priorGeneral = ConcordActions.publicChannel(priorRoot, community.generalChannelId, priorEpoch)
            assertTrue(session.channelAddresses().contains(priorGeneral.publicKeyHex), "historical plane not subscribed")
            assertTrue(session.streamKeys().any { it.publicKeyHex == priorGeneral.publicKeyHex }, "historical stream key not AUTHed")

            // The history pager asks for every epoch's plane at once: current + prior.
            val currentGeneral = ConcordActions.publicChannel(community.communityRoot, community.generalChannelId, community.rootEpoch)
            val allEpochPlanes = session.channelPlaneAddressesAllEpochs(community.generalChannelIdHex)
            assertTrue(allEpochPlanes.contains(currentGeneral.publicKeyHex), "current plane missing from all-epochs")
            assertTrue(allEpochPlanes.contains(priorGeneral.publicKeyHex), "prior plane missing from all-epochs")

            // A message authored on the prior-epoch plane, bound to the prior epoch, decrypts + emits.
            val oldMsg = ConcordActions.buildChannelMessage(owner, priorGeneral, community.generalChannelIdHex, priorEpoch, "gm from the old epoch", 2L)
            assertEquals(ConcordIngestOutcome.NON_STRUCTURAL, session.ingest(oldMsg))
            assertEquals(1, captured.count { it.content == "gm from the old epoch" })

            // A wrap on the prior plane but bound to the WRONG epoch is rejected (no cross-epoch replay).
            val spoofed = ConcordActions.buildChannelMessage(owner, priorGeneral, community.generalChannelIdHex, community.rootEpoch, "wrong epoch", 3L)
            session.ingest(spoofed)
            assertEquals(0, captured.count { it.content == "wrong epoch" })
        }

    @Test
    fun ingestsControlThenChannelWrapsIntoFlows() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val entry =
                ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    controlPk = community.controlPkHex,
                    controlRoot = community.controlRoot.toHexKey(),
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )

            val captured = mutableListOf<Triple<String, String, com.vitorpamplona.quartz.nip01Core.core.Event>>()
            val session = ConcordCommunitySession(entry, owner.pubKey) { communityId, channelIdHex, rumor, _ -> captured += Triple(communityId, channelIdHex, rumor) }
            assertEquals(community.controlPlane.address, session.controlPlaneAddress)

            // Feed the genesis control wraps → state folds, channels + membership resolve. A fold is
            // STRUCTURAL (it moves the subscription set), so it's allowed to bump the revision.
            community.genesisWraps.forEach { assertEquals(ConcordIngestOutcome.STRUCTURAL_FOLD, session.ingest(it)) }
            val state = session.state.value
            assertEquals("Nostrichs", state?.metadata?.name)
            assertTrue(state!!.channels.containsKey(community.generalChannelIdHex))
            assertEquals(ConcordMembership.OWNER, session.membership())

            // The #general channel plane is now a known address.
            val general = ConcordActions.publicChannel(community.communityRoot, community.generalChannelId, community.rootEpoch)
            assertTrue(session.channelAddresses().contains(general.publicKeyHex))

            // A channel message wrap decrypts and is emitted to the sink for #general.
            val msgWrap = ConcordActions.buildChannelMessage(owner, general, community.generalChannelIdHex, community.rootEpoch, "gm all", 2L)
            // A chat message lands in the feed but is NON_STRUCTURAL: it must never bump the revision
            // (per-message re-subscription is what rate-limited the plane REQs and emptied channels).
            assertEquals(ConcordIngestOutcome.NON_STRUCTURAL, session.ingest(msgWrap))
            val general9 = captured.filter { it.second == community.generalChannelIdHex && it.third.content == "gm all" }
            assertEquals(1, general9.size)
            assertEquals(community.communityIdHex, general9[0].first)
            assertEquals(owner.pubKey, general9[0].third.pubKey)
            val message = general9[0].third

            // A reaction to that message decrypts as a kind-7 bound to the channel, e-tagging the target.
            val reactionWrap = ConcordActions.buildChannelReaction(owner, general, community.generalChannelIdHex, community.rootEpoch, message, "🤙", 3L)
            assertEquals(ConcordIngestOutcome.NON_STRUCTURAL, session.ingest(reactionWrap))
            val reaction = captured.map { it.third }.first { it.kind == 7 }
            assertEquals("🤙", reaction.content)
            assertEquals(message.id, reaction.tags.first { it[0] == "e" }[1])

            // A reply decrypts as a kind-1111 NIP-22 thread comment: uppercase `E` at the thread
            // root and lowercase `e` at the immediate parent (both the message here), still bound
            // to the channel so it groups into the message's thread — the shape Armada threads.
            val replyWrap = ConcordActions.buildChannelReply(owner, general, community.generalChannelIdHex, community.rootEpoch, message, "gm back", 4L)
            assertEquals(ConcordIngestOutcome.NON_STRUCTURAL, session.ingest(replyWrap))
            val reply = captured.map { it.third }.first { it.content == "gm back" }
            assertEquals(1111, reply.kind)
            assertEquals(message.id, reply.tags.first { it[0] == "E" }[1])
            assertEquals(message.id, reply.tags.first { it[0] == "e" }[1])
            assertTrue(ChannelChat.isBoundTo(reply, community.generalChannelIdHex, community.rootEpoch))

            // Each incoming wrap is projected to the sink exactly ONCE (message + reaction + reply = 3),
            // never by re-decrypting the whole channel buffer per message — the O(1) ingest path that
            // keeps a full-history member harvest from being O(n²).
            assertEquals(3, captured.size)
            // Both channel authors observed (owner posted all three) — folded into the roster.
            assertEquals(setOf(owner.pubKey.lowercase()), session.observedAuthors.value)

            // A stray wrap from a different community is ignored.
            val outsider = ConcordCommunityFactory.create(owner, "Other", createdAt = 1L, relays = listOf("wss://r.example"))
            assertEquals(ConcordIngestOutcome.NOT_MINE, session.ingest(outsider.genesisWraps.first()))
        }

    private fun entryFor(community: NewConcordCommunity) =
        ConcordCommunityListEntry(
            id = community.communityIdHex,
            owner = community.ownerPubKey,
            ownerSalt = community.ownerSalt.toHexKey(),
            root = community.communityRoot.toHexKey(),
            rootEpoch = community.rootEpoch,
            controlPk = community.controlPkHex,
            controlRoot = community.controlRoot.toHexKey(),
            relays = listOf("wss://r.example"),
            name = "Doomed",
        )

    @Test
    fun anOwnerTombstoneAtTheDissolvedAddressSealsTheCommunity() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Doomed", createdAt = 1L, relays = listOf("wss://r.example"))
            val session = ConcordCommunitySession(entryFor(community), owner.pubKey)
            community.genesisWraps.forEach { session.ingest(it) }
            assertFalse(session.state.value!!.dissolved)
            assertTrue(session.auxStreamKeys().any { it.publicKeyHex == session.dissolvedAddress }, "the grave must be AUTHed for")

            // A stranger can sign at the (public) address, but only the owner's tombstone counts.
            val stranger = NostrSignerInternal(KeyPair())
            session.ingest(ConcordDissolution.build(stranger, community.communityIdHex))
            assertFalse(session.state.value!!.dissolved)

            session.ingest(ConcordDissolution.build(owner, community.communityIdHex))
            assertTrue(session.state.value!!.dissolved)

            // One-way: a later control fold never clears it.
            community.genesisWraps.forEach { session.ingest(it) }
            assertTrue(session.state.value!!.dissolved)
        }

    @Test
    fun noWriteIsBuiltFromAFoldThatHasNotDrained() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val session = ConcordCommunitySession(entryFor(community), owner.pubKey)
            assertEquals(null, session.foldForWrite(), "nothing folded")

            // The live subscription delivered part of the plane: readable, but not a base for a write.
            session.ingest(community.genesisWraps.first())
            assertTrue(session.state.value != null)
            assertFalse(session.controlDrained.value)
            assertEquals(null, session.foldForWrite())

            // The sweep paged the whole plane in and flagged it: writes may chain onto this fold.
            community.genesisWraps.forEach { session.ingest(it) }
            session.markControlDrained()
            assertEquals(session.state.value, session.foldForWrite())
            assertTrue(session.controlFloors().isEmpty(), "no prior epoch held, no floor")
        }
}
