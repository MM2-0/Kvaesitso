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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.relays.dialableRelays
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUserIds
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.isGroupScoped
import com.vitorpamplona.quartz.nip34Git.grasp.GraspServer
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.BookmarkSetEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent

/**
 * The signed-in account's own relay sets, as [BroadcastRelayPlanner] needs them. The app
 * implements it over `Account`'s relay-list flows ([EventBroadcaster]); amy over its
 * `~/.amy` relay lists.
 */
interface BroadcastRelaySource {
    /** The signed-in user, as a [User] of the same cache the planner reads. */
    fun userProfile(): User

    /** Where others reach this user: the NIP-65 inbox plus local relays. */
    fun notificationRelays(): Set<NormalizedRelayUrl>

    /** The user's broadcast-relay list (NIP-51 kind:10088). */
    fun broadcastRelays(): Set<NormalizedRelayUrl>

    /** The NIP-65 outbox plus private and local relays, with the broadcast list mixed in. */
    fun outboxRelays(): Set<NormalizedRelayUrl>

    /** The same outbox without the broadcast list — for personal and channel events. */
    fun personalOutboxRelays(): Set<NormalizedRelayUrl>

    /** Where a profile (kind:0) or relay list (kind:10002) goes: every relay we know of. */
    fun everywhereRelays(): Set<NormalizedRelayUrl>
}

/** How far up a reply chain the repository of a git event is looked for. */
private const val MAX_GIT_PARENT_DEPTH = 4

private const val REPOSITORY_PREFIX = "${GitRepositoryEvent.KIND}:"

/** NIP-34 kinds: announcement, state, patch, PR, PR update, issue, reply, cover note, statuses, review. */
private val GIT_KINDS =
    setOf(
        GitRepositoryEvent.KIND,
        GitRepositoryStateEvent.KIND,
        1617,
        1618,
        1619,
        1621,
        1622,
        1624,
        1630,
        1631,
        1632,
        1633,
        7321,
    )

/** Kinds that act on another event: a NIP-22 comment, reaction, repost, label and deletion. */
private val ACTING_KINDS = setOf(1111, 7, 6, 16, 1985, 5)

/**
 * Where an event the user publishes should go: the NIP-65 outbox model plus relay hints,
 * the inboxes of everyone the event tags or answers, channel and NIP-29 group home relays,
 * the broadcast list, and DM inboxes for gift wraps. Pure routing policy over an
 * [EventCache] and the account's own relay sets ([BroadcastRelaySource]) — no publishing.
 *
 * Extracted from `EventBroadcaster` so the app and amy route every event identically:
 * [EventBroadcaster] delegates here with the app's `LocalCache`; amy builds an
 * [EventCache] from its local store and calls the same methods.
 */
