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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class FitnessInsightsTest {
    private val zone: ZoneId = ZoneId.of("UTC")

    /** Wednesday 2026-10-07, noon: the Monday week started two days ago. */
    private val now: Instant = ZonedDateTime.of(2026, 10, 7, 12, 0, 0, 0, zone).toInstant()
    private val today: LocalDate = LocalDate.of(2026, 10, 7)

    private var nextId = 0

    private fun workout(
        date: LocalDate,
        exercise: ExerciseType = ExerciseType.RUNNING,
        minutes: Long = 30,
        hour: Int = 7,
        distanceMeters: Double? = null,
    ) = DetectedWorkout(
        id = "w${nextId++}",
        exercise = exercise,
        title = null,
        startTimeEpochSeconds = date.atTime(hour, 0).atZone(zone).toEpochSecond(),
        durationSeconds = minutes * 60,
        distanceMeters = distanceMeters,
        calories = null,
        avgHeartRate = null,
        maxHeartRate = null,
        steps = null,
        elevationGainMeters = null,
        source = "test",
    )

    private fun build(
        workouts: List<DetectedWorkout>,
        goals: FitnessGoals = FitnessGoals.DEFAULT,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ) = FitnessInsights.build(workouts, goals, now, zone, firstDayOfWeek)

    @Test
    fun `an empty log is empty but still has a full week series to draw`() {
        val insights = build(emptyList())

        assertTrue(insights.isEmpty)
        assertEquals(FitnessInsights.TREND_WEEKS, insights.weeks.size)
        assertEquals(FitnessInsights.HEATMAP_WEEKS * 7, insights.days.size)
        assertNull(insights.peakWeekday)
        assertEquals(0, insights.goalStreakWeeks)
    }

    @Test
    fun `this week is the calendar week, not the last seven days`() {
        // Sunday belongs to last week under a Monday start, and to this week under a Sunday start.
        val sunday = today.minusDays(3)
        val log = listOf(workout(today), workout(sunday))

        assertEquals(1, build(log).currentWeek.workoutCount)
        assertEquals(2, build(log, firstDayOfWeek = DayOfWeek.SUNDAY).currentWeek.workoutCount)
        assertEquals(LocalDate.of(2026, 10, 5), build(log).currentWeek.start)
        assertEquals(5, build(log).daysLeftInWeek)
    }

    @Test
    fun `fasting and meditation never count as active minutes`() {
        val log =
            listOf(
                workout(today, ExerciseType.FASTING, minutes = 16 * 60),
                workout(today, ExerciseType.MEDITATION, minutes = 20),
                workout(today, ExerciseType.RUNNING, minutes = 30),
            )
        val insights = build(log)

        assertEquals(30L, insights.currentWeek.activeMinutes)
        assertEquals(1, insights.currentWeek.workoutCount)
        // The 16-hour fast must not become the "longest workout" record either.
        val longest = insights.records.first { it.best.kind == WorkoutStats.BestKind.LONGEST_DURATION }
        assertEquals(ExerciseType.RUNNING, longest.best.workout.exercise)
        assertTrue(longest.isNew)
        // But it is still in the log.
        assertEquals(3, insights.recent.size)
    }

    @Test
    fun `pace is judged on completed days, so an empty Monday is not behind`() {
        val monday = ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, zone).toInstant()
        val insights = FitnessInsights.build(emptyList(), FitnessGoals.DEFAULT, monday, zone)
        assertTrue(insights.progress.all { it.pace == FitnessInsights.Pace.ON_TRACK })

        // By Wednesday two of seven days have gone: 2/7 of 150 is ~43 minutes.
        val behind = build(listOf(workout(today.minusDays(1), minutes = 30)))
        assertEquals(FitnessInsights.Pace.BEHIND, behind.progress.first { it.kind == FitnessInsights.GoalKind.ACTIVE_MINUTES }.pace)

        val onTrack = build(listOf(workout(today.minusDays(1), minutes = 50)))
        assertEquals(FitnessInsights.Pace.ON_TRACK, onTrack.progress.first { it.kind == FitnessInsights.GoalKind.ACTIVE_MINUTES }.pace)

        val met = build(listOf(workout(today, minutes = 150)))
        assertEquals(FitnessInsights.Pace.MET, met.progress.first { it.kind == FitnessInsights.GoalKind.ACTIVE_MINUTES }.pace)
    }

    @Test
    fun `an unfinished week does not break the goal streak, a missed one does`() {
        val weekStart = LocalDate.of(2026, 10, 5)
        // Three met weeks before this one, then a miss before those.
        val log =
            (1..3).map { workout(weekStart.minusWeeks(it.toLong()), minutes = 160) } +
                workout(weekStart.minusWeeks(4), minutes = 20) +
                (5..9).map { workout(weekStart.minusWeeks(it.toLong()), minutes = 200) }

        val insights = build(log)
        assertEquals(3, insights.goalStreakWeeks)
        assertEquals(5, insights.bestGoalStreakWeeks)
        // Ten weeks of history (nine back plus this one): the rest of the chart is not "missed".
        assertEquals(10, insights.weeksTracked)
        assertEquals(8, insights.weeksGoalMet)

        // Meeting it this week extends the run.
        assertEquals(4, build(log + workout(today, minutes = 150)).goalStreakWeeks)
    }

    @Test
    fun `distance goals count only their own activity`() {
        val goals = FitnessGoals(distance = FitnessGoals.DistanceGoal(ExerciseType.RUNNING, 20_000.0))
        val log =
            listOf(
                workout(today, ExerciseType.RUNNING, distanceMeters = 8_000.0),
                workout(today, ExerciseType.CYCLING, distanceMeters = 40_000.0),
            )
        val distance = build(log, goals).progress.first { it.kind == FitnessInsights.GoalKind.DISTANCE }

        assertEquals(8_000.0, distance.current, 0.001)
        assertEquals(0.4f, distance.fraction, 0.001f)
    }

    @Test
    fun `patterns find the usual day and time of day`() {
        val weekStart = LocalDate.of(2026, 10, 5)
        val log =
            (1..6).map { workout(weekStart.minusWeeks(it.toLong()).plusDays(5), minutes = 60, hour = 8) } + // Saturdays, morning
                workout(weekStart.minusWeeks(2).plusDays(1), minutes = 30, hour = 19) // a Tuesday evening

        val insights = build(log)
        assertEquals(DayOfWeek.SATURDAY, insights.peakWeekday)
        assertEquals(FitnessInsights.Daypart.MORNING, insights.peakDaypart)
        assertEquals(6, insights.daypartCounts[FitnessInsights.Daypart.MORNING.ordinal])
        assertEquals(1, insights.daypartCounts[FitnessInsights.Daypart.EVENING.ordinal])
    }

    @Test
    fun `colours follow total time over the history, not one week's rank`() {
        val weekStart = LocalDate.of(2026, 10, 5)
        val log =
            (1..8).map { workout(weekStart.minusWeeks(it.toLong()), ExerciseType.CYCLING, minutes = 90) } +
                (1..8).map { workout(weekStart.minusWeeks(it.toLong()), ExerciseType.RUNNING, minutes = 40) } +
                workout(today, ExerciseType.STRENGTH, minutes = 300) +
                workout(today, ExerciseType.YOGA, minutes = 10)

        assertEquals(listOf(ExerciseType.CYCLING, ExerciseType.RUNNING, ExerciseType.STRENGTH), build(log).colorOrder)
    }

    @Test
    fun `heatmap days after today are future, not missed`() {
        val insights = build(listOf(workout(today)))
        val last = insights.days.last()

        assertEquals(LocalDate.of(2026, 10, 11), last.date)
        assertTrue(last.isFuture)
        assertFalse(insights.days.first { it.date == today }.isFuture)
        assertEquals(1, insights.days.first { it.date == today }.workoutCount)
    }

    @Test
    fun `goals survive an encode and decode, and garbage falls back to the default`() {
        val goals = FitnessGoals(200, 4, 5, FitnessGoals.DistanceGoal(ExerciseType.CYCLING, 100_000.0))

        assertEquals(goals, FitnessGoals.decode(goals.encode()))
        assertEquals(FitnessGoals.DEFAULT, FitnessGoals.decode(FitnessGoals.DEFAULT.encode()))
        assertEquals(FitnessGoals.DEFAULT, FitnessGoals.decode(null))
        assertEquals(FitnessGoals.DEFAULT, FitnessGoals.decode("v9;1;2;3"))
        assertEquals(FitnessGoals.DEFAULT, FitnessGoals.decode("v1;150;3;9"))
    }
}
