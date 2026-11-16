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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.bookmarkgroups.repositories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.bookmarkgroups.repositories.dal.BookmarkRepositoriesFeedViewModel
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.repository_bookmarks
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address

@Composable
fun BookmarkedRepositoriesScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val repositoriesFeedViewModel: BookmarkRepositoriesFeedViewModel =
        rememberViewModel(
            key = "NostrBookmarkRepositoriesFeedViewModel",
            factory = BookmarkRepositoriesFeedViewModel.Factory(accountViewModel.account),
        )

    val repositoryBookmarks by accountViewModel.account.gitRepositoryListState.repositoryAddressSet
        .collectAsStateWithLifecycle()

    LaunchedEffect(repositoryBookmarks) {
        repositoriesFeedViewModel.invalidateData()
    }

    // Preload any bookmarked repo announcements not yet in cache so they don't pop in one by one.
    PreloadRepositoryEvents(repositoryBookmarks, accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.repository_bookmarks), nav)
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableFeedView(
            repositoriesFeedViewModel,
            null,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun PreloadRepositoryEvents(
    repositoryBookmarks: Set<Address>,
    accountViewModel: AccountViewModel,
) {
    val eventFinder = accountViewModel.dataSources().eventFinder
    val account = accountViewModel.account

    val queries =
        remember(repositoryBookmarks) {
            repositoryBookmarks
                .map { account.cache.getOrCreateAddressableNote(it) }
                .filter { it.event == null }
                .map { EventFinderQueryState(it, account) }
        }

    DisposableEffect(queries) {
        eventFinder.subscribe(queries)
        onDispose {
            eventFinder.unsubscribe(queries)
        }
    }
}
