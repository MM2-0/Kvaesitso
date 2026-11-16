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
package com.vitorpamplona.amethyst.commons.kanban.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.kanban.KanbanBoardSummary
import com.vitorpamplona.amethyst.commons.kanban.KanbanBoards
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReplies
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.kanban_assigned_to
import com.vitorpamplona.amethyst.commons.resources.kanban_board_label
import com.vitorpamplona.amethyst.commons.resources.kanban_card_label
import com.vitorpamplona.amethyst.commons.resources.kanban_column_card_count
import com.vitorpamplona.amethyst.commons.resources.kanban_more_assignees
import com.vitorpamplona.amethyst.commons.resources.kanban_on_board
import com.vitorpamplona.amethyst.commons.resources.kanban_open_board
import com.vitorpamplona.amethyst.commons.resources.kanban_unplaced_cards
import com.vitorpamplona.amethyst.commons.resources.kanban_untitled
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.kanban.board.KanbanBoardEvent
import com.vitorpamplona.quartz.experimental.kanban.card.KanbanCardEvent
import com.vitorpamplona.quartz.nip01Core.core.Address

/** How many assignee avatars a card shows; the rest are on the card's own page. */
private const val MAX_ASSIGNEES = 8

/**
 * Entry for a Kanban board (kind 30301, NIP PR #1665): its title, description and columns. Each
 * column carries its card count once any of the board's cards are in the cache — they arrive as
 * the board's replies (EventCache files each card under the board it names) — and is just a name
 * before that, rather than a row of zeros that would read as an empty board.
 */
@Composable
fun RenderKanbanBoard(
    baseNote: Note,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
) {
    val noteEvent = baseNote.event as? KanbanBoardEvent ?: return

    val repliesState by observeNoteReplies(baseNote, accountViewModel)
    val summary =
        remember(noteEvent, repliesState) {
            KanbanBoards.summarize(noteEvent, baseNote.replies.mapNotNull { it.event as? KanbanCardEvent })
        }

    KanbanBoardCard(
        title = noteEvent.title(),
        description = noteEvent.description(),
        summary = summary,
        makeItShort = makeItShort,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KanbanBoardCard(
    title: String?,
    description: String?,
    summary: KanbanBoardSummary,
    makeItShort: Boolean,
    modifier: Modifier = Modifier,
) {
    KanbanFrame(modifier) {
        KanbanLabel(MaterialSymbols.Dashboard, stringRes(Res.string.kanban_board_label))

        KanbanTitle(title)

        KanbanDescription(description, makeItShort)

        if (summary.columns.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                summary.columns.forEach { column ->
                    Column(
                        modifier =
                            Modifier
                                .clip(SmallBorder)
                                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, SmallBorder)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = column.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        column.cardCount?.let {
                            Text(
                                text = pluralStringRes(Res.plurals.kanban_column_card_count, it, it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.grayText,
                            )
                        }
                    }
                }
            }
        }

        if (summary.unplacedCards > 0) {
            Text(
                text = pluralStringRes(Res.plurals.kanban_unplaced_cards, summary.unplacedCards, summary.unplacedCards),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Entry for a Kanban card (kind 30302): its title, the column it sits in, its assignees and the
 * board it belongs to. The board is loaded (which also asks the relays for it) so the status can
 * read as the column's name rather than its id, and so the link names the board; until it
 * arrives the card shows the raw status and a generic "open the board" link.
 */
@Composable
fun RenderKanbanCard(
    baseNote: Note,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? KanbanCardEvent ?: return
    val boardTag = remember(noteEvent) { noteEvent.board() }

    if (boardTag == null) {
        KanbanCardBody(noteEvent, null, null, makeItShort, accountViewModel, nav)
        return
    }

    val address = remember(boardTag) { Address(boardTag.kind, boardTag.pubKeyHex, boardTag.dTag) }

    LoadAddressableNote(address) { boardNote ->
        if (boardNote == null) {
            KanbanCardBody(noteEvent, null, null, makeItShort, accountViewModel, nav)
        } else {
            // A checked cast: the address comes from the card's own `a` tag.
            val boardState by observeNote(boardNote, accountViewModel)
            val board = boardState.note.event as? KanbanBoardEvent
            KanbanCardBody(noteEvent, board, boardNote, makeItShort, accountViewModel, nav)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KanbanCardBody(
    card: KanbanCardEvent,
    board: KanbanBoardEvent?,
    boardNote: Note?,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val column = remember(card, board) { KanbanBoards.columnLabel(card, board) }
    val assignees = remember(card) { card.assigneeKeys().distinct() }

    KanbanFrame {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KanbanLabel(MaterialSymbols.Checklist, stringRes(Res.string.kanban_card_label), Modifier.weight(1f))
            column?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .clip(SmallBorder)
                            .border(1.dp, MaterialTheme.colorScheme.primary, SmallBorder)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        KanbanTitle(card.title())

        KanbanDescription(card.description(), makeItShort)

        if (assignees.isNotEmpty()) {
            Text(
                text = stringRes(Res.string.kanban_assigned_to),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.padding(top = 6.dp),
            )
            FlowRow(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                assignees.take(MAX_ASSIGNEES).forEach { key ->
                    UserPicture(userHex = key, size = 25.dp, accountViewModel = accountViewModel, nav = nav)
                }
                if (assignees.size > MAX_ASSIGNEES) {
                    Text(
                        text = (assignees.size - MAX_ASSIGNEES).let { pluralStringRes(Res.plurals.kanban_more_assignees, it, it) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.grayText,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }

        if (boardNote != null) {
            val boardTitle = board?.title()
            Text(
                text = if (boardTitle != null) stringRes(Res.string.kanban_on_board, boardTitle) else stringRes(Res.string.kanban_open_board),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .padding(top = 6.dp)
                        .clickable { nav.nav { routeFor(boardNote, accountViewModel.account) } },
            )
        }
    }
}

@Composable
private fun KanbanFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                .padding(10.dp),
    ) {
        content()
    }
}

@Composable
private fun KanbanLabel(
    symbol: MaterialSymbol,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.grayText,
        )
    }
}

@Composable
private fun KanbanTitle(title: String?) {
    Text(
        text = title ?: stringRes(Res.string.kanban_untitled),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun KanbanDescription(
    description: String?,
    makeItShort: Boolean,
) {
    if (description.isNullOrBlank()) return
    Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.grayText,
        maxLines = if (makeItShort) 2 else 6,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp),
    )
}
