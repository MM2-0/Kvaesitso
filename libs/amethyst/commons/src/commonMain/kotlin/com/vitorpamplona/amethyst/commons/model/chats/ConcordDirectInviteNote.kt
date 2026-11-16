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
import com.vitorpamplona.amethyst.commons.model.concord.ConcordDirectInviteView
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A synthetic row for a Direct Invite (CORD-05 §6) still waiting for an answer: a New Request on
 * Messages and a card on Notifications. Both surfaces render the same Accept / Decline decision the
 * Concord hub shows.
 *
 * It is not a real event: [event] stays null. The gift wrap carrying the invite can't stand in for
 * it, because its author is a throwaway key and its `created_at` is randomized up to two days back
 * (NIP-59), so [createdAt] is the rumor's own time instead. [idHex] is stable per wrap, so feed
 * diffing and LazyColumn keys treat it as the same row across refreshes.
 */
class ConcordDirectInviteNote(
    val invite: ConcordDirectInviteView,
) : Note(idFor(invite.wrapId)) {
    override fun createdAt(): Long = invite.opened.sentAt

    companion object {
        fun idFor(wrapId: HexKey): HexKey = "concorddirectinvite-$wrapId"
    }
}
