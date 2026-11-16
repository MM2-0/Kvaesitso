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
package com.vitorpamplona.amethyst.commons.kanban

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.kanban.board.KanbanBoardEvent
import com.vitorpamplona.quartz.experimental.kanban.board.tags.ColumnTag
import com.vitorpamplona.quartz.experimental.kanban.card.KanbanCardEvent

/** One column of a board as its card draws it: its name and, when any card is known, its count. */
@Immutable
class KanbanColumnSummary(
    val id: String,
    val name: String,
    /** null when no card of the board is known yet, so the card shows names rather than zeros. */
    val cardCount: Int?,
)

/**
 * A board's columns with what is known of its cards. [knownCards] counts the current cards
 * ([KanbanBoards.currentCards]); [unplacedCards] are those whose status names no column.
 */
@Immutable
class KanbanBoardSummary(
    val columns: List<KanbanColumnSummary>,
    val knownCards: Int,
    val unplacedCards: Int,
)

/**
 * The Kanban rules a card needs and that the events alone do not answer (NIP PR #1665): which
 * column a card's status names, and which cards actually belong to a board.
 */
object KanbanBoards {
    /**
     * The column [status] names. The PR shows a column name ("To do") while live boards write the
     * column id ("backlog"), so both are tried: an exact id first, then the id or the name without
     * regard to case.
     */
    fun columnOf(
        status: String?,
        columns: List<ColumnTag>,
    ): ColumnTag? {
        if (status.isNullOrBlank()) return null
        return columns.firstOrNull { it.id == status }
            ?: columns.firstOrNull { it.id.equals(status, ignoreCase = true) || it.name.equals(status, ignoreCase = true) }
    }

    /** What a card shows as its column: the board's column name when it resolves, else the raw status. */
    fun columnLabel(
        card: KanbanCardEvent,
        board: KanbanBoardEvent?,
    ): String? {
        val status = card.status() ?: return null
        if (board == null) return status
        return columnOf(status, board.columns())?.name ?: status
    }

    /** True when [card] names [board] in its `a` tag. */
    fun isOn(
        card: KanbanCardEvent,
        board: KanbanBoardEvent,
    ): Boolean {
        val on = card.board() ?: return false
        return on.kind == board.kind && on.pubKeyHex == board.pubKey && on.dTag == board.dTag()
    }

    /**
     * The cards that count on [board]: those that name it, published by its author or a
     * maintainer (anyone else's card is not the board's, whatever it claims), keeping the newest
     * version of each `d` since any of them may republish a card to edit it.
     */
    fun currentCards(
        board: KanbanBoardEvent,
        candidates: Iterable<KanbanCardEvent>,
    ): List<KanbanCardEvent> {
        val newest = LinkedHashMap<String, KanbanCardEvent>()
        for (card in candidates) {
            if (!isOn(card, board)) continue
            if (!board.canEditCards(card.pubKey)) continue
            val dTag = card.dTag()
            val previous = newest[dTag]
            if (previous == null || card.createdAt > previous.createdAt) newest[dTag] = card
        }
        return newest.values.toList()
    }

    /** The board's columns in their published order, each with its card count from [candidates]. */
    fun summarize(
        board: KanbanBoardEvent,
        candidates: Iterable<KanbanCardEvent>,
    ): KanbanBoardSummary {
        val columns = board.sortedColumns()
        val cards = currentCards(board, candidates)

        if (cards.isEmpty()) {
            return KanbanBoardSummary(columns.map { KanbanColumnSummary(it.id, it.name, null) }, 0, 0)
        }

        val counts = HashMap<String, Int>()
        var unplaced = 0
        for (card in cards) {
            val column = columnOf(card.status(), columns)
            if (column == null) {
                unplaced++
            } else {
                counts[column.id] = (counts[column.id] ?: 0) + 1
            }
        }

        return KanbanBoardSummary(
            columns = columns.map { KanbanColumnSummary(it.id, it.name, counts[it.id] ?: 0) },
            knownCards = cards.size,
            unplacedCards = unplaced,
        )
    }
}
