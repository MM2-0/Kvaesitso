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
package com.vitorpamplona.amethyst.commons.music

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.audio.declaredAsAudio
import com.vitorpamplona.amethyst.commons.audio.isMp4FamilyMedia
import com.vitorpamplona.amethyst.commons.richtext.normalizeMimeType
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent

/**
 * What a music track card plays: a music video through the video player, or audio through the
 * waveform strip under the cover.
 */
@Immutable
sealed interface MusicTrackMedia {
    @Immutable
    data class Video(
        val url: String,
    ) : MusicTrackMedia

    @Immutable
    data class Audio(
        val url: String,
        val mimeType: String?,
    ) : MusicTrackMedia
}

/**
 * Picks the media a music track card plays, or null when the track names none.
 *
 * The `video` tag wins. Without one, `url` is audio by spec, but some publishers put the music
 * video itself there (Nostria uploads a 1080p `.mp4` as `url`). A `url` the shared classifier
 * ([declaredAsAudio]) calls video plays as a video, except an MP4-family one (`.mp4`, `.mov`): that
 * container is as often a plain AAC upload (Blossom names every M4A `.mp4`, Amethyst's own uploads
 * included), so it stays audio here and the card reads the file's track list to find out
 * (`mayHoldVideo`). One the classifier can't place stays audio too: an HLS playlist carries either,
 * and a bare Blossom hash names nothing. A `format` tag declares `url` audio, so such a track keeps the
 * audio path whatever the extension.
 */
fun MusicTrackEvent.media(): MusicTrackMedia? {
    videoUrl()?.let { return MusicTrackMedia.Video(it) }
    val url = url() ?: return null
    val format = format()
    if (format == null && declaredAsAudio(null, url) == false && !isMp4FamilyMedia(null, url)) {
        return MusicTrackMedia.Video(url)
    }
    // Per spec, `format` is the audio format (mp3/flac/m4a/ogg): the MIME type it names, read as audio
    // (`m4a` is `audio/mp4`, `mp3` is `audio/mpeg`; a bare `mp4` would read as video, so it is `audio/mp4`).
    return MusicTrackMedia.Audio(url, format?.let { audioMimeTypeForFormat(it) })
}

private fun audioMimeTypeForFormat(format: String): String = normalizeMimeType(format)?.takeIf { it.startsWith("audio/") } ?: "audio/${format.lowercase()}"

/**
 * The `format` tag (mp3/flac/m4a/ogg) for an uploaded music file of [mimeType], so readers can tell the
 * file is audio without opening it: an M4A upload otherwise comes back from Blossom as `.mp4`. A
 * `video/mp4` is the same M4A as Blossom labels it (the music composer only uploads audio). Null for a
 * type it doesn't recognise.
 */
fun musicFormatTagFor(mimeType: String?): String? =
    when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/mp4", "audio/x-m4a", "audio/m4a", "video/mp4" -> "m4a"
        "audio/aac", "audio/aacp" -> "aac"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/ogg" -> "ogg"
        "audio/opus" -> "opus"
        "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
        "audio/webm" -> "webm"
        else -> null
    }
