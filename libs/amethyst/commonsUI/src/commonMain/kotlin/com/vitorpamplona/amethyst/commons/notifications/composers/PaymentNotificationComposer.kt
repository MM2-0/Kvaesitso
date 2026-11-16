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
package com.vitorpamplona.amethyst.commons.notifications.composers

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.notifications.NotificationMessage
import com.vitorpamplona.amethyst.commons.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.commons.notifications.NotificationTopic
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_payments_channel_message
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransaction
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * An incoming Lightning payment reported by the connected NWC wallet (NIP-47
 * `payment_received`). The title leads with the amount; the body is the payer's comment.
 *
 * Zaps are filtered out before this: a payment whose metadata carries a NIP-57 zap request
 * already notifies through [ZapNotificationComposer].
 */
object PaymentNotificationComposer {
    suspend fun compose(
        account: Account,
        tx: NwcTransaction,
    ): NotificationMessage? {
        val msats = tx.amount ?: return null
        val amount = showAmount(BigDecimal(msats / 1000L))

        val id = tx.payment_hash ?: tx.invoice ?: tx.created_at?.toString() ?: return null
        val time = tx.settled_at ?: tx.created_at ?: TimeUtils.now()

        val title = loadStringRes(Res.string.app_notification_payments_channel_message, amount)
        val comment = tx.parsedMetadata()?.displayComment() ?: tx.displayDescription()

        return NotificationMessage(
            topic = NotificationTopic.PAYMENT_RECEIVED,
            id = id,
            title = title,
            body = comment ?: title,
            time = time,
            pictureUrl = null,
            uri = NotificationRoutes.notificationsUri(NotificationRoutes.accountNpub(account), id),
        )
    }
}
