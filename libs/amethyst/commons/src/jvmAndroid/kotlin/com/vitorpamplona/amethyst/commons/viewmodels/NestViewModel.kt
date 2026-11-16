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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Channel.Companion.DefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.nestsclient.BroadcastHandle
import com.vitorpamplona.nestsclient.NestsClient
import com.vitorpamplona.nestsclient.NestsListener
import com.vitorpamplona.nestsclient.NestsListenerState
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.NestsSpeaker
import com.vitorpamplona.nestsclient.NestsSpeakerState
import com.vitorpamplona.nestsclient.audio.AudioCapture
import com.vitorpamplona.nestsclient.audio.AudioException
import com.vitorpamplona.nestsclient.audio.AudioFormat
import com.vitorpamplona.nestsclient.audio.AudioPlayer
import com.vitorpamplona.nestsclient.audio.NestPlayer
import com.vitorpamplona.nestsclient.audio.OpusDecoder
import com.vitorpamplona.nestsclient.audio.OpusEncoder
import com.vitorpamplona.nestsclient.connectReconnectingNestsListener
import com.vitorpamplona.nestsclient.connectReconnectingNestsSpeaker
import com.vitorpamplona.nestsclient.transport.WebTransportFactory
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Per-screen state holder for a NIP-53 audio-room.
 *
 * Owns one [NestsListener] for the lifetime of the screen and one
 * [NestPlayer] per active speaker subscription. The screen tells the
 * VM which speakers it cares about via [updateSpeakers]; subscribe / play
 * happen automatically once the listener is [NestsListenerState.Connected].
 *
 * Lifecycle:
 *   - Construction: idle. Nothing is on the wire until [connect] is called.
 *   - [connect]: launches the HTTP→WebTransport→MoQ handshake. Idempotent
 *     while connecting/connected.
 *   - [setMuted]: routes through to every player so the mute toggle is
 *     instant; the network keeps running so unmute has no extra latency.
 *   - [disconnect] / [onCleared]: cancels subscriptions, stops players,
 *     closes the listener. Idempotent.
 *
 * Audio-pipeline construction is injected via [decoderFactory] /
 * [playerFactory] so commonMain doesn't have to know which platform's
 * MediaCodec / AudioTrack is in play. M1 wires Android-only — desktop
 * passes nothing here yet. Both factories take a `channelCount` (1 for
 * mono, 2 for stereo) AND a `sampleRate` (Hz) discovered from the
 * publisher's `catalog.json` audio rendition; subscriptions await a
 * brief catalog-arrival window before constructing the decoder + player
 * so a stereo or non-48 kHz web publisher doesn't get its frames
 * decoded with the wrong channel layout / clock.
 *
 * **Threading contract:** all public methods (`connect`, `disconnect`,
 * `updateSpeakers`, `setMuted`, `setMicMuted`, `startBroadcast`,
 * `stopBroadcast`) MUST be called on the main thread. Internal mutation
 * of `activeSubscriptions` / `speakingExpiryJobs` / `requestedSpeakers`
 * is only thread-safe under that assumption — they're plain HashMaps
 * confined to `viewModelScope`'s dispatcher (Dispatchers.Main.immediate
 * on Android, which is the same dispatcher the MoQ flow's `onEach`
 * callback runs on because the player launch lives in viewModelScope).
 * If a future caller needs to invoke from a background thread, marshal
 * via `viewModelScope.launch(Dispatchers.Main.immediate) { ... }` first.
 */
