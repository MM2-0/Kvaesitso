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
package com.vitorpamplona.amethyst.commons.nip15Marketplace.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeToMessage
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chips_n_more
import com.vitorpamplona.amethyst.commons.resources.hi_there_is_this_still_available
import com.vitorpamplona.amethyst.commons.resources.marketplace_from_stall
import com.vitorpamplona.amethyst.commons.resources.marketplace_message_seller
import com.vitorpamplona.amethyst.commons.resources.marketplace_specs
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.ui.components.AutoNonlazyGrid
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.CappedChips
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.TonalChip
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip15Marketplace.stall.StallEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Images an event author can list are unbounded; the zoom pager gets this many. */
private const val MAX_IMAGES = 20

/** And the card's grid shows this many of them. */
private const val MAX_GRID_IMAGES = 4

/** Spec rows shown before the "+N more" chip. */
const val MAX_SPEC_ROWS = 8

/** Topic chips shown before the "+N more" chip. */
const val MAX_TOPIC_CHIPS = 6

/** The bordered frame every NIP-15 card draws in, the same one the NIP-99 classifieds use. */
@Composable
fun MarketCardFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder),
        content = content,
    )
}

/** The padded body under a card's images. */
@Composable
fun MarketCardBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** What the card is ("Stall", "Auction"), small and tinted, with optional badges after it. */
@Composable
fun MarketKindLabel(
    symbol: MaterialSymbol,
    label: String,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        trailing()
    }
}

/** The card's name line. */
@Composable
fun MarketTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Free-text description, cut to a few lines in a feed. */
@Composable
fun MarketDescription(
    text: String,
    makeItShort: Boolean,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (makeItShort) 3 else 8,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A price in the theme's primary color, the way the classifieds card draws one. */
@Composable
fun MarketPrice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
    )
}

/** An icon and one quiet line of text. */
@Composable
fun MarketFactLine(
    symbol: MaterialSymbol,
    text: String,
    color: Color = MaterialTheme.colorScheme.grayText,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(symbol = symbol, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A short status pill ("Out of stock", "Ended"), error-tinted when [warning]. */
@Composable
fun MarketBadge(
    text: String,
    warning: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (warning) scheme.onErrorContainer else scheme.onSecondaryContainer,
        maxLines = 1,
        modifier =
            Modifier
                .clip(SmallBorder)
                .background(if (warning) scheme.errorContainer else scheme.secondaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** The event's image URLs as gallery items, capped, built once per event. */
@Composable
fun rememberMarketImages(
    urls: ImmutableList<String>,
    note: Note,
): ImmutableList<MediaUrlImage> =
    remember(urls) {
        val uri = note.toNostrUri()
        urls.take(MAX_IMAGES).map { MediaUrlImage(url = it, uri = uri) }.toImmutableList()
    }

/**
 * The product's pictures in the grid the classifieds card uses; tapping one opens the zoom pager
 * over all of them.
 */
@Composable
fun MarketImageGallery(
    images: ImmutableList<MediaUrlImage>,
    accountViewModel: AccountViewModel,
) {
    if (images.isEmpty()) return
    if (images.size == 1) {
        // One picture keeps its own shape; the square grid would leave a band of empty card.
        ZoomableContentView(
            content = images[0],
            images = images,
            roundedCorner = false,
            contentScale = ContentScale.FillWidth,
            accountViewModel = accountViewModel,
        )
        return
    }
    val shown = remember(images) { images.take(MAX_GRID_IMAGES) }
    AutoNonlazyGrid(shown.size) {
        ZoomableContentView(
            content = shown[it],
            images = images,
            roundedCorner = false,
            contentScale = ContentScale.Crop,
            accountViewModel = accountViewModel,
        )
    }
}

/** One `[key, value]` entry of a NIP-15 `specs` array. */
@Immutable
class MarketSpec(
    val key: String,
    val value: String,
)

/** The `[key, value]` pairs Quartz read, as rows a card can hold. */
fun List<List<String>>.toMarketSpecs(): ImmutableList<MarketSpec> = mapNotNull { if (it.size >= 2) MarketSpec(it[0], it[1]) else null }.toImmutableList()

/** NIP-15 `specs` as label/value rows; the first [MAX_SPEC_ROWS], then a "+N more" chip. */
@Composable
fun MarketSpecs(specs: ImmutableList<MarketSpec>) {
    if (specs.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded || specs.size <= MAX_SPEC_ROWS) specs else specs.subList(0, MAX_SPEC_ROWS)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringRes(Res.string.marketplace_specs),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        shown.forEach { spec ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = spec.key,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.4f),
                )
                Text(
                    text = spec.value,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.6f),
                )
            }
        }
        val hidden = specs.size - shown.size
        if (hidden > 0) {
            TonalChip(
                text = pluralStringRes(Res.plurals.chips_n_more, hidden, hidden),
                emphasized = true,
                modifier = Modifier.padding(top = 2.dp).clickable { expanded = true },
            )
        }
    }
}

/** The `t` categories as hashtag chips that open the hashtag feed, capped. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarketTopics(
    topics: ImmutableList<String>,
    nav: INav,
) {
    if (topics.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CappedChips(topics, MAX_TOPIC_CHIPS) { topic ->
            TonalChip(text = "#$topic", modifier = Modifier.clickable { nav.nav(Route.Hashtag(topic)) })
        }
    }
}

/**
 * "From <stall name>": the stall a product or auction is sold from, by its address (same merchant,
 * `d` = `stall_id`). The stall loads like any quoted event while the card is on screen; nothing
 * is drawn until it has a name. Tapping it opens the stall.
 */
@Composable
fun MarketStallLine(
    stallAddress: Address?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (stallAddress == null) return
    LoadAddressableNote(stallAddress) { stallNote ->
        if (stallNote != null) {
            val stall by observeNoteEvent<StallEvent>(stallNote, accountViewModel)
            val name = remember(stall) { stall?.stallData()?.name }
            if (name != null) {
                MarketFactLine(
                    symbol = MaterialSymbols.Storefront,
                    text = stringRes(Res.string.marketplace_from_stall, name),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { routeFor(stallNote, accountViewModel.account)?.let { nav.nav(it) } },
                )
            }
        }
    }
}

/**
 * Opens a DM with the seller, the draft quoting the listing, like the classifieds thread view's
 * "Send the seller a message".
 */
@Composable
fun MessageSellerButton(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val author = note.author ?: return
    if (accountViewModel.isLoggedUser(author)) return
    val greeting = stringRes(Res.string.hi_there_is_this_still_available)
    OutlinedButton(
        onClick = {
            nav.nav {
                routeToMessage(
                    author,
                    draftMessage = note.toNostrUri() + "\n\n" + greeting,
                    accountViewModel = accountViewModel,
                )
            }
        },
    ) {
        Icon(symbol = MaterialSymbols.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringRes(Res.string.marketplace_message_seller))
    }
}
