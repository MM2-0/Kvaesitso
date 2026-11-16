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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The My Fitness dashboard renders before Health Connect's per-session metrics have arrived —
 * each of those costs its own IPC, so waiting for all of them is what made the screen sit on a
 * spinner. These tests pin what that first pass is allowed to look like.
 *
 * The contract has two halves, and the screen depends on both:
 *  - everything not derived from a metric must already be final, so no visible number *changes*
 *    when the metrics land — it only gains cells;
 *  - a metric that is not known yet must read as absent, never as zero, so the screen hides its
 *    cell instead of claiming the user ran 0 km.
 */
class PartialMetricsReportTest {
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

    /** What the first pass has: activity, when, how long. Nothing that needs an aggregate call. */
    private fun DetectedWorkout.withoutMetrics() =
        copy(
            distanceMeters = null,
            calories = null,
            avgHeartRate = null,
            maxHeartRate = null,
            steps = null,
            elevationGainMeters = null,
        )

    private val fullyLoaded =
        listOf(
            workout(daysAgo = 0, durationSeconds = 3600, distanceMeters = 10_000.0, calories = 700, avgHeartRate = 150, maxHeartRate = 175, steps = 9_000, elevationGainMeters = 120.0),
            workout(daysAgo = 1, durationSeconds = 1800, distanceMeters = 5_000.0, calories = 320, avgHeartRate = 140, maxHeartRate = 160, steps = 4_500),
            workout(daysAgo = 2, exercise = ExerciseType.CYCLING, durationSeconds = 5400, distanceMeters = 40_000.0, calories = 900, avgHeartRate = 130, maxHeartRate = 155, elevationGainMeters = 400.0),
            workout(daysAgo = 9, exercise = ExerciseType.STRENGTH, durationSeconds = 2700, calories = 250),
        )

    private val partial = fullyLoaded.map { it.withoutMetrics() }

    @Test
    fun `counts, time and consistency are already final before the metrics arrive`() {
        val early = WorkoutStats.report(partial, now, zone)
        val late = WorkoutStats.report(fullyLoaded, now, zone)

        assertEquals(late.windowTotals.workoutCount, early.windowTotals.workoutCount)
        assertEquals(late.windowTotals.durationSeconds, early.windowTotals.durationSeconds)
        assertEquals(late.thisWeek.workoutCount, early.thisWeek.workoutCount)
        assertEquals(late.thisWeek.durationSeconds, early.thisWeek.durationSeconds)
        assertEquals(late.previousWeek.workoutCount, early.previousWeek.workoutCount)
        assertEquals(late.weeklyAverage.durationSeconds, early.weeklyAverage.durationSeconds)
        assertEquals(late.activeDays, early.activeDays)
        assertEquals(late.currentStreakDays, early.currentStreakDays)
    }

    @Test
    fun `the activity split is already final, in the same order`() {
        val early = WorkoutStats.report(partial, now, zone)
        val late = WorkoutStats.report(fullyLoaded, now, zone)

        assertEquals(late.byActivity.map { it.exercise }, early.byActivity.map { it.exercise })
        assertEquals(
            late.byActivity.map { it.totals.workoutCount },
            early.byActivity.map { it.totals.workoutCount },
        )
        assertEquals(
            late.byActivity.map { it.totals.durationSeconds },
            early.byActivity.map { it.totals.durationSeconds },
        )
    }

    @Test
    fun `an unknown metric reads as absent, so the screen hides its cell instead of showing zero`() {
        val early = WorkoutStats.report(partial, now, zone)

        // The screen gates these cells on `> 0` / a non-null, so absent is what keeps them hidden.
        assertEquals(0.0, early.windowTotals.distanceMeters, 0.0)
        assertEquals(0, early.windowTotals.calories)
        assertEquals(0, early.windowTotals.steps)
        assertEquals(0.0, early.windowTotals.elevationGainMeters, 0.0)
        assertNull(early.windowTotals.avgHeartRate)
        assertNull(early.windowTotals.maxHeartRate)
    }

    @Test
    fun `only the duration best is offered before the metrics arrive`() {
        val early = WorkoutStats.report(partial, now, zone)
        val late = WorkoutStats.report(fullyLoaded, now, zone)

        // A best whose metric is unknown is withheld rather than awarded to whichever workout
        // happens to have a null — the cycling ride below is the real longest distance.
        assertEquals(listOf(WorkoutStats.BestKind.LONGEST_DURATION), early.bests.map { it.kind })

        assertTrue(late.bests.map { it.kind }.containsAll(early.bests.map { it.kind }))
        assertEquals(
            late.bests
                .first { it.kind == WorkoutStats.BestKind.LONGEST_DURATION }
                .workout.id,
            early.bests
                .first { it.kind == WorkoutStats.BestKind.LONGEST_DURATION }
                .workout.id,
        )
    }

    @Test
    fun `a partial pass with workouts in it is not mistaken for an empty log`() {
        val early = WorkoutStats.report(partial, now, zone)

        // isEmpty drives the "nothing logged yet" copy and the connect prompt. A user with four
        // workouts whose metrics are still loading must not see either.
        assertFalse(early.isEmpty)
        assertEquals(fullyLoaded.size, early.workouts.size)
    }

