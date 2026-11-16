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
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_new_message
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.service.notifications.NotificationCategory
import com.vitorpamplona.amethyst.service.notifications.NotificationEnricher
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.Conversation
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.postConversation
import com.vitorpamplona.amethyst.service.notifications.notificationManager
import com.vitorpamplona.amethyst.service.notifications.privateMessagesLocked
import com.vitorpamplona.quartz.buzz.workspace.buzzParticipants
import com.vitorpamplona.quartz.buzz.workspace.isBuzzDm
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Buzz DM notifications — a message (NIP-29 relay-group `StreamMessageV2Event`
 * kind 40002 or `ChatEvent` kind 9) posted into a Buzz `t=dm` channel whose 39000
 * participant list includes me. These carry no `p` tag, so — like Marmot/Concord
 * — being a participant of the DM channel is the relevance signal, resolved from
 * the channel's already-loaded metadata.
 *
 * Rendered with MessagingStyle on the Private Messages channel; the channel name
 * is the conversation title, the message shows "sender: text", and tapping opens
 * the relay-group chatroom (via the channel's kind-39000 naddr). The sender's
 * name + avatar are enriched observably.
 */
object BuzzDmNotification {
    /**
     * The Buzz DM channel [note] belongs to when it's a DM addressed to [me] —
     * else null. Shared with the dispatcher predicate so push and the in-app feed
     * agree on "this Buzz DM is for me". Returns null (no notification) when the
     * channel's 39000 metadata isn't loaded yet, mirroring the feed.
     */
    fun buzzDmChannelForMe(
        note: Note,
        me: HexKey,
    ): RelayGroupChannel? {
        val channel = LocalCache.getRelayGroupChannelForContent(note) ?: return null
        val metadata = channel.event ?: return null
        return if (metadata.isBuzzDm() && metadata.buzzParticipants().contains(me)) channel else null
    }

    suspend fun notify(
        context: Context,
        account: Account,
        event: Event,
    ) {
        // Honor the "show messages in notifications" toggle, like the in-app feed.
        if (!account.settings.showMessagesInNotifications.value) return

        val note = LocalCache.getNoteIfExists(event.id) ?: return
        val channel = buzzDmChannelForMe(note, account.signer.pubKey) ?: return

        val sender = LocalCache.getOrCreateUser(event.pubKey)
        val locked = privateMessagesLocked()
        val body = event.content.takeIf { it.isNotBlank() && !locked } ?: loadStringRes(Res.string.app_notification_new_message)

        val accountNpub = NotificationRoutes.accountNpub(account)
        // The channel's kind-39000 naddr routes straight to the chatroom via the
        // existing naddr → Route.RelayGroup path (no message load needed on tap).
        val channelNAddr = channel.toNAddr()
        val uri =
            channelNAddr?.let { NotificationRoutes.relayGroupUri(it, accountNpub) }
                ?: NotificationRoutes.noteUri(note, accountNpub)

        // Only the naddr identifies the room durably; without it there is no conversation to
        // pin a shortcut to. The message-content gate is the early return at the top.
        val conversation =
            channelNAddr?.let {
                Conversation(
                    id = NotificationRoutes.relayGroupShortcutId(it, accountNpub),
                    label = channel.toBestDisplayName(),
                    iconUrl = channel.profilePicture(),
                    isGroup = true,
                )
            }

        val nm = context.notificationManager()

        NotificationEnricher.enrichAndPost(
            context = context,
            account = account,
            notificationId = event.id,
            users = listOf(sender),
            notes = listOf(note),
            isComplete = { sender.metadataOrNull()?.bestName() != null },
        ) {
            nm.postConversation(
                category = NotificationCategory.DIRECT_MESSAGE,
                id = event.id,
                senderName = channel.toBestDisplayName(),
                pictureUrl = sender.profilePicture() ?: channel.profilePicture(),
                messageBody = "${sender.toBestDisplayName()}: $body",
                time = event.createdAt,
                uri = uri,
                applicationContext = context,
                accountPictureUrl = account.userProfile().profilePicture(),
                replyAction = null,
                conversation = conversation,
            )
        }
    }
}
