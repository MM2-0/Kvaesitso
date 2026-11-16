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
import kotlin.concurrent.Volatile

/**
 * Snapshot persisted to disk. Decoupled from the in-memory store so the same
 * persistence backend can swap (e.g. Android AccountSettings JSON vs Desktop
 * java.util.prefs.Preferences) without rippling into commons.
 *
 * [latencySamples] is populated only when a [RelayLatencyTracker] is wired into the store
 * (Desktop today; Android follow-up). Persistence backends should treat the field as
 * optional — older saved snapshots without it round-trip cleanly to empty maps.
 */
data class RelayHealthSnapshot(
    val records: Map<NormalizedRelayUrl, RelayHealthRecord> = emptyMap(),
    val firstScanAt: Long = 0,
    val lastSeenAny: Long = 0,
    val latencySamples: Map<NormalizedRelayUrl, Map<LatencyMetric, IntArray>> = emptyMap(),
)

/**
 * Per-account durable storage for relay health timestamps.
 *
 * Implementations must be safe to call from a background dispatcher.
 * Loads/saves are best-effort — corrupted state should be returned as
 * an empty snapshot so the store can re-bootstrap and the user simply
 * gets a fresh 7-day grace window.
 */
interface RelayHealthPersistence {
    fun load(): RelayHealthSnapshot

    fun save(snapshot: RelayHealthSnapshot)
}

/** No-op fallback for tests and CLI-style hosts that don't persist anything. */
class InMemoryRelayHealthPersistence(
    initial: RelayHealthSnapshot = RelayHealthSnapshot(),
) : RelayHealthPersistence {
    @Volatile private var current: RelayHealthSnapshot = initial

    override fun load(): RelayHealthSnapshot = current

    override fun save(snapshot: RelayHealthSnapshot) {
        current = snapshot
    }
}
