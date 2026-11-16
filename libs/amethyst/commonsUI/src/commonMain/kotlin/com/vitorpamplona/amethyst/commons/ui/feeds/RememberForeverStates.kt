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
package com.vitorpamplona.amethyst.commons.ui.feeds

import androidx.collection.LruCache
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.roundToInt

// Bounded: a few keys are per note (a community's or follow pack's pager), so a plain map would
// keep one entry for every one ever opened.
private val savedScrollStates = LruCache<String, ScrollState>(200)

private data class ScrollState(
    val index: Int,
    val scrollOffsetFraction: Float,
)

object ScrollStateKeys {
    const val NOTIFICATION_SCREEN = "NotificationsFeed"
    const val NOTIFICATION_SIDE_PANEL = "NotificationsSidePanel"
    const val NOTIFICATION_SIDE_PANEL_FOLLOWING = "NotificationsSidePanelFollowing"
    const val NOTIFICATION_FOLLOWING = "NotificationsFollowingFeed"
    const val NOTIFICATION_EVERYONE = "NotificationsEveryoneFeed"
    const val VIDEO_SCREEN = "VideoFeed"
    const val HOME_FOLLOWS = "HomeFollowsFeed"
    const val HOME_REPLIES = "HomeFollowsRepliesFeed"
    const val HOME_EVERYTHING = "HomeFollowsEverythingFeed"
    const val MESSAGES_KNOWN = "MessagesKnown"
    const val MESSAGES_NEW = "MessagesNew"
    const val PROFILE_GALLERY = "ProfileGalleryFeed"

    const val DRAFTS = "DraftsFeed"

    const val DISCOVER_FOLLOWS = "DiscoverFollowSetsFeed"
    const val DISCOVER_READS = "DiscoverReadsFeed"
    const val DISCOVER_CONTENT = "DiscoverDiscoverContentFeed"
    const val DISCOVER_MARKETPLACE = "DiscoverMarketplaceFeed"
    const val DISCOVER_LIVE = "DiscoverLiveFeed"
    const val DISCOVER_COMMUNITY = "DiscoverCommunitiesFeed"
    const val DISCOVER_CHATS = "DiscoverChatsFeed"

    const val POLLS_SCREEN = "PollsFeed"
    const val POLLS_OPEN = "PollsOpenFeed"
    const val POLLS_CLOSED = "PollsClosedFeed"
    const val CHESS_LOBBY = "ChessLobbyFeed"
    const val BADGES_SCREEN = "BadgesFeed"
    const val BROWSE_EMOJI_SETS_SCREEN = "BrowseEmojiSetsFeed"
    const val COMMUNITIES_LIST = "CommunitiesListFeed"
    const val PICTURES_SCREEN = "PicturesFeed"
    const val WORKOUTS_SCREEN = "WorkoutsFeed"
    const val GIT_REPOSITORIES_SCREEN = "GitRepositoriesFeed"
    const val NIPS_SCREEN = "NipsFeed"
    const val HIGHLIGHTS_SCREEN = "HighlightsFeed"
    const val RELAY_GROUPS_DISCOVERY_SCREEN = "RelayGroupsDiscoveryFeed"
    const val CALENDARS_SCREEN = "CalendarsFeed"
    const val CALENDAR_COLLECTIONS_SCREEN = "CalendarCollectionsFeed"
    const val PRODUCTS_SCREEN = "ProductsFeed"
    const val GEOCACHES_NEARBY = "GeocachesNearbyFeed"
    const val GEOCACHES_HUNTS = "GeocacheHuntsFeed"
    const val GEOCACHES_FINDS = "GeocacheFindsFeed"
    const val GEOCACHES_MINE = "GeocacheMineFeed"
    const val SHORTS_SCREEN = "ShortsFeed"
    const val PUBLIC_CHATS_SCREEN = "PublicChatsFeed"
    const val FOLLOW_PACKS_SCREEN = "FollowPacksFeed"
    const val LIVE_STREAMS_SCREEN = "LiveStreamsFeed"
    const val NESTS_SCREEN = "NestsFeed"
    const val LONGS_SCREEN = "LongsFeed"
    const val ARTICLES_SCREEN = "ArticlesFeed"
    const val MUSIC_TRACKS_SCREEN = "MusicTracksFeed"
    const val MUSIC_PLAYLISTS_SCREEN = "MusicPlaylistsFeed"
    const val PODCAST_EPISODES_SCREEN = "PodcastEpisodesFeed"
    const val PODCASTS_SCREEN = "PodcastsFeed"

