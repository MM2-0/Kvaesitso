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
package com.vitorpamplona.amethyst.commons.moderation.notifications

import com.vitorpamplona.amethyst.commons.model.provenZapper
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.utils.toLongValue

/**
 * What a notification *is*, for display: one variant per row shape. Built from a raw
 * event by [classify] after [NotificationKinds.tagsAnEventForUser] has decided the
 * event is for the user. Shared by the Desktop notifications screen and
 * `amy notifications`; extracted from Desktop's `NotificationsScreen`.
 */
sealed class NotificationItem(
    open val event: Event,
    open val timestamp: Long,
) {
    /** Stable lowercase discriminator (`mention`, `reply`, …) — amy's `type` field. */
    abstract val type: String

    data class Mention(
        override val event: Event,
        override val timestamp: Long,
    ) : NotificationItem(event, timestamp) {
        override val type = "mention"
    }

    data class Reply(
        override val event: Event,
        override val timestamp: Long,
    ) : NotificationItem(event, timestamp) {
        override val type = "reply"
    }

    data class Reaction(
        override val event: Event,
        override val timestamp: Long,
        val content: String,
    ) : NotificationItem(event, timestamp) {
        override val type = "reaction"
    }

    data class Repost(
        override val event: Event,
        override val timestamp: Long,
    ) : NotificationItem(event, timestamp) {
        override val type = "repost"
    }

    data class Zap(
        override val event: Event,
        override val timestamp: Long,
        val amount: Long?,
    ) : NotificationItem(event, timestamp) {
        override val type = "zap"

        /** The proven zapper of a NIP-57 receipt (see [provenZapper]), verified once. */
        val sender: HexKey? by lazy { (event as? ZapReceiptEvent)?.provenZapper() }
    }

    /**
     * Encrypted direct message notification. Carries no body until a decryption
     * pipeline (NIP-04 signer / NIP-17 gift-wrap unwrap) runs — never populate it
     * with raw ciphertext.
     */
    data class Dm(
        override val event: Event,
        override val timestamp: Long,
    ) : NotificationItem(event, timestamp) {
        override val type = "dm"
    }

    companion object {
        /**
         * Route a raw Nostr event to its [NotificationItem] variant based on kind.
         * Returns null for kinds that have no notification row — callers drop those.
         */
        fun classify(event: Event): NotificationItem? =
            when (event) {
                is ReactionEvent -> Reaction(event, event.createdAt, event.content)
                is RepostEvent, is GenericRepostEvent -> Repost(event, event.createdAt)
                is ZapReceiptEvent -> Zap(event, event.createdAt, event.amount?.toLongValue())
                // Nutzaps, BOLT12 and on-chain zaps: zaps whose amount needs the payment proof.
                is NutzapEvent, is Bolt12ZapEvent, is OnchainZapEvent -> Zap(event, event.createdAt, null)
                // A kind:1 that answers another note is a reply; one that only cites
                // (mention-marked `e`, `q`, inline nostr: link) is a mention. "Answers" is
                // what the app links as `Note.replyTo`: tagsWithoutCitations.
                is TextNoteEvent ->
                    if (event.tagsWithoutCitations().isNotEmpty()) {
                        Reply(event, event.createdAt)
                    } else {
                        Mention(event, event.createdAt)
                    }
                // NIP-22 threaded comments are reply-shaped.
                is CommentEvent -> Reply(event, event.createdAt)
                // NIP-28 channel messages read like public mentions.
                is ChannelMessageEvent -> Mention(event, event.createdAt)
                // DMs (NIP-04 legacy + NIP-17 gift-wrap + rumor + file-header).
                is EncryptedDmEvent,
                is ChatMessageEvent,
                is GiftWrapEvent,
                is ChatMessageEncryptedFileHeaderEvent,
                -> Dm(event, event.createdAt)
                else -> null
            }
    }
}

/**
 * The pubkey to display and fetch metadata for. For NIP-57 zap receipts the outer
 * `event.pubKey` is the LNURL provider — the actual zapper signed the nested zap
 * request, which only counts when it is proven ([NotificationItem.Zap.sender]); an
 * unproven or anonymous zap shows the provider. For gift-wrapped DMs the outer pubkey is
 * an ephemeral key; until decryption lands it falls back to `event.pubKey` for a stable
 * avatar.
 */
val NotificationItem.effectiveAuthorPubKey: String
    get() =
        when (this) {
            is NotificationItem.Zap -> sender ?: event.pubKey
            else -> event.pubKey
        }
