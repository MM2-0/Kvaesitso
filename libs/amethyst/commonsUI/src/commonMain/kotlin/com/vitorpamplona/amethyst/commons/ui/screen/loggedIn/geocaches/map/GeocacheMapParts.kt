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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_map_open
import com.vitorpamplona.amethyst.commons.resources.geocache_unnamed
import com.vitorpamplona.amethyst.commons.ui.note.GeocachePalette
import com.vitorpamplona.amethyst.commons.ui.note.GeocacheSpecLine
import com.vitorpamplona.amethyst.commons.ui.note.geocacheEmoji
import com.vitorpamplona.amethyst.commons.ui.note.geocachePoint
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import kotlin.math.pow
import kotlin.math.roundToLong

/*
 * What the geocaches map tab shows, whatever draws the map: Android's osmdroid view and the
 * desktop's tile map both place these pins, clusters and the peek sheet.
 */

/**
 * Above this many visible caches the map switches to counts. A starting figure rather than a
 * measured one; it wants checking on a mid-range device with a busy city on screen.
 */
const val GEOCACHE_CLUSTER_THRESHOLD = 50

/** The whole world, before the first cache arrives. */
const val GEOCACHE_MAP_INITIAL_ZOOM = 3.0

/** Where the map settles on the first cache: close for a few pins, wider when they are counted. */
const val GEOCACHE_MAP_FIRST_ZOOM = 13.0
const val GEOCACHE_MAP_FIRST_CLUSTERED_ZOOM = 11.0

/**
 * Degrees per cluster cell at [zoom]. Roughly a fixed number of screen pixels: each zoom level
 * halves the ground covered by a pixel, so the cell halves with it and clusters break apart at
 * the same apparent density all the way in.
 */
fun geocacheClusterCellSize(zoom: Double): Double = 40.0 / (256.0 * 2.0.pow(zoom)) * 360.0

/** One pin's look: its state as a colour (the palette's meaning), the cache type's emoji, faded when over. */
@Immutable
data class GeocachePin(
    val color: Color,
    val emoji: String,
    val alpha: Float,
)

/**
 * Pin colour encodes state, so the field reads at a glance: amber, unfound and in play; gold, an
 * unclaimed first-to-find; green, you have logged a find here; grey, archived or a claim taken.
 */
fun geocachePin(
    listing: GeocacheListingEvent,
    found: Set<String>,
    palette: GeocachePalette,
): GeocachePin {
    val mine = found.contains(listing.address().toValue())
    val over = listing.isArchived() || listing.firstToFindWinner() != null
    val tint =
        when {
            over -> palette.over
            mine -> palette.proven
            listing.isFirstToFind() -> palette.prize
            else -> palette.live
        }
    return GeocachePin(tint, listing.cacheType().geocacheEmoji(), if (over) GeocachePalette.OVER_ALPHA else 1f)
}

/** A pin on the map: one cache, or a count of the caches in one cluster cell. */
@Immutable
sealed interface GeocacheMapMark {
    val lat: Double
    val lon: Double

    @Immutable
    class Cache(
        val listing: GeocacheListingEvent,
        override val lat: Double,
        override val lon: Double,
    ) : GeocacheMapMark

    @Immutable
    class Cluster(
        val size: Int,
        override val lat: Double,
        override val lon: Double,
    ) : GeocacheMapMark
}

/**
 * The caches as marks at [zoom]: one each, or, above [GEOCACHE_CLUSTER_THRESHOLD], folded into
 * per-cell counts. A screen of overlapping teardrops tells the reader less than "7 here" does, and
 * drawing hundreds of pins on every pan is what turns a map into a slideshow.
 */
fun geocacheMapMarks(
    listings: List<GeocacheListingEvent>,
    zoom: Double,
): List<GeocacheMapMark> {
    val visible = listings.mapNotNull { l -> l.geocachePoint()?.let { l to it } }
    if (visible.size <= GEOCACHE_CLUSTER_THRESHOLD) {
        return visible.map { (listing, p) -> GeocacheMapMark.Cache(listing, p.first, p.second) }
    }
    val cell = geocacheClusterCellSize(zoom)
    return visible
        .groupBy { (_, p) -> (p.first / cell).roundToLong() to (p.second / cell).roundToLong() }
        .values
        .map { group ->
            val (listing, centre) = group.first()
            if (group.size == 1) {
                GeocacheMapMark.Cache(listing, centre.first, centre.second)
            } else {
                GeocacheMapMark.Cluster(group.size, centre.first, centre.second)
            }
        }
}

/**
 * Collects the loaded feed's own flow. [FeedState.Loaded] is handed out once and then mutated
 * through its inner flow, so reading `.value` here would pin the map to whatever had arrived when
 * the tab first composed: caches streaming in from relays would never get a pin.
 */
@Composable
fun rememberGeocacheListings(feedState: FeedState): List<GeocacheListingEvent> =
    when (feedState) {
        is FeedState.Loaded -> {
            val loaded by feedState.feed.collectAsStateWithLifecycle()

            remember(loaded) {
                loaded.list.mapNotNull { it.event as? GeocacheListingEvent }
            }
        }
        else -> emptyList()
    }

/**
 * Tapping a pin raises this rather than navigating, because a player comparing three caches
 * should not have to walk the back stack to do it.
 */
@Composable
fun GeocachePeekSheet(
    listing: GeocacheListingEvent,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text =
                    listing.cacheType().geocacheEmoji() + "  " +
                        (listing.cacheName()?.trim()?.ifBlank { null } ?: stringRes(Res.string.geocache_unnamed)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            GeocacheSpecLine(listing.cacheType(), listing.cacheSize(), listing.difficulty(), listing.terrain())

            Spacer(Modifier.height(10.dp))

            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text(stringRes(Res.string.geocache_map_open))
            }
        }
    }
}
