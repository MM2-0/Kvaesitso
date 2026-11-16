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

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.my_fitness_connect_banner
import com.vitorpamplona.amethyst.commons.resources.my_fitness_connect_button
import com.vitorpamplona.amethyst.commons.resources.my_fitness_connect_message
import com.vitorpamplona.amethyst.commons.resources.my_fitness_connect_title
import com.vitorpamplona.amethyst.commons.resources.my_fitness_empty_history
import com.vitorpamplona.amethyst.commons.resources.my_fitness_loading_metrics
import com.vitorpamplona.amethyst.commons.resources.my_fitness_title
import com.vitorpamplona.amethyst.commons.resources.workout_suggestion_connect_details
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.datasource.MyWorkoutsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.fitness.FitnessDashboard
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.fitness.FitnessGoalsDialog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.workouts.health.HealthConnectManager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.health.HealthConnectRationaleActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.suggestion.toNewWorkoutRoute

/**
 * The user's own training, summarised: how much they did this week against last, what they
 * spent the time on, their best efforts, and how many days in a row they have shown up.
 *
 * This is the reason Amethyst reads Health Connect at all — the numbers are for the person who
 * recorded them. Publishing one as a note is an optional action from the workout list, never a
 * precondition for seeing any of this.
 *
 * Reached from the drawer, or from a bottom-bar slot the user pinned.
 */
private val DashboardPadding = PaddingValues(16.dp)

@Composable
fun MyFitnessScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: MyFitnessViewModel = viewModel()
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Which account's workouts to summarise. Re-runs on an account switch, which resets the
    // dashboard to Loading rather than showing the previous user's numbers.
    viewModel.init(accountViewModel.userProfile().pubkeyHex, context)

    // The published side reads the cache; this fetches the user's own workouts into it.
    MyWorkoutsFilterAssemblerSubscription(accountViewModel)

    val permissionLauncher =
        rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            viewModel.refresh(context)
        }

    // Re-checks permissions as well as data, so revoking access in Health Connect drops the
    // screen back to its prompt instead of leaving stale numbers up.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh(context)
        onPauseOrDispose {}
    }

    val openRationale = { context.startActivity(Intent(context, HealthConnectRationaleActivity::class.java)) }
    val requestPermissions = { permissionLauncher.launch(HealthConnectManager.PERMISSIONS) }

    // DisappearingScaffold + AppBottomBar, like every other pinnable destination: My Fitness can
    // be pinned to the bottom bar, and a bare Scaffold would make that bar vanish on arrival.
    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = { TopBarWithBackButton(stringRes(Res.string.my_fitness_title), nav) },
        bottomBar = {
            AppBottomBar(Route.MyFitness, nav, accountViewModel) { route ->
                if (route != Route.MyFitness) nav.navBottomBar(route)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        // The Surface fills the whole scaffold, under the bars, and the dashboard pads inside its
        // scroll: padding the Surface instead left the bars' strips empty once they slid away.
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val current = state) {
                MyFitnessViewModel.State.Loading -> CenteredBox { CircularProgressIndicator() }

                is MyFitnessViewModel.State.Ready ->
                    if (current.insights.isEmpty) {
                        // Nothing logged yet. Offering Health Connect is the useful thing to do
                        // when it could fill the screen; otherwise just say the log is empty.
                        if (current.healthConnect == MyFitnessViewModel.HealthConnectStatus.AVAILABLE) {
                            ConnectPrompt(onDetails = openRationale, onConnect = requestPermissions)
                        } else {
                            CenteredBox {
                                Text(
                                    text = stringRes(Res.string.my_fitness_empty_history),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 32.dp),
                                )
                            }
                        }
                    } else {
                        var editingGoals by remember { mutableStateOf(false) }
                        val metricsPending = current.metricsPending

                        FitnessDashboard(
                            insights = current.insights,
                            onEditGoals = { editingGoals = true },
                            // Only what is still unpublished can be shared: offering to share a
                            // workout that is already posted would publish a second kind 1301 for
                            // the same effort. Nor while the metrics are still loading — the
                            // composer route carries them as primitives where 0 means absent, so
                            // sharing then would post a kind 1301 with its duration and nothing
                            // else, and the workout would count as shared from then on.
                            canShare = { !it.alreadyPublished && !metricsPending },
                            onShare = { workout, label -> nav.nav(workout.toNewWorkoutRoute(label)) },
                            showDeviceHorizon = current.healthConnect == MyFitnessViewModel.HealthConnectStatus.CONNECTED,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(rememberFeedContentPadding(DashboardPadding)),
                        ) {
                            // Only offered when it would actually add something: a device with no
                            // provider gets no banner to act on.
                            if (current.healthConnect == MyFitnessViewModel.HealthConnectStatus.AVAILABLE) {
                                ConnectBanner(onDetails = openRationale, onConnect = requestPermissions)
                            }
                            if (metricsPending) MetricsPendingNote()
                        }

                        if (editingGoals) {
                            FitnessGoalsDialog(
                                goals = current.insights.goals,
                                onDismiss = { editingGoals = false },
                                onSave = {
                                    viewModel.updateGoals(it)
                                    editingGoals = false
                                },
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(LocalDisappearingScaffoldPadding.current),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Shown above a dashboard that is already working, when Health Connect could add device-recorded
 * workouts to it. Deliberately a strip rather than a blocking card: the summary below it is real,
 * and this only offers to make it richer.
 */
@Composable
private fun ConnectBanner(
    onDetails: () -> Unit,
    onConnect: () -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringRes(Res.string.my_fitness_connect_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringRes(Res.string.my_fitness_connect_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDetails) { Text(stringRes(Res.string.workout_suggestion_connect_details)) }
                TextButton(onClick = onConnect) { Text(stringRes(Res.string.my_fitness_connect_button)) }
            }
        }
    }
}

/**
 * Shown while the per-session metrics are still being read from Health Connect. The dashboard
 * below it is already real — counts, time, streak, the activity split — but its distance,
 * calories and heart rate cells appear as each session's metrics land, and a row of numbers
 * growing on its own needs saying out loud.
 */
@Composable
private fun MetricsPendingNote() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringRes(Res.string.my_fitness_loading_metrics),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectPrompt(
    onDetails: () -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(LocalDisappearingScaffoldPadding.current).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    symbol = MaterialSymbols.DirectionsRun,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(stringRes(Res.string.my_fitness_connect_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringRes(Res.string.my_fitness_connect_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDetails) { Text(stringRes(Res.string.workout_suggestion_connect_details)) }
            Button(onClick = onConnect) { Text(stringRes(Res.string.my_fitness_connect_button)) }
        }
    }
}
