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
package com.vitorpamplona.amethyst.commons.relayClient.channel

import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupRolesEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The relay-group branch of the open-channel filter (`ChannelPublic`). After the state/content split this
 * must load **only** the group's relay-signed state (39000-39005, `#d`-scoped) plus an id-only backfill of
 * pinned message bodies — and crucially **no** kind-9/poll message window (that job moved to the chat tail +
 * history pager). This pins that contract so a regression can't quietly re-add a message-window REQ here.
 */
class FilterRelayGroupStateTest {
    private val relayA = RelayUrlNormalizer.normalizeOrNull("wss://relay-a.example/")!!
    private val groupId = GroupId("g1", relayA)
    private val relaySignKey = "b".repeat(64)
    private val sig = "0".repeat(128)

    private val metadataKinds =
        listOf(
            GroupMetadataEvent.KIND,
            GroupAdminsEvent.KIND,
            GroupMembersEvent.KIND,
            GroupRolesEvent.KIND,
        )
    private val pinKinds = listOf(GroupPinnedEvent.KIND)

    @Test
    fun `with no pins it is host d-scoped state filters and no message window`() {
        val channel = RelayGroupChannel(groupId)

        val filters = filterRelayGroupState(channel, since = null)

        assertEquals(2, filters.size)
        filters.forEach { f ->
            assertEquals(relayA, f.relay)
            assertEquals(listOf("g1"), f.filter.tags!!["d"])
            assertNull(f.filter.tags!!["h"], "state is #d-scoped, never #h — the message window is the tail/pager's job")
            assertNull(f.filter.limit, "state carries no message-window kinds (9/poll), so no limit either")
            assertNull(f.filter.until)
        }
        assertEquals(metadataKinds, filters[0].filter.kinds)
        assertEquals(pinKinds, filters[1].filter.kinds)
    }

    /**
     * Regression: relay29-family relays (0xchat's `groups.0xchat.com`) answer a filter that mixes the
     * 39000-39003 metadata kinds with any other kind — 39005 pins included — with
     * `CLOSED … "blocked: it's not allowed to mix metadata kinds with others"`, dropping the WHOLE REQ.
     * The group then never learns its name, roster or the user's own membership, so it renders as a raw
     * id and offers "Join" to somebody the relay already lists as an admin. Keep the two apart.
     */
    @Test
    fun `pins are never mixed into the metadata filter`() {
        val channel = RelayGroupChannel(groupId)

        filterRelayGroupState(channel, since = null).forEach { f ->
            val kinds = f.filter.kinds ?: return@forEach
            assertFalse(
                kinds.contains(GroupPinnedEvent.KIND) && kinds.any { it in metadataKinds },
                "39005 must not share a filter with the 39000-39003 metadata block: $kinds",
            )
        }
    }

    @Test
    fun `pins add an id-only backfill filter alongside the state filter`() {
        val channel = RelayGroupChannel(groupId)
        val pinnedIds = listOf("a".repeat(64), "c".repeat(64))
        channel.updatePinned(
            GroupPinnedEvent(
                id = "d".repeat(64),
                pubKey = relaySignKey,
                createdAt = 100L,
                tags = arrayOf(arrayOf("d", "g1"), arrayOf("e", pinnedIds[0]), arrayOf("e", pinnedIds[1])),
                content = "",
                sig = sig,
            ),
        )

        val filters = filterRelayGroupState(channel, since = null)
        assertEquals(3, filters.size)

        val pinFilter = filters.first { it.filter.ids != null }
        assertEquals(relayA, pinFilter.relay)
        assertEquals(pinnedIds, pinFilter.filter.ids)
        assertNull(pinFilter.filter.kinds, "pinned bodies are fetched by id, so no kinds")
        assertNull(pinFilter.filter.since, "pinned events are immutable, so no since either")

        // The state filters are still present and still carry no #h message window.
        val stateFilters = filters.filter { it.filter.ids == null }
        assertEquals(listOf(metadataKinds, pinKinds), stateFilters.map { it.filter.kinds })
        stateFilters.forEach {
            assertTrue(it.filter.tags!!.containsKey("d"))
            assertNull(it.filter.tags!!["h"])
        }
    }

    @Test
    fun `address pins add a kind-author-d backfill filter`() {
        val channel = RelayGroupChannel(groupId)
        val author = "b".repeat(64)
        channel.updatePinned(
            GroupPinnedEvent(
                id = "d".repeat(64),
                pubKey = relaySignKey,
                createdAt = 100L,
                tags = arrayOf(arrayOf("d", "g1"), arrayOf("a", "30023:$author:article")),
                content = "",
                sig = sig,
            ),
        )

        val filters = filterRelayGroupState(channel, since = null)
        assertEquals(3, filters.size, "state + pins filters plus one address back-fill, no id filter")
        assertTrue(filters.none { it.filter.ids != null })

        val addressFilter = filters.first { it.filter.authors != null }
        assertEquals(relayA, addressFilter.relay)
        assertEquals(listOf(30023), addressFilter.filter.kinds)
        assertEquals(listOf(author), addressFilter.filter.authors)
        assertEquals(listOf("article"), addressFilter.filter.tags!!["d"])
        assertNull(addressFilter.filter.since)
    }

    @Test
    fun `address pins sharing a kind and author collapse into one filter`() {
        val channel = RelayGroupChannel(groupId)
        val author = "b".repeat(64)
        val other = "e".repeat(64)
        channel.updatePinned(
            GroupPinnedEvent(
                id = "d".repeat(64),
                pubKey = relaySignKey,
                createdAt = 100L,
                tags =
                    arrayOf(
                        arrayOf("d", "g1"),
                        arrayOf("a", "30023:$author:one"),
                        arrayOf("a", "30023:$author:two"),
                        arrayOf("a", "30023:$other:three"),
                        arrayOf("a", "30311:$author:live"),
                    ),
                content = "",
                sig = sig,
            ),
        )

        val addressFilters = filterRelayGroupState(channel, since = null).filter { it.filter.authors != null }
        assertEquals(3, addressFilters.size, "one filter per (kind, author), not per address")

        val byKey = addressFilters.associateBy { it.filter.kinds!!.single() to it.filter.authors!!.single() }
        assertEquals(listOf("one", "two"), byKey[30023 to author]!!.filter.tags!!["d"])
        assertEquals(listOf("three"), byKey[30023 to other]!!.filter.tags!!["d"])
        assertEquals(listOf("live"), byKey[30311 to author]!!.filter.tags!!["d"])
    }
}
