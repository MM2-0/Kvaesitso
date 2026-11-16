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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.vitorpamplona.amethyst.service.cast.summarizeCastFormat
import com.vitorpamplona.amethyst.service.playback.PLAYBACK_DIAG_TAG
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import kotlin.math.ceil

internal fun getVideoTrackGroup(tracks: Tracks): Tracks.Group? = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 }

// Describes the video being played, for the Cast failure messages. Uses the highest-resolution
// track in the group: that is the one an adaptive stream will climb to, so it is the one a
// receiver with limited codec or resolution support will choke on.
@OptIn(UnstableApi::class)
internal fun castFormatSummary(group: Tracks.Group?): String? {
    if (group == null) return null
    var best: Format? = null
    for (i in 0 until group.length) {
        val format = group.getTrackFormat(i)
        if (best == null || format.width.toLong() * format.height > best.width.toLong() * best.height) {
            best = format
        }
    }
    val format = best ?: return null
    return summarizeCastFormat(format.sampleMimeType, format.codecs, format.width, format.height)
}

/**
 * Constrains adaptive selection to the area the player is actually drawn in.
 *
 * ExoPlayer already filters renditions by a viewport, but its default viewport is the **physical
 * display** (`TrackSelectionParameters.Builder.init` sets
 * `isViewportSizeLimitedByPhysicalDisplaySize`), so every player — a thumbnail in a note, a
 * full-bleed short, a PiP window — is told it has the whole screen to fill and picks whatever
 * bandwidth allows up to display resolution. Handing it the measured size instead is the whole
 * quality policy: a full-width video gets the top of the ladder, a small one gets the rung that
 * matches its pixels, and both still adapt to the connection underneath that ceiling.
 *
 * Safe by construction, which is why this uses `setViewportSize` rather than `setMaxVideoSize`:
 * DefaultTrackSelector derives its retain threshold from an actual rendition ("the smallest to
 * exceed the maximum size at which it can be displayed within the viewport") and leaves the group
 * untouched when no rendition covers the viewport, so a tiny player can never filter every track
 * away. A manual pick from the quality menu still wins — overrides are re-applied after
 * constraint-based selection runs.
 */
internal fun Modifier.constrainVideoQualityToViewport(
    player: Player,
    maxShortSidePx: () -> Int = { 0 },
): Modifier =
    onSizeChanged {
        // Evaluated at measure time, not at composition: a caller whose window is still resizing
        // (PiP) needs the answer for *this* layout pass. Always pushes something — a caller that
        // wants to distrust its own measurement caps it rather than skipping, so a pooled player
        // can never keep a viewport left over from the view that had it last.
        val (width, height) = clampViewportShortSide(it.width, it.height, maxShortSidePx())
        applyViewportConstraint(player, width, height)
    }

/**
 * Short side the viewport is capped to on a metered connection.
 *
 * Sizing the ladder to the player is the right default on wifi, but on mobile data it would hand a
 * full-width card most of the ladder with nothing holding it back — the app's other lever is the
 * autoplay `ConnectivityType` gate, which decides *whether* to play, not how much to pull. 480 on
 * the short side keeps a card watchable while staying near the rung the old fixed-lowest policy
 * would have picked.
 */
const val METERED_MAX_SHORT_SIDE_PX = 480

/**
 * Ceiling the PiP player uses until its window has actually shrunk.
 *
 * `processIntentForPiP` calls `enterPictureInPictureMode` from composition, so the first layout
 * pass can measure the activity at full screen. Capping rather than skipping the push means the
 * selector never sees that full-screen size, and never keeps a stale one either when PiP is never
 * entered at all — the window is a few inches wide, so nothing above this is ever wanted here.
 */
const val PIP_PRESHRINK_MAX_SHORT_SIDE_PX = 480

/**
 * Scales a measured player size down until its short side fits [maxShortSidePx], preserving aspect
 * so the viewport still describes the shape of the player and not just its area. Sizes already
 * within the cap, and a non-positive cap, pass through untouched.
 */
