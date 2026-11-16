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

import com.vitorpamplona.amethyst.commons.service.http.DualHttpClientManager
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.http.ProxiedSocketFactory
import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import javax.net.SocketFactory

class RoleBasedHttpClientBuilder(
    val okHttpClient: DualHttpClientManager,
    val torSettings: TorSettingsFlow,
    /**
     * When present, every role's client is wrapped with a byte-counting
     * interceptor so the resource-usage ledger can attribute HTTP traffic
     * per subsystem. Null keeps the raw shared clients (tests).
     */
    val usageMeter: ((role: String, base: OkHttpClient) -> OkHttpClient)? = null,
) : IRoleBasedHttpClientBuilder {
    private fun metered(
        role: String,
        base: OkHttpClient,
    ): OkHttpClient = usageMeter?.invoke(role, base) ?: base

    fun shouldUseTorForImageDownload(url: String) =
        shouldUseTorFor(
            url,
            torSettings.torType.value,
            torSettings.imagesViaTor.value,
        )

    fun shouldUseTorFor(
        url: String,
        torType: TorType,
        imagesViaTor: Boolean,
    ) = when (torType) {
        TorType.OFF -> false
        TorType.INTERNAL -> shouldUseTor(url, imagesViaTor)
        TorType.EXTERNAL -> shouldUseTor(url, imagesViaTor)
    }

    private fun shouldUseTor(
        normalizedUrl: String,
        final: Boolean,
    ): Boolean =
        if (RelayUrlNormalizer.isLocalHost(normalizedUrl) || RelayUrlNormalizer.isOverlayNetwork(normalizedUrl)) {
            // Overlay-mesh hosts (0200::/7) are reachable only through the local mesh
            // interface — Tor cannot route the range, so proxying only breaks the fetch.
            false
        } else if (RelayUrlNormalizer.isOnion(normalizedUrl)) {
            true
        } else {
            final
        }

    fun shouldUseTorForVideoDownload() =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> torSettings.videosViaTor.value
            TorType.EXTERNAL -> torSettings.videosViaTor.value
        }

    fun shouldUseTorForVideoDownload(url: String) =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, torSettings.videosViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, torSettings.videosViaTor.value)
        }

    fun shouldUseTorForPreviewUrl(url: String) =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, torSettings.urlPreviewsViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, torSettings.urlPreviewsViaTor.value)
        }

    fun shouldUseTorForTrustedRelays() =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> torSettings.trustedRelaysViaTor.value
            TorType.EXTERNAL -> torSettings.trustedRelaysViaTor.value
        }

    private fun checkLocalHostOnionAndThen(
        url: String,
        final: Boolean,
    ): Boolean = checkLocalHostOnionAndThen(url, torSettings.onionRelaysViaTor.value, final)

    private fun checkLocalHostOnionAndThen(
        normalizedUrl: String,
        isOnionRelaysActive: Boolean,
        final: Boolean,
    ): Boolean =
        if (RelayUrlNormalizer.isLocalHost(normalizedUrl) || RelayUrlNormalizer.isOverlayNetwork(normalizedUrl)) {
            false
        } else if (RelayUrlNormalizer.isOnion(normalizedUrl)) {
            isOnionRelaysActive
        } else {
            final
        }

    fun shouldUseTorForMoneyOperations(url: String) =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, torSettings.moneyOperationsViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, torSettings.moneyOperationsViaTor.value)
        }

    fun shouldUseTorForNIP05(url: String) =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, torSettings.nip05VerificationsViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, torSettings.nip05VerificationsViaTor.value)
        }

    fun shouldUseTorForUploads(url: String) =
        when (torSettings.torType.value) {
            TorType.OFF -> false
            TorType.INTERNAL -> checkLocalHostOnionAndThen(url, torSettings.mediaUploadsViaTor.value)
            TorType.EXTERNAL -> checkLocalHostOnionAndThen(url, torSettings.mediaUploadsViaTor.value)
        }

    override fun proxyPortForVideo(url: String): Int? = okHttpClient.getCurrentProxyPort(shouldUseTorForVideoDownload(url))

    override fun okHttpClientForNip05(url: String): OkHttpClient = metered(UsageKeys.ROLE_NIP05, okHttpClient.getHttpClient(shouldUseTorForNIP05(url)))

    override fun okHttpClientForUploads(url: String): OkHttpClient = metered(UsageKeys.ROLE_UPLOADS, okHttpClient.getHttpClient(shouldUseTorForUploads(url)))

    override fun okHttpClientForImage(url: String): OkHttpClient = metered(UsageKeys.ROLE_IMAGE, okHttpClient.getHttpClient(shouldUseTorForImageDownload(url)))

    override fun okHttpClientForVideo(url: String): OkHttpClient = metered(UsageKeys.ROLE_VIDEO, okHttpClient.getHttpClient(shouldUseTorForVideoDownload(url)))

    override fun okHttpClientForMoney(url: String): OkHttpClient = metered(UsageKeys.ROLE_MONEY, okHttpClient.getHttpClient(shouldUseTorForMoneyOperations(url)))

    override fun okHttpClientForPreview(url: String): OkHttpClient = metered(UsageKeys.ROLE_PREVIEW, okHttpClient.getHttpClient(shouldUseTorForPreviewUrl(url)))

    override fun okHttpClientForPushRegistration(url: String): OkHttpClient = metered(UsageKeys.ROLE_PUSH, okHttpClient.getHttpClient(shouldUseTorForTrustedRelays()))

    /**
     * Returns a [SocketFactory] that routes through the user's Tor proxy
     * when NIP-05 verification traffic should use Tor.
     *
     * Used by [ElectrumxClient] so that Namecoin lookups respect the
     * same proxy/Tor settings as HTTP-based NIP-05 verification,
     * preventing IP leaks through direct socket connections.
     */
    fun socketFactoryForNip05(): SocketFactory {
        // ElectrumX servers are always external, so we use a dummy
        // non-localhost, non-onion URL to query the Tor policy.
        val useTor = shouldUseTorForNIP05("https://electrumx.example.com")
        if (!useTor) return SocketFactory.getDefault()

        val proxy = okHttpClient.getCurrentProxy() ?: return SocketFactory.getDefault()
        val proxyAddr = proxy.address() as? InetSocketAddress ?: return SocketFactory.getDefault()

        return ProxiedSocketFactory(Proxy(Proxy.Type.SOCKS, proxyAddr))
    }
}
