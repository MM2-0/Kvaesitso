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

import android.os.SystemClock
import androidx.media3.common.Player
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys

/**
 * Accounts actual media playback time into the resource-usage ledger.
 * Playback is one of the most energy-dense things the app does — decoder,
 * screen, and streaming all at once — and this turns "video used 800 MB"
 * into "800 MB over 2h of playback" (normal) vs "over 10 minutes" (a bug).
 *
 * Attached once per player in [ExoPlayerBuilder]; a player released
 * mid-playback loses at most its final open segment.
 */
class MediaPlayTimeTracker(
    private val onPlayed: (elapsedMs: Long) -> Unit = { ms ->
        runCatching { Amethyst.instance.resourceUsage.add(UsageKeys.MEDIA_PLAY_MS, ms) }
    },
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) : Player.Listener {
    private var playingSinceMs = Long.MIN_VALUE

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val now = nowMs()
        if (isPlaying) {
            playingSinceMs = now
        } else if (playingSinceMs != Long.MIN_VALUE) {
            onPlayed(now - playingSinceMs)
            playingSinceMs = Long.MIN_VALUE
        }
    }
}
