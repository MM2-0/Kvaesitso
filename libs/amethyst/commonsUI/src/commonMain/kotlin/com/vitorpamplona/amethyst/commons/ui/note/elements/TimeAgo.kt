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
package com.vitorpamplona.amethyst.commons.ui.note.elements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeAgoLabels
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeOfDayFormatter
import com.vitorpamplona.amethyst.commons.ui.note.timeAbsoluteWith
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoShort
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoWith
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText

/**
 * Which relative-time format to use when the timestamp is *not* toggled to absolute.
 * Absolute-mode uses [timeAbsolute] (dotted) or [timeAbsoluteNoDot] regardless.
 */
enum class TimeAgoStyle {
    /** "• 5m", uses [timeAgo]. Used by note timestamps and chatroom row last-message time. */
    Dotted,

    /**
     * "• 5m" without the leading space — for rows whose `Arrangement.spacedBy`
     * already provides the gap before the timestamp (the note-header first row).
     */
    DottedTight,

    /** "5m" (no dot, no leading space), uses [timeAgoShort]. Used by chat bubbles and channel headers. */
    Short,
}

/**
 * Core toggleable timestamp Text. All on-screen TimeAgo composables delegate to this.
 *
 * Behaviour:
 * - Renders the timestamp relative to "now" (subscribing to [LocalNowSeconds]); on click,
 *   flips to an absolute date/time formatted by [timeAbsolute] / [timeAbsoluteNoDot].
 * - When displaying the absolute form we **stop subscribing to the tick** — the
 *   `derivedStateOf` lambda doesn't read `nowState.value` in that branch, so the Text
 *   does not recompose every 30s for items the user has "frozen" to a specific time.
 * - Uses [remember] (not `rememberSaveable`): the toggle is a transient peek, not state
 *   worth saving to the SavedStateRegistry for every visible+scrolled-past note in the
 *   feed. Recycling resets to relative, which matches how peeking should work.
 * - A single [MutableInteractionSource] per item, no ripple — tiny timestamp Text
 *   doesn't benefit from the indication.
 *
 * Stable params: all values, so the Compose compiler can skip this entirely when nothing changes.
 */
@Composable
fun ToggleableTimeAgoText(
    timestamp: Long,
    modifier: Modifier = Modifier,
    style: TimeAgoStyle = TimeAgoStyle.Dotted,
    color: Color = LocalContentColor.current,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    // When false, the text is not itself clickable, so an enclosing tap target (e.g. the
    // chat timestamp opening the relay/delivery dialog) receives the tap instead.
    toggleable: Boolean = true,
) {
    val timeOfDay = rememberTimeOfDayFormatter()
    val nowState = LocalNowSeconds.current
    val labels = rememberTimeAgoLabels()
    val interactionSource = remember { MutableInteractionSource() }
    var showAbsolute by remember(timestamp) { mutableStateOf(false) }

    val text by
        remember(timestamp, timeOfDay, style, nowState, labels) {
            derivedStateOf {
                if (showAbsolute) {
                    when (style) {
                        TimeAgoStyle.Dotted -> timeAbsoluteWith(timestamp, timeOfDay, labels.never)
                        TimeAgoStyle.DottedTight -> timeAbsoluteWith(timestamp, timeOfDay, labels.never).trimStart()
                        TimeAgoStyle.Short -> timeAbsoluteWith(timestamp, timeOfDay, labels.never, prefix = "")
                    }
                } else {
                    // Read nowState only when displaying a relative time, so an item
                    // toggled to absolute doesn't recompose on every 30-second tick.
                    nowState.value
                    when (style) {
                        TimeAgoStyle.Dotted -> timeAgoWith(timestamp, labels)
                        TimeAgoStyle.DottedTight -> timeAgoWith(timestamp, labels).trimStart()
                        TimeAgoStyle.Short -> timeAgoShort(timestamp, labels)
                    }
                }
            }
        }

    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        maxLines = maxLines,
        overflow = overflow,
        modifier =
            if (toggleable) {
                modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { showAbsolute = !showAbsolute }
            } else {
                modifier
            },
    )
}

@Composable
fun TimeAgo(
    note: Note,
    style: TimeAgoStyle = TimeAgoStyle.Dotted,
) {
    val time = note.createdAt() ?: return
    TimeAgo(time, style)
}

@Composable
fun TimeAgo(
    time: Long,
    style: TimeAgoStyle = TimeAgoStyle.Dotted,
) {
    ToggleableTimeAgoText(
        timestamp = time,
        style = style,
        color = MaterialTheme.colorScheme.placeholderText,
    )
}

@Composable
fun NormalTimeAgo(
    baseNote: Note,
    modifier: Modifier,
) {
    val time = baseNote.createdAt() ?: 0L
    ToggleableTimeAgoText(
        timestamp = time,
        style = TimeAgoStyle.Short,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
