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
package com.vitorpamplona.amethyst.commons.frontpage

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where a print is, as coarse as the background-work popup and the notification say it. */
@Immutable
sealed interface PrintJobStage {
    /** Asking the search relay whether the reader's rankings are ready. */
    data object Probe : PrintJobStage

    /** Reading the posts, desk by desk. */
    data class Reading(
        val done: Int,
        val total: Int,
    ) : PrintJobStage {
        val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    /**
     * The AI writes the page ([round] 2 is the repair after a failed check): waiting for it,
     * thinking, on [section], or all at once ([phase]); [since] is when the round was sent.
     */
    data class Writing(
        val round: Int,
        val phase: WritePhase = WritePhase.WAITING,
        val section: String? = null,
        val since: Long = 0,
    ) : PrintJobStage

    /** Checking quotes, pictures and links. */
    data object Checking : PrintJobStage

    /** The clean page is kept; it goes out to the reader's Observer site. */
    data object Publishing : PrintJobStage

    companion object {
        /** The stage of [step]: a new value only when the popup would say something new. */
        fun of(step: PrintStep): PrintJobStage =
            when (step) {
                PrintStep.Probe -> Probe
                is PrintStep.Pull -> Reading(step.desksDone, step.desksTotal)
                is PrintStep.Write -> Writing(step.round, step.phase, step.section, step.since)
                is PrintStep.Check -> Checking
            }
    }
}

/** How a print ended, for the popup and the notification. */
@Immutable
sealed interface PrintJobEnd {
    /** A clean page, kept as the edition of [day]. */
    data class Ready(
        val day: String,
        val headline: String?,
    ) : PrintJobEnd

    /** The writer failed: the key, the provider, the network. */
    data class Failed(
        val failure: WriterFailure,
        val status: Int? = null,
        /** For [WriterFailure.LOW_BALANCE]: the sats a Routstr key is short, when the node said. */
        val shortfallSats: Long? = null,
    ) : PrintJobEnd

    /** The reader's rankings aren't ready yet. */
    data object NotReady : PrintJobEnd

    /** The page didn't pass the check: nothing was kept. */
    data object Refused : PrintJobEnd
}

/**
 * A print as background work: whose it is, who asked for it, since when (unix seconds), where it
 * is, and, once it ended, how. The printer publishes one per print; the popup shows it while the
 * reader is elsewhere, and a platform keeps the process alive while a manual one runs.
 */
@Immutable
data class FrontPagePrintJob(
    val account: HexKey,
    val trigger: PrintTrigger,
    val startedAt: Long,
    val stage: PrintJobStage = PrintJobStage.Probe,
    val end: PrintJobEnd? = null,
    val endedAt: Long? = null,
    /** The reader put the popup away while it runs; opening The Observer brings it back, and so does its end. */
    val hidden: Boolean = false,
) {
    val running: Boolean get() = end == null

    /** A print the reader started that is still running: what a platform keeps alive. */
    val runningManual: Boolean get() = running && trigger == PrintTrigger.MANUAL
}

/** How [run] ended, as a [PrintJobEnd]; null while it runs or when nothing ended (cancelled, a lost claim). */
fun endOf(
    run: FrontPageRun,
    day: String?,
    headline: String?,
): PrintJobEnd? =
    when (run) {
        is FrontPageRun.Failed -> PrintJobEnd.Failed(run.failure, run.status, run.shortfallSats)
        is FrontPageRun.Done ->
            when (run.result) {
                is PrintResult.Printed -> day?.let { PrintJobEnd.Ready(it, headline) }
                is PrintResult.NotReady -> PrintJobEnd.NotReady
                is PrintResult.Refused -> PrintJobEnd.Refused
            }
        else -> null
    }

/**
 * What a platform does around a print the reader started, beside the in-app popup: Android runs
 * a foreground service while it prints, so leaving the app doesn't freeze or kill it. Called on
 * the printer's thread; implementations only hand off.
 */
interface FrontPagePrintHost {
    /** A manual print for [account] started. */
    fun printStarted(account: HexKey)

    /** That print ended, however it ended (cancelled included). */
    fun printEnded(account: HexKey)

    object None : FrontPagePrintHost {
        override fun printStarted(account: HexKey) {}

        override fun printEnded(account: HexKey) {}
    }
}

/**
 * One account's [FrontPagePrintJob] through a print: started, its stages, how it ended, and the
 * [host] told when a manual print starts and ends. Every change goes to [onJob] as well. Kept
 * apart from [FrontPagePrinter] so the job's life is tested without an account.
 */
class FrontPagePrintJobTracker(
    private val account: HexKey,
    private val host: FrontPagePrintHost = FrontPagePrintHost.None,
    private val onJob: (FrontPagePrintJob?) -> Unit = {},
    private val now: () -> Long,
) {
    private val _job = MutableStateFlow<FrontPagePrintJob?>(null)
    val job: StateFlow<FrontPagePrintJob?> = _job.asStateFlow()

    private fun publish(next: FrontPagePrintJob?) {
        _job.value = next
        onJob(next)
    }

    fun started(trigger: PrintTrigger) {
        publish(FrontPagePrintJob(account, trigger, now()))
        if (trigger == PrintTrigger.MANUAL) host.printStarted(account)
    }

    /** A new [step]; the job changes only when its stage does. */
    fun step(step: PrintStep) {
        val stage = PrintJobStage.of(step)
        _job.value?.takeIf { it.running && it.stage != stage }?.let { publish(it.copy(stage = stage)) }
    }

    /** How [ended] went, kept on the job until [dismiss]; null (and no job) when nothing ended. */
    fun ended(
        ended: FrontPageRun,
        kept: SavedEdition?,
    ): PrintJobEnd? {
        val end = endOf(ended, kept?.day, kept?.headline)
        publish(if (end == null) null else _job.value?.copy(end = end, endedAt = now(), hidden = false))
        return end
    }

    /** The popup's ×: the print goes on, out of sight, until The Observer is opened or it ends. */
    fun hide() {
        _job.value?.takeIf { it.running && !it.hidden }?.let { publish(it.copy(hidden = true)) }
    }

    /** The Observer is showing: a hidden print is shown again once the reader leaves it. */
    fun unhide() {
        _job.value?.takeIf { it.hidden }?.let { publish(it.copy(hidden = false)) }
    }

    /** The kept page is going out to the reader's Observer site: the job lives until it is out. */
    fun publishing() {
        _job.value?.takeIf { it.running && it.stage != PrintJobStage.Publishing }?.let { publish(it.copy(stage = PrintJobStage.Publishing)) }
    }

    /** Cancelled, or a print whose page another device's claim won: no job left. */
    fun dropped() = publish(null)

    /** The print let go of the press, however it ended. */
    fun finished(trigger: PrintTrigger) {
        if (trigger == PrintTrigger.MANUAL) host.printEnded(account)
    }

    /** The reader saw how it ended: it goes. A print still running stays. */
    fun dismiss() {
        if (_job.value?.running == false) publish(null)
    }

    companion object {
        /**
         * Whether a print that ended with [end] tells the reader outside the screen: a manual
         * print that gave no paper while no front page screen was showing. A clean page is
         * announced where it is kept; a scheduled run has its own rules.
         */
        fun tellsReaderOfFailure(
            end: PrintJobEnd?,
            trigger: PrintTrigger,
            watched: Int,
        ): Boolean = end != null && end !is PrintJobEnd.Ready && trigger == PrintTrigger.MANUAL && watched == 0
    }
}
