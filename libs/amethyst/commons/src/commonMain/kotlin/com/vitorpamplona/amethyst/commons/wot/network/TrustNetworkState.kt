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
package com.vitorpamplona.amethyst.commons.wot.network

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkHeader
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkIds
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkPartial
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkProgress
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkSyncResult
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.memberRank
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.passesMinRank
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One account's Web of Trust network: the NIP-85 rank provider's cards, as a
 * `TrustNetworkIndex` kept on disk ([store]) and in memory, outside `LocalCache`.
 *
 *  - **Active** ([isActive]) only when both hold: the account's kind 10040 names a
 *    `30382:rank` provider, and that provider's **whole** set of cards is downloaded. A
 *    download in progress, a partial one (never applied nor saved), a provider with no scores
 *    yet, or a removed provider (its files are deleted) all leave it inactive, and while
 *    inactive every consumer behaves as before the feature existed: nothing is hidden,
 *    collapsed or moved because a download has not finished.
 *  - **Loads at construction**, so a process woken by a push notification can wait on
 *    [awaitLoaded] (tens of ms) instead of letting strangers through. Until [rankProvider]
 *    resolves, the index on disk is used as is.
 *  - **Syncs** through [source] when [syncIfStale] finds one due (see [dueSync]): a download
 *    when there is no index, an update every [UPDATE_EVERY_SECS], a full check every
 *    [FULL_CHECK_EVERY_SECS]. Nothing syncs on its own: the platform calls [syncIfStale] (on
 *    foreground, from a periodic job).
 *  - **Decides** through [verdicts] snapshots (see [TrustVerdicts]).
 *
 * Sync scheduling (the running sync, the metered-network allowance) is confined to one
 * single-threaded dispatcher, so triggers from the UI, a worker and a provider change cannot race.
 */
