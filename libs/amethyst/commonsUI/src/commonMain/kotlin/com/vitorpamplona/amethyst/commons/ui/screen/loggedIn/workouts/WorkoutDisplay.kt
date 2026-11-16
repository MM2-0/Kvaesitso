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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.exercise_amrap
import com.vitorpamplona.amethyst.commons.resources.exercise_circuit
import com.vitorpamplona.amethyst.commons.resources.exercise_cycling
import com.vitorpamplona.amethyst.commons.resources.exercise_diet
import com.vitorpamplona.amethyst.commons.resources.exercise_emom
import com.vitorpamplona.amethyst.commons.resources.exercise_fasting
import com.vitorpamplona.amethyst.commons.resources.exercise_hiking
import com.vitorpamplona.amethyst.commons.resources.exercise_meditation
import com.vitorpamplona.amethyst.commons.resources.exercise_rowing
import com.vitorpamplona.amethyst.commons.resources.exercise_running
import com.vitorpamplona.amethyst.commons.resources.exercise_strength
import com.vitorpamplona.amethyst.commons.resources.exercise_swimming
import com.vitorpamplona.amethyst.commons.resources.exercise_walking
import com.vitorpamplona.amethyst.commons.resources.exercise_yoga
import com.vitorpamplona.amethyst.commons.resources.workout
import com.vitorpamplona.amethyst.commons.resources.workout_calories
import com.vitorpamplona.amethyst.commons.resources.workout_distance
import com.vitorpamplona.amethyst.commons.resources.workout_duration
import com.vitorpamplona.amethyst.commons.resources.workout_elevation
import com.vitorpamplona.amethyst.commons.resources.workout_elevation_loss
import com.vitorpamplona.amethyst.commons.resources.workout_exercises
import com.vitorpamplona.amethyst.commons.resources.workout_heart_rate
import com.vitorpamplona.amethyst.commons.resources.workout_max_heart_rate
import com.vitorpamplona.amethyst.commons.resources.workout_pace
import com.vitorpamplona.amethyst.commons.resources.workout_reps
import com.vitorpamplona.amethyst.commons.resources.workout_sets
import com.vitorpamplona.amethyst.commons.resources.workout_speed
import com.vitorpamplona.amethyst.commons.resources.workout_steps
import com.vitorpamplona.amethyst.commons.resources.workout_volume
import com.vitorpamplona.amethyst.commons.resources.workout_weight
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.util.phonePrefersMiles
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.fitness.workout.ExerciseGroup
import com.vitorpamplona.quartz.experimental.fitness.workout.ExerciseTemplateEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DistanceTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DurationTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.Elevation
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.WeightTag
import com.vitorpamplona.quartz.nip01Core.core.Address
import org.jetbrains.compose.resources.StringResource
import kotlin.math.abs
import kotlin.math.round

fun ExerciseType?.symbol(): MaterialSymbol =
    when (this) {
        ExerciseType.RUNNING -> MaterialSymbols.DirectionsRun
        ExerciseType.WALKING -> MaterialSymbols.DirectionsWalk
        ExerciseType.CYCLING -> MaterialSymbols.DirectionsBike
        ExerciseType.HIKING -> MaterialSymbols.Hiking
        ExerciseType.SWIMMING -> MaterialSymbols.Pool
        ExerciseType.ROWING -> MaterialSymbols.Rowing
        ExerciseType.STRENGTH -> MaterialSymbols.FitnessCenter
        ExerciseType.YOGA -> MaterialSymbols.SelfImprovement
        ExerciseType.MEDITATION -> MaterialSymbols.SelfImprovement
        ExerciseType.DIET -> MaterialSymbols.Restaurant
        ExerciseType.FASTING -> MaterialSymbols.Timer
        ExerciseType.CIRCUIT -> MaterialSymbols.FitnessCenter
        ExerciseType.EMOM -> MaterialSymbols.FitnessCenter
        ExerciseType.AMRAP -> MaterialSymbols.FitnessCenter
        null -> MaterialSymbols.DirectionsRun
    }

