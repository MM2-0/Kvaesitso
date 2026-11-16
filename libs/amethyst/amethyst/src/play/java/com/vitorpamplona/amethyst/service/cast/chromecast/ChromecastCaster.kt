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
package com.vitorpamplona.amethyst.service.cast.chromecast

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cast_error_connect_failed
import com.vitorpamplona.amethyst.commons.resources.cast_error_device_offline
import com.vitorpamplona.amethyst.commons.resources.cast_error_load_failed
import com.vitorpamplona.amethyst.commons.resources.cast_error_play_services_unavailable
import com.vitorpamplona.amethyst.commons.resources.cast_error_playback_failed
import com.vitorpamplona.amethyst.commons.resources.cast_error_playback_failed_detail
import com.vitorpamplona.amethyst.commons.resources.cast_error_receiver_not_responding
import com.vitorpamplona.amethyst.commons.resources.cast_error_receiver_not_responding_detail
import com.vitorpamplona.amethyst.commons.resources.cast_error_unsupported_media
import com.vitorpamplona.amethyst.commons.resources.cast_error_unsupported_media_detail
import com.vitorpamplona.amethyst.service.cast.CastDevice
import com.vitorpamplona.amethyst.service.cast.CastErrorMessage
import com.vitorpamplona.amethyst.service.cast.CastRequest
import com.vitorpamplona.amethyst.service.cast.CastRoutePlan
import com.vitorpamplona.amethyst.service.cast.CastSessionState
import com.vitorpamplona.amethyst.service.cast.effectiveMimeType
import com.vitorpamplona.amethyst.service.cast.planRouteSelection
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ChromecastCaster"
private const val SESSION_START_TIMEOUT_MS = 30_000L
private const val STOP_AWAIT_TIMEOUT_MS = 5_000L

/**
 * How long to wait for the session to actually end after asking the receiver app to stop.
 *
 * Unselecting the route drops the connection, so doing it before the receiver has processed
 * STOP_APP leaves the app running and the TV parked on the Default Media Receiver splash — the
 * "default renderer" screen the user has to leave with the TV remote. Observed at ~25-170ms on a
 * webOS TV, longer the more playback there was to flush, so this is generous.
 */
private const val SESSION_END_TIMEOUT_MS = 5_000L

/**
 * How long the receiver gets to move off LOADING before we call it stalled.
 *
 * A healthy receiver takes ~1s. A wedged one — the state an LG webOS TV lands in after its Cast
 * service crashes, cleared only by power-cycling the TV — accepts the load, reports LOADING, and
 * then reports nothing ever again: no progress, no error, no session end. Generous by design, since
 * overshooting only delays an error message while undershooting aborts a slow but working load.
 */
private const val LOAD_PROGRESS_TIMEOUT_MS = 20_000L

/** Android 17 — the first release to enforce Local Network Protection. See [ChromecastCaster.localNetworkState]. */
private const val LOCAL_NETWORK_PROTECTION_SDK = 37

/**
 * Google Cast (Chromecast) caster.
 *
 * Only present in the play flavor — the F-Droid flavor ships a no-op stub
 * with the same FQN. The class still works at runtime when Google Play
 * services are unavailable: discovery returns an empty list and casts fail
 * cleanly.
 */
