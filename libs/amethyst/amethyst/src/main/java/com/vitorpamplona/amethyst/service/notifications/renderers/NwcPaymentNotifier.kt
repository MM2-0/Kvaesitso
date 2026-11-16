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
package com.vitorpamplona.amethyst.service.notifications.renderers

import android.content.Context
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.notifications.composers.PaymentNotificationComposer
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.postStandard
import com.vitorpamplona.amethyst.service.notifications.notificationManager
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransaction

/**
 * Posts a tray notification for an incoming Lightning payment reported by the
 * connected NWC wallet (NIP-47 `payment_received`), on the green Payments channel.
 *
 * Zaps are intentionally NOT routed here — a `payment_received` whose transaction
 * metadata carries a NIP-57 zap request is filtered out upstream by
 * [com.vitorpamplona.amethyst.service.notifications.NwcPaymentNotificationWatcher],
 * because those already surface through the kind-9735 zap notification.
 */
object NwcPaymentNotifier {
    suspend fun notify(
        context: Context,
        account: Account,
        tx: NwcTransaction,
    ) {
        val nm = context.notificationManager()
        if (!nm.areNotificationsEnabled()) return

        val message = PaymentNotificationComposer.compose(account, tx) ?: return
        nm.postStandard(message, context)
    }
}
