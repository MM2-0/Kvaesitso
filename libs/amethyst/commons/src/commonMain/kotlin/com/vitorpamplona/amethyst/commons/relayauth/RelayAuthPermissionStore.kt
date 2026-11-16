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
package com.vitorpamplona.amethyst.commons.relayauth

/**
 * Per-relay [RelayAuthDecision] overrides. Used by [RelayAuthPermissionLedger] after
 * checking the global [RelayAuthPolicy]. The Android implementation uses a single
 * shared DataStore file so a relay URL lookup never touches another relay's data.
 */
interface RelayAuthPermissionStore {
    /** The stored per-relay override for [relayUrl], or `null` if no override is set. */
    suspend fun loadDecision(relayUrl: String): RelayAuthDecision?

    /** Persist [decision] as the override for [relayUrl]. */
    suspend fun storeDecision(
        relayUrl: String,
        decision: RelayAuthDecision,
    )

    /** Remove the per-relay override for [relayUrl]. */
    suspend fun clearDecision(relayUrl: String)

    /** All per-relay overrides — for the relay auth settings screen. */
    suspend fun allDecisions(): Map<String, RelayAuthDecision>

    /**
     * Records *why* [relayUrl] was authenticated with, so the settings screen can explain each
     * relay ("To send DMs to: …", "To download posts from: …"). [additions] maps a purpose to the
     * counterparty pubkeys seen for it; implementations merge into whatever is already stored.
     * Default no-op for stores that don't track rationale.
     */
    suspend fun recordUse(
        relayUrl: String,
        additions: Map<AuthPurposeKind, Set<String>>,
    ) {
        // no-op by default: stores that don't track rationale ignore recorded uses.
    }

    /** The accumulated grant rationale for [relayUrl] (purpose → counterparty pubkeys). */
    suspend fun loadRationale(relayUrl: String): Map<AuthPurposeKind, Set<String>> = emptyMap()

    /** All per-relay rationales — for the relay auth settings screen. */
    suspend fun allRationales(): Map<String, Map<AuthPurposeKind, Set<String>>> = emptyMap()

    /** Forgets the accumulated grant rationale for [relayUrl] (does not touch the ALLOW/DENY override). */
    suspend fun clearRationale(relayUrl: String) {
        // no-op by default: stores that don't track rationale have nothing to clear.
    }

    /** Epoch-second timestamp of the last time each relay was authenticated with (for display). */
    suspend fun allLastUsed(): Map<String, Long> = emptyMap()
}
