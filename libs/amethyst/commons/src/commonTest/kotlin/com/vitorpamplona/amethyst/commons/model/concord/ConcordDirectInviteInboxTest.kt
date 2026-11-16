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
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelKeyring
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord05Invites.CommunityInvite
import com.vitorpamplona.quartz.concord.cord05Invites.InviteChannel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The headless Direct Invite inbox (CORD-05 §6): collects wraps, dedupes by wrap id, skips expired
 * handoffs, validates, parks — and never joins. Plus the accept decision (expired → refuse; held →
 * catch-up keys only on the same base, never a base move).
 */
class ConcordDirectInviteInboxTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val sender = NostrSignerInternal(KeyPair())
    private val me = NostrSignerInternal(KeyPair())
    private val stranger = NostrSignerInternal(KeyPair())

    private val vip = "b2".repeat(32)

    private suspend fun community(): NewConcordCommunity = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))

    private fun inviteFor(
        c: NewConcordCommunity,
        expiresAt: Long? = null,
        channels: List<InviteChannel> = emptyList(),
        root: String = c.communityRoot.toHexKey(),
        epoch: Long = c.rootEpoch,
    ) = CommunityInvite(
        communityId = c.communityIdHex,
        owner = c.ownerPubKey,
        ownerSalt = c.ownerSalt.toHexKey(),
        communityRoot = root,
        rootEpoch = epoch,
        controlPk = c.controlPkHex,
        channels = channels,
        relays = listOf("wss://relay.example"),
        name = "Nostrichs",
        expiresAt = expiresAt,
    )

    private fun heldEntryOf(c: NewConcordCommunity) =
        ConcordCommunityListEntry(
            id = c.communityIdHex,
            owner = c.ownerPubKey,
            ownerSalt = c.ownerSalt.toHexKey(),
            root = c.communityRoot.toHexKey(),
            rootEpoch = c.rootEpoch,
            controlPk = c.controlPkHex,
            relays = listOf("wss://relay.example"),
            name = "Nostrichs",
            inviteRef = "anchor",
        )

    private fun stateOf(c: NewConcordCommunity): ConcordCommunityState = ConcordCommunityState.fold(ConcordActions.controlEditions(c.genesisWraps, c.controlPlane), c.communityId, c.ownerPubKey)

    /** [c]'s fold with [vip] defined as a live Private Channel. */
    private suspend fun stateWithVip(c: NewConcordCommunity): ConcordCommunityState {
        val editions = ConcordActions.controlEditions(c.genesisWraps, c.controlPlane).toMutableList()
        editions += ConcordActions.controlEditions(listOf(ConcordModeration.defineChannel(owner, c.controlPlane, c.communityId, vip.hexToByteArray(), ChannelEntity(name = "vip", private = true), editions, createdAt = 2L, owner = c.ownerPubKey)), c.controlPlane)
        return ConcordCommunityState.fold(editions, c.communityId, c.ownerPubKey)
    }

    @Test
    fun aValidWrapIsParkedWithItsVerifiedSenderAndDedupedByWrapId() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me)
            val wrap = ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c))

            val first = assertNotNull(inbox.offer(wrap))
            assertEquals(sender.pubKey, first.sender)
            assertEquals(c.communityIdHex, first.invite.communityId)
            assertEquals(setOf(wrap.id), inbox.pending.value.keys)

            // The same wrap again (a re-delivery, or the DM pipeline seeing it too) is the same entry.
            assertSame(first, inbox.offer(wrap))
            assertEquals(1, inbox.pending.value.size)
            assertEquals(wrap.createdAt, inbox.newestWrapCreatedAt)
        }

    @Test
    fun wrapsForSomeoneElseOrForgedOrExpiredAreNotParked() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me)
            // Addressed to someone else.
            assertNull(inbox.offer(ConcordActions.buildDirectInvite(sender, stranger.pubKey, inviteFor(c))))
            // A bundle whose owner proof fails.
            assertNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c).copy(owner = stranger.pubKey))))
            // A handoff whose NIP-40 expiration passed is never decrypted.
            val expired = ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c, expiresAt = 1_000_000L))
            assertNull(inbox.offer(expired, nowSecs = 1_000L))
            assertTrue(inbox.pending.value.isEmpty())
        }

    @Test
    fun theDmPipelineSealPathParksTheSameInvite() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me)
            val wrap = ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c))
            val seal = assertIs<SealEvent>(wrap.unwrapOrNull(me))
            val opened = assertNotNull(inbox.offerSeal(wrap.copyNoContent(), seal))
            assertEquals(sender.pubKey, opened.sender)
            assertEquals(wrap.id, opened.wrapId)
            // The sweep delivering the full wrap later doesn't duplicate it.
            assertSame(opened, inbox.offer(wrap))
        }

    @Test
    fun theDmPipelineRumorPathParksWithoutAnotherDecryptAndTheSweepSkipsIt() =
        runTest {
            val c = community()
            val counting = FlakySigner(me)
            val inbox = ConcordDirectInviteInbox(counting)
            val wrap = ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c))
            // What the NIP-17 pipeline did already: one decrypt per layer.
            val seal = assertIs<SealEvent>(wrap.unwrapOrNull(me))
            val rumor = seal.unsealRumorThrowing(me)
            val opened = assertNotNull(inbox.offerRumor(wrap.copyNoContent(), seal, rumor))
            assertEquals(sender.pubKey, opened.sender)
            assertEquals(0, counting.decrypts, "the inbox never decrypted again")
            // The sweep fetching the same wrap later costs nothing either.
            assertSame(opened, inbox.offer(wrap))
            assertEquals(0, counting.decrypts)

            // A spoofed rumor (claimed author differs from the seal's) is refused on this path too.
            val spoofed = Rumor(rumor.id, stranger.pubKey, rumor.createdAt, rumor.kind, rumor.tags, rumor.content)
            val other = ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c))
            val otherSeal = assertIs<SealEvent>(other.unwrapOrNull(me))
            assertNull(inbox.offerRumor(other.copyNoContent(), otherSeal, spoofed))
        }

    @Test
    fun declineDiscardsAndTheWrapNeverResurfaces() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me)
            val wrap = ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c))
            inbox.offer(wrap)

            assertTrue(inbox.decline(wrap.id))
            assertTrue(inbox.pending.value.isEmpty())
            assertEquals(setOf(wrap.id), inbox.declined.value)
            assertNull(inbox.offer(wrap))
            assertFalse(inbox.decline(wrap.id))

            // After a restart the persisted declines are restored and still win.
            val fresh = ConcordDirectInviteInbox(me)
            fresh.restoreDeclined(inbox.declined.value)
            assertNull(fresh.offer(wrap))
            assertTrue(fresh.pending.value.isEmpty())
        }

    @Test
    fun sinceRewindsTheCursorByTheBackdateWindow() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me)
            assertNull(inbox.since())
            val wrap = ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c))
            inbox.offer(wrap)
            assertEquals(wrap.createdAt - 2 * 24 * 60 * 60L, inbox.since())
        }

    @Test
    fun visibleHidesJoinedCommunitiesButKeepsCatchUpsAndFlagsExpiry() =
        runTest {
            val joinedCommunity = community()
            val newCommunity = community()
            val inbox = ConcordDirectInviteInbox(me)

            val toNew = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(newCommunity, expiresAt = 5_000L)), nowSecs = 1L))
            val plainForJoined = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(joinedCommunity))))
            val catchUp = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(joinedCommunity, channels = listOf(InviteChannel(vip, "db".repeat(32), 0, "vip"))))))
            val baseMove = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(joinedCommunity, root = "99".repeat(32), channels = listOf(InviteChannel(vip, "db".repeat(32), 0, "vip"))))))

            val views = ConcordDirectInviteInbox.visible(inbox.pending.value.values, listOf(heldEntryOf(joinedCommunity)), nowMs = 10_000L)
            val byWrap = views.associateBy { it.wrapId }
            assertEquals(setOf(toNew.wrapId, catchUp.wrapId), byWrap.keys)
            assertFalse(plainForJoined.wrapId in byWrap)
            assertFalse(baseMove.wrapId in byWrap)
            assertTrue(byWrap.getValue(catchUp.wrapId).catchUp)
            assertFalse(byWrap.getValue(toNew.wrapId).catchUp)
            assertTrue(byWrap.getValue(toNew.wrapId).expired)
            assertFalse(byWrap.getValue(catchUp.wrapId).expired)
            assertEquals(listOf("vip"), byWrap.getValue(catchUp.wrapId).newChannelNames())
            // Named by the held fold when it knows the channel.
            assertEquals(listOf("VIP lounge"), byWrap.getValue(catchUp.wrapId).newChannelNames { if (it == vip) "VIP lounge" else null })
        }

    @Test
    fun aCatchUpThatAcceptWouldRefuseIsNotOffered() =
        runTest {
            val c = community()
            val editions = ConcordActions.controlEditions(c.genesisWraps, c.controlPlane).toMutableList()
            editions += ConcordActions.controlEditions(listOf(ConcordModeration.defineChannel(owner, c.controlPlane, c.communityId, vip.hexToByteArray(), ChannelEntity(name = "vip", private = true), editions, 2L, owner = c.ownerPubKey)), c.controlPlane)
            val state = ConcordCommunityState.fold(editions, c.communityId, c.ownerPubKey)
            val grant = listOf(InviteChannel(vip, "db".repeat(32), 0, "vip"))
            val inbox = ConcordDirectInviteInbox(me)
            // A plain member hands over a key: accept refuses it (not staff), so it is not a card.
            val fromMember = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c, channels = grant))))
            val fromOwner = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(owner, me.pubKey, inviteFor(c, channels = grant))))
            val held = listOf(heldEntryOf(c))

            val views = ConcordDirectInviteInbox.visible(inbox.pending.value.values, held, heldStateOf = { state })
            assertEquals(listOf(fromOwner.wrapId), views.map { it.wrapId })
            assertIs<DirectInviteAcceptPlan.CatchUp>(ConcordDirectInviteInbox.acceptPlan(fromOwner, held.single(), state, me.pubKey))
            assertEquals(DirectInviteAcceptPlan.NothingNew, ConcordDirectInviteInbox.acceptPlan(fromMember, held.single(), state, me.pubKey))

            // Before the fold is in, the verdict is unknown: both stay (accept waits for the roster).
            assertEquals(2, ConcordDirectInviteInbox.visible(inbox.pending.value.values, held).size)
            // A dissolved community takes no keys at all.
            assertTrue(ConcordDirectInviteInbox.visible(inbox.pending.value.values, held, heldStateOf = { state.withDissolved(true) }).isEmpty())
        }

    @Test
    fun visibleKeepsOneInvitePerCommunityAndSender() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me)
            val older = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c), createdAt = 1_700_000_000L)))
            val newer = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c), createdAt = 1_700_000_100L)))
            val fromStranger = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(stranger, me.pubKey, inviteFor(c), createdAt = 1_700_000_050L)))
            assertEquals(3, inbox.pending.value.size)
            val views = ConcordDirectInviteInbox.visible(inbox.pending.value.values, emptyList())
            assertEquals(listOf(newer.wrapId, fromStranger.wrapId), views.map { it.wrapId })
            assertFalse(older.wrapId in views.map { it.wrapId })
        }

    @Test
    fun aFutureDatedInviteNeitherHidesALegitOneNorOutranksIt() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me)
            val now = 1_700_000_000L
            val legit = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c), createdAt = now - 10), nowSecs = now))
            // A stranger's invite dated a year ahead: it may show, but it cannot shadow the sender's.
            val future = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(stranger, me.pubKey, inviteFor(c), createdAt = now + 365 * 86_400L), nowSecs = now))
            val views = ConcordDirectInviteInbox.visible(inbox.pending.value.values, emptyList(), nowMs = now * 1000)
            assertEquals(setOf(legit.wrapId, future.wrapId), views.map { it.wrapId }.toSet())
            assertEquals(now, views.first { it.wrapId == future.wrapId }.clampedSentAt, "ranked as if sent now, not a year ahead")

            // Nor does it drag the sweep cursor into the future (D6): `since` stays near now.
            assertTrue(inbox.newestWrapCreatedAt!! <= now + ConcordDirectInviteInbox.FUTURE_SKEW_SECS)
        }

    @Test
    fun anInviteSentBeforeTheUserLeftTheCommunityStaysBuried() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me)
            val now = 1_700_000_000L
            assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c), createdAt = now - 100), nowSecs = now))
            val leftAtMs = (now - 50) * 1000
            val removed = mapOf(c.communityIdHex to leftAtMs)
            assertTrue(ConcordDirectInviteInbox.visible(inbox.pending.value.values, emptyList(), nowMs = now * 1000, removedAt = removed).isEmpty())

            // A fresh re-invite sent after leaving shows.
            val fresh = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(stranger, me.pubKey, inviteFor(c), createdAt = now - 10), nowSecs = now))
            assertEquals(listOf(fresh.wrapId), ConcordDirectInviteInbox.visible(inbox.pending.value.values, emptyList(), nowMs = now * 1000, removedAt = removed).map { it.wrapId })
        }

    @Test
    fun followedSendersRankFirstAndHiddenSendersAreNeverParked() =
        runTest {
            val c = community()
            val other = community()
            val inbox = ConcordDirectInviteInbox(me, isHidden = { it == stranger.pubKey }, isFollowed = { it == owner.pubKey })
            val now = 1_700_000_000L
            assertNull(inbox.offer(ConcordActions.buildDirectInvite(stranger, me.pubKey, inviteFor(c), createdAt = now), nowSecs = now), "a muted sender never parks")
            val newer = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c), createdAt = now), nowSecs = now))
            val followed = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(owner, me.pubKey, inviteFor(other), createdAt = now - 1_000), nowSecs = now))
            val views = ConcordDirectInviteInbox.visible(inbox.pending.value.values, emptyList(), nowMs = now * 1000, isFollowed = { it == owner.pubKey })
            assertEquals(listOf(followed.wrapId, newer.wrapId), views.map { it.wrapId })
        }

    @Test
    fun theInboxIsBoundedAndShedsTheLowestRanked() =
        runTest {
            val c = community()
            val inbox = ConcordDirectInviteInbox(me, isFollowed = { it == owner.pubKey })
            val now = 1_700_000_000L
            val followed = assertNotNull(inbox.offer(ConcordActions.buildDirectInvite(owner, me.pubKey, inviteFor(c), createdAt = now - 10_000), nowSecs = now))
            repeat(ConcordDirectInviteInbox.MAX_PENDING) { i ->
                inbox.offer(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c), createdAt = now - 5_000 + i), nowSecs = now)
            }
            assertEquals(ConcordDirectInviteInbox.MAX_PENDING, inbox.pending.value.size)
            assertTrue(followed.wrapId in inbox.pending.value, "the followed sender's older invite outranks strangers' newer ones")
        }

    @Test
    fun aSignerThatCannotAnswerNowLeavesTheWrapToBeRetried() =
        runTest {
            val c = community()
            val flaky = FlakySigner(me)
            val inbox = ConcordDirectInviteInbox(flaky)
            val wrap = ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c))

            flaky.failNext = true
            assertNull(inbox.offer(wrap), "the signer timed out: nothing parked")
            // Not written off: the next delivery opens it.
            assertNotNull(inbox.offer(wrap))

            // A definitive failure (not for us) is written off: a later offer never decrypts again.
            val notOurs = ConcordActions.buildDirectInvite(sender, stranger.pubKey, inviteFor(c))
            assertNull(inbox.offer(notOurs))
            val before = flaky.decrypts
            assertNull(inbox.offer(notOurs))
            assertEquals(before, flaky.decrypts)
        }

    /** Delegates to [inner], throwing a transient signer failure on the next decrypt when [failNext]. */
    private class FlakySigner(
        private val inner: NostrSignerInternal,
    ) : NostrSigner(inner.pubKey) {
        var failNext = false
        var decrypts = 0

        override fun isWriteable() = inner.isWriteable()

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T = inner.sign(createdAt, kind, tags, content)

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip04Encrypt(plaintext, toPublicKey)

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = inner.nip04Decrypt(ciphertext, fromPublicKey)

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip44Encrypt(plaintext, toPublicKey)

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ): String {
            decrypts++
            if (failNext) {
                failNext = false
                throw SignerExceptions.TimedOutException("signer busy")
            }
            return inner.nip44Decrypt(ciphertext, fromPublicKey)
        }

        override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = inner.decryptZapEvent(event)

        override suspend fun deriveKey(nonce: HexKey) = inner.deriveKey(nonce)

        override suspend fun signPsbt(psbtHex: String) = inner.signPsbt(psbtHex)

        override fun hasForegroundSupport() = inner.hasForegroundSupport()
    }

    @Test
    fun acceptRefusesAnExpiredInvite() =
        runTest {
            val c = community()
            val opened = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c, expiresAt = 5_000L)), me))
            assertEquals(DirectInviteAcceptPlan.Expired, ConcordDirectInviteInbox.acceptPlan(opened, null, null, me.pubKey, nowMs = 5_001L))
            assertEquals(DirectInviteAcceptPlan.Join, ConcordDirectInviteInbox.acceptPlan(opened, null, null, me.pubKey, nowMs = 4_999L))
        }

    @Test
    fun acceptOnAHeldCommunityOnlyAddsKeysAndNeverMovesTheBase() =
        runTest {
            val c = community()
            val held = heldEntryOf(c)
            val state = stateWithVip(c)
            val grant = listOf(InviteChannel(vip, "db".repeat(32), 0, "vip"))

            // Same base, new key, from staff (the owner): a catch-up that keeps the held base and anchor.
            val catchUp = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(owner, me.pubKey, inviteFor(c, channels = grant)), me))
            val plan = assertIs<DirectInviteAcceptPlan.CatchUp>(ConcordDirectInviteInbox.acceptPlan(catchUp, held, state, me.pubKey))
            assertEquals(held.root, plan.entry.root)
            assertEquals(held.rootEpoch, plan.entry.rootEpoch)
            assertEquals(held.controlPk, plan.entry.controlPk)
            assertEquals("anchor", plan.entry.inviteRef)
            assertEquals(listOf(vip), plan.entry.privateChannels.map { it.channelId })
            assertEquals(listOf(vip), plan.channelIds)

            // A plain keyholder can't plant a key, and a channel the fold doesn't know as Private isn't one.
            val fromMember = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c, channels = grant)), me))
            assertEquals(DirectInviteAcceptPlan.NothingNew, ConcordDirectInviteInbox.acceptPlan(fromMember, held, state, me.pubKey))
            assertEquals(DirectInviteAcceptPlan.NothingNew, ConcordDirectInviteInbox.acceptPlan(catchUp, held, stateOf(c), me.pubKey))

            // No fold yet: the ban verdict is unknown, so it waits.
            assertEquals(DirectInviteAcceptPlan.RosterNotLoaded, ConcordDirectInviteInbox.acceptPlan(catchUp, held, null, me.pubKey))

            // Already holding that key: nothing new.
            val holding = held.let { ConcordCommunityListEntry(it.id, it.owner, it.ownerSalt, it.root, it.rootEpoch, it.controlPk, privateChannels = listOf(PrivateChannelKey(vip, "db".repeat(32), 0, "vip")), relays = it.relays, name = it.name) }
            assertEquals(DirectInviteAcceptPlan.NothingNew, ConcordDirectInviteInbox.acceptPlan(catchUp, holding, state, me.pubKey))

            // Even from staff, a bundle never REPLACES a held key — not at a higher, nor an absurd, epoch.
            // A held key moves only through a channel rekey, whose prevcommit proves continuity.
            val hijack = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(owner, me.pubKey, inviteFor(c, channels = listOf(InviteChannel(vip, "ee".repeat(32), 1_000_000_000L, "vip")))), me))
            assertEquals(DirectInviteAcceptPlan.NothingNew, ConcordDirectInviteInbox.acceptPlan(hijack, holding, state, me.pubKey))

            // A different base for a held community is never adopted, keys or not.
            val baseMove = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c, root = "99".repeat(32), channels = grant)), me))
            assertEquals(DirectInviteAcceptPlan.NothingNew, ConcordDirectInviteInbox.acceptPlan(baseMove, held, state, me.pubKey))

            // A dissolved community takes no new keys.
            assertEquals(DirectInviteAcceptPlan.NothingNew, ConcordDirectInviteInbox.acceptPlan(catchUp, held, state.withDissolved(true), me.pubKey))
        }

    @Test
    fun acceptRefusesACatchUpWhenTheHeldRosterBansUs() =
        runTest {
            val c = community()
            val editions = ConcordActions.controlEditions(c.genesisWraps, c.controlPlane).toMutableList()
            editions += ConcordActions.controlEditions(listOf(ConcordModeration.ban(owner, c.controlPlane, c.communityId, me.pubKey, editions, createdAt = 2L, owner = c.ownerPubKey)), c.controlPlane)
            val banned = ConcordCommunityState.fold(editions, c.communityId, c.ownerPubKey)
            assertTrue(banned.authority.isBanned(me.pubKey))

            val catchUp = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(sender, me.pubKey, inviteFor(c, channels = listOf(InviteChannel(vip, "db".repeat(32), 0, "vip")))), me))
            assertEquals(DirectInviteAcceptPlan.Banned, ConcordDirectInviteInbox.acceptPlan(catchUp, heldEntryOf(c), banned, me.pubKey))
        }

    @Test
    fun aBannedMemberReInvitedAtANewerEpochIsOfferedAReadmission() =
        runTest {
            val c = community()
            val held = heldEntryOf(c)
            val editions = ConcordActions.controlEditions(c.genesisWraps, c.controlPlane).toMutableList()
            editions += ConcordActions.controlEditions(listOf(ConcordModeration.ban(owner, c.controlPlane, c.communityId, me.pubKey, editions, createdAt = 2L, owner = c.ownerPubKey)), c.controlPlane)
            val banned = ConcordCommunityState.fold(editions, c.communityId, c.ownerPubKey)

            // The ban Refounded; the owner unbanned us and re-invited us at the new epoch.
            val reInvite = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(owner, me.pubKey, inviteFor(c, root = "77".repeat(32), epoch = c.rootEpoch + 1)), me))
            assertEquals(DirectInviteAcceptPlan.Readmit, ConcordDirectInviteInbox.acceptPlan(reInvite, held, banned, me.pubKey))
            val shown = ConcordDirectInviteInbox.visible(listOf(reInvite), listOf(held), heldStateOf = { banned }, me = me.pubKey)
            assertEquals(1, shown.size)
            assertFalse(shown.single().catchUp)

            // Still banned at the SAME epoch: nothing to readmit into.
            val sameEpoch = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(owner, me.pubKey, inviteFor(c)), me))
            assertNotEquals(DirectInviteAcceptPlan.Readmit, ConcordDirectInviteInbox.acceptPlan(sameEpoch, held, banned, me.pubKey))
            assertTrue(ConcordDirectInviteInbox.visible(listOf(sameEpoch), listOf(held), heldStateOf = { banned }, me = me.pubKey).isEmpty())

            // Not banned: a newer-epoch bundle still never moves a held base (CORD-06 §2).
            assertEquals(DirectInviteAcceptPlan.NothingNew, ConcordDirectInviteInbox.acceptPlan(reInvite, held, stateOf(c), me.pubKey))
            assertTrue(ConcordDirectInviteInbox.visible(listOf(reInvite), listOf(held), heldStateOf = { stateOf(c) }, me = me.pubKey).isEmpty())

            // Dissolved: death wins.
            assertNotEquals(DirectInviteAcceptPlan.Readmit, ConcordDirectInviteInbox.acceptPlan(reInvite, held, banned.withDissolved(true), me.pubKey))

            // A bundle's root is not bound to the community id, so anyone can mint a "newer epoch"
            // under their own root. Only staff of the held community may readmit us: a stranger's
            // forgery must not move the held base, nor even be offered.
            val forged = assertNotNull(ConcordActions.openDirectInvite(ConcordActions.buildDirectInvite(stranger, me.pubKey, inviteFor(c, root = "66".repeat(32), epoch = c.rootEpoch + 50)), me))
            assertNotEquals(DirectInviteAcceptPlan.Readmit, ConcordDirectInviteInbox.acceptPlan(forged, held, banned, me.pubKey))
            assertTrue(ConcordDirectInviteInbox.visible(listOf(forged), listOf(held), heldStateOf = { banned }, me = me.pubKey).isEmpty())
        }

    @Test
    fun aReadmissionKeepsWhatTheHeldEntryKnew() =
        runTest {
            val c = community()
            val chan = "a1".repeat(32)
            val gone = "c3".repeat(32)
            // Held while banned: a private channel key at epoch 1 with an older prior, a cut on another
            // channel, and a root from before an earlier Refounding.
            val withKey = assertNotNull(ConcordChannelKeyring.withChannelKey(heldEntryOf(c), PrivateChannelKey(chan, "10".repeat(32), 0, "mods")))
            val rotated = assertNotNull(ConcordChannelKeyring.withRotatedKey(withKey, chan, "11".repeat(32), 1, retiredAt = 5))
            val held = ConcordChannelKeyring.withCut(rotated, gone, 3)
            val heldWithRoots =
                ConcordCommunityListEntry(
                    id = held.id,
                    owner = held.owner,
                    ownerSalt = held.ownerSalt,
                    root = held.root,
                    rootEpoch = held.rootEpoch,
                    controlPk = held.controlPk,
                    heldRoots = listOf(HeldRoot(held.rootEpoch - 1, "55".repeat(32), null, null)),
                    privateChannels = held.privateChannels,
                    relays = held.relays,
                    name = held.name,
                    addedAt = 1,
                    inviteRef = held.inviteRef,
                    residue = held.residue,
                )

            // The owner's readmission at the next epoch re-delivers the channel at epoch 2.
            val fresh =
                ConcordCommunityListEntry(
                    id = c.communityIdHex,
                    owner = c.ownerPubKey,
                    ownerSalt = c.ownerSalt.toHexKey(),
                    root = "77".repeat(32),
                    rootEpoch = held.rootEpoch + 1,
                    controlPk = "88".repeat(32),
                    privateChannels = listOf(PrivateChannelKey(chan, "12".repeat(32), 2, "mods")),
                    relays = listOf("wss://new.example"),
                    name = "Renamed",
                    addedAt = 99,
                )
            val merged = ConcordDirectInviteInbox.readmittedEntry(heldWithRoots, fresh)

            // The new base.
            assertEquals("77".repeat(32), merged.root)
            assertEquals(held.rootEpoch + 1, merged.rootEpoch)
            assertEquals("88".repeat(32), merged.controlPk)
            assertEquals(listOf("wss://new.example"), merged.relays)
            assertEquals(99, merged.addedAt)
            // Every root it held, the one it is leaving included, so pre-ban history stays readable.
            assertEquals(setOf(held.rootEpoch - 1, held.rootEpoch), merged.heldRoots.map { it.epoch }.toSet())
            // The channel moved to the delivered key, and both older keys still read their eras.
            assertEquals(2, ConcordChannelKeyring.heldKey(merged, chan)?.epoch)
            assertEquals(listOf(1L, 0L), ConcordChannelKeyring.historicalKeys(merged, chan).map { it.epoch })
            // The cut survives: a stale key for that channel can never come back.
            assertEquals(3L, ConcordChannelKeyring.cutsOf(merged)[gone])
            // The anchor for stranded recovery is kept when the invite carries none.
            assertEquals("anchor", merged.inviteRef)
        }
}
