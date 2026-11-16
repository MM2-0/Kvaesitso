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
package com.vitorpamplona.amethyst.service.cast

import android.content.Context
import com.vitorpamplona.amethyst.service.cast.chromecast.ChromecastCaster
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "CastRegistry"

/**
 * Thin wrapper around the platform-flavor [ChromecastCaster]. Ref-counts
 * discovery starts/stops so multiple UI callers (picker dialog open + cast
 * tap) can coordinate without tearing the underlying scan down mid-cast.
 *
 * On fdroid the wrapped [ChromecastCaster] is a no-op stub — the registry
 * still constructs and answers cleanly, but discovery returns no devices.
 * Casting is hidden entirely on fdroid via `BuildConfig.IS_CASTING_AVAILABLE`.
 */
class CastRegistry(
    appContext: Context,
) {
    private val caster = ChromecastCaster(appContext)

    val devices: StateFlow<List<CastDevice>> = caster.devices
    val sessionState: StateFlow<CastSessionState> = caster.sessionState

    private val refCount = AtomicInteger(0)

    fun startDiscovery() {
        val before = refCount.getAndIncrement()
        Log.d(TAG) { "startDiscovery refCount $before -> ${before + 1}" }
        if (before == 0) caster.startDiscovery()
    }

    fun stopDiscovery() {
        val after = refCount.decrementAndGet()
        Log.d(TAG) { "stopDiscovery refCount -> $after" }
        if (after <= 0) {
            refCount.set(0)
            caster.stopDiscovery()
        }
    }

    suspend fun cast(
        device: CastDevice,
        request: CastRequest,
    ) {
        Log.d(TAG) { "cast device=${device.name} url=${request.url} mime=${request.mimeType}" }
        caster.cast(device, request)
    }

    suspend fun stopCasting() {
        Log.d(TAG) { "stopCasting" }
        caster.stopCasting()
    }
}