@Stable
class NestViewModel(
    private val httpClient: NestsClient,
    private val transport: WebTransportFactory,
    private val decoderFactory: (channelCount: Int, sampleRate: Int) -> OpusDecoder,
    private val playerFactory: (channelCount: Int, sampleRate: Int) -> AudioPlayer,
    private val signer: NostrSigner,
    private val room: NestsRoomConfig,
    // Speaker-side audio capture/encode actuals. Optional — desktop and
    // listener-only callers pass null and the speaker UI hides the talk
    // button. Android passes `{ AudioRecordCapture() }` /
    // `{ MediaCodecOpusEncoder() }`.
    private val captureFactory: (() -> AudioCapture)? = null,
    private val encoderFactory: (() -> OpusEncoder)? = null,
    // Seam for tests — production code uses the default which delegates to
    // the real `connectNestsListener`. Tests inject a fake that returns a
    // listener whose state they can drive directly.
    private val connector: NestsListenerConnector = DefaultNestsListenerConnector,
    private val speakerConnector: NestsSpeakerConnector = DefaultNestsSpeakerConnector,
    // Scope used for fire-and-forget MoQ cleanup (UNANNOUNCE,
    // SUBSCRIBE_DONE, MoQ session close) that needs to outlive the VM's
    // own scope. Production passes the application scope so onCleared can
    // finish sending control frames before the QUIC transport drops; tests
    // pass their `backgroundScope` so assertions can observe the close.
    private val cleanupScope: CoroutineScope,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NestUiState())
    val uiState: StateFlow<NestUiState> = _uiState.asStateFlow()

    /**
     * Listener-side aggregation of every peer's most recent kind-10312
     * presence in this room. Populated by the platform layer
     * (amethyst observes [LocalCache] for `kinds=[10312], #a=[roomATag]`
     * and pipes events through [onPresenceEvent]) — keeping the data
     * source platform-specific lets commons stay free of
     * Android-only references.
     *
     * The participant grid (Tier 2 #1), hand-raise queue (T1 #5), and
     * listener counter (T1 #8) all subscribe to this StateFlow.
     */
    private val presenceAgg = RoomPresenceAggregator()
    private val _presences = MutableStateFlow<Map<String, RoomPresence>>(emptyMap())
    val presences: StateFlow<Map<String, RoomPresence>> = _presences.asStateFlow()

    /**
     * Chat ledger for the live-activities chat panel (#1) — every
     * kind-1311 ([LiveActivitiesChatMessageEvent]) tagged with this
     * room's `a`-pointer, ordered by [DefaultFeedOrder] (descending
     * `created_at`, then by id for stability). The chat panel renders
     * with `LazyColumn(reverseLayout = true)`, which puts data index 0
     * (the newest message) at the bottom of the viewport.
     *
     * Dedupes by event id — a relay re-emit on reconnect can't
     * produce a duplicate row.
     */
    private val chatById = LinkedHashMap<String, Note>()
    private val _chat =
        MutableStateFlow<List<Note>>(emptyList())
    val chat: StateFlow<List<Note>> = _chat.asStateFlow()

    /**
     * Recent kind-7 reactions for the floating speaker-avatar overlay
     * (#3). Keyed by target pubkey; room-wide reactions land under the
     * empty-string key. Sliding [REACTION_WINDOW_SEC] window driven
     * by the platform layer's tick (typically every 1 s).
     */
    private val reactionsAgg = RoomReactionsAggregator()
    private val _recentReactions = MutableStateFlow<Map<String, List<RoomReaction>>>(emptyMap())
    val recentReactions: StateFlow<Map<String, List<RoomReaction>>> = _recentReactions.asStateFlow()

    /**
     * Recent kind-9735 zap receipts for the floating zap-avatar overlay.
     * Same map shape as [recentReactions] (keyed by target pubkey,
     * room-wide zaps under the empty-string key) so the zap overlay
     * can mirror the reaction overlay's grouping logic. Sliding
     * [REACTION_WINDOW_SEC] window driven by the platform layer's
     * eviction tick.
     */
    private val zapsAgg = RoomZapsAggregator()
    private val _recentZaps = MutableStateFlow<Map<String, List<RoomZap>>>(emptyMap())
    val recentZaps: StateFlow<Map<String, List<RoomZap>>> = _recentZaps.asStateFlow()

    /**
     * moq-lite `catalog.json` payload per speaker pubkey. Populated
     * lazily as the per-speaker subscriptions land — listeners
     * without catalog support (IETF reference path) leave this map
     * empty. The participant context sheet reads this to surface
     * codec / sample-rate info; future commits could use it for
     * "speaker is broadcasting" indicators independent of the
     * actively-emitting `speakingNow` set.
     */
    private val _speakerCatalogs = MutableStateFlow<Map<String, RoomSpeakerCatalog>>(emptyMap())
    val speakerCatalogs: StateFlow<Map<String, RoomSpeakerCatalog>> = _speakerCatalogs.asStateFlow()

    /**
     * Pubkeys the moq-lite relay has announced as actively
     * broadcasting in this room. Populated by [observeAnnounces]
     * once the listener is Connected; an Active announce adds the
     * pubkey, an Ended announce removes it. Independent of the
     * kind-10312 `publishing` flag — announces come straight from
     * the relay's view of who has an open broadcast track, while
     * presence is the speaker's self-reported state. The two
     * usually agree but the announce flow is the more authoritative
     * "is this broadcast really live" signal.
     *
     * Empty when the listener doesn't expose announces (IETF
     * reference path); the UI falls back to `publishingNow` from
     * presence in that case.
     */
    private val _announcedSpeakers = MutableStateFlow<Set<String>>(emptySet())
    val announcedSpeakers: StateFlow<Set<String>> = _announcedSpeakers.asStateFlow()

    /**
     * Per-speaker peak audio amplitude for the most recent decoded
     * frame, normalized to `[0, 1]`. Drives the live "voice ring"
     * around speaker avatars: while a pubkey is in [NestUiState.speakingNow],
     * the UI reads this map to throb the green border in time with
     * the voice.
     *
     * Updated by [NestPlayer]'s `onLevel` callback at ~50 Hz per speaker
     * (one frame per 20 ms Opus packet); the VM coalesces those raw
     * updates into a single StateFlow emission every [LEVEL_TICK_MS]
     * via [levelEmitterJob]. Empty when no speaker is being decoded;
     * an entry drops when its subscription closes.
     */
    private val rawAudioLevels = mutableMapOf<String, Float>()
    private val _audioLevels = MutableStateFlow<Map<String, Float>>(emptyMap())
    val audioLevels: StateFlow<Map<String, Float>> = _audioLevels.asStateFlow()
    private var levelEmitterJob: Job? = null

    /**
     * `true` once the local user has been kicked (#5) — the platform
     * layer flips this on a valid kind-4312 from a host/moderator and
     * the UI can show a toast + finish the activity. Set-once; never
     * unset for the lifetime of the VM (a kick survives reconnect
     * attempts; the user must rejoin the room manually).
     */
    private val _wasKicked = MutableStateFlow(false)
    val wasKicked: StateFlow<Boolean> = _wasKicked.asStateFlow()

    /**
     * Mark the local user as kicked and disconnect the listener +
     * speaker pumps. Idempotent. Caller (amethyst layer) is
     * responsible for verifying the inbound kind-4312 was signed by
     * a host or moderator on the active kind-30312 before invoking
     * this — the relay does not enforce that.
     */
    fun onKick() {
        if (closed) return
        if (_wasKicked.value) return
        _wasKicked.value = true
        disconnect()
    }

    /**
     * Honor an inbound kind-4312 force-mute targeted at the local
     * user. No-ops when we aren't broadcasting; otherwise routes
     * through the existing [setMicMuted] path so the wire mute and
     * UI state both flip in sync. Caller (the AdminCommandsCollector)
     * is responsible for verifying the signer is host/moderator on
     * the active kind-30312 — the relay does not enforce that.
     */
    fun onForceMuted() {
        if (closed) return
        if (broadcastHandle == null) return
        if ((_uiState.value.broadcast as? BroadcastUiState.Broadcasting)?.isMuted == true) return
        setMicMuted(true)
    }

    private var listener: NestsListener? = null
    private var connectJob: Job? = null
    private var stateObserverJob: Job? = null
    private var announcesJob: Job? = null

    // Last in-flight `listener.close()` launched by teardown(). A subsequent
    // connect() awaits this before opening a fresh transport so two QUIC
    // sessions (the closing old one and the opening new one) don't briefly
    // coexist. Some servers dedupe by client pubkey and reject the new one.
    private var pendingCloseJob: Job? = null

    private val activeSubscriptions = mutableMapOf<String, ActiveSubscription>()
    private val speakingExpiryJobs = mutableMapOf<String, Job>()

    /**
     * Monotonic [kotlin.time.TimeMark] of the last MoQ object delivered
     * to the consumer flow per speaker pubkey. Updated in
     * [onSpeakerActivity] alongside the speaking-now flag. Read by
     * [cliffDetectorJob] to spot the relay-side forward-queue cliff
     * documented in
     * `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`:
     * the relay stops opening new uni streams to the listener while
     * the announce stream still says the broadcast is Active and the
     * subscribe bidi is still open from QUIC's POV — meaning no other
     * layer notices.
     */
    private val lastFrameAt = mutableMapOf<String, kotlin.time.TimeMark>()

    /**
     * Periodic scan of [activeSubscriptions] vs [_announcedSpeakers]
     * vs [lastFrameAt]. When a speaker has been announced Active
     * AND we have an active subscription AND no frames have arrived
     * for [ROOM_AUDIO_CLIFF_TIMEOUT_MS], force a `recycleSession()`
     * so the orchestrator opens a fresh QUIC transport — a clean
     * recovery from the relay-side per-subscriber forward-queue
     * starvation that has no QUIC- or moq-lite-level signal.
     */
    private var cliffDetectorJob: Job? = null

    /**
     * [kotlin.time.TimeMark] of the most recent recycle the cliff
     * detector triggered, or `null` if it has never fired. Combined
     * with [consecutiveCliffRecycles] to drive the per-attempt
     * backoff schedule in [computeStalledSpeakers] — short backoff
     * after the first failed recycle so a single moq-rs cliff
     * recovers within a few seconds, escalating to the
     * [ROOM_AUDIO_CLIFF_BACKOFF_MAX_MS] cap if the relay stays
     * stalled across multiple recycles.
     */
    private var lastCliffRecycleAt: kotlin.time.TimeMark? = null

    /**
     * Number of consecutive `recycleSession()` calls the detector has
     * issued without seeing a real frame in between. Reset to 0 in
     * [onSpeakerActivity] when any speaker delivers a frame, so a
     * recovered-then-restall pattern re-enters with attempt = 0
     * (immediate recycle eligible) rather than inheriting backoff
     * from a prior failed-recycle streak.
     */
    private var consecutiveCliffRecycles: Int = 0

    /**
     * Per-speaker catalog-fetch coroutines. Each entry is the
     * background `subscribeCatalog` collector launched in
     * [fetchSpeakerCatalog]; cancelled in [closeSubscription] so
     * removing a speaker doesn't leave a zombie subscription that
     * keeps re-adding the catalog entry on every wrapper-side
     * session swap.
     */
    private val catalogJobs = mutableMapOf<String, Job>()
    private var requestedSpeakers: Set<String> = emptySet()

    /**
     * `@Volatile` because [closed] is read by background coroutines
     * (cliff-detector, observe* loops, audio-focus + network observers)
     * but written from `leave()` / `onCleared()` which can fire from
     * the Activity destroy callback on a different stack frame than
     * the coroutine that's about to suspend in [delay] / [collect].
     * Without the marker, a coroutine that resumes from cancellation
     * could read a stale `closed = false` even though the user has
     * already left the room — visible in the trace as
     * `cliff-detector EXITED ... closed=false` after a Leave press.
     */
    @Volatile private var closed = false

    // Speaker / publisher path
    private var speaker: NestsSpeaker? = null
    private var broadcastHandle: BroadcastHandle? = null
    private var speakerStateJob: Job? = null
    private var speakerConnectJob: Job? = null

    /**
     * Mirror of [NestAudioFocusBus.state] flipped to a boolean: `true`
     * while another app holds audio focus (inbound phone call, Maps
     * voice prompt, system alarm). Composed with the user-visible
     * mute states (`NestUiState.isMuted` for listeners,
     * `BroadcastUiState.Broadcasting.isMuted` for the speaker mic) via
     * [effectiveListenMuted] / [effectiveMicMuted] so the user-chosen
     * state is preserved across a focus loss + regain cycle.
     *
     * Maintained by [audioFocusObserverJob], started in [launchConnect]
     * (so a never-connected VM doesn't subscribe needlessly).
     */
    @Volatile private var focusMuted: Boolean = false
    private var audioFocusObserverJob: Job? = null

    /**
     * Subscribed in [ensureNetworkChangeObserverStarted]; calls
     * [com.vitorpamplona.nestsclient.NestsListener.recycleSession]
     * (and the speaker counterpart) when the platform reports a
     * default-network change. Without this, a Wi-Fi → cellular
     * handover leaves the QUIC connection sitting on the now-dead
     * socket until its PTO fires (~30 s) — the wrapper's reconnect
     * loop only learns about the failure at PTO. With the bus
     * nudge, recycle happens immediately and the audible gap
     * shrinks to a single re-handshake.
     */
    private var networkChangeObserverJob: Job? = null

    /**
     * Push the latest known speaker set from the room event. The user's
     * own pubkey (when broadcasting) is filtered out so we don't subscribe
     * to our own forwarded audio — that would echo through the local
     * playback device whenever our broadcast track loops back from the relay.
     */
    fun updateSpeakers(speakerPubkeys: Set<String>) {
        if (closed) return
        val selfHex = signer.pubKey
        requestedSpeakers = if (selfHex.isEmpty()) speakerPubkeys else speakerPubkeys - selfHex
        if (_uiState.value.connection is ConnectionUiState.Connected) {
            reconcileSubscriptions()
        }
    }

    /**
     * Kick off the HTTP → WebTransport → MoQ handshake. No-op if a connect
     * attempt is already in flight or already connected.
     */
    fun connect() {
        if (closed) return
        val current = _uiState.value.connection
        if (current is ConnectionUiState.Connecting || current is ConnectionUiState.Connected) return

        // If a stale listener is still set (e.g. arrived in Failed and the
        // user is manually retrying before the auto-retry fires, or
        // entering from a server-Closed-but-not-yet-disconnected state),
        // tear it down so the new connect doesn't leave the old MoQ
        // session open and unowned (audit round-2 VM #3). teardown()
        // also cancels stateObserverJob so its delayed emissions can't
        // clobber the fresh Connecting UI.
        if (listener != null || stateObserverJob != null) {
            teardown(targetState = ConnectionUiState.Idle, finalCleanup = false)
        }

        ensureAudioFocusObserverStarted()
        ensureNetworkChangeObserverStarted()
        launchConnect()
    }

    /**
     * Subscribe to [NestAudioFocusBus] for the lifetime of this VM (or
     * until [teardown] cancels). On loss, mute every active listener
     * + the broadcast mic without touching the user-visible mute
     * states; on regain, restore the user's choice. Idempotent —
     * a second call while the job is alive is a no-op.
     */
    private fun ensureAudioFocusObserverStarted() {
        if (audioFocusObserverJob?.isActive == true) return
        audioFocusObserverJob =
            viewModelScope.launch {
                NestAudioFocusBus.state.collect { focusState ->
                    if (closed) return@collect
                    val newFocusMuted = focusState != NestAudioFocusState.Granted
                    if (newFocusMuted == focusMuted) return@collect
                    focusMuted = newFocusMuted
                    // Re-apply both directions. effectiveListenMuted()
                    // and the per-broadcast effective rebuild below
                    // both consume the just-updated [focusMuted] field.
                    applyEffectiveListenMute()
                    applyEffectiveMicMute()
                }
            }
    }

    /**
     * Subscribe to [NestNetworkChangeBus] for the lifetime of this VM
     * (or until [teardown]). On a default-network change, ask both
     * the listener and the speaker wrappers to recycle their
     * underlying sessions — much faster than waiting for the QUIC
     * PTO on a dead socket. Idempotent.
     */
    private fun ensureNetworkChangeObserverStarted() {
        if (networkChangeObserverJob?.isActive == true) return
        networkChangeObserverJob =
            viewModelScope.launch {
                NestNetworkChangeBus.events.collect {
                    if (closed) return@collect
                    // Best-effort. The wrapper-level recycleSession is a
                    // no-op on raw / non-reconnecting implementations
                    // (test fakes, IETF reference) so a network change
                    // there is silently ignored — the production paths
                    // wrap with [connectReconnectingNestsListener] /
                    // [connectReconnectingNestsSpeaker] which both
                    // override the hook to close the inner session.
                    listener?.let { runCatchingCancellable { it.recycleSession() } }
                    speaker?.let { runCatchingCancellable { it.recycleSession() } }
                }
            }
    }

    /**
     * Push the effective mic mute (`user choice OR focus-loss`) onto
     * the live broadcast handle, if any. The user-visible
     * [BroadcastUiState.Broadcasting.isMuted] stays the user's choice
     * — UI only reflects that, never the focus-driven temporary
     * silencing — so a focus regain is invisible to the user.
     */
    private fun applyEffectiveMicMute() {
        val handle = broadcastHandle ?: return
        val ui = _uiState.value.broadcast
        val userMuted = (ui as? BroadcastUiState.Broadcasting)?.isMuted ?: false
        val effective = userMuted || focusMuted
        viewModelScope.launch {
            runCatchingCancellable { handle.setMuted(effective) }
        }
    }

    fun setMuted(muted: Boolean) {
        if (closed) return
        _uiState.update { it.copy(isMuted = muted) }
        applyEffectiveListenMute()
    }

    /**
     * Effective listener mute = user-chosen mute (from the talk-bar)
     * OR audio-focus loss (phone call / nav prompt). Applied to every
     * active player. The user-visible state (`NestUiState.isMuted`)
     * stays the user's choice, so a focus regain restores it
     * automatically.
     */
    private fun effectiveListenMuted(): Boolean = _uiState.value.isMuted || focusMuted

    private fun applyEffectiveListenMute() {
        val effective = effectiveListenMuted()
        activeSubscriptions.values.forEach { it.player?.setMutedSafe(effective) }
    }

    /**
     * Locally silence (hush) one speaker without affecting the rest of
     * the room. Independent of [setMuted] (room-wide). The hush is
     * applied via [AudioPlayer.setVolume] so the network + decode
     * pipeline keeps running — re-enabling is sample-accurate.
     *
     * The set survives subscription churn: re-subscribing to a hushed
     * speaker re-applies the gain on the new player at attach time.
     */
    fun setLocalHushed(
        pubkey: String,
        hushed: Boolean,
    ) {
        if (closed) return
        val updated =
            _uiState.value.locallyHushed.let {
                if (hushed) it + pubkey else it - pubkey
            }
        if (updated == _uiState.value.locallyHushed) return
        _uiState.update { it.copy(locallyHushed = updated.toPersistentSet()) }
        activeSubscriptions[pubkey]?.player?.runCatching {
            setVolume(if (hushed) 0f else 1f)
        }
    }

    /**
     * Toggle whether we hold a speaker slot. Tier-2's "leave the stage"
     * tap flips this to `false`; the next kind-10312 heartbeat picks up
     * the change via [NestUiState.onStageNow]. Does NOT stop a
     * running broadcast — UI / caller is expected to call
     * [BroadcastHandle.close] separately when leaving the stage.
     */
    fun setOnStage(onStage: Boolean) {
        if (closed) return
        _uiState.update { it.copy(onStageNow = onStage) }
    }

    /**
     * Apply one kind-10312 presence event to the in-memory aggregator.
     * Caller owns the LocalCache → VM plumbing (the platform layer
     * filters events by the current room's `a`-tag before invoking
     * this). Out-of-order arrivals can't downgrade fresher state —
     * see [RoomPresenceAggregator.apply].
     */
    fun onPresenceEvent(event: com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent) {
        if (closed) return
        // Skip the StateFlow write when the aggregator says nothing
        // changed — saves a snapshot copy + an O(N) map equality check
        // per replay in big rooms (LocalCache.observeEvents re-emits
        // the full list on every cache mutation).
        presenceAgg.applyOrNull(event)?.let { _presences.value = it }
    }

    /**
     * Drop peers whose last heartbeat is older than [olderThanSec].
     * The platform layer drives this on a periodic tick (typically
     * every 60 s with `now - 6 * 60` so a peer that's missed one
     * heartbeat plus a 5-min "still here" window gets evicted).
     */
    fun evictStalePresences(olderThanSec: Long) {
        if (closed) return
        _presences.value = presenceAgg.evictOlderThan(olderThanSec)
    }

    /**
     * Apply one kind-1311 chat event to the room ledger. The platform
     * layer is the source — it filters by `a`-tag matching the current
     * room before invoking this. Same-id re-emits are deduped; the
     * resulting list is sorted by `created_at` ascending.
     */
    fun onChatEvent(chatNote: Note) {
        if (closed) return
        if (chatById.containsKey(chatNote.idHex)) return
        chatById[chatNote.idHex] = chatNote
        _chat.value = chatById.values.sortedWith(DefaultFeedOrder)
    }

    /**
     * Apply one kind-7 reaction event to the sliding-window aggregator.
     * Caller passes [nowSec] (so tests can be deterministic) and the
     * fixed [REACTION_WINDOW_SEC] window. Mirror of [evictReactions]
     * for the per-tick cleanup.
     */
    fun onReactionEvent(
        event: com.vitorpamplona.quartz.nip25Reactions.ReactionEvent,
        nowSec: Long,
        windowSec: Long = REACTION_WINDOW_SEC,
    ) {
        if (closed) return
        _recentReactions.value = reactionsAgg.apply(event, nowSec, windowSec)
    }

    /**
     * Drop reactions older than the staleness threshold. Platform
     * layer drives this on a 1-s tick to update the floating overlay
     * without per-component animation timers.
     */
    fun evictReactions(olderThanSec: Long) {
        if (closed) return
        _recentReactions.value = reactionsAgg.evictAndSnapshot(olderThanSec)
    }

    /**
     * Apply one kind-9735 zap receipt to the sliding-window aggregator.
     * Mirror of [onReactionEvent] — the floating zap chip uses the
     * same window so the visual cadence matches the React button.
     */
    fun onZapEvent(
        event: ZapReceiptEvent,
        nowSec: Long,
        windowSec: Long = REACTION_WINDOW_SEC,
    ) {
        if (closed) return
        _recentZaps.value = zapsAgg.apply(event, nowSec, windowSec)
    }

    /** Apply one kind-9736 BOLT12 zap to the same sliding-window aggregator. */
    fun onZapEvent(
        event: Bolt12ZapEvent,
        nowSec: Long,
        windowSec: Long = REACTION_WINDOW_SEC,
    ) {
        if (closed) return
        _recentZaps.value = zapsAgg.apply(event, nowSec, windowSec)
    }

    /**
     * Drop zaps older than the staleness threshold. Platform layer
     * drives this on a 1-s tick — the same cadence as [evictReactions]
     * — so the zap and reaction overlays age out in lockstep.
     */
    fun evictZaps(olderThanSec: Long) {
        if (closed) return
        _recentZaps.value = zapsAgg.evictAndSnapshot(olderThanSec)
    }

    /**
     * Whether this VM was constructed with capture + encoder factories. The
     * UI uses this to decide whether to render the talk button at all —
     * desktop and listener-only screens leave it false.
     */
    val canBroadcast: Boolean = captureFactory != null && encoderFactory != null

    /**
     * Begin broadcasting our own pubkey's audio. Caller must have already
     * obtained `RECORD_AUDIO`. Idempotent: returns immediately if a
     * broadcast is already in flight.
     *
     * If the listener path isn't [ConnectionUiState.Connected] yet, the
     * call is a no-op (the UI shouldn't expose the talk button before
     * Connected anyway).
     *
     * Note that the listener and speaker paths each own their own MoQ
     * session over a separate WebTransport — nests' protocol uses one
     * session per direction.
     */
    fun startBroadcast(
        speakerPubkeyHex: String,
        initialMuted: Boolean = false,
    ) {
        if (closed || !canBroadcast) return
        if (_uiState.value.connection !is ConnectionUiState.Connected) return
        if (_uiState.value.broadcast is BroadcastUiState.Broadcasting ||
            _uiState.value.broadcast is BroadcastUiState.Connecting
        ) {
            return
        }
        _uiState.update {
            it.copy(broadcast = BroadcastUiState.Connecting)
        }
        speakerConnectJob =
            viewModelScope.launch {
                try {
                    val s =
                        speakerConnector.connect(
                            httpClient = httpClient,
                            transport = transport,
                            scope = viewModelScope,
                            room = room,
                            signer = signer,
                            speakerPubkeyHex = speakerPubkeyHex,
                            captureFactory = captureFactory!!,
                            encoderFactory = encoderFactory!!,
                        )
                    if (closed) {
                        runCatching { s.close() }
                        return@launch
                    }
                    speaker = s
                    observeSpeakerState(s)
                    // The speaker-side `onLevel` callback is the ground
                    // truth for the local-speaking ring: it fires only
                    // after a frame actually reaches the wire (see
                    // NestMoqLiteBroadcaster.start). Reusing the same
                    // `onSpeakerActivity` / `onAudioLevel` plumbing the
                    // remote speakers use means the local avatar gets
                    // the same ring + glow + 250 ms timeout fade as
                    // any other speaker — no separate UI branch needed.
                    val handle =
                        s.startBroadcasting(
                            onLevel = { level ->
                                onSpeakerActivity(speakerPubkeyHex)
                                onAudioLevel(speakerPubkeyHex, level)
                            },
                        )
                    broadcastHandle = handle
                    // Auto-start path (room creation): the host wants
                    // listeners to be able to subscribe BEFORE they hit
                    // unmute, so we open the publisher session pre-muted.
                    // The mic stays open + the broadcaster keeps
                    // capturing + encoding, but `setMuted(true)` makes
                    // every send drop on the floor inside
                    // `NestMoqLiteBroadcaster.start`'s `if (muted) continue`
                    // gate. Listener-side announces fire as soon as the
                    // publisher registers with the relay, so the audience
                    // can subscribe immediately and switch from
                    // "Connecting…" to "Live (silent)" while they wait
                    // for the host's first word. Composes with `focusMuted`
                    // exactly the same way [setMicMuted] does so an
                    // inbound phone call during the auto-start window
                    // doesn't get a silent unmute.
                    if (initialMuted) {
                        val effective = initialMuted || focusMuted
                        runCatchingCancellable { handle.setMuted(effective) }
                    }
                    _uiState.update { it.copy(broadcast = BroadcastUiState.Broadcasting(isMuted = initialMuted)) }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    _uiState.update {
                        it.copy(
                            broadcast =
                                BroadcastUiState.Failed(
                                    t.message ?: t::class.simpleName ?: "broadcast failed",
                                ),
                        )
                    }
                }
            }
    }

    /**
     * Toggle the speaker-side mic mute. Cheap; the network keeps running.
     *
     * UI flips AFTER the suspending `broadcastHandle.setMuted(...)` returns
     * so the indicator never claims muted while audio is still flowing
     * (audit VM #7). We accept a small UI latency in exchange for an
     * accurate state machine.
     */
    fun setMicMuted(muted: Boolean) {
        if (closed) return
        val handle = broadcastHandle ?: return
        viewModelScope.launch {
            // Apply (user-mute OR audio-focus-loss) to the wire so the
            // mic stays silent through a phone call without us losing
            // the user's intent. UI then reflects the user choice (not
            // the focus-effective state) so a focus-driven mute doesn't
            // visually flip the talk button.
            val effective = muted || focusMuted
            val result = runCatchingCancellable { handle.setMuted(effective) }
            if (closed) return@launch
            _uiState.update {
                val current = it.broadcast
                if (current !is BroadcastUiState.Broadcasting) return@update it
                if (result.isSuccess) {
                    it.copy(broadcast = current.copy(isMuted = muted, muteError = null))
                } else {
                    // Surface the failure so the UI can show a toast / inline
                    // message instead of silently swallowing (audit round-2
                    // VM #8b). We keep the broadcast running with the prior
                    // mute state — only the mute toggle failed.
                    val why = result.exceptionOrNull()?.message ?: "mute failed"
                    it.copy(broadcast = current.copy(muteError = why))
                }
            }
        }
    }

    /** Stop broadcasting. UI keeps the listener path connected. Idempotent. */
    fun stopBroadcast() {
        if (closed) return
        teardownBroadcast(BroadcastUiState.Idle)
    }

    /** Tear down without finalizing the VM (e.g. user pressed Disconnect). */
    fun disconnect() {
        if (closed) return
        // Forget the requested speaker set so a fresh connect() to a
        // different room (or same room after a long pause) doesn't reuse
        // a stale snapshot that may no longer be on stage.
        requestedSpeakers = emptySet()
        teardownBroadcast(BroadcastUiState.Idle, finalCleanup = false)
        teardown(targetState = ConnectionUiState.Idle, finalCleanup = false)
    }

    /**
     * User-driven leave-the-activity teardown. Mirrors [onCleared]: marks
     * the VM closed and routes both the speaker handle close (which
     * releases the AudioRecord and clears the system mic indicator) and
     * the listener close to [cleanupScope] so the teardown survives
     * Activity destruction. Call this from the leave-the-room button
     * BEFORE finishing the activity; relying on [onCleared] alone leaves
     * a window where the AudioRecord stays held while the activity is
     * paused / queued for destruction.
     */
    fun leave() {
        if (closed) return
        closed = true
        requestedSpeakers = emptySet()
        teardownBroadcast(BroadcastUiState.Idle, finalCleanup = true)
        teardown(targetState = ConnectionUiState.Closed, finalCleanup = true)
    }

    override fun onCleared() {
        closed = true
        teardownBroadcast(BroadcastUiState.Idle, finalCleanup = true)
        teardown(targetState = ConnectionUiState.Closed, finalCleanup = true)
    }

    private fun observeSpeakerState(s: NestsSpeaker) {
        speakerStateJob?.cancel()
        speakerStateJob =
            viewModelScope.launch {
                s.state.collect { state ->
                    when (state) {
                        is NestsSpeakerState.Failed -> {
                            _uiState.update {
                                it.copy(broadcast = BroadcastUiState.Failed(state.reason))
                            }
                        }

                        NestsSpeakerState.Closed -> {
                            _uiState.update { it.copy(broadcast = BroadcastUiState.Idle) }
                        }

                        else -> { /* startBroadcast already set Broadcasting; no extra action */ }
                    }
                }
            }
    }

    private fun teardownBroadcast(
        targetState: BroadcastUiState,
        finalCleanup: Boolean = false,
    ) {
        speakerConnectJob?.cancel()
        speakerConnectJob = null
        speakerStateJob?.cancel()
        speakerStateJob = null
        val handle = broadcastHandle
        val s = speaker
        broadcastHandle = null
        speaker = null
        if (handle != null || s != null) {
            // User-driven disconnect can use viewModelScope (still alive);
            // onCleared must use cleanupScope because viewModelScope is
            // already cancelled and a launch on it would no-op without
            // emitting the UNANNOUNCE / SUBSCRIBE_DONE wire frames.
            val scope = if (finalCleanup) cleanupScope else viewModelScope
            scope.launch {
                handle?.runCatching { close() }
                s?.runCatching { close() }
            }
        }
        _uiState.update { it.copy(broadcast = targetState) }
    }

    /**
     * Drain the moq-lite ANNOUNCE flow into [announcedSpeakers].
     * Each Active emission adds the speaker pubkey; an inactive
     * emission removes it. Best-effort — listeners that don't
     * support announces (IETF reference path) throw on the first
     * `collect` and we silently leave the set empty (the UI falls
     * back to presence's `publishingNow` flag).
     */
    private fun observeAnnounces(l: NestsListener) {
        announcesJob?.cancel()
        announcesJob =
            viewModelScope.launch {
                com.vitorpamplona.quartz.utils.Log
                    .d("NestRx") { "observeAnnounces launching collect on l.announces()" }
                var emissionCount = 0
                val outcome =
                    runCatchingCancellable {
                        l.announces().collect { ann ->
                            if (closed) return@collect
                            emissionCount += 1
                            com.vitorpamplona.quartz.utils.Log.d("NestRx") {
                                "observeAnnounces #$emissionCount active=${ann.active} pubkey='${ann.pubkey.take(12)}' → ${if (ann.active) "ADD" else "REMOVE"}"
                            }
                            com.vitorpamplona.nestsclient.trace.NestsTrace.emit("vm_observe_announce") {
                                "\"emission\":$emissionCount,\"active\":${ann.active}," +
                                    "\"pubkey\":${com.vitorpamplona.nestsclient.trace.jsonStr(ann.pubkey)}"
                            }
                            if (ann.active) {
                                _announcedSpeakers.update { it + ann.pubkey }
                            } else {
                                _announcedSpeakers.update { it - ann.pubkey }
                            }
                        }
                    }
                com.vitorpamplona.quartz.utils.Log.w("NestRx") {
                    val why = outcome.exceptionOrNull()?.let { "${it::class.simpleName}: ${it.message}" } ?: "naturally"
                    "observeAnnounces collect ENDED $why (emissions=$emissionCount, _announcedSpeakers.size=${_announcedSpeakers.value.size})"
                }
            }
    }

    private fun observeListenerState(l: NestsListener) {
        stateObserverJob?.cancel()
        stateObserverJob =
            viewModelScope.launch {
                l.state.collect { state ->
                    _uiState.update { ui ->
                        ui.copy(connection = state.toUiState(ui.connection))
                    }
                    when (state) {
                        is NestsListenerState.Connected -> {
                            // Re-issue the requested speaker set against
                            // whatever session is now live. With the
                            // reconnecting wrapper in place, on a session
                            // swap the existing handles auto-survive via
                            // the wrapper's MutableSharedFlow pump, so
                            // this is typically a no-op in toAdd / toRemove
                            // — runs anyway for the first-Connected path.
                            reconcileSubscriptions()
                            // Defensive restart: if the wrapper went
                            // through a Closed transient (e.g. the cliff
                            // detector itself triggered `recycleSession()`,
                            // which closes the inner listener and waits
                            // for the orchestrator to reopen), our
                            // [Closed] branch below cancelled the
                            // detector via [teardown]. We need it back
                            // up on the fresh session — otherwise a
                            // second cliff event during the same room
                            // sits undetected. Idempotent: returns
                            // immediately if a job is already active.
                            startCliffDetector()
                        }

                        is NestsListenerState.Failed -> {
                            // Wrapper has already exhausted its retry
                            // policy; surface Failed to the user and
                            // wait for a manual reconnect tap.
                        }

                        NestsListenerState.Closed -> {
                            // Closed from the wrapper is *almost always*
                            // transient — the orchestrator emits Closed
                            // → Reconnecting → Connecting → Connected
                            // around every cliff-detector recycle and
                            // every JWT refresh. We must NOT teardown
                            // here: teardown cancels `cliffDetectorJob`,
                            // `announcesJob`, etc., AND calls
                            // `wrapper.close()`, which cancels the
                            // wrapper's orchestrator before it can
                            // reopen the inner listener. End result
                            // pre-fix: the very first cliff-detector
                            // recycle permanently kills the room
                            // instead of recovering it (visible in the
                            // 15:56:25 receiver log: cliff fires →
                            // teardown fires 521 ms later → wrapper
                            // never reopens → `cliff-detector EXITED
                            // closed=false`).
                            //
                            // User-initiated close goes through
                            // `disconnect()` / `onCleared()` which call
                            // `teardown` directly; the wrapper's
                            // subsequent Closed emission here is a
                            // redundant signal — no-op'ing it doesn't
                            // change anything for those paths. The UI
                            // still picks up the Closed via
                            // `state.toUiState(ui.connection)` above
                            // for visual feedback.
                        }

                        else -> { /* no extra side effect */ }
                    }
                }
            }
    }

    /**
     * Shared connect-launch body for [connect]. Awaits any in-flight
     * `listener.close()` from a previous teardown so two QUIC sessions
     * don't briefly coexist (audit round-2 VM #10), then runs the
     * connector and observes the resulting listener.
     *
     * Retry-on-failure is handled inside the connector (the production
     * default wraps each session in [connectReconnectingNestsListener]),
     * so this path no longer schedules its own retries.
     */
    private fun launchConnect() {
        _uiState.update { it.copy(connection = ConnectionUiState.Connecting(ConnectionUiState.Step.ResolvingRoom)) }

        val priorClose = pendingCloseJob
        pendingCloseJob = null

        connectJob =
            viewModelScope.launch {
                try {
                    // Drain the previous listener's close before opening a
                    // new transport. This is fast (< 100 ms typical: send
                    // UNSUBSCRIBE / UNANNOUNCE / WT_CLOSE_SESSION + drain).
                    if (priorClose?.isActive == true) {
                        runCatching { priorClose.join() }
                    }
                    val l =
                        connector.connect(
                            httpClient = httpClient,
                            transport = transport,
                            scope = viewModelScope,
                            room = room,
                            signer = signer,
                        )
                    if (closed) {
                        runCatching { l.close() }
                        return@launch
                    }
                    listener = l
                    observeListenerState(l)
                    observeAnnounces(l)
                    startCliffDetector()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    _uiState.update {
                        it.copy(connection = ConnectionUiState.Failed(t.message ?: t::class.simpleName ?: "connect failed"))
                    }
                }
            }
    }

    private fun reconcileSubscriptions() {
        val l = listener ?: return
        if (_uiState.value.connection !is ConnectionUiState.Connected) return

        val toAdd = requestedSpeakers - activeSubscriptions.keys
        val toRemove = activeSubscriptions.keys - requestedSpeakers

        toRemove.forEach { pubkey ->
            activeSubscriptions.remove(pubkey)?.let { closeSubscription(it) }
        }

        toAdd.forEach { pubkey ->
            // Mark as pending immediately so concurrent reconciles don't
            // double-subscribe; flip to active once the SUBSCRIBE_OK arrives.
            val pending = ActiveSubscription.pending(pubkey)
            activeSubscriptions[pubkey] = pending
            viewModelScope.launch {
                openSubscription(l, pubkey, pending)
            }
        }

        publishActiveSpeakers()
    }

    /**
     * Stop the per-speaker player + fire-and-forget UNSUBSCRIBE on the VM
     * scope. Both `NestPlayer.stop()` and `SubscribeHandle.unsubscribe()`
     * are suspend, so we route them through one coroutine instead of two.
     */
    private fun closeSubscription(slot: ActiveSubscription) {
        val (roomPlayer, handle) = slot.detach()
        speakingExpiryJobs.remove(slot.pubkey)?.cancel()
        // Cancel + drop the catalog collector BEFORE removing the
        // map entry. Otherwise the still-running collector could
        // re-add the entry on the next emission (the wrapper's
        // re-issuance pump keeps the underlying subscription alive
        // across session swaps).
        catalogJobs.remove(slot.pubkey)?.cancel()
        if (_uiState.value.speakingNow.contains(slot.pubkey)) {
            _uiState.update { it.copy(speakingNow = (it.speakingNow - slot.pubkey).toPersistentSet()) }
        }
        if (_uiState.value.connectingSpeakers.contains(slot.pubkey)) {
            _uiState.update { it.copy(connectingSpeakers = (it.connectingSpeakers - slot.pubkey).toPersistentSet()) }
        }
        if (_speakerCatalogs.value.containsKey(slot.pubkey)) {
            _speakerCatalogs.update { it - slot.pubkey }
        }
        if (rawAudioLevels.remove(slot.pubkey) != null) {
            _audioLevels.value = rawAudioLevels.toMap()
        }
        // Drop the cliff-detector heartbeat for this pubkey so the next
        // tick doesn't see a stale "last frame was 30s ago" entry for a
        // speaker we already stopped subscribing to and trigger a
        // gratuitous recycle.
        lastFrameAt.remove(slot.pubkey)
        if (roomPlayer != null || handle != null) {
            viewModelScope.launch {
                roomPlayer?.runCatching { stop() }
                handle?.runCatching { unsubscribe() }
            }
        }
    }

    /**
     * Wait briefly for [pubkey]'s catalog to land in [_speakerCatalogs]
     * and pick the audio config (channel count + sample rate) for the
     * decoder + AudioTrack. Falls back to [AudioFormat.DEFAULT_CHANNELS] /
     * [AudioFormat.SAMPLE_RATE_HZ] on timeout (the catalog never
     * arrived within [timeoutMs]) or when the catalog declares
     * unsupported values (channelCount outside `1..2`, or non-positive
     * sampleRate).
     *
     * Caller is responsible for having started [fetchSpeakerCatalog]
     * first; otherwise this always times out.
     *
     * Why a timeout: the catalog handshake is best-effort. If the
     * publisher doesn't publish a catalog (legacy publishers) or the
     * relay never forwards the catalog group, audio playback should
     * still proceed in the default config rather than block forever.
     * 500 ms is generous — with the speaker-side
     * `setOnNewSubscriber` hook the catalog group is on the wire
     * within one round-trip of the SUBSCRIBE_OK, so this typically
     * returns within tens of ms.
     */
    private suspend fun awaitAudioPipelineConfig(
        pubkey: String,
        timeoutMs: Long,
    ): AudioPipelineConfig {
        val catalog =
            withTimeoutOrNull(timeoutMs) {
                _speakerCatalogs
                    .map { it[pubkey] }
                    .filterNotNull()
                    .first()
            }
        val rendition = catalog?.primaryAudio()
        val declaredChannels = rendition?.numberOfChannels
        val channels =
            when {
                declaredChannels == null -> {
                    AudioFormat.DEFAULT_CHANNELS
                }

                declaredChannels !in 1..2 -> {
                    Log.w("NestRx") {
                        "publisher catalog for pubkey='${pubkey.take(8)}' declares numberOfChannels=$declaredChannels " +
                            "(only 1 / 2 supported); falling back to mono"
                    }
                    AudioFormat.DEFAULT_CHANNELS
                }

                else -> {
                    declaredChannels
                }
            }
        val declaredRate = rendition?.sampleRate
        val rate =
            when {
                declaredRate == null -> {
                    AudioFormat.SAMPLE_RATE_HZ
                }

                declaredRate <= 0 -> {
                    Log.w("NestRx") {
                        "publisher catalog for pubkey='${pubkey.take(8)}' declares sampleRate=$declaredRate " +
                            "(must be positive); falling back to ${AudioFormat.SAMPLE_RATE_HZ} Hz"
                    }
                    AudioFormat.SAMPLE_RATE_HZ
                }

                else -> {
                    declaredRate
                }
            }
        return AudioPipelineConfig(channelCount = channels, sampleRate = rate)
    }

    /**
     * Output of [awaitAudioPipelineConfig] — the validated audio
     * pipeline configuration for one subscription. Internal because the
     * factories' two-arg shape (`(channelCount, sampleRate)`) is what
     * the public surface deals in; this struct just bundles them inside
     * `openSubscription`.
     */
    private data class AudioPipelineConfig(
        val channelCount: Int,
        val sampleRate: Int,
    )

    /**
     * Open the speaker's `catalog.json` track in the background, parse
     * the first frame, and stash it in [speakerCatalogs]. Best-effort —
     * any failure (IETF listener throws UnsupportedOperationException,
     * the publisher doesn't publish a catalog, the JSON is malformed)
     * is silent. The catalog channel uses the same re-issuing pump
     * the audio path does, so it survives reconnects.
     */
    private fun fetchSpeakerCatalog(
        l: NestsListener,
        pubkey: String,
    ) {
        // Cancel any in-flight catalog collector for this pubkey
        // before launching a fresh one — guards against a re-add
        // racing with closeSubscription's cancel.
        catalogJobs.remove(pubkey)?.cancel()
        catalogJobs[pubkey] =
            viewModelScope.launch {
                val handle = runCatchingCancellable { l.subscribeCatalog(pubkey) }.getOrNull() ?: return@launch
                try {
                    handle.objects.collect { obj ->
                        if (closed) return@collect
                        val parsed = RoomSpeakerCatalog.parseOrNull(obj.payload) ?: return@collect
                        _speakerCatalogs.update { it + (pubkey to parsed) }
                    }
                } finally {
                    runCatching { handle.unsubscribe() }
                }
            }
    }

    private suspend fun openSubscription(
        l: NestsListener,
        pubkey: String,
        slot: ActiveSubscription,
    ) {
        if (closed || activeSubscriptions[pubkey] !== slot) return
        try {
            // [maxLatencyMs] tells the relay our staleness tolerance:
            // groups older than this should be evicted, not queued.
            // With `0L` (the wire default the JS reference uses) the
            // relay's per-subscriber forward queue grows unbounded
            // until it cliffs — see
            // `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`.
            // 500 ms is long enough to ride out a typical relay-side
            // hiccup without pruning a healthy group, short enough
            // that a wedged subscriber drains its buffer before the
            // queue overflows. Combined with the cliff-detector below
            // that triggers `recycleSession()` when frames stop
            // arriving despite the broadcast still being announced,
            // this is a defense-in-depth pair against the moq-rs
            // forward-queue starvation behaviour.
            val handle = l.subscribeSpeaker(pubkey, maxLatencyMs = ROOM_AUDIO_MAX_LATENCY_MS)
            // Re-check after the suspending subscribeSpeaker — the user
            // may have removed this speaker via updateSpeakers / disconnected
            // while the SUBSCRIBE was in flight. If so, abandon the handle
            // cleanly (fire-and-forget UNSUBSCRIBE) instead of attaching it
            // to a slot the reconcile loop has already discarded.
            if (closed || activeSubscriptions[pubkey] !== slot) {
                viewModelScope.launch { runCatching { handle.unsubscribe() } }
                return
            }
            // Start the catalog fetch BEFORE we wait for it — runs in
            // parallel with the main subscribe so the round-trip overlaps
            // with the audio-side handshake. Cancelled by closeSubscription.
            fetchSpeakerCatalog(l, pubkey)
            // Wait briefly for the catalog so we can configure the
            // decoder + AudioTrack to match the publisher's actual
            // channel layout. With the speaker-side emit-on-subscribe
            // hook the catalog frame is on the wire within one round-trip
            // of the SUBSCRIBE_OK, so this typically returns within tens
            // of ms. Falls back to mono if no catalog arrives within
            // [CATALOG_AWAIT_TIMEOUT_MS] — covers legacy publishers and
            // the failure-mode where the relay never forwards the catalog
            // group; audio still plays, just in the default config.
            val audioCfg = awaitAudioPipelineConfig(pubkey, CATALOG_AWAIT_TIMEOUT_MS)
            // Allocate native resources (MediaCodec decoder + AudioTrack
            // player on Android). Both are heavy and leaky if dropped on
            // the floor — wrap them in a nested try so any cancellation
            // or throw between here and slot.attach releases them
            // (audit round-2 VM #7).
            // Per-subscription decoder factory closure: captures the
            // catalog-derived [audioCfg] so a publisher-boundary
            // decoder rebuild (see [NestPlayer]'s `decoderFactory`
            // kdoc) reuses the SAME channel layout AND sample rate —
            // without it, a rebuild after a cliff-recycle would
            // default to mono / 48 kHz and a stereo or non-48 kHz
            // publisher would silently downmix / clock-mismatch on
            // the new decoder. The factory is also called for the
            // initial decoder so the construction path is uniform.
            val perSubscriptionDecoderFactory: () -> OpusDecoder = {
                decoderFactory(audioCfg.channelCount, audioCfg.sampleRate)
            }
            val decoder = perSubscriptionDecoderFactory()
            val player =
                try {
                    playerFactory(audioCfg.channelCount, audioCfg.sampleRate)
                } catch (t: Throwable) {
                    runCatching { decoder.release() }
                    throw t
                }
            try {
                // Apply (user-mute OR audio-focus-loss) so a speaker
                // that comes on stage during a phone call attaches
                // already silenced. The focus observer re-runs
                // applyEffectiveListenMute() on regain, restoring the
                // user's intent.
                val isMuted = effectiveListenMuted()
                val isHushed = pubkey in _uiState.value.locallyHushed
                val roomPlayer =
                    NestPlayer(
                        initialDecoder = decoder,
                        player = player,
                        scope = viewModelScope,
                        // ~100 ms of audio buffered before the AudioTrack
                        // starts consuming. Long enough to hide a typical
                        // Main-thread hiccup (Compose recomposition / GC)
                        // without making the join feel laggy. Empirically
                        // tuned in the audio-rooms audit; see NestPlayer
                        // kdoc for details.
                        prerollFrames = ROOM_PLAYER_PREROLL_FRAMES,
                        // Trigger a decoder rebuild on every publisher
                        // boundary (re-issuing wrapper spliced in a new
                        // SUBSCRIBE → trackAlias changes). Without this,
                        // Opus's predictor state from the prior
                        // publisher's last frame is fed into the new
                        // publisher's first frame and produces audible
                        // warble at every cliff-recycle / hot-swap.
                        decoderFactory = perSubscriptionDecoderFactory,
                    )
                // Apply current mute + per-speaker hush state before play()
                // opens the device so the first frame respects them.
                player.setMutedSafe(isMuted)
                if (isHushed) {
                    runCatching { player.setVolume(0f) }
                }
                // Tap the object flow to drive the speaking-now indicator before
                // the decoder consumes it.
                val instrumented = handle.objects.onEach { onSpeakerActivity(pubkey) }
                // Per-subscription consecutive-decoder-error counter. Single-frame
                // Opus errors are normal noise (PLC handles them), but a structural
                // mismatch — wrong wire format, missing legacy-container varint
                // strip, codec mismatch — fails *every* frame in a row. The
                // previous behaviour was to silently swallow per-packet
                // [AudioException.Kind.DecoderError] indefinitely, which made
                // exactly that class of bug invisible (no audio, no log, no UI
                // signal). Track the streak and surface it once per crossing so
                // the next protocol regression shows up in `adb logcat | grep
                // NestRx` rather than in a user bug report.
                val consecutiveDecodeErrors =
                    java.util.concurrent.atomic
                        .AtomicLong(0L)
                roomPlayer.play(
                    instrumented,
                    onError = { err ->
                        // Per-packet decoder errors are noise — Opus PLC
                        // papers over a single bad frame. Swallow those.
                        // PlaybackFailed (or DeviceUnavailable from a
                        // deferred [AudioPlayer.beginPlayback], or the
                        // audio-priority dispatcher dying mid-stream) is
                        // terminal: the device is wedged for THIS
                        // subscription. Roll the slot back so a future
                        // reconcile can retry rather than leave a
                        // perma-spinning speaker tile.
                        when (err.kind) {
                            AudioException.Kind.DecoderError,
                            AudioException.Kind.EncoderError,
                            -> {
                                val streak = consecutiveDecodeErrors.incrementAndGet()
                                // 50 frames = 1 s of solid decode failures.
                                // Anything past that is a structural bug, not
                                // jitter. Log once per crossing (use exact
                                // equality, not modulus) so we get a single
                                // attention-grabbing line per stuck stream
                                // rather than a 50 Hz flood.
                                if (streak == DECODE_ERROR_STREAK_LOG_THRESHOLD) {
                                    // Non-lambda overload here (rather than the
                                    // allocation-free lambda one) so we preserve
                                    // the throwable stacktrace — this fires
                                    // exactly once per stuck-stream streak, so
                                    // the unconditional string build is fine.
                                    Log.w(
                                        "NestRx",
                                        "decoder failed $DECODE_ERROR_STREAK_LOG_THRESHOLD consecutive frames " +
                                            "for pubkey='${pubkey.take(8)}' — likely wire-format mismatch " +
                                            "(missing legacy-container varint strip, codec/channel-count mismatch, " +
                                            "or corrupted Opus). last error: ${err.message}",
                                        err.cause,
                                    )
                                }
                            }

                            AudioException.Kind.PlaybackFailed,
                            AudioException.Kind.DeviceUnavailable,
                            -> {
                                if (activeSubscriptions[pubkey] === slot) {
                                    activeSubscriptions.remove(pubkey)?.let { closeSubscription(it) }
                                }
                            }
                        }
                    },
                    onLevel = { lvl ->
                        // A successful decode resets the error streak.
                        // Sample-rate-fast (50 Hz) but cheap on the
                        // success path: Atomic.set with no allocation.
                        consecutiveDecodeErrors.set(0L)
                        onAudioLevel(pubkey, lvl)
                    },
                )
                slot.attach(handle, roomPlayer, player)
                publishActiveSpeakers()
                // Enter the buffering window — UI renders a spinner
                // overlay until the first audio frame triggers
                // `onSpeakerActivity` and clears the entry.
                _uiState.update {
                    it.copy(connectingSpeakers = (it.connectingSpeakers + pubkey).toPersistentSet())
                }
                // Catalog fetch was started earlier (before the decoder
                // wait) so it could overlap with the audio-side handshake;
                // its job is already in [catalogJobs] and gets cancelled
                // by [closeSubscription] alongside the audio path.
            } catch (t: Throwable) {
                // Either CancellationException (scope cancelled mid-construction)
                // or an unexpected throw — release the half-built pipeline and
                // re-throw so the outer catch handles slot rollback.
                runCatching { player.stop() }
                runCatching { decoder.release() }
                throw t
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            // Roll back the slot we reserved so a future reconcile can retry.
            if (activeSubscriptions[pubkey] === slot) {
                activeSubscriptions.remove(pubkey)
                publishActiveSpeakers()
            }
        }
    }

    private fun teardown(
        targetState: ConnectionUiState,
        finalCleanup: Boolean = false,
    ) {
        connectJob?.cancel()
        connectJob = null
        stateObserverJob?.cancel()
        stateObserverJob = null
        announcesJob?.cancel()
        announcesJob = null
        levelEmitterJob?.cancel()
        levelEmitterJob = null
        cliffDetectorJob?.cancel()
        cliffDetectorJob = null
        lastFrameAt.clear()
        lastCliffRecycleAt = null
        consecutiveCliffRecycles = 0
        // Audio-focus observation only ends on the final teardown —
        // a transient disconnect+reconnect (user retry, room swap)
        // keeps the bus subscription alive so a focus loss that
        // happens during the gap is still picked up. The observer
        // is idempotent under [ensureAudioFocusObserverStarted], so
        // the next [connect] call is safe whether or not we
        // cancelled here.
        if (finalCleanup) {
            audioFocusObserverJob?.cancel()
            audioFocusObserverJob = null
            focusMuted = false
            networkChangeObserverJob?.cancel()
            networkChangeObserverJob = null
        }
        rawAudioLevels.clear()
        if (_audioLevels.value.isNotEmpty()) {
            _audioLevels.value = emptyMap()
        }
        if (_announcedSpeakers.value.isNotEmpty()) {
            _announcedSpeakers.value = emptySet()
        }
        // Cancel every per-speaker catalog collector — these are
        // launched on viewModelScope but track the OLD listener's
        // re-issuing handle. Without explicit cancel they'd survive
        // until onCleared and pile up across reconnect / room
        // swaps. Drop the catalog map at the same time.
        catalogJobs.values.forEach { it.cancel() }
        catalogJobs.clear()
        if (_speakerCatalogs.value.isNotEmpty()) {
            _speakerCatalogs.value = emptyMap()
        }
        // Detach + suspend-stop each player on the cleanup scope. The
        // listener.close() below tears down the MoQ session and drops
        // every active subscription, so we don't need to call
        // unsubscribe() per-handle here — but we DO need to await each
        // NestPlayer.stop() so the native MediaCodec / AudioTrack
        // is released after its decode loop has unwound.
        val scope = if (finalCleanup) cleanupScope else viewModelScope
        val players = activeSubscriptions.values.map { it.detach().first }
        activeSubscriptions.clear()
        speakingExpiryJobs.values.forEach { it.cancel() }
        speakingExpiryJobs.clear()
        if (players.isNotEmpty()) {
            scope.launch {
                players.forEach { p -> p?.runCatching { stop() } }
            }
        }
        val l = listener
        listener = null
        if (l != null) {
            // Listener.close() sends MoQ control frames (UNSUBSCRIBE etc.)
            // before the QUIC transport drops. Same scope rule as the
            // player stop above: viewModelScope when alive, cleanupScope
            // for onCleared so the wire teardown survives.
            // Track the close so the next connect() can await it (audit
            // round-2 VM #10). If `finalCleanup` is true we don't bother
            // — the VM is going away and no future connect() will run.
            val closeLaunch = scope.launch { runCatching { l.close() } }
            if (!finalCleanup) pendingCloseJob = closeLaunch
        }
        _uiState.update {
            it.copy(
                connection = targetState,
                activeSpeakers = persistentSetOf(),
                speakingNow = persistentSetOf(),
                connectingSpeakers = persistentSetOf(),
            )
        }
    }

    private fun publishActiveSpeakers() {
        val active =
            activeSubscriptions
                .filterValues { it.isPlaying }
                .keys
                .toPersistentSet()
        _uiState.update { it.copy(activeSpeakers = active) }
    }

    /**
     * Per-frame heartbeat. Called once per MoQ object received on the
     * speaker's track — i.e. once per ~20 ms regardless of whether
     * that frame contains actual speech, silence, or background noise.
     *
     * Bumps the cliff-detector timestamp so an active stream keeps
     * resetting the relay-forward-queue stall watchdog, and clears
     * the per-speaker buffering overlay the first time a frame lands.
     *
     * NB: this does NOT mark the speaker as "speaking right now".
     * That signal is energy-gated and lives in [onAudioLevel] —
     * mic-on with no voice MUST NOT light up the green ring.
     */
    private fun onSpeakerActivity(pubkey: String) {
        if (closed) return
        // Cliff-detector heartbeat: each MoQ object proves the
        // listener-side relay-forward queue is still flowing for this
        // speaker. The detector only acts when an announced speaker's
        // last-frame timestamp ages past `ROOM_AUDIO_CLIFF_TIMEOUT_MS`,
        // so an active stream resets it on every frame.
        lastFrameAt[pubkey] =
            kotlin.time.TimeSource.Monotonic
                .markNow()
        // A real frame proves the most recent recycle (if any) actually
        // fixed the cliff. Reset the consecutive-failed counter so a
        // future re-stall starts from attempt 0 (immediate-fire) rather
        // than inheriting a long backoff from the prior streak.
        if (consecutiveCliffRecycles != 0) consecutiveCliffRecycles = 0
        // First frame for this subscription — clear the buffering
        // overlay. Subsequent frames are no-ops here.
        if (_uiState.value.connectingSpeakers.contains(pubkey)) {
            _uiState.update { it.copy(connectingSpeakers = (it.connectingSpeakers - pubkey).toPersistentSet()) }
        }
    }

    /**
     * Mark [pubkey] as currently speaking and (re)arm a
     * [SPEAKING_TIMEOUT_MS] coroutine that clears the flag once their
     * audio drops back below the threshold for that long. Called from
     * [onAudioLevel] only when the decoded peak is loud enough to
     * read as speech (see [SPEAKING_LEVEL_THRESHOLD]).
     */
    private fun markSpeaking(pubkey: String) {
        speakingExpiryJobs[pubkey]?.cancel()
        if (!_uiState.value.speakingNow.contains(pubkey)) {
            _uiState.update { it.copy(speakingNow = (it.speakingNow + pubkey).toPersistentSet()) }
        }
        speakingExpiryJobs[pubkey] =
            viewModelScope.launch {
                delay(SPEAKING_TIMEOUT_MS)
                clearSpeaking(pubkey)
            }
    }

    /**
     * Periodic scan that detects the relay-side forward-queue cliff:
     * an announced speaker whose subscription is open but hasn't
     * delivered any object in [ROOM_AUDIO_CLIFF_TIMEOUT_MS]. Triggers
     * `recycleSession()` on the listener so the wrapper opens a fresh
     * QUIC transport — the new session has a fresh per-subscriber
     * queue on the relay's side.
     *
     * Idempotent on start. Stops itself when the listener is gone
     * (explicit teardown signal) — no-ops on transient empty
     * `activeSubscriptions` so an audience-only listener doesn't
     * needlessly recycle.
     */
    private fun startCliffDetector() {
        if (cliffDetectorJob?.isActive == true) return
        com.vitorpamplona.quartz.utils.Log
            .d("NestRx") { "cliff-detector launched" }
        cliffDetectorJob =
            viewModelScope.launch {
                var ticks = 0L
                try {
                    while (true) {
                        delay(ROOM_AUDIO_CLIFF_CHECK_INTERVAL_MS)
                        ticks += 1
                        if (closed) return@launch
                        val l = listener
                        val connState = _uiState.value.connection
                        if (l == null || connState !is ConnectionUiState.Connected) {
                            // Periodic visibility into "why isn't the cliff
                            // detector acting" — the receiver-side cliff
                            // we're chasing has been silent in production
                            // logs even when the wire conditions look right,
                            // so dump our gating state every 5 s.
                            if (ticks % CLIFF_DIAG_LOG_EVERY == 0L) {
                                com.vitorpamplona.quartz.utils.Log.d("NestRx") {
                                    "cliff-detector tick=$ticks gated: listener=${l != null} conn=${connState::class.simpleName}"
                                }
                            }
                            continue
                        }
                        val activeSpeakers =
                            activeSubscriptions
                                .asSequence()
                                .filter { (_, slot) -> slot.isPlaying }
                                .map { it.key }
                                .toSet()
                        val announced = _announcedSpeakers.value
                        if (ticks % CLIFF_DIAG_LOG_EVERY == 0L) {
                            val ages =
                                lastFrameAt.entries.joinToString { (k, mark) ->
                                    "${k.take(8)}=${mark.elapsedNow().inWholeMilliseconds}ms"
                                }
                            com.vitorpamplona.quartz.utils.Log.d("NestRx") {
                                "cliff-detector tick=$ticks active=${activeSpeakers.size} announced=${announced.size} lastFrameAges=[$ages]"
                            }
                            com.vitorpamplona.nestsclient.trace.NestsTrace.emit("cliff_tick") {
                                "\"tick\":$ticks," +
                                    "\"active\":${com.vitorpamplona.nestsclient.trace.jsonArrStr(activeSpeakers)}," +
                                    "\"announced\":${com.vitorpamplona.nestsclient.trace.jsonArrStr(announced)}," +
                                    "\"last_frame_age_ms\":{" +
                                    lastFrameAt.entries.joinToString {
                                        "${com.vitorpamplona.nestsclient.trace.jsonStr(it.key)}:${it.value.elapsedNow().inWholeMilliseconds}"
                                    } + "}"
                            }
                        }
                        val stalled =
                            computeStalledSpeakers(
                                activeSpeakers = activeSpeakers,
                                announcedSpeakers = announced,
                                lastFrameAt = lastFrameAt,
                                lastRecycleAt = lastCliffRecycleAt,
                                consecutiveFailedRecycles = consecutiveCliffRecycles,
                                cliffTimeoutMs = ROOM_AUDIO_CLIFF_TIMEOUT_MS,
                                postRecycleGraceMs = ROOM_AUDIO_CLIFF_RECYCLE_GRACE_MS,
                            )
                        if (stalled.isEmpty()) continue
                        consecutiveCliffRecycles += 1
                        com.vitorpamplona.quartz.utils.Log.w("NestRx") {
                            "cliff-detector: announced+subscribed but silent for ≥${ROOM_AUDIO_CLIFF_TIMEOUT_MS}ms — recycling session " +
                                "(consecutive=$consecutiveCliffRecycles). stalled=$stalled"
                        }
                        com.vitorpamplona.nestsclient.trace.NestsTrace.emit("cliff_recycle") {
                            "\"timeout_ms\":$ROOM_AUDIO_CLIFF_TIMEOUT_MS," +
                                "\"consecutive\":$consecutiveCliffRecycles," +
                                "\"stalled\":${com.vitorpamplona.nestsclient.trace.jsonArrStr(stalled)}"
                        }
                        lastCliffRecycleAt =
                            kotlin.time.TimeSource.Monotonic
                                .markNow()
                        // Note: we deliberately do NOT overwrite
                        // `lastFrameAt[pubkey]` with the recycle moment.
                        // Keeping the real-frame timestamp lets the next
                        // tick distinguish "the recycle delivered audio,
                        // we then re-stalled" (lastFrameAt advances past
                        // lastCliffRecycleAt — counter resets in
                        // `onSpeakerActivity`, attempt = 0, fire-eligible
                        // immediately) from "the recycle did nothing,
                        // still no frames" (lastFrameAt unchanged,
                        // counter keeps growing, backoff escalates).
                        // The previous reset collapsed both cases into
                        // a flat 30 s wait, which made a single failed
                        // recycle sound like a 30 s+ dropout to the
                        // user even when retrying earlier would have
                        // recovered.
                        try {
                            l.recycleSession()
                        } catch (ce: CancellationException) {
                            // User left mid-recycle — exit promptly
                            // rather than letting the next delay() be
                            // the one to honor the cancel. `runCatching`
                            // would have swallowed this CE and run one
                            // more loop body before exiting.
                            throw ce
                        } catch (_: Throwable) {
                            // Any other recycle failure is best-effort;
                            // keep the loop running so a follow-up tick
                            // can re-detect the cliff and retry.
                        }
                    }
                } finally {
                    com.vitorpamplona.quartz.utils.Log
                        .w("NestRx") { "cliff-detector EXITED after $ticks ticks (closed=$closed)" }
                }
            }
    }

    private fun clearSpeaking(pubkey: String) {
        speakingExpiryJobs.remove(pubkey)
        if (_uiState.value.speakingNow.contains(pubkey)) {
            _uiState.update { it.copy(speakingNow = (it.speakingNow - pubkey).toPersistentSet()) }
        }
        // Drop the latest level too — when the speaker goes quiet the
        // ring should fall back to the static "in speakingNow" colour
        // rather than freezing at the last loud peak.
        if (rawAudioLevels.remove(pubkey) != null) {
            _audioLevels.value = rawAudioLevels.toMap()
        }
    }

    /**
     * Record the latest decoded peak amplitude for [pubkey]. Called
     * from the [NestPlayer] decode loop on the same dispatcher as the
     * VM, so plain map mutation is safe. Boots the coalescing emitter
     * on first activity; the emitter shuts itself down once all audio
     * has gone quiet, so a long-idle room costs no scheduled work.
     */
    private fun onAudioLevel(
        pubkey: String,
        level: Float,
    ) {
        if (closed) return
        rawAudioLevels[pubkey] = level
        // Energy-gated speaking detector. The MoQ track delivers a
        // frame every ~20 ms while the mic is open, even when the
        // speaker is silent or only picking up room noise — gating the
        // green ring on "frame arrived" therefore lights it up the
        // moment the mic is unmuted, not when there's actually a voice
        // on it. The decoded peak amplitude (`peakAmplitude` in
        // nestsClient/audio/Amplitude.kt) gives us the signal we need:
        // background noise / breath stays under a few percent of full
        // scale, while even a quiet voice clears [SPEAKING_LEVEL_THRESHOLD].
        // The 250 ms expiry already wired up in [markSpeaking] gives
        // the indicator natural hysteresis between syllables.
        if (level >= SPEAKING_LEVEL_THRESHOLD) {
            markSpeaking(pubkey)
        }
        startLevelEmitter()
    }

    /**
     * Tick every [LEVEL_TICK_MS] and publish the current map of
     * per-speaker levels. Coalesces the high-frequency raw updates
     * into ~10 Hz UI state so the speaking-ring animation has a
     * smooth, lightweight signal to follow.
     *
     * Self-terminating: once a tick observes an empty raw map (and
     * has flushed the empty snapshot to consumers) the loop exits.
     * The next [onAudioLevel] restarts it. Idempotent — calling
     * this while already active is a no-op.
     */
    private fun startLevelEmitter() {
        if (levelEmitterJob?.isActive == true) return
        levelEmitterJob =
            viewModelScope.launch {
                while (true) {
                    delay(LEVEL_TICK_MS)
                    if (closed) return@launch
                    val snapshot = if (rawAudioLevels.isEmpty()) emptyMap() else rawAudioLevels.toMap()
                    if (snapshot != _audioLevels.value) {
                        _audioLevels.value = snapshot
                    }
                    if (snapshot.isEmpty()) return@launch
                }
            }
    }

    // ActiveSubscription was extracted to its own file in the same
    // package — see [ActiveSubscription.kt].

    // Platform-specific Factory lives in `amethyst/.../nests/room/`,
    // not commonMain — the lifecycle KMP `ViewModelProvider.Factory`
    // signature has been migrating between releases; a thin Android-side
    // factory keeps that churn out of shared code.
}

