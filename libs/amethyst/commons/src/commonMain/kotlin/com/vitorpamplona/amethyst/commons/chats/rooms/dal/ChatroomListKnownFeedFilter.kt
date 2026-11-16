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
package com.vitorpamplona.amethyst.commons.chats.rooms.dal

import com.vitorpamplona.amethyst.commons.chats.publicChannels.concord.isConcordTimelineMessage
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.isRelayGroupTimelineMessage
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.newestTimelineNote
import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.chats.ConcordServerRoomNote
import com.vitorpamplona.amethyst.commons.model.chats.PinnedChatroomNote
import com.vitorpamplona.amethyst.commons.model.chats.RelayGroupServerRoomNote
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.concord.ConcordViewMode
import com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupViewMode
import com.vitorpamplona.amethyst.commons.model.publicChatChannelIdOf
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.replace
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.groupId
import com.vitorpamplona.quartz.nip29RelayGroups.isGroupChatContent
import com.vitorpamplona.quartz.nip29RelayGroups.isGroupScoped
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent

class ChatroomListKnownFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex

    private fun isEnabled(type: ChatFeedType): Boolean = account.chatFeedToggles.isEnabled(type)

    private fun pinnedRooms(): Set<ChatroomKey> = account.settings.syncedSettings.chats.pinnedChatrooms.value

    // One stable placeholder per pinned room, so feed diffing sees the same row across rebuilds.
    private val placeholderLock = KmpLock()
    private val pinnedPlaceholders = HashMap<ChatroomKey, PinnedChatroomNote>()

    private fun pinnedPlaceholder(room: ChatroomKey): PinnedChatroomNote = placeholderLock.withLock { pinnedPlaceholders.getOrPut(room) { PinnedChatroomNote(room) } }

    /**
     * The DM room a row stands for: a real NIP-04/NIP-17 message, an unsent draft of one (a room's
     * newest entry can be my NIP-37 draft wrap, which the row renders by its rumor), or a pinned room
     * with nothing loaded.
     */
    private fun privateRoomKeyOf(
        note: Note,
        myPubKey: HexKey,
    ): ChatroomKey? {
        if (note is PinnedChatroomNote) return note.room
        val event = note.event
        if (event !is DraftWrapEvent) return (event as? ChatroomKeyable)?.chatroomKey(myPubKey)

        val rumor = account.draftsDecryptionCache.preCachedDraft(event) as? ChatroomKeyable
        return rumor?.chatroomKey(myPubKey) ?: draftRoomKeyOf(note)
    }

    /**
     * The room an indexed draft sits in, for when its rumor has dropped out of the decryption cache.
     * Without it the row would flip between pinned and unpinned as the cache evicts and re-decrypts.
     */
    private fun draftRoomKeyOf(note: Note): ChatroomKey? {
        val gatherers = note.inGatherers ?: return null
        var found: ChatroomKey? = null
        account.chatroomList.rooms.forEach { key, room ->
            if (found == null && room in gatherers) found = key
        }
        return found
    }

    // returns the last Note of each user.
    override fun feed(): List<Note> {
        val chatList = account.chatroomList
        val followingKeySet = account.followingKeySet()
        val pinned = pinnedRooms()

        // Pinning a room is an explicit "I know this conversation", so a pinned room is Known even
        // when its counterpart is a stranger I never answered.
        val privateMessages =
            chatList.rooms.mapNotNull { key, chatroom ->
                val newest = chatroom.newestMessage
                if (newest != null &&
                    account.isKnownChatroom(key, chatroom, followingKeySet) &&
                    !account.isAllHidden(key.users)
                ) {
                    newest
                } else {
                    null
                }
            }

        // Pinned rooms with no message loaded. NIP-17 gift wraps can't be fetched per counterpart, so
        // a pinned conversation whose newest message is older than the inbox's download window would
        // otherwise drop off Messages entirely. Keep a placeholder row until a message arrives.
        val pinnedWithoutMessages =
            // Only while NIP-17 is on: the placeholder stands in for messages its download window missed.
            // With NIP-17 off the room was emptied on purpose, and "No recent messages loaded" would be
            // wrong (and its NIP-04 messages, if any, still give it a real row).
            if (!isEnabled(ChatFeedType.NIP17)) {
                emptyList()
            } else {
                pinned.mapNotNull { key ->
                    if (chatList.rooms.get(key)?.newestMessage == null && !account.isAllHidden(key.users)) {
                        pinnedPlaceholder(key)
                    } else {
                        null
                    }
                }
            }

        // Every pin change rebuilds this feed, so forget the placeholders of rooms no longer pinned.
        placeholderLock.withLock { pinnedPlaceholders.keys.retainAll(pinned) }

        val publicChannels =
            if (!isEnabled(ChatFeedType.NIP28)) {
                emptyList()
            } else {
                account
                    .publicChatList.flowSet.value
                    .mapNotNull { channelId ->
                        LocalCache
                            .getOrCreatePublicChatChannel(channelId)
                            .notes
                            .filter { _, it -> account.isAcceptable(it) && it.event != null }
                            .sortedByDefaultFeedOrder()
                            .firstOrNull()
                    }
            }

        val ephemeralChats =
            if (!isEnabled(ChatFeedType.EPHEMERAL)) {
                emptyList()
            } else {
                account
                    .ephemeralChatList.liveEphemeralChatList.value
                    .mapNotNull { it ->
                        LocalCache
                            .getOrCreateEphemeralChannel(it)
                            .notes
                            .filter { _, it -> account.isAcceptable(it) && it.event != null }
                            .sortedByDefaultFeedOrder()
                            .firstOrNull()
                    }
            }

        // Joined geohash location channels (kind 10081 list). Ephemeral, so a quiet
        // cell has no stored message — show a placeholder row until one arrives, the
        // same way just-joined NIP-29/Marmot groups do.
        val geohashChannels =
            if (!isEnabled(ChatFeedType.GEOHASH)) {
                emptyList()
            } else {
                account.geohashList.flow.value.map { geohash ->
                    val channel = LocalCache.getOrCreateGeohashChannel(geohash)
                    channel.notes
                        .filter { _, it -> account.isAcceptable(it) && it.event != null }
                        .sortedByDefaultFeedOrder()
                        .firstOrNull() ?: channel.placeholderNote()
                }
            }

        val marmotGroups =
            if (!isEnabled(ChatFeedType.MARMOT)) {
                emptyList()
            } else {
                account.marmotGroupList.rooms.mapNotNull { _, chatroom ->
                    if (chatroom.isKnown(followingKeySet)) {
                        chatroom.newestMessage ?: chatroom.placeholderNote()
                    } else {
                        null
                    }
                }
            }

        // cordn groups. Every one this account holds is a room it is IN — a
        // cordn group is joined by accepting a Welcome, so there is no
        // "known vs new" split to make here the way there is for a DM from a
        // stranger. (The pending side is the Welcome inbox, Stage C.)
        //
        // One row per room, carried by the room's own Note: cordn messages are
        // MLS envelopes and never enter LocalCache, so the row reads its name
        // and preview from the room through Note.inGatherers.
        val cordnGroups =
            if (!isEnabled(ChatFeedType.CORDN)) {
                emptyList()
            } else {
                account.cordnRuntime
                    ?.groups
                    ?.all
                    ?.value
                    ?.map { it.inboxRow() }
                    .orEmpty()
            }

        // NIP-29 relay groups the user joined (kind 10009). In INLINE view mode each group is its
        // own row tagged with its host relay; in GROUPED mode all the groups on one relay collapse
        // to a single relay row positioned by that relay's newest message. Both interleave with the
        // rest of the Messages list by recency.
        val relayGroups =
            if (!isEnabled(ChatFeedType.NIP29)) {
                emptyList()
            } else {
                when (account.settings.relayGroupViewMode.value) {
                    RelayGroupViewMode.INLINE ->
                        account.relayGroupList.liveRelayGroupList.value.mapNotNull { groupTag ->
                            val relay = RelayUrlNormalizer.normalizeOrNull(groupTag.relayUrl) ?: return@mapNotNull null
                            val channel = LocalCache.getOrCreateRelayGroupChannel(GroupId(groupTag.groupId, relay))
                            // Newest loaded chat message, or a placeholder row so a just-joined group shows
                            // up on Messages before its first kind-9 arrives (mirrors the Marmot-group path
                            // above). Content kinds only — never a reaction/deletion as the "last message".
                            channel.newestTimelineNote(account) ?: channel.placeholderNote()
                        }

                    RelayGroupViewMode.GROUPED ->
                        // One row per host relay (never duplicated), carrying the newest chat across ALL of
                        // that relay's joined groups so it lands in the newest-message spot among the DMs.
                        account.relayGroupList.liveRelayGroupList.value
                            .groupBy { it.relayUrl }
                            .mapNotNull { (relayUrl, tags) ->
                                val relay = RelayUrlNormalizer.normalizeOrNull(relayUrl) ?: return@mapNotNull null
                                val newest =
                                    tags
                                        .mapNotNull { LocalCache.getOrCreateRelayGroupChannel(GroupId(it.groupId, relay)).newestTimelineNote(account) }
                                        .maxByOrNull { it.createdAt() ?: 0L }
                                RelayGroupServerRoomNote(relay, newest)
                            }
                }
            }

        // Concord Channels the user joined (kind 13302 list → folded Control Plane). In INLINE view
        // mode each channel is its own Messages row (newest decrypted message — a real Note in
        // LocalCache attached to the ConcordChannel — or a placeholder for a just-joined empty
        // channel). In GROUPED mode all of a community's channels collapse into one community row
        // positioned by that community's newest message. Concord groups by community exactly as
        // NIP-29 groups by host relay above; both interleave with the rest of Messages by recency.
        val concordChannels =
            if (!isEnabled(ChatFeedType.CONCORD)) {
                emptyList()
            } else {
                when (account.settings.concordViewMode.value) {
                    ConcordViewMode.INLINE ->
                        account.concordSessions.sessions().flatMap { session ->
                            // Not folded yet (just joined, or its Control Plane never arrived): no
                            // channels to list, so show the community itself. Skipping it made a
                            // community whose genesis is missing invisible everywhere, with no way in
                            // and no way to leave it.
                            val state = session.state.value ?: return@flatMap listOf<Note>(ConcordServerRoomNote(session.entry.id, null))
                            state.channels.keys.map { channelIdHex ->
                                val channel = LocalCache.getOrCreateConcordChannel(ConcordChannelId(session.entry.id, channelIdHex))
                                channel.newestConcordNote(account) ?: channel.placeholderNote()
                            }
                        }

                    ConcordViewMode.GROUPED ->
                        // One row per joined community, carrying the newest message across ALL its channels.
                        account.concordSessions.sessions().map { session ->
                            // An unfolded community still gets its row (see INLINE above).
                            val state = session.state.value
                            val newest =
                                state
                                    ?.channels
                                    ?.keys
                                    .orEmpty()
                                    .mapNotNull { LocalCache.getOrCreateConcordChannel(ConcordChannelId(session.entry.id, it)).newestConcordNote(account) }
                                    .maxByOrNull { it.createdAt() ?: 0L }
                            ConcordServerRoomNote(session.entry.id, newest)
                        }
                }
            }

        return sort(
            (
                privateMessages + pinnedWithoutMessages + publicChannels + ephemeralChats + geohashChannels +
                    marmotGroups + cordnGroups + relayGroups + concordChannels
            ).toSet(),
        )
    }

    override fun updateListWith(
        oldList: List<Note>,
        newItems: Set<Note>,
    ): List<Note> {
        val me = account.userProfile()

        // Gets the latest message by channel from the new items.
        val newRelevantPublicMessages = filterRelevantPublicMessages(newItems, account)
        val newRelevantEphemeralChats = filterRelevantEphemeralChats(newItems, account)
        val newRelevantGeohashChats = filterRelevantGeohashChats(newItems, account)

        // Gets the latest message by room from the new items.
        val newRelevantPrivateMessages = filterRelevantPrivateMessages(newItems, account)
        val newRelevantRelayGroups = filterRelevantRelayGroupMessages(newItems, account)
        val newRelevantConcord = filterRelevantConcordMessages(newItems, account)

        if (newRelevantPrivateMessages.isEmpty() &&
            newRelevantPublicMessages.isEmpty() &&
            newRelevantEphemeralChats.isEmpty() &&
            newRelevantGeohashChats.isEmpty() &&
            newRelevantRelayGroups.isEmpty() &&
            newRelevantConcord.isEmpty()
        ) {
            return oldList
        }

        var myNewList = oldList

        newRelevantPublicMessages.forEach { newNotePair ->
            var hasUpdated = false
            oldList.forEach { oldNote ->
                val channelId = publicChannelIdOf(oldNote)
                if (newNotePair.key == channelId) {
                    hasUpdated = true
                    if ((newNotePair.value.createdAt() ?: 0L) > (oldNote.createdAt() ?: 0L)) {
                        myNewList = myNewList.replace(oldNote, newNotePair.value)
                    }
                }
            }
            if (!hasUpdated) {
                myNewList = myNewList.plus(newNotePair.value)
            }
        }

        newRelevantEphemeralChats.forEach { newNotePair ->
            var hasUpdated = false
            oldList.forEach { oldNote ->
                val noteEvent = (oldNote.event as? EphemeralChatEvent)?.roomId()
                if (newNotePair.key == noteEvent) {
                    hasUpdated = true
                    if ((newNotePair.value.createdAt() ?: 0L) > (oldNote.createdAt() ?: 0L)) {
                        myNewList = myNewList.replace(oldNote, newNotePair.value)
                    }
                }
            }
            if (!hasUpdated) {
                myNewList = myNewList.plus(newNotePair.value)
            }
        }

        newRelevantGeohashChats.forEach { newNotePair ->
            var hasUpdated = false
            oldList.forEach { oldNote ->
                if (newNotePair.key == oldNote.geohashRowKey()) {
                    hasUpdated = true
                    if ((newNotePair.value.createdAt() ?: 0L) > (oldNote.createdAt() ?: 0L)) {
                        myNewList = myNewList.replace(oldNote, newNotePair.value)
                    }
                }
            }
            if (!hasUpdated) {
                myNewList = myNewList.plus(newNotePair.value)
            }
        }

        newRelevantPrivateMessages.forEach { newNotePair ->
            var hasUpdated = false
            oldList.forEach { oldNote ->
                val oldRoom = privateRoomKeyOf(oldNote, me.pubkeyHex)

                if (newNotePair.key == oldRoom) {
                    hasUpdated = true
                    if ((newNotePair.value.createdAt() ?: 0L) > (oldNote.createdAt() ?: 0L)) {
                        myNewList = myNewList.replace(oldNote, newNotePair.value)
                    }
                }
            }
            if (!hasUpdated) {
                myNewList = myNewList.plus(newNotePair.value)
            }
        }

        newRelevantRelayGroups.forEach { newNotePair ->
            var hasUpdated = false
            oldList.forEach { oldNote ->
                if (newNotePair.key == oldNote.relayGroupRowKey()) {
                    hasUpdated = true
                    if ((newNotePair.value.createdAt() ?: 0L) > (oldNote.createdAt() ?: 0L)) {
                        myNewList = myNewList.replace(oldNote, newNotePair.value)
                    }
                }
            }
            if (!hasUpdated) {
                myNewList = myNewList.plus(newNotePair.value)
            }
        }

        newRelevantConcord.forEach { newNotePair ->
            var hasUpdated = false
            oldList.forEach { oldNote ->
                if (newNotePair.key == oldNote.concordRowKey()) {
                    hasUpdated = true
                    if ((newNotePair.value.createdAt() ?: 0L) > (oldNote.createdAt() ?: 0L)) {
                        myNewList = myNewList.replace(oldNote, newNotePair.value)
                    }
                }
            }
            if (!hasUpdated) {
                myNewList = myNewList.plus(newNotePair.value)
            }
        }

        return sort(myNewList.toSet()).take(1000)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        // Gets the latest message by channel from the new items.
        val newRelevantPublicMessages = filterRelevantPublicMessages(newItems, account)
        val newRelevantEphemeralChats = filterRelevantEphemeralChats(newItems, account)
        val newRelevantGeohashChats = filterRelevantGeohashChats(newItems, account)

        // Gets the latest message by room from the new items.
        val newRelevantPrivateMessages = filterRelevantPrivateMessages(newItems, account)
        val newRelevantRelayGroups = filterRelevantRelayGroupMessages(newItems, account)
        val newRelevantConcord = filterRelevantConcordMessages(newItems, account)

        return if (newRelevantPrivateMessages.isEmpty() &&
            newRelevantPublicMessages.isEmpty() &&
            newRelevantEphemeralChats.isEmpty() &&
            newRelevantGeohashChats.isEmpty() &&
            newRelevantRelayGroups.isEmpty() &&
            newRelevantConcord.isEmpty()
        ) {
            emptySet()
        } else {
            (
                newRelevantPrivateMessages.values +
                    newRelevantPublicMessages.values +
                    newRelevantEphemeralChats.values +
                    newRelevantGeohashChats.values +
                    newRelevantRelayGroups.values +
                    newRelevantConcord.values
            ).toSet()
        }
    }

    /** The geohash a Messages row belongs to — from a real kind-20000 note or a placeholder's channel gatherer. */
    private fun Note.geohashRowKey(): String? =
        (event as? GeohashChatEvent)?.geohash()
            ?: inGatherers?.firstNotNullOfOrNull { (it as? GeohashChatChannel)?.geohash }

    private fun filterRelevantGeohashChats(
        newItems: Set<Note>,
        account: Account,
    ): MutableMap<String, Note> {
        if (!isEnabled(ChatFeedType.GEOHASH)) return mutableMapOf()
        val joined = account.geohashList.flow.value
        val newRelevant = mutableMapOf<String, Note>()
        newItems.forEach { newNote ->
            val geohash = (newNote.event as? GeohashChatEvent)?.geohash()
            if (geohash != null && geohash in joined && account.isAcceptable(newNote)) {
                val lastNote = newRelevant[geohash]
                if (lastNote == null || (newNote.createdAt() ?: 0L) > (lastNote.createdAt() ?: 0L)) {
                    newRelevant[geohash] = newNote
                }
            }
        }
        return newRelevant
    }

    /**
     * The row a Concord note belongs to, so [updateListWith] can find and replace it: a per-community
     * [ConcordServerRoomNote] (GROUPED), else the note's ConcordChannel gatherer keyed by channel
     * (INLINE) or by community (GROUPED), depending on the current view mode.
     */
    private fun Note.concordRowKey(): String? =
        when (this) {
            is ConcordServerRoomNote -> communityId
            else ->
                inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel }?.let { ch ->
                    when (account.settings.concordViewMode.value) {
                        ConcordViewMode.INLINE -> ch.channelId.toKey()
                        ConcordViewMode.GROUPED -> ch.channelId.communityId
                    }
                }
        }

    /**
     * Latest Concord rows from the new items, keyed the same way as [concordRowKey]: by channel in
     * INLINE mode (one row per channel) and by community in GROUPED mode (one row per community,
     * carried as a [ConcordServerRoomNote]). A Concord message note carries its ConcordChannel as a
     * gatherer (attached on decrypt); only message-like rumors are attached as rows — reactions/
     * deletes wire to their target note and never become a room's last message.
     */
    private fun filterRelevantConcordMessages(
        newItems: Set<Note>,
        account: Account,
    ): MutableMap<String, Note> {
        if (!isEnabled(ChatFeedType.CONCORD)) return mutableMapOf()
        // Newest new message per channel (INLINE) or per community (GROUPED).
        val grouped = account.settings.concordViewMode.value == ConcordViewMode.GROUPED
        val newestPerKey = mutableMapOf<String, Note>()
        newItems.forEach { newNote ->
            val channel = newNote.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } ?: return@forEach
            if (newNote.event == null || !account.isAcceptable(newNote)) return@forEach
            val key = if (grouped) channel.channelId.communityId else channel.channelId.toKey()
            val last = newestPerKey[key]
            if (last == null || (newNote.createdAt() ?: 0L) > (last.createdAt() ?: 0L)) newestPerKey[key] = newNote
        }
        if (!grouped) return newestPerKey
        // Wrap each community's newest into its collapsed server row.
        val result = mutableMapOf<String, Note>()
        newestPerKey.forEach { (communityId, note) -> result[communityId] = ConcordServerRoomNote(communityId, note) }
        return result
    }

    private fun filterRelevantPublicMessages(
        newItems: Set<Note>,
        account: Account,
    ): MutableMap<String, Note> {
        if (!isEnabled(ChatFeedType.NIP28)) return mutableMapOf()
        val followingChannels = account.publicChatList.flowSet.value
        val newRelevantPublicMessages = mutableMapOf<String, Note>()
        newItems
            .forEach { newNote ->
                val channelId = (newNote.event as? ChannelMessageEvent)?.channelId()
                if (channelId != null && channelId in followingChannels && account.isAcceptable(newNote)) {
                    val lastNote = newRelevantPublicMessages.get(channelId)
                    if (lastNote != null) {
                        if ((newNote.createdAt() ?: 0L) > (lastNote.createdAt() ?: 0L)) {
                            newRelevantPublicMessages.put(channelId, newNote)
                        }
                    } else {
                        newRelevantPublicMessages.put(channelId, newNote)
                    }
                }
            }
        return newRelevantPublicMessages
    }

    private fun filterRelevantEphemeralChats(
        newItems: Set<Note>,
        account: Account,
    ): MutableMap<RoomId, Note> {
        if (!isEnabled(ChatFeedType.EPHEMERAL)) return mutableMapOf()
        val followingEphemeralChats = account.ephemeralChatList.liveEphemeralChatList.value
        val newRelevantEphemeralChats = mutableMapOf<RoomId, Note>()
        newItems
            .forEach { newNote ->
                val noteEvent = newNote.event as? EphemeralChatEvent
                if (noteEvent != null) {
                    val room = noteEvent.roomId()
                    if (room != null && room in followingEphemeralChats && account.isAcceptable(newNote)) {
                        val lastNote = newRelevantEphemeralChats.get(room)
                        if (lastNote != null) {
                            if ((newNote.createdAt() ?: 0L) > (lastNote.createdAt() ?: 0L)) {
                                newRelevantEphemeralChats.put(room, newNote)
                            }
                        } else {
                            newRelevantEphemeralChats.put(room, newNote)
                        }
                    }
                }
            }
        return newRelevantEphemeralChats
    }

    /**
     * The newest decrypted *timeline* message loaded in this Concord channel, or null if none yet.
     * Uses [isConcordTimelineMessage] so a trailing kind-1111 thread reply (or a hidden author)
     * isn't shown as the Messages-row "last message" — the same predicate the channel feed and the
     * unread badge use, so the row summary can't disagree with what opening the channel renders.
     */
    private fun ConcordChannel.newestConcordNote(account: Account): Note? =
        notes
            .filter { _, it -> isConcordTimelineMessage(it, account) }
            .sortedByDefaultFeedOrder()
            .firstOrNull()

    /**
     * The row a relay-group note belongs to in the feed, so [updateListWith] can find and replace it:
     * a per-relay [RelayGroupServerRoomNote] (GROUPED), a joined group's chat note keyed by group id
     * (INLINE), or an empty-group placeholder resolved back to its group id via its channel gatherer.
     */
    private fun Note.relayGroupRowKey(): String? =
        when (this) {
            is RelayGroupServerRoomNote -> relay.url
            else ->
                event?.takeIf { it.isGroupScoped() }?.groupId()
                    ?: inGatherers?.firstNotNullOfOrNull { (it as? RelayGroupChannel)?.groupId?.id }
        }

    /**
     * Latest relay-group rows from the new items, keyed the same way as [relayGroupRowKey]: by group
     * id in INLINE mode (one row per group) and by host relay url in GROUPED mode (one row per relay).
     * Only actual group content counts — a reaction (kind 7)/deletion/label carries the group's `h`
     * tag too, so [Event.isGroupChatContent] gates them out of ever becoming a room's "last message".
     */
    private fun filterRelevantRelayGroupMessages(
        newItems: Set<Note>,
        account: Account,
    ): MutableMap<String, Note> {
        if (!isEnabled(ChatFeedType.NIP29)) return mutableMapOf()
        val joined = account.relayGroupList.liveRelayGroupList.value
        if (joined.isEmpty()) return mutableMapOf()

        return when (account.settings.relayGroupViewMode.value) {
            RelayGroupViewMode.INLINE -> {
                val joinedGroupIds = joined.mapTo(HashSet()) { it.groupId }
                val result = mutableMapOf<String, Note>()
                newItems.forEach { newNote ->
                    val gid = newNote.event?.takeIf { isRelayGroupTimelineMessage(newNote, account) }?.groupId()
                    if (gid != null && gid in joinedGroupIds) {
                        val lastNote = result[gid]
                        if (lastNote == null || (newNote.createdAt() ?: 0L) > (lastNote.createdAt() ?: 0L)) {
                            result[gid] = newNote
                        }
                    }
                }
                result
            }

            RelayGroupViewMode.GROUPED -> {
                val groupToRelay = HashMap<String, NormalizedRelayUrl>()
                joined.forEach { tag ->
                    RelayUrlNormalizer.normalizeOrNull(tag.relayUrl)?.let { groupToRelay[tag.groupId] = it }
                }
                // Newest new message per host relay, collapsed into one per-relay row.
                val newestPerRelay = HashMap<NormalizedRelayUrl, Note>()
                newItems.forEach { newNote ->
                    val gid = newNote.event?.takeIf { isRelayGroupTimelineMessage(newNote, account) }?.groupId() ?: return@forEach
                    val relay = groupToRelay[gid] ?: return@forEach
                    val lastNote = newestPerRelay[relay]
                    if (lastNote == null || (newNote.createdAt() ?: 0L) > (lastNote.createdAt() ?: 0L)) {
                        newestPerRelay[relay] = newNote
                    }
                }
                val result = mutableMapOf<String, Note>()
                newestPerRelay.forEach { (relay, note) -> result[relay.url] = RelayGroupServerRoomNote(relay, note) }
                result
            }
        }
    }

    private fun filterRelevantPrivateMessages(
        newItems: Set<Note>,
        account: Account,
    ): MutableMap<ChatroomKey, Note> {
        val me = account.userProfile()
        val followingKeySet = account.followingKeySet()

        val newRelevantPrivateMessages = mutableMapOf<ChatroomKey, Note>()
        newItems
            .forEach { newNote ->
                val dm = newNote.event as? ChatroomKeyable
                // Same gate as ingestion: a protocol turned off never reaches the rooms.
                if (dm != null && !account.chatFeedToggles.isEnabled(dm)) return@forEach
                val roomKey = dm?.chatroomKey(me.pubkeyHex)
                if (roomKey != null) {
                    val room = account.chatroomList.rooms.get(roomKey)
                    if (room != null &&
                        (
                            newNote.author?.pubkeyHex == me.pubkeyHex ||
                                account.isKnownChatroom(roomKey, room, followingKeySet)
                        ) &&
                        !account.isAllHidden(roomKey.users)
                    ) {
                        val lastNote = newRelevantPrivateMessages.get(roomKey)
                        if (lastNote != null) {
                            if ((newNote.createdAt() ?: 0L) > (lastNote.createdAt() ?: 0L)) {
                                newRelevantPrivateMessages.put(roomKey, newNote)
                            }
                        } else {
                            newRelevantPrivateMessages.put(roomKey, newNote)
                        }
                    }
                }
            }
        return newRelevantPrivateMessages
    }

    override fun sort(items: Set<Note>): List<Note> {
        val pinned = pinnedRooms()
        if (pinned.isEmpty()) return items.sortedByDefaultFeedOrder()

        val me = account.userProfile().pubkeyHex
        // Snapshots isPinned + createdAt once per note so the comparator stays consistent
        // even if another thread swaps a Note's event mid-sort. Avoids TimSort's
        // "Comparison method violates its general contract!" IllegalArgumentException.
        return items
            .map { Triple(it, isPinned(it, me, pinned), it.createdAt() ?: 0L) }
            .sortedWith(
                compareByDescending<Triple<Note, Boolean, Long>> { it.second }
                    .thenByDescending { it.third }
                    .thenBy { it.first.idHex },
            ).map { it.first }
    }

    private fun isPinned(
        note: Note,
        myPubKey: HexKey,
        pinned: Set<ChatroomKey>,
    ): Boolean {
        val room = privateRoomKeyOf(note, myPubKey) ?: return false
        return room in pinned
    }

    // Maps a note that represents a public chat row to its channel id. The
    // representative note for a channel may be the channel's create event
    // (id == channelId), a metadata update, or a message — all three resolve so
    // an arriving ChannelMessageEvent replaces an existing placeholder
    // metadata/create note for the same channel instead of duplicating it
    // (which would yield the same LazyColumn key twice).
    private fun publicChannelIdOf(note: Note): String? = publicChatChannelIdOf(note.event)
}
