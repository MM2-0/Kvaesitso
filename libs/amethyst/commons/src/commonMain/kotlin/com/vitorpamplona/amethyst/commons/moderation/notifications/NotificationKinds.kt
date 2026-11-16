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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
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

/**
 * Nostr event kinds that can generate a notification when they tag the
 * logged-in user via a `p` tag or otherwise reference them.
 *
 * The subscription filter is intentionally broader than what actually gets
 * shown in the notification inbox — server-side we ask relays for anything
 * that tags us, then client-side [tagsAnEventForUser] applies semantic
 * checks (e.g. a reaction only counts if it targets one of the user's own
 * notes, not just tags them).
 *
 * Shared between the Android and Desktop notification pipelines. Extracted
 * from Android's `NotificationFeedFilter.NOTIFICATION_KINDS`.
 */
object NotificationKinds {
    /**
     * Kinds subscribed to via `p`-tag filter for notification delivery.
     * Keep in sync with the Android `NOTIFICATION_KINDS` set (see
     * `amethyst/.../notifications/dal/NotificationFeedFilter.kt`).
     */
    val SUBSCRIPTION_KINDS: List<Int> =
        listOf(
            TextNoteEvent.KIND, // 1  — mentions + replies
            EncryptedDmEvent.KIND, // 4  — NIP-04 legacy DM
            RepostEvent.KIND, // 6
            ReactionEvent.KIND, // 7
            ChatMessageEvent.KIND, // 14 — NIP-17 DM rumor (rarely arrives raw; gift-wrap is more common)
            GenericRepostEvent.KIND, // 16
            ChannelMessageEvent.KIND, // 42 — NIP-28 public channel
            CommentEvent.KIND, // 1111 — NIP-22 threaded comment
            GiftWrapEvent.KIND, // 1059 — NIP-17 gift-wrapped DM
            NutzapEvent.KIND, // 9321 — NIP-61 Cashu nutzap
            ZapReceiptEvent.KIND, // 9735 — NIP-57 zap receipt
            OnchainZapEvent.KIND, // 8333 — onchain zap
            Bolt12ZapEvent.KIND, // 9736 — NIP-B1 BOLT12 zap
            // NIP-17 file-header messages (encrypted file DMs)
            ChatMessageEncryptedFileHeaderEvent.KIND,
        )

    /** The direct-message kinds in [SUBSCRIPTION_KINDS]: they need decryption to show anything. */
    val DM_KINDS: Set<Int> =
        setOf(EncryptedDmEvent.KIND, ChatMessageEvent.KIND, GiftWrapEvent.KIND, ChatMessageEncryptedFileHeaderEvent.KIND)

    /** [SUBSCRIPTION_KINDS] without the DMs — the public activity a reader can show as-is. */
    val PUBLIC_SUBSCRIPTION_KINDS: List<Int> = SUBSCRIPTION_KINDS.filter { it !in DM_KINDS }

    /**
     * The note a reaction or repost targets: the LAST `e` tag. NIP-25 reactions to a reply
     * carry the thread's root `e` first and the reacted-to note last; NIP-18 reposts have
     * one `e`. Null for other kinds. This is the id [tagsAnEventForUser]'s
     * `isTargetAuthoredByMe` is asked about, so a caller knows which notes to have loaded.
     */
    fun interactionTargetId(event: Event): HexKey? =
        when (event) {
            is ReactionEvent -> event.originalPost().lastOrNull()
            is RepostEvent -> event.boostedEventId()
            is GenericRepostEvent -> event.boostedEventId()
            else -> null
        }

    /**
     * Builds the standard notifications-for-user filter.
     * @param since Optional Unix seconds to gate `since` on the relay filter.
     * @param limit Optional server-side result cap.
     */
    fun subscriptionFilter(
        pubKeyHex: HexKey,
        limit: Int? = null,
        since: Long? = null,
    ): Filter =
        Filter(
            kinds = SUBSCRIPTION_KINDS,
            tags = mapOf("p" to listOf(pubKeyHex)),
            limit = limit,
            since = since,
        )

    /**
     * Client-side semantic check. An event that arrives via the subscription
     * filter is not automatically a notification — for example a reaction
     * with a stray `p` tag on our pubkey but no `e` tag on one of our notes
     * doesn't belong in the inbox.
     *
     * @param event The event to check.
     * @param myPubKeyHex Logged-in user's pubkey.
     * @param isTargetAuthoredByMe For reaction/repost events, whether the
     *     targeted note (via `e` tag) is authored by the current user.
     *     Callers pass a lambda that peeks into the local cache. Pass
     *     `false` if unknown — the check will still catch obvious matches.
     */
    fun tagsAnEventForUser(
        event: Event,
        myPubKeyHex: HexKey,
        isTargetAuthoredByMe: (targetNoteId: HexKey) -> Boolean = { false },
    ): Boolean {
        // Own events never notify — except zap receipts (ZapReceipt/Nutzap/Onchain)
        // which are signed by the LNURL provider or the payer, not by us.
        if (event.pubKey == myPubKeyHex &&
            event !is ZapReceiptEvent &&
            event !is NutzapEvent &&
            event !is OnchainZapEvent &&
            event !is Bolt12ZapEvent
        ) {
            return false
        }

        // Reactions and reposts require the target note to be authored by
        // the current user. A stray `p=me` tag on a stranger's reaction to
        // a stranger's note is NOT a notification. The target is the LAST
        // `e` (NIP-25/NIP-18): a reaction to a reply may carry the thread
        // root first, which is not the note that was reacted to.
        if (event is ReactionEvent || event is RepostEvent || event is GenericRepostEvent) {
            val target = interactionTargetId(event)
            return target != null && isTargetAuthoredByMe(target)
        }

        // Everything else must actually `p`-tag the current user. We can't
        // trust an implicit "the relay filter already narrowed to p=me"
        // assumption because this helper is also called from the cache-seed
        // pass on inbox open, which walks EVERY note the app has ever
        // received (from home feed, thread views, discover, etc.) — not
        // just events that came in via the notifications subscription.
        //
        // Android's equivalent (`NotificationFeedFilter.acceptableEvent` at
        // line 334) applies `isTaggedUser` outside `tagsAnEventByUser`; our
        // shared helper folds both checks into one so it's safe to call
        // from any ingest point.
        return isPTaggedUser(event, myPubKeyHex)
    }

    private fun isPTaggedUser(
        event: Event,
        myPubKeyHex: HexKey,
    ): Boolean = event.tags.any { it.size > 1 && it[0] == "p" && it[1] == myPubKeyHex }
}
