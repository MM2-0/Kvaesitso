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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.notifications.NotificationSummaryState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.today
import com.vitorpamplona.amethyst.commons.resources.zaps
import com.vitorpamplona.amethyst.commons.ui.note.CommentIcon
import com.vitorpamplona.amethyst.commons.ui.note.LikedIcon
import com.vitorpamplona.amethyst.commons.ui.note.RepostedIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.RoyalBlue
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size24Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText

@Composable
fun UserReactionsRow(
    model: NotificationSummaryState,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = CenterVertically,
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .padding(10.dp),
    ) {
        Row(verticalAlignment = CenterVertically, modifier = Modifier.width(68.dp)) {
            Text(
                text = stringRes(id = Res.string.today),
                fontWeight = FontWeight.Bold,
            )

            Icon(
                symbol = MaterialSymbols.ExpandMore,
                null,
                modifier = Size20Modifier,
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }

        Row(verticalAlignment = CenterVertically, modifier = Modifier.weight(1f)) {
            UserReplyModel(model)
        }

        Row(verticalAlignment = CenterVertically, modifier = Modifier.weight(1f)) {
            UserBoostModel(model)
        }

        Row(verticalAlignment = CenterVertically, modifier = Modifier.weight(1f)) {
            UserReactionModel(model)
        }

        // Zap totals run longer than the other counts, so this column gets the extra room.
        Row(verticalAlignment = CenterVertically, modifier = Modifier.weight(1.4f)) {
            UserZapModel(model)
        }
    }
}

@Composable
private fun UserZapModel(model: NotificationSummaryState) {
    Icon(
        symbol = MaterialSymbols.Bolt,
        contentDescription = stringRes(Res.string.zaps),
        modifier = Size24Modifier,
        tint = BitcoinOrange,
    )

    Spacer(modifier = Modifier.width(8.dp))

    UserZapReaction(model)
}

@Composable
private fun UserReactionModel(model: NotificationSummaryState) {
    LikedIcon(modifier = Size20Modifier)

    Spacer(modifier = StdHorzSpacer)

    UserLikeReaction(model)
}

@Composable
private fun UserBoostModel(model: NotificationSummaryState) {
    RepostedIcon(
        modifier = Size24Modifier,
    )

    Spacer(modifier = StdHorzSpacer)

    UserBoostReaction(model)
}

@Composable
private fun UserReplyModel(model: NotificationSummaryState) {
    CommentIcon(Size20Modifier, RoyalBlue)

    Spacer(modifier = StdHorzSpacer)

    UserReplyReaction(model)
}

@Composable
fun UserReplyReaction(model: NotificationSummaryState) {
    val showCounts by model.todaysReplyCount.collectAsStateWithLifecycle("")

    Text(
        showCounts,
        fontWeight = FontWeight.Bold,
        fontSize = summaryCountSize(),
    )
}

@Composable
fun UserBoostReaction(model: NotificationSummaryState) {
    val boosts by model.todaysBoostCount.collectAsStateWithLifecycle("")

    Text(
        boosts,
        fontWeight = FontWeight.Bold,
        fontSize = summaryCountSize(),
    )
}

@Composable
fun UserLikeReaction(model: NotificationSummaryState) {
    val reactions by model.todaysReactionCount.collectAsStateWithLifecycle("")

    Text(
        text = reactions,
        fontWeight = FontWeight.Bold,
        fontSize = summaryCountSize(),
    )
}

@Composable
fun UserZapReaction(model: NotificationSummaryState) {
    val amount by model.todaysZapAmount.collectAsStateWithLifecycle("")
    Text(
        amount,
        fontWeight = FontWeight.Bold,
        fontSize = summaryCountSize(),
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * The counts' size: 9/8 of the body text, so 18sp under the phone's type scale and smaller under the
 * desktop's, which sizes its body text like the OS's own apps.
 */
@Composable
private fun summaryCountSize(): TextUnit = MaterialTheme.typography.bodyLarge.fontSize * 1.125f