/**
 * Map [NestsListenerState] (from the transport library) onto the small set of
 * UI-level states the screen actually needs to render. We collapse the three
 * `Connecting` substeps into one enum so the chip can show a single
 * progress message; advanced UI can branch on [ConnectionUiState.Step]
 * later if it cares.
 */
private fun NestsListenerState.toUiState(previous: ConnectionUiState): ConnectionUiState =
    when (this) {
        // The transport library starts in Idle, but the VM has already
        // shown "Connecting → ResolvingRoom" by the time observation starts;
        // don't regress the UI back to Idle on the very first emission.
        NestsListenerState.Idle -> {
            if (previous is ConnectionUiState.Connecting) previous else ConnectionUiState.Idle
        }

        is NestsListenerState.Connecting -> {
            ConnectionUiState.Connecting(
                step =
                    when (step) {
                        NestsListenerState.Connecting.ConnectStep.ResolvingRoom -> ConnectionUiState.Step.ResolvingRoom
                        NestsListenerState.Connecting.ConnectStep.OpeningTransport -> ConnectionUiState.Step.OpeningTransport
                        NestsListenerState.Connecting.ConnectStep.MoqHandshake -> ConnectionUiState.Step.MoqHandshake
                    },
            )
        }

        is NestsListenerState.Connected -> {
            ConnectionUiState.Connected
        }

        is NestsListenerState.Reconnecting -> {
            ConnectionUiState.Reconnecting(attempt = attempt, delayMs = delayMs)
        }

        is NestsListenerState.Failed -> {
            ConnectionUiState.Failed(reason)
        }

        NestsListenerState.Closed -> {
            ConnectionUiState.Closed
        }
    }

