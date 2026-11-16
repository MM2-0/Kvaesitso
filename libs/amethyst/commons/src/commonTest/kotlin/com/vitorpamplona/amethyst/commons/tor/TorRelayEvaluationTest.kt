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

class TorRelayEvaluationTest {
    private val clearnetRelay = NormalizedRelayUrl("wss://relay.damus.io/")
    private val onionRelay = NormalizedRelayUrl("wss://abc123.onion/")
    private val localhostRelay = NormalizedRelayUrl("ws://127.0.0.1:8080/")
    private val localhostNameRelay = NormalizedRelayUrl("ws://localhost:8080/")
    private val localNetworkRelay = NormalizedRelayUrl("ws://192.168.1.100:8080/")
    private val dmRelay = NormalizedRelayUrl("wss://dm.relay.com/")
    private val trustedRelay = NormalizedRelayUrl("wss://trusted.relay.com/")
    private val moneyRelay = NormalizedRelayUrl("wss://wallet.relay.com/")
    private val assumedRelay = NormalizedRelayUrl("wss://assumed.relay.com/")
    private val signerRelay = NormalizedRelayUrl("wss://relay.nip46.com/")

    private fun buildEvaluation(
        torType: TorType = TorType.INTERNAL,
        onionViaTor: Boolean = true,
        dmViaTor: Boolean = true,
        newViaTor: Boolean = true,
        trustedViaTor: Boolean = false,
        moneyViaTor: Boolean = false,
        dmRelays: Set<NormalizedRelayUrl> = setOf(dmRelay),
        trustedRelays: Set<NormalizedRelayUrl> = setOf(trustedRelay),
        moneyOpRelays: Set<NormalizedRelayUrl> = setOf(moneyRelay),
        assumedRelays: Set<NormalizedRelayUrl> = setOf(assumedRelay),
        signerRelays: Set<NormalizedRelayUrl> = setOf(signerRelay),
    ) = TorRelayEvaluation(
        torSettings =
            TorRelaySettings(
                torType = torType,
                onionRelaysViaTor = onionViaTor,
                dmRelaysViaTor = dmViaTor,
                newRelaysViaTor = newViaTor,
                trustedRelaysViaTor = trustedViaTor,
                moneyOperationsViaTor = moneyViaTor,
            ),
        classification =
            RelayClassification(
                trusted = trustedRelays,
                dm = dmRelays,
                moneyOp = moneyOpRelays,
                signer = signerRelays,
                assumed = assumedRelays,
            ),
    )

    // --- NIP-46 signer relays: chosen by the user, so treated like their own lists ---

    /** The default settings send new relays over Tor but reach the remote signer directly. */
    @Test
    fun signerRelay_followsTrustedPreference() {
        assertFalse(buildEvaluation(trustedViaTor = false, newViaTor = true).useTor(signerRelay))
        assertTrue(buildEvaluation(trustedViaTor = true, newViaTor = false).useTor(signerRelay))
    }

    /** Before the login registers them, the same relays are strangers: the bug being fixed. */
    @Test
    fun unregisteredSignerRelay_isANewRelay() {
        assertTrue(buildEvaluation(trustedViaTor = false, newViaTor = true, signerRelays = emptySet()).useTor(signerRelay))
    }

    /** Being a signer relay never loosens the policy of an onion, DM or wallet relay. */
    @Test
    fun signerRelay_neverOverridesOnionDmOrMoney() {
        val eval =
            buildEvaluation(
                trustedViaTor = false,
                onionViaTor = true,
                dmViaTor = true,
                moneyViaTor = true,
                signerRelays = setOf(signerRelay, onionRelay, dmRelay, moneyRelay),
            )
        assertFalse(eval.useTor(signerRelay))
        assertTrue(eval.useTor(onionRelay))
        assertTrue(eval.useTor(dmRelay))
        assertTrue(eval.useTor(moneyRelay))
    }

    // --- assumed relays: the app's stand-in while the user's lists are unknown ---

    /**
     * The whole point: a guessed relay inherits the policy the user chose for their *own* lists, so
     * the default configuration starts on clearnet and gets a fast first login.
     */
    @Test
    fun assumedRelay_followsTrustedPreference() {
        assertFalse(buildEvaluation(trustedViaTor = false).useTor(assumedRelay))
        assertTrue(buildEvaluation(trustedViaTor = true).useTor(assumedRelay))
    }

