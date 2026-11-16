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

import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.AvgHeartRateTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.CaloriesTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DistanceTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DurationTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ElevationGainTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.StepsTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.TitleTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.WorkoutEndTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.WorkoutStartTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.WorkoutStartTimeTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishedWorkoutMappingTest {
    private val createdAt = 1_800_000_000L

    private fun event(vararg tags: Array<String>) =
        WorkoutRecordEvent(
            id = "eventid",
            pubKey = "pubkey",
            createdAt = createdAt,
            tags = arrayOf(*tags),
            content = "",
            sig = "sig",
        )

    @Test
    fun `a full workout round-trips into the training log`() {
        val workout =
            event(
                ExerciseTag.assemble(ExerciseType.RUNNING),
                TitleTag.assemble("Morning Run"),
                DurationTag.assemble(1800),
                DistanceTag.assemble(5.0, DistanceTag.KILOMETERS),
                CaloriesTag.assemble(380),
                AvgHeartRateTag.assemble(150),
                StepsTag.assemble(6000),
                WorkoutStartTimeTag.assemble(createdAt - 3600),
            ).toDetectedWorkout()

        assertNotNull(workout)
        assertEquals(ExerciseType.RUNNING, workout!!.exercise)
        assertEquals("Morning Run", workout.title)
        assertEquals(1800, workout.durationSeconds)
        assertEquals(5000.0, workout.distanceMeters!!, 0.001)
        assertEquals(380, workout.calories)
        assertEquals(150, workout.avgHeartRate)
        assertEquals(6000, workout.steps)
        assertEquals(createdAt - 3600, workout.startTimeEpochSeconds)
        assertEquals(WorkoutOrigin.PUBLISHED, workout.origin)
        // Recovered from a relay, so the dashboard must not offer to publish it again.
        assertTrue(workout.alreadyPublished)
    }

    /** Miles on the wire must land as metres in the log, or every total is wrong by 1.6x. */
    @Test
    fun `an imperial distance is converted to metres`() {
        val workout =
            event(
                ExerciseTag.assemble(ExerciseType.RUNNING),
                DurationTag.assemble(1800),
                DistanceTag.assemble(3.0, DistanceTag.MILES),
            ).toDetectedWorkout()

        assertEquals(4828.032, workout!!.distanceMeters!!, 0.001)
    }

    @Test
    fun `an elevation in feet is converted to metres`() {
        val workout =
            event(
                ExerciseTag.assemble(ExerciseType.HIKING),
                DurationTag.assemble(3600),
                ElevationGainTag.assemble(1000.0, "ft"),
            ).toDetectedWorkout()

        assertEquals(304.8, workout!!.elevationGainMeters!!, 0.01)
    }

    /**
     * A workout typed in by hand carries no start time. Falling back to created_at keeps it in
     * the right day bucket for streaks and the week split.
     */
    @Test
    fun `a manual entry without a start time falls back to the publish time`() {
        val workout =
            event(
                ExerciseTag.assemble(ExerciseType.YOGA),
                DurationTag.assemble(600),
            ).toDetectedWorkout()

        assertEquals(createdAt, workout!!.startTimeEpochSeconds)
    }

    @Test
    fun `an event with no recognisable activity is skipped`() {
        assertNull(event(DurationTag.assemble(1800)).toDetectedWorkout())
    }

    /**
     * POWR-dialect records carry `start`/`end` and no `duration` tag. Reading the raw duration
     * accessor drops them from the log entirely.
     */
    @Test
    fun `a POWR record with start and end but no duration tag is kept`() {
        val workout =
            event(
                ExerciseTag.assemble(ExerciseType.CYCLING),
                WorkoutStartTag.assemble(createdAt - 3600),
                WorkoutEndTag.assemble(createdAt - 387),
            ).toDetectedWorkout()

        assertNotNull(workout)
        assertEquals(3213, workout!!.durationSeconds)
        assertEquals(createdAt - 3600, workout.startTimeEpochSeconds)
    }

    @Test
    fun `an event with no duration is skipped`() {
        assertNull(event(ExerciseTag.assemble(ExerciseType.RUNNING)).toDetectedWorkout())
    }

    /** Zeroes on the wire are absences, not measurements — they must not drag totals or bests. */
    @Test
    fun `zero metrics are treated as absent`() {
        val workout =
            event(
                ExerciseTag.assemble(ExerciseType.STRENGTH),
                DurationTag.assemble(2400),
                CaloriesTag.assemble(0),
                StepsTag.assemble(0),
                AvgHeartRateTag.assemble(0),
            ).toDetectedWorkout()

        assertNull(workout!!.calories)
        assertNull(workout.steps)
        assertNull(workout.avgHeartRate)
    }
}
