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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ZapReaction
import com.vitorpamplona.amethyst.commons.ui.note.types.GoalProgressBar
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip75ZapGoals.ZapGoalEvent

/**
 * Compact header rendered above the live-activity chat feed when the stream
 * has a NIP-75 zap goal attached. Shows the goal title, a progress bar, and
 * a one-tap zap button targeting the goal event.
 */
@Composable
fun LiveStreamGoalHeader(
    channel: LiveActivitiesChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val goalId = channel.info?.goalEventId() ?: return

    LoadNote(baseNoteHex = goalId) { goalNote ->
        if (goalNote != null) {
            GoalHeaderContent(goalNote, accountViewModel, nav)
        }
    }
}

@Composable
private fun GoalHeaderContent(
    goalNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val goal = goalNote.event as? ZapGoalEvent ?: return

    val title =
        remember(goal) {
            goal.summary()?.ifBlank { null } ?: goal.content.take(120).ifBlank { null }
        }
    val goalAmountSats = remember(goal) { (goal.amount() ?: 0L) / 1000 }

    if (goalAmountSats <= 0) return

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            Spacer(StdHorzSpacer)
            ZapReaction(
                baseNote = goalNote,
                grayTint = MaterialTheme.colorScheme.placeholderText,
                accountViewModel = accountViewModel,
                iconSize = Size20dp,
                iconSizeModifier = Size20Modifier,
                showCounter = false,
                nav = nav,
            )
        }

        GoalProgressBar(
            note = goalNote,
            goalAmountSats = goalAmountSats,
            accountViewModel = accountViewModel,
        )
    }

    HorizontalDivider(thickness = 0.5.dp)
    Spacer(Modifier.height(2.dp))
}
