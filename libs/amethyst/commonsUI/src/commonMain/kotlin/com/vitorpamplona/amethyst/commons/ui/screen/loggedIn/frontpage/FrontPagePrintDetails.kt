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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.frontpage.PrintLog
import com.vitorpamplona.amethyst.commons.frontpage.PrintLogEntry
import com.vitorpamplona.amethyst.commons.frontpage.PrintLogKind
import com.vitorpamplona.amethyst.commons.frontpage.PrintStep
import com.vitorpamplona.amethyst.commons.frontpage.WritePhase
import com.vitorpamplona.amethyst.commons.frontpage.WriterFailure
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.front_page_hide_details
import com.vitorpamplona.amethyst.commons.resources.front_page_log_all_at_once
import com.vitorpamplona.amethyst.commons.resources.front_page_log_asked
import com.vitorpamplona.amethyst.commons.resources.front_page_log_first_byte
import com.vitorpamplona.amethyst.commons.resources.front_page_log_passed
import com.vitorpamplona.amethyst.commons.resources.front_page_log_plan_failed
import com.vitorpamplona.amethyst.commons.resources.front_page_log_planned
import com.vitorpamplona.amethyst.commons.resources.front_page_log_published
import com.vitorpamplona.amethyst.commons.resources.front_page_log_quotes
import com.vitorpamplona.amethyst.commons.resources.front_page_log_read
import com.vitorpamplona.amethyst.commons.resources.front_page_log_refused
import com.vitorpamplona.amethyst.commons.resources.front_page_log_repair
import com.vitorpamplona.amethyst.commons.resources.front_page_log_request_sent
import com.vitorpamplona.amethyst.commons.resources.front_page_log_section
import com.vitorpamplona.amethyst.commons.resources.front_page_log_thinking
import com.vitorpamplona.amethyst.commons.resources.front_page_log_writing
import com.vitorpamplona.amethyst.commons.resources.front_page_log_written
import com.vitorpamplona.amethyst.commons.resources.front_page_print_details
import com.vitorpamplona.amethyst.commons.resources.front_page_publish_failed
import com.vitorpamplona.amethyst.commons.resources.front_page_publishing
import com.vitorpamplona.amethyst.commons.resources.front_page_reason_not_ready
import com.vitorpamplona.amethyst.commons.resources.front_page_reasoning
import com.vitorpamplona.amethyst.commons.resources.front_page_show_details
import com.vitorpamplona.amethyst.commons.resources.front_page_step_all_at_once
import com.vitorpamplona.amethyst.commons.resources.front_page_step_fixing
import com.vitorpamplona.amethyst.commons.resources.front_page_step_planning
import com.vitorpamplona.amethyst.commons.resources.front_page_step_probe
import com.vitorpamplona.amethyst.commons.resources.front_page_step_thinking
import com.vitorpamplona.amethyst.commons.resources.front_page_step_waiting
import com.vitorpamplona.amethyst.commons.resources.front_page_step_writing
import com.vitorpamplona.amethyst.commons.resources.front_page_step_writing_section
import com.vitorpamplona.amethyst.commons.ui.broadcast.formatElapsedTime
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The writing step in words: "Waiting for the model… 0:42" until the first byte, "Thinking…" while
 * reasoning streams, "Writing: <section>" as the page arrives, "Fixing the page" for the repair
 * round, and, for a provider that sends the page whole, what to expect instead of a counter.
 */
@Composable
fun writeStepText(step: PrintStep.Write): String =
    when {
        step.phase == WritePhase.PLANNING -> stringRes(Res.string.front_page_step_planning)
        step.phase == WritePhase.ALL_AT_ONCE -> stringRes(Res.string.front_page_step_all_at_once)
        step.round > 1 -> stringRes(Res.string.front_page_step_fixing)
        step.phase == WritePhase.WAITING -> stringRes(Res.string.front_page_step_waiting, formatElapsedTime(rememberElapsed(step.since)))
        step.phase == WritePhase.THINKING -> stringRes(Res.string.front_page_step_thinking)
        step.section != null -> stringRes(Res.string.front_page_step_writing_section, step.section!!)
        else -> stringRes(Res.string.front_page_step_writing)
    }

/** Seconds since [since], ticking each second while the screen is started. */
@Composable
fun rememberElapsed(since: Long): Long {
    var started by rememberSaveable { mutableStateOf(true) }
    LifecycleStartEffect(Unit) {
        started = true
        onStopOrDispose { started = false }
    }
    val elapsed by produceState((TimeUtils.now() - since).coerceAtLeast(0), since, started) {
        while (started) {
            value = (TimeUtils.now() - since).coerceAtLeast(0)
            delay(1_000)
        }
    }
    return elapsed
}

/**
 * "Show details": the print's log, collapsed until asked for. Collected here, and in full only
 * while open: a closed toggle redraws only when the log starts or stops having lines.
 */
