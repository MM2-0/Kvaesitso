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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach

/**
 * Wires the platform's playback sources to a [NowPlayingPublisher] under one account's
 * [settings]. [sources] are in priority order: the first one playing an allowed track wins (the
 * in-app player goes first, since the user is looking at it).
 *
 * Every other app seen playing while [NowPlayingSettings.shareOtherApps] is on is reported to
 * [onOtherAppSeen], so the settings screen can list it and let the user block it.
 */
class NowPlayingStatusService(
    private val settings: StateFlow<NowPlayingSettings>,
    private val sources: List<Flow<NowPlaying?>>,
    private val onOtherAppSeen: (NowPlayingSource.OtherApp) -> Unit,
    private val publisher: NowPlayingPublisher,
) {
    suspend fun run() {
        val observed =
            sources.map { source ->
                source.onEach { track ->
                    val app = track?.source as? NowPlayingSource.OtherApp
                    if (app != null && settings.value.shareOtherApps) onOtherAppSeen(app)
                }
            }

        val tracks =
            combine(listOf(settings) + observed) { values ->
                val current = values[0] as NowPlayingSettings
                pickNowPlaying(current, *Array(values.size - 1) { values[it + 1] as NowPlaying? })
            }

        publisher.run(tracks)
    }
}
