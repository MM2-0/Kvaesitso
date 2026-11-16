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
package com.vitorpamplona.amethyst.commons.relays.health

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Detects the "Tor is Active but every circuit is dead" state and asks [onCircuitsDead] to
 * self-heal it.
 *
 * Tor can reach [com.vitorpamplona.amethyst.ui.tor.TorServiceStatus.Active] — SOCKS proxy up,
 * bootstrap "succeeded" off cached consensus — while no exit circuit actually works (the
 * `ExitTimeout` / `RESOLVEFAILED` barrage). The lifecycle watchdogs in
 * [com.vitorpamplona.amethyst.ui.tor.TorManager] can't see this: they only arm while status is
 * `Connecting`. The relay layer is the only place that has both halves of the signal — per-relay
 * success ([RelayConnectionListener.onConnected]) and failure
 * ([RelayConnectionListener.onCannotConnect]) — plus knowledge of which relays are Tor-routed.
 *
 * The discriminator (the whole point) is a *sustained* failure streak: fire only when, while Tor
 * is Active and connectivity is up, there is an unbroken run of Tor-routed failures that (a) holds
 * at least [FAIL_THRESHOLD] failures and (b) has lasted at least [SUSTAINED_MS], with **zero**
 * Tor-routed successes interrupting it. Any Tor success ends the streak — one good open means
 * circuits work, so suppress.
 *
 * The [SUSTAINED_MS] floor is essential, not cosmetic: the instant Tor flips Active the pool dials
 * the whole Tor-routed relay set at once, and on freshly built circuits a burst of ≥8 can fail
 * *before* the first Tor relay completes its handshake. Counting failures in a short window would
 * fire ~2s into a perfectly healthy warmup and wipe the good client (observed on device). Requiring
 * the streak to span [SUSTAINED_MS] gives a natural post-Active warmup grace — a real success
 * during warmup clears the streak; only genuinely dead circuits keep failing for the full span.
 * A gap longer than [SUSTAINED_MS] between failures also restarts the streak, so sparse unrelated
 * failures never accumulate.
 *
 * Gating on connectivity avoids resetting Tor during a general network outage (where clearnet
 * relays fail too). Arti emits no per-stream success log, so this success signal can only come from
 * the relay layer — a failure-only signal can't tell "all dead" from "some dead", and would reset
 * Tor every time a few dead relays are dialed.
 *
 * Register with [register] and tear down with [unregister]. All callbacks arrive on relay/OkHttp
 * threads, so the streak state is guarded by an intrinsic lock.
 */
class TorCircuitHealthTracker(
    private val client: INostrClient,
    private val isTorRouted: (NormalizedRelayUrl) -> Boolean,
    private val isTorActive: () -> Boolean,
    private val isConnectivityActive: () -> Boolean,
    private val onCircuitsDead: () -> Unit,
    private val nowMs: () -> Long = TimeUtils::nowMillis,
) {
    /** Epoch-millis of the first failure in the current unbroken streak, or -1 when no streak is open. */
    private var streakStartMs: Long = -1L

    /** Epoch-millis of the most recent failure in the current streak. */
    private var lastFailureMs: Long = -1L

    /** Failures in the current streak. */
    private var streakCount: Int = 0

    /** Guards the streak fields; KMP-safe replacement for `synchronized`. */
    private val lock = KmpLock()

    private val listener =
        object : RelayConnectionListener {
            override fun onConnected(
                relay: IRelayClient,
                pingMillis: Int,
                compressed: Boolean,
            ) {
                if (!isTorRouted(relay.url)) return
                // One Tor success means circuits work — end the streak.
                lock.withLock { resetStreak() }
            }

            override fun onCannotConnect(
                relay: IRelayClient,
                errorMessage: String,
            ) {
                if (!isTorRouted(relay.url)) return
                if (!isTorActive() || !isConnectivityActive()) return

                val now = nowMs()
                val fire =
                    lock.withLock {
                        // Start a fresh streak on the first failure, or whenever the gap since the
                        // last failure is too long for them to count as the same sustained outage.
                        if (streakStartMs < 0 || now - lastFailureMs > SUSTAINED_MS) {
                            streakStartMs = now
                            streakCount = 0
                        }
                        streakCount++
                        lastFailureMs = now

                        if (streakCount >= FAIL_THRESHOLD && now - streakStartMs >= SUSTAINED_MS) {
                            resetStreak() // re-arm; a fresh streak must build before firing again
                            true
                        } else {
                            false
                        }
                    }

                if (fire) {
                    Log.w(TAG) { "Tor Active but $FAIL_THRESHOLD+ Tor relays failed for ${SUSTAINED_MS}ms+ with no success — requesting self-heal" }
                    onCircuitsDead()
                }
            }
        }

    private fun resetStreak() {
        streakStartMs = -1L
        lastFailureMs = -1L
        streakCount = 0
    }

    fun register() {
        client.addConnectionListener(listener)
    }

    fun unregister() {
        client.removeConnectionListener(listener)
    }

    companion object {
        const val TAG = "TorCircuitHealthTracker"

        /** Unbroken Tor-routed failures needed (alongside the [SUSTAINED_MS] floor) to declare circuits dead. */
        const val FAIL_THRESHOLD = 8

        /**
         * The failure streak must span at least this long before it counts as a dead transport.
         * Doubles as the post-Active warmup grace and the max gap between failures of one streak.
         */
        const val SUSTAINED_MS = 30_000L
    }
}
