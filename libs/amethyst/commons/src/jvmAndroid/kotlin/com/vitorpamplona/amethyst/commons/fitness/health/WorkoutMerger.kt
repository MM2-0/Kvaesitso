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
package com.vitorpamplona.amethyst.commons.fitness.health

import com.vitorpamplona.amethyst.commons.fitness.DetectedWorkout
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import kotlin.math.roundToInt

/**
 * Combines Health Connect exercise sessions that belong to the same real-world
 * effort. Watches, Strava, and auto-pause features often split one long workout —
 * a 5-hour run with coffee/water breaks — into several back-to-back
 * `ExerciseSessionRecord`s of the same type. Posting each break as its own
 * workout is noise; the user wants the whole run as one thing.
 *
 * [mergeCloseWorkouts] walks the sessions in start-time order and joins any run
 * of same-type sessions whose consecutive gap (start of the next minus the end of
 * the previous) is under [DEFAULT_MAX_GAP_SECONDS] into a single [DetectedWorkout]:
 * distance, calories, steps, elevation and duration are summed, heart rate is
 * duration-weighted, max heart rate is the max, and [DetectedWorkout.sessionCount]
 * records how many sessions were folded in.
 *
 * Sessions of a different type occurring between two same-type sessions do not
 * break the chain — a brief cross-training block mid-run still lets the two run
 * segments merge — because gaps are tracked per exercise type.
 */
object WorkoutMerger {
    /** Sessions less than one hour apart are treated as one workout. */
    const val DEFAULT_MAX_GAP_SECONDS = 3600L

    /**
     * Merges close-by same-type sessions in [workouts]. Input order is irrelevant
     * (sessions are sorted by start time internally). The result is ordered by
     * each combined workout's start time, ascending. A gap of exactly
     * [maxGapSeconds] does NOT merge — only strictly closer sessions do.
     */
    fun mergeCloseWorkouts(
        workouts: List<DetectedWorkout>,
        maxGapSeconds: Long = DEFAULT_MAX_GAP_SECONDS,
    ): List<DetectedWorkout> {
        if (workouts.size < 2) return workouts

        val sorted = workouts.sortedBy { it.startTimeEpochSeconds }

        val groups = mutableListOf<MutableList<DetectedWorkout>>()
        // Most recent still-open group per exercise type, plus the latest end time
        // seen for that type, so an interleaved activity of a different type never
        // splits a run of same-type sessions.
        val openGroupByType = HashMap<ExerciseType, MutableList<DetectedWorkout>>()
        val lastEndByType = HashMap<ExerciseType, Long>()

        for (workout in sorted) {
            val openGroup = openGroupByType[workout.exercise]
            val lastEnd = lastEndByType[workout.exercise]
            val closeEnough = lastEnd != null && workout.startTimeEpochSeconds - lastEnd < maxGapSeconds

            if (openGroup != null && closeEnough) {
                openGroup.add(workout)
            } else {
                val newGroup = mutableListOf(workout)
                groups.add(newGroup)
                openGroupByType[workout.exercise] = newGroup
            }
            lastEndByType[workout.exercise] = maxOf(lastEnd ?: Long.MIN_VALUE, endOf(workout))
        }

        return groups.map { combine(it) }
    }

    /** End of a raw session: its start plus its (contiguous) duration. */
    private fun endOf(workout: DetectedWorkout): Long = workout.startTimeEpochSeconds + workout.durationSeconds

    /** Folds a group (already sorted by start time) into one [DetectedWorkout]. */
    private fun combine(group: List<DetectedWorkout>): DetectedWorkout {
        if (group.size == 1) return group.first()

        val earliest = group.first()

        val distance = group.mapNotNull { it.distanceMeters }.takeIf { it.isNotEmpty() }?.sum()
        val calories = group.mapNotNull { it.calories }.takeIf { it.isNotEmpty() }?.sum()
        val steps = group.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.sum()
        val elevation = group.mapNotNull { it.elevationGainMeters }.takeIf { it.isNotEmpty() }?.sum()
        val maxHeartRate = group.mapNotNull { it.maxHeartRate }.maxOrNull()

        return DetectedWorkout(
            id = group.joinToString("+") { it.id },
            exercise = earliest.exercise,
            title = group.firstNotNullOfOrNull { it.title?.takeIf(String::isNotBlank) },
            startTimeEpochSeconds = earliest.startTimeEpochSeconds,
            durationSeconds = group.sumOf { it.durationSeconds },
            distanceMeters = distance,
            calories = calories,
            avgHeartRate = weightedAvgHeartRate(group),
            maxHeartRate = maxHeartRate,
            steps = steps,
            elevationGainMeters = elevation,
            source = earliest.source,
            sessionCount = group.sumOf { it.sessionCount },
        )
    }

    /**
     * Duration-weighted average heart rate across the members that reported one,
     * so a 4-hour leg dominates a 5-minute leg. Null when no member has a heart
     * rate; falls back to a plain average if every contributing leg has zero
     * duration (shouldn't happen for real sessions).
     */
    private fun weightedAvgHeartRate(group: List<DetectedWorkout>): Int? {
        val withHeartRate = group.filter { it.avgHeartRate != null }
        if (withHeartRate.isEmpty()) return null

        val weightSum = withHeartRate.sumOf { it.durationSeconds }
        return if (weightSum > 0) {
            withHeartRate
                .sumOf { it.avgHeartRate!!.toDouble() * it.durationSeconds }
                .div(weightSum)
                .roundToInt()
        } else {
            withHeartRate.map { it.avgHeartRate!! }.average().roundToInt()
        }
    }
}
