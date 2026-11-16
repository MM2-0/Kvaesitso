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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip01Notifications

import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.AllNotificationKinds
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.GroupNotificationKinds
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.NotificationsPerKeyKinds
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.NotificationsPerKeyKinds2
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.NotificationsPerKeyKinds3
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.SummaryKinds
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.filterGroupNotificationsHistoryToPubkey
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.filterNotificationsHistoryToPubkey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the backward-paging notification history filters: they must ask for the N newest events strictly
 * OLDER than a cursor (`until`+`limit`, no `since`), so the single per-relay cursor the
 * [BackwardRelayPager][com.vitorpamplona.amethyst.commons.relayClient.paging.BackwardRelayPager] tracks
 * can't skip a band and an empty page truly means "nothing older" (see RelayLoadingCursors).
 */
class FilterNotificationsHistoryTest {
    private val relay = RelayUrlNormalizer.normalize("wss://inbox.example.com")
    private val pubkey = "aa".repeat(32)
    private val until = 1_700_000_000L

    @Test
    fun `history filter asks one until+limit page tagging me, no since`() {
        val filters = filterNotificationsHistoryToPubkey(relay, pubkey, until, 500)

        // A single combined-kinds filter, not the live query's split — one cursor stays gap-proof.
        assertEquals(1, filters.size)
        val f = filters.first().filter
        assertEquals(relay, filters.first().relay)
        assertEquals(until, f.until)
        assertEquals(500, f.limit)
        assertNull("history pages by until, never since", f.since)
        assertEquals(listOf(pubkey), f.tags?.get("p"))
        assertEquals(AllNotificationKinds, f.kinds)
    }

    @Test
    fun `combined kinds cover every live-query notification kind`() {
        listOf(SummaryKinds, NotificationsPerKeyKinds, NotificationsPerKeyKinds2, NotificationsPerKeyKinds3)
            .flatten()
            .forEach { kind ->
                assertTrue("AllNotificationKinds must include live kind $kind", kind in AllNotificationKinds)
            }
        // Flattened + de-duplicated: no kind appears twice.
        assertEquals(AllNotificationKinds.size, AllNotificationKinds.toSet().size)
    }

    @Test
    fun `group history filter scopes to my groups by h tag`() {
        val groupIds = listOf("group-a", "group-b")
        val filters = filterGroupNotificationsHistoryToPubkey(relay, pubkey, groupIds, until, 500)

        assertEquals(1, filters.size)
        val f = filters.first().filter
        assertEquals(until, f.until)
        assertNull(f.since)
        assertEquals(listOf(pubkey), f.tags?.get("p"))
        assertEquals(groupIds, f.tags?.get("h"))
        assertEquals(GroupNotificationKinds, f.kinds)
    }

    @Test
    fun `empty pubkey or groups yields no filter`() {
        assertTrue(filterNotificationsHistoryToPubkey(relay, null, until, 500).isEmpty())
        assertTrue(filterNotificationsHistoryToPubkey(relay, "", until, 500).isEmpty())
        assertTrue(filterGroupNotificationsHistoryToPubkey(relay, pubkey, emptyList(), until, 500).isEmpty())
    }
}
