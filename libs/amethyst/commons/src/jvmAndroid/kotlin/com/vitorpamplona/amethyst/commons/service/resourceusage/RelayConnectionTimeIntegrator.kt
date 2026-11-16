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
package com.vitorpamplona.amethyst.commons.service.resourceusage

import com.vitorpamplona.amethyst.commons.service.resourceusage.UsageKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Integrates relay-connection-time (Σ open connections × elapsed time) into
 * the ledger, split by network class and visibility. Connection-time is the
 * best single battery proxy the ping study found: most relays server-ping
 * every 30-70s, so the radio is active for as long as connections are open.
 */
class RelayConnectionTimeIntegrator(
    private val connectedCount: Flow<Int>,
    private val isMobile: Flow<Boolean?>,
    private val isForeground: Flow<Boolean>,
    accountant: ResourceUsageAccountant,
    nowMs: () -> Long = { elapsedRealtimeMillis() },
) : TimeSegmentIntegrator<RelayConnectionTimeIntegrator.SegmentState>(accountant, nowMs) {
    data class SegmentState(
        val count: Int,
        val mobile: Boolean,
        val foreground: Boolean,
    )

    fun start(scope: CoroutineScope): Job {
        registerFlushHook()
        return scope.launch {
            combine(connectedCount, isMobile, isForeground) { count, mobile, fg ->
                SegmentState(count, mobile ?: false, fg)
            }.collect { next -> transitionTo(next) }
        }
    }

    override fun account(
        state: SegmentState,
        elapsedMs: Long,
    ) {
        if (state.count <= 0 || elapsedMs <= 0) return
        accountant.add(UsageKeys.relayConnMs(state.mobile, state.foreground), state.count * elapsedMs)
    }
}
