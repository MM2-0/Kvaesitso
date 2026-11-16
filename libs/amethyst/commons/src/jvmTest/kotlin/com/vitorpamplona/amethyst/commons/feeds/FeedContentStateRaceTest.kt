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
package com.vitorpamplona.amethyst.commons.feeds

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.StubCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [FeedContentState]'s writers — the insert bundler, the full refresh, deletions and the
 * memory-pressure trim — run on different IO coroutines and each read the list, transform it
 * and write it back. Unserialized, a deletion that landed while an insert batch was merging was
 * overwritten by the insert's stale copy, and the deleted note stayed in the feed until the
 * next full rebuild. The merge is slow, so the fix must not make a deletion wait for it either.
 */
class FeedContentStateRaceTest {
    private val a = Note("a".repeat(64))
    private val b = Note("b".repeat(64))
    private val deleted = Note("d".repeat(64))

    /** Holds the merge open until the test lets it go, so the deletion lands mid-merge. */
    private class SlowMergeFilter(
        private val initial: List<Note>,
    ) : AdditiveFeedFilter<Note>() {
        val merging = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun feedKey(): Any = "feed"

        override fun feed(): List<Note> = initial

        override fun applyFilter(newItems: Set<Note>): Set<Note> {
            merging.countDown()
            release.await(5, TimeUnit.SECONDS)
            return newItems
        }

        override fun sort(items: Set<Note>): List<Note> = items.sortedBy { it.idHex }
    }

    @Test
    fun aDeletionDuringAnInsertMergeIsNotUndone() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val filter = SlowMergeFilter(listOf(a, deleted))
            val state = FeedContentState(filter, scope, StubCache())
            state.refreshSuspended()
            assertEquals(listOf(a, deleted), state.visibleNotes())

            val insert = thread { state.refreshFromOldState(setOf(b)) }
            assertTrue(filter.merging.await(5, TimeUnit.SECONDS), "the insert never started merging")

            val delete = thread { state.deleteFromFeed(setOf(deleted)) }
            delete.join(2_000)
            assertTrue(!delete.isAlive, "the deletion waited for the merge")
            assertEquals(listOf(a), state.visibleNotes(), "the deletion did not show until the merge ended")

            filter.release.countDown()
            insert.join(5_000)

            assertEquals(listOf(a, b), state.visibleNotes())
        } finally {
            scope.cancel()
        }
    }

    /** Holds the full rebuild open, the slowest writer, so the deletion lands mid-rebuild. */
    private class SlowLoadFilter(
        private val all: List<Note>,
    ) : AdditiveFeedFilter<Note>() {
        @Volatile var slow = false
        val loading = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun feedKey(): Any = "feed"

        override fun feed(): List<Note> {
            if (slow) {
                loading.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
            return all
        }

        override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems

        override fun sort(items: Set<Note>): List<Note> = items.sortedBy { it.idHex }
    }

    @Test
    fun aDeletionDuringAFullRefreshIsNotUndoneNorDelayed() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val filter = SlowLoadFilter(listOf(a, deleted))
            val state = FeedContentState(filter, scope, StubCache())
            state.refreshSuspended()

            filter.slow = true
            val refresh = thread { state.refreshSuspended() }
            assertTrue(filter.loading.await(5, TimeUnit.SECONDS), "the refresh never started loading")

            val delete = thread { state.deleteFromFeed(setOf(deleted)) }
            delete.join(2_000)
            assertTrue(!delete.isAlive, "the deletion waited for the refresh")

            // The rebuild read the cache before the deletion, so its copy still holds the note.
            filter.release.countDown()
            refresh.join(5_000)

            assertEquals(listOf(a), state.visibleNotes())
        } finally {
            scope.cancel()
        }
    }
}
