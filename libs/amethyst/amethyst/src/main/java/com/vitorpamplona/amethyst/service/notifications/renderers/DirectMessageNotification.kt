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
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.privateChats.isSubjectOnlyChatMessage
import com.vitorpamplona.amethyst.commons.notifications.NotificationContent
import com.vitorpamplona.amethyst.commons.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_new_message
import com.vitorpamplona.amethyst.commons.resources.chat_notification_renamed_conversation_to
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.service.notifications.NotificationCategory
import com.vitorpamplona.amethyst.service.notifications.NotificationEnricher
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.Conversation
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.ReplyAction
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.postConversation
import com.vitorpamplona.amethyst.service.notifications.notificationManager
import com.vitorpamplona.amethyst.service.notifications.privateMessagesLocked
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent

/**
 * Direct-message notifications — NIP-17 chat (kind 14), NIP-17 encrypted files
 * (kind 15), and legacy NIP-04 DMs (kind 4). Rendered with MessagingStyle so the
 * shade shows the sender's avatar + name and the message threads under the
 * Conversations section. NIP-17 messages carry an inline Reply action; NIP-04 is
 * read-only (matching the historical behavior).
 *
 * The message body is resolved once up front; only the sender's name + avatar
 * are enriched observably, so a cold-push notification fills in the sender's
 * metadata as the kind:0 lands.
 */
object DirectMessageNotification {
    suspend fun notify(
        context: Context,
        account: Account,
        event: ChatMessageEvent,
    ) = notifyRoom(context, account, event.id, event.createdAt, event.chatroomKey(account.signer.pubKey), decrypt = false)

    suspend fun notify(
        context: Context,
        account: Account,
        event: ChatMessageEncryptedFileHeaderEvent,
    ) = notifyRoom(context, account, event.id, event.createdAt, event.chatroomKey(account.signer.pubKey), decrypt = false)

    suspend fun notify(
        context: Context,
        account: Account,
        event: EncryptedDmEvent,
    ) {
        if (account.signer.pubKey != event.verifiedRecipientPubKey()) return
        notifyRoom(context, account, event.id, event.createdAt, event.chatroomKey(account.signer.pubKey), decrypt = true)
    }

    private suspend fun notifyRoom(
        context: Context,
        account: Account,
        eventId: String,
        createdAt: Long,
        chatRoom: ChatroomKey,
        decrypt: Boolean,
    ) {
        val chatNote = LocalCache.getNoteIfExists(eventId) ?: return

        val author = chatNote.author ?: return

        // A process woken by this push may not have read the trust network yet.
        account.trustNetwork.awaitLoaded()
        if (!account.isKnownChatroom(chatRoom, author.pubkeyHex)) return
        // Decrypt (NIP-04) or read (NIP-17) the body once — never re-decrypt on
        // each enrichment tick, which could hammer a remote signer.
        val locked = privateMessagesLocked()
        val body =
            if (locked) {
                loadStringRes(Res.string.app_notification_new_message)
            } else if (decrypt) {
                NotificationContent.decryptContent(chatNote, account.signer) ?: return
            } else {
                val event = chatNote.event ?: return
                // A rename with no text would post an empty notification; say what it did instead.
                if (event.isSubjectOnlyChatMessage()) {
                    loadStringRes(Res.string.chat_notification_renamed_conversation_to, event.subject() ?: "")
                } else {
                    event.content
                }
            }

        val accountNpub = NotificationRoutes.accountNpub(account)
        // The room, not the message: a rumor's nevent cites the gift wrap that delivered it,
        // which is gone from LocalCache as soon as the process dies and is unfetchable from
        // any relay. See [NotificationRoutes.chatroomUri].
        val uri = NotificationRoutes.chatroomUri(chatRoom, accountNpub)

        // Names the chat for the shade's Conversations section. Withheld when the account has
        // turned message content off, because publishing it puts the counterparty's name and
        // avatar in the launcher — see [ConversationShortcuts].
        val conversation =
            if (account.settings.showMessagesInNotifications.value && !locked) {
                Conversation(
                    id = NotificationRoutes.chatroomShortcutId(chatRoom, accountNpub),
                    label = roomLabel(chatRoom, author),
                )
            } else {
                null
            }
        val replyAction =
            if (decrypt || locked) {
                null // NIP-04 is read-only in the tray
            } else {
                ReplyAction.Dm(accountNpub = accountNpub, chatroomMembers = chatRoom.users.joinToString(","))
            }

        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            notificationId = eventId,
            users = listOf(author),
            notes = listOf(chatNote),
            isComplete = { author.metadataOrNull()?.bestName() != null },
        ) {
            nm.postConversation(
                category = NotificationCategory.DIRECT_MESSAGE,
                id = eventId,
                senderName = author.toBestDisplayName(),
                pictureUrl = author.profilePicture(),
                messageBody = body,
                time = createdAt,
                uri = uri,
                applicationContext = context,
                accountPictureUrl = account.userProfile().profilePicture(),
                replyAction = replyAction,
                conversation = conversation,
            )
        }
    }

    /**
     * What to call the chat. A one-to-one room is the other person, which is also the sender;
     * a group room is everyone in it, so the sender's name alone would mislabel it.
     */
    private fun roomLabel(
        chatRoom: ChatroomKey,
        author: User,
    ): String =
        if (chatRoom.users.size <= 1) {
            author.toBestDisplayName()
        } else {
            chatRoom.users.joinToString(", ") { LocalCache.getOrCreateUser(it).toBestDisplayName() }
        }
}
