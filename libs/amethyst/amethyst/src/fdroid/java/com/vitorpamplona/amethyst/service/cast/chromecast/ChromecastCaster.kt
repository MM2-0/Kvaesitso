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
package com.vitorpamplona.amethyst.service.cast.chromecast

import android.content.Context
import com.vitorpamplona.amethyst.service.cast.CastDevice
import com.vitorpamplona.amethyst.service.cast.CastRequest
import com.vitorpamplona.amethyst.service.cast.CastSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * F-Droid stub: the Google Cast SDK requires Google Play services, which are
 * not present on the FOSS build. This caster always reports an empty device
 * list so the registry simply returns no devices. The cast button is hidden
 * on fdroid via `BuildConfig.IS_CASTING_AVAILABLE`, so this stub should never
 * be exercised at runtime; it exists to keep the shared-source-set
 * `CastRegistry` constructible.
 */
@Suppress("UNUSED_PARAMETER")
class ChromecastCaster(
    appContext: Context,
) {
    val devices: StateFlow<List<CastDevice>> = MutableStateFlow<List<CastDevice>>(emptyList()).asStateFlow()

    val sessionState: StateFlow<CastSessionState> = MutableStateFlow<CastSessionState>(CastSessionState.Idle).asStateFlow()

    fun startDiscovery() = Unit

    fun stopDiscovery() = Unit

    suspend fun cast(
        device: CastDevice,
        request: CastRequest,
    ) = Unit

    suspend fun stopCasting() = Unit
}
