package de.mm20.launcher2.ktx

import android.location.Location
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

/* https://github.com/streetcomplete/StreetComplete/blob/master/app/src/main/java/de/westnordost/streetcomplete/util/location/LocationUtils.kt
 * GPLv3
 */
fun Location.isBetterThan(previous: Location?): Boolean {
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