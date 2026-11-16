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

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConcordActionsTest {
    private val owner = NostrSignerInternal(KeyPair())

    @Test
    fun createFoldSendReadRoundTrip() =
        runTest {
            val community = ConcordActions.createCommunity(owner, "Test Server", createdAt = 1L, relays = listOf("wss://r.example"))

            // Fold genesis -> live state
            val state = ConcordActions.foldCommunity(community.genesisWraps, community.controlPlane, community.communityId, community.ownerPubKey)
            assertEquals("Test Server", state.metadata?.name)
            assertTrue(state.channels.containsKey(community.generalChannelIdHex))

            // Send + read a channel message
            val channel = ConcordActions.publicChannel(community.communityRoot, community.generalChannelId, community.rootEpoch)
            val wrap = ConcordActions.buildChannelMessage(owner, channel, community.generalChannelIdHex, community.rootEpoch, "hello world", createdAt = 2L)
            val msgs = ConcordActions.channelMessages(listOf(wrap), channel, community.generalChannelIdHex, community.rootEpoch)
            assertEquals(1, msgs.size)
            assertEquals("hello world", msgs[0].content)
            assertEquals(owner.pubKey, msgs[0].author)
        }

    @Test
    fun inviteMintParseAndOpen() =
        runTest {
            val community = ConcordActions.createCommunity(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val invite =
                ConcordActions.inviteFor(
                    communityIdHex = community.communityIdHex,
                    ownerPubKey = community.ownerPubKey,
                    ownerSaltHex = community.ownerSalt.toHex(),
                    communityRootHex = community.communityRoot.toHex(),
                    rootEpoch = community.rootEpoch,
                    name = "Nostrichs",
                    relays = listOf("wss://r.example"),
                    // Without this the joiner has no Control Plane address to fold at — the
                    // bundle is the only place it can come from (CORD-05 §1).
                    controlPk = community.controlPkHex,
                )
            val minted = ConcordActions.mintInviteLink("https://vector.chat", invite, createdAt = 1L)

            val parsed = ConcordActions.parseInviteLink(minted.url)
            assertNotNull(parsed)
            val opened = ConcordActions.openBundle(minted.bundleEvent, parsed.fragment.token)
            assertNotNull(opened)
            assertEquals(community.communityIdHex, opened.communityId)

            // The joiner can derive the control plane and read the genesis.
            val controlPlane = ConcordActions.controlPlaneFor(opened)
            val state = ConcordActions.foldCommunity(community.genesisWraps, controlPlane, opened.communityId.hexToByteArray(), opened.owner)
            assertEquals("Nostrichs", state.metadata?.name)
        }

    @Test
    fun guestbookJoinFoldsIntoMembership() =
        runTest {
            val community = ConcordActions.createCommunity(owner, "Test", createdAt = 1L, relays = listOf("wss://r.example"))
            val alice = NostrSignerInternal(KeyPair())
            val bob = NostrSignerInternal(KeyPair())
            val guestbook = ConcordActions.guestbookPlane(community.communityRoot, community.communityId, community.rootEpoch)

            val joins =
                listOf(
                    ConcordActions.buildGuestbookJoin(alice, guestbook, createdAt = 2L),
                    ConcordActions.buildGuestbookJoin(bob, guestbook, createdAt = 3L),
                )
            val members = ConcordActions.guestbookMembers(joins, guestbook)
            assertEquals(setOf(alice.pubKey.lowercase(), bob.pubKey.lowercase()), members)
        }

    @Test
    fun refoundingReKeysRetainedAndSeversRemoved() =
        runTest {
            val community = ConcordActions.createCommunity(owner, "Test", createdAt = 1L, relays = listOf("wss://r.example"))
            val alice = NostrSignerInternal(KeyPair()) // retained
            val carol = NostrSignerInternal(KeyPair()) // removed

            val newRoot = ByteArray(32) { 0x33 }
            // A fresh control_root is minted beside the new root at every Refounding (CORD-02 §2).
            val newControlRoot = ByteArray(32) { 0x44 }
            val build =
                ConcordActions.buildRefounding(
                    rotatorSigner = owner,
                    communityId = community.communityIdHex,
                    priorRoot = community.communityRoot,
                    newRoot = newRoot,
                    newControlRoot = newControlRoot,
                    rootEpoch = community.rootEpoch,
                    priorControlWraps = community.genesisWraps,
                    priorControlKeys = community.controlPlane,
                    recipientsXOnly = listOf(owner.pubKey, alice.pubKey),
                    // Only the owner is staff, so only the owner's blob carries the secret.
                    staffXOnly = setOf(owner.pubKey),
                    createdAt = 5L,
                    ownerPubKey = owner.pubKey,
                )

            val baseRekey = ConcordActions.nextBaseRekeyPlane(community.communityRoot, community.communityId, community.rootEpoch)

            val aliceGot = ConcordActions.openBaseRekey(build.rekeyWraps, baseRekey, alice, community.communityIdHex, community.communityRoot, community.rootEpoch)
            val carolGot = ConcordActions.openBaseRekey(build.rekeyWraps, baseRekey, carol, community.communityIdHex, community.communityRoot, community.rootEpoch)
            assertNotNull(aliceGot)
            assertEquals(community.rootEpoch + 1, aliceGot.newEpoch)
            assertTrue(carolGot == null)

            // Alice is a plain member: her blob carries the new control_pk to read with, never the
            // secret to write with (CORD-06 §1).
            val deliveredControlPk = aliceGot.newControlPk
            assertNotNull(deliveredControlPk)
            assertTrue(aliceGot.newControlRoot == null, "a member's base blob must not carry the write key")

            // The compacted Control Plane folds identically at the new epoch, opened the way a
            // member does: the delivered address plus the derived read key.
            val newControl =
                ConcordActions.controlPlaneKeys(
                    communityRoot = aliceGot.newRoot,
                    communityId = community.communityId,
                    rootEpoch = aliceGot.newEpoch,
                    controlPk = deliveredControlPk.toHexKey(),
                )
            val state = ConcordActions.foldCommunity(build.controlWraps, newControl, community.communityId, community.ownerPubKey)
            assertEquals("Test", state.metadata?.name)
            assertTrue(state.channels.isNotEmpty())
        }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
