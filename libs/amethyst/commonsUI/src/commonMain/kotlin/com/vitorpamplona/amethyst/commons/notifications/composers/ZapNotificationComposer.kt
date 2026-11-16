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
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.notifications.NotificationContent
import com.vitorpamplona.amethyst.commons.notifications.NotificationDraft
import com.vitorpamplona.amethyst.commons.notifications.NotificationMessage
import com.vitorpamplona.amethyst.commons.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.commons.notifications.NotificationTopic
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_nutzap_channel_message_from
import com.vitorpamplona.amethyst.commons.resources.app_notification_onchain_channel_message_from
import com.vitorpamplona.amethyst.commons.resources.app_notification_zaps_channel_message
import com.vitorpamplona.amethyst.commons.resources.app_notification_zaps_channel_message_for
import com.vitorpamplona.amethyst.commons.resources.app_notification_zaps_channel_message_from
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.compareToValue
import org.jetbrains.compose.resources.StringResource

/**
 * Zap notifications: Lightning (NIP-57, kind 9735), Cashu nutzaps (NIP-61, kind 9321) and
 * onchain zaps (kind 8333). The title leads with the amount; the body names the sender and
 * the zapped post's excerpt. A private Lightning zap's sender is decrypted once, up front.
 */
object ZapNotificationComposer {
    private val MIN_ZAP_AMOUNT = BigDecimal(10)

    suspend fun compose(
        account: Account,
        event: ZapReceiptEvent,
    ): NotificationDraft? {
        LocalCache.getNoteIfExists(event.id) ?: return null
        val zapRequestNote = event.zapRequest?.id?.let { LocalCache.checkGetOrCreateNote(it) } ?: return null
        val zappedNote = event.zappedPost().firstOrNull()?.let { LocalCache.checkGetOrCreateNote(it) } ?: return null
        if (!account.isAcceptable(zappedNote)) return null
        val zapAmount = event.amount ?: return null
        if (zapAmount.compareToValue(MIN_ZAP_AMOUNT) < 0) return null

        val zapRequestEvent = zapRequestNote.event as? ZapRequestEvent ?: return null
        // Resolve the (possibly private) zapper once; never decrypt again on each render.
        val decrypted = NotificationContent.decryptZapContentAuthor(zapRequestEvent, account.signer) ?: return null
        val sender = LocalCache.getOrCreateUser(decrypted.pubKey)
        val comment = decrypted.content.ifBlank { null }
        val amount = showAmount(zapAmount)

        return draft(
            account = account,
            id = event.id,
            createdAt = event.createdAt,
            sender = sender,
            zappedNote = zappedNote,
            title = { _ -> zapTitle(amount, comment) },
            body = { user, excerpt -> fromLine(Res.string.app_notification_zaps_channel_message_from, user, excerpt) },
        )
    }

    fun compose(
        account: Account,
        event: NutzapEvent,
    ): NotificationDraft? {
        val zappedNote = event.linkedEventIds().lastOrNull()?.let { LocalCache.checkGetOrCreateNote(it) }
        if (zappedNote != null && !account.isAcceptable(zappedNote)) return null
        val sender = LocalCache.getOrCreateUser(event.pubKey)

        return draft(
            account = account,
            id = event.id,
            createdAt = event.createdAt,
            sender = sender,
            zappedNote = zappedNote,
            title = { user -> loadStringRes(Res.string.app_notification_nutzap_channel_message_from, user) },
            body = { user, excerpt -> excerpt.ifBlank { user } },
        )
    }

    fun compose(
        account: Account,
        event: OnchainZapEvent,
    ): NotificationDraft? {
        val zappedNote = event.zappedEvent()?.let { LocalCache.checkGetOrCreateNote(it) }
        if (zappedNote != null && !account.isAcceptable(zappedNote)) return null
        val sender = LocalCache.getOrCreateUser(event.pubKey)
        val sats = event.claimedAmountInSats()

        return draft(
            account = account,
            id = event.id,
            createdAt = event.createdAt,
            sender = sender,
            zappedNote = zappedNote,
            title = { user ->
                if (sats != null) {
                    loadStringRes(Res.string.app_notification_zaps_channel_message, showAmount(BigDecimal(sats)))
                } else {
                    loadStringRes(Res.string.app_notification_onchain_channel_message_from, user)
                }
            },
            body = { user, excerpt -> fromLine(Res.string.app_notification_onchain_channel_message_from, user, excerpt) },
        )
    }

    private fun draft(
        account: Account,
        id: String,
        createdAt: Long,
        sender: User,
        zappedNote: Note?,
        title: suspend (String) -> String,
        body: suspend (String, String) -> String,
    ): NotificationDraft {
        val uri = NotificationRoutes.notificationsUri(NotificationRoutes.accountNpub(account), id)

        return NotificationDraft(
            id = id,
            users = listOf(sender),
            notes = listOfNotNull(zappedNote),
            isComplete = { sender.metadataOrNull()?.bestName() != null },
        ) {
            val user = sender.toBestDisplayName()
            val excerpt =
                zappedNote?.let { NotificationContent.excerpt(NotificationContent.decryptContent(it, account.signer), 140) } ?: ""
            NotificationMessage(
                topic = NotificationTopic.ZAP,
                id = id,
                title = title(user),
                body = body(user, excerpt),
                time = createdAt,
                pictureUrl = sender.profilePicture(),
                uri = uri,
            )
        }
    }

    private suspend fun zapTitle(
        amount: String,
        comment: String?,
    ): String {
        val base = loadStringRes(Res.string.app_notification_zaps_channel_message, amount)
        return if (comment != null) "$base ($comment)" else base
    }

    private suspend fun fromLine(
        fromRes: StringResource,
        user: String,
        excerpt: String,
    ): String {
        var content = loadStringRes(fromRes, user)
        if (excerpt.isNotBlank()) {
            content += " " + loadStringRes(Res.string.app_notification_zaps_channel_message_for, excerpt)
        }
        return content
    }
}
