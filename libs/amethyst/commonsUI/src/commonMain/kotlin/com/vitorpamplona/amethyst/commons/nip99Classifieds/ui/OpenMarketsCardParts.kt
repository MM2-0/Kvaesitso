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
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketBadge
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ListingStatus
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ShippingCost
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ShippingDuration
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ShippingDurationUnit
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ShippingLimit
import com.vitorpamplona.amethyst.commons.nip99Classifieds.countriesSummary
import com.vitorpamplona.amethyst.commons.nip99Classifieds.countryCodes
import com.vitorpamplona.amethyst.commons.nip99Classifieds.normalized
import com.vitorpamplona.amethyst.commons.nip99Classifieds.shippingCost
import com.vitorpamplona.amethyst.commons.nip99Classifieds.shippingNameFromD
import com.vitorpamplona.amethyst.commons.nip99Classifieds.toShippingDuration
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.classifieds_status_sold
import com.vitorpamplona.amethyst.commons.resources.marketplace_shipping
import com.vitorpamplona.amethyst.commons.resources.marketplace_shipping_free
import com.vitorpamplona.amethyst.commons.resources.open_markets_collection_shipping
import com.vitorpamplona.amethyst.commons.resources.open_markets_cost_split
import com.vitorpamplona.amethyst.commons.resources.open_markets_duration_days
import com.vitorpamplona.amethyst.commons.resources.open_markets_duration_hours
import com.vitorpamplona.amethyst.commons.resources.open_markets_duration_weeks
import com.vitorpamplona.amethyst.commons.resources.open_markets_max_size
import com.vitorpamplona.amethyst.commons.resources.open_markets_max_weight
import com.vitorpamplona.amethyst.commons.resources.open_markets_min_size
import com.vitorpamplona.amethyst.commons.resources.open_markets_min_weight
import com.vitorpamplona.amethyst.commons.resources.open_markets_per_distance
import com.vitorpamplona.amethyst.commons.resources.open_markets_per_volume
import com.vitorpamplona.amethyst.commons.resources.open_markets_per_weight
import com.vitorpamplona.amethyst.commons.resources.open_markets_service_digital
import com.vitorpamplona.amethyst.commons.resources.open_markets_service_express
import com.vitorpamplona.amethyst.commons.resources.open_markets_service_overnight
import com.vitorpamplona.amethyst.commons.resources.open_markets_service_pickup
import com.vitorpamplona.amethyst.commons.resources.open_markets_service_standard
import com.vitorpamplona.amethyst.commons.resources.open_markets_status_ended
import com.vitorpamplona.amethyst.commons.resources.open_markets_status_inactive
import com.vitorpamplona.amethyst.commons.resources.open_markets_status_pre_order
import com.vitorpamplona.amethyst.commons.resources.open_markets_status_removed
import com.vitorpamplona.amethyst.commons.resources.open_markets_status_sold_out
import com.vitorpamplona.amethyst.commons.resources.open_markets_status_unlisted
import com.vitorpamplona.amethyst.commons.resources.open_markets_surcharge
import com.vitorpamplona.amethyst.commons.resources.open_markets_unnamed_shipping_option
import com.vitorpamplona.amethyst.commons.resources.open_markets_via_collection
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.collection.ProductCollectionEvent
import com.vitorpamplona.quartz.nip99Classifieds.shipping.ShippingOptionEvent
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.ShippingService
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import org.jetbrains.compose.resources.StringResource

/** A listing's status pill: error-tinted when it is no longer for sale. */
@Composable
fun ListingStatusBadge(status: ListingStatus) {
    val label =
        when (status) {
            ListingStatus.SOLD -> Res.string.classifieds_status_sold
            ListingStatus.ENDED -> Res.string.open_markets_status_ended
            ListingStatus.INACTIVE -> Res.string.open_markets_status_inactive
            ListingStatus.DELETED -> Res.string.open_markets_status_removed
            ListingStatus.SOLD_OUT -> Res.string.open_markets_status_sold_out
            ListingStatus.UNLISTED -> Res.string.open_markets_status_unlisted
            ListingStatus.PRE_ORDER -> Res.string.open_markets_status_pre_order
        }
    MarketBadge(stringRes(label), warning = status.gone)
}

/** "15 USD", "Free", "+5 USD", "10 USD + 5 EUR". */
@Composable
fun shippingCostText(cost: ShippingCost): String =
    when (cost) {
        ShippingCost.Free -> stringRes(Res.string.marketplace_shipping_free)
        is ShippingCost.Priced -> cost.text
        is ShippingCost.Surcharge -> stringRes(Res.string.open_markets_surcharge, cost.extra)
        is ShippingCost.Split -> stringRes(Res.string.open_markets_cost_split, cost.option, cost.extra)
    }

