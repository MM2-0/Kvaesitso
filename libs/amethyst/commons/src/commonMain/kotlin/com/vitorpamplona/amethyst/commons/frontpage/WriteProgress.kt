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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What one writer call says as it goes ([chatComplete]): the request, the provider's first word, reasoning, text. */
sealed interface WriterEvent {
    /** The request went out; [streaming] false for the whole-answer retry. */
    data class Sent(
        val streaming: Boolean,
    ) : WriterEvent

    /** The first line of the answer: the provider is working on it. */
    data object FirstByte : WriterEvent

    /** A keep-alive comment (`: OPENROUTER PROCESSING`): nothing to read, but still alive. */
    data object KeepAlive : WriterEvent

    /** The model's reasoning, as it streams. */
    class Reasoning(
        val delta: String,
    ) : WriterEvent

    /** Page text: [delta] added, [total] characters so far. */
    class Text(
        val delta: String,
        val total: Int,
    ) : WriterEvent

    /** This provider sends the page all at once, at the end: nothing to follow until then. */
    data object AllAtOnce : WriterEvent
}

/** Where a writer call is, as the reader is told. */
enum class WritePhase {
    /** Pass 1: the model plans the paper (which story leads, which posts to read in full). */
    PLANNING,

    /** Sent, and nothing to show yet: "Waiting for the model…" with how long. */
    WAITING,

    /** The model's reasoning is arriving. */
    THINKING,

    /** The page is arriving: "Writing: <the section it is on>". */
    WRITING,

    /** The provider doesn't stream: the page comes whole, at the end. */
    ALL_AT_ONCE,
}

private val HEADING = Regex("<(h[1-3])\\b[^>]*>([\\s\\S]*?)</\\1\\s*>", RegexOption.IGNORE_CASE)
private val KICKER = Regex("<([a-z][a-z0-9]*)\\b[^>]*\\bclass\\s*=\\s*[\"'][^\"']*\\bkicker\\b[^\"']*[\"'][^>]*>([\\s\\S]*?)</\\1\\s*>", RegexOption.IGNORE_CASE)
private val TAG = Regex("<[^>]*>")
private val SPACE = Regex("\\s+")

/** How far back [latestSection] looks for a heading still being closed. */
private const val SECTION_WINDOW = 4_000

/** The page text that must arrive between two [latestSection] scans of a stream. */
const val SECTION_SCAN_CHARS = 200

/** A section's name as the progress line shows it, at most this long. */
const val SECTION_MAX_CHARS = 80

/**
 * The section the page so far is on: the last complete `<h1>`–`<h3>` or desk kicker in [html],
 * as plain text, cut to [SECTION_MAX_CHARS]; null before the first. The masthead isn't a section.
 * Looks only at the last [SECTION_WINDOW] characters from [from] on, so a stream rescans little.
 */
fun latestSection(
    html: CharSequence,
    from: Int = 0,
): String? {
    val start = maxOf(from, html.length - SECTION_WINDOW, 0)
    val window = html.subSequence(start, html.length)
    var best: MatchResult? = null
    for (regex in listOf(HEADING, KICKER)) {
        regex.findAll(window).forEach { match ->
            val text = plainText(match.groupValues[2])
            if (text.isNotEmpty() && !text.equals(MASTHEAD, ignoreCase = true) && (best == null || match.range.last > best!!.range.last)) best = match
        }
    }
    return best?.let { cut(plainText(it.groupValues[2])) }
}

private fun plainText(html: String): String =
    html
        .replace(TAG, " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&rsquo;", "\u2019")
        .replace("&lsquo;", "\u2018")
        .replace("&mdash;", "\u2014")
        .replace(SPACE, " ")
        .trim()

private fun cut(text: String) = if (text.length <= SECTION_MAX_CHARS) text else text.take(SECTION_MAX_CHARS - 1).trimEnd() + "\u2026"

/**
 * One writer call's progress, from its [WriterEvent]s: waiting (with when it started, [since]),
 * thinking, writing (and on which section) or all at once; and the log lines they make. [on]
 * returns the new step when the reader would see something new, else null.
 */
class WriteTracker(
    val round: Int,
    private val since: Long,
    private val log: PrintLogger = PrintLogger.None,
) {
    var step = PrintStep.Write(round, 0, WritePhase.WAITING, null, since)
        private set

    private val page = StringBuilder()
    private var heard = false

    /** The page's length when [latestSection] last ran, and whether a tag has closed since. */
    private var scannedAt = 0
    private var closedSince = false

    fun on(event: WriterEvent): PrintStep.Write? =
        when (event) {
            is WriterEvent.Sent -> {
                log.add(PrintLogKind.REQUEST_SENT, numbers = listOf(round))
                null
            }
            WriterEvent.FirstByte, WriterEvent.KeepAlive -> {
                if (!heard) {
                    heard = true
                    log.add(PrintLogKind.FIRST_BYTE)
                }
                null
            }
            WriterEvent.AllAtOnce ->
                if (step.phase == WritePhase.ALL_AT_ONCE) {
                    null
                } else {
                    log.add(PrintLogKind.ALL_AT_ONCE)
                    next(step.copy(phase = WritePhase.ALL_AT_ONCE))
                }
            is WriterEvent.Reasoning -> {
                log.reasoning(event.delta)
                if (step.phase == WritePhase.WAITING) {
                    log.add(PrintLogKind.THINKING)
                    next(step.copy(phase = WritePhase.THINKING))
                } else {
                    null
                }
            }
            is WriterEvent.Text -> text(event)
        }

    private fun text(event: WriterEvent.Text): PrintStep.Write? {
        page.append(event.delta)
        var shown = step.copy(chars = event.total)
        if (step.phase == WritePhase.WAITING || step.phase == WritePhase.THINKING) {
            log.add(PrintLogKind.WRITING)
            shown = shown.copy(phase = WritePhase.WRITING)
        }
        // A heading closes on a '>': no new section can have ended in a chunk without one. The
        // regexes run once per [SECTION_SCAN_CHARS] of page at most, not on every chunk.
        if (event.delta.contains('>')) closedSince = true
        if (closedSince && page.length - scannedAt >= SECTION_SCAN_CHARS) {
            closedSince = false
            scannedAt = page.length
            val section = latestSection(page)
            if (section != null && section != step.section) {
                log.add(PrintLogKind.SECTION, detail = section)
                shown = shown.copy(section = section)
            }
        }
        return next(shown)
    }

    /** The page's text so far (all of it, once the call returned). */
    val text: CharSequence get() = page

    private fun next(shown: PrintStep.Write): PrintStep.Write? {
        if (shown == step) return null
        step = shown
        return shown
    }
}

