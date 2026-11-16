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
package com.vitorpamplona.amethyst.commons.wot.network

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkBuilder
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkHeader
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Loading, matching the provider, and the decisions. Syncing is in TrustNetworkSchedulingTest. */
class TrustNetworkStateTest {
    private val random = Random(7)
    private val providerKey = hex()
    private val relay = RelayUrlNormalizer.normalize("wss://scores.example.com")
    private val provider = ServiceProviderTag(ProviderTypes.rank, providerKey, relay)
    private val dir: Path = (System.getProperty("java.io.tmpdir") + "/wot-state-" + System.nanoTime()).toPath()
    private val store = TrustNetworkStore(dir)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val me = hex()

    private val trusted = hex()
    private val lowRank = hex()

    private fun hex() = Hex.encode(random.nextBytes(32))

    private fun card(
        subject: String,
        rank: Int,
        author: String,
    ) = Event(hex(), author, 1000, 30382, arrayOf(arrayOf("d", subject), arrayOf("rank", rank.toString())), "", "0".repeat(128))

    private fun writeIndex(providerOnDisk: String = providerKey) {
        val builder = TrustNetworkBuilder(providerOnDisk)
        builder.add(card(trusted, 40, providerOnDisk))
        builder.add(card(lowRank, 2, providerOnDisk))
        val (index, ids) = builder.build()
        val now = System.currentTimeMillis() / 1000
        store.write(TrustNetworkHeader(providerOnDisk, relay.url, 1000, now, now, heldAtCursor = 2), index, ids)
    }

    private fun state(
        rankProvider: MutableStateFlow<ResolvedProvider?> = MutableStateFlow(ResolvedProvider(provider)),
        minScore: MutableStateFlow<Int> = MutableStateFlow(5),
    ) = TrustNetworkState(
        rankProvider = rankProvider,
        minTrustScore = minScore,
        store = store,
        // No source: these tests cover loading and deciding, not syncing.
        source = null,
        scope = scope,
    )

    private fun TrustNetworkState.verdicts() = snapshot(me, emptySet())

