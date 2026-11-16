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

import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs

/**
 * The user's training log: everything they have done, from both places Amethyst can learn about
 * it.
 *
 * Health Connect knows what their watch recorded. It does not know about a workout they typed
 * into Amethyst by hand, or one they posted from another NIP-101e client, and Amethyst only reads
 * its last four weeks (see [WorkoutStats.WINDOW_DAYS]). Their own kind 1301 events know all of those and none of the
 * device detail. Neither source alone is the truth, so the dashboard reads both.
 *
 * The practical consequence is that My Fitness works with no health permissions at all — a user
 * who never connects Health Connect still gets a summary of what they have logged.
 */
object TrainingLog {
    /**
     * How far apart two records of the same activity can start and still be judged the same
     * workout. Generous on purpose: a published event often carries only the minute the user
     * typed, or the time the post went out, while Health Connect has the exact second.
     */
    const val DEDUPE_TOLERANCE_SECONDS = 900L

    /**
     * Combines both sources into one log, newest first.
     *
     * Where the same workout appears in both — the usual case once a user shares one that came
     * from their watch — the Health Connect copy normally wins: it carries the metrics the
     * published event may have dropped (heart rate, steps, climb), and its start time is the
     * recorded one rather than a publish timestamp.
     *
     * That preference is only justified while the Health Connect copy actually has those metrics.
     * It can arrive without them — its per-session aggregations are read separately and may not
     * have come back yet, or may have failed — and then it is strictly worse than the published
     * event it would displace. So a copy carrying no metric at all yields to a matching one that
     * does, rather than evicting it and taking numbers off the screen. See
     * [DetectedWorkout.hasAnyMetric].
     *
     * The winner keeps the loser's one piece of information: that a kind 1301 for this workout
     * exists. Dropping the published copy would otherwise lose that fact, and the survivor —
     * still flagged as Health Connect data — would read as never posted. See
     * [DetectedWorkout.alreadyPublished].
     */
    fun merge(
        healthConnect: List<DetectedWorkout>,
        published: List<DetectedWorkout>,
    ): List<DetectedWorkout> {
        val merged = ArrayList<DetectedWorkout>(healthConnect.size + published.size)
        // The published copies that won their tie and are therefore already in [merged].
        // Identity, not equality: two published workouts can be equal in every field, and one
        // winning must not silently exclude the other from the pass below.
        val kept = Collections.newSetFromMap(IdentityHashMap<DetectedWorkout, Boolean>())

        healthConnect.forEach { recorded ->
            val match = published.firstOrNull { candidate -> recorded.isProbablySameWorkoutAs(candidate) }

            when {
                match == null -> merged.add(recorded)
                recorded.hasAnyMetric || !match.hasAnyMetric -> merged.add(recorded.copy(alreadyPublished = true))
                else -> {
                    // The published event is flagged as published by construction, so no copy.
                    merged.add(match)
                    kept.add(match)
                }
            }
        }

        // A published workout that any Health Connect copy matched is already represented by
        // whichever of the two won — including the ones just added above.
        published.forEach { candidate ->
            if (candidate !in kept && healthConnect.none { it.isProbablySameWorkoutAs(candidate) }) merged.add(candidate)
        }

        return merged.sortedByDescending { it.startTimeEpochSeconds }
    }

    private fun DetectedWorkout.isProbablySameWorkoutAs(other: DetectedWorkout): Boolean =
        exercise == other.exercise &&
            abs(startTimeEpochSeconds - other.startTimeEpochSeconds) <= DEDUPE_TOLERANCE_SECONDS
}

/**
 * Reads a published workout back into the training log, or null when it carries nothing a
 * summary can use — no recognisable activity, or no duration.
 *
 * Start time prefers the explicit `start` tag and falls back to the event's own `created_at`.
 * That fallback is the publish time, not the workout time: a manual entry has no better signal,
 * and for day-level bucketing (streaks, active days, the week split) it is close enough, since
 * people post a workout the same day they do it.
 */
fun WorkoutRecordEvent.toDetectedWorkout(): DetectedWorkout? {
    val activity = activityType() ?: return null

    // effectiveDurationSeconds, not durationSeconds: a POWR-dialect record carries `start`/`end`
    // and no `duration` tag, so the raw accessor returns null and the workout would vanish from
    // the log entirely. WorkoutDisplay already reads it through the same helper.
    val duration = effectiveDurationSeconds() ?: return null
    if (duration <= 0) return null

    return DetectedWorkout(
        id = id,
        exercise = activity,
        title = title()?.takeIf { it.isNotBlank() },
        startTimeEpochSeconds = workoutStartTime() ?: workoutStart() ?: createdAt,
        durationSeconds = duration,
        distanceMeters = distance()?.toMeters()?.takeIf { it > 0 },
        calories = calories()?.takeIf { it > 0 },
        avgHeartRate = avgHeartRate()?.takeIf { it > 0 },
        maxHeartRate = maxHeartRate()?.takeIf { it > 0 },
        steps = steps()?.takeIf { it > 0 },
        elevationGainMeters = elevationGain()?.toMeters()?.takeIf { it > 0 },
        source = workoutSource() ?: "",
        origin = WorkoutOrigin.PUBLISHED,
        // It came back from a relay, so by definition it is already out there.
        alreadyPublished = true,
    )
}
