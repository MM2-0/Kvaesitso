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
package com.vitorpamplona.amethyst.service.notifications

import android.content.Context
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.service.notifications.renderers.NwcPaymentNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Posts tray notifications for non-zap Lightning payments reported by the logged-in
 * account's connected NWC wallets.
 *
 * The relay subscription that receives these events is NOT here — it lives in
 * [com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip47WalletConnect.NwcNotificationsEoseManager],
 * grouped with the account's always-on zap/notification inbox subscriptions so it
 * shares their lifecycle (open while logged in, warm in the background). That
 * manager decrypts each notification and publishes non-zap payments to
 * `NwcSignerState.incomingNonZapPayments`; this class is only the Context-bound
 * bridge that drains that flow into an OS notification.
 *
 * Keeping decode and display decoupled means the flow stays populated even when OS
 * notifications are denied (a future in-app Notifications-tab consumer can drain
 * the same flow); [NwcPaymentNotifier] itself is what no-ops when the tray is off.
 */
class NwcPaymentNotificationWatcher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val accountFlow: Flow<Account?>,
) {
    fun start() {
        scope.launch(Dispatchers.IO) {
            accountFlow
                .distinctUntilChanged { a, b -> a?.signer?.pubKey == b?.signer?.pubKey }
                .collectLatest { account ->
                    account ?: return@collectLatest
                    account.nip47SignerState.incomingNonZapPayments.collect { tx ->
                        NwcPaymentNotifier.notify(context, account, tx)
                    }
                }
        }
    }
}
