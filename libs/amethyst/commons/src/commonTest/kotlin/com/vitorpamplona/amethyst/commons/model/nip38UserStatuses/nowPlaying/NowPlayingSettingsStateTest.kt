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
package com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingSettingsStateTest {
    private fun TestScope.collect(state: NowPlayingSettingsState): List<Boolean> {
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { state.sharesOtherApps().collect { seen.add(it) } }
        runCurrent()
        return seen
    }

    @Test
    fun saysNothingBeforeTheSavedSettingsAreRestored() =
        runTest {
            val state = NowPlayingSettingsState()
            val seen = collect(state)

            // The defaults have sharing off; reporting them would read as the user turning it off.
            assertEquals(emptyList(), seen)

            state.restore(NowPlayingSettings(shareOtherApps = true))
            runCurrent()
            assertEquals(listOf(true), seen)
        }

    @Test
    fun followsTheOtherAppsToggleOnceRestored() =
        runTest {
            val state = NowPlayingSettingsState()
            state.restore(NowPlayingSettings(shareOtherApps = false))
            val seen = collect(state)

            state.setShareOtherApps(true)
            runCurrent()
            state.setShareOtherApps(false)
            runCurrent()

            assertEquals(listOf(false, true, false), seen)
        }

    @Test
    fun aLaterRestoreIsReported() =
        runTest {
            val state = NowPlayingSettingsState()
            state.restore(NowPlayingSettings(shareOtherApps = false))
            val seen = collect(state)

            state.restore(NowPlayingSettings(shareOtherApps = true))
            runCurrent()

            assertEquals(listOf(false, true), seen)
        }

    @Test
    fun ignoresChangesThatDoNotTouchOtherApps() =
        runTest {
            val state = NowPlayingSettingsState()
            state.restore(NowPlayingSettings(shareOtherApps = true))
            val seen = collect(state)

            state.setShareInApp(true)
            state.rememberApp("com.example.player", "Player")
            state.setAppBlocked("com.example.player", true)
            runCurrent()

            assertEquals(listOf(true), seen)
        }
}
