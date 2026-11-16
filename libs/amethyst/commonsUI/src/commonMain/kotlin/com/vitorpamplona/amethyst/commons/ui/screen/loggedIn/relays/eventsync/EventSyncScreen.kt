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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.eventsync

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.relays.eventsync.EventSync
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.event_sync_activity_log
import com.vitorpamplona.amethyst.commons.resources.event_sync_activity_log_finished
import com.vitorpamplona.amethyst.commons.resources.event_sync_cancel
import com.vitorpamplona.amethyst.commons.resources.event_sync_date_filter_all_time
import com.vitorpamplona.amethyst.commons.resources.event_sync_date_filter_now
import com.vitorpamplona.amethyst.commons.resources.event_sync_date_filter_since
import com.vitorpamplona.amethyst.commons.resources.event_sync_date_filter_title
import com.vitorpamplona.amethyst.commons.resources.event_sync_date_filter_until
import com.vitorpamplona.amethyst.commons.resources.event_sync_dm_relays
import com.vitorpamplona.amethyst.commons.resources.event_sync_done_accepted
import com.vitorpamplona.amethyst.commons.resources.event_sync_done_duration
import com.vitorpamplona.amethyst.commons.resources.event_sync_done_sent
import com.vitorpamplona.amethyst.commons.resources.event_sync_done_title
import com.vitorpamplona.amethyst.commons.resources.event_sync_error_title
import com.vitorpamplona.amethyst.commons.resources.event_sync_events_sent
import com.vitorpamplona.amethyst.commons.resources.event_sync_inbox_relays
import com.vitorpamplona.amethyst.commons.resources.event_sync_less_than_until
import com.vitorpamplona.amethyst.commons.resources.event_sync_log_new
import com.vitorpamplona.amethyst.commons.resources.event_sync_log_recv
import com.vitorpamplona.amethyst.commons.resources.event_sync_log_sent
import com.vitorpamplona.amethyst.commons.resources.event_sync_mobile_data_dialog_title
import com.vitorpamplona.amethyst.commons.resources.event_sync_outbox_relays
import com.vitorpamplona.amethyst.commons.resources.event_sync_relays_progress
import com.vitorpamplona.amethyst.commons.resources.event_sync_sending_to
import com.vitorpamplona.amethyst.commons.resources.event_sync_start
import com.vitorpamplona.amethyst.commons.resources.event_sync_start_anyway
import com.vitorpamplona.amethyst.commons.resources.event_sync_status_completed
import com.vitorpamplona.amethyst.commons.resources.event_sync_status_connecting
import com.vitorpamplona.amethyst.commons.resources.event_sync_status_downloading
import com.vitorpamplona.amethyst.commons.resources.event_sync_status_error
import com.vitorpamplona.amethyst.commons.resources.event_sync_step1
import com.vitorpamplona.amethyst.commons.resources.event_sync_step2
import com.vitorpamplona.amethyst.commons.resources.event_sync_step3
import com.vitorpamplona.amethyst.commons.resources.event_sync_title
import com.vitorpamplona.amethyst.commons.resources.event_sync_what_happens_body
import com.vitorpamplona.amethyst.commons.resources.event_sync_what_happens_title
import com.vitorpamplona.amethyst.commons.resources.event_sync_wifi_warning
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDotNoDay
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils

@Composable
fun EventSyncScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val syncViewModel = accountViewModel.eventSync
    val isMobileOrMetered by accountViewModel.settings.isMobileOrMeteredConnection.collectAsStateWithLifecycle()
    val syncState by syncViewModel.syncState.collectAsStateWithLifecycle()
    val liveActivity by syncViewModel.liveActivity.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopBarWithBackButton(
                caption = stringRes(Res.string.event_sync_title),
                nav = nav,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            EventScreenBody(
                syncState = syncState,
                liveActivity = liveActivity,
                isMobileOrMetered = isMobileOrMetered,
                onSinceChanged = { syncViewModel.sinceSecs.value = it },
                onUntilChanged = { syncViewModel.untilSecs.value = it },
                onStart = syncViewModel::start,
                onCancel = syncViewModel::cancel,
            )
        }
    }
}

