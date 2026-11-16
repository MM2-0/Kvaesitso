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
package com.vitorpamplona.amethyst.commons.service.http

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.SurgeDns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy

class DualHttpClientManager(
    userAgent: String,
    proxyPortProvider: StateFlow<Int?>,
    isMobileDataProvider: StateFlow<Boolean?>,
    keyCache: EncryptionKeyCache,
    scope: CoroutineScope,
    dns: SurgeDns,
    shouldBridgeBlossomCache: ((profilePicture: Boolean) -> Boolean)? = null,
    // Required (not nullable): every general-purpose HTTP client we mint must be
    // wired into the OnionLocationCache so the app's `.onion`-routing behavior
    // is uniform across image, upload, NIP-05, money, preview, and push roles.
    // See [OkHttpClientFactory] kdoc.
    onionCache: OnionLocationCache,
    // Resource-usage ledger counter, installed on the shared base client so
    // every derived client is accounted. See [OkHttpClientFactory].
    usageInterceptor: Interceptor? = null,
    // Signs BUD-01 read-auth to retry auth-gated Blossom downloads on 401.
    // See [BlossomReadAuthInterceptor].
    blossomReadAuth: Interceptor? = null,
    // Told when the local Blossom cache refuses a connection. See [LocalBlossomCacheRedirectInterceptor].
    onLocalBlossomCacheUnreachable: () -> Unit = {},
) : IHttpClientManager {
    val factory = OkHttpClientFactory(keyCache, userAgent, dns, shouldBridgeBlossomCache, onionCache, usageInterceptor, blossomReadAuth, onLocalBlossomCacheUnreachable)

    val defaultHttpClient: StateFlow<OkHttpClient> =
        combine(proxyPortProvider, isMobileDataProvider) { proxy, mobile ->
            factory.buildHttpClient(proxy, mobile)
        }.stateIn(
            scope,
            SharingStarted.WhileSubscribed(1000),
            factory.buildHttpClient(proxyPortProvider.value, isMobileDataProvider.value),
        )

    val defaultHttpClientWithoutProxy: StateFlow<OkHttpClient> =
        isMobileDataProvider
            .map { mobile ->
                factory.buildHttpClient(mobile)
            }.stateIn(
                scope,
                SharingStarted.WhileSubscribed(1000),
                factory.buildHttpClient(isMobileDataProvider.value),
            )

    fun getCurrentProxy(): Proxy? = defaultHttpClient.value.proxy

    override fun getCurrentProxyPort(useProxy: Boolean): Int? =
        if (useProxy) {
            (getCurrentProxy()?.address() as? InetSocketAddress)?.port
        } else {
            null
        }

    override fun getHttpClient(useProxy: Boolean): OkHttpClient =
        if (useProxy) {
            defaultHttpClient.value
        } else {
            defaultHttpClientWithoutProxy.value
        }

    fun getDynamicCallFactory(useProxy: Boolean) = DynamicCallFactory(useProxy, this)
}

/**
 * the okhttp can change on the manager without affecting other systems.
 *
 * [useProxy] is fixed when the factory is built (e.g. per video player pool), from the URL the
 * caller knew at the time. The URL that is finally requested can differ: a `blossom:` URI is
 * resolved to the local Blossom cache on `127.0.0.1:24242` only when the data source opens. Tor
 * refuses to connect to loopback/private addresses, so those requests must never take the proxied
 * client — the same rule RoleBasedHttpClientBuilder applies to URLs it sees upfront.
 */
class DynamicCallFactory(
    val useProxy: Boolean,
    val manager: DualHttpClientManager,
) : Call.Factory {
    override fun newCall(request: Request): Call = manager.getHttpClient(shouldUseProxy(useProxy, request.url.toString())).newCall(request)

    companion object {
        fun shouldUseProxy(
            useProxy: Boolean,
            url: String,
        ): Boolean = useProxy && !RelayUrlNormalizer.isLocalHost(url) && !RelayUrlNormalizer.isOverlayNetwork(url)
    }
}
