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
package com.vitorpamplona.amethyst.commons.nip69P2pOrderEvents.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip69P2pOrderEvents.MostroEscrow
import com.vitorpamplona.amethyst.commons.nip69P2pOrderEvents.MostroInstanceTerms
import com.vitorpamplona.amethyst.commons.nip69P2pOrderEvents.formatPercent
import com.vitorpamplona.amethyst.commons.nip69P2pOrderEvents.takeWithOverflow
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.mostro_all_currencies
import com.vitorpamplona.amethyst.commons.resources.mostro_currencies
import com.vitorpamplona.amethyst.commons.resources.mostro_escrow
import com.vitorpamplona.amethyst.commons.resources.mostro_escrow_cashu
import com.vitorpamplona.amethyst.commons.resources.mostro_escrow_lightning
import com.vitorpamplona.amethyst.commons.resources.mostro_fee
import com.vitorpamplona.amethyst.commons.resources.mostro_in_maintenance
import com.vitorpamplona.amethyst.commons.resources.mostro_instance_label
import com.vitorpamplona.amethyst.commons.resources.mostro_instance_unnamed
import com.vitorpamplona.amethyst.commons.resources.mostro_more_currencies
import com.vitorpamplona.amethyst.commons.resources.mostro_order_expiration
import com.vitorpamplona.amethyst.commons.resources.mostro_order_expiration_hours
import com.vitorpamplona.amethyst.commons.resources.mostro_order_size
import com.vitorpamplona.amethyst.commons.resources.mostro_order_size_max
import com.vitorpamplona.amethyst.commons.resources.mostro_order_size_min
import com.vitorpamplona.amethyst.commons.resources.mostro_order_size_range
import com.vitorpamplona.amethyst.commons.resources.mostro_version
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.MostroInfoEvent

/** How many currency codes the card lists before it says "+N more". */
private const val MAX_CURRENCIES = 12

/**
 * Entry for a Mostro instance's terms (kind 38385), the event a P2P exchange publishes about
 * itself: its name and daemon version, then what a trader needs before picking it — the fee, the
 * order size it accepts, the currencies, how long an order waits and how it escrows the sats.
 */
@Composable
fun RenderMostroInfo(baseNote: Note) {
    val noteEvent = baseNote.event as? MostroInfoEvent ?: return
    val terms = remember(noteEvent) { MostroInstanceTerms.from(noteEvent) }
    MostroInfoCard(terms)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MostroInfoCard(
    terms: MostroInstanceTerms,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                symbol = MaterialSymbols.SwapHoriz,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.grayText,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringRes(Res.string.mostro_instance_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = terms.name ?: stringRes(Res.string.mostro_instance_unnamed),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            terms.version?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringRes(Res.string.mostro_version, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                )
            }
        }

        if (terms.inMaintenance) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(
                    symbol = MaterialSymbols.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.warningColor,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringRes(Res.string.mostro_in_maintenance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.warningColor,
                )
            }
        }

        Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            terms.feePercent?.let { TermRow(stringRes(Res.string.mostro_fee), formatPercent(it)) }

            orderSizeText(terms)?.let { TermRow(stringRes(Res.string.mostro_order_size), it) }

            terms.pendingExpirationHours?.let {
                val hours = it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                TermRow(stringRes(Res.string.mostro_order_expiration), pluralStringRes(Res.plurals.mostro_order_expiration_hours, hours, hours))
            }

            TermRow(stringRes(Res.string.mostro_escrow), escrowText(terms))
        }

        val currencies = terms.currencies
        if (currencies != null) {
            Text(
                text = stringRes(Res.string.mostro_currencies),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (currencies.isEmpty()) {
                Text(
                    text = stringRes(Res.string.mostro_all_currencies),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                val (shown, more) = remember(currencies) { currencies.takeWithOverflow(MAX_CURRENCIES) }
                FlowRow(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    shown.forEach { code -> CurrencyChip(code) }
                    if (more > 0) {
                        Text(
                            text = pluralStringRes(Res.plurals.mostro_more_currencies, more, more),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.grayText,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun orderSizeText(terms: MostroInstanceTerms): String? {
    val min = terms.minOrderSats
    val max = terms.maxOrderSats
    return when {
        min != null && max != null -> stringRes(Res.string.mostro_order_size_range, formatGrouped(min), formatGrouped(max))
        min != null -> stringRes(Res.string.mostro_order_size_min, formatGrouped(min))
        max != null -> stringRes(Res.string.mostro_order_size_max, formatGrouped(max))
        else -> null
    }
}

@Composable
private fun escrowText(terms: MostroInstanceTerms): String =
    when (terms.escrow) {
        MostroEscrow.LIGHTNING -> stringRes(Res.string.mostro_escrow_lightning)
        MostroEscrow.CASHU -> stringRes(Res.string.mostro_escrow_cashu)
        MostroEscrow.OTHER -> terms.escrowCode.orEmpty()
    }

@Composable
private fun TermRow(
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.grayText,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CurrencyChip(code: String) {
    Text(
        text = code,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.grayText,
        modifier =
            Modifier
                .clip(SmallBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, SmallBorder)
                .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
