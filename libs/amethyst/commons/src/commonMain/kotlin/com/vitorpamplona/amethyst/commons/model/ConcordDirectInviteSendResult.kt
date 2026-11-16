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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.concord.cord05Invites.CommunityInvite

/** A Direct Invite bundle ready to wrap, or why this account may not send one (see `ConcordActions.draftDirectInvite`). */
sealed interface ConcordDirectInviteDraft {
    class Ready(
        val invite: CommunityInvite,
    ) : ConcordDirectInviteDraft

    class Refused(
        val reason: ConcordDirectInviteSendResult,
    ) : ConcordDirectInviteDraft
}

/** The outcome of sending a Concord Direct Invite (CORD-05 §6), so the UI can say why it failed. */
enum class ConcordDirectInviteSendResult {
    /** At least one of the recipient's inbox relays accepted the wrap. */
    SENT,

    /** This account can't sign (read-only key). */
    NOT_WRITEABLE,

    /** The recipient isn't a valid 32-byte pubkey. */
    INVALID_RECIPIENT,

    /** We don't hold this community, it was dissolved, or its roster bans us. */
    NOT_MEMBER,

    /** The community's Control Plane hasn't folded yet, so which keys the recipient may receive is unknown. */
    ROSTER_NOT_LOADED,

    /** The community's roster bans the recipient; their join would be refused anyway. */
    RECIPIENT_BANNED,

    /** No inbox relay accepted the wrap. */
    NOT_DELIVERED,
}
