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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_going
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_loading_event
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_maybe
import com.vitorpamplona.amethyst.commons.resources.calendar_rsvp_not_going
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.quartz.nip52Calendar.appt.tags.RSVPStatusTag
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import org.jetbrains.compose.resources.stringResource

/**
 * A NIP-52 calendar RSVP (kind 31925) as one card: the author's note as post text, then a single
 * quote-style frame whose tinted strip says how they answered and whose body is the appointment
 * they answered.
 *
 * The status lives *inside* the appointment's frame on purpose. It is a property of "Alice → this
 * event", so a header line floating above a separately framed event read as two unrelated posts;
 * and the frame is the feed's plain quote border, not a raised card, so the RSVP never nests a
 * box inside a box.
 *
 * [appointment] draws the event once the front end has it (no header of its own — the post's
 * author is who answered, and a second avatar row made it read like a quote of a quote). Until
 * then the strip stands over a muted "loading" line. [statusDetail] is the strip's trailing text,
 * e.g. "in 3 days". [onClick] makes the whole frame open the event.
 */
@Composable
fun CalendarRsvpCard(
    event: CalendarRSVPEvent,
    statusDetail: String? = null,
    onClick: (() -> Unit)? = null,
    appointment: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val status = remember(event) { event.status() }

    val frame =
        if (onClick != null) {
            MaterialTheme.colorScheme.replyModifier.clickable(onClick = onClick)
        } else {
            MaterialTheme.colorScheme.replyModifier
        }

    Column {
        if (event.content.isNotBlank()) {
            Text(
                text = event.content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Column(frame) {
            RsvpStatusStrip(status, statusDetail)

            if (appointment != null) {
                appointment()
            } else {
                Text(
                    text = stringResource(Res.string.calendar_rsvp_loading_event),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun RsvpStatusStrip(
    status: RSVPStatusTag.STATUS?,
    detail: String?,
) {
    val (label, color, symbol) = rsvpStatusLook(status)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = color,
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class RsvpStatusLook(
    val label: String,
    val color: Color,
    val symbol: MaterialSymbol,
)

@Composable
private fun rsvpStatusLook(status: RSVPStatusTag.STATUS?): RsvpStatusLook =
    when (status) {
        RSVPStatusTag.STATUS.ACCEPTED ->
            RsvpStatusLook(stringResource(Res.string.calendar_rsvp_going), MaterialTheme.colorScheme.primary, MaterialSymbols.CheckCircle)
        RSVPStatusTag.STATUS.TENTATIVE ->
            RsvpStatusLook(stringResource(Res.string.calendar_rsvp_maybe), MaterialTheme.colorScheme.tertiary, MaterialSymbols.Schedule)
        RSVPStatusTag.STATUS.DECLINED ->
            RsvpStatusLook(stringResource(Res.string.calendar_rsvp_not_going), MaterialTheme.colorScheme.error, MaterialSymbols.Cancel)
        null ->
            RsvpStatusLook("—", Color.Gray, MaterialSymbols.CalendarMonth)
    }