    @AfterTest
    fun cleanup() {
        scope.cancel()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    @Test
    fun loadsTheIndexOnDiskAndAppliesTheMinimumScore() =
        runBlocking {
            writeIndex()
            val minScore = MutableStateFlow(5)
            val wot = state(minScore = minScore)
            wot.awaitLoaded()

            assertTrue(wot.isActive)
            assertTrue(wot.verdicts().passes(trusted))
            assertFalse(wot.verdicts().passes(lowRank))
            assertFalse(wot.verdicts().passes(hex()))
            assertEquals(40, wot.rankOf(trusted))

            minScore.value = 1
            assertTrue(wot.verdicts().passes(lowRank))
        }

    @Test
    fun keepsTheIndexUntilTheProviderListIsRead() =
        runBlocking {
            writeIndex()
            val rankProvider = MutableStateFlow<ResolvedProvider?>(null)
            val wot = state(rankProvider)
            wot.awaitLoaded()
            // Startup: the 10040 has not been read yet, so a push can still be filtered.
            delay(200)
            assertTrue(wot.isActive)

            // The list is read and names no provider: filtering turns off and the files go.
            rankProvider.value = ResolvedProvider(null)
            withTimeout(5_000) { wot.network.first { it == null } }
            assertFalse(wot.verdicts().passes(trusted))
            assertNull(store.readIndex())
            assertFalse(FileSystem.SYSTEM.exists(dir / TrustNetworkStore.IDS_FILE))
        }

    @Test
    fun ignoresAnIndexFromAnotherProvider() =
        runBlocking {
            writeIndex(providerOnDisk = hex())
            val wot = state()
            wot.awaitReady()
            assertNull(wot.network.value)
            assertNull(wot.rankOf(trusted))
            // Deleted, or a cold start (a push) would filter with it until the list is read.
            assertNull(store.readIndex())
        }

    @Test
    fun withoutAFileNothingIsFiltered() =
        runBlocking {
            val wot = state()
            wot.awaitReady()
            assertFalse(wot.isActive)
            assertFalse(wot.verdicts().isOutside(trusted))
        }

    @Test
    fun explainsEveryVerdict() =
        runBlocking {
            writeIndex()
            val wot = state()
            wot.awaitReady()
            val friend = hex()
            val verdicts = wot.snapshot(me, setOf(friend))
            assertEquals(TrustVerdict.SELF, verdicts.explain(me))
            assertEquals(TrustVerdict.FOLLOW, verdicts.explain(friend))
            assertEquals(TrustVerdict.TRUSTED, verdicts.explain(trusted))
            assertEquals(TrustVerdict.BELOW_MIN_SCORE, verdicts.explain(lowRank))
            assertEquals(TrustVerdict.NOT_IN_NETWORK, verdicts.explain(hex()))
            assertEquals(false, TrustVerdict.NOT_IN_NETWORK.isKnown)
        }

    @Test
    fun withoutANetworkTheVerdictIsNeutral() =
        runBlocking {
            val wot = state()
            wot.awaitReady()
            assertEquals(TrustVerdict.NO_NETWORK, wot.verdicts().explain(hex()))
            assertNull(TrustVerdict.NO_NETWORK.isKnown)
        }

    @Test
    fun publishesVerdictsOnlyWhenAnAnswerCanChange() =
        runBlocking {
            writeIndex()
            val follows = MutableStateFlow(emptySet<String>())
            val minScore = MutableStateFlow(5)
            val wot = state(minScore = minScore)
            wot.awaitReady()
            val flow = wot.verdicts(me, follows, scope)
            val first = withTimeout(5_000) { flow.first { it.isActive } }

            // Waits for the answer, not for the next object: the combine's first value (computed from
            // the old follows on another thread) can replace the initial snapshot after `first` was
            // read, and is a new object that does not have the friend yet.

            // Following someone is a new answer.
            val friend = hex()
            follows.value = setOf(friend)
            val followed = withTimeout(5_000) { flow.first { it.explain(friend) == TrustVerdict.FOLLOW } }
            assertTrue(followed !== first)

            // So is a new minimum score.
            minScore.value = 1
            val lowered = withTimeout(5_000) { flow.first { it.passes(lowRank) } }
            assertTrue(lowered !== followed)
        }

    private fun newerCard(
        subject: String,
        rank: Int,
        at: Long,
        author: String = providerKey,
    ) = UserAssertionEvent(hex(), author, at, arrayOf(arrayOf("d", subject), arrayOf("rank", rank.toString()), arrayOf("followers", "12")), "", "0".repeat(128))

    @Test
    fun cardsSeenBetweenSyncsUpdateTheNetwork() =
        runBlocking {
            // The index on disk was synced up to created_at 1000.
            writeIndex()
            val wot = state()
            wot.awaitReady()
            val stranger = hex()
            val start = wot.verdicts()

            wot.offer(
                listOf(
                    // Already in the index (at the cursor): ignored.
                    newerCard(lowRank, 90, at = 1000),
                    // Someone the index does not have yet, and a rise above the minimum.
                    newerCard(stranger, 30, at = 2000),
                    // Another author's card: ignored.
                    newerCard(trusted, 1, at = 2000, author = hex()),
                ),
            )
            assertEquals(30, wot.rankOf(stranger))
            assertEquals(12, wot.followersOf(stranger))
            val letIn = wot.verdicts()
            assertTrue(letIn.passes(stranger))
            assertFalse(letIn.passes(lowRank))
            assertEquals(40, wot.rankOf(trusted))
            assertFalse(start.sameAnswersAs(letIn), "letting someone in is a new answer")

            // The provider removes them (rank 0), then an older copy arrives late: still removed.
            wot.offer(listOf(newerCard(stranger, 0, at = 3000)))
            wot.offer(listOf(newerCard(stranger, 30, at = 2500)))
            assertNull(wot.rankOf(stranger))
            val removed = wot.verdicts()
            assertEquals(TrustVerdict.NOT_IN_NETWORK, removed.explain(stranger))
            assertFalse(letIn.sameAnswersAs(removed))

            // A rank change that does not cross the minimum updates the badge, not the answers.
            wot.offer(listOf(newerCard(trusted, 60, at = 2000)))
            assertEquals(60, wot.rankOf(trusted))
            assertTrue(removed.sameAnswersAs(wot.verdicts()))
            assertTrue(wot.isActive)
        }

    @Test
    fun withoutANetworkFollowsDoNotChangeTheAnswers() {
        val none = TrustVerdicts(null, emptyMap(), 5, me, setOf(hex()))
        assertTrue(none.sameAnswersAs(TrustVerdicts(null, emptyMap(), 5, me, setOf(hex(), hex()))))
    }
}
