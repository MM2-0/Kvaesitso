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
package com.vitorpamplona.amethyst.commons.browser

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The visit history's ranking inputs and its bound, neither of which had coverage while this was an
 * Android-side `object`. The omnibox ranks on [BrowserHistoryEntry.visitCount] and
 * [BrowserHistoryEntry.lastVisitedAt], so a bump that does not bump is a silently wrong suggestion
 * order rather than a crash.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserHistoryRegistryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var seq = 0

    private fun newFile() = File(folder.root, "browser_history_${seq++}.preferences_pb")

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
    private fun TestScope.session(file: File): Pair<BrowserHistoryRegistry, CoroutineScope> {
        val scope = CoroutineScope(coroutineContext + Job())
        return BrowserHistoryRegistry(store(scope, file), scope) to scope
    }

    /** A revisit is a bump, not a second row — this is what makes frecency mean anything. */
    @Test
    fun revisitingAUrlBumpsTheCountInsteadOfAddingARow() =
        runTest {
            val (registry, _) = session(newFile())
            registry.init()
            advanceUntilIdle()

            registry.record("https://example.com/a", "A")
            registry.record("https://example.com/a", "A again")

            assertEquals("one row for one url", 1, registry.history.value.size)
            assertEquals(
                "visit count bumped",
                2,
                registry.history.value
                    .single()
                    .visitCount,
            )
            assertEquals(
                "title refreshed",
                "A again",
                registry.history.value
                    .single()
                    .title,
            )
        }

    /** A page that finishes loading with no <title> must not blank out the name the user recognises. */
    @Test
    fun aBlankTitleOnARevisitKeepsTheOldOne() =
        runTest {
            val (registry, _) = session(newFile())
            registry.init()
            advanceUntilIdle()

            registry.record("https://example.com/a", "Real Title")
            registry.record("https://example.com/a", "   ")

            assertEquals(
                "the blank did not overwrite it",
                "Real Title",
                registry.history.value
                    .single()
                    .title,
            )
        }

    /** Most recent first, because that is the order the omnibox shows them in. */
    @Test
    fun theMostRecentVisitLeads() =
        runTest {
            val (registry, _) = session(newFile())
            registry.init()
            advanceUntilIdle()

            registry.record("https://first.example", "First")
            registry.record("https://second.example", "Second")

            assertEquals(
                "newest at the front",
                listOf("https://second.example", "https://first.example"),
                registry.history.value.map { it.url },
            )
        }

    /** The bound is what stops an unbounded JSON blob being rewritten on every page load. */
    @Test
    fun historyIsCappedAtFiveHundredEntries() =
        runTest {
            val (registry, _) = session(newFile())
            registry.init()
            advanceUntilIdle()

            repeat(505) { registry.record("https://example.com/page$it", "Page $it") }

            assertEquals("capped", 500, registry.history.value.size)
            assertEquals(
                "and it is the oldest that fell off",
                "https://example.com/page504",
                registry.history.value
                    .first()
                    .url,
            )
            assertTrue("page0 is gone", registry.history.value.none { it.url == "https://example.com/page0" })
        }

    @Test
    fun historySurvivesARestart() =
        runTest {
            val file = newFile()

            val (first, firstScope) = session(file)
            first.init()
            advanceUntilIdle()
            first.record("https://example.com/a", "A")
            advanceUntilIdle()
            firstScope.cancel()

            val (second, _) = session(file)
            second.init()
            advanceUntilIdle()

            assertEquals("hydrated from disk", listOf("https://example.com/a"), second.history.value.map { it.url })
            assertEquals(
                "with its count",
                1,
                second.history.value
                    .single()
                    .visitCount,
            )
        }

    /** Clearing is a privacy action: it has to reach disk, not just the in-memory flow. */
    @Test
    fun clearingEmptiesTheStoredHistoryToo() =
        runTest {
            val file = newFile()

            val (first, firstScope) = session(file)
            first.init()
            advanceUntilIdle()
            first.record("https://example.com/a", "A")
            advanceUntilIdle()
            first.clear()
            advanceUntilIdle()
            firstScope.cancel()

            val (second, _) = session(file)
            second.init()
            advanceUntilIdle()

            assertTrue("nothing came back", second.history.value.isEmpty())
        }

    @Test
    fun removingOneUrlLeavesTheRest() =
        runTest {
            val (registry, _) = session(newFile())
            registry.init()
            advanceUntilIdle()

            registry.record("https://keep.example", "Keep")
            registry.record("https://drop.example", "Drop")
            registry.remove("https://drop.example")

            assertEquals("only the one", listOf("https://keep.example"), registry.history.value.map { it.url })
        }

    /**
     * One DuckDuckGo search left three rows: the typed `%20` query, the `+` query the page rewrote it to,
     * and the `&ia=web` it rewrote that to. Each rewrite replaced the page in place, so it is one visit.
     */
    @Test
    fun aPageReplacedInPlaceIsOneVisit() =
        runTest {
            val (registry, _) = session(newFile())
            registry.init()
            advanceUntilIdle()

            registry.record("https://duckduckgo.com/?q=nostr%20protocol", "DuckDuckGo")
            registry.record("https://duckduckgo.com/?q=nostr+protocol", "DuckDuckGo", replaces = "https://duckduckgo.com/?q=nostr%20protocol")
            registry.record("https://duckduckgo.com/?q=nostr+protocol&ia=web", "nostr protocol at DuckDuckGo", replaces = "https://duckduckgo.com/?q=nostr+protocol")

            assertEquals(
                "only the landed url",
                listOf("https://duckduckgo.com/?q=nostr+protocol&ia=web"),
                registry.history.value.map { it.url },
            )
            assertEquals(
                "one visit",
                1,
                registry.history.value
                    .single()
                    .visitCount,
            )
        }

    /** A page visited before keeps its row when a later visit to it is replaced — it only loses that visit. */
    @Test
    fun replacingAnEarlierVisitedPageOnlyTakesBackOneVisit() =
        runTest {
            val (registry, _) = session(newFile())
            registry.init()
            advanceUntilIdle()

            registry.record("https://example.com/a", "A")
            registry.record("https://example.com/a", "A")
            registry.record("https://example.com/b", "B", replaces = "https://example.com/a")

            val byUrl = registry.history.value.associate { it.url to it.visitCount }
            assertEquals("a keeps its first visit", 1, byUrl["https://example.com/a"])
            assertEquals("b got the replaced one", 1, byUrl["https://example.com/b"])
        }

    /** A reload reports the page as replacing itself; that must not cost it a visit. */
    @Test
    fun replacingAPageWithItselfIsAPlainRevisit() =
        runTest {
            val (registry, _) = session(newFile())
            registry.init()
            advanceUntilIdle()

            registry.record("https://example.com/a", "A")
            registry.record("https://example.com/a", "A", replaces = "https://example.com/a")

            assertEquals(
                "still counted",
                2,
                registry.history.value
                    .single()
                    .visitCount,
            )
        }
}
