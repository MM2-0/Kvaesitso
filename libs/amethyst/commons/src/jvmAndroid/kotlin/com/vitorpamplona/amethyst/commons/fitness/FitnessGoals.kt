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

/**
 * What the user is training towards, per calendar week. The My Fitness dashboard is built around
 * these: every chart answers "how am I doing against what I set out to do", not "here is a number".
 *
 * Goals are the user's own and private. They are kept on the device next to the account (see
 * [encode]); nothing about them is published.
 *
 * The defaults follow the WHO adult guideline — at least 150 minutes of moderate activity a week —
 * spread over three sessions on three days, so a user who never opens the editor still sees a
 * sensible target instead of an empty ring.
 */
@Immutable
data class FitnessGoals(
    val weeklyActiveMinutes: Int = WHO_WEEKLY_MINUTES,
    val weeklyWorkouts: Int = 3,
    val weeklyActiveDays: Int = 3,
    /** An optional distance target for one activity — "20 km of running a week". */
    val distance: DistanceGoal? = null,
) {
    /**
     * Distance only means something per activity: 10 km of running and 10 km of cycling are not
     * the same week, so a distance goal names the one it counts.
     */
    @Immutable
    data class DistanceGoal(
        val exercise: ExerciseType,
        val weeklyMeters: Double,
    )

    /** `v1;150;3;3;running;20000` — compact, versioned, and free of any serialization library. */
    fun encode(): String =
        buildString {
            append(VERSION).append(';')
            append(weeklyActiveMinutes).append(';')
            append(weeklyWorkouts).append(';')
            append(weeklyActiveDays)
            distance?.let { append(';').append(it.exercise.code).append(';').append(it.weeklyMeters.toLong()) }
        }

    companion object {
        /** WHO: adults should do at least 150 minutes of moderate-intensity activity a week. */
        const val WHO_WEEKLY_MINUTES = 150

        private const val VERSION = "v1"

        val DEFAULT = FitnessGoals()

        /** The inverse of [encode]; anything unreadable falls back to [DEFAULT] rather than failing. */
        fun decode(encoded: String?): FitnessGoals {
            if (encoded.isNullOrBlank()) return DEFAULT
            val parts = encoded.split(';')
            if (parts.size < 4 || parts[0] != VERSION) return DEFAULT

            val minutes = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return DEFAULT
            val workouts = parts[2].toIntOrNull()?.takeIf { it > 0 } ?: return DEFAULT
            val days = parts[3].toIntOrNull()?.takeIf { it in 1..7 } ?: return DEFAULT

            val distance =
                if (parts.size >= 6) {
                    val exercise = ExerciseType.parse(parts[4])
                    val meters = parts[5].toLongOrNull()?.takeIf { it > 0 }
                    if (exercise != null && meters != null) DistanceGoal(exercise, meters.toDouble()) else null
                } else {
                    null
                }

            return FitnessGoals(minutes, workouts, days, distance)
        }
    }
}
