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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

/**
 * How long Tor may keep failing to carry traffic before the UI says relays are stuck waiting for
 * it. The same minute Android waits before offering to continue without Tor: an ordinary bootstrap
 * finishes well inside it, so a startup never flashes the warning.
 */
const val TOR_STUCK_AFTER_MS: Long = 60_000L

/**
 * Tor is meant to carry traffic but cannot yet: starting, or failed. Not when it is off, which is
 * either the user's choice or Android's session bypass, where the relays dial directly instead.
 */
val TorServiceStatus.isHoldingRelays: Boolean
    get() = this is TorServiceStatus.Connecting || this is TorServiceStatus.Error

/**
 * The relays among [relays] that are not dialed yet because they go through Tor and Tor cannot
 * carry traffic. The same rule as the platforms' websocket gate (a Tor-routed relay waits for a
 * ready Tor), so this names exactly the relays being held back.
 */
fun TorRelayEvaluation.waitingForTor(
    relays: Collection<NormalizedRelayUrl>,
    status: TorServiceStatus,
): List<NormalizedRelayUrl> = if (status.isHoldingRelays) relays.filter(::useTor) else emptyList()

/**
 * True once Tor has been trying to start for [afterMs] without becoming ready, or has failed; false
 * while it is ready or off. For a backend whose status goes straight from connecting to ready or
 * failed: one that passes through off while retrying would restart the wait each time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<TorServiceStatus>.stuckAfter(afterMs: Long): Flow<Boolean> =
    transformLatest { status ->
        when (status) {
            is TorServiceStatus.Connecting -> {
                emit(false)
                delay(afterMs)
                emit(true)
            }
            is TorServiceStatus.Error -> emit(true)
            is TorServiceStatus.Active, TorServiceStatus.Off -> emit(false)
        }
    }
