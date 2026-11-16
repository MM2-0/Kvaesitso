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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geohash

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.geohash.dal.GeoHashFeedViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.profile.ui.FollowButton
import com.vitorpamplona.amethyst.commons.profile.ui.UnfollowButton
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserIsFollowingGeohash
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geohash_chat_open
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_follow
import com.vitorpamplona.amethyst.commons.resources.login_with_a_private_key_to_be_able_to_unfollow
import com.vitorpamplona.amethyst.commons.resources.read_only_user
import com.vitorpamplona.amethyst.commons.search.SearchSeed
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SearchTopBarAction
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geohash.datasource.GeoHashFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

@Composable
fun GeoHashScreen(
    tag: Route.Geohash,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (tag.geohash.isEmpty()) return

    PrepareViewModelsGeoHashScreen(tag, accountViewModel, nav)
}

@Suppress("StateFlowValueCalledInComposition")
@Composable
fun PrepareViewModelsGeoHashScreen(
    tag: Route.Geohash,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val geohashViewModel: GeoHashFeedViewModel =
        rememberViewModel(
            key = tag.geohash + "GeoHashFeedViewModel",
            factory =
                GeoHashFeedViewModel.Factory(
                    tag.geohash,
                    accountViewModel.account.followOutboxesOrProxy.flow.value,
                    accountViewModel.account,
                ),
        )

    GeoHashScreen(tag, geohashViewModel, accountViewModel, nav)
}

@Composable
fun GeoHashScreen(
    tag: Route.Geohash,
    feedViewModel: GeoHashFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedViewModel)
    GeoHashFilterAssemblerSubscription(tag, accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {
                    DisplayGeoTagHeader(tag.geohash, Modifier.weight(1f))
                    GeoHashActionOptions(tag.geohash, accountViewModel, nav)
                },
                // The box opens holding `geo:<geohash>`, which draws under the city's name.
                actions = { SearchTopBarAction(SearchSeed.byGeohash(tag.geohash), nav) },
                nav = nav,
            )
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewGeoPostButton(tag.geohash, accountViewModel, nav)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        Column {
            GeocachesHereChip(tag.geohash, accountViewModel, nav)

            RefresheableFeedView(
                feedViewModel,
                null,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun DisplayGeoTagHeader(
    geohash: String,
    modifier: Modifier,
) {
    LoadCityName(geohashStr = geohash) { cityName ->
        Text(
            cityName,
            modifier = modifier,
        )
    }
}

@Composable
fun GeoHashActionOptions(
    tag: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    IconButton(onClick = { nav.nav(Route.GeohashChat(tag)) }) {
        SymbolIcon(
            symbol = MaterialSymbols.AutoMirrored.Chat,
            contentDescription = stringRes(Res.string.geohash_chat_open),
        )
    }

    val isFollowingTag by observeUserIsFollowingGeohash(tag, accountViewModel)

    if (isFollowingTag) {
        UnfollowButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
                    Res.string.read_only_user,
                    Res.string.login_with_a_private_key_to_be_able_to_unfollow,
                )
            } else {
                accountViewModel.unfollowGeohash(tag)
            }
        }
    } else {
        FollowButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toastManager.toast(
                    Res.string.read_only_user,
                    Res.string.login_with_a_private_key_to_be_able_to_follow,
                )
            } else {
                accountViewModel.followGeohash(tag)
            }
        }
    }
}