/** What a log line says; the UI words it, with [PrintLogEntry.detail] and [PrintLogEntry.numbers]. */
enum class PrintLogKind {
    /** Provider and model asked: detail "OpenRouter · anthropic/claude-sonnet-4.5". */
    ASKED,
    PROBE,

    /** Pass 1 sent: the model plans the paper. */
    PLANNING,

    /** The plan came back: detail = the lead's angle, numbers = [posts sent in full]. */
    PLANNED,

    /** No plan came back (not JSON, an error, too slow): the page is written from the digest alone. */
    PLAN_FAILED,

    /** The desks read: numbers = [desks, posts]. */
    READ,

    /** numbers = [round]. */
    REQUEST_SENT,
    FIRST_BYTE,
    THINKING,
    WRITING,
    ALL_AT_ONCE,

    /** detail = the section's heading. */
    SECTION,

    /** numbers = [characters]. */
    WRITTEN,

    /** numbers = [quotes verified, quotes taken out]. */
    QUOTES,

    /** numbers = [problems sent back]. */
    REPAIR,
    PASSED,

    /** numbers = [problems]. */
    REFUSED,
    NOT_READY,
    PUBLISHING,
    PUBLISHED,
    PUBLISH_FAILED,

    /** detail = the [WriterFailure]'s name, numbers = [HTTP status] when there was one. */
    FAILED,
}

@Immutable
data class PrintLogEntry(
    /** Unix seconds. */
    val at: Long,
    val kind: PrintLogKind,
    val detail: String? = null,
    val numbers: List<Int> = emptyList(),
)

/** The current print's log, newest last, and the tail of the model's reasoning. In memory only. */
@Immutable
data class PrintLog(
    val entries: List<PrintLogEntry> = emptyList(),
    val reasoning: String = "",
    /**
     * How many characters of reasoning have arrived in all, as published: it keeps growing after
     * [reasoning] is capped at [PrintLogBook.REASONING_KEPT], so a view that follows the end keys on it.
     */
    val reasoningReceived: Long = 0,
)

/** Where a print writes its log lines. */
interface PrintLogger {
    fun add(
        kind: PrintLogKind,
        detail: String? = null,
        numbers: List<Int> = emptyList(),
    )

    fun reasoning(delta: String)

    object None : PrintLogger {
        override fun add(
            kind: PrintLogKind,
            detail: String?,
            numbers: List<Int>,
        ) {}

        override fun reasoning(delta: String) {}
    }
}

/**
 * The log of the print running now ([log]), cleared when the next one starts ([start]). The
 * reasoning keeps its last [REASONING_KEPT] characters, published at most every [REASONING_STEP]
 * new ones so a screen showing it isn't redrawn per token.
 */
class PrintLogBook(
    private val now: () -> Long,
) : PrintLogger {
    private val _log = MutableStateFlow(PrintLog())
    val log: StateFlow<PrintLog> = _log.asStateFlow()

    // Written by one stream at a time (a print's calls run one after another).
    private val reasoning = StringBuilder()
    private var unpublished = 0
    private var received = 0L

    fun start(asked: String) {
        reasoning.clear()
        unpublished = 0
        received = 0
        _log.value = PrintLog(listOf(PrintLogEntry(now(), PrintLogKind.ASKED, asked)))
    }

    override fun add(
        kind: PrintLogKind,
        detail: String?,
        numbers: List<Int>,
    ) {
        flushReasoning()
        _log.update { it.copy(entries = it.entries + PrintLogEntry(now(), kind, detail, numbers)) }
    }

    override fun reasoning(delta: String) {
        reasoning.append(delta)
        if (reasoning.length > REASONING_KEPT * 2) reasoning.deleteRange(0, reasoning.length - REASONING_KEPT)
        unpublished += delta.length
        received += delta.length
        if (unpublished >= REASONING_STEP) flushReasoning()
    }

    private fun flushReasoning() {
        if (unpublished == 0) return
        unpublished = 0
        val tail = if (reasoning.length > REASONING_KEPT) reasoning.substring(reasoning.length - REASONING_KEPT) else reasoning.toString()
        val total = received
        _log.update { it.copy(reasoning = tail, reasoningReceived = total) }
    }

    companion object {
        const val REASONING_KEPT = 4_000
        const val REASONING_STEP = 160
    }
}
