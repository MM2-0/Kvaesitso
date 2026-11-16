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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common

import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelayListAutoSaverTest {
    private fun CoroutineScope.launcher(): (suspend () -> Unit) -> Unit = { save -> launch { runCatching { save() } } }

    @Test
    fun burstOfEditsFlushesOnceAfterTheyStop() =
        runTest {
            val saver = RelayListAutoSaver<List<String>>(backgroundScope)
            var flushes = 0

            repeat(5) {
                saver.schedule { flushes++ }
                advanceTimeBy(RelayListAutoSaver.DEBOUNCE_MS / 2)
            }
            assertEquals(0, flushes)
            assertFalse(saver.isIdle)

            advanceTimeBy(RelayListAutoSaver.DEBOUNCE_MS)
            runCurrent()
            assertEquals(1, flushes)
            assertTrue(saver.isIdle)
        }

    @Test
    fun cancelPendingDropsTheScheduledFlush() =
        runTest {
            val saver = RelayListAutoSaver<List<String>>(backgroundScope)
            var flushes = 0

            saver.schedule { flushes++ }
            saver.cancelPending()
            advanceTimeBy(RelayListAutoSaver.DEBOUNCE_MS * 2)
            runCurrent()

            assertEquals(0, flushes)
            assertTrue(saver.isIdle)
        }

    @Test
    fun listEqualToPersistedIsNotPublished() =
        runTest {
            val saver = RelayListAutoSaver<List<String>>(backgroundScope)
            saver.persisted = listOf("a", "b")
            val published = mutableListOf<List<String>>()

            // A drag dropped back where it started.
            saver.save(listOf("a", "b"), launcher()) { published.add(it) }
            runCurrent()
            assertEquals(emptyList(), published)

            saver.save(listOf("b", "a"), launcher()) { published.add(it) }
            runCurrent()
            assertEquals(listOf(listOf("b", "a")), published)
            assertEquals(listOf("b", "a"), saver.persisted)
        }

    @Test
    fun failedSaveKeepsPersistedSoTheNextEditRetries() =
        runTest {
            val saver = RelayListAutoSaver<List<String>>(backgroundScope)
            saver.persisted = listOf("a")

            saver.save(listOf("a", "b"), launcher()) { error("signer refused") }
            runCurrent()

            assertEquals(listOf("a"), saver.persisted)
            assertTrue(saver.isIdle)
        }

    @Test
    fun saveIsInFlightUntilItsPublishReturns() =
        runTest {
            val saver = RelayListAutoSaver<List<String>>(backgroundScope)
            val signed = CompletableDeferred<Unit>()

            saver.save(listOf("a"), launcher()) { signed.await() }
            runCurrent()
            assertFalse(saver.isIdle)

            signed.complete(Unit)
            runCurrent()
            assertTrue(saver.isIdle)
        }

    @Test
    fun consecutiveSavesNeverShareACreatedAtSecond() =
        runTest {
            val startedAt = mutableListOf<Long>()

            // Wall-clock seconds are what end up in created_at, so this one runs on real time.
            withContext(Dispatchers.Default) {
                val saver = RelayListAutoSaver<Int>(this)
                repeat(3) { i -> saver.save(i, launcher()) { startedAt.add(TimeUtils.now()) } }
            }

            assertEquals(3, startedAt.size)
            assertTrue(startedAt.zipWithNext().all { (a, b) -> b > a }, "$startedAt")
        }
}
