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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drss_heard_played
import com.vitorpamplona.amethyst.commons.resources.drss_heard_resume_at
import com.vitorpamplona.amethyst.commons.resources.drss_listen_plays
import com.vitorpamplona.amethyst.commons.resources.drss_listen_time_left
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.drss.listenProgress.DrssListenProgress
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip92IMeta.imetas
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** An episode's audio file: where it is and, when the event says, its type. */
@Immutable
data class EpisodeAudio(
    val url: String,
    val mimeType: String?,
)

private val AUDIO_EXTENSIONS = listOf(".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".flac")

/**
 * The audio of an article that is a podcast episode, as drss.io publishes them: the first `imeta`
 * typed `audio/…`, or one whose URL ends like an audio file.
 */
fun Event.episodeAudio(): EpisodeAudio? =
    imetas().firstNotNullOfOrNull { meta ->
        val mime = meta.properties["m"]?.firstOrNull()
        val path = meta.url.substringBefore('?').lowercase()
        if (mime?.startsWith("audio/") == true || (mime == null && AUDIO_EXTENSIONS.any { path.endsWith(it) })) {
            EpisodeAudio(meta.url, mime)
        } else {
            null
        }
    }

/**
 * The user's progress through [episode], from what the cache holds. It loads with the account's
 * other data, so a card shows it without asking relays for each episode.
 */
@Composable
fun observeMyListening(
    episode: AddressableNote,
    accountViewModel: AccountViewModel,
): State<DrssListenProgress?> {
    val drss = accountViewModel.account.drss
    val flow =
        remember(episode.address) {
            // The kind-30078 record's own note, and the account-wide tick for the kind-30054 one
            // drss has not moved yet: no card holds a note for an address that is almost always empty.
            merge(
                drss
                    .progressNote(episode.address)
                    .flow()
                    .metadata.stateFlow,
                drss.progressVersion,
            ).map { drss.progressOf(episode.address) }
        }
    return flow.collectAsStateWithLifecycle(drss.progressOf(episode.address))
}

/**
 * How much of an episode the user has heard, for a card: the plays and a check once finished,
 * otherwise where they stopped, as a thin bar and the time left. Draws nothing for an article the
 * user has not played or one without audio.
 */
@Composable
fun DrssHeardBadge(
    note: AddressableNote,
    event: LongFormContentEvent,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val hasAudio = remember(event) { event.episodeAudio() != null }
    if (!hasAudio) return
    val progress by observeMyListening(note, accountViewModel)
    progress?.let { DrssHeardBadge(it, modifier) }
}

@Composable
fun DrssHeardBadge(
    progress: DrssListenProgress,
    modifier: Modifier = Modifier,
) {
    val position = progress.position()
    val duration = progress.durationInSeconds()?.takeIf { it > 0 }
    val plays = progress.plays()?.toInt()?.takeIf { it > 0 } ?: if (progress.completedAt() != null) 1 else null

    if ((position == null || position <= 0) && plays == null) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (position != null && position > 0) {
            Icon(symbol = MaterialSymbols.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            if (duration != null) {
                Box(
                    Modifier
                        .width(56.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f))
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(
                    text = stringRes(Res.string.drss_listen_time_left, formatListenClock((duration - position).coerceAtLeast(0))),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
            } else {
                Text(
                    text = stringRes(Res.string.drss_heard_resume_at, formatListenClock(position)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }
        if (plays != null) {
            Icon(symbol = MaterialSymbols.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.allGoodColor, modifier = Modifier.size(14.dp))
            Text(
                text = if (plays > 1) pluralStringRes(Res.plurals.drss_listen_plays, plays, plays) else stringRes(Res.string.drss_heard_played),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.allGoodColor,
            )
        }
    }
}

/** `1:02:03` for an hour or more, `12:03` below. */
fun formatListenClock(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = (seconds % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$s" else "$m:$s"
}
