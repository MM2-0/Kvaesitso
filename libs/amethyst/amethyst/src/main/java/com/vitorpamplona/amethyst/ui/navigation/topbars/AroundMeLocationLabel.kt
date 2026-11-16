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
package com.vitorpamplona.amethyst.ui.navigation.topbars

import android.Manifest
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.lack_location_permissions
import com.vitorpamplona.amethyst.commons.resources.loading_location
import com.vitorpamplona.amethyst.commons.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font12SP
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer

/** The "around me" feed's subtitle: asks for location, then shows the city it resolved to. */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AndroidAroundMeLocationLabel() {
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (!locationPermissionState.status.isGranted) {
        LaunchedEffect(locationPermissionState) { locationPermissionState.launchPermissionRequest() }

        Text(
            text = stringRes(Res.string.lack_location_permissions),
            fontSize = Font12SP,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        val location by LocalAppServices.current.deviceLocation.geohashStateFlow
            .collectAsStateWithLifecycle()

        when (val myLocation = location) {
            is LocationResult.Success -> {
                LoadCityName(
                    geohashStr = myLocation.geoHash.toString(),
                    onLoading = {
                        Row {
                            Text(
                                text = "(${myLocation.geoHash})",
                                fontSize = Font12SP,
                                lineHeight = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = StdHorzSpacer)
                            LoadingAnimation(indicatorSize = 12.dp, circleWidth = 2.dp)
                        }
                    },
                ) { cityName ->
                    Text(
                        text = "($cityName)",
                        fontSize = Font12SP,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            LocationResult.LackPermission -> {
                Text(
                    text = stringRes(Res.string.lack_location_permissions),
                    fontSize = Font12SP,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            LocationResult.Loading -> {
                Text(
                    text = stringRes(Res.string.loading_location),
                    fontSize = Font12SP,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
