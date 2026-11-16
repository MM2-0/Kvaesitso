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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MediaCaptureStateTest {
    private fun MediaCaptureState.change(block: MediaCaptureState.() -> Unit) {
        block()
        // Outside a composition nothing sends the apply notifications snapshotFlow listens for.
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun nothingLoadingWaitsForTheFloor() =
        runTest {
            val capture = MediaCaptureState()
            capture.awaitMediaLoaded(timeoutMs = 15_000, minMs = 900, quietMs = 400)
            assertEquals(900, currentTime)
        }

    @Test
    fun waitsForTheLastLoadPlusTheQuietWindow() =
        runTest {
            val capture = MediaCaptureState()
            capture.change { begin() }
            launch {
                delay(2_000)
                capture.change { end() }
            }
            capture.awaitMediaLoaded(timeoutMs = 15_000, minMs = 900, quietMs = 400)
            assertEquals(2_400, currentTime)
        }

    @Test
    fun aLoadStartingInTheQuietWindowIsWaitedFor() =
        runTest {
            val capture = MediaCaptureState()
            launch {
                // Like a link preview's image, which starts loading once its metadata arrives.
                delay(200)
                capture.change { begin() }
                delay(1_000)
                capture.change { end() }
            }
            capture.awaitMediaLoaded(timeoutMs = 15_000, minMs = 0, quietMs = 400)
            assertEquals(1_600, currentTime)
        }

    @Test
    fun aLoadThatNeverEndsHitsTheTimeout() =
        runTest {
            val capture = MediaCaptureState()
            capture.change { begin() }
            capture.awaitMediaLoaded(timeoutMs = 15_000, minMs = 900, quietMs = 400)
            assertEquals(15_000, currentTime)
        }
}
