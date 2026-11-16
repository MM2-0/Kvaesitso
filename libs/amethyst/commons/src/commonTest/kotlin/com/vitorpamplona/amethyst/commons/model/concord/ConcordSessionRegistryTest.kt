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
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ConcordSessionRegistryTest {
    private val owner = NostrSignerInternal(KeyPair())

    private fun entryFor(
        community: NewConcordCommunity,
        name: String,
        controlRoot: String? = community.controlRoot.toHexKey(),
        rootEpoch: Long = community.rootEpoch,
    ) = ConcordCommunityListEntry(
        id = community.communityIdHex,
        owner = community.ownerPubKey,
        ownerSalt = community.ownerSalt.toHexKey(),
        root = community.communityRoot.toHexKey(),
        rootEpoch = rootEpoch,
        controlPk = community.controlPkHex,
        controlRoot = controlRoot,
        relays = listOf("wss://r.example"),
        name = name,
    )

    @Test
    fun syncsSessionsRoutesWrapsAndDropsDepartedCommunities() =
        runTest {
            val alpha = ConcordCommunityFactory.create(owner, "Alpha", createdAt = 1L, relays = listOf("wss://r.example"))
            val beta = ConcordCommunityFactory.create(owner, "Beta", createdAt = 1L, relays = listOf("wss://r.example"))

            val captured = mutableListOf<Triple<String, String, com.vitorpamplona.quartz.nip01Core.core.Event>>()
            val registry = ConcordSessionRegistry { communityId, channelIdHex, rumor, _ -> captured += Triple(communityId, channelIdHex, rumor) }

            // First sync creates a session for each joined community.
            val created = registry.sync(listOf(entryFor(alpha, "Alpha"), entryFor(beta, "Beta")), owner.pubKey)
            assertEquals(setOf(alpha.communityIdHex, beta.communityIdHex), created)
            assertNotNull(registry.sessionFor(alpha.communityIdHex))
            assertNotNull(registry.sessionFor(beta.communityIdHex))

            // Both control-plane addresses are in the subscribe set from the entries alone.
            assertTrue(registry.subscribeAddresses().contains(alpha.controlPlane.address))
            assertTrue(registry.subscribeAddresses().contains(beta.controlPlane.address))

            // A genesis control wrap routes to Alpha's session and folds it (STRUCTURAL).
            alpha.genesisWraps.forEach { assertEquals(ConcordIngestOutcome.STRUCTURAL_FOLD, registry.ingest(it)) }
            val alphaState = registry.sessionFor(alpha.communityIdHex)!!.state.value
            assertEquals("Alpha", alphaState?.metadata?.name)

            // After the fold, Alpha's #general channel plane joins the subscribe set.
            val general = ConcordActions.publicChannel(alpha.communityRoot, alpha.generalChannelId, alpha.rootEpoch)
            assertTrue(registry.subscribeAddresses().contains(general.publicKeyHex))

            // A channel message decrypts and is emitted to the sink for Alpha's #general.
            val msg = ConcordActions.buildChannelMessage(owner, general, alpha.generalChannelIdHex, alpha.rootEpoch, "gm", 2L)
            assertEquals(ConcordIngestOutcome.NON_STRUCTURAL, registry.ingest(msg))
            val general9 = captured.filter { it.first == alpha.communityIdHex && it.second == alpha.generalChannelIdHex && it.third.content == "gm" }
            assertEquals(1, general9.size)

            // A re-sync that keeps Alpha but drops Beta preserves Alpha's folded state and removes Beta.
            val createdAgain = registry.sync(listOf(entryFor(alpha, "Alpha")), owner.pubKey)
            assertTrue(createdAgain.isEmpty())
            assertNotNull(registry.sessionFor(alpha.communityIdHex))
            assertNull(registry.sessionFor(beta.communityIdHex))
            assertEquals(
                "Alpha",
                registry
                    .sessionFor(alpha.communityIdHex)!!
                    .state.value
                    ?.metadata
                    ?.name,
            )

            // A wrap from an unknown community is routed nowhere.
            val gamma = ConcordCommunityFactory.create(owner, "Gamma", createdAt = 1L, relays = listOf("wss://r.example"))
            assertEquals(ConcordIngestOutcome.NOT_MINE, registry.ingest(gamma.genesisWraps.first()))
        }

    /**
     * A promotion to staff delivers the `control_root` inside the fold itself (CORD-04 §3),
     * so it lands on an entry whose session was built as a read-only member long before. The
     * session must pick the key up **without** being rebuilt: a rebuild would drop the
     * buffered Control Plane wraps and fold the community empty, which is exactly what the
     * user would see instead of their new moderation powers.
     */
    @Test
    fun adoptsAControlRootDeliveredMidSessionWithoutLosingTheFold() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Alpha", createdAt = 1L, relays = listOf("wss://r.example"))
            val registry = ConcordSessionRegistry()

            // Joined as a plain member: the address is held, the write secret is not.
            val asMember = entryFor(community, "Alpha", controlRoot = null)
            registry.sync(listOf(asMember), owner.pubKey)
            val session = registry.sessionFor(community.communityIdHex)!!
            community.genesisWraps.forEach { registry.ingest(it) }

            assertEquals(
                "Alpha",
                session.state.value
                    ?.metadata
                    ?.name,
            )
            assertFalse(session.controlPlaneKeys().canWrite, "a member holds no control_root")

            // The staff-making Grant lands and the drain writes the secret onto the entry.
            val asStaff = entryFor(community, "Alpha")
            val createdOnAdopt = registry.sync(listOf(asStaff), owner.pubKey)

            // Adopted in place: same session object, no rebuild.
            assertTrue(createdOnAdopt.isEmpty(), "adopting a control_root must not rebuild the session")
            assertSame(session, registry.sessionFor(community.communityIdHex))

            // The write key is live...
            assertTrue(session.controlPlaneKeys().canWrite, "the delivered control_root must flip canWrite")
            assertEquals(community.controlRoot.toHexKey(), session.entry.controlRoot, "the entry carries it onward for the next Grant")
            assertTrue(session.streamKeys().any { it.publicKeyHex == community.controlPkHex }, "staff now AUTHs as the plane")

            // ...and the address and the fold are untouched — the bug this guards.
            assertEquals(community.controlPlane.address, session.controlPlaneAddress)
            assertEquals(
                "Alpha",
                session.state.value
                    ?.metadata
                    ?.name,
                "adoption must not discard the buffered wraps",
            )

            // A real rotation still rebuilds rather than adopting in place.
            val nextEpoch = entryFor(community, "Alpha", rootEpoch = community.rootEpoch + 1)
            assertEquals(setOf(community.communityIdHex), registry.sync(listOf(nextEpoch), owner.pubKey))
        }
}
