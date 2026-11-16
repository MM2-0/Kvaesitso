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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessGameSnapshot
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessGameViewModel
import com.vitorpamplona.amethyst.commons.nip64Chess.ui.LiveChessGameScreen
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.chess_game_not_found
import com.vitorpamplona.amethyst.commons.resources.chess_loading_game
import com.vitorpamplona.amethyst.commons.resources.chess_open_seat
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chess.datasource.ChessGameFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The board of one game. The game streams in over its own REQ for as long as the screen is open,
 * and every move, the user's or the opponent's, redraws it from LocalCache.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessGameScreen(
    gameId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: ChessGameViewModel =
        rememberViewModel(
            key = gameId + "ChessGameViewModel",
            factory = ChessGameViewModel.Factory(gameId, accountViewModel.account),
        )

    ChessGameFilterAssemblerSubscription(viewModel.startNote, accountViewModel)

    val game by viewModel.game.collectAsStateWithLifecycle()
    val startMetadata by viewModel.startNote
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            Icon(
                                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                                contentDescription = stringRes(Res.string.back),
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        val current = game
        when {
            current != null -> {
                ChessBoardContent(current, viewModel, accountViewModel, nav, Modifier.padding(paddingValues))
            }

            // The start event is still on its way.
            startMetadata.note.event == null -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringRes(Res.string.chess_loading_game))
                    }
                }
            }

            // Loaded, but not a Jester game start.
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringRes(Res.string.chess_game_not_found),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChessBoardContent(
    game: ChessGameSnapshot,
    viewModel: ChessGameViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier,
) {
    LiveChessGameScreen(
        game = game,
        onMoveMade = { from, to, offerDraw -> accountViewModel.launchSigner { viewModel.move(from, to, offerDraw) } },
        onResign = { accountViewModel.launchSigner { viewModel.resign() } },
        player = { pubkey -> ChessPlayer(pubkey, accountViewModel, nav) },
        modifier = modifier,
        canPlay = accountViewModel.account.isWriteable(),
        onAcceptDraw = { accountViewModel.launchSigner { viewModel.acceptDraw() } },
        onClaimAbandonment = { accountViewModel.launchSigner { viewModel.claimAbandonment() } },
        onExit = { nav.popBack() },
    )
}

/** A player beside the board: avatar and name, opening their profile; or the seat nobody has taken. */
@Composable
private fun ChessPlayer(
    pubkey: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (pubkey == null) {
        Text(
            text = stringRes(Res.string.chess_open_seat),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LoadUser(pubkey) { user ->
        if (user == null) return@LoadUser
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable { nav.nav(Route.Profile(pubkey)) },
        ) {
            ClickableUserPicture(baseUser = user, size = 24.dp, accountViewModel = accountViewModel, onClick = { nav.nav(Route.Profile(pubkey)) })
            UsernameDisplay(baseUser = user, accountViewModel = accountViewModel)
        }
    }
}
