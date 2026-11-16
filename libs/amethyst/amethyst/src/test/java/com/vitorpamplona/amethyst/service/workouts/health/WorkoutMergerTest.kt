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
package com.vitorpamplona.amethyst.service.workouts.health

import com.vitorpamplona.amethyst.commons.fitness.DetectedWorkout
import com.vitorpamplona.amethyst.commons.fitness.health.WorkoutMerger
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMergerTest {
    private val hour = 3600L

    private fun workout(
        id: String,
        exercise: ExerciseType = ExerciseType.RUNNING,
        startTimeEpochSeconds: Long,
        durationSeconds: Long,
        distanceMeters: Double? = null,
        calories: Int? = null,
        avgHeartRate: Int? = null,
        maxHeartRate: Int? = null,
        steps: Int? = null,
        elevationGainMeters: Double? = null,
        title: String? = null,
        source: String = "Samsung Health",
    ) = DetectedWorkout(
        id = id,
        exercise = exercise,
        title = title,
        startTimeEpochSeconds = startTimeEpochSeconds,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        calories = calories,
        avgHeartRate = avgHeartRate,
        maxHeartRate = maxHeartRate,
        steps = steps,
        elevationGainMeters = elevationGainMeters,
        source = source,
    )

    @Test
    fun emptyListPassesThrough() {
        assertTrue(WorkoutMerger.mergeCloseWorkouts(emptyList()).isEmpty())
    }

    @Test
    fun singleWorkoutIsReturnedUnchanged() {
        val one = workout("a", startTimeEpochSeconds = 0, durationSeconds = 100)
        val result = WorkoutMerger.mergeCloseWorkouts(listOf(one))
        assertEquals(1, result.size)
        assertSame(one, result.first())
        assertEquals(1, result.first().sessionCount)
    }

    @Test
    fun twoCloseSameTypeSessionsMergeAndSumMetrics() {
        // Run 10:00-11:00, then Run 11:30-12:00 (30 min gap < 1h): one 90-min run.
        val first =
            workout(
                id = "first",
                startTimeEpochSeconds = 0,
                durationSeconds = hour,
                distanceMeters = 10_000.0,
                calories = 600,
                steps = 12_000,
                elevationGainMeters = 50.0,
            )
        val second =
            workout(
                id = "second",
                startTimeEpochSeconds = hour + 1800, // starts 30 min after first ends
                durationSeconds = 1800,
                distanceMeters = 5_000.0,
                calories = 300,
                steps = 6_000,
                elevationGainMeters = 25.0,
            )

        val result = WorkoutMerger.mergeCloseWorkouts(listOf(first, second))

        assertEquals(1, result.size)
        val merged = result.first()
        assertEquals("first+second", merged.id)
        assertEquals(2, merged.sessionCount)
        assertEquals(0, merged.startTimeEpochSeconds) // earliest start
        assertEquals(hour + 1800, merged.durationSeconds) // summed active duration
        assertEquals(15_000.0, merged.distanceMeters!!, 0.0001)
        assertEquals(900, merged.calories)
        assertEquals(18_000, merged.steps)
        assertEquals(75.0, merged.elevationGainMeters!!, 0.0001)
    }

    @Test
    fun sameTypeButFarApartDoesNotMerge() {
        val first = workout("first", startTimeEpochSeconds = 0, durationSeconds = hour)
        // Starts 90 min after the first one ends -> gap exceeds the 1h threshold.
        val second = workout("second", startTimeEpochSeconds = hour + hour + 1800, durationSeconds = hour)

        val result = WorkoutMerger.mergeCloseWorkouts(listOf(first, second))

        assertEquals(2, result.size)
        assertEquals(listOf("first", "second"), result.map { it.id })
    }

    @Test
    fun gapOfExactlyOneHourDoesNotMerge() {
        val first = workout("first", startTimeEpochSeconds = 0, durationSeconds = hour)
        // Starts exactly 1h after the first ends -> boundary is exclusive.
        val second = workout("second", startTimeEpochSeconds = hour + hour, durationSeconds = hour)

        val result = WorkoutMerger.mergeCloseWorkouts(listOf(first, second))

        assertEquals(2, result.size)
    }

    @Test
    fun differentTypesCloseTogetherDoNotMerge() {
        val run = workout("run", exercise = ExerciseType.RUNNING, startTimeEpochSeconds = 0, durationSeconds = hour)
        val ride = workout("ride", exercise = ExerciseType.CYCLING, startTimeEpochSeconds = hour + 60, durationSeconds = hour)

        val result = WorkoutMerger.mergeCloseWorkouts(listOf(run, ride))

        assertEquals(2, result.size)
    }

    @Test
    fun interleavedOtherTypeDoesNotBreakSameTypeChain() {
        // Run, then a short walk during a break, then Run again — the two runs
        // are close in time and should still combine across the walk.
        val run1 = workout("run1", exercise = ExerciseType.RUNNING, startTimeEpochSeconds = 0, durationSeconds = hour)
        val walk = workout("walk", exercise = ExerciseType.WALKING, startTimeEpochSeconds = hour + 300, durationSeconds = 600)
        val run2 = workout("run2", exercise = ExerciseType.RUNNING, startTimeEpochSeconds = hour + 1200, durationSeconds = hour)

        val result = WorkoutMerger.mergeCloseWorkouts(listOf(run1, walk, run2))

        assertEquals(2, result.size)
        val run = result.first { it.exercise == ExerciseType.RUNNING }
        assertEquals("run1+run2", run.id)
        assertEquals(2, run.sessionCount)
        assertEquals(2 * hour, run.durationSeconds)
        assertEquals(1, result.first { it.exercise == ExerciseType.WALKING }.sessionCount)
    }

    @Test
    fun heartRateIsDurationWeighted() {
        // 1h at 120 bpm + 0.5h at 150 bpm -> (120*3600 + 150*1800) / 5400 = 130.
        val first = workout("first", startTimeEpochSeconds = 0, durationSeconds = hour, avgHeartRate = 120, maxHeartRate = 140)
        val second = workout("second", startTimeEpochSeconds = hour + 60, durationSeconds = 1800, avgHeartRate = 150, maxHeartRate = 175)

        val merged = WorkoutMerger.mergeCloseWorkouts(listOf(first, second)).single()

        assertEquals(130, merged.avgHeartRate)
        assertEquals(175, merged.maxHeartRate)
    }

    @Test
    fun nullMetricsAreSummedOnlyWhenPresent() {
        val first = workout("first", startTimeEpochSeconds = 0, durationSeconds = hour, distanceMeters = 10_000.0, calories = null)
        val second = workout("second", startTimeEpochSeconds = hour + 60, durationSeconds = 1800, distanceMeters = null, calories = 200)

        val merged = WorkoutMerger.mergeCloseWorkouts(listOf(first, second)).single()

        // Distance present only on one -> that value survives; calories only on the other.
        assertEquals(10_000.0, merged.distanceMeters!!, 0.0001)
        assertEquals(200, merged.calories)
    }

    @Test
    fun allNullMetricStaysNull() {
        val first = workout("first", startTimeEpochSeconds = 0, durationSeconds = hour)
        val second = workout("second", startTimeEpochSeconds = hour + 60, durationSeconds = 1800)

        val merged = WorkoutMerger.mergeCloseWorkouts(listOf(first, second)).single()

        assertNull(merged.distanceMeters)
        assertNull(merged.calories)
        assertNull(merged.avgHeartRate)
        assertNull(merged.maxHeartRate)
        assertNull(merged.steps)
        assertNull(merged.elevationGainMeters)
    }

    @Test
    fun titleTakesFirstNonBlank() {
        val first = workout("first", startTimeEpochSeconds = 0, durationSeconds = hour, title = "  ")
        val second = workout("second", startTimeEpochSeconds = hour + 60, durationSeconds = 1800, title = "Morning long run")

        val merged = WorkoutMerger.mergeCloseWorkouts(listOf(first, second)).single()

        assertEquals("Morning long run", merged.title)
    }

    @Test
    fun unsortedInputProducesResultsOrderedByStart() {
        // Three same-type runs, all within an hour of each other, given out of order.
        val a = workout("a", startTimeEpochSeconds = 0, durationSeconds = 600)
        val b = workout("b", startTimeEpochSeconds = 1200, durationSeconds = 600)
        val c = workout("c", startTimeEpochSeconds = 2400, durationSeconds = 600)

        val merged = WorkoutMerger.mergeCloseWorkouts(listOf(c, a, b)).single()

        assertEquals("a+b+c", merged.id)
        assertEquals(3, merged.sessionCount)
        assertEquals(0, merged.startTimeEpochSeconds)
        assertEquals(1800, merged.durationSeconds)
    }

    @Test
    fun skeletonsWithoutMetricsGroupExactlyAsTheFullyLoadedOnesDo() {
        // The progressive read merges twice: once over session skeletons, to get something on
        // screen, and again once each session's metrics have been aggregated. Grouping keys on
        // type, start and duration only, so both passes must produce the same shape — otherwise
        // the dashboard would reshuffle its workout list when the metrics land.
        val loaded =
            listOf(
                workout("a", startTimeEpochSeconds = 0, durationSeconds = 600, distanceMeters = 2_000.0, calories = 150, avgHeartRate = 140, steps = 1_800),
                workout("b", startTimeEpochSeconds = 1200, durationSeconds = 600, distanceMeters = 2_100.0, calories = 160, avgHeartRate = 150, steps = 1_900),
                workout("c", exercise = ExerciseType.CYCLING, startTimeEpochSeconds = 20_000, durationSeconds = 3600, distanceMeters = 25_000.0),
            )
        val skeletons =
            loaded.map {
                it.copy(distanceMeters = null, calories = null, avgHeartRate = null, maxHeartRate = null, steps = null, elevationGainMeters = null)
            }

        val early = WorkoutMerger.mergeCloseWorkouts(skeletons)
        val late = WorkoutMerger.mergeCloseWorkouts(loaded)

        assertEquals(late.map { it.id }, early.map { it.id })
        assertEquals(late.map { it.sessionCount }, early.map { it.sessionCount })
        assertEquals(late.map { it.startTimeEpochSeconds }, early.map { it.startTimeEpochSeconds })
        assertEquals(late.map { it.durationSeconds }, early.map { it.durationSeconds })
    }

    @Test
    fun aggregatingPerSessionBeforeMergingIsWhatSumsASplitEffort() {
        // Why the progressive read still aggregates each raw session and merges afterwards,
        // rather than aggregating the merged span once: the merged workout's metrics are the
        // sum of its members', and the span between them is not part of the effort.
        val first = workout("a", startTimeEpochSeconds = 0, durationSeconds = 600, distanceMeters = 2_000.0, calories = 150, steps = 1_800)
        val second = workout("b", startTimeEpochSeconds = 1200, durationSeconds = 600, distanceMeters = 2_100.0, calories = 160, steps = 1_900)

        val merged = WorkoutMerger.mergeCloseWorkouts(listOf(first, second)).single()

        assertEquals(4_100.0, merged.distanceMeters!!, 0.001)
        assertEquals(310, merged.calories)
        assertEquals(3_700, merged.steps)
        // The 10-minute break between them is excluded: duration is the sum, not end minus start.
        assertEquals(1200, merged.durationSeconds)
    }

    @Test
    fun chainMergesEvenWhenAdjacentGapsAreShortButEndsAreFarApart() {
        // 45-min sessions each starting 50 min apart: consecutive gaps are 5 min,
        // so the whole chain merges though first and last are hours apart.
        val sessions =
            (0 until 5).map {
                workout("s$it", startTimeEpochSeconds = it * 3000L, durationSeconds = 2700)
            }

        val merged = WorkoutMerger.mergeCloseWorkouts(sessions)

        assertEquals(1, merged.size)
        assertEquals(5, merged.single().sessionCount)
    }
}
