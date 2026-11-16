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
package com.vitorpamplona.amethyst.commons.viewmodels

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide signal published by the platform-side connectivity
 * listener (Android: `NestForegroundService`'s
 * `ConnectivityManager.NetworkCallback`) and consumed by every active
 * [NestViewModel] to recycle its [com.vitorpamplona.nestsclient.NestsListener]
 * / [com.vitorpamplona.nestsclient.NestsSpeaker] sessions.
 *
 * Why this exists: when a phone hands over from Wi-Fi to cellular (or
 * the other way), the local socket binding's source IP changes. The
 * QUIC connection sitting on the previous socket isn't notified of
 * the change — it'll keep retransmitting into the void until its PTO
 * fires (`~30 s` for an idle connection). Without an external nudge
 * the user hears 30 seconds of silence before the wrapper's reconnect
 * loop notices the failure. With this bus, the wrapper recycles the
 * QUIC session the moment the platform sees the network change,
 * shrinking the audible gap to a single re-handshake (≈ 1 s on
 * typical mobile networks).
 *
 * Decoupled from `android.net.ConnectivityManager` so commons stays
 * platform-free — the platform layer translates `onAvailable` /
 * `onLost` callbacks into a single `publish()` event before the
 * VM observes it.
 */
object NestNetworkChangeBus {
    /**
     * `extraBufferCapacity = 1, DROP_OLDEST` — the consumer only cares
     * that *a* network change happened, not how many. A burst of
     * onLost / onAvailable calls during a Wi-Fi flap collapses to a
     * single recycle event, which is exactly what we want (one
     * handshake instead of N).
     */
    private val _events =
        MutableSharedFlow<Unit>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /**
     * Hot flow of network-change events. Consumers ([NestViewModel])
     * collect this and call `recycleSession()` on their listener +
     * speaker.
     */
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    /**
     * Publish a network-change event. Only the platform-side listener
     * calls this; consumers observe via [events].
     */
    fun publish() {
        _events.tryEmit(Unit)
    }
}
