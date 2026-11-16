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
package com.vitorpamplona.amethyst.ui.components

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.Window
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import coil3.BitmapImage
import coil3.Image
import com.vitorpamplona.amethyst.commons.ui.components.getActivityWindow
import com.vitorpamplona.amethyst.commons.ui.components.getDialogWindow
import com.vitorpamplona.amethyst.service.playback.composable.rememberTracksState
import java.util.WeakHashMap

/**
 * The images on one window that currently want HDR, and how much headroom each asks for.
 *
 * An Ultra HDR photo (a JPEG with a gain map) only renders brighter than SDR white while its
 * window is in [ActivityInfo.COLOR_MODE_HDR]; otherwise the platform silently draws the SDR base
 * image. The window is shared by every card in a feed, so HDR stays on until the last image
 * asking for it leaves composition, and then the window gets its [originalColorMode] back.
 */
class HdrRequests(
    val originalColorMode: Int,
) {
    private val headrooms = mutableListOf<Float>()

    val wantsHdr: Boolean get() = headrooms.isNotEmpty()

    /** [UNCAPPED] if any request is uncapped or there are none, else the largest cap asked for. */
    val headroom: Float get() = if (UNCAPPED in headrooms) UNCAPPED else headrooms.maxOrNull() ?: UNCAPPED

    fun add(headroom: Float) {
        headrooms.add(headroom)
    }

    /** Drops one request for [headroom]; equal requests are interchangeable. */
    fun remove(headroom: Float) {
        headrooms.remove(headroom)
    }

    companion object {
        /** `Window.setDesiredHdrHeadroom`'s "no preference": the display's full HDR range. */
        const val UNCAPPED = 0f

        /**
         * Feed cards share the screen with SDR text and chrome, which full HDR brightness makes
         * look dim and grey. Cap them; the fullscreen viewer asks for [UNCAPPED].
         */
        const val FEED_HEADROOM = 2f
    }
}

// Main-thread only: composition and disposal both run there.
private val windowRequests = WeakHashMap<Window, HdrRequests>()

// Each setter dispatches the window attributes to the window manager even when the value is
// unchanged, and cards scroll in and out of a feed constantly: only write what actually changed.
private fun Window.applyHdr(requests: HdrRequests) {
    val mode = if (requests.wantsHdr) ActivityInfo.COLOR_MODE_HDR else requests.originalColorMode
    if (colorMode != mode) colorMode = mode
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        if (desiredHdrHeadroom != requests.headroom) desiredHdrHeadroom = requests.headroom
    }
}

fun Image.hasGainmap(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && (this as? BitmapImage)?.bitmap?.hasGainmap() == true

/**
 * Whether the video track the player is actually rendering carries an HDR transfer (HLG or PQ).
 * Only selected tracks count: an adaptive stream can offer an HDR rendition the selector skipped.
 */
@OptIn(UnstableApi::class)
fun Tracks.hasSelectedHdrVideo(): Boolean =
    groups.any { group ->
        group.type == C.TRACK_TYPE_VIDEO &&
            (0 until group.length).any { group.isTrackSelected(it) && ColorInfo.isTransferHdr(group.getTrackFormat(it).colorInfo) }
    }

/**
 * Puts the hosting window (the dialog's own window inside a `Dialog`) into HDR mode while this
 * is in composition and [image] carries a gain map, so an Ultra HDR photo shows its highlights.
 * A [fullscreen] image gets the display's full HDR range; a feed card is capped.
 */
@Composable
fun RequestHdrFor(
    image: Image,
    fullscreen: Boolean,
) = RequestHdr(wantsHdr = remember(image) { image.hasGainmap() }, fullscreen = fullscreen)

/**
 * Puts the hosting window into HDR mode while the video track [player] renders carries an HDR
 * transfer (HLG or PQ), following quality switches and item changes. Only worth calling when the
 * player draws into a `SurfaceView`: a `TextureView`'s GL copy flattens HDR frames to SDR, so the
 * window mode would change and the picture would not. That is the fullscreen viewer, on its own
 * window, so the request is for the display's full HDR range.
 */
@Composable
fun RequestHdrFor(player: Player) {
    val wantsHdr by rememberTracksState(player) { it.hasSelectedHdrVideo() }
    RequestHdr(wantsHdr = wantsHdr, fullscreen = true)
}

/**
 * The shared core: holds one HDR request on the hosting window while in composition and
 * [wantsHdr]; the window goes back to its original mode once the last request leaves.
 */
@Composable
private fun RequestHdr(
    wantsHdr: Boolean,
    fullscreen: Boolean,
) {
    if (!wantsHdr) return
    val window = getDialogWindow() ?: getActivityWindow() ?: return
    val headroom = if (fullscreen) HdrRequests.UNCAPPED else HdrRequests.FEED_HEADROOM

    DisposableEffect(window, headroom) {
        val requests = windowRequests.getOrPut(window) { HdrRequests(window.colorMode) }
        requests.add(headroom)
        window.applyHdr(requests)

        onDispose {
            requests.remove(headroom)
            window.applyHdr(requests)
            if (!requests.wantsHdr) windowRequests.remove(window)
        }
    }
}
