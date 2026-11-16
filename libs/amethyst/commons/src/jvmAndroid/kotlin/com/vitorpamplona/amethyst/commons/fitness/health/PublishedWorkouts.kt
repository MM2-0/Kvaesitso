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
package com.vitorpamplona.amethyst.commons.fitness.health

import com.vitorpamplona.amethyst.commons.fitness.DetectedWorkout
import com.vitorpamplona.amethyst.commons.fitness.toDetectedWorkout
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The workouts [pubkeyHex] has published, as a live view of the cache.
 *
 * Shared by the My Fitness dashboard, which counts them, and the New Workout carousel, which uses
 * them to avoid offering a workout the user already shared. Both need the same view of "what have
 * I already posted", and two answers to that question would mean the carousel offering something
 * the dashboard knows is a duplicate.
 *
 * [LocalCache.observeEvents] rather than a scan: it is indexed by kind and author, so it neither
 * walks every note in the cache nor needs re-running on a timer — the dashboard updates itself
 * when a relay delivers a workout, including the one the user just published.
 *
 * Reports only what the cache holds; it issues no REQ of its own.
 */
fun publishedWorkoutsOf(pubkeyHex: String): Flow<List<DetectedWorkout>> =
    LocalCache
        .observeEvents<WorkoutRecordEvent>(
            Filter(kinds = listOf(WorkoutRecordEvent.KIND), authors = listOf(pubkeyHex)),
        ).map { events -> events.mapNotNull { it.toDetectedWorkout() } }
