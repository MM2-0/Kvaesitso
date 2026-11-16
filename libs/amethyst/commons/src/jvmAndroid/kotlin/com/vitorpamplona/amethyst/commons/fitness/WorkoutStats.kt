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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Turns a flat list of [DetectedWorkout] into the figures the My Fitness screen shows the
 * user about their own training: period totals, week-over-week movement, a per-activity
 * breakdown, bests, and how consistently they have been showing up.
 *
 * Pure and platform-free on purpose — every number the screen displays is derived here, so
 * the arithmetic can be tested without Health Connect, a device, or Compose.
 *
 * Everything is scoped to [WINDOW_DAYS]. Without `READ_HEALTH_DATA_HISTORY`, which Amethyst
 * deliberately does not request, Health Connect lets an app read back to 30 days before it was
 * first granted access — so on a fresh grant, 30 days is all there is. Amethyst reads four whole
 * weeks every time, the largest window that is always inside that limit, and it makes the
 * "this week vs last week" comparison land inside it too.
 */
object WorkoutStats {
    /** Four whole weeks — see the class note on Health Connect's history limit. */
    const val WINDOW_DAYS = 28L

    /** Days in the recent-comparison period. */
    const val WEEK_DAYS = 7L

    /**
     * Totals over some set of workouts. Sums are plain sums; [avgHeartRate] is
     * duration-weighted so a two-hour ride outweighs a ten-minute walk.
     */
    @Immutable
    data class Totals(
        val workoutCount: Int = 0,
        val durationSeconds: Long = 0,
        val distanceMeters: Double = 0.0,
        val calories: Int = 0,
        val steps: Int = 0,
        val elevationGainMeters: Double = 0.0,
        val avgHeartRate: Int? = null,
        val maxHeartRate: Int? = null,
    ) {
        val isEmpty: Boolean get() = workoutCount == 0
    }

    /** One activity's slice of the window, e.g. "Running: 5 workouts, 42 km". */
    @Immutable
    data class ActivityTotals(
        val exercise: ExerciseType,
        val totals: Totals,
    )

    /** A single best effort in the window, with the workout that set it. */
    @Immutable
    data class Best(
        val kind: BestKind,
        val workout: DetectedWorkout,
    )

    enum class BestKind { LONGEST_DISTANCE, LONGEST_DURATION, BIGGEST_CLIMB, MOST_STEPS, HIGHEST_HEART_RATE }

    /** Everything the My Fitness screen renders. */
    @Immutable
    data class Report(
        val windowTotals: Totals,
        val thisWeek: Totals,
        val previousWeek: Totals,
        val weeklyAverage: Totals,
        val byActivity: List<ActivityTotals>,
        val bests: List<Best>,
        val activeDays: Int,
        val currentStreakDays: Int,
        val workouts: List<DetectedWorkout>,
    ) {
        val isEmpty: Boolean get() = windowTotals.isEmpty
    }

