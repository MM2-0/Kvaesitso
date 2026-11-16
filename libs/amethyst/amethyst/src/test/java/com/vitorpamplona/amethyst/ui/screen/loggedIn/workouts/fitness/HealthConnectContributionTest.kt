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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.fitness

import com.vitorpamplona.amethyst.commons.fitness.DetectedWorkout
import com.vitorpamplona.amethyst.service.workouts.health.HealthConnectManager
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides which stages of a progressive Health Connect read are allowed to reach
 * the My Fitness dashboard.
 *
 * The screen re-reads on every resume, and a read's first stage carries no metrics. Applying it
 * unconditionally would be a downgrade for anyone coming back to a dashboard that was already
 * complete.
 */
class HealthConnectContributionTest {
    private fun workout(
        id: String,
        distanceMeters: Double? = null,
    ) = DetectedWorkout(
        id = id,
        exercise = ExerciseType.RUNNING,
        title = null,
        startTimeEpochSeconds = 1_700_000_000,
        durationSeconds = 1800,
        distanceMeters = distanceMeters,
        calories = null,
        avgHeartRate = null,
        maxHeartRate = null,
        steps = null,
        elevationGainMeters = null,
        source = "Samsung Health",
    )

    private fun stageOne(vararg workouts: DetectedWorkout) = HealthConnectManager.WorkoutRead(workouts.toList(), metricsPending = true)

    private fun stageTwo(vararg workouts: DetectedWorkout) = HealthConnectManager.WorkoutRead(workouts.toList(), metricsPending = false)

    @Test
    fun `the first load shows stage one, which is the whole point of reading in stages`() {
        val skeleton = workout("a")

        val after = HealthConnectContribution().after(stageOne(skeleton))

        assertEquals(listOf(skeleton), after.workouts)
        assertTrue(after.metricsPending)
        assertFalse(after.sessionsPending)
    }

    @Test
    fun `stage two replaces stage one and clears the pending flag`() {
        val loaded = workout("a", distanceMeters = 10_000.0)

        val after = HealthConnectContribution().after(stageOne(workout("a"))).after(stageTwo(loaded))

        assertEquals(listOf(loaded), after.workouts)
        assertFalse(after.metricsPending)
    }

    @Test
    fun `a resume does not strip the metrics off an already-complete dashboard`() {
        val loaded = workout("a", distanceMeters = 10_000.0)
        val complete = HealthConnectContribution(workouts = listOf(loaded))

        // What refresh() does on resume, then the new read's stage 1 arriving.
        val rereading = complete.copy(sessionsPending = true)
        val after = rereading.after(stageOne(workout("a")))

        assertEquals(listOf(loaded), after.workouts)
        // Nothing on screen has changed, so nothing should be annotated as loading either.
        assertFalse(after.metricsPending)
        assertFalse(after.sessionsPending)
    }

    @Test
    fun `the resumed read still lands, atomically, when its metrics arrive`() {
        val old = workout("a", distanceMeters = 10_000.0)
        val fresh = workout("b", distanceMeters = 12_000.0)

        val after =
            HealthConnectContribution(workouts = listOf(old))
                .copy(sessionsPending = true)
                .after(stageOne(workout("a"), workout("b")))
                .after(stageTwo(old, fresh))

        assertEquals(listOf(old, fresh), after.workouts)
        assertFalse(after.metricsPending)
    }

    @Test
    fun `an empty stage two is applied even when workouts are on screen, so revoked data clears`() {
        val complete = HealthConnectContribution(workouts = listOf(workout("a", distanceMeters = 10_000.0)))

        // Not a downgrade to decline — a completed read that found nothing is the truth.
        val after = complete.after(stageTwo())

        assertTrue(after.workouts.isEmpty())
        assertFalse(after.metricsPending)
    }

    @Test
    fun `a read that finds nothing clears the sessions-pending gate`() {
        // Health Connect unavailable, the read failing, and a device with no mappable sessions
        // all emit one empty non-pending result. Any of them leaving sessionsPending set would
        // strand an empty dashboard on its spinner forever.
        val after = HealthConnectContribution(sessionsPending = true).after(stageTwo())

        assertTrue(after.workouts.isEmpty())
        assertFalse(after.sessionsPending)
        assertFalse(after.metricsPending)
    }

    @Test
    fun `declining a stage keeps the same workout list instance rather than rebuilding it`() {
        val workouts = listOf(workout("a", distanceMeters = 10_000.0))
        val complete = HealthConnectContribution(workouts = workouts, sessionsPending = true)

        assertSame(workouts, complete.after(stageOne(workout("a"))).workouts)
    }
}
