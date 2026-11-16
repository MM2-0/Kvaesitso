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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Integrates time-per-screen into the ledger (`screen.<Name>.ms`): which
 * parts of the app the display/CPU time actually goes to, so a report can
 * distinguish "8 h of video" from "8 h of feeds".
 *
 * PRIVACY: only the route's base name is recorded ([screenNameOf] strips
 * navigation arguments before anything reaches the ledger) — "Profile" is
 * tracked, whose profile never is. Time only accrues while the app is in the
 * foreground: the current-route × foreground combination is the segment
 * state, so backgrounding on a screen closes its segment.
 */
class ScreenTimeIntegrator(
    accountant: ResourceUsageAccountant,
    nowMs: () -> Long = { elapsedRealtimeMillis() },
) : TimeSegmentIntegrator<String>(accountant, nowMs) {
    private val currentScreen = MutableStateFlow<String?>(null)

    /** Called from the navigation listener with an already-sanitized name (or null when unknown). */
    fun onScreen(name: String?) {
        currentScreen.value = name
    }

    fun start(
        scope: CoroutineScope,
        isForeground: Flow<Boolean>,
    ): Job {
        registerFlushHook()
        return scope.launch {
            combine(currentScreen, isForeground) { screen, fg -> if (fg) screen else null }
                .collect { transitionTo(it) }
        }
    }

    override fun account(
        state: String,
        elapsedMs: Long,
    ) {
        if (elapsedMs > 0) accountant.add(UsageKeys.screenMs(state), elapsedMs)
    }

    companion object {
        /**
         * Reduces a Navigation Compose route pattern to its bare screen name:
         * `com...routes.Route.Profile/{userId}?tab={tab}` becomes `Profile`.
         * Everything after `/` or `?` — where the arguments live — is dropped
         * BEFORE the value leaves the navigation layer.
         */
        fun screenNameOf(route: String?): String? =
            route
                ?.substringBefore('/')
                ?.substringBefore('?')
                ?.substringAfterLast('.')
                ?.takeIf { it.isNotBlank() }
    }
}