class BroadcastRelayPlanner(
    private val cache: EventCache,
    private val source: BroadcastRelaySource,
) {
    private fun computeRelayListForLinkedUser(user: User): Set<NormalizedRelayUrl> =
        if (user == source.userProfile()) {
            source.notificationRelays()
        } else {
            user.inboxRelays()?.ifEmpty { null }?.toSet()
                ?: (
                    cache.relayHints
                        .hintsForKey(user.pubkeyHex)
                        .toSet() + user.allUsedRelays()
                )
        }

    private fun computeRelayListForLinkedUser(pubkey: HexKey): Set<NormalizedRelayUrl> =
        if (pubkey == source.userProfile().pubkeyHex) {
            source.notificationRelays()
        } else {
            cache
                .getUserIfExists(pubkey)
                ?.inboxRelays()
                ?.ifEmpty { null }
                ?.toSet()
                ?: cache.relayHints
                    .hintsForKey(pubkey)
                    .toSet()
        }

    private fun computeRelaysForChannels(event: Event): Set<NormalizedRelayUrl> = cache.getAnyChannel(event)?.relays() ?: emptySet()

    // Personal events the user stores just for themselves — drafts, app settings, bookmark
    // lists — and channel/community events that already declare their own home relays
    // should not be replicated to the user's broadcasting relays. Channel/community events
    // that don't define any home relays fall through to broadcast, since there's nowhere
    // else for them to land.
    private fun wantsBroadcastRelays(event: Event): Boolean {
        if (event is DraftWrapEvent ||
            event is AppSpecificDataEvent ||
            event is BookmarkListEvent ||
            event is OldBookmarkListEvent ||
            event is BookmarkSetEvent
        ) {
            return false
        }
        if (event is PollEvent && event.relays().isNotEmpty()) return false
        if (event is MeetingSpaceEvent && event.allRelayUrls().isNotEmpty()) return false
        if (event is MeetingRoomEvent && event.allRelayUrls().isNotEmpty()) return false
        if (event is LiveActivitiesEvent && event.allRelayUrls().isNotEmpty()) return false

        val channelRelays = cache.getAnyChannel(event)?.relays()
        if (channelRelays != null && channelRelays.isNotEmpty()) return false

        // A group-scoped event whose room this cache doesn't know yet: it still must not go to the
        // broadcast list. Its `h` tag names a room only its host can serve, so broadcasting it says
        // "I am in this group" to relays that can do nothing with the content.
        if (event.isGroupScoped()) return false

        return true
    }

    fun computeRelayListToBroadcast(event: Event): Set<NormalizedRelayUrl> = privateGitRelays(event) ?: computeRelayListToBroadcast(event, mutableSetOf())

    /** Where the deletion of [notes] goes; a private repository's events' deletions stay on its relays. */
    private fun privateDeletionRelays(notes: List<Note>): Set<NormalizedRelayUrl>? {
        val private = notes.mapNotNull { note -> note.event?.let { privateGitRelays(it) } }
        if (private.isEmpty()) return null
        return private.flatMapTo(HashSet()) { it } + notes.flatMap { it.relays }
    }

    /**
     * GRASP-08: an event of a private repository (its announcement, or anything filed under it or
     * acting on something filed under it) goes only to that repository's relays, never to the
     * user's or anyone's outbox. Null when the event belongs to no private repository.
     *
     * A repository whose announcement the cache lacks may be private: until it loads, the event
     * goes only where the items it acts on were seen (or, with none seen, to the relay hints that
     * name the repository), not to the outbox.
     *
     * Only git events and what acts on them count (see [actsOnRepository]): an ordinary note that
     * merely names a repository in an `a` tag routes as usual, so no note can steer the replies and
     * reactions to it onto a relay its author picked.
     */
    private fun privateGitRelays(event: Event): Set<NormalizedRelayUrl>? {
        // Gift wraps, seals, rumors and NIP-29 group content have their own routing rules.
        if (event is GiftWrapEvent || event is SealEvent || event is BaseDMGroupEvent || event.sig.isEmpty()) return null
        if (event is GitRepositoryEvent) return if (event.isPrivate()) repositoryRelaysOf(event) else null
        if (cache.relayGroupHostsFor(event).isNotEmpty()) return null
        val walk = gitWalk(event)
        if (walk.repositories.isEmpty()) return null
        val announcements = walk.repositories.map { cache.getAddressableNoteIfExists(it)?.event as? GitRepositoryEvent }
        val private = announcements.filterNotNull().filter { it.isPrivate() }
        if (private.isNotEmpty()) return private.flatMapTo(HashSet()) { repositoryRelaysOf(it) }
        if (announcements.all { it != null }) return null
        // Unknown: where its items were seen, rather than a hint someone else wrote; nowhere known,
        // the usual routing is all there is.
        return walk.seenOn.ifEmpty { walk.hints }.ifEmpty { null }
    }

    /** A private repository's relays: its `relays`, or with none listed, its GRASP servers' (from its clone URLs). */
    private fun repositoryRelaysOf(announcement: GitRepositoryEvent): Set<NormalizedRelayUrl> {
        val listed = announcement.relaysNorm().toSet()
        if (listed.isNotEmpty()) return listed
        return announcement
            .clones()
            .filter { it.contains("/npub1") }
            .mapNotNullTo(HashSet()) { clone -> GraspServer.relayUrl(clone)?.let { RelayUrlNormalizer.normalizeOrNull(it) } }
    }

    /** What [gitWalk] found: the repositories an event belongs to, where its items were seen, and the hints naming them. */
    private class GitWalk(
        val repositories: List<Address>,
        val seenOn: Set<NormalizedRelayUrl>,
        val hints: Set<NormalizedRelayUrl>,
    )

    /**
     * The repositories [event] belongs to, breadth first up to [MAX_GIT_PARENT_DEPTH] levels: the
     * ones it names when it is a git event or acts on one, else those of what it points at (e/E/q
     * and a/A). Breadth first, so a short path to a private item is never hidden by a long one
     * that reached the same event at the depth limit.
     */
    private fun gitWalk(event: Event): GitWalk {
        val repositories = LinkedHashSet<Address>()
        val seenOn = HashSet<NormalizedRelayUrl>()
        val hints = HashSet<NormalizedRelayUrl>()
        val visited = HashSet<HexKey>()
        var level = listOf(event)
        var depth = 0
        while (level.isNotEmpty() && depth <= MAX_GIT_PARENT_DEPTH) {
            val next = ArrayList<Event>()
            for (current in level) {
                if (!visited.add(current.id)) continue
                // The items acted on were seen somewhere; the event being published was not.
                if (depth > 0) cache.getNoteIfExists(current.id)?.relays?.let { seenOn.addAll(it) }
                val own = if (actsOnRepository(current)) gitRepositoryAddresses(current, hints) else emptyList()
                if (own.isNotEmpty()) {
                    repositories.addAll(own)
                    continue
                }
                if (depth == MAX_GIT_PARENT_DEPTH) continue
                current.tags.fastForEach { tag ->
                    if (tag.size < 2) return@fastForEach
                    val parent =
                        when (tag[0]) {
                            "e", "E", "q" -> cache.getNoteIfExists(tag[1])?.event
                            "a", "A" -> Address.parse(tag[1])?.let { cache.getAddressableNoteIfExists(it)?.event }
                            else -> null
                        }
                    if (parent != null) next.add(parent)
                }
            }
            level = next
            depth++
        }
        return GitWalk(repositories.toList(), seenOn, hints)
    }

    /** Git events, and the kinds that act on them (comments, reactions, reposts, labels, deletions). */
    private fun actsOnRepository(event: Event): Boolean = event.kind in GIT_KINDS || event.kind in ACTING_KINDS

    /** The repositories [event] names (adding the hints of those tags to [hints]); a repository's own address for an announcement or its signed state. */
    private fun gitRepositoryAddresses(
        event: Event,
        hints: MutableSet<NormalizedRelayUrl>,
    ): List<Address> {
        if (event is GitRepositoryEvent) return listOf(Address(GitRepositoryEvent.KIND, event.pubKey, event.dTag()))
        if (event is GitRepositoryStateEvent) return listOf(Address(GitRepositoryEvent.KIND, event.pubKey, event.dTag()))
        val found = ArrayList<Address>()
        event.tags.fastForEach { tag ->
            if (tag.size > 1 && (tag[0] == "a" || tag[0] == "A") && tag[1].startsWith(REPOSITORY_PREFIX)) {
                Address.parse(tag[1])?.let { found.add(it) }
                tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }?.let { hints.add(it) }
            }
        }
        return found
    }

    private fun computeRelayListToBroadcast(
        event: Event,
        visited: MutableSet<HexKey>,
    ): Set<NormalizedRelayUrl> {
        // a-tagged events can form cycles; without this the two recursive descents stack-overflow.
        if (!visited.add(event.id)) return emptySet()

        if (event is GiftWrapEvent) {
            val receiver = event.recipientPubKey()
            return if (receiver != null) {
                val relayList =
                    cache
                        .getOrCreateUser(receiver)
                        .dmInboxRelayList()
                        ?.dialableRelays()
                        ?.ifEmpty { null }
                relayList?.toSet() ?: computeRelayListForLinkedUser(receiver)
            } else {
                emptySet()
            }
        }
        // Seals, inner DM messages, and unsigned rumors never get broadcast
        // relays: they only travel inside gift wraps.
        if (event is SealEvent || event is BaseDMGroupEvent || event.sig.isEmpty()) {
            return emptySet()
        }

        // NIP-29 group content, and everything that refers to it — a kind-9 message, a kind-1111 comment,
        // a like, a zap request — exists in a room on a host relay and nowhere else. The room's members
        // read it there; the author's outbox and the broadcast list can neither serve it to them nor do
        // anything else useful with it, and for a private or closed group publishing it there advertises
        // who is in which room. So the host wins outright rather than being one more relay in the union.
        // Same rule the group reply composer already applies (CommentPostViewModel), applied to every
        // group-scoped event instead of just that one path.
        val groupHosts = cache.relayGroupHostsFor(event)
        if (groupHosts.isNotEmpty()) return groupHosts

        val includeBroadcast = wantsBroadcastRelays(event)
        val broadcastRelays = if (includeBroadcast) source.broadcastRelays() else emptySet()

        if (event is MetadataEvent || event is AdvertisedRelayListEvent) {
            // everywhere
            return source.everywhereRelays() + broadcastRelays
        }

        val relayList = mutableSetOf<NormalizedRelayUrl>()
        relayList.addAll(broadcastRelays)

        val author = cache.getUserIfExists(event.pubKey)

        if (author != null) {
            if (author == source.userProfile()) {
                if (includeBroadcast) {
                    relayList.addAll(source.outboxRelays())
                } else {
                    // outboxRelays() mixes in the broadcast list; for personal/channel events
                    // we want the user's NIP-65 / private / local outbox without it.
                    relayList.addAll(source.personalOutboxRelays())
                }
            } else {
                val relays =
                    author.outboxRelays()?.ifEmpty { null }
                        ?: author.allUsedRelaysOrNull()
                        ?: cache.relayHints.hintsForKey(author.pubkeyHex)

                relayList.addAll(relays)
            }
        } else {
            relayList.addAll(cache.relayHints.hintsForKey(event.pubKey))
        }

        if (event is PubKeyHintProvider) {
            event.pubKeyHints().forEach {
                relayList.add(it.relay)
            }
            event.linkedPubKeys().forEach { pubkey ->
                relayList.addAll(computeRelayListForLinkedUser(pubkey))
            }
        }

        if (event is EventHintProvider) {
            event.eventHints().forEach {
                relayList.add(it.relay)
            }
            event.linkedEventIds().forEach { eventId ->
                cache.getNoteIfExists(eventId)?.let { linkedNote ->
                    val linkedNoteAuthor = linkedNote.author

                    if (linkedNoteAuthor != null) {
                        relayList.addAll(computeRelayListForLinkedUser(linkedNoteAuthor))
                    } else {
                        relayList.addAll(linkedNote.relays.toSet())
                    }

                    linkedNote.event?.let { linkedEvent ->
                        relayList.addAll(computeRelayListToBroadcast(linkedEvent, visited))
                    }
                }
            }
        }

        if (event is AddressHintProvider) {
            event.addressHints().forEach {
                relayList.add(it.relay)
            }

            // An `a` coordinate names its own author, so the addressed user's inbox is reachable
            // straight from the tag. Everything in the loop below is nested inside a cache
            // lookup, so without this an event aimed at an addressable this device never cached
            // - an RSVP to a calendar appointment that arrived as a bare reference, say - went
            // only to the sender's own outbox and never to the author it was answering.
            addressedAuthors(event).forEach { authorPubKey ->
                relayList.addAll(computeRelayListForLinkedUser(authorPubKey))
            }

            event.linkedAddressIds().forEach { addressId ->
                cache.getAddressableNoteIfExists(addressId)?.let { linkedNote ->
                    val linkedNoteAuthor = linkedNote.author

                    if (linkedNoteAuthor != null) {
                        relayList.addAll(computeRelayListForLinkedUser(linkedNoteAuthor))
                    } else {
                        relayList.addAll(linkedNote.relays.toSet())
                    }

                    linkedNote.event?.let { linkedEvent ->
                        relayList.addAll(computeRelayListToBroadcast(linkedEvent, visited))
                    }
                }
            }
        }

        // NIP-34: the repository's own relays are where its patches and issues are read, and a
        // GRASP server only hosts a repository whose announcement it holds. Issues, patches and
        // PRs reach this through their `a` tag, which recurses into the cached announcement.
        if (event is GitRepositoryEvent) {
            relayList.addAll(event.relaysNorm())
        }

        if (event is PollEvent) {
            relayList.addAll(event.relays())
        }

        // A chess game is played on the relays it was found on: an opponent on jesterui, say,
        // listens only to its own relay set, so its moves have to reach those relays too.
        if (event is JesterEvent) {
            event.startEventId()?.let { gameId -> cache.getNoteIfExists(gameId)?.relays?.let(relayList::addAll) }
        }

        if (event is MeetingSpaceEvent) {
            relayList.addAll(event.allRelayUrls())
        }

        if (event is MeetingRoomEvent) {
            relayList.addAll(event.allRelayUrls())
        }

        if (event is LiveActivitiesEvent) {
            relayList.addAll(event.allRelayUrls())
        }

        relayList.addAll(computeRelaysForChannels(event))

        return relayList
    }

    fun computeRelayListToBroadcast(note: Note): Set<NormalizedRelayUrl> {
        val noteEvent = note.event
        return if (noteEvent != null) {
            computeRelayListToBroadcast(noteEvent)
        } else {
            note.relays.toSet()
        }
    }

    fun computeMyReactionToNote(
        note: Note,
        reaction: Event,
    ): Set<NormalizedRelayUrl> {
        // A reaction or repost of a private repository's event stays on that repository's relays.
        privateGitRelays(reaction)?.let { return it }
        val relaysItCameFrom = note.relays

        val inboxRelaysOfTheAuthorOfTheOriginalNote =
            note.author?.inboxRelays() ?: note.author?.pubkeyHex?.let {
                cache.relayHints.hintsForKey(it)
            } ?: emptyList()

        val reactionOutBoxRelays = source.outboxRelays()

        val taggedUsers = reaction.taggedUserIds() + (note.event?.taggedUserIds() ?: emptyList())

        val taggedUserInboxRelays =
            taggedUsers.flatMapTo(mutableSetOf()) { pubkey ->
                if (pubkey == source.userProfile().pubkeyHex) {
                    source.notificationRelays()
                } else {
                    cache
                        .getUserIfExists(pubkey)
                        ?.inboxRelays()
                        ?.ifEmpty { null }
                        ?.toSet()
                        ?: cache.relayHints.hintsForKey(pubkey).toSet()
                }
            }

        val channelRelays = cache.getAnyChannel(note)?.relays() ?: emptySet()

        val replyRelays =
            note.replyTo?.flatMapTo(mutableSetOf()) {
                val existingRelays = it.relays.toSet()

                val replyToAuthor = it.author

                val replyAuthorRelays =
                    if (replyToAuthor != null) {
                        if (replyToAuthor == source.userProfile()) {
                            source.outboxRelays()
                        } else {
                            replyToAuthor.inboxRelays()?.ifEmpty { null }?.toSet()
                                ?: replyToAuthor.allUsedRelaysOrNull()
                                ?: cache.relayHints
                                    .hintsForKey(replyToAuthor.pubkeyHex)
                                    .ifEmpty { null }
                                    ?.toSet()
                                ?: emptySet()
                        }
                    } else {
                        emptySet()
                    }

                existingRelays + replyAuthorRelays
            } ?: emptySet()

        return reactionOutBoxRelays +
            inboxRelaysOfTheAuthorOfTheOriginalNote +
            taggedUserInboxRelays +
            channelRelays +
            replyRelays +
            relaysItCameFrom
    }

    /**
     * Where a NIP-09 deletion of [notes] goes: our outbox and every relay a target was
     * seen on — a relay can only honour a deletion it receives.
     */
    fun computeDeletionRelays(notes: List<Note>): Set<NormalizedRelayUrl> {
        // A kind 5 names the deleted event's id and kind: for a private repository's event, that
        // must not reach the outbox either.
        privateDeletionRelays(notes)?.let { return it }
        val myRelayList = source.outboxRelays().toMutableSet()
        notes.forEach {
            myRelayList.addAll(it.relays)
        }
        return myRelayList
    }
}