    /**
     * Builds the report from [workouts] — which the caller should already have limited to
     * [WINDOW_DAYS] — as of [now]. [zone] decides day boundaries for the streak and
     * active-day counts, so a workout at 23:30 counts for that day, not the next.
     */
    fun report(
        workouts: List<DetectedWorkout>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Report {
        val windowStart = now.epochSecond - WINDOW_DAYS * SECONDS_PER_DAY
        val weekStart = now.epochSecond - WEEK_DAYS * SECONDS_PER_DAY
        val previousWeekStart = weekStart - WEEK_DAYS * SECONDS_PER_DAY

        val inWindow =
            workouts
                .filter { it.startTimeEpochSeconds >= windowStart }
                .sortedByDescending { it.startTimeEpochSeconds }

        val thisWeek = inWindow.filter { it.startTimeEpochSeconds >= weekStart }
        val previousWeek = inWindow.filter { it.startTimeEpochSeconds in previousWeekStart until weekStart }

        val windowTotals = total(inWindow)

        return Report(
            windowTotals = windowTotals,
            thisWeek = total(thisWeek),
            previousWeek = total(previousWeek),
            weeklyAverage = perWeek(windowTotals),
            byActivity = byActivity(inWindow),
            bests = bests(inWindow),
            activeDays = activeDays(inWindow, zone).size,
            currentStreakDays = streak(inWindow, now, zone),
            workouts = inWindow,
        )
    }

    private const val SECONDS_PER_DAY = 86_400L

    /** Sums a set of workouts. Absent metrics contribute nothing rather than zero-ing the total. */
    fun total(workouts: List<DetectedWorkout>): Totals {
        if (workouts.isEmpty()) return Totals()

        val withHeartRate = workouts.filter { it.avgHeartRate != null }
        val heartRateWeight = withHeartRate.sumOf { it.durationSeconds }

        return Totals(
            workoutCount = workouts.size,
            durationSeconds = workouts.sumOf { it.durationSeconds },
            distanceMeters = workouts.sumOf { it.distanceMeters ?: 0.0 },
            calories = workouts.sumOf { it.calories ?: 0 },
            steps = workouts.sumOf { it.steps ?: 0 },
            elevationGainMeters = workouts.sumOf { it.elevationGainMeters ?: 0.0 },
            avgHeartRate =
                when {
                    withHeartRate.isEmpty() -> null
                    heartRateWeight > 0 ->
                        withHeartRate
                            .sumOf { it.avgHeartRate!!.toDouble() * it.durationSeconds }
                            .div(heartRateWeight)
                            .roundToInt()
                    else -> withHeartRate.map { it.avgHeartRate!! }.average().roundToInt()
                },
            maxHeartRate = workouts.mapNotNull { it.maxHeartRate }.maxOrNull(),
        )
    }

    /** The window's totals expressed per week, for "your weekly average" lines. */
    private fun perWeek(totals: Totals): Totals {
        val weeks = WINDOW_DAYS.toDouble() / WEEK_DAYS
        return Totals(
            workoutCount = (totals.workoutCount / weeks).roundToInt(),
            durationSeconds = (totals.durationSeconds / weeks).toLong(),
            distanceMeters = totals.distanceMeters / weeks,
            calories = (totals.calories / weeks).roundToInt(),
            steps = (totals.steps / weeks).roundToInt(),
            elevationGainMeters = totals.elevationGainMeters / weeks,
            // Averages don't divide: the mean heart rate of a week is the mean of the window.
            avgHeartRate = totals.avgHeartRate,
            maxHeartRate = totals.maxHeartRate,
        )
    }

    /** Per-activity totals, busiest first (by time spent, then by count). */
    private fun byActivity(workouts: List<DetectedWorkout>): List<ActivityTotals> =
        workouts
            .groupBy { it.exercise }
            .map { (exercise, list) -> ActivityTotals(exercise, total(list)) }
            .sortedWith(
                compareByDescending<ActivityTotals> { it.totals.durationSeconds }
                    .thenByDescending { it.totals.workoutCount },
            )

    /**
     * The standout efforts of the window. A best is only reported when the metric is present
     * and positive, so a user whose watch records no elevation never sees an empty "biggest
     * climb" card.
     */
    internal fun bests(workouts: List<DetectedWorkout>): List<Best> =
        listOfNotNull(
            workouts
                .filter { (it.distanceMeters ?: 0.0) > 0 }
                .maxByOrNull { it.distanceMeters!! }
                ?.let { Best(BestKind.LONGEST_DISTANCE, it) },
            workouts
                .filter { it.durationSeconds > 0 }
                .maxByOrNull { it.durationSeconds }
                ?.let { Best(BestKind.LONGEST_DURATION, it) },
            workouts
                .filter { (it.elevationGainMeters ?: 0.0) > 0 }
                .maxByOrNull { it.elevationGainMeters!! }
                ?.let { Best(BestKind.BIGGEST_CLIMB, it) },
            workouts
                .filter { (it.steps ?: 0) > 0 }
                .maxByOrNull { it.steps!! }
                ?.let { Best(BestKind.MOST_STEPS, it) },
            workouts
                .filter { (it.maxHeartRate ?: 0) > 0 }
                .maxByOrNull { it.maxHeartRate!! }
                ?.let { Best(BestKind.HIGHEST_HEART_RATE, it) },
        )

    /** The distinct local dates on which the user trained. */
    private fun activeDays(
        workouts: List<DetectedWorkout>,
        zone: ZoneId,
    ): Set<LocalDate> = workouts.mapTo(HashSet()) { it.localDate(zone) }

    /**
     * Consecutive days up to today on which the user trained. A rest day today does not break
     * a streak that is otherwise alive — the count then runs back from yesterday — so the
     * number only resets once a whole day has genuinely been missed.
     */
    private fun streak(
        workouts: List<DetectedWorkout>,
        now: Instant,
        zone: ZoneId,
    ): Int {
        val days = activeDays(workouts, zone)
        if (days.isEmpty()) return 0

        val today = now.atZone(zone).toLocalDate()
        var cursor = if (today in days) today else today.minusDays(1)

        var streak = 0
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun DetectedWorkout.localDate(zone: ZoneId): LocalDate = Instant.ofEpochSecond(startTimeEpochSeconds).atZone(zone).toLocalDate()

    /**
     * Percentage change from [previous] to [current], or null when there is no previous value
     * to compare against (an arrow up from zero says nothing).
     */
    fun percentChange(
        current: Double,
        previous: Double,
    ): Int? {
        if (previous <= 0.0) return null
        return (((current - previous) / previous) * 100).roundToInt()
    }
}