    @Test
    fun `merging both sources still dedupes when the health connect copy has no metrics yet`() {
        val published =
            fullyLoaded.take(1).map {
                it.copy(id = "published", origin = WorkoutOrigin.PUBLISHED, alreadyPublished = true)
            }

        // Dedupe keys on activity and start time, neither of which is a metric, so the first pass
        // must already collapse the pair rather than double-count it and then halve the count.
        val merged = TrainingLog.merge(partial, published)

        assertEquals(partial.size, merged.size)
        assertEquals(1, merged.count { it.alreadyPublished })
    }

    @Test
    fun `a metric-less health connect copy yields to the published one that still has its numbers`() {
        val published =
            fullyLoaded.take(1).map {
                it.copy(id = "published", origin = WorkoutOrigin.PUBLISHED, alreadyPublished = true)
            }

        // Health Connect normally wins the tie because it carries more. During stage 1 it carries
        // less, and evicting the published copy then would take 10 km off a dashboard that was
        // already showing it — down to zero, and back up a second later.
        val merged = TrainingLog.merge(partial, published)
        val survivor = merged.first { it.startTimeEpochSeconds == published.single().startTimeEpochSeconds }

        assertEquals(10_000.0, survivor.distanceMeters!!, 0.001)
        assertEquals(700, survivor.calories)
        assertTrue(survivor.alreadyPublished)
    }

    @Test
    fun `the whole window keeps its published totals through the partial pass`() {
        val published =
            fullyLoaded.map {
                it.copy(id = "published-${'$'}{it.id}", origin = WorkoutOrigin.PUBLISHED, alreadyPublished = true)
            }

        // The user has published every one of these. Stage 1 must not make their distance and
        // calorie totals dip to zero on the way to showing the same numbers again.
        val beforeAnyHealthConnect = WorkoutStats.report(TrainingLog.merge(emptyList(), published), now, zone)
        val duringStageOne = WorkoutStats.report(TrainingLog.merge(partial, published), now, zone)

        assertEquals(beforeAnyHealthConnect.windowTotals.distanceMeters, duringStageOne.windowTotals.distanceMeters, 0.001)
        assertEquals(beforeAnyHealthConnect.windowTotals.calories, duringStageOne.windowTotals.calories)
        assertEquals(beforeAnyHealthConnect.windowTotals.steps, duringStageOne.windowTotals.steps)
        assertEquals(beforeAnyHealthConnect.windowTotals.maxHeartRate, duringStageOne.windowTotals.maxHeartRate)
        assertEquals(beforeAnyHealthConnect.bests.map { it.kind }.toSet(), duringStageOne.bests.map { it.kind }.toSet())
    }

    @Test
    fun `a loaded health connect copy still wins over the published one, as before`() {
        // The yielding rule is narrow: it must not invert the normal preference, or a shared
        // workout would lose the device detail the published event dropped.
        val published =
            fullyLoaded.take(1).map {
                it.copy(
                    id = "published",
                    origin = WorkoutOrigin.PUBLISHED,
                    alreadyPublished = true,
                    steps = null,
                    elevationGainMeters = null,
                )
            }

        val merged = TrainingLog.merge(fullyLoaded, published)
        val survivor = merged.first { it.startTimeEpochSeconds == published.single().startTimeEpochSeconds }

        assertEquals(WorkoutOrigin.HEALTH_CONNECT, survivor.origin)
        assertEquals(9_000, survivor.steps)
        assertTrue(survivor.alreadyPublished)
    }

    @Test
    fun `one health connect session still absorbs every published copy it matches`() {
        // Pre-existing dedupe behaviour, kept: the yielding rule changes which copy survives a
        // tie, never how many survive. Two published events inside the 15-minute tolerance and
        // one recorded session is still one workout, not two.
        val recorded = listOf(workout(daysAgo = 1, durationSeconds = 1800, distanceMeters = 5_000.0))
        val start = recorded.single().startTimeEpochSeconds
        val published =
            listOf(
                recorded.single().copy(id = "p1", startTimeEpochSeconds = start, origin = WorkoutOrigin.PUBLISHED, alreadyPublished = true),
                recorded.single().copy(id = "p2", startTimeEpochSeconds = start + 300, origin = WorkoutOrigin.PUBLISHED, alreadyPublished = true),
            )

        assertEquals(1, TrainingLog.merge(recorded, published).size)
        // And the same when the recorded copy is the metric-less one that yields.
        assertEquals(1, TrainingLog.merge(recorded.map { it.withoutMetrics() }, published).size)
    }

    @Test
    fun `two copies that both lack metrics keep the health connect one`() {
        // A gym session genuinely has no distance or steps on either side. Nothing to prefer, so
        // the normal rule stands and the pair still collapses to one entry.
        val strengthOnly = listOf(workout(daysAgo = 3, exercise = ExerciseType.STRENGTH, durationSeconds = 2700))
        val published =
            strengthOnly.map {
                it.copy(id = "published", origin = WorkoutOrigin.PUBLISHED, alreadyPublished = true)
            }

        val merged = TrainingLog.merge(strengthOnly, published)

        assertEquals(1, merged.size)
        assertEquals(WorkoutOrigin.HEALTH_CONNECT, merged.single().origin)
        assertTrue(merged.single().alreadyPublished)
    }
}
