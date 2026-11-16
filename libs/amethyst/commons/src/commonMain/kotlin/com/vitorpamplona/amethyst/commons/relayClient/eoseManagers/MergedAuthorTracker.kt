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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Remembers which accounts a merged filter last covered on each relay, and reports when that set
 * *gains* a member.
 *
 * A merged subscription keeps one EOSE cursor per relay. That is correct while the set of accounts it
 * covers is stable, and silently wrong the moment it grows: the account that joins inherits a cursor
 * earned by the accounts already there, so its `since` skips everything older — the app opens the
 * subscription, believes it is live, and never asks for the history the new account actually needs.
 *
 * It is the normal startup path, not an edge case. The account on screen mounts from Compose and
 * EOSEs within a second or two; the other accounts arrive from the registry a moment later and land on
 * relays that have already reported EOSE.
 *
 * Growth is the only trigger. An account *leaving* takes nothing with it — the accounts that remain
 * already have their events, so their cursor stays valid.
 */
class MergedAuthorTracker {
    private val lastSeen = mutableMapOf<NormalizedRelayUrl, Set<HexKey>>()

    /**
     * Records [authors] as the current set for [relay] and returns true when it contains someone the
     * previous set did not — i.e. the caller should drop that relay's EOSE and refetch from scratch.
     *
     * False the first time a relay is seen: there is no cursor to invalidate yet.
     */
    fun gainedAuthors(
        relay: NormalizedRelayUrl,
        authors: Collection<HexKey>,
    ): Boolean {
        val current = authors.toSet()
        val previous = lastSeen.put(relay, current)
        return previous != null && !previous.containsAll(current)
    }

    /** Forgets everything, for teardown. */
    fun clear() = lastSeen.clear()
}
