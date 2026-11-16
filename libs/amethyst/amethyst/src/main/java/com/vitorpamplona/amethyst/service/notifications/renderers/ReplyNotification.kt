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
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.notifications.NotificationContent
import com.vitorpamplona.amethyst.commons.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_me
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.service.notifications.NotificationCategory
import com.vitorpamplona.amethyst.service.notifications.NotificationEnricher
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.InlineReplyTarget
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.ParentMessage
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.postConversation
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.replyGroupKeyFor
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.replySummaryIdFor
import com.vitorpamplona.amethyst.service.notifications.notificationManager
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * Reply notifications — someone replied to your note (NIP-10 kind 1, NIP-22
 * comment kind 1111, or a NIP-28 public-chat reply into your message). Rendered
 * with MessagingStyle: the parent (your) message is shown as prior context, the
 * reply as the latest message, and an inline Reply action lets you answer from
 * the shade. Grouped per-thread so a busy thread collapses into one bundle.
 *
 * The replier's name + avatar are enriched observably.
 */
object ReplyNotification {
    suspend fun notify(
        context: Context,
        account: Account,
        event: Event,
        parentNote: Note?,
        threadRootId: String,
    ) {
        val replyNote = LocalCache.getNoteIfExists(event.id) ?: return
        if (!account.isAcceptable(replyNote)) return

        val author = LocalCache.getOrCreateUser(event.pubKey)
        val accountNpub = NotificationRoutes.accountNpub(account)
        val uri = NotificationRoutes.noteUri(replyNote, accountNpub)

        val citedUsers = NotificationContent.resolveMentions(event.content).citedUsers

        // The parent may be the account's own note (a direct reply) OR a third
        // party's — e.g. someone replying to another reply in a thread the account
        // started. Attribute it to whoever actually wrote it, and observe that
        // author so their name/avatar fill in like the replier's do.
        val parentContent = parentNote?.event?.content
        val parentAuthor = parentNote?.author
        val parentIsFromMe = parentAuthor?.pubkeyHex == account.signer.pubKey
        val observedParentAuthor = parentAuthor?.takeUnless { parentIsFromMe }

        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            notificationId = event.id,
            users = listOf(author) + citedUsers + listOfNotNull(observedParentAuthor),
            notes = listOf(replyNote),
            isComplete = {
                author.metadataOrNull()?.bestName() != null &&
                    citedUsers.all { it.metadataOrNull()?.bestName() != null } &&
                    (observedParentAuthor == null || observedParentAuthor.metadataOrNull()?.bestName() != null)
            },
        ) {
            val user = author.toBestDisplayName()
            val replyExcerpt = NotificationContent.resolveMentions(event.content).text
            val parentExcerpt = parentContent?.let { NotificationContent.resolveMentions(it, 140).text }?.takeIf { it.isNotBlank() }
            val parent =
                parentExcerpt?.let {
                    ParentMessage(
                        senderName =
                            if (parentIsFromMe || parentAuthor == null) {
                                loadStringRes(Res.string.app_notification_me)
                            } else {
                                parentAuthor.toBestDisplayName()
                            },
                        body = it,
                        pictureUrl = if (parentIsFromMe) account.userProfile().profilePicture() else parentAuthor?.profilePicture(),
                        isFromMe = parentIsFromMe,
                    )
                }
            nm.postConversation(
                category = NotificationCategory.REPLY,
                id = event.id,
                senderName = user,
                pictureUrl = author.profilePicture(),
                messageBody = replyExcerpt,
                time = event.createdAt,
                uri = uri,
                applicationContext = context,
                accountPictureUrl = account.userProfile().profilePicture(),
                parent = parent,
                publicInlineReply = InlineReplyTarget(accountNpub = accountNpub, targetEventId = event.id),
                addMarkRead = false,
                groupKey = replyGroupKeyFor(threadRootId),
                summaryId = replySummaryIdFor(threadRootId),
            )
        }
    }
}