private fun AudioPlayer.setMutedSafe(muted: Boolean) {
    runCatching { setMuted(muted) }
}

@Immutable
data class NestUiState(
    val connection: ConnectionUiState = ConnectionUiState.Idle,
    val isMuted: Boolean = false,
    /** Pubkeys we have an open MoQ subscription for. */
    val activeSpeakers: ImmutableSet<String> = persistentSetOf(),
    /** Pubkeys whose audio track delivered an object in the last [SPEAKING_TIMEOUT_MS]. */
    val speakingNow: ImmutableSet<String> = persistentSetOf(),
    /**
     * Pubkeys we have an open subscription for but no audio frame has
     * arrived yet — the pre-roll window between SUBSCRIBE_OK and the
     * first decoded packet. Typically 0.5-2 s on a fresh join. The UI
     * shows a buffering overlay here so the user knows audio is on
     * its way (vs the speaker sitting silent on stage).
     *
     * Membership is set-once-per-subscription: once a frame arrives
     * the pubkey moves out of this set and stays out, even if the
     * speaker subsequently goes quiet. Cleared on speaker removal.
     */
    val connectingSpeakers: ImmutableSet<String> = persistentSetOf(),
    /** Speaker / publisher state — only relevant when [NestViewModel.canBroadcast]. */
    val broadcast: BroadcastUiState = BroadcastUiState.Idle,
    /**
     * `true` when we hold a speaker slot vs being pure audience. Defaults
     * to `true` for users with [NestViewModel.canBroadcast]; the
     * "leave the stage" tap (Tier 2) flips it to `false`. Drives the
     * `["onstage", "0|1"]` tag on emitted kind-10312 presence events.
     */
    val onStageNow: Boolean = true,
    /**
     * Pubkeys the local user has chosen to silence in their own
     * playback ("hush"). Independent of [isMuted]. Persists across
     * subscription churn so re-subscribing to a hushed speaker stays
     * silent. Drives a "Hush this speaker locally" toggle in the
     * participant context sheet.
     */
    val locallyHushed: ImmutableSet<String> = persistentSetOf(),
) {
    /**
     * Derived: are we currently pushing audio packets to the relay?
     * `true` only when the broadcast handle is live AND we're not muted.
     * Drives the `["publishing", "0|1"]` tag on emitted kind-10312
     * presence events.
     */
    val publishingNow: Boolean
        get() = broadcast is BroadcastUiState.Broadcasting && !broadcast.isMuted
}

