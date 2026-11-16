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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.fitness.FitnessGoals
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.my_fitness_active_days
import com.vitorpamplona.amethyst.commons.resources.my_fitness_distance
import com.vitorpamplona.amethyst.commons.resources.my_fitness_goal_active_minutes
import com.vitorpamplona.amethyst.commons.resources.my_fitness_goal_distance_off
import com.vitorpamplona.amethyst.commons.resources.my_fitness_goal_editor_hint
import com.vitorpamplona.amethyst.commons.resources.my_fitness_goals_edit
import com.vitorpamplona.amethyst.commons.resources.my_fitness_minutes_value
import com.vitorpamplona.amethyst.commons.resources.my_fitness_workouts
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.labelRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DistanceTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import kotlin.math.roundToInt

/** Activities a distance goal makes sense for, in the order the chips offer them. */
private val DISTANCE_ACTIVITIES = listOf(ExerciseType.RUNNING, ExerciseType.CYCLING, ExerciseType.WALKING, ExerciseType.HIKING, ExerciseType.SWIMMING, ExerciseType.ROWING)

/**
 * Edits the weekly goals with steppers rather than text fields: every value is a small whole number
 * with a sensible step, and a stepper cannot be given "abc" or a negative target.
 */
@Composable
fun FitnessGoalsDialog(
    goals: FitnessGoals,
    onDismiss: () -> Unit,
    onSave: (FitnessGoals) -> Unit,
    miles: Boolean = remember { prefersMiles() },
) {
    var draft by remember(goals) { mutableStateOf(goals) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.my_fitness_goals_edit)) },
        text = { FitnessGoalsEditor(draft, { draft = it }, miles) },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text(stringRes(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

/** The body of [FitnessGoalsDialog], on its own so it can be embedded or rendered without a window. */
@Composable
fun FitnessGoalsEditor(
    draft: FitnessGoals,
    onChange: (FitnessGoals) -> Unit,
    miles: Boolean,
) {
    val unitMeters = if (miles) DistanceTag.METERS_PER_MILE else 1000.0
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Stepper(
            label = stringRes(Res.string.my_fitness_goal_active_minutes),
            value = stringRes(Res.string.my_fitness_minutes_value, draft.weeklyActiveMinutes),
            onMinus = { onChange(draft.copy(weeklyActiveMinutes = (draft.weeklyActiveMinutes - 15).coerceAtLeast(15))) },
            onPlus = { onChange(draft.copy(weeklyActiveMinutes = (draft.weeklyActiveMinutes + 15).coerceAtMost(1500))) },
        )
        Text(
            stringRes(Res.string.my_fitness_goal_editor_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Stepper(
            label = stringRes(Res.string.my_fitness_workouts),
            value = draft.weeklyWorkouts.toString(),
            onMinus = { onChange(draft.copy(weeklyWorkouts = (draft.weeklyWorkouts - 1).coerceAtLeast(1))) },
            onPlus = { onChange(draft.copy(weeklyWorkouts = (draft.weeklyWorkouts + 1).coerceAtMost(21))) },
        )
        Stepper(
            label = stringRes(Res.string.my_fitness_active_days),
            value = draft.weeklyActiveDays.toString(),
            onMinus = { onChange(draft.copy(weeklyActiveDays = (draft.weeklyActiveDays - 1).coerceAtLeast(1))) },
            onPlus = { onChange(draft.copy(weeklyActiveDays = (draft.weeklyActiveDays + 1).coerceAtMost(7))) },
        )

        Text(stringRes(Res.string.my_fitness_distance), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.distance == null,
                onClick = { onChange(draft.copy(distance = null)) },
                label = { Text(stringRes(Res.string.my_fitness_goal_distance_off)) },
            )
            DISTANCE_ACTIVITIES.forEach { exercise ->
                FilterChip(
                    selected = draft.distance?.exercise == exercise,
                    onClick = {
                        onChange(draft.copy(distance = FitnessGoals.DistanceGoal(exercise, draft.distance?.weeklyMeters ?: (20 * unitMeters))))
                    },
                    label = { Text(stringRes(exercise.labelRes())) },
                )
            }
        }
        draft.distance?.let { goal ->
            val units = (goal.weeklyMeters / unitMeters).roundToInt()
            Stepper(
                label = stringRes(goal.exercise.labelRes()),
                value = "$units ${distanceUnit(miles)}",
                onMinus = { onChange(draft.copy(distance = goal.copy(weeklyMeters = (units - 5).coerceAtLeast(5) * unitMeters))) },
                onPlus = { onChange(draft.copy(distance = goal.copy(weeklyMeters = (units + 5).coerceAtMost(1000) * unitMeters))) },
            )
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(36.dp)) {
            Icon(MaterialSymbols.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 64.dp))
        FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(36.dp)) {
            Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}
