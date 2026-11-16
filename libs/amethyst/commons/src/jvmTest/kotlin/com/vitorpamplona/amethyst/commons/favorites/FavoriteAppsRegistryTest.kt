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
package com.vitorpamplona.amethyst.commons.favorites

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The registry's disk lifecycle, which had no coverage while it was an Android-side `object` reaching
 * into `Amethyst.instance` for its store. Taking the [DataStore] as a constructor argument is what
 * makes these reachable.
 *
 * Every test drives the registry on the test scheduler, so "hydration has not finished yet" is a state
 * the test controls rather than races: [advanceUntilIdle] is the only thing that lets the coroutine
 * [FavoriteAppsRegistry.init] launches actually run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteAppsRegistryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var seq = 0

    private fun newFile() = File(folder.root, "favorite_apps_${seq++}.preferences_pb")

    private fun store(
        scope: CoroutineScope,
        file: File,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { file.toOkioPath() })

    /**
     * One app "session" over [file]. Each gets its own [Job] so the DataStore it opened is released
     * when the session is cancelled: DataStore refuses a second live instance on a path that already
     * has one, which is exactly why production keeps a single instance per file in the store holder.
     * A restart test that skipped this would read an empty store and look like data loss.
     */
    private fun TestScope.session(file: File): Pair<FavoriteAppsRegistry, CoroutineScope> {
        val scope = CoroutineScope(coroutineContext + Job())
        return FavoriteAppsRegistry(store(scope, file), scope) to scope
    }

    private fun web(
        url: String,
        label: String = url,
    ) = FavoriteApp.WebApp(url, label, addedAt = 1L)

    /** What the user actually notices: a favorite added in one session is there in the next. */
    @Test
    fun aFavoriteSurvivesARestart() =
        runTest {
            val file = newFile()

            val (first, firstScope) = session(file)
            first.init()
            advanceUntilIdle()
            first.add(web("https://example.com"))
            advanceUntilIdle()
            firstScope.cancel()

            val (second, _) = session(file)
            second.init()
            advanceUntilIdle()

            assertEquals(
                "the favorite written by the first session is what the second one hydrates",
                listOf("url:https://example.com"),
                second.favorites.value.map { it.id },
            )
        }

    /**
     * The tombstone. Removing between init() and the merge landing must win, or the disk copy
     * resurrects a favorite the user just deleted — and then persists it again.
     */
    @Test
    fun hydrationDoesNotResurrectAFavoriteRemovedBeforeItFinished() =
        runTest {
            val file = newFile()

            val (seeded, seededScope) = session(file)
            seeded.init()
            advanceUntilIdle()
            seeded.add(web("https://gone.example"))
            seeded.add(web("https://kept.example"))
            advanceUntilIdle()
            seededScope.cancel()

            val (reopened, _) = session(file)
            reopened.init()
            // Still inside the window: init() launched the merge but nothing has run it yet.
            reopened.remove("url:https://gone.example")
            advanceUntilIdle()

            assertEquals(
                "the removal beat the merge and must survive it",
                listOf("url:https://kept.example"),
                reopened.favorites.value.map { it.id },
            )
        }

    /** The other side of the same window: an add made before the merge must not be dropped by it. */
    @Test
    fun hydrationKeepsAnAddMadeBeforeItFinished() =
        runTest {
            val file = newFile()

            val (seeded, seededScope) = session(file)
            seeded.init()
            advanceUntilIdle()
            seeded.add(web("https://ondisk.example"))
            advanceUntilIdle()
            seededScope.cancel()

            val (reopened, _) = session(file)
            reopened.init()
            reopened.add(web("https://thissession.example"))
            advanceUntilIdle()

            assertEquals(
                "both the disk copy and the pre-merge add are present",
                setOf("url:https://ondisk.example", "url:https://thissession.example"),
                reopened.favorites.value
                    .map { it.id }
                    .toSet(),
            )
        }

    /**
     * Persistence is gated on init(). Without the gate a write in that window flushes a list that has
     * not merged with disk yet, which is the stored list being replaced by a partial one.
     */
    @Test
    fun nothingIsWrittenBeforeInit() =
        runTest {
            val file = newFile()

            val (registry, writerScope) = session(file)
            registry.add(web("https://notpersisted.example"))
            advanceUntilIdle()

            assertEquals(
                "the add is live in memory",
                listOf("url:https://notpersisted.example"),
                registry.favorites.value.map { it.id },
            )
            writerScope.cancel()
            advanceUntilIdle()
            val readerScope = CoroutineScope(coroutineContext + Job())
            assertNull(
                "but nothing reached disk, because init() never ran",
                store(readerScope, file).data.first()[stringPreferencesKey("favorites")],
            )
            readerScope.cancel()
        }

    /** A favorite's cached manifest must not outlive the favorite. */
    @Test
    fun removingANostrFavoriteDropsItsCachedManifest() =
        runTest {
            val (registry, _) = session(newFile())
            registry.init()
            advanceUntilIdle()

            val coordinate = "31990:pubkey:slug"
            registry.add(FavoriteApp.NostrApp(coordinate, "An App", addedAt = 1L))
            registry.cacheManifest(coordinate, """{"kind":31990}""")
            assertEquals("cached while favorited", """{"kind":31990}""", registry.cachedManifest(coordinate))

            registry.remove("nostr:$coordinate")
            advanceUntilIdle()

            assertNull("and gone with the favorite", registry.cachedManifest(coordinate))
        }

    /** Garbage on disk must degrade to an empty list, never take the launcher down with it. */
    @Test
    fun aCorruptStoredListHydratesAsEmpty() =
        runTest {
            val file = newFile()
            val seedScope = CoroutineScope(coroutineContext + Job())
            store(seedScope, file).edit { it[stringPreferencesKey("favorites")] = "}not json[" }
            advanceUntilIdle()
            seedScope.cancel()
            advanceUntilIdle()

            val (registry, _) = session(file)
            registry.init()
            advanceUntilIdle()

            assertTrue("decode failed softly", registry.favorites.value.isEmpty())
        }

    /** Ids are the identity: adding the same app twice is a no-op, not a duplicate row. */
    @Test
    fun addingTheSameAppTwiceKeepsOneEntry() =
        runTest {
            val (registry, _) = session(newFile())
            registry.init()
            advanceUntilIdle()

            registry.add(web("https://example.com", "First"))
            registry.add(web("https://example.com", "Second"))

            assertEquals("de-duplicated by id", 1, registry.favorites.value.size)
            assertEquals(
                "and the first one won",
                "First",
                registry.favorites.value
                    .single()
                    .label,
            )
        }
}
