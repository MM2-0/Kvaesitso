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
package com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup

import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The discovery-feed augmentation that surfaces groups your follows are in. A relay-signed 39000 means a
 * follow appears three ways; this pins the exact REQ shapes: (1) `{authors, kinds:[39000]}`, (2)
 * `{kinds:[39001,39002], #p:<follows>}`, and (3) a `{kinds:[39000], #d:<their group ids>}` backfill for
 * rosters already cached. `cachedChannels` is passed explicitly here so the test stays off `LocalCache`.
 */
class FilterRelayGroupsByAuthorsTest {
    private val relayA = RelayUrlNormalizer.normalizeOrNull("wss://relay-a.example/")!!
    private val relaySignKey = "b".repeat(64)
    private val sig = "0".repeat(128)
    private val f1 = "11".repeat(32) // sorts before f2
    private val f2 = "22".repeat(32)

    @Test
    fun `empty authors produces no filters`() {
        assertTrue(filterRelayGroupsByAuthors(relayA, emptySet(), since = null, cachedChannels = emptyList()).isEmpty())
    }

    @Test
    fun `builds the author-signed and p-tagged roster filters with authors sorted`() {
        val filters = filterRelayGroupsByAuthors(relayA, setOf(f2, f1), since = 7L, cachedChannels = emptyList())
        assertEquals(2, filters.size)

        // (1) groups whose relay signing key is a follow — an `authors` filter, no tags.
        val byAuthor = filters.single { it.filter.authors != null }
        assertEquals(relayA, byAuthor.relay)
        assertEquals(listOf(f1, f2), byAuthor.filter.authors) // sorted, deterministic
        assertEquals(listOf(GroupMetadataEvent.KIND), byAuthor.filter.kinds)
        assertEquals(200, byAuthor.filter.limit)
        assertEquals(7L, byAuthor.filter.since)
        assertNull(byAuthor.filter.tags)

        // (2) a follow is an admin/member — a #p filter over the roster kinds.
        val byRoster = filters.single { it.filter.tags?.containsKey(PTag.TAG_NAME) == true }
        assertEquals(listOf(GroupAdminsEvent.KIND, GroupMembersEvent.KIND), byRoster.filter.kinds)
        assertEquals(listOf(f1, f2), byRoster.filter.tags!![PTag.TAG_NAME])
        assertEquals(200, byRoster.filter.limit)
        assertNull(byRoster.filter.authors)
    }

    @Test
    fun `a cached roster containing a follow adds a d-scoped metadata backfill`() {
        val channel = RelayGroupChannel(GroupId("g1", relayA))
        channel.updateMembers(
            GroupMembersEvent(
                id = "e".repeat(64),
                pubKey = relaySignKey,
                createdAt = 1L,
                tags = arrayOf(arrayOf("d", "g1"), arrayOf("p", f1)),
                content = "",
                sig = sig,
            ),
        )

        val filters = filterRelayGroupsByAuthors(relayA, setOf(f1), since = null, cachedChannels = listOf(channel))
        assertEquals(3, filters.size)

        val backfill = filters.single { it.filter.tags?.containsKey(DTag.TAG_NAME) == true }
        assertEquals(listOf(GroupMetadataEvent.KIND), backfill.filter.kinds)
        assertEquals(listOf("g1"), backfill.filter.tags!![DTag.TAG_NAME])
        assertEquals(200, backfill.filter.limit)
    }

    @Test
    fun `a cached roster with no follow in it adds no backfill`() {
        val channel = RelayGroupChannel(GroupId("g1", relayA))
        channel.updateMembers(
            GroupMembersEvent(
                id = "e".repeat(64),
                pubKey = relaySignKey,
                createdAt = 1L,
                tags = arrayOf(arrayOf("d", "g1"), arrayOf("p", f2)), // f2 is NOT in the author set below
                content = "",
                sig = sig,
            ),
        )

        val filters = filterRelayGroupsByAuthors(relayA, setOf(f1), since = null, cachedChannels = listOf(channel))
        assertEquals(2, filters.size) // only (1) and (2); no #d backfill
        assertTrue(filters.none { it.filter.tags?.containsKey(DTag.TAG_NAME) == true })
    }
}
