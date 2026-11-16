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
package com.vitorpamplona.amethyst.commons.service.namecoin

import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.DEFAULT_ELECTRUMX_SERVERS
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NAMECOIN_LOOKUP_TIMEOUT_MS
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinResolveState
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinLookupCache
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNostrResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Application-level singleton for Namecoin name resolution.
 *
 * Thread-safe, lifecycle-aware, and designed for integration
 * into Amethyst's existing `ServiceManager` infrastructure.
 */
class NamecoinNameService(
    electrumxClient: ElectrumXClient,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Custom server list (user-configurable)
    @Volatile
    private var customServers: List<ElectrumxServer> = emptyList()

    private val resolver =
        NamecoinNameResolver(
            electrumxClient = electrumxClient,
            lookupTimeoutMs = NAMECOIN_LOOKUP_TIMEOUT_MS,
            serverListProvider = { customServers.ifEmpty { DEFAULT_ELECTRUMX_SERVERS } },
        )
    private val cache = NamecoinLookupCache()

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Resolve a Namecoin identifier to a Nostr pubkey.
     *
     * Returns cached results when available. This is the primary method
     * that the search bar and NIP-05 verifier should call.
     *
     * @param identifier e.g. "alice@example.bit", "id/bob", "example.bit"
     * @return [NamecoinNostrResult] or null
     */
    suspend fun resolve(identifier: String): NamecoinNostrResult? {
        // Check cache first
        val cached = cache.get(identifier)
        if (cached != null) return cached.result

        // Perform lookup
        val result = resolver.resolve(identifier)
        cache.put(identifier, result)
        return result
    }

    /**
     * Resolve and return just the hex pubkey, or null.
     * Convenience for follow-import integration.
     */
    suspend fun resolvePubkey(identifier: String): String? = resolve(identifier)?.pubkey

    /**
     * Resolve with detailed outcome for error reporting.
     */
    suspend fun resolveDetailed(identifier: String): NamecoinResolveOutcome = resolver.resolveDetailed(identifier)

    /**
     * Verify that a Namecoin name maps to the expected pubkey.
     *
     * This is the Namecoin equivalent of NIP-05 verification:
     * given a pubkey from a kind-0 event and a `nip05` value
     * ending in `.bit`, check that the Namecoin blockchain
     * confirms the mapping.
     *
     * @param nip05Address The nip05 field value, e.g. "alice@example.bit"
     * @param expectedPubkeyHex The pubkey from the kind-0 event
     * @return true if the Namecoin name resolves to the expected pubkey
     */
    suspend fun verifyNip05(
        nip05Address: String,
        expectedPubkeyHex: String,
    ): Boolean {
        if (!NamecoinNameResolver.isNamecoinIdentifier(nip05Address)) return false
        val result = resolve(nip05Address) ?: return false
        return result.pubkey.equals(expectedPubkeyHex, ignoreCase = true)
    }

    /**
     * Perform a lookup and emit results via a StateFlow.
     *
     * Useful for composable UIs that observe resolution state.
     */
    fun resolveLive(
        identifier: String,
        scope: CoroutineScope = this.scope,
    ): StateFlow<NamecoinResolveState> {
        val state = MutableStateFlow<NamecoinResolveState>(NamecoinResolveState.Loading)
        scope.launch {
            try {
                val result = resolve(identifier)
                state.value =
                    if (result != null) {
                        NamecoinResolveState.Resolved(result)
                    } else {
                        NamecoinResolveState.NotFound
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("NamecoinNameService", "resolve failed for $identifier", e)
                state.value = NamecoinResolveState.Error(NamecoinResolveState.Reason.Other(e.message ?: e::class.simpleName))
            }
        }
        return state
    }

    /**
     * Configure custom ElectrumX servers.
     *
     * Users who run their own ElectrumX instance can add it here
     * for better privacy and reliability.
     */
    fun setCustomServers(servers: List<ElectrumxServer>) {
        customServers = servers
    }

    /**
     * Clear the resolution cache.
     */
    suspend fun clearCache() = cache.clear()
}
