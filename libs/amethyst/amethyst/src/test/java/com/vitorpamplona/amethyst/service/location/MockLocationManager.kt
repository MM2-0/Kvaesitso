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
package com.vitorpamplona.amethyst.service.location

import android.location.LocationListener
import android.location.LocationManager
import io.mockk.every
import io.mockk.mockk

/**
 * A [LocationManager] that reports [providers], has no cached fix, and refuses
 * [denied] with a `SecurityException` — mimicking the pre-API-31 fine-location
 * requirement.
 *
 * Shared by [LocationFlowTest] and [LocationLedgerCompositionTest] so the mock's
 * surface tracks the binder calls [LocationFlow] actually makes in one place.
 */
internal fun mockLocationManager(
    providers: List<String>,
    denied: Set<String> = emptySet(),
): LocationManager {
    val lm = mockk<LocationManager>(relaxed = true)
    every { lm.allProviders } returns providers
    every { lm.getLastKnownLocation(any()) } returns null
    every {
        lm.requestLocationUpdates(any<String>(), any<Long>(), any<Float>(), any<LocationListener>(), any())
    } answers {
        val provider = firstArg<String>()
        if (provider in denied) throw SecurityException("denied: $provider")
    }
    return lm
}