@Composable
fun EventScreenBody(
    syncState: EventSync.SyncState,
    liveActivity: EventSync.LiveSyncActivity,
    isMobileOrMetered: Boolean = false,
    onSinceChanged: (Long?) -> Unit = {},
    onUntilChanged: (Long?) -> Unit = {},
    onStart: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            // ---- Progress / Status area ----
            when (syncState) {
                is EventSync.SyncState.Idle -> ExplanationCard(isMobileOrMetered, onSinceChanged, onUntilChanged, onStart)
                is EventSync.SyncState.Running -> SyncProgressCard(state = syncState, onCancel)
                is EventSync.SyncState.Done -> DoneCard(state = syncState, isMobileOrMetered, onSinceChanged, onUntilChanged, onStart)
                is EventSync.SyncState.Error -> ErrorCard(syncState, isMobileOrMetered, onSinceChanged, onUntilChanged, onStart)
            }
        }

        // ---- Live relay activity (shown during and after sync) ----
        if (liveActivity.outboxTargets.isNotEmpty() ||
            liveActivity.inboxTargets.isNotEmpty() ||
            liveActivity.dmTargets.isNotEmpty()
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                DestinationRelaysCard(activity = liveActivity)
            }
        }

        val runningSize = liveActivity.runningRelays.size
        if (runningSize > 0) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringRes(Res.string.event_sync_activity_log, runningSize),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(5.dp))
            }

            itemsIndexed(liveActivity.runningRelays.values.toList(), key = { _, item -> item.relay.url }) { index, info ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                ActivityLogRow(info = info)
            }
        }

        val completedSize = liveActivity.completedRelays.size
        if (completedSize > 0) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringRes(Res.string.event_sync_activity_log_finished, completedSize),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(5.dp))
            }

            itemsIndexed(liveActivity.sortedCompletedRelays, key = { _, item -> item.relay.url }) { index, info ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                ActivityLogRow(info = info)
            }
        }
    }
}

