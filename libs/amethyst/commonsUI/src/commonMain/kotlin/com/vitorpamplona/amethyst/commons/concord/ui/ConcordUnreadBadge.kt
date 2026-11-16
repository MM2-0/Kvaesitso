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
package com.vitorpamplona.amethyst.commons.concord.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.chats.ui.CHAT_UNREAD_CAP
import com.vitorpamplona.amethyst.commons.chats.ui.ChatUnreadBadge
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.concord_unread_messages
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes

/**
 * A small pill showing an unread-message [count] (new messages since this account last read).
 * Renders nothing when [count] is zero, so callers can place it unconditionally. Capped at
 * [CHAT_UNREAD_CAP]+ and carries a plural content description for screen readers.
 *
 * The pill itself is [ChatUnreadBadge], shared with the other chat lists; this
 * wrapper only supplies Concord's own plural so the two surfaces cannot drift
 * apart visually.
 */
@Composable
fun ConcordUnreadBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    ChatUnreadBadge(
        count = count,
        contentDescription = pluralStringRes(Res.plurals.concord_unread_messages, count, count),
        modifier = modifier,
    )
}
