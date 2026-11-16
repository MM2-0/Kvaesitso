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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.nests.room.activity.NestBridge
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nest_create_cancel
import com.vitorpamplona.amethyst.commons.resources.nest_create_field_endpoint
import com.vitorpamplona.amethyst.commons.resources.nest_create_field_endpoint_hint
import com.vitorpamplona.amethyst.commons.resources.nest_create_field_image
import com.vitorpamplona.amethyst.commons.resources.nest_create_field_room
import com.vitorpamplona.amethyst.commons.resources.nest_create_field_service
import com.vitorpamplona.amethyst.commons.resources.nest_create_field_service_hint
import com.vitorpamplona.amethyst.commons.resources.nest_create_field_summary
import com.vitorpamplona.amethyst.commons.resources.nest_create_schedule_toggle
import com.vitorpamplona.amethyst.commons.resources.nest_create_submit
import com.vitorpamplona.amethyst.commons.resources.nest_create_title
import com.vitorpamplona.amethyst.commons.resources.nest_create_when
import com.vitorpamplona.amethyst.commons.resources.next
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.search.calendar.atTime
import com.vitorpamplona.amethyst.commons.search.calendar.fromPickerMillis
import com.vitorpamplona.amethyst.commons.search.calendar.secondOfDay
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectSingleFromGallery
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.note.rememberIs24HourClock
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.NestsScreen
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.launch

