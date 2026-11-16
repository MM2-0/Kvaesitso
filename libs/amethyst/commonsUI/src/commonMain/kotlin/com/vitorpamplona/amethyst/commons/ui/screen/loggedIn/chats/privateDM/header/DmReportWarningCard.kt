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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.header

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip56Reports.UserReportWarningState
import com.vitorpamplona.amethyst.commons.nip56Reports.ui.reportTypeLabels
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dm_sender_reported
import com.vitorpamplona.amethyst.commons.resources.dm_sender_reported_dismiss
import com.vitorpamplona.amethyst.commons.resources.dm_sender_reported_hide
import com.vitorpamplona.amethyst.commons.resources.dm_sender_reported_icon
import com.vitorpamplona.amethyst.commons.resources.dm_sender_reported_more_count
import com.vitorpamplona.amethyst.commons.resources.dm_sender_reported_subtitle
import com.vitorpamplona.amethyst.commons.resources.dm_sender_reported_who
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size30dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size36dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey

/** Reporter avatars shown before the row collapses into a "+N" overflow chip. */
private const val MAX_REPORTER_AVATARS = 6

/**
 * Warns that the person on the other end of a 1:1 DM has been reported by someone the user follows.
 *
 * Collapsed it states the count and, when the reports agree on one reason, that reason. The reporters
 * themselves stay behind a tap so an accusation is visible only when the user asks for it. Group rooms
 * render nothing — the warning names one person.
 */
@Composable
fun DmReportWarningCard(
    room: ChatroomKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val counterpartHex = room.users.singleOrNull() ?: return

    LoadUser(baseUserHex = counterpartHex) { user ->
        if (user != null) {
            val flow = remember(user) { accountViewModel.createUserReportWarningFlow(user) }
            val state by flow.collectAsStateWithLifecycle()

            key(counterpartHex) {
                // Hoisted above the `shouldWarn` check so it survives the transient SILENT state the
                // flow emits on ~20s of backgrounding (`WhileSubscribed(10000, 10000)` +
                // lifecycle-driven unsubscribe at ON_STOP) rather than being lost when this
                // `remember` group briefly leaves composition.
                var dismissed by remember { mutableStateOf(false) }

                if (state.shouldWarn && !dismissed) {
                    ReportWarningBody(state, accountViewModel, nav) { dismissed = true }
                }
            }
        }
    }
}

/**
 * Restrained Material 3 banner: the alert rides on a neutral `surfaceContainer` card and carries its
 * severity through the `error` color role — a tinted report badge, an error headline, and a
 * filled-tonal primary action — rather than flooding the whole header in `errorContainer`. Themes for
 * free: every color resolves from the scheme, so light and dark need no branching here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportWarningBody(
    state: UserReportWarningState,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val typeLabels = reportTypeLabels(state.types)

    // Reason chips: the single agreed reason stays visible collapsed; the full list appears on expand.
    val chipLabels = if (expanded || typeLabels.size == 1) typeLabels else emptyList()

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReportBadge()
                Column {
                    Text(
                        text = reportWarningHeadline(state),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringRes(Res.string.dm_sender_reported_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (chipLabels.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chipLabels.forEach { ReasonChip(it) }
                }
            }

            if (expanded) {
                ReporterCluster(state, accountViewModel, nav)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                ) {
                    Text(text = stringRes(Res.string.dm_sender_reported_dismiss))
                }
                FilledTonalButton(
                    onClick = { expanded = !expanded },
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(
                        text =
                            if (expanded) {
                                stringRes(Res.string.dm_sender_reported_hide)
                            } else {
                                stringRes(Res.string.dm_sender_reported_who)
                            },
                    )
                }
            }
        }
    }
}

/** Error-tinted circular badge carrying the report glyph — the card's single splash of red. */
@Composable
private fun ReportBadge() {
    Box(
        modifier =
            Modifier
                .size(Size36dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.Report,
            contentDescription = stringRes(Res.string.dm_sender_reported_icon),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(Size20dp),
        )
    }
}

/** Outlined error chip naming one report reason. */
@Composable
private fun ReasonChip(label: String) {
    val error = MaterialTheme.colorScheme.error
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = error,
        modifier =
            Modifier
                .border(1.dp, error.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * Overlapping reporter avatars, capped at [MAX_REPORTER_AVATARS] and spilling the remainder into a
 * "+N" chip so a busy account doesn't render a wall of faces. Each avatar wears a card-colored ring
 * so the overlap reads cleanly.
 */
@Composable
private fun ReporterCluster(
    state: UserReportWarningState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val shown = state.reports.take(MAX_REPORTER_AVATARS)
    val overflow = state.reporterCount - shown.size

    Row(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEach { report ->
            Box(
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                        .padding(2.dp),
            ) {
                NoteAuthorPicture(
                    baseNote = report,
                    size = Size30dp,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
        if (overflow > 0) {
            Spacer(DoubleHorzSpacer)
            OverflowChip(overflow)
        }
    }
}

/** The "+N" pill closing the capped reporter row. */
@Composable
private fun OverflowChip(count: Int) {
    Box(
        modifier =
            Modifier
                .height(Size30dp)
                .defaultMinSize(minWidth = Size30dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringRes(Res.string.dm_sender_reported_more_count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "Reported by 2 people you follow". The reason, if any, is rendered separately. */
@Composable
private fun reportWarningHeadline(state: UserReportWarningState): String =
    pluralStringRes(
        Res.plurals.dm_sender_reported,
        state.reporterCount,
        state.reporterCount,
    )

/**
 * The headline followed by the reasons as separate sentences, for the chat-list row's screen reader
 * label — where there is no second line to put them on. Sentence-separated rather than spliced, for
 * the same translation reason the headline avoids interpolation.
 */
@Composable
fun reportWarningContentDescription(state: UserReportWarningState): String {
    val headline = reportWarningHeadline(state)
    val typeLabels = reportTypeLabels(state.types)

    return remember(headline, typeLabels) {
        if (typeLabels.isEmpty()) headline else headline + ". " + typeLabels.joinToString(", ")
    }
}
