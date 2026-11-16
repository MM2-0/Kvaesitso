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
package com.vitorpamplona.amethyst.commons.nests.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Drawer feed for NIP-53 kind 30312 (Interactive Rooms / audio spaces).
 *
 * Shares LocalCache.liveChatChannels with the Live Streams feed,
 * narrowed to MeetingSpaceEvent (30312) so the audio-room surface
 * is independent of video live streams. Kind 30313 (NIP-53 meetings)
 * intentionally does NOT surface here — the room concept and the
 * meeting concept render on the standard NoteCompose / thread paths
 * outside the Nests drawer.
 */
class NestsFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList().code

    override fun limit() = 50

    fun followList(): TopFilter = account.settings.defaultNestsFollowList.value

    private fun TopFilter.isMuteList() = this is TopFilter.MuteList

    private fun TopFilter.isBlockList() = this is TopFilter.PeopleList && this.address == account.blockPeopleList.getBlockListAddress()

    private fun TopFilter.wantsToSeeNegativeStuff() = isMuteList() || isBlockList()

    override fun showHiddenKey(): Boolean = followList().wantsToSeeNegativeStuff()

    override fun feed(): List<Note> {
        val allRoomNotes = LocalCache.liveChatChannels.mapNotNull { _, channel -> LocalCache.getAddressableNoteIfExists(channel.address) }
        return sort(innerApplyFilter(allRoomNotes))
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val topFilter = account.liveNestsFollowLists.value
        val filterParams =
            FilterByListParams.create(
                followLists = topFilter,
                hiddenUsers = account.hiddenUsers.flow.value,
            )
        val expandableAuthors = followsAuthorsForExpansion(topFilter)
        val now = TimeUtils.now()
        val presenceCutoff = now - PRESENCE_FRESHNESS_WINDOW_SECONDS
        val endedHistoryCutoff = now - ENDED_HISTORY_WINDOW_SECONDS

        return collection.filterTo(HashSet()) {
            val noteEvent = it.event as? MeetingSpaceEvent ?: return@filterTo false
            if (!hasMinimumNestFields(noteEvent)) return@filterTo false
            if (!isWithinPlannedWindow(noteEvent, now)) return@filterTo false
            if (!isWithinEndedHistoryWindow(noteEvent, endedHistoryCutoff)) return@filterTo false
            if (!hasFreshSpeakers(noteEvent, presenceCutoff)) return@filterTo false

            if (filterParams.match(noteEvent, it.relays)) return@filterTo true

            // p-tag follow expansion: surface rooms whose host fails the
            // top filter but where any p-tagged speaker is in the user's
            // follows. Mirrors NostrNests' "Following" tab. Limited to
            // follow-style top filters via [followsAuthorsForExpansion]
            // so mute/relay/global filters keep their existing behavior.
            val authors = expandableAuthors ?: return@filterTo false
            if (!filterParams.isNotInTheFuture(noteEvent)) return@filterTo false
            if (!filterParams.isHiddenList && !filterParams.isNotHidden(noteEvent.pubKey)) return@filterTo false
            if (filterParams.hasExcessiveHashtags(noteEvent)) return@filterTo false
            noteEvent.participantsIntersect(authors)
        }
    }

    /**
     * EGG-01 rule 2: a kind:30312 with any of `room`, `status`, `service`,
     * `endpoint` missing MUST be treated as un-joinable. Apply that as a
     * feed-filter gate so rooms-with-no-content (the d-tag-only events
     * relays sometimes leak) don't render an empty card with a broken
     * Join button.
     *
     * Also rejects non-HTTPS service/endpoint URLs — mirrors the
     * NostrNests guard that drops legacy `wss+livekit://` streaming URLs
     * from first-generation servers a moq-lite client cannot reach.
     *
     * Closed rooms with all four fields ARE rendered — they may carry a
     * recording (EGG-11) and the listen-back card is the audience's
     * only path to that audio post-close.
     */
    private fun hasMinimumNestFields(event: MeetingSpaceEvent): Boolean {
        val service = event.service()
        val endpoint = event.endpoint()
        return !event.room().isNullOrBlank() &&
            event.status() != null &&
            !service.isNullOrBlank() &&
            !endpoint.isNullOrBlank() &&
            service.startsWith("https://") &&
            endpoint.startsWith("https://")
    }

    /**
     * For PLANNED rooms only: drop entries whose `starts` time is more
     * than [PLANNED_STALE_SECONDS] in the past (host never went live)
     * or more than [PLANNED_MAX_FUTURE_SECONDS] in the future (likely
     * spam or a mis-set timestamp). Mirrors the NostrNests planned
     * bucket window. Other statuses pass through.
     */
    private fun isWithinPlannedWindow(
        event: MeetingSpaceEvent,
        now: Long,
    ): Boolean {
        if (event.status() != StatusTag.STATUS.PLANNED) return true
        val starts = event.starts() ?: return true
        return starts > now - PLANNED_STALE_SECONDS &&
            starts < now + PLANNED_MAX_FUTURE_SECONDS
    }

    /**
     * For ENDED rooms only: cap the historical bucket so the feed
     * doesn't drown in months-old recordings. Anything within
     * [ENDED_HISTORY_WINDOW_SECONDS] of `now` (compared against the
     * room's most recent kind-30312 createdAt — the closest signal
     * we have to "ended at") passes; older entries drop. Other
     * statuses pass through.
     */
    private fun isWithinEndedHistoryWindow(
        event: MeetingSpaceEvent,
        endedHistoryCutoff: Long,
    ): Boolean {
        val status = event.checkStatus(event.status())
        if (status != StatusTag.STATUS.ENDED) return true
        return event.createdAt >= endedHistoryCutoff
    }

    /**
     * Drop OPEN/PRIVATE rooms whose live speaker slate is empty. A room
     * with no fresh kind-10312 presence carrying `onstage=1` published
     * in the last [PRESENCE_FRESHNESS_WINDOW_SECONDS] has no one left
     * on stage — even if the kind-30312 status still says `live`,
     * there is nothing to listen to and the room has effectively
     * ended. Mirrors (and tightens) the NostrNests lobby gate.
     *
     * Brand-new rooms get a created-at grace so they surface before
     * the first speaker heartbeat arrives. CLOSED rooms bypass this
     * gate (they may carry a recording — EGG-11), as do PLANNED rooms
     * (not started yet, no presence expected).
     *
     * Reads the room's [LiveActivitiesChannel.presenceNotes] index
     * (keyed by author, populated by
     * `LocalCache.consume(MeetingRoomPresenceEvent)`) so the scan is
     * O(speakers) instead of O(all chat + zaps + presence).
     */
    private fun hasFreshSpeakers(
        event: MeetingSpaceEvent,
        presenceCutoff: Long,
    ): Boolean {
        val status = event.status()
        if (status != StatusTag.STATUS.LIVE && status != StatusTag.STATUS.PRIVATE) return true
        if (event.createdAt > presenceCutoff) return true

        val channel = LocalCache.getLiveActivityChannelIfExists(event.address()) ?: return false
        var hasSpeaker = false
        channel.presenceNotes.forEach { _, note ->
            if (hasSpeaker) return@forEach
            val e = note.event
            if (e is MeetingRoomPresenceEvent && e.createdAt > presenceCutoff && e.onstage() == true) {
                hasSpeaker = true
            }
        }
        return hasSpeaker
    }

    /**
     * Author set used for p-tag follow expansion. Returns null for
     * top filters where expansion is not meaningful (mute/block, the
     * relay filter, global) so we don't accidentally widen feeds the
     * user explicitly narrowed to a specific axis.
     */
    private fun followsAuthorsForExpansion(topFilter: IFeedTopNavFilter?): Set<HexKey>? =
        when (topFilter) {
            is AuthorsByOutboxTopNavFilter -> topFilter.authors
            is AuthorsByProxyTopNavFilter -> topFilter.authors
            is AllFollowsByOutboxTopNavFilter -> topFilter.authors
            is AllFollowsByProxyTopNavFilter -> topFilter.authors
            is SingleCommunityTopNavFilter -> topFilter.authors
            else -> null
        }

    /**
     * Three-bucket sort: LIVE rooms first (ordered by how many of the
     * user's follows are participating, then by total participants),
     * SCHEDULED rooms next (soonest start first), ENDED rooms last
     * (most-recently-ended first). The screen relies on this strict
     * ordering to walk the list and inject sticky section headers
     * without re-sorting.
     */
    override fun sort(items: Set<Note>): List<Note> {
        val topFilter = account.liveNestsFollowLists.value
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

        val counter = ParticipantListBuilder()
        val live = ArrayList<Note>()
        val scheduled = ArrayList<Note>()
        val ended = ArrayList<Note>()

        items.forEach {
            when (bucketOf(it.event as? MeetingSpaceEvent)) {
                NestBucket.LIVE -> live.add(it)
                NestBucket.SCHEDULED -> scheduled.add(it)
                NestBucket.ENDED -> ended.add(it)
            }
        }

        // LIVE: follows-participating DESC, total participants DESC,
        // createdAt DESC, idHex (stable tiebreak).
        val followsParticipating = live.associateWith { counter.countFollowsThatParticipateOn(it, followingKeySet) }
        val totalParticipating = live.associateWith { counter.countFollowsThatParticipateOn(it, null) }
        live.sortWith(
            compareByDescending<Note> { followsParticipating[it] ?: 0 }
                .thenByDescending { totalParticipating[it] ?: 0 }
                .thenByDescending { it.createdAt() ?: 0 }
                .thenBy { it.idHex },
        )

        // SCHEDULED: starts ASC (soonest first); rooms missing a starts
        // tag fall to the end.
        scheduled.sortWith(
            compareBy<Note> { (it.event as? MeetingSpaceEvent)?.starts() ?: Long.MAX_VALUE }
                .thenBy { it.idHex },
        )

        // ENDED: createdAt DESC (most-recently-ended first; the kind
        // 30312 createdAt advances when the host republishes with
        // status=ended, so it's our closest "ended at" signal).
        ended.sortWith(
            compareByDescending<Note> { it.createdAt() ?: 0 }
                .thenBy { it.idHex },
        )

        return live + scheduled + ended
    }

    enum class NestBucket {
        LIVE,
        SCHEDULED,
        ENDED,
    }

    companion object Companion {
        /**
         * Window inside which a kind-10312 presence event still counts
         * an OPEN room as live. Same 10-minute cutoff NostrNests uses
         * in its lobby. Speakers heartbeat every ~60 s, so a 10-minute
         * window tolerates up to ~9 missed heartbeats before the room
         * is hidden as crashed.
         */
        private const val PRESENCE_FRESHNESS_WINDOW_SECONDS = 10L * 60L

        /**
         * PLANNED rooms whose `starts` is more than 1 h in the past
         * are considered stale: the host never opened the room.
         */
        private const val PLANNED_STALE_SECONDS = 60L * 60L

        /**
         * PLANNED rooms whose `starts` is more than 30 d in the
         * future are likely spam or mis-set timestamps.
         */
        private const val PLANNED_MAX_FUTURE_SECONDS = 30L * 24L * 60L * 60L

        /**
         * ENDED rooms older than 7 d drop out of the historic bucket.
         * The audience can still reach a recording via direct link;
         * the feed just stops surfacing it.
         */
        private const val ENDED_HISTORY_WINDOW_SECONDS = 7L * 24L * 60L * 60L

        /**
         * Maps a meeting-space event to its display bucket. PRIVATE
         * rooms are live (the audience can hear them once they get
         * a join token); only PLANNED rooms go to SCHEDULED. Any
         * unknown / stale-promoted status falls through to ENDED.
         */
        fun bucketOf(event: MeetingSpaceEvent?): NestBucket =
            when (event?.checkStatus(event.status())) {
                StatusTag.STATUS.LIVE,
                StatusTag.STATUS.PRIVATE,
                -> NestBucket.LIVE

                StatusTag.STATUS.PLANNED -> NestBucket.SCHEDULED

                else -> NestBucket.ENDED
            }
    }
}
