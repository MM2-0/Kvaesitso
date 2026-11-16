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
package com.vitorpamplona.amethyst.service.playback.composable.wavefront

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.linc.audiowaveform.infiniteLinearGradient
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.ui.components.AudioWaveformReadOnly
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState

@Composable
fun Waveform(
    waveform: WaveformData,
    mediaControllerState: MediaControllerState,
    modifier: Modifier,
) {
    val waveformProgress = remember { mutableFloatStateOf(0F) }

    DrawWaveform(waveform, waveformProgress, modifier)

    val restartFlow = remember { mutableIntStateOf(0) }

    // Keeps the screen on while playing and viewing videos.
    DisposableEffect(key1 = mediaControllerState.controller) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // doesn't consider the mutex because the screen can turn off if the video
                    // being played in the mutex is not visible.
                    if (isPlaying) {
                        restartFlow.intValue += 1
                    }
                }
            }

        mediaControllerState.controller.addListener(listener)
        onDispose { mediaControllerState.controller.removeListener(listener) }
    }

    LaunchedEffect(key1 = restartFlow.intValue) {
        mediaControllerState.controller.pollCurrentRelativePositionFlow().collect { value -> waveformProgress.floatValue = value }
    }
}

@Composable
fun DrawWaveform(
    waveform: WaveformData,
    waveformProgress: MutableFloatState,
    modifier: Modifier,
) {
    AudioWaveformReadOnly(
        modifier = modifier.padding(start = 10.dp, end = 10.dp),
        amplitudes = waveform.wave,
        progress = waveformProgress.floatValue,
        progressBrush =
            Brush.infiniteLinearGradient(
                colors = listOf(Color(0xff2598cf), Color(0xff652d80)),
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                width = 128F,
            ),
        onProgressChange = { waveformProgress.floatValue = it },
    )
}
