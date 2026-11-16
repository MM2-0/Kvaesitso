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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.fitness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.fitness.DetectedWorkout
import com.vitorpamplona.amethyst.commons.fitness.FitnessInsights
import com.vitorpamplona.amethyst.commons.fitness.FitnessInsights.GoalKind
import com.vitorpamplona.amethyst.commons.fitness.FitnessInsights.Pace
import com.vitorpamplona.amethyst.commons.fitness.WorkoutStats
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.my_fitness_active_days
import com.vitorpamplona.amethyst.commons.resources.my_fitness_all_goals_met
import com.vitorpamplona.amethyst.commons.resources.my_fitness_average
import com.vitorpamplona.amethyst.commons.resources.my_fitness_average_change
import com.vitorpamplona.amethyst.commons.resources.my_fitness_best_biggest_climb
import com.vitorpamplona.amethyst.commons.resources.my_fitness_best_highest_heart_rate
import com.vitorpamplona.amethyst.commons.resources.my_fitness_best_longest_distance
import com.vitorpamplona.amethyst.commons.resources.my_fitness_best_longest_duration
import com.vitorpamplona.amethyst.commons.resources.my_fitness_best_most_steps
import com.vitorpamplona.amethyst.commons.resources.my_fitness_best_streak
import com.vitorpamplona.amethyst.commons.resources.my_fitness_consistency
import com.vitorpamplona.amethyst.commons.resources.my_fitness_daypart_afternoon
import com.vitorpamplona.amethyst.commons.resources.my_fitness_daypart_evening
import com.vitorpamplona.amethyst.commons.resources.my_fitness_daypart_morning
import com.vitorpamplona.amethyst.commons.resources.my_fitness_daypart_night
import com.vitorpamplona.amethyst.commons.resources.my_fitness_days_left
import com.vitorpamplona.amethyst.commons.resources.my_fitness_goal_active_minutes
import com.vitorpamplona.amethyst.commons.resources.my_fitness_goal_distance
import com.vitorpamplona.amethyst.commons.resources.my_fitness_goal_line
import com.vitorpamplona.amethyst.commons.resources.my_fitness_goal_streak
import com.vitorpamplona.amethyst.commons.resources.my_fitness_goals_edit
import com.vitorpamplona.amethyst.commons.resources.my_fitness_goals_title
import com.vitorpamplona.amethyst.commons.resources.my_fitness_heatmap_less
import com.vitorpamplona.amethyst.commons.resources.my_fitness_heatmap_more
import com.vitorpamplona.amethyst.commons.resources.my_fitness_history_footer
import com.vitorpamplona.amethyst.commons.resources.my_fitness_history_note
import com.vitorpamplona.amethyst.commons.resources.my_fitness_minutes_value
import com.vitorpamplona.amethyst.commons.resources.my_fitness_mix
import com.vitorpamplona.amethyst.commons.resources.my_fitness_mix_change
import com.vitorpamplona.amethyst.commons.resources.my_fitness_mix_other
import com.vitorpamplona.amethyst.commons.resources.my_fitness_pace_behind
import com.vitorpamplona.amethyst.commons.resources.my_fitness_pace_met
import com.vitorpamplona.amethyst.commons.resources.my_fitness_pace_on_track
import com.vitorpamplona.amethyst.commons.resources.my_fitness_patterns
import com.vitorpamplona.amethyst.commons.resources.my_fitness_peak_day
import com.vitorpamplona.amethyst.commons.resources.my_fitness_peak_time
import com.vitorpamplona.amethyst.commons.resources.my_fitness_per_week
import com.vitorpamplona.amethyst.commons.resources.my_fitness_progress_of
import com.vitorpamplona.amethyst.commons.resources.my_fitness_recent
import com.vitorpamplona.amethyst.commons.resources.my_fitness_record_new
import com.vitorpamplona.amethyst.commons.resources.my_fitness_records
import com.vitorpamplona.amethyst.commons.resources.my_fitness_share
import com.vitorpamplona.amethyst.commons.resources.my_fitness_this_week
import com.vitorpamplona.amethyst.commons.resources.my_fitness_time_of_day
import com.vitorpamplona.amethyst.commons.resources.my_fitness_to_go
import com.vitorpamplona.amethyst.commons.resources.my_fitness_unit_bpm
import com.vitorpamplona.amethyst.commons.resources.my_fitness_unit_kcal
import com.vitorpamplona.amethyst.commons.resources.my_fitness_week_of
import com.vitorpamplona.amethyst.commons.resources.my_fitness_week_trend
import com.vitorpamplona.amethyst.commons.resources.my_fitness_weekday_average
import com.vitorpamplona.amethyst.commons.resources.my_fitness_weeks_met
import com.vitorpamplona.amethyst.commons.resources.my_fitness_workout_count
import com.vitorpamplona.amethyst.commons.resources.my_fitness_workouts
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.labelRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.symbol
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * The redesigned My Fitness dashboard: goal-first, then history.
 *
 * Reading order is the questions a user brings to it — *am I on track this week?* (goal rings and
 * the week strip), *is it trending the right way?* (twelve weeks of bars against the goal line),
 * *am I consistent?* (week streak and an 18-week heatmap), *when do I train?* (weekday and
 * time-of-day profiles), *what am I doing more or less of?* (activity mix vs the block before), and
 * *what have I achieved?* (records). The log itself comes last.
 *
 * Stateless apart from which week the trend chart has selected; everything else is [insights].
 * Platform actions (Health Connect, navigation) come in through [header] and the callbacks, so the
 * same dashboard renders on Android and Desktop.
 */
