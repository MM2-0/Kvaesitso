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
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkBuilder
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkCheckpoint
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkHeader
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkIds
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkIndex
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkNews
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkPartial
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkProgress
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkSyncResult
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * When syncs start, which kind runs, and how each ends, against a fake relay
 * ([FakeSource]). The relay protocol itself is covered in quartz's TrustNetworkSyncTest.
 */
class TrustNetworkSchedulingTest {
    private val random = Random(5)
    private val relay = RelayUrlNormalizer.normalize("wss://scores.example.com")
    private val providerA = ServiceProviderTag(ProviderTypes.rank, hex(), relay)
    private val providerB = ServiceProviderTag(ProviderTypes.rank, hex(), relay)
    private val dir: Path = (System.getProperty("java.io.tmpdir") + "/wot-sched-" + System.nanoTime()).toPath()
    private val store = TrustNetworkStore(dir)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val now = 1_800_000_000L

    private fun hex() = Hex.encode(random.nextBytes(32))

    private fun network(
        provider: ServiceProviderTag,
        size: Int = 3,
    ): Pair<TrustNetworkIndex, TrustNetworkIds> {
        val builder = TrustNetworkBuilder(provider.pubkey)
        repeat(size) { builder.add(Event(hex(), provider.pubkey, 1000, 30382, arrayOf(arrayOf("d", hex()), arrayOf("rank", "50")), "", "0".repeat(128))) }
        return builder.build()
    }

    private fun result(
        provider: ServiceProviderTag,
        size: Int = 3,
    ): TrustNetworkSyncResult {
        val (index, ids) = network(provider, size)
        val header = TrustNetworkHeader(provider.pubkey, provider.relayUrl.url, 1000, now, now, heldAtCursor = ids.countSince(1000))
        return TrustNetworkSyncResult(header, index, ids, complete = true, invalid = 0, received = size, detail = "test")
    }

    /** An index for [provider] on disk, last updated [updatedAgo] seconds ago (fresh: no sync due). */
    private fun writeIndex(
        provider: ServiceProviderTag,
        updatedAgo: Long = 60,
        withIds: Boolean = true,
        fullCheckAgo: Long = 3600,
    ) {
        val (index, ids) = network(provider)
        store.write(TrustNetworkHeader(provider.pubkey, provider.relayUrl.url, 1000, now - fullCheckAgo, now - updatedAgo, heldAtCursor = 3), index, ids)
        if (!withIds) FileSystem.SYSTEM.delete(dir / TrustNetworkStore.IDS_FILE)
    }

