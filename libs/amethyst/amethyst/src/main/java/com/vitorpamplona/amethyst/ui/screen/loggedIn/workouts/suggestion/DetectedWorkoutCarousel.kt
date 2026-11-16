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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.suggestion

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.fitness.DetectedWorkout
import com.vitorpamplona.amethyst.commons.fitness.TrainingLog
import com.vitorpamplona.amethyst.commons.fitness.WorkoutOrigin
import com.vitorpamplona.amethyst.commons.fitness.health.publishedWorkoutsOf
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.BooleanType
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.workout_from_health_connect
import com.vitorpamplona.amethyst.commons.resources.workout_suggestion_combined_sessions
import com.vitorpamplona.amethyst.commons.resources.workout_suggestion_connect_button
import com.vitorpamplona.amethyst.commons.resources.workout_suggestion_connect_details
import com.vitorpamplona.amethyst.commons.resources.workout_suggestion_connect_message
import com.vitorpamplona.amethyst.commons.resources.workout_suggestion_connect_title
import com.vitorpamplona.amethyst.commons.resources.workout_suggestion_distance_km
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.labelRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.symbol
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.workouts.health.HealthConnectManager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.health.HealthConnectRationaleActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Health Connect integration for the New Workout composer — the single place the
 * app touches Health Connect. Without permission it shows a Connect prompt; with
 * permission it shows a horizontal list of workouts from the last
 * [HealthConnectManager.LOOKBACK_DAYS] days, and tapping one pre-loads the form
 * via [onPick].
 *
 * Renders nothing when Health Connect is unavailable, the suggestion setting is
 * off, or (once granted) no workouts are found.
 */
@Composable
fun DetectedWorkoutCarousel(
    accountViewModel: AccountViewModel,
    onPick: (Route.NewWorkout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val available = remember { HealthConnectManager.isAvailable(context) }
    if (!available) return

    val enabled by accountViewModel.settings.uiSettingsFlow.suggestWorkoutsFromHealthConnect
        .collectAsStateWithLifecycle()
    if (enabled == BooleanType.NEVER) return

    val manager = remember { HealthConnectManager(context) }
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf<Boolean?>(null) }
    var workouts by remember { mutableStateOf<List<DetectedWorkout>>(emptyList()) }

    val myPubkey = accountViewModel.userProfile().pubkeyHex

    val reload: suspend () -> Unit = {
        val ok = manager.hasAllPermissions()
        granted = ok
        workouts =
            if (ok) {
                val since = Instant.now().minus(Duration.ofDays(HealthConnectManager.LOOKBACK_DAYS))
                // Offering a workout the user already shared would publish a second kind 1301 for
                // the same effort. merge() flags the Health Connect copies that match something
                // already posted, so drop those; what is left is genuinely unshared.
                TrainingLog
                    .merge(manager.readWorkouts(since), publishedWorkoutsOf(myPubkey).first())
                    .filter { it.origin == WorkoutOrigin.HEALTH_CONNECT && !it.alreadyPublished }
                    .sortedByDescending { it.startTimeEpochSeconds }
            } else {
                emptyList()
            }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            scope.launch { reload() }
        }

    LifecycleResumeEffect(Unit) {
        scope.launch { reload() }
        onPauseOrDispose {}
    }

    when (granted) {
        null -> return // not checked yet — render nothing so the prompt never flashes
        false ->
            ConnectCard(
                modifier = modifier,
                onDetails = { context.startActivity(Intent(context, HealthConnectRationaleActivity::class.java)) },
                onConnect = { permissionLauncher.launch(HealthConnectManager.PERMISSIONS) },
            )
        true -> {
            if (workouts.isEmpty()) return
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringRes(Res.string.workout_from_health_connect),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    items(workouts, key = { it.id }) { workout ->
                        val label = workout.title ?: stringRes(workout.exercise.labelRes())
                        WorkoutChip(
                            workout = workout,
                            label = label,
                            summary = summaryLine(workout),
                            onClick = { onPick(workout.toNewWorkoutRoute(label)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectCard(
    modifier: Modifier,
    onDetails: () -> Unit,
    onConnect: () -> Unit,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        symbol = MaterialSymbols.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringRes(Res.string.workout_suggestion_connect_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringRes(Res.string.workout_suggestion_connect_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The rationale is one tap away *before* the system dialog, not only from inside
            // Health Connect: the user should be able to read what Amethyst reads and why
            // before deciding to grant anything.
            TextButton(onClick = onDetails) {
                Text(stringRes(Res.string.workout_suggestion_connect_details))
            }
            Button(onClick = onConnect) {
                Text(stringRes(Res.string.workout_suggestion_connect_button))
            }
        }
    }
}

@Composable
private fun summaryLine(workout: DetectedWorkout): String {
    val parts = mutableListOf<String>()
    workout.distanceMeters?.takeIf { it > 0 }?.let {
        parts.add(stringRes(Res.string.workout_suggestion_distance_km, "%.2f".format(it / 1000.0)))
    }
    parts.add(formatWorkoutDuration(workout.durationSeconds))
    if (workout.sessionCount > 1) {
        val sessions = workout.sessionCount
        parts.add(pluralStringRes(Res.plurals.workout_suggestion_combined_sessions, sessions, sessions))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun WorkoutChip(
    workout: DetectedWorkout,
    label: String,
    summary: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.width(150.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            symbol = workout.exercise.symbol(),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = workoutRelativeTime(workout.startTimeEpochSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