@Composable
private fun StartSyncButton(
    isMobileOrMetered: Boolean,
    onClick: () -> Unit,
) {
    var showMobileDataDialog by remember { mutableStateOf(false) }

    Button(
        onClick = {
            if (isMobileOrMetered) {
                showMobileDataDialog = true
            } else {
                onClick()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringRes(Res.string.event_sync_start))
    }

    // ---- Mobile-data confirmation dialog ----
    if (showMobileDataDialog) {
        AlertDialog(
            onDismissRequest = { showMobileDataDialog = false },
            title = { Text(stringRes(Res.string.event_sync_mobile_data_dialog_title)) },
            text = { Text(stringRes(Res.string.event_sync_wifi_warning)) },
            confirmButton = {
                Button(
                    onClick = {
                        showMobileDataDialog = false
                        onClick()
                    },
                ) {
                    Text(stringRes(Res.string.event_sync_start_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMobileDataDialog = false }) {
                    Text(stringRes(Res.string.event_sync_cancel))
                }
            },
        )
    }
}

@Composable
private fun ExplanationCard(
    isMobileOrMetered: Boolean,
    onSinceChanged: (Long?) -> Unit = {},
    onUntilChanged: (Long?) -> Unit = {},
    onStart: () -> Unit = {},
) {
    // ---- Explanation card ----
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(Res.string.event_sync_what_happens_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringRes(Res.string.event_sync_what_happens_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            StepRow(number = "1", text = stringRes(Res.string.event_sync_step1))
            Spacer(Modifier.height(4.dp))
            StepRow(number = "2", text = stringRes(Res.string.event_sync_step2))
            Spacer(Modifier.height(4.dp))
            StepRow(number = "3", text = stringRes(Res.string.event_sync_step3))
            Spacer(Modifier.height(10.dp))

            // ---- WiFi warning ----
            if (isMobileOrMetered) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                ) {
                    Text(
                        text = stringRes(Res.string.event_sync_wifi_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            DateRangeFilterCard(
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                onSinceChanged = onSinceChanged,
                onUntilChanged = onUntilChanged,
            )
            Spacer(Modifier.height(14.dp))
            StartSyncButton(isMobileOrMetered = isMobileOrMetered, onStart)
        }
    }
}

// -------------------------------------------------------------------------
// Progress / status cards
// -------------------------------------------------------------------------

@Composable
private fun SyncProgressCard(
    state: EventSync.SyncState.Running,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            RelayStatement(state)
            Spacer(Modifier.height(8.dp))
            EventsReceivedStatement(state)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(stringRes(Res.string.event_sync_cancel))
            }
        }
    }
}

@Composable
private fun EventsReceivedStatement(state: EventSync.SyncState.Running) {
    val eventsReceived by state.eventsReceived.collectAsStateWithLifecycle()
    val eventsSent by state.eventsSent.collectAsStateWithLifecycle()
    val eventsAccepted by state.eventsAccepted.collectAsStateWithLifecycle()

    Text(
        text = stringRes(Res.string.event_sync_events_sent, eventsAccepted, eventsSent, eventsReceived),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RelayStatement(state: EventSync.SyncState.Running) {
    val relaysCompleted by state.relaysCompleted.collectAsStateWithLifecycle()
    val totalRelays by state.totalRelays.collectAsStateWithLifecycle()

    Text(
        text = stringRes(Res.string.event_sync_relays_progress, relaysCompleted, totalRelays),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = {
            if (totalRelays > 0) {
                relaysCompleted / totalRelays.toFloat()
            } else {
                0f
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DoneCard(
    state: EventSync.SyncState.Done,
    isMobileOrMetered: Boolean = false,
    onSinceChanged: (Long?) -> Unit = {},
    onUntilChanged: (Long?) -> Unit = {},
    onStart: () -> Unit = { },
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(Res.string.event_sync_done_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringRes(Res.string.event_sync_done_sent, state.totalEventsSent, state.totalEventsReceived),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringRes(Res.string.event_sync_done_accepted, state.totalEventsAccepted),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringRes(Res.string.event_sync_done_duration, (state.durationMs / 1000).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(14.dp))

            DateRangeFilterCard(
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                onSinceStartValue = state.sinceFilter,
                onUntilStartValue = state.untilFilter,
                onSinceChanged = onSinceChanged,
                onUntilChanged = onUntilChanged,
            )

            Spacer(Modifier.height(10.dp))
            StartSyncButton(isMobileOrMetered, onStart)
        }
    }
}

@Composable
private fun ErrorCard(
    state: EventSync.SyncState.Error,
    isMobileOrMetered: Boolean = false,
    onSinceChanged: (Long?) -> Unit = {},
    onUntilChanged: (Long?) -> Unit = {},
    onStart: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(Res.string.event_sync_error_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(14.dp))

            DateRangeFilterCard(
                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                onSinceStartValue = state.sinceFilter,
                onUntilStartValue = state.untilFilter,
                onSinceChanged = onSinceChanged,
                onUntilChanged = onUntilChanged,
            )
            Spacer(Modifier.height(10.dp))
            StartSyncButton(isMobileOrMetered, onStart)
        }
    }
}

// -------------------------------------------------------------------------
// Live activity cards
// -------------------------------------------------------------------------

/**
 * Shows where events are being sent: outbox, inbox, and DM relay lists.
 */
@Composable
private fun DestinationRelaysCard(activity: EventSync.LiveSyncActivity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(Res.string.event_sync_sending_to),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            if (activity.outboxTargets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                DestinationSection(
                    label = stringRes(Res.string.event_sync_outbox_relays),
                    relays = activity.outboxTargets.values,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (activity.inboxTargets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
                DestinationSection(
                    label = stringRes(Res.string.event_sync_inbox_relays),
                    relays = activity.inboxTargets.values,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            if (activity.dmTargets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
                DestinationSection(
                    label = stringRes(Res.string.event_sync_dm_relays),
                    relays = activity.dmTargets.values,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun DestinationSection(
    label: String,
    relays: Collection<EventSync.LiveSyncActivity.DestinationRelayInfo>,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        relays.forEachIndexed { index, info ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
            DestinationRelayRow(info = info, color = color)
        }
    }
}

@Composable
private fun DestinationRelayRow(
    info: EventSync.LiveSyncActivity.DestinationRelayInfo,
    color: androidx.compose.ui.graphics.Color,
) {
    val eventsSent by info.eventsSent.collectAsStateWithLifecycle()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Text(
            text = info.relay.displayHost(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            modifier = Modifier.weight(1f),
        )
        if (eventsSent > 0) {
            val eventsAccepted by info.eventsAccepted.collectAsStateWithLifecycle()
            Text(
                text = stringRes(Res.string.event_sync_log_sent, formatCount(eventsSent)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.3f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
            Text(
                text = stringRes(Res.string.event_sync_log_new, formatCount(eventsAccepted)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (eventsAccepted > 0) FontWeight.SemiBold else FontWeight.Normal,
                color = if (eventsAccepted > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.3f),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
            )
        }
    }
}

@Composable
private fun ActivityLogRow(info: EventSync.LiveSyncActivity.SourceRelayInfo) {
    val eventsFound by info.eventsFound.collectAsStateWithLifecycle()
    val hasEvents = eventsFound > 0
    val dotColor =
        if (hasEvents) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        }
    val textColor =
        if (hasEvents) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        Text(
            text = info.relay.displayHost(),
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            modifier = Modifier.weight(0.6f),
        )
        if (hasEvents) {
            val untilPage by info.pageUntil.collectAsStateWithLifecycle()
            val eventsAccepted by info.eventsAccepted.collectAsStateWithLifecycle()
            untilPage?.let {
                Text(
                    text = stringRes(Res.string.event_sync_less_than_until, timeAgoNoDotNoDay(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    modifier = Modifier.weight(0.3f),
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    overflow = TextOverflow.StartEllipsis,
                )
            }
            Text(
                text = stringRes(Res.string.event_sync_log_recv, formatCount(eventsFound)),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                modifier = Modifier.weight(0.3f),
                maxLines = 1,
                textAlign = TextAlign.End,
                overflow = TextOverflow.StartEllipsis,
            )
            Text(
                text = stringRes(Res.string.event_sync_log_new, formatCount(eventsAccepted)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (eventsAccepted > 0) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    if (eventsAccepted > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                textAlign = TextAlign.End,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.weight(0.3f),
            )
        } else {
            val status by info.status.collectAsStateWithLifecycle()
            Text(
                text =
                    when (status) {
                        EventSync.LiveSyncActivity.ConnectionStatus.Connecting -> stringRes(Res.string.event_sync_status_connecting)
                        EventSync.LiveSyncActivity.ConnectionStatus.Querying -> stringRes(Res.string.event_sync_status_downloading)
                        is EventSync.LiveSyncActivity.ConnectionStatus.Error -> (status as EventSync.LiveSyncActivity.ConnectionStatus.Error).msg.ifBlank { stringRes(Res.string.event_sync_status_error) }
                        EventSync.LiveSyncActivity.ConnectionStatus.Completed -> stringRes(Res.string.event_sync_status_completed)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.weight(0.45f),
            )
        }
    }
}

/** Oldest possible "since" — roughly 2020-01-01 in epoch seconds. */
private const val EPOCH_2023 = 1_672_549_201L

@Composable
private fun DateRangeFilterCard(
    backgroundColor: Color,
    onSinceStartValue: Long? = null,
    onUntilStartValue: Long? = null,
    onSinceChanged: (Long?) -> Unit = {},
    onUntilChanged: (Long?) -> Unit = {},
) {
    val now = remember { TimeUtils.now() }

    // Slider range: 0f = EPOCH_2020, 1f = now
    val range = (now - EPOCH_2023).toFloat()

    // Initialise from lastSyncTimestamp when available, otherwise full range
    var sliderValues by remember(onSinceStartValue, onUntilStartValue) {
        val initialSince =
            if (onSinceStartValue != null) {
                ((onSinceStartValue - EPOCH_2023) / range).coerceIn(0f, 1f)
            } else {
                0f
            }

        val finalSince =
            if (onUntilStartValue != null) {
                ((onUntilStartValue - EPOCH_2023) / range).coerceIn(0f, 1f)
            } else {
                1f
            }
        mutableStateOf(initialSince..finalSince)
    }

    // Derive actual timestamps
    val sinceEpoch = (EPOCH_2023 + (sliderValues.start * range).toLong())
    val untilEpoch = (EPOCH_2023 + (sliderValues.endInclusive * range).toLong())

    // Fire callbacks
    val effectiveSince = if (sliderValues.start <= 0.001f) null else sinceEpoch
    val effectiveUntil = if (sliderValues.endInclusive >= 0.999f) null else untilEpoch

    // Notify parent on each change
    LaunchedEffect(effectiveSince) { onSinceChanged(effectiveSince) }
    LaunchedEffect(effectiveUntil) { onUntilChanged(effectiveUntil) }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(Res.string.event_sync_date_filter_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(12.dp))

            // Labels for current selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text =
                        if (effectiveSince == null) {
                            stringRes(Res.string.event_sync_date_filter_all_time)
                        } else {
                            stringRes(Res.string.event_sync_date_filter_since) + " " + formatDateTime(sinceEpoch * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.NONE)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text =
                        if (effectiveUntil == null) {
                            stringRes(Res.string.event_sync_date_filter_now)
                        } else {
                            stringRes(Res.string.event_sync_date_filter_until) + " " + formatDateTime(untilEpoch * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.NONE)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Box(
                // block left drawer and back gestures when sliding
                Modifier.pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                    }
                },
            ) {
                RangeSlider(
                    value = sliderValues,
                    onValueChange = { sliderValues = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

@Composable
private fun StepRow(
    number: String,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Strips the WebSocket scheme and trailing slash for compact display. */
private fun NormalizedRelayUrl.displayHost(): String =
    url
        .removePrefix("wss://")
        .removePrefix("ws://")
        .trimEnd('/')

/** Formats a count with K/M suffix for large numbers. */
private fun formatCount(n: Int): String =
    when {
        n >= 1_000_000 -> "${n / 1_000}K"
        else -> n.toString()
    }

// -------------------------------------------------------------------------
// Preview data
// -------------------------------------------------------------------------

private val previewRunning =
    listOf(
        EventSync.LiveSyncActivity.SourceRelayInfo(NormalizedRelayUrl("wss://relay.damus.io"), EventSync.LiveSyncActivity.ConnectionStatus.Querying, 1247, 891),
        EventSync.LiveSyncActivity.SourceRelayInfo(NormalizedRelayUrl("wss://nos.lol"), EventSync.LiveSyncActivity.ConnectionStatus.Querying, 892, 45),
        EventSync.LiveSyncActivity.SourceRelayInfo(NormalizedRelayUrl("wss://nos2.lol"), EventSync.LiveSyncActivity.ConnectionStatus.Connecting, 0, 0),
    )

private val previewCompletions =
    listOf(
        EventSync.LiveSyncActivity.SourceRelayInfo(NormalizedRelayUrl("wss://relay.nostr.band"), EventSync.LiveSyncActivity.ConnectionStatus.Completed, 3500, 3498),
        EventSync.LiveSyncActivity.SourceRelayInfo(NormalizedRelayUrl("wss://slow.relay.example.com"), EventSync.LiveSyncActivity.ConnectionStatus.Completed, 0, 0),
        EventSync.LiveSyncActivity.SourceRelayInfo(NormalizedRelayUrl("wss://nostr.bitcoiner.social"), EventSync.LiveSyncActivity.ConnectionStatus.Completed, 15, 0),
        EventSync.LiveSyncActivity.SourceRelayInfo(NormalizedRelayUrl("wss://unreachable.relay.xyz"), EventSync.LiveSyncActivity.ConnectionStatus.Error("connection failed"), 0, 0),
    )

private val previewActivity =
    EventSync.LiveSyncActivity(
        runningRelays = previewRunning,
        completedRelays = previewCompletions,
        outboxTargets =
            listOf(
                EventSync.LiveSyncActivity.DestinationRelayInfo(NormalizedRelayUrl("wss://outbox.nostr.com"), 1247, 891),
                EventSync.LiveSyncActivity.DestinationRelayInfo(NormalizedRelayUrl("wss://relay.damus.io"), 892, 45),
            ),
        inboxTargets =
            listOf(
                EventSync.LiveSyncActivity.DestinationRelayInfo(NormalizedRelayUrl("wss://inbox.nostr.com"), 500, 500),
                EventSync.LiveSyncActivity.DestinationRelayInfo(NormalizedRelayUrl("wss://nos.lol"), 0, 0),
            ),
        dmTargets =
            listOf(
                EventSync.LiveSyncActivity.DestinationRelayInfo(NormalizedRelayUrl("wss://dm.nostr.com"), 15, 10),
            ),
    )

// -------------------------------------------------------------------------
// Previews
// -------------------------------------------------------------------------

@Composable
@Preview
fun IdleCardWifiPreview() {
    ThemeComparisonColumn {
        ExplanationCard(false)
    }
}

@Composable
@Preview
fun IdleCardMobilePreview() {
    ThemeComparisonColumn {
        ExplanationCard(true)
    }
}

@Composable
@Preview
fun SyncProgressCardPreview() {
    ThemeComparisonColumn {
        SyncProgressCard(
            state =
                EventSync.SyncState.Running(
                    relaysCompleted = 312,
                    totalRelays = 1024,
                    eventsAccepted = 12,
                    eventsSent = 4821,
                    eventsReceived = 10000,
                ),
            onCancel = {},
        )
    }
}

@Composable
@Preview
fun DoneCardPreview() {
    ThemeComparisonColumn {
        DoneCard(
            state =
                EventSync.SyncState.Done(
                    totalEventsReceived = 20_000,
                    totalEventsSent = 18_432,
                    totalEventsAccepted = 14_891,
                    durationMs = 187_000,
                ),
        )
    }
}

@Composable
@Preview
fun ErrorCardPreview() {
    ThemeComparisonColumn {
        ErrorCard(state = EventSync.SyncState.Error("No outbox, inbox, or DM relays configured."))
    }
}

@Composable
@Preview
fun DestinationRelaysCardPreview() {
    ThemeComparisonColumn {
        DestinationRelaysCard(activity = previewActivity)
    }
}

@Composable
@Preview(device = "spec:width=1800px,height=2340px,dpi=440")
fun EventScreenBodyPreview() {
    ThemeComparisonRow {
        EventScreenBody(
            EventSync.SyncState.Idle,
            EventSync.LiveSyncActivity(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
        )
    }
}

@Composable
@Preview(device = "spec:width=1800px,height=2340px,dpi=440")
fun EventScreenBody2Preview() {
    ThemeComparisonRow {
        EventScreenBody(
            EventSync.SyncState.Running(1047, 1224, 100, 4821, 10000),
            previewActivity,
        )
    }
}
