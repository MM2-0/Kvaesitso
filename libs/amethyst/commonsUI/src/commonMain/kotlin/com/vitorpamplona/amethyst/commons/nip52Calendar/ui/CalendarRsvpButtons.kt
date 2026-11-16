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
package com.vitorpamplona.amethyst.commons.nip52Calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_going
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_maybe
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_not_going
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag

/**
 * Going / Maybe / Not going buttons for a NIP-52 calendar event. The one matching
 * [currentStatus] is filled in its status colour; the others are outlined. [onSelect] publishes
 * the chosen status.
 */
@Composable
fun CalendarRsvpButtons(
    currentStatus: RSVPStatusTag.STATUS?,
    onSelect: (RSVPStatusTag.STATUS) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RsvpButton(
            label = stringRes(Res.string.calendar_rsvp_going),
            status = RSVPStatusTag.STATUS.ACCEPTED,
            currentStatus = currentStatus,
            modifier = Modifier.weight(1f),
            onClick = onSelect,
        )
        RsvpButton(
            label = stringRes(Res.string.calendar_rsvp_maybe),
            status = RSVPStatusTag.STATUS.TENTATIVE,
            currentStatus = currentStatus,
            modifier = Modifier.weight(1f),
            onClick = onSelect,
        )
        RsvpButton(
            label = stringRes(Res.string.calendar_rsvp_not_going),
            status = RSVPStatusTag.STATUS.DECLINED,
            currentStatus = currentStatus,
            modifier = Modifier.weight(1f),
            onClick = onSelect,
        )
    }
}

@Composable
private fun RsvpButton(
    label: String,
    status: RSVPStatusTag.STATUS,
    currentStatus: RSVPStatusTag.STATUS?,
    modifier: Modifier,
    onClick: (RSVPStatusTag.STATUS) -> Unit,
) {
    val selected = status == currentStatus
    if (selected) {
        FilledTonalButton(
            onClick = { onClick(status) },
            modifier = modifier,
            contentPadding = RsvpButtonPadding,
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = colorFor(status),
                    contentColor = Color.White,
                ),
        ) {
            RsvpButtonLabel(label)
        }
    } else {
        OutlinedButton(
            onClick = { onClick(status) },
            modifier = modifier,
            contentPadding = RsvpButtonPadding,
        ) {
            RsvpButtonLabel(label)
        }
    }
}

private val RsvpButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

@Composable
private fun RsvpButtonLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun colorFor(status: RSVPStatusTag.STATUS) =
    when (status) {
        RSVPStatusTag.STATUS.ACCEPTED -> MaterialTheme.colorScheme.primary
        RSVPStatusTag.STATUS.TENTATIVE -> MaterialTheme.colorScheme.tertiary
        RSVPStatusTag.STATUS.DECLINED -> MaterialTheme.colorScheme.error
    }