    /** A relay that answers from these fields and records what it was asked. */
    private inner class FakeSource : TrustNetworkSource {
        val calls = CopyOnWriteArrayList<String>()
        var news = TrustNetworkNews(cards = false)
        var newsGate: CompletableDeferred<Unit>? = null
        var downloadGate: CompletableDeferred<Unit>? = null
        var downloadSize = 3
        var downloadComplete = true

        /** How long a cancelled download takes to unwind (a real one closes its socket, saves). */
        var unwindMs = 0L

        /** Thrown by the download, as a phone's OutOfMemoryError was. */
        var downloadError: Throwable? = null

        /** The update finds nothing new but learns the held counts (a version 3 file lacks one). */
        var updateLearnsCounts = false

        /** Cards each download was asked to resume from (0 = from scratch). */
        val resumedFrom = CopyOnWriteArrayList<Int>()

        override suspend fun <T> connect(block: suspend (TrustNetworkConnection) -> T): T =
            block(
                object : TrustNetworkConnection {
                    override suspend fun news(
                        provider: ServiceProviderTag,
                        header: TrustNetworkHeader,
                    ): TrustNetworkNews {
                        calls.add("news")
                        newsGate?.await()
                        return news
                    }

                    override suspend fun download(
                        provider: ServiceProviderTag,
                        progress: TrustNetworkProgress,
                        checkpoint: TrustNetworkCheckpoint?,
                        resumeFrom: TrustNetworkPartial?,
                    ): TrustNetworkSyncResult {
                        calls.add("download:${provider.pubkey}")
                        resumedFrom.add(resumeFrom?.cards ?: 0)
                        try {
                            downloadGate?.await()
                        } catch (e: CancellationException) {
                            withContext(NonCancellable) { delay(unwindMs) }
                            throw e
                        }
                        downloadError?.let { throw it }
                        return result(provider, downloadSize).let {
                            if (downloadComplete) it else TrustNetworkSyncResult(it.header, it.index, it.ids, complete = false, invalid = 0, received = it.received, detail = "partial")
                        }
                    }

                    override suspend fun update(
                        provider: ServiceProviderTag,
                        header: TrustNetworkHeader,
                        index: TrustNetworkIndex,
                        ids: TrustNetworkIds,
                        news: TrustNetworkNews?,
                        progress: TrustNetworkProgress,
                    ): TrustNetworkSyncResult {
                        calls.add("update")
                        if (updateLearnsCounts) {
                            return TrustNetworkSyncResult(header.copy(lastUpdate = now, heldAfterCursor = 0), index, ids, complete = true, invalid = 0, received = 0, detail = "need 0, gone 0", unchanged = true)
                        }
                        return result(provider)
                    }

                    override suspend fun reconcile(
                        provider: ServiceProviderTag,
                        header: TrustNetworkHeader,
                        index: TrustNetworkIndex,
                        ids: TrustNetworkIds,
                        progress: TrustNetworkProgress,
                    ): TrustNetworkSyncResult? {
                        calls.add("reconcile")
                        return null
                    }
                },
            )
    }

    private fun state(
        rankProvider: MutableStateFlow<ResolvedProvider?>,
        source: FakeSource,
        metered: Boolean = false,
        isMetered: () -> Boolean = { metered },
    ) = TrustNetworkState(
        rankProvider = rankProvider,
        minTrustScore = MutableStateFlow(5),
        store = store,
        source = source,
        scope = scope,
        canDownloadLarge = { !isMetered() },
        clock = { now },
        meteredCheckMs = 20,
    )

