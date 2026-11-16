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
import com.vitorpamplona.amethyst.commons.resources.app_notification_articles_channel_message
import com.vitorpamplona.amethyst.commons.resources.app_notification_articles_channel_message_highlight
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent

/**
 * Article and highlight notifications: long-form (kind 30023), wiki (30818) and NIP-84
 * highlights (9802) that mention or highlight your writing. A highlight shows the highlighted
 * passage; an article shows its excerpt.
 */
object ArticleNotificationComposer {
    fun compose(
        account: Account,
        event: Event,
    ): NotificationDraft? {
        val note = LocalCache.getNoteIfExists(event.id) ?: return null
        if (!account.isAcceptable(note)) return null

        val author = LocalCache.getOrCreateUser(event.pubKey)
        val uri = NotificationRoutes.noteUri(note, NotificationRoutes.accountNpub(account))

        val titleRes =
            if (event is HighlightEvent) {
                Res.string.app_notification_articles_channel_message_highlight
            } else {
                Res.string.app_notification_articles_channel_message
            }
        val bodySource = if (event is HighlightEvent) event.quote() else event.content
        val rendered = NotificationContent.renderNoteText(bodySource)

        return NotificationDraft(
            id = event.id,
            users = listOf(author) + rendered.citedUsers,
            notes = listOf(note),
            isComplete = {
                author.metadataOrNull()?.bestName() != null &&
                    rendered.citedUsers.all { it.metadataOrNull()?.bestName() != null }
            },
        ) {
            val body = NotificationContent.renderNoteText(bodySource)
            NotificationMessage(
                topic = NotificationTopic.ARTICLE,
                id = event.id,
                title = loadStringRes(titleRes, author.toBestDisplayName()),
                body = body.text,
                time = event.createdAt,
                pictureUrl = author.profilePicture(),
                uri = uri,
                bigPictureUrl = body.imageUrl,
            )
        }
    }
}
