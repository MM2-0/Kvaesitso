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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.nip64Chess.ui.ChessGameViewer
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chess_color_black
import com.vitorpamplona.amethyst.commons.resources.chess_color_white
import com.vitorpamplona.amethyst.commons.resources.chess_note_challenge_from
import com.vitorpamplona.amethyst.commons.resources.chess_note_challenge_sent_to
import com.vitorpamplona.amethyst.commons.resources.chess_note_game_ended
import com.vitorpamplona.amethyst.commons.resources.chess_note_open_challenge
import com.vitorpamplona.amethyst.commons.resources.chess_note_result
import com.vitorpamplona.amethyst.commons.resources.chess_note_termination
import com.vitorpamplona.amethyst.commons.resources.chess_note_time
import com.vitorpamplona.amethyst.commons.resources.chess_note_versus
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip64Chess.challenge.offer.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.end.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.game.ChessGameEvent

/**
 * Render NIP-64 Chess Game event (Kind 64)
 *
 * Displays interactive chess board with PGN game replay functionality.
 * Wraps content in sensitivity warning for user-controlled content filtering.
 *
 * @param note The note containing the ChessGameEvent
 * @param backgroundColor Mutable state for background color
 * @param accountViewModel Account view model for sensitivity checking
 * @param nav Navigation interface
 */
@Composable
fun RenderChessGame(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = (note.event as? ChessGameEvent) ?: return

    SensitivityWarning(note = note, accountViewModel = accountViewModel) {
        ChessGameViewer(
            pgnContent = event.pgn(),
            playerContent = { playerHex ->
                LoadUser(playerHex) { user ->
                    if (user != null) {
                        UserChip(user = user, accountViewModel = accountViewModel, nav = nav)
                    } else {
                        Text(
                            text = playerHex.take(8) + "...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            },
        )
    }
}

/**
 * Render Live Chess Challenge event (Kind 30064)
 *
 * Shows who challenged whom. Kind 30064 is not played by any client (Amethyst plays Jester's
 * kind 30), so the card offers no way to accept it.
 */
@Composable
fun RenderLiveChessChallenge(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = (note.event as? LiveChessGameChallengeEvent) ?: return

    val isOpenChallenge = event.opponentPubkey() == null
    val isIncomingChallenge = event.opponentPubkey() == accountViewModel.account.userProfile().pubkeyHex

    val borderColor =
        when {
            isOpenChallenge -> Color(0xFF4CAF50)

            // Green for open
            isIncomingChallenge -> Color(0xFFFFA726)

            // Orange for incoming
            else -> MaterialTheme.colorScheme.outline // Gray for sent
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(2.dp, borderColor, MaterialTheme.shapes.medium),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when {
                        isOpenChallenge -> {
                            Text(
                                text = stringRes(Res.string.chess_note_open_challenge),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        isIncomingChallenge -> {
                            Text(
                                text = stringRes(Res.string.chess_note_challenge_from),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            note.author?.let { author ->
                                UserChip(user = author, accountViewModel = accountViewModel, nav = nav)
                            }
                        }

                        else -> {
                            Text(
                                text = stringRes(Res.string.chess_note_challenge_sent_to),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            event.opponentPubkey()?.let { opponentHex ->
                                LoadUser(opponentHex) { user ->
                                    if (user != null) {
                                        UserChip(user = user, accountViewModel = accountViewModel, nav = nav)
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text =
                        if (event.playerColor()?.name == "WHITE") stringRes(Res.string.chess_color_white) else stringRes(Res.string.chess_color_black),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            event.timeControl()?.let {
                Text(
                    text = stringRes(Res.string.chess_note_time, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Render Live Chess Game End event (Kind 30067)
 *
 * Shows final game result with PGN viewer
 */
@Composable
fun RenderLiveChessGameEnd(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = (note.event as? LiveChessGameEndEvent) ?: return

    SensitivityWarning(note = note, accountViewModel = accountViewModel) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Result header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringRes(Res.string.chess_note_game_ended),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    // Players row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        note.author?.let { author ->
                            UserChip(user = author, accountViewModel = accountViewModel, nav = nav)
                        }

                        Text(
                            text = stringRes(Res.string.chess_note_versus),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )

                        event.opponentPubkey()?.let { opponentHex ->
                            LoadUser(opponentHex) { user ->
                                if (user != null) {
                                    UserChip(user = user, accountViewModel = accountViewModel, nav = nav)
                                }
                            }
                        }
                    }

                    Text(
                        text = stringRes(Res.string.chess_note_result, event.result()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    event.termination()?.let {
                        Text(
                            text = stringRes(Res.string.chess_note_termination, it.replace("_", " ")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            // Show PGN if available
            event.pgn().takeIf { it.isNotBlank() }?.let { pgn ->
                ChessGameViewer(
                    pgnContent = pgn,
                    playerContent = { playerHex ->
                        LoadUser(playerHex) { user ->
                            if (user != null) {
                                UserChip(user = user, accountViewModel = accountViewModel, nav = nav)
                            } else {
                                Text(
                                    text = playerHex.take(8) + "...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Compact user display with small picture and name, shown inline.
 */
@Composable
private fun UserChip(
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ClickableUserPicture(
            baseUser = user,
            size = 24.dp,
            accountViewModel = accountViewModel,
            onClick = { nav.nav(routeFor(it)) },
        )
        UsernameDisplay(
            baseUser = user,
            accountViewModel = accountViewModel,
        )
    }
}
