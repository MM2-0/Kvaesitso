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
package com.vitorpamplona.amethyst.commons.model.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The device's position as geohashes, for "around me" feeds, location chats and the composers'
 * location tag. The platform decides when it actually listens; readers only collect.
 */
interface DeviceLocation {
    /** A roughly 5 km geohash cell, or why there is none. */
    val geohashStateFlow: StateFlow<LocationResult>

    /** The finest geohash the platform offers, for location channels that truncate it per level. */
    val preciseGeohashStateFlow: StateFlow<LocationResult>

    /** Tells the source whether the user has granted location access, so it can start or stop. */
    fun setLocationPermission(newValue: Boolean)

    /** No location source on this platform: always reports a missing permission. */
    object None : DeviceLocation {
        private val lacking = MutableStateFlow<LocationResult>(LocationResult.LackPermission)

        override val geohashStateFlow: StateFlow<LocationResult> = lacking
        override val preciseGeohashStateFlow: StateFlow<LocationResult> = lacking

        override fun setLocationPermission(newValue: Boolean) = Unit
    }
}
