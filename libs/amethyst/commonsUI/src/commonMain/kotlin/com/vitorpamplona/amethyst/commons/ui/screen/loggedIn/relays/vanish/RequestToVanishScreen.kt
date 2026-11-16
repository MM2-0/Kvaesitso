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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.vanish

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.Nip11CachedRetriever
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.Nip11Fetcher
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.confirm
import com.vitorpamplona.amethyst.commons.resources.next
import com.vitorpamplona.amethyst.commons.resources.request_to_vanish
import com.vitorpamplona.amethyst.commons.resources.request_to_vanish_description
import com.vitorpamplona.amethyst.commons.resources.vanish_all_relays
import com.vitorpamplona.amethyst.commons.resources.vanish_all_relays_warning
import com.vitorpamplona.amethyst.commons.resources.vanish_confirm_all_relays
import com.vitorpamplona.amethyst.commons.resources.vanish_confirm_single_relay
import com.vitorpamplona.amethyst.commons.resources.vanish_confirm_title
import com.vitorpamplona.amethyst.commons.resources.vanish_date_explainer
import com.vitorpamplona.amethyst.commons.resources.vanish_date_label
import com.vitorpamplona.amethyst.commons.resources.vanish_reason_label
import com.vitorpamplona.amethyst.commons.resources.vanish_reason_placeholder
import com.vitorpamplona.amethyst.commons.resources.vanish_request_sent
import com.vitorpamplona.amethyst.commons.resources.vanish_select_date
import com.vitorpamplona.amethyst.commons.resources.vanish_select_time
import com.vitorpamplona.amethyst.commons.resources.vanish_send_request
import com.vitorpamplona.amethyst.commons.resources.vanish_target_relay
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.note.rememberIs24HourClock
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfoDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.RelayUrlEditField
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.HorzHalfVertPadding
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestToVanishScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RequestToVanishScreen(
        nip11CachedRetriever = LocalCache.appHost.nip11Cache,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestToVanishScreen(
    nip11CachedRetriever: Nip11CachedRetriever,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val connectedRelays by accountViewModel.account.client
        .connectedRelaysFlow()
        .collectAsStateWithLifecycle()

    var selectedRelayUrls by remember { mutableStateOf(emptyList<NormalizedRelayUrl>()) }
    var allRelaysSelected by remember { mutableStateOf(false) }
    var vanishDate by remember { mutableLongStateOf(TimeUtils.now()) }
    var reason by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = vanishDate * 1000,
        )

    // The vanish time as a local wall-clock time: seconds since the epoch shifted by this zone's offset.
    val localSecondOfDay = (vanishDate + LocalClock.utcOffsetSeconds(vanishDate)).mod(TimeUtils.ONE_DAY)
    val timePickerState =
        rememberTimePickerState(
            initialHour = localSecondOfDay / TimeUtils.ONE_HOUR,
            initialMinute = (localSecondOfDay % TimeUtils.ONE_HOUR) / TimeUtils.ONE_MINUTE,
            is24Hour = rememberIs24HourClock(),
        )

    val relayOptions =
        remember(connectedRelays) {
            connectedRelays
                .sortedBy { it.url }
                .map { relay ->
                    TitleExplainer(relay.displayUrl(), relay.url)
                }.toImmutableList()
        }

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.request_to_vanish), nav)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = stringRes(Res.string.request_to_vanish_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ALL RELAYS checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Relay Selection
                Text(
                    text = stringRes(Res.string.vanish_target_relay),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Checkbox(
                    checked = allRelaysSelected,
                    onCheckedChange = {
                        allRelaysSelected = it
                    },
                )
                Text(
                    text = stringRes(Res.string.vanish_all_relays),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (!allRelaysSelected) {
                selectedRelayUrls.forEach {
                    val info =
                        remember(it) {
                            relaySetupInfoBuilder(it, false)
                        }

                    BasicRelaySetupInfoDialog(
                        info,
                        onDelete = { selectedRelayUrls -= selectedRelayUrls },
                        modifier = HorzHalfVertPadding,
                        nip11CachedRetriever = nip11CachedRetriever,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                RelayUrlEditField(
                    onNewRelay = {
                        selectedRelayUrls += selectedRelayUrls + it
                        allRelaysSelected = false
                    },
                    nip11CachedRetriever = nip11CachedRetriever,
                    modifier = Modifier,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            } else {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(8.dp),
                            ).padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringRes(Res.string.vanish_all_relays_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(thickness = DividerThickness)

            Spacer(modifier = Modifier.height(20.dp))

            // Date Picker
            Text(
                text = stringRes(Res.string.vanish_date_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringRes(Res.string.vanish_date_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedCard(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        MaterialSymbols.CalendarMonth,
                        contentDescription = stringRes(Res.string.vanish_select_date),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = formatDateTime(vanishDate * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(thickness = DividerThickness)

            Spacer(modifier = Modifier.height(20.dp))

            // Reason
            SpellCheckedOutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(stringRes(Res.string.vanish_reason_label)) },
                placeholder = { Text(stringRes(Res.string.vanish_reason_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 4,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Send button
            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = allRelaysSelected || selectedRelayUrls.isNotEmpty(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Icon(
                    MaterialSymbols.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringRes(Res.string.vanish_send_request))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    showTimePicker = true
                }) { Text(stringRes(Res.string.next)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            title = {
                Text(stringRes(Res.string.vanish_select_time))
            },
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val datetimeLocalTimeZone =
                            datePickerState.selectedDateMillis?.let { localDayAtZeroHourMillis ->
                                (localDayAtZeroHourMillis / 1000) +
                                    (timePickerState.hour * TimeUtils.ONE_HOUR) +
                                    (timePickerState.minute * TimeUtils.ONE_MINUTE)
                            } ?: TimeUtils.now()

                        vanishDate = datetimeLocalTimeZone - LocalClock.utcOffsetSeconds(TimeUtils.now())

                        showTimePicker = false
                    },
                ) { Text(stringRes(Res.string.confirm)) }
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }

    if (showConfirmDialog) {
        ConfirmVanishDialog(
            isAllRelays = allRelaysSelected,
            relays = selectedRelayUrls,
            onConfirm = {
                showConfirmDialog = false
                if (allRelaysSelected) {
                    accountViewModel.requestToVanishFromEverywhere(reason, vanishDate)
                } else {
                    if (selectedRelayUrls.isNotEmpty()) {
                        accountViewModel.requestToVanish(selectedRelayUrls, reason, vanishDate)
                    }
                }
                accountViewModel.toastManager.toast(
                    Res.string.request_to_vanish,
                    Res.string.vanish_request_sent,
                )
                nav.popBack()
            },
            onDismiss = { showConfirmDialog = false },
        )
    }
}

@Composable
private fun ConfirmVanishDialog(
    isAllRelays: Boolean,
    relays: List<NormalizedRelayUrl>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                MaterialSymbols.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = stringRes(Res.string.vanish_confirm_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text =
                    if (isAllRelays) {
                        stringRes(Res.string.vanish_confirm_all_relays)
                    } else {
                        val relayNames =
                            relays.joinToString(
                                ", ",
                                limit = 10,
                                transform = { it.displayUrl() },
                            )
                        stringRes(Res.string.vanish_confirm_single_relay, relayNames)
                    },
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(stringRes(Res.string.vanish_send_request))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

@Preview
@Composable
fun RequestToVanishScreenPreview() {
    ThemeComparisonColumn {
        RequestToVanishScreen(
            nip11CachedRetriever = Nip11CachedRetriever(Nip11Fetcher.Offline),
            accountViewModel = mockAccountViewModel(),
            nav = EmptyNav(),
        )
    }
}