@Composable
fun PrintDetailsToggle(
    log: StateFlow<PrintLog>,
    modifier: Modifier = Modifier,
) {
    val hasEntries by remember(log) { log.map { it.entries.isNotEmpty() }.distinctUntilChanged() }.collectAsStateWithLifecycle(log.value.entries.isNotEmpty())
    if (!hasEntries) return
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        TextButton(onClick = { open = !open }) {
            Text(stringRes(if (open) Res.string.front_page_hide_details else Res.string.front_page_show_details))
            Icon(if (open) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore, contentDescription = null)
        }
        // A long print's log scrolls in its place rather than pushing the page away.
        if (open) {
            val shown by log.collectAsStateWithLifecycle()
            PrintDetails(shown, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 4.dp, bottom = 8.dp))
        }
    }
}

/** The print's details in a sheet: for the popup, which has no room for them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintDetailsSheet(
    log: PrintLog,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringRes(Res.string.front_page_print_details), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            PrintDetails(log)
        }
    }
}

/**
 * The live log, newest last: each line with how far into the print it came, then the model's
 * reasoning as it streams, in a scrolling monospace block that follows its end.
 */
@Composable
fun PrintDetails(
    log: PrintLog,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val start = log.entries.firstOrNull()?.at ?: 0L
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        log.entries.forEach { entry ->
            Row {
                Text(
                    formatElapsedTime((entry.at - start).coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp).padding(top = 2.dp),
                )
                Text(logLine(entry), style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
            }
        }
        if (log.reasoning.isNotEmpty()) {
            Text(stringRes(Res.string.front_page_reasoning), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            val scroll = rememberScrollState()
            // Follows the newest reasoning, as a terminal does.
            // Keyed on how much has arrived, not the length shown: that stops growing once capped.
            LaunchedEffect(log.reasoningReceived) { scroll.scrollTo(scroll.maxValue) }
            Text(
                log.reasoning,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = scheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(scheme.surfaceContainerHighest)
                        .verticalScroll(scroll)
                        .padding(10.dp),
            )
        }
    }
}

@Composable
private fun logLine(entry: PrintLogEntry): String {
    fun n(i: Int) = entry.numbers.getOrElse(i) { 0 }
    return when (entry.kind) {
        PrintLogKind.ASKED -> stringRes(Res.string.front_page_log_asked, entry.detail.orEmpty())
        PrintLogKind.PROBE -> stringRes(Res.string.front_page_step_probe)
        PrintLogKind.PLANNING -> stringRes(Res.string.front_page_step_planning)
        PrintLogKind.PLANNED -> stringRes(Res.string.front_page_log_planned, n(0), entry.detail.orEmpty())
        PrintLogKind.PLAN_FAILED -> stringRes(Res.string.front_page_log_plan_failed)
        PrintLogKind.READ -> stringRes(Res.string.front_page_log_read, n(0), formatGrouped(n(1).toLong()))
        PrintLogKind.REQUEST_SENT -> stringRes(Res.string.front_page_log_request_sent, n(0))
        PrintLogKind.FIRST_BYTE -> stringRes(Res.string.front_page_log_first_byte)
        PrintLogKind.THINKING -> stringRes(Res.string.front_page_log_thinking)
        PrintLogKind.WRITING -> stringRes(Res.string.front_page_log_writing)
        PrintLogKind.ALL_AT_ONCE -> stringRes(Res.string.front_page_log_all_at_once)
        PrintLogKind.SECTION -> stringRes(Res.string.front_page_log_section, entry.detail.orEmpty())
        PrintLogKind.WRITTEN -> stringRes(Res.string.front_page_log_written, formatGrouped(n(0).toLong()))
        PrintLogKind.QUOTES -> stringRes(Res.string.front_page_log_quotes, n(0), n(1))
        PrintLogKind.REPAIR -> stringRes(Res.string.front_page_log_repair, n(0))
        PrintLogKind.PASSED -> stringRes(Res.string.front_page_log_passed)
        PrintLogKind.REFUSED -> stringRes(Res.string.front_page_log_refused, n(0))
        PrintLogKind.NOT_READY -> stringRes(Res.string.front_page_reason_not_ready)
        PrintLogKind.PUBLISHING -> stringRes(Res.string.front_page_publishing)
        PrintLogKind.PUBLISHED -> stringRes(Res.string.front_page_log_published)
        PrintLogKind.PUBLISH_FAILED -> stringRes(Res.string.front_page_publish_failed)
        PrintLogKind.FAILED -> {
            val failure = WriterFailure.entries.firstOrNull { it.name == entry.detail } ?: WriterFailure.UNREACHABLE
            if (failure == WriterFailure.HTTP) stringRes(failure.sentenceRes(), n(0)) else stringRes(failure.sentenceRes())
        }
    }
}
