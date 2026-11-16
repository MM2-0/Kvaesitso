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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.audio.MediaTrackProbeCache
import com.vitorpamplona.amethyst.commons.audio.MediaTracks
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.audio.mayHoldVideo
import com.vitorpamplona.amethyst.commons.audio.probeMediaTracks
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.MusicTrackAudioPlayer
import com.vitorpamplona.amethyst.commons.ui.note.platform.NoteMediaView
import com.vitorpamplona.amethyst.commons.ui.note.platform.RenderAudioWithWaveform
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * What the file at [url] holds, for a label that can't say ([mayHoldVideo]: an `.mp4` or `video/mp4`
 * that may be a music video or a plain AAC upload). [MediaTracks.UNKNOWN] right away for anything
 * else; null while the probe is still reading the file's track list.
 */
@Composable
fun rememberMediaTracks(
    url: String,
    mimeType: String?,
    accountViewModel: AccountViewModel,
): MediaTracks? {
    val needsProbe = remember(url, mimeType) { mayHoldVideo(mimeType, url) }
    if (!needsProbe) return MediaTracks.UNKNOWN
    val tracks by produceState(MediaTrackProbeCache.get(url), url, mimeType) {
        if (value == null) value = accountViewModel.httpClientBuilder.probeMediaTracks(url, mimeType)
    }
    return tracks
}

/**
 * The media of an audio kind (music track, audio track, audio header): the audio card — [cover] over a
 * player strip when [showCover], else the waveform strip — unless the file turns out to hold video.
 *
 * Publishers put both in the same slot. Nostria writes a music video's `.mp4` as a track's `url`, and a
 * Blossom server names an AAC upload `.mp4` and serves it as `video/mp4`, so neither the extension nor
 * the MIME type tells them apart. The file's track list does: while it is being read, and when it can't be
 * read, the card stays audio (what the kind says it is); a video track switches it to the video player.
 */
@Composable
fun AudioFirstMedia(
    url: String,
    mimeType: String?,
    title: String?,
    cover: String?,
    showCover: Boolean,
    waveform: WaveformData?,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val tracks = rememberMediaTracks(url, mimeType, accountViewModel)

    if (tracks == MediaTracks.HAS_VIDEO) {
        NoteMediaView(
            mediaUri = url,
            isAudio = false,
            title = title,
            coverUri = cover,
            waveform = null,
            contentScale = ContentScale.FillWidth,
            note = note,
            accountViewModel = accountViewModel,
        )
        return
    }

    // A file the probe found audio-only plays as audio even where its label said video, so the
    // players don't open it as a video and wait for a frame that never comes.
    val audioMimeType = if (tracks == MediaTracks.AUDIO_ONLY && mimeType?.startsWith("audio/") != true) "audio/mp4" else mimeType

    if (showCover) {
        MusicTrackCover(cover, note, accountViewModel)
        MusicTrackAudioPlayer(
            audioUrl = url,
            mimeType = audioMimeType,
            title = title,
            note = note,
            borderModifier = STRIP_UNDER_COVER,
            accountViewModel = accountViewModel,
        )
    } else {
        RenderAudioWithWaveform(
            mediaUrl = url,
            title = title,
            mimeType = audioMimeType,
            waveform = waveform,
            note = note,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

private val STRIP_UNDER_COVER = Modifier.clip(RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp))
