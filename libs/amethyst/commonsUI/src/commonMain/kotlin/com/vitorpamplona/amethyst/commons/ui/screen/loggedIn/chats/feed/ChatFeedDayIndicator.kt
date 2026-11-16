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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.chats.ui.ChatDivisor
import com.vitorpamplona.amethyst.commons.chats.ui.chatDayKey
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.today
import com.vitorpamplona.amethyst.commons.resources.yesterday
import com.vitorpamplona.amethyst.commons.ui.note.chatDayLabelWith
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font12SP
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.quartz.utils.TimeUtils

// Where the pill floats, and the band under the top edge it owns: a day divider entering that band
// takes over from the pill instead of being covered by it.
private val ChatDayPillTopPadding = 8.dp
private val ChatDayPillZone = 48.dp
private val ChatDayPillShape = RoundedCornerShape(50)
private val ChatDayPillInset = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)

// The padding under a ChatDivisor's label (StdPadding 10dp + HalfPadding 5dp): the pill takes over
// as soon as the label itself has scrolled off, not when the empty padding under it has too.
private val ChatDividerPaddingBelowLabel = 15.dp

/**
 * What the day indicator shows: the day of row [rowIndex], whether it is on screen, and how far the
 * docked bar is pushed up (0 or negative) by the next day's divider.
 */
@Immutable
internal data class ChatDayIndicatorState(
    val rowIndex: Int,
    val visible: Boolean,
    val offsetPx: Int,
)

/**
 * Works out the day indicator from where the rows of a reverse-laid-out chat feed sit.
 *
 * Every row that opens a day carries a divider at its top ([rowCount] - 1, the oldest loaded row,
 * always does). The top row is the one with the highest index; its day is the one to show.
 *
 * [docked] is the compact layout's bar: always shown, it sits over the top row's divider while that
 * divider is still in view and is pushed up by the next day's divider, the way a sticky section
 * header is. The pill is shown once the top row's own divider label has scrolled away
 * ([dividerLabelBottomPx] is how far below a divider's top its label ends), and while no other
 * divider is in the band under the top edge.
 */
internal fun chatDayIndicatorState(
    info: LazyListLayoutInfo,
    rowCount: Int,
    docked: Boolean,
    barHeightPx: Int,
    pillZonePx: Int,
    dividerLabelBottomPx: Int,
    dayKeyAt: (Int) -> Int?,
): ChatDayIndicatorState? {
    if (rowCount == 0) return null
    val items = info.visibleItemsInfo

    // The trailing items past the rows (the older-history boundary) carry no day.
    var top: LazyListItemInfo? = null
    for (i in items.indices) {
        val item = items[i]
        if (item.index < rowCount && (top == null || item.index > top.index)) top = item
    }
    if (top == null) return null

    // reverseLayout: offsets run up from the bottom, so the viewport's top sits at viewportEndOffset.
    fun topEdgeOf(item: LazyListItemInfo): Int = info.viewportEndOffset - (item.offset + item.size)

    fun opensDay(index: Int): Boolean = index == rowCount - 1 || dayKeyAt(index + 1) != dayKeyAt(index)

    val topDividerTop = if (opensDay(top.index)) topEdgeOf(top) else null

    // The divider closest under the top row: where the next (newer) day starts.
    var nextIndex = -1
    var nextDividerTop: Int? = null
    for (i in items.indices) {
        val item = items[i]
        if (item.index < top.index && item.index > nextIndex && opensDay(item.index)) {
            nextIndex = item.index
            nextDividerTop = topEdgeOf(item)
        }
    }

    return if (docked) {
        var offset = if (topDividerTop != null && topDividerTop > 0) topDividerTop else 0
        if (nextDividerTop != null) offset = minOf(offset, nextDividerTop - barHeightPx)
        ChatDayIndicatorState(top.index, visible = true, offsetPx = offset)
    } else {
        val ownDividerShowing = topDividerTop != null && topDividerTop + dividerLabelBottomPx > 0
        val nextDividerNear = nextDividerTop != null && nextDividerTop < pillZonePx
        ChatDayIndicatorState(top.index, visible = !ownDividerShowing && !nextDividerNear, offsetPx = 0)
    }
}