@Composable
fun FitnessDashboard(
    insights: FitnessInsights.Insights,
    onEditGoals: () -> Unit,
    canShare: (DetectedWorkout) -> Boolean,
    onShare: (DetectedWorkout, String) -> Unit,
    modifier: Modifier = Modifier,
    showDeviceHorizon: Boolean = false,
    miles: Boolean = remember { prefersMiles() },
    header: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = rememberFitnessChartColors()
    val activityColor = remember(insights.colorOrder, colors) { ActivityColors(insights.colorOrder, colors) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        header()
        GoalsHero(insights, colors, activityColor, miles, onEditGoals)
        WeeklyTrendCard(insights, colors, activityColor, showDeviceHorizon)
        ConsistencyCard(insights, colors)
        PatternsCard(insights, colors)
        ActivityMixCard(insights, activityColor)
        RecordsCard(insights, miles)
        RecentCard(insights, activityColor, miles, canShare, onShare)

        Text(
            text = stringRes(Res.string.my_fitness_history_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Colour by activity, fixed for the screen: see [FitnessInsights.Insights.colorOrder]. */
private class ActivityColors(
    private val order: List<ExerciseType>,
    private val colors: FitnessChartColors,
) {
    operator fun get(exercise: ExerciseType): Color {
        val i = order.indexOf(exercise)
        return if (i in colors.activities.indices) colors.activities[i] else colors.other
    }

    val legend: List<Pair<ExerciseType?, Color>> get() = order.mapIndexed { i, e -> e to colors.activities[i] }
}

private val locale: Locale get() = Locale.getDefault()

private fun LocalDate.shortDate(): String = format(DateTimeFormatter.ofPattern("MMM d", locale))

private fun DayOfWeek.narrow(): String = getDisplayName(TextStyle.NARROW, locale)

private fun DayOfWeek.short(): String = getDisplayName(TextStyle.SHORT, locale)

private fun DayOfWeek.full(): String = getDisplayName(TextStyle.FULL, locale)

@Composable
private fun DashboardCard(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                action()
            }
            content()
        }
    }
}

@Composable
private fun Caption(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

// ---------------------------------------------------------------- goals

@Composable
private fun goalLabel(
    kind: GoalKind,
    insights: FitnessInsights.Insights,
): String =
    when (kind) {
        GoalKind.ACTIVE_MINUTES -> stringRes(Res.string.my_fitness_goal_active_minutes)
        GoalKind.WORKOUTS -> stringRes(Res.string.my_fitness_workouts)
        GoalKind.ACTIVE_DAYS -> stringRes(Res.string.my_fitness_active_days)
        GoalKind.DISTANCE ->
            stringRes(Res.string.my_fitness_goal_distance, insights.goals.distance?.let { stringRes(it.exercise.labelRes()) } ?: "")
    }

@Composable
private fun goalValue(
    progress: FitnessInsights.GoalProgress,
    miles: Boolean,
): String =
    when (progress.kind) {
        GoalKind.ACTIVE_MINUTES ->
            stringRes(Res.string.my_fitness_progress_of, progress.current.toLong().toString(), stringRes(Res.string.my_fitness_minutes_value, progress.target.toInt()))
        GoalKind.WORKOUTS, GoalKind.ACTIVE_DAYS ->
            stringRes(Res.string.my_fitness_progress_of, progress.current.toInt().toString(), progress.target.toInt().toString())
        GoalKind.DISTANCE ->
            stringRes(
                Res.string.my_fitness_progress_of,
                formatDistanceValue(progress.current, miles),
                "${formatDistanceValue(progress.target, miles)} ${distanceUnit(miles)}",
            )
    }

@Composable
private fun GoalsHero(
    insights: FitnessInsights.Insights,
    colors: FitnessChartColors,
    activityColor: ActivityColors,
    miles: Boolean,
    onEditGoals: () -> Unit,
) {
    val rings = insights.progress.filter { it.kind != GoalKind.DISTANCE }
    val distance = insights.progress.firstOrNull { it.kind == GoalKind.DISTANCE }
    val minutes = rings.first { it.kind == GoalKind.ACTIVE_MINUTES }

    DashboardCard(
        title = stringRes(Res.string.my_fitness_goals_title),
        action = {
            IconButton(onClick = onEditGoals, modifier = Modifier.size(32.dp)) {
                Icon(MaterialSymbols.Tune, contentDescription = stringRes(Res.string.my_fitness_goals_edit), modifier = Modifier.size(20.dp))
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            GoalRings(rings.mapIndexed { i, p -> RingSpec(p.fraction, colors.rings[i]) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(minutes.current.toLong().toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringRes(Res.string.my_fitness_minutes_value, minutes.target.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rings.forEachIndexed { i, p -> GoalRow(colors.rings[i], goalLabel(p.kind, insights), goalValue(p, miles), p.pace) }
            }
        }

        distance?.let { p ->
            val color =
                insights.goals.distance
                    ?.exercise
                    ?.let { activityColor[it] } ?: colors.rings[0]
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(goalLabel(p.kind, insights), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text(goalValue(p, miles), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                LabeledBar(label = "", fraction = p.fraction, value = "${(p.fraction * 100).toInt()}%", color = color, colors = colors, labelWidth = 0.dp)
            }
        }

        val thisWeek = insights.days.takeLast(7)
        WeekStrip(
            labels = thisWeek.map { it.date.dayOfWeek.narrow() },
            marks =
                thisWeek.map {
                    when {
                        it.date == insights.today -> if (it.workoutCount > 0) DayMark.TODAY_DONE else DayMark.TODAY
                        it.isFuture -> DayMark.FUTURE
                        it.workoutCount > 0 -> DayMark.DONE
                        else -> DayMark.REST
                    }
                },
            color = colors.rings[2],
            colors = colors,
        )

        val summary =
            if (insights.progress.all { it.pace == Pace.MET }) {
                stringRes(Res.string.my_fitness_all_goals_met)
            } else if (minutes.pace != Pace.MET) {
                stringRes(Res.string.my_fitness_to_go, stringRes(Res.string.my_fitness_minutes_value, minutes.remaining.toInt())) +
                    " · " + pluralStringRes(Res.plurals.my_fitness_days_left, insights.daysLeftInWeek, insights.daysLeftInWeek)
            } else {
                pluralStringRes(Res.plurals.my_fitness_days_left, insights.daysLeftInWeek, insights.daysLeftInWeek)
            }
        Text(summary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GoalRow(
    color: Color,
    label: String,
    value: String,
    pace: Pace,
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            PaceLabel(pace)
        }
    }
}

/** Pace is never colour alone: an icon and a word carry it, the colour only echoes them. */
@Composable
private fun PaceLabel(pace: Pace) {
    val (symbol, text, tint) =
        when (pace) {
            Pace.MET -> Triple(MaterialSymbols.CheckCircle, stringRes(Res.string.my_fitness_pace_met), MaterialTheme.colorScheme.primary)
            Pace.ON_TRACK -> Triple(MaterialSymbols.AutoMirrored.ShowChart, stringRes(Res.string.my_fitness_pace_on_track), MaterialTheme.colorScheme.onSurfaceVariant)
            Pace.BEHIND -> Triple(MaterialSymbols.Schedule, stringRes(Res.string.my_fitness_pace_behind), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(symbol, contentDescription = null, modifier = Modifier.size(12.dp), tint = tint)
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

// ---------------------------------------------------------------- trend

@Composable
private fun WeeklyTrendCard(
    insights: FitnessInsights.Insights,
    colors: FitnessChartColors,
    activityColor: ActivityColors,
    showDeviceHorizon: Boolean,
) {
    var selected by remember(insights.weeks.size, insights.today) { mutableIntStateOf(insights.weeks.lastIndex) }
    val week = insights.weeks[selected.coerceIn(insights.weeks.indices)]
    val hasOther = insights.weeks.any { w -> w.secondsByActivity.keys.any { it !in insights.colorOrder } }

    DashboardCard(stringRes(Res.string.my_fitness_week_trend)) {
        Column {
            Caption(if (week.isCurrent) stringRes(Res.string.my_fitness_this_week) else stringRes(Res.string.my_fitness_week_of, week.start.shortDate()))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatDuration(week.activeSeconds), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = pluralStringRes(Res.plurals.my_fitness_workout_count, week.workoutCount, week.workoutCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }

        val bars =
            insights.weeks.map { w ->
                val colored = insights.colorOrder.map { e -> (w.secondsByActivity[e] ?: 0L) / 60f to activityColor[e] }
                val other =
                    w.secondsByActivity
                        .filterKeys { it !in insights.colorOrder }
                        .values
                        .sum() / 60f
                StackedBar(colored + (other to colors.other), w.start.shortDate())
            }
        val description = pluralStringRes(Res.plurals.my_fitness_weeks_met, insights.weeksTracked, insights.weeksGoalMet, insights.weeksTracked)

        WeeklyBarChart(
            bars = bars,
            goal = insights.goals.weeklyActiveMinutes.toFloat(),
            selected = selected,
            onSelect = { selected = it },
            colors = colors,
            modifier = Modifier.chartDescription("${stringRes(Res.string.my_fitness_week_trend)}. $description"),
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            activityColor.legend.forEach { (exercise, color) -> LegendChip(color, exercise?.let { stringRes(it.labelRes()) } ?: "") }
            if (hasOther) LegendChip(colors.other, stringRes(Res.string.my_fitness_mix_other))
            GoalLineLegend(
                "${stringRes(Res.string.my_fitness_goal_line)} ${stringRes(Res.string.my_fitness_minutes_value, insights.goals.weeklyActiveMinutes)}",
                colors,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (insights.weeksTracked >= 2) Text(description, style = MaterialTheme.typography.bodySmall)
            val change = WorkoutStats.percentChange(insights.recentWeeklyAverageSeconds.toDouble(), insights.previousWeeklyAverageSeconds.toDouble())
            if (insights.recentWeeklyAverageSeconds > 0) {
                Text(
                    text =
                        if (change != null) {
                            stringRes(Res.string.my_fitness_average_change, formatDuration(insights.recentWeeklyAverageSeconds), "${if (change >= 0) "+" else ""}$change%")
                        } else {
                            stringRes(Res.string.my_fitness_average, formatDuration(insights.recentWeeklyAverageSeconds))
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showDeviceHorizon &&
                insights.weeks
                    .first()
                    .start
                    .isBefore(insights.deviceHorizon)
            ) {
                Text(
                    text = stringRes(Res.string.my_fitness_history_note, insights.deviceHorizon.shortDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- consistency

/** Rest, a short session, a solid one, a long one, a big day. */
private fun heatLevel(seconds: Long): Int {
    val minutes = seconds / 60
    return when {
        minutes <= 0 -> 0
        minutes < 20 -> 1
        minutes < 45 -> 2
        minutes < 90 -> 3
        else -> 4
    }
}

@Composable
private fun ConsistencyCard(
    insights: FitnessInsights.Insights,
    colors: FitnessChartColors,
) {
    DashboardCard(stringRes(Res.string.my_fitness_consistency)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                MaterialSymbols.LocalFireDepartment,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (insights.goalStreakWeeks > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                filled = insights.goalStreakWeeks > 0,
            )
            Text(
                text = pluralStringRes(Res.plurals.my_fitness_goal_streak, insights.goalStreakWeeks, insights.goalStreakWeeks),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (insights.bestGoalStreakWeeks > 0) {
                Caption(pluralStringRes(Res.plurals.my_fitness_best_streak, insights.bestGoalStreakWeeks, insights.bestGoalStreakWeeks))
            }
        }

        val weeks = insights.days.size / 7
        val rowLabels = insights.days.take(7).mapIndexed { i, d -> if (i % 2 == 0) d.date.dayOfWeek.short() else "" }
        val columnLabels =
            (0 until weeks).map { w ->
                val week = insights.days.subList(w * 7, w * 7 + 7)
                week
                    .firstOrNull { it.date.dayOfMonth == 1 }
                    ?.date
                    ?.month
                    ?.getDisplayName(TextStyle.SHORT, locale)
            }
        val active = insights.days.count { it.workoutCount > 0 }

        CalendarHeatmap(
            levels = insights.days.map { if (it.isFuture) -1 else heatLevel(it.activeSeconds) },
            weeks = weeks,
            rowLabels = rowLabels,
            columnLabels = columnLabels,
            colors = colors,
            modifier = Modifier.chartDescription("${stringRes(Res.string.my_fitness_consistency)}: $active ${stringRes(Res.string.my_fitness_active_days).lowercase(locale)}"),
        )
        HeatmapLegend(stringRes(Res.string.my_fitness_heatmap_less), stringRes(Res.string.my_fitness_heatmap_more), colors, Modifier.align(Alignment.End))
    }
}

// ---------------------------------------------------------------- patterns

@Composable
private fun daypartLabel(part: FitnessInsights.Daypart): String =
    when (part) {
        FitnessInsights.Daypart.MORNING -> stringRes(Res.string.my_fitness_daypart_morning)
        FitnessInsights.Daypart.AFTERNOON -> stringRes(Res.string.my_fitness_daypart_afternoon)
        FitnessInsights.Daypart.EVENING -> stringRes(Res.string.my_fitness_daypart_evening)
        FitnessInsights.Daypart.NIGHT -> stringRes(Res.string.my_fitness_daypart_night)
    }

@Composable
private fun PatternsCard(
    insights: FitnessInsights.Insights,
    colors: FitnessChartColors,
) {
    val peakDay = insights.peakWeekday ?: return
    // A profile from a handful of sessions is an anecdote, not a pattern.
    if (insights.daypartCounts.sum() < MIN_PATTERN_SESSIONS) return
    val weekdays = (0..6).map { insights.firstDayOfWeek.plus(it.toLong()) }

    DashboardCard(stringRes(Res.string.my_fitness_patterns)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InsightTile(MaterialSymbols.CalendarMonth, stringRes(Res.string.my_fitness_peak_day), peakDay.full(), Modifier.weight(1f))
            insights.peakDaypart?.let {
                InsightTile(MaterialSymbols.Schedule, stringRes(Res.string.my_fitness_peak_time), daypartLabel(it), Modifier.weight(1f))
            }
        }

        Caption(stringRes(Res.string.my_fitness_weekday_average))
        ColumnChart(
            values = insights.weekdayAverageSeconds.map { it / 60f },
            labels = weekdays.map { it.narrow() },
            color = MaterialTheme.colorScheme.primary,
            highlight = weekdays.indexOf(peakDay),
            colors = colors,
            modifier = Modifier.chartDescription("${stringRes(Res.string.my_fitness_peak_day)}: ${peakDay.full()}"),
        )

        Caption(stringRes(Res.string.my_fitness_time_of_day))
        val max = (insights.daypartCounts.maxOrNull() ?: 0).coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FitnessInsights.Daypart.entries.forEach { part ->
                val count = insights.daypartCounts[part.ordinal]
                LabeledBar(
                    label = daypartLabel(part),
                    fraction = count.toFloat() / max,
                    value = count.toString(),
                    color = if (part == insights.peakDaypart) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun InsightTile(
    symbol: MaterialSymbol,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), modifier = modifier) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(symbol, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------------------------------------------------------- mix

@Composable
private fun ActivityMixCard(
    insights: FitnessInsights.Insights,
    activityColor: ActivityColors,
) {
    val mix = insights.mix.filter { it.recentSeconds > 0 }
    if (mix.isEmpty()) return
    val weeks = FitnessInsights.COMPARE_WEEKS

    DashboardCard(stringRes(Res.string.my_fitness_mix)) {
        ShareBar(mix.map { it.recentSeconds.toFloat() to activityColor[it.exercise] })
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            mix.take(5).forEach { share ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(activityColor[share.exercise]))
                    Icon(share.exercise.symbol(), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringRes(share.exercise.labelRes()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringRes(Res.string.my_fitness_per_week, formatDuration(share.recentSeconds / weeks)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        if (share.previousSeconds > 0) {
                            val delta = (share.recentSeconds - share.previousSeconds) / weeks
                            Caption(stringRes(Res.string.my_fitness_mix_change, "${if (delta >= 0) "+" else "−"}${formatDuration(abs(delta))}"))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- records

@Composable
private fun recordLabel(kind: WorkoutStats.BestKind): String =
    when (kind) {
        WorkoutStats.BestKind.LONGEST_DISTANCE -> stringRes(Res.string.my_fitness_best_longest_distance)
        WorkoutStats.BestKind.LONGEST_DURATION -> stringRes(Res.string.my_fitness_best_longest_duration)
        WorkoutStats.BestKind.BIGGEST_CLIMB -> stringRes(Res.string.my_fitness_best_biggest_climb)
        WorkoutStats.BestKind.MOST_STEPS -> stringRes(Res.string.my_fitness_best_most_steps)
        WorkoutStats.BestKind.HIGHEST_HEART_RATE -> stringRes(Res.string.my_fitness_best_highest_heart_rate)
    }

@Composable
private fun recordValue(
    best: WorkoutStats.Best,
    miles: Boolean,
): String =
    when (best.kind) {
        WorkoutStats.BestKind.LONGEST_DISTANCE -> "${formatDistanceValue(best.workout.distanceMeters ?: 0.0, miles)} ${distanceUnit(miles)}"
        WorkoutStats.BestKind.LONGEST_DURATION -> formatDuration(best.workout.durationSeconds)
        WorkoutStats.BestKind.BIGGEST_CLIMB -> "${formatElevationValue(best.workout.elevationGainMeters ?: 0.0, miles)} ${elevationUnit(miles)}"
        WorkoutStats.BestKind.MOST_STEPS -> (best.workout.steps ?: 0).toString()
        WorkoutStats.BestKind.HIGHEST_HEART_RATE -> "${best.workout.maxHeartRate ?: 0} ${stringRes(Res.string.my_fitness_unit_bpm)}"
    }

private fun DetectedWorkout.date(): LocalDate = Instant.ofEpochSecond(startTimeEpochSeconds).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun RecordsCard(
    insights: FitnessInsights.Insights,
    miles: Boolean,
) {
    if (insights.records.isEmpty()) return

    DashboardCard(stringRes(Res.string.my_fitness_records)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            insights.records.forEach { record ->
                val workout = record.best.workout
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        MaterialSymbols.MilitaryTech,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (record.isNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        filled = record.isNew,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(recordLabel(record.best.kind), style = MaterialTheme.typography.bodyMedium)
                        Caption("${stringRes(workout.exercise.labelRes())} · ${workout.date().shortDate()}")
                    }
                    if (record.isNew) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                stringRes(Res.string.my_fitness_record_new),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(recordValue(record.best, miles), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- recent

private const val RECENT_LIMIT = 8

private const val MIN_PATTERN_SESSIONS = 8

@Composable
private fun DetectedWorkout.displayLabel(): String = title?.takeIf { it.isNotBlank() } ?: stringRes(exercise.labelRes())

@Composable
private fun workoutSummary(
    workout: DetectedWorkout,
    miles: Boolean,
): String {
    val parts = mutableListOf<String>()
    parts.add(formatDuration(workout.durationSeconds))
    workout.distanceMeters?.takeIf { it > 0 }?.let { parts.add("${formatDistanceValue(it, miles)} ${distanceUnit(miles)}") }
    workout.calories?.takeIf { it > 0 }?.let { parts.add("$it ${stringRes(Res.string.my_fitness_unit_kcal)}") }
    workout.avgHeartRate?.takeIf { it > 0 }?.let { parts.add("$it ${stringRes(Res.string.my_fitness_unit_bpm)}") }
    workout.elevationGainMeters?.takeIf { it > 0 }?.let { parts.add("${formatElevationValue(it, miles)} ${elevationUnit(miles)}") }
    return parts.joinToString(" · ")
}

@Composable
private fun RecentCard(
    insights: FitnessInsights.Insights,
    activityColor: ActivityColors,
    miles: Boolean,
    canShare: (DetectedWorkout) -> Boolean,
    onShare: (DetectedWorkout, String) -> Unit,
) {
    DashboardCard(stringRes(Res.string.my_fitness_recent)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            insights.recent.take(RECENT_LIMIT).forEach { workout ->
                // Resolved here rather than in the click lambda: it reads a string resource.
                val label = workout.displayLabel()
                val color = activityColor[workout.exercise]
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(workout.exercise.symbol(), contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.size(6.dp))
                            Caption(workout.date().let { "${it.dayOfWeek.short()}, ${it.shortDate()}" })
                        }
                        Text(
                            text = workoutSummary(workout, miles),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (canShare(workout)) {
                        IconButton(onClick = { onShare(workout, label) }, modifier = Modifier.size(36.dp)) {
                            Icon(MaterialSymbols.Share, contentDescription = stringRes(Res.string.my_fitness_share), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
