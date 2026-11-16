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
package com.vitorpamplona.amethyst.commons.ui.navigation.topbars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserPicture
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.your_profile_image
import com.vitorpamplona.amethyst.commons.search.SearchQuery
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.SearchIcon
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.HeaderPictureModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The standard root top bar: the drawer avatar (or a back arrow), the screen's own title slot,
 * and the search button.
 *
 * [baseFilter] is what this screen already knows about what it is showing — its kind window, the
 * person whose profile it is, the topic it is filtered to. It seeds the search box so the reader
 * carries on narrowing from where they were instead of starting over.
 *
 * Deliberately has no default. It started with one, and the result was that two thirds of the
 * screens in the app opened search bare without anyone noticing — an omission that looks exactly
 * like a decision. A screen with nothing to say passes null and says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDrawerSearchTopBar(
    accountViewModel: AccountViewModel,
    nav: INav,
    baseFilter: SearchQuery?,
    content: @Composable () -> Unit,
) {
    ShorterTopAppBar(
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                content()
            }
        },
        navigationIcon = { TopBarNavigationIcon(accountViewModel, nav) },
        actions = {
            IconButton(onClick = { nav.nav(searchRouteFor(baseFilter)) }) {
                SearchIcon(modifier = Size22Modifier, MaterialTheme.colorScheme.placeholderText)
            }
        },
    )
}

/**
 * The search screen this button opens: seeded with [baseFilter] when the screen named one, and
 * bare when it did not — an empty query seeds nothing rather than an empty chip.
 */
fun searchRouteFor(baseFilter: SearchQuery?): Route.Search = baseFilter?.takeIf { !it.isEmpty }?.let { Route.Search.of(it) } ?: Route.Search()

/**
 * A search button for a top bar that is not the root one — a hashtag feed, a location feed, a
 * profile — opening search already narrowed to what that screen is showing.
 */
@Composable
fun SearchTopBarAction(
    baseFilter: SearchQuery?,
    nav: INav,
) {
    IconButton(onClick = { nav.nav(searchRouteFor(baseFilter)) }) {
        SearchIcon(modifier = Size22Modifier, MaterialTheme.colorScheme.placeholderText)
    }
}

/**
 * The standard leading slot for root-capable top bars: a back arrow when the screen sits on
 * top of a back stack; otherwise the avatar drawer-opener — unless a large-screen shell
 * already presents the drawer (rail avatar or permanently docked pane), in which case
 * nothing is shown. Use this instead of hand-writing the branches per top bar.
 */
@Composable
fun TopBarNavigationIcon(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (nav.canPop()) {
        IconButton(onClick = nav::popBack) {
            ArrowBackIcon()
        }
    } else if (!LocalScreenLayout.current.isLargeScreen) {
        LoggedInUserPictureDrawer(accountViewModel, nav::openDrawer)
    }
}

@Composable
fun LoggedInUserPictureDrawer(
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        val profilePicture by observeUserPicture(accountViewModel.userProfile(), accountViewModel)

        RobohashFallbackAsyncImage(
            robot = accountViewModel.userProfile().pubkeyHex,
            model = profilePicture,
            contentDescription = stringRes(id = Res.string.your_profile_image),
            modifier = HeaderPictureModifier,
            contentScale = ContentScale.Crop,
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
            autoPlayGif =
                accountViewModel.settings.autoPlayVideosFlow
                    .collectAsStateWithLifecycle()
                    .value,
        )
    }
}
