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
package com.vitorpamplona.amethyst.model.torState

import com.vitorpamplona.amethyst.commons.tor.RelayClassification
import com.vitorpamplona.amethyst.commons.tor.TorRelayEvaluation
import com.vitorpamplona.amethyst.commons.tor.TorRelaySettings
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorRelayEvaluationTest {
    // Helper relay URLs
    private val clearnetRelay = NormalizedRelayUrl("wss://relay.damus.io/")
    private val onionRelay = NormalizedRelayUrl("wss://abc123.onion/")
    private val localhostRelay = NormalizedRelayUrl("ws://127.0.0.1:8080/")
    private val localhostNameRelay = NormalizedRelayUrl("ws://localhost:8080/")
    private val localNetworkRelay = NormalizedRelayUrl("ws://192.168.1.100:8080/")
    private val dmRelay = NormalizedRelayUrl("wss://dm.relay.com/")
    private val trustedRelay = NormalizedRelayUrl("wss://trusted.relay.com/")

    private fun buildEvaluation(
        torType: TorType = TorType.INTERNAL,
        onionViaTor: Boolean = true,
        dmViaTor: Boolean = true,
        newViaTor: Boolean = true,
        trustedViaTor: Boolean = false,
        dmRelays: Set<NormalizedRelayUrl> = setOf(dmRelay),
        trustedRelays: Set<NormalizedRelayUrl> = setOf(trustedRelay),
    ) = TorRelayEvaluation(
        torSettings =
            TorRelaySettings(
                torType = torType,
                onionRelaysViaTor = onionViaTor,
                dmRelaysViaTor = dmViaTor,
                newRelaysViaTor = newViaTor,
                trustedRelaysViaTor = trustedViaTor,
            ),
        classification =
            RelayClassification(
                trusted = trustedRelays,
                dm = dmRelays,
            ),
    )

    // --- Tor OFF: always false ---

    @Test
    fun torOff_clearnetRelay_returnsFalse() {
        val eval = buildEvaluation(torType = TorType.OFF)
        assertFalse(eval.useTor(clearnetRelay))
    }

    @Test
    fun torOff_onionRelay_returnsFalse() {
        val eval = buildEvaluation(torType = TorType.OFF)
        assertFalse(eval.useTor(onionRelay))
    }

    @Test
    fun torOff_dmRelay_returnsFalse() {
        val eval = buildEvaluation(torType = TorType.OFF)
        assertFalse(eval.useTor(dmRelay))
    }

    // --- Localhost: the user's own local relays are always false regardless of Tor ---

    private val ownLocal = setOf(localhostRelay, localhostNameRelay, localNetworkRelay)

    @Test
    fun localhost127_alwaysFalse() {
        val eval = buildEvaluation(torType = TorType.INTERNAL, trustedRelays = ownLocal)
        assertFalse(eval.useTor(localhostRelay))
    }

    @Test
    fun localhostName_alwaysFalse() {
        val eval = buildEvaluation(torType = TorType.INTERNAL, trustedRelays = ownLocal)
        assertFalse(eval.useTor(localhostNameRelay))
    }

    @Test
    fun localNetwork192_alwaysFalse() {
        val eval = buildEvaluation(torType = TorType.INTERNAL, trustedRelays = ownLocal)
        assertFalse(eval.useTor(localNetworkRelay))
    }

    // --- .onion relays ---

    @Test
    fun onionRelay_torInternal_onionEnabled_returnsTrue() {
        val eval = buildEvaluation(torType = TorType.INTERNAL, onionViaTor = true)
        assertTrue(eval.useTor(onionRelay))
    }

    @Test
    fun onionRelay_torInternal_onionDisabled_returnsFalse() {
        val eval = buildEvaluation(torType = TorType.INTERNAL, onionViaTor = false)
        assertFalse(eval.useTor(onionRelay))
    }

    @Test
    fun onionRelay_torExternal_onionEnabled_returnsTrue() {
        val eval = buildEvaluation(torType = TorType.EXTERNAL, onionViaTor = true)
        assertTrue(eval.useTor(onionRelay))
    }

    // --- DM relays ---

    @Test
    fun dmRelay_dmViaTorEnabled_returnsTrue() {
        val eval = buildEvaluation(dmViaTor = true)
        assertTrue(eval.useTor(dmRelay))
    }

    @Test
    fun dmRelay_dmViaTorDisabled_returnsFalse() {
        val eval = buildEvaluation(dmViaTor = false)
        assertFalse(eval.useTor(dmRelay))
    }

    // --- Trusted relays ---

    @Test
    fun trustedRelay_trustedViaTorEnabled_returnsTrue() {
        val eval = buildEvaluation(trustedViaTor = true)
        assertTrue(eval.useTor(trustedRelay))
    }

    @Test
    fun trustedRelay_trustedViaTorDisabled_returnsFalse() {
        val eval = buildEvaluation(trustedViaTor = false)
        assertFalse(eval.useTor(trustedRelay))
    }

    // --- New/unknown relays ---

    @Test
    fun unknownRelay_newViaTorEnabled_returnsTrue() {
        val eval = buildEvaluation(newViaTor = true)
        assertTrue(eval.useTor(clearnetRelay))
    }

    @Test
    fun unknownRelay_newViaTorDisabled_returnsFalse() {
        val eval = buildEvaluation(newViaTor = false)
        assertFalse(eval.useTor(clearnetRelay))
    }

    // --- Priority: .onion > DM > trusted > new ---

    @Test
    fun onionRelay_inDmList_treatedAsOnionNotDm() {
        // If a relay is both .onion AND in DM list, .onion takes precedence
        val onionDmRelay = NormalizedRelayUrl("wss://dmrelay.onion/")
        val eval =
            buildEvaluation(
                onionViaTor = false,
                dmViaTor = true,
                dmRelays = setOf(onionDmRelay),
            )
        // onion check happens first, and it's disabled → false
        assertFalse(eval.useTor(onionDmRelay))
    }

    @Test
    fun relay_inBothDmAndTrusted_dmTakesPrecedence() {
        val bothRelay = NormalizedRelayUrl("wss://both.relay.com/")
        val eval =
            buildEvaluation(
                dmViaTor = true,
                trustedViaTor = false,
                dmRelays = setOf(bothRelay),
                trustedRelays = setOf(bothRelay),
            )
        // DM check happens before trusted check
        assertTrue(eval.useTor(bothRelay))
    }

    @Test
    fun relay_inBothDmAndTrusted_dmDisabled_doesNotFallToTrusted() {
        val bothRelay = NormalizedRelayUrl("wss://both.relay.com/")
        val eval =
            buildEvaluation(
                dmViaTor = false,
                trustedViaTor = true,
                dmRelays = setOf(bothRelay),
                trustedRelays = setOf(bothRelay),
            )
        // DM check matches first, dm is disabled → false
        // Does NOT fall through to trusted check
        assertFalse(eval.useTor(bothRelay))
    }

    // --- Empty relay lists ---

    @Test
    fun emptyRelayLists_allRelaysAreNew() {
        val eval =
            buildEvaluation(
                newViaTor = true,
                dmRelays = emptySet(),
                trustedRelays = emptySet(),
            )
        assertTrue(eval.useTor(clearnetRelay))
        assertTrue(eval.useTor(dmRelay)) // Not in DM list, treated as new
        assertTrue(eval.useTor(trustedRelay)) // Not in trusted list, treated as new
    }

    // --- External Tor mode ---

    @Test
    fun torExternal_newViaTor_returnsTrue() {
        val eval = buildEvaluation(torType = TorType.EXTERNAL, newViaTor = true)
        assertTrue(eval.useTor(clearnetRelay))
    }

    @Test
    fun torExternal_localhostStillFalse() {
        val eval = buildEvaluation(torType = TorType.EXTERNAL, trustedRelays = ownLocal)
        assertFalse(eval.useTor(localhostRelay))
    }

    @Test
    fun someoneElsesLocalRelay_followsTheNewRelayPolicy() {
        assertTrue(buildEvaluation(torType = TorType.INTERNAL, newViaTor = true).useTor(localNetworkRelay))
        assertFalse(buildEvaluation(torType = TorType.INTERNAL, newViaTor = false).useTor(localNetworkRelay))
    }
}
