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
package com.vitorpamplona.amethyst.commons.relayClient.discover.nip90DVMs

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DVMs publish heartbeats to their own outbox relays, which may not overlap the user's discovery
 * relays at all — so the global heartbeat REQ alone leaves alive DVMs invisible (their beats never
 * reach the cache the freshness gate reads). The outbox fetcher batches the announcement authors
 * per DVM outbox relay so the gate sees the beats the DVMs actually publish.
 */
class DvmHeartbeatOutboxFiltersTest {
    private val r1 = RelayUrlNormalizer.normalizeOrNull("wss://r1.example/")!!
    private val r2 = RelayUrlNormalizer.normalizeOrNull("wss://r2.example/")!!
    private val r3 = RelayUrlNormalizer.normalizeOrNull("wss://r3.example/")!!

    private fun announcement(pubKey: HexKey) =
        AppDefinitionEvent(
            id = pubKey.take(16) + "a".repeat(48),
            pubKey = pubKey,
            createdAt = 1_760_000_000L,
            tags = arrayOf(arrayOf("d", "dvm"), arrayOf("k", "5300")),
            content = """{"name":"DVM"}""",
            sig = "b".repeat(128),
        )

    private val authorA = "aa".repeat(32)
    private val authorB = "bb".repeat(32)
    private val authorC = "cc".repeat(32)

    private val outboxes: (HexKey) -> Set<NormalizedRelayUrl> =
        {
            when (it) {
                authorA -> setOf(r1, r2)
                authorB -> setOf(r1, r3)
                else -> emptySet()
            }
        }

    @Test
    fun batchesAnnouncementAuthorsPerOutboxRelay() {
        val filters =
            dvmHeartbeatOutboxFilters(
                announcements = listOf(announcement(authorA), announcement(authorB), announcement(authorC)),
                outboxRelaysFor = outboxes,
                now = 1_760_000_420L,
            )

        assertEquals(3, filters.size, "r1 carries A+B, r2 carries A, r3 carries B; author C has no outbox")

        val authorsOn = { relay: String -> filters.first { it.relay.url == relay }.filter.authors }

        assertEquals(setOf(authorA, authorB), authorsOn(r1.url)?.toSet())
        assertEquals(setOf(authorA), authorsOn(r2.url)?.toSet())
        assertEquals(setOf(authorB), authorsOn(r3.url)?.toSet())

        filters.forEach {
            assertEquals(listOf(DvmHeartbeatEvent.KIND), it.filter.kinds)
            assertEquals(1_759_999_520L, it.filter.since, "rolling window: now - MAX_AGE_SECONDS")
        }
    }

    @Test
    fun anAuthorWithNoKnownOutboxIsSkippedNotBlocked() {
        val filters =
            dvmHeartbeatOutboxFilters(
                announcements = listOf(announcement(authorC)),
                outboxRelaysFor = outboxes,
                now = 1_760_000_420L,
            )

        assertTrue(filters.isEmpty(), "no outbox known — the global REQ is that DVM's fallback")
    }

    @Test
    fun relayCapKeepsTheMostCoveringRelays() {
        val filters =
            dvmHeartbeatOutboxFilters(
                announcements = listOf(announcement(authorA), announcement(authorB)),
                outboxRelaysFor = outboxes,
                now = 1_760_000_420L,
                maxRelays = 2,
            )

        assertEquals(setOf(r1.url, r2.url), filters.map { it.relay.url }.toSet(), "r1 covers 2, r2 and r3 tie at 1 — tie broken deterministically")
    }

    @Test
    fun noAnnouncementsMeansNoRequests() {
        assertTrue(
            dvmHeartbeatOutboxFilters(emptyList(), outboxRelaysFor = outboxes, now = 1_760_000_420L).isEmpty(),
        )
    }
}
