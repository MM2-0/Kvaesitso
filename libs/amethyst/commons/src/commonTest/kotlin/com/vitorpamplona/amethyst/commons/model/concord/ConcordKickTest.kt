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
import com.vitorpamplona.quartz.concord.cord02Community.GuestbookAction
import com.vitorpamplona.quartz.concord.cord02Community.GuestbookEntry
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Cooperative Kick (CORD-02 §5, CORD-04 §6) through the session fold: an honored Kick departs its
 * target in everyone's roster and tells the target's own client to leave; a Kick from someone who may
 * not kick is dropped; a Kick whose Grant has not folded yet parks until it does; and a re-join
 * (a newer Join, or a re-added entry) leaves the Kick behind.
 */
class ConcordKickTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val mod = NostrSignerInternal(KeyPair())
    private val target = NostrSignerInternal(KeyPair())
    private val bystander = NostrSignerInternal(KeyPair())

    private class World(
        val community: NewConcordCommunity,
        val controlWraps: MutableList<Event>,
    ) {
        fun state(): ConcordCommunityState = ConcordCommunityState.fold(ConcordActions.controlEditions(controlWraps, community.controlPlane), community.communityId, community.ownerPubKey)
    }

    /** A community whose owner defined a Mod role (position 5, KICK only) and granted it to [mod]. */
    private suspend fun world(): Pair<World, Event> {
        val c = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))
        val w = World(c, c.genesisWraps.toMutableList())
        val roleId = ByteArray(32) { 7 }
        val role = RoleEntity(name = "Mod", position = 5, permissions = ConcordPermissions.of(ConcordPermissions.KICK).toWire())
        w.controlWraps += ConcordModeration.defineRole(owner, c.controlPlane, c.communityId, roleId, role, ConcordActions.controlEditions(w.controlWraps, c.controlPlane), 2L, owner = c.ownerPubKey)
        val modGrant = ConcordModeration.grant(owner, c.controlPlane, c.communityId, mod.pubKey, listOf(roleId.toHexKey()), ConcordActions.controlEditions(w.controlWraps, c.controlPlane), 3L, owner = c.ownerPubKey)
        w.controlWraps += modGrant
        return w to modGrant
    }

    private fun entryOf(
        c: NewConcordCommunity,
        addedAt: Long,
    ) = ConcordCommunityListEntry(
        id = c.communityIdHex,
        owner = c.ownerPubKey,
        ownerSalt = c.ownerSalt.toHexKey(),
        root = c.communityRoot.toHexKey(),
        rootEpoch = c.rootEpoch,
        controlPk = c.controlPkHex,
        relays = listOf("wss://relay.example"),
        name = "Nostrichs",
        addedAt = addedAt,
    )

    private fun session(
        c: NewConcordCommunity,
        me: String,
        wraps: List<Event>,
        addedAt: Long = 1_000L,
    ): ConcordCommunitySession = ConcordCommunitySession(entryOf(c, addedAt), me) { _, _, _, _ -> }.also { s -> wraps.forEach { s.ingest(it) } }

    private fun guestbook(c: NewConcordCommunity) = ConcordActions.guestbookPlane(c.communityRoot, c.communityId, c.rootEpoch)

    private fun citationOf(
        w: World,
        actor: String,
    ): AuthorityCitation? = w.state().authority.citationFor(actor)

    @Test
    fun anHonoredKickDepartsTheTargetAndAsksTheirClientToLeave() =
        runTest {
            val (w, _) = world()
            val c = w.community
            val gb = guestbook(c)
            val join = ConcordActions.buildGuestbookJoin(target, gb, createdAt = 5L)
            val kick = ConcordActions.buildGuestbookKick(mod, gb, target.pubKey, citationOf(w, mod.pubKey), createdAt = 10L)

            val ownerView = session(c, owner.pubKey, w.controlWraps + join + kick)
            val t = target.pubKey.lowercase()
            assertEquals(GuestbookAction.KICK, ownerView.guestbook.value[t]?.action)
            assertFalse(t in ownerView.members.value)
            assertEquals(GuestbookAction.KICK, ownerView.departedMembers()[t]?.action)
            assertFalse(t in ownerView.allMembers())
            assertNull(ownerView.kickedMe()) // the Kick names the target, not the owner

            // The target's client finds the Kick against this membership and complies.
            val targetView = session(c, target.pubKey, w.controlWraps + join + kick, addedAt = 1_000L)
            val verdict = assertNotNull(targetView.kickedMe())
            assertEquals(mod.pubKey, verdict.author)

            // Re-invited after the Kick: the entry's added_at postdates it, so it judges nothing.
            assertNull(session(c, target.pubKey, w.controlWraps + join + kick, addedAt = 11_000L).kickedMe())

            // Re-joined: a newer self-signed Join supersedes the Kick.
            val rejoin = ConcordActions.buildGuestbookJoin(target, gb, createdAt = 20L)
            targetView.ingest(rejoin)
            assertNull(targetView.kickedMe())
            assertTrue(t in targetView.members.value)
        }

    @Test
    fun aKickFromSomeoneWhoMayNotKickIsDropped() =
        runTest {
            val (w, _) = world()
            val c = w.community
            val gb = guestbook(c)
            val join = ConcordActions.buildGuestbookJoin(target, gb, createdAt = 5L)
            // A roleless member holds no KICK; nobody may kick the owner.
            val forged = ConcordActions.buildGuestbookKick(bystander, gb, target.pubKey, citationOf(w, bystander.pubKey), createdAt = 10L)
            val atOwner = ConcordActions.buildGuestbookKick(mod, gb, owner.pubKey, citationOf(w, mod.pubKey), createdAt = 10L)

            val targetView = session(c, target.pubKey, w.controlWraps + join + forged + atOwner)
            assertEquals(GuestbookAction.JOIN, targetView.guestbook.value[target.pubKey.lowercase()]?.action)
            assertNull(targetView.kickedMe())
            assertNull(targetView.guestbook.value[owner.pubKey.lowercase()])
            assertTrue(targetView.departedMembers().isEmpty())
        }

    @Test
    fun aKickParksUntilItsCitedGrantFolds() =
        runTest {
            val (w, modGrant) = world()
            val c = w.community
            val gb = guestbook(c)
            val join = ConcordActions.buildGuestbookJoin(target, gb, createdAt = 5L)
            val kick = ConcordActions.buildGuestbookKick(mod, gb, target.pubKey, citationOf(w, mod.pubKey), createdAt = 10L)

            // The Guestbook can lead the Control Plane: without the mod's Grant their Kick parks.
            val targetView = session(c, target.pubKey, (w.controlWraps - modGrant) + join + kick)
            assertNull(targetView.kickedMe())
            assertEquals(GuestbookAction.JOIN, targetView.guestbook.value[target.pubKey.lowercase()]?.action)

            // The Grant folds: the same held Kick is now honored, with no Guestbook arrival.
            targetView.ingest(modGrant)
            assertNotNull(targetView.kickedMe())
        }

    @Test
    fun aKickWithoutACitationFromANonOwnerParks() =
        runTest {
            val (w, _) = world()
            val c = w.community
            val gb = guestbook(c)
            val join = ConcordActions.buildGuestbookJoin(target, gb, createdAt = 5L)
            val uncited = ConcordActions.buildGuestbookKick(mod, gb, target.pubKey, citation = null, createdAt = 10L)
            assertNull(session(c, target.pubKey, w.controlWraps + join + uncited).kickedMe())

            // The owner cites nothing and needs nothing.
            val byOwner = ConcordActions.buildGuestbookKick(owner, gb, target.pubKey, citation = null, createdAt = 10L)
            assertNotNull(session(c, target.pubKey, w.controlWraps + join + byOwner).kickedMe())
        }

    /** The shared rule both the app session and amy apply (CORD-04 §6). */
    @Test
    fun honoredKickAgainstOnlyCountsAKickNewerThanTheMembership() {
        val me = "a".repeat(64)
        val owner = "b".repeat(64)
        val kick = GuestbookEntry(member = me, action = GuestbookAction.KICK, createdAt = 100, inviteCreator = null, inviteLabel = null, author = owner)
        val coalesced = mapOf(me to kick)

        assertNotNull(ConcordActions.honoredKickAgainst(coalesced, me.uppercase(), owner, addedAtMs = 0))
        assertNotNull(ConcordActions.honoredKickAgainst(coalesced, me, owner, addedAtMs = 99_999))
        // Re-joined after the Kick: it judged the earlier membership.
        assertNull(ConcordActions.honoredKickAgainst(coalesced, me, owner, addedAtMs = 100_000))
        // The owner is never kicked; a latest motion that is a Join is not a Kick.
        assertNull(ConcordActions.honoredKickAgainst(mapOf(owner to kick), owner, owner, addedAtMs = 0))
        val join = GuestbookEntry(member = me, action = GuestbookAction.JOIN, createdAt = 200, inviteCreator = null, inviteLabel = null)
        assertNull(ConcordActions.honoredKickAgainst(mapOf(me to join), me, owner, addedAtMs = 0))
    }
}
