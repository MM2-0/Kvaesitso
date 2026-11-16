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

import com.vitorpamplona.amethyst.commons.model.ConcordDirectInviteDraft
import com.vitorpamplona.amethyst.commons.model.concord.ConcordCommunitySession
import com.vitorpamplona.amethyst.commons.model.concord.ConcordIngestOutcome
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeyring
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteVend
import com.vitorpamplona.quartz.concord.cord06Rekey.ChannelRekeyOutcome
import com.vitorpamplona.quartz.concord.cord06Rekey.ConcordChannelRekey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Private Channels end to end, headless (CORD-03 §1-2, CORD-04 §2, CORD-05 §6, CORD-06 §1-2): create
 * with an access Role, vend on grant through a Direct Invite limited to the gained channel, the
 * recipient's click-free adoption, rotate on revoke to the remaining entitled set, and each member's
 * receive (the kept adopts, the cut drops the key and records the cut). Plus privatise/publicise.
 */
class ConcordPrivateChannelsTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val alice = NostrSignerInternal(KeyPair())
    private val bob = NostrSignerInternal(KeyPair())

    private class World(
        val community: NewConcordCommunity,
        val editions: MutableList<ControlEdition>,
    ) {
        fun add(wrap: Event) {
            editions += ConcordActions.controlEditions(listOf(wrap), community.controlPlane)
        }

        fun state(): ConcordCommunityState = ConcordCommunityState.fold(editions, community.communityId, community.ownerPubKey)
    }

    private suspend fun world(): World {
        val c = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))
        return World(c, ConcordActions.controlEditions(c.genesisWraps, c.controlPlane).toMutableList())
    }

    private fun entryOf(
        c: NewConcordCommunity,
        channels: List<PrivateChannelKey> = emptyList(),
    ) = ConcordCommunityListEntry(
        id = c.communityIdHex,
        owner = c.ownerPubKey,
        ownerSalt = c.ownerSalt.toHexKey(),
        root = c.communityRoot.toHexKey(),
        rootEpoch = c.rootEpoch,
        controlPk = c.controlPkHex,
        privateChannels = channels,
        relays = listOf("wss://relay.example"),
        name = "Nostrichs",
        addedAt = 1_000L,
    )

    @Test
    fun aPrivateChannelIsVendedOnGrantAndRotatedOnRevoke() =
        runTest {
            val w = world()
            val c = w.community
            val cid = c.communityId

            // 1. Create: an access Role at the bottom of the roster, the channel flagged private, a key at epoch 0.
            val build = assertNotNull(ConcordPrivateChannels.create(owner, c.controlPlane, cid, "mods", null, w.editions, w.state().authority, c.ownerPubKey, 2L))
            build.wraps.forEach { w.add(it) }
            val ch = build.channelIdHex
            assertEquals(0L, build.key.epoch)
            assertTrue(ch in w.state().privateChannelIds)
            val role = assertNotNull(w.state().roles[build.roleIdHex])
            assertEquals("channel", role.scope?.kind)
            assertEquals(ch, role.scope?.channelId)
            assertEquals("0", role.permissions)
            val ownerEntry = assertNotNull(ConcordChannelKeyring.withChannelKey(entryOf(c), build.key))

            // 2. Grant alice and bob the access Role: both gained the channel.
            val beforeGrant = w.state().authority
            w.add(ConcordModeration.grant(owner, c.controlPlane, cid, alice.pubKey, listOf(build.roleIdHex), w.editions, 3L, owner = c.ownerPubKey))
            w.add(ConcordModeration.grant(owner, c.controlPlane, cid, bob.pubKey, listOf(build.roleIdHex), w.editions, 3L, owner = c.ownerPubKey))
            val granted = ConcordInviteVend.accessChanges(beforeGrant, w.state().authority, w.state().privateChannelIds).single()
            assertEquals(setOf(alice.pubKey, bob.pubKey), granted.gained)

            // 3. Vend: a Direct Invite limited to the gained channel, from staff, adopted by alice without a click.
            val draft = assertIs<ConcordDirectInviteDraft.Ready>(ConcordActions.draftDirectInvite(ownerEntry, w.state(), owner.pubKey, alice.pubKey, onlyChannelIds = setOf(ch)))
            assertEquals(listOf(ch), draft.invite.channels.map { it.id })
            val aliceHeld = entryOf(c)
            assertEquals(
                ConcordInviteVend.CatchUpVerdict.ADOPT,
                ConcordInviteVend.judgeCatchUp(w.state().authority, w.state().privateChannelIds, alice.pubKey, owner.pubKey, draft.invite, aliceHeld),
            )
            val aliceEntry = assertNotNull(ConcordInviteVend.adoptCatchUp(aliceHeld, draft.invite))
            val bobEntry = assertNotNull(ConcordChannelKeyring.withChannelKey(entryOf(c), build.key))
            assertEquals(build.key.key, ConcordChannelKeyring.heldKey(aliceEntry, ch)?.key)

            // 4. Revoke bob: he lost the channel, so the owner rotates it to the remaining entitled set.
            val beforeRevoke = w.state().authority
            w.add(ConcordModeration.grant(owner, c.controlPlane, cid, bob.pubKey, emptyList(), w.editions, 4L, owner = c.ownerPubKey))
            val revoked = ConcordInviteVend.accessChanges(beforeRevoke, w.state().authority, w.state().privateChannelIds).single()
            assertEquals(setOf(bob.pubKey), revoked.lost)
            val keep = ConcordPrivateChannels.keepSet(w.state().authority, ch, owner.pubKey)
            assertEquals(setOf(owner.pubKey, alice.pubKey), keep)
            assertTrue(ConcordPrivateChannels.canRotate(w.state().authority, owner.pubKey, revoked.lost))
            // A plain member can't rotate anyone out.
            assertFalse(ConcordPrivateChannels.canRotate(w.state().authority, alice.pubKey, setOf(bob.pubKey)))

            val newKey = ConcordChannelRekey.mintKey()
            val held = assertNotNull(ConcordChannelKeyring.heldKey(ownerEntry, ch))
            val wraps = ConcordPrivateChannels.buildRotation(owner, c.communityRoot, held, newKey, keep, 5L, authority = null)

            // 5. Receive: alice adopts epoch 1, bob is cut and the cut is recorded.
            val aliceOut = ConcordPrivateChannels.receive(aliceEntry, wraps, w.editions, w.state().authority, alice)
            val adopted = assertIs<ChannelRekeyOutcome.Adopted>(aliceOut[ch])
            assertEquals(1L, adopted.epoch)
            val aliceNext = assertNotNull(ConcordPrivateChannels.applyOutcome(aliceEntry, aliceOut, mapOf(ch to 0L)))
            assertEquals(newKey.toHexKey(), ConcordChannelKeyring.heldKey(aliceNext, ch)?.key)
            assertEquals(1L, ConcordChannelKeyring.heldKey(aliceNext, ch)?.epoch)

            val bobOut = ConcordPrivateChannels.receive(bobEntry, wraps, w.editions, w.state().authority, bob)
            assertEquals(1L, assertIs<ChannelRekeyOutcome.Removed>(bobOut[ch]).epoch)
            val bobNext = assertNotNull(ConcordPrivateChannels.applyOutcome(bobEntry, bobOut, mapOf(ch to 0L)))
            assertNull(ConcordChannelKeyring.heldKey(bobNext, ch))
            assertEquals(mapOf(ch to 1L), ConcordChannelKeyring.cutsOf(bobNext))
            // The stale epoch-0 key can't come back through a bundle.
            assertTrue(ConcordInviteVend.catchUpChannelIds(bobNext, draft.invite).isEmpty())

            // A result computed from a stale epoch never rolls the List back.
            assertNull(ConcordPrivateChannels.applyOutcome(aliceNext, aliceOut, mapOf(ch to 0L)))
        }

    @Test
    fun aRotationFromAMemberWithoutAuthorityIsIgnored() =
        runTest {
            val w = world()
            val c = w.community
            val build = assertNotNull(ConcordPrivateChannels.create(owner, c.controlPlane, c.communityId, "mods", null, w.editions, w.state().authority, c.ownerPubKey, 2L))
            build.wraps.forEach { w.add(it) }
            w.add(ConcordModeration.grant(owner, c.controlPlane, c.communityId, alice.pubKey, listOf(build.roleIdHex), w.editions, 3L, owner = c.ownerPubKey))
            w.add(ConcordModeration.grant(owner, c.controlPlane, c.communityId, bob.pubKey, listOf(build.roleIdHex), w.editions, 3L, owner = c.ownerPubKey))
            val aliceEntry = assertNotNull(ConcordChannelKeyring.withChannelKey(entryOf(c), build.key))

            // Bob holds the key but no MANAGE_CHANNELS: holding a key is never authority (CORD-06 §3).
            val forged = ConcordPrivateChannels.buildRotation(bob, c.communityRoot, build.key, ConcordChannelRekey.mintKey(), setOf(bob.pubKey), 5L, authority = null)
            assertTrue(ConcordPrivateChannels.receive(aliceEntry, forged, w.editions, w.state().authority, alice).isEmpty())
        }

    @Test
    fun aSessionWatchesAndBuffersItsChannelRekeys() =
        runTest {
            val c = world().community
            val chId = ByteArray(32) { 0x5C }
            val key = PrivateChannelKey(chId.toHexKey(), "10".repeat(32), 3, "mods")
            val entry = entryOf(c, listOf(key))
            val session = ConcordCommunitySession(entry, alice.pubKey)

            // The next LOOKAHEAD channel epochs past the held one, under the current root.
            val next = ConcordChannelRekey.address(c.communityRoot, chId, 4).publicKeyHex
            assertTrue(next in session.channelRekeyAddresses())
            assertTrue(ConcordChannelRekey.address(c.communityRoot, chId, 3 + ConcordChannelRekey.LOOKAHEAD.toLong()).publicKeyHex in session.channelRekeyAddresses())
            assertFalse(ConcordChannelRekey.address(c.communityRoot, chId, 3).publicKeyHex in session.channelRekeyAddresses())
            assertTrue(session.ownsPlane(next))
            // Also subscribed with the auxiliary planes.
            assertTrue(ConcordSubscriptionPlanner.auxiliaryPlaneSubs(listOf(entry)).any { it.pubKeyHex == next })

            val wraps = ConcordPrivateChannels.buildRotation(owner, c.communityRoot, key, ConcordChannelRekey.mintKey(), setOf(alice.pubKey), 5L, null)
            assertEquals(ConcordIngestOutcome.STRUCTURAL, session.ingest(wraps.single()))
            assertEquals(ConcordIngestOutcome.NON_STRUCTURAL, session.ingest(wraps.single()))
            assertEquals(listOf(wraps.single().id), session.pendingChannelRekeyWraps().map { it.id })
        }

    @Test
    fun privatizeClimbsTheChannelEpochAndPublicizeFlipsTheFlagBack() =
        runTest {
            val w = world()
            val c = w.community
            val general = c.generalChannelIdHex
            val standing = assertNotNull(w.state().channels[general]).definition
            assertFalse(standing.private)

            val build = assertNotNull(ConcordPrivateChannels.privatize(owner, c.controlPlane, entryOf(c), general, standing, "insiders", w.editions, w.state().authority, 2L))
            build.wraps.forEach { w.add(it) }
            // The first privatisation is epoch 1 (CORD-03 §2); a floor seen on the wire lifts it.
            assertEquals(1L, build.key.epoch)
            assertTrue(general in w.state().privateChannelIds)
            assertEquals("insiders", w.state().roles[build.roleIdHex]?.name)
            val later = assertNotNull(ConcordPrivateChannels.privatize(owner, c.controlPlane, entryOf(c), general, standing, null, w.editions, w.state().authority, 2L, observedFloor = 4))
            assertEquals(5L, later.key.epoch)
            // Already private: nothing to do.
            assertNull(ConcordPrivateChannels.privatize(owner, c.controlPlane, entryOf(c), general, w.state().channels[general]!!.definition, null, w.editions, w.state().authority, 2L))

            val flip = assertNotNull(ConcordPrivateChannels.publicize(owner, c.controlPlane, c.communityId, general, w.state().channels[general]!!.definition, w.editions, c.ownerPubKey, 3L))
            w.add(flip)
            assertFalse(general in w.state().privateChannelIds)
            // The held private-era key keeps reading its history once the channel is public again.
            val heldEntry = assertNotNull(ConcordChannelKeyring.withChannelKey(entryOf(c), build.key))
            val planes = ConcordActions.historicalChannelPlanes(heldEntry, general, isPrivate = false)
            assertTrue(planes.any { it.epoch == 1L })
            // And the next privatisation climbs past it.
            assertEquals(2L, ConcordChannelKeyring.nextChannelEpoch(heldEntry, general))
            assertTrue(general.hexToByteArray().size == 32)
        }
}
