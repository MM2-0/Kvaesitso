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
package com.vitorpamplona.amethyst.commons.ui.note.creators.location

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geohash_title
import com.vitorpamplona.amethyst.commons.resources.location_change_place
import com.vitorpamplona.amethyst.commons.resources.location_pick_on_map
import com.vitorpamplona.amethyst.commons.resources.remove_location
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.ILocationGrabber
import com.vitorpamplona.amethyst.commons.ui.platform.GeohashLocationPickerDialog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp

/**
 * The shared location section for post composers. Defaults to the device-GPS flow
 * ([LocationAsHash]), and adds a "pick a place on the map" action that opens
 * [GeohashLocationPickerDialog] and stores the chosen geohash in
 * [ILocationGrabber.pickedGeoHash] — which each composer's build step then prefers
 * over the live GPS fix. Picking a place also skips the GPS permission prompt.
 *
 * [innerContent] is a slot for composer-specific extras rendered beneath the
 * location readout (e.g. the "location-exclusive post" switch).
 */
@Composable
fun GeoHashPostSection(
    model: ILocationGrabber,
    innerContent: @Composable () -> Unit = {},
) {
    var showPicker by remember { mutableStateOf(false) }
    val picked = model.pickedGeoHash

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Size10dp, horizontal = Size10dp),
    ) {
        if (picked != null) {
            // A map-picked place: show it, and let the user clear back to GPS.
            Row(verticalAlignment = CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    symbol = MaterialSymbols.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringRes(Res.string.geohash_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier.padding(start = 10.dp),
                )
                DisplayLocationInTitle(geohash = picked)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { model.pickedGeoHash = null }) {
                    Icon(
                        symbol = MaterialSymbols.Close,
                        contentDescription = stringRes(Res.string.remove_location),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            innerContent()
        } else {
            // GPS mode (unchanged): current device location + the composer's extras.
            LocationAsHash(model, innerContent)
        }

        TextButton(onClick = { showPicker = true }, modifier = Modifier.padding(top = 4.dp)) {
            Icon(
                symbol = MaterialSymbols.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringRes(if (picked != null) Res.string.location_change_place else Res.string.location_pick_on_map),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }

    if (showPicker) {
        GeohashLocationPickerDialog(
            initialGeohash = picked,
            onDismiss = { showPicker = false },
            onConfirm = { cell ->
                model.pickedGeoHash = cell
                showPicker = false
            },
        )
    }
}
