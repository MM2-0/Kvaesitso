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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Print today's front page: check the reader's ranking, pull the desks through it, have
 * the reader's own AI write the page, then resolve and check it.
 *
 * Ported from NosFabrica/the-nostr-observer @ 0901b92 (MIT, see ./LICENSE.observer): the
 * `nostr-observer` skill's Steps 2–6 and `generator/.../nostr/Pull.kt`, matched to
 * Brainstorm's port (client/src/services/frontPage.ts). Nothing after the page: no
 * publishing, no Blossom, no archive.
 *
 * The corpus is NOT consumed into LocalCache, on purpose: an edition is a MEASUREMENT of
 * what one ranked read returned in one window, its edition code fingerprints exactly that
 * set, and a day of strangers' events would otherwise show up in the app's own feeds.
 */

enum class ProbeVerdict { READY, PROJECTION_PENDING }

sealed interface PrintStep {
    data object Probe : PrintStep

    class Pull(
        val desksDone: Int,
        val desksTotal: Int,
        val events: Int,
    ) : PrintStep

    /**
     * The AI writes the page: round 2 fixes what the check refused. [phase] says what can be
     * shown ([WritePhase]), [section] the heading it is on, [chars] the page's length so far,
     * [since] when this round was sent (unix seconds), for "Waiting for the model… 0:42".
     */
    data class Write(
        val round: Int,
        val chars: Int,
        val phase: WritePhase = WritePhase.WAITING,
        val section: String? = null,
        val since: Long = 0,
    ) : PrintStep

    class Check(
        val round: Int,
    ) : PrintStep
}

sealed interface PrintResult {
    class NotReady(
        val verdict: ProbeVerdict,
    ) : PrintResult

    class Printed(
        val html: String,
        val corpus: Corpus,
        val changes: List<ResolveChange>,
        val quotes: Int,
        val usage: Usage?,
        val rounds: Int,
        val headline: String? = null,
        val digestChars: Int = 0,
        /** Written from a plan (two passes); false when the plan failed and the page came from the digest alone. */
        val planned: Boolean = false,
        /** The Kitchen's recipe the page cites ([recipeAddress]), so the next editions don't run it again. */
        val recipe: String? = null,
    ) : PrintResult

    class Refused(
        val violations: List<Violation>,
        val changes: List<ResolveChange>,
        val corpus: Corpus,
        val usage: Usage?,
    ) : PrintResult
}

/** One writer call: its messages, and whether it plans or writes the page (the caller sets the output limit by it). */
class WriteCall(
    val messages: List<ChatMessage>,
    val purpose: Purpose,
) {
    enum class Purpose { PLAN, PAGE }
}

/** Repairs after the first page, each sending only what to fix: up to four page calls in all. */
const val MAX_REPAIR_ROUNDS = 3

/** How much of the plan's lead angle the print's details show. */
const val PLAN_ANGLE_SHOWN = 240

/**
 * Whether a failed plan call leaves the page to be written from the digest alone: a provider error,
 * a cut-off or empty answer, the network, a busy or down upstream. A refused or unfunded key, an
 * unknown model or a charge withheld would fail the page call too (and a withheld charge must
 * never be sent again on its own), so those end the print.
 */
fun planFailureFallsBack(failure: WriterFailure): Boolean = failure in setOf(WriterFailure.HTTP, WriterFailure.UNREACHABLE, WriterFailure.CUT_OFF, WriterFailure.EMPTY, WriterFailure.RATE_LIMITED, WriterFailure.UPSTREAM_DOWN)

/**
 * One run, start to finish. [write] is one Chat Completions call with the reader's own key: a
 * plan, then the page ([WriteCall.Purpose]); [boundary] resolves and checks. The validator is not
 * negotiable: a page still refused after [MAX_REPAIR_ROUNDS] repairs is never returned as a page.
 */
