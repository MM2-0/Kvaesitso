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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts

import com.vitorpamplona.amethyst.commons.fitness.DetectedWorkout
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.NewWorkoutViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.suggestion.toNewWorkoutRoute
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.SourceTag
import org.junit.Assert.assertEquals
import org.junit.Test

class NewWorkoutPrefillTest {
    private fun route(
        exercise: ExerciseType,
        title: String,
        durationSeconds: Long,
        distanceMeters: Double = 0.0,
        calories: Int = 0,
        source: String = "Samsung Health",
    ) = Route.NewWorkout(
        exercise = exercise.code,
        title = title,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        calories = calories,
        avgHeartRate = 0,
        maxHeartRate = 0,
        steps = 0,
        elevationGainMeters = 0.0,
        startTime = 1_700_000_000L,
        source = source,
    )

    /**
     * Picking a second suggestion must replace the form, not merge into it. A gym
     * session has no distance and no calories; if the run's numbers survive, the
     * user publishes a workout that never happened.
     */
    @Test
    fun `picking a second workout clears metrics the new one does not have`() {
        val vm = NewWorkoutViewModel()

        vm.applyPrefill(route(ExerciseType.RUNNING, "Morning Run", 1800, distanceMeters = 5000.0, calories = 380))
        assertEquals("380", vm.calories)

        vm.applyPrefill(route(ExerciseType.STRENGTH, "Gym", 2400))

        assertEquals(ExerciseType.STRENGTH, vm.exercise)
        assertEquals("Gym", vm.title)
        assertEquals("", vm.distance)
        assertEquals("", vm.calories)
    }

    /**
     * A Health Connect import publishes the NIP-101e vocabulary token, not the writing
     * app's label: the feed badge uppercases whatever is in the tag, so a raw label
     * renders as "SAMSUNG HEALTH" (or "COM.HUAWEI.HEALTH" when the app is not installed)
     * next to other clients' "GPS" and "MANUAL".
     */
    @Test
    fun `a detected workout carries the health_connect source token`() {
        val detected =
            DetectedWorkout(
                id = "abc",
                exercise = ExerciseType.RUNNING,
                title = "Morning Run",
                startTimeEpochSeconds = 1_700_000_000L,
                durationSeconds = 1800,
                distanceMeters = 5000.0,
                calories = 380,
                avgHeartRate = 150,
                maxHeartRate = 172,
                steps = 6000,
                elevationGainMeters = 40.0,
                source = "Samsung Health",
            )

        assertEquals(SourceTag.HEALTH_CONNECT, detected.toNewWorkoutRoute("Morning Run").source)
    }

    /** Duration must switch too, not keep the previous workout's clock. */
    @Test
    fun `picking a second workout replaces the duration`() {
        val vm = NewWorkoutViewModel()

        vm.applyPrefill(route(ExerciseType.RUNNING, "Morning Run", 3661))
        assertEquals("1", vm.hours)

        vm.applyPrefill(route(ExerciseType.YOGA, "Yoga", 600))

        assertEquals("0", vm.hours)
        assertEquals("10", vm.minutes)
        assertEquals("0", vm.seconds)
    }
}