class TrustNetworkState(
    private val rankProvider: StateFlow<ResolvedProvider?>,
    val minTrustScore: StateFlow<Int>,
    private val store: TrustNetworkStore?,
    private val source: TrustNetworkSource?,
    private val scope: CoroutineScope,
    /** False while on a metered network: background cold downloads (tens of MB) then wait. */
    private val canDownloadLarge: () -> Boolean = { true },
    /**
     * Whether a provider change starts the due sync by itself. The apps want that; a one-shot
     * caller (the CLI) turns it off and calls [syncNow].
     */
    private val autoSync: Boolean = true,
    private val clock: () -> Long = { TimeUtils.now() },
    /** How often a download that may not use mobile data checks it still has Wi-Fi. */
    private val meteredCheckMs: Long = METERED_CHECK_MS,
) {
    private val _network = MutableStateFlow<TrustNetwork?>(null)

    /** The active network, or null while there is no provider or no index for it yet. */
    val network: StateFlow<TrustNetwork?> = _network.asStateFlow()

    /**
     * Cards newer than the index, by subject, seen between syncs. Lookups read them before the
     * index, so a provider that recomputes on demand shows up on screen right away.
     */
    private val _overlay = MutableStateFlow<Map<HexKey, TrustOverlayCard>>(emptyMap())
    val overlay: StateFlow<Map<HexKey, TrustOverlayCard>> = _overlay.asStateFlow()

    /** Bumps when a card from [overlay] moves someone in or out of the network. */
    private val verdictRevision = MutableStateFlow(0)

    private val _status = MutableStateFlow(TrustNetworkSyncStatus())
    val status: StateFlow<TrustNetworkSyncStatus> = _status.asStateFlow()

    private val loaded = MutableStateFlow(false)

    /** The provider choice [network] was last matched against, once that has happened. */
    private val applied = MutableStateFlow<ResolvedProvider?>(null)

    /**
     * The provider choice fully handled: [applied], and its due sync started (into [running]) or
     * found not due. [awaitReady] waits on this one, so an [awaitIdle] after it sees that sync.
     */
    private val handled = MutableStateFlow<ResolvedProvider?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val control = Dispatchers.Default.limitedParallelism(1)

    // Only touched on [control].
    private var running: Deferred<TrustNetworkRun>? = null
    private var runningFor: ServiceProviderTag? = null
    private var forceDownloadUntil = 0L

    /**
     * Held while the provider changes and while a sync publishes its result, so a sync for a
     * provider that is being replaced (or removed) can never write its files or network after
     * the change.
     */
    private val commitLock = Mutex()

    /** Full checks that could not reconcile in a row. Only the running sync touches it. */
    private var failedFullChecks = 0

    init {
        scope.launch(Dispatchers.IO) {
            // Load whatever is on disk right away: the provider list may take a moment to
            // resolve, and a push must not wait on that.
            _network.value = store?.readIndex()
            loaded.value = true

            rankProvider.collectLatest { choice ->
                // Not read yet: keep the tentative index.
                if (choice != null) onProvider(choice)
            }
        }
    }

    /**
     * The provider [network] belongs to: the last choice applied, not the raw [rankProvider]. That
     * one goes null while a newer 10040's private part cannot be decrypted yet (an external signer
     * in the background); read here, it made a finished download look like a provider change and
     * threw it away.
     */
    private val currentProvider: ServiceProviderTag? get() = applied.value?.provider

    /**
     * The switch every filter keys on (through [TrustVerdicts.isActive]): true when the provider
     * on the list has its complete set loaded. [network] only ever holds a complete set: a sync
     * applies its result only when complete, and only applied results are saved. Before the
     * provider list is first read, the set on disk stands for it (it was complete when saved,
     * and is deleted when the list drops the provider), so a push in a cold process is filtered.
     */
    val isActive: Boolean get() = _network.value != null

    /** The decisions as of now, for [me] following [follows]. Prefer [verdicts] in feeds. */
    fun snapshot(
        me: HexKey,
        follows: Set<HexKey>,
    ) = TrustVerdicts(_network.value, _overlay.value, minTrustScore.value, me, follows, verdictRevision.value)

    /**
     * Snapshots for [me], published whenever an answer can change: a new index, a new minimum
     * score, a card that moves someone in or out, a new follow list. A header-only change (an
     * update that found nothing) or a rank change that stays on the same side publishes nothing.
     */
    fun verdicts(
        me: HexKey,
        follows: StateFlow<Set<HexKey>>,
        scope: CoroutineScope,
    ): StateFlow<TrustVerdicts> =
        combine(_network, minTrustScore, verdictRevision, follows) { _, _, _, followed -> snapshot(me, followed) }
            .distinctUntilChanged { old, new -> old.sameAnswersAs(new) }
            .stateIn(scope, SharingStarted.Eagerly, snapshot(me, follows.value))

    /** The provider's rank for [pubkey], live: a newer card seen since the sync, else the index. */
    fun rankOf(pubkey: HexKey): Int? = rankIn(_network.value, _overlay.value, pubkey)

    fun followersOf(pubkey: HexKey): Int? {
        val loaded = _network.value ?: return null
        val card = _overlay.value[pubkey] ?: return loaded.index.followersOf(pubkey)
        return if (card.rank != null) card.followers else null
    }

    /** Why [pubkey] is or is not in [me]'s network, given who [me] follows. */
    fun explain(
        pubkey: HexKey,
        me: HexKey,
        follows: Set<HexKey>,
    ): TrustVerdict = snapshot(me, follows).explain(pubkey)

    /**
     * Takes the provider's cards that reached the app between syncs (profiles on screen ask
     * the provider's relay for theirs). A card newer than the index wins until the next sync,
     * which downloads it anyway. Expects signatures already checked, as `LocalCache` does.
     * Kind 5 deletions are left to the sync.
     */
    fun offer(events: Iterable<UserAssertionEvent>) {
        val cards = events.toList()
        if (cards.isEmpty()) return
        var flipped = false
        // Compare-and-set: a sync pruning the overlay or a provider change clearing it at the
        // same time is never undone by a stale copy.
        _overlay.update { overlay ->
            flipped = false
            val loaded = _network.value ?: return@update overlay
            withCards(overlay, loaded, cards, minTrustScore.value) { flipped = true }
        }
        if (flipped) verdictRevision.update { it + 1 }
    }

    /** [overlay] plus the [cards] newer than [loaded]'s index. Calls [onFlip] when one moves someone in or out. */
    private fun withCards(
        overlay: Map<HexKey, TrustOverlayCard>,
        loaded: TrustNetwork,
        cards: List<UserAssertionEvent>,
        minScore: Int,
        onFlip: () -> Unit,
    ): Map<HexKey, TrustOverlayCard> {
        var result: MutableMap<HexKey, TrustOverlayCard>? = null
        for (event in cards) {
            if (event.pubKey != loaded.header.provider) continue
            // At or before the cursor the index has it (or a deletion the sync saw removed it).
            if (event.createdAt <= loaded.header.syncCursor) continue
            val subject = event.aboutUser()
            if (subject.length != 64 || !Hex.isHex64(subject)) continue
            val held = (result ?: overlay)[subject]
            if (held != null && held.createdAt >= event.createdAt) continue

            val before = passesMinRank(if (held != null) held.rank else loaded.index.rankOf(subject), minScore)
            val card =
                TrustOverlayCard(
                    rank = memberRank(event.rank()?.coerceAtMost(127)),
                    followers = (event.followerCount() ?: 0).coerceAtLeast(0),
                    createdAt = event.createdAt,
                )
            // One copy of the overlay per batch, not per card.
            val batch = result ?: overlay.toMutableMap().also { result = it }
            batch[subject] = card
            if (passesMinRank(card.rank, minScore) != before) onFlip()
        }
        return result ?: overlay
    }

    /**
     * Waits until the index file has been read (or [timeoutMs] passes). Returns at once after
     * the first load. For code that runs in a freshly started process, like push handling.
     */
    suspend fun awaitLoaded(timeoutMs: Long = 3_000) {
        if (loaded.value) return
        withTimeoutOrNull(timeoutMs) { loaded.first { it } }
    }

    /**
     * Waits until the index has been read and matched against the resolved provider, so
     * [network] is final, and (with [autoSync]) the sync that match made due has started:
     * [awaitIdle] then waits for it. For one-shot callers; the apps use [awaitLoaded].
     */
    suspend fun awaitReady(timeoutMs: Long = 10_000) {
        withTimeoutOrNull(timeoutMs) { handled.first { it != null && it === rankProvider.value } }
    }

    private suspend fun onProvider(choice: ResolvedProvider) {
        // A sync for the provider being replaced (or removed) would only be dropped at its commit,
        // after downloading tens of MB, and the new provider's sync waits for it to end.
        withContext(control) {
            if (running?.isActive == true && runningFor != choice.provider) running?.cancel()
        }
        commitLock.withLock { applyProvider(choice) }
        // Started here rather than queued (syncIfStale): a caller woken by [handled] must find it
        // in [running], or its awaitIdle returns before the sync has begun.
        if (autoSync) withContext(control) { start(requested = null, force = false) }
        handled.value = choice
    }

    private suspend fun applyProvider(choice: ResolvedProvider) {
        val provider = choice.provider
        // The previous provider's problem or wait is not the new one's. The same provider again
        // (another row of the 10040 changed) keeps it: during its first download there is no
        // network yet, and resetting would hide the running download.
        if (provider == null || applied.value?.provider != provider) _status.value = TrustNetworkSyncStatus()
        if (provider == null) {
            // The list says there is no provider (removed here or on another device): drop the
            // files too, or the next cold start would filter with them until the list is read.
            withContext(Dispatchers.IO) { store?.delete() }
            _network.value = null
            _overlay.value = emptyMap()
        } else {
            val current = _network.value
            if (current == null || !current.isFrom(provider)) {
                // The old network goes before the overlay is cleared: while it stands, [offer]
                // accepts the old provider's cards into the fresh overlay, where they would
                // outrank the new provider's index.
                _network.value = null
                _overlay.value = emptyMap()
                _network.value =
                    withContext(Dispatchers.IO) {
                        val onDisk = store?.readIndex()
                        // Another provider's files are no longer the user's network: a cold start
                        // (a push) would filter with them until the list is read, for as long as
                        // the new provider's first download takes.
                        if (onDisk != null && !onDisk.isFrom(provider)) store.delete()
                        onDisk?.takeIf { it.isFrom(provider) }
                    }
            }
        }
        applied.value = choice
    }

    /**
     * Call before publishing a new provider from the settings screen: the user is waiting for
     * it, so its first download may start on mobile data, for the next [FORCE_DOWNLOAD_WINDOW_SECS].
     */
    fun expectNewProvider() {
        scope.launch(control) { forceDownloadUntil = clock() + FORCE_DOWNLOAD_WINDOW_SECS }
    }

    /**
     * Starts whatever sync is due ([dueSync]), unless one is running. [force] runs an update (or
     * the download) now, and lets a download use a metered network.
     */
    fun syncIfStale(force: Boolean = false) {
        scope.launch(control) { start(requested = null, force = force) }
    }

    /** Re-downloads everything for the current provider, discarding the local index. */
    fun redownload() {
        scope.launch(control) { start(requested = TrustNetworkSyncStatus.Kind.DOWNLOAD, force = true) }
    }

    /**
     * Starts whatever sync is due, once the provider list is read, and waits for it (or for the
     * one already running). For a background job that must not end before the sync does.
     */
    suspend fun syncDue(): TrustNetworkRun? {
        awaitReady()
        return withContext(control) { start(requested = null, force = false) ?: running }?.awaitRun()
    }

    /**
     * Suspends until no sync runs. A run that ends can start the next one from its completion
     * (its provider was replaced while it unwound), on [control] after it: that one is waited for too.
     */
    suspend fun awaitIdle() {
        var joined: Deferred<TrustNetworkRun>? = null
        while (true) {
            val run = withContext(control) { running } ?: return
            // Its completion, queued on [control] before this read, did not clear it: the scope
            // is cancelled, so nothing will. Looping would spin on a finished run.
            if (run === joined) return
            run.join()
            joined = run
        }
    }

    /**
     * Runs a sync now and returns what happened: [kind], or whatever is due when null. For
     * one-shot callers like the CLI; the apps use [syncIfStale]. Returns null when there is no
     * provider, nowhere to store the index, no source, or another sync is running.
     */
    suspend fun syncNow(kind: TrustNetworkSyncStatus.Kind? = null): TrustNetworkRun? {
        awaitReady()
        return withContext(control) { start(requested = kind, force = true) }?.awaitRun()
    }

    /** The run's result, or null when it was cancelled (its provider was replaced) while this caller lives on. */
    private suspend fun Deferred<TrustNetworkRun>.awaitRun(): TrustNetworkRun? =
        try {
            await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        }

    /** On [control]: decides and launches a sync. Null when none started. */
    private fun start(
        requested: TrustNetworkSyncStatus.Kind?,
        force: Boolean,
    ): Deferred<TrustNetworkRun>? {
        // Not isActive: a run cancelled for a replaced provider is no longer active while it still
        // unwinds (a checkpoint write, its status reset). Starting the next one then would race it
        // for the status and the partial file; its completion starts the next run instead.
        if (running?.isCompleted == false) return null
        val choice = rankProvider.value ?: return null
        val provider = choice.provider ?: return null
        if (store == null || source == null) return null
        // Until the file on disk is read and matched to this provider, "no index" would mean a
        // cold download of tens of MB. onProvider calls back once it is.
        if (applied.value !== choice) return null

        val allowMetered = force || clock() < forceDownloadUntil
        val current = _network.value?.takeIf { it.isFrom(provider) }
        val kind = requested ?: dueSync(current, clock(), allowMetered) ?: return null
        if (kind == TrustNetworkSyncStatus.Kind.DOWNLOAD) {
            if (!allowMetered && !canDownloadLarge()) {
                _status.update { it.copy(waitingForUnmetered = true) }
                return null
            }
            forceDownloadUntil = 0
        }

        val run = scope.async(Dispatchers.IO) { runSync(store, source, provider, current.takeUnless { kind == TrustNetworkSyncStatus.Kind.DOWNLOAD }, kind, allowMetered) }
        running = run
        runningFor = provider
        run.invokeOnCompletion {
            scope.launch(control) {
                if (running === run) {
                    running = null
                    runningFor = null
                }
                // The provider changed while it ran: its result was dropped, and the new
                // provider's trigger found it running. Start the new one now. A cancelled run too:
                // switching away and back (P, Q, P) leaves the provider equal, but P's files are gone.
                if (autoSync && (currentProvider != provider || run.isCancelled)) start(requested = null, force = false)
            }
        }
        return run
    }

    private suspend fun runSync(
        store: TrustNetworkStore,
        source: TrustNetworkSource,
        provider: ServiceProviderTag,
        current: TrustNetwork?,
        requested: TrustNetworkSyncStatus.Kind,
        allowMetered: Boolean,
    ): TrustNetworkRun {
        val mayDownload = allowMetered || canDownloadLarge()
        _status.value = TrustNetworkSyncStatus(running = requested)

        val run =
            try {
                source.connect { relay -> sync(relay, store, provider, current, requested, mayDownload, allowMetered) }
            } catch (e: CancellationException) {
                _status.value = TrustNetworkSyncStatus()
                throw e
            } catch (e: Throwable) {
                // Not only Exceptions: an Error (an OutOfMemoryError building a large index) that
                // escaped here left the status "running" for good, with no Retry, while nothing ran.
                Log.w(TAG, "Trust network $requested failed for ${provider.relayUrl.url}", e)
                TrustNetworkRun(requested, TrustNetworkOutcome.Failed(e.message ?: e::class.simpleName.orEmpty()))
            }

        _status.value =
            when (val outcome = run.outcome) {
                is TrustNetworkOutcome.Applied, is TrustNetworkOutcome.Unchanged, TrustNetworkOutcome.ProviderChanged -> TrustNetworkSyncStatus()
                TrustNetworkOutcome.WaitingForUnmetered -> TrustNetworkSyncStatus(waitingForUnmetered = true)
                TrustNetworkOutcome.NoScoresYet -> TrustNetworkSyncStatus(problem = TrustNetworkProblem.NoScoresYet)
                is TrustNetworkOutcome.Incomplete -> TrustNetworkSyncStatus(problem = TrustNetworkProblem.Failed(outcome.detail ?: "incomplete"))
                is TrustNetworkOutcome.Failed -> TrustNetworkSyncStatus(problem = TrustNetworkProblem.Failed(outcome.message))
            }
        return run
    }

    /** One sync over [relay]: [requested], or a download when the ids file is missing. */
    private suspend fun sync(
        relay: TrustNetworkConnection,
        store: TrustNetworkStore,
        provider: ServiceProviderTag,
        current: TrustNetwork?,
        requested: TrustNetworkSyncStatus.Kind,
        mayDownload: Boolean,
        allowMetered: Boolean,
    ): TrustNetworkRun {
        // The common case: nothing changed. Two COUNTs answer it before the ids file (12 MB at
        // 300k) is read, and nothing is written: the files keep an older lastUpdate, which only
        // means the next process start asks again.
        val news = if (requested == TrustNetworkSyncStatus.Kind.UPDATE && current?.header?.heldAtCursor != null) relay.news(provider, current.header) else null
        if (current != null && news != null && !news.any) {
            return commitUnchanged(provider, requested, current, current.header.copy(lastUpdate = clock()), result = null)
        }

        // An update or full check needs the id column saved with this index; without it,
        // download again.
        val ids = if (current != null) withContext(Dispatchers.IO) { store.readIds(current.header) }?.takeIf { it.size == current.index.size } else null
        val kind = if (current == null || ids == null) TrustNetworkSyncStatus.Kind.DOWNLOAD else requested
        if (kind == TrustNetworkSyncStatus.Kind.DOWNLOAD && !mayDownload) return TrustNetworkRun(kind, TrustNetworkOutcome.WaitingForUnmetered)

        _status.value = TrustNetworkSyncStatus(running = kind)
        val progress = TrustNetworkProgress { verified, expected -> _status.update { it.copy(verified = verified, expected = expected) } }

        val result =
            when (kind) {
                TrustNetworkSyncStatus.Kind.DOWNLOAD -> {
                    // A download cut off earlier (the process killed, the relay gone) resumes from
                    // its last checkpoint: at a phone's pace, the whole download is minutes.
                    val partial = withContext(Dispatchers.IO) { store.readPartial(provider.pubkey) }
                    if (partial != null) Log.d(TAG) { "Resuming the download from ${partial.cards} cards" }
                    val download = suspend { relay.download(provider, progress, checkpoint = { savePartial(store, it) }, resumeFrom = partial) }
                    if (allowMetered) download() else unlessMetered(download) ?: return TrustNetworkRun(kind, TrustNetworkOutcome.WaitingForUnmetered)
                }

                TrustNetworkSyncStatus.Kind.UPDATE -> {
                    relay.update(provider, current!!.header, current.index, ids!!, news, progress)
                }

                TrustNetworkSyncStatus.Kind.FULL_CHECK -> {
                    fullCheck(relay, provider, current!!, ids!!, mayDownload, progress)
                }
            }

        // A partial walk must not become the network (a cold one would wrongly reject everyone
        // it missed), nor advance the cursor past what it skipped.
        if (!result.complete) {
            // Keep what the download got, so the next try only fetches the rest.
            if (kind == TrustNetworkSyncStatus.Kind.DOWNLOAD && currentProvider == provider && result.index.size + result.ids.tombstones > 0) {
                withContext(Dispatchers.IO) { savePartial(store, TrustNetworkPartial(result.header, result.index, result.ids)) }
            }
            return TrustNetworkRun(kind, TrustNetworkOutcome.Incomplete(result.detail), result)
        }

        // A provider still computing a new user's scores has published nothing yet. An empty
        // network would leave only follows as "known", so never apply one, whichever sync
        // produced it (a full check that fell back to a download included).
        if (result.index.size == 0) {
            if (kind == TrustNetworkSyncStatus.Kind.DOWNLOAD) withContext(Dispatchers.IO) { store.deletePartial() }
            return TrustNetworkRun(kind, TrustNetworkOutcome.NoScoresYet, result)
        }

        if (result.unchanged && current != null) return commitUnchanged(provider, kind, current, result.header, result)

        return commitLock.withLock {
            if (currentProvider != provider) return@withLock TrustNetworkRun(kind, TrustNetworkOutcome.ProviderChanged, result)
            val header =
                withContext(Dispatchers.IO) {
                    store.write(result.header, result.index, result.ids).also { store.deletePartial() }
                }
            // The sync fetched everything up to its cursor; only cards seen after it still add.
            // Pruned before the network is published, so no snapshot pairs the new index with
            // cards older than it.
            // Published first, so a card offered from now on is checked against the new cursor;
            // then the cards it superseded go, and verdicts re-run if any did.
            _network.value = TrustNetwork(header, result.index)
            pruneOverlay(header.syncCursor)
            Log.d(TAG) { "$kind done: ${result.index.size} entries, ${result.received} received, ${result.invalid} invalid (${result.detail})" }
            TrustNetworkRun(kind, TrustNetworkOutcome.Applied(result), result)
        }
    }

    /**
     * Runs [download], one not allowed on mobile data, and stops it (null) if the phone moves to a
     * metered network meanwhile. The phone dropping Wi-Fi mid-download (or WorkManager cancelling
     * the worker for it) otherwise left the rest of tens of MB to go over mobile data. The last
     * checkpoint stays, so the download resumes from it on Wi-Fi.
     */
    private suspend fun <T> unlessMetered(download: suspend () -> T): T? =
        coroutineScope {
            val work = async { download() }
            val watch =
                launch {
                    while (canDownloadLarge()) delay(meteredCheckMs)
                    Log.d(TAG) { "On a metered network now: the download waits for Wi-Fi" }
                    work.cancel()
                }
            try {
                work.await()
            } catch (e: CancellationException) {
                // Our own stop, unless the sync itself was cancelled.
                currentCoroutineContext().ensureActive()
                null
            } finally {
                watch.cancel()
            }
        }

    /** A checkpoint that fails to save only costs the resume, never the download. */
    private fun savePartial(
        store: TrustNetworkStore,
        partial: TrustNetworkPartial,
    ) {
        try {
            store.writePartial(partial)
        } catch (e: Exception) {
            Log.w(TAG, "Could not save the download's progress", e)
        }
    }

    /** Publishes [header] over [current]'s index (nothing to write), unless the provider changed. */
    private suspend fun commitUnchanged(
        provider: ServiceProviderTag,
        kind: TrustNetworkSyncStatus.Kind,
        current: TrustNetwork,
        header: TrustNetworkHeader,
        result: TrustNetworkSyncResult?,
    ): TrustNetworkRun =
        commitLock.withLock {
            if (currentProvider != provider) return@withLock TrustNetworkRun(kind, TrustNetworkOutcome.ProviderChanged, result)
            // Same index instance: feeds keyed on it do not rebuild. Only a finished full check
            // is saved (its date decides when the next one runs), or held counts the saved
            // header lacked (a file from before they existed would otherwise make every process
            // start reconcile again); an update's lastUpdate is not, which only means the next
            // process start asks again.
            val countsLearned =
                header.heldAtCursor != current.header.heldAtCursor ||
                    header.heldAfterCursor != current.header.heldAfterCursor ||
                    header.heldTotal != current.header.heldTotal
            // A kind 5 that changed nothing still moves the cursor past it, or every update finds it again.
            val cursorMoved = header.syncCursor != current.header.syncCursor
            val saved =
                if (result != null && (header.lastFullCheck != current.header.lastFullCheck || countsLearned || cursorMoved)) {
                    withContext(Dispatchers.IO) { store?.write(header, current.index, result.ids) }
                } else {
                    null
                }
            _network.value = TrustNetwork(saved ?: header.copy(generation = current.header.generation), current.index)
            // Cards seen between syncs at or before the new cursor are the index's to answer now.
            if (cursorMoved) pruneOverlay(header.syncCursor)
            TrustNetworkRun(kind, TrustNetworkOutcome.Unchanged(header), result)
        }

    /** Drops the cards seen between syncs that the index now holds (at or before [cursor]). */
    private fun pruneOverlay(cursor: Long) {
        var removed = false
        _overlay.update { cards ->
            val kept = cards.filterValues { it.createdAt > cursor }
            removed = kept.size != cards.size
            kept
        }
        if (removed) verdictRevision.update { it + 1 }
    }

    /**
     * The weekly check. A relay that cannot reconcile (no NIP-77, or a timeout: they look the
     * same) gets a cheap update instead, so the network stays current, and the check is retried
     * in [FULL_CHECK_RETRY_SECS]; after [MAX_FAILED_FULL_CHECKS] in a row in one process, a fresh
     * download replaces it when [mayDownload].
     */
    private suspend fun fullCheck(
        relay: TrustNetworkConnection,
        provider: ServiceProviderTag,
        current: TrustNetwork,
        ids: TrustNetworkIds,
        mayDownload: Boolean,
        progress: TrustNetworkProgress,
    ): TrustNetworkSyncResult {
        val reconciled = relay.reconcile(provider, current.header, current.index, ids, progress)
        if (reconciled != null) {
            failedFullChecks = 0
            return reconciled
        }
        failedFullChecks++
        if (failedFullChecks >= MAX_FAILED_FULL_CHECKS && mayDownload) {
            failedFullChecks = 0
            return relay.download(provider, progress)
        }
        // Keep current with a cheap update, and try the check again in a day rather than on
        // every app open (a relay that ignores NIP-77 makes each attempt wait out a timeout).
        val updated = relay.update(provider, current.header, current.index, ids, null, progress)
        val retryAt = clock() - FULL_CHECK_EVERY_SECS + FULL_CHECK_RETRY_SECS
        return TrustNetworkSyncResult(
            header = updated.header.copy(lastFullCheck = maxOf(updated.header.lastFullCheck, retryAt)),
            index = updated.index,
            ids = updated.ids,
            complete = updated.complete,
            invalid = updated.invalid,
            received = updated.received,
            detail = "full check unavailable; ${updated.detail}",
            unchanged = updated.unchanged,
        )
    }

    companion object {
        private const val TAG = "TrustNetworkState"

        /**
         * Some providers recompute on demand, and checking costs two COUNTs of a few bytes when
         * nothing changed (see `updateTrustNetwork`), so every app open after 15 minutes asks.
         */
        const val UPDATE_EVERY_SECS = 15 * 60L
        const val FULL_CHECK_EVERY_SECS = 7 * 24 * 60 * 60L
        const val FORCE_DOWNLOAD_WINDOW_SECS = 10 * 60L
        const val MAX_FAILED_FULL_CHECKS = 3
        const val FULL_CHECK_RETRY_SECS = 24 * 60 * 60L
        const val METERED_CHECK_MS = 5_000L

        /**
         * Which sync is due for [current] at [now]: a download when there is no index, a full
         * check once a week, an update every 15 minutes (or now, when [force]d), else none.
         */
        fun dueSync(
            current: TrustNetwork?,
            now: Long,
            force: Boolean,
        ): TrustNetworkSyncStatus.Kind? =
            when {
                current == null -> TrustNetworkSyncStatus.Kind.DOWNLOAD
                now - current.header.lastFullCheck >= FULL_CHECK_EVERY_SECS -> TrustNetworkSyncStatus.Kind.FULL_CHECK
                force || now - current.header.lastUpdate >= UPDATE_EVERY_SECS -> TrustNetworkSyncStatus.Kind.UPDATE
                else -> null
            }
    }
}
