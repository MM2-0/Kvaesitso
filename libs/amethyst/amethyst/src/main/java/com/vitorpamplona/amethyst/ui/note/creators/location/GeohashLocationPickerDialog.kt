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

import android.Manifest
import android.location.Address
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.clear
import com.vitorpamplona.amethyst.commons.resources.location_picker_area
import com.vitorpamplona.amethyst.commons.resources.location_picker_confirm
import com.vitorpamplona.amethyst.commons.resources.location_picker_hint
import com.vitorpamplona.amethyst.commons.resources.location_picker_search_empty
import com.vitorpamplona.amethyst.commons.resources.location_picker_search_hint
import com.vitorpamplona.amethyst.commons.resources.location_picker_title
import com.vitorpamplona.amethyst.commons.resources.location_picker_use_mine
import com.vitorpamplona.amethyst.commons.resources.location_picker_zoom_in
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.GeohashPickerDefaults
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.GeohashPickerDefaults.WORLD_CENTER_LAT
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.GeohashPickerDefaults.WORLD_CENTER_LON
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.GeohashPickerDefaults.WORLD_ZOOM
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.GeohashPickerDefaults.zoomForGeohashLength
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.areaSize
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.geohashChat.label
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.service.location.ForwardGeolocation
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChannelLevel
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

/** Zoom the map animates to after a search hit or a "use my location" tap. */
private const val RECENTER_ZOOM = 14.0

/** Give up waiting for a GPS fix after this long so the button never spins forever. */
private const val GPS_FIX_TIMEOUT_MS = 20_000L

/**
 * A full-screen, map-first location picker dialog that produces a geohash string.
 *
 * Thin chrome around [GeohashLocationPickerContent]: a close button + title header
 * over the shared picker body. Use this when you need the picker as a modal (e.g.
 * the NIP-29 group form); use [GeohashLocationPickerContent] directly when hosting
 * it inside your own screen scaffold (e.g. the Geohash-chat Teleport screen).
 */
@Composable
fun GeohashLocationPickerDialog(
    initialGeohash: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    precision: Int? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                PickerHeader(onClose = onDismiss)
                GeohashLocationPickerContent(
                    initialGeohash = initialGeohash,
                    confirmLabel = stringRes(Res.string.location_picker_confirm),
                    onConfirm = onConfirm,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    precision = precision,
                )
            }
        }
    }
}

