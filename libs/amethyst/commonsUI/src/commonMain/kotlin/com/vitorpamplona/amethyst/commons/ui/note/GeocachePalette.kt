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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.warningColor
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent

/**
 * The one place geocaching decides what a colour means.
 *
 * Four states, and only four, because the palette is doing information design rather than
 * decoration: a player scanning a map, a feed and a detail screen should read the same colour as
 * the same fact on all three. Before this existed the map invented its own tints and the cards
 * used a different pair, so a cache could be gold on one surface and amber on the next.
 *
 * - [prize] — an unclaimed first-to-find. The only thing on screen that is scarce.
 * - [proven] — a verified find, or a cache that demands proof. The feature nothing else has.
 * - [live] — an ordinary cache still in play. Deliberately the theme's own primary, so the
 *   common case does not shout.
 * - [over] — archived, or a claim already taken. Muted, never hidden: a finished cache is still
 *   a place worth visiting, it is simply no longer a prize.
 *
 * Each colour comes with a [wash] for use as a background. The full-strength value is for marks
 * — a pin, a rule, an icon — and the wash for anything with text on top of it, because the
 * theme's amber is unreadable as text on a light ground while `onSurface` over a wash reads in
 * both themes.
 */
@Immutable
data class GeocachePalette(
    val prize: Color,
    val proven: Color,
    val live: Color,
    val over: Color,
) {
    fun wash(color: Color): Color = color.copy(alpha = WASH_ALPHA)

    /** The colour that describes a cache's current state, in priority order. */
    fun forState(
        isArchived: Boolean,
        isClaimed: Boolean,
        isFirstToFind: Boolean,
        requiresVerification: Boolean,
    ): Color =
        when {
            isArchived || isClaimed -> over
            isFirstToFind -> prize
            requiresVerification -> proven
            else -> live
        }

    companion object {
        const val WASH_ALPHA = 0.16f

        /** Alpha for a pin or hero whose cache is out of play. */
        const val OVER_ALPHA = 0.45f
    }
}

@Composable
fun rememberGeocachePalette(): GeocachePalette {
    val prize = MaterialTheme.colorScheme.warningColor
    val proven = MaterialTheme.colorScheme.allGoodColor
    val live = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.onSurfaceVariant

    return remember(prize, proven, live, over) { GeocachePalette(prize, proven, live, over) }
}

/** The state colour for this listing, given whether its first-to-find claim is taken. */
@Composable
fun GeocachePalette.forListing(
    listing: GeocacheListingEvent,
    isClaimed: Boolean,
): Color =
    forState(
        isArchived = listing.isArchived(),
        isClaimed = isClaimed,
        isFirstToFind = listing.isFirstToFind(),
        requiresVerification = listing.requiresVerification(),
    )
