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
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip15Marketplace.formatMarketPrice
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.marketplace_in_stock
import com.vitorpamplona.amethyst.commons.resources.marketplace_out_of_stock
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip15Marketplace.product.ProductEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
private class ProductCardState(
    val name: String,
    val description: String?,
    val images: ImmutableList<String>,
    val price: String?,
    /** null: unlimited, nothing to say; 0: sold out; otherwise the count. */
    val quantity: Int?,
    val specs: ImmutableList<MarketSpec>,
    val topics: ImmutableList<String>,
    val stallAddress: Address?,
) {
    companion object {
        fun from(event: ProductEvent): ProductCardState? {
            val data = event.productData() ?: return null
            val name = data.name ?: return null
            return ProductCardState(
                name = name,
                description = data.description,
                images = data.images.toImmutableList(),
                price = data.price?.let { formatMarketPrice(it, data.currency) },
                quantity = data.quantity?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt(),
                specs = data.specs.toMarketSpecs(),
                topics = event.categories().distinct().toImmutableList(),
                stallAddress = event.stallAddress(),
            )
        }
    }
}

/**
 * A NIP-15 product (kind 30018): pictures, name, price, stock, specifications, categories, the
 * stall it is sold from and a way to message the seller. Observed, not read once: the merchant
 * replaces it at the same address (a price change, the last one sold).
 */
@Composable
fun RenderProduct(
    note: Note,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<ProductEvent>(note, accountViewModel)
    val state = remember(event) { event?.let { ProductCardState.from(it) } } ?: return
    val images = rememberMarketImages(state.images, note)

    MarketCardFrame {
        MarketImageGallery(images, accountViewModel)

        MarketCardBody {
            MarketTitle(state.name)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.price?.let { MarketPrice(it) }
                when (val quantity = state.quantity) {
                    null -> {}
                    0 -> MarketBadge(stringRes(Res.string.marketplace_out_of_stock), warning = true)
                    else ->
                        Text(
                            text = pluralStringRes(Res.plurals.marketplace_in_stock, quantity, quantity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.grayText,
                        )
                }
            }

            MarketStallLine(state.stallAddress, accountViewModel, nav)

            state.description?.let { MarketDescription(it, makeItShort) }

            MarketSpecs(state.specs)

            MarketTopics(state.topics, nav)

            MessageSellerButton(note, accountViewModel, nav)
        }
    }
}
