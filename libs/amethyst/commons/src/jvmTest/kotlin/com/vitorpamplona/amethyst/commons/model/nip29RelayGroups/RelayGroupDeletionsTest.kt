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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The device-global "deleted channels" bookkeeping: a delete is relay-scoped (keyed by
 * [GroupId.toKey]) and terminal (only ever added), so the same id on a different relay stays visible.
 */
class RelayGroupDeletionsTest {
    private val relayA = RelayUrlNormalizer.normalize("wss://a.example.com")
    private val relayB = RelayUrlNormalizer.normalize("wss://b.example.com")
    private val gid = "0123456789abcdef"

    @BeforeTest
    fun reset() = RelayGroupDeletions.clearForTesting()

    @AfterTest
    fun tearDown() = RelayGroupDeletions.clearForTesting()

    @Test
    fun marksAChannelDeletedAndReflectsInTheFlow() {
        val group = GroupId(gid, relayA)
        assertFalse(RelayGroupDeletions.isDeleted(group))

        RelayGroupDeletions.markDeleted(group)

        assertTrue(RelayGroupDeletions.isDeleted(group))
        assertTrue(RelayGroupDeletions.isDeleted(group.toKey()))
        assertEquals(setOf(group.toKey()), RelayGroupDeletions.flow.value)
    }

    @Test
    fun deletionIsRelayScoped() {
        RelayGroupDeletions.markDeleted(GroupId(gid, relayA))

        // The same group id on a different host relay is a different group, so it stays visible.
        assertTrue(RelayGroupDeletions.isDeleted(GroupId(gid, relayA)))
        assertFalse(RelayGroupDeletions.isDeleted(GroupId(gid, relayB)))
    }

    @Test
    fun markingIsIdempotent() {
        val group = GroupId(gid, relayA)
        RelayGroupDeletions.markDeleted(group)
        RelayGroupDeletions.markDeleted(group)

        assertEquals(1, RelayGroupDeletions.flow.value.size)
    }

    @Test
    fun restoreReplacesTheWholeSet() {
        RelayGroupDeletions.markDeleted(GroupId(gid, relayA))

        val restored = setOf(GroupId("aaaa", relayB).toKey(), GroupId("bbbb", relayB).toKey())
        RelayGroupDeletions.restore(restored)

        assertEquals(restored, RelayGroupDeletions.flow.value)
        assertFalse(RelayGroupDeletions.isDeleted(GroupId(gid, relayA)))
    }
}
