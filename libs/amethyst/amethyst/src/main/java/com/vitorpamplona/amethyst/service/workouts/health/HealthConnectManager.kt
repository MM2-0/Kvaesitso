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

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.vitorpamplona.amethyst.commons.fitness.DetectedWorkout
import com.vitorpamplona.amethyst.commons.fitness.health.WorkoutMerger
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Reads finished workouts from Android Health Connect — the single aggregator
 * every Android health source funnels into (Samsung Health/Galaxy Watch,
 * Google Fit, Fitbit, Garmin Connect, Strava, …) — and maps each session to a
 * [DetectedWorkout] ready to become a NIP-101e kind 1301 event.
 *
 * Read-only. The caller decides when to request permission; this class never
 * prompts on its own.
 */
class HealthConnectManager(
    private val context: Context,
) {
    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    /** Writer package -> display label. See [resolveSourceName]. */
    private val sourceNames = mutableMapOf<String, String>()

    companion object {
        private const val TAG = "HealthConnectManager"

        /** How far back the New Workout carousel looks for workouts to offer. */
        const val LOOKBACK_DAYS = 7L

        /**
         * How many per-session metric aggregations may be in flight at once.
         *
         * Each is an independent binder transaction into the Health Connect provider, so running
         * them in series made the read scale with the window length. Capped rather than unbounded
         * because the provider serves them from a finite thread/transaction pool, and a four-week
         * window for a daily trainer would otherwise fire dozens at once.
         */
        private const val MAX_CONCURRENT_AGGREGATES = 6

        private const val DEFAULT_SOURCE = "Health Connect"

        /** Friendly names for well-known writers when their app isn't installed to read a label from. */
        private val KNOWN_SOURCES =
            mapOf(
                "com.sec.android.app.shealth" to "Samsung Health",
                "com.google.android.apps.fitness" to "Google Fit",
                "com.google.android.apps.healthdata" to "Health Connect",
                "com.fitbit.FitbitMobile" to "Fitbit",
                "com.garmin.android.apps.connectmobile" to "Garmin Connect",
                "com.strava" to "Strava",
                "com.nike.plusgps" to "Nike Run Club",
            )

        /** Read permissions needed to map a workout. */
        val PERMISSIONS =
            setOf(
                HealthPermission.getReadPermission(ExerciseSessionRecord::class),
                HealthPermission.getReadPermission(DistanceRecord::class),
                HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
                HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
                HealthPermission.getReadPermission(HeartRateRecord::class),
                HealthPermission.getReadPermission(StepsRecord::class),
                HealthPermission.getReadPermission(ElevationGainedRecord::class),
            )

        /** True when a Health Connect provider is installed and up to date on this device. */
        fun isAvailable(context: Context): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

        /** True when the device has no Health Connect provider at all (vs needing an update). */
        fun needsProviderUpdate(context: Context): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
    }

    suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

    suspend fun hasAllPermissions(): Boolean {
        // Some OEM builds report the provider as SDK_AVAILABLE yet fail to bind to the
        // Health Connect service (e.g. RemoteException "Binding to service failed" on
        // ITEL/low-end devices). Treat any such failure as "not granted" instead of
        // letting it crash the app — the carousel then quietly stays in its prompt state.
        val granted =
            try {
                grantedPermissions()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Failed to read Health Connect granted permissions", e)
                return false
            }
        val ok = granted.containsAll(PERMISSIONS)
        if (!ok) {
            Log.i(TAG) { "hasAllPermissions=false; missing=${PERMISSIONS - granted}" }
        } else {
            Log.i(TAG) { "hasAllPermissions=true (${granted.size} granted)" }
        }
        return ok
    }

    /**
     * One stage of a progressive Health Connect read. See [readWorkoutsProgressively].
     */
    data class WorkoutRead(
        val workouts: List<DetectedWorkout>,
        /**
         * True while the per-session aggregate metrics (distance, calories, heart rate, steps,
         * elevation) are still being fetched, so [workouts] currently carries nulls for them.
         */
        val metricsPending: Boolean,
    )

    /**
     * All exercise sessions that ended within [since]..[now], mapped to [DetectedWorkout], in two
     * stages.
     *
     * The session list itself is one IPC. Every optional metric — distance, calories, heart rate,
     * steps, elevation — needs a *separate* [aggregate] round trip per session, and a four-week
     * window for someone who trains daily is dozens of them. Waiting for all of that before
     * showing anything is what made the My Fitness dashboard sit on a spinner for seconds.
     *
     * So this emits twice:
     *  1. the sessions with what the read already knows — activity, title, start, duration,
     *     source — and [WorkoutRead.metricsPending] true;
     *  2. the same workouts with their metrics filled in, and the flag false.
     *
     * That first emission already carries everything the dashboard's counts, durations, streak,
     * active days and per-activity split need. `WorkoutStats` totals and bests treat an absent
     * metric as contributing nothing rather than zero, so the partial pass is honest rather than
     * wrong — a distance cell is missing until it is known, not shown as 0.
     *
     * A single-emission caller that wants the finished numbers takes [readWorkouts].
     *
     * Aggregation is per *raw* session and merging happens after it, exactly as a one-shot read
     * did: [WorkoutMerger] sums the members' metrics, so aggregating a merged span instead would
     * fold in the breaks between segments and change the totals.
     *
     * Emits a single empty result (never throws) if Health Connect is unavailable or the read
     * fails.
     */
    fun readWorkoutsProgressively(
        since: Instant,
        now: Instant = Instant.now(),
    ): Flow<WorkoutRead> =
        // flowOn(IO): callers collect from Dispatchers.Main, Health Connect's own calls suspend,
        // but the PackageManager lookup in resolveSourceName is a blocking binder call — so the
        // whole read moves off the UI thread rather than relying on each step to behave.
        flow {
            if (!isAvailable(context)) {
                Log.i(TAG) { "readWorkouts: Health Connect unavailable (status=${HealthConnectClient.getSdkStatus(context)})" }
                emit(WorkoutRead(emptyList(), metricsPending = false))
                return@flow
            }

            val sessions =
                try {
                    client
                        .readRecords(
                            ReadRecordsRequest(
                                recordType = ExerciseSessionRecord::class,
                                timeRangeFilter = TimeRangeFilter.between(since, now),
                            ),
                        ).records
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Failed to read workouts from Health Connect", e)
                    emit(WorkoutRead(emptyList(), metricsPending = false))
                    return@flow
                }

            Log.i(TAG) { "readWorkouts: ${sessions.size} exercise session(s) in window $since .. $now" }

            // Kept paired with their sessions: stage 2 aggregates over each raw session's own
            // time range, which the mapped workout no longer carries once merging rewrites it.
            val skeletons = sessions.mapNotNull { session -> mapSession(session)?.let { session to it } }

            if (skeletons.isEmpty()) {
                Log.i(TAG) { "readWorkouts: no mappable sessions" }
                emit(WorkoutRead(emptyList(), metricsPending = false))
                return@flow
            }

            emit(WorkoutRead(merge(skeletons.map { it.second }), metricsPending = true))

            // Bounded fan-out rather than one-at-a-time: these are independent IPCs into the
            // provider, and running them in series is what the window length multiplied. The
            // permit cap keeps a month of daily training from opening 60 concurrent binder
            // transactions at a provider that has a finite pool for them.
            val detailed =
                coroutineScope {
                    val gate = Semaphore(MAX_CONCURRENT_AGGREGATES)
                    skeletons
                        .map { (session, workout) ->
                            async { gate.withPermit { workout.withMetrics(aggregate(session)) } }
                        }.awaitAll()
                }

            val merged = merge(detailed)
            Log.i(TAG) { "readWorkouts: mapped ${detailed.size} -> ${merged.size} workout(s) after type/duration filtering and merging" }
            emit(WorkoutRead(merged, metricsPending = false))
        }.flowOn(Dispatchers.IO)

    /**
     * All exercise sessions in [since]..[now], with their metrics — the finished result of
     * [readWorkoutsProgressively]. For callers that have nothing useful to show from a partial
     * read and so gain nothing from the intermediate stage.
     */
    suspend fun readWorkouts(
        since: Instant,
        now: Instant = Instant.now(),
    ): List<DetectedWorkout> = readWorkoutsProgressively(since, now).last().workouts

    /**
     * Folds split-up sessions of the same activity (a long run broken around breaks) into one
     * workout so the composer offers the whole effort.
     */
    private fun merge(workouts: List<DetectedWorkout>) = WorkoutMerger.mergeCloseWorkouts(workouts)

    /** Copies [totals] — the result of one [aggregate] call — onto a session skeleton. */
    private fun DetectedWorkout.withMetrics(totals: AggregationResult?): DetectedWorkout {
        if (totals == null) return this

        return copy(
            distanceMeters = totals[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
            // Prefer active calories (what RUNSTR publishes); fall back to total for
            // sources that only record total energy. Total includes basal burn, so it
            // over-reports the workout if used as the primary figure.
            calories =
                (
                    totals[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                        ?: totals[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                )?.roundToInt(),
            avgHeartRate = totals[HeartRateRecord.BPM_AVG]?.toInt(),
            maxHeartRate = totals[HeartRateRecord.BPM_MAX]?.toInt(),
            steps = totals[StepsRecord.COUNT_TOTAL]?.toInt(),
            elevationGainMeters = totals[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters,
        )
    }

    /**
     * The session as the record itself describes it: activity, title, when, how long, who wrote
     * it. The optional metrics are left null for [withMetrics] to fill in — they each cost their
     * own IPC, so they are not part of mapping a session.
     *
     * Null when the activity type is one Amethyst cannot represent, or the session has no
     * positive duration.
     */
    private fun mapSession(session: ExerciseSessionRecord): DetectedWorkout? {
        val exercise = ExerciseTypeMapper.toExerciseType(session.exerciseType)
        if (exercise == null) {
            Log.i(TAG) {
                "Skipping session: unmapped exerciseType=${session.exerciseType} " +
                    "title='${session.title}' from ${session.metadata.dataOrigin.packageName}"
            }
            return null
        }

        val durationSeconds = Duration.between(session.startTime, session.endTime).seconds
        if (durationSeconds <= 0) {
            Log.i(TAG) { "Skipping session: non-positive duration ($durationSeconds s) for exerciseType=${session.exerciseType}" }
            return null
        }

        Log.i(TAG) {
            "Mapped session: exerciseType=${session.exerciseType} -> $exercise, ${durationSeconds}s, " +
                "from ${session.metadata.dataOrigin.packageName}"
        }

        return DetectedWorkout(
            id = session.metadata.id,
            exercise = exercise,
            title = session.title?.takeIf { it.isNotBlank() },
            startTimeEpochSeconds = session.startTime.epochSecond,
            durationSeconds = durationSeconds,
            distanceMeters = null,
            calories = null,
            avgHeartRate = null,
            maxHeartRate = null,
            steps = null,
            elevationGainMeters = null,
            source = resolveSourceName(session.metadata.dataOrigin.packageName),
        )
    }

    /**
     * Friendly name of the app/device that wrote the record. Resolves the
     * Health Connect data-origin package to the installed app's label
     * ("Samsung Health", "Google Fit", …); falls back to a known-package map,
     * then the raw package, then "Health Connect" when nothing is available.
     */
    private fun resolveSourceName(packageName: String): String {
        if (packageName.isBlank()) return DEFAULT_SOURCE
        // Memoized: getApplicationInfo is a blocking binder call, and a week of sessions
        // almost always comes from the same one or two writer apps, so an uncached lookup
        // pays for the same round trip once per session.
        sourceNames[packageName]?.let { return it }

        val resolved =
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull() ?: KNOWN_SOURCES[packageName] ?: packageName

        sourceNames[packageName] = resolved
        return resolved
    }

    /** Aggregates the optional metrics over the session window. Null if aggregation fails. */
    private suspend fun aggregate(session: ExerciseSessionRecord): AggregationResult? =
        try {
            client.aggregate(
                AggregateRequest(
                    metrics =
                        setOf(
                            DistanceRecord.DISTANCE_TOTAL,
                            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                            TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                            HeartRateRecord.BPM_AVG,
                            HeartRateRecord.BPM_MAX,
                            StepsRecord.COUNT_TOTAL,
                            ElevationGainedRecord.ELEVATION_GAINED_TOTAL,
                        ),
                    timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                ),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to aggregate workout metrics", e)
            null
        }
}
