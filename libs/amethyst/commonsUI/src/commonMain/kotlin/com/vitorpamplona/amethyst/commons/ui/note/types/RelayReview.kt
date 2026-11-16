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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.ratings.RatingMark
import com.vitorpamplona.quartz.experimental.ratings.RelayReviewEvent
import com.vitorpamplona.quartz.experimental.ratings.tags.CategoryRatingTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.roundToInt

/**
 * Renders a kind-31987 relay review: the relay, an overall star row, per-aspect scores, and the
 * written review.
 *
 * It shares [RatingStars] with the kind-34259 entity rating on purpose — the two kinds are the
 * same gesture aimed at different things, and a reader should not have to learn two star
 * vocabularies. The scales agree: 31987 is always the 0..1 fraction, which is one of the two
 * conventions `EntityRatingEvent.stars()` already resolves.
 *
 * Per-aspect scores are shown as percentages rather than stars. They are secondary to the
 * overall score, and five more star rows would drown it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderRelayReview(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observed, not read once: these kinds are DEFINED by replacement — a newer rating,
    // review or redirect lands on the same Note instance. `Note` is @Stable and `event`
    // is a plain @Volatile var, so a bare read registers no snapshot dependency and
    // Compose would keep showing the superseded version.
    val observedEvent by observeNoteEvent<RelayReviewEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    val relayUrl = remember(noteEvent) { noteEvent.relay()?.url ?: noteEvent.relayUrl() }
    val stars = remember(noteEvent) { noteEvent.stars() }
    val categories = remember(noteEvent) { noteEvent.categoryRatings().toImmutableList() }

    Column(Modifier.fillMaxWidth()) {
        Column(MaterialTheme.colorScheme.replyModifier) {
            relayUrl?.let {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = Size5dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Dns,
                        contentDescription = null,
                        modifier = Modifier.width(18.dp),
                        tint = MaterialTheme.colorScheme.grayText,
                    )

                    Spacer(StdHorzSpacer)

                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // RatingMark.RELAY suppresses the mark chip: the Dns icon and the URL above already
            // say what is being rated.
            stars?.let { RatingStars(it, RatingMark.RELAY) }

            if (categories.isNotEmpty()) {
                CategoryScores(categories)
            }
        }

        if (noteEvent.content.isNotBlank()) {
            val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

            TranslatableRichTextViewer(
                content = noteEvent.content,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                tags = tags,
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryScores(categories: ImmutableList<CategoryRatingTag>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(Size5dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        categories.forEach { rating ->
            Text(
                // The category is publisher-chosen free text, not a translatable label, so it is
                // shown as published — tidied of separators, never mapped through a string table
                // that would silently drop every value nobody enumerated.
                text = "${readableToken(rating.category!!)} ${(rating.value * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

@Preview
@Composable
fun CategoryScoresPreview() {
    ThemeComparisonColumn {
        CategoryScores(
            listOf(
                CategoryRatingTag(1.0, "speed"),
                CategoryRatingTag(0.6, "uptime"),
                CategoryRatingTag(0.8, "content-policy"),
            ).toImmutableList(),
        )
    }
}
