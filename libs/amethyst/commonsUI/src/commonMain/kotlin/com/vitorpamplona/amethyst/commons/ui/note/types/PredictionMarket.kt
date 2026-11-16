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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.prediction_market
import com.vitorpamplona.amethyst.commons.resources.prediction_market_bet_range
import com.vitorpamplona.amethyst.commons.resources.prediction_market_cancel_reason
import com.vitorpamplona.amethyst.commons.resources.prediction_market_closed_on
import com.vitorpamplona.amethyst.commons.resources.prediction_market_closes_in
import com.vitorpamplona.amethyst.commons.resources.prediction_market_demo
import com.vitorpamplona.amethyst.commons.resources.prediction_market_fee
import com.vitorpamplona.amethyst.commons.resources.prediction_market_max_bet
import com.vitorpamplona.amethyst.commons.resources.prediction_market_min_bet
import com.vitorpamplona.amethyst.commons.resources.prediction_market_more_outcomes
import com.vitorpamplona.amethyst.commons.resources.prediction_market_status_cancelled
import com.vitorpamplona.amethyst.commons.resources.prediction_market_status_closed
import com.vitorpamplona.amethyst.commons.resources.prediction_market_status_open
import com.vitorpamplona.amethyst.commons.resources.prediction_market_status_resolved
import com.vitorpamplona.amethyst.commons.resources.prediction_market_winning_outcome
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.HeaderPill
import com.vitorpamplona.amethyst.commons.ui.note.StatusPill
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.note.timeAheadNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.redColorOnSecondSurface
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.ui.theme.warningColorOnSecondSurface
import com.vitorpamplona.amethyst.commons.util.formatDecimal
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.predictionMarkets.PredictionMarketEvent
import com.vitorpamplona.quartz.experimental.predictionMarkets.PredictionMarketOutcome
import com.vitorpamplona.quartz.experimental.predictionMarkets.PredictionMarketStatus
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** How many outcomes a card lists before collapsing the rest into "+N more". */
private const val MAX_OUTCOMES = 8

private val OutcomeShape = RoundedCornerShape(8.dp)

/**
 * Renders a kind-38000 prediction market (BAO Markets): the question, its status, whether real
 * money is at stake, the outcomes with the winner marked once resolved, and when betting closes.
 *
 * Observed rather than read once: a market is addressable, and its publisher replaces it as it
 * moves from open to closed to resolved, so a newer version lands on the same Note.
 */
@Composable
fun RenderPredictionMarket(
    note: Note,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
) {
    val observedEvent by observeNoteEvent<PredictionMarketEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    PredictionMarketCard(noteEvent, makeItShort)
}

