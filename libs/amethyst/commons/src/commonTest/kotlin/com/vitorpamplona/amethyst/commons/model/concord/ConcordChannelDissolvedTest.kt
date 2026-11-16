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

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A member of a **dissolved** Concord community must not be able to post: CORD-02 §9 seals the
 * community read-only on an owner-signed tombstone. History stays readable (the composer gate is the
 * only thing this changes), so [ConcordChannel.canPost] must fold the dissolution flag in alongside
 * membership.
 */
class ConcordChannelDissolvedTest {
    private val owner = "0f".repeat(32)
    private val channelId = "ce".repeat(32)

    private fun ed(
        kind: ControlEntityKind,
        eid: String,
        content: String,
        author: String = owner,
    ) = ControlEdition(kind, eid.hexToByteArray(), 0, null, null, content, author, "r-$eid", 0)

    // The tombstone lives on its own plane (CORD-02 §9); the session sets the flag from there.
    private fun state(dissolved: Boolean): ConcordCommunityState =
        ConcordCommunityState
            .fold(listOf(ed(ControlEntityKind.CHANNEL, channelId, """{"name":"general"}""")), "cc".repeat(32).hexToByteArray(), owner)
            .withDissolved(dissolved)

    @Test
    fun liveCommunityLetsTheOwnerPost() {
        val channel = ConcordChannel(ConcordChannelId(owner, channelId))
        channel.updateFrom(state(dissolved = false), emptySet(), owner)
        assertFalse(channel.dissolved)
        assertTrue(channel.canPost(), "a live community with a live standing must be postable")
    }

    @Test
    fun dissolvedCommunitySealsPostingEvenForTheOwner() {
        val channel = ConcordChannel(ConcordChannelId(owner, channelId))
        // The owner is the most privileged member; if even they can't post, no one can.
        val changed = channel.updateFrom(state(dissolved = true), emptySet(), owner)
        assertTrue(changed, "flipping to dissolved is a displayed-field change (drives recomposition)")
        assertTrue(channel.dissolved)
        assertFalse(channel.canPost(), "a dissolved community is read-only (CORD-02 §9)")
    }
}
