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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip99Classifieds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteAndMap
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DisplayAuthorBanner
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.util.showAmountInteger
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
import com.vitorpamplona.quartz.utils.parseBigDecimalOrNull

@Immutable
data class ClassifiedsThumb(
    val image: String?,
    val title: String?,
    val price: PriceTag?,
)

@Composable
fun RenderClassifiedsThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (baseNote.event !is ClassifiedsEvent) return

    val card by observeNoteAndMap(baseNote, accountViewModel) {
        val noteEvent = it.event as? ClassifiedsEvent
        ClassifiedsThumb(
            image = noteEvent?.image(),
            title = noteEvent?.title(),
            price = noteEvent?.price(),
        )
    }

    InnerRenderClassifiedsThumb(card, baseNote, accountViewModel)
}

@Preview
@Composable
fun RenderClassifiedsThumbPreview() {
    Surface(Modifier.size(200.dp)) {
        InnerRenderClassifiedsThumb(
            card =
                ClassifiedsThumb(
                    image = null,
                    title = "Like New",
                    price = PriceTag("800000", "SATS", null),
                ),
            note = Note("hex"),
            mockAccountViewModel(),
        )
    }
}

@Composable
fun InnerRenderClassifiedsThumb(
    card: ClassifiedsThumb,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = BottomStart,
    ) {
        card.image?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: run { DisplayAuthorBanner(note, accountViewModel, Modifier.fillMaxSize()) }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(0.6f))
                .padding(Size5dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            card.title?.let {
                Text(
                    text = it,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
            }

            card.price?.let {
                val priceTag =
                    remember(card) {
                        val newAmount = parseBigDecimalOrNull(it.amount)?.let { showAmountInteger(it) } ?: it.amount

                        if (it.frequency != null && it.currency != null) {
                            "$newAmount ${it.currency}/${it.frequency}"
                        } else if (it.currency != null) {
                            "$newAmount ${it.currency}"
                        } else {
                            newAmount
                        }
                    }

                Text(
                    text = priceTag,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                )
            }
        }
    }
}
