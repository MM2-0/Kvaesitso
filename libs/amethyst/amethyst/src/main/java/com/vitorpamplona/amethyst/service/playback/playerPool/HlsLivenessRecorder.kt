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
package com.vitorpamplona.amethyst.service.playback.playerPool

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.vitorpamplona.amethyst.service.playback.PLAYBACK_DIAG_TAG
import com.vitorpamplona.amethyst.service.playback.diskCache.HlsLivenessCache
import com.vitorpamplona.quartz.utils.Log

/**
 * The verdict to store for an HLS URL given the player's current window, or null to store nothing.
 *
 * The recording is deliberately asymmetric. A wrong "live" only forgoes caching, while a wrong
 * "on-demand" caches a live stream and breaks it (PlaylistStuck), so live is cheap to record and
 * on-demand must be earned:
 *
 * - record **live** as soon as any window says so ([allowOnDemand] irrelevant), but
 * - record **on-demand** only when [allowOnDemand] (the player reached STATE_READY, i.e. it is
 *   actually playing, not a geo-blocked stream that served a VOD-shaped placeholder playlist and
 *   then errored), the window is a resolved static finite seekable VOD, and the URL is not already
 *   known live.
 *
 * Recording on-demand only at READY is what keeps a broken/geo-fenced live stream — which reports a
 * static window from a timeline event but 403/404s before it can ever reach READY — from being
 * mislearned as cacheable. Pure, so the classification is unit-testable without a [Player].
 */
internal fun livenessVerdictToRecord(
    isLive: Boolean,
    isDynamic: Boolean,
    isSeekable: Boolean,
    hasKnownDuration: Boolean,
    known: Boolean?,
    allowOnDemand: Boolean,
): Boolean? =
    when {
        isLive -> true
        !allowOnDemand -> null
        !isDynamic && isSeekable && hasKnownDuration && known != true -> false
        else -> null
    }

/**
 * Records into [HlsLivenessCache] whether the current HLS item is live, so [CustomMediaSourceFactory]
 * can cache proven on-demand HLS on the *next* play while never caching a live stream. The only
 * reliable live/on-demand discriminator (`#EXT-X-ENDLIST`) is inside the playlist, so it is knowable
 * only once ExoPlayer has loaded it — hence learning it here rather than from the URL.
 *
 * Only HLS items are considered; progressive media is unambiguous and never routed by liveness.
 */
class HlsLivenessRecorder(
    private val player: Player,
) : Player.Listener {
    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) = maybeRecord(allowOnDemand = false)

    override fun onPlaybackStateChanged(state: Int) {
        // Only a stream that actually plays (reached READY) may be recorded on-demand; a geo-blocked
        // stream that errors before READY must never be learned as cacheable. Live is still recorded
        // from the timeline above the moment the window says so.
        if (state == Player.STATE_READY) maybeRecord(allowOnDemand = true)
    }

    private fun maybeRecord(allowOnDemand: Boolean) {
        if (player.currentTimeline.isEmpty) return
        val mediaItem = player.currentMediaItem ?: return
        // Must be the same predicate CustomMediaSourceFactory routes on — see isHlsMediaItem for
        // what goes wrong when the two disagree.
        if (!isHlsMediaItem(mediaItem)) return
        val url = mediaItem.mediaId

        val known = HlsLivenessCache.verdict(url)
        val toRecord =
            livenessVerdictToRecord(
                isLive = player.isCurrentMediaItemLive,
                isDynamic = player.isCurrentMediaItemDynamic,
                isSeekable = player.isCurrentMediaItemSeekable,
                hasKnownDuration = player.contentDuration != C.TIME_UNSET,
                known = known,
                allowOnDemand = allowOnDemand,
            ) ?: return

        // Only write when the verdict actually changes: onTimelineChanged fires on every manifest
        // refresh of a live stream, and the verdict is stable once learned, so re-putting the same
        // value would take a ConcurrentHashMap bin lock on every callback for nothing.
        if (known != toRecord) {
            Log.d(PLAYBACK_DIAG_TAG) { "LIVENESS ${if (toRecord) "LIVE" else "ON-DEMAND"} learned for $url" }
            HlsLivenessCache.record(url, toRecord)
        }
    }
}
