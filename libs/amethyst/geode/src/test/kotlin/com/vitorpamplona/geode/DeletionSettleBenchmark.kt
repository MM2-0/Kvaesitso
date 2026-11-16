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
package com.vitorpamplona.geode

import com.vitorpamplona.geode.testing.RelayClientTest
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropyReconcileIds
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySettleDeletions
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cost of the deletion side-channel ([negentropySettleDeletions]) at database scale.
 *
 * The whole point of the two-pass design is that turning deletions on does NOT re-fetch
 * content — the content sync already downloaded the need set, and the settle only touches
 * the reconcile *residual* (the events a deletion stopped from converging). So its cost is
 * one reconcile per round plus the residual, independent of how big the database is.
 *
 * This models the post-content-settle state: a relay holding N notes, and a local store
 * holding the same N notes EXCEPT K it deleted (it keeps the K kind-5s). The residual is
 * exactly those K — so a `sendUp` settle fetches K, not N. It prints the reconcile cost
 * (the O(N) part it shares with any sync) next to the settle cost, so the deletion
 * overhead is visible as "≈ a couple of reconciles + K", not "+ a content re-download".
 *
 * Why the printed settle can read as several× a bare reconcile at large N: the extra time
 * is NOT the deletion algorithm (a phase breakdown showed reconciles stay ~sub-second at
 * N=100k, and the settle re-fetches K=20, not N). It is entirely the K `publishAndConfirm`
 * ingests into a large geode relay — publishing K *plain* notes costs the same — and that
 * ingest path is JVM-cold on first use: consecutive K-note batches dropped monotonically
 * (~3100 → ~570 ms) purely from JIT warmup. So the cost is O(K) relay-ingest dominated by
 * one-time warmup, independent of N.
 *
 * Default N is small so it doubles as a fast correctness guard; scale it with
 * `-DdelBenchN=200000` to see the shape at size. Not a speed assertion (container noise).
 */
class DeletionSettleBenchmark : RelayClientTest() {
    private val signer = NostrSignerSync(KeyPair())
    private val local = EventStore(null)

    @AfterTest fun closeLocal() = local.close()

    private val n = System.getProperty("delBenchN")?.toInt() ?: 2_000
    private val k = System.getProperty("delBenchK")?.toInt() ?: 20

    @Test
    fun settleCostIsResidualNotDatabase() =
        runBlocking {
            val base = TimeUtils.now() - n
            // N notes with monotonic created_at (sorted order == index order).
            val notes = (0 until n).map { signer.sign(TextNoteEvent.build("n$it", createdAt = base + it.toLong())) }
            // The last K are the ones we deleted locally.
            val deleted = notes.takeLast(k)
            val kept = notes.dropLast(k)
            val deletions = deleted.map { signer.sign(DeletionRequestEvent.build(listOf(it), createdAt = it.createdAt + 1)) }

            // Relay holds all N notes; we hold the N-K we didn't delete, plus the K kind-5s.
            defaultRelay.preload(notes)
            kept.forEach { local.insert(it) }
            deletions.forEach { local.insert(it) }
            assertEquals(n - k, local.query<Event>(Filter(kinds = listOf(1))).size, "local kept N-K notes")

            // Cost of one reconcile — the O(N) work every sync round already does.
            val r0 = System.nanoTime()
            val diff =
                withTimeout(120_000) {
                    client.negentropyReconcileIds(defaultRelayUrl, Filter(kinds = listOf(1)), local.snapshotIdsForNegentropy(listOf(Filter(kinds = listOf(1)))))
                }
            val reconcileMs = (System.nanoTime() - r0) / 1e6
            assertEquals(k, diff.needIds.size, "the residual is exactly the K deleted notes, not N")

            // Cost of the whole settle: reconcile(s) + resolve the K-event residual.
            val s0 = System.nanoTime()
            val res =
                withTimeout(120_000) {
                    client.negentropySettleDeletions(
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                        store = local,
                        sendUp = true,
                        applyDown = false,
                        idleTimeoutMs = 60_000,
                    )
                }
            val settleMs = (System.nanoTime() - s0) / 1e6

            assertEquals(k, res.sentUp, "sent exactly K deletions up")
            assertEquals(
                n - k,
                defaultRelay.store.query<Event>(Filter(kinds = listOf(1))).size,
                "relay converged: the K deleted notes are gone",
            )

            println("─ DeletionSettleBenchmark @ N=$n K=$k ─")
            println("  one reconcile:   ${"%.1f".format(reconcileMs)} ms  (O(N), shared with any sync)")
            println("  full settle:     ${"%.1f".format(settleMs)} ms  (${res.rounds} rounds, sentUp=${res.sentUp})")
            println("  deletion cost:   settle is ~${"%.1f".format(settleMs / reconcileMs)}× one reconcile — fetched K=$k, not N=$n")
        }
}
