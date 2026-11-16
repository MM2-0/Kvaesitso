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

/**
 * A synthetic Messages-list row that collapses ALL of a user's channels in one Concord
 * [communityId] into a single entry — the "grouped by community" view mode
 * ([com.vitorpamplona.amethyst.commons.model.concord.ConcordViewMode.GROUPED]). It is the
 * Concord analog of [RelayGroupServerRoomNote] (NIP-29 groups by host relay; Concord groups
 * by community).
 *
 * It is not a real event: [event] stays null and [createdAt] mirrors [newestMessage] (the
 * newest decrypted message across that community's channels) so the row interleaves with DMs
 * and other chats by recency. Tapping it opens the community's channel list. Exactly one
 * instance exists per community — keyed by a stable [idHex] so feed diffing and the LazyColumn
 * treat it as the same row across refreshes.
 */
class ConcordServerRoomNote(
    val communityId: HexKey,
    val newestMessage: Note?,
) : Note(idFor(communityId)) {
    override fun createdAt(): Long? = newestMessage?.createdAt()

    companion object {
        fun idFor(communityId: HexKey): HexKey = "concordserver-$communityId"
    }
}
