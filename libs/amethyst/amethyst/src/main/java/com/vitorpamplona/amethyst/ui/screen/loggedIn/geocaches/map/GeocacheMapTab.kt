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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.geocaches.map

import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_map_long_press
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.geocachePoint
import com.vitorpamplona.amethyst.commons.ui.note.rememberGeocachePalette
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.GEOCACHE_CLUSTER_THRESHOLD
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.GEOCACHE_MAP_FIRST_CLUSTERED_ZOOM
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.GEOCACHE_MAP_FIRST_ZOOM
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.GEOCACHE_MAP_INITIAL_ZOOM
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.GeocacheMapMark
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.GeocachePeekSheet
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.GeocachePin
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.geocacheMapMarks
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.geocachePin
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.map.rememberGeocacheListings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.rememberMyFoundCacheIds
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ui.note.creators.location.roadEventPinBitmap
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheGeohash
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import kotlin.math.abs

/**
 * The caches on a map, which is how people actually browse for something to go and find.
 *
 * This is the only genuinely new piece of map UI in the app: every other map composable places
 * exactly one marker and clears the rest. Here the pin set is the same feed the Nearby tab
 * renders, so switching tabs costs no additional relay traffic, and pin colour encodes state so
 * the field reads at a glance:
 *
 * - **amber** — unfound, still in play
 * - **gold** — an unclaimed first-to-find
 * - **green** — you have already logged a find here
 * - **grey** — archived, or a claim already taken
 *
 * Tapping a pin raises a peek rather than navigating, because a player comparing three caches
 * should not have to walk the back stack to do it. Long-pressing empty map starts a new cache
 * at that spot, which is the fastest path from "I am standing here" to a published listing.
 */
@Composable
fun GeocacheMapTab(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val feedState by feedContentState.feedContent.collectAsStateWithLifecycle()
    val found = rememberMyFoundCacheIds(accountViewModel)

    val listings = rememberGeocacheListings(feedState)

    var peek by remember { mutableStateOf<GeocacheListingEvent?>(null) }

    // The map used to pick its own tints, which meant a cache could be one colour here and a
    // different one on its own card. The palette is the single source of that meaning now.
    val palette = rememberGeocachePalette()

    val mapView =
        remember(context) {
            Configuration.getInstance().userAgentValue = context.packageName

            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                // osmdroid opens at zoom 0, one 256px world tile repeated down the screen. Zoom 3 fills
                // a phone, and the world stops at the poles instead of stacking copies of itself.
                isVerticalMapRepetitionEnabled = false
                setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
                controller.setZoom(GEOCACHE_MAP_INITIAL_ZOOM)
                setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP -> view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
            }
        }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { map ->
                map.overlays.removeAll { it is Marker || it is MapEventsOverlay }

                map.overlays.add(
                    0,
                    MapEventsOverlay(
                        object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                peek = null
                                return false
                            }

                            override fun longPressHelper(p: GeoPoint?): Boolean {
                                // A cache hidden where the finger landed. The picker still opens
                                // inside the composer, so this is a starting point, not a commit.
                                p ?: return false
                                nav.nav(Route.NewGeocache(geohash = GeoHash.encode(p.latitude, p.longitude, GeocacheGeohash.MAX_TAGGED).toString()))
                                return true
                            }
                        },
                    ),
                )

                // Above the threshold, pins are folded into per-cell counts (geocacheMapMarks).
                // osmdroid draws every Marker on every invalidate, so a few hundred individual
                // pins turn a pan into a slideshow. The cell size tracks zoom, so zooming in splits
                // clusters apart, which is the gesture people already expect to do.
                val clustered = listings.count { it.geocachePoint() != null } > GEOCACHE_CLUSTER_THRESHOLD
                val marks = geocacheMapMarks(listings, map.zoomLevelDouble)

                marks.forEach { mark ->
                    val geo = GeoPoint(mark.lat, mark.lon)
                    when (mark) {
                        is GeocacheMapMark.Cache -> map.overlays.add(markerFor(map, mark.listing, geo, context, geocachePin(mark.listing, found, palette)) { peek = it })
                        is GeocacheMapMark.Cluster ->
                            map.overlays.add(
                                Marker(map).apply {
                                    position = geo
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    setInfoWindow(null)
                                    icon =
                                        roadEventPinBitmap(
                                            mark.size.toString(),
                                            palette.live.toArgb(),
                                            resources.displayMetrics.density,
                                        ).toDrawable(resources)
                                    setOnMarkerClickListener { _, view ->
                                        // Zoom into the cluster rather than picking one of
                                        // its caches arbitrarily.
                                        view.controller.animateTo(geo, view.zoomLevelDouble + 2.0, 300L)
                                        true
                                    }
                                },
                            )
                    }
                }

                marks.firstOrNull()?.let {
                    // Only recentre while the map has not been positioned yet, so a pan is not
                    // yanked back every time a new cache arrives from a relay.
                    if (map.isUnpositioned()) {
                        map.controller.setZoom(if (clustered) GEOCACHE_MAP_FIRST_CLUSTERED_ZOOM else GEOCACHE_MAP_FIRST_ZOOM)
                        map.controller.setCenter(GeoPoint(it.lat, it.lon))
                    }
                }

                map.invalidate()
            },
        )

        peek?.let { listing ->
            GeocachePeekSheet(
                listing = listing,
                onOpen = { nav.nav(Route.GeocacheDetail(listing.address())) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (peek == null) {
            Text(
                text = stringRes(Res.string.geocache_map_long_press),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            )
        }
    }
}

/** Still on the untouched initial view: centred on 0,0, which a zoom can leave a hair off exact zero. */
private fun MapView.isUnpositioned() = abs(mapCenter.latitude) < 1e-6 && abs(mapCenter.longitude) < 1e-6

private fun markerFor(
    map: MapView,
    listing: GeocacheListingEvent,
    geo: GeoPoint,
    context: Context,
    pin: GeocachePin,
    onTap: (GeocacheListingEvent) -> Unit,
): Marker =
    Marker(map).apply {
        position = geo
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        setInfoWindow(null)
        icon = roadEventPinBitmap(pin.emoji, pin.color.toArgb(), context.resources.displayMetrics.density).toDrawable(context.resources)
        alpha = pin.alpha
        setOnMarkerClickListener { _, _ ->
            onTap(listing)
            true
        }
    }
