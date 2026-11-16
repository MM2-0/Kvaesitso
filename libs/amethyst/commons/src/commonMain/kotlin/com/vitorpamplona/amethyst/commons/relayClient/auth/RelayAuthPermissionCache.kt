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
package com.vitorpamplona.amethyst.commons.relayClient.auth

import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * In-memory, warm-cached view over one account's [RelayAuthPermissionStore] (a per-account file —
 * see the Android `DataStoreRelayAuthPermissionStore` built from `accounts/<pubkey>/`). Held on the
 * [com.vitorpamplona.amethyst.commons.model.Account] like the other state caches.
 *
 * The ALLOW/DENY overrides are the only thing read on the hot NIP-42 decision path
 * ([RelayAuthPermissionLedger.decide]). They are snapshotted into memory once, right after the
 * account loads, and served from there — so an incoming AUTH challenge is answered without a disk
 * read (a slow disk hit here would stall login-time relay auth). Writes update memory *and* disk.
 *
 * Rationale + last-used are read only by the settings screen, off the hot path, so they pass
 * straight through to disk. Implements [RelayAuthPermissionStore] so it drops into every existing
 * call site (the ledger and the settings screen) unchanged.
 */
class RelayAuthPermissionCache(
    private val disk: RelayAuthPermissionStore,
    scope: CoroutineScope,
) : RelayAuthPermissionStore {
    private val loaded = CompletableDeferred<Unit>()
    private val _overrides = MutableStateFlow<Map<String, RelayAuthDecision>>(emptyMap())

    /** Per-relay overrides for this account, observable so the settings screen refreshes on change. */
    val overrides: StateFlow<Map<String, RelayAuthDecision>> = _overrides.asStateFlow()

    init {
        scope.launch {
            _overrides.value = disk.allDecisions()
            loaded.complete(Unit)
        }
    }

    /** Non-suspending override lookup — returns null until the initial warm load finishes. */
    fun decisionOrNull(relayUrl: String): RelayAuthDecision? = _overrides.value[relayUrl]

    override suspend fun loadDecision(relayUrl: String): RelayAuthDecision? {
        loaded.await()
        return _overrides.value[relayUrl]
    }

    override suspend fun storeDecision(
        relayUrl: String,
        decision: RelayAuthDecision,
    ) {
        disk.storeDecision(relayUrl, decision)
        _overrides.update { it + (relayUrl to decision) }
    }

    override suspend fun clearDecision(relayUrl: String) {
        disk.clearDecision(relayUrl)
        _overrides.update { it - relayUrl }
    }

    override suspend fun allDecisions(): Map<String, RelayAuthDecision> {
        loaded.await()
        return _overrides.value
    }

    // --- rationale + last-used: settings-screen only, never on the auth decision path ---

    override suspend fun recordUse(
        relayUrl: String,
        additions: Map<AuthPurposeKind, Set<String>>,
    ) = disk.recordUse(relayUrl, additions)

    override suspend fun loadRationale(relayUrl: String): Map<AuthPurposeKind, Set<String>> = disk.loadRationale(relayUrl)

    override suspend fun allRationales(): Map<String, Map<AuthPurposeKind, Set<String>>> = disk.allRationales()

    override suspend fun clearRationale(relayUrl: String) = disk.clearRationale(relayUrl)

    override suspend fun allLastUsed(): Map<String, Long> = disk.allLastUsed()
}
