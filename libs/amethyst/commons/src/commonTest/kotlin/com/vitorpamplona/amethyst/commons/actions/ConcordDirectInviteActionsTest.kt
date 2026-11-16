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
import com.vitorpamplona.amethyst.commons.model.ConcordDirectInviteSendResult
import com.vitorpamplona.marmot.RecipientRelayFetcher
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.concord.cord04Roles.RoleScope
import com.vitorpamplona.quartz.concord.cord05Invites.InviteRelayDictionary
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * CORD-05 §6 send side: a Direct Invite carries exactly the Private Channel keys the recipient's
 * Roles entitle them to (Armada `vendableChannels`, audience "member"), and goes to the
 * recipient's 10050 → NIP-65 read → stock relays.
 */
class ConcordDirectInviteActionsTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val mod = NostrSignerInternal(KeyPair())
    private val member = NostrSignerInternal(KeyPair())

    private val modsChannel = "a1".repeat(32)
    private val vipChannel = "b2".repeat(32)
    private val modsRoleId = ByteArray(32) { 7 }

    private fun entryOf(community: NewConcordCommunity) =
        ConcordCommunityListEntry(
            id = community.communityIdHex,
            owner = community.ownerPubKey,
            ownerSalt = community.ownerSalt.toHexKey(),
            root = community.communityRoot.toHexKey(),
            rootEpoch = community.rootEpoch,
            controlPk = community.controlPkHex,
            controlRoot = community.controlRoot.toHexKey(),
            privateChannels =
                listOf(
                    PrivateChannelKey(modsChannel, "ca".repeat(32), 2, "mods"),
                    PrivateChannelKey(vipChannel, "db".repeat(32), 0, "vip"),
                ),
            relays = listOf("wss://relay.example"),
            name = "Nostrichs",
        )

    /** A community where [mod] holds a Role scoped to [modsChannel]; nobody is scoped to [vipChannel]. */
    private suspend fun foldWithModsRole(community: NewConcordCommunity): ConcordCommunityState {
        val cp = community.controlPlane
        val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList<ControlEdition>()

        fun add(wrap: Event) {
            editions += ConcordActions.controlEditions(listOf(wrap), cp)
        }
        val role =
            RoleEntity(
                roleId = modsRoleId.toHexKey(),
                name = "Mods",
                position = 5,
                permissions = ConcordPermissions.of(ConcordPermissions.MENTION_EVERYONE).toWire(),
                scope = RoleScope(kind = "channel", channelId = modsChannel),
            )
        add(ConcordModeration.defineRole(owner, cp, community.communityId, modsRoleId, role, editions, createdAt = 2L, owner = community.ownerPubKey))
        add(ConcordModeration.grant(owner, cp, community.communityId, mod.pubKey, listOf(modsRoleId.toHexKey()), editions, createdAt = 3L, owner = community.ownerPubKey))
        return ConcordCommunityState.fold(editions, community.communityId, community.ownerPubKey)
    }

    @Test
    fun aDirectInviteCarriesOnlyTheChannelsTheRecipientIsEntitledTo() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))
            val state = foldWithModsRole(community)
            assertTrue(modsRoleId.toHexKey() in state.authority.rolesOf(mod.pubKey))
            val entry = entryOf(community)

            // A plain member holds no channel-scoped Role: no Private Channel keys.
            val toMember = ConcordActions.directInviteFor(entry, state.authority, member.pubKey, creator = owner.pubKey)
            assertTrue(toMember.channels.isEmpty())

            // The mod gets #mods (their Role's scope) and nothing else.
            val toMod = ConcordActions.directInviteFor(entry, state.authority, mod.pubKey, creator = owner.pubKey, expiresAtMs = 1_900_000_000_000L)
            assertEquals(listOf(modsChannel), toMod.channels.map { it.id })
            assertEquals("ca".repeat(32), toMod.channels.single().key)
            assertEquals(2L, toMod.channels.single().epoch)
            assertEquals(1_900_000_000_000L, toMod.expiresAt)
            assertEquals(owner.pubKey, toMod.creatorNpub)

            // The owner is entitled to every channel.
            val toOwner = ConcordActions.directInviteFor(entry, state.authority, owner.pubKey, creator = mod.pubKey)
            assertEquals(setOf(modsChannel, vipChannel), toOwner.channels.map { it.id }.toSet())

            // The bundle is the held base, and it validates as a fetched one would.
            assertEquals(entry.root, toMember.communityRoot)
            assertEquals(entry.rootEpoch, toMember.rootEpoch)
            assertEquals(entry.controlPk, toMember.controlPk)
        }

    @Test
    fun draftRefusesBannedPartiesAndBadRecipients() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))
            val cp = community.controlPlane
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()
            editions += ConcordActions.controlEditions(listOf(ConcordModeration.ban(owner, cp, community.communityId, member.pubKey, editions, createdAt = 2L, owner = community.ownerPubKey)), cp)
            val state = ConcordCommunityState.fold(editions, community.communityId, community.ownerPubKey)
            val entry = entryOf(community)

            fun refusal(draft: ConcordDirectInviteDraft) = (draft as? ConcordDirectInviteDraft.Refused)?.reason

            assertEquals(ConcordDirectInviteSendResult.RECIPIENT_BANNED, refusal(ConcordActions.draftDirectInvite(entry, state, owner.pubKey, member.pubKey)))
            assertEquals(ConcordDirectInviteSendResult.NOT_MEMBER, refusal(ConcordActions.draftDirectInvite(entry, state, member.pubKey, mod.pubKey)))
            assertEquals(ConcordDirectInviteSendResult.NOT_MEMBER, refusal(ConcordActions.draftDirectInvite(entry, state.withDissolved(true), owner.pubKey, mod.pubKey)))
            assertEquals(ConcordDirectInviteSendResult.INVALID_RECIPIENT, refusal(ConcordActions.draftDirectInvite(entry, state, owner.pubKey, "npub1notahexkey")))

            // The folded metadata names the preview.
            val ready = assertIs<ConcordDirectInviteDraft.Ready>(ConcordActions.draftDirectInvite(entry, state, owner.pubKey, mod.pubKey.uppercase()))
            assertEquals("Nostrichs", ready.invite.name)
            assertEquals(owner.pubKey, ready.invite.creatorNpub)
        }

    @Test
    fun theBuiltWrapOpensForTheRecipient() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://relay.example"))
            val state = foldWithModsRole(community)
            val invite = ConcordActions.directInviteFor(entryOf(community), state.authority, mod.pubKey, creator = owner.pubKey)
            val wrap = ConcordActions.buildDirectInvite(owner, mod.pubKey, invite)

            // The indexed lookup a recipient runs matches the wrap's tags.
            val filter = ConcordActions.directInvitesFilter(mod.pubKey, since = 5L)
            assertEquals(listOf(mod.pubKey), filter.tags?.get("p"))
            assertEquals(listOf("3313"), filter.tags?.get("k"))
            assertEquals(5L, filter.since)
            assertTrue(filter.match(wrap))

            val opened = assertNotNull(ConcordActions.openDirectInvite(wrap, mod))
            assertEquals(owner.pubKey, opened.sender)
            assertEquals(listOf(modsChannel), ConcordActions.privateChannelKeysOf(opened.invite).map { it.channelId })
        }

    @Test
    fun deliveryGoesTo10050ThenNip65ReadThenStock() {
        val dm = RelayUrlNormalizer.normalizeOrNull("wss://dm.example")!!
        val withDm = RecipientRelayFetcher.Lists(dmInbox = listOf(dm), keyPackage = emptyList(), nip65 = null)
        assertEquals(setOf(dm), ConcordActions.directInviteDeliveryRelays(withDm))

        val stock = InviteRelayDictionary.STOCK.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
        assertEquals(stock, ConcordActions.directInviteDeliveryRelays(null))
        assertEquals(stock, ConcordActions.directInviteDeliveryRelays(RecipientRelayFetcher.Lists(emptyList(), emptyList(), null)))
    }
}