/**
 * Bottom sheet that lets the logged-in user start a new NIP-53 kind 30312
 * audio room. On submit, [CreateNestViewModel] builds and signs the
 * MeetingSpaceEvent (with the user as `host`), broadcasts it to the
 * account's relays, and then launches the room screen against the
 * fresh address — the user lands inside the room as host with the
 * Talk button enabled.
 *
 * Hidden by default; surfaced by the "Start space" FAB on
 * [NestsScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNestSheet(
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val viewModelKey = remember(accountViewModel) { accountViewModel.account.userProfile().pubkeyHex }
    val viewModel: CreateNestViewModel =
        rememberViewModel(key = "CreateNest-$viewModelKey") { CreateNestViewModel() }
    LaunchedEffect(viewModel) { viewModel.bindAccountIfMissing(accountViewModel) }

    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val appPlatform = LocalAppPlatform.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringRes(Res.string.nest_create_title),
                style = MaterialTheme.typography.titleLarge,
            )

            SpellCheckedOutlinedTextField(
                value = state.roomName,
                onValueChange = viewModel::onRoomNameChange,
                label = { Text(stringRes(Res.string.nest_create_field_room)) },
                singleLine = true,
                isError = state.error != null && state.roomName.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            SpellCheckedOutlinedTextField(
                value = state.summary,
                onValueChange = viewModel::onSummaryChange,
                label = { Text(stringRes(Res.string.nest_create_field_summary)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            OutlinedTextField(
                value = state.serviceUrl,
                onValueChange = viewModel::onServiceUrlChange,
                label = { Text(stringRes(Res.string.nest_create_field_service)) },
                singleLine = true,
                isError = state.error != null && state.serviceUrl.isBlank(),
                supportingText = { Text(stringRes(Res.string.nest_create_field_service_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.endpointUrl,
                onValueChange = viewModel::onEndpointUrlChange,
                label = { Text(stringRes(Res.string.nest_create_field_endpoint)) },
                singleLine = true,
                isError = state.error != null && state.endpointUrl.isBlank(),
                supportingText = { Text(stringRes(Res.string.nest_create_field_endpoint_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.imageUrl,
                onValueChange = viewModel::onImageUrlChange,
                label = { Text(stringRes(Res.string.nest_create_field_image)) },
                singleLine = true,
                leadingIcon = {
                    SelectSingleFromGallery(
                        isUploading = state.isUploadingImage,
                        tint = MaterialTheme.colorScheme.placeholderText,
                        modifier = Modifier.padding(start = 5.dp),
                    ) { media ->
                        viewModel.uploadForImage(
                            uri = media,
                            uploader = accountViewModel.mediaUploader,
                            onError = accountViewModel.toastManager::toast,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // Schedule toggle + start-time picker. Hidden picker when
            // toggled off so a "Start now" room doesn't waste vertical
            // space on a date row that isn't relevant.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Switch(
                    checked = state.scheduled,
                    onCheckedChange = viewModel::onScheduledToggle,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringRes(Res.string.nest_create_schedule_toggle))
            }
            if (state.scheduled) {
                ScheduleStartPicker(
                    unixSeconds = state.scheduledStartUnix,
                    onChange = viewModel::onScheduledStartChange,
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss, enabled = !state.isPublishing) {
                    Text(stringRes(Res.string.nest_create_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val launchInfo = viewModel.publishAndBuildLaunchInfo() ?: return@launch
                            // Hand the active AccountViewModel to the
                            // separately-tasked NestActivity (mirrors
                            // the join-card flow).
                            NestBridge.set(accountViewModel)
                            appPlatform.openNestRoom(launchInfo.addressValue)
                            onDismiss()
                        }
                    },
                    enabled = !state.isPublishing && state.canSubmit,
                ) {
                    if (state.isPublishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringRes(Res.string.nest_create_submit))
                    }
                }
            }
        }
    }
}

/**
 * Schedule-start picker. Shows the currently-selected start time
 * as a button; tapping it opens a Material3 DatePicker → TimePicker
 * chain (same shape as [com.vitorpamplona.amethyst.commons.ui.note.creators.expiration.ExpirationDatePicker]
 * — hour + minute resolved in the local zone, then converted to
 * UTC unix seconds via the system zone offset).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleStartPicker(
    unixSeconds: Long,
    onChange: (Long) -> Unit,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val pretty =
        if (unixSeconds <= 0L) {
            stringRes(Res.string.nest_create_when)
        } else {
            formatDateTime(unixSeconds * 1000L, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT)
        }

    val initialSeconds = if (unixSeconds > 0L) unixSeconds else TimeUtils.now()
    val initialMillis = initialSeconds * 1000L
    val initialSecondOfDay = LocalClock.secondOfDay(initialSeconds)
    val initialHour = initialSecondOfDay / TimeUtils.ONE_HOUR
    val initialMinute = (initialSecondOfDay % TimeUtils.ONE_HOUR) / TimeUtils.ONE_MINUTE

    // Re-key the picker state on `unixSeconds` so the dialog opens
    // pre-populated with the COMMITTED value. Without this key, a
    // mid-edit Cancel would leave the in-dialog state reflecting
    // the half-typed cancelled choice on the next reopen.
    val datePickerState =
        androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )
    val timePickerState =
        androidx.compose.material3.rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = rememberIs24HourClock(),
        )

    // On dismiss (Cancel or back-press), restore the picker states
    // to the committed `unixSeconds`. rememberDatePickerState /
    // TimePickerState are reference-stable across recompositions
    // and survive `cancel`, so we have to rewind them by hand.
    fun resetPickersToCommitted() {
        datePickerState.selectedDateMillis = initialMillis
        timePickerState.hour = initialHour
        timePickerState.minute = initialMinute
    }

    androidx.compose.material3.OutlinedButton(
        onClick = { showDate = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(pretty)
    }

    if (showDate) {
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = {
                resetPickersToCommitted()
                showDate = false
            },
            confirmButton = {
                TextButton(onClick = {
                    showDate = false
                    showTime = true
                }) { Text(stringRes(Res.string.next)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    resetPickersToCommitted()
                    showDate = false
                }) {
                    Text(stringRes(Res.string.nest_create_cancel))
                }
            },
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }

    if (showTime) {
        androidx.compose.material3.TimePickerDialog(
            title = { Text(stringRes(Res.string.nest_create_when)) },
            onDismissRequest = {
                resetPickersToCommitted()
                showTime = false
            },
            confirmButton = {
                TextButton(onClick = {
                    val dayMillisUtc = datePickerState.selectedDateMillis
                    if (dayMillisUtc != null) {
                        // DatePicker hands back UTC midnight of the
                        // selected calendar date. Reinterpret that
                        // calendar date as local + the picked
                        // hour:minute, converted with the zone offset
                        // AT THE PICKED INSTANT, so rooms scheduled
                        // across a DST transition land on the hour.
                        val localDate = SearchDate.fromPickerMillis(dayMillisUtc)
                        onChange(LocalClock.atTime(localDate, timePickerState.hour, timePickerState.minute))
                    }
                    showTime = false
                }) { Text(stringRes(Res.string.nest_create_submit)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    resetPickersToCommitted()
                    showTime = false
                }) {
                    Text(stringRes(Res.string.nest_create_cancel))
                }
            },
        ) {
            androidx.compose.material3.TimePicker(state = timePickerState)
        }
    }
}