fun ExerciseType.labelRes(): StringResource =
    when (this) {
        ExerciseType.RUNNING -> Res.string.exercise_running
        ExerciseType.WALKING -> Res.string.exercise_walking
        ExerciseType.CYCLING -> Res.string.exercise_cycling
        ExerciseType.HIKING -> Res.string.exercise_hiking
        ExerciseType.SWIMMING -> Res.string.exercise_swimming
        ExerciseType.ROWING -> Res.string.exercise_rowing
        ExerciseType.STRENGTH -> Res.string.exercise_strength
        ExerciseType.YOGA -> Res.string.exercise_yoga
        ExerciseType.MEDITATION -> Res.string.exercise_meditation
        ExerciseType.DIET -> Res.string.exercise_diet
        ExerciseType.FASTING -> Res.string.exercise_fasting
        ExerciseType.CIRCUIT -> Res.string.exercise_circuit
        ExerciseType.EMOM -> Res.string.exercise_emom
        ExerciseType.AMRAP -> Res.string.exercise_amrap
    }

private fun Double.trimmed(): String = if (this % 1.0 == 0.0 && abs(this) < 1e15) toLong().toString() else toString()

private fun paceMinPerUnit(
    durationSeconds: Long,
    distanceValue: Double,
): String {
    val secondsPerUnit = (durationSeconds / distanceValue).toLong()
    return "${secondsPerUnit / 60}:${(secondsPerUnit % 60).toString().padStart(2, '0')}"
}

/** Average speed, more natural than pace for wheeled/water sports. Returns e.g. `24.5 km/h` or `15.2 mph`. */
private fun speed(
    durationSeconds: Long,
    distance: DistanceTag,
): String {
    val hours = durationSeconds / 3600.0
    return if (distance.unit == DistanceTag.MILES) {
        "${trimToOneDecimal(distance.value / hours)} mph"
    } else {
        "${trimToOneDecimal(distance.toKilometers() / hours)} km/h"
    }
}

private fun trimToOneDecimal(value: Double): String {
    val rounded = round(value * 10.0) / 10.0
    return rounded.trimmed()
}

/** Distance in the viewer's preferred unit (miles or km), rounded to 2 decimals. */
private fun DistanceTag.toDisplay(miles: Boolean): DistanceTag =
    if (miles) {
        DistanceTag(round(toMeters() / DistanceTag.METERS_PER_MILE * 100.0) / 100.0, DistanceTag.MILES)
    } else {
        DistanceTag(round(toKilometers() * 100.0) / 100.0, DistanceTag.KILOMETERS)
    }

/** Elevation in the viewer's preferred unit (feet or metres), rounded to whole units. */
private fun Elevation.toDisplay(miles: Boolean): Elevation =
    if (miles) {
        Elevation(round(toMeters() / Elevation.METERS_PER_FOOT), Elevation.FEET)
    } else {
        Elevation(round(toMeters()), Elevation.METERS)
    }

/** Weight in the viewer's preferred unit (lbs or kg), rounded to 1 decimal. */
private fun WeightTag.toDisplay(miles: Boolean): WeightTag =
    if (miles) {
        WeightTag(round(toKilograms() / WeightTag.KILOGRAMS_PER_POUND * 10.0) / 10.0, WeightTag.POUNDS)
    } else {
        WeightTag(round(toKilograms() * 10.0) / 10.0, WeightTag.KILOGRAMS)
    }

/** Renders a kilogram weight (POWR's native unit) in the viewer's preferred unit, e.g. `84 kg` or `185 lbs`. */
private fun formatWeightKg(
    kg: Double,
    miles: Boolean,
): String {
    val display = WeightTag(kg, WeightTag.KILOGRAMS).toDisplay(miles)
    return "${display.value.trimmed()} ${display.unit}"
}

