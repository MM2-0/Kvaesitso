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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChatEditEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.supersedes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUsers
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.taggedQuoteIds
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import com.vitorpamplona.quartz.nip40Expiration.isExpirationBefore
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Memory-reclaim policy over the [LocalCache] stores: trims the soft caches,
 * prunes hidden/old/expired/superseded events, and owns the shared
 * [unlinkAndRemove] removal primitive that [LocalCache.deleteNote] also relies on.
 *
 * Pure policy — it holds no state of its own beyond the cache reference, so every
 * function can be exercised against a populated cache in tests. Driven by
 * `MemoryTrimmingService`.
 */
class CachePruner(
    private val cache: EventCache,
) {
    fun cleanMemory() {
        Log.d("LargeCache") { "Notes cleanup started. Current size: ${cache.notes.size()}" }
        cache.notes.cleanUp()
        Log.d("LargeCache") { "Notes cleanup completed. Remaining size: ${cache.notes.size()}" }

        Log.d("LargeCache") { "Addressables cleanup started. Current size: ${cache.addressables.size()}" }
        cache.addressables.cleanUp()
        Log.d("LargeCache") { "Addressables cleanup completed. Remaining size: ${cache.addressables.size()}" }

        Log.d("LargeCache") { "Users cleanup started. Current size: ${cache.users.size()}" }
        cache.users.cleanUp()
        Log.d("LargeCache") { "Users cleanup completed. Remaining size: ${cache.users.size()}" }
    }

    fun cleanObservers() {
        cache.notes.forEach { _, it -> it.clearFlow() }
        cache.addressables.forEach { _, it -> it.clearFlow() }
    }

    private fun pruneHiddenMessagesChannel(
        channel: Channel,
        account: IAccount,
    ) {
        val toBeRemoved = channel.pruneHiddenMessages(account)

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            unlinkAndRemove(it)

            childrenToBeRemoved.addAll(it.clearChildLinks())
        }

        unlinkAndRemove(childrenToBeRemoved)

        if (toBeRemoved.size > 100 || channel.notes.size() > 100) {
            println(
                "PRUNE: ${toBeRemoved.size} hidden messages removed from ${channel.toBestDisplayName()}. ${channel.notes.size()} kept",
            )
        }
    }

    fun pruneHiddenMessages(account: IAccount) {
        cache.ephemeralChannels.forEach { _, channel ->
            pruneHiddenMessagesChannel(channel, account)
        }

        cache.geohashChannels.forEach { _, channel ->
            pruneHiddenMessagesChannel(channel, account)
        }

        cache.liveChatChannels.forEach { _, channel ->
            pruneHiddenMessagesChannel(channel, account)
        }

        cache.publicChatChannels.forEach { _, channel ->
            pruneHiddenMessagesChannel(channel, account)
        }

        cache.relayGroupChannels.forEach { _, channel ->
            pruneHiddenMessagesChannel(channel, account)
        }

        cache.concordChannels.forEach { _, channel ->
            pruneHiddenMessagesChannel(channel, account)
        }
    }

    // 2× the 10-min `PRESENCE_FRESHNESS_WINDOW_SECONDS` used by
    // `NestsFeedFilter` so a presence still inside any feed's window
    // can never be pruned.
    private val presencePruneAgeSeconds = 20L * 60L

    private fun pruneOldMessagesChannel(channel: Channel) {
        val toBeRemoved = channel.pruneOldMessages()

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            unlinkAndRemove(it)

            childrenToBeRemoved.addAll(it.clearChildLinks())
        }

        unlinkAndRemove(childrenToBeRemoved)

        // Audio-room presence is keyed separately from `notes` and
        // never gets reaped by the top-N rule. Drop entries older
        // than 2× the 10-min freshness window so the index doesn't
        // grow unbounded with every author who ever heartbeat here.
        if (channel is LiveActivitiesChannel) {
            channel.pruneStalePresence(TimeUtils.now() - presencePruneAgeSeconds)
        }

        if (toBeRemoved.size > 100 || channel.notes.size() > 100) {
            println(
                "PRUNE: ${toBeRemoved.size} old messages removed from ${channel.toBestDisplayName()}. ${channel.notes.size()} kept",
            )
        }
    }

    fun pruneOldMessages() {
        cache.ephemeralChannels.forEach { _, channel ->
            pruneOldMessagesChannel(channel)
        }

        cache.geohashChannels.forEach { _, channel ->
            pruneOldMessagesChannel(channel)
        }

        cache.liveChatChannels.forEach { _, channel ->
            pruneOldMessagesChannel(channel)
        }

        cache.publicChatChannels.forEach { _, channel ->
            pruneOldMessagesChannel(channel)
        }

        cache.relayGroupChannels.forEach { _, channel ->
            pruneOldMessagesChannel(channel)
        }

        // Concord channels were left out, so a busy community's messages were never trimmed.
        cache.concordChannels.forEach { _, channel ->
            pruneOldMessagesChannel(channel)
        }

        cache.chatroomList.forEach { userHex, room ->
            // History floors are pinned per scope on first advance; null means that window never paged
            // history, so its cursors hold no position to misalign and nothing needs rewinding. Only the
            // bands strictly BELOW a floor are this window's responsibility — a pruned message newer than
            // the floor is the always-on live tail's concern, and rewinding history for it would needlessly
            // re-page (and, for a busy room straddling the floor, mis-set the boundary). Hence the per-floor
            // filter when accumulating below.
            val giftWrapFloor = room.giftWrapHistory.floor
            val accountNip04Floor = room.nip04History.floor

            room.rooms.map { key, chatroom ->
                val toBeRemoved = chatroom.pruneMessagesToTheLatestOnly()

                val childrenToBeRemoved = mutableListOf<Note>()

                // Newest pruned `created_at` per relay, in each window's cursor space, capped at < floor.
                // Gift wraps page by the OUTER wrap time (from the rumor-host index); NIP-04 by the event's
                // own time, and a kind:4 belongs to BOTH the account (rooms-list) and per-conversation cursor.
                val giftWrapPruned = HashMap<NormalizedRelayUrl, Long>()
                val accountNip04Pruned = HashMap<NormalizedRelayUrl, Long>()
                val roomNip04Pruned = HashMap<NormalizedRelayUrl, Long>()
                // chatroom.nip04History is lazy — only touch (allocate) it when this room actually drops a
                // kind:4 message, so rooms that never paged conversation history pay nothing.
                val roomNip04Floor = if (toBeRemoved.any { it.event is EncryptedDmEvent }) chatroom.nip04History.floor else null

                toBeRemoved.forEach { note ->
                    when (val ev = note.event) {
                        is BaseDMGroupEvent ->
                            if (giftWrapFloor != null) {
                                val outerUntil = note.rumorHost?.createdAt ?: ev.createdAt
                                if (outerUntil < giftWrapFloor) note.relays.forEach { giftWrapPruned.mergeMax(it, outerUntil) }
                            }
                        is EncryptedDmEvent -> {
                            val until = ev.createdAt
                            if (accountNip04Floor != null && until < accountNip04Floor) note.relays.forEach { accountNip04Pruned.mergeMax(it, until) }
                            if (roomNip04Floor != null && until < roomNip04Floor) note.relays.forEach { roomNip04Pruned.mergeMax(it, until) }
                        }
                    }

                    childrenToBeRemoved.addAll(removeIfWrap(note))
                    unlinkAndRemove(note)

                    childrenToBeRemoved.addAll(note.clearChildLinks())
                }

                unlinkAndRemove(childrenToBeRemoved)

                // Realign the windows so a relay that already paged past (or `done` below) the dropped band
                // re-requests it on the next demand-advance instead of skipping the hole.
                if (giftWrapPruned.isNotEmpty()) {
                    room.giftWrapHistory.rewindTo(giftWrapPruned)
                    Log.d("DMPagination") { "[giftwrap] window rewound after prune: ${giftWrapPruned.size} relay(s), newest pruned wrap @${giftWrapPruned.values.max()}" }
                }
                if (accountNip04Pruned.isNotEmpty()) {
                    room.nip04History.rewindTo(accountNip04Pruned)
                    Log.d("DMPagination") { "[rooms.nip04] window rewound after prune: ${accountNip04Pruned.size} relay(s), newest pruned @${accountNip04Pruned.values.max()}" }
                }
                if (roomNip04Pruned.isNotEmpty()) {
                    chatroom.nip04History.rewindTo(roomNip04Pruned)
                    Log.d("DMPagination") { "[convo.nip04] window rewound after prune of ${key.users.joinToString()}: ${roomNip04Pruned.size} relay(s), newest pruned @${roomNip04Pruned.values.max()}" }
                }

                if (toBeRemoved.size > 1) {
                    println(
                        "PRUNE: ${toBeRemoved.size} private messages from $userHex to ${key.users.joinToString()} removed. ${chatroom.messages.size} kept",
                    )
                }
            }
        }
    }

    /**
     * Drops the gift wrap and seal that delivered each of [rumors], keeping the rumors. A wrap still in
     * the cache is skipped on arrival as already seen, so without this a rumor collected after its room
     * let go of it could never come back from a re-fetch of the same wrap.
     */
    fun forgetGiftWraps(rumors: Collection<Note>) {
        val children = mutableListOf<Note>()
        rumors.forEach { children.addAll(removeIfWrap(it)) }
        unlinkAndRemove(children)
    }

    private fun removeIfWrap(note: Note): List<Note> {
        val host = note.rumorHost ?: return emptyList()

        val children = mutableListOf<Note>()
        cache.getNoteIfExists(host.id)?.let { hostNote ->
            (hostNote.event as? GiftWrapEvent)?.innerEventId?.let { sealId ->
                cache.getNoteIfExists(sealId)?.let { sealNote ->
                    unlinkAndRemove(sealNote)
                    children.addAll(sealNote.clearChildLinks())
                }
            }
            unlinkAndRemove(hostNote)
            children.addAll(hostNote.clearChildLinks())
        }
        note.rumorHost = null
        return children
    }

    fun prunePastVersionsOfReplaceables() {
        val toBeRemoved =
            cache.notes.filter { _, note ->
                val noteEvent = note.event
                if (noteEvent is AddressableEvent) {
                    // The same NIP-01 rule that picked the address's version, tiebreak included,
                    // so a same-second loser is pruned too while the kept version never is.
                    cache.addressables
                        .get(noteEvent.address())
                        ?.event
                        ?.supersedes(noteEvent) == true
                } else {
                    false
                }
            }

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            val newerVersion = (it.event as? AddressableEvent)?.address()?.let { tag -> cache.addressables.get(tag) }
            if (newerVersion != null) {
                it.moveAllReferencesTo(newerVersion)
            }

            unlinkAndRemove(it)
            childrenToBeRemoved.addAll(it.clearChildLinks())
        }

        unlinkAndRemove(childrenToBeRemoved)

        if (toBeRemoved.size > 1) {
            println("PRUNE: ${toBeRemoved.size} old version of addressables removed.")
        }
    }

    fun pruneRepliesAndReactions(accounts: Set<HexKey>) {
        val toBeRemoved =
            cache.notes.filter { _, note ->
                (
                    (note.event is TextNoteEvent && !note.isNewThread()) ||
                        note.event is ReactionEvent ||
                        note.event is ZapReceiptEvent ||
                        note.event is ZapRequestEvent ||
                        note.event is ReportEvent ||
                        note.event is GenericRepostEvent
                ) &&
                    note.replyTo?.any { it.flowSet?.isInUse() == true } != true &&
                    note.flowSet?.isInUse() != true &&
                    // don't delete if observing.
                    note.author?.pubkeyHex !in
                    accounts &&
                    // don't delete if it is the logged in account
                    note.event?.isTaggedUsers(accounts) !=
                    true // don't delete if it's a notification to the logged in user
            }

        val childrenToBeRemoved = mutableListOf<Note>()

        toBeRemoved.forEach {
            unlinkAndRemove(it)
            childrenToBeRemoved.addAll(it.clearChildLinks())
        }

        unlinkAndRemove(childrenToBeRemoved)

        if (toBeRemoved.size > 1) {
            println("PRUNE: ${toBeRemoved.size} thread replies removed.")
        }
    }

    /**
     * Unlinks [note] from everything in the cache that references it, then drops it
     * from the notes map and notifies observers. This is the shared "unlink from
     * above" half of removal, used by both the prune callers and [LocalCache.deleteNote].
     *
     * It detaches the note from:
     *  - its parent notes (their replies/reactions/zaps/boosts/reports/labels maps);
     *    because event-level reports and torrent comments both carry the target in
     *    `replyTo`, [Note.removeNote] cleans those up here too;
     *  - its channels/gatherers (`inGatherers` is authoritative — `Channel.addNote`
     *    always registers the gatherer — and `getAnyChannel` is a belt-and-suspenders
     *    resolve so a note can never linger in a channel after leaving the cache);
     *  - the per-target indexes `replyTo` does NOT reach: user-level reports and
     *    reported addresses, contact cards, statuses, and poll responses.
     *
     * It deliberately does NOT touch the note's own children: prune callers collect
     * them via [Note.clearChildLinks] and remove the subtree, while [LocalCache.deleteNote]
     * keeps them and severs only their back-reference. Every per-target removal is
     * idempotent, so the overlap between `replyTo` and the explicit indexes (e.g. an
     * event-level report reachable both ways) is harmless. Addressable notes are
     * dropped from the addressables map by the caller; this only removes from notes.
     */
    fun unlinkAndRemove(note: Note) {
        note.replyTo?.forEach { masterNote ->
            masterNote.removeNote(note)
        }

        note.inGatherers?.forEach { it.removeNote(note) }

        cache.getAnyChannel(note)?.removeNote(note)

        val noteEvent = note.event

        // Quote-repost boosts are tracked outside `replyTo` (see addQuoteBoosts), so
        // detach this note from every quoted note's boosts here.
        noteEvent?.taggedQuoteIds()?.forEach { quotedId ->
            cache.getNoteIfExists(quotedId)?.removeBoost(note)
        }

        // Edits (1010/3302/40003) are anchored on their target's Note.edits and carry no `replyTo`
        // back-link, so the unlink above can't reach them — resolve the target by the edit's `e` tag
        // and drop it there, or a deleted edit would keep overlaying its message.
        editedTargetIdOf(noteEvent)?.let { cache.getNoteIfExists(it)?.removeEdit(note) }

        // OTS attestations (kind 1040) are likewise anchored on their target's Note.timestamps with
        // no `replyTo` back-link — resolve the target by the `e` tag and drop the proof there.
        if (noteEvent is OtsEvent) {
            noteEvent.digestEventId()?.let { cache.getNoteIfExists(it)?.removeTimestamp(note) }
        }

        // NIP-32 hashtag labels are anchored on their targets' Note.labels (EventCache attaches them
        // through labeledEvents()), and a #t-only label has no `replyTo` back-link: drop it there, or
        // a deleted label would keep showing on the item.
        if (noteEvent is LabelEvent) {
            noteEvent.labeledEvents().forEach { cache.getNoteIfExists(it)?.removeLabel(note) }
        }

        if (noteEvent is ReportEvent) {
            noteEvent.reportedAuthor().forEach {
                cache.getUserIfExists(it.pubKey)?.reportsOrNull()?.let { reports ->
                    reports.removeReport(note)
                    reports.removeReportNamingUser(note)
                }
            }

            noteEvent.reportedPost().forEach {
                cache.getNoteIfExists(it.eventId)?.removeReport(note)
            }

            noteEvent.reportedAddresses().forEach {
                cache.getAddressableNoteIfExists(it.address)?.removeReport(note)
            }
        }

        if (note is AddressableNote && noteEvent is UserAssertionEvent) {
            cache.getUserIfExists(noteEvent.aboutUser())?.cardsOrNull()?.removeCard(note)
        }

        if (note is AddressableNote && noteEvent is UserStatusEvent) {
            note.author?.statusStateOrNull()?.removeStatus(note)
        }

        if (noteEvent is PollResponseEvent) {
            noteEvent.poll()?.eventId?.let {
                cache.getNoteIfExists(it)?.pollStateOrNull()?.removeResponse(note)
            }
        }

        note.clearFlow()

        cache.notes.remove(note.idHex)

        cache.refreshDeletedNoteObservers(note)
    }

    /** The id of the message/post an edit event targets (its `e` tag), across all three edit kinds. */
    private fun editedTargetIdOf(event: Event?): HexKey? =
        when (event) {
            is TextNoteModificationEvent -> event.editedNote()?.eventId
            is ConcordChatEditEvent -> event.editedMessageId()
            is StreamMessageEditEvent -> event.editedMessage()
            else -> null
        }

    fun unlinkAndRemove(nextToBeRemoved: List<Note>) {
        nextToBeRemoved.forEach { note -> unlinkAndRemove(note) }
    }

    fun pruneExpiredEvents() {
        val now = TimeUtils.now()
        val versionsToBeRemoved = cache.notes.filter { _, it -> it.event?.isExpirationBefore(now) == true }
        val addressesToBeRemoved = cache.addressables.filter { _, it -> it.event?.isExpirationBefore(now) == true }

        val childrenToBeRemoved = mutableListOf<Note>()

        versionsToBeRemoved.forEach {
            unlinkAndRemove(it)
            childrenToBeRemoved.addAll(it.clearChildLinks())
        }

        addressesToBeRemoved.forEach {
            unlinkAndRemove(it)
            // unlinkAndRemove only clears `notes`; every address lookup reads `addressables`.
            cache.addressables.remove(it.address)
            childrenToBeRemoved.addAll(it.clearChildLinks())
        }

        unlinkAndRemove(childrenToBeRemoved)

        if (versionsToBeRemoved.size > 1 || addressesToBeRemoved.size > 1) {
            println("PRUNE: ${versionsToBeRemoved.size} events and ${addressesToBeRemoved.size} expired.")
        }
    }

    /**
     * Drops every event authored by a muted or blocked user.
     *
     * Takes the mute list by value rather than the account that holds it: the pubkeys are all
     * this needs, and [IAccount] exposes hidden users only as hash codes, which would match
     * authors the reader never muted.
     */
    fun pruneHiddenEvents(hidden: LiveHiddenUsers) {
        val childrenToBeRemoved = mutableListOf<Note>()

        val hiddenUsers = hidden.hiddenUsers
        if (hiddenUsers.isEmpty()) return

        // One pass per store, each author looked up in the set. A pass per muted user made every
        // trim cost (mutes x cache size), and mute lists run into the hundreds.
        val toBeRemoved =
            cache.notes.filter { _, it -> it.event?.pubKey?.let { author -> author in hiddenUsers } == true } +
                cache.addressables.filter { _, it -> it.event?.pubKey?.let { author -> author in hiddenUsers } == true }

        toBeRemoved.forEach {
            unlinkAndRemove(it)
            // unlinkAndRemove only clears `notes`; every address lookup reads `addressables`.
            if (it is AddressableNote) cache.addressables.remove(it.address)
            childrenToBeRemoved.addAll(it.clearChildLinks())
        }

        unlinkAndRemove(childrenToBeRemoved)

        println("PRUNE: ${toBeRemoved.size} messages removed because they were Hidden")
    }
}

/**
 * `Map.merge(key, value, ::maxOf)` spelled for every target — the JVM's own merge is not in the
 * common stdlib. Keeps the highest value seen for [relay].
 */
private fun MutableMap<NormalizedRelayUrl, Long>.mergeMax(
    relay: NormalizedRelayUrl,
    value: Long,
) {
    val existing = this[relay]
    this[relay] = if (existing == null) value else maxOf(existing, value)
}
