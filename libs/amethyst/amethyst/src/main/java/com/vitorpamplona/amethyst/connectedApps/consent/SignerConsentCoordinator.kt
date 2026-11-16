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
import com.vitorpamplona.amethyst.commons.connectedApps.consent.PendingConsent
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConsentInfo
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_notif_sign_title
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bridges the broker to the per-operation signer consent UI. The signer services requests
 * concurrently (so their prompts can batch), so several requests can await consent at once: they all
 * land in [pending], one [SignerConsentActivity] observes that list and shows a single-request dialog
 * or a batched list, and each resolved token completes its own request. A dismissed/ignored request
 * resolves to [SignerOpGrant.DenyOnce] — fails closed.
 */
object SignerConsentCoordinator {
    private val queue = ConsentQueue<SignerConsentInfo, SignerOpGrant>(unanswered = SignerOpGrant.DenyOnce)

    /** The live set of requests awaiting the user's decision; the Activity renders this. */
    val pending: StateFlow<List<PendingConsent>> = queue.pending

    // A stable notification id (one prompt notification for the whole batch, updated as requests
    // arrive) so concurrent requests don't each post their own.
    private val batchNotificationId = "nip46-signer-consent".hashCode()

    // Guards the surface (post/cancel of the one shared notification) so a concurrent arrival's post
    // can't be clobbered by another request's teardown cancel. A request joins [pending] before it
    // posts and leaves before it checks, so under this lock a request that finds [pending] empty
    // cancels before any later arrival posts. Without it, request A could read "pending now empty" and
    // then cancel AFTER request B posted a fresh notification under the same id, leaving B with no UI
    // while backgrounded (silent deny at timeout).
    private val surfaceLock = Mutex()

    suspend fun requestConsent(
        context: Context,
        info: SignerConsentInfo,
    ): SignerOpGrant =
        queue.ask(
            info,
            onQueued = {
                surfaceLock.withLock {
                    // Fast path when Amethyst already owns the foreground: open the dialog directly. When the app
                    // is backgrounded this is silently dropped by Android 12+ BAL, so the full-screen-intent
                    // notification is what surfaces the prompt. Both are idempotent — the Activity is singleTop and
                    // observes [pending], and the notification uses a stable id, so concurrent requests just
                    // refresh the one prompt. Wrapped because a BAL-blocked launch can throw rather than no-op.
                    runCatching {
                        context.startActivity(
                            Intent(context, SignerConsentActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        )
                    }
                    SignerConsentNotifier.show(
                        context = context,
                        activityClass = SignerConsentActivity::class.java,
                        extraKey = EXTRA_TOKEN,
                        token = "nip46-signer-consent",
                        titleRes = Res.string.nip46_signer_notif_sign_title,
                    )
                }
            },
            onSettled = {
                surfaceLock.withLock {
                    if (queue.pending.value.isEmpty()) SignerConsentNotifier.cancel(context, batchNotificationId)
                }
            },
        )

    /**
     * Brings the consent dialog back to the front when requests are still waiting on it. Called when
     * Amethyst returns to the foreground: the dialog's task is excluded from Recents, so after a trip
     * to the launcher nothing else would show it again.
     */
    fun resurfacePending(context: Context) {
        if (queue.pending.value.isEmpty()) return
        runCatching {
            context.startActivity(
                Intent(context, SignerConsentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    fun complete(
        token: String,
        grant: SignerOpGrant,
    ) = queue.answer(token, grant)

    fun completeAll(
        tokens: Collection<String>,
        grant: SignerOpGrant,
    ) {
        tokens.forEach { complete(it, grant) }
    }

    const val EXTRA_TOKEN = "napplet_signer_consent_token"
}
