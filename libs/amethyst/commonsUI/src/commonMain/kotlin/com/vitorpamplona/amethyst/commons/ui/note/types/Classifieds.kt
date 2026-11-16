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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip99Classifieds.listingStatus
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ui.ListingStatusBadge
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.ui.components.AutoNonlazyGrid
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.utils.parseBigDecimalOrNull
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

private val PriceTagModifier =
    Modifier
        .clip(SmallBorder)
        .padding(start = 5.dp)

@Composable
fun RenderClassifieds(
    noteEvent: ClassifiedsEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val imageSet: ImmutableList<MediaUrlImage>? =
        remember(noteEvent) {
            noteEvent
                .imageMetas()
                .ifEmpty { null }
                ?.map {
                    MediaUrlImage(
                        url = it.url,
                        description = it.alt,
                        hash = it.hash,
                        blurhash = it.blurhash,
                        dim = it.dimension,
                        uri = note.toNostrUri(),
                        mimeType = it.mimeType,
                        thumbhash = it.thumbhash,
                    )
                }?.toImmutableList()
        }
    val title = remember(noteEvent) { noteEvent.title() }
    val summary =
        remember(noteEvent) {
            noteEvent.summary() ?: noteEvent.content.take(200).ifBlank { null }
        }
    val price = remember(noteEvent) { noteEvent.price() }
    val location = remember(noteEvent) { noteEvent.location()?.ifBlank { null } }
    val status = remember(noteEvent) { noteEvent.listingStatus() }

    Row(
        modifier =
            Modifier
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ),
    ) {
        Column {
            Row {
                imageSet?.let { images ->
                    AutoNonlazyGrid(images.size) {
                        ZoomableContentView(
                            content = images[it],
                            images = images,
                            roundedCorner = false,
                            contentScale = ContentScale.Crop,
                            accountViewModel = accountViewModel,
                        )
                    }
                } ?: run {
                    DefaultImageHeader(note, accountViewModel, Modifier.fillMaxWidth())
                }
            }

            Row(
                Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                status?.let {
                    Spacer(Modifier.width(5.dp))
                    ListingStatusBadge(it)
                }

                price?.let {
                    val priceTag =
                        remember(noteEvent) {
                            val newAmount =
                                parseBigDecimalOrNull(price.amount)?.let { showAmount(it) }
                                    ?: price.amount

                            if (price.frequency != null && price.currency != null) {
                                "$newAmount ${price.currency}/${price.frequency}"
                            } else if (price.currency != null) {
                                "$newAmount ${price.currency}"
                            } else {
                                newAmount
                            }
                        }

                    Text(
                        text = priceTag,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = PriceTagModifier,
                    )
                }
            }

            if (summary != null || location != null) {
                Row(
                    Modifier.padding(start = 10.dp, end = 10.dp, top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    summary?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = Color.Gray,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            location?.let {
                Row(
                    Modifier.padding(start = 10.dp, end = 10.dp, top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        symbol = MaterialSymbols.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.grayText,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = DoubleVertSpacer)
        }
    }
}
