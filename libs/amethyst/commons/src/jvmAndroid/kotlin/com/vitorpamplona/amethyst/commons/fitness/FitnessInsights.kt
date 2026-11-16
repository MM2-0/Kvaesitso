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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The goal-oriented, historical view of the training log that the redesigned My Fitness screen
 * draws: progress against [FitnessGoals] this calendar week, the weekly series behind the trend
 * chart, the day-by-day grid behind the consistency heatmap, when in the week and the day the user
 * tends to train, how their activity mix is shifting, and their records.
 *
 * Where [WorkoutStats] looks at a rolling 28 days, this looks at calendar weeks over up to
 * [HISTORY_WEEKS]. Calendar weeks because a goal is "this week", and a rolling seven days moves
 * the goalposts every morning. Longer because patterns need history: a weekday profile from four
 * weeks is four samples per bar. The older weeks come from the user's published kind 1301s only —
 * Amethyst reads only [WorkoutStats.WINDOW_DAYS] from Health Connect — which [Insights.deviceHorizon]
 * marks so the chart can say so.
 *
 * Pure and platform-free like [WorkoutStats], so every number on the screen is testable.
 */
object FitnessInsights {
    /** Half a year: enough for a weekday profile and a heatmap, small enough to compute per emission. */
    const val HISTORY_WEEKS = 26

    /** Weeks in the trend chart. A quarter shows a build or a slump without squeezing the bars. */
    const val TREND_WEEKS = 12

    /** Weeks in the consistency heatmap. */
    const val HEATMAP_WEEKS = 18

    /** The "recent" half of the activity-mix and average comparisons. */
    const val COMPARE_WEEKS = 4

    /**
     * Logged kinds whose duration is not exercise. A 16-hour fast is not 960 active minutes, and
     * it must not fill a ring or set the "longest workout" record. They still show in the log.
     */
    val NOT_MOVEMENT = setOf(ExerciseType.DIET, ExerciseType.FASTING, ExerciseType.MEDITATION)

    fun DetectedWorkout.isMovement(): Boolean = exercise !in NOT_MOVEMENT

    @Immutable
    data class Week(
        val start: LocalDate,
        val activeSeconds: Long,
        val workoutCount: Int,
        val activeDays: Int,
        val secondsByActivity: Map<ExerciseType, Long>,
        /** Distance in the [FitnessGoals.distance] activity, 0 when there is no distance goal. */
        val goalDistanceMeters: Double,
        val isCurrent: Boolean,
    ) {
        val activeMinutes: Long get() = activeSeconds / 60
    }

    enum class GoalKind { ACTIVE_MINUTES, WORKOUTS, ACTIVE_DAYS, DISTANCE }

    /** Where a goal stands, judged against how much of the week has gone rather than all of it. */
    enum class Pace { MET, ON_TRACK, BEHIND }

    @Immutable
    data class GoalProgress(
        val kind: GoalKind,
        val current: Double,
        val target: Double,
        val pace: Pace,
    ) {
        val fraction: Float get() = if (target <= 0) 0f else (current / target).toFloat()
        val remaining: Double get() = (target - current).coerceAtLeast(0.0)
    }

    @Immutable
    data class Day(
        val date: LocalDate,
        val activeSeconds: Long,
        val workoutCount: Int,
        /** Later than today: drawn as an empty slot, never as a missed day. */
        val isFuture: Boolean,
    )

    enum class Daypart { MORNING, AFTERNOON, EVENING, NIGHT }

    @Immutable
    data class ActivityShare(
        val exercise: ExerciseType,
        val recentSeconds: Long,
        val previousSeconds: Long,
    )

    @Immutable
    data class Record(
        val best: WorkoutStats.Best,
        /** Set this calendar week — worth a badge. */
        val isNew: Boolean,
    )

    @Immutable
    data class Insights(
        val goals: FitnessGoals,
        val today: LocalDate,
        val firstDayOfWeek: DayOfWeek,
        /** Days left in this week, today included. */
        val daysLeftInWeek: Int,
        val progress: List<GoalProgress>,
        /** Oldest first; the last one is the current week. Always [TREND_WEEKS] long. */
        val weeks: List<Week>,
        /** Oldest first, [HEATMAP_WEEKS] whole weeks starting on [firstDayOfWeek]. */
        val days: List<Day>,
        /** Average active seconds per weekday, ordered from [firstDayOfWeek]. */
        val weekdayAverageSeconds: List<Long>,
        /** Movement sessions started in each [Daypart], indexed by its ordinal. */
        val daypartCounts: List<Int>,
        val peakWeekday: DayOfWeek?,
        val peakDaypart: Daypart?,
        /** Consecutive weeks the active-minutes goal was met, an in-progress week not breaking it. */
        val goalStreakWeeks: Int,
        val bestGoalStreakWeeks: Int,
        /** Of the last [weeksTracked] weeks, how many met the active-minutes goal. */
        val weeksGoalMet: Int,
        /**
         * The trend weeks since the user's first logged workout, at most [TREND_WEEKS]. A new user
         * is "1 of 1", not "1 of the last 12" — the weeks before they started are not misses.
         */
        val weeksTracked: Int,
        /** Per-week average active seconds over the last [COMPARE_WEEKS] completed weeks, and the block before. */
        val recentWeeklyAverageSeconds: Long,
        val previousWeeklyAverageSeconds: Long,
        /** Time per activity, the last [COMPARE_WEEKS] weeks (this one included) vs the block before, busiest first. */
        val mix: List<ActivityShare>,
        /**
         * The activities that get their own colour, busiest over the whole history first. Fixed
         * for the screen, so a colour follows the activity from chart to chart and never its rank
         * in one week. Everything else is "Other".
         */
        val colorOrder: List<ExerciseType>,
        val records: List<Record>,
        /** Newest first. */
        val recent: List<DetectedWorkout>,
        val historyStart: LocalDate?,
        /** The oldest day Health Connect can contribute to; older weeks hold published workouts only. */
        val deviceHorizon: LocalDate,
    ) {
        val isEmpty: Boolean get() = recent.isEmpty()
        val currentWeek: Week get() = weeks.last()
    }

