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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Counts above this render as "N+" so a very busy room doesn't blow out the row. */
const val CHAT_UNREAD_CAP = 99

/**
 * The unread pill every chat list shares: Concord channels, Marmot groups.
 *
 * Sizing is `sizeIn` + padding rather than a fixed `size`, so "99+" widens the
 * pill instead of being clipped by it, and the label rides `labelSmall` rather
 * than a hardcoded sp so it still tracks the reader's font scale.
 *
 * [contentDescription] is the caller's, because the plural that reads well out
 * loud is per-surface ("3 unread messages" vs "3 unread invites"); the pill
 * itself is the same object everywhere.
 */
@Composable
fun ChatUnreadBadge(
    count: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val label = if (count > CHAT_UNREAD_CAP) "$CHAT_UNREAD_CAP+" else count.toString()
    Box(
        modifier =
            modifier
                .semantics { this.contentDescription = contentDescription }
                // Smoothly grows/shrinks as the count changes digits (1 → 2 → … → 99+) instead of
                // snapping — a small touch that makes new activity feel noticed.
                .animateContentSize()
                .sizeIn(minWidth = 20.dp, minHeight = 20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
