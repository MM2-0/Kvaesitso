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

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteRegistry
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * CORD-05 §5 end to end through the writer: a creator's Invite Registry edition, published over the
 * Control Plane, opened and folded like any other edition, drives the Public/Private mode, and that
 * mode decides whether a ban Refounds (CORD-06 §3) and whether a retire privatizes (CORD-05 §2).
 */
class ConcordInviteRegistryPublishTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val inviter = NostrSignerInternal(KeyPair())
    private val troll = NostrSignerInternal(KeyPair())
    private val link1 = "c1".repeat(32)
    private val link2 = "c2".repeat(32)

    @Test
    fun registriesPublishFoldAndDriveThePublicPrivateMode() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val cp = community.controlPlane
            val cid = community.communityId
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()

            fun add(wrap: Event) {
                editions += ConcordActions.controlEditions(listOf(wrap), cp)
            }

            fun fold(): ConcordCommunityState = ConcordCommunityState.fold(editions, cid, community.ownerPubKey)

            // A fresh community has no live link: Private, and a ban would Refound.
            assertFalse(fold().isPublic)
            assertTrue(fold().banRequiresRefounding(listOf(troll.pubKey)))

            // The owner mints: the registry lists the link signer and the community reads Public.
            add(ConcordModeration.setInviteRegistry(owner, cp, cid, listOf(link1), editions, createdAt = 2L, owner = community.ownerPubKey))
            val ownerHead = editions.last()
            assertEquals(ControlEntityKind.INVITE_REGISTRY, ownerHead.entityKind)
            assertEquals(ConcordInviteRegistry.coordinateHex(cid, owner.pubKey), ownerHead.entityIdHex)
            assertEquals("""["$link1"]""", ownerHead.content)
            assertTrue(fold().isPublic)
            assertFalse(fold().banRequiresRefounding(listOf(troll.pubKey)), "a Public ban is the Banlist alone")

            // A CREATE_INVITE holder's registry is honored beside the owner's; a troll's is not.
            val roleId = ByteArray(32) { 5 }
            add(
                ConcordModeration.defineRole(
                    owner,
                    cp,
                    cid,
                    roleId,
                    RoleEntity(name = "Inviter", position = 2, permissions = ConcordPermissions.of(ConcordPermissions.CREATE_INVITE).toWire()),
                    editions,
                    createdAt = 3L,
                    owner = community.ownerPubKey,
                ),
            )
            add(ConcordModeration.grant(owner, cp, cid, inviter.pubKey, listOf(roleId.toHexKey()), editions, createdAt = 4L, owner = community.ownerPubKey))
            add(ConcordModeration.setInviteRegistry(inviter, cp, cid, listOf(link2), editions, createdAt = 5L, owner = community.ownerPubKey))
            add(ConcordModeration.setInviteRegistry(troll, cp, cid, listOf("dd".repeat(32)), editions, createdAt = 5L, owner = community.ownerPubKey))
            val both = fold()
            assertEquals(setOf(link1, link2), both.liveInviteLinks)
            assertEquals(listOf(link2), both.registryOf(inviter.pubKey))
            assertEquals(emptyList(), both.registryOf(troll.pubKey))

            // The inviter's edition carried the `vac` citation that made it count.
            val inviterEdition: ControlEdition = assertNotNull(editions.lastOrNull { it.author == inviter.pubKey && it.entityKind == ControlEntityKind.INVITE_REGISTRY })
            assertNotNull(inviterEdition.authorityCitation)

            // Retiring the owner's link leaves the inviter's: still Public, nothing privatizes.
            assertFalse(both.retiringWouldPrivatize(listOf(link1)))
            add(ConcordModeration.setInviteRegistry(owner, cp, cid, emptyList(), editions, createdAt = 6L, owner = community.ownerPubKey))
            val ownerRetired = fold()
            assertEquals(2L, editions.last().version, "the retire chains onto the owner's own registry head")
            assertEquals(setOf(link2), ownerRetired.liveInviteLinks)

            // Now the inviter's is the last live link: retiring it privatizes (a Refounding, CORD-05 §2).
            assertTrue(ownerRetired.retiringWouldPrivatize(listOf(link2)))
            // Banning the inviter would take their registry with them: that ban Refounds.
            assertTrue(ownerRetired.banRequiresRefounding(listOf(inviter.pubKey)))

            add(ConcordModeration.setInviteRegistry(inviter, cp, cid, emptyList(), editions, createdAt = 7L, owner = community.ownerPubKey))
            assertFalse(fold().isPublic)
        }

    @Test
    fun aRegistryEditChainsOntoTheFloorAwareHeadAcrossARefounding() =
        runTest {
            // The prior epoch reached v2 of the owner's registry; the current epoch has not (yet)
            // re-wrapped it, so the honored head is the floor, not "no registry".
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val cp = community.controlPlane
            val cid = community.communityId
            val prior = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()
            prior += ConcordActions.controlEditions(listOf(ConcordModeration.setInviteRegistry(owner, cp, cid, listOf(link1), prior, 2L, owner = community.ownerPubKey)), cp)
            prior += ConcordActions.controlEditions(listOf(ConcordModeration.setInviteRegistry(owner, cp, cid, listOf(link1, link2), prior, 3L, owner = community.ownerPubKey)), cp)
            val v2 = prior.last()
            assertEquals(2L, v2.version)
            val floors = ConcordCommunityState.authorizedHeads(prior, cid, community.ownerPubKey)

            val current = ConcordActions.controlEditions(community.genesisWraps, cp)
            val naive = ConcordActions.controlEditions(listOf(ConcordModeration.setInviteRegistry(owner, cp, cid, listOf(link2), current, 4L, owner = community.ownerPubKey)), cp).single()
            assertEquals(1L, naive.version, "ignoring the floor forks a fresh v1 below the honored v2")

            val chained = ConcordActions.controlEditions(listOf(ConcordModeration.setInviteRegistry(owner, cp, cid, listOf(link2), current, 4L, owner = community.ownerPubKey, floors = floors)), cp).single()
            assertEquals(3L, chained.version)
            assertEquals(v2.hashHex, chained.prevHash?.toHexKey())
            // And the fold with the same floors honors it.
            assertEquals(listOf(link2), ConcordCommunityState.fold(current + chained, cid, community.ownerPubKey, floors).registryOf(owner.pubKey))
        }
}
