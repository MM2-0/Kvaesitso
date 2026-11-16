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
package com.vitorpamplona.amethyst.commons.ui.navigation.deck

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The deck column the user last pressed in, or null once they press in the main screen. Back and
 * Esc, and the column shortcuts, go to it. One window shows one deck, as with [DeckCommandBus].
 */
object DeckFocus {
    var columnId: String? by mutableStateOf(null)
}

/** What a keyboard shortcut or menu item asks the deck to do. */
sealed interface DeckCommand {
    /** Opens the add-column picker. */
    data object AddColumn : DeckCommand

    /** Closes the focused column. */
    data object CloseColumn : DeckCommand

    /** Moves the focused column [delta] places. */
    data class MoveColumn(
        val delta: Int,
    ) : DeckCommand

    /** Focuses the column at [index] (0-based) and scrolls it into view. */
    data class FocusColumn(
        val index: Int,
    ) : DeckCommand

    /** Asks for a name and saves the columns as a new workspace. */
    data object SaveWorkspace : DeckCommand
}

/**
 * How the desktop's menu bar and shortcuts reach the deck, which lives deep in the shared shell.
 * One window shows one deck, so one bus is enough; nothing is delivered while no deck is shown.
 */
object DeckCommandBus {
    private val flow = MutableSharedFlow<DeckCommand>(extraBufferCapacity = 8)

    val commands: SharedFlow<DeckCommand> = flow

    /** Whether a deck is on screen to take commands; while not, their keys stay with the text fields. */
    val active: Boolean get() = flow.subscriptionCount.value > 0

    /** How many decks listen, as a flow: the menu bar greys the deck's items out while none does. */
    val listeners: StateFlow<Int> get() = flow.subscriptionCount

    fun send(command: DeckCommand) {
        flow.tryEmit(command)
    }
}
