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
package com.vitorpamplona.amethyst.commons.nip99Classifieds.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketFactLine
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketSpec
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketSpecs
import com.vitorpamplona.amethyst.commons.nip99Classifieds.acknowledges
import com.vitorpamplona.amethyst.commons.nip99Classifieds.normalized
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.marketplace_in_stock
import com.vitorpamplona.amethyst.commons.resources.open_markets_digital
import com.vitorpamplona.amethyst.commons.resources.open_markets_in_collection
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.collection.ProductCollectionEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Collections a listing page names before it stops. */
private const val MAX_COLLECTIONS = 6

@Immutable
private class ListingDetailsState(
    /** Positive stock only: zero is the "Sold out" badge, absent is untracked. */
    val inStock: Int?,
    val isDigital: Boolean,
    val specs: ImmutableList<MarketSpec>,
    val collections: ImmutableList<Address>,
) {
    companion object {
        fun from(event: ClassifiedsEvent) =
            ListingDetailsState(
                inStock =
                    event
                        .stock()
                        ?.takeIf { it > 0 }
                        ?.coerceAtMost(Int.MAX_VALUE.toLong())
                        ?.toInt(),
                isDigital = event.productType()?.isDigital() == true,
                specs = event.specs().map { MarketSpec(it.key, it.value) }.toImmutableList(),
                collections =
                    event
                        .collections()
                        .map { it.normalized() }
                        .distinct()
                        .take(MAX_COLLECTIONS)
                        .toImmutableList(),
            )
    }
}

/**
 * The Open Markets parts of a listing's own page: stock, whether it is a digital good, its
 * specifications, the collections it files itself under, and every way it ships.
 */
@Composable
fun ClassifiedsOpenMarketsDetails(
    event: ClassifiedsEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val state = remember(event) { ListingDetailsState.from(event) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 10.dp),
    ) {
        state.inStock?.let { MarketFactLine(MaterialSymbols.Storefront, pluralStringRes(Res.plurals.marketplace_in_stock, it, it)) }
        if (state.isDigital) MarketFactLine(MaterialSymbols.Download, stringRes(Res.string.open_markets_digital))
        state.collections.forEach { CollectionLine(it, event, accountViewModel, nav) }
        MarketSpecs(state.specs)
        ListingShippingSection(event, accountViewModel, nav)
    }
}

/**
 * "In <collection>", opening it; nothing until the collection has loaded with a name, and nothing
 * for another seller's collection that does not name this listing.
 */
@Composable
private fun CollectionLine(
    address: Address,
    listing: ClassifiedsEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { collectionNote ->
        if (collectionNote == null) return@LoadAddressableNote
        val collection by observeNoteEvent<ProductCollectionEvent>(collectionNote, accountViewModel)
        val title =
            remember(collection, listing) {
                collection
                    ?.takeIf { it.acknowledges(listing) }
                    ?.title()
                    ?.trim()
                    ?.ifEmpty { null }
            } ?: return@LoadAddressableNote
        MarketFactLine(
            symbol = MaterialSymbols.CollectionsBookmark,
            text = stringRes(Res.string.open_markets_in_collection, title),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { routeFor(collectionNote, accountViewModel.account)?.let { nav.nav(it) } },
        )
    }
}