    @AfterTest
    fun cleanup() {
        scope.cancel()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    @Test
    fun dueSyncFollowsTheIndexAge() {
        val (index, _) = network(providerA)

        fun at(
            lastUpdate: Long,
            lastFullCheck: Long,
        ) = TrustNetwork(TrustNetworkHeader(providerA.pubkey, relay.url, 1000, lastFullCheck, lastUpdate), index)
        assertEquals(TrustNetworkSyncStatus.Kind.DOWNLOAD, TrustNetworkState.dueSync(null, now, force = false))
        assertNull(TrustNetworkState.dueSync(at(now - 60, now - 60), now, force = false))
        assertEquals(TrustNetworkSyncStatus.Kind.UPDATE, TrustNetworkState.dueSync(at(now - 60, now - 60), now, force = true))
        assertEquals(TrustNetworkSyncStatus.Kind.UPDATE, TrustNetworkState.dueSync(at(now - TrustNetworkState.UPDATE_EVERY_SECS, now - 60), now, force = false))
        assertEquals(TrustNetworkSyncStatus.Kind.FULL_CHECK, TrustNetworkState.dueSync(at(now - 60, now - TrustNetworkState.FULL_CHECK_EVERY_SECS), now, force = false))
    }

    @Test
    fun aDownloadStopsWhenThePhoneMovesToMobileData() =
        runBlocking {
            var metered = false
            val source = FakeSource().apply { downloadGate = CompletableDeferred() }
            val wot = state(MutableStateFlow(ResolvedProvider(providerA)), source, isMetered = { metered })
            wot.awaitReady()
            withTimeout(5_000) { wot.status.first { it.running == TrustNetworkSyncStatus.Kind.DOWNLOAD } }

            // Wi-Fi drops mid-download: the rest must not go over mobile data.
            metered = true
            withTimeout(5_000) { wot.status.first { it.waitingForUnmetered } }
            wot.awaitIdle()
            assertNull(wot.status.value.running)
            assertNull(wot.network.value)
            assertEquals(1, source.calls.size, "and it does not start again on mobile data")
        }

    @Test
    fun theListChangingElsewhereKeepsTheRunningDownloadOnScreen() =
        runBlocking {
            // Another row of the 10040 changes mid-download: the same rank provider resolves again.
            val source = FakeSource().apply { downloadGate = CompletableDeferred() }
            val rankProvider = MutableStateFlow<ResolvedProvider?>(ResolvedProvider(providerA))
            val wot = state(rankProvider, source)
            wot.awaitReady()
            withTimeout(5_000) { wot.status.first { it.running == TrustNetworkSyncStatus.Kind.DOWNLOAD } }

            rankProvider.value = ResolvedProvider(providerA)
            withTimeout(5_000) { wot.awaitReady() }
            delay(100)
            assertEquals(TrustNetworkSyncStatus.Kind.DOWNLOAD, wot.status.value.running, "the download is still shown")

            source.downloadGate?.complete(Unit)
            withTimeout(5_000) { wot.network.first { it?.isFrom(providerA) == true } }
            assertEquals(1, source.calls.size)
        }

    @Test
    fun aListThatCannotBeDecryptedForAMomentKeepsTheDownload() =
        runBlocking {
            // A newer 10040 arrives while an external signer cannot decrypt it: the resolved
            // provider reads null ("not known yet") while the download finishes.
            val source = FakeSource().apply { downloadGate = CompletableDeferred() }
            val rankProvider = MutableStateFlow<ResolvedProvider?>(ResolvedProvider(providerA))
            val wot = state(rankProvider, source)
            wot.awaitReady()
            withTimeout(5_000) { wot.status.first { it.running == TrustNetworkSyncStatus.Kind.DOWNLOAD } }

            rankProvider.value = null
            source.downloadGate?.complete(Unit)
            withTimeout(5_000) { wot.network.first { it?.isFrom(providerA) == true } }
            assertTrue(store.readIndex()?.isFrom(providerA) == true, "the finished download is kept")
        }

    @Test
    fun switchingAwayAndBackStillDownloads() =
        runBlocking {
            val source =
                FakeSource().apply {
                    downloadGate = CompletableDeferred()
                    unwindMs = 500
                }
            val rankProvider = MutableStateFlow<ResolvedProvider?>(ResolvedProvider(providerA))
            val wot = state(rankProvider, source, metered = false)
            wot.awaitReady()
            withTimeout(5_000) { wot.status.first { it.running == TrustNetworkSyncStatus.Kind.DOWNLOAD } }

            // A to B and back to A while A's download is still unwinding from its cancel: both
            // switches find it running, so neither starts a download; only its end can.
            rankProvider.value = ResolvedProvider(providerB)
            wot.awaitReady()
            rankProvider.value = ResolvedProvider(providerA)
            wot.awaitReady()
            source.downloadGate?.complete(Unit)
            withTimeout(5_000) { wot.network.first { it?.isFrom(providerA) == true } }
            assertTrue(wot.isActive)
        }

    @Test
    fun aSyncKilledByAnErrorEndsInAProblemInsteadOfRunningForever() =
        runBlocking {
            // Not an Exception: an OutOfMemoryError used to skip the status reset, so the screen
            // said "Downloading" for good, with no Retry, while nothing ran.
            val source = FakeSource().apply { downloadError = OutOfMemoryError("Java heap space") }
            val wot = state(MutableStateFlow(ResolvedProvider(providerA)), source)
            wot.awaitReady()
            wot.awaitIdle()

            assertEquals(listOf("download:${providerA.pubkey}"), source.calls.toList())
            assertNull(wot.status.value.running, "not stuck on downloading")
            assertEquals(TrustNetworkProblem.Failed("Java heap space"), wot.status.value.problem)

            // Retry works.
            source.downloadError = null
            assertIs<TrustNetworkOutcome.Applied>(assertNotNull(wot.syncNow()).outcome)
            assertNull(wot.status.value.problem)
            assertTrue(wot.network.value != null)
        }

    @Test
    fun nothingSyncsBeforeTheProviderListIsRead() =
        runBlocking {
            writeIndex(providerA)
            val source = FakeSource()
            val wot = state(MutableStateFlow(null), source)
            wot.awaitLoaded()
            wot.syncIfStale(force = true)
            delay(300)
            wot.awaitIdle()
            assertTrue(source.calls.isEmpty(), "an unread provider list must not look like 'no index'")
        }

    @Test
    fun nothingNewAnswersFromTheCountsAlone() =
        runBlocking {
            writeIndex(providerA)
            val before = store.readIndex()!!.index
            val source = FakeSource()
            val wot = state(MutableStateFlow(ResolvedProvider(providerA)), source)
            wot.awaitReady()
            val loaded = wot.network.value!!.index

            val run = assertNotNull(wot.syncNow(TrustNetworkSyncStatus.Kind.UPDATE))
            assertIs<TrustNetworkOutcome.Unchanged>(run.outcome)
            assertEquals(listOf("news"), source.calls.toList(), "no update, no ids read")
            assertSame(loaded, wot.network.value!!.index, "the same index: feeds keyed on it do not rebuild")
            assertEquals(
                now,
                wot.network.value!!
                    .header.lastUpdate,
            )
            assertTrue(before.keys.contentEquals(store.readIndex()!!.index.keys))
        }

    @Test
    fun newsRunsTheUpdateWithoutCountingAgain() =
        runBlocking {
            writeIndex(providerA)
            val source = FakeSource().apply { news = TrustNetworkNews(cards = true) }
            val wot = state(MutableStateFlow(ResolvedProvider(providerA)), source)
            wot.awaitReady()
            val run = assertNotNull(wot.syncNow(TrustNetworkSyncStatus.Kind.UPDATE))
            assertIs<TrustNetworkOutcome.Applied>(run.outcome)
            assertEquals(listOf("news", "update"), source.calls.toList())
        }

    @Test
    fun countsLearnedByAnUnchangedUpdateAreSaved() =
        runBlocking {
            // A file written before heldAfterCursor existed: the counts can't answer, so the update
            // reconciles. It finds nothing, but now knows the count: saving it is what keeps the
            // next process start from reconciling the whole batch again.
            writeIndex(providerA)
            assertNull(store.readIndex()!!.header.heldAfterCursor)
            val source =
                FakeSource().apply {
                    news = TrustNetworkNews(cards = true)
                    updateLearnsCounts = true
                }
            val wot = state(MutableStateFlow(ResolvedProvider(providerA)), source)
            wot.awaitReady()
            val loaded = wot.network.value!!.index

            val run = assertNotNull(wot.syncNow(TrustNetworkSyncStatus.Kind.UPDATE))
            assertIs<TrustNetworkOutcome.Unchanged>(run.outcome)
            assertSame(loaded, wot.network.value!!.index, "still the same index: feeds do not rebuild")
            assertEquals(0, store.readIndex()!!.header.heldAfterCursor, "the learned count is on disk")
        }

    @Test
    fun aCutOffDownloadResumesFromWhatItGot() =
        runBlocking {
            // The first download (started on load) stops short: what it got is saved, not used.
            val source =
                FakeSource().apply {
                    downloadSize = 5
                    downloadComplete = false
                }
            val wot = state(MutableStateFlow(ResolvedProvider(providerA)), source)
            wot.awaitReady()
            wot.awaitIdle()
            withTimeout(5_000) { wot.status.first { it.problem is TrustNetworkProblem.Failed } }
            assertNull(wot.network.value, "a partial download never becomes the network")
            assertNull(store.readIndex())
            assertEquals(5, store.readPartial(providerA.pubkey)?.cards)
            assertNull(store.readPartial(providerB.pubkey), "another provider's download starts over")

            // The next one is handed those cards; once it finishes, the checkpoint goes.
            source.downloadComplete = true
            val done = assertNotNull(wot.syncNow(TrustNetworkSyncStatus.Kind.DOWNLOAD))
            assertIs<TrustNetworkOutcome.Applied>(done.outcome)
            assertEquals(0, source.resumedFrom.first(), "the first download starts from scratch")
            assertEquals(5, source.resumedFrom.last(), "the next resumes from the checkpoint")
            assertNotNull(store.readIndex())
            assertNull(store.readPartial(providerA.pubkey), "the checkpoint is gone once the network is saved")
        }

    @Test
    fun aFallbackDownloadWaitsForAnUnmeteredNetwork() =
        runBlocking {
            // The ids file is gone, so an update would have to download everything.
            writeIndex(providerA, updatedAgo = TrustNetworkState.UPDATE_EVERY_SECS, withIds = false)
            val source = FakeSource().apply { news = TrustNetworkNews(cards = true) }
            val wot = state(MutableStateFlow(ResolvedProvider(providerA)), source, metered = true)
            wot.awaitReady()
            wot.awaitIdle()
            withTimeout(5_000) { wot.status.first { it.waitingForUnmetered } }
            assertTrue(source.calls.none { it.startsWith("download") }, "calls: ${source.calls}")
        }

    @Test
    fun aProviderChangeDuringASyncStartsTheNewProvidersDownload() =
        runBlocking {
            val gate = CompletableDeferred<Unit>()
            val source = FakeSource().apply { downloadGate = gate }
            val rankProvider = MutableStateFlow<ResolvedProvider?>(ResolvedProvider(providerA))
            val wot = state(rankProvider, source)
            wot.awaitReady()
            withTimeout(5_000) { while (source.calls.isEmpty()) delay(10) }

            // The user switches provider while A's download is still running.
            rankProvider.value = ResolvedProvider(providerB)
            delay(200)
            gate.complete(Unit)

            withTimeout(5_000) { wot.network.first { it?.isFrom(providerB) == true } }
            assertEquals(listOf("download:${providerA.pubkey}", "download:${providerB.pubkey}"), source.calls.toList())
        }

    @Test
    fun awaitIdleWaitsForTheSyncAProviderSwitchStartsOnceTheOldOneUnwinds() =
        runBlocking {
            // A's cancelled download still unwinds when B is applied, so B's download only starts
            // from A's completion: idle means after that one too.
            val source =
                FakeSource().apply {
                    downloadGate = CompletableDeferred()
                    unwindMs = 300
                }
            val rankProvider = MutableStateFlow<ResolvedProvider?>(ResolvedProvider(providerA))
            val wot = state(rankProvider, source)
            wot.awaitReady()
            withTimeout(5_000) { while (source.calls.isEmpty()) delay(10) }
            source.downloadGate = null
            rankProvider.value = ResolvedProvider(providerB)
            wot.awaitReady()
            wot.awaitIdle()
            assertEquals(listOf("download:${providerA.pubkey}", "download:${providerB.pubkey}"), source.calls.toList())
        }

    @Test
    fun awaitIdleReturnsOnceTheScopeIsCancelled() =
        runBlocking {
            // A cancelled scope never runs the completion that clears the finished sync: waiting
            // for "no sync" must still end (an account logged out mid-download).
            val source = FakeSource().apply { downloadGate = CompletableDeferred() }
            val wot = state(MutableStateFlow(ResolvedProvider(providerA)), source)
            wot.awaitReady()
            withTimeout(5_000) { while (source.calls.isEmpty()) delay(10) }
            scope.cancel()
            withTimeout(3_000) { wot.awaitIdle() }
        }

    @Test
    fun filteringStaysOffUntilTheWholeSetIsDownloaded() =
        runBlocking {
            val source = FakeSource().apply { downloadGate = CompletableDeferred() }
            val wot = state(MutableStateFlow(ResolvedProvider(providerA)), source)
            wot.awaitReady()
            withTimeout(5_000) { while (source.calls.isEmpty()) delay(10) }

            // A provider, and a download running: not yet.
            assertEquals(false, wot.isActive)
            assertEquals(false, wot.snapshot(hex(), emptySet()).isActive)

            source.downloadGate?.complete(Unit)
            withTimeout(5_000) { wot.network.first { it?.isFrom(providerA) == true } }
            assertTrue(wot.isActive)
        }

    @Test
    fun aPartialDownloadNeverTurnsFilteringOn() =
        runBlocking {
            val source = FakeSource().apply { downloadComplete = false }
            val wot = state(MutableStateFlow(ResolvedProvider(providerA)), source)
            wot.awaitReady()
            wot.awaitIdle()
            withTimeout(5_000) { wot.status.first { it.problem is TrustNetworkProblem.Failed } }
            assertNull(wot.network.value)
            assertEquals(false, wot.isActive)
            // Nor saved, so the next start cannot pick it up either.
            assertNull(store.readIndex())
        }

    @Test
    fun aProviderWithNoScoresYetIsReportedNotApplied() =
        runBlocking {
            val source = FakeSource().apply { downloadSize = 0 }
            val wot = state(MutableStateFlow(ResolvedProvider(providerA)), source)
            wot.awaitReady()
            wot.awaitIdle()
            withTimeout(5_000) { wot.status.first { it.problem == TrustNetworkProblem.NoScoresYet } }
            assertNull(wot.network.value)
        }

    @Test
    fun aNewProvidersFirstDownloadMayUseMobileData() =
        runBlocking {
            val source = FakeSource()
            val rankProvider = MutableStateFlow<ResolvedProvider?>(ResolvedProvider(null))
            val wot = state(rankProvider, source, metered = true)
            wot.awaitReady()

            wot.expectNewProvider()
            rankProvider.value = ResolvedProvider(providerA)
            withTimeout(5_000) { wot.network.first { it?.isFrom(providerA) == true } }
            assertEquals(listOf("download:${providerA.pubkey}"), source.calls.toList())
        }

    @Test
    fun aProviderRemovedDuringACheckStaysRemoved() =
        runBlocking {
            writeIndex(providerA)
            val gate = CompletableDeferred<Unit>()
            val source = FakeSource().apply { newsGate = gate }
            val rankProvider = MutableStateFlow<ResolvedProvider?>(ResolvedProvider(providerA))
            val wot = state(rankProvider, source)
            wot.awaitReady()
            val run = scope.async { wot.syncNow(TrustNetworkSyncStatus.Kind.UPDATE) }
            withTimeout(5_000) { while (source.calls.isEmpty()) delay(10) }

            // The user removes the provider while the counts are on their way.
            rankProvider.value = ResolvedProvider(null)
            withTimeout(5_000) { wot.network.first { it == null } }
            gate.complete(Unit)

            // Cancelled when the provider went away (or, had it raced to its commit, dropped there).
            val outcome = run.await()?.outcome
            assertTrue(outcome == null || outcome is TrustNetworkOutcome.ProviderChanged, "outcome=$outcome")
            assertNull(wot.network.value, "the removed provider's network must not come back")
            assertNull(store.readIndex())
        }

    @Test
    fun aFullCheckThatCannotReconcileUpdatesAndRetries() =
        runBlocking {
            writeIndex(providerA)
            val source = FakeSource()
            val wot = state(MutableStateFlow(ResolvedProvider(providerA)), source)
            wot.awaitReady()
            repeat(TrustNetworkState.MAX_FAILED_FULL_CHECKS) {
                wot.syncNow(TrustNetworkSyncStatus.Kind.FULL_CHECK)
            }
            // Two failures fall back to a cheap update; the third downloads again.
            assertEquals(listOf("reconcile", "update", "reconcile", "update", "reconcile", "download:${providerA.pubkey}"), source.calls.toList())
        }

    @Test
    fun anIndexIsNeverPairedWithAnotherSavesIds() =
        runBlocking {
            val (index, ids) = network(providerA)
            val header = TrustNetworkHeader(providerA.pubkey, relay.url, 1000, now, now)
            val first = store.write(header, index, ids)
            assertNotNull(store.readIds(first))
            // Another save (same size) whose index write never happened: its ids do not match.
            store.write(header, index, ids)
            assertNull(store.readIds(first))
        }
}
