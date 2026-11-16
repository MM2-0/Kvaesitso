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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ballot
import com.vitorpamplona.amethyst.commons.resources.ballot_election
import com.vitorpamplona.amethyst.commons.resources.ballot_more_answers
import com.vitorpamplona.amethyst.commons.resources.ballot_proof_hash
import com.vitorpamplona.amethyst.commons.resources.ballot_vote
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.ballots.BallotAnswer
import com.vitorpamplona.quartz.experimental.ballots.BallotEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** How many answers a card lists before collapsing the rest into "+N more answers". */
private const val MAX_ANSWERS = 4

/**
 * Renders a kind-38000 ballot from an auditable-voting app: the election it was cast in, its
 * answers as question → answer rows, and the proof hash that lets the vote be audited.
 *
 * Observed rather than read once: a ballot is addressable, so a re-cast vote replaces it in place.
 */
@Composable
fun RenderBallot(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val observedEvent by observeNoteEvent<BallotEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    BallotCard(noteEvent)
}

@Immutable
private class BallotCardState(
    val election: String?,
    val answers: ImmutableList<BallotAnswer>,
    val hiddenAnswers: Int,
    val proofHash: String?,
) {
    companion object {
        fun from(event: BallotEvent): BallotCardState {
            val answers = event.answers()
            return BallotCardState(
                election = event.election(),
                answers = answers.take(MAX_ANSWERS).toImmutableList(),
                hiddenAnswers = (answers.size - MAX_ANSWERS).coerceAtLeast(0),
                proofHash = event.proofHash(),
            )
        }
    }
}

@Composable
fun BallotCard(event: BallotEvent) {
    val state = remember(event) { BallotCardState.from(event) }

    Column(MaterialTheme.colorScheme.replyModifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    symbol = MaterialSymbols.Checklist,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringRes(Res.string.ballot),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            state.election?.let {
                Text(
                    text = stringRes(Res.string.ballot_election, it),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (state.answers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // The answers as a small table: one bordered block, a hairline between rows.
                    Column(
                        Modifier
                            .clip(AnswersShape)
                            .border(1.dp, MaterialTheme.colorScheme.subtleBorder, AnswersShape),
                    ) {
                        state.answers.forEachIndexed { i, answer ->
                            if (i > 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.subtleBorder)
                            AnswerRow(answer)
                        }
                    }

                    if (state.hiddenAnswers > 0) {
                        Text(
                            text = pluralStringRes(Res.plurals.ballot_more_answers, state.hiddenAnswers, state.hiddenAnswers),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    }
                }
            }

            state.proofHash?.let { ProofHash(it) }
        }
    }
}

@Composable
private fun AnswerRow(answer: BallotAnswer) {
    // A lone `vote_choice` has no question of its own; everything else is shown as published.
    val question =
        if (answer.question == BallotEvent.VOTE_QUESTION) {
            stringRes(Res.string.ballot_vote)
        } else {
            answer.question
        }

    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.grayText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = answer.answer,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.6f),
        )
    }
}

private val AnswersShape = RoundedCornerShape(10.dp)

@Composable
private fun ProofHash(hash: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            symbol = MaterialSymbols.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringRes(Res.string.ballot_proof_hash, hash),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}

@Preview
@Composable
fun BallotCardPreview() {
    val ballot =
        BallotEvent(
            id = "78f5f012a9518a0b39134e659ef180eae6df9f6b38ce3b4b86963409837e2776",
            pubKey = "caf0b6ef4eb461677ed7bf7b569a0212443d6429f6cc555ca8f00a599e453bb2",
            createdAt = 1773998101,
            tags =
                arrayOf(
                    arrayOf("election", "spring-2026-council"),
                    arrayOf("proof_hash", "bb64227a0b57fa6af765881b0952fe9d01a3a18faf52a3e34eeb7a896ee36b95"),
                ),
            content =
                "{\"ballot\":{\"funding_priority\":\"community-grants\",\"audit_policy\":\"every-election\"," +
                    "\"treasurer\":\"alice\",\"quorum\":\"60%\",\"term_length\":\"1 year\"}}",
            sig = "",
        )

    ThemeComparisonColumn {
        Column(Modifier.padding(10.dp)) {
            BallotCard(ballot)
        }
    }
}
