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
package com.vitorpamplona.amethyst.commons.model

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SyncedSettingsPublishesTest {
    private val settings = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val feedFilters = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Collects the publish ticks; returns a reader for how many have fired. */
    private fun TestScope.publishes(): () -> Int {
        var count = 0
        backgroundScope.launch { syncedSettingsPublishes(settings, feedFilters).collect { count++ } }
        runCurrent()
        return { count }
    }

    @Test
    fun aSettingPublishesASecondAfterTheLastChange() =
        runTest {
            val count = publishes()
            settings.emit(Unit)
            advanceTimeBy(500)
            settings.emit(Unit)
            advanceTimeBy(999)
            assertEquals(0, count())
            advanceTimeBy(2)
            assertEquals(1, count())
        }

    @Test
    fun feedSwitchesWaitThirtySecondsAndPublishOnce() =
        runTest {
            val count = publishes()
            repeat(5) {
                feedFilters.emit(Unit)
                advanceTimeBy(10_000)
            }
            assertEquals(0, count())
            advanceTimeBy(20_001)
            assertEquals(1, count())
        }

    @Test
    fun aFeedSwitchDoesNotHoldBackASettingChangedJustBefore() =
        runTest {
            val count = publishes()
            settings.emit(Unit)
            advanceTimeBy(200)
            feedFilters.emit(Unit)
            advanceTimeBy(1_001)
            assertEquals(1, count())
            // Nothing left over: the feed switch went out with the setting.
            advanceTimeBy(60_000)
            assertEquals(1, count())
        }

    @Test
    fun aPendingFeedSwitchRidesAlongWithTheNextSetting() =
        runTest {
            val count = publishes()
            feedFilters.emit(Unit)
            advanceTimeBy(5_000)
            settings.emit(Unit)
            advanceTimeBy(1_001)
            assertEquals(1, count())
            advanceTimeBy(60_000)
            assertEquals(1, count())
        }
}
