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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.suggestion

import android.text.format.DateUtils
import com.vitorpamplona.amethyst.commons.fitness.DetectedWorkout
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.SourceTag

/** Builds the pre-filled composer route for a detected workout. Shared by the
 * feed suggestion banner and the New Workout carousel so they never drift. */
internal fun DetectedWorkout.toNewWorkoutRoute(title: String) =
    Route.NewWorkout(
        exercise = exercise.code,
        title = title,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters ?: 0.0,
        calories = calories ?: 0,
        avgHeartRate = avgHeartRate ?: 0,
        maxHeartRate = maxHeartRate ?: 0,
        steps = steps ?: 0,
        elevationGainMeters = elevationGainMeters ?: 0.0,
        startTime = startTimeEpochSeconds,
        // The `source` tag carries the NIP-101e vocabulary token, not the writing app's
        // label: the badge in the feed renders it uppercased next to "GPS" and "MANUAL",
        // and other clients match on the token. DetectedWorkout.source stays the friendly
        // name for the UI to show.
        source = SourceTag.HEALTH_CONNECT,
    )

internal fun formatWorkoutDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

internal fun workoutRelativeTime(epochSeconds: Long): String =
    DateUtils
        .getRelativeTimeSpanString(
            epochSeconds * 1000L,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
