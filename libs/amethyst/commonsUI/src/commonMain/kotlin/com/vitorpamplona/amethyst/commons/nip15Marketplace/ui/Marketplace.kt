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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chips_n_more
import com.vitorpamplona.amethyst.commons.resources.marketplace_market
import com.vitorpamplona.amethyst.commons.resources.marketplace_merchants
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip15Marketplace.marketplace.MarketplaceEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Merchant avatars drawn before the "+N more" count. */
private const val MAX_MERCHANT_AVATARS = 10

@Immutable
private class MarketplaceCardState(
    val name: String?,
    val about: String?,
    val picture: String?,
    val banner: String?,
    val shownMerchants: ImmutableList<HexKey>,
    val merchantCount: Int,
) {
    companion object {
        fun from(event: MarketplaceEvent): MarketplaceCardState? {
            val data = event.marketplaceData() ?: return null
            val merchants = event.merchants().distinct()
            return MarketplaceCardState(
                name = data.name,
                about = data.about,
                picture = data.ui?.picture?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                banner = data.ui?.banner?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                shownMerchants = merchants.take(MAX_MERCHANT_AVATARS).toImmutableList(),
                merchantCount = merchants.size,
            )
        }
    }
}

/**
 * A NIP-15 marketplace (kind 30019): a curated set of merchants with a name, a logo and a banner.
 * Observed, not read once: its curator replaces it at the same address.
 */
@Composable
fun RenderMarketplace(
    note: Note,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<MarketplaceEvent>(note, accountViewModel)
    val state = remember(event) { event?.let { MarketplaceCardState.from(it) } } ?: return

    MarketCardFrame {
        state.banner?.let {
            MyAsyncImage(
                imageUrl = it,
                contentDescription = stringRes(Res.string.preview_card_image_for, it),
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxWidth().aspectRatio(3f),
                loadedImageModifier = Modifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = null,
                onError = null,
            )
        }

        MarketCardBody {
            MarketKindLabel(MaterialSymbols.Storefront, stringRes(Res.string.marketplace_market))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.picture?.let {
                    MyAsyncImage(
                        imageUrl = it,
                        contentDescription = stringRes(Res.string.preview_card_image_for, it),
                        contentScale = ContentScale.Crop,
                        mainImageModifier = Modifier.size(44.dp).clip(CircleShape),
                        loadedImageModifier = Modifier,
                        accountViewModel = accountViewModel,
                        onLoadingBackground = null,
                        onError = null,
                    )
                }
                Column(Modifier.weight(1f)) {
                    MarketTitle(state.name ?: stringRes(Res.string.marketplace_market))
                    if (state.merchantCount > 0) {
                        Text(
                            text = pluralStringRes(Res.plurals.marketplace_merchants, state.merchantCount, state.merchantCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.grayText,
                        )
                    }
                }
            }

            state.about?.let { MarketDescription(it, makeItShort) }

            if (state.shownMerchants.isNotEmpty()) {
                MerchantAvatars(state, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun MerchantAvatars(
    state: MarketplaceCardState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        state.shownMerchants.forEach { merchant ->
            UserPicture(merchant, 28.dp, accountViewModel = accountViewModel, nav = nav)
        }
        val hidden = state.merchantCount - state.shownMerchants.size
        if (hidden > 0) {
            Text(
                text = pluralStringRes(Res.plurals.chips_n_more, hidden, hidden),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}
