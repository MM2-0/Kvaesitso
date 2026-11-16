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
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcordPlaneRegistryTest {
    private val owner = NostrSignerInternal(KeyPair())

    @Test
    fun routesControlAndChannelWrapsAndRejectsOutsiders() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val entry =
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

            val registry = ConcordPlaneRegistry()
            registry.registerControlPlanes(listOf(entry))

            // A genesis control wrap routes to the CONTROL plane.
            val controlWrap = community.genesisWraps.first()
            assertTrue(registry.isKnownPlane(controlWrap.pubKey))
            val routedControl = registry.route(controlWrap)
            assertNotNull(routedControl)
            assertEquals(ConcordPlaneKind.CONTROL, routedControl.plane.kind)
            assertEquals(community.communityIdHex, routedControl.plane.communityId)

            // After folding + registering channels, a channel message routes to CHANNEL.
            val state = ConcordActions.foldCommunity(community.genesisWraps, community.controlPlane, community.communityId, community.ownerPubKey)
            registry.registerChannels(entry, state)

            val channel = ConcordActions.publicChannel(community.communityRoot, community.generalChannelId, community.rootEpoch)
            val msgWrap = ConcordActions.buildChannelMessage(owner, channel, community.generalChannelIdHex, community.rootEpoch, "gm", 2L)
            val routedMsg = registry.route(msgWrap)
            assertNotNull(routedMsg)
            assertEquals(ConcordPlaneKind.CHANNEL, routedMsg.plane.kind)
            assertEquals(community.generalChannelIdHex, routedMsg.plane.channelId?.channelId)
            assertEquals(ChatEvent.KIND, routedMsg.opened.rumor.kind)
            assertEquals("gm", routedMsg.opened.rumor.content)

            // A wrap from an unrelated plane (different community) is not ours.
            val outsider = ConcordCommunityFactory.create(owner, "Other", createdAt = 1L, relays = listOf("wss://r.example"))
            assertNull(registry.route(outsider.genesisWraps.first()))
        }
}
