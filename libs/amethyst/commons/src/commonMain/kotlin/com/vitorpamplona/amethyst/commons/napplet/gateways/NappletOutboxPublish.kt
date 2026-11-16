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
package com.vitorpamplona.amethyst.commons.napplet.gateways

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long an `outbox.publish` waits on the relays before answering the napplet.
 *
 * A napplet waits on its own clock (the Game Boy emulator gives a save 10 s, signing included),
 * so one silent relay must not hold the answer: it comes when every relay has answered, or
 * [graceMs] after the first one took the event, or at [capMs], whichever is first. A relay that
 * had not answered by then counts as not taken; the event stays queued for it all the same.
 */
class NappletOutboxPublish(
    private val graceMs: Long = GRACE_MS,
    private val capMs: Long = CAP_MS,
) {
    /**
     * Sends through [send] to every relay in [targets] at once and returns each relay's verdict.
     * [send] publishes to one relay and calls back with whether it took the event; it is
     * cancelled once the answer is decided.
     */
    suspend fun publish(
        targets: Set<NormalizedRelayUrl>,
        send: suspend (relay: NormalizedRelayUrl, answered: (accepted: Boolean) -> Unit) -> Unit,
    ): Map<NormalizedRelayUrl, Boolean> {
        val answers = MutableStateFlow<Map<NormalizedRelayUrl, Boolean>>(emptyMap())
        coroutineScope {
            val sends = targets.map { relay -> launch { send(relay) { accepted -> answers.update { it + (relay to accepted) } } } }
            withTimeoutOrNull(capMs) {
                val first = answers.first { it.size == targets.size || it.containsValue(true) }
                if (first.size < targets.size) withTimeoutOrNull(graceMs) { answers.first { it.size == targets.size } }
            }
            sends.forEach { it.cancel() }
        }
        return targets.associateWith { answers.value[it] ?: false }
    }

    companion object {
        const val GRACE_MS = 1_500L
        const val CAP_MS = 6_000L
    }
}
