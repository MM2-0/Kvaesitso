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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.podcast_by_author
import com.vitorpamplona.amethyst.commons.resources.podcast_completed
import com.vitorpamplona.amethyst.commons.resources.podcast_explicit
import com.vitorpamplona.amethyst.commons.resources.podcast_support_show
import com.vitorpamplona.amethyst.commons.resources.podcast_view_episodes
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.PodcastBadge
import com.vitorpamplona.amethyst.commons.ui.note.PodcastLinkChip
import com.vitorpamplona.amethyst.commons.ui.note.PodcastValueSplits
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.resolvePodcastShow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderPodcastMetadata(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event ?: return
    // Resolves NIP-F4 kind:10154 and Podcasting-2.0 kind:30078 shows to one PodcastShow view.
    val show = remember(noteEvent) { resolvePodcastShow(noteEvent) } ?: return

    val title = remember(noteEvent) { show.showTitle() }
    val author = remember(noteEvent) { show.showAuthor() }
    val image = remember(noteEvent) { show.showImage() }
    val description = remember(noteEvent) { show.showDescription() }
    val websites = remember(noteEvent) { show.showWebsites() }
    val categories = remember(noteEvent) { show.showCategories() }
    val fundingUrls = remember(noteEvent) { show.showFundingUrls() }
    val isExplicit = remember(noteEvent) { show.showIsExplicit() }
    val isComplete = remember(noteEvent) { show.showIsComplete() }
    val copyright = remember(noteEvent) { show.showCopyright() }
    val value = remember(noteEvent) { show.showValue() }
    // In both drafts the show's author pubkey IS the podcast id used to open its dedicated
    // screen with the full episode list (episodes are authored by the same key).
    val podcastPubkey = remember(noteEvent) { noteEvent.pubKey }

    Column(
        MaterialTheme.colorScheme.replyModifier.clickable {
            nav.nav(Route.Podcast(podcastPubkey))
        },
    ) {
        PodcastCoverCard(image, note, accountViewModel)

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Size5dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                PodcastBookmarkButton(note, accountViewModel)
            }

            author?.let {
                Text(
                    text = stringRes(Res.string.podcast_by_author, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isExplicit || isComplete || categories.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isComplete) {
                        PodcastBadge(
                            label = stringRes(Res.string.podcast_completed),
                            symbol = MaterialSymbols.CheckCircle,
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    if (isExplicit) {
                        PodcastBadge(
                            label = stringRes(Res.string.podcast_explicit),
                            symbol = null,
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    categories.forEach { category ->
                        PodcastBadge(
                            label = category,
                            symbol = MaterialSymbols.Tag,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            description?.takeIf { !makeItShort }?.let {
                val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

                TranslatableRichTextViewer(
                    content = it,
                    canPreview = canPreview,
                    quotesLeft = 1,
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id = note.idHex,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            value?.takeIf { !makeItShort }?.let {
                PodcastValueSplits(value = it)
            }

            if (fundingUrls.isNotEmpty() && !makeItShort) {
                val uriHandler = LocalUriHandler.current
                Button(
                    onClick = { runCatching { uriHandler.openUri(fundingUrls.first()) } },
                    modifier = Modifier.fillMaxWidth().padding(top = Size5dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = stringRes(Res.string.podcast_support_show),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            if (websites.isNotEmpty() && !makeItShort) {
                val uriHandler = LocalUriHandler.current
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Size5dp),
                    verticalArrangement = Arrangement.spacedBy(Size5dp),
                ) {
                    websites.forEach { website ->
                        PodcastLinkChip(website, MaterialSymbols.Public) { runCatching { uriHandler.openUri(website) } }
                    }
                }
            }

            copyright?.takeIf { !makeItShort }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.padding(top = Size5dp),
                )
            }

            // Affordance that this card opens a full show page with every episode.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Size5dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Podcasts,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringRes(Res.string.podcast_view_episodes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    symbol = MaterialSymbols.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
