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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A user's standing in a Concord community, derived from the locally-folded
 * Control Plane (the owner-rooted [AuthorityResolver] + banlist).
 *
 * Unlike NIP-29 (where the relay's signed roster is the truth), Concord
 * membership is **key possession**: anyone holding the community key is at least a
 * [MEMBER]. Roles layer moderation power on top ([ADMIN]/[OWNER]); the banlist
 * removes standing ([BANNED]). [NONE] is only for a user we can't place at all.
 */
enum class ConcordMembership {
    /** The community founder — supreme, unremovable. */
    OWNER,

    /** Holds at least one moderation permission (manage roles/channels, kick, ban…). */
    ADMIN,

    /** Holds the community key, no elevated role. */
    MEMBER,

    /** On the community banlist — dropped everywhere. */
    BANNED,

    /** Not placeable in this community. */
    NONE,
    ;

    /** True when the user is an active participant (holds the key and isn't banned). */
    fun isMember(): Boolean = this == OWNER || this == ADMIN || this == MEMBER

    /** True when the user may take moderation actions. */
    fun canModerate(): Boolean = this == OWNER || this == ADMIN

    companion object {
        private val MOD_BITS =
            intArrayOf(
                ConcordPermissions.MANAGE_ROLES,
                ConcordPermissions.MANAGE_CHANNELS,
                ConcordPermissions.MANAGE_METADATA,
                ConcordPermissions.KICK,
                ConcordPermissions.BAN,
                ConcordPermissions.MANAGE_MESSAGES,
            )

        /**
         * Classifies [pubKey] against a folded [authority]. [holdsKey] tells us the
         * user is a member of this community locally (we joined it / hold its key),
         * which distinguishes a plain [MEMBER] from [NONE].
         */
        fun of(
            authority: AuthorityResolver,
            pubKey: HexKey,
            holdsKey: Boolean = true,
        ): ConcordMembership {
            if (authority.isBanned(pubKey)) return BANNED
            if (authority.isOwner(pubKey)) return OWNER
            val perms = authority.effectivePermissions(pubKey)
            if (MOD_BITS.any { perms.has(it) }) return ADMIN
            return if (holdsKey) MEMBER else NONE
        }
    }
}
