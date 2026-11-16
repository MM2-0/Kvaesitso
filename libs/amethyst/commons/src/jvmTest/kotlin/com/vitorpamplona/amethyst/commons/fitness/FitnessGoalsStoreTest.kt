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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FitnessGoalsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun open(
        file: File,
        scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { file.toOkioPath() })

    /** A new account sees the defaults, not an error or a blank dashboard. */
    @Test
    fun anEmptyStoreReadsTheDefaults() =
        runTest {
            val store = FitnessGoalsStore(open(File(folder.root, "a.preferences_pb"), CoroutineScope(Dispatchers.IO + SupervisorJob())))
            assertEquals(FitnessGoals.DEFAULT, store.load())
        }

    /** Goals saved in one session are what the next session (a new DataStore on the same file) reads. */
    @Test
    fun savedGoalsSurviveReopeningTheStore() =
        runTest {
            val file = File(folder.root, "b.preferences_pb")
            val goals =
                FitnessGoals(
                    weeklyActiveMinutes = 180,
                    weeklyWorkouts = 4,
                    weeklyActiveDays = 4,
                    distance = FitnessGoals.DistanceGoal(ExerciseType.RUNNING, 25_000.0),
                )

            val first = CoroutineScope(Dispatchers.IO + SupervisorJob())
            FitnessGoalsStore(open(file, first)).save(goals)
            // DataStore holds the path until the owning scope completes; release it before reopening.
            first.cancel()
            first.coroutineContext.job.join()

            val reopened = FitnessGoalsStore(open(file, CoroutineScope(Dispatchers.IO + SupervisorJob())))
            assertEquals(goals, reopened.load())
        }
}
