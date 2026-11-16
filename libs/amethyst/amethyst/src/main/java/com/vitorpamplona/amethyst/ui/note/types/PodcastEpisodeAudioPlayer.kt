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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.audio.syntheticWaveformFor
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.note.PodcastSoundbites
import com.vitorpamplona.amethyst.commons.ui.note.types.PodcastChaptersView
import com.vitorpamplona.amethyst.commons.ui.note.types.getAudioMetaWithWaveform
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.playback.composable.GetVideoController
import com.vitorpamplona.amethyst.service.playback.composable.PauseControllerWhenInBackground
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import com.vitorpamplona.quartz.podcasts.PodcastAudio
import com.vitorpamplona.quartz.podcasts.PodcastEpisode

// The voice player's internal controls are laid out for 100.dp; 80.dp is the tightest height
// that still fits the play button without clipping it. Shared so the feed card and the
// per-podcast list row stay pixel-identical.
private val PLAYER_HEIGHT_MODIFIER = Modifier.fillMaxWidth().height(80.dp)

/**
 * The inline audio strip for a podcast episode: one [PodcastAudio] played through the shared
 * media-controller stack. Works for both NIP-F4 (kind 54) and Podcasting-2.0 (kind 30054)
 * episodes via the spec-neutral audio holder. [borderModifier] shapes the strip — bottom-rounded
 * when it butts up under a cover image, fully rounded when it stands alone in a list.
 */
@Composable
fun PodcastEpisodeAudioPlayer(
    audio: PodcastAudio,
    note: Note,
    title: String?,
    image: String?,
    borderModifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val callbackUri = remember(note) { note.toNostrUri() }
    // Use a real waveform when the episode ships one in its IMeta; NIP-F4 usually doesn't, so
    // fall back to a decorative waveform seeded by the note id — same approach as music tracks,
    // a per-episode silhouette instead of a flat strip.
    val waveform =
        remember(note) {
            note.event
                ?.getAudioMetaWithWaveform()
                ?.waveform
                ?.let { WaveformData(it) }
                ?: syntheticWaveformFor(note.idHex)
        }

    // The episode's value-for-value block, if any — drives the per-minute streaming control below
    // the player. Pulled through the spec-neutral PodcastEpisode interface so both kinds work.
    val value = remember(note) { (note.event as? PodcastEpisode)?.episodeValue() }

    // Highlight clips (Podcasting-2.0 soundbites) — rendered under the player so a tap can seek the
    // live controller to the clip's start.
    val soundbites = remember(note) { (note.event as? PodcastEpisode)?.episodeSoundbites().orEmpty() }

    // Off-event chapters document URL, if any — the list seeks the live controller too.
    val chaptersUrl = remember(note) { (note.event as? PodcastEpisode)?.episodeChaptersUrl() }

    Column(Modifier.fillMaxWidth()) {
        GetMediaItem(
            videoUri = audio.url,
            title = title,
            artworkUri = image,
            authorName = note.author?.toBestDisplayName(),
            callbackUri = callbackUri,
            mimeType = audio.mediaType,
            aspectRatio = null,
            proxyPort = accountViewModel.httpClientBuilder.proxyPortForVideo(audio.url),
            keepPlaying = false,
            waveformData = waveform,
        ) { mediaItem ->
            GetVideoController(
                mediaItem = mediaItem,
                muted = false,
            ) { controller ->
                PauseControllerWhenInBackground(controller)
                Row(
                    PLAYER_HEIGHT_MODIFIER,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RenderVoicePlayer(
                        mediaItem = mediaItem,
                        controllerState = controller,
                        waveform = waveform,
                        borderModifier = borderModifier,
                        accountViewModel = accountViewModel,
                    )
                }

                value?.let {
                    PodcastStreamingControl(
                        value = it,
                        note = note,
                        episodeName = title,
                        podcastName = null,
                        controllerState = controller,
                        accountViewModel = accountViewModel,
                    )
                }

                PodcastSoundbites(soundbites) { startMillis ->
                    controller.controller.seekTo(startMillis)
                    controller.controller.play()
                }

                chaptersUrl?.let { url ->
                    PodcastChaptersView(
                        chaptersUrl = url,
                        onSeek = { startMillis ->
                            controller.controller.seekTo(startMillis)
                            controller.controller.play()
                        },
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }
    }
}
