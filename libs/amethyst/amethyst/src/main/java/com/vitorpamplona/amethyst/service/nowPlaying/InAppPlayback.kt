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
package com.vitorpamplona.amethyst.service.nowPlaying

import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.Player
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlaying
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemCache
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

/** What one of the app's players is audibly playing: its item's `nostr:` URI and where it ends. */
@Immutable
data class InAppPlayback(
    val callbackUri: String,
    val title: String?,
    val artist: String?,
    val endsAt: Long?,
) {
    /** Same item, ending within a few seconds: a position tick rather than a change. */
    fun isSameMoment(other: InAppPlayback?): Boolean {
        if (other == null || callbackUri != other.callbackUri || title != other.title || artist != other.artist) return false
        val a = endsAt
        val b = other.endsAt
        return if (a == null || b == null) a == b else abs(a - b) <= NowPlaying.MOMENT_TOLERANCE_SECONDS
    }
}

/**
 * The players in this process that are audibly playing something with a Nostr event behind it,
 * keyed by player. Fed by [InAppPlaybackListener]; read by the now-playing coordinator, which
 * resolves the URI to a music track or podcast episode and ignores everything else.
 */
object InAppPlaybackRegistry {
    private val playing = MutableStateFlow<Map<Int, InAppPlayback>>(emptyMap())

    val flow: StateFlow<Map<Int, InAppPlayback>> = playing

    fun update(
        player: Player,
        playback: InAppPlayback?,
    ) {
        val key = System.identityHashCode(player)
        playing.update { current ->
            val previous = current[key]
            when {
                playback == null -> if (previous != null) current - key else current
                // Live streams and some players report timeline/position changes every few
                // seconds; keeping the same map avoids re-running everything downstream.
                playback.isSameMoment(previous) -> current
                // The same item with a corrected end keeps its place; a new item moves to the
                // end, so iterating backwards visits the most recently started first.
                previous?.callbackUri == playback.callbackUri -> current + (key to playback)
                else -> (current - key) + (key to playback)
            }
        }
    }

    /** A released player reports nothing on its way out, so the pool drops it here. */
    fun forget(player: Player) = update(player, null)
}

/**
 * Reports a player's audible playback to [InAppPlaybackRegistry]. A muted player (feed autoplay)
 * or one without a `nostr:` callback URI reports nothing.
 *
 * Attached once per player in `ExoPlayerBuilder`; runs on the player's looper.
 */
class InAppPlaybackListener(
    private val registry: InAppPlaybackRegistry = InAppPlaybackRegistry,
    private val nowSeconds: () -> Long = TimeUtils::now,
) : Player.Listener {
    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (events.containsAny(*RELEVANT_EVENTS)) {
            registry.update(player, snapshot(player))
        }
    }

    private fun snapshot(player: Player): InAppPlayback? {
        if (!player.isPlaying || player.volume < 0.001f) return null

        val item = player.currentMediaItem ?: return null
        val metadata = item.mediaMetadata
        val callbackUri = metadata.extras?.getString(MediaItemCache.EXTRA_CALLBACK_URI) ?: return null
        if (!callbackUri.startsWith("nostr:")) return null

        val duration = player.duration.takeIf { it != C.TIME_UNSET && !player.isCurrentMediaItemLive }

        return InAppPlayback(
            callbackUri = callbackUri,
            // MediaItemCache falls back to the media URL (the media id) when there is no title.
            title = metadata.title?.toString()?.takeUnless { it == item.mediaId },
            artist = metadata.artist?.toString(),
            endsAt = NowPlaying.endsAt(nowSeconds(), duration, player.currentPosition, player.playbackParameters.speed),
        )
    }

    companion object {
        private val RELEVANT_EVENTS =
            intArrayOf(
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_VOLUME_CHANGED,
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                Player.EVENT_MEDIA_METADATA_CHANGED,
            )
    }
}
