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

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Wraps [LocationManager] update registration as a cold [Flow].
 *
 * Registers on **one** provider, chosen by [LocationProviderLadder], rather than
 * on every provider the device reports. The previous shotgun cost four
 * simultaneous registrations — passive, network, fused and gps, the last at
 * HIGH_ACCURACY — to produce a 5 km geohash.
 *
 * Takes a [LocationManager] rather than a `Context` so the registration
 * behaviour is unit-testable; the caller does the `getSystemService` lookup.
 *
 * [onListening] is fired from inside the flow, after a registration succeeds, and
 * released again from the `try`/`finally` that wraps everything after it, never
 * as an `onStart`/`onCompletion` pair on the returned flow. The distinction
 * matters: an `onStart` fires on collection even when nothing registered, so a
 * device with no usable provider would accrue location time with no location
 * running, and — because the ledger refcounts the two [LocationState] flows
 * together — the unpaired close would steal the other flow's holder.
 *
 * The pair is kept honest from both ends. The acquire cannot fire without a
 * registration, because a failure to register throws before reaching it. The
 * release cannot be skipped, because everything after the acquire runs inside a
 * `try`/`finally` rather than inside `awaitClose` — [freshestLastKnownLocation]
 * (the seed sweep below) can throw a non-cancellation exception and unwind
 * before `awaitClose` is ever reached, and `try`/`finally` is what still runs
 * the release on that path; see `releasesTheRegistrationWhenTheSeedThrows` in
 * `LocationFlowTest`. (In principle a collector cancelling mid-seed would also
 * unwind past `awaitClose` the same way, but that path could not be
 * reproduced — `callbackFlow`'s buffer is empty at this point, so the single
 * seed send returns without suspending and never observes the cancellation.)
 */
class LocationFlow(
    private val locationManager: LocationManager,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    @Suppress("MissingPermission")
    fun get(
        minTimeMs: Long,
        minDistanceM: Float,
        onListening: ((Boolean) -> Unit)? = null,
    ): Flow<Location> =
        callbackFlow {
            val locationCallback =
                LocationListener { location ->
                    Log.d("LocationFlow") { "onLocationChanged $location" }
                    launch { send(location) }
                }

            // One binder call, reused for both the ladder filter and the seed.
            val providers = locationManager.allProviders

            val candidates = LocationProviderLadder.chooseProviders(sdkInt) { it in providers }

            val registered =
                candidates.firstOrNull { requestUpdates(it, minTimeMs, minDistanceM, locationCallback) }
                    ?: throw SecurityException("No usable location provider. Candidates: $candidates")

            Log.i("LocationFlow") { "Listening on $registered every ${minTimeMs}ms / ${minDistanceM}m" }
            onListening?.invoke(true)

            // Cleanup lives in this finally, not in awaitClose — see the class
            // KDoc, and `releasesTheRegistrationWhenTheSeedThrows` in
            // LocationFlowTest, which fails if it moves.
            try {
                // Seeded after registration so the no-provider path throws
                // without having emitted anything; seeding first would show the
                // consumer Success -> LackPermission on a device with no
                // compatible provider.
                freshestLastKnownLocation(candidates)?.let {
                    Log.d("LocationFlow") { "Last known location is $it" }
                    send(it)
                }

                awaitClose { }
            } finally {
                Log.i("LocationFlow") { "Stopped listening on $registered" }
                locationManager.removeUpdates(locationCallback)
                onListening?.invoke(false)
            }
        }

    /** True when the registration was accepted; false when the provider refused it. */
    @Suppress("MissingPermission")
    private fun requestUpdates(
        provider: String,
        minTimeMs: Long,
        minDistanceM: Float,
        locationCallback: LocationListener,
    ): Boolean =
        try {
            locationManager.requestLocationUpdates(
                provider,
                minTimeMs,
                minDistanceM,
                locationCallback,
                Looper.getMainLooper(),
            )
            true
        } catch (e: SecurityException) {
            Log.w("LocationFlow", "Provider $provider refused the update request", e)
            false
        }

    /**
     * The freshest cached fix across the providers the ladder deemed usable.
     * Sweeping every provider the device reports instead would, on the
     * coarse-only legacy path, pay a guaranteed-to-throw binder call per
     * fine-only provider on every flow start. Still guarded per provider, like
     * the update request is — on a device where one refuses us, the others
     * should still seed.
     */
    @Suppress("MissingPermission")
    private fun freshestLastKnownLocation(providers: List<String>): Location? =
        providers
            .mapNotNull { provider ->
                try {
                    locationManager.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    Log.w("LocationFlow", "No permission to read the last known location of $provider", e)
                    null
                }
            }.maxByOrNull { it.time }
}
