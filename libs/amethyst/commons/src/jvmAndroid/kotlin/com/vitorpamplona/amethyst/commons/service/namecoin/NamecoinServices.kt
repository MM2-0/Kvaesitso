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
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.TOR_ELECTRUMX_SERVERS
import com.vitorpamplona.amethyst.commons.model.preferences.NamecoinSettingsStore
import com.vitorpamplona.amethyst.commons.service.http.RoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.CompositeNamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxNameBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.IElectrumXClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NameShowResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcClient
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcConfig
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.RpcProbeResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ServerTestResult
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The live Namecoin backends behind `.bit` and `d/`/`id/` lookups: the ElectrumX client, the
 * Namecoin Core JSON-RPC client and the resolver that composes them from [settings]. Every client is
 * built on first use and routed through [httpClients] (Tor when the NIP-05 role says so).
 */
class NamecoinServices(
    private val settings: NamecoinSettingsStore,
    private val httpClients: RoleBasedHttpClientBuilder,
    private val scope: CoroutineScope,
) : NamecoinClients {
    // Custom servers still trust the user's pins through ElectrumXClient's shared store
    // (usePinnedTrustStore). Moving each pin onto its server's pinnedCertsPem, as Quartz 3.0.0
    // requires, changes how the pins are stored and is left for that migration.
    @Suppress("DEPRECATION")
    val electrumXClient by lazy {
        val client = ElectrumXClient(socketFactory = { httpClients.socketFactoryForNip05() })
        scope.launch {
            try {
                val pinnedCerts = settings.loadPinnedCerts()
                if (pinnedCerts.isNotEmpty()) client.setDynamicCerts(pinnedCerts)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Non-fatal: the default certificates still work.
                Log.w(TAG, "Could not load the pinned Namecoin certificates", e)
            }
        }
        client
    }

    /**
     * The long-lived Namecoin Core JSON-RPC client. The current [NamecoinCoreRpcConfig] is pushed in
     * each time the user saves settings, so the hot lookup path never reads the settings file.
     */
    val coreRpcClient by lazy {
        val client = NamecoinCoreRpcClient(httpClientForUrl = httpClients::okHttpClientForNip05)
        // The same pinned trust store the ElectrumX client loads, so user-pinned certificates are
        // available on both backends after a restart.
        scope.launch {
            try {
                client.setConfig(settings.current.namecoinCoreRpc)
                val pinnedCerts = settings.loadPinnedCerts()
                if (pinnedCerts.isNotEmpty()) client.setDynamicCerts(pinnedCerts)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Non-fatal: the user can re-pin from Settings.
                Log.w(TAG, "Could not configure the Namecoin Core client", e)
            }
        }
        client
    }

    private fun defaultServers(): List<ElectrumxServer> = if (httpClients.shouldUseTorForNIP05(ELECTRUMX_PROBE_URL)) TOR_ELECTRUMX_SERVERS else DEFAULT_ELECTRUMX_SERVERS

    /**
     * The lookup backend the current settings ask for: Namecoin Core RPC or ElectrumX (custom servers,
     * the defaults, or both), with the user's fallback policy. Built fresh per call so a settings
     * change takes effect on the next lookup.
     */
    fun buildBackend(): IElectrumXClient {
        val current = settings.current
        val custom = current.toElectrumxServers()
        val defaults = defaultServers()

        val customExBackend = custom?.let { servers -> ElectrumxNameBackend(electrumXClient) { servers } }
        val defaultExBackend = ElectrumxNameBackend(electrumXClient) { defaults }

        return when (current.backend) {
            NamecoinBackend.NAMECOIN_CORE_RPC -> {
                // Refresh the client's config in case the user just saved it.
                coreRpcClient.setConfig(current.namecoinCoreRpc)
                CompositeNamecoinBackend(
                    primary = coreRpcClient,
                    customElectrumx = customExBackend,
                    defaultElectrumx = defaultExBackend,
                    policy = current.toFallbackPolicy(),
                    isPrimaryCoreRpc = true,
                )
            }

            NamecoinBackend.ELECTRUMX -> {
                // Custom servers first, if any. With only the public defaults configured, primary is
                // the default backend and the fallback toggle is moot.
                val primary: NamecoinNameBackend = customExBackend ?: defaultExBackend
                CompositeNamecoinBackend(
                    primary = primary,
                    customElectrumx = null,
                    defaultElectrumx = if (customExBackend != null) defaultExBackend else null,
                    policy = current.toFallbackPolicy(),
                    isPrimaryCoreRpc = false,
                )
            }
        }
    }

    val resolver by lazy {
        NamecoinNameResolver(
            lookupTimeoutMs = NAMECOIN_LOOKUP_TIMEOUT_MS,
            electrumxClient =
                object : IElectrumXClient {
                    override suspend fun nameShowWithFallback(
                        identifier: String,
                        servers: List<ElectrumxServer>,
                    ): NameShowResult? = buildBackend().nameShowWithFallback(identifier, servers)
                },
            // Kept for NamecoinNameResolver's API; the composite backend ignores it and reads the
            // settings through buildBackend().
            serverListProvider = { settings.customServersOrNull ?: defaultServers() },
        )
    }

    override suspend fun testElectrumxServer(server: ElectrumxServer): ServerTestResult = electrumXClient.testServer(server)

    override suspend fun probeCoreRpc(config: NamecoinCoreRpcConfig): RpcProbeResult = coreRpcClient.probe(config)

    override fun addPinnedCert(pem: String) {
        @Suppress("DEPRECATION") // the shared store, see electrumXClient
        electrumXClient.addPinnedCert(pem)
        coreRpcClient.addPinnedCert(pem)
    }

    override fun setCoreRpcConfig(config: NamecoinCoreRpcConfig) {
        coreRpcClient.setConfig(config)
    }

    companion object {
        private const val TAG = "NamecoinServices"

        /** Any https URL: the NIP-05 Tor decision only depends on the scheme and the host kind. */
        private const val ELECTRUMX_PROBE_URL = "https://electrumx.example.com"
    }
}