/** Everything the card shows, read off the event once per event rather than on every recomposition. */
@Immutable
private class PredictionMarketCardState(
    val title: String?,
    val description: String?,
    val outcomes: ImmutableList<PredictionMarketOutcome>,
    val status: PredictionMarketStatus?,
    val resolution: String?,
    val endsAt: Long?,
    val category: String?,
    val isDemo: Boolean,
    /** Bet limits and fee, already formatted for display. */
    val minBetSats: String?,
    val maxBetSats: String?,
    val feePercent: String?,
    val cancelReason: String?,
    /** The moment the status was read, so the closing-time line agrees with the status badge. */
    val now: Long,
) {
    fun isWinner(outcome: PredictionMarketOutcome) = outcome.isNamedBy(resolution)

    /** Two outcomes, one a yes and one a no — "YES"/"NO", "Yes (stays online)"/"No (goes offline)". */
    val isBinary = outcomes.size == 2 && outcomes.any { isYes(it.label) } && outcomes.any { isNo(it.label) }

    companion object {
        fun from(
            event: PredictionMarketEvent,
            now: Long,
        ) = PredictionMarketCardState(
            title = event.title() ?: socialPostHeadline(event.content),
            description = event.description()?.let(::collapseBlankLines),
            outcomes = event.outcomeOptions().toImmutableList(),
            status = event.status(now),
            resolution = event.resolution(),
            endsAt = event.endsAt(),
            category = event.category(),
            isDemo = event.isDemo(),
            minBetSats = event.minBetSats()?.let(::formatGrouped),
            maxBetSats = event.maxBetSats()?.let(::formatGrouped),
            feePercent = event.feePercent()?.let(::formatPercent),
            cancelReason = event.cancelReason(),
            now = now,
        )

        fun isYes(outcome: String) = YES.containsMatchIn(outcome)

        fun isNo(outcome: String) = NO.containsMatchIn(outcome)

        private val YES = Regex("""^\s*yes\b""", RegexOption.IGNORE_CASE)
        private val NO = Regex("""^\s*no\b""", RegexOption.IGNORE_CASE)

        /** The first line of a social-post `content`, for a market with no title anywhere else. */
        private fun socialPostHeadline(content: String): String? {
            if (content.trimStart().startsWith("{")) return null
            return content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        }

        /** 4.21 → "4.21%" ("4,21%" in German), 2.0 → "2%"; nothing for NaN, infinities or a negative. */
        private fun formatPercent(value: Double): String? = if (value.isFinite() && value >= 0) "${formatDecimal(value, 2)}%" else null

        /**
         * BAO's descriptions carry paragraph breaks ("…UTC.\n\nSource of Truth: …"); clipped to a
         * few lines, a blank line reads as a hole in the card. One line break each, never a gap.
         */
        private fun collapseBlankLines(text: String): String =
            // Line by line rather than a regex: a run of whitespace in a crafted event made the
            // obvious pattern rescan it from every position — seconds on the main thread.
            text
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
    }
}

@Composable
fun PredictionMarketCard(
    event: PredictionMarketEvent,
    makeItShort: Boolean,
) {
    val state = remember(event) { PredictionMarketCardState.from(event, TimeUtils.now()) }

    Column(MaterialTheme.colorScheme.replyModifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarketKicker(state.status)

            state.title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (state.isDemo || state.category != null) {
                MarketTags(state.isDemo, state.category)
            }

            state.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = if (makeItShort) 3 else 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (state.outcomes.isNotEmpty()) {
                MarketOutcomes(state)
            }

            if (state.status == PredictionMarketStatus.CANCELLED) {
                state.cancelReason?.let { CancelReason(it) }
            } else {
                state.endsAt?.let { ClosingTime(it, state.status, state.now) }
            }

            MarketTerms(state.minBetSats, state.maxBetSats, state.feePercent)
        }
    }
}

@Composable
private fun MarketKicker(status: PredictionMarketStatus?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            symbol = MaterialSymbols.AutoMirrored.ShowChart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringRes(Res.string.prediction_market),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp).weight(1f),
        )
        status?.let { MarketStatusPill(it) }
    }
}

