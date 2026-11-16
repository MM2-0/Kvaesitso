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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.fitness

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.fitness.DetectedWorkout
import com.vitorpamplona.amethyst.commons.fitness.FitnessGoals
import com.vitorpamplona.amethyst.commons.fitness.FitnessInsights
import com.vitorpamplona.amethyst.commons.fitness.TrainingLog
import com.vitorpamplona.amethyst.commons.fitness.WorkoutStats
import com.vitorpamplona.amethyst.commons.fitness.health.publishedWorkoutsOf
import com.vitorpamplona.amethyst.service.workouts.health.HealthConnectManager
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * State holder for the My Fitness dashboard: the user's own training against their weekly
 * [FitnessGoals], over up to [FitnessInsights.HISTORY_WEEKS] of history, summarised by
 * [FitnessInsights].
 *
 * The log is built from both sources Amethyst has — Health Connect, and the user's own published
 * kind 1301 events (see [TrainingLog]) — so the dashboard is useful before any health permission
 * is granted and stays useful if one is revoked. Health Connect adds device detail; it is not a
 * precondition.
 *
 * Nothing here publishes or touches the network. The dashboard is the user looking at their own
 * numbers; sharing one is a separate, deliberate action from the workout list.
 */
@Stable
class MyFitnessViewModel : ViewModel() {
    /** Whether the richer Health Connect source is switched on, and whether it even could be. */
    enum class HealthConnectStatus {
        /** Granted and contributing to the log. */
        CONNECTED,

        /** A provider is installed but Amethyst has no permissions — worth offering. */
        AVAILABLE,

        /** No provider on this device; there is nothing to offer. */
        UNAVAILABLE,
    }

    @Immutable
    sealed interface State {
        /** First load, before Health Connect has been checked. */
        data object Loading : State

        /**
         * Summarised. [FitnessInsights.Insights.isEmpty] covers "nothing logged yet", which is a
         * normal state rather than an error — a new user has published nothing and may not have
         * connected Health Connect.
         */
        data class Ready(
            val insights: FitnessInsights.Insights,
            val healthConnect: HealthConnectStatus,
            /**
             * True while Health Connect's per-session metrics are still arriving. The report is
             * real and complete in every other respect — counts, time, streak, active days, the
             * per-activity split — but distance, calories, heart rate, steps and elevation are
             * still filling in, so the screen says so rather than letting cells appear unexplained.
             */
            val metricsPending: Boolean = false,
        ) : State
    }

    private val pubkeyHex = MutableStateFlow<String?>(null)

    /**
     * Health Connect's contribution. A push source: the platform has no change feed we can
     * observe, so [refresh] re-reads it when the screen resumes or a permission is granted.
     */
    private val fromHealthConnect = MutableStateFlow(HealthConnectContribution())

    /** The in-flight [refresh], cancelled by the next one so two reads never interleave. */
    private var refreshJob: Job? = null

    /** Null until the first [refresh] resolves, which is what keeps the screen on [State.Loading]. */
    private val healthConnectStatus = MutableStateFlow<HealthConnectStatus?>(null)

    private var manager: HealthConnectManager? = null

    /**
     * The user's weekly goals, private to this device. Null until read, which — like
     * [healthConnectStatus] — keeps the screen on [State.Loading] so a saved goal never flashes
     * the default first.
     */
    private val goals = MutableStateFlow<FitnessGoals?>(null)

    // The account's goals store, from its own preference DataStore (see FitnessGoalsStore).
    private fun goalsStore(pubkeyHex: String) = LocalPreferences.fitnessGoalsStore(pubkeyHex.hexToByteArray().toNpub())

    /**
     * The user's published workouts, live. Re-subscribes on an account switch; a workout arriving
     * from a relay — or the one the user just posted — lands here without a refresh.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val fromRelays: Flow<List<DetectedWorkout>> =
        pubkeyHex.flatMapLatest { me ->
            if (me == null) flowOf(emptyList()) else publishedWorkoutsOf(me)
        }

    val state: StateFlow<State> =
        combine(fromHealthConnect, fromRelays, healthConnectStatus, goals) { healthConnect, published, status, goals ->
            // The status check is cheap; reading the workouts is not. Waiting only on the former
            // is what lets a user's published log render while their watch data is still coming.
            if (status == null || goals == null) return@combine State.Loading

            val now = Instant.now()
            // Health Connect is read for WINDOW_DAYS only (see WorkoutStats); the user's own kind 1301s reach as far as
            // the history goes, which is what gives the trend, heatmap and patterns their depth.
            val since = now.minus(Duration.ofDays(FitnessInsights.HISTORY_WEEKS * 7L + 7)).epochSecond

            val insights =
                FitnessInsights.build(
                    workouts = TrainingLog.merge(healthConnect.workouts, published.filter { it.startTimeEpochSeconds >= since }),
                    goals = goals,
                    now = now,
                    firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
                )

            // Nothing to show *yet* is not the same as nothing logged. Going Ready here would
            // flash the empty state — or the connect prompt — at a user whose sessions are one
            // IPC away, so an empty report keeps waiting while the session list is in flight.
            if (insights.isEmpty && healthConnect.sessionsPending) return@combine State.Loading

            State.Ready(
                insights = insights,
                healthConnect = status,
                metricsPending = healthConnect.metricsPending,
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State.Loading)

    /** The account whose workouts this dashboard summarises. */
    fun init(
        pubkeyHex: String,
        context: Context,
    ) {
        if (this.pubkeyHex.value == pubkeyHex) return
        this.pubkeyHex.value = pubkeyHex
        // Another account's goals must not stay up while this one's are read.
        goals.value = null
        viewModelScope.launch { loadGoals() }
    }

