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
import com.vitorpamplona.amethyst.commons.actions.ConcordModeration
import com.vitorpamplona.amethyst.commons.actions.ConcordSubscriptionPlanner
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord03Channels.ChannelChat
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeys
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Chat Plane batch at the commons layer: Private Channels on their own keys (S2), in-stream
 * deletes (S3), the Chat ingest gate (S9) and the channel-name build cap (I14).
 */
class ConcordChatPlaneConformanceTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val secretId = ByteArray(32) { 0x5C }
    private val secretIdHex = secretId.toHexKey()
    private val channelKey = ByteArray(32) { 0x3C }

    private fun entryFor(
        community: NewConcordCommunity,
        privateChannels: List<PrivateChannelKey> = emptyList(),
        heldRoots: List<HeldRoot> = emptyList(),
    ) = ConcordCommunityListEntry(
        id = community.communityIdHex,
        owner = community.ownerPubKey,
        ownerSalt = community.ownerSalt.toHexKey(),
        root = community.communityRoot.toHexKey(),
        rootEpoch = community.rootEpoch,
        controlPk = community.controlPkHex,
        controlRoot = community.controlRoot.toHexKey(),
        heldRoots = heldRoots,
        privateChannels = privateChannels,
        relays = listOf("wss://r.example"),
        name = "Nostrichs",
    )

    /** Genesis plus a `private:true` channel edition, as the Control Plane delivers them. */
    private suspend fun communityWithPrivateChannel(): Pair<NewConcordCommunity, List<Event>> {
        val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
        val define =
            ConcordModeration.defineChannel(
                owner,
                community.controlPlane,
                community.communityId,
                secretId,
                ChannelEntity(name = "secret", private = true),
                community.genesisEditions,
                createdAt = 2L,
                owner = community.ownerPubKey,
            )
        return community to (community.genesisWraps + define)
    }

    @Test
    fun aPrivateChannelWithoutAHeldKeyIsNeverDerivedOnTheRootPlane() =
        runTest {
            val (community, control) = communityWithPrivateChannel()
            val captured = mutableListOf<Event>()
            val session = ConcordCommunitySession(entryFor(community), owner.pubKey) { _, _, rumor, _ -> captured += rumor }
            control.forEach { session.ingest(it) }
            val state = session.state.value!!
            assertTrue(state.channels[secretIdHex]!!.definition.private)

            val rootPlane = ConcordActions.publicChannel(community.communityRoot, secretId, community.rootEpoch)
            assertFalse(rootPlane.publicKeyHex in session.channelAddresses(), "a private channel must never be subscribed on the root plane")
            assertTrue(session.streamKeys().none { it.publicKeyHex == rootPlane.publicKeyHex })
            assertNull(session.currentChannelPlane(secretIdHex), "no plane to write without the key")
            assertNull(ConcordActions.currentChannelPlane(session.entry, state, secretIdHex))
            assertFalse(ConcordActions.canAccessChannel(session.entry, state, secretIdHex))

            // The planner and the plane registry agree: nothing for the keyless private channel.
            val subs = ConcordSubscriptionPlanner.channelPlaneSubs(session.entry, state)
            assertTrue(subs.none { it.channelId?.channelId == secretIdHex })
            val registry = ConcordPlaneRegistry().apply { registerChannels(session.entry, state) }
            assertFalse(registry.isKnownPlane(rootPlane.publicKeyHex))

            // A post someone made on the root-derived plane never reaches the store.
            val leaked = ConcordActions.buildChannelMessage(owner, rootPlane, secretIdHex, community.rootEpoch, "psst", 3L)
            assertEquals(ConcordIngestOutcome.NOT_MINE, session.ingest(leaked))
            assertTrue(captured.none { it.content == "psst" })

            // The channel object is locked: no composer, no post.
            val channel = ConcordChannel(ConcordChannelId(community.communityIdHex, secretIdHex))
            channel.updateFrom(state, emptySet(), owner.pubKey, keyHeld = false)
            assertFalse(channel.keyHeld)
            assertFalse(channel.canPost())

            // The public #general is untouched.
            assertNotNull(session.currentChannelPlane(community.generalChannelIdHex))
        }

    @Test
    fun aHeldPrivateKeyReadsAndWritesOnItsOwnPlaneAtTheChannelEpoch() =
        runTest {
            val (community, control) = communityWithPrivateChannel()
            val channelEpoch = 3L
            val entry = entryFor(community, listOf(PrivateChannelKey(secretIdHex, channelKey.toHexKey(), channelEpoch, "secret")))
            val captured = mutableListOf<Event>()
            val session = ConcordCommunitySession(entry, owner.pubKey) { _, _, rumor, _ -> captured += rumor }
            control.forEach { session.ingest(it) }

            val privatePlane = ConcordChannelKeys.privateChannel(channelKey, secretId, channelEpoch)
            val plane = session.currentChannelPlane(secretIdHex)!!
            assertEquals(privatePlane.publicKeyHex, plane.key.publicKeyHex)
            assertEquals(channelEpoch, plane.epoch, "a private channel binds to its own epoch, not the root epoch")
            assertTrue(privatePlane.publicKeyHex in session.channelAddresses())
            assertTrue(session.streamKeys().any { it.publicKeyHex == privatePlane.publicKeyHex })
            val rootPlane = ConcordActions.publicChannel(community.communityRoot, secretId, community.rootEpoch)
            assertFalse(rootPlane.publicKeyHex in session.channelAddresses())

            val subs = ConcordSubscriptionPlanner.channelPlaneSubs(entry, session.state.value!!)
            assertEquals(listOf(privatePlane.publicKeyHex), subs.filter { it.channelId?.channelId == secretIdHex }.map { it.pubKeyHex })

            val msg = ConcordActions.buildChannelMessage(owner, privatePlane, secretIdHex, channelEpoch, "members only", 4L)
            assertEquals(ConcordIngestOutcome.NON_STRUCTURAL, session.ingest(msg))
            assertEquals(1, captured.count { it.content == "members only" })

            // Bound to the ROOT epoch on the private plane: a binding mismatch, dropped.
            val wrongEpoch = ConcordActions.buildChannelMessage(owner, privatePlane, secretIdHex, community.rootEpoch, "wrong epoch", 5L)
            session.ingest(wrongEpoch)
            assertTrue(captured.none { it.content == "wrong epoch" })
        }

    @Test
    fun aKeyDeliveredLaterIsAdoptedInPlace() =
        runTest {
            val (community, control) = communityWithPrivateChannel()
            val session = ConcordCommunitySession(entryFor(community), owner.pubKey)
            control.forEach { session.ingest(it) }
            assertNull(session.currentChannelPlane(secretIdHex))

            val withKey = entryFor(community, listOf(PrivateChannelKey(secretIdHex, channelKey.toHexKey(), 1L)))
            assertTrue(session.adoptPrivateChannels(withKey))
            assertEquals(ConcordChannelKeys.privateChannel(channelKey, secretId, 1L).publicKeyHex, session.currentChannelPlane(secretIdHex)?.key?.publicKeyHex)
            assertFalse(session.adoptPrivateChannels(withKey), "adopting the same keys again is a no-op")
            // The buffered Control Plane survived: still folded.
            assertEquals(
                "Nostrichs",
                session.state.value
                    ?.metadata
                    ?.name,
            )
        }

    @Test
    fun aKeyArrivingWithControlMaterialIsStillAdoptedThroughTheRegistry() =
        runTest {
            val (community, control) = communityWithPrivateChannel()
            // A plain member: holds the control_pk but not the control_root.
            val member =
                ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = community.communityRoot.toHexKey(),
                    rootEpoch = community.rootEpoch,
                    controlPk = community.controlPkHex,
                    relays = listOf("wss://r.example"),
                )
            val registry = ConcordSessionRegistry()
            registry.sync(listOf(member), owner.pubKey)
            val session = registry.sessionFor(community.communityIdHex)!!
            control.forEach { session.ingest(it) }
            assertNull(session.currentChannelPlane(secretIdHex))

            // One list update delivers both the staff write key and the private channel key: the
            // Control adoption swaps the entry first, and the channel planes must still follow.
            registry.sync(listOf(entryFor(community, listOf(PrivateChannelKey(secretIdHex, channelKey.toHexKey(), 2L)))), owner.pubKey)
            assertEquals(session, registry.sessionFor(community.communityIdHex), "adopted in place, not rebuilt")
            assertEquals(ConcordChannelKeys.privateChannel(channelKey, secretId, 2L).publicKeyHex, session.currentChannelPlane(secretIdHex)?.key?.publicKeyHex)
        }

    @Test
    fun aDeleteRidesTheChannelPlaneAndLandsAsAChannelBoundKind5() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val captured = mutableListOf<Event>()
            val session = ConcordCommunitySession(entryFor(community), owner.pubKey) { _, _, rumor, _ -> captured += rumor }
            community.genesisWraps.forEach { session.ingest(it) }
            val plane = session.currentChannelPlane(community.generalChannelIdHex)!!

            session.ingest(ConcordActions.buildChannelMessage(owner, plane.key, plane.channelIdHex, plane.epoch, "oops", 2L))
            val message = captured.single { it.content == "oops" }

            val delete = ConcordActions.buildChannelDelete(owner, plane.key, plane.channelIdHex, plane.epoch, listOf(message), 3L)
            // The wrap is authored by the channel plane, never the author: nothing outside the
            // community learns the rumor id.
            assertEquals(plane.key.publicKeyHex, delete.pubKey)
            assertEquals(ConcordStreamEnvelope.KIND_WRAP, delete.kind)
            assertEquals(ConcordIngestOutcome.NON_STRUCTURAL, session.ingest(delete))
            val kind5 = captured.single { it.kind == 5 }
            assertEquals(listOf(message.id), kind5.tags.filter { it[0] == "e" }.map { it[1] })
            assertEquals(listOf("9"), kind5.tags.filter { it[0] == "k" }.map { it[1] })
            assertTrue(ChannelChat.isBoundTo(kind5, plane.channelIdHex, plane.epoch))

            // A delete of an older message goes back to the plane that carried it.
            assertEquals(plane, session.channelPlaneFor(plane.channelIdHex, plane.epoch))
        }

    @Test
    fun theHistoricalPlaneOfAnOlderMessageIsFoundForItsDelete() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val priorRoot = KeyPair().pubKey
            val session = ConcordCommunitySession(entryFor(community, heldRoots = listOf(HeldRoot(7L, priorRoot.toHexKey()))), owner.pubKey)
            community.genesisWraps.forEach { session.ingest(it) }
            val prior = session.channelPlaneFor(community.generalChannelIdHex, 7L)
            assertEquals(ConcordActions.publicChannel(priorRoot, community.generalChannelId, 7L).publicKeyHex, prior?.key?.publicKeyHex)
        }

    @Test
    fun chatIngestDropsOtherPlanesKindsAndPlaintextSeals() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val captured = mutableListOf<Event>()
            val session = ConcordCommunitySession(entryFor(community), owner.pubKey) { _, _, rumor, _ -> captured += rumor }
            community.genesisWraps.forEach { session.ingest(it) }
            val plane = session.currentChannelPlane(community.generalChannelIdHex)!!
            val binding = arrayOf(arrayOf("channel", plane.channelIdHex), arrayOf("epoch", plane.epoch.toString()))

            // A channel key-holder forging a Control edition (kind 3308) into the chat plane.
            val forgedControl = RumorAssembler.assembleRumor<Event>(owner.pubKey, 2L, 3308, binding, "{}")
            session.ingest(ConcordStreamEnvelope.wrap(forgedControl, plane.key, owner, encrypted = true))
            // A Guestbook join (3306) likewise.
            val forgedJoin = RumorAssembler.assembleRumor<Event>(owner.pubKey, 3L, 3306, binding, "join")
            session.ingest(ConcordStreamEnvelope.wrap(forgedJoin, plane.key, owner, encrypted = true))
            // A proper chat message in a plaintext (Control-only) seal.
            val plaintextSeal = ChannelChat.message(owner.pubKey, plane.channelIdHex, plane.epoch, "plaintext", 4L)
            session.ingest(ConcordStreamEnvelope.wrap(plaintextSeal, plane.key, owner, encrypted = false))
            // A chat message with a malformed ms.
            val badMs = RumorAssembler.assembleRumor<Event>(owner.pubKey, 5L, 9, binding + arrayOf(arrayOf("ms", "01")), "bad ms")
            session.ingest(ConcordStreamEnvelope.wrap(badMs, plane.key, owner, encrypted = true))

            assertTrue(captured.isEmpty(), "none of these may reach the store: ${captured.map { it.kind }}")

            // And the good path still lands.
            session.ingest(ConcordActions.buildChannelMessage(owner, plane.key, plane.channelIdHex, plane.epoch, "ok", 6L))
            assertEquals(listOf("ok"), captured.map { it.content })
        }

    @Test
    fun channelMessagesSortByTheMillisecondBasis() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val plane = ConcordActions.currentChannelPlane(entryFor(community), community.generalChannelIdHex, isPrivate = false)!!

            suspend fun wrap(
                text: String,
                secs: Long,
                ms: Int,
            ) = ConcordStreamEnvelope.wrap(ChannelChat.message(owner.pubKey, plane.channelIdHex, plane.epoch, text, secs, ms = ms), plane.key, owner, encrypted = true)
            val msgs = ConcordActions.channelMessages(listOf(wrap("third", 11, 0), wrap("second", 10, 950), wrap("first", 10, 100)), plane.key, plane.channelIdHex, plane.epoch)
            assertEquals(listOf("first", "second", "third"), msgs.map { it.content })
        }

    @Test
    fun aChannelNameOverTheCapIsNeverMinted() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            assertFailsWith<IllegalArgumentException> {
                ConcordModeration.defineChannel(owner, community.controlPlane, community.communityId, secretId, ChannelEntity(name = "x".repeat(65)), community.genesisEditions, 2L, owner = community.ownerPubKey)
            }
            assertFailsWith<IllegalArgumentException> {
                ConcordModeration.defineChannel(owner, community.controlPlane, community.communityId, secretId, ChannelEntity(name = ""), community.genesisEditions, 2L, owner = community.ownerPubKey)
            }
        }
}
