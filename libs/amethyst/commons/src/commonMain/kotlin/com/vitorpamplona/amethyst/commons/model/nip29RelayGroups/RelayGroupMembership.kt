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
package com.vitorpamplona.amethyst.commons.model.nip29RelayGroups

/**
 * A user's membership in a NIP-29 group, derived from the relay's own signed
 * lists (kind 39001 admins / 39002 members). This is the relay's truth, not the
 * client's kind-10009 intent.
 *
 * [PENDING] is a client-side, optimistic state: the user sent a join request
 * (kind 9021) but the relay hasn't added them to the roster yet (a closed group
 * awaiting approval, or an open group we haven't re-fetched). It is never read
 * from a relay event.
 */
enum class RelayGroupMembership {
    /** Holds the `admin` role — full control. */
    ADMIN,

    /** Holds the `moderator` role — can moderate. */
    MODERATOR,

    /** In the members list, no elevated role. */
    MEMBER,

    /** Join request sent, not yet admitted (optimistic, client-side only). */
    PENDING,

    /** Not in the roster. */
    NONE,
    ;

    /** True when the relay currently considers the user part of the group. */
    fun isMember(): Boolean = this == ADMIN || this == MODERATOR || this == MEMBER

    /** True when the user may moderate (remove users, delete messages, invite). */
    fun canModerate(): Boolean = this == ADMIN || this == MODERATOR

    companion object {
        const val ROLE_ADMIN = "admin"
        const val ROLE_MODERATOR = "moderator"

        /**
         * Buzz's top role. Its hierarchy is `owner` > `admin` > `member` (no moderator), so the
         * channel's creator carries `owner` and never the literal `admin` — which used to leave the
         * one person with full authority classified below it, unable to promote anyone.
         */
        const val ROLE_OWNER = "owner"

        /** Role strings that mean full authority over the group, across both dialects. */
        val ADMIN_ROLES = listOf(ROLE_ADMIN, ROLE_OWNER)
    }
}
