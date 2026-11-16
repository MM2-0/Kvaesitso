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
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TopNavFollowListStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var seq = 0

    private fun store(
        scope: CoroutineScope,
        migrations: List<DataMigration<Preferences>> = emptyList(),
    ): TopNavFollowListStore {
        val file = File(folder.root, "acct_${seq++}.preferences_pb")
        return TopNavFollowListStore(
            PreferenceDataStoreFactory.createWithPath(
                scope = scope,
                migrations = migrations,
                produceFile = { file.toOkioPath() },
            ),
        )
    }

    private fun scope() = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Encodes as the app does.
     *
     * The parameter type matters: kotlinx serializes by STATIC type, so
     * `encode(TopFilter.Global)` sees the concrete object and writes
     * `{}` with no polymorphic discriminator, which cannot be read back as a
     * TopFilter. Widening to TopFilter here is what the production call sites do
     * by reading a `StateFlow<TopFilter>.value`.
     */
    private fun encode(filter: TopFilter): String = JsonMapper.toJson(filter)

    /** An empty store must hand back each slot's documented default, not null and not a blanket Global. */
    @Test
    fun emptyStoreReturnsEachSlotsOwnDefault() =
        runTest {
            val loaded = store(scope()).load()

            assertEquals(FollowListSlot.entries.size, loaded.size)
            FollowListSlot.entries.forEach { slot ->
                assertEquals("default for ${slot.prefKey}", slot.default, loaded.getValue(slot))
            }
        }

    /** The defaults are not uniform — a regression that collapsed them would pass the test above. */
    @Test
    fun defaultsAreNotAllTheSame() {
        val defaults = FollowListSlot.entries.map { it.default }.toSet()

        assertTrue("expected several distinct defaults, got $defaults", defaults.size >= 4)
        assertEquals(TopFilter.AllFollows, FollowListSlot.HOME.default)
        assertEquals(TopFilter.Selected, FollowListSlot.NOTIFICATION.default)
        assertEquals(TopFilter.AroundMe, FollowListSlot.GEOCACHES.default)
        assertEquals(TopFilter.Mine, FollowListSlot.BADGES.default)
    }

    @Test
    fun savedValuesReadBack() =
        runTest {
            val subject = store(scope())

            subject.save(FollowListSlot.HOME, TopFilter.Global)

            assertEquals(TopFilter.Global, subject.load().getValue(FollowListSlot.HOME))
            assertEquals("other slots keep their defaults", TopFilter.Selected, subject.load().getValue(FollowListSlot.NOTIFICATION))
        }

    @Test
    fun saveAllWritesEverySlot() =
        runTest {
            val subject = store(scope())

            subject.saveAll(FollowListSlot.entries.associateWith { TopFilter.Mine })

            assertTrue(subject.load().values.all { it == TopFilter.Mine })
        }

    /** A corrupt entry costs that one feed's filter, not the whole account load. */
    @Test
    fun anUnparseableValueFallsBackToThatSlotsDefault() =
        runTest {
            val scope = scope()
            val file = File(folder.root, "corrupt.preferences_pb")
            val raw =
                PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { file.toOkioPath() })
            raw.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    this[FollowListSlot.HOME.key] = "{ not json"
                    this[FollowListSlot.STORIES.key] = encode(TopFilter.Mine)
                }
            }

            val loaded = TopNavFollowListStore(raw).load()

            assertEquals(FollowListSlot.HOME.default, loaded.getValue(FollowListSlot.HOME))
            assertEquals("a sibling slot still loads", TopFilter.Mine, loaded.getValue(FollowListSlot.STORIES))
        }

    /** The prefKeys are a compatibility surface: renaming one silently resets that feed for everyone. */
    @Test
    fun prefKeysAreTheOnesTheAndroidAppHasAlwaysWritten() {
        assertEquals("defaultHomeFollowList", FollowListSlot.HOME.prefKey)
        assertEquals("defaultNotificationFollowList", FollowListSlot.NOTIFICATION.prefKey)
        assertEquals("defaultAppRecommendationsFollowList", FollowListSlot.APP_RECOMMENDATIONS.prefKey)
        assertEquals("defaultChessFollowList", FollowListSlot.CHESS.prefKey)
        assertEquals(32, FollowListSlot.entries.size)
        assertEquals(
            "prefKeys must be unique",
            32,
            FollowListSlot.entries
                .map { it.prefKey }
                .toSet()
                .size,
        )
    }

    // ── migration ──────────────────────────────────────────────────────

    @Test
    fun migrationCopiesLegacyValuesOnFirstRead() =
        runTest {
            val legacy =
                mapOf(
                    Pair(FollowListSlot.HOME.key, encode(TopFilter.Global)),
                    Pair(FollowListSlot.BADGES.key, encode(TopFilter.AllFollows)),
                )

            val loaded = store(scope(), listOf(CopyOnceMigration("migrated.followLists") { out -> legacy.forEach { (k, v) -> out[k] = v } })).load()

            assertEquals(TopFilter.Global, loaded.getValue(FollowListSlot.HOME))
            assertEquals(TopFilter.AllFollows, loaded.getValue(FollowListSlot.BADGES))
            assertEquals("unmigrated slots keep defaults", FollowListSlot.STORIES.default, loaded.getValue(FollowListSlot.STORIES))
        }

    /**
     * The migration must not run a second time and overwrite what the user has
     * changed since — the marker key in the destination is what prevents it.
     *
     * Each open gets its own scope, cancelled before the next: DataStore
     * refuses two live instances over one file, which is exactly why
     * [AccountPreferenceStores] caches one per account.
     */
    @Test
    fun migrationDoesNotClobberLaterEdits() =
        runTest {
            val file = File(folder.root, "once.preferences_pb")
            var reads = 0
            val legacy = mapOf(Pair(FollowListSlot.HOME.key, encode(TopFilter.Global)))

            suspend fun <T> withStore(block: suspend (TopNavFollowListStore) -> T): T {
                val scope = scope()
                try {
                    return block(
                        TopNavFollowListStore(
                            PreferenceDataStoreFactory.createWithPath(
                                scope = scope,
                                migrations =
                                    listOf(
                                        CopyOnceMigration("migrated.followLists") { out ->
                                            reads++
                                            legacy.forEach { (k, v) -> out[k] = v }
                                        },
                                    ),
                                produceFile = { file.toOkioPath() },
                            ),
                        ),
                    )
                } finally {
                    // cancel() only asks. DataStore's registry keeps the entry until
                    // the owning job actually completes, so without this join the
                    // next open of the same file races it — which is what failed on
                    // a loaded CI runner while passing locally every time.
                    scope.cancel()
                    scope.coroutineContext.job.join()
                }
            }

            assertEquals(TopFilter.Global, withStore { it.load().getValue(FollowListSlot.HOME) })
            withStore { it.save(FollowListSlot.HOME, TopFilter.Mine) }

            assertEquals(
                "the user's later choice survives a reopen",
                TopFilter.Mine,
                withStore { it.load().getValue(FollowListSlot.HOME) },
            )
            assertEquals("the legacy store is read once", 1, reads)
        }
}
