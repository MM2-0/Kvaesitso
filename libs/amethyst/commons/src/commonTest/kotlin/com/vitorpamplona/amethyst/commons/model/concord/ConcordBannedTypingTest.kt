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
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The receive half of the soft-ban audit's A4: a banned member's typing heartbeat must not reach
 * the "… is typing" row. The send half is a guard in the app's own action layer, which a malicious
 * or modified client simply won't run — so this filter, on the receive side, is the only one that
 * actually protects the room. It shipped without a test; this is it.
 */
class ConcordBannedTypingTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val troll = NostrSignerInternal(KeyPair())
    private val regular = NostrSignerInternal(KeyPair())

    private fun entryFor(community: NewConcordCommunity) =
        ConcordCommunityListEntry(
            id = community.communityIdHex,
            owner = community.ownerPubKey,
            ownerSalt = community.ownerSalt.toHexKey(),
            root = community.communityRoot.toHexKey(),
            rootEpoch = community.rootEpoch,
            controlPk = community.controlPkHex,
            controlRoot = community.controlRoot.toHexKey(),
            relays = listOf("wss://r.example"),
            name = "Nostrichs",
        )

    @Test
    fun dropsABannedMembersTypingHeartbeatAndKeepsEveryoneElses() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val session = ConcordCommunitySession(entryFor(community), owner.pubKey)
            community.genesisWraps.forEach { session.ingest(it) }

            val channelId = community.generalChannelIdHex
            val plane = ConcordActions.publicChannel(community.communityRoot, community.generalChannelId, community.rootEpoch)
            val now = TimeUtils.now()

            // Ban first, so what follows tests the filter rather than an entry seated before the ban.
            // The banlist edition folds through the Control Plane exactly as it would on the wire.
            session.ingest(
                ConcordModeration.ban(
                    actor = owner,
                    controlPlane = session.controlPlaneKeys(),
                    communityId = community.communityIdHex.hexToByteArray(),
                    member = troll.pubKey,
                    current = session.controlEditions(),
                    createdAt = now,
                    owner = community.ownerPubKey,
                ),
            )
            assertTrue(
                session.state.value
                    ?.authority
                    ?.isBanned(troll.pubKey) == true,
                "the ban must have folded before the heartbeats are judged",
            )

            // The banned member keeps broadcasting — a modified client ignores the send-side guard.
            session.ingest(ConcordActions.buildChannelTyping(troll, plane, channelId, community.rootEpoch, now))
            assertEquals(
                null,
                session.typing.value[channelId]?.get(troll.pubKey.lowercase()),
                "a banned member must never be seated in the typing row",
            )

            // The filter is targeted, not a blanket mute: an ordinary member still types normally.
            session.ingest(ConcordActions.buildChannelTyping(regular, plane, channelId, community.rootEpoch, now))
            assertTrue(
                session.typing.value[channelId]?.containsKey(regular.pubKey.lowercase()) == true,
                "an unbanned member's typing heartbeat must still show",
            )
            assertEquals(
                null,
                session.typing.value[channelId]?.get(troll.pubKey.lowercase()),
                "seating one member must not drag the banned one in",
            )
        }
}
