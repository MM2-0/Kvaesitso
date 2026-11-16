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
package com.vitorpamplona.amethyst.commons.ui.note.creators.scheduling

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.confirm
import com.vitorpamplona.amethyst.commons.resources.next
import com.vitorpamplona.amethyst.commons.resources.schedule_post
import com.vitorpamplona.amethyst.commons.resources.schedule_post_helper
import com.vitorpamplona.amethyst.commons.resources.schedule_post_pick_label
import com.vitorpamplona.amethyst.commons.resources.schedule_post_pick_time
import com.vitorpamplona.amethyst.commons.resources.schedule_post_picker_time_title
import com.vitorpamplona.amethyst.commons.resources.schedule_post_preset_in_one_hour
import com.vitorpamplona.amethyst.commons.resources.schedule_post_preset_next_monday_morning
import com.vitorpamplona.amethyst.commons.resources.schedule_post_preset_tomorrow_morning
import com.vitorpamplona.amethyst.commons.resources.schedule_post_publishes_in
import com.vitorpamplona.amethyst.commons.resources.schedule_post_time_label
import com.vitorpamplona.amethyst.commons.resources.schedule_post_warning_multi
import com.vitorpamplona.amethyst.commons.resources.schedule_post_warning_single
import com.vitorpamplona.amethyst.commons.resources.schedule_post_warning_title
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.ui.note.rememberIs24HourClock
import com.vitorpamplona.amethyst.commons.ui.note.timeAheadNoDot
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.currentTimeMillis

/**
 * Two-stage date + time picker for scheduling a post for future publication.
 *
 * The selected time is rounded up to the next quarter-hour to set realistic
 * expectations: the periodic worker that fires scheduled posts runs at
 * WorkManager's minimum 15-min interval, so per-minute precision is misleading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleAtPicker(
    scheduledForSec: Long,
    onChanged: (Long) -> Unit,
    alwaysOnEnabled: Boolean = true,
    hasMultipleAccounts: Boolean = false,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // The scheduled time as a local wall-clock time: seconds since the epoch shifted by this zone's offset.
    val localSeconds = scheduledForSec + LocalClock.utcOffsetSeconds(scheduledForSec)
    val localSecondOfDay = localSeconds.mod(TimeUtils.ONE_DAY)
    val currentYear = SearchDate.civilFromDays(localSeconds.floorDiv(TimeUtils.ONE_DAY)).year

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = scheduledForSec * 1000,
            yearRange = currentYear..2050,
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= currentTimeMillis() - 86_400_000
                },
        )

    val timePickerState =
        rememberTimePickerState(
            initialHour = localSecondOfDay / TimeUtils.ONE_HOUR,
            initialMinute = (localSecondOfDay % TimeUtils.ONE_HOUR) / TimeUtils.ONE_MINUTE,
            is24Hour = rememberIs24HourClock(),
        )

    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Timer,
                contentDescription = stringRes(Res.string.schedule_post_time_label),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = stringRes(Res.string.schedule_post),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        HorizontalDivider(thickness = DividerThickness)

        Text(
            text = stringRes(Res.string.schedule_post_helper),
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.padding(vertical = 10.dp),
        )

        if (!alwaysOnEnabled) {
            ReliabilityWarning(hasMultipleAccounts = hasMultipleAccounts)
        }

        PresetChips(onPick = onChanged)

        OutlinedCard(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MaterialSymbols.Timer, contentDescription = stringRes(Res.string.schedule_post_pick_time))
                Spacer(Modifier.width(12.dp))

                if (scheduledForSec < TimeUtils.oneMinuteFromNow()) {
                    Text(stringRes(Res.string.schedule_post_pick_label), style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(
                        text = stringRes(Res.string.schedule_post_publishes_in, timeAheadNoDot(scheduledForSec)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
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
            title = { Text(stringRes(Res.string.schedule_post_picker_time_title)) },
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val datetimeLocalTimeZone =
                            datePickerState.selectedDateMillis?.let { localDayAtZeroHourMillis ->
                                (localDayAtZeroHourMillis / 1000) +
                                    (timePickerState.hour * TimeUtils.ONE_HOUR) +
                                    (timePickerState.minute * TimeUtils.ONE_MINUTE)
                            } ?: TimeUtils.oneDayAhead()

                        val rawSec = datetimeLocalTimeZone - LocalClock.utcOffsetSeconds(TimeUtils.now())

                        onChanged(roundUpToNextQuarterHour(rawSec))
                        showTimePicker = false
                    },
                ) { Text(stringRes(Res.string.confirm)) }
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

@Composable
private fun ReliabilityWarning(hasMultipleAccounts: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    symbol = MaterialSymbols.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringRes(Res.string.schedule_post_warning_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text =
                    stringRes(
                        if (hasMultipleAccounts) {
                            Res.string.schedule_post_warning_multi
                        } else {
                            Res.string.schedule_post_warning_single
                        },
                    ),
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun PresetChips(onPick: (Long) -> Unit) {
    val scroll = rememberScrollState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { onPick(roundUpToNextQuarterHour(presetInOneHour())) },
            label = { Text(stringRes(Res.string.schedule_post_preset_in_one_hour)) },
        )
        AssistChip(
            onClick = { onPick(roundUpToNextQuarterHour(presetTomorrowMorning())) },
            label = { Text(stringRes(Res.string.schedule_post_preset_tomorrow_morning)) },
        )
        AssistChip(
            onClick = { onPick(roundUpToNextQuarterHour(presetNextMondayMorning())) },
            label = { Text(stringRes(Res.string.schedule_post_preset_next_monday_morning)) },
        )
    }
}

private fun presetInOneHour(): Long = (currentTimeMillis() / 1000) + 3600

private fun presetTomorrowMorning(): Long = nineAm(LocalClock.today().plusDays(1))

private fun presetNextMondayMorning(): Long {
    // Always step at least one day forward — if today is Monday, return next Monday.
    val tomorrow = LocalClock.today().plusDays(1)
    val monday = 1
    return nineAm(tomorrow.plusDays((monday - tomorrow.dayOfWeek()).mod(7)))
}

/** 09:00 local time on [date], read with the offset in force at 09:00, not at midnight. */
private fun nineAm(date: SearchDate): Long {
    val midnight = LocalClock.startOfDay(date)
    val guess = midnight + 9 * TimeUtils.ONE_HOUR
    return guess + LocalClock.utcOffsetSeconds(midnight) - LocalClock.utcOffsetSeconds(guess)
}

/**
 * Rounds [epochSec] up to the next 15-minute boundary. If already on a boundary,
 * returns the boundary itself. Edge case: if rounding yields a moment in the past
 * (rare — only if the user picks the exact current quarter-hour), bump forward
 * one slot.
 */
fun roundUpToNextQuarterHour(epochSec: Long): Long {
    val quarter = 15 * 60L
    val rounded = ((epochSec + quarter - 1) / quarter) * quarter
    val nowSec = currentTimeMillis() / 1000
    return if (rounded <= nowSec) rounded + quarter else rounded
}
