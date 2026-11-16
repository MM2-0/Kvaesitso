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

import android.location.LocationManager
import android.os.Build

/**
 * Picks which location providers to try, in order.
 *
 * Deliberately selects on **provider existence**, never on
 * [LocationManager.isProviderEnabled]. A registration on a disabled provider
 * goes live by itself when the user enables location — including from the
 * quick-settings shade without leaving the app, which is exactly what someone
 * does after seeing an empty "Around Me" feed. An enabled-state guard evaluated
 * once at subscription start would lose that.
 *
 * Below API 31, `gps`, `passive` and `fused` required `ACCESS_FINE_LOCATION`;
 * only `network` accepted `ACCESS_COARSE_LOCATION`. Approximate location, which
 * lets a coarse-only app request any provider and receive a fuzzed result, is an
 * Android 12 change. Amethyst declares coarse only, so the legacy branch is
 * unconditional below API 31.
 *
 * Adding `ACCESS_FINE_LOCATION` later would **not** widen this on its own — the
 * branch below has no permission input, so pre-31 devices would keep getting
 * `network` alone and silently lose the precision the new permission was granted
 * for. Whoever adds it must widen the condition here too.
 *
 * Returns the ordered candidate list rather than a single choice so the caller
 * can fall through to the next rung if a registration is refused. An empty list
 * means no compatible provider exists.
 */
object LocationProviderLadder {
    // Compile-time String constants, inlined by the compiler, so naming
    // FUSED_PROVIDER (added in API 31) is safe on older runtimes.
    @Suppress("InlinedApi")
    private val FULL_LADDER =
        listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

    private val COARSE_ONLY_LEGACY_LADDER = listOf(LocationManager.NETWORK_PROVIDER)

    fun chooseProviders(
        sdkInt: Int,
        exists: (String) -> Boolean,
    ): List<String> {
        val ladder = if (sdkInt >= Build.VERSION_CODES.S) FULL_LADDER else COARSE_ONLY_LEGACY_LADDER

        return ladder.filter(exists)
    }
}
