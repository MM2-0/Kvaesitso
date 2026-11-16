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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip90DVMs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dvm_card_generic
import com.vitorpamplona.amethyst.commons.resources.dvm_card_personalized
import com.vitorpamplona.amethyst.commons.resources.dvm_card_price_flexible
import com.vitorpamplona.amethyst.commons.resources.dvm_card_price_sats
import com.vitorpamplona.amethyst.commons.resources.dvm_card_price_unknown
import com.vitorpamplona.amethyst.commons.resources.marketplace_shipping_free
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.layouts.LeftPictureLayout
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LikeReaction
import com.vitorpamplona.amethyst.commons.ui.note.ZapReaction
import com.vitorpamplona.amethyst.commons.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms.FavoriteAlgoFeedToggle
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms.observeAppDefinition
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.HalfTopPadding
import com.vitorpamplona.amethyst.commons.ui.theme.RowColSpacing5dp
import com.vitorpamplona.amethyst.commons.ui.theme.SimpleImageBorder
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.nip05
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Immutable
data class DVMCard(
    val name: String,
    val description: String?,
    val cover: String?,
    val amount: String?,
    val personalized: Boolean?,
)

@Composable
fun RenderContentDVMThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // downloads user metadata to pre-load the NIP-65 relays.
    baseNote.author?.let { UserFinderFilterAssemblerSubscription(it, accountViewModel) }
    val card = observeAppDefinition(appDefinitionNote = baseNote, accountViewModel)

    LeftPictureLayout(
        imageFraction = 0.20f,
        onImage = {
            card.cover?.let {
                Box(contentAlignment = BottomStart) {
                    MyAsyncImage(
                        imageUrl = it,
                        contentDescription = card.name,
                        contentScale = ContentScale.Crop,
                        mainImageModifier = Modifier,
                        loadedImageModifier = SimpleImageBorder,
                        accountViewModel = accountViewModel,
                        onLoadingBackground = {
                            baseNote.author?.let { author ->
                                BannerImage(author, SimpleImageBorder, accountViewModel)
                            }
                        },
                        onError = {
                            baseNote.author?.let { author ->
                                BannerImage(author, SimpleImageBorder, accountViewModel)
                            }
                        },
                    )
                }
            } ?: run {
                baseNote.author?.let { author ->
                    BannerImage(author, SimpleImageBorder, accountViewModel)
                }
            }
        },
        onTitleRow = {
            Text(
                text = card.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (baseNote is AddressableNote) {
                FavoriteAlgoFeedToggle(
                    appDefinitionNote = baseNote,
                    accountViewModel = accountViewModel,
                )
            }
        },
        onDescription = {
            card.description?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    modifier = HalfTopPadding,
                )
            }
        },
        onBottomRow = {
            card.amount?.let {
                var color = Color.DarkGray
                var amount = it
                when {
                    card.amount == "free" || card.amount == "0" -> {
                        color = MaterialTheme.colorScheme.secondary
                        amount = stringRes(Res.string.marketplace_shipping_free)
                    }

                    card.amount == "flexible" -> {
                        color = MaterialTheme.colorScheme.primaryContainer
                        amount = stringRes(Res.string.dvm_card_price_flexible)
                    }

                    card.amount == "" -> {
                        color = MaterialTheme.colorScheme.grayText
                        amount = stringRes(Res.string.dvm_card_price_unknown)
                    }

                    else -> {
                        color = MaterialTheme.colorScheme.primary
                        amount = stringRes(Res.string.dvm_card_price_sats, card.amount)
                    }
                }
                Text(
                    textAlign = TextAlign.End,
                    text = " $amount ",
                    color = color,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .border(Dp(.1f), color, shape = RoundedCornerShape(20)),
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = StdHorzSpacer)
            card.personalized?.let {
                var color = Color.DarkGray
                var name = "generic"
                if (card.personalized) {
                    color = MaterialTheme.colorScheme.bitcoinColor
                    name = stringRes(Res.string.dvm_card_personalized)
                } else {
                    color = MaterialTheme.colorScheme.nip05
                    name = stringRes(Res.string.dvm_card_generic)
                }
                Text(
                    textAlign = TextAlign.End,
                    text = " $name ",
                    color = color,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .border(Dp(.1f), color, shape = RoundedCornerShape(20)),
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = RowColSpacing5dp,
            ) {
                LikeReaction(
                    baseNote = baseNote,
                    grayTint = MaterialTheme.colorScheme.onSurface,
                    accountViewModel = accountViewModel,
                    nav,
                )
                Spacer(modifier = StdHorzSpacer)
                ZapReaction(
                    baseNote = baseNote,
                    grayTint = MaterialTheme.colorScheme.onSurface,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        },
    )
}
