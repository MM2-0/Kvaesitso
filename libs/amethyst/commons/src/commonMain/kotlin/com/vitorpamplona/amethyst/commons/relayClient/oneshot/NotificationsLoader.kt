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
package com.vitorpamplona.amethyst.commons.relayClient.oneshot

import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationItem
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationKinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent

/**
 * One page of public notifications for an account over a [OneShotRelayAccess]: the
 * Desktop inbox's subscription filter ([NotificationKinds.subscriptionFilter]), its "is
 * this really for me" rule ([NotificationKinds.tagsAnEventForUser]) and the shared
 * [NotificationItem.classify]. DMs are out: they need the signer to read.
 */
object NotificationsLoader {
    /** Every type [NotificationItem.classify] produces for public kinds. */
    val TYPES = setOf("mention", "reply", "reaction", "repost", "zap")

    /**
     * The newest [limit] notifications for [me] of [types], from the store plus
     * [relays] (normally [me]'s inbox, where others reach them, and outbox). Reactions and reposts count only when
     * their target is [me]'s own note; targets missing from the store are fetched by id
     * from [outboxRelays], where [me]'s notes live.
     */
    suspend fun load(
        access: OneShotRelayAccess,
        me: HexKey,
        relays: Set<NormalizedRelayUrl>,
        outboxRelays: Set<NormalizedRelayUrl>,
        limit: Int,
        since: Long? = null,
        until: Long? = null,
        types: Set<String> = TYPES,
        timeoutMs: Long = 8_000,
    ): List<NotificationItem> {
        val kinds = kindsFor(types)
        if (kinds.isEmpty()) return emptyList()
        // Over-fetch: classification drops what only p-tags us in passing.
        val base = NotificationKinds.subscriptionFilter(me, limit = (limit * 2).coerceAtMost(500), since = since)
        val filter = base.copy(kinds = kinds, until = until)

        // Relays still serve what a relay that missed the kind:5 kept; the store's deletions win.
        val fetched = access.fetch(relays, filter, timeoutMs).map { it.second }
        val events = access.withoutStoredDeletions((access.query(filter) + fetched).distinctBy { it.id })
        val authoredByMe = targetsAuthoredBy(access, me, events, outboxRelays, timeoutMs)
        return events
            .asSequence()
            .filter { NotificationKinds.tagsAnEventForUser(it, me) { id -> id in authoredByMe } }
            .mapNotNull(NotificationItem::classify)
            .filter { it.type in types }
            .sortedByDescending { it.timestamp }
            .take(limit)
            .toList()
    }

    /**
     * The public subscription kinds that can classify as one of [types], so a narrowed request
     * spends its limit on those kinds instead of on what will be dropped.
     */
    fun kindsFor(types: Set<String>): List<Int> = NotificationKinds.PUBLIC_SUBSCRIPTION_KINDS.filter { kind -> TYPES_BY_KIND[kind].orEmpty().any { it in types } }

    /** What [NotificationItem.classify] can make of each public kind. */
    private val TYPES_BY_KIND: Map<Int, Set<String>> =
        mapOf(
            TextNoteEvent.KIND to setOf("reply", "mention"),
            CommentEvent.KIND to setOf("reply"),
            ChannelMessageEvent.KIND to setOf("mention"),
            ReactionEvent.KIND to setOf("reaction"),
            RepostEvent.KIND to setOf("repost"),
            GenericRepostEvent.KIND to setOf("repost"),
            ZapReceiptEvent.KIND to setOf("zap"),
            NutzapEvent.KIND to setOf("zap"),
            OnchainZapEvent.KIND to setOf("zap"),
            Bolt12ZapEvent.KIND to setOf("zap"),
        )

    /**
     * The ids, among the targets of the reactions and reposts in [events], that [me]
     * authored. A reaction to someone else's note that merely p-tags us is not a
     * notification. Targets missing from the store are fetched in one request.
     */
    private suspend fun targetsAuthoredBy(
        access: OneShotRelayAccess,
        me: HexKey,
        events: List<Event>,
        outboxRelays: Set<NormalizedRelayUrl>,
        timeoutMs: Long,
    ): Set<HexKey> {
        val targets = events.mapNotNullTo(mutableSetOf()) { NotificationKinds.interactionTargetId(it) }
        if (targets.isEmpty()) return emptySet()

        val known = targets.chunked(MAX_FILTER_VALUES).flatMap { access.query(Filter(ids = it)) }.associateByTo(mutableMapOf()) { it.id }
        val missing = targets - known.keys
        if (missing.isNotEmpty() && outboxRelays.isNotEmpty()) {
            val filters = missing.chunked(MAX_FILTER_VALUES).map { Filter(ids = it, authors = listOf(me)) }
            access.fetch(outboxRelays.associateWith { filters }, timeoutMs).forEach { (_, event) -> known[event.id] = event }
        }
        return known.values.filter { it.pubKey == me }.mapTo(mutableSetOf()) { it.id }
    }
}
