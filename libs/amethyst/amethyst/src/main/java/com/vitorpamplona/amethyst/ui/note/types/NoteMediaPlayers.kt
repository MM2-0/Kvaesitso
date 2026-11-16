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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.audio.syntheticWaveformFor
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.playback.composable.GetVideoController
import com.vitorpamplona.amethyst.service.playback.composable.LoadThumbAndThenVideoView
import com.vitorpamplona.amethyst.service.playback.composable.PauseControllerWhenInBackground
import com.vitorpamplona.amethyst.service.playback.composable.VideoView
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem

// 80dp shrinks the visible top/bottom border around the 75dp play button to ~2.5dp on each side —
// the default 100dp inherited from VoiceHeightModifier left a 12.5dp empty band that read as too
// tall on a music card, where the player butts up against the cover.
private val PLAYER_HEIGHT_MODIFIER = Modifier.fillMaxWidth().height(80.dp)

/**
 * Android's music track audio strip. We *don't* call RenderAudioWithWaveform here: it wraps the
 * player in `MaxWidthPaddingTop5dp`, renders its own hashtag row (the card already shows them as
 * chips) and hard-codes an all-rounded border. So this inlines the GetMediaItem →
 * GetVideoController → RenderVoicePlayer chain with the card's [borderModifier]; ExoPlayer logic,
 * controls and PiP all come from the shared composables — only the chrome differs.
 */
@Composable
fun MusicTrackAudioPlayer(
    audioUrl: String,
    mimeType: String?,
    title: String?,
    note: Note,
    borderModifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    // Spec doesn't carry a waveform for kind 36787, so we synthesize one keyed off the track
    // address. The bars are purely decorative — ExoPlayer owns actual playback progress.
    val syntheticWaveform = remember(note.idHex) { syntheticWaveformFor(note.idHex) }
    val callbackUri = remember(note) { note.toNostrUri() }

    Row(PLAYER_HEIGHT_MODIFIER, verticalAlignment = Alignment.CenterVertically) {
        GetMediaItem(
            videoUri = audioUrl,
            title = title,
            artworkUri = null,
            authorName = note.author?.toBestDisplayName(),
            callbackUri = callbackUri,
            mimeType = mimeType,
            aspectRatio = null,
            proxyPort = accountViewModel.httpClientBuilder.proxyPortForVideo(audioUrl),
            keepPlaying = false,
            waveformData = syntheticWaveform,
        ) { mediaItem ->
            GetVideoController(
                mediaItem = mediaItem,
                muted = false,
            ) { controller ->
                PauseControllerWhenInBackground(controller)
                RenderVoicePlayer(
                    mediaItem = mediaItem,
                    controllerState = controller,
                    waveform = syntheticWaveform,
                    borderModifier = borderModifier,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

/**
 * Android's NoteMediaView: the full video player, with [coverUri] as its thumb when there is one,
 * and [waveform]'s bars over an audio file that has them.
 */
@Composable
fun NoteMediaView(
    mediaUri: String,
    title: String?,
    coverUri: String?,
    waveform: WaveformData?,
    contentScale: ContentScale,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val callbackUri = remember(note) { note.toNostrUri() }
    if (coverUri != null) {
        LoadThumbAndThenVideoView(
            videoUri = mediaUri,
            mimeType = null,
            title = title,
            thumbUri = coverUri,
            authorName = note.author?.toBestDisplayName(),
            roundedCorner = true,
            contentScale = contentScale,
            nostrUriCallback = callbackUri,
            accountViewModel = accountViewModel,
        )
    } else {
        VideoView(
            videoUri = mediaUri,
            mimeType = null,
            title = title,
            waveform = waveform,
            authorName = note.author?.toBestDisplayName(),
            roundedCorner = true,
            contentScale = contentScale,
            nostrUriCallback = callbackUri,
            accountViewModel = accountViewModel,
        )
    }
}