    /** Anyone who asked for Tor on their own relays keeps it here, with no separate opt-out. */
    @Test
    fun assumedRelay_hardenedUserStillUsesTor() {
        assertTrue(buildEvaluation(trustedViaTor = true, newViaTor = true).useTor(assumedRelay))
    }

    /**
     * The branch sits below every other classification, so being guessed can never downgrade a
     * relay that already had a stricter policy.
     */
    @Test
    fun assumedRelay_neverOverridesOnionDmOrMoney() {
        val eval =
            buildEvaluation(
                trustedViaTor = false,
                onionViaTor = true,
                dmViaTor = true,
                moneyViaTor = true,
                assumedRelays = setOf(assumedRelay, onionRelay, dmRelay, moneyRelay),
            )
        assertTrue(eval.useTor(onionRelay))
        assertTrue(eval.useTor(dmRelay))
        assertTrue(eval.useTor(moneyRelay))
    }

    /** A relay we are not guessing about is still a stranger. */
    @Test
    fun unknownRelay_stillFollowsNewPreference() {
        assertTrue(buildEvaluation(newViaTor = true).useTor(clearnetRelay))
        assertFalse(buildEvaluation(newViaTor = false).useTor(clearnetRelay))
    }

    /** With nothing guessed — every account that has any list — behaviour is exactly as before. */
    @Test
    fun emptyAssumedList_isTodaysBehaviour() {
        val eval = buildEvaluation(assumedRelays = emptySet(), newViaTor = true, trustedViaTor = false)
        assertTrue(eval.useTor(assumedRelay))
        assertTrue(eval.useTor(clearnetRelay))
        assertFalse(eval.useTor(trustedRelay))
    }

    // --- Tor OFF ---
    @Test
    fun torOff_alwaysFalse() {
        val eval = buildEvaluation(torType = TorType.OFF)
        assertFalse(eval.useTor(clearnetRelay))
        assertFalse(eval.useTor(onionRelay))
        assertFalse(eval.useTor(dmRelay))
    }

    // --- Localhost bypass: only for the user's own relays ---
    private val tailnetRelay = NormalizedRelayUrl("ws://100.100.10.10:4848/")
    private val lanNamedRelay = NormalizedRelayUrl("ws://nas.lan:7777/")
    private val singleLabelRelay = NormalizedRelayUrl("ws://beelink:7777/")
    private val allLocal = setOf(localhostRelay, localhostNameRelay, localNetworkRelay, tailnetRelay, lanNamedRelay, singleLabelRelay)

    @Test
    fun ownLocalRelays_alwaysFalse() {
        // Tor cannot reach the user's own local relays, whatever list they are in.
        val eval = buildEvaluation(trustedRelays = allLocal)
        allLocal.forEach { assertFalse(eval.useTor(it), "$it") }
        assertFalse(buildEvaluation(dmRelays = setOf(localhostRelay)).useTor(localhostRelay))
        assertFalse(buildEvaluation(moneyOpRelays = setOf(localNetworkRelay), moneyViaTor = true).useTor(localNetworkRelay))
        assertFalse(buildEvaluation(signerRelays = setOf(lanNamedRelay)).useTor(lanNamedRelay))
    }

    @Test
    fun someoneElsesLocalRelay_followsTheNewRelayPolicy() {
        // A local url from someone else's data that reached the pool (a stream's relay tag, a
        // link's hint) is never dialed on our network off Tor: with Tor on it fails inside Tor.
        allLocal.forEach {
            assertTrue(buildEvaluation(newViaTor = true).useTor(it), "$it")
            assertFalse(buildEvaluation(newViaTor = false).useTor(it), "$it")
        }
    }

    // --- .onion ---
    @Test
    fun onion_enabled_returnsTrue() {
        val eval = buildEvaluation(onionViaTor = true)
        assertTrue(eval.useTor(onionRelay))
    }

    @Test
    fun onion_disabled_returnsFalse() {
        val eval = buildEvaluation(onionViaTor = false)
        assertFalse(eval.useTor(onionRelay))
    }

    // --- DM relays ---
    @Test
    fun dm_enabled_returnsTrue() = assertTrue(buildEvaluation(dmViaTor = true).useTor(dmRelay))

