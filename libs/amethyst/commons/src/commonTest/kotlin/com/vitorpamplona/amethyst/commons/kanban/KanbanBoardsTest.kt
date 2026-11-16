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

import com.vitorpamplona.quartz.experimental.kanban.board.KanbanBoardEvent
import com.vitorpamplona.quartz.experimental.kanban.card.KanbanCardEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KanbanBoardsTest {
    private val owner = "aa".repeat(32)
    private val maintainer = "bb".repeat(32)
    private val stranger = "cc".repeat(32)
    private val sig = "22".repeat(64)

    private val board =
        KanbanBoardEvent(
            "01".repeat(32),
            owner,
            1_700_000_000L,
            arrayOf(
                arrayOf("d", "roadmap"),
                arrayOf("title", "Roadmap"),
                arrayOf("col", "done", "Done", "2"),
                arrayOf("col", "backlog", "To do", "0"),
                arrayOf("col", "doing", "In progress", "1"),
                arrayOf("p", maintainer),
            ),
            "",
            sig,
        )

    private fun card(
        d: String,
        status: String?,
        author: String = owner,
        createdAt: Long = 1_700_000_100L,
        boardRef: String = "30301:$owner:roadmap",
    ) = KanbanCardEvent(
        (d.hashCode().toString(16) + author.take(8) + createdAt).padEnd(64, '0').take(64),
        author,
        createdAt,
        listOfNotNull(
            arrayOf("d", d),
            arrayOf("title", d),
            arrayOf("a", boardRef),
            status?.let { arrayOf("s", it) },
        ).toTypedArray(),
        "",
        sig,
    )

    @Test
    fun statusMatchesAColumnByIdOrByName() {
        val columns = board.columns()
        assertEquals("backlog", KanbanBoards.columnOf("backlog", columns)?.id)
        assertEquals("backlog", KanbanBoards.columnOf("To do", columns)?.id)
        assertEquals("doing", KanbanBoards.columnOf("IN PROGRESS", columns)?.id)
        assertNull(KanbanBoards.columnOf("archived", columns))
        assertNull(KanbanBoards.columnOf(null, columns))
    }

    @Test
    fun aCardShowsTheColumnNameWhenItsBoardIsKnown() {
        val c = card("a", "doing")
        assertEquals("In progress", KanbanBoards.columnLabel(c, board))
        assertEquals("doing", KanbanBoards.columnLabel(c, null))
        assertEquals("archived", KanbanBoards.columnLabel(card("b", "archived"), board))
        assertNull(KanbanBoards.columnLabel(card("c", null), board))
    }

    @Test
    fun withoutCardsTheColumnsAreNamesInTheirOrder() {
        val summary = KanbanBoards.summarize(board, emptyList())
        assertEquals(listOf("To do", "In progress", "Done"), summary.columns.map { it.name })
        assertEquals(listOf(null, null, null), summary.columns.map { it.cardCount })
        assertEquals(0, summary.knownCards)
    }

    @Test
    fun countsOnlyTheBoardsOwnCardsAtTheirNewestVersion() {
        val cards =
            listOf(
                card("a", "backlog"),
                card("b", "doing", author = maintainer),
                // An older version of b, moved since: only the newest counts.
                card("b", "backlog", author = owner, createdAt = 1_700_000_050L),
                card("c", "done"),
                card("d", "To do"),
                // Not the board's: a stranger cannot add cards, and another board's card is not this one's.
                card("e", "done", author = stranger),
                card("f", "done", boardRef = "30301:$owner:other"),
                // On the board, but in no column.
                card("g", "archived"),
            )

        val summary = KanbanBoards.summarize(board, cards)

        assertEquals(listOf(2, 1, 1), summary.columns.map { it.cardCount })
        assertEquals(5, summary.knownCards)
        assertEquals(1, summary.unplacedCards)
    }
}