@Composable
private fun MarketStatusPill(status: PredictionMarketStatus) {
    when (status) {
        PredictionMarketStatus.OPEN -> {
            StatusPill(
                label = stringRes(Res.string.prediction_market_status_open),
                symbol = MaterialSymbols.RadioButtonChecked,
                container = Color(0xFF1F883D),
                content = Color.White,
            )
        }

        PredictionMarketStatus.CLOSED -> {
            StatusPill(
                label = stringRes(Res.string.prediction_market_status_closed),
                symbol = MaterialSymbols.Lock,
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PredictionMarketStatus.RESOLVED -> {
            StatusPill(
                label = stringRes(Res.string.prediction_market_status_resolved),
                symbol = MaterialSymbols.CheckCircle,
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
            )
        }

        // Called off is not an error: quiet, like Closed.
        PredictionMarketStatus.CANCELLED -> {
            StatusPill(
                label = stringRes(Res.string.prediction_market_status_cancelled),
                symbol = MaterialSymbols.Block,
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarketTags(
    isDemo: Boolean,
    category: String?,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isDemo) {
            val warning = MaterialTheme.colorScheme.warningColorOnSecondSurface
            StatusPill(
                label = stringRes(Res.string.prediction_market_demo),
                symbol = MaterialSymbols.Science,
                container = warning.copy(alpha = 0.16f),
                content = warning,
            )
        }
        category?.let {
            HeaderPill(symbol = MaterialSymbols.Tag, text = it)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarketOutcomes(state: PredictionMarketCardState) {
    val shown = if (state.outcomes.size > MAX_OUTCOMES) state.outcomes.subList(0, MAX_OUTCOMES) else state.outcomes
    val hidden = state.outcomes.size - shown.size
    // A cancelled market has no winner and takes no bets: its outcomes are shown, but muted.
    val muted = state.status == PredictionMarketStatus.CANCELLED

    if (state.isBinary) {
        BinaryOutcomes(state, muted)
        return
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        shown.forEach { outcome ->
            OutcomeChip(outcome.label, isWinner = state.isWinner(outcome), muted = muted)
        }
        if (hidden > 0) {
            Text(
                text = pluralStringRes(Res.plurals.prediction_market_more_outcomes, hidden, hidden),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.placeholderText,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

/**
 * A yes/no market as its two sides, side by side and full width — the way betting apps lay out
 * Yes and No: green and red while it is undecided; once it resolves the winner keeps its colour
 * and a check, and the loser goes quiet.
 */
@Composable
private fun BinaryOutcomes(
    state: PredictionMarketCardState,
    muted: Boolean,
) {
    val decided = state.resolution != null
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.outcomes.forEach { outcome ->
            val isWinner = state.isWinner(outcome)
            val quiet = muted || (decided && !isWinner)
            val side =
                if (PredictionMarketCardState.isYes(outcome.label)) {
                    MaterialTheme.colorScheme.allGoodColor
                } else {
                    MaterialTheme.colorScheme.redColorOnSecondSurface
                }
            val color = if (quiet) MaterialTheme.colorScheme.placeholderText else side
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(SideShape)
                        .background(if (quiet) Color.Transparent else side.copy(alpha = if (isWinner) 0.20f else 0.12f))
                        .border(1.dp, if (quiet) MaterialTheme.colorScheme.subtleBorder else side.copy(alpha = 0.45f), SideShape)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isWinner) {
                    Icon(
                        symbol = MaterialSymbols.CheckCircle,
                        contentDescription = stringRes(Res.string.prediction_market_winning_outcome),
                        tint = color,
                        filled = true,
                        modifier = Modifier.padding(end = 4.dp).size(16.dp),
                    )
                }
                Text(
                    text = outcome.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val SideShape = RoundedCornerShape(10.dp)

@Composable
private fun OutcomeChip(
    outcome: String,
    isWinner: Boolean,
    muted: Boolean,
) {
    val success = MaterialTheme.colorScheme.allGoodColor
    val borderColor = if (isWinner) success else MaterialTheme.colorScheme.subtleBorder
    val textColor =
        when {
            isWinner -> success
            muted -> MaterialTheme.colorScheme.placeholderText
            else -> MaterialTheme.colorScheme.onSurface
        }

    Row(
        modifier =
            Modifier
                .clip(OutcomeShape)
                .background(if (isWinner) success.copy(alpha = 0.14f) else Color.Transparent)
                .border(1.dp, borderColor, OutcomeShape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isWinner) {
            Icon(
                symbol = MaterialSymbols.CheckCircle,
                contentDescription = stringRes(Res.string.prediction_market_winning_outcome),
                tint = success,
                filled = true,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = outcome,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ClosingTime(
    endsAt: Long,
    status: PredictionMarketStatus?,
    now: Long,
) {
    // Read against the same moment as the status badge, so the two never disagree. A market
    // declared closed or settled before its end has no closing time worth stating.
    val stillOpen = status == PredictionMarketStatus.OPEN && endsAt > now
    if (!stillOpen && endsAt > now) return
    val text =
        if (stillOpen) {
            stringRes(Res.string.prediction_market_closes_in, timeAheadNoDot(endsAt))
        } else {
            val date = remember(endsAt) { formatDateTime(endsAt * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.NONE) }
            stringRes(Res.string.prediction_market_closed_on, date)
        }

    MarketFootnote(
        symbol = if (stillOpen) MaterialSymbols.Schedule else MaterialSymbols.EventAvailable,
        text = text,
        color = MaterialTheme.colorScheme.placeholderText,
    )
}

@Composable
private fun CancelReason(reason: String) {
    MarketFootnote(
        symbol = MaterialSymbols.Block,
        text = stringRes(Res.string.prediction_market_cancel_reason, reason),
        color = MaterialTheme.colorScheme.placeholderText,
    )
}

@Composable
private fun MarketTerms(
    minBetSats: String?,
    maxBetSats: String?,
    feePercent: String?,
) {
    val limits =
        when {
            minBetSats != null && maxBetSats != null -> {
                stringRes(Res.string.prediction_market_bet_range, minBetSats, maxBetSats)
            }

            minBetSats != null -> {
                stringRes(Res.string.prediction_market_min_bet, minBetSats)
            }

            maxBetSats != null -> {
                stringRes(Res.string.prediction_market_max_bet, maxBetSats)
            }

            else -> {
                null
            }
        }
    val fee = feePercent?.let { stringRes(Res.string.prediction_market_fee, it) }

    if (limits == null && fee == null) return

    MarketFootnote(
        symbol = MaterialSymbols.Paid,
        text = listOfNotNull(limits, fee).joinToString(" · "),
        color = MaterialTheme.colorScheme.placeholderText,
    )
}

@Composable
private fun MarketFootnote(
    symbol: MaterialSymbol,
    text: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Preview
@Composable
fun PredictionMarketCardPreview() {
    val resolved =
        PredictionMarketEvent(
            id = "3de5a164159cd7b3925c3d7aa16a5c32f8f01e50d296e2a3821bfbf5c22db82e",
            pubKey = "2eaf781b3cabef3cbf17313bc43580761748651bc09273d50646651dd06c6349",
            createdAt = 1778605676,
            tags =
                arrayOf(
                    arrayOf("d", "caec08eca65442725a5bd8f77e6d2666"),
                    arrayOf("market", "caec08eca65442725a5bd8f77e6d2666"),
                    arrayOf("category", "ai-llm"),
                    arrayOf("status", "resolved"),
                    arrayOf("end", "1777680000"),
                    arrayOf("network", "demo"),
                    arrayOf("fee_percent", "4.21"),
                    arrayOf("min_bet", "100"),
                    arrayOf("max_bet", "100000"),
                    arrayOf("outcome", "YES"),
                    arrayOf("outcome", "NO"),
                    arrayOf("title", "Will an AI coding tool appear in GitHub top trending repos today?"),
                    arrayOf("resolution", "NO"),
                ),
            content = "",
            sig = "",
        )

    val open =
        PredictionMarketEvent(
            id = "31da397fdc3a5c61aaa3fc12e8413279f99175ecdba65443d0a2e2495d73f5df",
            pubKey = "2eaf781b3cabef3cbf17313bc43580761748651bc09273d50646651dd06c6349",
            createdAt = 1786702119,
            tags =
                arrayOf(
                    arrayOf("d", "181f3a7acd28280a49e81bf70ec22998"),
                    arrayOf("market", "181f3a7acd28280a49e81bf70ec22998"),
                    arrayOf("status", "active"),
                    arrayOf("category", "mining"),
                    arrayOf("end", (TimeUtils.now() + TimeUtils.ONE_DAY * 3).toString()),
                    arrayOf("network", "mainnet"),
                    arrayOf("outcome", "Foundry USA"),
                    arrayOf("outcome", "AntPool"),
                    arrayOf("outcome", "F2Pool"),
                    arrayOf("outcome", "ViaBTC"),
                    arrayOf("outcome", "Other"),
                    arrayOf("title", "Which mining pool will mine the most Bitcoin blocks today?"),
                ),
            content = "{\"description\":\"Resolves based on the total block count at midnight UTC.\"}",
            sig = "",
        )

    ThemeComparisonColumn {
        Column(Modifier.padding(10.dp)) {
            PredictionMarketCard(resolved, makeItShort = true)
            PredictionMarketCard(open, makeItShort = true)
        }
    }
}
