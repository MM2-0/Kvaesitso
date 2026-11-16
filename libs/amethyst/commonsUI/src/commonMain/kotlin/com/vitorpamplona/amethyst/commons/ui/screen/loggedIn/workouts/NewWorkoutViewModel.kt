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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.util.phonePrefersMiles
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.avgHeartRate
import com.vitorpamplona.quartz.experimental.fitness.workout.calories
import com.vitorpamplona.quartz.experimental.fitness.workout.distance
import com.vitorpamplona.quartz.experimental.fitness.workout.elevationGain
import com.vitorpamplona.quartz.experimental.fitness.workout.maxHeartRate
import com.vitorpamplona.quartz.experimental.fitness.workout.source
import com.vitorpamplona.quartz.experimental.fitness.workout.steps
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.DistanceTag
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.SourceTag
import com.vitorpamplona.quartz.experimental.fitness.workout.workoutStartTime
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlin.math.roundToInt

@Stable
class NewWorkoutViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    var exercise by mutableStateOf(ExerciseType.RUNNING)
    var title by mutableStateOf("")
    var hours by mutableStateOf("")
    var minutes by mutableStateOf("")
    var seconds by mutableStateOf("")
    var distance by mutableStateOf("")
    var distanceUnit by mutableStateOf(if (phonePrefersMiles()) DistanceTag.MILES else DistanceTag.KILOMETERS)
    var calories by mutableStateOf("")
    var notes by mutableStateOf("")

    // Source plus extra metrics carried from a detected workout. These are not
    // editable in the simple form but are published when present (> 0).
    private var source = SourceTag.MANUAL
    private var avgHeartRate = 0
    private var maxHeartRate = 0
    private var steps = 0
    private var elevationGainMeters = 0.0
    private var workoutStartTime = 0L

    private var hasPrefilled = false

    fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
        this.account = accountVM.account
    }

    /**
     * Pre-fills the form from a [Route.NewWorkout] (e.g. a Health Connect
     * detection) the first time only, so user edits are not overwritten on
     * recomposition; a blank route leaves the empty manual form untouched.
     */
    fun prefill(route: Route.NewWorkout) {
        if (hasPrefilled) return
        hasPrefilled = true
        applyPrefill(route)
    }

    /**
     * Unconditionally fills the form from [route]. Used when the user taps a
     * workout in the New Workout carousel and expects the fields to switch to
     * that workout, overwriting whatever was there.
     */
    fun applyPrefill(route: Route.NewWorkout) {
        route.exercise?.let { ExerciseType.parse(it) }?.let { exercise = it }
        title = route.title ?: ""

        // Every metric is assigned on both branches. Picking a second suggestion has to
        // REPLACE the form, not merge into it: a gym session carries no distance, so
        // leaving the previous run's 5 km in the field would publish a workout that
        // never happened.
        if (route.durationSeconds > 0) {
            hours = (route.durationSeconds / 3600).toString()
            minutes = ((route.durationSeconds % 3600) / 60).toString()
            seconds = (route.durationSeconds % 60).toString()
        } else {
            hours = ""
            minutes = ""
            seconds = ""
        }
        if (route.distanceMeters > 0) {
            val miles = phonePrefersMiles()
            distanceUnit = if (miles) DistanceTag.MILES else DistanceTag.KILOMETERS
            val value = if (miles) route.distanceMeters / DistanceTag.METERS_PER_MILE else route.distanceMeters / 1000.0
            distance = ((value * 100).roundToInt() / 100.0).toString()
        } else {
            distance = ""
        }
        calories = if (route.calories > 0) route.calories.toString() else ""

        route.source?.let { source = it }
        avgHeartRate = route.avgHeartRate
        maxHeartRate = route.maxHeartRate
        steps = route.steps
        elevationGainMeters = route.elevationGainMeters
        workoutStartTime = route.startTime
    }

    fun durationSeconds(): Long =
        (hours.toLongOrNull() ?: 0L) * 3600 +
            (minutes.toLongOrNull() ?: 0L) * 60 +
            (seconds.toLongOrNull() ?: 0L)

    fun canPost(): Boolean = durationSeconds() > 0

    fun cancel() {
        exercise = ExerciseType.RUNNING
        title = ""
        hours = ""
        minutes = ""
        seconds = ""
        distance = ""
        distanceUnit = if (phonePrefersMiles()) DistanceTag.MILES else DistanceTag.KILOMETERS
        calories = ""
        notes = ""
        source = SourceTag.MANUAL
        avgHeartRate = 0
        maxHeartRate = 0
        steps = 0
        elevationGainMeters = 0.0
        workoutStartTime = 0L
        hasPrefilled = false
    }

    suspend fun sendPostSync() {
        val template = createTemplate() ?: return
        onUiThread { cancel() }
        account.signAndComputeBroadcast(template)
    }

    private fun createTemplate(): EventTemplate<WorkoutRecordEvent>? {
        val durationSecs = durationSeconds()
        if (durationSecs <= 0) return null

        val distanceValue = distance.toDoubleOrNull()
        val kcal = calories.toIntOrNull()

        return WorkoutRecordEvent.build(
            exercise = exercise,
            durationSeconds = durationSecs,
            notes = notes,
            title = title.ifBlank { null },
        ) {
            source(source)
            distanceValue?.let { distance(it, distanceUnit) }
            kcal?.let { calories(it) }
            if (avgHeartRate > 0) avgHeartRate(avgHeartRate)
            if (maxHeartRate > 0) maxHeartRate(maxHeartRate)
            if (steps > 0) steps(steps)
            if (elevationGainMeters > 0) elevationGain(elevationGainMeters)
            if (workoutStartTime > 0) workoutStartTime(workoutStartTime)
        }
    }
}
