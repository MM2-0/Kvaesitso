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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_number
import com.vitorpamplona.amethyst.commons.resources.podcast_season
import com.vitorpamplona.amethyst.commons.resources.podcast_season_episode
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.commons.ui.note.platform.PodcastEpisodeAudioPlayer
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeAgoLabels
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoWith
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.podcasts.PodcastEpisode

private val PLAYER_SHAPE = Modifier.clip(RoundedCornerShape(12.dp))

/**
 * One episode as a compact "show list" row inside a podcast's screen: publish date, title,
 * short description, and an inline audio player so the episode plays without leaving the list.
 * The show artwork is the same for every episode, so it's omitted here — the header carries it.
 */
@Composable
fun PodcastEpisodeListItem(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event ?: return
    // Both kind 54 (NIP-F4) and kind 30054 (Podcasting 2.0) episodes implement PodcastEpisode.
    val episode = noteEvent as? PodcastEpisode ?: return

    val title = remember(noteEvent) { episode.episodeTitle() }
    val description = remember(noteEvent) { episode.episodeDescription() }
    // Prefer audio; fall back to a video source so video-only episodes still play inline.
    val media = remember(noteEvent) { episode.episodeAudio().firstOrNull() ?: episode.episodeVideo() }
    val image = remember(noteEvent) { episode.episodeImage() }
    val season = remember(noteEvent) { episode.episodeSeason() }
    val episodeNumber = remember(noteEvent) { episode.episodeNumber() }

    val timeLabels = rememberTimeAgoLabels()
    val dateStr = remember(noteEvent, timeLabels) { timeAgoWith(noteEvent.createdAt, timeLabels, prefix = "") }
    val seasonEpisodeLabel =
        when {
            season != null && episodeNumber != null -> stringRes(Res.string.podcast_season_episode, season, episodeNumber)
            episodeNumber != null -> stringRes(Res.string.podcast_episode_number, episodeNumber)
            season != null -> stringRes(Res.string.podcast_season, season)
            else -> null
        }
    val subtitle = listOfNotNull(seasonEpisodeLabel, dateStr.takeIf { it.isNotBlank() }).joinToString(" · ")

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { routeFor(note, accountViewModel.account)?.let { nav.nav(it) } }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(Size5dp),
    ) {
        subtitle.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }

        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        media?.let { audio ->
            PodcastEpisodeAudioPlayer(
                audio = audio,
                note = note,
                title = title,
                image = image,
                borderModifier = PLAYER_SHAPE,
                accountViewModel = accountViewModel,
            )
        }

        // Standard engagement row per episode (comment / zap / react) — same as every other note.
        // addPadding = false since the row already sits inside this item's horizontal padding.
        ReactionsRow(
            baseNote = note,
            showReactionDetail = true,
            addPadding = false,
            editState = null,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
