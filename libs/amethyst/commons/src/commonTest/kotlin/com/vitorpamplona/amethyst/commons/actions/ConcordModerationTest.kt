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
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.ControlRootWrap
import com.vitorpamplona.quartz.concord.cord04Roles.EditionFold
import com.vitorpamplona.quartz.concord.cord04Roles.GrantEntity
import com.vitorpamplona.quartz.concord.cord04Roles.MetadataEntity
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcordModerationTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val admin = NostrSignerInternal(KeyPair())
    private val troll = NostrSignerInternal(KeyPair())
    private val stranger = NostrSignerInternal(KeyPair())

    @Test
    fun ownerDefinesRoleGrantsItAndBans() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val cp = community.controlPlane
            val communityId = community.communityId

            // Accumulate the community's editions as we publish more.
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()

            fun add(wrap: com.vitorpamplona.quartz.nip01Core.core.Event) {
                editions += ConcordActions.controlEditions(listOf(wrap), cp)
            }

            // Owner defines an "Admin" role (position 1) that can BAN and KICK.
            val roleId = ByteArray(32) { (it + 1).toByte() }
            val roleIdHex = roleId.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
            val adminRole =
                RoleEntity(
                    name = "Admin",
                    position = 1,
                    permissions = ConcordPermissions.of(ConcordPermissions.BAN, ConcordPermissions.KICK).toWire(),
                )
            add(ConcordModeration.defineRole(owner, cp, community.communityId, roleId, adminRole, editions, createdAt = 2L, owner = community.ownerPubKey))

            // Owner grants that role to the admin user.
            add(ConcordModeration.grant(owner, cp, communityId, admin.pubKey, listOf(roleIdHex), editions, createdAt = 3L, owner = community.ownerPubKey))

            // Owner bans the troll.
            add(ConcordModeration.ban(owner, cp, communityId, troll.pubKey, editions, createdAt = 4L, owner = community.ownerPubKey))

            val state: ConcordCommunityState = ConcordCommunityState.fold(editions, community.communityId, community.ownerPubKey)

            // The role exists, the admin holds BAN + the role id, and the troll is banned.
            assertTrue(state.roles.containsKey(roleIdHex))
            assertTrue(state.authority.effectivePermissions(admin.pubKey).has(ConcordPermissions.BAN))
            assertTrue(roleIdHex in state.authority.rolesOf(admin.pubKey))
            assertTrue(state.authority.isBanned(troll.pubKey))
            assertFalse(state.authority.isBanned(admin.pubKey))

            // Revoking (an empty grant, as "Remove admin" does) strips the role and its permissions.
            add(ConcordModeration.grant(owner, cp, communityId, admin.pubKey, emptyList(), editions, createdAt = 7L, owner = community.ownerPubKey))
            val demoted = ConcordCommunityState.fold(editions, community.communityId, community.ownerPubKey)
            assertFalse(demoted.authority.effectivePermissions(admin.pubKey).has(ConcordPermissions.BAN))
            assertTrue(demoted.authority.rolesOf(admin.pubKey).isEmpty())

            // Unbanning the troll clears the flag (version chains onto the ban).
            add(ConcordModeration.unban(owner, cp, communityId, troll.pubKey, editions, createdAt = 5L, owner = community.ownerPubKey))
            val healed = ConcordCommunityState.fold(editions, community.communityId, community.ownerPubKey)
            assertFalse(healed.authority.isBanned(troll.pubKey))

            // A grant forged by the troll (who outranks nobody) is dropped by the fold.
            val forged = ConcordModeration.grant(troll, cp, communityId, troll.pubKey, listOf(roleIdHex), editions, createdAt = 6L, owner = community.ownerPubKey)
            val forgedEditions: List<ControlEdition> = editions + ConcordActions.controlEditions(listOf(forged), cp)
            val afterForgery = ConcordCommunityState.fold(forgedEditions, community.communityId, community.ownerPubKey)
            assertFalse(afterForgery.authority.effectivePermissions(troll.pubKey).has(ConcordPermissions.BAN))
        }

    /**
     * Regression: [ConcordModeration] used to locate an entity's head with
     * `current.firstOrNull { … }`. `current` is the raw edition list in **wrap-arrival**
     * order, not chain order, so once an entity had ≥2 editions the "head" was whichever
     * one a relay delivered first — a stale one. The next edition then chained off it,
     * forking the chain at an already-used version, and [EditionFold] resolved the fork by
     * `minByOrNull { rumorId }` — a coin flip that could silently drop the change. Bans are
     * masked by the down-only healing union, but an unban is not, so an unban simply
     * failed to apply.
     *
     * Here the banlist has two editions (v1 bans [troll], v2 also bans [stranger]) before
     * the unban. The unban must chain off v2 — the folded head — at v3, not off v1.
     */
    @Test
    fun thirdBanlistEditionChainsOffTheFoldedHeadNotTheFirstArrival() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val cp = community.controlPlane
            val communityId = community.communityId

            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()

            // v1: ban the troll. v2: ban the stranger too (chains onto v1). Versions start at 1 (CORD-04 §1).
            editions += ConcordActions.controlEditions(listOf(ConcordModeration.ban(owner, cp, communityId, troll.pubKey, editions, createdAt = 2L, owner = community.ownerPubKey)), cp)
            editions += ConcordActions.controlEditions(listOf(ConcordModeration.ban(owner, cp, communityId, stranger.pubKey, editions, createdAt = 3L, owner = community.ownerPubKey)), cp)

            val banlistSoFar = editions.filter { it.entityKind == ControlEntityKind.BANLIST }
            assertEquals(2, banlistSoFar.size)
            val head = EditionFold.foldEntity(banlistSoFar)!!
            assertEquals(2L, head.version)
            // The stale v1 sorts first in arrival order — exactly what the old firstOrNull picked up.
            assertEquals(1L, banlistSoFar.first().version)

            // Now unban the troll. The head must be found regardless of arrival order — in
            // particular in the natural order, where the stale v1 comes first and is exactly
            // what the old firstOrNull latched onto.
            for (arrival in listOf(editions.toList(), editions.reversed())) {
                val unbanWrap = ConcordModeration.unban(owner, cp, communityId, troll.pubKey, arrival, createdAt = 4L, owner = community.ownerPubKey)
                val unban = ConcordActions.controlEditions(listOf(unbanWrap), cp).single()

                // Chains onto the folded head (v2), not the first-arrival v1.
                assertEquals(3L, unban.version)
                assertEquals(head.hashHex, unban.prevHash!!.toHexKey())

                // And the resulting state is the one the moderator asked for: troll freed, stranger still banned.
                val state = ConcordCommunityState.fold(editions + unban, community.communityId, community.ownerPubKey)
                assertFalse(state.authority.isBanned(troll.pubKey))
                assertTrue(state.authority.isBanned(stranger.pubKey))
            }
        }

    /** The same stale-head trap on a versioned entity: a third role edition must be v3. */
    @Test
    fun thirdRoleEditionChainsOffTheFoldedHead() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val cp = community.controlPlane
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()

            val roleId = ByteArray(32) { (it + 1).toByte() }

            fun role(name: String) = RoleEntity(name = name, position = 1, permissions = ConcordPermissions.of(ConcordPermissions.KICK).toWire())

            editions += ConcordActions.controlEditions(listOf(ConcordModeration.defineRole(owner, cp, community.communityId, roleId, role("Mod"), editions, createdAt = 2L, owner = community.ownerPubKey)), cp)
            editions += ConcordActions.controlEditions(listOf(ConcordModeration.defineRole(owner, cp, community.communityId, roleId, role("Admin"), editions, createdAt = 3L, owner = community.ownerPubKey)), cp)

            for (arrival in listOf(editions.toList(), editions.reversed())) {
                val third = ConcordActions.controlEditions(listOf(ConcordModeration.defineRole(owner, cp, community.communityId, roleId, role("Owner"), arrival, createdAt = 4L, owner = community.ownerPubKey)), cp).single()
                assertEquals(3L, third.version)

                val state = ConcordCommunityState.fold(editions + third, community.communityId, community.ownerPubKey)
                assertEquals("Owner", state.roles[roleId.toHexKey()]?.name)
            }
        }

    /**
     * The writer must chain onto the head a READER would honor, not the raw structural tip.
     * Given the community's owner, `headOf` folds the authority-gated head, so a rogue
     * edition sitting at the tip of the banlist chain does not drag the honest moderator's
     * next edition up behind it (version inflation an attacker controls). Armada's writers
     * chain off `folded.heads` — `pickHead`'s gated pick — for the same reason.
     */
    @Test
    fun theWriterChainsOntoTheAuthorityGatedHeadNotTheRogueTip() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val cp = community.controlPlane
            val communityId = community.communityId
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()

            // v1: the owner bans the troll.
            editions += ConcordActions.controlEditions(listOf(ConcordModeration.ban(owner, cp, communityId, troll.pubKey, editions, createdAt = 2L, owner = community.ownerPubKey)), cp)
            // v2: the stranger — who holds nothing — forges an empty banlist at the tip.
            val rogue = ConcordActions.controlEditions(listOf(ConcordModeration.unban(stranger, cp, communityId, troll.pubKey, editions, createdAt = 3L, owner = community.ownerPubKey)), cp).single()
            assertEquals(2L, rogue.version)
            val poisoned = editions + rogue

            // The owner now bans the stranger. It must chain onto v1 — the head the fold
            // honors — not onto the rogue v2, so the version is 2, not 3.
            val next =
                ConcordActions
                    .controlEditions(
                        listOf(ConcordModeration.ban(owner, cp, communityId, stranger.pubKey, poisoned, createdAt = 4L, owner = community.ownerPubKey)),
                        cp,
                    ).single()
            assertEquals(2L, next.version, "the rogue tip must not inflate the honest edition's version")

            // And, critically, the owner's banlist is computed from the GATED head, so it still
            // carries the troll. Reading the rogue's content instead would launder the forged
            // unban into an owner-signed edition and free the troll for good.
            val state = ConcordCommunityState.fold(poisoned + next, community.communityId, community.ownerPubKey)
            assertTrue(state.authority.isBanned(troll.pubKey), "the forged unban must not free the troll")
            assertTrue(state.authority.isBanned(stranger.pubKey))
        }

    /**
     * The staff-delivery decision (CORD-04 §3): a Grant that hands out a Control-writing
     * bit must carry the `control_root` in the same edition (`control_wrap`), and every
     * other shape of Grant must not — a non-staff role, a revoke, an unresolvable role,
     * or a granter who holds no secret to deliver.
     */
    @Test
    fun aStaffMakingGrantDeliversTheControlRootAndNothingElseDoes() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val cp = community.controlPlane
            val communityId = community.communityId
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()

            // A staff role (BAN writes Control editions) and a non-staff one (KICK writes the Guestbook).
            val staffRoleId = ByteArray(32) { 0x51 }
            val staffRoleIdHex = staffRoleId.toHexKey()
            val kickRoleId = ByteArray(32) { 0x52 }
            val kickRoleIdHex = kickRoleId.toHexKey()
            editions +=
                ConcordActions.controlEditions(
                    listOf(
                        ConcordModeration.defineRole(owner, cp, community.communityId, staffRoleId, RoleEntity(name = "Mod", position = 2, permissions = ConcordPermissions.of(ConcordPermissions.BAN).toWire()), editions, createdAt = 2L, owner = community.ownerPubKey),
                    ),
                    cp,
                )
            editions +=
                ConcordActions.controlEditions(
                    listOf(
                        ConcordModeration.defineRole(owner, cp, community.communityId, kickRoleId, RoleEntity(name = "Bouncer", position = 3, permissions = ConcordPermissions.of(ConcordPermissions.KICK).toWire()), editions, createdAt = 3L, owner = community.ownerPubKey),
                    ),
                    cp,
                )

            assertTrue(ConcordModeration.makesStaff(listOf(staffRoleIdHex), editions, community.communityId, community.ownerPubKey))
            assertFalse(ConcordModeration.makesStaff(listOf(kickRoleIdHex), editions, community.communityId, community.ownerPubKey))
            assertFalse(ConcordModeration.makesStaff(emptyList(), editions, community.communityId, community.ownerPubKey), "a revoke hands out nothing")
            assertFalse(ConcordModeration.makesStaff(listOf("ee".repeat(32)), editions, community.communityId, community.ownerPubKey), "an unresolvable role must not trigger a delivery")

            suspend fun grantEntity(
                roleIds: List<String>,
                controlRoot: ByteArray?,
            ): GrantEntity {
                val wrap =
                    ConcordModeration.grantWithStaffDelivery(
                        actor = owner,
                        controlPlane = cp,
                        communityId = communityId,
                        member = admin.pubKey,
                        roleIds = roleIds,
                        current = editions,
                        createdAt = 4L,
                        owner = community.ownerPubKey,
                        controlRoot = controlRoot,
                        epoch = community.rootEpoch,
                    )
                val edition = ConcordActions.controlEditions(listOf(wrap), cp).single()
                return ConcordJson.decodeOrNull<GrantEntity>(edition.content)!!
            }

            // A staff-making Grant carries the wrap; the promotee opens it and it derives to the
            // control_pk every member holds for the epoch — the adoption gate (CORD-04 §3).
            val staffGrant = grantEntity(listOf(staffRoleIdHex), community.controlRoot)
            val controlWrap = staffGrant.controlWrap
            assertNotNull(controlWrap, "a staff-making Grant must deliver the write key in the same edition")
            val opened = ControlRootWrap.openOrNull(controlWrap, admin, owner.pubKey)
            assertNotNull(opened, "the promotee must be able to open the delivery")
            assertEquals(community.rootEpoch, opened.epoch, "the wrap must be fresh for the current epoch")
            assertTrue(ControlRootWrap.derivesTo(opened.controlRoot, communityId, community.rootEpoch, community.controlPkHex))

            // Every other shape is a plain Grant.
            assertNull(grantEntity(listOf(kickRoleIdHex), community.controlRoot).controlWrap, "a Guestbook-writing role needs no key")
            assertNull(grantEntity(emptyList(), community.controlRoot).controlWrap, "a revoke delivers nothing")
            assertNull(grantEntity(listOf("ee".repeat(32)), community.controlRoot).controlWrap, "an unresolved role conservatively delivers nothing")
            assertNull(grantEntity(listOf(staffRoleIdHex), controlRoot = null).controlWrap, "no held secret, no delivery (legacy community or keyless granter)")
        }

    /**
     * CORD-04 §1/§5: every non-owner authority action cites the Grant it acts under (`vac`), and a
     * reader — this client and the reference one alike — drops a non-owner edition that doesn't. We
     * never wrote it, so every edition a delegated admin authored here was invisible to Armada.
     */
    @Test
    fun aDelegatedAdminsEditionsCiteTheirGrantAndAreHonored() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L)
            val cp = community.controlPlane
            val communityId = community.communityId
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()

            fun add(wrap: Event) = ConcordActions.controlEditions(listOf(wrap), cp).single().also { editions += it }

            val roleId = ByteArray(32) { 0x31 }
            val adminBits = ConcordPermissions.of(ConcordPermissions.BAN, ConcordPermissions.MANAGE_METADATA, ConcordPermissions.MANAGE_ROLES)
            val role = add(ConcordModeration.defineRole(owner, cp, communityId, roleId, RoleEntity(name = "Admin", position = 1, permissions = adminBits.toWire()), editions, 2L, owner = community.ownerPubKey))
            val grant = add(ConcordModeration.grant(owner, cp, communityId, admin.pubKey, listOf(roleId.toHexKey()), editions, 3L, owner = community.ownerPubKey))
            assertNull(role.authorityCitation, "the owner cites nothing")
            assertNull(grant.authorityCitation)

            val ban = add(ConcordModeration.ban(admin, cp, communityId, troll.pubKey, editions, 4L, owner = community.ownerPubKey))
            val vac = ban.authorityCitation
            assertNotNull(vac, "a delegated admin's edition must cite its Grant")
            assertEquals(ConcordKeyDerivation.grantCoordinate(communityId, admin.pubKey.hexToByteArray()).toHexKey(), vac.grantId.toHexKey())
            assertEquals(grant.version, vac.grantVersion)
            assertEquals(grant.hashHex, vac.grantHash.toHexKey())

            val rename = add(ConcordModeration.editMetadata(admin, cp, communityId, MetadataEntity(name = "Renamed"), editions, 5L, owner = community.ownerPubKey))
            assertNotNull(rename.authorityCitation)

            val state = ConcordCommunityState.fold(editions, communityId, community.ownerPubKey)
            assertTrue(state.authority.isBanned(troll.pubKey), "the cited ban is honored")
            assertEquals("Renamed", state.metadata?.name, "and so is the cited metadata edit")

            // The same actions stripped of their citation are dropped.
            val uncited = editions.map { if (it.author == admin.pubKey) it.withCitation(null) else it }
            val stripped = ConcordCommunityState.fold(uncited, communityId, community.ownerPubKey)
            assertFalse(stripped.authority.isBanned(troll.pubKey))
            assertEquals("Nostrichs", stripped.metadata?.name)
        }

    /** CORD-04 §1/§2: a new entity starts at version 1, and a Role carries its own id as `role_id`. */
    @Test
    fun newEntitiesStartAtVersionOneAndRolesCarryTheirRoleId() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L)
            val cp = community.controlPlane
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp)
            val roleId = ByteArray(32) { 0x41 }

            val role =
                ConcordActions
                    .controlEditions(
                        listOf(ConcordModeration.defineRole(owner, cp, community.communityId, roleId, RoleEntity(name = "Mod", position = 2), editions, 2L, owner = community.ownerPubKey)),
                        cp,
                    ).single()
            assertEquals(1L, role.version)
            assertNull(role.prevHash)
            assertEquals(roleId.toHexKey(), ConcordJson.decodeOrNull<RoleEntity>(role.content)?.roleId)

            val ban = ConcordActions.controlEditions(listOf(ConcordModeration.ban(owner, cp, community.communityId, troll.pubKey, editions, 3L, owner = community.ownerPubKey)), cp).single()
            assertEquals(1L, ban.version)
        }

    /** CORD-04 §2 / CORD-02 §6: a write every reader would drop is refused instead of published. */
    @Test
    fun writesPastTheProtocolCapsAreRefused() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L)
            val cp = community.controlPlane
            val cid = community.communityId
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp)

            assertFailsWith<IllegalArgumentException> {
                ConcordModeration.defineRole(owner, cp, cid, ByteArray(32) { 1 }, RoleEntity(name = "r".repeat(65), position = 2), editions, 2L, owner = community.ownerPubKey)
            }
            assertFailsWith<IllegalArgumentException> {
                ConcordModeration.defineRole(owner, cp, cid, ByteArray(32) { 1 }, RoleEntity(name = "Peer", position = 0), editions, 2L, owner = community.ownerPubKey)
            }
            assertFailsWith<IllegalArgumentException> {
                ConcordModeration.editMetadata(owner, cp, cid, MetadataEntity(name = "n".repeat(65)), editions, 2L, owner = community.ownerPubKey)
            }
            assertFailsWith<IllegalArgumentException> {
                ConcordModeration.editMetadata(owner, cp, cid, MetadataEntity(name = "ok", description = "d".repeat(10_001)), editions, 2L, owner = community.ownerPubKey)
            }
            assertFailsWith<IllegalArgumentException> {
                ConcordModeration.grant(owner, cp, cid, admin.pubKey, (1..65).map { it.toString(16).padStart(64, '0') }, editions, 2L, owner = community.ownerPubKey)
            }
        }
}
