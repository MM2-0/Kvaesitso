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

import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.MotionEvent
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** Default close-up zoom for a single pinned location (street-level), when the caller picks none. */
const val DEFAULT_MAP_ZOOM = 16.0

/**
 * Night-mode filter for the (always-light) MAPNIK tiles so the map follows the
 * app theme. This is a lightness invert composed with a 180° hue rotation: it
 * darkens the map (light land → near-black) while *preserving* hue, so forests
 * stay green and water stays blue and the surfaces remain distinguishable —
 * unlike a plain colour invert (forests → magenta) or a desaturating invert
 * (everything → black).
 */
internal val NIGHT_TILE_FILTER: ColorFilter =
    ColorMatrixColorFilter(
        floatArrayOf(
            0.574f,
            -1.430f,
            -0.144f,
            0f,
            255f,
            -0.426f,
            -0.430f,
            -0.144f,
            0f,
            255f,
            -0.426f,
            -1.430f,
            0.856f,
            0f,
            255f,
            0f,
            0f,
            0f,
            1f,
            0f,
        ),
    )

/**
 * Light-mode "calm" filter for the busy MAPNIK tiles: desaturate (mute the loud
 * road/POI colours) and lift brightness a touch so labels read softer. Keeps the
 * map legible for picking a spot without the full-colour visual noise.
 */
internal val MUTED_TILE_FILTER: ColorFilter =
    ColorMatrixColorFilter(
        ColorMatrix().apply {
            setSaturation(0.55f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        0.92f,
                        0f,
                        0f,
                        0f,
                        16f,
                        0f,
                        0.92f,
                        0f,
                        0f,
                        16f,
                        0f,
                        0f,
                        0.92f,
                        0f,
                        16f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f,
                    ),
                ),
            )
        },
    )

/**
 * A small OpenStreetMap (osmdroid) preview centered on [latitude]/[longitude]
 * with a single pin at that point.
 *
 * Used by the Roadstr road event cards (kind 1315/1316) in place of a
 * reverse-geocoded city name. Tiles are fetched from the public OSM tile
 * servers, which requires the app's package name as the User-Agent (set on
 * [Configuration] below) — without it OSM returns HTTP 403.
 *
 * When [pinColor] and [pinEmoji] are supplied the marker becomes a colored
 * teardrop with the category emoji on it (see [roadEventPinBitmap]); otherwise
 * osmdroid's default pin is used. [pinAlpha] fades the marker to signal
 * freshness (e.g. an event close to its effective expiry).
 *
 * Pan/zoom stay enabled, but a touch listener asks the parent to stop
 * intercepting gestures while the finger is on the map, so dragging the map
 * pans it instead of scrolling the surrounding feed.
 */
@Composable
fun LocationPreviewMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    zoom: Double = DEFAULT_MAP_ZOOM,
    aspectRatio: Float = 1f,
    pinColor: Color? = null,
    pinEmoji: String? = null,
    pinAlpha: Float = 1f,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val darkTheme = !MaterialTheme.colorScheme.isLight

    val markerIcon =
        remember(pinColor, pinEmoji, resources) {
            if (pinColor != null && pinEmoji != null) {
                val bitmap = roadEventPinBitmap(pinEmoji, pinColor.toArgb(), resources.displayMetrics.density)
                bitmap.toDrawable(resources)
            } else {
                null
            }
        }

    val mapView =
        remember(context) {
            // Must be set before the MapView is created so OSM tile requests
            // carry a valid User-Agent (the default "osmdroid" is rejected).
            Configuration.getInstance().userAgentValue = context.packageName

            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP -> view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    // Returning false lets the MapView still handle the pan/zoom.
                    false
                }
            }
        }

    // osmdroid's MapView is a plain Android View with its own lifecycle: it
    // needs onResume/onPause to (re)start its tile threads, and onDetach to
    // free the tile cache when the composable leaves the tree.
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

    AndroidView(
        modifier = modifier.fillMaxWidth().aspectRatio(aspectRatio),
        factory = { mapView },
        update = { map ->
            val point = GeoPoint(latitude, longitude)
            map.controller.setZoom(zoom)
            map.controller.setCenter(point)

            // Follow the app theme: dim in dark mode, mute the busy colours in light mode.
            map.overlayManager.tilesOverlay.setColorFilter(if (darkTheme) NIGHT_TILE_FILTER else MUTED_TILE_FILTER)

            map.overlays.removeAll { it is Marker }
            val marker =
                Marker(map).apply {
                    position = point
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setInfoWindow(null)
                    markerIcon?.let { icon = it }
                    alpha = pinAlpha
                }
            map.overlays.add(marker)
            map.invalidate()
        },
    )
}
