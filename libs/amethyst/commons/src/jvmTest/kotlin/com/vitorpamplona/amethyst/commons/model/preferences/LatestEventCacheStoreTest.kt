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
package com.vitorpamplona.amethyst.commons.model.preferences

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LatestEventCacheStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var seq = 0

    private fun store(migrations: List<DataMigration<Preferences>> = emptyList()): LatestEventCacheStore {
        val file = File(folder.root, "cache_${seq++}.preferences_pb")
        return LatestEventCacheStore(
            PreferenceDataStoreFactory.createWithPath(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                migrations = migrations,
                produceFile = { file.toOkioPath() },
            ),
        )
    }

    /** An absent slot means nothing was cached — not an empty string that would fail to parse. */
    @Test
    fun anEmptyStoreReturnsNoSlots() =
        runTest {
            assertTrue(store().load().isEmpty())
        }

    @Test
    fun savedJsonReadsBackVerbatim() =
        runTest {
            val subject = store()
            val json = """{"id":"abc","kind":0,"content":"{}"}"""

            subject.saveAll(mapOf(LatestEventSlot.USER_METADATA to json))

            assertEquals(json, subject.load()[LatestEventSlot.USER_METADATA])
        }

    /**
     * A null must remove the key. Leaving the previous value behind would keep
     * serving an event the account no longer has.
     */
    @Test
    fun aNullValueRemovesTheSlot() =
        runTest {
            val subject = store()
            subject.saveAll(mapOf(LatestEventSlot.MUTE_LIST to """{"kind":10000}"""))

            subject.saveAll(mapOf(LatestEventSlot.MUTE_LIST to null))

            assertFalse(subject.load().containsKey(LatestEventSlot.MUTE_LIST))
        }

    @Test
    fun slotsAreIndependent() =
        runTest {
            val subject = store()

            subject.saveAll(
                mapOf(
                    LatestEventSlot.CONTACT_LIST to """{"kind":3}""",
                    LatestEventSlot.MUTE_LIST to null,
                ),
            )

            assertEquals("""{"kind":3}""", subject.load()[LatestEventSlot.CONTACT_LIST])
            assertFalse(subject.load().containsKey(LatestEventSlot.MUTE_LIST))
        }

    @Test
    fun prefKeysMatchWhatTheAppHasAlwaysWritten() {
        assertEquals("latestUserMetadata", LatestEventSlot.USER_METADATA.prefKey)
        assertEquals("latestContactList", LatestEventSlot.CONTACT_LIST.prefKey)
        assertEquals("latestNIP65RelayList", LatestEventSlot.NIP65_RELAY_LIST.prefKey)
        assertEquals("latestConcordListFragments", LatestEventSlot.CONCORD_LIST_FRAGMENTS.prefKey)
        assertEquals(27, LatestEventSlot.entries.size)
        assertEquals(
            "prefKeys must be unique",
            27,
            LatestEventSlot.entries
                .map { it.prefKey }
                .toSet()
                .size,
        )
    }

    @Test
    fun migrationCopiesTheLegacyCache() =
        runTest {
            val legacy = mapOf(Pair(LatestEventSlot.USER_METADATA.key, """{"kind":0}"""))

            val loaded = store(listOf(CopyOnceMigration("migrated.latestEvents") { out -> legacy.forEach { (k, v) -> out[k] = v } })).load()

            assertEquals("""{"kind":0}""", loaded[LatestEventSlot.USER_METADATA])
            assertFalse("slots absent from the legacy store stay absent", loaded.containsKey(LatestEventSlot.MUTE_LIST))
        }
}