/** "Express", "Pickup", "Digital delivery"; a service outside the spec as its publisher wrote it. */
@Composable
fun shippingServiceText(service: String?): String? {
    val code = service?.trim()?.lowercase()?.ifEmpty { null } ?: return null
    val known =
        when (ShippingService.of(code)) {
            ShippingService.STANDARD -> Res.string.open_markets_service_standard
            ShippingService.EXPRESS -> Res.string.open_markets_service_express
            ShippingService.OVERNIGHT -> Res.string.open_markets_service_overnight
            ShippingService.PICKUP -> Res.string.open_markets_service_pickup
            null -> if (code == "digital") Res.string.open_markets_service_digital else null
        }
    return known?.let { stringRes(it) } ?: code.replaceFirstChar { it.uppercaseChar() }
}

/** "1–3 days", "24 hours". */
@Composable
fun shippingDurationText(duration: ShippingDuration): String {
    val plural =
        when (duration.unit) {
            ShippingDurationUnit.HOURS -> Res.plurals.open_markets_duration_hours
            ShippingDurationUnit.DAYS -> Res.plurals.open_markets_duration_days
            ShippingDurationUnit.WEEKS -> Res.plurals.open_markets_duration_weeks
        }
    return pluralStringRes(plural, duration.pluralCount(), duration.range())
}

fun ShippingLimit.label(): StringResource =
    when (this) {
        ShippingLimit.MIN_WEIGHT -> Res.string.open_markets_min_weight
        ShippingLimit.MAX_WEIGHT -> Res.string.open_markets_max_weight
        ShippingLimit.MIN_SIZE -> Res.string.open_markets_min_size
        ShippingLimit.MAX_SIZE -> Res.string.open_markets_max_size
        ShippingLimit.PER_WEIGHT -> Res.string.open_markets_per_weight
        ShippingLimit.PER_VOLUME -> Res.string.open_markets_per_volume
        ShippingLimit.PER_DISTANCE -> Res.string.open_markets_per_distance
    }

/** A `[label, value]` fact row, the way the NIP-15 specs list draws them. */
@Composable
fun OpenMarketsFactRow(
    label: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.grayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.6f),
        )
    }
}

/**
 * Shipping references a listing page follows. A tag list has no length limit, and every row loads
 * its option (and every collection its options), so a listing naming hundreds must not open
 * hundreds of lookups.
 */
private const val MAX_LISTING_OPTIONS = 12
private const val MAX_LISTING_COLLECTIONS = 4

/** A collection's options listed on a product's page, per collection. */
private const val MAX_OPTIONS_PER_COLLECTION = 12

/** A shipping reference (an option or a collection) with the extra cost the product adds to it. */
@Immutable
private class CollectionShippingRef(
    val address: Address,
    val extra: String?,
)

@Immutable
private class ListingShippingState(
    val options: ImmutableList<CollectionShippingRef>,
    val collections: ImmutableList<CollectionShippingRef>,
    val currency: String?,
) {
    fun isEmpty() = options.isEmpty() && collections.isEmpty()

    companion object {
        fun from(event: ClassifiedsEvent): ListingShippingState {
            val refs = event.shippingOptions().map { CollectionShippingRef(it.address.normalized(), it.extraCost) }
            return ListingShippingState(
                options =
                    refs
                        .filter { it.address.kind == ShippingOptionEvent.KIND }
                        .distinctBy { it.address }
                        .take(MAX_LISTING_OPTIONS)
                        .toImmutableList(),
                collections =
                    refs
                        .filter { it.address.kind == ProductCollectionEvent.KIND }
                        .distinctBy { it.address }
                        .take(MAX_LISTING_COLLECTIONS)
                        .toImmutableList(),
                currency = event.price()?.currency,
            )
        }
    }
}

/**
 * Every way a listing ships, from its `shipping_option` references: the options it names, each at
 * its own price plus the product's extra cost, then the options of any collection it names, the
 * extra applied to each (Open Markets: "the extra cost should be applied to all shipping options
 * from the collection"). An option the product names itself wins over the same option reached
 * through a collection. An option or collection not loaded yet still says the product ships.
 */
