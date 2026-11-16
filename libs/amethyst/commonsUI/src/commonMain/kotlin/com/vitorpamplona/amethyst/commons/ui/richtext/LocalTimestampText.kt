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
package com.vitorpamplona.amethyst.commons.ui.richtext

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.local_timestamp_ago
import com.vitorpamplona.amethyst.commons.resources.local_timestamp_in
import com.vitorpamplona.amethyst.commons.resources.now
import com.vitorpamplona.amethyst.commons.richtext.LocalTimestamp
import com.vitorpamplona.amethyst.commons.richtext.TimestampSegment
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.note.timeAheadNoDot
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * A word carrying `<t:…>` tokens, with each token shown as a local time.
 *
 * The instant is formatted here, at render time, in the reader's locale and
 * time zone, which is the point of the syntax: the sender wrote an instant, not
 * a string. A relative token (`R`) keeps ticking while it is on screen.
 */
@Composable
fun LocalTimestampText(segment: TimestampSegment) {
    val hasRelative = segment.pieces.any { it is LocalTimestamp.Piece.Time && it.timestamp.style == LocalTimestamp.Style.RELATIVE }

    // Only a relative token needs a clock; the absolute styles never change on screen.
    val now by
        produceState(TimeUtils.now(), hasRelative) {
            if (!hasRelative) return@produceState
            while (true) {
                delay(RELATIVE_TICK_MS)
                value = TimeUtils.now()
            }
        }

    val accent = MaterialTheme.colorScheme.primary
    val ago = stringRes(Res.string.local_timestamp_ago, "%1\$s")
    val ahead = stringRes(Res.string.local_timestamp_in, "%1\$s")

    val rendered =
        segment.pieces.map { piece ->
            when (piece) {
                is LocalTimestamp.Piece.Text -> piece.text to false
                is LocalTimestamp.Piece.Time -> formatLocalTimestamp(piece.timestamp, now, ago, ahead) to true
            }
        }

    Text(
        buildAnnotatedString {
            rendered.forEach { (text, isTime) ->
                if (isTime) {
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) { append(text) }
                } else {
                    append(text)
                }
            }
        },
    )
}

@Composable
private fun formatLocalTimestamp(
    timestamp: LocalTimestamp,
    now: Long,
    agoTemplate: String,
    aheadTemplate: String,
): String {
    val millis = timestamp.unixSeconds * 1000
    return when (timestamp.style) {
        LocalTimestamp.Style.SHORT_TIME -> formatDateTime(millis, DateTimeStyle.NONE, DateTimeStyle.SHORT)
        LocalTimestamp.Style.LONG_TIME -> formatDateTime(millis, DateTimeStyle.NONE, DateTimeStyle.MEDIUM)
        LocalTimestamp.Style.SHORT_DATE -> formatDateTime(millis, DateTimeStyle.SHORT, DateTimeStyle.NONE)
        LocalTimestamp.Style.LONG_DATE -> formatDateTime(millis, DateTimeStyle.LONG, DateTimeStyle.NONE)
        LocalTimestamp.Style.SHORT_DATE_TIME -> formatDateTime(millis, DateTimeStyle.LONG, DateTimeStyle.SHORT)
        // The platform styles stop at LONG, which spells the month out but drops
        // the weekday; that is the closest predefined form on every target.
        LocalTimestamp.Style.LONG_DATE_TIME -> formatDateTime(millis, DateTimeStyle.LONG, DateTimeStyle.SHORT)
        LocalTimestamp.Style.COMPACT_DATE_TIME -> formatDateTime(millis, DateTimeStyle.SHORT, DateTimeStyle.SHORT)
        LocalTimestamp.Style.COMPACT_DATE_TIME_SECONDS -> formatDateTime(millis, DateTimeStyle.SHORT, DateTimeStyle.MEDIUM)
        LocalTimestamp.Style.RELATIVE ->
            if (abs(timestamp.unixSeconds - now) < TimeUtils.ONE_MINUTE) {
                stringRes(Res.string.now)
            } else if (abs(timestamp.unixSeconds - now) >= TimeUtils.ONE_MONTH) {
                // The short forms stop at weeks and turn into a date beyond; "Jul 22 ago"
                // reads wrong, so a time that far out is shown as its date.
                formatDateTime(millis, DateTimeStyle.MEDIUM, DateTimeStyle.NONE)
            } else if (timestamp.unixSeconds > now) {
                aheadTemplate.replace("%1\$s", timeAheadNoDot(timestamp.unixSeconds))
            } else {
                agoTemplate.replace("%1\$s", timeAgoNoDot(timestamp.unixSeconds))
            }
    }
}

private const val RELATIVE_TICK_MS = 30_000L
