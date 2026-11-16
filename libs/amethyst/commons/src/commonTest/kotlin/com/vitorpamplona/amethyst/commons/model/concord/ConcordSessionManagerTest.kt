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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConcordSessionManagerTest {
    private val owner = NostrSignerInternal(KeyPair())

    private fun entryFor(
        community: NewConcordCommunity,
        name: String,
    ) = ConcordCommunityListEntry(
        id = community.communityIdHex,
        owner = community.ownerPubKey,
        ownerSalt = community.ownerSalt.toHexKey(),
        root = community.communityRoot.toHexKey(),
        rootEpoch = community.rootEpoch,
        controlPk = community.controlPkHex,
        controlRoot = community.controlRoot.toHexKey(),
        relays = listOf("wss://r.example"),
        name = name,
    )

    @Test
    fun syncsFromFlowFoldsOnIngestAndAdvancesRevision() =
        runTest {
            val alpha = ConcordCommunityFactory.create(owner, "Alpha", createdAt = 1L, relays = listOf("wss://r.example"))
            val communities = MutableStateFlow(listOf(entryFor(alpha, "Alpha")))

            val manager = ConcordSessionManager(communities, owner.pubKey, backgroundScope)
            testScheduler.runCurrent()

            // The joined community produced a session, and its control plane is in the subscribe set.
            assertTrue(manager.subscribeAddresses().contains(alpha.controlPlane.address))
            val revAfterSync = manager.revision.value
            assertTrue(revAfterSync > 0)

            // Ingesting the genesis control wraps folds Alpha and advances the revision.
            alpha.genesisWraps.forEach { assertTrue(manager.ingest(it)) }
            testScheduler.runCurrent()
            assertEquals(
                "Alpha",
                manager
                    .sessionFor(alpha.communityIdHex)
                    ?.state
                    ?.value
                    ?.metadata
                    ?.name,
            )
            assertTrue(manager.revision.value > revAfterSync)

            // The folded #general channel plane is now part of the subscribe set.
            val general = ConcordActions.publicChannel(alpha.communityRoot, alpha.generalChannelId, alpha.rootEpoch)
            assertTrue(manager.subscribeAddresses().contains(general.publicKeyHex))

            // Joining a second community via the flow creates its session too.
            val beta = ConcordCommunityFactory.create(owner, "Beta", createdAt = 1L, relays = listOf("wss://r.example"))
            communities.value = listOf(entryFor(alpha, "Alpha"), entryFor(beta, "Beta"))
            testScheduler.runCurrent()
            assertTrue(manager.subscribeAddresses().contains(beta.controlPlane.address))
            // Alpha's fold survived the re-sync.
            assertEquals(
                "Alpha",
                manager
                    .sessionFor(alpha.communityIdHex)
                    ?.state
                    ?.value
                    ?.metadata
                    ?.name,
            )
        }

    @Test
    fun exposesStreamKeysScopedToTheCommunityRelaysForNip42Auth() =
        runTest {
            val alpha = ConcordCommunityFactory.create(owner, "Alpha", createdAt = 1L, relays = listOf("wss://r.example"))
            val communities = MutableStateFlow(listOf(entryFor(alpha, "Alpha")))

            val manager = ConcordSessionManager(communities, owner.pubKey, backgroundScope)
            testScheduler.runCurrent()

            val hosted = RelayUrlNormalizer.normalize("wss://r.example")
            val elsewhere = RelayUrlNormalizer.normalize("wss://other.example")

            // Before any fold, only the control-plane key must AUTH — and only on the community's relay.
            val beforeFold = manager.streamAuthSecretsFor(hosted).map { it.toHexKey() }
            assertTrue(
                beforeFold.contains(
                    alpha.controlPlane.signer!!
                        .secretKey
                        .toHexKey(),
                ),
            )
            assertTrue(manager.streamAuthSecretsFor(elsewhere).isEmpty()) // relay-scoped

            // After the Control Plane folds, the #general channel key joins the AUTH set.
            alpha.genesisWraps.forEach { manager.ingest(it) }
            testScheduler.runCurrent()
            val general = ConcordActions.publicChannel(alpha.communityRoot, alpha.generalChannelId, alpha.rootEpoch)
            val afterFold = manager.streamAuthSecretsFor(hosted).map { it.toHexKey() }
            assertTrue(
                afterFold.contains(
                    alpha.controlPlane.signer!!
                        .secretKey
                        .toHexKey(),
                ),
            )
            assertTrue(afterFold.contains(general.secretKey.toHexKey()))
            assertFalse(manager.streamAuthSecretsFor(elsewhere).any { it.toHexKey() == general.secretKey.toHexKey() })
        }
}