@Immutable
sealed class BroadcastUiState {
    data object Idle : BroadcastUiState()

    data object Connecting : BroadcastUiState()

    data class Broadcasting(
        val isMuted: Boolean,
        /**
         * Non-null when the most recent mute toggle failed (e.g. handle
         * setMuted threw). UI surfaces this as an inline message; the
         * broadcast itself stays running with the previous mute state.
         * Cleared on the next successful mute toggle.
         */
        val muteError: String? = null,
    ) : BroadcastUiState()

    data class Failed(
        val reason: String,
    ) : BroadcastUiState()
}

/**
 * How long a speaker stays "speaking" after their last decoded frame
 * over the [SPEAKING_LEVEL_THRESHOLD]. Roughly 12 × the 20 ms Opus
 * frame so brief packet jitter and inter-syllable pauses don't make
 * the indicator flicker between adjacent words.
 */
const val SPEAKING_TIMEOUT_MS: Long = 250L

/**
 * Minimum decoded peak amplitude (normalized to `[0, 1]`) that counts
 * as "this person is actually speaking right now". Frames whose peak
 * lands below this threshold are treated as silence / room tone /
 * breath — they keep the per-speaker subscription healthy (the cliff
 * heartbeat in [NestViewModel.onSpeakerActivity] still fires) but do
 * NOT light up the green speaking ring.
 *
 * 0.06 ≈ -24 dBFS, comfortably above the typical residential-mic
 * noise floor (~-40 to -30 dBFS) while still tripping on a quiet
 * voice. Tuned in conjunction with [SPEAKING_TIMEOUT_MS]: a single
 * loud frame is enough to arm the indicator; ≥ 250 ms below the
 * threshold drops it.
 */
