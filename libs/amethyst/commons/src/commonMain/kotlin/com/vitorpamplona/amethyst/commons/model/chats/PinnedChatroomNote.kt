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
package com.vitorpamplona.amethyst.commons.model.chats

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey

/**
 * A synthetic Messages-list row for a pinned DM [room] that has no message loaded yet.
 *
 * NIP-17 messages arrive as gift wraps addressed to me, so they cannot be fetched per counterpart:
 * the inbox only ever sees the newest few hundred wraps. A pinned conversation whose last message is
 * older than that window would otherwise vanish from Messages even though the user asked to keep it
 * on top. This row keeps it there until a real message for the room arrives and replaces it.
 *
 * It is not a real event: [event] stays null and [createdAt] is null, so it never lights an unread
 * dot and sorts below the pinned rooms that do have a message. Keyed by a stable [idHex] so feed
 * diffing and the LazyColumn treat it as the same row across refreshes.
 */
class PinnedChatroomNote(
    val room: ChatroomKey,
) : Note(idFor(room)) {
    companion object {
        fun idFor(room: ChatroomKey): HexKey = "pinnedroom-" + room.users.sorted().joinToString(",")
    }
}
