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
package com.vitorpamplona.amethyst.commons.tor

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An overlay-mesh relay (`0200::/7`, e.g. Yggdrasil) must never be dialed through the Tor SOCKS
 * proxy: Tor cannot route the range, so proxying guarantees failure rather than privacy. The
 * overlay already encrypts end to end and authenticates the peer by its key-derived address.
 */
class YggdrasilTorRoutingTest {
    private val yggdrasilRelay = NormalizedRelayUrl("ws://[201:d0e:9ba5:8bbc::1]:8080/")
    private val yggdrasilSubnetRelay = NormalizedRelayUrl("ws://[300:1b5d:d0e9:ba58::1]:4848/")
    private val lanRelay = NormalizedRelayUrl("ws://192.168.1.100:8080/")
    private val ulaRelay = NormalizedRelayUrl("ws://[fd12:3456::1]:8080/")
    private val clearnetIpv6Relay = NormalizedRelayUrl("wss://[2001:db8::1]:8080/")

    private fun evaluation(newViaTor: Boolean) =
        TorRelayEvaluation(
            torSettings =
                TorRelaySettings(
                    torType = TorType.INTERNAL,
                    onionRelaysViaTor = true,
                    dmRelaysViaTor = true,
                    newRelaysViaTor = newViaTor,
                    trustedRelaysViaTor = false,
                    moneyOperationsViaTor = false,
                ),
            classification =
                RelayClassification(
                    // The LAN relays are the user's own: someone else's follows newRelaysViaTor.
                    trusted = setOf(lanRelay, ulaRelay),
                    dm = emptySet(),
                ),
        )

    @Test
    fun overlayRelaysAreNeverTorifiedEvenWhenNewRelaysViaTorIsOn() {
        val eval = evaluation(newViaTor = true)
        assertFalse(eval.useTor(yggdrasilRelay), "0200::/8 node address must not be proxied")
        assertFalse(eval.useTor(yggdrasilSubnetRelay), "0300::/8 subnet address must not be proxied")
        assertFalse(eval.useTor(lanRelay), "LAN relay stays off Tor")
        assertFalse(eval.useTor(ulaRelay), "IPv6 unique local address stays off Tor")
    }

    @Test
    fun clearnetIpv6RelaysStillFollowTheTorSetting() {
        // The overlay exemption must not leak into ordinary IPv6 relays.
        assertTrue(evaluation(newViaTor = true).useTor(clearnetIpv6Relay))
        assertFalse(evaluation(newViaTor = false).useTor(clearnetIpv6Relay))
    }
}
