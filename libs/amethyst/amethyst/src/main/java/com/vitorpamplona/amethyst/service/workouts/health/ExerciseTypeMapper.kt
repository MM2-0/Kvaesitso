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
package com.vitorpamplona.amethyst.service.workouts.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.vitorpamplona.quartz.experimental.fitness.workout.tags.ExerciseType

/**
 * Maps a Health Connect [ExerciseSessionRecord.exerciseType] integer to the
 * NIP-101e [ExerciseType] verb. Returns null for activity types Amethyst's
 * workout vocabulary cannot represent, so those sessions are skipped rather
 * than mislabeled.
 */
object ExerciseTypeMapper {
    fun toExerciseType(healthConnectType: Int): ExerciseType? =
        when (healthConnectType) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
            -> ExerciseType.RUNNING

            ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            -> ExerciseType.WALKING

            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
            -> ExerciseType.CYCLING

            ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
            -> ExerciseType.HIKING

            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
            -> ExerciseType.SWIMMING

            ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
            -> ExerciseType.ROWING

            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
            -> ExerciseType.STRENGTH

            ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
            -> ExerciseType.YOGA

            else -> null
        }
}
