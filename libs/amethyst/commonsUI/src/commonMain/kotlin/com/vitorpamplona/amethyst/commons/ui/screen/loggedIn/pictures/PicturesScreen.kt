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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.pictures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.pictures.datasource.PicturesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun PicturesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    attachments: List<String> = emptyList(),
    message: String? = null,
) {
    PicturesScreen(
        picturesFeedContentState = accountViewModel.feedStates.picturesFeed,
        accountViewModel = accountViewModel,
        nav = nav,
        attachments = attachments,
        message = message,
    )
}

@Composable
fun PicturesScreen(
    picturesFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
    attachments: List<String> = emptyList(),
    message: String? = null,
) {
    WatchLifecycleAndUpdateModel(picturesFeedContentState)
    WatchAccountForPicturesScreen(picturesFeedContentState = picturesFeedContentState, accountViewModel = accountViewModel)
    PicturesFilterAssemblerSubscription(accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            PicturesTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.Pictures(), nav, accountViewModel) { route ->
                if (route is Route.Pictures) {
                    picturesFeedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewPictureButton(accountViewModel, nav, picturesFeedContentState::sendToTop, attachments, message)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(picturesFeedContentState, true) {
            SaveableFeedContentState(picturesFeedContentState, scrollStateKey = ScrollStateKeys.PICTURES_SCREEN) { listState ->
                RenderFeedContentState(
                    feedContentState = picturesFeedContentState,
                    accountViewModel = accountViewModel,
                    listState = listState,
                    nav = nav,
                    routeForLastRead = "PicturesFeed",
                    onLoaded = { loaded ->
                        PictureFeedLoaded(
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
}

@Composable
fun WatchAccountForPicturesScreen(
    picturesFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.livePicturesFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers =
        accountViewModel.account.hiddenUsers.flow
            .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        picturesFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}
