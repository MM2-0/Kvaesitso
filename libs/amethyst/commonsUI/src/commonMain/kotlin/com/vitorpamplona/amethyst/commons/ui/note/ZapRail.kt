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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.MIN_ONCHAIN_ZAP_SATS
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nutzap
import com.vitorpamplona.amethyst.commons.resources.reload_mint_title
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import androidx.compose.material3.Icon as Material3Icon

/** Payment rails a zap-amount chip can offer, in canonical display order. */
enum class ZapRail { CASHU, RELOAD, LIGHTNING, ONCHAIN }

/** Below this, the default rail is cashu (Lightning min/fees make tiny zaps awkward). */
const val CASHU_PREFERRED_BELOW_SATS = 10L

/** Above this, the default rail is an on-chain transaction. */
const val ONCHAIN_PREFERRED_ABOVE_SATS = 10_000L

/**
 * Default rail for a preset amount with no recipient context (the settings
 * preview): cashu under [CASHU_PREFERRED_BELOW_SATS], on-chain over
 * [ONCHAIN_PREFERRED_ABOVE_SATS] (and at/above the on-chain minimum), else
 * Lightning. Mirrors the live chip's tiering so the preview matches the feed.
 */
fun previewPreferredRail(amountInSats: Long): ZapRail =
    when {
        amountInSats < CASHU_PREFERRED_BELOW_SATS -> ZapRail.CASHU
        amountInSats > ONCHAIN_PREFERRED_ABOVE_SATS && amountInSats >= MIN_ONCHAIN_ZAP_SATS -> ZapRail.ONCHAIN
        else -> ZapRail.LIGHTNING
    }

/** Rails a preset of [amountInSats] could use, default first then the rest. */
fun previewRailsFor(amountInSats: Long): List<ZapRail> {
    val preferred = previewPreferredRail(amountInSats)
    val all =
        buildList {
            add(ZapRail.CASHU)
            add(ZapRail.LIGHTNING)
            if (amountInSats >= MIN_ONCHAIN_ZAP_SATS) add(ZapRail.ONCHAIN)
        }
    return listOf(preferred) + all.filter { it != preferred }
}

/**
 * Just the rail's logo (no click target), drawn at [size]. [colored] renders it
 * in its accent colour (the preferred rail); otherwise it's flattened to the
 * on-surface monochrome tint. The cashu mark is drawn ~0.86× the symbol size —
 * the new monochrome outline carries less weight than the old multi-tone mark,
 * so it needs less shrinking to read at a matching optical size.
 */
@Composable
fun ZapRailIcon(
    rail: ZapRail,
    colored: Boolean,
    size: Dp = 18.dp,
) {
    val mono = MaterialTheme.colorScheme.onSurface
    val cashuSize = size * 0.86f
    when (rail) {
        ZapRail.CASHU -> {
            Material3Icon(
                imageVector = CustomHashTagIcons.Cashu,
                contentDescription = stringRes(Res.string.nutzap),
                modifier = Modifier.size(cashuSize),
                // The cashu mark is a monochrome, tintable outline — colour it the
                // same brand orange as the other rails when selected, else mono.
                tint = if (colored) BitcoinOrange else mono,
            )
        }

        ZapRail.RELOAD -> {
            // Funds exist but in the wrong mint — a dimmed cashu logo with a
            // small "+" badge; tapping it opens the top-up screen.
            Box(contentAlignment = Alignment.BottomEnd) {
                Material3Icon(
                    imageVector = CustomHashTagIcons.Cashu,
                    contentDescription = stringRes(Res.string.reload_mint_title),
                    modifier = Modifier.size(cashuSize).alpha(0.5f),
                    tint = if (colored) BitcoinOrange else mono,
                )
                Icon(
                    symbol = MaterialSymbols.AddCircle,
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.5f),
                    tint = if (colored) BitcoinOrange else mono,
                )
            }
        }

        ZapRail.LIGHTNING -> {
            Icon(
                symbol = MaterialSymbols.Bolt,
                contentDescription = null,
                modifier = Modifier.size(size),
                tint = if (colored) BitcoinOrange else mono,
            )
        }

        ZapRail.ONCHAIN -> {
            Icon(
                symbol = MaterialSymbols.CurrencyBitcoin,
                contentDescription = null,
                modifier = Modifier.size(size),
                tint = if (colored) BitcoinOrange else mono,
            )
        }
    }
}
