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
package com.vitorpamplona.amethyst.napplet

import android.content.Context
import android.content.Intent
import com.vitorpamplona.amethyst.commons.connectedApps.consent.ConsentQueue
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentInfo
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridges the main-process broker to the consent UI. The broker suspends in [requestConsent], which
 * queues the question and brings up [NappletConsentActivity]; the activity shows the oldest question
 * waiting and reports the user's choice back here.
 *
 * A dismissed dialog resolves to [GrantState.ASK] — i.e. "not authorized, ask again next
 * time" — so closing the prompt never silently grants nor permanently blocks.
 */
object NappletConsentCoordinator {
    private val queue = ConsentQueue<NappletConsentInfo, GrantState>(unanswered = GrantState.ASK)

    /** The questions waiting, oldest first; the activity shows the first. */
    val pending: StateFlow<List<ConsentQueue.Request<NappletConsentInfo, GrantState>>> = queue.pending

    suspend fun requestConsent(
        context: Context,
        info: NappletConsentInfo,
    ): GrantState = queue.ask(info, onQueued = { show(context) })

    /**
     * Brings the dialog back when questions still wait on it. Called when Amethyst returns to the
     * foreground: its task is excluded from Recents, so after a trip to the launcher nothing else
     * would show it again.
     */
    fun resurfacePending(context: Context) {
        if (queue.pending.value.isNotEmpty()) show(context)
    }

    // A launch the system refuses (from the background) is no failure of the request: it waits, and the
    // dialog comes back with the app.
    private fun show(context: Context) {
        runCatching {
            context.startActivity(
                Intent(context, NappletConsentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    /** Called by [NappletConsentActivity] with the user's decision. */
    fun complete(
        token: String,
        grant: GrantState,
    ) = queue.answer(token, grant)

    /** Called when the dialog is dismissed without a choice — fails closed (no grant). */
    fun cancel(token: String) = queue.dismiss(token)
}