/**
 * Pins the day of the messages at the top of a chat feed, so a time-only stamp ("5:20 PM") is never
 * read as today's when the day divider above it has scrolled away.
 *
 * - [docked] false (the bubble layout): a floating pill, as messengers do. Bubbles leave gutters
 *   and read as content, so a small pill over them reads as chrome.
 * - [docked] true (the compact layout): an opaque bar in the divider's own style, a sticky section
 *   header. Compact rows are text edge to edge with no gutter for a pill to float in.
 *
 * Drawn over the list, inside the feed, so it sits under any bars the screen stacks above the feed.
 */
@Composable
fun BoxScope.ChatFeedDayIndicator(
    listState: LazyListState,
    rows: List<Note>,
    docked: Boolean,
) {
    val latestRows by rememberUpdatedState(rows)
    ChatFeedDayIndicator(listState, rows.size, docked) { index -> latestRows.getOrNull(index)?.createdAt() }
}

/**
 * [ChatFeedDayIndicator] over any list of [rowCount] rows, the newest at index 0, where
 * [createdAtOf] gives a row's Unix timestamp. Rows past [rowCount] (a trailing loader) carry no day.
 */
@Composable
fun BoxScope.ChatFeedDayIndicator(
    listState: LazyListState,
    rowCount: Int,
    docked: Boolean,
    createdAtOf: (index: Int) -> Long?,
) {
    val latestCreatedAtOf by rememberUpdatedState(createdAtOf)
    val density = LocalDensity.current
    val pillZonePx = remember(density) { with(density) { (ChatDayPillTopPadding + ChatDayPillZone).roundToPx() } }
    val paddingBelowLabelPx = remember(density) { with(density) { ChatDividerPaddingBelowLabel.roundToPx() } }
    // The height of a ChatDivisor at the current font size, measured, not guessed: the docked bar
    // is one, and the pill measures an invisible one.
    var barHeightPx by remember { mutableIntStateOf(0) }

    val state by remember(listState, rowCount, docked) {
        derivedStateOf {
            val labelBottomPx = (barHeightPx - paddingBelowLabelPx).coerceAtLeast(0)
            chatDayIndicatorState(listState.layoutInfo, rowCount, docked, barHeightPx, pillZonePx, labelBottomPx) { index ->
                latestCreatedAtOf(index)?.let(::chatDayKey)
            }
        }
    }

    val today = stringRes(Res.string.today)
    val yesterday = stringRes(Res.string.yesterday)
    val createdAt = state?.rowIndex?.let { latestCreatedAtOf(it) }

    val computed = remember(createdAt, today, yesterday) { createdAt?.let { chatDayLabelWith(it, TimeUtils.nowMillis(), today, yesterday) } }
    // The last label, kept so the pill doesn't go blank while it fades out. Plain storage, not
    // state: it is only read in this same composition.
    val lastLabel = remember { arrayOf("") }
    if (computed != null) lastLabel[0] = computed
    val label = computed ?: lastLabel[0]

    val current = state
    if (docked) {
        if (current != null && createdAt != null) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .offset { IntOffset(0, current.offsetPx) }
                    .background(MaterialTheme.colorScheme.background)
                    .onSizeChanged { barHeightPx = it.height },
            ) {
                ChatDivisor(label)
            }
        }
    } else {
        Box(Modifier.alpha(0f).onSizeChanged { barHeightPx = it.height }) {
            ChatDivisor(label)
        }
        AnimatedVisibility(
            visible = current?.visible == true && createdAt != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = ChatDayPillTopPadding),
        ) {
            ChatDayPill(label)
        }
    }
}

@Composable
private fun ChatDayPill(label: String) {
    // The screen's own background with an outline, not a grey fill: a grey pill all but vanished
    // over the grey bubbles of other people in the dark theme. Same type as the dividers' labels.
    Surface(
        shape = ChatDayPillShape,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Text(
            text = label,
            fontSize = Font12SP,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = ChatDayPillInset,
        )
    }
}
