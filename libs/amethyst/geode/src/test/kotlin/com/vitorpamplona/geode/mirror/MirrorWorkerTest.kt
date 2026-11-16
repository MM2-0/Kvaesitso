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
package com.vitorpamplona.geode.mirror

import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.geode.testing.publish
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventAssembler
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end `[[mirror]]` behavior over the in-process transport: a
 * downstream relay (verification ON via the parallel-verify queue) dials
 * an upstream and streams its events.
 *
 *  - `trusted = true` — the relay-to-relay trust switch — must land
 *    events the downstream could never verify itself.
 *  - `trusted = false` must keep verify-everything semantics: forged
 *    events from the upstream are dropped, valid ones land.
 */
class MirrorWorkerTest {
    private val upstreamUrl = RelayUrlNormalizer.normalize("ws://upstream.relay/")
    private val downstreamUrl = RelayUrlNormalizer.normalize("ws://downstream.relay/")

    /** Upstream side: no verification (EmptyPolicy), so forged events store fine. */
    private val hub = InProcessRelays()

    /** Downstream side: signature verification on, in the IngestQueue. */
    private val downstreamStore = EventStore(null)
    private val downstream =
        RelayEngine(
            url = downstreamUrl,
            store = downstreamStore,
            parallelVerify = true,
        )

    private var worker: MirrorWorker? = null

    private val signer = KeyPair()

    @After
    fun tearDown() {
        worker?.close()
        downstream.close()
        hub.close()
    }

    private fun startMirror(
        trusted: Boolean,
        filter: Filter? = null,
        direction: MirrorDirection = MirrorDirection.DOWN,
    ): MirrorWorker =
        MirrorWorker(
            upstreams =
                listOf(
                    MirrorUpstream(
                        upstreamUrl,
                        trusted = trusted,
                        backfillSeconds = 3600,
                        filter = filter,
                        direction = direction,
                    ),
                ),
            server = downstream.server,
            websocketBuilder = hub,
        ).also {
            worker = it
            it.start()
        }

    private fun forgedEvent(
        idSeed: Int,
        kind: Int = 1,
    ): Event =
        Event(
            id = idSeed.toString().padStart(64, '0'),
            pubKey = "1".repeat(64),
            createdAt = TimeUtils.now() - idSeed,
            kind = kind,
            tags = emptyArray(),
            content = "forged $idSeed",
            sig = "f".repeat(128),
        )

    private fun signedEvent(content: String): Event =
        EventAssembler.hashAndSign(
            pubKey = signer.pubKey.toHexKey(),
            createdAt = TimeUtils.now() - 5,
            kind = 1,
            tags = emptyArray(),
            content = content,
            privKey = signer.privKey!!,
        )

    private suspend fun awaitDownstreamCount(expected: Int) =
        withTimeout(15_000) {
            while (downstreamStore.count(Filter()) < expected) delay(25)
        }

    private suspend fun await(condition: suspend () -> Boolean) =
        withTimeout(15_000) {
            while (!condition()) delay(25)
        }

    // NOTE: assertions are on the downstream STORE, not on exact counter
    // values — the client may legitimately re-send its REQ while settling
    // (connect + filter sync), so an upstream can replay an event twice and
    // the duplicate shows up as one extra rejection. That's the documented
    // mirror behavior, not a failure.

    @Test
    fun trustedUpstreamLandsEventsTheDownstreamCannotVerify() =
        runBlocking {
            val stored = forgedEvent(1)
            val live = forgedEvent(2)

            // Stored replay: exists on the upstream before the mirror dials.
            hub.getOrCreate(upstreamUrl).preload(stored)

            startMirror(trusted = true)
            awaitDownstreamCount(1)

            // Live tail: published upstream after the mirror subscribed.
            hub.getOrCreate(upstreamUrl).publish(live)
            awaitDownstreamCount(2)

            val ids = downstreamStore.query<Event>(Filter()).map { it.id }.toSet()
            assertEquals(setOf(stored.id, live.id), ids)
        }

