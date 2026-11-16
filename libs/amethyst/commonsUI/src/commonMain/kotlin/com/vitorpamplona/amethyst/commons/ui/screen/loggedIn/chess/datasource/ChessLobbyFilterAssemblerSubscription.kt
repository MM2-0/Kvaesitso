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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chess.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.nip64Chess.dal.chessGamesMissingTheirStart
import com.vitorpamplona.amethyst.commons.relayClient.chess.ChessLobbyGamesQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.topNavFeedQueryState
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** How many listed games have their moves streamed: the newest ones, which are the ones on screen. */
private const val WATCHED_GAMES = 100

/**
 * The chess screen's REQs while it is on screen: the games of the list picked in the top bar, the
 * user's own games (and the challenges of theirs that never arrived), and the moves of the games
 * it lists.
 */
@Composable
fun ChessLobbyFilterAssemblerSubscription(accountViewModel: AccountViewModel) {
    val topNav =
        remember(accountViewModel.account) {
            val account = accountViewModel.account
            accountViewModel.topNavFeedQueryState(
                account.settings.defaultChessFollowList,
                account.liveChessFollowListsPerRelay,
                accountViewModel.feedStates.chessLobbyFeed,
            )
        }
    LifecycleAwareKeyDataSourceSubscription(topNav, accountViewModel.dataSources().chess)

    val listed by remember(accountViewModel) { gameIds(accountViewModel.feedStates.chessLobbyFeed) }.collectAsStateWithLifecycle(emptyList())
    val missingStarts by remember(accountViewModel) {
        chessGamesMissingTheirStart(accountViewModel.account.userProfile().pubkeyHex)
    }.collectAsStateWithLifecycle(emptySet())

    val watched = remember(listed) { listed.take(WATCHED_GAMES).toSet() }
    val games = remember(accountViewModel.account, watched, missingStarts) { ChessLobbyGamesQueryState(accountViewModel.account, watched, missingStarts) }
    LifecycleAwareKeyDataSourceSubscription(games, accountViewModel.dataSources().chessLobbyGames)
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun gameIds(feed: FeedContentState): Flow<List<String>> =
    feed.feedContent.flatMapLatest { state ->
        if (state is FeedState.Loaded) state.feed.map { loaded -> loaded.list.map { it.idHex } } else flowOf(emptyList())
    }