const val SPEAKING_LEVEL_THRESHOLD: Float = 0.06f

/**
 * How long [NestViewModel.openSubscription] waits for the publisher's
 * `catalog.json` to land before constructing the decoder + AudioTrack.
 * The catalog declares the audio rendition's `numberOfChannels` and
 * `sampleRate`, which the decoder needs at construction time so a
 * stereo or non-48 kHz web publisher doesn't get its frames decoded
 * with the wrong layout / clock.
 *
 * Sized to be generous enough that a typical publisher's catalog
 * (which arrives within a single round-trip of the SUBSCRIBE_OK with
 * the speaker-side emit-on-subscribe hook in place) lands well before
 * the timeout, and short enough that a publisher that never emits a
 * catalog (legacy publishers, relay-side bug) doesn't visibly stall
 * the listener — audio playback proceeds in the default config.
 *
 * **Frame-buffer budget**: while we wait, audio frames stream from
 * the relay into the wrapper's
 * [com.vitorpamplona.nestsclient.ReconnectingNestsListener]
 * `SUBSCRIBE_BUFFER = 64` SharedFlow with `DROP_OLDEST` overflow.
 * At 50 fps Opus that's 1.28 s of headroom; the 500 ms wait
 * consumes at most 25 frames of buffer, leaving ≥ 39 frames of
 * margin (≈780 ms) before the oldest frame would be dropped. Even
 * at the production `framesPerGroup = 50` (= 1 group / sec) cadence
 * this never trips the eviction path during normal startup.
 */
