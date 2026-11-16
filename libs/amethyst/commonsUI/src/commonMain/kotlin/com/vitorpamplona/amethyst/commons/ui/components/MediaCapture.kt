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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Present while a note is rendered only to be captured as a still image (share as image).
 *
 * Media views read it through [LocalMediaCapture] to:
 * - load images, link previews and video frames even when the data-saving settings would
 *   leave a download button, since nobody can tap that button on a picture;
 * - draw a video as a still frame instead of a player, whose surface a `GraphicsLayer`
 *   cannot record;
 * - report, with [ReportCaptureLoading], what is still loading so the capture can wait for it.
 */
@Stable
class MediaCaptureState {
    private val pending = mutableIntStateOf(0)

    fun begin() {
        pending.intValue++
    }

    fun end() {
        pending.intValue--
    }

    /**
     * Suspends until nothing has been loading for [quietMs] in a row and at least [minMs] have
     * passed, or [timeoutMs] passes. The quiet window covers loads that start only after another
     * finishes, like the image inside a link preview, and the first frames before a load
     * registers. The floor covers what doesn't report, like avatars and custom emoji.
     */
    suspend fun awaitMediaLoaded(
        timeoutMs: Long,
        minMs: Long = 900,
        quietMs: Long = 400,
    ) {
        withTimeoutOrNull(timeoutMs) {
            coroutineScope {
                val floor = launch { delay(minMs) }
                do {
                    snapshotFlow { pending.intValue }.first { it <= 0 }
                    delay(quietMs)
                } while (pending.intValue > 0)
                floor.join()
            }
        }
    }
}

val LocalMediaCapture = staticCompositionLocalOf<MediaCaptureState?> { null }

/** Marks the enclosing media as still loading for as long as this stays in the composition. */
@Composable
fun ReportCaptureLoading() {
    val capture = LocalMediaCapture.current ?: return
    DisposableEffect(capture) {
        capture.begin()
        onDispose { capture.end() }
    }
}

/**
 * For a plain `AsyncImage`: an `onState` that keeps the image reported as loading until it
 * succeeds or fails, while a capture is running. Null otherwise, so a feed pays for no extra
 * state or recomposition.
 */
@Composable
fun rememberCaptureLoadingTracker(key: Any?): ((AsyncImagePainter.State) -> Unit)? {
    if (LocalMediaCapture.current == null) return null
    var loading by remember(key) { mutableStateOf(true) }
    if (loading) ReportCaptureLoading()
    return remember(key) {
        { state ->
            when (state) {
                is AsyncImagePainter.State.Loading -> loading = true
                is AsyncImagePainter.State.Success, is AsyncImagePainter.State.Error -> loading = false
                else -> {}
            }
        }
    }
}
