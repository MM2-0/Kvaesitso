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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.ChannelClassification
import com.vitorpamplona.amethyst.commons.model.buzz.MembershipNotice
import com.vitorpamplona.amethyst.commons.model.buzz.MembershipNotificationKinds
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.buzz.notifications.MemberRemovedNotificationEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

/*
 * The cache-side view of the Buzz membership stream: everything that reads kind-44100/44101 out of
 * LocalCache instead of asking a relay for its own copy.
 *
 * The relay subscription lives in BuzzMembershipEoseManager, mounted with the rest of the account
 * loaders. Every consumer of the stream — the Notifications feed's invite cards and Buzz DM discovery —
 * observes the cache through here, so there is exactly one `#p=me` REQ per workspace relay for all of
 * them.
 *
 * This is model code, not UI: the notifications DAL reads it from `acceptableEvent`, so it must not
 * live under `ui/`.
 */

/** Every membership verdict addressed to [me], as the cache observers want it. */
fun membershipNoticeFilter(me: HexKey) =
    Filter(
        kinds = MembershipNotificationKinds,
        tags = mapOf("p" to listOf(me)),
    )

/**
 * The workspace relay that vouched for this notice.
 *
 * A note records every relay it was seen on, and a Buzz membership notification is only meaningful on
 * the relay that issued it — the channel UUID it names is that relay's. So prefer a relay we joined as a
 * workspace; fall back to whatever else delivered it, which keeps a notice usable when the workspace set
 * hasn't been restored from disk yet.
 *
 * [workspaces] is passed in rather than read from a singleton because the joined set is per account
 * (`Account.buzzWorkspaces`): whose workspaces to prefer is a question only the caller can answer.
 */
private fun Note.membershipRelay(workspaces: Set<NormalizedRelayUrl>): NormalizedRelayUrl? {
    val seen = relays
    if (seen.isEmpty()) return null
    return seen.firstOrNull { it in workspaces } ?: seen.first()
}

/** Flattens a cached kind-44100/44101 into a [MembershipNotice], or null when it isn't usable. */
fun Note.toMembershipNotice(workspaces: Set<NormalizedRelayUrl>): MembershipNotice? {
    val relay = membershipRelay(workspaces) ?: return null
    return when (val noteEvent = event) {
        is MemberAddedNotificationEvent ->
            noteEvent.channel()?.let {
                MembershipNotice(noteEvent.id, it, relay, noteEvent.actor(), noteEvent.createdAt, removed = false)
            }

        is MemberRemovedNotificationEvent ->
            noteEvent.channel()?.let {
                MembershipNotice(noteEvent.id, it, relay, noteEvent.actor(), noteEvent.createdAt, removed = true)
            }

        else -> null
    }
}

fun List<Note>.toMembershipNotices(workspaces: Set<NormalizedRelayUrl>): List<MembershipNotice> = mapNotNull { it.toMembershipNotice(workspaces) }

private fun Note.isMembershipNoticeFor(me: HexKey): Boolean =
    when (val noteEvent = event) {
        is MemberAddedNotificationEvent -> noteEvent.target().equals(me, ignoreCase = true)
        is MemberRemovedNotificationEvent -> noteEvent.target().equals(me, ignoreCase = true)
        else -> false
    }

/**
 * Every membership verdict for [me] currently in the cache.
 *
 * Scanned off [LocalCache.notes] rather than read from an `observeNotes` snapshot, because that
 * snapshot cannot contain these kinds. `LocalCache.filter` only yields addressables plus notes whose
 * `kind.isRegular()` — and `isRegular()` is `> 0 && < 10_000`, so a Buzz 44100/44101 matches none of
 * its branches and the seed comes back empty every time. Live arrivals are fine (the observer's `new()`
 * applies no such gate), which is why a cold start looked correct: the observer registers before the
 * relay answers. What broke was any projection built *after* the events had landed — switching to
 * another account and back builds a fresh one, and `consumeRegularEvent` never re-notifies a duplicate,
 * so it would have stayed empty for the rest of the session.
 *
 * So the observer is kept purely as the change signal and this scan is the data. It is the same shape
 * `NotificationFeedFilter.feed()` uses over the same map, for the same reason.
 */
fun LocalCache.membershipNotices(
    me: HexKey,
    workspaces: Set<NormalizedRelayUrl>,
): List<MembershipNotice> =
    notes
        .filterIntoSet { _, note -> note.isMembershipNoticeFor(me) }
        .toList()
        .toMembershipNotices(workspaces)

/**
 * The Buzz type of every channel whose kind-39000 the cache already holds, keyed by group id.
 *
 * Built from the metadata events themselves rather than from the [RelayGroupChannel]s they populate,
 * because the two are filled in at different moments. `LocalCache.consume(GroupMetadataEvent)` loads the
 * event onto its addressable note and wakes the cache observers *first*, and only then copies it into
 * the channel — so a projection woken by that very emission reads a channel that is still empty, gets
 * [ChannelClassification.UNKNOWN], and, because nothing emits a second time, stays wrong until an
 * unrelated membership notice happens to arrive. (It also covers the case where the channel is never
 * populated at all: `consume` only touches it when the event carried relay provenance.) Reading the
 * event that caused the emission cannot race with itself.
 *
 * Keyed by group id alone, without the host relay: this is a fallback for [classifyBuzzChannel], which
 * still prefers the relay-scoped channel whenever that one has already been filled in.
 */
fun buzzChannelTypes(metadataNotes: List<Note>): Map<String, ChannelClassification> {
    val types = HashMap<String, ChannelClassification>(metadataNotes.size)
    metadataNotes.forEach { note ->
        val metadata = note.event as? GroupMetadataEvent ?: return@forEach
        types[metadata.groupId()] =
            if (metadata.isBuzzDmChannel()) ChannelClassification.DM else ChannelClassification.NAMED
    }
    return types
}

/**
 * What [cache] currently knows about a channel's type, from its kind-39000.
 *
 * [ChannelClassification.UNKNOWN] until the directory lands — callers decide what to do with that, and
 * the invite projection deliberately withholds rather than guessing (see
 * [com.vitorpamplona.amethyst.commons.model.buzz.BuzzChannelInvites.pendingInvites]).
 *
 * [knownTypes] (from [buzzChannelTypes]) is consulted when the channel has no metadata yet, which is
 * what makes the answer stable at the instant the directory lands — see that function for why the
 * channel alone is not enough.
 */
fun classifyBuzzChannel(
    cache: LocalCache,
    channelId: String,
    relay: NormalizedRelayUrl,
    knownTypes: Map<String, ChannelClassification> = emptyMap(),
): ChannelClassification {
    val metadata =
        cache.getRelayGroupChannelIfExists(GroupId(channelId, relay))?.event
            ?: return knownTypes[channelId] ?: ChannelClassification.UNKNOWN
    return if (metadata.isBuzzDmChannel()) ChannelClassification.DM else ChannelClassification.NAMED
}
