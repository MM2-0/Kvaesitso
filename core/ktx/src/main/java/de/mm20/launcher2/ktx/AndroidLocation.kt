package de.mm20.launcher2.ktx

import android.hardware.GeomagneticField
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

import android.location.Location as AndroidLocation

/* https://github.com/streetcomplete/StreetComplete/blob/master/app/src/main/java/de/westnordost/streetcomplete/util/location/LocationUtils.kt
 * GPL-3.0-or-later
 */
fun AndroidLocation?.isBetterThan(previous: AndroidLocation?): Boolean {
    if (this == null) return false
    if (longitude.isNaN() || latitude.isNaN()) return false
    if (previous == null) return true

    val locationTimeDiff = elapsedRealtimeNanos.nanoseconds - previous.elapsedRealtimeNanos.nanoseconds
    val isMuchNewer = locationTimeDiff > 2.minutes
    val isMuchOlder = locationTimeDiff < (-2).minutes
    val isNewer = locationTimeDiff.isPositive()

    val accuracyDelta = accuracy - previous.accuracy
    val isLessAccurate = accuracyDelta > 0f
    val isMoreAccurate = accuracyDelta < 0f
    val isMuchLessAccurate = accuracyDelta > 200f

    val isFromSameProvider = provider == previous.provider

    return when {
        // the user has likely moved
        isMuchNewer -> true
        // If the new location is more than two minutes older, it must be worse
        isMuchOlder -> false
        isMoreAccurate -> true
        isNewer && !isLessAccurate -> true
        isNewer && !isMuchLessAccurate && isFromSameProvider -> true
        else -> false
    }
}

val AndroidLocation.declination: Float
    get() = GeomagneticField(
        latitude.toFloat(),
        longitude.toFloat(),
        altitude.toFloat(),
        time
    ).declination

