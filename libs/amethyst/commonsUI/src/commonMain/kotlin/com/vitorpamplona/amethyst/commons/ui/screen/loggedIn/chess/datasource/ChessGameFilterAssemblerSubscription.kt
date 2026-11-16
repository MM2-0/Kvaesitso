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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.chess.ChessGameFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.chess.ChessGameQueryState
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * Keeps one game's REQ open while its board is on screen. When a move arrives from a new player
 * or relay, the filter re-assembles so those relays are asked too.
 */
@Composable
fun ChessGameFilterAssemblerSubscription(
    startNote: Note,
    accountViewModel: AccountViewModel,
    dataSource: ChessGameFilterAssembler = accountViewModel.dataSources().chessGame,
) {
    val state = remember(startNote, accountViewModel.account) { ChessGameQueryState(startNote.idHex, accountViewModel.account) }

    LifecycleAwareKeyDataSourceSubscription(state, dataSource)

    LaunchedEffect(startNote, dataSource) {
        startNote
            .flow()
            .replies.stateFlow
            .collect { dataSource.invalidateFilters() }
    }
    LaunchedEffect(startNote, dataSource) {
        startNote
            .flow()
            .relays.stateFlow
            .collect { dataSource.invalidateFilters() }
    }
}
