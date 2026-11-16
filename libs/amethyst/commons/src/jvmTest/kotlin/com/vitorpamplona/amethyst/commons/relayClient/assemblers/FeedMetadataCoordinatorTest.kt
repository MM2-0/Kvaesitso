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
package com.vitorpamplona.amethyst.commons.relayClient.assemblers

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression tests for PR #3483 review findings on FeedMetadataCoordinator:
 *
 *   - Finding 5: `queuedKind3Pubkeys` was marked-on-send, so if every index
 *     relay timed out the pubkeys were permanently marked and subsequent
 *     calls short-circuited — WoT stayed empty for the whole session.
 *     Fix: pubkeys land in `queuedKind3Pubkeys` only after ≥1 EOSE; on
 *     zero-EOSE timeout they roll out of `inFlightBatchedKind3` for retry.
 *
 *   - Finding 6: `eoseReceived: MutableSet` was mutated from per-relay
 *     `onEose` callbacks running on `Dispatchers.IO` with no sync. Fix:
 *     `BatchEoseGate` funnels EOSE notifications through a `Channel` so a
 *     single consumer coroutine is the sole reader/writer of the `seen`
 *     set.
 */
class FeedMetadataCoordinatorTest {
    private lateinit var scope: CoroutineScope
    private val relay1 = NormalizedRelayUrl("wss://relay1.test/")
    private val relay2 = NormalizedRelayUrl("wss://relay2.test/")
    private val relay3 = NormalizedRelayUrl("wss://relay3.test/")
    private val indexRelays = setOf(relay1, relay2, relay3)

    @Before
    fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun teardown() {
        scope.cancel()
    }

    private fun pubkey(seed: Int): HexKey = seed.toString(16).padStart(64, '0')

    /**
     * Suspends until every coroutine the coordinator launched into [scope] has finished.
     *
     * This is what makes the assertions deterministic. The coordinator does its work in
     * `scope.launch { subscribe(); gate.awaitAll(timeout); unsubscribe(); promote-or-roll-back }`,
     * so "has call 1 finished?" is a question about the job tree, not about the clock. The tests
     * used to answer it with `delay(timeoutMs + margin)` and assert straight after — which holds
     * only while the dispatcher is free to start that coroutine promptly. On a loaded runner
     * (macOS CI, 1431 tests in the same module) the launch itself can be queued past the margin,
     * the roll-back lands late, the next call short-circuits, and the assertion fails on a
     * perfectly healthy coordinator. Waiting on the jobs removes the margin entirely.
     */
    private suspend fun awaitCoordinatorIdle(timeoutMs: Long = 30_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (scope.coroutineContext.job.children
                .any { it.isActive }
        ) {
            check(System.currentTimeMillis() < deadline) { "coordinator work did not settle within ${timeoutMs}ms" }
            delay(2)
        }
    }