    /** Saves new weekly goals for this account; the dashboard re-derives from them at once. */
    fun updateGoals(newGoals: FitnessGoals) {
        val me = pubkeyHex.value ?: return
        goals.value = newGoals
        viewModelScope.launch(Dispatchers.IO) { goalsStore(me).save(newGoals) }
    }

    /**
     * Re-reads Health Connect. Safe to call on every resume: it re-checks permissions as well as
     * data, so revoking access drops those workouts out of the log rather than leaving stale
     * numbers on display. The published side needs no refresh — it is observed.
     */
    fun refresh(context: Context) {
        // A resume while the previous read is still running would leave two collectors writing
        // fromHealthConnect, and the slower one could land a stale list last.
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                loadGoals()
                val status = healthConnectStatus(context)
                val hc = manager

                // Read through the local rather than the field: nothing may set sessionsPending
                // without a reader that will clear it again, or an empty dashboard waits forever.
                if (status != HealthConnectStatus.CONNECTED || hc == null) {
                    fromHealthConnect.value = HealthConnectContribution()
                    healthConnectStatus.value = status
                    return@launch
                }

                // Published before the read, not after: the status is what the screen is gated on,
                // and it is now known. The workouts arrive into an already-rendered dashboard.
                // The previous read's workouts stay up meanwhile, so a resume re-reads in place
                // rather than blanking a dashboard that is already correct.
                fromHealthConnect.value = fromHealthConnect.value.copy(sessionsPending = true)
                healthConnectStatus.value = status

                val now = Instant.now()
                hc
                    .readWorkoutsProgressively(now.minus(Duration.ofDays(WorkoutStats.WINDOW_DAYS)), now)
                    .collect { read -> fromHealthConnect.value = fromHealthConnect.value.after(read) }
            }
    }

    private suspend fun loadGoals() {
        val me = pubkeyHex.value ?: return
        if (goals.value != null) return
        val loaded =
            withContext(Dispatchers.IO) {
                goalsStore(me).load()
            }
        // An account switch while reading: the newer read owns the slot.
        if (pubkeyHex.value == me && goals.value == null) goals.value = loaded
    }

    /**
     * Both calls here are binder round trips — a PackageManager query, and a bind to the Health
     * Connect service that [HealthConnectManager] makes lazily on first use — so they run off the
     * main thread. [viewModelScope] is `Dispatchers.Main.immediate`, which would otherwise stall
     * the frame that opens the screen.
     */
    private suspend fun healthConnectStatus(context: Context): HealthConnectStatus =
        withContext(Dispatchers.IO) {
            if (!HealthConnectManager.isAvailable(context)) return@withContext HealthConnectStatus.UNAVAILABLE

            val hc = manager ?: HealthConnectManager(context.applicationContext).also { manager = it }
            if (hc.hasAllPermissions()) HealthConnectStatus.CONNECTED else HealthConnectStatus.AVAILABLE
        }
}

/**
 * Health Connect's contribution to the dashboard, and how far along reading it is.
 *
 * [sessionsPending] and [metricsPending] are the two stages of
 * [HealthConnectManager.readWorkoutsProgressively]: the session list costs one IPC, the metrics
 * cost one per session. They are tracked separately because they mean different things to the
 * screen — an empty dashboard must not say "nothing logged yet" while the sessions are still
 * coming, but it can show real counts and times while the metrics are.
 */
data class HealthConnectContribution(
    val workouts: List<DetectedWorkout> = emptyList(),
    val sessionsPending: Boolean = false,
    val metricsPending: Boolean = false,
) {
    /**
     * Folds one stage of a read in — and declines the ones that would take information off a
     * screen that already has it.
     *
     * A stage-1 result carries no metrics. On a first load that is exactly what makes the
     * dashboard appear in one IPC instead of dozens. On a re-read of an already-populated
     * dashboard — [MyFitnessViewModel.refresh] runs on every resume — publishing it would strip
     * the distance, calories, heart-rate, steps and elevation cells and most of Best Efforts for
     * as long as stage 2 takes, then put them back. So a partial stage only reaches the screen
     * when there is nothing better on it already; otherwise the previous read's numbers stay up,
     * correct and unannotated, and stage 2 swaps them atomically.
     */
    fun after(read: HealthConnectManager.WorkoutRead): HealthConnectContribution =
        if (read.metricsPending && workouts.isNotEmpty()) {
            copy(sessionsPending = false)
        } else {
            HealthConnectContribution(
                workouts = read.workouts,
                sessionsPending = false,
                metricsPending = read.metricsPending,
            )
        }
}
