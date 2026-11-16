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
package com.vitorpamplona.amethyst.commons.relays.health

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassifyRelayHealthTest {
    private val now: Long = 1_700_000_000L
    private val week: Long = TimeUtils.ONE_WEEK.toLong()
    private val dead = RelayUrlNormalizer.normalizeOrNull("wss://dead.example.com")!!
    private val alive = RelayUrlNormalizer.normalizeOrNull("wss://alive.example.com")!!

    @Test
    fun firstRunQuiet_noFlagsWithinSevenDaysOfFirstScan() {
        val out =
            classifyRelayHealth(
                records = mapOf(dead to RelayHealthRecord(0, 0, 0)),
                listMembership = mapOf(dead to setOf(RelayListKind.Nip65)),
                firstScanAt = now - 60,
                lastSeenAny = now - 60,
                torEnabled = false,
                now = now,
            )
        assertEquals(0, out.size)
    }

    @Test
    fun torModeSkipsClassificationEntirely() {
        val out =
            classifyRelayHealth(
                records = mapOf(dead to RelayHealthRecord(0, 0, 0)),
                listMembership = mapOf(dead to setOf(RelayListKind.Nip65)),
                firstScanAt = now - 2 * week,
                lastSeenAny = now - 60,
                torEnabled = true,
                now = now,
            )
        assertEquals(0, out.size)
    }

    @Test
    fun offlineGraceSuppressesFlagsWhenNoRelayRespondedRecently() {
        val out =
            classifyRelayHealth(
                records = mapOf(dead to RelayHealthRecord(0, 0, 0)),
                listMembership = mapOf(dead to setOf(RelayListKind.Nip65)),
                firstScanAt = now - 2 * week,
                lastSeenAny = now - 2 * week,
                torEnabled = false,
                now = now,
            )
        assertEquals(0, out.size)
    }

    @Test
    fun deadRelayInMonitoredListIsFlagged() {
        val out =
            classifyRelayHealth(
                records =
                    mapOf(
                        dead to RelayHealthRecord(0, 0, 0),
                        alive to RelayHealthRecord(now - 60, now - 30, 0),
                    ),
                listMembership =
                    mapOf(
                        dead to setOf(RelayListKind.Nip65),
                        alive to setOf(RelayListKind.Nip65),
                    ),
                firstScanAt = now - 2 * week,
                lastSeenAny = now - 60,
                torEnabled = false,
                now = now,
            )
        assertEquals(1, out.size)
        assertEquals(dead, out[0].url)
        assertTrue(out[0].lists.contains(RelayListKind.Nip65))
    }

    @Test
    fun snoozedRelayIsHiddenUntilSnoozeExpires() {
        val out =
            classifyRelayHealth(
                records = mapOf(dead to RelayHealthRecord(0, 0, snoozedUntil = now + 60)),
                listMembership = mapOf(dead to setOf(RelayListKind.Nip65)),
                firstScanAt = now - 2 * week,
                lastSeenAny = now - 60,
                torEnabled = false,
                now = now,
            )
        assertEquals(0, out.size)

        val outAfter =
            classifyRelayHealth(
                records = mapOf(dead to RelayHealthRecord(0, 0, snoozedUntil = now - 60)),
                listMembership = mapOf(dead to setOf(RelayListKind.Nip65)),
                firstScanAt = now - 2 * week,
                lastSeenAny = now - 60,
                torEnabled = false,
                now = now,
            )
        assertEquals(1, outAfter.size)
    }

    @Test
    fun blockedOnlyRelaysAreNotFlagged() {
        val out =
            classifyRelayHealth(
                records = mapOf(dead to RelayHealthRecord(0, 0, 0)),
                listMembership = mapOf(dead to setOf(RelayListKind.Blocked)),
                firstScanAt = now - 2 * week,
                lastSeenAny = now - 60,
                torEnabled = false,
                now = now,
            )
        assertEquals(0, out.size)
    }

    @Test
    fun multiListMembershipPreservedInOutput() {
        val out =
            classifyRelayHealth(
                records = mapOf(dead to RelayHealthRecord(0, 0, 0)),
                listMembership =
                    mapOf(
                        dead to setOf(RelayListKind.Nip65, RelayListKind.DmInbox, RelayListKind.Blocked),
                    ),
                firstScanAt = now - 2 * week,
                lastSeenAny = now - 60,
                torEnabled = false,
                now = now,
            )
        assertEquals(1, out.size)
        assertEquals(3, out[0].lists.size)
        assertTrue(out[0].lists.contains(RelayListKind.Blocked))
    }

    @Test
    fun recentlySeenRelaysAreNotFlagged() {
        val out =
            classifyRelayHealth(
                records = mapOf(dead to RelayHealthRecord(now - 60, 0, 0)),
                listMembership = mapOf(dead to setOf(RelayListKind.Nip65)),
                firstScanAt = now - 2 * week,
                lastSeenAny = now - 60,
                torEnabled = false,
                now = now,
            )
        assertEquals(0, out.size)
    }
}