    /** Polls [predicate] to a deadline. For preconditions we cannot express as job completion. */
    private suspend fun waitUntil(
        message: String,
        timeoutMs: Long = 30_000,
        predicate: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for: $message" }
            delay(2)
        }
    }

    /**
     * Fake client that captures subscribe/unsubscribe and lets the test
     * drive EOSE notifications on any dispatcher we choose.
     *
     * Every collection here is written from the coordinator's coroutines (`Dispatchers.Default`,
     * and `Dispatchers.IO` for the concurrent-EOSE test) and read from the test thread, so plain
     * `mutableMapOf`/`mutableListOf` were two more races: an unsynchronized `size` read can be
     * stale, and `fireEose` iterating `subscriptions.values` while a coordinator coroutine calls
     * `unsubscribe` can throw ConcurrentModificationException. Concurrency is the thing under
     * test here, so the fake must not be the weak link.
     */
    private class ControllableClient(
        private val delegate: INostrClient = EmptyNostrClient(),
    ) : INostrClient by delegate {
        val subscriptions = ConcurrentHashMap<String, SubscriptionListener>()
        val subscribeCalls: MutableList<Map<NormalizedRelayUrl, List<Filter>>> =
            Collections.synchronizedList(mutableListOf())
        private val unsubscribes = AtomicInteger(0)
        val unsubscribeCallCount: Int get() = unsubscribes.get()

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            listener?.let { subscriptions[subId] = it }
            subscribeCalls.add(filters)
        }

        override fun unsubscribe(subId: String) {
            subscriptions.remove(subId)
            unsubscribes.incrementAndGet()
        }

        fun fireEose(relay: NormalizedRelayUrl) {
            // Snapshot: a coordinator coroutine may unsubscribe concurrently.
            subscriptions.values.toList().forEach { it.onEose(relay, forFilters = null) }
        }
    }

    @Test
    fun `loadKind3Batched retries after zero-EOSE timeout`() =
        runBlocking {
            val client = ControllableClient()
            val coordinator =
                FeedMetadataCoordinator(
                    client = client,
                    scope = scope,
                    indexRelays = indexRelays,
                )

            val pubkeys = listOf(pubkey(1), pubkey(2), pubkey(3))

            // Call 1 — no relay EOSEs; must time out.
            coordinator.loadKind3Batched(pubkeys, timeoutMs = 200)
            awaitCoordinatorIdle() // call 1 timed out and rolled back

            // Call 2 — the same pubkeys must be re-subscribed since call 1
            // never got a successful EOSE. The old code would silently
            // short-circuit here.
            coordinator.loadKind3Batched(pubkeys, timeoutMs = 200)
            awaitCoordinatorIdle()

            assertEquals(
                "Zero-EOSE timeout must not permanently dedup pubkeys",
                2,
                client.subscribeCalls.size,
            )
            assertEquals(
                "Second call must re-request the same author set",
                pubkeys.size,
                client.subscribeCalls[1]
                    .values
                    .first()
                    .first()
                    .authors!!
                    .size,
            )
        }

    @Test
    fun `loadKind3Batched short-circuits after successful EOSE`() =
        runBlocking {
            val client = ControllableClient()
            val coordinator =
                FeedMetadataCoordinator(
                    client = client,
                    scope = scope,
                    indexRelays = indexRelays,
                )

            val pubkeys = listOf(pubkey(1), pubkey(2))

            coordinator.loadKind3Batched(pubkeys, timeoutMs = 1_000)
            // The listener must be registered before we fire, or the EOSEs go nowhere.
            waitUntil("subscription registered") { client.subscriptions.isNotEmpty() }
            indexRelays.forEach(client::fireEose)
            awaitCoordinatorIdle() // coordinator finished + promoted to queued

            coordinator.loadKind3Batched(pubkeys, timeoutMs = 200)
            awaitCoordinatorIdle()

            assertEquals(
                "Successful call must dedup subsequent identical calls",
                1,
                client.subscribeCalls.size,
            )
        }

    @Test
    fun `loadKind3Batched promotes even when only some relays EOSE`() =
        runBlocking {
            val client = ControllableClient()
            val coordinator =
                FeedMetadataCoordinator(
                    client = client,
                    scope = scope,
                    indexRelays = indexRelays,
                )

            val pubkeys = listOf(pubkey(1))

            coordinator.loadKind3Batched(pubkeys, timeoutMs = 300)
            waitUntil("subscription registered") { client.subscriptions.isNotEmpty() }
            // Only 1 of 3 EOSEs — timeout still fires but we made progress.
            client.fireEose(relay1)
            awaitCoordinatorIdle()

            coordinator.loadKind3Batched(pubkeys, timeoutMs = 200)
            awaitCoordinatorIdle()

            assertEquals(
                "≥1 EOSE = progress = promote to queued (avoid re-asking)",
                1,
                client.subscribeCalls.size,
            )
        }

    /**
     * Regression for finding 6 — pumps EOSE from many dispatchers in
     * parallel. The old MutableSet-based code could drop entries or throw
     * ConcurrentModificationException on the internal HashSet iterator.
     * BatchEoseGate must aggregate every distinct relay exactly once.
     */
    @Test
    fun `EOSE aggregator is safe under concurrent per-relay callbacks`() =
        runBlocking {
            val bigIndexSet =
                (0..19).map { NormalizedRelayUrl("wss://relay$it.test/") }.toSet()
            val client = ControllableClient()
            val coordinator =
                FeedMetadataCoordinator(
                    client = client,
                    scope = scope,
                    indexRelays = bigIndexSet,
                )

            coordinator.loadKind3Batched(listOf(pubkey(1)), timeoutMs = 2_000)
            waitUntil("subscription registered") { client.subscriptions.isNotEmpty() }

            // Fire EOSEs concurrently from many dispatchers.
            val jobs =
                bigIndexSet.map { relay ->
                    scope.launch(Dispatchers.IO) {
                        client.fireEose(relay)
                    }
                }
            jobs.forEach { it.join() }

            // The 2nd call must short-circuit — every relay EOSE'd, so
            // pubkey(1) is now in queuedKind3Pubkeys.
            awaitCoordinatorIdle()
            coordinator.loadKind3Batched(listOf(pubkey(1)), timeoutMs = 200)
            awaitCoordinatorIdle()

            assertEquals(
                "Under concurrent EOSE from all relays, aggregator must reach target",
                1,
                client.subscribeCalls.size,
            )
        }

    @Test
    fun `loadMetadataBatched follows the same retry semantics`() =
        runBlocking {
            val client = ControllableClient()
            val coordinator =
                FeedMetadataCoordinator(
                    client = client,
                    scope = scope,
                    indexRelays = indexRelays,
                )

            val pubkeys = listOf(pubkey(1), pubkey(2))

            // Call 1 — zero EOSE, timeout.
            coordinator.loadMetadataBatched(pubkeys, timeoutMs = 200)
            awaitCoordinatorIdle()
            // Call 2 — must re-subscribe.
            coordinator.loadMetadataBatched(pubkeys, timeoutMs = 200)
            awaitCoordinatorIdle()

            assertTrue(
                "Metadata batch also retries on zero-EOSE timeout",
                client.subscribeCalls.size >= 2,
            )
        }

    @Test
    fun `clear releases in-flight dedup so a fresh call always fires`() =
        runBlocking {
            val client = ControllableClient()
            val coordinator =
                FeedMetadataCoordinator(
                    client = client,
                    scope = scope,
                    indexRelays = indexRelays,
                )

            coordinator.loadKind3Batched(listOf(pubkey(1)), timeoutMs = 200)
            waitUntil("subscription registered") { client.subscriptions.isNotEmpty() }
            // clear() must drop the in-flight tracker even mid-request.
            coordinator.clear()
            awaitCoordinatorIdle() // call 1 finished + rolled back

            coordinator.loadKind3Batched(listOf(pubkey(1)), timeoutMs = 200)
            awaitCoordinatorIdle()

            assertTrue(client.subscribeCalls.size >= 2)
        }
}
