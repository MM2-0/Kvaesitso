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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.nip64Chess.publishChessChallenge
import com.vitorpamplona.amethyst.commons.nip64Chess.ui.ChessSideBadge
import com.vitorpamplona.amethyst.commons.nip64Chess.ui.NewChessGameButton
import com.vitorpamplona.amethyst.commons.nip64Chess.ui.NewChessGameDialog
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chess_browse_open_games
import com.vitorpamplona.amethyst.commons.resources.chess_no_games_here
import com.vitorpamplona.amethyst.commons.resources.chess_no_games_here_hint
import com.vitorpamplona.amethyst.commons.resources.chess_no_games_hint
import com.vitorpamplona.amethyst.commons.resources.chess_no_games_title
import com.vitorpamplona.amethyst.commons.resources.new_chess_game
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chess.datasource.ChessLobbyFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip64Chess.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The chess screen: one feed of games for the list picked in the top bar. "Mine" (the default)
 * is the user's own games, open challenges to them included; any other list is a lobby of the
 * games its people play, Global every open challenge. Each game is a note in the feed, loaded
 * like any other feed and drawn by its card.
 */
@Composable
fun ChessLobbyScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feed = accountViewModel.feedStates.chessLobbyFeed

    WatchLifecycleAndUpdateModel(feed)
    WatchAccountForChessScreen(feed, accountViewModel)
    ChessLobbyFilterAssemblerSubscription(accountViewModel)

    var showNewGameDialog by remember { mutableStateOf(false) }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = { ChessTopBar(accountViewModel, nav) },
        bottomBar = {
            AppBottomBar(Route.Chess, nav, accountViewModel) { route ->
                if (route == Route.Chess) feed.sendToTop() else nav.navBottomBar(route)
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewChessGameButton { showNewGameDialog = true }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(feed, true) {
            SaveableFeedContentState(feed, scrollStateKey = ScrollStateKeys.CHESS_LOBBY) { listState ->
                RenderFeedContentState(
                    feedContentState = feed,
                    accountViewModel = accountViewModel,
                    listState = listState,
                    nav = nav,
                    routeForLastRead = ScrollStateKeys.CHESS_LOBBY,
                    onEmpty = { NoChessGames(accountViewModel, onNewGame = { showNewGameDialog = true }) },
                )
            }
        }
    }

    if (showNewGameDialog) {
        NewChessGameDialog(
            onDismiss = { showNewGameDialog = false },
            onCreateGame = { opponentPubkey, color ->
                showNewGameDialog = false
                accountViewModel.launchSigner {
                    val challenge = accountViewModel.account.publishChessChallenge(color, opponentPubkey)
                    withContext(Dispatchers.Main) { nav.nav(Route.ChessGame(challenge.id)) }
                }
            },
        )
    }
}

/**
 * An empty feed: the two sides, what is missing, and the ways on. With "Mine" that is starting a
 * game or browsing open ones; with another list, that nobody on it is playing.
 */
@Composable
private fun NoChessGames(
    accountViewModel: AccountViewModel,
    onNewGame: () -> Unit,
) {
    val list by accountViewModel.account.settings.defaultChessFollowList
        .collectAsStateWithLifecycle()
    val mine = list is TopFilter.Mine

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChessSideBadge(Color.WHITE, 64.dp)
            ChessSideBadge(Color.BLACK, 64.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringRes(if (mine) Res.string.chess_no_games_title else Res.string.chess_no_games_here),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringRes(if (mine) Res.string.chess_no_games_hint else Res.string.chess_no_games_here_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (list !is TopFilter.Global) {
                OutlinedButton(onClick = { accountViewModel.account.settings.changeDefaultChessFollowList(TopFilter.Global) }) {
                    Text(stringRes(Res.string.chess_browse_open_games))
                }
            }
            Button(onClick = onNewGame) { Text(stringRes(Res.string.new_chess_game)) }
        }
    }
}

@Composable
private fun WatchAccountForChessScreen(
    feed: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveChessFollowLists.collectAsStateWithLifecycle()
    val hiddenUsers by accountViewModel.account.hiddenUsers.flow
        .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        feed.checkKeysInvalidateDataAndSendToTop()
    }
}
