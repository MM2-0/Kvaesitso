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
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.notifications.NotificationDraft
import com.vitorpamplona.amethyst.commons.notifications.NotificationMessage
import com.vitorpamplona.amethyst.commons.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.commons.notifications.NotificationTopic
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_badges_channel_message
import com.vitorpamplona.amethyst.commons.resources.app_notification_badges_channel_message_from
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent

/** Badge-award notifications, NIP-58 kind 8: "You earned a badge", awarded by X. */
object BadgeNotificationComposer {
    fun compose(
        account: Account,
        event: BadgeAwardEvent,
    ): NotificationDraft? {
        val note = LocalCache.getNoteIfExists(event.id) ?: return null
        if (!account.isAcceptable(note)) return null

        val issuer = LocalCache.getOrCreateUser(event.pubKey)
        val uri = NotificationRoutes.notificationsUri(NotificationRoutes.accountNpub(account), event.id)

        return NotificationDraft(
            id = event.id,
            users = listOf(issuer),
            notes = listOf(note),
            isComplete = { issuer.metadataOrNull()?.bestName() != null },
        ) {
            NotificationMessage(
                topic = NotificationTopic.BADGE,
                id = event.id,
                title = loadStringRes(Res.string.app_notification_badges_channel_message),
                body = loadStringRes(Res.string.app_notification_badges_channel_message_from, issuer.toBestDisplayName()),
                time = event.createdAt,
                pictureUrl = issuer.profilePicture(),
                uri = uri,
            )
        }
    }
}
