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
package com.vitorpamplona.amethyst.ui.note.creators.location

import android.view.MotionEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/**
 * An interactive OpenStreetMap (osmdroid) picker: tapping the map reports the
 * tapped coordinate via [onPick], and [pickedLatitude]/[pickedLongitude] (when
 * set) show a marker there. Used to "teleport" into a remote geohash cell.
 *
 * Shares the tile/User-Agent/lifecycle setup with [LocationPreviewMap]; unlike
 * that display-only map, this one installs a [MapEventsOverlay] for tap picking.
 *
 * Two optional hooks power a "move the map under a fixed center pin" experience
 * (the modern picker style) without breaking the tap-to-drop callers:
 * - [onCenterChanged] fires whenever the map is scrolled or zoomed, reporting the
 *   new map center — pair it with a Compose crosshair drawn over the map's center.
 * - [recenter] animates the map to a new point when its value changes (e.g. after
 *   a place search or a "use my location" tap). Passing the same value twice is a
 *   no-op, so it is safe to hoist in state.
 * - [onZoomChanged] fires with the new zoom level whenever the map zooms.
 * - [onUserInteraction] fires when a finger first lands on the map. [onCenterChanged]
 *   alone cannot tell a user pan from osmdroid's own layout-time scroll (which the
 *   MapView emits at the opening center with pixel-quantized coordinates), so callers
 *   that must not treat an automatic scroll as a choice gate on this instead.
 */
@Composable
fun LocationPickerMap(
    latitude: Double,
    longitude: Double,
    pickedLatitude: Double?,
    pickedLongitude: Double?,
    modifier: Modifier = Modifier,
    zoom: Double = 4.0,
    recenter: GeoPoint? = null,
    recenterZoom: Double? = null,
    zoomTo: Double? = null,
    highlight: BoundingBox? = null,
    highlightColor: Int = 0,
    onCenterChanged: ((Double, Double) -> Unit)? = null,
    onZoomChanged: ((Double) -> Unit)? = null,
    onUserInteraction: (() -> Unit)? = null,
    onPick: (Double, Double) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPick by rememberUpdatedState(onPick)
    val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val currentOnUserInteraction by rememberUpdatedState(onUserInteraction)
    val darkTheme = !MaterialTheme.colorScheme.isLight

    // Tracks the last point we animated to, so a recomposition that re-supplies the
    // same [recenter] doesn't yank the map back while the user is panning.
    val lastRecenter = remember { arrayOfNulls<GeoPoint>(1) }

    // Tracks the last marker point so we only rebuild the marker overlay (and invalidate)
    // when it actually changes — not on every scroll-driven recomposition.
    val lastMarker = remember { arrayOfNulls<GeoPoint>(1) }

    // Same change-guards for the highlighted cell rectangle and the level-driven zoom.
    val lastHighlight = remember { arrayOfNulls<BoundingBox>(1) }
    val lastZoomTo = remember { doubleArrayOf(Double.NaN) }

    val mapView =
        remember(context) {
            Configuration.getInstance().userAgentValue = context.packageName

            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(zoom)
                controller.setCenter(GeoPoint(latitude, longitude))

                // Ask ancestors (nav drawer, horizontal pager, feed) to stop intercepting
                // touches while a finger is on the map, so a horizontal drag pans the map
                // instead of opening the drawer or being stolen as a swipe. Mirrors
                // LocationPreviewMap. Returning false lets the MapView still pan/zoom/tap.
                setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            currentOnUserInteraction?.invoke()
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }

                val receiver =
                    object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            currentOnPick(p.latitude, p.longitude)
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean {
                            currentOnPick(p.latitude, p.longitude)
                            return true
                        }
                    }
                overlays.add(0, MapEventsOverlay(receiver))

                addMapListener(
                    object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            mapCenter.let { currentOnCenterChanged?.invoke(it.latitude, it.longitude) }
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            mapCenter.let { currentOnCenterChanged?.invoke(it.latitude, it.longitude) }
                            currentOnZoomChanged?.invoke(zoomLevelDouble)
                            return false
                        }
                    },
                )
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

    // Follow the app theme: dim the bright MAPNIK tiles in dark mode (matching the
    // display-only LocationPreviewMap). Applied only when the theme flips, not per frame.
    LaunchedEffect(mapView, darkTheme) {
        mapView.overlayManager.tilesOverlay.setColorFilter(if (darkTheme) NIGHT_TILE_FILTER else MUTED_TILE_FILTER)
        mapView.invalidate()
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            if (recenter != null && recenter != lastRecenter[0]) {
                lastRecenter[0] = recenter
                if (recenterZoom != null) {
                    map.controller.animateTo(recenter, recenterZoom, 800L)
                } else {
                    map.controller.animateTo(recenter)
                }
            }

            // Animate the zoom when the caller asks (e.g. the area-size level changed),
            // keeping the current center. Guarded so it fires once per distinct value.
            if (zoomTo != null && zoomTo != lastZoomTo[0]) {
                lastZoomTo[0] = zoomTo
                map.controller.zoomTo(zoomTo, 400L)
            }

            // Outline the selected geohash cell so the user sees exactly which region a
            // post/filter will cover. Rebuilt only when the cell bounds change.
            if (highlight != lastHighlight[0]) {
                lastHighlight[0] = highlight
                map.overlays.removeAll { it is Polygon }
                if (highlight != null) {
                    map.overlays.add(
                        0,
                        Polygon(map).apply {
                            points =
                                listOf(
                                    GeoPoint(highlight.latNorth, highlight.lonWest),
                                    GeoPoint(highlight.latNorth, highlight.lonEast),
                                    GeoPoint(highlight.latSouth, highlight.lonEast),
                                    GeoPoint(highlight.latSouth, highlight.lonWest),
                                )
                            fillPaint.color = ColorUtils.setAlphaComponent(highlightColor, 38)
                            outlinePaint.color = highlightColor
                            outlinePaint.strokeWidth = 4f
                            setOnClickListener { _, _, _ -> false }
                        },
                    )
                }
                map.invalidate()
            }

            // Only rebuild the marker overlay when the picked point actually changes. The
            // update block runs on every scroll-driven recomposition, so unconditionally
            // clearing/re-adding the marker and invalidating would be per-frame waste.
            val marker = if (pickedLatitude != null && pickedLongitude != null) GeoPoint(pickedLatitude, pickedLongitude) else null
            if (marker != lastMarker[0]) {
                lastMarker[0] = marker
                map.overlays.removeAll { it is Marker }
                if (marker != null) {
                    map.overlays.add(
                        Marker(map).apply {
                            position = marker
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setInfoWindow(null)
                        },
                    )
                }
                map.invalidate()
            }
        },
    )
}