    /** Activities with their own colour; the rest fold into "Other". */
    const val COLORED_ACTIVITIES = 3

    fun build(
        workouts: List<DetectedWorkout>,
        goals: FitnessGoals,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        deviceWindowDays: Long = WorkoutStats.WINDOW_DAYS,
    ): Insights {
        val today = now.atZone(zone).toLocalDate()
        val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val historyStartWeek = thisWeekStart.minusWeeks(HISTORY_WEEKS - 1L)

        val log =
            workouts
                .filter { !it.localDate(zone).isBefore(historyStartWeek) && !it.localDate(zone).isAfter(today) }
                .sortedByDescending { it.startTimeEpochSeconds }

        val byWeek = log.groupBy { it.localDate(zone).with(TemporalAdjusters.previousOrSame(firstDayOfWeek)) }

        val allWeeks =
            (0 until HISTORY_WEEKS).map { i ->
                val start = historyStartWeek.plusWeeks(i.toLong())
                week(start, byWeek[start].orEmpty(), goals, zone, isCurrent = start == thisWeekStart)
            }

        val current = allWeeks.last()
        val daysCompleted = ChronoUnit.DAYS.between(thisWeekStart, today).toInt() // 0 on the first day
        val daysLeft = 7 - daysCompleted

        val progress =
            buildList {
                add(progress(GoalKind.ACTIVE_MINUTES, current.activeMinutes.toDouble(), goals.weeklyActiveMinutes.toDouble(), daysCompleted))
                add(progress(GoalKind.WORKOUTS, current.workoutCount.toDouble(), goals.weeklyWorkouts.toDouble(), daysCompleted))
                add(progress(GoalKind.ACTIVE_DAYS, current.activeDays.toDouble(), goals.weeklyActiveDays.toDouble(), daysCompleted))
                goals.distance?.let { add(progress(GoalKind.DISTANCE, current.goalDistanceMeters, it.weeklyMeters, daysCompleted)) }
            }

        val met = allWeeks.map { it.activeMinutes >= goals.weeklyActiveMinutes }
        val trend = allWeeks.takeLast(TREND_WEEKS)

        val historyStart = log.lastOrNull()?.localDate(zone)
        // Weeks the user could have logged in: from their first workout's week, or the whole span.
        val weeksSpanned =
            historyStart
                ?.let { ChronoUnit.WEEKS.between(it.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)), thisWeekStart) + 1 }
                ?.coerceIn(1, HISTORY_WEEKS.toLong())
                ?: 1L

        val movement = log.filter { it.isMovement() }

        val weekdayOrder = (0..6).map { firstDayOfWeek.plus(it.toLong()) }
        val weekdaySeconds = movement.groupBy { it.localDate(zone).dayOfWeek }.mapValues { (_, l) -> l.sumOf { it.durationSeconds } }
        val weekdayAverage = weekdayOrder.map { (weekdaySeconds[it] ?: 0L) / weeksSpanned }

        val daypartCounts = IntArray(Daypart.entries.size)
        movement.forEach { daypartCounts[daypart(Instant.ofEpochSecond(it.startTimeEpochSeconds).atZone(zone).hour).ordinal]++ }

        val completed = allWeeks.dropLast(1)
        val recentBlock = completed.takeLast(COMPARE_WEEKS)
        val previousBlock = completed.dropLast(COMPARE_WEEKS).takeLast(COMPARE_WEEKS)

        val colorOrder =
            movement
                .groupBy { it.exercise }
                .mapValues { (_, l) -> l.sumOf { it.durationSeconds } }
                .entries
                .sortedByDescending { it.value }
                .take(COLORED_ACTIVITIES)
                .map { it.key }

        return Insights(
            goals = goals,
            today = today,
            firstDayOfWeek = firstDayOfWeek,
            daysLeftInWeek = daysLeft,
            progress = progress,
            weeks = trend,
            days = days(log, thisWeekStart, today, zone),
            weekdayAverageSeconds = weekdayAverage,
            daypartCounts = daypartCounts.toList(),
            peakWeekday =
                weekdayOrder
                    .zip(weekdayAverage)
                    .filter { it.second > 0 }
                    .maxByOrNull { it.second }
                    ?.first,
            peakDaypart = Daypart.entries.filter { daypartCounts[it.ordinal] > 0 }.maxByOrNull { daypartCounts[it.ordinal] },
            goalStreakWeeks = currentStreak(met),
            bestGoalStreakWeeks = longestRun(met),
            weeksGoalMet = met.takeLast(weeksSpanned.toInt().coerceAtMost(TREND_WEEKS)).count { it },
            weeksTracked = weeksSpanned.toInt().coerceAtMost(TREND_WEEKS),
            recentWeeklyAverageSeconds = recentBlock.averageActive(),
            previousWeeklyAverageSeconds = previousBlock.averageActive(),
            mix = mix(allWeeks.takeLast(COMPARE_WEEKS), allWeeks.dropLast(COMPARE_WEEKS).takeLast(COMPARE_WEEKS)),
            colorOrder = colorOrder,
            records =
                WorkoutStats.bests(movement).map { best ->
                    Record(best, isNew = !best.workout.localDate(zone).isBefore(thisWeekStart))
                },
            recent = log,
            historyStart = historyStart,
            deviceHorizon = today.minusDays(deviceWindowDays),
        )
    }

    private fun week(
        start: LocalDate,
        workouts: List<DetectedWorkout>,
        goals: FitnessGoals,
        zone: ZoneId,
        isCurrent: Boolean,
    ): Week {
        val movement = workouts.filter { it.isMovement() }
        return Week(
            start = start,
            activeSeconds = movement.sumOf { it.durationSeconds },
            workoutCount = movement.size,
            activeDays = movement.mapTo(HashSet()) { it.localDate(zone) }.size,
            secondsByActivity = movement.groupBy { it.exercise }.mapValues { (_, l) -> l.sumOf { it.durationSeconds } },
            goalDistanceMeters =
                goals.distance?.let { goal ->
                    movement.filter { it.exercise == goal.exercise }.sumOf { it.distanceMeters ?: 0.0 }
                } ?: 0.0,
            isCurrent = isCurrent,
        )
    }

    /**
     * Pace is judged on the days already behind us, not today: nothing logged by Monday lunchtime
     * is not "behind". A goal is on track while what is done covers the completed days' share.
     */
    private fun progress(
        kind: GoalKind,
        current: Double,
        target: Double,
        daysCompleted: Int,
    ): GoalProgress {
        val expected = target * daysCompleted / 7.0
        val pace =
            when {
                current >= target -> Pace.MET
                current >= expected -> Pace.ON_TRACK
                else -> Pace.BEHIND
            }
        return GoalProgress(kind, current, target, pace)
    }

    private fun days(
        log: List<DetectedWorkout>,
        thisWeekStart: LocalDate,
        today: LocalDate,
        zone: ZoneId,
    ): List<Day> {
        val byDay = log.filter { it.isMovement() }.groupBy { it.localDate(zone) }
        val first = thisWeekStart.minusWeeks(HEATMAP_WEEKS - 1L)
        return (0 until HEATMAP_WEEKS * 7).map { i ->
            val date = first.plusDays(i.toLong())
            val list = byDay[date].orEmpty()
            Day(date, list.sumOf { it.durationSeconds }, list.size, isFuture = date.isAfter(today))
        }
    }

    internal fun daypart(hour: Int): Daypart =
        when (hour) {
            in 5..11 -> Daypart.MORNING
            in 12..16 -> Daypart.AFTERNOON
            in 17..21 -> Daypart.EVENING
            else -> Daypart.NIGHT
        }

    /** The current week counts once its goal is met, but an unmet week in progress never breaks the run. */
    internal fun currentStreak(met: List<Boolean>): Int {
        if (met.isEmpty()) return 0
        val completed = met.dropLast(1)
        var streak = if (met.last()) 1 else 0
        for (i in completed.indices.reversed()) {
            if (!completed[i]) break
            streak++
        }
        return streak
    }

    private fun longestRun(met: List<Boolean>): Int {
        var best = 0
        var run = 0
        met.forEach {
            run = if (it) run + 1 else 0
            if (run > best) best = run
        }
        return best
    }

    private fun List<Week>.averageActive(): Long = if (isEmpty()) 0 else sumOf { it.activeSeconds } / size

    private fun mix(
        recent: List<Week>,
        previous: List<Week>,
    ): List<ActivityShare> {
        val now = recent.flatMap { it.secondsByActivity.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        val before = previous.flatMap { it.secondsByActivity.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        return (now.keys + before.keys)
            .map { ActivityShare(it, now[it] ?: 0, before[it] ?: 0) }
            .filter { it.recentSeconds > 0 || it.previousSeconds > 0 }
            .sortedByDescending { it.recentSeconds }
    }

    private fun DetectedWorkout.localDate(zone: ZoneId): LocalDate = Instant.ofEpochSecond(startTimeEpochSeconds).atZone(zone).toLocalDate()
}
