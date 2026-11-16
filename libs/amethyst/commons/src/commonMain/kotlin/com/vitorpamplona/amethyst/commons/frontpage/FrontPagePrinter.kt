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
import com.vitorpamplona.amethyst.commons.agents.AgentProviderPresets
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.localPrivKeyOrNull
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.SyncedSchedule
import com.vitorpamplona.amethyst.commons.routstr.RoutstrPricing
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverEdition
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverParams
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSite
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.AgentKeyEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronExpression
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.SimpleCron
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

/** Who asked for a print: the reader, or their morning schedule. */
enum class PrintTrigger { MANUAL, SCHEDULED }

/** What a scheduled run could not get past on its own. */
enum class ScheduledOutcome {
    PRINTED,
    FAILED,
    NO_KEY,
    NO_LENS,
    NEEDS_SIGNER,
    UNSUPPORTED,
    BUSY,

    /** Today's edition already exists (a manual print, another device, Brainstorm): done for today. */
    ALREADY_PRINTED,

    /** This device doesn't print the mornings, the schedule is off, or it can't be run here. */
    NOT_RUNNER,

    /** No occurrence has come yet. */
    NOT_DUE,

    /** Another device (or this one, earlier) claimed this occurrence. */
    ALREADY_CLAIMED,

    /** Another device's claim won, before or during the print. */
    LOST_CLAIM,

    /** Not enough of the reader's relays answered to claim safely. */
    NO_QUORUM,

    /**
     * This install already ran this occurrence and the run ended (it failed, or was stopped): it
     * isn't run again on its own, since the failed run may have been paid for. The reader can
     * still print by hand.
     */
    ALREADY_ATTEMPTED,

    /** The reader cancelled the print, or the platform stopped the run. */
    CANCELLED,
}

/** What a platform shows outside the app when a paper is ready, or needs the reader. */
interface FrontPageNotifier {
    suspend fun paperReady(
        account: HexKey,
        edition: SavedEdition,
    )

    /** A scheduled edition could not print on its own: the reader's signer, key or lens needs them. */
    suspend fun needsYou(account: HexKey)

    /** A print the reader started gave no paper ([end] says why) while they weren't looking. */
    suspend fun printFailed(
        account: HexKey,
        end: PrintJobEnd,
    ) {}

    object None : FrontPageNotifier {
        override suspend fun paperReady(
            account: HexKey,
            edition: SavedEdition,
        ) {}

        override suspend fun needsYou(account: HexKey) {}
    }
}

/**
 * Why a scheduled print can't start, or null when it can, decided before any relay is asked.
 * Only a key held on this device decrypts the AI key with nobody there; the account's signer is
 * always wrapped (client tag, metering), so it is unwrapped before the check.
 */
fun unattendedBlocker(
    signer: NostrSigner,
    boundary: PaperBoundary?,
    busy: Boolean,
): ScheduledOutcome? =
    when {
        boundary == null -> ScheduledOutcome.UNSUPPORTED
        signer.localPrivKeyOrNull() == null -> ScheduledOutcome.NEEDS_SIGNER
        busy -> ScheduledOutcome.BUSY
        else -> null
    }

/** What stands between the reader and a paper, from the key, the lens and the platform. */
fun gateOf(
    key: AgentKeyTag?,
    hasLens: Boolean,
    boundary: PaperBoundary?,
): FrontPageGate =
    when {
        boundary == null -> FrontPageGate.UNSUPPORTED
        key == null -> FrontPageGate.NO_KEY
        !hasLens -> FrontPageGate.NO_LENS
        else -> FrontPageGate.READY
    }

/**
 * One account's front page, owned by the account rather than a screen: a print runs on the
 * account's scope, so leaving the screen (or the app, on desktop) never cancels a job that takes
 * minutes and costs the reader money. Screens, the shell's banner and the scheduler all observe
 * the same [run]. Clean pages are kept in [FrontPageEditionStore].
 */
