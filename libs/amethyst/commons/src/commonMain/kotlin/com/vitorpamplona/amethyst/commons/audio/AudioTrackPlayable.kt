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
package com.vitorpamplona.amethyst.commons.audio

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.tags.MimeTypeTag

/** A file an event names to play, with the mime declared beside it, if any. */
@Immutable
data class PlayableAudio(
    val url: String,
    val mimeType: String?,
)

/**
 * The file a kind-31337 audio track plays, or null when it names none.
 *
 * The kind is borrowed: AntennaPod publishes its ad-skip data on it, games their saved state, sync
 * tools their file index, and readers their links to a streaming site's page. Such an event is no
 * track, and drawing one as a player shows an empty "untitled" card over its JSON. So a track is an
 * event that names an audio file the player can take: `media`, then `url`, then each imeta's file,
 * the first one that is http(s) and audio by the player's own rule ([declaredAsAudio]: its mime, from
 * its imeta or the event's NIP-94 `m`, else its extension, `.mpga` included). An MP4-family file
 * counts too ([mayHoldVideo]): the audio card reads its track list and plays it either way.
 */
fun AudioTrackEvent.playableAudio(): PlayableAudio? {
    val eventMime = tags.fastFirstNotNullOfOrNull(MimeTypeTag::parse)
    // Parsed once, on the first candidate.
    var imetas: List<IMetaTag>? = null

    fun allImetas() = imetas ?: tags.imetas().also { imetas = it }

    fun mimeOf(url: String): String? =
        allImetas()
            .firstOrNull { it.url == url }
            ?.properties
            ?.get("m")
            ?.firstOrNull()
            ?.ifBlank { null } ?: eventMime

    fun playable(url: String?): PlayableAudio? {
        val trimmed = url?.trim() ?: return null
        if (!isHttp(trimmed)) return null
        val mime = mimeOf(trimmed)
        return if (declaredAsAudio(mime, trimmed) == true || mayHoldVideo(mime, trimmed)) PlayableAudio(trimmed, mime) else null
    }

    return playable(media()) ?: playable(url()) ?: allImetas().firstNotNullOfOrNull { playable(it.url) }
}

/** True when the track names a file to play: the kind is drawn as a track only then. */
fun AudioTrackEvent.hasPlayableAudio() = playableAudio() != null

private fun isHttp(url: String) = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
