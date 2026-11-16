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
package com.vitorpamplona.amethyst.commons.audio.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.audio.PlayableLayout
import com.vitorpamplona.amethyst.commons.audio.playableLayout
import com.vitorpamplona.amethyst.commons.robohash.CachedRobohash
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.ui.theme.isLight

/** The post that prompted the audio cards (an MP3 with `m audio/mpeg`, no ID3 tags), plus the other cases. */
object AudioPlayerSamples {
    const val AUTHOR_HEX = "f728d9e6e7048358e70930f5ca64b097770d989ccd86854fe618eda9c8a38106"
    const val AUTHOR_NAME = "npub17u5d\u2026t4tp"
    const val POST_TEXT =
        "There aren't many nerdcore hip hop artists making music but I just realized that's no longer a problem: " +
            "I can simply generate the esoteric beats I want to hear!"
    private const val HASH = "b21c6e2a4d38f2abac617ac6643aba919f271b37c1c539b5643c17753716c506"
    const val MP3_URL = "https://npub17u5dneh8qjp43ecfxr6u5e9sjamsmxyuekrg2nlxrrk6nj9rsyrqywt4tp.blossom.band/$HASH.mp3"
    const val HLS_URL = "https://stream.example/live/index.m3u8"

    /** Everything the imeta gives: format, size, hash. No title, no waveform. */
    val mp3 = AudioCardInfo(title = null, artist = AUTHOR_NAME, format = "MP3", sizeBytes = 4_992_768, seed = HASH)

    /** The same kind of post with a NIP-A0 `waveform` (0..100 amplitudes). */
    val mp3WithWaveform =
        AudioCardInfo(
            title = null,
            artist = AUTHOR_NAME,
            format = "MP3",
            sizeBytes = 1_204_224,
            seed = "5e0f",
            waveform = listOf(4f, 9f, 22f, 41f, 38f, 12f, 6f, 30f, 72f, 88f, 64f, 20f, 8f, 15f, 46f, 93f, 100f, 77f, 35f, 10f, 5f, 18f, 52f, 61f, 28f, 9f, 3f, 25f, 58f, 80f, 66f, 31f, 12f, 7f, 19f, 44f, 70f, 49f, 16f, 4f),
        )

    /** A named track with artwork (the cover here is the generated fallback; the imeta `image` would load over it). */
    val withArtwork =
        AudioCardInfo(
            title = "Segfault in the Cipher",
            artist = "Esoteric Beats Vol. 1",
            format = "MP3",
            sizeBytes = 4_992_768,
            seed = "3fa1c27be0d94e1a6c55b8f02d7e4a9c1b6f8e03d2a7c5b941e60f8d3c2b1a07",
        )

    /** An HLS playlist: audio only or video, nothing tells until the player probes it. */
    val stream = AudioCardInfo(title = null, artist = AUTHOR_NAME, format = "HLS", sizeBytes = null, seed = HLS_URL)

    /** A 1h12 podcast episode: long enough that the card offers picture-in-picture. */
    val podcast =
        AudioCardInfo(
            title = null,
            artist = AUTHOR_NAME,
            format = "MP3",
            sizeBytes = 69_206_016,
            seed = "a7d93c0e5b2f4816",
            declaredDurationMs = 4_325_000,
        )

    val playing = AudioPlaybackUi(isPlaying = true, positionMs = 72_000, durationMs = 208_000)
    val podcastPlaying = AudioPlaybackUi(isPlaying = true, positionMs = 1_534_000, durationMs = 4_325_000)
    val pausedEarly = AudioPlaybackUi(isPlaying = false, positionMs = 9_000, durationMs = 41_000)
}

/** Dark and light side by side, each filling its half, so screenshots have no transparent gaps. */
@Composable
private fun AudioPreviewFrame(content: @Composable () -> Unit) {
    ThemeComparisonRow {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/** A stripped-down note: avatar, name, the post text, then the audio card where the media goes. */
@Composable
fun MockAudioPost(
    text: String = AudioPlayerSamples.POST_TEXT,
    media: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(12.dp)) {
        Image(
            imageVector = CachedRobohash.get(AudioPlayerSamples.AUTHOR_HEX, MaterialTheme.colorScheme.isLight),
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(AudioPlayerSamples.AUTHOR_NAME, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(" · 2h", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            media()
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp),
    )
}

@Composable
private fun Card(
    layout: PlayableLayout,
    info: AudioCardInfo,
    playback: AudioPlaybackUi = AudioPlaybackUi.Idle,
    pip: Boolean = false,
) = PlayableMediaCard(layout, info, playback, onPlayPause = {}, onSeek = {}, onPictureInPicture = if (pip) ({}) else null)

@Preview(widthDp = 820, heightDp = 760)
@Composable
fun AudioKnownAudioPreview() =
    AudioPreviewFrame {
        val layout = playableLayout("audio/mpeg", AudioPlayerSamples.MP3_URL, hasArtwork = false)
        Column {
            Caption("Known audio, no waveform tag: synthetic waveform, before load")
            MockAudioPost { Card(layout, AudioPlayerSamples.mp3) }
            Caption("Playing")
            MockAudioPost { Card(layout, AudioPlayerSamples.mp3, AudioPlayerSamples.playing) }
            Caption("With a waveform tag")
            MockAudioPost(text = "Quick voice memo about the relay outage") { Card(layout, AudioPlayerSamples.mp3WithWaveform, AudioPlayerSamples.pausedEarly) }
        }
    }

@Preview(widthDp = 820, heightDp = 620)
@Composable
fun AudioWithArtworkPreview() =
    AudioPreviewFrame {
        Column {
            Caption("Known audio with artwork: the cover with the same scrubber")
            MockAudioPost { Card(playableLayout("audio/mpeg", AudioPlayerSamples.MP3_URL, hasArtwork = true), AudioPlayerSamples.withArtwork, AudioPlayerSamples.playing) }
        }
    }

@Preview(widthDp = 820, heightDp = 360)
@Composable
fun AudioUndecidedPreview() =
    AudioPreviewFrame {
        Column {
            Caption("Audio or video? (HLS playlist): neutral card until the player probes it")
            MockAudioPost(text = "Live set tonight, tune in") { Card(playableLayout(null, AudioPlayerSamples.HLS_URL, hasArtwork = false), AudioPlayerSamples.stream) }
        }
    }

@Preview(widthDp = 820, heightDp = 1000)
@Composable
fun AudioPictureInPicturePreview() =
    AudioPreviewFrame {
        val waveform = playableLayout("audio/mpeg", AudioPlayerSamples.MP3_URL, hasArtwork = false)
        val cover = playableLayout("audio/mpeg", AudioPlayerSamples.MP3_URL, hasArtwork = true)
        Column {
            Caption("Long audio offers picture-in-picture (before load: imeta duration)")
            MockAudioPost(text = "New episode is out!") { Card(waveform, AudioPlayerSamples.podcast, pip = true) }
            MockAudioPost(text = "New episode is out!") { Card(cover, AudioPlayerSamples.podcast, AudioPlayerSamples.podcastPlaying, pip = true) }
            Caption("The PiP window")
            Row(Modifier.padding(12.dp)) {
                Box(Modifier.size(180.dp).clip(RoundedCornerShape(12.dp))) {
                    AudioPipContent(AudioPlayerSamples.podcast, AudioPlayerSamples.podcastPlaying)
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(180.dp).clip(RoundedCornerShape(12.dp))) {
                    AudioPipContent(AudioPlayerSamples.withArtwork, AudioPlayerSamples.playing)
                }
            }
        }
    }