const val CATALOG_AWAIT_TIMEOUT_MS: Long = 500L

/**
 * Per-subscription consecutive Opus decode-error count that triggers a
 * single conspicuous warning log. Single-frame decode errors are normal
 * — Opus PLC papers over them — and are silently swallowed; a streak of
 * this size is structural (wire-format mismatch, missing
 * legacy-container varint strip, codec/channel-count mismatch, corrupt
 * publisher) and demands attention because the user-visible symptom is
 * "speaker tile shows up but no audio plays" with no other signal.
 *
 * 50 × 20 ms ≈ 1 s. Long enough that a transient flurry of bad frames
 * doesn't cry wolf; short enough that an "every frame fails" structural
 * bug surfaces in a logcat line within the first second of audio.
 *
 * Logged once per crossing (exact equality, not modulus) so a stuck
 * stream produces ONE attention-grabbing line rather than 50 Hz of
 * noise.
 */
const val DECODE_ERROR_STREAK_LOG_THRESHOLD: Long = 50L

/**
 * Per-speaker pre-roll: number of decoded PCM frames buffered before the
 * underlying [com.vitorpamplona.nestsclient.audio.AudioPlayer] starts
 * consuming. 10 × 20 ms ≈ 200 ms of audio.
 *
 * Sized to absorb the slowest-cadence publisher we expect: kixelated/moq's
 * web reference encoder uses `groupDuration: 100ms` (5 frames per group)
 * by default but adds another 100–200 ms of jitter on the relay's
 * outbound forward queue under residential network conditions. 100 ms of
 * preroll (the previous value) reliably underran on web speakers — the
 * AudioTrack ran out of samples between adjacent groups and the user
 * heard short clicks. 200 ms covers the observed jitter envelope while
 * keeping perceived join latency well under the typical "speaker becomes
 * audible" delay on the wire.
 *
 * Combines with [com.vitorpamplona.nestsclient.audio.AudioTrackPlayer]'s
 * ~250 ms AudioTrack buffer for ~450 ms of total slack between the
 * arrival-of-frame and the underrun horizon.
 */
const val ROOM_PLAYER_PREROLL_FRAMES: Int = 10

/**
 * Coalescing interval for [NestViewModel.audioLevels]. The decode loop
 * pushes a fresh peak every ~20 ms (one per Opus frame); we publish to
 * the StateFlow at this cadence instead so the UI ring animates ~10 Hz
 * instead of recomposing every frame. 100 ms is fast enough that the
 * eye still reads the throb as live, slow enough that the cost across
 * a busy stage stays trivial.
 */
const val LEVEL_TICK_MS: Long = 100L

/**
 * `max_latency` we send on every speaker SUBSCRIBE. Tells the relay
 * to evict groups older than this from the per-subscriber forward
 * queue rather than holding them up to the relay's `MAX_GROUP_AGE`
 * default (30 s). Defends against the moq-rs forward-queue cliff
 * documented in
 * `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`.
 * 500 ms = 25 Opus frames at 20 ms cadence — long enough to ride
 * out a typical hiccup without dropping a healthy frame, short
 * enough to keep the relay's per-subscriber queue bounded.
 */
