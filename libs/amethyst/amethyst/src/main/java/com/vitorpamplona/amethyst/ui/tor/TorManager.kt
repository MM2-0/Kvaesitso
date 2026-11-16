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
package com.vitorpamplona.amethyst.ui.tor

import com.vitorpamplona.amethyst.commons.tor.TorPreferencesPort
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * There should be only one instance of the Tor binding per app.
 *
 * Tor will connect as soon as status is listened to.
 *
 * [service] and [torPrefs] are constructor-injected so the manager can be unit-tested
 * with in-memory fakes — see `TorManagerTest`. [ioDispatcher] is the dispatcher for
 * background I/O (DataStore reads/writes, [TorBackend] calls); tests pass a
 * `TestDispatcher` so virtual time controls scheduling.
 */
class TorManager(
    private val torPrefs: TorPreferencesPort,
    val service: TorBackend,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    /**
     * In-memory only — when true, the manager emits [TorServiceStatus.Off] regardless of
     * the persisted [TorType]. Cleared on process death, on network change, and on any
     * user-initiated change to [TorType].
     */
    val sessionBypass = MutableStateFlow(false)

    /**
     * Epoch-millis of the user's most recent "Use regular connection" choice, persisted
     * across cold starts. While [APPROVAL_REMEMBER_MS] hasn't elapsed, a stuck-connecting
     * timeout silently flips [sessionBypass] without re-prompting.
     */
    @Volatile private var lastBypassApprovalMs: Long = 0L

    /**
     * Bumped by self-heal paths ([onNetworkChange], stuck-Connecting watcher) so the
     * [status] combine re-fires and re-enters the [TorType.INTERNAL] branch — which
     * calls [TorService.start] again and, because [TorService.reset] flipped
     * `initialized` back to false, runs full Arti re-initialization with a fresh
     * bootstrap, new guards, new circuits.
     */
    private val resetEpoch = MutableStateFlow(0)

    /** Wall-clock of the last automatic self-heal — rate-limits the stuck-Connecting reset. */
    @Volatile private var lastSelfHealAtMs: Long = 0L

    /**
     * Flipped the first time [status] reaches [TorServiceStatus.Active] in this process. Before
     * that, the stuck-Connecting watchdog uses the gentler [TorService.reset] (drop client only)
     * rather than [TorService.resetWithCleanState] — because on a slow legitimate first
     * bootstrap there is no stale state to wipe, and wiping just forces an unnecessary
     * re-bootstrap cycle. Once we've seen Tor work once, persisted `arti/state/` is fair game
     * for the recovery to wipe.
     *
     * Also seeded at startup from [TorBackend.hasBootstrappedBefore]: the in-memory flag resets
     * every process, but Arti's persisted guard sample does not. If it already holds a confirmed
     * guard, Tor bootstrapped here before, so a stuck Connecting span means the persisted state
     * is stale/poisoned and the watchdog should wipe it. Without this seed a fresh process would
     * mistake poisoned guards for a pristine first bootstrap and only ever gentle-reset (keeping
     * the poison), looping forever — the exact "can't connect to Tor across restarts" failure.
     */
    @Volatile private var hasEverBootstrapped: Boolean = false

    /**
     * Epoch-millis of the first moment Tor was expected to work and didn't, spanning the self-heal
     * retries in between. 0 while Tor is working or off. See [connectionFailure].
     */
    @Volatile private var tryingSinceMs: Long = 0L

    /**
     * Epoch-millis of the most recent transition INTO a trying state. Distinct from
     * [tryingSinceMs], which deliberately spans self-heal retries: this one restarts on every
     * attempt, because the patience owed to a directory download is per-attempt.
     */
    @Volatile private var lastTryingTransitionMs: Long = 0L

    /**
     * Epoch-millis of the last time the directory download moved. Seeded when a download starts so
     * a fresh attempt is never mistaken for a stalled one, then stamped by the collector below on
     * every distinct progress value.
     */
    @Volatile private var lastProgressAtMs: Long = 0L

    /**
     * Whether the bypass prompt is currently raised. Survives the transient [TorServiceStatus.Off]
     * a self-heal reset passes through, so the dialog stays up instead of blinking. Cleared
     * wherever [tryingSinceMs] is.
     */
    @Volatile private var failureRaised: Boolean = false

    /**
     * Whether this outage already got its automatic restart. The first time the
     * [BOOTSTRAP_TIMEOUT_MS] deadline passes, [connectionFailure] restarts Tor itself (the same
     * reset as [restartTor]) instead of prompting, and the user is only asked if that attempt
     * also times out. Cleared where the outage genuinely ends: a bootstrapped Tor, a network
     * change, a bypass, or a user change of [TorType].
     */
    @Volatile private var autoRestartedThisOutage: Boolean = false

    /** Consecutive gentle (state-preserving) self-heals with no successful bootstrap in between. */
    @Volatile private var consecutiveGentleResets: Int = 0

    init {
        // Seed hasEverBootstrapped from persisted on-disk evidence before the watchdog can fire
        // (well under SELF_HEAL_AFTER_MS), so a stuck bootstrap on a previously-working install
        // wipes its stale guard state instead of nursing it.
        scope.launch(ioDispatcher) {
            if (service.hasBootstrappedBefore()) {
                hasEverBootstrapped = true
                Log.d("TorManager") { "Seeded hasEverBootstrapped from persisted confirmed guard" }
            }
        }

        scope.launch(ioDispatcher) {
            lastBypassApprovalMs = torPrefs.loadLastBypassApprovalMs()
        }

        // Any user-initiated change to torType clears the in-memory bypass AND the
        // remembered-approval window. Otherwise a single past "Use regular connection"
        // traps the user in a silent-bypass loop: every Connecting span >60s
        // auto-flips sessionBypass without showing the dialog, force-stop preserves
        // the DataStore-backed approval, and toggling Tor off/on only clears the
        // in-memory half — so wiping app data becomes the only recovery path.
        torPrefs.torType
            .drop(1)
            .onEach {
                sessionBypass.value = false
                lastBypassApprovalMs = 0L
                tryingSinceMs = 0L
                failureRaised = false
                autoRestartedThisOutage = false
                torPrefs.saveLastBypassApprovalMs(0L)
            }.launchIn(scope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val status =
        combine(
            torPrefs.torType,
            torPrefs.externalSocksPort,
            sessionBypass,
            resetEpoch,
        ) { torType, externalSocksPort, bypass, _ ->
            Triple(torType, externalSocksPort, bypass)
        }.transformLatest { (torType, externalSocksPort, bypass) ->
            if (bypass) {
                service.stop()
                emit(TorServiceStatus.Off)
                return@transformLatest
            }
            when (torType) {
                TorType.INTERNAL -> {
                    // Subscribe to the backend's status BEFORE start(), not after.
                    //
                    // start() awaits a blocking JNI bootstrap that runs to its own timeout, so
                    // awaiting it first meant nothing observed Connecting until that whole attempt
                    // had already finished. Every timer keyed on the Connecting span therefore
                    // started one full attempt late: on device the stuck-watchdog fired at 105s
                    // instead of 45s, and the connection-failure dialog measured its 60s from the
                    // wrong instant. Running start() alongside the emitAll makes the status the
                    // app reacts to the status the service is actually in.
                    coroutineScope {
                        launch { service.start() }
                        emitAll(service.status)
                    }
                }

                TorType.OFF -> {
                    service.stop()
                    emit(TorServiceStatus.Off)
                }

                TorType.EXTERNAL -> {
                    service.stop()
                    if (externalSocksPort > 0) {
                        emit(TorServiceStatus.Active(externalSocksPort))
                    } else {
                        emit(TorServiceStatus.Off)
                    }
                }
            }
        }.catch { e ->
            Log.e("TorManager") { "Tor service error: ${e.message}" }
            emit(TorServiceStatus.Off)
        }.flowOn(ioDispatcher)
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(30000),
                TorServiceStatus.Off,
            )

    val activePortOrNull: StateFlow<Int?> =
        status
            .map {
                it.socksPort
            }.stateIn(
                scope,
                SharingStarted.WhileSubscribed(2000),
                status.value.socksPort,
            )

    /**
     * Emits true after [BOOTSTRAP_TIMEOUT_MS] of continuous [TorServiceStatus.Connecting]
     * (and we are not already bypassing). When the user has approved a bypass within the
     * last [APPROVAL_REMEMBER_MS] this auto-flips [sessionBypass] silently and stays at
     * false. Otherwise the first expiry in an outage restarts Tor automatically and stays at
     * false; only when that restarted attempt also runs out its own [BOOTSTRAP_TIMEOUT_MS]
     * does it emit true so the UI can show the prompt.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionFailure: StateFlow<Boolean> =
        status
            .transformLatest { s ->
                if (!s.isTryingToConnect()) {
                    // Deliberately does NOT clear [tryingSinceMs]: the self-heal watchdog's own
                    // reset passes through Off on its way back to Bootstrapping, and clearing here
                    // would let the retry cycle rearm the timer forever. It is cleared where the
                    // outage genuinely ends — on a bootstrapped Tor, or on a user intent change.
                    //
                    // For the same reason this re-emits [failureRaised] rather than a flat false:
                    // that transient Off would otherwise dismiss the dialog, and the following
                    // Bootstrapping would re-raise it immediately (its deadline has already
                    // passed), so a stuck Tor blinked a modal at the user on every watchdog tick.
                    emit(failureRaised)
                    return@transformLatest
                }

                // Measure from when Tor STOPPED WORKING, not from this status span.
                //
                // `transformLatest` restarts on every status change, and the self-heal watchdog
                // deliberately bounces Off -> Bootstrapping every SELF_HEAL_AFTER_MS (45s) while
                // stuck — less than this 60s timeout. Keyed on the span, the timer was reset by its
                // own watchdog before it could ever expire, so the user was never offered the
                // bypass no matter how long Tor stayed broken. The question being asked is "has Tor
                // been down for a minute", which spans those retries.
                if (tryingSinceMs == 0L) tryingSinceMs = nowMs()
                emit(false)

                val remaining = BOOTSTRAP_TIMEOUT_MS - (nowMs() - tryingSinceMs)
                if (remaining > 0) delay(remaining)

                // Never offer to give up on a bootstrap attempt that is still running: a cold
                // install legitimately outlasts this timeout, and prompting mid-attempt asks the
                // user to abandon something that is working.
                service.bootstrapInFlight.first { !it }

                if (rememberedApprovalActive()) {
                    sessionBypass.value = true
                    emit(false)
                } else if (!autoRestartedThisOutage) {
                    // Try the restart the dialog would offer before asking. The reset drives
                    // status through Off, which cancels this block; the next attempt re-arms the
                    // timer from scratch because restart() cleared [tryingSinceMs].
                    autoRestartedThisOutage = true
                    emit(false)
                    restart(byUser = false)
                } else {
                    failureRaised = true
                    emit(true)
                }
            }.stateIn(
                scope,
                SharingStarted.WhileSubscribed(2000),
                false,
            )

    /**
     * Fires every [SELF_HEAL_AFTER_MS] for as long as status stays [TorServiceStatus.Connecting].
     * Drives the watchdog wired up below. `transformLatest` cancels the pending delay whenever the
     * status changes, so a brief Connecting blip never fires.
     *
     * It **repeats** rather than firing once per Connecting span, and that is the whole point. The
     * retry loop is driven by status transitions, but the failure it has to recover from produces
     * no transition: when the native bootstrap hits its own timeout, [TorService.start] gives up
     * and deliberately leaves status at Connecting for this watchdog to retry. A one-shot signal
     * has already been consumed by then, so nothing ever re-armed and Tor sat at Connecting
     * forever — no retry, no dialog change, no recovery short of a network-identity change or a
     * process restart. Repeating means every stuck span is re-examined until it stops being stuck.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val selfHealSignal =
        status.transformLatest { s ->
            if (s.isTryingToConnect()) {
                while (true) {
                    delay(SELF_HEAL_AFTER_MS)
                    emit(Unit)
                }
            }
        }

    init {
        // Self-heal watchdog. When status sits at Connecting for longer than
        // SELF_HEAL_AFTER_MS, the in-memory Arti state is likely stuck — bad guards,
        // broken circuits, expired consensus. Drop the TorClient and bump resetEpoch
        // so the status combine re-fires and re-enters the INTERNAL branch, which
        // runs full Arti re-init. Rate-limited so a permanently broken network
        // doesn't loop us. Fires BEFORE the 60s connectionFailure dialog so most
        // users never see it.
        //
        // Pre-first-bootstrap: gentle reset (drop client, keep state). On a slow
        // legitimate first bootstrap there's nothing on disk worth wiping, and
        // wiping just costs another full bootstrap cycle.
        // Post-first-bootstrap: full reset (drop client + wipe state). Once we've
        // seen Tor work once, a stuck Connecting almost certainly means stale on-disk
        // state from a different network needs to go.
        status
            .onEach {
                if (it.isFullyBootstrapped) {
                    hasEverBootstrapped = true
                    tryingSinceMs = 0L
                    lastTryingTransitionMs = 0L
                    failureRaised = false
                    autoRestartedThisOutage = false
                    consecutiveGentleResets = 0
                } else if (it.isTryingToConnect() && lastTryingTransitionMs == 0L) {
                    lastTryingTransitionMs = nowMs()
                    // A download that has just begun has not stalled, whatever the last attempt did.
                    lastProgressAtMs = nowMs()
                } else if (it == TorServiceStatus.Off) {
                    // A reset passes through Off on its way to a fresh attempt; the next
                    // Bootstrapping earns a full patience window of its own.
                    lastTryingTransitionMs = 0L
                }
            }.launchIn(scope)

        // Rotten guard sample while Tor is otherwise UP. The watchdog above only fires on a status
        // stuck at Connecting, and the on-disk check only sees guards Arti has permanently retired —
        // so a sample whose guards are all merely *unreachable* falls between them: status reaches
        // Active, `guards.json` still lists usable entries, and every circuit fails anyway. Observed
        // in the field surviving repeated restarts with ~87% of relay connections failing. Arti's own
        // AllGuardsDown log is the only reliable signal, so route it through the same rate-limited
        // wipe. Always a clean-state reset: the whole point is that the persisted sample is the
        // problem.
        // Stamps the moment Tor last moved. A StateFlow only emits distinct values, so this fires
        // exactly when progress changes — making `lastProgressAtMs` the age of the last real
        // advance rather than the age of the attempt.
        service.bootstrapProgress
            .onEach { lastProgressAtMs = nowMs() }
            .launchIn(scope)

        service.guardsDownSignal
            .onEach {
                val now = nowMs()
                if (now - lastSelfHealAtMs < SELF_HEAL_COOLDOWN_MS) return@onEach
                lastSelfHealAtMs = now
                Log.w("TorManager") { "Arti guard sample rotten at runtime — self-healing (drop client + wipe state)" }
                service.resetWithCleanState()
                resetEpoch.update { it + 1 }
            }.launchIn(scope)

        selfHealSignal
            .onEach {
                // Re-check: the signal repeats, so by the time it lands the status may have moved
                // on. Resetting a client that just reached Active is the opposite of self-healing.
                if (!status.value.isTryingToConnect()) return@onEach

                // A native attempt that is still running is not stuck — it is working. (Under
                // on-demand bootstrap `initialize` returns in ~130ms, so this only covers client
                // creation; the directory download is covered by the patience window below.)
                if (service.bootstrapInFlight.value) return@onEach

                val downloading = status.value is TorServiceStatus.Bootstrapping

                // A running directory download is judged on forward progress, not elapsed time.
                //
                // A timer cannot tell slow from stalled: measured cold downloads ran 12.6-34.4s on
                // this same hardware and network, so any fixed patience is either short enough to
                // kill healthy ones — and a reset discards the partial consensus, so firing early
                // can stop the download EVER completing — or long enough to sit uselessly on a dead
                // one. Progress separates them exactly: a download that is still advancing is left
                // alone indefinitely, and one that has not moved at all is reset promptly.
                if (downloading && nowMs() - lastProgressAtMs < BOOTSTRAP_STALL_MS) return@onEach

                val now = nowMs()
                if (now - lastSelfHealAtMs < selfHealCooldownMs()) return@onEach
                lastSelfHealAtMs = now

                // Never wipe while downloading. `resetWithCleanState` deletes `arti/cache`, which
                // is precisely the consensus this state is in the middle of fetching: wiping it
                // guarantees the next attempt restarts from zero, and on a slow link that loops
                // forever. The clean-state hammer is for a lifecycle that cannot even get a client
                // up, where the persisted state is the prime suspect.
                // Escalate a fresh install that cannot even get a client up.
                //
                // The inline `clearAllArtiData()` retry used to cover corrupt on-disk state; it was
                // removed because it fired on every failure, including "no network". But with
                // `hasEverBootstrapped` false there is no confirmed guard on disk, so the gentle
                // branch below would drop the client forever without ever wiping — and
                // `noUsableGuards()` only inspects `guards.json`, so a corrupt `cache/` or the rest
                // of `state/` is invisible to it. Escalate once the gentle path has demonstrably
                // failed several times in a row.
                val exhaustedGentleRetries = !downloading && consecutiveGentleResets >= GENTLE_RESETS_BEFORE_WIPE

                if ((hasEverBootstrapped || exhaustedGentleRetries) && !downloading) {
                    Log.w("TorManager") { "Tor stuck with no client for >${SELF_HEAL_AFTER_MS}ms — self-healing (drop client + wipe state)" }
                    consecutiveGentleResets = 0
                    service.resetWithCleanState()
                } else {
                    consecutiveGentleResets++
                    val what =
                        if (downloading) {
                            "directory download stuck at ${service.bootstrapProgress.value}/1000 for >${BOOTSTRAP_STALL_MS}ms"
                        } else {
                            "stuck with no client >${SELF_HEAL_AFTER_MS}ms"
                        }
                    Log.w("TorManager") { "Tor $what — self-healing (drop client only, keeping the consensus cache)" }
                    service.reset()
                }
                resetEpoch.update { it + 1 }
            }.launchIn(scope)
    }

    /**
     * How long to wait between self-heals.
     *
     * Once Tor has bootstrapped on this install, a reset is expensive and rarely the answer, so the
     * full [SELF_HEAL_COOLDOWN_MS] applies — a permanently broken network must not put us in a
     * reset loop. Before the first successful bootstrap the trade is reversed: there is no working
     * state to protect, retrying is nearly free (Arti's directory cache persists across attempts,
     * so each retry resumes rather than restarts), and the alternative is a brand-new install
     * sitting on a dead Tor for five minutes at a time. So a fresh install retries on
     * [FIRST_BOOTSTRAP_RETRY_COOLDOWN_MS] instead.
     */
    private fun selfHealCooldownMs(): Long = if (hasEverBootstrapped) SELF_HEAL_COOLDOWN_MS else FIRST_BOOTSTRAP_RETRY_COOLDOWN_MS

    /**
     * Tor is meant to be working and isn't yet — the span both the stuck watchdog and the
     * connection-failure dialog exist to bound.
     *
     * It is deliberately NOT `is Connecting`. Under on-demand bootstrap the client is created in
     * ~130ms, so status leaves Connecting almost immediately and spends the entire 12-34s directory
     * download in [TorServiceStatus.Bootstrapping]. Keying on Connecting alone would have made both
     * safety nets unreachable: a download that never completes would sit at Bootstrapping forever
     * with nothing watching it.
     */
    private fun TorServiceStatus.isTryingToConnect() = this != TorServiceStatus.Off && !isFullyBootstrapped

    fun rememberedApprovalActive(): Boolean {
        val ts = lastBypassApprovalMs
        return ts > 0 && (nowMs() - ts) < APPROVAL_REMEMBER_MS
    }

    /** Called when the user picks "Use regular connection". Starts a fresh 1-hour window. */
    fun approveBypassForOneHour() {
        val now = nowMs()
        tryingSinceMs = 0L
        failureRaised = false
        autoRestartedThisOutage = false
        lastBypassApprovalMs = now
        sessionBypass.value = true
        scope.launch(ioDispatcher) {
            torPrefs.saveLastBypassApprovalMs(now)
        }
    }

    /**
     * Called when the user picks "Restart Tor" on the connection-failure dialog. Unlike the
     * watchdog this ignores the self-heal cooldown, because the user asked for it. The same
     * restart runs once on its own before the dialog is ever shown (see [connectionFailure]).
     *
     * A stalled directory download gets the warm [TorBackend.reset], which keeps the partial
     * consensus in `arti/cache` so the next attempt resumes instead of starting over. Any other
     * stuck state (no client, no proxy) gets [TorBackend.resetWithCleanState], since the persisted
     * state is then the likeliest culprit.
     *
     * Clears the failure flags so the dialog closes and the new attempt gets its own full
     * [BOOTSTRAP_TIMEOUT_MS], and primes [lastSelfHealAtMs] so the watchdog doesn't reset the new
     * attempt right away.
     */
    fun restartTor() = restart(byUser = true)

    private fun restart(byUser: Boolean) {
        val downloading = status.value is TorServiceStatus.Bootstrapping
        tryingSinceMs = 0L
        lastTryingTransitionMs = 0L
        failureRaised = false
        consecutiveGentleResets = 0
        lastSelfHealAtMs = nowMs()
        Log.w("TorManager") {
            val who = if (byUser) "User restarted Tor" else "Tor not connecting — restarting it before asking the user"
            "$who (${if (downloading) "drop client, keep cache" else "drop client + wipe state"})"
        }
        scope.launch(ioDispatcher) {
            if (downloading) service.reset() else service.resetWithCleanState()
            resetEpoch.update { it + 1 }
        }
    }

    /**
     * Network identity changed (wifi↔cellular, captive portal cleared, regained from
     * offline). The old network's guards and circuits are dead, but Arti's in-memory
     * TorClient doesn't always notice — and even if it does, on-disk `state/` can hold
     * unreachable guards that the next process load will pick up again. Drop the
     * client, clear `sessionBypass`, clear the persisted approval, and bump
     * [resetEpoch] so the status flow re-enters the INTERNAL branch with
     * `initialized=false` — forcing a full Arti re-init with fresh bootstrap.
     */
    fun onNetworkChange() {
        sessionBypass.value = false
        lastBypassApprovalMs = 0L
        tryingSinceMs = 0L
        failureRaised = false
        autoRestartedThisOutage = false
        // Prevent the stuck-Connecting watchdog from firing a second reset while the
        // network-change bootstrap is still legitimately in progress (initial bootstrap
        // on a new network can take ~10–30s, sometimes longer).
        lastSelfHealAtMs = nowMs()
        scope.launch(ioDispatcher) {
            torPrefs.saveLastBypassApprovalMs(0L)
            service.reset()
            resetEpoch.update { it + 1 }
        }
    }

    /**
     * Tor is [TorServiceStatus.Active] (SOCKS proxy up, bootstrap "succeeded" off cached
     * consensus) yet every Tor-routed relay is failing — no successful Tor open while exit
     * failures pile up. The circuits behind the proxy are dead, but the lifecycle can't see
     * it: the stuck-Connecting watchdog and the [connectionFailure] dialog only arm while
     * status is [TorServiceStatus.Connecting], not Active. The relay layer (which knows both
     * the per-relay success/failure outcome and the Tor-routing of each url) detects the
     * all-failing condition and pokes us here — analogous to [onNetworkChange].
     *
     * The failure is exit-side, not entry-side: the dead circuits' *exits* can't reach the
     * relays (`ExitTimeout` / `RESOLVEFAILED`), while the guards (entry) and the cached
     * consensus are fine. So recovery is a **warm** [TorBackend.reset]: drop the in-process
     * client so the next start rebuilds the circuit pool from scratch — a fresh exit draw —
     * but keep `arti/state/` (the guards were never the problem) and the consensus cache, so
     * the re-bootstrap is a ~5s warm restart, not a ~60s cold consensus re-download. We
     * deliberately do NOT [TorBackend.resetWithCleanState] here: exits aren't persisted, so
     * wiping guards + cache can't improve exit selection, and the 60s cold bootstrap it forces
     * is exactly the blackout that strands users on the [connectionFailure] dialog. Then bump
     * [resetEpoch] so the status combine re-enters the INTERNAL branch and re-inits.
     *
     * Shares [lastSelfHealAtMs]/[SELF_HEAL_COOLDOWN_MS] with the Connecting watchdog so the
     * two can't thrash — at most one self-heal per cooldown window. If the fresh circuits are
     * still dead after the rotation, the cooldown suppresses further resets and the 60s
     * [connectionFailure] dialog still offers the user the bypass.
     */
    fun onTorCircuitsDead() {
        if (sessionBypass.value) return
        if (!status.value.isFullyBootstrapped) return
        val now = nowMs()
        if (now - lastSelfHealAtMs < SELF_HEAL_COOLDOWN_MS) return
        lastSelfHealAtMs = now
        Log.w("TorManager") { "Tor Active but all circuits failing — self-healing (drop client to rotate exits, keep state)" }
        scope.launch(ioDispatcher) {
            service.reset()
            resetEpoch.update { it + 1 }
        }
    }

    /**
     * Whether a Tor-routed dial has somewhere to go. Both callers
     * (`AppModules`' relay gate and the media-http `isTorActive` probe) are asking "can I send this
     * through Tor", not "is the directory ready" — a dial during the download queues on its own
     * circuit, which is strictly better than the alternative of refusing it or sending it in clear.
     */
    fun isSocksReady() = status.value.socksPort != null

    /**
     * Tor can carry traffic now — the gate for "start using Tor", as opposed to [isSocksReady]'s
     * "route through it if you do".
     *
     * Dialling merely because the port exists costs more than it saves: measured, relays dialled
     * during the download simply time out (Tor connect timeout is 30s, inside the 12-34s window)
     * and enter exponential backoff, and because the port is identical either side of
     * Bootstrapping -> Active the transport never "changes", so `RelayProxyClientConnector` never
     * calls `resetBackoff()` to forgive them. Time-to-first-socket was unchanged by dialling early
     * (n=3), so the backoff is pure loss.
     */
    fun isTorReady() = status.value.isFullyBootstrapped

    fun socksPort(): Int = status.value.socksPort ?: 17392

    companion object {
        const val BOOTSTRAP_TIMEOUT_MS: Long = 60_000L
        const val APPROVAL_REMEMBER_MS: Long = 60L * 60L * 1000L

        /** Self-heal kicks in BEFORE the 60s [BOOTSTRAP_TIMEOUT_MS] dialog so most users never see it. */
        const val SELF_HEAL_AFTER_MS: Long = 45_000L
        const val SELF_HEAL_COOLDOWN_MS: Long = 5L * 60L * 1000L

        /** Cooldown before Tor has ever bootstrapped on this install. See [selfHealCooldownMs]. */
        const val FIRST_BOOTSTRAP_RETRY_COOLDOWN_MS: Long = 30_000L

        /**
         * How long a directory download may make **no forward progress at all** before it counts as
         * stalled. Time spent downloading does not count against it — only time spent not moving.
         */
        const val BOOTSTRAP_STALL_MS: Long = 60_000L

        /**
         * Gentle self-heals to try before wiping on-disk state on an install that has never
         * bootstrapped. Corrupt `cache/`/`state/` is invisible to [ArtiGuardState.hasNoUsableGuards]
         * (it only reads `guards.json`), so without this a fresh install with a bad cache would
         * drop-and-retry the client forever and never clear the thing actually blocking it.
         */
        const val GENTLE_RESETS_BEFORE_WIPE: Int = 3
    }
}
