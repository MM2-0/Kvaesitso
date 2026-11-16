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
package com.vitorpamplona.amethyst.commons.relayClient.topNavFeeds

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.relay.RelayTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.relayClient.badges.makeBadgesFilter
import com.vitorpamplona.amethyst.commons.relayClient.calendars.makeCalendarsFilter
import com.vitorpamplona.amethyst.commons.relayClient.discover.nip23LongForm.makeLongFormFilter
import com.vitorpamplona.amethyst.commons.relayClient.discover.nip28Chats.makePublicChatsFilter
import com.vitorpamplona.amethyst.commons.relayClient.discover.nip51FollowSets.makeFollowSetsFilter
import com.vitorpamplona.amethyst.commons.relayClient.discover.nip53LiveActivities.makeLiveActivitiesFilter
import com.vitorpamplona.amethyst.commons.relayClient.discover.nip72Communities.makeCommunitiesFilter
import com.vitorpamplona.amethyst.commons.relayClient.discover.nip90DVMs.makeContentDVMsFilter
import com.vitorpamplona.amethyst.commons.relayClient.discover.nip99Classifieds.makeClassifiedsFilter
import com.vitorpamplona.amethyst.commons.relayClient.emojipacks.makeBrowseEmojiSetsFilter
import com.vitorpamplona.amethyst.commons.relayClient.geocaches.makeGeocachesFilter
import com.vitorpamplona.amethyst.commons.relayClient.gitRepositories.makeGitRepositoriesFilter
import com.vitorpamplona.amethyst.commons.relayClient.highlights.makeHighlightsFilter
import com.vitorpamplona.amethyst.commons.relayClient.longs.makeLongsFilter
import com.vitorpamplona.amethyst.commons.relayClient.music.makeMusicPlaylistsFilter
import com.vitorpamplona.amethyst.commons.relayClient.music.makeMusicTracksFilter
import com.vitorpamplona.amethyst.commons.relayClient.napplets.makeNappletsFilter
import com.vitorpamplona.amethyst.commons.relayClient.nests.makeNestsFilter
import com.vitorpamplona.amethyst.commons.relayClient.nsites.makeNsitesFilter
import com.vitorpamplona.amethyst.commons.relayClient.pictures.makePicturesFilter
import com.vitorpamplona.amethyst.commons.relayClient.podcasts.makePodcastEpisodesFilter
import com.vitorpamplona.amethyst.commons.relayClient.podcasts.makePodcastsFilter
import com.vitorpamplona.amethyst.commons.relayClient.polls.makePollsFilter
import com.vitorpamplona.amethyst.commons.relayClient.shorts.makeShortsFilter
import com.vitorpamplona.amethyst.commons.relayClient.softwareapps.makeSoftwareAppsFilter
import com.vitorpamplona.amethyst.commons.relayClient.video.makePictureAndVideoFilter
import com.vitorpamplona.amethyst.commons.relayClient.workouts.makeWorkoutsFilter
import com.vitorpamplona.amethyst.commons.relays.MutableTime
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A single relay picked in a feed's top bar ([RelayTopNavPerRelayFilterSet]). Each builder has
 * its own case for it; before that, every one of these feeds fell through to `else ->
 * emptyList()`, sent no REQ and stayed blank.
 */
class SingleRelayFeedFilterTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://nos.lol")!!
    private val selection = RelayTopNavPerRelayFilterSet(relay)

    /**
     * Requests that look back a fixed few minutes on purpose, to tell what is live right now:
     * Nests' presence probe and the DVM heartbeat. They are not feed windows.
     */
    private val livenessKinds = setOf(listOf(MeetingRoomPresenceEvent.KIND), listOf(DvmHeartbeatEvent.KIND))

    private val makers: List<Pair<String, TopNavFeedFilterMaker>> =
        listOf(
            "makeBadgesFilter" to ::makeBadgesFilter,
            "makeBrowseEmojiSetsFilter" to ::makeBrowseEmojiSetsFilter,
            "makeCalendarsFilter" to ::makeCalendarsFilter,
            "makeClassifiedsFilter" to ::makeClassifiedsFilter,
            "makeCommunitiesFilter" to ::makeCommunitiesFilter,
            "makeFollowSetsFilter" to ::makeFollowSetsFilter,
            "makeGeocachesFilter" to ::makeGeocachesFilter,
            "makeGitRepositoriesFilter" to ::makeGitRepositoriesFilter,
            "makeHighlightsFilter" to ::makeHighlightsFilter,
            "makeLiveActivitiesFilter" to ::makeLiveActivitiesFilter,
            "makeLongFormFilter" to ::makeLongFormFilter,
            "makeLongsFilter" to ::makeLongsFilter,
            "makeMusicPlaylistsFilter" to ::makeMusicPlaylistsFilter,
            "makeMusicTracksFilter" to ::makeMusicTracksFilter,
            "makeNappletsFilter" to ::makeNappletsFilter,
            "makeNestsFilter" to ::makeNestsFilter,
            "makeNsitesFilter" to ::makeNsitesFilter,
            "makePictureAndVideoFilter" to ::makePictureAndVideoFilter,
            "makePicturesFilter" to ::makePicturesFilter,
            "makePodcastEpisodesFilter" to ::makePodcastEpisodesFilter,
            "makePodcastsFilter" to ::makePodcastsFilter,
            "makePollsFilter" to ::makePollsFilter,
            "makePublicChatsFilter" to ::makePublicChatsFilter,
            "makeShortsFilter" to ::makeShortsFilter,
            "makeWorkoutsFilter" to ::makeWorkoutsFilter,
            "makeContentDVMsFilter" to ::makeContentDVMsFilter,
            "makeSoftwareAppsFilter" to { s, since, d -> makeSoftwareAppsFilter(s, since, d) },
        )

    @Test
    fun everyTopNavFeedAsksThePickedRelay() {
        makers.forEach { (name, make) ->
            val filters = make(selection, null, null)
            assertTrue(filters.isNotEmpty(), "$name sent no REQ for a single relay")
            assertTrue(filters.all { it.relay == relay }, "$name asked a relay that was not picked")
        }
    }

    @Test
    fun noFeedAsksTheSameThingTwice() {
        makers.forEach { (name, make) ->
            val filters = make(selection, null, null).map { it.filter.toJson() }
            assertEquals(filters.size, filters.toSet().size, "$name sent a duplicate REQ filter")
        }
    }

    @Test
    fun aSingleRelayIsNotCutToARecentWindow() {
        // vitor.nostr1.com held 39 pictures, none from the last week: Global's one-week window
        // left its feed empty.
        makers.forEach { (name, make) ->
            val late = make(selection, null, null).filter { it.filter.since != null && it.filter.kinds !in livenessKinds }
            assertTrue(late.isEmpty(), "$name cut a single relay to since=${late.map { it.filter.since }}")
        }
    }

    @Test
    fun aSingleRelayResumesFromItsOwnEose() {
        val since = mutableMapOf(relay to MutableTime(5000L))

        makers.forEach { (name, make) ->
            val sinces = make(selection, since, null).filter { it.filter.kinds !in livenessKinds }.map { it.filter.since }
            assertEquals(setOf(5000L), sinces.toSet(), "$name did not resume from the relay's EOSE")
        }
    }

    @Test
    fun aBlockedRelayIsNotAskedForApps() {
        assertTrue(makeSoftwareAppsFilter(selection, null, null, blockedRelays = setOf(relay)).isEmpty())
    }
}