const val ROOM_AUDIO_MAX_LATENCY_MS: Long = 500L

/**
 * Inactivity threshold for the listener-side cliff detector. If
 * a speaker is `_announcedSpeakers` (= relay says they're broadcasting)
 * AND the wrapper's MoqObject flow doesn't deliver a frame for this
 * many milliseconds, the listener forces a `recycleSession()` so the
 * orchestrator opens a fresh QUIC transport. Resets the relay's
 * per-subscriber forward queue.
 *
 * 2 500 ms is past every legitimate group boundary (`framesPerGroup =
 * 50` packs 1 s of audio per uni stream, so the receiver sees one
 * `drainOneGroup` FIN every ~1 s on a healthy stream — 2.5 s of
 * silence means more than two group cycles missed, very likely a
 * cliff). Earlier than 2 s would false-positive on the natural 1 s
 * cadence; later than 3 s wastes audible audio at every cliff edge.
 *
 * Production logs (`6e4df4a` run 18:37:43..18:38:08) showed the
 * cliff-after-streaming case losing ~6 s of audio at the 4 s
 * detector + ~2 s reconnect handshake. Tightening detection to
 * 2.5 s shaves ~1.5 s off the audible gap.
 */
const val ROOM_AUDIO_CLIFF_TIMEOUT_MS: Long = 2_500L

/**
 * How often the cliff detector wakes up to scan
 * `activeSubscriptions` against `lastFrameAt`. 1 s is a coarse
 * enough cadence not to cost anything (one map walk per second)
 * while keeping detection latency well under the 4 s threshold.
 */
const val ROOM_AUDIO_CLIFF_CHECK_INTERVAL_MS: Long = 1_000L

/**
 * Post-recycle handshake grace. NEVER recycle again within this
 * window of the previous recycle — the wrapper is still tearing
 * down + reopening the QUIC session, so a "no frames since recycle"
 * reading is just the handshake, not a relay-side cliff.
 *
 * 3 s covers a typical re-handshake (drain old session UNSUB +
 * UNANNOUNCE + WT_CLOSE + open new WebTransport + SUBSCRIBE +
 * SUBSCRIBE_OK). Anything shorter risks recycling inside our own
 * handshake window; longer wastes audible silence in the recovering-
 * within-grace case.
 */
const val ROOM_AUDIO_CLIFF_RECYCLE_GRACE_MS: Long = 3_000L

/**
 * Maximum backoff for the consecutive-failed-recycle schedule —
 * the cap that [defaultCliffBackoffMs] saturates at.
 *
 * The earlier flat 30 s cooldown was motivated by production logs
 * at commit ea08c43 where 4 back-to-back recycles in ~30 s drove
 * moq-rs into a "subscribe stream FIN before reply" loop that
 * refused all subsequent subscribes. The replacement schedule
 * (5 s → 12 s → 24 s → 30 s, reset on first real frame) keeps
 * the same final-state protection — by the 4th consecutive failed
 * recycle we're spaced 30 s apart — while letting the *recovering*
 * case (your trace: 1st recycle worked, 2nd cliff fires later)
 * retry within ~5 s instead of the previous 30 s of dead air.
 */
const val ROOM_AUDIO_CLIFF_BACKOFF_MAX_MS: Long = 30_000L

/**
 * Diagnostic log frequency for the cliff detector. Emit a state-dump
 * line every Nth 1-second tick — i.e. every 5 s while connected and
 * idle, so logcat captures the "active / announced / lastFrameAges"
 * snapshot we use to debug silent-cliff cases without flooding when
 * everything is fine.
 */
private const val CLIFF_DIAG_LOG_EVERY: Long = 5L

/**
 * Default backoff schedule for consecutive failed recycles. Returns
 * the minimum elapsed time since [NestViewModel.lastCliffRecycleAt]
 * before the [attempt + 1]-th recycle is permitted.
 *
 *   attempt 0  → 0 ms       (no recycle yet — first cliff fires immediately)
 *   attempt 1  → 5 000 ms   (one prior recycle, no audio since)
 *   attempt 2  → 12 000 ms
 *   attempt 3  → 24 000 ms
 *   attempt 4+ → 30 000 ms  (cap, [ROOM_AUDIO_CLIFF_BACKOFF_MAX_MS])
 *
 * Resets to 0 on the first real frame after a recycle (see
 * [NestViewModel.onSpeakerActivity]) — so a recover-then-restall
 * pattern always gets the immediate-fire treatment again rather
 * than inheriting backoff from the prior failed-recycle streak.
 *
 * Cumulative wall-clock at attempt N: 0 → 5 → 17 → 41 → 71 s.
 * 4 consecutive recycles span ~41 s — meaningfully slower than the
 * "4 in ~30 s" pattern that wedged moq-rs in commit ea08c43, while
 * still letting the typical 1-failure case retry inside 5 s.
 */
internal fun defaultCliffBackoffMs(attempt: Int): Long =
    when {
        attempt <= 0 -> 0L
        attempt == 1 -> 5_000L
        attempt == 2 -> 12_000L
        attempt == 3 -> 24_000L
        else -> ROOM_AUDIO_CLIFF_BACKOFF_MAX_MS
    }

/**
 * Pure logic of the cliff detector — extracted so headless tests can
 * exercise it with a [kotlin.time.TestTimeSource] without standing up
 * the full [NestViewModel] coroutine machinery.
 *
 * Returns the list of pubkeys that should trigger a `recycleSession()`
 * RIGHT NOW. Empty list means "no cliff observed; do nothing on this
 * tick."
 *
 * Recycle conditions, all-of:
 *   - the pubkey has an active subscription on our side
 *     ([activeSpeakers]) — i.e. we're actively pulling its audio,
 *   - the relay has told us this pubkey is broadcasting
 *     ([announcedSpeakers]),
 *   - we've received at least one MoQ object in the past
 *     ([lastFrameAt] has an entry — we don't preemptively recycle
 *     a brand-new subscription that simply hasn't ramped up yet),
 *   - the elapsed time since that last object is at or past
 *     [cliffTimeoutMs].
 *
 * Suppression (when [lastRecycleAt] is non-null):
 *   - while elapsed since [lastRecycleAt] is below [postRecycleGraceMs],
 *     never recycle — we're still inside the previous handshake window.
 *   - while elapsed is below [backoffForAttempt]([consecutiveFailedRecycles]),
 *     suppress as well: the per-attempt schedule throttles
 *     consecutive failed recycles so we don't hammer the relay.
 *   - the consecutive counter is reset by the caller on the first
 *     real frame after a recycle, so a recovered-then-restall
 *     case always re-enters with attempt = 0 and fires immediately.
 */
internal fun computeStalledSpeakers(
    activeSpeakers: Set<String>,
    announcedSpeakers: Set<String>,
    lastFrameAt: Map<String, kotlin.time.TimeMark>,
    lastRecycleAt: kotlin.time.TimeMark?,
    consecutiveFailedRecycles: Int = 0,
    cliffTimeoutMs: Long = ROOM_AUDIO_CLIFF_TIMEOUT_MS,
    postRecycleGraceMs: Long = ROOM_AUDIO_CLIFF_RECYCLE_GRACE_MS,
    backoffForAttempt: (Int) -> Long = ::defaultCliffBackoffMs,
): List<String> {
    if (lastRecycleAt != null) {
        val sinceRecycleMs = lastRecycleAt.elapsedNow().inWholeMilliseconds
        if (sinceRecycleMs < postRecycleGraceMs) return emptyList()
        if (sinceRecycleMs < backoffForAttempt(consecutiveFailedRecycles)) return emptyList()
    }
    return activeSpeakers
        .asSequence()
        .filter { it in announcedSpeakers }
        .filter { pubkey ->
            val mark = lastFrameAt[pubkey] ?: return@filter false
            mark.elapsedNow().inWholeMilliseconds >= cliffTimeoutMs
        }.toList()
}

/**
 * How long a kind-7 reaction stays in
 * [NestViewModel.recentReactions] before the eviction sweep
 * drops it. Reactions are about what the speaker is talking about
 * RIGHT NOW — a 10 s window keeps the floating-emoji overlay
 * timely (a reaction to one sentence shouldn't bleed into the
 * next paragraph) and matches the duration of the fade-up
 * animation in the SpeakerReactionOverlay.
 */
const val REACTION_WINDOW_SEC: Long = 10L

/**
 * Indirection over the top-level `connectNestsListener` so tests can drive
 * a fake [NestsListener] directly without standing up an HTTP fake +
 * WebTransport fake.
 */
fun interface NestsListenerConnector {
    suspend fun connect(
        httpClient: NestsClient,
        transport: WebTransportFactory,
        scope: CoroutineScope,
        room: NestsRoomConfig,
        signer: NostrSigner,
    ): NestsListener
}

/**
 * Production listener factory — wraps each session in
 * [connectReconnectingNestsListener] so transport drops auto-retry
 * with exponential backoff and `SubscribeHandle`s survive session
 * swaps via the wrapper's MutableSharedFlow re-issuance pump.
 *
 * The VM's own scheduleAutoRetry path was retired in favour of
 * this — a single retry policy lives in the transport layer rather
 * than racing two of them.
 */
private val DefaultNestsListenerConnector =
    NestsListenerConnector { httpClient, transport, scope, room, signer ->
        connectReconnectingNestsListener(
            httpClient = httpClient,
            transport = transport,
            scope = scope,
            room = room,
            signer = signer,
        )
    }

/** Speaker-side equivalent of [NestsListenerConnector]. */
fun interface NestsSpeakerConnector {
    suspend fun connect(
        httpClient: NestsClient,
        transport: WebTransportFactory,
        scope: CoroutineScope,
        room: NestsRoomConfig,
        signer: NostrSigner,
        speakerPubkeyHex: String,
        captureFactory: () -> AudioCapture,
        encoderFactory: () -> OpusEncoder,
    ): NestsSpeaker
}

/**
 * Production speaker factory — wraps each session in
 * [connectReconnectingNestsSpeaker] so transport drops auto-retry
 * with exponential backoff AND the moq-auth 600 s JWT TTL is
 * proactively refreshed (default 540 s recycle window). The
 * returned [BroadcastHandle] survives every refresh — the wrapper
 * re-issues publishing onto each fresh session and replays the
 * user's mute state on the new handle.
 */
private val DefaultNestsSpeakerConnector =
    NestsSpeakerConnector { httpClient, transport, scope, room, signer, pubkey, capF, encF ->
        connectReconnectingNestsSpeaker(
            httpClient = httpClient,
            transport = transport,
            scope = scope,
            room = room,
            signer = signer,
            speakerPubkeyHex = pubkey,
            captureFactory = capF,
            encoderFactory = encF,
        )
    }

@Immutable
sealed class ConnectionUiState {
    data object Idle : ConnectionUiState()

    data class Connecting(
        val step: Step,
    ) : ConnectionUiState()

    data object Connected : ConnectionUiState()

    /**
     * The previous session dropped and the wrapper's retry loop is
     * waiting [delayMs] before its next attempt. [attempt] is
     * 1-indexed (1 = first retry after the original session
     * failed). UI shows "Reconnecting…" with a friendlier message
     * than a raw `Failed` would convey — the user typically
     * doesn't need to do anything; the orchestrator will flip back
     * to [Connected] on its own.
     */
    data class Reconnecting(
        val attempt: Int,
        val delayMs: Long,
    ) : ConnectionUiState()

    data class Failed(
        val reason: String,
    ) : ConnectionUiState()

    data object Closed : ConnectionUiState()

    enum class Step {
        ResolvingRoom,
        OpeningTransport,
        MoqHandshake,
    }
}
