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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ZapraiserStatus
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteZaps
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.goal_closed
import com.vitorpamplona.amethyst.commons.resources.goal_ends_in
import com.vitorpamplona.amethyst.commons.resources.goal_progress
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.durationPhrase
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Font14SP
import com.vitorpamplona.amethyst.commons.ui.theme.fundraiserProgressColor
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip75ZapGoals.ZapGoalEvent
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.math.roundToInt
import com.vitorpamplona.amethyst.commons.model.zapraiserStatus as computeZapraiserStatus

@Composable
fun RenderGoal(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? ZapGoalEvent ?: return

    GoalHeader(noteEvent, note, accountViewModel, nav)
}

@Composable
fun GoalHeader(
    noteEvent: ZapGoalEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val image = noteEvent.image()
    // NIP-75: the content is the goal's human-readable description, which clients show as its
    // title; `summary` is a longer explanation under it. Show both rather than letting the
    // summary replace the content.
    val title = remember(noteEvent) { noteEvent.content.ifBlank { null } }
    val summary = remember(noteEvent) { noteEvent.summary()?.ifBlank { null }?.takeIf { it != title } }
    val goalAmountMillisats = noteEvent.amount() ?: 0L
    val goalAmountSats = goalAmountMillisats / 1000
    val closedAt = noteEvent.closedAt()
    val isClosed = closedAt != null && closedAt < TimeUtils.now()

    Column(MaterialTheme.colorScheme.replyModifier) {
        image?.let {
            Box {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription = stringRes(Res.string.preview_card_image_for, it),
                    contentScale = ContentScale.FillWidth,
                    mainImageModifier = Modifier.fillMaxWidth(),
                    loadedImageModifier = Modifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = { DefaultImageHeaderBackground(note, accountViewModel) },
                    onError = { DefaultImageHeader(note, accountViewModel) },
                )
            }
        }

        Column(Modifier.padding(10.dp)) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (title != null || summary != null) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (goalAmountSats > 0) {
                GoalProgressBar(
                    note = note,
                    goalAmountSats = goalAmountSats,
                    accountViewModel = accountViewModel,
                )
            }

            if (isClosed) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringRes(Res.string.goal_closed),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            } else if (closedAt != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringRes(Res.string.goal_ends_in, durationPhrase(closedAt - TimeUtils.now())),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}

@Composable
fun GoalProgressBar(
    note: Note,
    goalAmountSats: Long,
    accountViewModel: AccountViewModel,
) {
    val zapsState by observeNoteZaps(note, accountViewModel)

    var zapraiserStatus by
        remember { mutableStateOf(ZapraiserStatus(0F, showAmount(BigDecimal(goalAmountSats)))) }

    LaunchedEffect(key1 = zapsState) {
        zapsState?.note?.let {
            zapraiserStatus = computeZapraiserStatus(accountViewModel.account.zaps.calculateZappedAmount(note), goalAmountSats)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(24.dp),
            color = MaterialTheme.colorScheme.fundraiserProgressColor,
            progress = { zapraiserStatus.progress },
            gapSize = 0.dp,
            strokeCap = StrokeCap.Square,
            drawStopIndicator = {},
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            val totalPercentage by
                remember(zapraiserStatus) {
                    derivedStateOf { "${(zapraiserStatus.progress * 100).roundToInt()}%" }
                }

            Text(
                text =
                    stringRes(
                        Res.string.goal_progress,
                        totalPercentage,
                        showAmount(BigDecimal(goalAmountSats)),
                    ),
                fontSize = Font14SP,
                maxLines = 1,
            )
        }
    }
}