internal fun clampViewportShortSide(
    widthPx: Int,
    heightPx: Int,
    maxShortSidePx: Int,
): Pair<Int, Int> {
    val shortSide = minOf(widthPx, heightPx)
    if (maxShortSidePx <= 0 || shortSide <= 0 || shortSide <= maxShortSidePx) return widthPx to heightPx

    val scale = maxShortSidePx.toDouble() / shortSide
    // Round up so the cap is never undershot into a lower rung by a rounding artifact.
    return ceil(widthPx * scale).toInt() to ceil(heightPx * scale).toInt()
}

// Runs from onSizeChanged on the player's application looper (main thread), which is where
// trackSelectionParameters must be written. Writing them re-runs track selection and, for a
// pooled controller, crosses an IPC boundary, so [needsViewportUpdate] keeps a no-op layout pass
// from churning either.
internal fun applyViewportConstraint(
    player: Player,
    widthPx: Int,
    heightPx: Int,
) {
    val current = player.trackSelectionParameters
    if (!needsViewportUpdate(current.viewportWidth, current.viewportHeight, widthPx, heightPx)) return

    Log.d(VIDEO_QUALITY_TAG) {
        "viewport ${widthPx}x$heightPx mediaId=${player.currentMediaItem?.mediaId} " +
            "(was ${current.viewportWidth}x${current.viewportHeight})"
    }

    player.trackSelectionParameters =
        current
            .buildUpon()
            // orientationMayChange mirrors media3's own default: it keeps a landscape rendition in
            // a portrait box (a letterboxed live stream) from being judged against the short edge.
            .setViewportSize(widthPx, heightPx, true)
            .build()
}

/**
 * Logcat tag for the rendition trace: every viewport push, and the rung actually selected against
 * the ladder that was on offer. Replaces the per-media-item trace the old fixed-policy selector
 * emitted — without it, "Amethyst is eating my data" is not a debuggable report.
 *
 * ```
 * adb logcat -s VideoQuality
 * ```
 */
const val VIDEO_QUALITY_TAG = "VideoQuality"

/**
 * Traces which rendition adaptive selection landed on, against the full ladder the manifest
 * offered.
 *
 * The listener is registered only when the trace can actually be emitted. `Log.minLevel` is above
 * `DEBUG` in every variant by default — `INFO` for debug and benchmark builds, `WARN` for release
 * (see [PLAYBACK_DIAG_TAG]) — so this costs nothing until `Amethyst.VERBOSE_LOGS` is turned on,
 * and every build keeps the "no listener per player" property that dropping the old selector
 * bought.
 */
@Composable
fun LogVideoQualitySelection(player: Player) {
    if (Log.minLevel > LogLevel.DEBUG) return

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    val group = getVideoTrackGroup(tracks) ?: return
                    val ladder =
                        (0 until group.length).joinToString(", ") { i ->
                            val f = group.getTrackFormat(i)
                            "${f.width}x${f.height}${if (group.isTrackSelected(i)) "*" else ""}"
                        }
                    Log.d(VIDEO_QUALITY_TAG) {
                        "selected(*) mediaId=${player.currentMediaItem?.mediaId} of $ladder"
                    }
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}

// A zero dimension means the player has not been laid out yet — leaving the previous constraint
// in place is better than declaring a viewport nothing can fill.
internal fun needsViewportUpdate(
    currentWidth: Int,
    currentHeight: Int,
    newWidth: Int,
    newHeight: Int,
): Boolean = newWidth > 0 && newHeight > 0 && (currentWidth != newWidth || currentHeight != newHeight)

@OptIn(UnstableApi::class)
internal fun hasVideoOverride(player: Player): Boolean = player.trackSelectionParameters.overrides.any { (key, _) -> key.type == C.TRACK_TYPE_VIDEO }

internal fun clearVideoOverride(player: Player) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
}

@OptIn(UnstableApi::class)
internal fun selectVideoTrack(
    player: Player,
    group: Tracks.Group,
    trackIndex: Int,
) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
}