/** One readable line summarizing the sets logged for a single exercise. */
private fun ExerciseGroup.summaryLine(miles: Boolean): String {
    val descriptors =
        sets.map { set ->
            val reps = set.reps
            val kg = set.weightKg?.takeIf { it > 0.0 }
            when {
                kg != null && reps != null -> "$reps × ${formatWeightKg(kg, miles)}"
                reps != null -> "$reps reps"
                kg != null -> formatWeightKg(kg, miles)
                else -> "—"
            }
        }
    if (descriptors.isEmpty()) return ""
    // Collapse identical sets (e.g. 3 sets of 8 × 84 kg) into "3 × 8 × 84 kg".
    return if (descriptors.size > 1 && descriptors.distinct().size == 1) {
        "${descriptors.size} × ${descriptors.first()}"
    } else {
        descriptors.joinToString(", ")
    }
}

/** One-shot snapshot of the parsed workout tags, so the feed doesn't re-scan the tag array on every recomposition. */
@Immutable
class WorkoutInfo(
    val title: String?,
    val type: ExerciseType?,
    val exerciseRaw: String?,
    val typeRaw: String?,
    val source: String?,
    val durationSeconds: Long?,
    val distance: DistanceTag?,
    val elevationGain: Elevation?,
    val elevationLoss: Elevation?,
    val calories: Int?,
    val steps: Int?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val sets: Int?,
    val reps: Int?,
    val weight: WeightTag?,
    // POWR / NIP-101e strength dialect: per-exercise logged sets (weights are in kilograms).
    val exerciseGroups: List<ExerciseGroup>,
) {
    /** Rewrites the unit-bearing metrics into the viewer's preferred system (miles/feet/lbs vs km/m/kg). */
    fun inUnits(miles: Boolean) =
        WorkoutInfo(
            title = title,
            type = type,
            exerciseRaw = exerciseRaw,
            typeRaw = typeRaw,
            source = source,
            durationSeconds = durationSeconds,
            distance = distance?.toDisplay(miles),
            elevationGain = elevationGain?.toDisplay(miles),
            elevationLoss = elevationLoss?.toDisplay(miles),
            calories = calories,
            steps = steps,
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            sets = sets,
            reps = reps,
            weight = weight?.toDisplay(miles),
            // Kept in kilograms; the breakdown converts per the viewer's unit at render time.
            exerciseGroups = exerciseGroups,
        )

    companion object {
        fun from(event: WorkoutRecordEvent) =
            WorkoutInfo(
                title = event.title(),
                type = event.activityType(),
                exerciseRaw = event.exercise(),
                typeRaw = event.workoutTypeCode(),
                source = event.workoutSource() ?: event.client(),
                durationSeconds = event.effectiveDurationSeconds(),
                distance = event.distance(),
                elevationGain = event.elevationGain(),
                elevationLoss = event.elevationLoss(),
                calories = event.calories(),
                steps = event.steps(),
                avgHeartRate = event.avgHeartRate(),
                maxHeartRate = event.maxHeartRate(),
                sets = event.sets(),
                reps = event.reps(),
                weight = event.weight(),
                exerciseGroups = event.exerciseGroups(),
            )
    }
}

/** Which metric is promoted to the hero number, so the grid below can skip repeating it. */
private enum class HeroKind { DISTANCE, STEPS, DURATION, NONE }

/** A single secondary metric: a bold value over a muted label. */
@Immutable
private class Stat(
    val value: String,
    val label: String,
)

