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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip15Marketplace.formatMarketPrice
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketCardBody
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketCardFrame
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketDescription
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketKindLabel
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketPrice
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketTitle
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ShippingDuration
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ShippingLimitFact
import com.vitorpamplona.amethyst.commons.nip99Classifieds.countryCodes
import com.vitorpamplona.amethyst.commons.nip99Classifieds.limits
import com.vitorpamplona.amethyst.commons.nip99Classifieds.parseMarketAmount
import com.vitorpamplona.amethyst.commons.nip99Classifieds.regionCodes
import com.vitorpamplona.amethyst.commons.nip99Classifieds.toShippingDuration
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.marketplace_merchant
import com.vitorpamplona.amethyst.commons.resources.marketplace_shipping_free
import com.vitorpamplona.amethyst.commons.resources.open_markets_carrier
import com.vitorpamplona.amethyst.commons.resources.open_markets_pick_up_at
import com.vitorpamplona.amethyst.commons.resources.open_markets_priced_by_seller
import com.vitorpamplona.amethyst.commons.resources.open_markets_regions
import com.vitorpamplona.amethyst.commons.resources.open_markets_service
import com.vitorpamplona.amethyst.commons.resources.open_markets_shipping_option
import com.vitorpamplona.amethyst.commons.resources.open_markets_ships_from
import com.vitorpamplona.amethyst.commons.resources.open_markets_ships_to
import com.vitorpamplona.amethyst.commons.resources.open_markets_takes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.LabeledInlineUser
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip99Classifieds.shipping.ShippingOptionEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Countries listed on the card before it says how many more. */
private const val MAX_COUNTRIES = 30

@Immutable
private class ShippingOptionCardState(
    val title: String,
    /** null: unpriced; "" never; a zero price is [isFree]. */
    val price: String?,
    val isFree: Boolean,
    val service: String?,
    val isPickup: Boolean,
    val carrier: String?,
    val countries: String?,
    val regions: String?,
    val duration: ShippingDuration?,
    val location: String?,
    val description: String?,
    val limits: ImmutableList<ShippingLimitFact>,
) {
    companion object {
        fun from(event: ShippingOptionEvent): ShippingOptionCardState? {
            val title = event.title()?.trim()?.ifEmpty { null } ?: return null
            val price = event.price()
            val amount = price?.let { parseMarketAmount(it.amount) }
            val currency = price?.currency?.trim()?.ifEmpty { null }
            val countries = event.countryCodes()
            return ShippingOptionCardState(
                title = title,
                price = if (amount != null && currency != null && amount > 0) formatMarketPrice(amount, currency) else null,
                isFree = amount == 0.0 && currency != null,
                service = event.service(),
                isPickup = event.isPickup(),
                carrier = event.carrier()?.trim()?.ifEmpty { null },
                countries =
                    countries
                        .take(MAX_COUNTRIES)
                        .joinToString(", ")
                        .ifEmpty { null }
                        ?.let { if (countries.size > MAX_COUNTRIES) "$it +${countries.size - MAX_COUNTRIES}" else it },
                regions = event.regionCodes().joinToString(", ").ifEmpty { null },
                duration = event.duration()?.toShippingDuration(),
                location = event.location()?.trim()?.ifEmpty { null },
                description = event.content.trim().ifEmpty { null },
                limits = event.limits().toImmutableList(),
            )
        }
    }
}

/**
 * An Open Markets shipping option (kind 30406): what it costs, how it travels, where it goes, how
 * long it takes, and its package limits. Shopstr's escrow records on this kind parse as another
 * class and never reach this card. Observed, not read once: the seller replaces it in place.
 */
@Composable
fun RenderShippingOption(
    note: Note,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<ShippingOptionEvent>(note, accountViewModel)
    val state = remember(event) { event?.let { ShippingOptionCardState.from(it) } } ?: return

    MarketCardFrame {
        MarketCardBody {
            MarketKindLabel(MaterialSymbols.Public, stringRes(Res.string.open_markets_shipping_option))
            MarketTitle(state.title)
            when {
                state.isFree -> MarketPrice(stringRes(Res.string.marketplace_shipping_free))
                state.price != null -> MarketPrice(state.price)
                else ->
                    Text(
                        text = stringRes(Res.string.open_markets_priced_by_seller),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.grayText,
                    )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shippingServiceText(state.service)?.let { OpenMarketsFactRow(stringRes(Res.string.open_markets_service), it) }
                state.carrier?.let { OpenMarketsFactRow(stringRes(Res.string.open_markets_carrier), it) }
                state.countries?.let { OpenMarketsFactRow(stringRes(Res.string.open_markets_ships_to), it) }
                state.regions?.let { OpenMarketsFactRow(stringRes(Res.string.open_markets_regions), it) }
                state.duration?.let { OpenMarketsFactRow(stringRes(Res.string.open_markets_takes), shippingDurationText(it)) }
                state.location?.let {
                    OpenMarketsFactRow(stringRes(if (state.isPickup) Res.string.open_markets_pick_up_at else Res.string.open_markets_ships_from), it)
                }
                state.limits.forEach { OpenMarketsFactRow(stringRes(it.limit.label()), it.value) }
            }

            state.description?.let { MarketDescription(it, makeItShort) }

            note.author?.let {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    LabeledInlineUser(stringRes(Res.string.marketplace_merchant), it.pubkeyHex, accountViewModel, nav)
                }
            }
        }
    }
}
