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
package com.vitorpamplona.amethyst.commons.napplet.permissions

import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock

/**
 * Persistence for the **standing** napplet grants ([GrantState.ALLOW_ALWAYS] and
 * [GrantState.DENY]). Keyed by `coordinate` (see [com.vitorpamplona.amethyst.commons.napplet.NappletIdentity]).
 *
 * Session and one-shot grants are NOT persisted here — the ledger keeps those in memory.
 * The Android actual is DataStore-backed; tests use [InMemoryNappletPermissionStore].
 */
interface NappletPermissionStore {
    suspend fun load(coordinate: String): Map<NappletCapability, GrantState>

    suspend fun store(
        coordinate: String,
        capability: NappletCapability,
        grant: GrantState,
    )

    suspend fun clear(coordinate: String)

    /** All persisted grants, keyed by coordinate — used by the permissions-management UI. */
    suspend fun all(): Map<String, Map<NappletCapability, GrantState>>

    /** Removes a single persisted grant (revoking one capability for one napplet). */
    suspend fun remove(
        coordinate: String,
        capability: NappletCapability,
    )

    /** This store's grants for [pubKey] alone, whoever is logged in. A store kept per account overrides it. */
    fun forAccount(pubKey: String): NappletPermissionStore = this
}

/** A thread-safe in-memory [NappletPermissionStore] for tests and ephemeral sessions. */
class InMemoryNappletPermissionStore : NappletPermissionStore {
    private val lock = KmpLock()
    private val data = mutableMapOf<String, MutableMap<NappletCapability, GrantState>>()

    override suspend fun load(coordinate: String): Map<NappletCapability, GrantState> = lock.withLock { data[coordinate]?.toMap() ?: emptyMap() }

    override suspend fun store(
        coordinate: String,
        capability: NappletCapability,
        grant: GrantState,
    ) = lock.withLock {
        // Only persistent decisions belong in the store; the ledger filters, but guard here too.
        if (grant == GrantState.ALLOW_ALWAYS || grant == GrantState.DENY) {
            data.getOrPut(coordinate) { mutableMapOf() }[capability] = grant
        }
    }

    override suspend fun clear(coordinate: String) =
        lock.withLock {
            data.remove(coordinate)
            Unit
        }

    override suspend fun all(): Map<String, Map<NappletCapability, GrantState>> = lock.withLock { data.mapValues { it.value.toMap() } }

    override suspend fun remove(
        coordinate: String,
        capability: NappletCapability,
    ) = lock.withLock {
        data[coordinate]?.remove(capability)
        Unit
    }
}
