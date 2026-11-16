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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup

import com.vitorpamplona.amethyst.commons.tor.RelayClassification
import com.vitorpamplona.amethyst.commons.tor.TorRelayEvaluation
import com.vitorpamplona.amethyst.commons.tor.TorRelaySettings
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.commons.tor.torDefaultPreset
import com.vitorpamplona.amethyst.commons.tor.torFullyPrivate
import com.vitorpamplona.amethyst.commons.tor.torSmallPayloadsPreset
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Can't reach this relay over Tor" banner used to be driven by a bare 6s timer, so it appeared on
 * a relay that was connected and delivering. These pin the gates that replaced it.
 */
class TorClearnetFallbackTest {
    private fun offer(
        usesTor: Boolean = true,
        torIsUp: Boolean = true,
        trustingMovesToClearnet: Boolean = true,
        disconnectedLongEnough: Boolean = true,
    ) = shouldOfferTorClearnetFallback(usesTor, torIsUp, trustingMovesToClearnet, disconnectedLongEnough)

    @Test
    fun offersWhenTorRoutedRelayStaysSilent() {
        assertTrue(offer())
    }

    @Test
    fun silentWhileTheRelayIsReachable() {
        // The regression: the socket is open and the relay is replying, so there is nothing to escape.
        // The screen models that as the debounce never completing.
        assertFalse(offer(disconnectedLongEnough = false))
    }

    @Test
    fun silentWhenTheRelayIsNotRoutedOverTor() {
        // Tor can be on globally while this relay is dialed over clearnet (onion/localhost/trusted
        // presets) — blaming Tor for that relay's silence would be wrong.
        assertFalse(offer(usesTor = false))
    }

    @Test
    fun silentWhileTorItselfIsStillBootstrapping() {
        // Nothing Tor-routed connects during bootstrap — that's Tor's failure, not the relay's.
        assertFalse(offer(torIsUp = false))
    }

    @Test
    fun silentWhenTrustingWouldNotChangeRouting() {
        assertFalse(offer(trustingMovesToClearnet = false))
    }

    // --- the routing inputs the screen feeds the predicate, read off the real presets ---

    private val relay = NormalizedRelayUrl("wss://buzz.example.com/")

    private fun evaluation(preset: TorSettings) =
        TorRelayEvaluation(
            torSettings =
                TorRelaySettings(
                    torType = preset.torType,
                    onionRelaysViaTor = preset.onionRelaysViaTor,
                    dmRelaysViaTor = preset.dmRelaysViaTor,
                    newRelaysViaTor = preset.newRelaysViaTor,
                    trustedRelaysViaTor = preset.trustedRelaysViaTor,
                    moneyOperationsViaTor = preset.moneyOperationsViaTor,
                ),
            classification =
                RelayClassification(
                    trusted = emptySet(),
                    dm = emptySet(),
                ),
        )

    @Test
    fun defaultPresetRoutesANewRelayOverTorAndTrustingEscapesIt() {
        val eval = evaluation(torDefaultPreset)
        assertTrue(eval.useTor(relay))
        assertTrue(!eval.torSettings.trustedRelaysViaTor)
    }

    @Test
    fun privacyPresetsKeepTrustedRelaysOnTorSoTheOfferIsWithheld() {
        // Both keep trusted relays on Tor: adding this relay to the Trusted list would move nothing,
        // so the banner must not offer it (the button would be inert).
        assertTrue(evaluation(torSmallPayloadsPreset).torSettings.trustedRelaysViaTor)
        assertTrue(evaluation(torFullyPrivate).torSettings.trustedRelaysViaTor)
    }
}
