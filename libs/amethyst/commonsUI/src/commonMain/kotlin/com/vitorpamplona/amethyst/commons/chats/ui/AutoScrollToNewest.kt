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
package com.vitorpamplona.amethyst.commons.chats.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Keeps a reverse-laid-out chat sitting on its newest message.
 *
 * [newest] is whatever identifies the bottom-most row — a note, a message id,
 * anything whose equality changes when a message arrives. In a `reverseLayout`
 * list the newest row is index 0, and a list anchors itself to the row that was
 * already first visible, so without this a sent message lands just below the
 * viewport and the sender never sees it.
 *
 * Someone else's message only pulls the view down when the reader is already at
 * the bottom. A reader who scrolled up into history is deliberately left there:
 * yanking them away because somebody else typed is worse than a missed arrival,
 * which the unread divider covers anyway. Index 1 counts as "at the bottom"
 * because a row that just arrived has already pushed the previous newest up one.
 *
 * [mine] lifts that guard for the reader's own message, and is the whole reason
 * this takes a flag. Sending is an explicit act with an obvious expectation —
 * every chat app puts you on what you just sent — so someone who scrolls up,
 * types and sends must land on it rather than be left reading history with the
 * message they just wrote somewhere off-screen below.
 */
@Composable
fun AutoScrollToNewest(
    listState: LazyListState,
    newest: Any?,
    mine: Boolean = false,
) {
    LaunchedEffect(newest) {
        if (mine || listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }
}
