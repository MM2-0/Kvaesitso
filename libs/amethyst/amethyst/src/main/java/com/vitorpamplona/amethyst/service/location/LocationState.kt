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

import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.vitorpamplona.amethyst.commons.model.location.DeviceLocation
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChannelLevel
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeohashPrecision
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

// `toGeoHash` is an extension on Location declared in LocationGeoHash.kt, same
// package, so it needs no import.

/**
 * Turns the device's location into geohashes, listening **only while the app is
 * in the foreground**.
 *
 * The gate is not an optimisation of last resort: `Account` builds 30
 * `SharingStarted.Eagerly` top-nav filter states on the account scope, and
 * `AccountSettings.defaultProductsFollowList` ships as `TopFilter.AroundMe`, so
 * without it every user with location permission holds a registration for the
 * life of the process. See `amethyst/plans/2026-07-29-location-foreground-gate.md`.
 *
 * Switching the *consumers* to `WhileSubscribed` is not an option: roughly 60
 * call sites read `account.live*FollowLists.value` synchronously rather than
 * collecting, and would silently serve a stale or initial value.
 */
class LocationState(
    context: Context,
    private val scope: CoroutineScope,
    private val isForeground: StateFlow<Boolean>,
    /**
     * Resource-ledger hook: true while location updates are actively
     * registered. Reaches the OS only through the default [locationSource],
     * which hands it to [LocationFlow] — a caller that overrides
     * [locationSource] (the tests do) is responsible for firing it, or not.
     */
    private val onListening: ((Boolean) -> Unit)? = null,
    private val locationSource: (Long, Float) -> Flow<Location> = { minTimeMs, minDistanceM ->
        LocationFlow(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
            .get(minTimeMs, minDistanceM, onListening)
    },
) : DeviceLocation {
    companion object {
        /** A 5 km cell takes 2.5 minutes to cross at 120 km/h; 60s/500m is ample. */
        const val COARSE_MIN_TIME: Long = 60_000L
        const val COARSE_MIN_DISTANCE: Float = 500.0f

        /** Building-level geohashes need the tighter profile. */
        const val PRECISE_MIN_TIME: Long = 10_000L
        const val PRECISE_MIN_DISTANCE: Float = 100.0f

        /**
         * How long to keep listening after the last activity stops, so a
         * one-second app switch doesn't destroy and rebuild the registration.
         * Same intent as [SUBSCRIPTION_STOP_TIMEOUT_MS], on the other axis.
         */
        const val BACKGROUND_GRACE_MS: Long = 5_000L

        /**
         * How long `stateIn` keeps the upstream alive after the last collector
         * leaves, so a screen rotation or a tab switch doesn't rebuild the
         * registration either.
         */
        const val SUBSCRIPTION_STOP_TIMEOUT_MS: Long = 5_000L
    }

    private enum class Gate { NoPermission, Paused, Listen }

    private var hasLocationPermission = MutableStateFlow(false)

    // Read by R1 below to decide whether to emit Loading, from a different
    // coroutine than the onEach that writes it — hence a StateFlow rather than
    // a plain field.
    private val latestLocation = MutableStateFlow<LocationResult>(LocationResult.Loading)

    private val latestPreciseLocation = MutableStateFlow<LocationResult>(LocationResult.Loading)

    override fun setLocationPermission(newValue: Boolean) {
        if (newValue != hasLocationPermission.value) {
            hasLocationPermission.tryEmit(newValue)
        }
    }

    /**
     * Foreground with an asymmetric delay: leaving the foreground waits out
     * [BACKGROUND_GRACE_MS], returning to it is immediate.
     *
     * `debounce(5000)` would delay both edges, and the duration-selector
     * overload that allows an asymmetric delay is `@FlowPreview`.
     * `transformLatest` cancels the pending `delay` when foreground returns
     * first, which is exactly the semantics wanted, with no preview opt-in.
     *
     * Known and harmless: [ForegroundTracker] starts at `false`, so on a
     * process that starts backgrounded the first emission — and therefore the
     * first gate verdict, including `LackPermission` — is delayed by
     * [BACKGROUND_GRACE_MS]. Nothing renders while backgrounded, and a process
     * that starts into the foreground emits immediately, because the activity's
     * `onStart` cancels the pending delay.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val settledForeground: Flow<Boolean> =
        isForeground.transformLatest { foreground ->
            if (!foreground) delay(BACKGROUND_GRACE_MS)
            emit(foreground)
        }

    private val gate: Flow<Gate> =
        combine(hasLocationPermission, settledForeground) { permitted, foreground ->
            when {
                !permitted -> Gate.NoPermission
                foreground -> Gate.Listen
                else -> Gate.Paused
            }
        }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun buildGeohashStateFlow(
        tag: String,
        charsCount: Int,
        minTimeMs: Long,
        minDistanceM: Float,
        cache: MutableStateFlow<LocationResult>,
    ): StateFlow<LocationResult> =
        gate
            .transformLatest { state ->
                when (state) {
                    // Deliberately does NOT write to the cache. Today's code emits
                    // LackPermission without touching the cache, and wiping it
                    // here would cost a Loading emission — and so an empty-feed
                    // flash — on every permission flap, which is the regression R1
                    // exists to prevent. Consumers already see LackPermission from
                    // the StateFlow; the cache is internal and only decides whether
                    // Loading is emitted.
                    Gate.NoPermission -> emit(LocationResult.LackPermission)

                    // Emit nothing: stateIn keeps the last value, so every
                    // synchronous .value reader still sees the last known geohash
                    // while the OS registration is released.
                    Gate.Paused -> Unit

                    Gate.Listen -> {
                        // Only when there is nothing cached. Emitting Loading on
                        // every foreground return would flash the "Around Me" feed
                        // empty, because AroundMeFeedFlow.convert maps anything
                        // that is not Success to an empty geotag set.
                        if (cache.value !is LocationResult.Success) emit(LocationResult.Loading)

                        emitAll(
                            locationSource(minTimeMs, minDistanceM)
                                .map { LocationResult.Success(it.toGeoHash(charsCount)) as LocationResult }
                                .onEach { cache.value = it }
                                .catch { e ->
                                    Log.w(tag, "Exception in the flow", e)
                                    cache.value = LocationResult.LackPermission
                                    emit(LocationResult.LackPermission)
                                },
                        )
                    }
                }
            }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIPTION_STOP_TIMEOUT_MS), cache.value)

    override val geohashStateFlow: StateFlow<LocationResult> by lazy {
        buildGeohashStateFlow(
            tag = "GeohashStateFlow",
            charsCount = GeohashPrecision.KM_5_X_5.digits,
            minTimeMs = COARSE_MIN_TIME,
            minDistanceM = COARSE_MIN_DISTANCE,
            cache = latestLocation,
        )
    }

    /**
     * Like [geohashStateFlow] but at building-level precision
     * ([GeohashChannelLevel.BUILDING] = 8 chars). Location channels truncate this
     * to every coarser level (a geohash is a prefix code), so one fix yields the
     * whole region→building ladder. Kept separate so the coarser
     * [geohashStateFlow] the "around me" feed relies on is unchanged.
     *
     * Note that Amethyst declares only `ACCESS_COARSE_LOCATION`, so Android
     * fuzzes every fix to roughly a 3 km grid and this is not in fact
     * building-level today. The profile is kept so the intent survives if the
     * app ever requests `ACCESS_FINE_LOCATION`.
     */
    override val preciseGeohashStateFlow: StateFlow<LocationResult> by lazy {
        buildGeohashStateFlow(
            tag = "PreciseGeohashStateFlow",
            charsCount = GeohashChannelLevel.BUILDING.chars,
            minTimeMs = PRECISE_MIN_TIME,
            minDistanceM = PRECISE_MIN_DISTANCE,
            cache = latestPreciseLocation,
        )
    }
}
