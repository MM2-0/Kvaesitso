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

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcConfig
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.RpcProbeResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ServerTestResult

/**
 * What the Namecoin settings screen asks of the live clients: test a server, probe a Namecoin
 * Core node, and hand them a pinned certificate or a new Core RPC config. The ElectrumX and Core
 * RPC clients themselves are JVM-only (raw TLS sockets, OkHttp), so the screen reaches them here.
 */
interface NamecoinClients {
    suspend fun testElectrumxServer(server: ElectrumxServer): ServerTestResult

    suspend fun probeCoreRpc(config: NamecoinCoreRpcConfig): RpcProbeResult

    /** Pins a self-signed certificate on both backends, so the user confirms it once. */
    fun addPinnedCert(pem: String)

    fun setCoreRpcConfig(config: NamecoinCoreRpcConfig)

    /** No clients: every test reports that nothing is available. */
    object None : NamecoinClients {
        override suspend fun testElectrumxServer(server: ElectrumxServer): ServerTestResult = ServerTestResult(server = server, success = false, responseTimeMs = 0, error = "Namecoin is not available on this platform")

        override suspend fun probeCoreRpc(config: NamecoinCoreRpcConfig): RpcProbeResult = RpcProbeResult(success = false, elapsedMs = 0, error = "Namecoin is not available on this platform")

        override fun addPinnedCert(pem: String) {}

        override fun setCoreRpcConfig(config: NamecoinCoreRpcConfig) {}
    }
}
