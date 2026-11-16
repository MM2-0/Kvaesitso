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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.devtools.RelayProbePhase
import com.vitorpamplona.amethyst.commons.devtools.RelayProbeResult
import com.vitorpamplona.amethyst.commons.devtools.RelayProbeStatus
import com.vitorpamplona.amethyst.commons.devtools.RelayProbeViewModel
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.devtools_event_finder_title
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_discovering
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_event_explainer
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_found_only
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_include_discovered
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_newest
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_none_found
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_progress
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_start
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_stop
import com.vitorpamplona.amethyst.commons.resources.devtools_status_auth
import com.vitorpamplona.amethyst.commons.resources.devtools_status_empty
import com.vitorpamplona.amethyst.commons.resources.devtools_status_found
import com.vitorpamplona.amethyst.commons.resources.devtools_status_offline
import com.vitorpamplona.amethyst.commons.resources.devtools_status_querying
import com.vitorpamplona.amethyst.commons.resources.devtools_status_refused
import com.vitorpamplona.amethyst.commons.resources.devtools_status_timeout
import com.vitorpamplona.amethyst.commons.resources.devtools_status_tor
import com.vitorpamplona.amethyst.commons.resources.devtools_status_waiting
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.toPrettyJson
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import org.jetbrains.compose.resources.StringResource

/** "Where is my event": which relays serve [eventId] right now. */
@Composable
fun EventFinderScreen(
    eventId: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel =
        rememberViewModel(key = "EventFinder-$eventId") {
            RelayProbeViewModel.forEvent(eventId, devToolsSeedRelays(accountViewModel), accountViewModel::buildCrawlClient, accountViewModel.host.websocketBuilder::canConnect)
        }
    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.devtools_event_finder_title), nav) },
    ) { padding ->
        RelayProbeList(padding, Res.string.devtools_probe_event_explainer, viewModel, nav)
    }
}

internal fun devToolsSeedRelays(accountViewModel: AccountViewModel) =
    RelayProbeViewModel.defaultSeeds(
        accountViewModel.account.client
            .availableRelaysFlow()
            .value,
    )

/** The event as found, then the crawl controls, then one row per relay. */
@Composable
private fun RelayProbeList(
    padding: PaddingValues,
    explainer: StringResource,
    viewModel: RelayProbeViewModel,
    nav: INav,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var includeDiscovered by rememberSaveable { mutableStateOf(true) }
    var foundOnly by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        if (viewModel.state.value.phase == RelayProbePhase.IDLE) viewModel.start(includeDiscovered)
    }

    val rows =
        remember(state.results, foundOnly) {
            state.results.values
                .filter { !foundOnly || it.status == RelayProbeStatus.FOUND }
                .sortedWith(compareBy<RelayProbeResult> { it.status.order() }.thenByDescending { it.newestAt ?: 0 }.thenBy { it.relay.url })
        }
    val running = state.phase == RelayProbePhase.DISCOVERING || state.phase == RelayProbePhase.PROBING

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = Modifier.padding(padding),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val newest = state.newest
                if (newest != null) {
                    DevToolsSectionTitle(stringRes(Res.string.devtools_probe_newest) + " · " + timeAgoNoDot(newest.createdAt))
                    val json = remember(newest) { newest.toPrettyJson() }
                    DevToolsCodeBlock(json, maxHeight = 320)
                } else if (state.phase == RelayProbePhase.DONE) {
                    Text(stringRes(Res.string.devtools_probe_none_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(stringRes(explainer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringRes(Res.string.devtools_probe_include_discovered), modifier = Modifier.weight(1f))
                    Switch(checked = includeDiscovered, onCheckedChange = { includeDiscovered = it }, enabled = !running)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (running) {
                        OutlinedButton(onClick = viewModel::cancel) { Text(stringRes(Res.string.devtools_probe_stop)) }
                    } else {
                        Button(onClick = { viewModel.start(includeDiscovered) }) { Text(stringRes(Res.string.devtools_probe_start)) }
                    }
                    FilterChip(
                        selected = foundOnly,
                        onClick = { foundOnly = !foundOnly },
                        label = { Text(stringRes(Res.string.devtools_probe_found_only)) },
                    )
                }

                when (state.phase) {
                    RelayProbePhase.DISCOVERING -> {
                        Text(stringRes(Res.string.devtools_probe_discovering), style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    RelayProbePhase.PROBING, RelayProbePhase.DONE -> {
                        Text(
                            pluralStringRes(Res.plurals.devtools_probe_progress, state.total, state.finished, state.total, state.found),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (running && state.total > 0) {
                            LinearProgressIndicator(
                                progress = { state.finished / state.total.toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    RelayProbePhase.IDLE -> {}
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }

        items(rows, key = { it.relay.url }) { result ->
            ProbeRow(result) { nav.nav(Route.RelayInfo(result.relay.url)) }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ProbeRow(
    result: RelayProbeResult,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                result.relay.displayUrl(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details =
                buildList {
                    result.newestAt?.let { add(timeAgoNoDot(it).trim()) }
                    result.message?.let { add(it) }
                }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        StatusChip(result.status)
    }
}

@Composable
private fun StatusChip(status: RelayProbeStatus) {
    val (label, color) = status.look()
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.15f), modifier = Modifier.padding(start = 8.dp)) {
        Text(
            stringRes(label),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun RelayProbeStatus.look(): Pair<StringResource, Color> =
    when (this) {
        RelayProbeStatus.WAITING -> Res.string.devtools_status_waiting to MaterialTheme.colorScheme.onSurfaceVariant
        RelayProbeStatus.QUERYING -> Res.string.devtools_status_querying to MaterialTheme.colorScheme.secondary
        RelayProbeStatus.FOUND -> Res.string.devtools_status_found to MaterialTheme.colorScheme.primary
        RelayProbeStatus.EMPTY -> Res.string.devtools_status_empty to MaterialTheme.colorScheme.onSurfaceVariant
        RelayProbeStatus.AUTH_REQUIRED -> Res.string.devtools_status_auth to MaterialTheme.colorScheme.tertiary
        RelayProbeStatus.REFUSED -> Res.string.devtools_status_refused to MaterialTheme.colorScheme.tertiary
        RelayProbeStatus.OFFLINE -> Res.string.devtools_status_offline to MaterialTheme.colorScheme.error
        RelayProbeStatus.TIMEOUT -> Res.string.devtools_status_timeout to MaterialTheme.colorScheme.error
        RelayProbeStatus.WAITING_FOR_TOR -> Res.string.devtools_status_tor to MaterialTheme.colorScheme.onSurfaceVariant
    }

/** Found relays first, then the ones still working, then every kind of "no". */
private fun RelayProbeStatus.order() =
    when (this) {
        RelayProbeStatus.FOUND -> 0
        RelayProbeStatus.QUERYING -> 1
        RelayProbeStatus.WAITING -> 2
        RelayProbeStatus.EMPTY -> 3
        RelayProbeStatus.AUTH_REQUIRED -> 4
        RelayProbeStatus.REFUSED -> 5
        RelayProbeStatus.TIMEOUT -> 6
        RelayProbeStatus.OFFLINE -> 7
        RelayProbeStatus.WAITING_FOR_TOR -> 8
    }
