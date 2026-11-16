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
import kotlinx.coroutines.launch

/**
 * Integrates time-with-UI-visible into the ledger ([UsageKeys.APP_FG_MS]).
 * Screen-on time is what display power is proportional to, and it's the
 * denominator that makes every other counter interpretable (MB per hour in
 * app vs MB while backgrounded).
 */
class ForegroundTimeIntegrator(
    private val isForeground: Flow<Boolean>,
    accountant: ResourceUsageAccountant,
    nowMs: () -> Long = { elapsedRealtimeMillis() },
) : TimeSegmentIntegrator<Unit>(accountant, nowMs) {
    fun start(scope: CoroutineScope): Job {
        registerFlushHook()
        return scope.launch {
            isForeground.collect { fg -> transitionTo(if (fg) Unit else null) }
        }
    }

    override fun account(
        state: Unit,
        elapsedMs: Long,
    ) {
        if (elapsedMs > 0) accountant.add(UsageKeys.APP_FG_MS, elapsedMs)
    }
}
