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
package com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NamecoinSettingsTest {
    // ── Server string parsing ──────────────────────────────────────────

    @Test
    fun `parses host colon port as TLS`() {
        val s = NamecoinSettings.parseServerString("example.com:50006")
        assertNotNull(s)
        assertEquals("example.com", s.host)
        assertEquals(50006, s.port)
        assertTrue(s.useSsl)
    }

    @Test
    fun `parses host colon port colon tcp as plaintext`() {
        val s = NamecoinSettings.parseServerString("example.com:50001:tcp")
        assertNotNull(s)
        assertEquals("example.com", s.host)
        assertEquals(50001, s.port)
        assertFalse(s.useSsl)
    }

    @Test
    fun `parses onion address`() {
        val s = NamecoinSettings.parseServerString("abc123def.onion:50001:tcp")
        assertNotNull(s)
        assertEquals("abc123def.onion", s.host)
        assertEquals(50001, s.port)
        assertFalse(s.useSsl)
        @Suppress("DEPRECATION") // the custom-server trust path NamecoinServices still uses
        assertTrue(s.usePinnedTrustStore)
    }

    @Test
    fun `trims whitespace`() {
        val s = NamecoinSettings.parseServerString("  example.com : 50006  ")
        assertNotNull(s)
        assertEquals("example.com", s.host)
        assertEquals(50006, s.port)
    }

    @Test
    fun `rejects empty host`() {
        assertNull(NamecoinSettings.parseServerString(":50006"))
    }

    @Test
    fun `rejects invalid port`() {
        assertNull(NamecoinSettings.parseServerString("example.com:abc"))
        assertNull(NamecoinSettings.parseServerString("example.com:0"))
        assertNull(NamecoinSettings.parseServerString("example.com:99999"))
    }

    @Test
    fun `rejects no port`() {
        assertNull(NamecoinSettings.parseServerString("example.com"))
    }

    // ── Format round-trip ──────────────────────────────────────────────

    @Test
    fun `formats TLS server without suffix`() {
        val server = ElectrumxServer("example.com", 50006, true)
        assertEquals("example.com:50006", NamecoinSettings.formatServerString(server))
    }

    @Test
    fun `formats TCP server with tcp suffix`() {
        val server = ElectrumxServer("example.com", 50001, false)
        assertEquals("example.com:50001:tcp", NamecoinSettings.formatServerString(server))
    }

    @Test
    fun `round-trips server string through parse and format`() {
        val original = "myserver.onion:50001:tcp"
        val parsed = NamecoinSettings.parseServerString(original)!!
        val formatted = NamecoinSettings.formatServerString(parsed)
        assertEquals(original, formatted)
    }

    // ── toElectrumxServers ─────────────────────────────────────────────

    @Test
    fun `returns null when no custom servers`() {
        val settings = NamecoinSettings(customServers = emptyList())
        assertNull(settings.toElectrumxServers())
    }

    @Test
    fun `returns parsed list for valid custom servers`() {
        val settings =
            NamecoinSettings(
                customServers =
                    listOf(
                        "server1.com:50006",
                        "server2.onion:50001:tcp",
                    ),
            )
        val servers = settings.toElectrumxServers()
        assertNotNull(servers)
        assertEquals(2, servers.size)
        assertEquals("server1.com", servers[0].host)
        assertTrue(servers[0].useSsl)
        assertEquals("server2.onion", servers[1].host)
        assertFalse(servers[1].useSsl)
        @Suppress("DEPRECATION") // the custom-server trust path NamecoinServices still uses
        assertTrue(servers[1].usePinnedTrustStore)
    }

    @Test
    fun `skips invalid entries in custom server list`() {
        val settings =
            NamecoinSettings(
                customServers =
                    listOf(
                        "valid.com:50006",
                        "invalid", // no port
                        "also-invalid:abc", // non-numeric port
                    ),
            )
        val servers = settings.toElectrumxServers()
        assertNotNull(servers)
        assertEquals(1, servers.size)
        assertEquals("valid.com", servers[0].host)
    }

    @Test
    fun `returns null when all custom servers are invalid`() {
        val settings = NamecoinSettings(customServers = listOf("bad", "also-bad"))
        assertNull(settings.toElectrumxServers())
    }

    // ── hasCustomServers flag ──────────────────────────────────────────

    @Test
    fun `hasCustomServers is false when empty`() {
        assertFalse(NamecoinSettings().hasCustomServers)
    }

    @Test
    fun `hasCustomServers is true when populated`() {
        assertTrue(NamecoinSettings(customServers = listOf("x:1")).hasCustomServers)
    }

    // ── Default settings ───────────────────────────────────────────────

    @Test
    fun `default settings are enabled with no custom servers`() {
        val d = NamecoinSettings.DEFAULT
        assertTrue(d.enabled)
        assertTrue(d.customServers.isEmpty())
        assertFalse(d.hasCustomServers)
    }

    // ── Backend / RPC plumbing ─────────────────────────────────────────

    @Test
    fun `default backend is electrumx`() {
        assertEquals(NamecoinBackend.ELECTRUMX, NamecoinSettings.DEFAULT.backend)
    }

    @Test
    fun `default fallback policy is all-off`() {
        val s = NamecoinSettings.DEFAULT
        assertFalse(s.fallbackToCustomElectrumx)
        assertFalse(s.fallbackToDefaultElectrumx)
        val policy = s.toFallbackPolicy()
        assertFalse(policy.fallbackToCustomElectrumx)
        assertFalse(policy.fallbackToDefaultElectrumx)
    }

    @Test
    fun `hasUsableCoreRpc requires http url`() {
        assertFalse(NamecoinSettings.DEFAULT.hasUsableCoreRpc)
        val ok =
            NamecoinSettings.DEFAULT.copy(
                namecoinCoreRpc =
                    NamecoinCoreRpcConfig(
                        url = "http://node.local:8336/",
                        username = "u",
                        password = "p",
                    ),
            )
        assertTrue(ok.hasUsableCoreRpc)

        val bad =
            NamecoinSettings.DEFAULT.copy(
                namecoinCoreRpc = NamecoinCoreRpcConfig(url = "node.local:8336"),
            )
        assertFalse(bad.hasUsableCoreRpc)
    }

    @Test
    fun `toFallbackPolicy mirrors toggles`() {
        val s =
            NamecoinSettings.DEFAULT.copy(
                fallbackToCustomElectrumx = true,
                fallbackToDefaultElectrumx = true,
            )
        val p = s.toFallbackPolicy()
        assertTrue(p.fallbackToCustomElectrumx)
        assertTrue(p.fallbackToDefaultElectrumx)
    }

    @Test
    fun `backend can be set to namecoin core rpc`() {
        val s =
            NamecoinSettings.DEFAULT.copy(
                backend = NamecoinBackend.NAMECOIN_CORE_RPC,
            )
        assertEquals(NamecoinBackend.NAMECOIN_CORE_RPC, s.backend)
    }
}
