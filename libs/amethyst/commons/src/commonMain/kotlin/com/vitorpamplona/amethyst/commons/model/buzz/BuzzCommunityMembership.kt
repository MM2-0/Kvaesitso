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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Process-wide registry of **Buzz community (relay-wide) membership**, from the NIP-43 relay-member
 * events a Buzz relay signs: the full roster snapshot ([updateSnapshot], kind 13534) and the
 * incremental add/remove deltas ([applyDelta], kinds 8000/8001).
 *
 * Buzz tracks membership at two levels: a per-channel NIP-29 roster (39001/39002) AND this
 * community-wide list. A community member can read and post across the community's channels without
 * appearing in any per-channel roster, so [RelayGroupChannel.membershipOf] consults this as a
 * fallback — otherwise a community member (e.g. an admin) reads as `NONE` and is wrongly gated.
 *
 * Roles are intentionally dropped: Quartz's `MemberTag`/`PTag` parsers expose only the pubkey, and
 * for the participation gate plain membership is enough — mapping a community member to `MEMBER`
 * (never channel `ADMIN`) avoids over-granting per-channel moderation.
 *
 * Ordering is last-writer-wins on `created_at`: the newest snapshot-or-delta a relay has emitted
 * wins, and older out-of-order arrivals are ignored. Buzz republishes the 13534 snapshot after every
 * membership change, so the snapshot is normally the freshest event and is authoritative.
 *
 * Like `LocalCache`/`BuzzRelayDialect`, this is one copy per process (populated only in the main app
 * process). Marks are scoped per relay (community) since a member may belong to several.
 */
object BuzzCommunityMembership {
    private val lock = KmpLock()

    private class Roster {
        val members = HashSet<HexKey>()

        /** `created_at` of the last snapshot-or-delta applied; guards against out-of-order arrivals. */
        var version = 0L
    }

    private val rosters = HashMap<NormalizedRelayUrl, Roster>()

    /** Whether [pubkey] is a community member of [relay] per the relay-signed NIP-43 roster. */
    fun isMember(
        relay: NormalizedRelayUrl,
        pubkey: HexKey,
    ): Boolean = lock.withLock { rosters[relay]?.members?.contains(pubkey) == true }

    /**
     * Apply a full roster snapshot (kind 13534). Replaces the relay's member set when [createdAt] is at
     * least as new as the last applied event. Returns true when the effective set changed.
     */
    fun updateSnapshot(
        relay: NormalizedRelayUrl,
        members: Set<HexKey>,
        createdAt: Long,
    ): Boolean =
        lock.withLock {
            val roster = rosters.getOrPut(relay) { Roster() }
            if (createdAt < roster.version) return false
            val changed = roster.members != members
            roster.members.clear()
            roster.members.addAll(members)
            roster.version = createdAt
            changed
        }

    /**
     * Apply an incremental delta (kind 8000 add / 8001 remove) on top of the current set, when
     * [createdAt] is at least as new as the last applied event. Returns true when the set changed.
     */
    fun applyDelta(
        relay: NormalizedRelayUrl,
        add: Set<HexKey> = emptySet(),
        remove: Set<HexKey> = emptySet(),
        createdAt: Long,
    ): Boolean =
        lock.withLock {
            val roster = rosters.getOrPut(relay) { Roster() }
            if (createdAt < roster.version) return false
            var changed = false
            if (add.isNotEmpty()) changed = roster.members.addAll(add) || changed
            if (remove.isNotEmpty()) changed = roster.members.removeAll(remove) || changed
            roster.version = createdAt
            changed
        }

    /** Test-only: clears all rosters so unit tests don't leak state into each other. */
    fun clearForTesting() = lock.withLock { rosters.clear() }
}