    @Test
    fun dm_disabled_returnsFalse() = assertFalse(buildEvaluation(dmViaTor = false).useTor(dmRelay))

    // --- Trusted relays ---
    @Test
    fun trusted_enabled_returnsTrue() = assertTrue(buildEvaluation(trustedViaTor = true).useTor(trustedRelay))

    @Test
    fun trusted_disabled_returnsFalse() = assertFalse(buildEvaluation(trustedViaTor = false).useTor(trustedRelay))

    // --- New/unknown relays ---
    @Test
    fun unknown_enabled_returnsTrue() = assertTrue(buildEvaluation(newViaTor = true).useTor(clearnetRelay))

    @Test
    fun unknown_disabled_returnsFalse() = assertFalse(buildEvaluation(newViaTor = false).useTor(clearnetRelay))

    // --- Money-operation relays ---
    @Test
    fun money_enabled_returnsTrue() = assertTrue(buildEvaluation(moneyViaTor = true).useTor(moneyRelay))

    @Test
    fun money_disabled_returnsFalse() = assertFalse(buildEvaluation(moneyViaTor = false).useTor(moneyRelay))

    @Test
    fun money_takesPrecedenceOverNew() {
        // A money-op relay not in any other list must NOT fall through to the new-relay policy.
        val eval = buildEvaluation(moneyViaTor = false, newViaTor = true, dmRelays = emptySet(), trustedRelays = emptySet())
        assertFalse(eval.useTor(moneyRelay))
    }

    @Test
    fun money_takesPrecedenceOverTrustedAndDm() {
        // When the same relay is both a money-op relay and trusted/DM, money policy wins.
        val both = NormalizedRelayUrl("wss://wallet-and-trusted.relay.com/")
        val eval =
            buildEvaluation(
                moneyViaTor = true,
                dmViaTor = false,
                trustedViaTor = false,
                dmRelays = setOf(both),
                trustedRelays = setOf(both),
                moneyOpRelays = setOf(both),
            )
        assertTrue(eval.useTor(both))
    }

    @Test
    fun money_onionStillWins() {
        // .onion reachability check precedes the money classification.
        val onionMoney = NormalizedRelayUrl("wss://wallet.onion/")
        val eval = buildEvaluation(onionViaTor = false, moneyViaTor = true, moneyOpRelays = setOf(onionMoney))
        assertFalse(eval.useTor(onionMoney))
    }

    // --- Priority ---
    @Test
    fun onionInDmList_treatedAsOnion() {
        val onionDm = NormalizedRelayUrl("wss://dmrelay.onion/")
        val eval = buildEvaluation(onionViaTor = false, dmViaTor = true, dmRelays = setOf(onionDm))
        assertFalse(eval.useTor(onionDm)) // onion check first, disabled
    }

    @Test
    fun relayInBothDmAndTrusted_dmTakesPrecedence() {
        val both = NormalizedRelayUrl("wss://both.relay.com/")
        val eval = buildEvaluation(dmViaTor = true, trustedViaTor = false, dmRelays = setOf(both), trustedRelays = setOf(both))
        assertTrue(eval.useTor(both))
    }

    @Test
    fun relayInBothDmAndTrusted_dmDisabled_noFallToTrusted() {
        val both = NormalizedRelayUrl("wss://both.relay.com/")
        val eval = buildEvaluation(dmViaTor = false, trustedViaTor = true, dmRelays = setOf(both), trustedRelays = setOf(both))
        assertFalse(eval.useTor(both))
    }

    // --- Empty lists ---
    @Test
    fun emptyLists_allAreNew() {
        val eval = buildEvaluation(newViaTor = true, dmRelays = emptySet(), trustedRelays = emptySet())
        assertTrue(eval.useTor(clearnetRelay))
        assertTrue(eval.useTor(dmRelay)) // not in list, treated as new
    }

    // --- External mode ---
    @Test
    fun torExternal_routesLikeInternal() {
        val eval = buildEvaluation(torType = TorType.EXTERNAL, newViaTor = true, trustedRelays = setOf(trustedRelay, localhostRelay))
        assertTrue(eval.useTor(clearnetRelay))
        assertFalse(eval.useTor(localhostRelay))
    }
}
