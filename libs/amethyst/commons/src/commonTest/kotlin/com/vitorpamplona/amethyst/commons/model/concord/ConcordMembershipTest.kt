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
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals

class ConcordMembershipTest {
    private val owner = "0f".repeat(32)
    private val admin = "a1".repeat(32)
    private val member = "b2".repeat(32)
    private val banned = "c3".repeat(32)
    private val stranger = "d4".repeat(32)
    private val adminRole = "11".repeat(32)

    private fun ed(
        kind: ControlEntityKind,
        eid: String,
        content: String,
        author: String = owner,
    ) = ControlEdition(kind, eid.hexToByteArray(), 0, null, null, content, author, "r-$eid", 0)

    private val communityId = "cc".repeat(32).hexToByteArray()

    private val authority =
        AuthorityResolver.resolve(
            listOf(
                ed(ControlEntityKind.ROLE, adminRole, """{"name":"Admin","position":1,"permissions":"25"}"""), // KICK|BAN|MANAGE_ROLES
                ed(
                    ControlEntityKind.GRANT,
                    ConcordKeyDerivation.grantCoordinate(communityId, admin.hexToByteArray()).toHexKey(),
                    """{"member":"$admin","role_ids":["$adminRole"]}""",
                ),
                ed(ControlEntityKind.BANLIST, ConcordKeyDerivation.banlistCoordinate(communityId).toHexKey(), """["$banned"]"""),
            ),
            communityId,
            owner,
        )

    @Test
    fun classifiesStandingFromAuthority() {
        assertEquals(ConcordMembership.OWNER, ConcordMembership.of(authority, owner))
        assertEquals(ConcordMembership.ADMIN, ConcordMembership.of(authority, admin))
        assertEquals(ConcordMembership.MEMBER, ConcordMembership.of(authority, member))
        assertEquals(ConcordMembership.BANNED, ConcordMembership.of(authority, banned))
        // A user we don't hold the key for reads as NONE.
        assertEquals(ConcordMembership.NONE, ConcordMembership.of(authority, stranger, holdsKey = false))
    }

    @Test
    fun capabilityHelpers() {
        assertEquals(true, ConcordMembership.OWNER.canModerate())
        assertEquals(true, ConcordMembership.ADMIN.canModerate())
        assertEquals(false, ConcordMembership.MEMBER.canModerate())
        assertEquals(true, ConcordMembership.MEMBER.isMember())
        assertEquals(false, ConcordMembership.BANNED.isMember())
    }
}
