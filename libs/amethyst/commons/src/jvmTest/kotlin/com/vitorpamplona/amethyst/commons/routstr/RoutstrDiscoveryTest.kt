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
package com.vitorpamplona.amethyst.commons.routstr

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.routstr.node.RoutstrNodeEvent
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The 82 kind 38421 announcements live on relay.routstr.com, relay.damus.io and nos.lol on
 * 2026-11-13 (79 addresses), through the picker's discovery code.
 */
class RoutstrDiscoveryTest {
    private val events: List<Event> =
        GZIPInputStream(javaClass.getResourceAsStream("/routstr/38421-2026-11-13.jsonl.gz")!!)
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }
            .map { Event.fromJson(it) }

    /** The newest announcement in the corpus: "now" for its freshness. */
    private val now = events.maxOf { it.createdAt }

    @Test
    fun everyFixtureParsesAsANodeAnnouncement() {
        assertEquals(82, events.size)
        assertTrue(events.all { it is RoutstrNodeEvent })
        val nodes = events.routstrNodes().map(RoutstrNode::from)
        assertEquals(82, nodes.size)
        // Each one names itself and says where it is, in one of the shapes nodes use.
        nodes.forEach { node ->
            assertNotNull(node.displayName)
            assertTrue(node.mints.all { it.startsWith("http") }, "mints of ${node.displayName}")
        }
        assertEquals(79, RoutstrAccount.newestPerAddress(events.routstrNodes()).size)
    }

    @Test
    fun listsAMonthWithTheWeekFirst() {
        val clear = RoutstrNodes.list(events, torEnabled = false, now = now)
        assertEquals(2, clear.fresh.size)
        assertEquals(14, clear.stale.size)

        val tor = RoutstrNodes.list(events, torEnabled = true, now = now)
        assertEquals(3, tor.fresh.size)
        assertEquals(16, tor.stale.size)

        val all = clear.all(includeStale = true)
        assertEquals(all.sortedByDescending { it.lastSeen / 86_400 }.map { it.lastSeen / 86_400 }, all.map { it.lastSeen / 86_400 })
        assertTrue(clear.fresh.all { it.isFresh(now) })
        assertTrue(clear.stale.none { it.isFresh(now) })
        assertTrue(all.all { now - it.lastSeen <= RoutstrNodeEvent.LISTED_MAX_AGE })
    }

    @Test
    fun callsOnlyHttpsOrOnionThroughTor() {
        val clear = RoutstrNodes.list(events, torEnabled = false, now = now).all(includeStale = true)
        clear.forEach { node ->
            val base = assertNotNull(node.callBase(torEnabled = false), node.displayName)
            assertTrue(base.startsWith("https://"), base)
            // Never an IP literal, a mesh name or plain http.
            assertFalse(Regex("^https://\\d+\\.\\d+\\.\\d+\\.\\d+").containsMatchIn(base), base)
            assertFalse(".fips" in base, base)
            assertEquals("$base/v1", node.keyUrl)
        }

        val tor = RoutstrNodes.list(events, torEnabled = true, now = now).all(includeStale = true)
        val onionOnly = tor.filter { !it.hasHttps }
        assertTrue(onionOnly.isNotEmpty())
        onionOnly.forEach { node ->
            assertTrue(node.callBase(torEnabled = true)!!.endsWith(".onion"))
            // NIP-FA keeps keys off plain http, so an onion-only node can't hold a saved key.
            assertEquals(null, node.keyUrl)
            assertEquals(null, RoutstrTarget.of(node))
        }
        // A node that has both is called at its https host until that host's own /v1/info names
        // an onion: an announcement's onion could be anyone's.
        tor.filter { it.hasHttps && it.hasOnion }.forEach { assertEquals(it.httpsUrl, it.callBase(torEnabled = true)) }
    }

    @Test
    fun searchesByName() {
        val all = RoutstrNodes.list(events, torEnabled = true, now = now).all(includeStale = true)
        val first = all.first { it.name != null }
        assertTrue(RoutstrNodes.matches(first, first.name!!.uppercase()))
        assertTrue(all.filter { RoutstrNodes.matches(it, "zzz-no-such-node") }.isEmpty())
    }
}
