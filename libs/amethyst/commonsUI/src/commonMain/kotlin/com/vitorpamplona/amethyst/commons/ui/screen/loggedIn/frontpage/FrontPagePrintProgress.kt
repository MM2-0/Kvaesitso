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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.frontpage.FrontPagePrintJob
import com.vitorpamplona.amethyst.commons.frontpage.FrontPagePrinter
import com.vitorpamplona.amethyst.commons.frontpage.PrintJobEnd
import com.vitorpamplona.amethyst.commons.frontpage.PrintJobStage
import com.vitorpamplona.amethyst.commons.frontpage.PrintLog
import com.vitorpamplona.amethyst.commons.frontpage.WritePhase
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dismiss
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_check
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_fix
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_probe
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_publish
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_pull
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_thinking
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_waiting
import com.vitorpamplona.amethyst.commons.resources.front_page_banner_write
import com.vitorpamplona.amethyst.commons.resources.front_page_job_failed
import com.vitorpamplona.amethyst.commons.resources.front_page_job_hide
import com.vitorpamplona.amethyst.commons.resources.front_page_job_tap_to_open
import com.vitorpamplona.amethyst.commons.resources.front_page_ready_title
import com.vitorpamplona.amethyst.commons.resources.front_page_show_details
import com.vitorpamplona.amethyst.commons.resources.front_page_step_planning
import com.vitorpamplona.amethyst.commons.resources.front_page_step_writing_section
import com.vitorpamplona.amethyst.commons.ui.broadcast.formatElapsedTime
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** How long the popup shows how a print ended before it goes on its own. */
private const val END_SHOWN_MS = 8_000L

/**
 * The account's print as background work, for the app-wide popup: while it runs and the reader
 * is not on the front page, then how it ended, briefly. On the front page itself it is null (the
 * screen shows the print and the paper), and an ended print is let go there.
 */
@Composable
fun rememberBackgroundPrint(accountViewModel: AccountViewModel): Pair<FrontPagePrinter, FrontPagePrintJob>? {
    val printer = LocalAppServices.current.frontPagePrinters?.of(accountViewModel.account) ?: return null
    val job by printer.job.collectAsStateWithLifecycle()
    val watched by printer.watched.collectAsStateWithLifecycle()

    // Seen on the front page, or shown here long enough: an ended print goes.
    val ended = job?.takeIf { !it.running }
    LaunchedEffect(ended, watched) {
        if (ended == null) return@LaunchedEffect
        if (watched == 0) delay(END_SHOWN_MS)
        printer.dismissJob()
    }
    // Hidden by its ×: out of sight until The Observer is opened, or the print ends.
    return job?.takeIf { watched == 0 && !it.hidden }?.let { printer to it }
}

/**
 * One print in the background-work popup: its step and how long it has run (a bar that fills while
 * the desks are read), or how it ended. Tapping it opens the front page. × only puts the popup
 * away (a running print goes on, and comes back on The Observer, where it can be cancelled with a
 * confirmation): a paid print is never one tap from being thrown away.
 */
@Composable
fun FrontPagePrintJobContent(
    job: FrontPagePrintJob,
    onOpen: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
    /** The print's details, for a sheet the popup opens: the popup itself has no room for them. */
    log: StateFlow<PrintLog>? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var details by remember { mutableStateOf(false) }
    if (details && log != null) {
        val shown by log.collectAsStateWithLifecycle()
        PrintDetailsSheet(shown, onDismiss = { details = false })
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                when (val end = job.end) {
                    // The one moving indicator while it runs: the bar below only fills while the desks are read.
                    null -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = scheme.primary)
                    is PrintJobEnd.Ready -> Icon(MaterialSymbols.CheckCircle, contentDescription = null, tint = scheme.allGoodColor, modifier = Modifier.size(18.dp), filled = true)
                    else -> Icon(MaterialSymbols.Error, contentDescription = null, tint = scheme.error, modifier = Modifier.size(18.dp), filled = true)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    headline(job),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Ready or failed is read out when it lands; the steps aren't.
                    modifier = if (job.running) Modifier else Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                when (val end = job.end) {
                    null -> ElapsedText(job.startedAt, stringRes(Res.string.front_page_job_tap_to_open))
                    is PrintJobEnd.Ready -> DetailText(end.headline?.takeIf { it.isNotBlank() } ?: stringRes(Res.string.front_page_job_tap_to_open))
                    // Why, in a sentence of its own: never a fragment glued into the headline.
                    else -> DetailText(printEndShort(end) ?: stringRes(Res.string.front_page_job_tap_to_open))
                }
            }
            if (log != null) {
                IconButton(onClick = { details = true }, modifier = Modifier.size(22.dp)) {
                    Icon(MaterialSymbols.Info, contentDescription = stringRes(Res.string.front_page_show_details), tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                }
            }
            val running = job.running
            IconButton(onClick = if (running) onHide else onDismiss, modifier = Modifier.size(22.dp)) {
                Icon(
                    MaterialSymbols.Close,
                    contentDescription = stringRes(if (running) Res.string.front_page_job_hide else Res.string.dismiss),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        val stage = job.stage
        if (job.running && stage is PrintJobStage.Reading && stage.total > 0) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { stage.fraction }, modifier = Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.surfaceVariant)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DetailText(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * "1:23 • [suffix]": the only part of the popup that changes each second, so only it recomposes,
 * and only while the screen is started (a stopped app doesn't wake every second for it).
 */
@Composable
private fun ElapsedText(
    startedAt: Long,
    suffix: String,
) {
    var started by remember { mutableStateOf(true) }
    LifecycleStartEffect(Unit) {
        started = true
        onStopOrDispose { started = false }
    }
    val elapsed by produceState((TimeUtils.now() - startedAt).coerceAtLeast(0), startedAt, started) {
        while (started) {
            value = (TimeUtils.now() - startedAt).coerceAtLeast(0)
            delay(1_000)
        }
    }
    DetailText("${formatElapsedTime(elapsed)} • $suffix")
}

@Composable
private fun headline(job: FrontPagePrintJob): String =
    when (val end = job.end) {
        null -> printStageText(job.stage)
        is PrintJobEnd.Ready -> stringRes(Res.string.front_page_ready_title)
        else -> stringRes(Res.string.front_page_job_failed)
    }

/** "Writing your Observer…": a print's step in the words the popup and the screen share. */
@Composable
fun printStageText(stage: PrintJobStage): String =
    when (stage) {
        PrintJobStage.Probe -> stringRes(Res.string.front_page_banner_probe)
        is PrintJobStage.Reading -> stringRes(Res.string.front_page_banner_pull, stage.done, stage.total)
        is PrintJobStage.Writing ->
            when {
                stage.phase == WritePhase.PLANNING -> stringRes(Res.string.front_page_step_planning)
                stage.round > 1 -> stringRes(Res.string.front_page_banner_fix)
                stage.phase == WritePhase.WAITING -> stringRes(Res.string.front_page_banner_waiting)
                stage.phase == WritePhase.THINKING -> stringRes(Res.string.front_page_banner_thinking)
                stage.phase == WritePhase.WRITING && stage.section != null -> stringRes(Res.string.front_page_step_writing_section, stage.section!!)
                else -> stringRes(Res.string.front_page_banner_write)
            }
        PrintJobStage.Checking -> stringRes(Res.string.front_page_banner_check)
        PrintJobStage.Publishing -> stringRes(Res.string.front_page_banner_publish)
    }
