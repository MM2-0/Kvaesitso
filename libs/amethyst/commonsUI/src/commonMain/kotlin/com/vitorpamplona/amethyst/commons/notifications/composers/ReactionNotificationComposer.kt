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
import com.vitorpamplona.amethyst.commons.resources.app_notification_reactions_channel_message
import com.vitorpamplona.amethyst.commons.resources.app_notification_reactions_channel_message_for
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji

/**
 * Reaction (like) notifications, kind 7: titled with the reactor's emoji and name, with the
 * reacted post's excerpt as the body. A NIP-30 custom emoji can't render as text, so it comes
 * back as a badge over the reactor's avatar.
 */
object ReactionNotificationComposer {
    private const val LIKE_EMOJI = "🤙" // 🤙
    private const val DISLIKE_EMOJI = "👎" // 👎

    fun compose(
        account: Account,
        event: ReactionEvent,
    ): NotificationDraft? {
        // NIP-25: the LAST `e` tag is the note actually reacted to.
        val reactedPostId = event.originalPost().lastOrNull() ?: return null
        val reactedNote = LocalCache.checkGetOrCreateNote(reactedPostId)
        if (reactedNote != null && !account.isAcceptable(reactedNote)) return null

        val author = LocalCache.getOrCreateUser(event.pubKey)
        val reactionContent = event.content
        val customEmojiUrl = CustomEmoji.createEmojiMap(event.tags)[reactionContent]
        val uri = NotificationRoutes.notificationsUri(NotificationRoutes.accountNpub(account), event.id)

        return NotificationDraft(
            id = event.id,
            users = listOf(author),
            notes = listOfNotNull(reactedNote),
            isComplete = { author.metadataOrNull()?.bestName() != null },
        ) {
            val user = author.toBestDisplayName()
            val title =
                if (customEmojiUrl != null) {
                    user
                } else {
                    "${symbolFor(reactionContent)} $user"
                }
            val reactedContent = NotificationContent.excerpt(reactedNote?.event?.content, 140)
            val body =
                if (reactedContent.isNotBlank()) {
                    loadStringRes(Res.string.app_notification_reactions_channel_message_for, reactedContent)
                } else {
                    loadStringRes(Res.string.app_notification_reactions_channel_message, user)
                }
            NotificationMessage(
                topic = NotificationTopic.REACTION,
                id = event.id,
                title = title,
                body = body,
                time = event.createdAt,
                pictureUrl = author.profilePicture(),
                uri = uri,
                badgeUrl = customEmojiUrl,
            )
        }
    }

    private fun symbolFor(content: String): String =
        when {
            content == ReactionEvent.LIKE || content.isBlank() -> LIKE_EMOJI
            content == ReactionEvent.DISLIKE -> DISLIKE_EMOJI
            else -> content
        }
}
