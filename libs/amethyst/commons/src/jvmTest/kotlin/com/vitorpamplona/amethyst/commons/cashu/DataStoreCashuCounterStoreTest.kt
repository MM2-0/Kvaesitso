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
package com.vitorpamplona.amethyst.commons.cashu

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.vitorpamplona.amethyst.commons.model.preferences.CopyOnceMigration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * NUT-13 counters decide whether ecash is spendable.
 *
 * A counter handed out twice under the same (seed, keyset) derives the same
 * blinded secret twice; the mint answers `outputs already signed` and the
 * proofs are stranded. These tests exist because that path had no coverage at
 * all while it was backed by SharedPreferences.
 */
class DataStoreCashuCounterStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var seq = 0

    /**
     * Closes a store's scope and waits for it.
     *
     * DataStore refuses two live instances over one file and only releases it
     * once the owning job has actually finished, so a bare cancel() races the
     * next open.
     */
    private suspend fun CoroutineScope.release() {
        coroutineContext.job.cancelAndJoin()
    }

    private fun raw(
        file: File = File(folder.root, "cashu_${seq++}.preferences_pb"),
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        migrations: List<DataMigration<Preferences>> = emptyList(),
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
            scope = scope,
            migrations = migrations,
            produceFile = { file.toOkioPath() },
        )

    @Test
    fun anUnseenKeysetStartsAtZero() =
        runTest {
            assertEquals(0L, DataStoreCashuCounterStore(raw()).peek("keyset1"))
        }

    @Test
    fun peekDoesNotAdvance() =
        runTest {
            val store = DataStoreCashuCounterStore(raw())

            store.peek("keyset1")
            store.peek("keyset1")

            assertEquals(0L, store.reserve("keyset1", 1))
        }

    @Test
    fun reserveReturnsTheFirstIndexAndAdvancesByCount() =
        runTest {
            val store = DataStoreCashuCounterStore(raw())

            assertEquals(0L, store.reserve("keyset1", 3))
            assertEquals(3L, store.peek("keyset1"))
            assertEquals(3L, store.reserve("keyset1", 2))
            assertEquals(5L, store.peek("keyset1"))
        }

    @Test
    fun keysetsAdvanceIndependently() =
        runTest {
            val store = DataStoreCashuCounterStore(raw())

            store.reserve("keyset1", 5)

            assertEquals(0L, store.reserve("keyset2", 1))
        }

    @Test
    fun aNonPositiveReservationIsRejected() =
        runTest {
            val store = DataStoreCashuCounterStore(raw())

            // Not assertThrows: a nested runTest would wrap the failure.
            val thrown = runCatching { store.reserve("keyset1", 0) }.exceptionOrNull()

            assertTrue("expected IllegalArgumentException, got $thrown", thrown is IllegalArgumentException)
        }

    /**
     * The property everything else rests on: no index is ever handed out twice.
     * Concurrent reservations must carve up disjoint ranges.
     */
    @Test
    fun concurrentReservationsNeverOverlap() =
        runTest {
            val store = DataStoreCashuCounterStore(raw())
            val batch = 4
            val workers = 25

            val firsts =
                (1..workers)
                    .map { async(Dispatchers.IO) { store.reserve("keyset1", batch) } }
                    .awaitAll()

            val handedOut = firsts.flatMap { first -> (0 until batch).map { first + it } }
            assertEquals("every index handed out exactly once", handedOut.size, handedOut.toSet().size)
            assertEquals("the counter accounts for all of them", (workers * batch).toLong(), store.peek("keyset1"))
        }

    /** A reserved counter must survive the process that reserved it. */
    @Test
    fun reservationsSurviveAReopen() =
        runTest {
            val file = File(folder.root, "persist.preferences_pb")

            val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            DataStoreCashuCounterStore(raw(file, firstScope)).reserve("keyset1", 7)
            firstScope.release()

            val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            assertEquals(7L, DataStoreCashuCounterStore(raw(file, secondScope)).peek("keyset1"))
            secondScope.release()
        }

    // ── seeding from older stores ─────────────────────────────────────

    @Test
    fun seedIfMissingCarriesALegacyValueForward() =
        runTest {
            val store = DataStoreCashuCounterStore(raw())

            store.seedIfMissing("keyset1", 42L)

            assertEquals(42L, store.peek("keyset1"))
        }

    /** Never backwards: a legacy value below the current one must be ignored. */
    @Test
    fun seedIfMissingNeverMovesACounterBackwards() =
        runTest {
            val store = DataStoreCashuCounterStore(raw())
            store.reserve("keyset1", 100)

            store.seedIfMissing("keyset1", 5L)

            assertEquals(100L, store.peek("keyset1"))
        }

    @Test
    fun seedIfMissingIgnoresNonPositiveValues() =
        runTest {
            val store = DataStoreCashuCounterStore(raw())
            store.reserve("keyset1", 3)

            store.seedIfMissing("keyset1", 0L)
            store.seedIfMissing("keyset1", -1L)

            assertEquals(3L, store.peek("keyset1"))
        }

    /**
     * The SharedPreferences -> DataStore migration. A counter lost here
     * restarts a keyset at zero and reuses every index it already spent.
     */
    @Test
    fun theLegacyMigrationCarriesEveryCounter() =
        runTest {
            val legacy =
                mapOf(
                    "counter_keysetA" to 17L,
                    "counter_keysetB" to 4L,
                )
            val migration =
                CopyOnceMigration("migrated.cashuCounters") { out ->
                    legacy.forEach { (key, value) -> out[longPreferencesKey(key)] = value }
                }

            val store = DataStoreCashuCounterStore(raw(migrations = listOf(migration)))

            assertEquals(17L, store.peek("keysetA"))
            assertEquals(4L, store.peek("keysetB"))
            assertEquals("the next reservation continues, never replays", 17L, store.reserve("keysetA", 1))
        }

    /** The migration must not re-run and rewind counters spent since it ran. */
    @Test
    fun theLegacyMigrationDoesNotRewindLaterReservations() =
        runTest {
            val file = File(folder.root, "once.preferences_pb")
            val migration = { CopyOnceMigration("migrated.cashuCounters") { out -> out[longPreferencesKey("counter_keysetA")] = 10L } }

            val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            DataStoreCashuCounterStore(raw(file, firstScope, listOf(migration()))).reserve("keysetA", 5)
            firstScope.release()

            val secondScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val reopened = DataStoreCashuCounterStore(raw(file, secondScope, listOf(migration())))
            assertEquals(15L, reopened.peek("keysetA"))
            assertTrue("a later reservation is past everything spent", reopened.reserve("keysetA", 1) >= 15L)
            secondScope.release()
        }

    /** A host that wired no store must fail loudly rather than answer 0. */
    @Test
    fun theUnavailableStoreRefusesToAnswer() =
        runTest {
            val onPeek = runCatching { UnavailableCashuKeysetCounterStore.peek("keyset1") }.exceptionOrNull()
            val onReserve = runCatching { UnavailableCashuKeysetCounterStore.reserve("keyset1", 1) }.exceptionOrNull()

            assertTrue("peek must refuse, got $onPeek", onPeek is IllegalStateException)
            assertTrue("reserve must refuse, got $onReserve", onReserve is IllegalStateException)
        }
}
