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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet

const val RELAY_HEALTH_THRESHOLD_SECONDS: Long = TimeUtils.ONE_WEEK.toLong()

/** Lists whose membership counts toward "is this relay in the user's set." */
val DETECTION_LISTS: Set<RelayListKind> =
    setOf(RelayListKind.Nip65, RelayListKind.DmInbox, RelayListKind.Search)

/**
 * Pure classifier. Returns the list of relays the UI should surface as unhealthy.
 *
 * Inputs:
 *  - records: durable per-relay timestamps (events received, connects, snoozes)
 *  - listMembership: which lists each relay currently belongs to
 *  - firstScanAt: when the local store first started observing this account.
 *                 Used as a global newcomer/first-run grace gate.
 *                 If `now - firstScanAt < threshold` (e.g. 7d), nothing is flagged.
 *  - lastSeenAny: most recent any-relay activity. Used as an offline-grace gate.
 *                 If `now - lastSeenAny > threshold`, nothing is flagged (we're offline).
 *  - torEnabled: when true (Tor mode), classification is skipped entirely (relay timing
 *                is intentionally lossy through Tor; v2 may be smarter).
 *  - now: clock injection (epoch seconds).
 */
fun classifyRelayHealth(
    records: Map<NormalizedRelayUrl, RelayHealthRecord>,
    listMembership: Map<NormalizedRelayUrl, Set<RelayListKind>>,
    firstScanAt: Long,
    lastSeenAny: Long,
    torEnabled: Boolean,
    now: Long = TimeUtils.now(),
    threshold: Long = RELAY_HEALTH_THRESHOLD_SECONDS,
): PersistentList<UnhealthyRelay> {
    if (torEnabled) return persistentListOf()

    // First-run/newcomer grace: don't flag until we've been observing for `threshold` seconds.
    if (firstScanAt == 0L || now - firstScanAt < threshold) return persistentListOf()

    // Offline grace: if nothing-at-all has responded in `threshold`, we're probably offline.
    if (lastSeenAny != 0L && now - lastSeenAny > threshold) return persistentListOf()

    val flagged = mutableListOf<UnhealthyRelay>()
    for ((url, lists) in listMembership) {
        // Skip relays not in any detection list (e.g. only in Blocked).
        val detectionLists = lists.intersect(DETECTION_LISTS)
        if (detectionLists.isEmpty()) continue

        val rec = records[url] ?: RelayHealthRecord()
        if (rec.snoozedUntil > now) continue

        val gap = now - rec.lastSeenAt()
        if (gap <= threshold) continue

        flagged.add(
            UnhealthyRelay(
                url = url,
                lastConnectAt = rec.lastConnectAt,
                lastIncomingAt = rec.lastIncomingAt,
                lists = lists.toPersistentSet(),
            ),
        )
    }
    return flagged.toPersistentList()
}

fun emptyHealthLists(): Set<RelayListKind> = persistentSetOf()