@Composable
fun ListingShippingSection(
    event: ClassifiedsEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val state = remember(event) { ListingShippingState.from(event) }
    if (state.isEmpty()) return
    val own = remember(state) { state.options.map { it.address }.toImmutableSet() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringRes(Res.string.marketplace_shipping),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.options.forEach { ShippingOptionRow(it.address, it.extra, state.currency, null, accountViewModel, nav) }
        CollectionsShipping(state.collections, 0, own, state.currency, accountViewModel, nav)
    }
}

/**
 * The options of the listing's collections, from the [index]th on. Each collection is drawn
 * inside the one before it, so [seen] (the product's own options and every earlier collection's)
 * keeps an option two collections share to one row: the first that names it, as Open Markets
 * reads a product's configuration before its collections'.
 */
@Composable
private fun CollectionsShipping(
    collections: ImmutableList<CollectionShippingRef>,
    index: Int,
    seen: ImmutableSet<Address>,
    currency: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val ref = collections.getOrNull(index) ?: return
    LoadAddressableNote(ref.address) { collectionNote ->
        val collection =
            if (collectionNote != null) {
                val observed by observeNoteEvent<ProductCollectionEvent>(collectionNote, accountViewModel)
                observed
            } else {
                null
            }
        if (collection == null) {
            ShippingLine(
                title = stringRes(Res.string.open_markets_collection_shipping),
                details = shippingNameFromD(ref.address.dTag)?.let { stringRes(Res.string.open_markets_via_collection, it) },
                cost = shippingCost(null, ref.extra, currency)?.let { shippingCostText(it) },
                onClick = null,
            )
            CollectionsShipping(collections, index + 1, seen, currency, accountViewModel, nav)
        } else {
            val options =
                remember(collection, seen) {
                    collection
                        .shippingOptions()
                        .map { it.address.normalized() }
                        .filter { it.kind == ShippingOptionEvent.KIND && it !in seen }
                        .distinct()
                        .take(MAX_OPTIONS_PER_COLLECTION)
                }
            val title = remember(collection) { collection.title()?.trim()?.ifEmpty { null } }
            options.forEach { ShippingOptionRow(it, ref.extra, currency, title, accountViewModel, nav) }
            val next = remember(seen, options) { (seen + options).toImmutableSet() }
            CollectionsShipping(collections, index + 1, next, currency, accountViewModel, nav)
        }
    }
}

/**
 * One shipping option as a listing or collection lists it: title, what it costs this product,
 * and service · duration · countries. Tapping it opens the option.
 */
@Composable
fun ShippingOptionRow(
    address: Address,
    extra: String?,
    productCurrency: String?,
    viaCollection: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { optionNote ->
        val option =
            if (optionNote != null) {
                val observed by observeNoteEvent<ShippingOptionEvent>(optionNote, accountViewModel)
                observed
            } else {
                null
            }
        val via = viaCollection?.let { stringRes(Res.string.open_markets_via_collection, it) }
        if (option == null) {
            ShippingLine(
                title = shippingNameFromD(address.dTag) ?: stringRes(Res.string.open_markets_unnamed_shipping_option),
                details = via,
                cost = shippingCost(null, extra, productCurrency)?.let { shippingCostText(it) },
                onClick = null,
            )
        } else {
            val row = remember(option, extra, productCurrency) { ShippingRowFacts.from(option, extra, productCurrency) }
            val service = shippingServiceText(row.service)
            val duration = row.duration?.let { shippingDurationText(it) }
            ShippingLine(
                title = row.title ?: stringRes(Res.string.open_markets_unnamed_shipping_option),
                details = listOfNotNull(service, duration, row.countries, via).joinToString(" · ").ifEmpty { null },
                cost = row.cost?.let { shippingCostText(it) },
                onClick = { optionNote?.let { routeFor(it, accountViewModel.account) }?.let { nav.nav(it) } },
            )
        }
    }
}

/** A loaded option's row, parsed once per option version and extra cost. */
@Immutable
private class ShippingRowFacts(
    val title: String?,
    val service: String?,
    val duration: ShippingDuration?,
    val countries: String?,
    val cost: ShippingCost?,
) {
    companion object {
        fun from(
            option: ShippingOptionEvent,
            extra: String?,
            productCurrency: String?,
        ) = ShippingRowFacts(
            title = option.title()?.trim()?.ifEmpty { null },
            service = option.service(),
            duration = option.duration()?.toShippingDuration(),
            countries = countriesSummary(option.countryCodes()),
            cost = shippingCost(option.price(), extra, productCurrency),
        )
    }
}

@Composable
private fun ShippingLine(
    title: String,
    details: String?,
    cost: String?,
    onClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            details?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        cost?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}
