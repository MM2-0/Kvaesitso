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
package com.vitorpamplona.amethyst.connectedApps.consent

import android.content.Context
import android.content.Intent
import com.vitorpamplona.amethyst.commons.connectedApps.consent.ConsentQueue
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_notif_connect_title
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridges the broker to the "Connect to Nostr" first-connect UI. Suspends in [requestConnect];
 * the Activity resolves the request with the user's choice.
 * A dismissed dialog resolves to [AppConnectResult.Cancelled] — fails closed, no silent grant.
 */
object SignerConnectCoordinator {
    private val queue = ConsentQueue<SignerConnectInfo, AppConnectResult>(unanswered = AppConnectResult.Cancelled)

    /** The requests waiting, oldest first; the activity shows the first. */
    val pending: StateFlow<List<ConsentQueue.Request<SignerConnectInfo, AppConnectResult>>> = queue.pending

    /** Brings the dialog back when requests still wait on it (see SignerConsentCoordinator.resurfacePending). */
    fun resurfacePending(context: Context) {
        if (queue.pending.value.isEmpty()) return
        runCatching {
            context.startActivity(
                Intent(context, SignerConnectActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    suspend fun requestConnect(
        context: Context,
        info: SignerConnectInfo,
    ): AppConnectResult {
        var notificationId: Int? = null
        return queue.ask(
            info,
            onQueued = { request ->
                // Fast path when Amethyst already owns the foreground; the full-screen-intent notification
                // below is what surfaces the dialog when a connect request arrives while backgrounded (see
                // SignerConsentNotifier). Wrapped because a BAL-blocked launch can throw on some OEMs.
                runCatching {
                    context.startActivity(
                        Intent(context, SignerConnectActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            .putExtra(EXTRA_TOKEN, request.token),
                    )
                }
                notificationId =
                    SignerConsentNotifier.show(
                        context = context,
                        activityClass = SignerConnectActivity::class.java,
                        extraKey = EXTRA_TOKEN,
                        token = request.token,
                        titleRes = Res.string.nip46_signer_notif_connect_title,
                    )
            },
            onSettled = { notificationId?.let { SignerConsentNotifier.cancel(context, it) } },
        )
    }

    fun infoFor(token: String): SignerConnectInfo? = queue.info(token)

    fun complete(
        token: String,
        result: AppConnectResult,
    ) = queue.answer(token, result)

    fun cancel(token: String) = queue.dismiss(token)

    const val EXTRA_TOKEN = "napplet_connect_token"
}
