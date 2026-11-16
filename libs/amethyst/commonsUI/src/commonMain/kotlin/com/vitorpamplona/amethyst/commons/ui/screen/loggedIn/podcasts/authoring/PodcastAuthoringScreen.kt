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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.authoring

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.rememberAsyncImagePainter
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_create_show_hint
import com.vitorpamplona.amethyst.commons.resources.podcast_create_your_show
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_count
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_subtitle_episode
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_subtitle_season
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_subtitle_season_episode
import com.vitorpamplona.amethyst.commons.resources.podcast_new_episode
import com.vitorpamplona.amethyst.commons.resources.podcast_new_trailer
import com.vitorpamplona.amethyst.commons.resources.podcast_no_episodes_yet
import com.vitorpamplona.amethyst.commons.resources.podcast_tap_to_edit_show
import com.vitorpamplona.amethyst.commons.resources.podcast_trailer_count
import com.vitorpamplona.amethyst.commons.resources.podcast_untitled
import com.vitorpamplona.amethyst.commons.resources.podcast_your_podcast
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.datasource.MyPodcastFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.Podcasting20PodcastMetadata
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent

/**
 * "Your podcast" — the authoring hub. Shows the creator's own Podcasting-2.0 show (or a create CTA),
 * the new-episode / new-trailer / edit-show entry points, and lists the episodes and trailers the
 * creator has already published (tap to edit). Data is read from [LocalCache] and refreshed each time
 * the screen resumes, so it reflects anything just published from a composer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastAuthoringScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val me = accountViewModel.account.userProfile().pubkeyHex

    // Keep a REQ open for the creator's own catalog while the hub is visible, so the lists below
    // fill in from relays even when nothing was cached yet.
    MyPodcastFilterAssemblerSubscription(accountViewModel)

    // Re-scan LocalCache on each resume (returning from a composer) and whenever the creator's own
    // podcast events arrive over the open REQ, so the lists stay current without a manual refresh.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    LaunchedEffect(me) {
        LocalCache.live.newEventBundles.collect { bundle ->
            val mine =
                bundle.any {
                    val e = it.event
                    e?.pubKey == me &&
                        (
                            e is Podcasting20EpisodeEvent ||
                                e is Podcasting20TrailerEvent ||
                                (e is AppSpecificDataEvent && e.dTag() == Podcasting20PodcastMetadata.PODCAST_METADATA_D_TAG)
                        )
                }
            if (mine) refresh++
        }
    }

    val show =
        remember(refresh) {
            val address = Address(AppSpecificDataEvent.KIND, me, Podcasting20PodcastMetadata.PODCAST_METADATA_D_TAG)
            (LocalCache.addressables.get(address)?.event as? AppSpecificDataEvent)?.let { Podcasting20PodcastMetadata.parse(it) }
        }

    val episodes =
        remember(refresh) {
            LocalCache.addressables
                .filterIntoSet { _, note ->
                    val e = note.event
                    e is Podcasting20EpisodeEvent && e.pubKey == me
                }.mapNotNull { it.event as? Podcasting20EpisodeEvent }
                .sortedByDescending { it.createdAt }
        }

    val trailers =
        remember(refresh) {
            LocalCache.addressables
                .filterIntoSet { _, note ->
                    val e = note.event
                    e is Podcasting20TrailerEvent && e.pubKey == me
                }.mapNotNull { it.event as? Podcasting20TrailerEvent }
                .sortedByDescending { it.createdAt }
        }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.podcast_your_podcast), nav) },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ShowHeaderCard(show, onEdit = { nav.nav(Route.EditPodcastShow) }) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { nav.nav(Route.NewPodcastEpisode()) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(symbol = MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = stringRes(Res.string.podcast_new_episode), modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = { nav.nav(Route.NewPodcastTrailer) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringRes(Res.string.podcast_new_trailer))
                    }
                }
            }

            if (episodes.isNotEmpty()) {
                item { SectionHeader(pluralStringRes(Res.plurals.podcast_episode_count, episodes.size, episodes.size)) }
                items(episodes, key = { it.id }) { ep ->
                    EpisodeRow(
                        title = ep.title() ?: stringRes(Res.string.podcast_untitled),
                        subtitle = episodeSubtitle(ep),
                        onClick = { nav.nav(Route.NewPodcastEpisode(ep.dTag())) },
                    )
                }
            }

            if (trailers.isNotEmpty()) {
                item { SectionHeader(pluralStringRes(Res.plurals.podcast_trailer_count, trailers.size, trailers.size)) }
                items(trailers, key = { it.id }) { tr ->
                    EpisodeRow(
                        title = tr.title() ?: stringRes(Res.string.podcast_untitled),
                        subtitle = tr.url(),
                        onClick = null,
                    )
                }
            }

            if (episodes.isEmpty()) {
                item {
                    Text(
                        text = stringRes(Res.string.podcast_no_episodes_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.grayText,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowHeaderCard(
    show: Podcasting20PodcastMetadata?,
    onEdit: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onEdit)
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val image = show?.showImage()
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (!image.isNullOrBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(model = image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(symbol = MaterialSymbols.Podcasts, contentDescription = null, modifier = Modifier.size(30.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = show?.showTitle()?.takeIf { it.isNotBlank() } ?: stringRes(Res.string.podcast_create_your_show),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (show != null) stringRes(Res.string.podcast_tap_to_edit_show) else stringRes(Res.string.podcast_create_show_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(symbol = MaterialSymbols.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun EpisodeRow(
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onClick != null) {
            Icon(symbol = MaterialSymbols.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Gray)
        }
    }
}

@Composable
private fun episodeSubtitle(ep: Podcasting20EpisodeEvent): String? {
    val season = ep.season()
    val number = ep.number()
    return when {
        season != null && number != null -> stringRes(Res.string.podcast_episode_subtitle_season_episode, season, number)
        number != null -> stringRes(Res.string.podcast_episode_subtitle_episode, number)
        season != null -> stringRes(Res.string.podcast_episode_subtitle_season, season)
        else -> null
    }
}
