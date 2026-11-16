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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.math.abs

/** Where a track is being played. */
@Immutable
sealed interface NowPlayingSource {
    /** Amethyst's own player (a music track or podcast episode opened in the app). */
    data object InApp : NowPlayingSource

    /**
     * Another app on this device. [id] is stable across sessions (an Android package name, an
     * MPRIS bus name, a Windows app id) and is what the per-app block list keys on; [label] is
     * what the settings screen shows.
     */
    data class OtherApp(
        val id: String,
        val label: String,
    ) : NowPlayingSource
}

/**
 * A track (song or podcast episode) the user is playing right now, ready to become a NIP-38
 * `music` status.
 *
 * [endsAt] is the Unix time (seconds) the track will finish at its current position and speed,
 * which becomes the status' NIP-40 expiration, as NIP-38 asks for music. It is null when the
 * length is unknown (a live stream, an app that does not report a duration).
 *
 * [url], [addressId] and [eventId] become the status' `r`, `a` and `e` tags, so readers can open
 * what is playing: in-app tracks point at their Nostr event.
 */
@Immutable
data class NowPlaying(
    val title: String,
    val artist: String? = null,
    val source: NowPlayingSource,
    val endsAt: Long? = null,
    val url: String? = null,
    val addressId: String? = null,
    val eventId: HexKey? = null,
) {
    /** The status line, in the `Title - Artist` shape of the NIP-38 example. */
    fun statusText(): String = if (artist.isNullOrBlank()) title else "$title - $artist"

    /** Same track, regardless of the playback position. */
    fun isSameTrack(other: NowPlaying): Boolean =
        title == other.title &&
            artist == other.artist &&
            url == other.url &&
            addressId == other.addressId &&
            eventId == other.eventId

    /**
     * Same track from the same source, ending within [toleranceSeconds] of [other]. Sources call
     * this to drop the position ticks some players report every second, which would otherwise
     * re-emit a "new" value (and re-run everything downstream) for a status that has not changed.
     */
    fun isSameMoment(
        other: NowPlaying?,
        toleranceSeconds: Long = MOMENT_TOLERANCE_SECONDS,
    ): Boolean {
        if (other == null || source != other.source || !isSameTrack(other)) return false
        val a = endsAt
        val b = other.endsAt
        return if (a == null || b == null) a == b else abs(a - b) <= toleranceSeconds
    }

    companion object {
        const val MOMENT_TOLERANCE_SECONDS = 5L

        /**
         * When a track [durationMs] long, now at [positionMs] and playing at [speed], ends,
         * measured from [nowSeconds]. Null when the duration is unknown or the track already ended.
         */
        fun endsAt(
            nowSeconds: Long,
            durationMs: Long?,
            positionMs: Long,
            speed: Float = 1f,
        ): Long? {
            if (durationMs == null || durationMs <= 0) return null
            val remainingMs = durationMs - positionMs.coerceAtLeast(0)
            if (remainingMs <= 0) return null
            val effectiveSpeed = if (speed > 0f) speed else 1f
            return nowSeconds + (remainingMs / effectiveSpeed / 1000).toLong() + 1
        }
    }
}