    @Test
    fun filterScopesTheMirrorToDeclaredKinds() =
        runBlocking {
            val wantedStored = forgedEvent(1, kind = 1)
            val unwantedStored = forgedEvent(2, kind = 7)
            hub.getOrCreate(upstreamUrl).preload(wantedStored, unwantedStored)

            startMirror(trusted = true, filter = Filter(kinds = listOf(1)))
            awaitDownstreamCount(1)

            // Live tail: the out-of-scope kind is published FIRST, so by the
            // time the in-scope one lands downstream (same connection, same
            // ordered pipeline), the kind-7 has already had its chance.
            hub.getOrCreate(upstreamUrl).publish(forgedEvent(3, kind = 7))
            val wantedLive = forgedEvent(4, kind = 1)
            hub.getOrCreate(upstreamUrl).publish(wantedLive)
            awaitDownstreamCount(2)

            val stored = downstreamStore.query<Event>(Filter())
            assertEquals(setOf(wantedStored.id, wantedLive.id), stored.map { it.id }.toSet())
            assertTrue(stored.all { it.kind == 1 })
        }

    @Test
    fun upDirectionPushesLocalEventsToTheUpstream() =
        runBlocking {
            // Pre-existing local event: the up replay (backfill window)
            // must carry it; then a live local publish must follow.
            val preexisting = forgedEvent(1)
            downstream.preload(preexisting)

            startMirror(trusted = false, direction = MirrorDirection.UP)

            val upstreamStore = hub.getOrCreate(upstreamUrl).store
            await { upstreamStore.count(Filter()) >= 1 }

            // Live events go through the verifying publish path, so they
            // must be genuinely signed (preload bypasses verification).
            val live = signedEvent("up live")
            downstream.publish(live)
            await { upstreamStore.count(Filter()) >= 2 }

            val ids = upstreamStore.query<Event>(Filter()).map { it.id }.toSet()
            assertEquals(setOf(preexisting.id, live.id), ids)
        }

    @Test
    fun bothDirectionConvergesWithoutPingPong() =
        runBlocking {
            // One event only the upstream has, one only the local relay
            // has. BOTH must converge the two stores; echo suppression
            // (plus store dedup as the backstop) must keep the shared
            // events from bouncing.
            val upstreamOnly = forgedEvent(1)
            val localOnly = forgedEvent(2)
            hub.getOrCreate(upstreamUrl).preload(upstreamOnly)
            downstream.preload(localOnly)

            val mirror = startMirror(trusted = true, direction = MirrorDirection.BOTH)

            val upstreamStore = hub.getOrCreate(upstreamUrl).store
            await { downstreamStore.count(Filter()) == 2 && upstreamStore.count(Filter()) == 2 }

            val expected = setOf(upstreamOnly.id, localOnly.id)
            assertEquals(expected, downstreamStore.query<Event>(Filter()).map { it.id }.toSet())
            assertEquals(expected, upstreamStore.query<Event>(Filter()).map { it.id }.toSet())

            // Live: an event published locally reaches the upstream AND
            // its echo back down doesn't disturb either store. Signed,
            // because the local publish path verifies.
            val live = signedEvent("both live")
            downstream.publish(live)
            await { upstreamStore.count(Filter()) == 3 }
            // Let any echo settle, then confirm counts are exact.
            delay(500)
            assertEquals(3, downstreamStore.count(Filter()))
            assertEquals(3, upstreamStore.count(Filter()))
            assertTrue(mirror.sentUp.get() >= 2)
        }

    @Test
    fun untrustedUpstreamStillVerifiesEverything() =
        runBlocking {
            val forged = forgedEvent(1)
            val valid = signedEvent("the real one")
            hub.getOrCreate(upstreamUrl).preload(forged, valid)

            val mirror = startMirror(trusted = false)

            // The valid event lands; the forged one is verified and dropped
            // (a forged event can never land untrusted, so once both have
            // been processed the store can only hold the valid one).
            await { mirror.rejected.get() >= 1 && downstreamStore.count(Filter()) == 1 }

            val stored = downstreamStore.query<Event>(Filter()).single()
            assertEquals(valid.id, stored.id)
            assertTrue(downstreamStore.query<Event>(Filter(ids = listOf(forged.id))).isEmpty())
        }
}
