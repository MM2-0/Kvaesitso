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
import com.vitorpamplona.amethyst.commons.notifications.NotificationContent
import com.vitorpamplona.amethyst.commons.notifications.NotificationDraft
import com.vitorpamplona.amethyst.commons.notifications.NotificationMessage
import com.vitorpamplona.amethyst.commons.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.commons.notifications.NotificationTopic
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_reposts_channel_message
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent

/** Repost notifications, NIP-18 kinds 6 and 16: "X reposted your post" with the reposted excerpt. */
object RepostNotificationComposer {
    fun compose(
        account: Account,
        event: RepostEvent,
    ) = draft(account, event.id, event.createdAt, event.pubKey, event.boostedEventId())

    fun compose(
        account: Account,
        event: GenericRepostEvent,
    ) = draft(account, event.id, event.createdAt, event.pubKey, event.boostedEventId())

    private fun draft(
        account: Account,
        id: String,
        createdAt: Long,
        boosterPubkey: String,
        boostedEventId: String?,
    ): NotificationDraft? {
        val boostedNote = boostedEventId?.let { LocalCache.checkGetOrCreateNote(it) }
        if (boostedNote != null && !account.isAcceptable(boostedNote)) return null

        val booster = LocalCache.getOrCreateUser(boosterPubkey)
        val uri = NotificationRoutes.notificationsUri(NotificationRoutes.accountNpub(account), id)

        return NotificationDraft(
            id = id,
            users = listOf(booster),
            notes = listOfNotNull(boostedNote),
            isComplete = { booster.metadataOrNull()?.bestName() != null },
        ) {
            NotificationMessage(
                topic = NotificationTopic.REPOST,
                id = id,
                title = loadStringRes(Res.string.app_notification_reposts_channel_message, booster.toBestDisplayName()),
                body = NotificationContent.excerpt(boostedNote?.event?.content, 140),
                time = createdAt,
                pictureUrl = booster.profilePicture(),
                uri = uri,
            )
        }
    }
}
