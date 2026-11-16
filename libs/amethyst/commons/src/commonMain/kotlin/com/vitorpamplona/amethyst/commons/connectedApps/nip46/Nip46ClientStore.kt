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
package com.vitorpamplona.amethyst.commons.connectedApps.nip46

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock

/**
 * Display + transport info a NIP-46 remote-signer client sent us when it paired:
 * its self-declared [name]/[url]/[image] (shown in Connected Apps) and the
 * [relays] it talks to us on. The relays matter for the `nostrconnect://` flow —
 * an app that offered its own relays (not the user's inbox) is only reachable
 * there, so they are persisted and re-added to the listen set on the next launch.
 */
data class Nip46ClientInfo(
    val name: String? = null,
    val url: String? = null,
    val image: String? = null,
    val relays: Set<String> = emptySet(),
) {
    fun isEmpty() = name == null && url == null && image == null && relays.isEmpty()
}

/**
 * Persists [Nip46ClientInfo] per connected client, keyed by the same
 * `nip46:<signerPubKey>:<clientPubKey>` coordinate the permission ledger uses, so
 * a client's metadata and its trust grant live under one key and are namespaced
 * per account. Unlike the permission ledger this is display/transport data, not a
 * security decision — a hostile value can only mislabel a card, never grant access.
 */
interface Nip46ClientStore {
    suspend fun load(coordinate: String): Nip46ClientInfo?

    suspend fun store(
        coordinate: String,
        info: Nip46ClientInfo,
    )

    suspend fun remove(coordinate: String)

    /** All stored client info, keyed by coordinate — for startup relay recovery + the management screen. */
    suspend fun all(): Map<String, Nip46ClientInfo>
}

/** A thread-safe in-memory [Nip46ClientStore] for tests and ephemeral sessions. */
class InMemoryNip46ClientStore : Nip46ClientStore {
    private val lock = KmpLock()
    private val map = mutableMapOf<String, Nip46ClientInfo>()

    override suspend fun load(coordinate: String): Nip46ClientInfo? = lock.withLock { map[coordinate] }

    override suspend fun store(
        coordinate: String,
        info: Nip46ClientInfo,
    ) = lock.withLock { map[coordinate] = info }

    override suspend fun remove(coordinate: String) =
        lock.withLock {
            map.remove(coordinate)
            Unit
        }

    override suspend fun all(): Map<String, Nip46ClientInfo> = lock.withLock { map.toMap() }
}
