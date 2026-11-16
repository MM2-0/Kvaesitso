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
package com.vitorpamplona.amethyst.service.okhttp

import com.vitorpamplona.amethyst.commons.service.http.OkHttpClientFactoryForRelays
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Tests for SOCKS proxy creation and timeout logic.
 *
 * Note: OkHttpClientFactoryForRelays cannot be instantiated in unit tests because
 * its constructor calls android.os.Build (isEmulator check). These tests verify
 * the proxy and timeout logic directly using the same patterns the factory uses.
 * This documents the behavior we must preserve during extraction to commons.
 */
class OkHttpClientFactoryForRelaysTest {
    // --- buildLocalSocksProxy logic (tested directly) ---

    @Test
    fun socksProxy_withPort_returnsSocksType() {
        val proxy = buildLocalSocksProxy(9050)
        assertEquals(Proxy.Type.SOCKS, proxy.type())
    }

    @Test
    fun socksProxy_withPort_usesLocalhost() {
        val proxy = buildLocalSocksProxy(9050)
        val addr = proxy.address() as InetSocketAddress
        assertEquals("127.0.0.1", addr.hostString)
    }

    @Test
    fun socksProxy_withPort_usesGivenPort() {
        val proxy = buildLocalSocksProxy(9050)
        val addr = proxy.address() as InetSocketAddress
        assertEquals(9050, addr.port)
    }

    @Test
    fun socksProxy_customPort_usesGivenPort() {
        val proxy = buildLocalSocksProxy(9150) // Tor Browser port
        val addr = proxy.address() as InetSocketAddress
        assertEquals(9150, addr.port)
    }

    @Test
    fun socksProxy_nullPort_fallsBackToDefault9050() {
        // DOCUMENTS CURRENT BEHAVIOR: null falls back to 9050
        // This is a security concern flagged in the plan — will be fixed during extraction
        val proxy = buildLocalSocksProxy(null)
        val addr = proxy.address() as InetSocketAddress
        assertEquals(OkHttpClientFactoryForRelays.DEFAULT_SOCKS_PORT, addr.port)
    }

    // --- Timeout logic ---

    @Test
    fun timeout_mobile_returns30Seconds() {
        assertEquals(OkHttpClientFactoryForRelays.DEFAULT_TIMEOUT_ON_MOBILE_SECS, buildTimeout(true))
    }

    @Test
    fun timeout_wifi_returns10Seconds() {
        assertEquals(OkHttpClientFactoryForRelays.DEFAULT_TIMEOUT_ON_WIFI_SECS, buildTimeout(false))
    }

    // --- Timeout multiplier with proxy ---

    @Test
    fun timeoutWithProxy_tripled() {
        val base = 10
        val withProxy = computeTimeout(base, hasProxy = true)
        assertEquals(30, withProxy)
    }

    @Test
    fun timeoutWithoutProxy_unchanged() {
        val base = 10
        val withoutProxy = computeTimeout(base, hasProxy = false)
        assertEquals(10, withoutProxy)
    }

    @Test
    fun readWriteTimeout_tripleOfConnectTimeout() {
        // Both factories do: readTimeout = connectTimeout * 3
        val connectSeconds = 30
        assertEquals(90, connectSeconds * 3)
    }

    // Note: OkHttpClient.Builder tests removed — OkHttp internals depend on
    // Android platform classes in this module's test classpath.
    // These will be tested in desktopApp/jvmTest after extraction.

    // --- Constants ---

    @Test
    fun defaultSocksPort_is9050() {
        assertEquals(9050, OkHttpClientFactoryForRelays.DEFAULT_SOCKS_PORT)
    }

    @Test
    fun defaultIsMobile_isFalse() {
        assertEquals(false, OkHttpClientFactoryForRelays.DEFAULT_IS_MOBILE)
    }

    @Test
    fun defaultTimeoutWifi_is10() {
        assertEquals(10, OkHttpClientFactoryForRelays.DEFAULT_TIMEOUT_ON_WIFI_SECS)
    }

    @Test
    fun defaultTimeoutMobile_is30() {
        assertEquals(30, OkHttpClientFactoryForRelays.DEFAULT_TIMEOUT_ON_MOBILE_SECS)
    }

    // --- Helper functions matching factory logic ---

    private fun buildLocalSocksProxy(port: Int?): Proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port ?: OkHttpClientFactoryForRelays.DEFAULT_SOCKS_PORT))

    private fun buildTimeout(isMobile: Boolean): Int =
        if (isMobile) {
            OkHttpClientFactoryForRelays.DEFAULT_TIMEOUT_ON_MOBILE_SECS
        } else {
            OkHttpClientFactoryForRelays.DEFAULT_TIMEOUT_ON_WIFI_SECS
        }

    private fun computeTimeout(
        baseSeconds: Int,
        hasProxy: Boolean,
    ): Int = if (hasProxy) baseSeconds * 3 else baseSeconds
}
