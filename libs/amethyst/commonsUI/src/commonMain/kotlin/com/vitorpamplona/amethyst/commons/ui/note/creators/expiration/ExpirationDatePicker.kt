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
package com.vitorpamplona.amethyst.commons.ui.note.creators.expiration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.composer.IExpiration
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.confirm
import com.vitorpamplona.amethyst.commons.resources.expiration_date_explainer
import com.vitorpamplona.amethyst.commons.resources.expiration_date_label
import com.vitorpamplona.amethyst.commons.resources.expiration_date_select
import com.vitorpamplona.amethyst.commons.resources.expiration_expires_in
import com.vitorpamplona.amethyst.commons.resources.expiration_time
import com.vitorpamplona.amethyst.commons.resources.next
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.ui.note.rememberIs24HourClock
import com.vitorpamplona.amethyst.commons.ui.note.timeAheadNoDot
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.currentTimeMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpirationDatePicker(model: IExpiration) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // The expiration as a local wall-clock time: seconds since the epoch shifted by this zone's offset.
    val localSeconds = model.expirationDate + LocalClock.utcOffsetSeconds(model.expirationDate)
    val localSecondOfDay = localSeconds.mod(TimeUtils.ONE_DAY)
    val currentYear = SearchDate.civilFromDays(localSeconds.floorDiv(TimeUtils.ONE_DAY)).year

    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = model.expirationDate * 1000,
            yearRange = currentYear..2050,
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= currentTimeMillis() - 86400000
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
                contentDescription = stringRes(Res.string.expiration_date_label),
                modifier = Modifier.size(20.dp),
                tint = Color(0xFFFF6600),
            )

            Text(
                text = stringRes(Res.string.expiration_date_label),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        HorizontalDivider(thickness = DividerThickness)

        Text(
            text = stringRes(Res.string.expiration_date_explainer),
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.padding(vertical = 10.dp),
        )

        OutlinedCard(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MaterialSymbols.Timer, contentDescription = stringRes(Res.string.expiration_date_select))
                Spacer(Modifier.width(12.dp))

                if (model.expirationDate < TimeUtils.oneMinuteFromNow()) {
                    Text(stringRes(Res.string.expiration_date_label) + " " + model.expirationDate, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(
                        text = stringRes(Res.string.expiration_expires_in, timeAheadNoDot(model.expirationDate)),
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
            title = {
                Text(stringRes(Res.string.expiration_time))
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
                            } ?: TimeUtils.oneDayAhead()

                        model.expirationDate = datetimeLocalTimeZone - LocalClock.utcOffsetSeconds(TimeUtils.now())

                        showTimePicker = false
                    },
                ) { Text(stringRes(Res.string.confirm)) }
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }
}
