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
package com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.GroupDiscoveryConstraint
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.toGroupConstraints
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo.isRelaySignedRelayGroup
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

/**
 * Resolves a relay-signed kind-39000 metadata note to the live [RelayGroupChannel] it belongs to.
 * A group is keyed by (host relay + group id); the SAME resolver is used by the feed filter's
 * match/sort AND the discovery row's renderer, so a note seen on more than one relay always binds
 * to one channel (no more "sorted by relay B's roster, rendered with relay A's empty one").
 */
fun relayGroupDiscoveryChannelFor(note: Note): RelayGroupChannel? {
    val groupId = (note.event as? GroupMetadataEvent)?.groupId() ?: return null
    return note.relays.firstNotNullOfOrNull { LocalCache.getRelayGroupChannelIfExists(GroupId(groupId, it)) }
}

/**
 * The Relay Groups discovery feed. Rows are the relay-signed kind-39000 metadata notes; the
 * top-nav filter is resolved into a per-relay [GroupDiscoveryConstraint] ([toGroupConstraints])
 * that decides which groups qualify: every group for Global, relay-key / admin / member for the
 * people filters (rosters read from the group's [RelayGroupChannel]), or a `#t`/`#g` tag match
 * for topic/geo filters. Structurally identical to
 * [com.vitorpamplona.amethyst.commons.gitRepositories.dal.GitRepositoriesFeedFilter];
 * the only difference is that a group's author is its relay, so the match runs through the
 * roster-aware constraint rather than the author-based [com.vitorpamplona.amethyst.ui.dal.FilterByListParams].
 */
class RelayGroupDiscoveryFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    /**
     * The feed identity is the selected list PLUS a fingerprint of what the feed actually filters
     * on. The list flips synchronously when the spinner changes, but the per-relay set it resolves
     * to ([liveRelayGroupsDiscoveryFollowListsPerRelay]) lands a frame later (the outbox loader is
     * async). If the key were the list alone, the first refresh would run against the STALE set
     * (showing the old filter's groups) and the catch-up emission — same list code — would be
     * swallowed by [checkKeysInvalidateDataAndSendToTop]'s "key unchanged" guard, freezing the feed
     * on the previous filter until a manual pull-to-refresh. Folding the resolved discriminator in
     * (the joined ids for "My Groups", the per-relay constraints otherwise — both content-hashed via
     * data classes) makes the key move when the resolution lands, so the refresh actually fires.
     */
    override fun feedKey(): String =
        account.userProfile().pubkeyHex + "-" + followList().code + "-" +
            (if (isMine()) joinedGroupIds().hashCode() else constraints().hashCode())

    override fun limit() = 200

    fun followList(): TopFilter = account.settings.defaultRelayGroupsDiscoveryFollowList.value

    /**
     * "My Groups" = every group I'm participating in. That's the UNION of:
     *  - the groups I explicitly joined (my kind-10009 list) — authoritative and immediate, even
     *    before the relay adds me to its roster or for open groups it doesn't roster; and
     *  - any group whose relay-signed roster (39001/39002) lists me as an admin/member (e.g. an
     *    admin added me, or I was rostered on a relay I haven't listed in kind 10009).
     *
     * These live on their host relays (from kind 10009), not my outbox, so the standard
     * [TopFilter.Mine] relay-set resolution wouldn't reach them. The joined groups' metadata +
     * rosters are kept in cache by RelayGroupMyJoinedGroupsSubscription mounted on the screen.
     */
    private fun isMine(): Boolean = followList() is TopFilter.Mine

    /** The (relay + id) of every group in my kind-10009 joined list. */
    private fun joinedGroupIds(): Set<GroupId> =
        account.relayGroupList.liveRelayGroupList.value
            .mapNotNullTo(HashSet()) { tag ->
                RelayUrlNormalizer.normalizeOrNull(tag.relayUrl)?.let { GroupId(tag.groupId, it) }
            }

    private fun isMyGroup(
        channel: RelayGroupChannel,
        joined: Set<GroupId>,
    ): Boolean = channel.groupId in joined || channel.membershipOf(account.userProfile().pubkeyHex).isMember()

    private fun constraints(): Map<NormalizedRelayUrl, GroupDiscoveryConstraint> = account.liveRelayGroupsDiscoveryFollowListsPerRelay.value.toGroupConstraints()

    override fun feed(): List<Note> {
        if (isMine()) return sort(myGroupNotes())
        val byRelay = constraints()
        val notes =
            LocalCache.addressables.filterIntoSet(GroupMetadataEvent.KIND) { _, note ->
                // Non-mine path here (isMine() returned above), so the joined set is never read.
                matches(note, byRelay, emptySet())
            }
        return sort(notes)
    }

    private fun myGroupNotes(): Set<Note> {
        val joined = joinedGroupIds()
        val notes = HashSet<Note>()
        // Groups I explicitly joined — resolve each to its metadata note (may be null until loaded).
        // DMs (hidden 39000s) are excluded even from "My Groups": they belong to the DM section.
        joined.forEach {
            LocalCache
                .getRelayGroupChannelIfExists(it)
                ?.takeUnless { c -> c.isHidden() }
                ?.metadataNote
                ?.let(notes::add)
        }
        // Plus any group whose roster lists me as an admin/member.
        val me = account.userProfile().pubkeyHex
        LocalCache.relayGroupChannels
            .filter { _, channel -> channel.event != null && !channel.isHidden() && channel.membershipOf(me).isMember() }
            .forEach { it.metadataNote?.let(notes::add) }
        return notes
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        val byRelay = constraints()
        // Compute the joined set once per batch instead of rebuilding a HashSet (with per-tag URL
        // normalization) inside matches() for every event.
        val joined = if (isMine()) joinedGroupIds() else emptySet()
        return newItems.flatMapTo(HashSet()) { note ->
            when (note.event) {
                is GroupMetadataEvent -> if (matches(note, byRelay, joined)) listOf(note) else emptyList()
                // A roster change (39001/39002) can newly qualify a group whose 39000 already
                // arrived (a follow just became an admin/member). The roster note itself isn't a
                // feed row, so re-test the group's metadata note and inject it if it now matches —
                // otherwise the group would stay hidden (or frozen at 0 members) until a refresh.
                is GroupAdminsEvent, is GroupMembersEvent ->
                    rosterMetadataNote(note)?.takeIf { matches(it, byRelay, joined) }?.let { listOf(it) } ?: emptyList()
                else -> emptyList()
            }
        }
    }

    /**
     * The group qualifies when its host relay is in the filter's set AND that relay's constraint
     * accepts it. [relayGroupDiscoveryChannelFor] picks the one channel (metadata + roster); the
     * roster lives on the [RelayGroupChannel] [LocalCache.consume] created when the metadata arrived.
     */
    private fun matches(
        note: Note,
        byRelay: Map<NormalizedRelayUrl, GroupDiscoveryConstraint>,
        joined: Set<GroupId>,
    ): Boolean {
        val channel = relayGroupDiscoveryChannelFor(note) ?: return false
        // DMs carry the NIP-29 `hidden` tag (Buzz sets it on t=dm 39000s) — private conversations, not
        // browsable groups. Keep them out of the discovery list entirely (they live in the DM section).
        if (channel.isHidden()) return false
        if (isMine()) return isMyGroup(channel, joined)
        if (byRelay.isEmpty()) return false
        // Only surface groups whose 39000 is actually signed by the host relay's key (NIP-29's
        // authority). This drops stray user-published 39000s stored on general relays — the ones
        // with no roster that can't be joined — even when the relay itself is otherwise queried.
        if (!isRelaySignedRelayGroup(channel)) return false
        return byRelay[channel.groupId.relayUrl]?.matches(channel) == true
    }

    /** The 39000 metadata note for the group a roster (39001/39002) note belongs to, if cached. */
    private fun rosterMetadataNote(note: Note): Note? {
        val groupId =
            when (val event = note.event) {
                is GroupAdminsEvent -> event.groupId()
                is GroupMembersEvent -> event.groupId()
                else -> null
            } ?: return null
        return note.relays
            .firstNotNullOfOrNull { LocalCache.getRelayGroupChannelIfExists(GroupId(groupId, it)) }
            ?.metadataNote
    }

    override fun sort(items: Set<Note>): List<Note> {
        // Snapshot the member count once per note (stable key), then order by it descending with
        // the shared default feed order (createdAt snapshot + id) as a stable tie-break. Reading
        // createdAt live inside a comparator risks TimSort's "contract violated" crash when a
        // newer 39000 replaces a note's event mid-sort — sortedByDefaultFeedOrder avoids that.
        val memberCount = items.associateWith { relayGroupDiscoveryChannelFor(it)?.memberCount() ?: 0 }
        return items.sortedByDefaultFeedOrder().sortedByDescending { memberCount[it] }
    }
}
