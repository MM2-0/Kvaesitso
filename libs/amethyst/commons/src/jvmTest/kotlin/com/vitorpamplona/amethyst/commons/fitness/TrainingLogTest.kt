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
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingLogTest {
    private val noon = 1_800_000_000L

    private fun workout(
        id: String,
        origin: WorkoutOrigin,
        startTimeEpochSeconds: Long,
        exercise: ExerciseType = ExerciseType.RUNNING,
        distanceMeters: Double? = null,
        avgHeartRate: Int? = null,
    ) = DetectedWorkout(
        id = id,
        exercise = exercise,
        title = null,
        startTimeEpochSeconds = startTimeEpochSeconds,
        durationSeconds = 1800,
        distanceMeters = distanceMeters,
        calories = null,
        avgHeartRate = avgHeartRate,
        maxHeartRate = null,
        steps = null,
        elevationGainMeters = null,
        source = "test",
        origin = origin,
        // Matches the real mappers: anything recovered from a relay is by definition published.
        alreadyPublished = origin == WorkoutOrigin.PUBLISHED,
    )

    @Test
    fun `with no health connect the log is just what the user published`() {
        val published = listOf(workout("a", WorkoutOrigin.PUBLISHED, noon))

        val merged = TrainingLog.merge(emptyList(), published)

        assertEquals(1, merged.size)
        assertEquals("a", merged[0].id)
    }

    @Test
    fun `with no published workouts the log is just health connect`() {
        val hc = listOf(workout("a", WorkoutOrigin.HEALTH_CONNECT, noon))

        assertEquals(1, TrainingLog.merge(hc, emptyList()).size)
    }

    /** The common case: the user shared a workout their watch recorded. It must not count twice. */
    @Test
    fun `a published copy of a health connect workout is dropped`() {
        val hc = listOf(workout("hc", WorkoutOrigin.HEALTH_CONNECT, noon, avgHeartRate = 150))
        val published = listOf(workout("pub", WorkoutOrigin.PUBLISHED, noon + 60))

        val merged = TrainingLog.merge(hc, published)

        assertEquals(1, merged.size)
        assertEquals("hc", merged[0].id)
        // The Health Connect copy wins because it carries the metrics the post dropped.
        assertEquals(150, merged[0].avgHeartRate)
    }

    /**
     * The survivor is Health Connect data, so its origin cannot answer "has this been posted?".
     * Without the flag the dashboard offers to share it again and the user posts a second
     * kind 1301 for the same effort.
     */
    @Test
    fun `the surviving copy remembers that the workout was already published`() {
        val hc = listOf(workout("hc", WorkoutOrigin.HEALTH_CONNECT, noon))
        val published = listOf(workout("pub", WorkoutOrigin.PUBLISHED, noon + 60))

        val merged = TrainingLog.merge(hc, published)

        assertEquals(1, merged.size)
        assertEquals(WorkoutOrigin.HEALTH_CONNECT, merged[0].origin)
        assertTrue(merged[0].alreadyPublished)
    }

    @Test
    fun `a health connect workout that was never shared is not marked published`() {
        val hc = listOf(workout("hc", WorkoutOrigin.HEALTH_CONNECT, noon))
        val published = listOf(workout("pub", WorkoutOrigin.PUBLISHED, noon + TrainingLog.DEDUPE_TOLERANCE_SECONDS + 1))

        val merged = TrainingLog.merge(hc, published)

        assertEquals(2, merged.size)
        assertFalse(merged.first { it.id == "hc" }.alreadyPublished)
        assertTrue(merged.first { it.id == "pub" }.alreadyPublished)
    }

    /** Only the matching session is flagged; an unrelated one in the same log is left alone. */
    @Test
    fun `flagging one workout does not flag the rest of the log`() {
        val hc =
            listOf(
                workout("shared", WorkoutOrigin.HEALTH_CONNECT, noon),
                workout("private", WorkoutOrigin.HEALTH_CONNECT, noon - 86_400),
            )
        val published = listOf(workout("pub", WorkoutOrigin.PUBLISHED, noon))

        val merged = TrainingLog.merge(hc, published)

        assertEquals(2, merged.size)
        assertTrue(merged.first { it.id == "shared" }.alreadyPublished)
        assertFalse(merged.first { it.id == "private" }.alreadyPublished)
    }

    @Test
    fun `a published workout just outside the tolerance is kept as its own`() {
        val hc = listOf(workout("hc", WorkoutOrigin.HEALTH_CONNECT, noon))
        val published = listOf(workout("pub", WorkoutOrigin.PUBLISHED, noon + TrainingLog.DEDUPE_TOLERANCE_SECONDS + 1))

        assertEquals(2, TrainingLog.merge(hc, published).size)
    }

    @Test
    fun `the tolerance boundary itself counts as the same workout`() {
        val hc = listOf(workout("hc", WorkoutOrigin.HEALTH_CONNECT, noon))
        val published = listOf(workout("pub", WorkoutOrigin.PUBLISHED, noon + TrainingLog.DEDUPE_TOLERANCE_SECONDS))

        assertEquals(1, TrainingLog.merge(hc, published).size)
    }

    /** Deduping on time alone would swallow a genuine second session. */
    @Test
    fun `a different activity at the same time is not a duplicate`() {
        val hc = listOf(workout("hc", WorkoutOrigin.HEALTH_CONNECT, noon, exercise = ExerciseType.RUNNING))
        val published = listOf(workout("pub", WorkoutOrigin.PUBLISHED, noon, exercise = ExerciseType.STRENGTH))

        assertEquals(2, TrainingLog.merge(hc, published).size)
    }

    @Test
    fun `dedupe also applies when the published copy is slightly earlier`() {
        val hc = listOf(workout("hc", WorkoutOrigin.HEALTH_CONNECT, noon))
        val published = listOf(workout("pub", WorkoutOrigin.PUBLISHED, noon - 300))

        assertEquals(1, TrainingLog.merge(hc, published).size)
    }

    @Test
    fun `the merged log is ordered newest first`() {
        val hc = listOf(workout("hc", WorkoutOrigin.HEALTH_CONNECT, noon - 10_000))
        val published =
            listOf(
                workout("older", WorkoutOrigin.PUBLISHED, noon - 50_000),
                workout("newest", WorkoutOrigin.PUBLISHED, noon),
            )

        val merged = TrainingLog.merge(hc, published)

        assertEquals(listOf("newest", "hc", "older"), merged.map { it.id })
    }

    @Test
    fun `one health connect workout does not swallow several unrelated published ones`() {
        val hc = listOf(workout("hc", WorkoutOrigin.HEALTH_CONNECT, noon))
        val published =
            listOf(
                workout("dup", WorkoutOrigin.PUBLISHED, noon + 30),
                workout("yesterday", WorkoutOrigin.PUBLISHED, noon - 86_400),
                workout("tomorrow", WorkoutOrigin.PUBLISHED, noon + 86_400),
            )

        val merged = TrainingLog.merge(hc, published)

        assertEquals(3, merged.size)
        assertTrue(merged.none { it.id == "dup" })
    }

    @Test
    fun `an empty log merges to empty`() {
        assertTrue(TrainingLog.merge(emptyList(), emptyList()).isEmpty())
    }
}
