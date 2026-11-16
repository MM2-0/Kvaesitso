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
package com.vitorpamplona.amethyst.service.playback.composable.mainVideo

import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import java.lang.ref.WeakReference
import kotlin.math.abs

/**
 * Tracking entry pairs the [MediaControllerState] with its active flag so the
 * mutex can update the loser/winner directly without a secondary lookup.
 */
private class TrackedVideo(
    val controller: MediaControllerState,
    val active: MutableState<Boolean>,
)

// Process-wide list of currently registered videos. Compose callbacks run on
// the main thread so no synchronization is required. ArrayList beats HashSet
// here because the only iteration is the rare re-election after the winner
// disappears; adds/removes happen only when entering/leaving composition.
private val trackingVideos = ArrayList<TrackedVideo>()

// The closest video. Maintained as a single-winner cache so each scroll-frame
// position update is O(1) instead of O(N) per video (O(N^2) total).
private var winner: TrackedVideo? = null

// Cached result of view.getGlobalVisibleRect. The compose root rarely changes
// across a scroll burst; caching avoids N native calls per frame.
// The view is held weakly: it is only used for an identity check, never
// dereferenced, and a strong static reference would leak the Activity.
private val cachedRootRect = Rect()
private var cachedRootRectView: WeakReference<View>? = null
private var cachedRootRectVisible: Boolean = false
private var cachedRootRectTimeNs: Long = 0L
private const val ROOT_RECT_CACHE_TTL_NS = 8_000_000L // ~half a frame at 60fps

/**
 * This function selects only one Video to be active. The video that is closest to the center of the
 * screen wins the mutex.
 */
@Composable
fun VideoPlayerActiveMutex(
    controller: MediaControllerState,
    inner: @Composable (Modifier, MutableState<Boolean>) -> Unit,
) {
    // Is the current video the closest to the center?
    val isClosestToTheCenterOfTheScreen = remember(controller) { mutableStateOf(false) }

    val tracked =
        remember(controller, isClosestToTheCenterOfTheScreen) {
            TrackedVideo(controller, isClosestToTheCenterOfTheScreen)
        }

    // Keep track of all available videos.
    DisposableEffect(key1 = tracked) {
        trackingVideos.add(tracked)
        onDispose {
            trackingVideos.remove(tracked)
            tracked.controller.visibility.distanceToCenter = null
            if (winner === tracked) {
                winner = null
                electNewWinner()
            }
        }
    }

    val view = LocalView.current

    val videoModifier =
        remember(tracked, view) {
            Modifier.fillMaxWidth().heightIn(min = 120.dp).onGloballyPositioned { coordinates ->
                if (!coordinates.isAttached) {
                    reportPosition(tracked, null)
                    return@onGloballyPositioned
                }
                val bounds = coordinates.boundsInWindow()
                val left = bounds.left.toInt()
                val top = bounds.top.toInt()
                val right = bounds.right.toInt()
                val bottom = bounds.bottom.toInt()
                tracked.controller.visibility.setBounds(left, top, right, bottom)
                reportPosition(tracked, distanceToCenter(view, left, top, right, bottom))
            }
        }

    inner(videoModifier, isClosestToTheCenterOfTheScreen)
}

/**
 * Single-winner update path. Called from onGloballyPositioned during scroll.
 *
 * Cost is O(1) for every position update except when the current winner becomes
 * invisible — in that case we re-elect (O(N)). This brings the per-frame cost
 * down from O(N^2) to O(N).
 */
private fun reportPosition(
    tracked: TrackedVideo,
    distanceToCenter: Float?,
) {
    val previous = tracked.controller.visibility.distanceToCenter
    if (previous == distanceToCenter) return // nothing changed

    tracked.controller.visibility.distanceToCenter = distanceToCenter

    if (distanceToCenter == null) {
        // Became invisible. Only meaningful work if this video was the winner.
        if (winner === tracked) {
            tracked.active.value = false
            winner = null
            electNewWinner()
        }
        return
    }

    val current = winner
    when {
        current === tracked -> {
            // Still the winner; distance was already updated above.
        }

        current == null -> {
            // No winner yet — claim it.
            winner = tracked
            tracked.active.value = true
        }

        else -> {
            val currentDistance = current.controller.visibility.distanceToCenter
            if (currentDistance == null || distanceToCenter < currentDistance) {
                // Dethrone the current winner.
                current.active.value = false
                winner = tracked
                tracked.active.value = true
            }
        }
    }
}

private fun electNewWinner() {
    var bestDistance = Float.MAX_VALUE
    var best: TrackedVideo? = null
    val list = trackingVideos
    for (i in list.indices) {
        val tv = list[i]
        val d = tv.controller.visibility.distanceToCenter ?: continue
        if (d < bestDistance) {
            bestDistance = d
            best = tv
        }
    }
    if (best != null) {
        winner = best
        best.active.value = true
    }
}

/**
 * Returns the cached visible-rect of the root view if it was computed within
 * the last frame, otherwise refreshes the cache. The Rect is shared and must
 * be treated as read-only by callers.
 */
private fun cachedGlobalVisibleRect(view: View): Rect? {
    val now = System.nanoTime()
    if (cachedRootRectView?.get() === view && (now - cachedRootRectTimeNs) < ROOT_RECT_CACHE_TTL_NS) {
        return if (cachedRootRectVisible) cachedRootRect else null
    }
    cachedRootRectVisible = view.getGlobalVisibleRect(cachedRootRect)
    cachedRootRectView = WeakReference(view)
    cachedRootRectTimeNs = now
    return if (cachedRootRectVisible) cachedRootRect else null
}

private fun distanceToCenter(
    view: View,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
): Float? {
    if (right <= left || bottom <= top) return null

    val rootRect = cachedGlobalVisibleRect(view) ?: return null

    // Make sure we are completely in bounds.
    if (top < rootRect.top || left < rootRect.left || right > rootRect.right || bottom > rootRect.bottom) {
        return null
    }

    return abs(((top + bottom) / 2.0f) - ((rootRect.top + rootRect.bottom) / 2.0f))
}