/**
 * The reusable, chrome-less body of the location picker: a map with a fixed center
 * pin, a place-search bar, a "use my location" button, precision chips, a live
 * place-name readout, and a confirm button labeled [confirmLabel]. Fill it into a
 * dialog ([GeohashLocationPickerDialog]) or a screen scaffold.
 *
 * The user has three ways to land on a place — pan the map under the center pin,
 * search for a place by name (forward geocoding), or tap "use my location" (device
 * GPS) — and never has to know what a geohash is. The chosen precision
 * ([GeohashChannelLevel]) controls how many characters the resulting geohash has.
 * On confirm, [onConfirm] receives the encoded geohash (e.g. `u4pruy`).
 *
 * Reuses [LocationPickerMap] (in center-pin mode), [LoadCityName] for the
 * human-readable place name, and the app-wide [LocationState] for GPS.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GeohashLocationPickerContent(
    initialGeohash: String?,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
    precision: Int? = null,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val locationManager = LocalAppServices.current.deviceLocation

    // A geohash is lowercase; an uppercase or otherwise invalid seed decodes to nothing.
    val seedText = remember(initialGeohash) { initialGeohash?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } }
    val seed = remember(seedText) { seedText?.let { GeoHash.decode(it) } }
    val seedLen = if (seed != null) seedText?.length ?: 0 else 0
    // A seed that matches no level exactly (3 characters, or a geocache's 9) shows the nearest one.
    val seedLevel = remember(seed) { if (seed != null) GeohashPickerDefaults.nearestLevel(seedLen) else null }

    // The map opens centered here. Without a seed there is no real selection yet.
    val initialLat = seed?.centerLat ?: WORLD_CENTER_LAT
    val initialLon = seed?.centerLon ?: WORLD_CENTER_LON

    var pickedLat by remember(seed) { mutableStateOf(seed?.centerLat) }
    var pickedLon by remember(seed) { mutableStateOf(seed?.centerLon) }
    var hasSelection by remember(seed) { mutableStateOf(seed != null) }

    val openingZoom = if (seed != null) zoomForGeohashLength(seedLen) else WORLD_ZOOM
    var mapZoom by remember(seed) { mutableStateOf(openingZoom) }
    // A search hit or a GPS fix flies in close enough to place a fixed-precision pin.
    val recenterZoom = if (precision != null) maxOf(RECENTER_ZOOM, zoomForGeohashLength(precision)) else RECENTER_ZOOM

    // osmdroid emits a scroll event when the MapView is first laid out, reporting a
    // pixel-quantized version of the opening center — at world zoom a single pixel is
    // ~0.4 degrees, so that phantom "pan" can be hundreds of km away from where we asked
    // it to open. Treating it as a pick auto-selected whatever the default center was and
    // enabled Confirm with nothing chosen. Only map movement that follows a real finger
    // down on the map counts, so an automatic scroll can never become a selection.
    var mapTouched by remember { mutableStateOf(false) }
    var level by remember(seed) { mutableStateOf(seedLevel ?: GeohashChannelLevel.CITY) }
    var recenter by remember { mutableStateOf<GeoPoint?>(null) }

    // The pin moved once the picked point is no longer the seed's centre (or, unseeded, once anything
    // was picked).
    val moved = hasSelection && !(seed != null && pickedLat == seed.centerLat && pickedLon == seed.centerLon)
    val cell =
        remember(seedText, seedLevel, pickedLat, pickedLon, level, moved, precision, mapZoom) {
            GeohashPickerDefaults.pickedCell(
                seedText = if (seed != null) seedText else null,
                seedLevel = seedLevel,
                level = level,
                precision = precision,
                moved = moved,
                lat = pickedLat,
                lon = pickedLon,
                zoom = mapZoom,
            )
        }
    val needsZoomIn = GeohashPickerDefaults.needsZoomIn(precision, moved, mapZoom)

    // Debounce the reverse-geocode: panning changes [cell] constantly, and we don't
    // want to hammer the Geocoder — only resolve a place name once the map settles.
    var settledCell by remember { mutableStateOf(cell) }
    LaunchedEffect(cell) {
        delay(450)
        settledCell = cell
    }

    // The selected geohash cell outlined on the map, and its stroke colour.
    val highlightColor = MaterialTheme.colorScheme.primary.toArgb()
    val highlight = remember(cell) { cell?.let(::geohashBounds) }

    // When the area-size level changes (only after a real selection), zoom the map so the
    // new cell is comfortably framed.
    var zoomTo by remember { mutableStateOf<Double?>(null) }
    // Not on open: a seed already opened at its own zoom, which a level that only approximates it
    // (a 3-character seed shown as Region) would otherwise override.
    LaunchedEffect(seed) {
        snapshotFlow { level }.drop(1).collect { newLevel ->
            if (hasSelection) zoomTo = zoomForGeohashLength(newLevel.chars)
        }
    }

    // Lift the center pin briefly whenever the target point moves, for tactile feedback.
    var pinLifted by remember { mutableStateOf(false) }
    LaunchedEffect(pickedLat, pickedLon) {
        pinLifted = true
        delay(220)
        pinLifted = false
    }

    // "Use my location": tapping either fires the fetch (permission already granted) or
    // asks for it; [awaitingPermission] tracks the in-flight prompt so the button shows a
    // spinner. The result callback fires on BOTH grant and denial — so a denial always
    // clears the spinner (an isGranted-keyed effect wouldn't re-run on the false→false case).
    var wantsMyLocation by remember { mutableStateOf(false) }
    var awaitingPermission by remember { mutableStateOf(false) }
    val permission =
        rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION) { granted ->
            awaitingPermission = false
            if (granted) wantsMyLocation = true
        }
    LaunchedEffect(permission.status.isGranted) {
        locationManager.setLocationPermission(permission.status.isGranted)
    }
    LaunchedEffect(wantsMyLocation) {
        if (wantsMyLocation) {
            // Wait for the first real fix, but bail on a timeout so the button never spins
            // forever (permission granted yet location off, indoors, emulator with no fix…).
            val fix =
                withTimeoutOrNull(GPS_FIX_TIMEOUT_MS) {
                    locationManager.preciseGeohashStateFlow.first { it is LocationResult.Success }
                } as? LocationResult.Success
            if (fix != null) {
                recenter = GeoPoint(fix.geoHash.centerLat, fix.geoHash.centerLon)
                pickedLat = fix.geoHash.centerLat
                pickedLon = fix.geoHash.centerLon
                hasSelection = true
            }
            wantsMyLocation = false
        }
    }
    val onUseMyLocation = {
        if (permission.status.isGranted) {
            wantsMyLocation = true
        } else {
            awaitingPermission = true
            permission.launchPermissionRequest()
        }
    }

    // On open with no seed, if we already have location permission, start the map at the
    // user's current position instead of the neutral world view. Runs once, and never
    // prompts — it only uses permission that's already granted.
    var autoLocated by remember { mutableStateOf(false) }
    LaunchedEffect(permission.status.isGranted) {
        if (!autoLocated && seed == null && permission.status.isGranted) {
            autoLocated = true
            wantsMyLocation = true
        }
    }

    // Forward-geocode search. Results are shown as a pick list; choosing one flies there.
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchMissed by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Address>>(emptyList()) }
    val runSearch = {
        val q = query.trim()
        if (q.isNotEmpty()) {
            keyboard?.hide()
            searching = true
            searchMissed = false
            results = emptyList()
            // Off the main thread: on Android < 13 ForwardGeolocation.execute() calls the
            // blocking Geocoder synchronously, which would freeze/ANR the UI thread.
            scope.launch(Dispatchers.IO) {
                ForwardGeolocation.execute(q, context) { addresses ->
                    searching = false
                    val hits = addresses.orEmpty().filter { it.hasLatitude() && it.hasLongitude() }
                    results = hits
                    searchMissed = hits.isEmpty()
                }
            }
        }
    }
    val selectResult: (Address) -> Unit = { hit ->
        recenter = GeoPoint(hit.latitude, hit.longitude)
        pickedLat = hit.latitude
        pickedLon = hit.longitude
        hasSelection = true
        results = emptyList()
        query = ""
        keyboard?.hide()
    }

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            LocationPickerMap(
                latitude = initialLat,
                longitude = initialLon,
                pickedLatitude = null,
                pickedLongitude = null,
                zoom = openingZoom,
                recenter = recenter,
                recenterZoom = recenterZoom,
                zoomTo = zoomTo,
                highlight = highlight,
                highlightColor = highlightColor,
                onZoomChanged = { mapZoom = it },
                onUserInteraction = { mapTouched = true },
                onCenterChanged = { lat, lon ->
                    // Only a movement the user drove counts as their first real pick.
                    if (mapTouched) {
                        pickedLat = lat
                        pickedLon = lon
                        hasSelection = true
                    }
                },
                onPick = { lat, lon ->
                    recenter = GeoPoint(lat, lon)
                    pickedLat = lat
                    pickedLon = lon
                    hasSelection = true
                },
                modifier = Modifier.fillMaxSize(),
            )

            CenterPin(lifted = pinLifted, modifier = Modifier.align(Alignment.Center))

            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                SearchField(
                    query = query,
                    onQueryChange = {
                        query = it
                        searchMissed = false
                    },
                    onSearch = runSearch,
                    searching = searching,
                    missed = searchMissed,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (results.isNotEmpty()) {
                    SearchResults(
                        results = results,
                        onSelect = selectResult,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }

            MyLocationButton(
                loading = wantsMyLocation || awaitingPermission,
                onClick = onUseMyLocation,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
            )
        }

        PickerBottomBar(
            cell = cell,
            settledCell = settledCell,
            level = level,
            showLevels = precision == null,
            needsZoomIn = needsZoomIn,
            confirmLabel = confirmLabel,
            onLevel = { level = it },
            onConfirm = { cell?.let(onConfirm) },
        )
    }
}

@Composable
private fun PickerHeader(onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(Res.string.cancel),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringRes(Res.string.location_picker_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * The fixed pin hovering over the map center; the point under its tip is the selection.
 * While [lifted] (the target is moving) the pin rises off the map and its shadow dot
 * widens, for the tactile "dropping a pin" feel of a modern place picker.
 */
