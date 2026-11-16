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
package com.vitorpamplona.amethyst.commons.model.cordnGroups

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.Note

/**
 * The Messages-list row for a cordn room, carrying the one thing a plain [Note]
 * cannot work out for itself: when the room last said something.
 *
 * A cordn row has no event. Its messages are MLS envelopes from a coordinator
 * and never enter `LocalCache`, so `Note.createdAt()` — which reads
 * `event?.createdAt` — returns null for it. The Messages feed sorts on exactly
 * that value and maps null to `0L`, so every cordn room used to sink to the
 * bottom of the inbox permanently, tie-broken alphabetically by [idHex] against
 * the other cordn rooms. The row itself displayed the right time the whole
 * while; only the sort could not see it.
 *
 * Overriding [createdAt] is how the other event-less inbox rows already solve
 * this — see `RelayGroupServerRoomNote` and `ConcordServerRoomNote`, both of
 * which mirror their newest message for the same reason. cordn is the one that
 * was missing it.
 */
@Stable
class CordnInboxRowNote(
    val room: CordnGroupChatroom,
) : Note(CordnGroupChatroom.rowIdHex(room.coordinatorPubKey, room.gid)) {
    override fun createdAt(): Long? =
        room.newest.value
            ?.envelope
            ?.createdAt
}
