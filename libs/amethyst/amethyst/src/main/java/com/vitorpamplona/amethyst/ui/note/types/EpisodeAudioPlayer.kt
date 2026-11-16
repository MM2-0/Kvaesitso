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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.audio.syntheticWaveformFor
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.note.platform.EpisodeCommand
import com.vitorpamplona.amethyst.commons.ui.note.platform.EpisodeControl
import com.vitorpamplona.amethyst.commons.ui.note.platform.EpisodePlayhead
import com.vitorpamplona.amethyst.commons.ui.note.types.getAudioMetaWithWaveform
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.playback.composable.GetVideoController
import com.vitorpamplona.amethyst.service.playback.composable.PauseControllerWhenInBackground
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** How close to the start a loaded player must be for [EpisodeAudioPlayer] to move it to the resume point. */
private const val RESUME_WINDOW_MS = 2_000L

/**
 * An episode's audio through the shared media-controller stack, moved to [startAtSeconds] when it
 * loads at the start and played at [speed], obeying [control], reporting its playhead to
 * [onPlayhead] every second while shown.
 */
@Composable
fun EpisodeAudioPlayer(
    audioUrl: String,
    mimeType: String?,
    title: String?,
    image: String?,
    note: Note,
    startAtSeconds: Long?,
    speed: Float,
    control: EpisodeControl,
    onPlayhead: (EpisodePlayhead) -> Unit,
    accountViewModel: AccountViewModel,
) {
    val callbackUri = remember(note) { note.toNostrUri() }
    val waveform =
        remember(note.event) {
            note.event
                ?.getAudioMetaWithWaveform()
                ?.waveform
                ?.let { WaveformData(it) }
                ?: syntheticWaveformFor(note.idHex)
        }
    val report by rememberUpdatedState(onPlayhead)

    GetMediaItem(
        videoUri = audioUrl,
        title = title,
        artworkUri = image,
        authorName = note.author?.toBestDisplayName(),
        callbackUri = callbackUri,
        mimeType = mimeType,
        aspectRatio = null,
        proxyPort = accountViewModel.httpClientBuilder.proxyPortForVideo(audioUrl),
        keepPlaying = false,
        waveformData = waveform,
    ) { mediaItem ->
        GetVideoController(
            mediaItem = mediaItem,
            muted = false,
        ) { controller ->
            PauseControllerWhenInBackground(controller)

            LaunchedEffect(controller, startAtSeconds) {
                val start = startAtSeconds ?: return@LaunchedEffect
                if (controller.controller.currentPosition < RESUME_WINDOW_MS) controller.controller.seekTo(start * 1000)
            }

            LaunchedEffect(controller, speed) {
                controller.controller.setPlaybackSpeed(speed)
            }

            // Players are pooled and reused for other media: the feed's speed stays with this episode.
            DisposableEffect(controller) {
                onDispose { if (controller.controller.playbackParameters.speed != 1f) controller.controller.setPlaybackSpeed(1f) }
            }

            LaunchedEffect(controller, control) {
                control.commands.collect { command ->
                    val player = controller.controller
                    when (command) {
                        is EpisodeCommand.Seek -> {
                            player.seekTo(command.millis)
                            if (command.play) player.play()
                        }
                        EpisodeCommand.Play -> player.play()
                        EpisodeCommand.Pause -> player.pause()
                    }
                    report(player.playhead())
                }
            }

            LaunchedEffect(controller) {
                while (isActive) {
                    report(controller.controller.playhead())
                    delay(1_000)
                }
            }

            Row(
                Modifier.fillMaxWidth().height(80.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RenderVoicePlayer(
                    mediaItem = mediaItem,
                    controllerState = controller,
                    waveform = waveform,
                    borderModifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

private fun Player.playhead() =
    EpisodePlayhead(
        positionMillis = currentPosition.coerceAtLeast(0),
        durationMillis = duration.takeIf { it != C.TIME_UNSET && it > 0 },
        isPlaying = isPlaying,
        hasEnded = playbackState == Player.STATE_ENDED,
    )
