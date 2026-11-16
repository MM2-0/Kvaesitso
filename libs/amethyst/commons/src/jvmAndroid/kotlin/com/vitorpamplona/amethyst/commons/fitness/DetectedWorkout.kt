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
package com.vitorpamplona.amethyst.commons.fitness

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType

/** Where a workout in the training log came from. */
enum class WorkoutOrigin {
    /** Recorded by a watch or fitness app and read from Health Connect. */
    HEALTH_CONNECT,

    /** A kind 1301 the user published themselves — typed into Amethyst, or posted from another client. */
    PUBLISHED,
}

/**
 * A finished workout in the user's training log: read from Health Connect, or
 * recovered from a kind 1301 they published. Carries the fields Amethyst
 * can publish as a NIP-101e kind 1301 event. Platform-neutral and free of any
 * Health Connect types so it can feed the navigation route and the suggestion
 * UI directly.
 *
 * [id] is the Health Connect record id, used to remember which sessions the
 * user has already handled (accepted or dismissed) so each is offered once. When
 * several close-by sessions of the same type are combined by [WorkoutMerger],
 * [id] becomes the members' ids joined with `+` and [sessionCount] rises above 1.
 */
@Immutable
data class DetectedWorkout(
    val id: String,
    val exercise: ExerciseType,
    val title: String?,
    val startTimeEpochSeconds: Long,
    val durationSeconds: Long,
    val distanceMeters: Double?,
    val calories: Int?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val steps: Int?,
    val elevationGainMeters: Double?,
    /** Human-readable name of the app/device that wrote the record (e.g. "Samsung Health"). */
    val source: String,
    /**
     * Which store this came from. Defaults to Health Connect because that is where the type
     * originated and where every constructor but the published-event mapper still builds from.
     */
    val origin: WorkoutOrigin = WorkoutOrigin.HEALTH_CONNECT,
    /**
     * Whether a kind 1301 for this workout already exists.
     *
     * Not the same question as [origin]: a workout read from Health Connect is published the
     * moment the user shares it, and [TrainingLog.merge] keeps the richer Health Connect copy
     * rather than the published one — so the survivor is still [WorkoutOrigin.HEALTH_CONNECT]
     * while a kind 1301 for it is already out there. Anything offering to publish a workout
     * must read this, not the origin, or it offers to post the same effort twice.
     */
    val alreadyPublished: Boolean = false,
    /**
     * How many Health Connect sessions this workout represents. 1 for a raw
     * session; higher when [WorkoutMerger] combined several close-by same-type
     * sessions (e.g. a long run split around breaks) into a single suggestion.
     */
    val sessionCount: Int = 1,
) {
    /**
     * Whether this carries any metric beyond its duration.
     *
     * False means one of two things, and the difference matters to whoever is holding two copies
     * of the same effort: the activity genuinely has none to record (a gym session has no
     * distance and often no calories), or they are simply not known yet — a Health Connect
     * session read before its per-session aggregations have come back. Either way a copy that
     * has some is the better one to keep, which is what [TrainingLog.merge] uses this for.
     */
    val hasAnyMetric: Boolean
        get() =
            distanceMeters != null ||
                calories != null ||
                avgHeartRate != null ||
                maxHeartRate != null ||
                steps != null ||
                elevationGainMeters != null
}
