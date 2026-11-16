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

import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class WorkoutStatsTest {
    private val zone: ZoneId = ZoneId.of("UTC")

    /** Noon UTC so a test never straddles a day boundary by accident. */
    private val now: Instant = ZonedDateTime.of(2026, 3, 15, 12, 0, 0, 0, zone).toInstant()

    private var nextId = 0

    private fun workout(
        daysAgo: Long,
        exercise: ExerciseType = ExerciseType.RUNNING,
        durationSeconds: Long = 1800,
        distanceMeters: Double? = null,
        calories: Int? = null,
        avgHeartRate: Int? = null,
        maxHeartRate: Int? = null,
        steps: Int? = null,
        elevationGainMeters: Double? = null,
    ) = DetectedWorkout(
        id = "w${nextId++}",
        exercise = exercise,
        title = null,
        startTimeEpochSeconds = now.epochSecond - daysAgo * 86_400L,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        calories = calories,
        avgHeartRate = avgHeartRate,
        maxHeartRate = maxHeartRate,
        steps = steps,
        elevationGainMeters = elevationGainMeters,
        source = "Samsung Health",
    )

    @Test
    fun `an empty history reports empty rather than zeroes everywhere`() {
        val report = WorkoutStats.report(emptyList(), now, zone)

        assertTrue(report.isEmpty)
        assertEquals(0, report.activeDays)
        assertEquals(0, report.currentStreakDays)
        assertTrue(report.bests.isEmpty())
    }

    @Test
    fun `workouts older than the window are excluded`() {
        val report =
            WorkoutStats.report(
                listOf(workout(daysAgo = 3), workout(daysAgo = 40, distanceMeters = 99_000.0)),
                now,
                zone,
            )

        assertEquals(1, report.windowTotals.workoutCount)
        assertEquals(0.0, report.windowTotals.distanceMeters, 0.001)
    }

    @Test
    fun `this week and last week are split at the seven day boundary`() {
        val report =
            WorkoutStats.report(
                listOf(
                    workout(daysAgo = 1, distanceMeters = 5000.0),
                    workout(daysAgo = 6, distanceMeters = 3000.0),
                    workout(daysAgo = 8, distanceMeters = 10_000.0),
                    workout(daysAgo = 20, distanceMeters = 1000.0),
                ),
                now,
                zone,
            )

        assertEquals(2, report.thisWeek.workoutCount)
        assertEquals(8000.0, report.thisWeek.distanceMeters, 0.001)
        assertEquals(1, report.previousWeek.workoutCount)
        assertEquals(10_000.0, report.previousWeek.distanceMeters, 0.001)
        assertEquals(4, report.windowTotals.workoutCount)
    }

    /** A missing metric must not be counted as a zero that drags an average down. */
    @Test
    fun `absent metrics contribute nothing to totals`() {
        val report =
            WorkoutStats.report(
                listOf(
                    workout(daysAgo = 1, distanceMeters = 5000.0, calories = 300, steps = 6000),
                    workout(daysAgo = 2),
                ),
                now,
                zone,
            )

        assertEquals(5000.0, report.windowTotals.distanceMeters, 0.001)
        assertEquals(300, report.windowTotals.calories)
        assertEquals(6000, report.windowTotals.steps)
        assertNull(report.windowTotals.avgHeartRate)
    }

    @Test
    fun `average heart rate is duration weighted across the window`() {
        val report =
            WorkoutStats.report(
                listOf(
                    workout(daysAgo = 1, durationSeconds = 3600, avgHeartRate = 160),
                    workout(daysAgo = 2, durationSeconds = 600, avgHeartRate = 100),
                ),
                now,
                zone,
            )

        // (160*3600 + 100*600) / 4200 = 151.4 -> 151
        assertEquals(151, report.windowTotals.avgHeartRate)
    }

    @Test
    fun `the activity breakdown is ordered by time spent`() {
        val report =
            WorkoutStats.report(
                listOf(
                    workout(daysAgo = 1, exercise = ExerciseType.RUNNING, durationSeconds = 600),
                    workout(daysAgo = 2, exercise = ExerciseType.CYCLING, durationSeconds = 7200),
                    workout(daysAgo = 3, exercise = ExerciseType.RUNNING, durationSeconds = 600),
                ),
                now,
                zone,
            )

        assertEquals(ExerciseType.CYCLING, report.byActivity[0].exercise)
        assertEquals(1, report.byActivity[0].totals.workoutCount)
        assertEquals(ExerciseType.RUNNING, report.byActivity[1].exercise)
        assertEquals(2, report.byActivity[1].totals.workoutCount)
    }

    @Test
    fun `a best is only reported for metrics that are actually present`() {
        val report =
            WorkoutStats.report(
                listOf(workout(daysAgo = 1, distanceMeters = 5000.0)),
                now,
                zone,
            )

        val kinds = report.bests.map { it.kind }
        assertTrue(WorkoutStats.BestKind.LONGEST_DISTANCE in kinds)
        assertTrue(WorkoutStats.BestKind.LONGEST_DURATION in kinds)
        assertTrue(WorkoutStats.BestKind.BIGGEST_CLIMB !in kinds)
        assertTrue(WorkoutStats.BestKind.MOST_STEPS !in kinds)
        assertTrue(WorkoutStats.BestKind.HIGHEST_HEART_RATE !in kinds)
    }

    @Test
    fun `two workouts on the same day count as one active day`() {
        val report =
            WorkoutStats.report(
                listOf(workout(daysAgo = 1), workout(daysAgo = 1), workout(daysAgo = 3)),
                now,
                zone,
            )

        assertEquals(2, report.activeDays)
    }

    @Test
    fun `the streak counts consecutive days back from today`() {
        val report =
            WorkoutStats.report(
                listOf(workout(daysAgo = 0), workout(daysAgo = 1), workout(daysAgo = 2), workout(daysAgo = 5)),
                now,
                zone,
            )

        assertEquals(3, report.currentStreakDays)
    }

    /** Resting today shouldn't wipe out a streak that is still alive. */
    @Test
    fun `a rest day today keeps yesterday's streak alive`() {
        val report =
            WorkoutStats.report(
                listOf(workout(daysAgo = 1), workout(daysAgo = 2)),
                now,
                zone,
            )

        assertEquals(2, report.currentStreakDays)
    }

    @Test
    fun `missing a whole day resets the streak`() {
        val report =
            WorkoutStats.report(
                listOf(workout(daysAgo = 2), workout(daysAgo = 3)),
                now,
                zone,
            )

        assertEquals(0, report.currentStreakDays)
    }

    @Test
    fun `the weekly average divides the four week window by four`() {
        val report =
            WorkoutStats.report(
                List(8) { workout(daysAgo = (it * 3).toLong(), distanceMeters = 5000.0) },
                now,
                zone,
            )

        assertEquals(8, report.windowTotals.workoutCount)
        assertEquals(2, report.weeklyAverage.workoutCount)
        assertEquals(10_000.0, report.weeklyAverage.distanceMeters, 0.001)
    }

    @Test
    fun `percent change is null when there is nothing to compare against`() {
        assertNull(WorkoutStats.percentChange(current = 10.0, previous = 0.0))
        assertEquals(50, WorkoutStats.percentChange(current = 15.0, previous = 10.0))
        assertEquals(-25, WorkoutStats.percentChange(current = 7.5, previous = 10.0))
    }
}
