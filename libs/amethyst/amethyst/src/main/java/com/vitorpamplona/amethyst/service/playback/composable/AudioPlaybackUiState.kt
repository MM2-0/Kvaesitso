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
package com.vitorpamplona.amethyst.service.playback.composable

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.vitorpamplona.amethyst.commons.audio.player.AudioPlaybackUi
import kotlinx.coroutines.delay

/** The player's state as the card draws it: event-driven, plus a position poll while it plays. */
@Composable
fun rememberAudioPlaybackUi(player: Player): State<AudioPlaybackUi> {
    val state = remember(player) { mutableStateOf(player.toPlaybackUi()) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    state.value = player.toPlaybackUi()
                }
            }
        player.addListener(listener)
        state.value = player.toPlaybackUi()
        onDispose { player.removeListener(listener) }
    }

    val isPlaying = state.value.isPlaying
    LaunchedEffect(player, isPlaying) {
        while (isPlaying) {
            delay(POSITION_POLL_MS)
            state.value = player.toPlaybackUi()
        }
    }
    return state
}

@OptIn(UnstableApi::class)
private fun Player.toPlaybackUi() =
    AudioPlaybackUi(
        // Matches what the button should offer: pause while playing or about to (buffering with
        // playWhenReady), play when paused, ended or idle.
        isPlaying = !Util.shouldShowPlayButton(this),
        positionMs = currentPosition.coerceAtLeast(0),
        durationMs = duration.takeIf { it != C.TIME_UNSET && it > 0 },
    )

private const val POSITION_POLL_MS = 250L
