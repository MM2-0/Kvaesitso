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
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_added_to_group
import com.vitorpamplona.amethyst.commons.resources.app_notification_new_message
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.service.notifications.NotificationCategory
import com.vitorpamplona.amethyst.service.notifications.NotificationEnricher
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.Conversation
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.ReplyAction
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.postConversation
import com.vitorpamplona.amethyst.service.notifications.notificationManager
import com.vitorpamplona.amethyst.service.notifications.privateMessagesLocked
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Marmot / MLS group notifications — a kind:445 group message (rendered as a
 * MessagingStyle chat with an encrypted inline reply) and a welcome invite
 * ("you've been added to …"). These arrive without a `p` tag, so the dispatcher
 * hands them here directly once the MLS layer has decrypted the inner event.
 */
object GroupMessageNotification {
    suspend fun notifyGroupMessage(
        context: Context,
        account: Account,
        innerEvent: ChatEvent,
        nostrGroupId: String,
    ) {
        if (!context.notificationManager().areNotificationsEnabled()) return
        if (MainActivity.isResumed) return
        if (innerEvent.createdAt < TimeUtils.fifteenMinutesAgo()) return
        if (innerEvent.pubKey == account.signer.pubKey) return

        val chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        val groupName = chatroom.displayName.value?.takeIf { it.isNotBlank() } ?: DEFAULT_GROUP_NAME
        val sender = LocalCache.getOrCreateUser(innerEvent.pubKey)
        val locked = privateMessagesLocked()
        val fallbackBody = innerEvent.content.takeIf { it.isNotBlank() && !locked } ?: loadStringRes(Res.string.app_notification_new_message)

        val accountNpub = NotificationRoutes.accountNpub(account)
        val uri = NotificationRoutes.marmotUri(nostrGroupId, accountNpub)
        // Withheld when the account has message content turned off — the shortcut it publishes
        // names the group in the launcher. See [ConversationShortcuts].
        val conversation =
            if (account.settings.showMessagesInNotifications.value && !locked) {
                // No iconUrl: a Marmot group's avatar is an encrypted Blossom blob, not a
                // URL an image loader can take.
                Conversation(
                    id = NotificationRoutes.marmotShortcutId(nostrGroupId, accountNpub),
                    label = groupName,
                    isGroup = true,
                )
            } else {
                null
            }
        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            notificationId = innerEvent.id,
            users = listOf(sender),
            notes = emptyList(),
            isComplete = { sender.metadataOrNull()?.bestName() != null },
        ) {
            nm.postConversation(
                category = NotificationCategory.DIRECT_MESSAGE,
                id = innerEvent.id,
                senderName = groupName,
                pictureUrl = sender.profilePicture(),
                messageBody = "${sender.toBestDisplayName()}: $fallbackBody",
                time = innerEvent.createdAt,
                uri = uri,
                applicationContext = context,
                accountPictureUrl = account.userProfile().profilePicture(),
                conversation = conversation,
                replyAction =
                    if (locked) {
                        null
                    } else {
                        ReplyAction.Marmot(
                            accountNpub = accountNpub,
                            nostrGroupId = nostrGroupId,
                            replyToInnerEventId = innerEvent.id,
                            replyToInnerAuthor = innerEvent.pubKey,
                        )
                    },
            )
        }
    }

    suspend fun notifyWelcome(
        context: Context,
        account: Account,
        event: WelcomeEvent,
    ) {
        if (!context.notificationManager().areNotificationsEnabled()) return
        if (MainActivity.isResumed) return
        if (event.createdAt < TimeUtils.fifteenMinutesAgo()) return
        if (event.pubKey == account.signer.pubKey) return

        val nostrGroupId = event.nostrGroupId() ?: return
        val chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        val groupName = chatroom.displayName.value?.takeIf { it.isNotBlank() } ?: DEFAULT_PRIVATE_GROUP
        val inviter = LocalCache.getOrCreateUser(event.pubKey)

        val accountNpub = NotificationRoutes.accountNpub(account)
        val uri = NotificationRoutes.marmotUri(nostrGroupId, accountNpub)
        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            notificationId = event.id,
            users = listOf(inviter),
            notes = emptyList(),
            isComplete = { inviter.metadataOrNull()?.bestName() != null },
        ) {
            nm.postConversation(
                category = NotificationCategory.DIRECT_MESSAGE,
                id = event.id,
                senderName = inviter.toBestDisplayName(),
                pictureUrl = inviter.profilePicture(),
                messageBody = loadStringRes(Res.string.app_notification_added_to_group, groupName),
                time = event.createdAt,
                uri = uri,
                applicationContext = context,
                accountPictureUrl = account.userProfile().profilePicture(),
                replyAction = null,
            )
        }
    }

    private const val DEFAULT_GROUP_NAME = "Private group"
    private const val DEFAULT_PRIVATE_GROUP = "a private group"
}
