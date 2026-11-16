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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.ui.playback.controls.AnimatedPlayPauseButton
import com.vitorpamplona.amethyst.service.cast.CastSessionState
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.seekBackward
import com.vitorpamplona.amethyst.service.playback.composable.skipForward
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun RenderCenterButtons(
    controllerState: MediaControllerState,
    controllerVisible: MutableState<Boolean>,
    videoUri: String,
    modifier: Modifier,
    isLiveStream: Boolean = false,
) {
    val state = rememberPlayPauseButtonState(controllerState.controller)

    val castSessionState by Amethyst.instance.castRegistry.sessionState
        .collectAsStateWithLifecycle()
    val isCastingThisVideo =
        (castSessionState as? CastSessionState.Casting)?.request?.url == videoUri

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isLiveStream) {
            AnimatedSkipButton(
                controllerVisible = controllerVisible,
                isForward = false,
                enabled = !isCastingThisVideo,
            ) {
                controllerState.controller.seekBackward()
            }
        }

        AnimatedPlayPauseButton(
            controllerVisible,
            Modifier,
            !state.showPlay,
            enabled = !isCastingThisVideo,
        ) {
            state.onClick()
        }

        if (!isLiveStream) {
            AnimatedSkipButton(
                controllerVisible = controllerVisible,
                isForward = true,
                enabled = !isCastingThisVideo,
            ) {
                controllerState.controller.skipForward()
            }
        }
    }

    // Auto-hide controls 3s after playback begins — but stay visible while
    // casting so the user can see the disabled transport controls and find
    // the cast-stop button in the top bar without tapping the screen first.
    // Read through rememberUpdatedState: the effect outlives recompositions, and hiding a
    // controllerVisible the caller has since replaced would leave the drawn controls up.
    val currentControllerVisible by rememberUpdatedState(controllerVisible)
    if (!state.showPlay && !isCastingThisVideo) {
        LaunchedEffect(state.showPlay) {
            delay(3000)
            currentControllerVisible.value = false
        }
    }
}
