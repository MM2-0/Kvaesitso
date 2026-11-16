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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.create

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_event_pick_time
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.confirm
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.search.calendar.atTime
import com.vitorpamplona.amethyst.commons.search.calendar.fromPickerMillis
import com.vitorpamplona.amethyst.commons.search.calendar.secondOfDay
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.note.rememberIs24HourClock
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Tap-to-edit button that opens a Material3 DatePicker (and, when [includeTime] is true,
 * chains into a TimePicker). The resolved instant is converted to UTC epoch seconds using
 * the device's zone offset *at the picked moment*, so DST transitions are handled correctly.
 *
 * Pass `0L` for [unixSeconds] when the user hasn't picked anything yet — the button shows
 * [placeholder] instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDateTimePickerButton(
    unixSeconds: Long,
    placeholder: String,
    includeTime: Boolean,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val pretty =
        if (unixSeconds <= 0L) {
            placeholder
        } else if (includeTime) {
            formatDateTime(unixSeconds * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT)
        } else {
            formatDateTime(unixSeconds * 1000, DateTimeStyle.LONG, DateTimeStyle.NONE)
        }

    val initialSeconds = if (unixSeconds > 0L) unixSeconds else TimeUtils.now()
    val initialMillis = initialSeconds * 1000L
    val initialSecondOfDay = LocalClock.secondOfDay(initialSeconds)
    val initialHour = initialSecondOfDay / TimeUtils.ONE_HOUR
    val initialMinute = (initialSecondOfDay % TimeUtils.ONE_HOUR) / TimeUtils.ONE_MINUTE

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )
    val timePickerState =
        rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = rememberIs24HourClock(),
        )

    fun reset() {
        datePickerState.selectedDateMillis = initialMillis
        timePickerState.hour = initialHour
        timePickerState.minute = initialMinute
    }

    OutlinedButton(
        onClick = { showDate = true },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(pretty)
    }

    if (showDate) {
        DatePickerDialog(
            onDismissRequest = {
                reset()
                showDate = false
            },
            confirmButton = {
                TextButton(onClick = {
                    showDate = false
                    if (includeTime) {
                        showTime = true
                    } else {
                        commit(datePickerState.selectedDateMillis, includeTime = false, hour = 0, minute = 0, onChange = onChange)
                    }
                }) { Text(stringRes(Res.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    reset()
                    showDate = false
                }) { Text(stringRes(Res.string.cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTime) {
        TimePickerDialog(
            title = { Text(stringRes(Res.string.calendar_event_pick_time)) },
            onDismissRequest = {
                reset()
                showTime = false
            },
            confirmButton = {
                TextButton(onClick = {
                    commit(
                        dayMillisUtc = datePickerState.selectedDateMillis,
                        includeTime = true,
                        hour = timePickerState.hour,
                        minute = timePickerState.minute,
                        onChange = onChange,
                    )
                    showTime = false
                }) { Text(stringRes(Res.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    reset()
                    showTime = false
                }) { Text(stringRes(Res.string.cancel)) }
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

private fun commit(
    dayMillisUtc: Long?,
    includeTime: Boolean,
    hour: Int,
    minute: Int,
    onChange: (Long) -> Unit,
) {
    if (dayMillisUtc == null) return
    val localDate = SearchDate.fromPickerMillis(dayMillisUtc)
    val picked =
        if (includeTime) {
            LocalClock.atTime(localDate, hour, minute)
        } else {
            // For date-only events, anchor to midnight in the user's local zone so the day
            // boundary matches their wall-clock intent.
            LocalClock.startOfDay(localDate)
        }
    onChange(picked)
}
