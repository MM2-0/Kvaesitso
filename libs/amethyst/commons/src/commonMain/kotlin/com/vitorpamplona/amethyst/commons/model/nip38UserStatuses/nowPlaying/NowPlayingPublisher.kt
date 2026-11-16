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

import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

/**
 * Picks what to share out of the tracks each source reports, in priority order (the in-app
 * player first), skipping the sources [settings] does not allow.
 */
fun pickNowPlaying(
    settings: NowPlayingSettings,
    vararg candidates: NowPlaying?,
): NowPlaying? = candidates.firstOrNull { it != null && settings.isAllowed(it.source) }

/**
 * Turns a stream of "what is playing now" (null when nothing is) into NIP-38 `music` statuses.
 *
 * - A new track is published only after it has played for [Config.startDelayMs], so skipping
 *   through a playlist does not sign and send an event per skipped song.
 * - The status expires when the track ends (NIP-38 asks music statuses for that), so a crash or a
 *   dead battery never leaves a stale "listening to". When the length is unknown it expires after
 *   [Config.unknownLengthTtlSeconds] and is refreshed shortly before that while still playing.
 * - A seek or a resume that moves the end by more than [Config.toleranceSeconds] republishes the
 *   same track with the corrected expiration.
 * - When playback stops, the status is cleared (blank content, as NIP-38 says) after
 *   [Config.clearDelayMs], so a short pause or a buffering stall does not clear and re-post it.
 *
 * Runs on a single coroutine ([run]); a newer value cancels whatever wait the previous one was in.
 */
class NowPlayingPublisher(
    private val publish: suspend (track: NowPlaying, expiration: Long) -> Unit,
    private val clear: suspend () -> Unit,
    private val nowSeconds: () -> Long = TimeUtils::now,
    private val config: Config = Config(),
) {
    data class Config(
        val startDelayMs: Long = 5_000,
        val clearDelayMs: Long = 30_000,
        val unknownLengthTtlSeconds: Long = 600,
        val refreshLeadSeconds: Long = 60,
        val toleranceSeconds: Long = 20,
    )

    private var published: NowPlaying? = null
    private var publishedExpiration: Long = 0

    suspend fun run(tracks: Flow<NowPlaying?>) {
        tracks
            .distinctUntilChanged(::isEquivalent)
            .collectLatest { track ->
                if (track == null) stopped() else playing(track)
            }
    }

    private fun isEquivalent(
        old: NowPlaying?,
        new: NowPlaying?,
    ): Boolean =
        when {
            old == null || new == null -> old == new
            old.source != new.source || !old.isSameTrack(new) -> false
            else -> isSameEnd(old.endsAt, new.endsAt)
        }

    private fun isSameEnd(
        a: Long?,
        b: Long?,
    ) = if (a == null || b == null) a == b else abs(a - b) <= config.toleranceSeconds

    /** The status on relays still shows [track]. */
    private fun isLive(track: NowPlaying) = published?.isSameTrack(track) == true && publishedExpiration > nowSeconds()

    private suspend fun playing(track: NowPlaying) {
        if (!isLive(track)) delay(config.startDelayMs)

        while (true) {
            val now = nowSeconds()
            val expiration = track.endsAt?.takeIf { it > now } ?: (now + config.unknownLengthTtlSeconds)

            if (!isLive(track) || abs(publishedExpiration - expiration) > config.toleranceSeconds) {
                send(track, expiration)
            }

            // A known end needs nothing more: the next track, a seek or a stop comes in as a new value.
            if (track.endsAt != null) return

            val refreshIn = (publishedExpiration - nowSeconds() - config.refreshLeadSeconds).coerceAtLeast(MIN_REFRESH_SECONDS)
            delay(refreshIn * 1000)
        }
    }

    private suspend fun stopped() {
        if (published == null) return

        if (publishedExpiration <= nowSeconds()) {
            // Relays already dropped it.
            published = null
            return
        }

        delay(config.clearDelayMs)

        try {
            clear()
            published = null
            publishedExpiration = 0
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("NowPlayingPublisher", "Could not clear the music status", e)
        }
    }

    private suspend fun send(
        track: NowPlaying,
        expiration: Long,
    ) {
        try {
            publish(track, expiration)
            published = track
            publishedExpiration = expiration
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("NowPlayingPublisher", "Could not publish the music status", e)
        }
    }

    companion object {
        private const val MIN_REFRESH_SECONDS = 60L
    }
}