@Composable
fun WorkoutDisplay(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    canPreview: Boolean,
    quotesLeft: Int,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = (baseNote.event as? WorkoutRecordEvent) ?: return

    val miles = remember { phonePrefersMiles() }
    val info = remember(baseNote, miles) { WorkoutInfo.from(event).inUnits(miles) }
    val typeLabel =
        info.type?.let { stringRes(it.labelRes()) }
            ?: info.exerciseRaw
            ?: info.typeRaw?.replaceFirstChar { it.uppercaseChar() }
            ?: stringRes(Res.string.workout)

    val duration = info.durationSeconds
    val distance = info.distance
    val steps = info.steps

    val heroKind =
        when {
            distance != null && distance.value > 0.0 -> HeroKind.DISTANCE
            steps != null -> HeroKind.STEPS
            duration != null -> HeroKind.DURATION
            else -> HeroKind.NONE
        }

    val secondaryStats =
        buildSecondaryStats(
            info = info,
            heroKind = heroKind,
            miles = miles,
            durationLabel = stringRes(Res.string.workout_duration),
            distanceLabel = stringRes(Res.string.workout_distance),
            paceLabel = stringRes(Res.string.workout_pace),
            speedLabel = stringRes(Res.string.workout_speed),
            elevationGainLabel = stringRes(Res.string.workout_elevation),
            elevationLossLabel = stringRes(Res.string.workout_elevation_loss),
            caloriesLabel = stringRes(Res.string.workout_calories),
            stepsLabel = stringRes(Res.string.workout_steps),
            heartRateLabel = stringRes(Res.string.workout_heart_rate),
            maxHeartRateLabel = stringRes(Res.string.workout_max_heart_rate),
            setsLabel = stringRes(Res.string.workout_sets),
            repsLabel = stringRes(Res.string.workout_reps),
            weightLabel = stringRes(Res.string.workout_weight),
            exercisesLabel = stringRes(Res.string.workout_exercises),
            volumeLabel = stringRes(Res.string.workout_volume),
        )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    symbol = info.type.symbol(),
                    contentDescription = typeLabel,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.title ?: typeLabel,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (info.title != null) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                }
            }

            info.source?.let {
                Spacer(modifier = Modifier.width(8.dp))
                WorkoutSourceBadge(it)
            }
        }

        when (heroKind) {
            HeroKind.DISTANCE ->
                WorkoutHero(distance!!.value.trimmed(), distance.unit, stringRes(Res.string.workout_distance))
            HeroKind.STEPS ->
                WorkoutHero(steps!!.toString(), null, stringRes(Res.string.workout_steps))
            HeroKind.DURATION ->
                WorkoutHero(DurationTag.formatTime(duration!!), null, stringRes(Res.string.workout_duration))
            HeroKind.NONE -> {}
        }

        WorkoutStatsGrid(secondaryStats)

        if (info.exerciseGroups.isNotEmpty()) {
            ExerciseBreakdown(info.exerciseGroups, miles, accountViewModel)
        }

        // Route the note (event content) through the same kind-1 pipeline: rich text with
        // links/mentions/hashtags, embeds, sensitivity warning and inline translations.
        val notes = event.content.trim()
        if (notes.isNotEmpty()) {
            val callbackUri = remember(baseNote) { baseNote.toNostrUri() }
            val tags = remember(baseNote) { event.tags.toImmutableListOfLists() }
            SensitivityWarning(note = baseNote, accountViewModel = accountViewModel) {
                TranslatableRichTextViewer(
                    content = notes,
                    canPreview = canPreview,
                    quotesLeft = quotesLeft,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id = baseNote.idHex,
                    callbackUri = callbackUri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

/** Builds the ordered list of secondary metrics, skipping whichever one is shown as the hero. */
private fun buildSecondaryStats(
    info: WorkoutInfo,
    heroKind: HeroKind,
    miles: Boolean,
    durationLabel: String,
    distanceLabel: String,
    paceLabel: String,
    speedLabel: String,
    elevationGainLabel: String,
    elevationLossLabel: String,
    caloriesLabel: String,
    stepsLabel: String,
    heartRateLabel: String,
    maxHeartRateLabel: String,
    setsLabel: String,
    repsLabel: String,
    weightLabel: String,
    exercisesLabel: String,
    volumeLabel: String,
): List<Stat> {
    val duration = info.durationSeconds
    val distance = info.distance

    return buildList {
        // POWR / NIP-101e strength workouts: aggregate the per-set exercise tags.
        val groups = info.exerciseGroups
        if (groups.isNotEmpty()) {
            add(Stat("${groups.size}", exercisesLabel))
            add(Stat("${groups.sumOf { it.sets.size }}", setsLabel))
            val volumeKg = groups.mapNotNull { it.totalVolumeKg() }.takeIf { it.isNotEmpty() }?.sum()
            volumeKg?.let { add(Stat(formatWeightKg(it, miles), volumeLabel)) }
        }
        if (heroKind != HeroKind.DURATION) {
            duration?.let { add(Stat(DurationTag.formatTime(it), durationLabel)) }
        }
        if (heroKind != HeroKind.DISTANCE) {
            distance?.let { add(Stat("${it.value.trimmed()} ${it.unit}", distanceLabel)) }
        }
        if (duration != null && distance != null && distance.value > 0.0) {
            // Cycling is conventionally reported as speed; running/walking/etc. as pace.
            if (info.type == ExerciseType.CYCLING) {
                add(Stat(speed(duration, distance), speedLabel))
            } else {
                add(Stat("${paceMinPerUnit(duration, distance.value)} /${distance.unit}", paceLabel))
            }
        }
        info.elevationGain?.let { add(Stat("${it.value.trimmed()} ${it.unit}", elevationGainLabel)) }
        info.elevationLoss?.let { add(Stat("${it.value.trimmed()} ${it.unit}", elevationLossLabel)) }
        info.calories?.let { add(Stat("$it kcal", caloriesLabel)) }
        if (heroKind != HeroKind.STEPS) {
            info.steps?.let { add(Stat("$it", stepsLabel)) }
        }
        info.avgHeartRate?.let { add(Stat("$it bpm", heartRateLabel)) }
        info.maxHeartRate?.let { add(Stat("$it bpm", maxHeartRateLabel)) }
        info.sets?.let { add(Stat("$it", setsLabel)) }
        info.reps?.let { add(Stat("$it", repsLabel)) }
        info.weight?.let { add(Stat("${it.value.trimmed()} ${it.unit}", weightLabel)) }
    }
}

/** The headline metric, shown large above the secondary grid (e.g. `5.2 km`). */
@Composable
private fun WorkoutHero(
    value: String,
    unit: String?,
    label: String,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            unit?.let {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.placeholderText,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }
}

/** Fixed-column grid so secondary metrics line up in tidy columns instead of free-flowing. */
@Composable
private fun WorkoutStatsGrid(
    stats: List<Stat>,
    columns: Int = 3,
) {
    if (stats.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stats.chunked(columns).forEach { rowStats ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowStats.forEach { stat ->
                    WorkoutStat(stat.value, stat.label, Modifier.weight(1f))
                }
                // Pad the last row so columns stay aligned across rows.
                repeat(columns - rowStats.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Per-exercise breakdown for POWR / NIP-101e strength workouts: exercise name + its logged sets. */
@Composable
private fun ExerciseBreakdown(
    groups: List<ExerciseGroup>,
    miles: Boolean,
    accountViewModel: AccountViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { group ->
            ExerciseRow(group, miles, accountViewModel)
        }
    }
}

@Composable
private fun ExerciseRow(
    group: ExerciseGroup,
    miles: Boolean,
    accountViewModel: AccountViewModel,
) {
    val fallback = group.displayName() ?: "—"
    val address = remember(group.reference) { Address.parse(group.reference) }

    if (address == null) {
        ExerciseRowContent(fallback, group.summaryLine(miles))
        return
    }

    // Resolve the kind-33401 template to show its real title; falls back to the slug
    // until the template is fetched (the workout event's relay hints drive the fetch).
    LoadAddressableNote(address) { templateNote ->
        val name =
            if (templateNote != null) {
                val templateEvent by observeNoteEvent<ExerciseTemplateEvent>(templateNote, accountViewModel)
                templateEvent?.title() ?: fallback
            } else {
                fallback
            }
        ExerciseRowContent(name, group.summaryLine(miles))
    }
}

@Composable
private fun ExerciseRowContent(
    name: String,
    summary: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }
}

/** Small chip showing how the workout was recorded (e.g. GPS, RUNSTR, HEALTHKIT, MANUAL). */
@Composable
private fun WorkoutSourceBadge(source: String) {
    Text(
        text = source.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun WorkoutStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }
}
