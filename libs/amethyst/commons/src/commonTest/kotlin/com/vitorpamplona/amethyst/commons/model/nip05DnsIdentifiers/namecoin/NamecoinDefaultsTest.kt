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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NamecoinDefaultsTest {
    private val lists = listOf(DEFAULT_ELECTRUMX_SERVERS, TOR_ELECTRUMX_SERVERS)

    @Test
    fun deadHostIsGone() {
        lists.forEach { servers -> assertFalse(servers.any { it.host == "electrum.nmc.ethicnology.com" }) }
    }

    @Test
    fun replacementIsListedOnceOverSystemCaTls() {
        lists.forEach { servers ->
            val replacement = servers.filter { it.host == "electrumx2.testls.space" }
            assertEquals(1, replacement.size)
            assertEquals(50002, replacement.single().port)
            assertTrue(replacement.single().useSsl)
            assertTrue(replacement.single().pinnedCertsPem.isEmpty())
        }
    }

    @Test
    fun noServerIsListedTwice() {
        lists.forEach { servers -> assertEquals(servers.size, servers.map { it.host to it.port }.toSet().size) }
    }

    @Test
    fun selfSignedServersCarryTheirOwnPin() {
        val selfSigned = setOf("nmc2.bitcoins.sk", "46.229.238.187", "relay.testls.bit", "23.158.233.10")
        lists.flatten().filter { it.host in selfSigned }.forEach { server ->
            assertEquals(1, server.pinnedCertsPem.size, server.host)
            assertTrue(server.pinnedCertsPem.single().startsWith("-----BEGIN CERTIFICATE-----"), server.host)
        }
    }

    @Test
    fun peersShareTheirOperatorsPin() {
        val byHost = DEFAULT_ELECTRUMX_SERVERS.associateBy { it.host }
        assertEquals(byHost.getValue("nmc2.bitcoins.sk").pinnedCertsPem, byHost.getValue("46.229.238.187").pinnedCertsPem)
        assertEquals(byHost.getValue("relay.testls.bit").pinnedCertsPem, byHost.getValue("23.158.233.10").pinnedCertsPem)
        assertTrue(byHost.getValue("nmc2.bitcoins.sk").pinnedCertsPem != byHost.getValue("relay.testls.bit").pinnedCertsPem)
    }

    @Test
    fun noDefaultUsesTheSharedTrustStore() {
        @Suppress("DEPRECATION")
        lists.flatten().forEach { assertFalse(it.usePinnedTrustStore, it.host) }
    }
}
