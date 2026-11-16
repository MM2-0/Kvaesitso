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
package com.vitorpamplona.amethyst.commons.ui.feeds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.chats.privateDM.history.ChatHistoryPhase
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_auto_body
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_auto_title
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_continue
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_has_more
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_incomplete_body
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_incomplete_title
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_keep_looking
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_loaded_to
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_none_body
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_none_title
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_paused_title
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_search_body
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_search_title
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_start_body
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_start_title
import com.vitorpamplona.amethyst.commons.resources.chats_history_gate_stop
import com.vitorpamplona.amethyst.commons.resources.retry
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayPagingProgress
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.jetbrains.compose.resources.stringResource

/**
 * The older-history card at the top of a conversation whose NIP-17 history is paged by a
 * [ChatHistoryGate][com.vitorpamplona.amethyst.commons.chats.privateDM.history.ChatHistoryGate]: it says
 * where loading stopped and offers the one action that moves it — Continue, Keep looking, Stop or Retry.
 * Hidden while [phase] is [ChatHistoryPhase.IDLE]. Tapping it lists the relays and how far each got.
 *
 * @param chatName who the chat is with, woven into sentences ("No older messages with Alice yet").
 * @param completeTo epoch seconds every message after which has arrived from the relays still answering,
 *   or null before any of them has reported.
 * @param stalledRelays short names of the relays that didn't answer.
 */
@Composable
fun ChatHistoryGateCard(
    phase: ChatHistoryPhase,
    chatName: String,
    completeTo: Long?,
    stalledRelays: List<String>,
    relayProgress: Map<NormalizedRelayUrl, RelayPagingProgress>,
    onResume: () -> Unit,
    onKeepLooking: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (phase == ChatHistoryPhase.IDLE) return

    var showRelays by remember { mutableStateOf(false) }
    if (showRelays) {
        DmHistoryRelayDialog("NIP-17", relayProgress, ::formatHistoryDay) { showRelays = false }
    }

    val date = remember(completeTo) { completeTo?.let(::formatHistoryDay) }
    val incomplete = phase == ChatHistoryPhase.END && stalledRelays.isNotEmpty()

    val title =
        when (phase) {
            ChatHistoryPhase.AUTO -> stringResource(Res.string.chats_history_gate_auto_title)
            ChatHistoryPhase.PAUSED -> stringResource(Res.string.chats_history_gate_paused_title)
            ChatHistoryPhase.BUTTON -> stringResource(Res.string.chats_history_gate_none_title, chatName)
            ChatHistoryPhase.SEARCH -> stringResource(Res.string.chats_history_gate_search_title)
            else ->
                if (incomplete) {
                    stringResource(Res.string.chats_history_gate_incomplete_title)
                } else {
                    stringResource(Res.string.chats_history_gate_start_title, chatName)
                }
        }
    val body =
        when (phase) {
            ChatHistoryPhase.AUTO -> stringResource(Res.string.chats_history_gate_auto_body)
            ChatHistoryPhase.PAUSED ->
                if (date != null) {
                    stringResource(Res.string.chats_history_gate_loaded_to, date)
                } else {
                    stringResource(Res.string.chats_history_gate_has_more)
                }
            ChatHistoryPhase.BUTTON -> date?.let { stringResource(Res.string.chats_history_gate_none_body, it) }
            ChatHistoryPhase.SEARCH -> date?.let { stringResource(Res.string.chats_history_gate_search_body, it, chatName) }
            else ->
                if (incomplete) {
                    stringResource(Res.string.chats_history_gate_incomplete_body, stalledRelays.joinToString(", "))
                } else {
                    stringResource(Res.string.chats_history_gate_start_body)
                }
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .then(if (relayProgress.isNotEmpty()) Modifier.clickable { showRelays = true } else Modifier),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                when {
                    phase == ChatHistoryPhase.AUTO || phase == ChatHistoryPhase.SEARCH ->
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    incomplete -> HistoryGlyph("…", MaterialTheme.colorScheme.error)
                    phase == ChatHistoryPhase.END -> HistoryGlyph("✓", MaterialTheme.colorScheme.primary)
                    else -> HistoryGlyph("⋯", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (body != null) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    phase == ChatHistoryPhase.PAUSED -> GateAction(stringResource(Res.string.chats_history_gate_continue), primary = true, onResume)
                    phase == ChatHistoryPhase.BUTTON -> GateAction(stringResource(Res.string.chats_history_gate_keep_looking), primary = true, onKeepLooking)
                    phase == ChatHistoryPhase.SEARCH -> GateAction(stringResource(Res.string.chats_history_gate_stop), primary = false, onStop)
                    // A stalled relay is retried by paging it again, which is what Keep looking does.
                    incomplete -> GateAction(stringResource(Res.string.retry), primary = false, onKeepLooking)
                }
            }
        }
    }
}

@Composable
private fun HistoryGlyph(
    glyph: String,
    color: Color,
) {
    Text(glyph, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
}

@Composable
private fun GateAction(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    if (primary) {
        FilledTonalButton(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

private fun formatHistoryDay(epochSeconds: Long): String = formatDateTime(epochSeconds * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.NONE)
