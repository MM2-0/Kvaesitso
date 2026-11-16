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
import com.vitorpamplona.amethyst.commons.resources.app_notification_mentions_channel_message
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip01Core.core.Event
import org.jetbrains.compose.resources.StringResource

/**
 * Text-mention notifications: someone mentioned, quoted or cited you in a note (kind 1), or
 * asked a poll that tags you. Titled "X mentioned you", with the post as the body.
 *
 * Media, articles and git events have their own composers; this covers plain text and polls.
 */
object MentionNotificationComposer {
    fun compose(
        account: Account,
        event: Event,
        titleRes: StringResource = Res.string.app_notification_mentions_channel_message,
    ): NotificationDraft? {
        val note = LocalCache.getNoteIfExists(event.id) ?: return null
        if (!account.isAcceptable(note)) return null

        val author = LocalCache.getOrCreateUser(event.pubKey)
        val uri = NotificationRoutes.noteUri(note, NotificationRoutes.accountNpub(account))

        // Users cited inline in the text (nostr:npub/nprofile) are loaded too, so their
        // names fill in as their kind:0 metadata arrives. An inline image link becomes the
        // big picture instead of a raw URL.
        val rendered = NotificationContent.renderNoteText(event.content)

        return NotificationDraft(
            id = event.id,
            users = listOf(author) + rendered.citedUsers,
            notes = listOf(note),
            isComplete = {
                author.metadataOrNull()?.bestName() != null &&
                    rendered.citedUsers.all { it.metadataOrNull()?.bestName() != null }
            },
        ) {
            val body = NotificationContent.renderNoteText(event.content)
            NotificationMessage(
                topic = NotificationTopic.MENTION,
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
