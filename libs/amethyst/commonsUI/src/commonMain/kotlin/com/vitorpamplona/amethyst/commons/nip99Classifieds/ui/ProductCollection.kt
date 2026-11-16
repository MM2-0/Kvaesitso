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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.nip15Marketplace.formatMarketPrice
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketCardBody
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketCardFrame
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketDescription
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketFactLine
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketImageGallery
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketKindLabel
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketTitle
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketTopics
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.rememberMarketImages
import com.vitorpamplona.amethyst.commons.nip99Classifieds.listingStatus
import com.vitorpamplona.amethyst.commons.nip99Classifieds.normalized
import com.vitorpamplona.amethyst.commons.nip99Classifieds.parseMarketAmount
import com.vitorpamplona.amethyst.commons.nip99Classifieds.shows
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chips_n_more
import com.vitorpamplona.amethyst.commons.resources.marketplace_merchant
import com.vitorpamplona.amethyst.commons.resources.marketplace_shipping
import com.vitorpamplona.amethyst.commons.resources.open_markets_collection
import com.vitorpamplona.amethyst.commons.resources.open_markets_products
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DisplayAuthorBanner
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.LabeledInlineUser
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.collection.ProductCollectionEvent
import com.vitorpamplona.quartz.nip99Classifieds.shipping.ShippingOptionEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Products a card shows in a feed; an `a` list has no length limit, and every tile loads its
 * product. Feeds and quotes alike: the feed passes `makeItShort = false` too, so only [isPage]
 * (the thread view's master note) can tell a page from a card.
 */
private const val FEED_PRODUCTS = 4

/** And the collection's own page. */
private const val PAGE_PRODUCTS = 24

/** The collection's shipping options a feed card lists, and its page. */
private const val FEED_SHIPPING = 3
private const val PAGE_SHIPPING = 12

@Immutable
private class CollectionCardState(
    val title: String,
    val summary: String?,
    val description: String?,
    val images: ImmutableList<String>,
    val location: String?,
    val topics: ImmutableList<String>,
    val products: ImmutableList<Address>,
    val shipping: ImmutableList<Address>,
) {
    companion object {
        fun from(event: ProductCollectionEvent): CollectionCardState? {
            val title = event.title()?.trim()?.ifEmpty { null } ?: return null
            val summary = event.summary()?.trim()?.ifEmpty { null }
            return CollectionCardState(
                title = title,
                summary = summary,
                description =
                    event.content
                        .trim()
                        .ifEmpty { null }
                        ?.takeIf { it != summary },
                images = event.image()?.let { persistentListOf(it) } ?: persistentListOf(),
                location = event.location()?.trim()?.ifEmpty { null },
                topics = event.categories().distinct().toImmutableList(),
                products =
                    event
                        .products()
                        .map { it.normalized() }
                        .distinct()
                        .toImmutableList(),
                shipping =
                    event
                        .shippingOptions()
                        .map { it.address.normalized() }
                        .filter { it.kind == ShippingOptionEvent.KIND }
                        .distinct()
                        .toImmutableList(),
            )
        }
    }
}

/**
 * An Open Markets product collection (kind 30405): its picture, name, summary, the products it
 * names and the shipping options it offers them, a few in a feed and more on its own page
 * ([isPage]). Products load while the card is on screen and
 * are drawn as the marketplace's thumbnails; another seller's product shows only when it files
 * itself under the collection too, and one no longer for sale is left out. A hidden
 * (`visibility: hidden`) product does show: a collection is how an unlisted product is reached.
 * Shopstr's encrypted carts on this kind parse as another class and never reach this card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderProductCollection(
    note: Note,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    isPage: Boolean = false,
) {
    val event by observeNoteEvent<ProductCollectionEvent>(note, accountViewModel)
    val collection = event ?: return
    val state = remember(collection) { CollectionCardState.from(collection) } ?: return
    val images = rememberMarketImages(state.images, note)

    MarketCardFrame {
        MarketImageGallery(images, accountViewModel)

        MarketCardBody {
            MarketKindLabel(MaterialSymbols.CollectionsBookmark, stringRes(Res.string.open_markets_collection))
            MarketTitle(state.title)
            state.summary?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = if (makeItShort) 2 else 4)
            }
            state.description?.let { MarketDescription(it, makeItShort) }
            state.location?.let { MarketFactLine(MaterialSymbols.LocationOn, it) }

            if (state.products.isNotEmpty()) {
                MarketFactLine(
                    MaterialSymbols.Storefront,
                    pluralStringRes(Res.plurals.open_markets_products, state.products.size, state.products.size),
                )
                val limit = if (isPage) PAGE_PRODUCTS else FEED_PRODUCTS
                val shown = remember(state, limit) { state.products.take(limit) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { CollectionProduct(it, collection, accountViewModel, nav) }
                }
                val more = state.products.size - shown.size
                if (more > 0) {
                    Text(
                        text = pluralStringRes(Res.plurals.chips_n_more, more, more),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }
            }

            MarketTopics(state.topics, nav)

            if (state.shipping.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringRes(Res.string.marketplace_shipping),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The collection's own options, priced as they are: a collection's extra cost
                    // is added per product, in the product's money, on the product's page.
                    val shown = remember(state, isPage) { state.shipping.take(if (isPage) PAGE_SHIPPING else FEED_SHIPPING) }
                    shown.forEach { ShippingOptionRow(it, null, null, null, accountViewModel, nav) }
                    val more = state.shipping.size - shown.size
                    if (more > 0) {
                        Text(
                            text = pluralStringRes(Res.plurals.chips_n_more, more, more),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.grayText,
                        )
                    }
                }
            }

            note.author?.let {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    LabeledInlineUser(stringRes(Res.string.marketplace_merchant), it.pubkeyHex, accountViewModel, nav)
                }
            }
        }
    }
}

/** A product tile's width: two side by side on a phone, more on a wide window. */
private val TILE_WIDTH = 150.dp

/** One product of the collection, as a tappable tile, once it has loaded and qualifies. */
@Composable
private fun CollectionProduct(
    address: Address,
    collection: ProductCollectionEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { productNote ->
        if (productNote == null) return@LoadAddressableNote
        val product by observeNoteEvent<ClassifiedsEvent>(productNote, accountViewModel)
        val listing = product ?: return@LoadAddressableNote
        val tile = remember(listing, collection) { ProductTile.from(listing, collection) } ?: return@LoadAddressableNote
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
                Modifier
                    .width(TILE_WIDTH)
                    .clip(SmallBorder)
                    .clickable { routeFor(productNote, accountViewModel.account)?.let { nav.nav(it) } },
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(SmallBorder)) {
                if (tile.image != null) {
                    AsyncImage(
                        model = tile.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    DisplayAuthorBanner(productNote, accountViewModel, Modifier.fillMaxSize())
                }
            }
            Text(
                text = tile.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            tile.price?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** What a product tile shows, read once per product version. */
@Immutable
private class ProductTile(
    val title: String,
    val image: String?,
    val price: String?,
) {
    companion object {
        /** Null for a product the collection does not show, or one no longer for sale. */
        fun from(
            listing: ClassifiedsEvent,
            collection: ProductCollectionEvent,
        ): ProductTile? {
            if (!collection.shows(listing) || listing.listingStatus()?.gone == true) return null
            val title = listing.title()?.trim()?.ifEmpty { null } ?: return null
            val price = listing.price()
            return ProductTile(
                title = title,
                image = listing.image(),
                price = price?.let { tag -> parseMarketAmount(tag.amount)?.let { formatMarketPrice(it, tag.currency) } ?: tag.amount },
            )
        }
    }
}