@Composable
private fun CenterPin(
    lifted: Boolean,
    modifier: Modifier = Modifier,
) {
    val lift by animateDpAsState(targetValue = if (lifted) 8.dp else 0.dp, label = "pinLift")
    val dot by animateDpAsState(targetValue = if (lifted) 10.dp else 7.dp, label = "pinDot")

    Box(modifier = modifier.size(56.dp), contentAlignment = Alignment.Center) {
        // The pin's tip sits at the map center; lift the whole glyph up by half its height
        // plus the animated hover offset.
        Icon(
            symbol = MaterialSymbols.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .size(46.dp)
                    .offset { IntOffset(0, (-20.dp - lift).roundToPx()) },
        )
        // A shadow/anchor dot marking the exact center point; it spreads as the pin lifts.
        Box(
            Modifier
                .size(dot)
                .shadow(if (lifted) 4.dp else 2.dp, CircleShape)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}

/** A tappable list of forward-geocode candidates shown under the search field. */
@Composable
private fun SearchResults(
    results: List<Address>,
    onSelect: (Address) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.shadow(6.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            results.forEachIndexed { index, address ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(address) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = address.displayLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searching: Boolean,
    missed: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.shadow(6.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(stringRes(Res.string.location_picker_search_hint)) },
            leadingIcon = {
                Icon(
                    symbol = MaterialSymbols.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (searching) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            symbol = MaterialSymbols.Close,
                            contentDescription = stringRes(Res.string.clear),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            isError = missed,
            supportingText =
                if (missed) {
                    { Text(stringRes(Res.string.location_picker_search_empty)) }
                } else {
                    null
                },
            // The pill comes from the wrapping Surface; hide the text field's own
            // rectangular outline so its square corners don't poke through the pill.
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    errorBorderColor = Color.Transparent,
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MyLocationButton(
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = !loading,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        modifier = modifier.shadow(6.dp, CircleShape).size(52.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Icon(
                symbol = MaterialSymbols.MyLocation,
                contentDescription = stringRes(Res.string.location_picker_use_mine),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickerBottomBar(
    cell: String?,
    settledCell: String?,
    level: GeohashChannelLevel,
    showLevels: Boolean,
    needsZoomIn: Boolean,
    confirmLabel: String,
    onLevel: (GeohashChannelLevel) -> Unit,
    onConfirm: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePaddingSafe()
                .padding(16.dp),
        ) {
            // A caller that fixes the precision (a geocache) picks a point, not an area.
            if (showLevels) {
                Text(
                    text = stringRes(Res.string.location_picker_area),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Drop the 48dp minimum touch-target so wrapped chip rows sit 8dp apart
                // (matching the horizontal gap) instead of ~24dp apart.
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GeohashChannelLevel.ordered.forEach { lvl ->
                            FilterChip(
                                selected = lvl == level,
                                onClick = { onLevel(lvl) },
                                label = { Text("${lvl.label()} · ${lvl.areaSize()}") },
                            )
                        }
                    }
                }
            }

            if (cell == null) {
                Text(
                    text = stringRes(if (needsZoomIn) Res.string.location_picker_zoom_in else Res.string.location_picker_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                symbol = MaterialSymbols.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        // The place name must never contradict the geohash under it. Resolve
                        // it only for the settled cell, and only while that IS the current
                        // cell — mid-pan the debounced [settledCell] still names the previous
                        // cell, and drawing it beside a fresh geohash is worse than no name.
                        // LoadCityName echoes the geohash back when it cannot resolve a name
                        // (no geocoder backend, or a point at sea); drop that too rather than
                        // repeat the geohash as if it were a place.
                        if (settledCell == cell) {
                            LoadCityName(geohashStr = cell) { cityName ->
                                if (cityName != cell) {
                                    Text(
                                        cityName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Text(
                            "#$cell",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Button(
                onClick = onConfirm,
                enabled = cell != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * A human, one-line label for a geocoder result: its most specific name, then the
 * enclosing locality/region/country, de-duplicated. Falls back to the full address line.
 */
private fun Address.displayLine(): String {
    val primary = featureName ?: locality ?: subAdminArea ?: adminArea ?: getAddressLine(0)
    val context = listOfNotNull(locality, adminArea, countryName).distinct().filter { it != primary }
    return listOfNotNull(primary, context.joinToString(", ").ifBlank { null }).joinToString(", ")
}

/**
 * The lat/lon bounding box of a geohash cell. A geohash of length L uses 5L bits,
 * split lon = ceil(5L/2), lat = floor(5L/2); each halves its axis per bit, so the cell
 * spans 180/2^latBits by 360/2^lonBits around the decoded center.
 */
private fun geohashBounds(geohash: String): BoundingBox? {
    val gh = GeoHash.decode(geohash) ?: return null
    val bits = 5 * geohash.length
    val latBits = bits / 2
    val lonBits = bits - latBits
    val latHeight = 180.0 / (1L shl latBits)
    val lonWidth = 360.0 / (1L shl lonBits)
    return BoundingBox(
        gh.centerLat + latHeight / 2,
        gh.centerLon + lonWidth / 2,
        gh.centerLat - latHeight / 2,
        gh.centerLon - lonWidth / 2,
    )
}
