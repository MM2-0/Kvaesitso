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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TorWaitTest {
    private val dmRelay = NormalizedRelayUrl("wss://nos.lol/")
    private val newRelay = NormalizedRelayUrl("wss://relay.example.com/")
    private val trustedRelay = NormalizedRelayUrl("wss://relay.ditto.pub/")
    private val localRelay = NormalizedRelayUrl("ws://127.0.0.1:8080/")
    private val all = listOf(dmRelay, newRelay, trustedRelay, localRelay)

    /** The defaults: DM and new relays over Tor, the user's own lists in the clear. */
    private val evaluation =
        TorRelayEvaluation(
            TorRelaySettings(torType = TorType.INTERNAL, dmRelaysViaTor = true, newRelaysViaTor = true, trustedRelaysViaTor = false),
            // The local relay is the user's own; someone else's would wait for Tor like a new relay.
            RelayClassification(trusted = setOf(dmRelay, trustedRelay, localRelay), dm = setOf(dmRelay)),
        )

    @Test
    fun torRoutedRelaysWaitWhileTorIsNotReady() {
        assertEquals(listOf(dmRelay, newRelay), evaluation.waitingForTor(all, TorServiceStatus.Connecting))
        assertEquals(listOf(dmRelay, newRelay), evaluation.waitingForTor(all, TorServiceStatus.Error("no route")))
    }

    @Test
    fun nothingWaitsOnceTorIsReady() {
        assertEquals(emptyList(), evaluation.waitingForTor(all, TorServiceStatus.Active(9050)))
    }

    @Test
    fun nothingWaitsWhenTorIsTurnedOff() {
        val off = TorRelayEvaluation(TorRelaySettings(torType = TorType.OFF), evaluation.classification)
        assertEquals(emptyList(), off.waitingForTor(all, TorServiceStatus.Off))
    }

    /** Android's session bypass: Tor is off but the relays keep their Tor policy, and dial directly. */
    @Test
    fun nothingWaitsWhileTorIsBypassed() {
        assertEquals(emptyList(), evaluation.waitingForTor(all, TorServiceStatus.Off))
    }

    @Test
    fun stuckOnlyAfterTheWait() =
        runTest {
            val status = MutableStateFlow<TorServiceStatus>(TorServiceStatus.Connecting)
            val seen = mutableListOf<Boolean>()
            val job =
                status
                    .stuckAfter(TOR_STUCK_AFTER_MS)
                    .distinctUntilChanged()
                    .onEach { seen += it }
                    .launchIn(backgroundScope)

            runCurrent()
            assertEquals(listOf(false), seen)

            advanceTimeBy(TOR_STUCK_AFTER_MS - 1)
            runCurrent()
            assertEquals(listOf(false), seen)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf(false, true), seen)

            // Tor comes up late: no longer stuck.
            status.value = TorServiceStatus.Active(9050)
            runCurrent()
            assertEquals(listOf(false, true, false), seen)
            job.cancel()
        }

    @Test
    fun aQuickBootstrapNeverReadsStuck() =
        runTest {
            val status = MutableStateFlow<TorServiceStatus>(TorServiceStatus.Connecting)
            val seen = mutableListOf<Boolean>()
            status
                .stuckAfter(TOR_STUCK_AFTER_MS)
                .distinctUntilChanged()
                .onEach { seen += it }
                .launchIn(backgroundScope)

            advanceTimeBy(10_000)
            status.value = TorServiceStatus.Active(9050)
            advanceTimeBy(TOR_STUCK_AFTER_MS * 2)
            runCurrent()
            assertEquals(listOf(false), seen)
        }

    @Test
    fun aFailedStartIsStuckAtOnce() =
        runTest {
            val status = MutableStateFlow<TorServiceStatus>(TorServiceStatus.Error("could not start"))
            val seen = mutableListOf<Boolean>()
            status.stuckAfter(TOR_STUCK_AFTER_MS).onEach { seen += it }.launchIn(backgroundScope)
            runCurrent()
            assertEquals(listOf(true), seen)
        }
}
