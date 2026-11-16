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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.lack_location_permissions
import com.vitorpamplona.amethyst.commons.resources.loading_location
import com.vitorpamplona.amethyst.commons.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.ILocationGrabber
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer

@Composable
fun DisplayLocationObserver(viewModel: ILocationGrabber) {
    val location by viewModel.locationFlow().collectAsStateWithLifecycle()

    when (val myLocation = location) {
        is LocationResult.Success -> {
            DisplayLocationInTitle(geohash = myLocation.geoHash.toString())
        }

        LocationResult.LackPermission -> {
            Text(
                text = stringRes(Res.string.lack_location_permissions),
                fontSize = 12.sp,
                lineHeight = 12.sp,
            )
        }

        LocationResult.Loading -> {
            Text(
                text = stringRes(Res.string.loading_location),
                fontSize = 12.sp,
                lineHeight = 12.sp,
            )
        }
    }
}

@Composable
fun DisplayLocationInTitle(geohash: String) {
    LoadCityName(
        geohashStr = geohash,
        onLoading = {
            Spacer(modifier = StdHorzSpacer)
            LoadingAnimation()
        },
    ) { cityName ->
        Text(
            text = cityName,
            fontSize = 20.sp,
            fontWeight = FontWeight.W500,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.padding(start = Size5dp),
        )
    }
}
