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
package com.vitorpamplona.amethyst.commons.home.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveComplexFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag as MeetingSpaceStatusTag

class HomeLiveFilter(
    val account: Account,
) : AdditiveComplexFeedFilter<Channel, Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + account.settings.defaultHomeFollowList.value

    override fun showHiddenKey(): Boolean = false

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            followLists = account.liveHomeFollowLists.value,
            hiddenUsers = account.hiddenUsers.flow.value,
        )

    fun limitTime() = TimeUtils.fifteenMinutesAgo()

    override fun feed(): List<Channel> {
        val filterParams = buildFilterParams(account)
        val fifteenMinsAgo = limitTime()

        val list =
            LocalCache.ephemeralChannels.filter { _, channel ->
                shouldIncludeChannel(channel, filterParams, fifteenMinsAgo)
            } +
                LocalCache.liveChatChannels.filter { _, channel ->
                    shouldIncludeChannel(channel, filterParams, fifteenMinsAgo)
                } +
                LocalCache.geohashChannels.filter { _, channel ->
                    // Geohash chat is anonymous (throwaway keys), so it is not follow-filtered — any
                    // recent activity in a cell the user is engaged with surfaces as a "near you" bubble.
                    (channel.lastNote?.createdAt() ?: 0L) > fifteenMinsAgo
                }

        return sort(list.toSet())
    }

    fun shouldIncludeChannel(
        channel: EphemeralChatChannel,
        filterParams: FilterByListParams,
        timeLimit: Long,
    ): Boolean =
        channel.notes
            .filter { _, value ->
                acceptableChatEvent(value, filterParams, timeLimit)
            }.isNotEmpty()

    fun shouldIncludeChannel(
        channel: LiveActivitiesChannel,
        filterParams: FilterByListParams,
        timeLimit: Long,
    ): Boolean {
        val liveChannel =
            channel.info?.let {
                val startedAt = it.starts()
                it.createdAt > timeLimit &&
                    (startedAt == null || (it.createdAt - startedAt) < TimeUtils.ONE_DAY) &&
                    it.status() == StatusTag.STATUS.LIVE &&
                    filterParams.match(it, channel.relays().toList())
            }

        if (liveChannel == true) {
            return true
        }

        // Audio-room presence (kind-10312) lives in `presenceNotes`,
        // not `notes`. Check it separately so a follow broadcasting in
        // a Nest still surfaces the bubble even if no chat happened.
        val hasPresence =
            channel.presenceNotes
                .filter { _, note ->
                    acceptableChatEvent(note, filterParams, timeLimit)
                }.isNotEmpty()
        if (hasPresence) return true

        return channel.notes
            .filter { _, value ->
                acceptableChatEvent(value, filterParams, timeLimit)
            }.isNotEmpty()
    }

    override fun updateListWith(
        oldList: List<Channel>,
        newItems: Set<Note>,
    ): List<Channel> {
        val fifteenMinsAgo = limitTime()

        val revisedOldList =
            oldList.filter { channel ->
                val channelTime = (channel as? LiveActivitiesChannel)?.info?.createdAt
                (channelTime == null || channelTime > fifteenMinsAgo) ||
                    (channel.lastNote?.createdAt() ?: 0L) > fifteenMinsAgo
            }

        val newItemsToBeAdded = applyFilter(newItems)
        return if (newItemsToBeAdded.isNotEmpty()) {
            val channelsToAdd =
                newItemsToBeAdded
                    .mapNotNull {
                        val event = it.event
                        when (event) {
                            is EphemeralChatEvent -> {
                                event.roomId()?.let { roomId ->
                                    LocalCache.getEphemeralChatChannelIfExists(roomId)
                                }
                            }

                            is GeohashChatEvent -> {
                                event.geohash()?.let { geohash ->
                                    LocalCache.getGeohashChannelIfExists(geohash)
                                }
                            }

                            is LiveActivitiesChatMessageEvent -> {
                                event.activityAddress()?.let { addr ->
                                    LocalCache.getLiveActivityChannelIfExists(addr)
                                }
                            }

                            // Audio-room presence (kind-10312) drops
                            // into the same `liveChatChannels` cache —
                            // see consume(MeetingRoomPresenceEvent).
                            // Resolve the channel via the room's
                            // address from the `["a", ...]` tag.
                            is MeetingRoomPresenceEvent -> {
                                event.interactiveRoom()?.address?.let { addr ->
                                    LocalCache.getLiveActivityChannelIfExists(addr)
                                }
                            }

                            else -> {
                                null
                            }
                        }
                    }

            val newList = revisedOldList.toSet() + channelsToAdd
            sort(newList).take(limit())
        } else {
            revisedOldList
        }
    }

    private fun applyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams = buildFilterParams(account)

        return collection.filterTo(HashSet()) {
            acceptableChatEvent(it, filterParams, limitTime())
        }
    }

    private fun acceptableChatEvent(
        note: Note,
        filterParams: FilterByListParams,
        timeLimit: Long,
    ): Boolean {
        val createdAt = note.createdAt() ?: return false
        val noteEvent = note.event

        // Geohash chat is anonymous and location-scoped — surface any recent message, not follow-filtered.
        if (noteEvent is GeohashChatEvent) return createdAt > timeLimit

        if (noteEvent is LiveActivitiesChatMessageEvent) {
            val activity = noteEvent.activityAddress() ?: return false

            // Two flavors of "live" share the same kind-1311 chat
            // channel: streaming (kind-30311, status=LIVE) and audio
            // rooms (kind-30312, status=OPEN/PRIVATE). The chat
            // surfaces in liveChatChannels for both because
            // consume(LiveActivitiesChatMessageEvent) just keys on
            // the a-tag — but the channel.info field only ever
            // gets populated for streaming. Read the audio-room
            // status straight off the addressable instead so the
            // bubble surfaces while a follow is chatting in a
            // currently-open kind-30312.
            when (activity.kind) {
                LiveActivitiesEvent.KIND -> {
                    val streamChannel = LocalCache.getLiveActivityChannelIfExists(activity) ?: return false
                    if (streamChannel.info?.status() != StatusTag.STATUS.LIVE) return false
                }

                MeetingSpaceEvent.KIND -> {
                    if (!isMeetingSpaceLive(activity)) return false
                }

                else -> {
                    return false
                }
            }
        }

        // Audio-room presence (kind-10312) — surface the bubble when a
        // follow is currently broadcasting in an OPEN/PRIVATE room.
        // Companion to the chat-driven path above; both signals point
        // at the same kind-30312 channel and either is enough to pull
        // the room into the bubble row.
        if (noteEvent is MeetingRoomPresenceEvent) {
            val roomAddress = noteEvent.interactiveRoom()?.address ?: return false
            if (roomAddress.kind != MeetingSpaceEvent.KIND) return false
            // Only follows actively pushing audio. Hand-raise / mute /
            // pure-listener presences are noise for the home bubble —
            // they'd flood it with everyone in the room every 30 s.
            if (noteEvent.publishing() != true) return false
            if (!isMeetingSpaceLive(roomAddress)) return false
        }

        return (
            noteEvent is EphemeralChatEvent ||
                noteEvent is LiveActivitiesChatMessageEvent ||
                noteEvent is MeetingRoomPresenceEvent
        ) &&
            createdAt > timeLimit &&
            filterParams.match(noteEvent, note.relays)
    }

    private fun isMeetingSpaceLive(roomAddress: com.vitorpamplona.quartz.nip01Core.core.Address): Boolean {
        val room =
            LocalCache.getAddressableNoteIfExists(roomAddress)?.event as? MeetingSpaceEvent
                ?: return false
        val status = room.status()
        return status == MeetingSpaceStatusTag.STATUS.LIVE ||
            status == MeetingSpaceStatusTag.STATUS.PRIVATE
    }

    fun sort(collection: Set<Channel>): List<Channel> {
        val topFilter = account.liveHomeFollowLists.value
        val topFilterAuthors =
            when (topFilter) {
                is AuthorsByOutboxTopNavFilter -> topFilter.authors
                is MutedAuthorsByOutboxTopNavFilter -> topFilter.authors
                is AllFollowsByOutboxTopNavFilter -> topFilter.authors
                is SingleCommunityTopNavFilter -> topFilter.authors
                is AuthorsByProxyTopNavFilter -> topFilter.authors
                is MutedAuthorsByProxyTopNavFilter -> topFilter.authors
                is AllFollowsByProxyTopNavFilter -> topFilter.authors
                else -> null
            }

        val followingKeySet = topFilterAuthors ?: account.kind3FollowList.flow.value.authors

        val followCounts =
            collection.associateWith { followsThatParticipateOn(it, followingKeySet) }

        return collection.sortedWith(
            compareByDescending<Channel> { followCounts[it] }
                .thenByDescending { it.lastNote?.createdAt() ?: 0L }
                .thenBy { it.hashCode() },
        )
    }

    fun followsThatParticipateOn(
        channel: Channel,
        followingSet: Set<HexKey>?,
    ): Int {
        var count = 0

        channel.notes.forEach { _, value ->
            val author = value.author
            if (author != null && (followingSet == null || author.pubkeyHex in followingSet)) {
                count++
            }
        }

        // Audio-room presence is indexed separately from `notes`. Add
        // its author count so audio-room hosts/speakers still factor
        // into the follow-participation sort even when they haven't
        // chatted in the room.
        if (channel is LiveActivitiesChannel) {
            channel.presenceNotes.forEach { authorHex, _ ->
                if (followingSet == null || authorHex in followingSet) {
                    count++
                }
            }
        }

        return count
    }
}
