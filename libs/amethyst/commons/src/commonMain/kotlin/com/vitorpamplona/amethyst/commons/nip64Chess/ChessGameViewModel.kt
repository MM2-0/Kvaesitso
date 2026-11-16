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
package com.vitorpamplona.amethyst.commons.nip64Chess

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlin.reflect.KClass

/**
 * One chess board, read from LocalCache.
 *
 * The game is its start note plus the moves linked to it as replies, so it is rebuilt whenever a
 * move lands, whether it came from the board's relay subscription, the lobby's, or the user's own
 * publish. There is no game state of its own to keep in sync: a move is signed, consumed into
 * LocalCache and broadcast like any other event, and the board redraws from what arrived.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Stable
class ChessGameViewModel(
    val gameId: HexKey,
    val account: Account,
) : ViewModel() {
    val startNote: Note = account.cache.getOrCreateNote(gameId)

    private val viewer = account.userProfile().pubkeyHex

    val game: StateFlow<ChessGameSnapshot?> =
        merge(startNote.flow().metadata.stateFlow, startNote.flow().replies.stateFlow)
            .mapLatest { build() }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, build())

    private fun build(): ChessGameSnapshot? = startNote.jesterGameEvents()?.let { ChessGameSnapshot.build(it, viewer) }

    /**
     * Plays [from]-[to] (with an optional promotion letter on [to], like `e8q`) if it is the
     * user's legal move; with [offerDraw] the move also offers a draw.
     */
    suspend fun move(
        from: String,
        to: String,
        offerDraw: Boolean = false,
    ) {
        game.value?.moveTemplate(from, to, offerDraw)?.let { account.signAndComputeBroadcast(it) }
    }

    /** Accepts the opponent's draw offer. */
    suspend fun acceptDraw() {
        game.value?.acceptDrawTemplate()?.let { account.signAndComputeBroadcast(it) }
    }

    /** Claims the game from an opponent who has stopped moving. */
    suspend fun claimAbandonment() {
        game.value?.claimAbandonmentTemplate()?.let { account.signAndComputeBroadcast(it) }
    }

    /** Ends the game in the opponent's favour. */
    suspend fun resign() {
        game.value?.resignTemplate()?.let { account.signAndComputeBroadcast(it) }
    }

    class Factory(
        val gameId: HexKey,
        val account: Account,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = ChessGameViewModel(gameId, account) as T
    }
}
