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
package com.vitorpamplona.amethyst.commons.service.call

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallManager
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallState
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * Process-level singleton that bridges the active [CallManager] and
 * [AccountViewModel] between the main activity and
 * [com.vitorpamplona.amethyst.ui.call.CallActivity] (which runs in its
 * own window but in the same process).
 *
 * No call controller / session is held here — each [CallActivity]
 * creates and owns its own [com.vitorpamplona.amethyst.ui.call.session.CallSession]
 * whose lifetime is tied to the Activity's lifecycle.
 */
object CallSessionBridge {
    /** Account-scoped: survives MainActivity being destroyed mid-call. */
    var callManager: CallManager? = null
        private set

    /** Account-scoped: everything [CallActivity] needs (signer, settings, signaling publish). */
    var account: Account? = null
        private set

    /**
     * Activity-scoped, and therefore nullable at any time: MainActivity can be destroyed while a
     * call is still running. Only consumers that genuinely need ViewModel-level helpers should
     * read this, and they must tolerate null.
     */
    var accountViewModel: AccountViewModel? = null
        private set

    fun set(
        callManager: CallManager,
        account: Account,
        accountViewModel: AccountViewModel,
    ) {
        this.callManager = callManager
        this.account = account
        this.accountViewModel = accountViewModel
    }

    /**
     * Drops only the Activity-scoped [accountViewModel] reference. Called from
     * [AccountViewModel.onCleared], which fires on every MainActivity destruction — including
     * while a call is in progress — so it must leave [callManager] and [account] intact.
     *
     * While a call is up the reference is kept: [CallActivity] still renders its UI from this
     * ViewModel and holds a strong reference to it either way, so clearing here would free
     * nothing and would only break the running call's UI. It is replaced wholesale by [set] as
     * soon as MainActivity comes back, and dropped by [clear] on logout / account switch.
     *
     * Only [viewModel]'s own reference is dropped: on an account switch the next account's
     * ViewModel is created (and [set]) before the previous one is cleared.
     */
    fun clearViewModel(viewModel: AccountViewModel) {
        if (accountViewModel !== viewModel) return
        if (callManager?.state?.value !is CallState.Idle) return
        accountViewModel = null
    }

    /**
     * Ends the current call and clears all references. Called on a real logout or account switch
     * from `AccountSessionManager`, alongside `NestBridge.clear()`.
     *
     * Uses [CallManager.reset] (non-blocking, no mutex) instead of
     * [CallManager.hangup] to avoid deadlocking on `stateMutex` if
     * a cancelled coroutine still holds it. Hangup signaling to the remote peer is the
     * responsibility of [CallActivity.onDestroy] and [CallForegroundService], not
     * the bridge teardown.
     */
    fun clear() {
        callManager?.reset()
        callManager = null
        account = null
        accountViewModel = null
    }
}