class FrontPagePress(
    private val client: INostrClient,
    private val relay: NormalizedRelayUrl,
    private val boundary: PaperBoundary,
    private val write: suspend (call: WriteCall, onEvent: (WriterEvent) -> Unit) -> Written,
    private val floor: Int = DEFAULT_TRUST_FLOOR,
    private val deskIdleMs: Long = 25_000,
    private val probeIdleMs: Long = 15_000,
    private val now: () -> Long = { TimeUtils.now() },
    private val planTimeoutMs: Long = PLAN_TIMEOUT_MS,
    /** Where this app's citations and bylines point ([PaperLinkSet]). */
    private val links: PaperLinkSet = PaperLinkSet.JUMBLE,
) {
    private suspend fun ask(
        filter: Filter,
        idleMs: Long = deskIdleMs,
    ): List<Event> = client.fetchAll(relay, filter, idleMs)

    /**
     * The Observer's link 4: the same question through the reader's ranking and without it.
     * Amethyst's own gate has already seen a rank provider in the reader's kind 10040; this
     * is what it cannot see — cards stored but not yet PROJECTED, where the observed read
     * returns nothing while the anonymous one returns notes.
     */
    suspend fun probe(
        observer: HexKey,
        since: Long,
    ): ProbeVerdict =
        coroutineScope {
            val observed = async { ask(rankedProbe(observer, since), probeIdleMs) }
            val anonymous = async { ask(rankedProbe(null, since), probeIdleMs) }
            if (anonymous.await().isNotEmpty() && observed.await().isEmpty()) ProbeVerdict.PROJECTION_PENDING else ProbeVerdict.READY
        }

    /**
     * Every desk through the reader's ranking, the control run, and the names. The Kitchen reads
     * its own 30 days and keeps one recipe, the best-ranked not among [recentRecipes].
     */
    suspend fun pull(
        observer: HexKey,
        until: Long,
        onStep: (PrintStep) -> Unit = {},
        recentRecipes: Set<String> = emptySet(),
    ): Corpus =
        coroutineScope {
            val since = until - FRONT_PAGE_WINDOW_SECONDS
            val total = DESKS.size + 1
            var done = 0
            var events = 0
            // The desks finish on whatever thread their relay answered on.
            val tally = Mutex()

            suspend fun report(n: Int) =
                tally.withLock {
                    done += 1
                    events += n
                    onStep(PrintStep.Pull(done, total, events))
                }
            // The relay has spells of not answering: five desks at a time, not fourteen.
            val gate = Semaphore(5)
            val control =
                async {
                    // The same window, no ranking, NO FLOOR.
                    gate.withPermit { ask(deskFilter(listOf(1), since, until, 400, null, floor)) }.also { report(0) }
                }
            val desks =
                DESKS
                    .map { desk ->
                        async {
                            val read = gate.withPermit { desk.belongs(observer, ask(deskFilter(desk.kinds, until - desk.lookbackSeconds, until, desk.limit, observer, floor, desk.tags))) }
                            val kept = if (desk.key == "kitchen") listOfNotNull(pickRecipe(read, recentRecipes)) else read
                            report(kept.size)
                            desk.key to kept
                        }
                    }.awaitAll()
                    .toMap()

            // Bylines, chunked: one REQ with every author of a busy day is the largest frame
            // this builds, and the relay DROPS an oversized frame in silence.
            val authors =
                desks.values
                    .flatten()
                    .flatMap { listOf(it.pubKey) + highlightAuthorKeys(it) }
                    .distinct()
            val nameGate = Semaphore(3)
            val profiles =
                authors
                    .chunked(PROFILE_CHUNK)
                    .map { some -> async { nameGate.withPermit { ask(Filter(kinds = listOf(0), authors = some, search = INCLUDE_SPAM)) } } }
                    .awaitAll()
                    .flatten()

            assemble(observer, relay.url, floor, since, until, desks, control.await(), profilesFrom(profiles), links)
        }

    suspend fun print(
        observer: HexKey,
        readerName: String,
        language: String? = null,
        log: PrintLogger = PrintLogger.None,
        context: EditionContext = EditionContext(),
        onStep: (PrintStep) -> Unit = {},
    ): PrintResult {
        val until = now()
        onStep(PrintStep.Probe)
        log.add(PrintLogKind.PROBE)
        val verdict = probe(observer, until - FRONT_PAGE_WINDOW_SECONDS)
        if (verdict != ProbeVerdict.READY) return PrintResult.NotReady(verdict).also { log.add(PrintLogKind.NOT_READY) }

        val corpus = pull(observer, until, onStep, context.recentRecipes)
        log.add(PrintLogKind.READ, numbers = listOf(DESKS.size, corpus.events.size))
        val digest = digest(corpus, clock = context.clock)
        val size = DaySize(corpus.events.size, corpus.events.mapTo(HashSet()) { it.pubKey }.size, keptInDigest(corpus))
        // The system brief and this prefix are first, and the same, in every call of the print.
        val prefix = corpusPrefix(digest, editionHeader(readerName, language, context, corpus, size))
        val system = ChatMessage("system", systemPrompt())

        var usage: Usage? = null

        // Pass 1: the plan. Anything short of a usable plan leaves today's single pass.
        val planned = plan(system, prefix, log, onStep)
        usage = planned.usage
        val plan = planned.plan
        val full = plan?.let { fullBlock(corpus, it.full, context.clock) }
        if (plan != null) {
            log.add(PrintLogKind.PLANNED, detail = plan.lead?.angle?.take(PLAN_ANGLE_SHOWN), numbers = listOf(full?.ids?.size ?: 0))
        } else {
            log.add(PrintLogKind.PLAN_FAILED)
        }

        // Pass 2: the page, then up to MAX_REPAIR_ROUNDS repairs, each sending only what to fix.
        val first = listOf(system, ChatMessage("user", "\n\n" + writeMessage(plan, full), prefix))
        var messages = first
        for (round in 1..1 + MAX_REPAIR_ROUNDS) {
            val tracker = WriteTracker(round, now(), log)
            onStep(tracker.step)
            val written = write(WriteCall(messages, WriteCall.Purpose.PAGE)) { event -> tracker.on(event)?.let(onStep) }
            usage = usage?.plus(written.usage) ?: written.usage
            log.add(PrintLogKind.WRITTEN, numbers = listOf(written.text.length))
            val page = extractDocument(written.text)
            if (page.isBlank()) throw WriterException(WriterFailure.EMPTY)

            onStep(PrintStep.Check(round))
            // The house stylesheet goes in before the check: what is checked and kept is what renders.
            val checked = boundary.resolveAndCheck(insertHouseCss(page), corpus)
            log.add(PrintLogKind.QUOTES, numbers = listOf(checked.quotes, checked.violations.count { it.kind == Violation.Kind.QUOTE }))
            if (checked.violations.isEmpty()) {
                log.add(PrintLogKind.PASSED)
                return PrintResult.Printed(checked.html, corpus, checked.changes, checked.quotes, usage, round, checked.headline, digest.length, planned = plan != null, recipe = citedRecipe(checked.html, corpus))
            }
            if (round == 1 + MAX_REPAIR_ROUNDS) return PrintResult.Refused(checked.violations, checked.changes, corpus, usage).also { log.add(PrintLogKind.REFUSED, numbers = listOf(checked.violations.size)) }
            log.add(PrintLogKind.REPAIR, numbers = listOf(checked.violations.size))
            // The writer's own page (without the inserted stylesheet) and what to fix in it; the
            // pages of earlier rounds are not sent again.
            messages = first + ChatMessage("assistant", page) + ChatMessage("user", repairMessage(checked.violations))
        }
        error("unreachable: the last round always returns")
    }

    private class Planned(
        val plan: EditionPlan?,
        val usage: Usage?,
    )

    /**
     * Pass 1: the brief, the shared prefix and the planning instruction; the answer read as a plan.
     * No plan when the answer isn't one, or the call fails in a way the page call may not
     * ([planFailureFallsBack]), or takes longer than [planTimeoutMs]. A failure the page call
     * would meet too (a refused key, no credit, a charge withheld) ends the print here.
     */
    private suspend fun plan(
        system: ChatMessage,
        prefix: String,
        log: PrintLogger,
        onStep: (PrintStep) -> Unit,
    ): Planned {
        val since = now()
        onStep(PrintStep.Write(1, 0, WritePhase.PLANNING, null, since))
        log.add(PrintLogKind.PLANNING)
        var heard = false
        val written =
            try {
                withTimeoutOrNull(planTimeoutMs) {
                    write(WriteCall(listOf(system, ChatMessage("user", "\n\n" + PLAN_INSTRUCTION, prefix)), WriteCall.Purpose.PLAN)) { event ->
                        when (event) {
                            WriterEvent.FirstByte, WriterEvent.KeepAlive ->
                                if (!heard) {
                                    heard = true
                                    log.add(PrintLogKind.FIRST_BYTE)
                                }
                            is WriterEvent.Reasoning -> log.reasoning(event.delta)
                            else -> {}
                        }
                    }
                }
            } catch (e: WriterException) {
                if (!planFailureFallsBack(e.failure)) throw e
                Log.w(TAG) { "No plan: ${e.failure} ${e.status ?: ""}" }
                null
            }
        return Planned(written?.let { parsePlan(it.text) }, written?.usage)
    }

    companion object {
        private const val TAG = "FrontPagePress"

        /** Authors per kind 0 REQ: under the relay's 262,144-byte frame with room to spare. */
        const val PROFILE_CHUNK = 2_700
    }
}

/**
 * The Kitchen's recipe, when the page cites it (a Source link to it): what this edition ran. A
 * recipe is addressable, so the page cites it by its naddr; an older page, or an older writer, by
 * its nevent.
 */
fun citedRecipe(
    html: String,
    corpus: Corpus,
): String? =
    corpus.desks["kitchen"]
        .orEmpty()
        .firstOrNull { recipe ->
            PaperLinkSet.addressOf(recipe)?.let { html.contains(it) } == true || html.contains(toNevent(recipe.id))
        }?.let(::recipeAddress)