    const val SEARCH_SCREEN = "SearchFeed"

    const val WEB_BOOKMARKS = "WebBookmarksFeed"
}

object PagerStateKeys {
    const val HOME_SCREEN = "PagerHome"
    const val DISCOVER_SCREEN = "PagerDiscover"
    const val POLLS_SCREEN = "PagerPolls"
    const val GEOCACHES_SCREEN = "PagerGeocaches"
    const val NOTIFICATION_SCREEN = "PagerNotification"
}

@Composable
fun rememberForeverLazyGridState(
    key: String,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
): LazyGridState {
    val scrollState =
        rememberSaveable(saver = LazyGridState.Saver) {
            val savedValue = savedScrollStates[key]
            val savedIndex = savedValue?.index ?: initialFirstVisibleItemIndex
            val savedOffset =
                savedValue?.scrollOffsetFraction ?: initialFirstVisibleItemScrollOffset.toFloat()
            LazyGridState(
                savedIndex,
                savedOffset.roundToInt(),
            )
        }
    DisposableEffect(scrollState) {
        onDispose {
            val lastIndex = scrollState.firstVisibleItemIndex
            val lastOffset = scrollState.firstVisibleItemScrollOffset
            savedScrollStates.put(key, ScrollState(lastIndex, lastOffset.toFloat()))
        }
    }
    return scrollState
}

@Composable
fun rememberForeverLazyStaggeredGridState(
    key: String,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
): LazyStaggeredGridState {
    val scrollState =
        rememberSaveable(saver = LazyStaggeredGridState.Saver) {
            val savedValue = savedScrollStates[key]
            val savedIndex = savedValue?.index ?: initialFirstVisibleItemIndex
            val savedOffset =
                savedValue?.scrollOffsetFraction ?: initialFirstVisibleItemScrollOffset.toFloat()
            LazyStaggeredGridState(
                savedIndex,
                savedOffset.roundToInt(),
            )
        }
    DisposableEffect(scrollState) {
        onDispose {
            val lastIndex = scrollState.firstVisibleItemIndex
            val lastOffset = scrollState.firstVisibleItemScrollOffset
            savedScrollStates.put(key, ScrollState(lastIndex, lastOffset.toFloat()))
        }
    }
    return scrollState
}

@Composable
fun rememberForeverLazyListState(
    key: String,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
): LazyListState {
    val scrollState =
        rememberSaveable(saver = LazyListState.Saver) {
            val savedValue = savedScrollStates[key]
            val savedIndex = savedValue?.index ?: initialFirstVisibleItemIndex
            val savedOffset =
                savedValue?.scrollOffsetFraction ?: initialFirstVisibleItemScrollOffset.toFloat()
            LazyListState(
                savedIndex,
                savedOffset.roundToInt(),
            )
        }
    DisposableEffect(scrollState) {
        onDispose {
            val lastIndex = scrollState.firstVisibleItemIndex
            val lastOffset = scrollState.firstVisibleItemScrollOffset
            savedScrollStates.put(key, ScrollState(lastIndex, lastOffset.toFloat()))
        }
    }
    return scrollState
}

@Composable
fun rememberForeverPagerState(
    key: String,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Float = 0.0f,
    pageCount: () -> Int,
): PagerState =
    rememberForeverPagerState(key, initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset, pageCount) { initialPage, initialPageOffsetFraction, pageCount ->
        rememberPagerState(initialPage, initialPageOffsetFraction, pageCount)
    }

@Composable
fun rememberForeverPagerState(
    key: String,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Float = 0.0f,
    pageCount: () -> Int,
    rememberPagerStateFunction: @Composable (
        initialPage: Int,
        initialPageOffsetFraction: Float,
        pageCount: () -> Int,
    ) -> PagerState,
): PagerState {
    val savedValue = savedScrollStates[key]
    val savedIndex = savedValue?.index ?: initialFirstVisibleItemIndex
    val savedOffset = savedValue?.scrollOffsetFraction ?: initialFirstVisibleItemScrollOffset

    val scrollState =
        rememberPagerStateFunction(
            savedIndex,
            savedOffset,
            pageCount,
        )

    DisposableEffect(scrollState) {
        onDispose {
            val lastIndex = scrollState.currentPage
            val lastOffset = scrollState.currentPageOffsetFraction
            savedScrollStates.put(key, ScrollState(lastIndex, lastOffset))
        }
    }

    return scrollState
}
