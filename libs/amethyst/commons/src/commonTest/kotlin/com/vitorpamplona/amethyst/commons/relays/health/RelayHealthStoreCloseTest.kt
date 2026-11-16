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
package com.vitorpamplona.amethyst.commons.relays.health

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RelayHealthStoreCloseTest {
    private class CountingPersistence : RelayHealthPersistence {
        @Volatile var saves: Int = 0

        @Volatile var lastSnapshot: RelayHealthSnapshot? = null

        override fun load(): RelayHealthSnapshot = RelayHealthSnapshot()

        override fun save(snapshot: RelayHealthSnapshot) {
            saves++
            lastSnapshot = snapshot
        }
    }

    private val url = RelayUrlNormalizer.normalizeOrNull("wss://example.com")!!

    @Test
    fun close_is_idempotent() =
        runTest {
            val persistence = CountingPersistence()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            val store =
                RelayHealthStore(
                    persistence = persistence,
                    parentScope = scope,
                    ioDispatcher = dispatcher,
                )

            store.close()
            store.close()
            store.close()

            advanceUntilIdle()
            // First close runs a fire-and-forget final save; later closes are no-ops.
            assertEquals(1, persistence.saves)
            scope.cancel()
        }

    @Test
    fun recordIncoming_after_close_does_not_schedule_persist() =
        runTest {
            val persistence = CountingPersistence()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            val store =
                RelayHealthStore(
                    persistence = persistence,
                    parentScope = scope,
                    ioDispatcher = dispatcher,
                )

            // NB: the store's init launches an always-on `while(true){ reclassify(); delay(60s) }`
            // ticker on this shared test scheduler. advanceUntilIdle() would chase that periodic
            // delay forever (livelock). Advance just past the persist debounce instead so init's
            // debounced save fires while the ticker stays parked at its 60s mark.
            advanceTimeBy(RelayHealthStore.PERSIST_DEBOUNCE_MS + 1)
            runCurrent()
            val baseline = persistence.saves

            store.close()
            advanceUntilIdle()
            val afterClose = persistence.saves
            assertTrue(afterClose >= baseline + 1, "close should run the final save")

            store.recordIncoming(url, atSeconds = 1_700_000_000L)
            advanceUntilIdle()
            // closed → schedulePersist short-circuits, no extra save.
            assertEquals(afterClose, persistence.saves)
            scope.cancel()
        }

    @Test
    fun close_with_internal_scope_owns_its_lifecycle() =
        runTest {
            val persistence = CountingPersistence()
            val store =
                RelayHealthStore(
                    persistence = persistence,
                    parentScope = null, // store owns its scope
                    ioDispatcher = Dispatchers.Default,
                )

            // Sanity: schedulePersist on internal scope worked.
            store.recordConnect(url, atSeconds = 1_700_000_000L)

            store.close()
            // Idempotent + does not throw.
            store.close()
        }
}
