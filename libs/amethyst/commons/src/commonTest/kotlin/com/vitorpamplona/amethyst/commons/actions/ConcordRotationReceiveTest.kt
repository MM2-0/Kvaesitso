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
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordRefounding
import com.vitorpamplona.quartz.concord.cord06Rekey.ReceivedRefounding
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The receive side of CORD-06 rotations as commons wires it: the `vac`-cited authority check a
 * receiver runs before adopting (I9), racing rotations converging on the lowest authorized root
 * and the down-only same-epoch heal (I12), and the sibling address a session watches for it.
 */
class ConcordRotationReceiveTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val admin = NostrSignerInternal(KeyPair())
    private val member = NostrSignerInternal(KeyPair())
    private val now = 1_700_000_000L

    private class Fixture(
        val community: NewConcordCommunity,
        val editions: List<ControlEdition>,
    )

    /** A community whose owner granted [admin] a BAN role. */
    private suspend fun withAdmin(): Fixture {
        val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
        val cp = community.controlPlane
        val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()
        val roleId = ByteArray(32) { (it + 1).toByte() }
        val role = RoleEntity(name = "Admin", position = 1, permissions = ConcordPermissions.of(ConcordPermissions.BAN).toWire())
        editions += ConcordActions.controlEditions(listOf(ConcordModeration.defineRole(owner, cp, community.communityId, roleId, role, editions, createdAt = 2L, owner = community.ownerPubKey)), cp)
        editions += ConcordActions.controlEditions(listOf(ConcordModeration.grant(owner, cp, community.communityId, admin.pubKey, listOf(roleId.toHexKey()), editions, createdAt = 3L, owner = community.ownerPubKey)), cp)
        return Fixture(community, editions)
    }

    private fun entryFor(
        community: NewConcordCommunity,
        root: String = community.communityRoot.toHexKey(),
        epoch: Long = community.rootEpoch,
        heldRoots: List<HeldRoot> = emptyList(),
    ) = ConcordCommunityListEntry(
        id = community.communityIdHex,
        owner = community.ownerPubKey,
        ownerSalt = community.ownerSalt.toHexKey(),
        root = root,
        rootEpoch = epoch,
        controlPk = community.controlPkHex,
        heldRoots = heldRoots,
        relays = listOf("wss://r.example"),
        name = "Nostrichs",
    )

    private suspend fun rotate(
        community: NewConcordCommunity,
        rotator: NostrSigner,
        newRoot: ByteArray,
        authority: AuthorityCitation?,
    ): List<Event> {
        val newEpoch = community.rootEpoch + 1
        val controlRoot = ByteArray(32) { 0x6B }
        return ConcordRefounding.buildBaseRekeyWraps(
            rotatorSigner = rotator,
            baseRekeyKey = ConcordActions.nextBaseRekeyPlane(community.communityRoot, community.communityId, community.rootEpoch),
            recipientsXOnly = listOf(member.pubKey),
            staffXOnly = emptySet(),
            newRoot = newRoot,
            newControlPk = ConcordKeyDerivation.controlSignerKey(controlRoot, community.communityId, newEpoch).publicKey,
            newControlRoot = controlRoot,
            newEpoch = newEpoch,
            prevEpoch = community.rootEpoch,
            prevCommit = ConcordKeyDerivation.epochKeyCommitment(community.rootEpoch, community.communityRoot).toHexKey(),
            createdAt = now,
            authority = authority,
        )
    }

    private suspend fun receive(
        f: Fixture,
        wraps: List<Event>,
    ): ReceivedRefounding? {
        val entry = entryFor(f.community)
        val authority = ConcordCommunityState.fold(f.editions, f.community.communityId, f.community.ownerPubKey).authority
        return ConcordActions.openBaseRekey(
            wraps = wraps,
            baseRekey = ConcordActions.nextBaseRekeyPlane(f.community.communityRoot, f.community.communityId, f.community.rootEpoch),
            recipientSigner = member,
            communityId = f.community.communityIdHex,
            priorRoot = f.community.communityRoot,
            rootEpoch = f.community.rootEpoch,
            accept = { ConcordReceive.isHonoredRotation(entry, f.editions, authority, it) },
        )
    }

    // ---- I9 ----------------------------------------------------------------------------------

    @Test
    fun anAdminsRotationIsHonoredOnlyWithItsGrantCited() =
        runTest {
            val f = withAdmin()
            val entry = entryFor(f.community)
            val citation = ConcordReceive.rotationCitation(entry, f.editions, admin.pubKey)
            assertNotNull(citation, "a BAN-holding admin has a Grant to cite")
            assertNull(ConcordReceive.rotationCitation(entry, f.editions, owner.pubKey), "the owner cites nothing")

            val root = ByteArray(32) { 0x22 }
            assertContentEquals(root, receive(f, rotate(f.community, admin, root, citation))?.newRoot)
            assertNull(receive(f, rotate(f.community, admin, root, authority = null)), "an uncited delegated rotation is dropped")
            assertContentEquals(root, receive(f, rotate(f.community, owner, root, authority = null))?.newRoot, "the owner needs no citation")
        }

    @Test
    fun aRotationCitingAGrantWeHaveNotSyncedIsParked() =
        runTest {
            val f = withAdmin()
            val real = ConcordReceive.rotationCitation(entryFor(f.community), f.editions, admin.pubKey)!!
            val ahead = AuthorityCitation(real.grantId, real.grantVersion + 1, real.grantHash)
            assertNull(receive(f, rotate(f.community, admin, ByteArray(32) { 0x22 }, ahead)))
        }

    @Test
    fun aStrangerCannotRotateEvenCitingSomeonesGrant() =
        runTest {
            val f = withAdmin()
            val stranger = NostrSignerInternal(KeyPair())
            val adminsCitation = ConcordReceive.rotationCitation(entryFor(f.community), f.editions, admin.pubKey)
            assertNull(receive(f, rotate(f.community, stranger, ByteArray(32) { 0x22 }, adminsCitation)))
        }

    // ---- I12 ---------------------------------------------------------------------------------

    @Test
    fun racingHonoredRotationsConvergeOnTheLowestRoot() =
        runTest {
            val f = withAdmin()
            val citation = ConcordReceive.rotationCitation(entryFor(f.community), f.editions, admin.pubKey)
            val high = ByteArray(32) { 0x70 }
            val low = ByteArray(32) { 0x05 }
            val wraps = rotate(f.community, owner, high, null) + rotate(f.community, admin, low, citation)
            assertContentEquals(low, receive(f, wraps)?.newRoot)
            assertContentEquals(low, receive(f, wraps.reversed())?.newRoot)

            // An uncited (dishonored) lower root never wins the race.
            val rogue = rotate(f.community, admin, ByteArray(32) { 0x01 }, null)
            assertContentEquals(high, receive(f, rotate(f.community, owner, high, null) + rogue)?.newRoot)
        }

    @Test
    fun theHealMovesOnlyToAStrictlyLowerSiblingAndKeepsTheLoser() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L)
            val prior = HeldRoot(0, "aa".repeat(32), community.controlPkHex)
            val adopted = entryFor(community, root = "70".repeat(32), epoch = 1, heldRoots = listOf(prior))

            fun sibling(root: String) = ReceivedRefounding(root.hexToByteArray(), 1, owner.pubKey, ByteArray(32) { 3 }, null)

            val healed = ConcordReceive.withHealedRoot(adopted, sibling("05".repeat(32)))
            assertNotNull(healed)
            assertEquals("05".repeat(32), healed.root)
            assertEquals(1L, healed.rootEpoch)
            assertEquals(ByteArray(32) { 3 }.toHexKey(), healed.controlPk, "the control pair is the winner's")
            assertTrue(healed.heldRoots.any { it.epoch == 1L && it.key == "70".repeat(32) && it.controlPk == null }, "the losing fork's root is kept for its messages")
            assertEquals(prior.key, ConcordRefounding.canonicalHeldRoots(healed.heldRoots).first { it.epoch == 0L }.key)

            assertNull(ConcordReceive.withHealedRoot(adopted, sibling("90".repeat(32))), "down-only: a higher sibling never re-forks the epoch")
            assertNull(ConcordReceive.withHealedRoot(adopted, sibling("70".repeat(32))))

            // The next adoption keeps both same-epoch roots instead of collapsing them by epoch.
            val next = ConcordReceive.withAdoptedRoot(healed, ByteArray(32) { 9 }, 2)
            assertEquals(
                setOf("05".repeat(32), "70".repeat(32)),
                next.heldRoots
                    .filter { it.epoch == 1L }
                    .map { it.key }
                    .toSet(),
            )
            assertEquals("05".repeat(32), ConcordRefounding.canonicalHeldRoots(next.heldRoots).first { it.epoch == 1L }.key)
        }

    @Test
    fun aSessionWatchesTheRotationIntoItsOwnEpoch() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L)
            val prior = HeldRoot(community.rootEpoch, community.communityRoot.toHexKey(), community.controlPkHex)
            val entry = entryFor(community, root = "70".repeat(32), epoch = community.rootEpoch + 1, heldRoots = listOf(prior))

            val sibling = ConcordActions.siblingBaseRekeyPlane(entry)
            assertNotNull(sibling)
            assertEquals(ConcordActions.nextBaseRekeyPlane(community.communityRoot, community.communityId, community.rootEpoch).publicKeyHex, sibling.publicKeyHex)
            assertNull(ConcordActions.siblingBaseRekeyPlane(entryFor(community)), "a joiner holding no prior root has nothing to watch")

            val session = ConcordCommunitySession(entry, member.pubKey)
            assertEquals(sibling.publicKeyHex, session.siblingBaseRekeyAddress)
            assertTrue(session.ownsPlane(sibling.publicKeyHex))
            assertTrue(session.auxStreamKeys().any { it.publicKeyHex == sibling.publicKeyHex })

            val wraps = rotate(community, owner, ByteArray(32) { 0x05 }, null)
            wraps.forEach { session.ingest(it) }
            assertEquals(wraps.map { it.id }.toSet(), session.pendingSiblingRekeyWraps().map { it.id }.toSet())

            assertTrue(ConcordSubscriptionPlanner.auxiliaryPlaneSubs(listOf(entry)).any { it.pubKeyHex == sibling.publicKeyHex })
        }

    @Test
    fun aLosingForkRootIsNotFoldedAsAControlPlane() {
        val community = "11".repeat(32)
        val entry =
            ConcordCommunityListEntry(
                id = community,
                owner = "22".repeat(32),
                ownerSalt = "33".repeat(32),
                root = "44".repeat(32),
                rootEpoch = 2,
                heldRoots = listOf(HeldRoot(1, "05".repeat(32)), HeldRoot(1, "70".repeat(32))),
                relays = listOf("wss://r.example"),
            )
        // One Control Plane for epoch 1 (the winner's), plus the current one.
        assertEquals(2, ConcordSubscriptionPlanner.controlPlaneSubs(listOf(entry)).size)
        assertFalse(ConcordRefounding.canonicalHeldRoots(entry.heldRoots).any { it.key == "70".repeat(32) })
    }
}
