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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
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
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip15Marketplace.formatMarketPrice
import com.vitorpamplona.amethyst.commons.nip15Marketplace.marketCurrencyLabel
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chips_n_more
import com.vitorpamplona.amethyst.commons.resources.marketplace_merchant
import com.vitorpamplona.amethyst.commons.resources.marketplace_prices_in
import com.vitorpamplona.amethyst.commons.resources.marketplace_shipping
import com.vitorpamplona.amethyst.commons.resources.marketplace_shipping_free
import com.vitorpamplona.amethyst.commons.resources.marketplace_shipping_regions
import com.vitorpamplona.amethyst.commons.resources.marketplace_stall
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.LabeledInlineUser
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip15Marketplace.stall.ShippingZone
import com.vitorpamplona.quartz.nip15Marketplace.stall.StallEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Shipping zones listed before the "+N more" line. */
private const val MAX_SHIPPING_ZONES = 6

/** A shipping zone, formatted once: its name, its base cost in the stall's currency, how many regions. */
@Immutable
private class ShippingZoneLine(
    val name: String?,
    val cost: String?,
    val isFree: Boolean,
    val regionCount: Int,
)

@Immutable
private class StallCardState(
    val name: String,
    val description: String?,
    val currency: String?,
    val zones: ImmutableList<ShippingZoneLine>,
    val hiddenZones: Int,
) {
    companion object {
        fun from(event: StallEvent): StallCardState? {
            val data = event.stallData() ?: return null
            val name = data.name ?: return null
            return StallCardState(
                name = name,
                description = data.description,
                currency = marketCurrencyLabel(data.currency),
                zones =
                    data.shipping
                        .take(MAX_SHIPPING_ZONES)
                        .map { it.toLine(data.currency) }
                        .toImmutableList(),
                hiddenZones = (data.shipping.size - MAX_SHIPPING_ZONES).coerceAtLeast(0),
            )
        }

        private fun ShippingZone.toLine(currency: String?) =
            ShippingZoneLine(
                name = name ?: id,
                cost = cost?.let { formatMarketPrice(it, currency) },
                isFree = cost == 0.0,
                regionCount = destinations().size,
            )
    }
}

/**
 * A NIP-15 stall (kind 30017): a merchant's shop, with its currency and where it ships. Observed,
 * not read once: the merchant replaces it at the same address.
 */
@Composable
fun RenderStall(
    note: Note,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<StallEvent>(note, accountViewModel)
    val state = remember(event) { event?.let { StallCardState.from(it) } } ?: return

    MarketCardFrame {
        MarketCardBody {
            MarketKindLabel(MaterialSymbols.Storefront, stringRes(Res.string.marketplace_stall))
            MarketTitle(state.name)
            state.description?.let { MarketDescription(it, makeItShort) }
            state.currency?.let { MarketFactLine(MaterialSymbols.AttachMoney, stringRes(Res.string.marketplace_prices_in, it)) }

            if (state.zones.isNotEmpty()) {
                ShippingZones(state)
            }

            note.author?.let {
                // The thread view's larger text style would otherwise blow the name up.
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    LabeledInlineUser(stringRes(Res.string.marketplace_merchant), it.pubkeyHex, accountViewModel, nav)
                }
            }
        }
    }
}

@Composable
private fun ShippingZones(state: StallCardState) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringRes(Res.string.marketplace_shipping),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.zones.forEach { zone ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = zone.name ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (zone.regionCount > 0) {
                    Text(
                        text = pluralStringRes(Res.plurals.marketplace_shipping_regions, zone.regionCount, zone.regionCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 1,
                    )
                }
                val cost = if (zone.isFree) stringRes(Res.string.marketplace_shipping_free) else zone.cost
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
        if (state.hiddenZones > 0) {
            Text(
                text = pluralStringRes(Res.plurals.chips_n_more, state.hiddenZones, state.hiddenZones),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}