class FrontPagePrinter(
    val account: Account,
    private val http: IRoleBasedHttpClientBuilder,
    val memory: FrontPageModelMemory,
    private val store: FrontPageEditionStore,
    private val notifier: FrontPageNotifier,
    val boundary: PaperBoundary? = paperBoundary(),
    private val scope: CoroutineScope = account.scope,
    private val now: () -> Long = { TimeUtils.now() },
    private val observerPrefs: ObserverPrefsStore = ObserverPrefsStore.None,
    /** The synced morning schedule; null runs every scheduled call it gets (tests). */
    val morning: FrontPageMorning? = null,
    observerIO: ObserverSiteIO? = observerSiteIO(account, http),
    private val press: (AgentKeyTag, AgentModel, WritingEffort, PaperBoundary) -> FrontPagePress = { key, model, effort, boundary ->
        FrontPagePress(account.client, Constants.brainstorm, boundary, { call, onEvent -> http.chatComplete(key, model.id, call.messages, outputLimitFor(model, call), effort, onEvent) })
    },
    /** What the platform does around a manual print (Android: a foreground service). */
    private val host: FrontPagePrintHost = FrontPagePrintHost.None,
    /** Every change of [job], for the app-wide list of prints ([FrontPagePrinters.printing]). */
    private val onJob: (FrontPagePrintJob?) -> Unit = {},
    /** True while no screen can be seen at all (a desktop app whose window was closed mid-print): nobody watches then. */
    private val readerAway: () -> Boolean = { false },
    /** Every time the press is taken (true) or let go (false), for the app-wide list ([FrontPagePrinters.pressHeld]). */
    onPress: (Boolean) -> Unit = {},
) {
    private val pubKey get() = account.signer.pubKey

    private val _run = MutableStateFlow<FrontPageRun>(FrontPageRun.Idle)

    /** The print in progress, or how the last one ended. Every observer sees the same run. */
    val run = _run.asStateFlow()

    private val jobs = FrontPagePrintJobTracker(account.signer.pubKey, host, onJob, now)

    private val logBook = PrintLogBook(now)

    /**
     * The print's details, newest last: what was asked of whom, when the model answered, its
     * reasoning as it streams, each section, the checks, the publish. The current print only, in
     * memory: the next print starts a new one.
     */
    val log: StateFlow<PrintLog> = logBook.log

    /**
     * The print as background work: its stage and start, then how it ended, until the reader has
     * seen that ([dismissJob]) or the next print starts. Changes only when the popup would say
     * something new, never per streamed chunk.
     */
    val job: StateFlow<FrontPagePrintJob?> = jobs.job

    /** The popup showed how the print ended: it goes. A print still running stays. */
    fun dismissJob() = jobs.dismiss()

    /** The popup's ×: hides a running print from the popup without stopping it (Cancel is on The Observer). */
    fun hideJob() = jobs.hide()

    private val localEditions = MutableStateFlow<List<SavedEdition>>(emptyList())

    /** The reader's own Observer site, as last read or published; null until read, or when there is none. */
    private val siteView = MutableStateFlow<ObserverSiteView?>(null)

    /**
     * The Observer site of the runner the reader's schedule names (NIP-FB, Runner: their
     * Assistant prints there, signed by itself, tagging the reader), as last read; null when the
     * schedule names none, or it has none yet.
     */
    private val runnerSiteView = MutableStateFlow<RunnerSiteView?>(null)

    /** The runner whose site was last read (found or not); null before any read. */
    private val runnerReadFor = MutableStateFlow<HexKey?>(null)

    /** The one runner the reader's own signed schedule names, or null. */
    private fun scheduleRunner(): HexKey? =
        account.observerSchedule.flow.value
            .runner()

    /** The runner's site, only while it is still the runner the schedule names. */
    private fun runnerEditions(): List<ObserverEdition> =
        runnerSiteView.value
            ?.takeIf { it.runner == scheduleRunner() }
            ?.view
            ?.editions
            .orEmpty()

    /**
     * The history: editions kept on this device plus the ones on the reader's Observer site this
     * device doesn't have (printed in another app or on another device), plus the ones on their
     * runner's site (marked [SavedEdition.byRunner]), newest first.
     */
    val editions: StateFlow<List<SavedEdition>> =
        combine(localEditions, siteView, runnerSiteView) { local, site, runner -> mergeHistory(local, site?.editions.orEmpty(), runner?.view?.editions.orEmpty()) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * What the two sites hold, for the schedule's status line and missed-run warning: the reader's
     * own (their site and the editions kept here) and the runner's.
     */
    val sites: StateFlow<ObserverSites> =
        combine(localEditions, siteView, runnerSiteView, runnerReadFor, account.observerSchedule.flow) { local, site, runner, readFor, synced ->
            val named = synced.runner()
            observerSitesOf(local, site?.editions.orEmpty(), runner?.view?.editions.orEmpty()).copy(runnerRead = named == null || readFor == named)
        }.stateIn(scope, SharingStarted.Eagerly, ObserverSites())

    /** The reader's Observer site event, for Open and Share. */
    val site: StateFlow<NamedSiteEvent?> =
        siteView
            .map { it?.site }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private val publisher = observerIO?.let { ObserverPublisher(it, account.signer.pubKey, ::readerName, now) }

    /** Whether this platform can publish to an Observer site at all. */
    val canPublish: Boolean get() = publisher != null

    private val _observer = MutableStateFlow<ObserverStatus>(ObserverStatus.Idle)

    /** The last publish to the Observer site. */
    val observer = _observer.asStateFlow()

    fun observerPrefs(): Flow<ObserverPrefs> = observerPrefs.flow(pubKey)

    suspend fun setObserverPrefs(prefs: ObserverPrefs) = observerPrefs.set(pubKey, prefs)

    private val publishing = Mutex()

    private val _watched = MutableStateFlow(0)

    /** Whether a front page screen is showing: then a finished print needs no notification. */
    val watched: StateFlow<Int> = _watched.asStateFlow()

    private var printing: Job? = null

    /** The press: one print at a time, a scheduled run holding it from before its claim to its end; Cancel stops either. */
    private val slot = PrintSlot(onPress)

    /** One read of both sites at a time ([refreshSites]), and when the last began. Before `init`, which starts the reads. */
    private val siteReads = Mutex()

    @Volatile
    private var sitesReadAt = 0L

    init {
        scope.launch {
            refresh()
            refreshSites()
        }
        // The runner's site is read whenever the schedule comes to name one (or another), and
        // dropped when it names none: a site by any other key is never read.
        scope.launch {
            account.observerSchedule.flow
                .map { it.runner() }
                .distinctUntilChanged()
                .collect { runner -> refreshRunnerSite(runner) }
        }
        // While the app is open, both sites are read again after each due occurrence: the paper
        // another device or the runner printed meanwhile shows, instead of an offer to print it
        // again (and pay twice) and, three hours on, a missed-run warning for a paper that exists.
        scope.launch {
            account.observerSchedule.flow
                .map { (it as? SyncedSchedule.Readable)?.task }
                .distinctUntilChanged()
                .collectLatest { task -> rereadAfterOccurrences(task) }
        }
    }

    /** Sleeps until each of [task]'s site re-reads ([nextSiteReread]) and reads both sites then; returns when it has none. */
    private suspend fun rereadAfterOccurrences(task: ScheduledTask?) {
        if (task == null || !task.enabled) return
        val expression = task.expression() ?: return
        val zone = FrontPageClock.cronZone(task.tz) ?: return
        while (true) {
            val at = now()
            val next = nextSiteReread(expression, zone, at) ?: return
            // In steps: a computer that sleeps overnight wakes to a wall clock past the re-read.
            delay((next - at).coerceIn(1, SITE_REREAD_STEP_SECONDS) * 1000)
            if (now() >= next) refreshSites()
        }
    }

    /**
     * Reads both Observer sites again, the reader's and their runner's, unless they were read
     * less than [minAgeSeconds] ago or are being read now.
     */
    suspend fun refreshSites(minAgeSeconds: Long = 0) {
        if (!siteReads.tryLock()) return
        try {
            if (minAgeSeconds > 0 && now() - sitesReadAt < minAgeSeconds) return
            sitesReadAt = now()
            refreshSite()
            refreshRunnerSite()
        } finally {
            siteReads.unlock()
        }
    }

    /** The screens showing the paper that someone can see: none while the reader is away. */
    private fun watchers(): Int = if (readerAway()) 0 else _watched.value

    private val _todayRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Today's paper was asked for again ([showToday]): a front page screen showing a past edition
     * goes back to today. Navigating to the screen that is already on top does nothing, so a
     * "paper ready" click needs this to leave a past edition.
     */
    val todayRequests: SharedFlow<Unit> = _todayRequests.asSharedFlow()

    /** Every front page screen of this account shows today ([todayRequests]). */
    fun showToday() {
        _todayRequests.tryEmit(Unit)
    }

    /** A screen showing the paper calls this, and the returned function when it goes. */
    fun watch(): () -> Unit {
        _watched.update { it + 1 }
        // Opening The Observer is how a hidden popup comes back.
        jobs.unhide()
        // And how it learns what was printed since it last looked: another device, or the runner.
        scope.launch { refreshSites(minAgeSeconds = SITE_REREAD_ON_OPEN_SECONDS) }
        var released = false
        return {
            if (!released) {
                released = true
                _watched.update { (it - 1).coerceAtLeast(0) }
            }
        }
    }

    suspend fun refresh() {
        localEditions.value =
            try {
                // Editions kept before the Observer site existed carry no hash; it is what tells a
                // site edition apart from one this device already has.
                store.list(pubKey).map { kept -> if (kept.sha256 != null) kept else store.html(pubKey, kept.day)?.let { kept.copy(sha256 = publishedSha256(it)) } ?: kept }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the kept editions", e)
                emptyList()
            }
    }

    /** Reads the reader's Observer site, so its editions join the history. */
    suspend fun refreshSite() {
        val publisher = publisher ?: return
        try {
            publisher.read()?.let { siteView.value = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the Observer site", e)
        }
    }

    /** Reads the Observer site [runner] publishes for the reader; clears it when [runner] is null. */
    suspend fun refreshRunnerSite(runner: HexKey? = scheduleRunner()) {
        if (runner == null) {
            runnerSiteView.value = null
            return
        }
        val publisher = publisher ?: return
        try {
            val view = publisher.readRunner(runner)
            runnerSiteView.value = view?.let { RunnerSiteView(runner, it) } ?: runnerSiteView.value?.takeIf { it.runner == runner }
            runnerReadFor.value = runner
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the runner's Observer site", e)
        }
    }

    /**
     * [day]'s page: from this device, else from the reader's Observer site (or their runner's) by
     * its hash (verified), then kept here so it opens offline next time.
     */
    suspend fun html(day: String): String? {
        store.html(pubKey, day)?.let { return it }
        val own = siteView.value?.let { view -> view.editions.firstOrNull { it.day == day }?.let { Triple(it, view.servers, false) } }
        val byRunner = runnerSiteView.value?.view?.let { view -> view.editions.firstOrNull { it.day == day }?.let { Triple(it, view.servers, true) } }
        val (edition, servers, fromRunner) = own ?: byRunner ?: return null
        val page = publisher?.page(edition, servers) ?: return null
        savedFromSite(edition, fromRunner).let { kept ->
            try {
                store.save(pubKey, kept, page)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not keep the site's edition of $day", e)
            }
        }
        return page
    }

    /** Whether a print holds the press: one the reader started, or a scheduled run (claiming or printing). */
    val isBusy: Boolean get() = printing?.isActive == true || slot.isHeld

    /**
     * Starts a print on the account's scope; false when one is already running (the reader's, or
     * the morning's on this device), and nothing was started.
     */
    fun print(
        key: AgentKeyTag,
        model: AgentModel,
        trigger: PrintTrigger = PrintTrigger.MANUAL,
        /** The writing effort; null reads the schedule's `effort` param. */
        effort: WritingEffort? = null,
    ): Boolean {
        if (isBusy) return false
        printing = scope.launch { printNow(key, model, trigger, effort = effort) }
        return true
    }

    /**
     * Stops the print that is running, whoever started it: a scheduled one ends
     * [ScheduledOutcome.CANCELLED], and its morning isn't printed again on its own (NIP-FB's
     * claim stays unfinished; this install's record says the run ended).
     */
    fun cancel() {
        slot.cancel()
        printing?.cancel()
        printing = null
        _run.value = FrontPageRun.Idle
        jobs.dropped()
    }

    /**
     * Prints in the caller's coroutine and publishes to [run] as it goes. Returns how it ended,
     * or null when another print holds the press (nothing was started); a clean page is already
     * stored and, when nobody is watching, announced. [keep] is asked before a clean page is kept:
     * false (another device's claim won the morning) drops it, unkept, unannounced, unpublished,
     * and the run ends [FrontPageRun.Idle].
     */
    suspend fun printNow(
        key: AgentKeyTag,
        model: AgentModel,
        trigger: PrintTrigger = PrintTrigger.MANUAL,
        keep: suspend () -> Boolean = { true },
        effort: WritingEffort? = null,
    ): FrontPageRun? {
        val boundary = boundary ?: return FrontPageRun.Failed(WriterFailure.UNSUPPORTED).also { _run.value = it }
        if (!slot.tryHold()) return null
        try {
            return printHolding(key, model, boundary, trigger, keep, effort = effort)
        } finally {
            slot.release()
        }
    }

    /**
     * The print, with the press already held, as its own job so [cancel] stops it alone: the
     * caller (a scheduled run) carries on to say it was cancelled. [FrontPageRun.Cancelled] then.
     */
    private suspend fun printHolding(
        key: AgentKeyTag,
        model: AgentModel,
        boundary: PaperBoundary,
        trigger: PrintTrigger,
        keep: suspend () -> Boolean,
        /** What the paper is dated (its `printedAt`): the occurrence a scheduled print is for; null, when the print starts. */
        printedAt: Long? = null,
        effort: WritingEffort? = null,
    ): FrontPageRun = slot.runCancellable(FrontPageRun.Cancelled) { printBody(key, model, boundary, trigger, keep, printedAt ?: now(), effort ?: FrontPageParams.effort(scheduledTask())) }

    private suspend fun printBody(
        key: AgentKeyTag,
        model: AgentModel,
        boundary: PaperBoundary,
        trigger: PrintTrigger,
        keep: suspend () -> Boolean,
        printedAt: Long,
        effort: WritingEffort,
    ): FrontPageRun {
        jobs.started(trigger)
        logBook.start(askedOf(key, model))
        var kept: SavedEdition? = null
        try {
            val ended =
                try {
                    _run.value = FrontPageRun.Running(PrintStep.Probe)
                    var shown: PrintStep = PrintStep.Probe
                    val context = editionContext(printedAt)
                    val result =
                        press(key, model, effort, boundary).print(pubKey, readerName(), readerLanguage(), logBook, context) { step ->
                            // The stream reports every chunk; every observer recomposing per chunk is the cost.
                            if (progressWorthShowing(shown, step)) {
                                shown = step
                                _run.value = FrontPageRun.Running(step)
                                jobs.step(step)
                            }
                        }
                    if (result is PrintResult.Printed) {
                        if (!keep()) {
                            jobs.dropped()
                            return FrontPageRun.Idle.also { _run.value = it }
                        }
                        kept = keep(result, model, trigger, printedAt)
                    }
                    FrontPageRun.Done(result)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: WriterException) {
                    logBook.add(PrintLogKind.FAILED, e.failure.name, listOfNotNull(e.status))
                    FrontPageRun.Failed(e.failure, e.status, e.providerSaid, e.shortfallSats)
                } catch (e: Exception) {
                    Log.w(TAG, "Printing failed", e)
                    logBook.add(PrintLogKind.FAILED, WriterFailure.UNREACHABLE.name)
                    FrontPageRun.Failed(WriterFailure.UNREACHABLE)
                }
            _run.value = ended
            endJob(ended, kept, trigger)
            return ended
        } catch (e: CancellationException) {
            _run.value = FrontPageRun.Idle
            jobs.dropped()
            throw e
        } finally {
            jobs.finished(trigger)
        }
    }

    /** The job says how [ended] went; a manual print that gave no paper tells a reader who isn't looking. */
    private suspend fun endJob(
        ended: FrontPageRun,
        kept: SavedEdition?,
        trigger: PrintTrigger,
    ) {
        val end = jobs.ended(ended, kept)
        if (end != null && FrontPagePrintJobTracker.tellsReaderOfFailure(end, trigger, watchers())) {
            try {
                notifier.printFailed(pubKey, end)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not tell the reader the print failed", e)
            }
        }
    }

    /**
     * Keeps a clean page dated [at]: when the print started, or the occurrence a scheduled one is
     * for, never when it finished. A paper begun at 23:55 and done at 00:03 is that evening's: dated
     * by its finish, it would count as the next day's ([ObserverSite.printedFor]), and the next
     * day's 23:55 run would skip as already printed.
     */
    private suspend fun keep(
        printed: PrintResult.Printed,
        model: AgentModel,
        trigger: PrintTrigger,
        at: Long,
    ): SavedEdition {
        // The page as it leaves the app (the Download, the Observer site): with the paper's CSP.
        val html = withPaperCsp(printed.html)
        val edition =
            SavedEdition(
                day = FrontPageClock.dayKey(at),
                code = printed.corpus.code,
                printedAt = at,
                model = model.id,
                tokens = printed.usage?.total,
                costUsd = actualCost(model, printed.usage),
                quotes = printed.quotes,
                posts = printed.corpus.events.size,
                headline = printed.headline,
                removed = printed.changes.filter { it.noteworthy }.map { it.detail },
                digestChars = printed.digestChars,
                rounds = printed.rounds,
                sha256 = publishedSha256(html),
                recipe = printed.recipe,
            )
        try {
            store.save(pubKey, edition, html)
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not keep the edition", e)
        }
        if (trigger == PrintTrigger.SCHEDULED || watchers() == 0) notifier.paperReady(pubKey, edition)
        // Published before the print ends, so the job (and Android's foreground service, and the
        // platform's scheduled run) lives until it is out, the popup saying so.
        publishPending(trigger, onPublishing = jobs::publishing)
        return edition
    }

    /**
     * Puts every kept edition the site doesn't have yet on the reader's Observer site: today's,
     * and any an earlier publish failed to send. Only when publishing is on and the reader has
     * said yes, once, to a public site; until then a manual print asks ([ObserverStatus.AwaitingConsent])
     * and nothing is uploaded. A scheduled print publishes only with a signer that signs on its
     * own. Never anything that wasn't printed (and checked) here.
     */
    suspend fun publishPending(
        trigger: PrintTrigger = PrintTrigger.MANUAL,
        onPublishing: () -> Unit = {},
    ) {
        val publisher = publisher ?: return
        // Read now, not when the print started: turning it off mid-print keeps that paper private.
        val prefs = observerPrefs.get(pubKey)
        if (!prefs.publish) return
        if (!prefs.noticeSeen) {
            if (trigger == PrintTrigger.MANUAL) _observer.value = ObserverStatus.AwaitingConsent
            return
        }
        if (trigger == PrintTrigger.SCHEDULED && !canPrintUnattended()) return
        // One at a time: a second request waits, then publishes whatever the first left pending.
        publishing.lock()
        try {
            val pending =
                localEditions.value
                    .filter { !it.published && it.sha256 != null }
                    .mapNotNull { kept -> store.html(pubKey, kept.day)?.let { kept to withPaperCsp(it) } }
                    .filter { (kept, html) -> ObserverPublisher.sha256Hex(html.encodeToByteArray()).equals(kept.sha256, ignoreCase = true) }
            if (pending.isEmpty()) return
            _observer.value = ObserverStatus.Publishing
            logBook.add(PrintLogKind.PUBLISHING)
            onPublishing()
            val result = publisher.publish(pending.map { (kept, html) -> ObserverPage(ObserverPublisher.entryFor(kept, kept.sha256!!.lowercase()), html) })
            logBook.add(if (result is ObserverPublishResult.Published) PrintLogKind.PUBLISHED else PrintLogKind.PUBLISH_FAILED)
            _observer.value =
                when (result) {
                    is ObserverPublishResult.Published -> {
                        pending.forEach { (kept, html) -> store.save(pubKey, kept.copy(published = true), html) }
                        siteView.value = ObserverSiteView(result.site, result.kept, result.site.servers())
                        refresh()
                        ObserverStatus.Published(now())
                    }
                    is ObserverPublishResult.Failed -> ObserverStatus.Failed(result.reason)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Publishing to the Observer site failed", e)
            _observer.value = ObserverStatus.Failed(ObserverFailure.PUBLISH)
        } finally {
            publishing.unlock()
        }
    }

    /** The reader said "Not now" to the public site: nothing goes out, and the next print asks again. */
    fun declinePublish() {
        if (_observer.value == ObserverStatus.AwaitingConsent) _observer.value = ObserverStatus.Idle
    }

    /**
     * The model to print with: the schedule's `model` param ([named]) as written (a printer that
     * can't use it fails, it never picks another: the reader chose that price), else the one this
     * install remembers for the endpoint, else the best-ranked one it lists.
     */
    suspend fun chooseModel(
        key: AgentKeyTag,
        named: String? = null,
    ): AgentModel? {
        val listed =
            try {
                http.listAgentModels(key)
            } catch (e: WriterException) {
                emptyList()
            }
        val remembered = runCatching { memory.get(key.url) }.getOrNull()
        val byId = listed.associateBy { it.id }
        val pick = named ?: remembered ?: rankModels(listed.map { it.id }).firstOrNull() ?: return null
        return byId[pick] ?: AgentModel(pick)
    }

    /** The reader's schedule, when this device has read it. */
    private fun scheduledTask(): ScheduledTask? = (account.observerSchedule.flow.value as? SyncedSchedule.Readable)?.task

    /**
     * What the edition knows beyond its posts, as of a print dated [at]: the paper as the newest
     * editions left it, its number, the last front pages, the recipes recently run, and the
     * reader's clock (the schedule's zone, else this device's). Never fails a print: what can't
     * be read is left out.
     */
    private suspend fun editionContext(at: Long): EditionContext {
        val today = FrontPageClock.dayKey(at)
        val known = editions.value
        val pages =
            known
                .sortedByDescending { it.day }
                .distinctBy { it.day }
                .take(PAGES_READ_BACK)
                .mapIndexedNotNull { index, edition ->
                    try {
                        // The newest may be fetched from a site; the rest only if kept here.
                        if (index == 0) html(edition.day) else store.html(pubKey, edition.day)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not read back the edition of ${edition.day}", e)
                        null
                    }
                }
        return EditionContext(
            paper = standingPaper(pages),
            number = editionNumber(known.map { it.day }, today),
            recent = recentHeadlines(known, today),
            clock = EditionClock.forReader(scheduledTask()?.tz),
            recentRecipes = recentRecipes(known, today),
        )
    }

    /** The digest size a print is estimated with: the last edition's, or the budget. */
    private fun estimateDigestChars(): Int =
        localEditions.value
            .firstOrNull()
            ?.digestChars
            ?.takeIf { it > 0 } ?: DEFAULT_DIGEST_BUDGET

    /** The cost estimate for one print with [model]: the last edition's digest size, or the budget. */
    fun estimate(
        model: AgentModel?,
        effort: WritingEffort = WritingEffort.STANDARD,
    ): Double? = estimateCost(model, estimateDigestChars(), effort = effort)

    /** The same estimate in sats, for a model a Routstr node prices in sats; null otherwise. */
    fun estimateSats(
        model: AgentModel?,
        effort: WritingEffort = WritingEffort.STANDARD,
    ): Long? = RoutstrPricing.editionSats(model?.sats, estimateDigestChars(), effort = effort)

    /**
     * The morning edition, with nobody there: only a signer that can decrypt on its own (a key
     * on this device) can read the AI key. Amber, other external signers and remote bunkers need
     * the reader, and nothing here stores the AI key anywhere else to work around that.
     */
    suspend fun printScheduled(
        waitMs: Long = 60_000,
        /** When the platform stops this run (unix seconds); null when it doesn't. */
        deadline: Long? = null,
    ): ScheduledOutcome {
        unattendedBlocker(account.signer, boundary, busy = isBusy)?.let { blocked ->
            if (blocked == ScheduledOutcome.NEEDS_SIGNER) notifier.needsYou(pubKey)
            return blocked
        }
        // The press is held from before the claim to the end: a print the reader starts meanwhile
        // is refused (and says so), so a won claim never meets a busy press, and a busy press
        // never leaves a claim behind.
        if (!slot.tryHold()) return ScheduledOutcome.BUSY
        try {
            val morning = morning ?: return printOccurrence(waitMs, null, null) { true }
            // "Quit and cancel" while the run waited for the press: nothing claimed, nothing to record.
            if (slot.isCancelRequested) return ScheduledOutcome.CANCELLED
            // The occurrence the claim won: the claim asks whether its paper exists right before it
            // prints ([FrontPageMorning.runOccurrence]), and the paper is dated by it.
            var due: Long? = null
            // NIP-FB: only a device the reader turned on, and only the one whose claim wins; a paper
            // that already exists for the occurrence is marked done without printing.
            return morning.runOccurrence(
                account,
                alreadyDone = { occurrence, zone ->
                    due = occurrence
                    alreadyPrinted(occurrence, zone)
                },
                deadline,
            ) { budget, keep ->
                // Cancelled during the claim and its settle: the claim is this install's, and it
                // ends CANCELLED (recorded as ended), never printed.
                if (slot.isCancelRequested) ScheduledOutcome.CANCELLED else printOccurrence(waitMs, budget, due, keep)
            }
        } finally {
            slot.release()
        }
    }

    /**
     * The print itself, once the occurrence due at [due] is this device's, in [budgetSeconds] at
     * most (null: no limit), its paper dated [due] (null: when it starts). The press is held.
     */
    private suspend fun printOccurrence(
        waitMs: Long,
        budgetSeconds: Long?,
        due: Long?,
        keep: suspend () -> Boolean,
    ): ScheduledOutcome {
        val boundary = boundary ?: return ScheduledOutcome.UNSUPPORTED
        val (key, hasLens) = awaitOwnLists(waitMs)
        if (key == null) return ScheduledOutcome.NO_KEY.also { notifier.needsYou(pubKey) }
        if (!hasLens) return ScheduledOutcome.NO_LENS.also { notifier.needsYou(pubKey) }
        val model = chooseModel(key, scheduledTask()?.let(ObserverParams::model)) ?: return ScheduledOutcome.FAILED
        val ended =
            if (budgetSeconds == null) {
                printHolding(key, model, boundary, PrintTrigger.SCHEDULED, keep, due)
            } else {
                // Out of time: stopped before the platform kills it, so `ran` can still be written for a paper that made it.
                withTimeoutOrNull(budgetSeconds * 1000) { printHolding(key, model, boundary, PrintTrigger.SCHEDULED, keep, due) }
                    ?: return ScheduledOutcome.FAILED
            }
        return scheduledOutcomeOf(ended)
    }

    fun canPrintUnattended() = account.signer.localPrivKeyOrNull() != null

    /**
     * Whether the edition of the occurrence due at [occurrence] already exists, so a scheduled
     * print skips: one kept here or listed on the reader's Observer site, printed on that
     * occurrence's calendar day on [zone]'s clock (the schedule's), or a site entry with no print
     * time dated the occurrence's UTC date ([ObserverSite.alreadyPrinted]). A site that can't be
     * read leaves the answer to this device's own editions. Manual prints never ask.
     */
    suspend fun alreadyPrinted(
        occurrence: Long,
        zone: CronZone,
    ): Boolean {
        val kept =
            try {
                store.list(pubKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the kept editions", e)
                emptyList()
            }
        if (kept.any { ObserverSite.printedFor(it.printedAt, null, occurrence, zone) }) return true
        val site =
            try {
                publisher?.read()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the Observer site before a scheduled print", e)
                null
            }
        site?.let { siteView.value = it }
        // NIP-FB, Runner: the runner's site counts too, and only the runner the schedule names.
        scheduleRunner()?.let { refreshRunnerSite(it) }
        return ObserverSite.alreadyPrinted(site?.editions.orEmpty(), runnerEditions(), occurrence, zone)
    }

    /**
     * The reader's AI key (kind 12000) and rank provider (kind 10040). A process started cold by
     * the scheduler has neither in its cache, so it asks the reader's own outbox relays (and the
     * search relay, which mirrors replaceables) for both, then waits for them to decrypt.
     */
    private suspend fun awaitOwnLists(waitMs: Long): Pair<AgentKeyTag?, Boolean> {
        // The schedule's `key` param picks among the usable keys; NIP-FA's default (the first) without one.
        val ready = combine(account.agentKeys.flow, account.trustProviderList.liveUserRankProvider) { keys, rank -> scheduledKey(scheduledTask(), keys) to (rank != null) }
        val now = ready.first()
        if (now.first != null && now.second) return now
        fetchOwnLists()
        return withTimeoutOrNull(waitMs) { ready.first { it.first != null && it.second } } ?: ready.first()
    }

    private suspend fun fetchOwnLists() =
        coroutineScope {
            val kinds = listOf(AgentKeyEvent.KIND, TrustProviderListEvent.KIND)
            val authors = listOf(pubKey)
            val relays = (account.outboxRelays.flow.first() + Constants.brainstorm).distinct()
            relays
                .map { relay ->
                    async {
                        // The search relay closes tokenless queries; everyone else ignores the token.
                        val asked = Filter(kinds = kinds, authors = authors, search = if (relay == Constants.brainstorm) INCLUDE_SPAM else null)
                        try {
                            account.client.fetchAll(relay, asked, 10_000).forEach { account.cache.justConsume(it, relay, wasVerified = false) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not read the reader's lists from ${relay.url}", e)
                        }
                    }
                }.awaitAll()
        }

    /** The reader's name for the masthead line; never hex. */
    private fun readerName(): String =
        account
            .userProfile()
            .metadataOrNull()
            ?.bestName()
            ?.trim()
            ?.ifEmpty { null } ?: shortNpub(pubKey)

    /** The language the reader reads Amethyst's translations in. */
    private fun readerLanguage(): String? =
        account.settings.syncedSettings.languages.translateTo.value
            .trim()
            .ifEmpty { null }

    companion object {
        private const val TAG = "FrontPagePrinter"

        /** How many of the newest editions are read back for the masthead, motto and sections. */
        const val PAGES_READ_BACK = 7
    }
}

/** A call's output limit for [model]: the plan's ([planTokenLimit]) or the page's ([outputTokenLimit]). */
fun outputLimitFor(
    model: AgentModel,
    call: WriteCall,
): Int =
    when (call.purpose) {
        WriteCall.Purpose.PLAN -> planTokenLimit(model)
        WriteCall.Purpose.PAGE -> outputTokenLimit(model, approxTokens(call.messages.sumOf { it.text.length }))
    }

/** Opening The Observer reads both sites again, unless they were read less than this long ago (seconds): a rotation or a tab switch isn't news. */
const val SITE_REREAD_ON_OPEN_SECONDS = 60L

/** After an occurrence is due, the open app reads both sites again this long after (seconds): the paper of a print that went well is out by then. */
const val SITE_REREAD_AFTER_DUE_SECONDS = 20 * 60L

/** And once more this long before a missing paper is called missed ([ScheduledTask.GRACE_SECONDS]), so the warning reads what is there. */
const val SITE_REREAD_BEFORE_MISSED_SECONDS = 10 * 60L

/** The longest single sleep before a re-read: the wall clock is looked at again after it. */
const val SITE_REREAD_STEP_SECONDS = 10 * 60L

/**
 * When, strictly after [now], the open app next reads both Observer sites again for [expression]
 * on [zone]'s clock: [SITE_REREAD_AFTER_DUE_SECONDS] after an occurrence, and
 * [SITE_REREAD_BEFORE_MISSED_SECONDS] before its missed-run warning could show. Null when the
 * schedule has no occurrence around [now].
 */
fun nextSiteReread(
    expression: CronExpression,
    zone: CronZone,
    now: Long,
): Long? {
    val occurrences = listOfNotNull(expression.lastAtOrBefore(now, zone), expression.nextAfter(now, zone))
    return occurrences
        .flatMap { due -> listOf(due + SITE_REREAD_AFTER_DUE_SECONDS, due + ScheduledTask.GRACE_SECONDS - SITE_REREAD_BEFORE_MISSED_SECONDS) }
        .filter { it > now }
        .minOrNull()
}

/**
 * What the two Observer sites hold, for the status line and the missed-run warning: the reader's
 * own site plus the editions kept on this device, and the runner's site. A runner's edition kept
 * here (opened once, so it reads offline) is still the runner's print.
 */
fun observerSitesOf(
    kept: List<SavedEdition>,
    site: List<ObserverEdition>,
    runnerSite: List<ObserverEdition>,
): ObserverSites {
    val (keptByRunner, keptOwn) = kept.filter { it.printedAt > 0 }.partition { it.byRunner }
    return ObserverSites(
        reader = site + keptOwn.map { ObserverPublisher.entryFor(it, it.sha256 ?: "") },
        runner = runnerSite + keptByRunner.map { ObserverPublisher.entryFor(it, it.sha256 ?: "") },
    )
}

/** The site the runner [runner] publishes for the reader, as last read. */
class RunnerSiteView(
    val runner: HexKey,
    val view: ObserverSiteView,
)

/** The key to print with: the schedule's `key` param among [keys] ([ObserverParams.key]), else NIP-FA's default (the first). */
fun scheduledKey(
    task: ScheduledTask?,
    keys: List<AgentKeyTag>,
): AgentKeyTag? = task?.let { ObserverParams.key(it, keys) } ?: keys.firstOrNull()

/** How the last publish to the reader's Observer site went. */
sealed interface ObserverStatus {
    data object Idle : ObserverStatus

    /** A clean print would publish, but the reader hasn't said yes to a public site yet. */
    data object AwaitingConsent : ObserverStatus

    data object Publishing : ObserverStatus

    data class Published(
        val at: Long,
    ) : ObserverStatus

    data class Failed(
        val reason: ObserverFailure,
    ) : ObserverStatus
}

/** How a scheduled print's [ended] run reads as a [ScheduledOutcome]. */
fun scheduledOutcomeOf(ended: FrontPageRun?): ScheduledOutcome =
    when {
        // The press was taken: nothing was started.
        ended == null -> ScheduledOutcome.BUSY
        ended is FrontPageRun.Done && ended.result is PrintResult.Printed -> ScheduledOutcome.PRINTED
        ended == FrontPageRun.Cancelled -> ScheduledOutcome.CANCELLED
        // The page was dropped: another device's claim won before it was kept.
        ended == FrontPageRun.Idle -> ScheduledOutcome.LOST_CLAIM
        else -> ScheduledOutcome.FAILED
    }

/** The reader's Cancel, as the print's cancellation cause. */
class PrintCancelled : CancellationException("The reader cancelled the print")

/**
 * One print at a time ([tryHold], [release]), and a Cancel that stops whichever print is running
 * ([runCancellable]): the reader's, or the morning's that a scheduled run holds the slot for from
 * before its claim to its end.
 */
class PrintSlot(
    /** Told each time the slot is taken (true) and let go (false), in that order. */
    private val onHeld: (Boolean) -> Unit = {},
) {
    private val mutex = Mutex()

    @Volatile
    private var current: Job? = null

    /**
     * Cancel was asked while the slot was held: a scheduled run holds it through its wait, claim,
     * settle and list reads, when no print is [current] to stop. Cleared when the slot is taken
     * again.
     */
    @Volatile
    private var cancelAsked = false

    /** Whether a print (or a scheduled run on its way to one) holds the slot. */
    val isHeld: Boolean get() = mutex.isLocked

    /** Whether the holder was asked to stop ([cancel]): a scheduled run checks it before it claims and before it prints. */
    val isCancelRequested: Boolean get() = cancelAsked

    fun tryHold(): Boolean {
        if (!mutex.tryLock()) return false
        cancelAsked = false
        onHeld(true)
        return true
    }

    fun release() {
        // Said before the unlock, so the next holder's true always comes after this false.
        onHeld(false)
        mutex.unlock()
    }

    /**
     * [body] as its own job, so [cancel] stops it alone: then [cancelled] comes back and the
     * caller (a scheduled run) carries on to record and say how it ended. A caller that is
     * itself cancelled (the platform stopping its run) is cancelled as usual.
     */
    suspend fun <T> runCancellable(
        cancelled: T,
        body: suspend () -> T,
    ): T {
        // Asked to stop before the print began: it never begins.
        if (cancelAsked) return cancelled
        return coroutineScope {
            val work = async { body() }
            current = work
            // A cancel that came between the check above and this line: the print stops at once.
            if (cancelAsked) work.cancel(PrintCancelled())
            try {
                work.await()
            } catch (e: CancellationException) {
                if (work.isCancelled && isActive) cancelled else throw e
            } finally {
                if (current === work) current = null
            }
        }
    }

    /**
     * Stops the print running now, if any, and tells the holder to stop: a scheduled run that
     * hasn't printed yet ends cancelled instead of claiming or printing.
     */
    fun cancel() {
        if (mutex.isLocked) cancelAsked = true
        current?.cancel(PrintCancelled())
    }
}

/**
 * Who was asked, for the log: the provider as the reader knows it and the model id. Never the key,
 * and never the URL's path or query (a custom endpoint may carry a token there): only its host.
 */
fun askedOf(
    key: AgentKeyTag,
    model: AgentModel,
): String {
    val provider = AgentProviderPresets.forUrl(key.url)?.name ?: AgentProviderPresets.hostOf(key.url)?.substringAfterLast('@') ?: "?"
    return "$provider \u00b7 ${model.id}"
}

/** The hash an edition is published under: its bytes once the paper's CSP is in ([withPaperCsp]). */
fun publishedSha256(html: String): String = ObserverPublisher.sha256Hex(withPaperCsp(html).encodeToByteArray())

/**
 * Whether [next] is worth a new [FrontPageRun.Running]: every step change is, but the writer's
 * running character count only every [WRITE_PROGRESS_CHARS], since the stream reports each chunk.
 */
fun progressWorthShowing(
    shown: PrintStep,
    next: PrintStep,
): Boolean {
    if (next !is PrintStep.Write || shown !is PrintStep.Write || next.round != shown.round) return true
    if (next.phase != shown.phase || next.section != shown.section) return true
    return next.chars - shown.chars >= WRITE_PROGRESS_CHARS
}

const val WRITE_PROGRESS_CHARS = 400

/**
 * When this device prints one account's morning edition: [cron] (NIP-FB's five fields) on the
 * wall clock of [zone]; never, when [zone] is missing or unknown here (the old per-device
 * schedule, kept only as this device's on/off, has none). Off by default; 7:00 every day is only
 * the suggestion. [enabled] already says whether *this device* prints: see [scheduleForThisDevice].
 */
@Immutable
data class FrontPageSchedule(
    val enabled: Boolean = false,
    val cron: String = ScheduledTask.DEFAULT_CRON,
    val zone: String? = null,
) {
    /** Parsed once: the timers ask on every reschedule. */
    private val parsed: CronExpression? by lazy { CronExpression.parse(cron) }

    /** The schedule, or null when [cron] is outside NIP-FB's subset: then it never runs here. */
    fun expression(): CronExpression? = parsed

    /** The time and weekdays a simple editor shows, or null when it can only show [cron] as written. */
    fun simple(): SimpleCron? = SimpleCron.parse(cron)

    /** The next run strictly after [now]; null when there is none, or [zone] is missing or unknown here (NIP-FB: unreadable). */
    fun nextRun(now: Long): Long? {
        val zone = FrontPageClock.cronZone(zone) ?: return null
        return expression()?.nextAfter(now, zone)
    }

    /** The last occurrence at or before [now]: what a run woken now is for. Null when none, or the zone is unknown. */
    fun lastRun(now: Long): Long? {
        val zone = FrontPageClock.cronZone(zone) ?: return null
        return expression()?.lastAtOrBefore(now, zone)
    }

    companion object {
        /** Every day at [hour]:[minute] on this device's clock: the old per-device schedule. */
        fun daily(
            enabled: Boolean,
            hour: Int,
            minute: Int,
        ) = FrontPageSchedule(enabled, SimpleCron(hour, minute).toCron())
    }
}

/**
 * One [FrontPagePrinter] per account for the whole app, so the screen, the shell's banner and
 * the scheduler share one print. A reloaded account (same key, new object) gets a new printer.
 */
class FrontPagePrinters(
    private val http: IRoleBasedHttpClientBuilder,
    val memory: FrontPageModelMemory,
    val store: FrontPageEditionStore,
    val schedules: FrontPageScheduleStore,
    private val notifier: FrontPageNotifier,
    val observerPrefs: ObserverPrefsStore = ObserverPrefsStore.None,
    installs: FrontPageInstallStore,
    /** How this install names itself as a runner; null where it can't print (iOS). */
    installInfo: FrontPageInstallInfo?,
    /** What the platform does around a print the reader started (Android: a foreground service). */
    private val host: FrontPagePrintHost = FrontPagePrintHost.None,
) {
    /** The synced morning schedule of every account, and this install's place in it. */
    val morning = FrontPageMorning(schedules, installs, installInfo, memory = memory)

    private val printers = MutableStateFlow<Map<HexKey, FrontPagePrinter>>(emptyMap())

    private val _printing = MutableStateFlow<Map<HexKey, FrontPagePrintJob>>(emptyMap())

    /**
     * Set while no screen of the app can be seen (the desktop window closed with a print kept
     * going): a print that ends then is announced as if no front page were showing.
     */
    val readerAway = MutableStateFlow(false)

    /** Every account's print as background work ([FrontPagePrinter.job]): what a foreground service shows. */
    val printing: StateFlow<Map<HexKey, FrontPagePrintJob>> = _printing.asStateFlow()

    private val _pressHeld = MutableStateFlow<Set<HexKey>>(emptySet())

    /**
     * The accounts whose press is held ([FrontPagePrinter.isBusy]): a print, or a scheduled run
     * from before its claim until its `ran` is written, which goes on after [printing] says the
     * print ended. Quitting before then leaves the claim unfinished, for another device to pay for again.
     */
    val pressHeld: StateFlow<Set<HexKey>> = _pressHeld.asStateFlow()

    /** Stops every print running now, the reader's and the morning's: the app is quitting. */
    fun cancelAll() {
        val accounts =
            _printing.value.values
                .filter { it.running }
                .map { it.account } + _pressHeld.value
        accounts.distinct().forEach { printers.value[it]?.cancel() }
    }

    /**
     * [cancelAll], then waits for every press to be let go, [timeoutMs] at most: a scheduled run
     * records that its morning was cancelled before it lets go, so the next launch doesn't resume
     * it. True when they all settled in time.
     */
    suspend fun cancelAllAndSettle(timeoutMs: Long = CANCEL_SETTLE_MS): Boolean {
        cancelAll()
        return withTimeoutOrNull(timeoutMs) {
            combine(_pressHeld, _printing) { held, jobs -> held.isEmpty() && jobs.values.none { it.running } }.first { it }
        } != null
    }

    /** Stops every print the reader started (the notification's Cancel). */
    fun cancelManualPrints() {
        _printing.value.values
            .filter { it.runningManual }
            .forEach { printers.value[it.account]?.cancel() }
    }

    private fun track(
        account: HexKey,
        job: FrontPagePrintJob?,
    ) = _printing.update { if (job == null) it - account else it + (account to job) }

    fun of(account: Account): FrontPagePrinter {
        printers.value[account.signer.pubKey]?.takeIf { it.account === account }?.let { return it }
        var made: FrontPagePrinter? = null
        printers.update { current ->
            val existing = current[account.signer.pubKey]?.takeIf { it.account === account }
            if (existing != null) {
                made = existing
                current
            } else {
                val key = account.signer.pubKey
                val printer =
                    made ?: FrontPagePrinter(
                        account,
                        http,
                        memory,
                        store,
                        notifier,
                        observerPrefs = observerPrefs,
                        morning = morning,
                        host = host,
                        onJob = { track(key, it) },
                        readerAway = { readerAway.value },
                        onPress = { held -> _pressHeld.update { if (held) it + key else it - key } },
                    )
                made = printer
                current + (account.signer.pubKey to printer)
            }
        }
        return made!!
    }

    /** The printer already made for [account], without making one. */
    fun existing(account: HexKey): FrontPagePrinter? = printers.value[account]

    /**
     * A removed account leaves nothing behind on this install: its print stops, and its kept
     * papers, local schedule, runner id and Observer choice go. The synced schedule stays: it is
     * the reader's, on their relays. The platform cancels its own scheduled job ([FrontPageScheduler]).
     */
    suspend fun forget(account: HexKey) {
        printers.value[account]?.cancel()
        printers.update { it - account }
        _printing.update { it - account }
        _pressHeld.update { it - account }
        store.removeAccount(account)
        morning.forget(account)
        observerPrefs.set(account, ObserverPrefs())
    }

    companion object {
        /** How long Quit waits for cancelled prints to record it before the app exits. */
        const val CANCEL_SETTLE_MS = 5_000L
    }
}
