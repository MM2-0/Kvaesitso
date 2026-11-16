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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.video

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.pictures.PictureCardCompose
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.shorts.VideoCardCompose
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.video.datasource.VideoFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileMetadataEvent

@Composable
fun VideoScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    attachments: List<String> = emptyList(),
    message: String? = null,
) {
    VideoScreen(
        accountViewModel.feedStates.videoFeed,
        accountViewModel,
        nav,
        attachments,
        message,
    )
}

@Composable
fun VideoScreen(
    videoFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
    attachments: List<String> = emptyList(),
    message: String? = null,
) {
    WatchLifecycleAndUpdateModel(videoFeedContentState)
    WatchAccountForVideoScreen(videoFeedContentState = videoFeedContentState, accountViewModel = accountViewModel)
    VideoFilterAssemblerSubscription(accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            StoriesTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.Video(), nav, accountViewModel) { route ->
                if (route is Route.Video) {
                    videoFeedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewVideoFeedButton(accountViewModel, nav, videoFeedContentState::sendToTop, attachments, message)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RenderFeed(
            videoFeedContentState = videoFeedContentState,
            scrollKey = ScrollStateKeys.VIDEO_SCREEN,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun WatchAccountForVideoScreen(
    videoFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveStoriesFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        videoFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}

@Composable
private fun RenderFeed(
    videoFeedContentState: FeedContentState,
    scrollKey: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RefresheableBox(invalidateableContent = videoFeedContentState) {
        SaveableFeedContentState(videoFeedContentState, scrollStateKey = scrollKey) { listState ->
            RenderFeedContentState(
                feedContentState = videoFeedContentState,
                accountViewModel = accountViewModel,
                listState = listState,
                nav = nav,
                routeForLastRead = "VideosFeed",
                onLoaded = { loaded ->
                    VideoFeedLoaded(
                        loaded = loaded,
                        listState = listState,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                },
            )
        }
    }
}

@Composable
fun VideoFeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        itemsIndexed(
            items.list,
            key = { _, item -> item.idHex },
            contentType = { _, item -> item.event?.kind ?: -1 },
        ) { _, item ->
            when {
                item.event is PictureEvent -> {
                    PictureCardCompose(
                        baseNote = item,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )

                    HorizontalDivider(
                        thickness = DividerThickness,
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                item.event is VideoEvent -> {
                    VideoCardCompose(item, accountViewModel, nav)

                    HorizontalDivider(
                        thickness = DividerThickness,
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                item.event is FileMetadataEvent -> {
                    FileMetadataCardCompose(item, accountViewModel, nav)

                    HorizontalDivider(
                        thickness = DividerThickness,
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