class ChromecastCaster(
    private val appContext: Context,
) {
    private val devicesFlow = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = devicesFlow.asStateFlow()

    private val sessionFlow = MutableStateFlow<CastSessionState>(CastSessionState.Idle)
    val sessionState: StateFlow<CastSessionState> = sessionFlow.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var mediaRouter: MediaRouter? = null
    private var routeSelector: MediaRouteSelector? = null

    @Volatile
    private var castContext: CastContext? = null
    private var registered = false

    // Session listener lifetime is independent of discovery: the picker may
    // open and close while a cast is still running, so we register the
    // listener once when CastContext becomes available and keep it for the
    // lifetime of the caster. Without this, stopDiscovery() during cast()'s
    // finally block would unregister the listener mid-session and we'd never
    // observe onSessionEnded — leaving sessionFlow out of sync with the TV.
    @Volatile
    private var sessionListenerAttached = false

    private val routes = mutableMapOf<String, MediaRouter.RouteInfo>()

    private val routerCallback =
        object : MediaRouter.Callback() {
            override fun onRouteAdded(
                router: MediaRouter,
                route: MediaRouter.RouteInfo,
            ) {
                Log.d(TAG) { "onRouteAdded id=${route.id} name=${route.name}" }
                updateRoutes(router)
            }

            override fun onRouteRemoved(
                router: MediaRouter,
                route: MediaRouter.RouteInfo,
            ) {
                Log.d(TAG) { "onRouteRemoved id=${route.id} name=${route.name}" }
                updateRoutes(router)
            }

            override fun onRouteChanged(
                router: MediaRouter,
                route: MediaRouter.RouteInfo,
            ) {
                Log.d(TAG) { "onRouteChanged id=${route.id} name=${route.name} selected=${route.isSelected}" }
                updateRoutes(router)
            }
        }

    private val mediaClientCallback =
        object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                val client = currentMediaClient ?: return
                val status = client.mediaStatus
                val idleReason = status?.idleReason ?: -1
                Log.i(TAG) {
                    "media.onStatusUpdated playerState=${playerStateName(client.playerState)} " +
                        "idleReason=${idleReasonName(idleReason)} " +
                        "pos=${client.approximateStreamPosition}/${client.streamDuration}ms"
                }
                // Any real progress means the receiver is alive and the watchdog has done its job.
                if (client.playerState == MediaStatus.PLAYER_STATE_PLAYING || client.playerState == MediaStatus.PLAYER_STATE_BUFFERING) {
                    cancelLoadWatchdog()
                }
                // The receiver can also fail without ever calling onMediaError — dropping to IDLE
                // with an ERROR reason is the terminal signal in that case.
                if (client.playerState == MediaStatus.PLAYER_STATE_IDLE && idleReason == MediaStatus.IDLE_REASON_ERROR) {
                    reportMediaFailure(null)
                }
            }

            override fun onMediaError(mediaError: MediaError) {
                Log.w(TAG) {
                    "media.onMediaError code=${mediaError.detailedErrorCode} reason=${mediaError.reason} " +
                        "type=${mediaError.type} media=${castingFormat ?: "unknown"} customData=${mediaError.customData}"
                }
                reportMediaFailure(mediaError.detailedErrorCode)
            }
        }

    @Volatile
    private var currentMediaClient: RemoteMediaClient? = null

    private fun attachMediaClientCallback(session: CastSession) {
        val client = session.remoteMediaClient ?: return
        if (currentMediaClient === client) return
        currentMediaClient?.unregisterCallback(mediaClientCallback)
        currentMediaClient = client
        client.registerCallback(mediaClientCallback)
        Log.d(TAG) { "media.callback attached" }
    }

    private fun detachMediaClientCallback() {
        currentMediaClient?.let {
            it.unregisterCallback(mediaClientCallback)
            Log.d(TAG) { "media.callback detached" }
        }
        currentMediaClient = null
    }

    /**
     * Cast surfaces failures as bare ints spread across several unrelated ranges (CommonStatusCodes,
     * CastStatusCodes, and internal codes documented nowhere). [CastStatusCodes.getStatusCodeString]
     * is the SDK's own lookup, so it decodes far more than the public constants do — keep the raw
     * number alongside it for the ones it doesn't recognise either.
     */
    private fun statusName(code: Int): String = "$code(${CastStatusCodes.getStatusCodeString(code)})"

    private fun playerStateName(state: Int): String =
        when (state) {
            MediaStatus.PLAYER_STATE_IDLE -> "IDLE"
            MediaStatus.PLAYER_STATE_PLAYING -> "PLAYING"
            MediaStatus.PLAYER_STATE_PAUSED -> "PAUSED"
            MediaStatus.PLAYER_STATE_BUFFERING -> "BUFFERING"
            MediaStatus.PLAYER_STATE_LOADING -> "LOADING"
            else -> "UNKNOWN($state)"
        }

    private fun idleReasonName(reason: Int): String =
        when (reason) {
            MediaStatus.IDLE_REASON_NONE -> "NONE"
            MediaStatus.IDLE_REASON_FINISHED -> "FINISHED"
            MediaStatus.IDLE_REASON_CANCELED -> "CANCELED"
            MediaStatus.IDLE_REASON_INTERRUPTED -> "INTERRUPTED"
            MediaStatus.IDLE_REASON_ERROR -> "ERROR"
            else -> "—"
        }

    private val sessionListener =
        object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) {
                Log.d(TAG) { "session.onStarting" }
            }

            override fun onSessionStarted(
                session: CastSession,
                sessionId: String,
            ) {
                Log.i(TAG) { "session.onStarted id=$sessionId connected=${session.isConnected} hasClient=${session.remoteMediaClient != null}" }
                attachMediaClientCallback(session)
                pendingSessionStart?.complete(true)
                pendingSessionStart = null
            }

            override fun onSessionStartFailed(
                session: CastSession,
                error: Int,
            ) {
                Log.w(TAG) { "session.onStartFailed error=${statusName(error)}" }
                cancelLoadWatchdog()
                pendingSessionStart?.complete(false)
                pendingSessionStart = null
                if (error == CastStatusCodes.CANCELED) {
                    // The user backed out of the connection; that is not a failure to report.
                    sessionFlow.value = CastSessionState.Idle
                    return
                }
                // The raw SDK integer belongs in the log, not in front of the user. Every code that
                // reaches here means the same thing to them — the device would not accept a
                // connection — and on a webOS TV whose Cast service has died (2252, seen repeatedly
                // once it wedges) restarting it is genuinely the fix.
                val device = currentDevice()
                sessionFlow.value =
                    CastSessionState.Error(device, CastErrorMessage(Res.string.cast_error_connect_failed))
            }

            override fun onSessionEnding(session: CastSession) {
                Log.d(TAG) { "session.onEnding" }
            }

            override fun onSessionEnded(
                session: CastSession,
                error: Int,
            ) {
                detachMediaClientCallback()
                // Whoever is tearing down gets told first, before any of the swap/failure handling
                // below decides to return early — stopCasting() is blocked on this.
                pendingSessionEnd?.complete(Unit)
                // Moving to a different receiver ends the outgoing session by design, and that
                // callback lands *after* cast() has installed the pending start for the incoming
                // one. Failing it here is what made every device-to-device switch report
                // "session start refused" and skip the load.
                if (expectingSessionSwapEnd) {
                    expectingSessionSwapEnd = false
                    Log.i(TAG) { "session.onEnded error=${statusName(error)} — expected teardown while switching receivers" }
                    return
                }
                Log.i(TAG) { "session.onEnded error=${statusName(error)}" }
                cancelLoadWatchdog()
                // If a cast() was awaiting a session start, this is also a terminal
                // outcome — the session never reached a usable state. Without
                // completing here the cast coroutine hangs and the discovery
                // ref-count leaks +1 for every failed attempt.
                pendingSessionStart?.complete(false)
                pendingSessionStart = null
                sessionFlow.value = CastSessionState.Idle
            }

            override fun onSessionResuming(
                session: CastSession,
                sessionId: String,
            ) {
                Log.d(TAG) { "session.onResuming id=$sessionId" }
            }

            override fun onSessionResumed(
                session: CastSession,
                wasSuspended: Boolean,
            ) {
                Log.d(TAG) { "session.onResumed wasSuspended=$wasSuspended" }
                // Resume counts as the session being usable — let cast() proceed.
                attachMediaClientCallback(session)
                pendingSessionStart?.complete(true)
                pendingSessionStart = null
            }

            override fun onSessionResumeFailed(
                session: CastSession,
                error: Int,
            ) {
                Log.w(TAG) { "session.onResumeFailed error=${statusName(error)}" }
                pendingSessionStart?.complete(false)
                pendingSessionStart = null
            }

            override fun onSessionSuspended(
                session: CastSession,
                reason: Int,
            ) {
                Log.d(TAG) { "session.onSuspended reason=$reason" }
                // Suspended sessions can't accept loads — fail any pending start so
                // the caller doesn't try to call remoteMediaClient.load() against
                // a broken session.
                pendingSessionStart?.complete(false)
                pendingSessionStart = null
            }
        }

    @Volatile
    private var pendingSessionStart: CompletableDeferred<Boolean>? = null

    /**
     * Armed while a [CastRoutePlan.SWAP_RECEIVER] is in flight — between asking MediaRouter to move
     * to a different receiver and the outgoing session's teardown callback. That teardown is the
     * expected consequence of the move, not the new attempt failing, and must not complete the
     * pending start. Cleared as soon as the attempt resolves, so a later genuine end still counts.
     */
    @Volatile
    private var expectingSessionSwapEnd = false

    /**
     * Fires when the receiver accepted a load and then went quiet — see [LOAD_PROGRESS_TIMEOUT_MS].
     * Nothing else covers this: the media callbacks only speak when the receiver does, and the
     * session is still perfectly connected, so without this the picker claims to be casting forever
     * while the device sits on its splash screen.
     */
    private val loadWatchdog =
        Runnable {
            val state = currentMediaClient?.playerState
            val progressed = state == MediaStatus.PLAYER_STATE_PLAYING || state == MediaStatus.PLAYER_STATE_BUFFERING
            if (progressed || sessionFlow.value is CastSessionState.Error) return@Runnable

            val device = watchedDevice ?: currentDevice() ?: castingDevice
            Log.w(TAG) {
                "load watchdog: still ${playerStateName(state ?: -1)} after ${LOAD_PROGRESS_TIMEOUT_MS}ms on ${device?.name}"
            }
            val format = castingFormat
            val message =
                if (format != null) {
                    Res.string.cast_error_receiver_not_responding_detail
                } else {
                    Res.string.cast_error_receiver_not_responding
                }
            sessionFlow.value = CastSessionState.Error(device, CastErrorMessage(message, format))
            watchedDevice = null
        }

    /** The device a load is currently being watched for, so the message can name it. */
    @Volatile
    private var watchedDevice: CastDevice? = null

    /** What the in-flight cast is, so a failure can say what the receiver refused. */
    @Volatile
    private var castingFormat: String? = null

    /**
     * Which device the in-flight cast targets. [currentDevice] reads it back off the session state,
     * which a teardown has usually already reset by the time a late failure arrives — leaving the
     * message to name "the cast device" instead of the TV the user was looking at.
     */
    @Volatile
    private var castingDevice: CastDevice? = null

    /** Set while [stopCasting] waits for the receiver app to actually go away. */
    @Volatile
    private var pendingSessionEnd: CompletableDeferred<Unit>? = null

    private fun armLoadWatchdog(device: CastDevice) {
        main.removeCallbacks(loadWatchdog)
        watchedDevice = device
        main.postDelayed(loadWatchdog, LOAD_PROGRESS_TIMEOUT_MS)
    }

    private fun cancelLoadWatchdog() {
        main.removeCallbacks(loadWatchdog)
        watchedDevice = null
    }

    /**
     * Turns a receiver-side playback failure into a [CastSessionState.Error] the picker can show.
     *
     * Without this the UI stays on [CastSessionState.Casting] — set the moment `load()` is
     * submitted — while the receiver has already given up, so a rejected video looks exactly like a
     * working one: the device sits on its splash screen and nothing ever explains why.
     *
     * The first report wins. A failure usually arrives twice (onMediaError, then IDLE/ERROR) and the
     * earlier one carries the detailed code, so it is the more specific of the two.
     */
    private fun reportMediaFailure(detailedErrorCode: Int?) {
        cancelLoadWatchdog()
        if (sessionFlow.value is CastSessionState.Error) return
        val device = currentDevice() ?: castingDevice
        val unsupported =
            detailedErrorCode == MediaError.DetailedErrorCode.MEDIA_SRC_NOT_SUPPORTED ||
                detailedErrorCode == MediaError.DetailedErrorCode.MEDIA_DECODE
        val format = castingFormat
        val message =
            when {
                unsupported && format != null -> Res.string.cast_error_unsupported_media_detail
                unsupported -> Res.string.cast_error_unsupported_media
                format != null -> Res.string.cast_error_playback_failed_detail
                else -> Res.string.cast_error_playback_failed
            }
        Log.w(TAG) { "media failure surfaced to UI: code=$detailedErrorCode device=${device?.name}" }
        sessionFlow.value = CastSessionState.Error(device, CastErrorMessage(message, format))
    }

    private fun currentDevice(): CastDevice? =
        when (val s = sessionFlow.value) {
            is CastSessionState.Connecting -> s.device
            is CastSessionState.Casting -> s.device
            else -> null
        }

    private fun ensureCastContext(): CastContext? {
        castContext?.let { return it }
        val gms = GoogleApiAvailability.getInstance()
        val status = gms.isGooglePlayServicesAvailable(appContext)
        if (status != ConnectionResult.SUCCESS) {
            Log.w(TAG) { "Google Play services unavailable (status=$status); Chromecast disabled." }
            return null
        }
        return try {
            // The blocking overload is the right choice here: we only call it on
            // the main thread, OptionsProvider is declared in the play manifest,
            // and the SDK caches the singleton after the first call.
            @Suppress("DEPRECATION")
            CastContext.getSharedInstance(appContext).also {
                castContext = it
                attachSessionListener(it)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize CastContext", t)
            null
        }
    }

    private fun attachSessionListener(ctx: CastContext) {
        if (sessionListenerAttached) return
        ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        sessionListenerAttached = true
        Log.d(TAG) { "sessionListener attached (caster lifetime)" }
    }

    fun startDiscovery() {
        Log.i(TAG) { "startDiscovery (already registered? $registered) ${localNetworkState()}" }
        main.post {
            if (registered) {
                Log.i(TAG) { "startDiscovery: already registered, no-op" }
                return@post
            }
            val ctx = ensureCastContext()
            if (ctx == null) {
                Log.w(TAG) { "startDiscovery: CastContext unavailable, aborting" }
                return@post
            }
            val router = MediaRouter.getInstance(appContext)
            val selector =
                MediaRouteSelector
                    .Builder()
                    .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                    .build()
            router.addCallback(selector, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
            mediaRouter = router
            routeSelector = selector
            registered = true
            Log.i(TAG) { "startDiscovery: registered router callback (sessionListener already attached)" }
            updateRoutes(router)
        }
    }

    /**
     * Android 17 (API 37) Local Network Protection gates the mDNS/multicast traffic the Cast SDK
     * uses for discovery behind [Manifest.permission.ACCESS_LOCAL_NETWORK]. When it is denied the
     * SDK reports no error at all — the picker simply stays empty forever — so the grant state is
     * the single most important thing a discovery log can tell us apart from the route count.
     */
    private fun localNetworkState(): String {
        if (Build.VERSION.SDK_INT < LOCAL_NETWORK_PROTECTION_SDK) return "lnp=n/a(sdk${Build.VERSION.SDK_INT})"
        val granted =
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
                PackageManager.PERMISSION_GRANTED
        return "lnp=sdk${Build.VERSION.SDK_INT} ACCESS_LOCAL_NETWORK=${if (granted) "GRANTED" else "DENIED"}"
    }

    fun stopDiscovery() {
        Log.d(TAG) { "stopDiscovery (registered=$registered)" }
        main.post {
            if (!registered) return@post
            mediaRouter?.removeCallback(routerCallback)
            // Intentionally NOT removing sessionListener: it must stay attached
            // for the lifetime of any in-flight cast session, otherwise the
            // session-end callbacks fire into the void.
            registered = false
            routes.clear()
            devicesFlow.value = emptyList()
            Log.d(TAG) { "stopDiscovery: router callback removed (sessionListener kept)" }
        }
    }

    private fun updateRoutes(router: MediaRouter) {
        val selector = routeSelector ?: return
        routes.clear()
        for (route in router.routes) {
            if (route.isDefault || route.isBluetooth) continue
            if (!route.matchesSelector(selector)) continue
            routes[route.id] = route
        }
        val list =
            routes.values.map { route ->
                CastDevice(
                    id = route.id,
                    name = route.name,
                )
            }
        // Log the unfiltered router total alongside the kept count: an empty picker with
        // seen=0 means discovery itself never saw anything (LNP / Wi-Fi / mDNS), whereas
        // seen>0 with count=0 means the routes exist but none advertises the Cast control
        // category — two completely different faults that look identical from the UI.
        Log.i(TAG) { "updateRoutes: seen=${router.routes.size} kept=${list.size} -> [${list.joinToString { it.name }}]" }
        devicesFlow.value = list
    }

    suspend fun cast(
        device: CastDevice,
        request: CastRequest,
    ) {
        Log.i(TAG) { "cast device=${device.name} url=${request.url}" }
        val ctx = withContext(Dispatchers.Main) { ensureCastContext() }
        if (ctx == null) {
            Log.w(TAG, "cast: CastContext unavailable")
            sessionFlow.value =
                CastSessionState.Error(device, CastErrorMessage(Res.string.cast_error_play_services_unavailable))
            return
        }
        val route =
            withContext(Dispatchers.Main) {
                routes[device.id] ?: mediaRouter?.routes?.firstOrNull { it.id == device.id }
            }
        if (route == null) {
            Log.w(TAG) { "cast: route ${device.id} not in current set; offline?" }
            sessionFlow.value = CastSessionState.Error(device, CastErrorMessage(Res.string.cast_error_device_offline))
            return
        }

        sessionFlow.value = CastSessionState.Connecting(device)

        val started =
            withContext(Dispatchers.Main) {
                val existing = ctx.sessionManager.currentCastSession
                Log.d(TAG) {
                    "cast: existing session connected=${existing?.isConnected} hasClient=${existing?.remoteMediaClient != null}"
                }
                val plan =
                    planRouteSelection(
                        targetRouteId = route.id,
                        selectedRouteId = mediaRouter?.selectedRoute?.id,
                        hasConnectedSession = existing?.isConnected == true,
                    )
                Log.i(TAG) { "cast: plan=$plan target=${route.id} selected=${mediaRouter?.selectedRoute?.id}" }

                val pending = CompletableDeferred<Boolean>()
                // If a previous cast() is still awaiting a callback, fail it
                // before swapping in our deferred — otherwise the earlier call
                // hangs to the 30s timeout.
                pendingSessionStart?.complete(false)
                pendingSessionStart = pending
                // Arm this before selectRoute, not after: the teardown callback for the outgoing
                // session can arrive on the very next main-thread tick.
                expectingSessionSwapEnd = plan.expectsPreviousSessionToEnd
                try {
                    if (plan.needsRouteSelection) {
                        Log.i(TAG) { "cast: selectRoute id=${route.id}" }
                        mediaRouter?.selectRoute(route)
                    } else {
                        Log.i(TAG) { "cast: reusing the session already connected to this receiver" }
                        pending.complete(true)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "cast: selectRoute threw", t)
                    expectingSessionSwapEnd = false
                    pending.complete(false)
                }
                // Defence in depth: if a callback is somehow missed (SDK bug,
                // process killed mid-flight, …) don't hang the coroutine forever.
                // The session listener already covers every documented terminal
                // state, so reaching the timeout means something genuinely went
                // wrong on the SDK side.
                val outcome = withTimeoutOrNull(SESSION_START_TIMEOUT_MS) { pending.await() }
                if (outcome == null) {
                    Log.w(TAG) { "cast: session start timed out after ${SESSION_START_TIMEOUT_MS}ms" }
                    pendingSessionStart = null
                }
                // However this attempt ended, the swap it was waiting on is over. Leaving the flag
                // armed would make the *next* genuine session end get swallowed as an expected one.
                expectingSessionSwapEnd = false
                outcome ?: false
            }

        if (!started) {
            Log.w(TAG, "cast: session start refused")
            // onSessionStartFailed usually got here first with a better-informed message; keep it.
            sessionFlow.value =
                sessionFlow.value as? CastSessionState.Error
                    ?: CastSessionState.Error(device, CastErrorMessage(Res.string.cast_error_connect_failed))
            return
        }

        val ok =
            withContext(Dispatchers.Main) {
                val session = ctx.sessionManager.currentCastSession
                val client = session?.remoteMediaClient
                Log.d(TAG) {
                    "cast: post-start session=${session != null} connected=${session?.isConnected} client=${client != null}"
                }
                if (client == null) {
                    Log.w(TAG, "cast: remoteMediaClient is null (session not fully ready); load skipped")
                    false
                } else {
                    try {
                        val loadRequest = buildLoadRequest(request)
                        val info = loadRequest.mediaInfo
                        castingFormat = request.formatSummary
                        castingDevice = device
                        Log.i(TAG) {
                            "cast: load() submitting contentType=${info?.contentType} " +
                                "streamType=${info?.streamType} media=${request.formatSummary ?: "unknown"} " +
                                "url=${info?.contentId}"
                        }
                        // load() returns a PendingResult carrying the receiver's verdict. Dropping it
                        // (as this used to) makes a refused load indistinguishable from a successful
                        // one: the coroutine reports Casting, the TV sits on its splash screen, and
                        // nothing anywhere records why. This callback is the only place the receiver
                        // ever tells us what it disliked about the media.
                        armLoadWatchdog(device)
                        client.load(loadRequest).setResultCallback { result ->
                            val status = result.status
                            if (status.isSuccess) {
                                Log.i(TAG) { "cast: load() accepted by receiver" }
                            } else {
                                Log.w(TAG) {
                                    "cast: load() REFUSED by receiver code=${statusName(status.statusCode)} " +
                                        "msg=${status.statusMessage}"
                                }
                                // CANCELED is what the receiver answers when the load was
                                // abandoned because we tore the session down — i.e. the user pressed
                                // stop. That is the outcome they asked for, not a failure to report.
                                if (status.statusCode != CastStatusCodes.CANCELED) {
                                    reportMediaFailure(null)
                                }
                            }
                        }
                        true
                    } catch (t: Throwable) {
                        cancelLoadWatchdog()
                        Log.w(TAG, "cast: remoteMediaClient.load failed", t)
                        false
                    }
                }
            }

        // A receiver can reject the media before this coroutine gets here — onMediaError has been
        // seen ~150ms after load(). This attempt set Connecting on entry, so any Error sitting here
        // now came from its own callbacks and carries the receiver's reason; don't paper over it
        // with a Casting state that claims a video is playing when it already failed.
        val reportedFailure = sessionFlow.value as? CastSessionState.Error
        sessionFlow.value =
            when {
                reportedFailure != null -> reportedFailure
                ok -> CastSessionState.Casting(device, request)
                else -> CastSessionState.Error(device, CastErrorMessage(Res.string.cast_error_load_failed))
            }
    }

    private fun buildLoadRequest(request: CastRequest): MediaLoadRequestData {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
        request.title?.let { metadata.putString(MediaMetadata.KEY_TITLE, it) }
        request.artworkUri?.let {
            runCatching { metadata.addImage(WebImage(it.toUri())) }
        }
        // A live HLS playlist has no #EXT-X-ENDLIST and no duration. Declaring it BUFFERED asks the
        // receiver for a seekable stream of known length, which it cannot resolve — the LG webOS
        // receiver sits in LOADING forever rather than reporting an error.
        val streamType =
            if (request.isLive) {
                MediaInfo.STREAM_TYPE_LIVE
            } else {
                MediaInfo.STREAM_TYPE_BUFFERED
            }
        val info =
            MediaInfo
                .Builder(request.url)
                .setStreamType(streamType)
                .setContentType(request.effectiveMimeType())
                .setMetadata(metadata)
                .build()
        return MediaLoadRequestData
            .Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            .build()
    }

    /**
     * Serialises teardown. A stop against an unresponsive receiver can take seconds to ack, so the
     * user reasonably presses stop again — and two overlapping teardowns wreck each other: both
     * call `stop()` (the second erroring), both call `endCurrentSession`, and the later one installs
     * its session-end wait after `onSessionEnded` has already fired, so it waits out the full
     * timeout for an event that will never come again.
     */
    private val stopInFlight = Mutex()

    suspend fun stopCasting() {
        if (!stopInFlight.tryLock()) {
            Log.i(TAG) { "stopCasting: a teardown is already running; ignoring the repeat request" }
            return
        }
        try {
            stopCastingLocked()
        } finally {
            stopInFlight.unlock()
        }
    }

    private suspend fun stopCastingLocked() {
        Log.i(TAG) { "stopCasting (hasClient=${currentMediaClient != null})" }
        withContext(Dispatchers.Main) { cancelLoadWatchdog() }
        // Await MEDIA_STOP before endCurrentSession() — racing them on the
        // same main-thread tick loses the stop on some receivers (LG webOS).
        val client = currentMediaClient
        if (client != null) {
            val stopAck = CompletableDeferred<Int>()
            withContext(Dispatchers.Main) {
                try {
                    client.stop().setResultCallback { result ->
                        val status = result.status
                        Log.i(TAG) {
                            "remoteMediaClient.stop ack code=${statusName(status.statusCode)} msg=${status.statusMessage}"
                        }
                        stopAck.complete(status.statusCode)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "remoteMediaClient.stop threw", t)
                    stopAck.complete(-1)
                }
            }
            val acked = withTimeoutOrNull(STOP_AWAIT_TIMEOUT_MS) { stopAck.await() }
            if (acked == null) {
                Log.w(TAG) { "remoteMediaClient.stop did not ack within ${STOP_AWAIT_TIMEOUT_MS}ms" }
            }
        }
        val ended = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
            pendingSessionEnd = ended
            try {
                // true: shut the receiver application down, don't just detach from it.
                //
                // This was false, to "keep the receiver running on its splash screen so the next
                // cast can reuse the connection cleanly". That is what strands an LG webOS TV on the
                // Default Media Receiver holding screen instead of returning it to its home screen,
                // and the reused connection is not clean: the SDK hands the same session id back to
                // later casts, and after a few stop/start cycles the receiver stops accepting
                // connections at all (every start fails 2252) until the TV is power-cycled.
                //
                // The race that motivated false is now handled directly — the MEDIA_STOP ack is
                // awaited above before we get here, and a receiver swap no longer mistakes the
                // outgoing session's teardown for a failure of the incoming one.
                Log.i(TAG) { "stopCasting: ending session and stopping the receiver app" }
                castContext?.sessionManager?.endCurrentSession(true)
            } catch (t: Throwable) {
                Log.w(TAG, "endCurrentSession failed", t)
                ended.complete(Unit)
            }
        }
        // Wait for the session to be gone before unselecting. Unselecting drops the connection the
        // STOP_APP message travels over, so doing it on the same tick — as this used to — can beat
        // the message to the TV and leave the receiver running on its splash screen.
        if (withTimeoutOrNull(SESSION_END_TIMEOUT_MS) { ended.await() } == null) {
            Log.w(TAG) { "stopCasting: session did not end within ${SESSION_END_TIMEOUT_MS}ms; unselecting anyway" }
        }
        withContext(Dispatchers.Main) {
            pendingSessionEnd = null
            mediaRouter?.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
        }
        sessionFlow.value = CastSessionState.Idle
    }
}
