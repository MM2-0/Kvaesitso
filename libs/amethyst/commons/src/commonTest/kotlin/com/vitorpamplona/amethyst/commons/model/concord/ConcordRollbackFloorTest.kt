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
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord04Roles.MetadataEntity
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordRefounding
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Session-level anti-rollback (CORD-06 §3).
 *
 * `ConcordRefounding.compactControlPlane` re-wraps ONE edition per entity when a community
 * rotates its root, and the ROTATOR picks which one. A rotator that simply omits the newest
 * edition of a chain walks the entity backwards — a revoked role restored, a banlist cleared,
 * metadata reverted — with every signature genuine. The defense is memory: the account already
 * persists the rotated-out roots (`heldRoots`, in the NIP-44 self-encrypted kind-13302 list), so
 * the session re-derives each prior epoch's Control Plane, folds it, and requires the new epoch's
 * chain to connect to the heads it already knew.
 */
class ConcordRollbackFloorTest {
    private val owner = NostrSignerInternal(KeyPair())

    @Test
    fun sessionRefusesAMetadataRollbackAcrossARefounding() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))

            // Epoch 0: genesis metadata (v0 "Nostrichs") plus an owner rename (v1 "Nostrichs HQ").
            val genesisEditions = ConcordActions.controlEditions(community.genesisWraps, community.controlPlane)
            val rename =
                ConcordModeration.editMetadata(
                    actor = owner,
                    controlPlane = community.controlPlane,
                    communityId = community.communityId,
                    metadata = MetadataEntity(name = "Nostrichs HQ"),
                    current = genesisEditions,
                    createdAt = 2L,
                    owner = community.ownerPubKey,
                )
            val epoch0Wraps = community.genesisWraps + rename

            // The rotator refounds, but compacts from the genesis subset ONLY — the rename (v1) is
            // silently dropped. Every wrap it publishes is a genuine, owner-signed edition.
            val newRoot = ByteArray(32) { 0x33 }
            val newEpoch = community.rootEpoch + 1
            // The rotator mints a fresh control_root beside the new root (CORD-02 §2), so the
            // new epoch's plane is split and addressed by the derived signer, not the root.
            val newControlRoot = ByteArray(32) { 0x44 }
            val newControl = ControlPlaneKeys.forStaff(newRoot, community.communityId, newEpoch, newControlRoot)
            val rolledBack = ConcordRefounding.compactControlPlane(community.genesisWraps, community.controlPlane, newControl, community.communityId, community.ownerPubKey)

            val entry =
                ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = newRoot.toHexKey(),
                    rootEpoch = newEpoch,
                    controlPk = newControl.address,
                    controlRoot = newControlRoot.toHexKey(),
                    // The prior epoch is banked with the address it was folded at: a split epoch's
                    // Control Plane can never be re-derived, only remembered (CORD-02 §2).
                    heldRoots = listOf(HeldRoot(community.rootEpoch, community.communityRoot.toHexKey(), community.controlPkHex, community.controlRoot.toHexKey())),
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )
            val session = ConcordCommunitySession(entry, owner.pubKey)

            // The prior epoch's Control Plane is subscribed and AUTHed for — that is where the floor
            // comes from, and without it the client has no memory to check the rotator against.
            assertTrue(
                session.historicalControlPlaneAddresses().contains(community.controlPlane.address),
                "prior-epoch control plane not subscribed",
            )
            assertTrue(
                session.streamKeys().any { it.publicKeyHex == community.controlPlane.address },
                "prior-epoch control plane not AUTHed",
            )

            // Feed the rolled-back new epoch first, then the prior epoch drains in.
            rolledBack.forEach { session.ingest(it) }
            epoch0Wraps.forEach { session.ingest(it) }

            assertEquals(
                "Nostrichs HQ",
                session.state.value
                    ?.metadata
                    ?.name,
                "the rollback to v0 must be refused",
            )
        }

    @Test
    fun sessionAdoptsAnHonestCompaction() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val genesisEditions = ConcordActions.controlEditions(community.genesisWraps, community.controlPlane)
            val rename =
                ConcordModeration.editMetadata(
                    actor = owner,
                    controlPlane = community.controlPlane,
                    communityId = community.communityId,
                    metadata = MetadataEntity(name = "Nostrichs HQ"),
                    current = genesisEditions,
                    createdAt = 2L,
                    owner = community.ownerPubKey,
                )
            val epoch0Wraps = community.genesisWraps + rename

            val newRoot = ByteArray(32) { 0x33 }
            val newEpoch = community.rootEpoch + 1
            // The rotator mints a fresh control_root beside the new root (CORD-02 §2), so the
            // new epoch's plane is split and addressed by the derived signer, not the root.
            val newControlRoot = ByteArray(32) { 0x44 }
            val newControl = ControlPlaneKeys.forStaff(newRoot, community.communityId, newEpoch, newControlRoot)
            // Honest: compacted from the FULL prior plane, so each entity's head (metadata v1) survives.
            val honest = ConcordRefounding.compactControlPlane(epoch0Wraps, community.controlPlane, newControl, community.communityId, community.ownerPubKey)

            val entry =
                ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = newRoot.toHexKey(),
                    rootEpoch = newEpoch,
                    controlPk = newControl.address,
                    controlRoot = newControlRoot.toHexKey(),
                    // The prior epoch is banked with the address it was folded at: a split epoch's
                    // Control Plane can never be re-derived, only remembered (CORD-02 §2).
                    heldRoots = listOf(HeldRoot(community.rootEpoch, community.communityRoot.toHexKey(), community.controlPkHex, community.controlRoot.toHexKey())),
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )
            val session = ConcordCommunitySession(entry, owner.pubKey)
            epoch0Wraps.forEach { session.ingest(it) }
            honest.forEach { session.ingest(it) }

            val state = session.state.value
            assertEquals("Nostrichs HQ", state?.metadata?.name)
            assertTrue(state!!.channels.containsKey(community.generalChannelIdHex), "#general must survive an honest compaction")
        }

    /**
     * A fresh joiner holds no prior root, so it holds no floor: the dangling compacted head IS its
     * baseline (CORD-04 §1). The floor must never regress this — that regression is what hid a
     * refounded community's name, icon and channels.
     */
    @Test
    fun freshJoinerWithNoHeldRootsStillFoldsACompactedPlane() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val newRoot = ByteArray(32) { 0x33 }
            val newEpoch = community.rootEpoch + 1
            // The rotator mints a fresh control_root beside the new root (CORD-02 §2), so the
            // new epoch's plane is split and addressed by the derived signer, not the root.
            val newControlRoot = ByteArray(32) { 0x44 }
            val newControl = ControlPlaneKeys.forStaff(newRoot, community.communityId, newEpoch, newControlRoot)
            val compacted = ConcordRefounding.compactControlPlane(community.genesisWraps, community.controlPlane, newControl, community.communityId, community.ownerPubKey)

            val entry =
                ConcordCommunityListEntry(
                    id = community.communityIdHex,
                    owner = community.ownerPubKey,
                    ownerSalt = community.ownerSalt.toHexKey(),
                    root = newRoot.toHexKey(),
                    rootEpoch = newEpoch,
                    controlPk = newControl.address,
                    relays = listOf("wss://r.example"),
                    name = "Nostrichs",
                )
            val session = ConcordCommunitySession(entry, owner.pubKey)
            assertTrue(session.historicalControlPlaneAddresses().isEmpty())
            compacted.forEach { session.ingest(it) }

            assertEquals(
                "Nostrichs",
                session.state.value
                    ?.metadata
                    ?.name,
            )
            assertTrue(
                session.state.value!!
                    .channels
                    .containsKey(community.generalChannelIdHex),
            )
        }

    /**
     * The floor is only as trustworthy as the editions it is built from. Any ex-member still holds
     * a rotated-out root and could mint a high-version edition on that old Control Plane; if the
     * floor were taken from an ungated fold, that would freeze the entity for every honest client.
     * [ConcordCommunityState.authorizedHeads] gates the same way the live fold does, so an
     * unprivileged author raises no floor.
     */
    @Test
    fun anUnprivilegedEditionOnAnOldPlaneRaisesNoFloor() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val rogue = NostrSignerInternal(KeyPair())
            val genesisEditions = ConcordActions.controlEditions(community.genesisWraps, community.controlPlane)

            // The rogue holds the (rotated-out) root, so it can publish a well-formed v1 metadata
            // edition — it just has no MANAGE_METADATA and no owner-rooted grant.
            val rogueEdit =
                ConcordModeration.editMetadata(
                    actor = rogue,
                    controlPlane = community.controlPlane,
                    communityId = community.communityId,
                    metadata = MetadataEntity(name = "Hijacked"),
                    current = genesisEditions,
                    createdAt = 2L,
                    owner = community.ownerPubKey,
                )

            val floors =
                ConcordCommunityState.authorizedHeads(
                    ConcordActions.controlEditions(community.genesisWraps + rogueEdit, community.controlPlane),
                    community.communityId,
                    community.ownerPubKey,
                )

            val metadataFloor = floors[community.communityIdHex]
            assertEquals(1L, metadataFloor?.version, "an unauthorized edition must not raise the floor")
        }
}
