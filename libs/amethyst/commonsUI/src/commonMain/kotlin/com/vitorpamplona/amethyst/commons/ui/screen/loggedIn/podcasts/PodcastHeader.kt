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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_count
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.commons.ui.note.types.PodcastCoverCard
import com.vitorpamplona.amethyst.commons.ui.note.types.PodcastPeople
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.podcasts.PodcastShow

/**
 * Hero header for a single podcast screen: large cover art, title, websites and the show
 * description (rich text + translation, same as a profile's About), followed by the
 * "Episodes (N)" section divider that the episode rows hang under.
 *
 * Spec-neutral: [show] is either a NIP-F4 [PodcastMetadataEvent] (kind 10154) or a Podcasting-2.0
 * show (kind 30078, `d=podcast-metadata`), both adapting to the shared [PodcastShow]. The claimed-
 * author verification row is NIP-F4 only (its `p`-tag claims + kind:10064 counter-claims), so it's
 * shown only when the underlying event is a [PodcastMetadataEvent].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PodcastHeader(
    metadataNote: Note,
    show: PodcastShow?,
    episodeCount: Int?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title = remember(show) { show?.showTitle() }
    val image = remember(show) { show?.showImage() }
    val description = remember(show) { show?.showDescription() }
    val websites = remember(show) { show?.showWebsites() ?: emptyList() }
    val f4 = show as? PodcastMetadataEvent
    val claimedAuthors = remember(f4) { f4?.claimedAuthors() ?: emptyList() }
    val podcastPubkey = remember(f4) { f4?.pubKey }
    val tags = remember(metadataNote) { metadataNote.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }

    Column(Modifier.fillMaxWidth()) {
        PodcastCoverCard(image, metadataNote, accountViewModel)

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (websites.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Size5dp),
                    verticalArrangement = Arrangement.spacedBy(Size5dp),
                ) {
                    websites.forEach { website ->
                        Text(
                            text = website,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.grayText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            description?.let {
                val background = remember { mutableStateOf(Color.Transparent) }

                TranslatableRichTextViewer(
                    content = it,
                    canPreview = false,
                    quotesLeft = 1,
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags,
                    backgroundColor = background,
                    id = metadataNote.idHex,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            if (claimedAuthors.isNotEmpty() && podcastPubkey != null) {
                PodcastAuthors(podcastPubkey, claimedAuthors, accountViewModel, nav)
            }

            val persons = remember(show) { show?.showPersons() ?: emptyList() }
            PodcastPeople(persons, accountViewModel, nav)
        }

        // Standard engagement row for the show itself (comment / zap / react), like any other
        // content detail. Only shown once the show event resolves so it acts on a real note.
        if (show != null) {
            HorizontalDivider(thickness = DividerThickness)

            ReactionsRow(
                baseNote = metadataNote,
                showReactionDetail = true,
                addPadding = true,
                editState = null,
                accountViewModel = accountViewModel,
                nav = nav,
            )

            PodcastTopSupporters(metadataNote, accountViewModel, nav)
        }

        // Only render once episodes have actually loaded — avoids flashing "0 episodes"
        // under the cover while the relay request is still in flight.
        episodeCount?.let { count ->
            HorizontalDivider(thickness = DividerThickness)

            Text(
                text = pluralStringRes(Res.plurals.podcast_episode_count, count, count),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
            )

            HorizontalDivider(thickness = DividerThickness)
        }
    }
}
