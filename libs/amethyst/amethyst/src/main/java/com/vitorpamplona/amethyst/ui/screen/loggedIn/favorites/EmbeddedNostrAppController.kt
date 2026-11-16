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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.privacysandbox.ui.client.SandboxedUiAdapterFactory
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import androidx.privacysandbox.ui.client.view.SandboxedSdkViewEventListener
import androidx.privacysandbox.ui.core.SandboxedUiAdapter
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.ui.pill.ConsoleLine
import com.vitorpamplona.amethyst.commons.napplet.NappletDownloadProgress
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.favorite_notice_paid
import com.vitorpamplona.amethyst.commons.resources.favorite_notice_published
import com.vitorpamplona.amethyst.commons.resources.favorite_notice_uploaded
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.napplet.NappletLaunchRegistry
import com.vitorpamplona.amethyst.napplet.NappletWebViewProfiles
import com.vitorpamplona.amethyst.napplet.WebFileChooserCoordinator
import com.vitorpamplona.amethyst.napplethost.NappletEmbedContract
import com.vitorpamplona.amethyst.napplethost.NappletHostContract
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.ConsoleBridge
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.ConsoleBuffer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedAutoRecovery
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedImeBridge
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedLoadStatus
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedMagnifierProbe
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedSurfaceController
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabFactory
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.FindBridge
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.FindResult
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.ImeEvent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.MagnifierFrame
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.consoleLevelOf
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.parseImeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Client-side handle to an embedded nsite/napplet. Binds [NappletHostService][com.vitorpamplona.amethyst.napplethost.NappletHostService]
 * (in the keyless `:napplet` process), hands its `SandboxedUiAdapter` to a [SandboxedSdkView] so the
 * verified-blob WebView renders inside the main activity, and relays back/reload while receiving
 * navigation state + "allow always" notices that the trusted main-process chrome reflects.
 *
 * [params] is the bundle minted in the main process by
 * [NappletLauncher.buildLaunchParams][com.vitorpamplona.amethyst.napplet.NappletLauncher.buildLaunchParams]
 * — the verified manifest, identity, and launch token. The mirror of `EmbeddedWebAppController`.
 */
@RequiresApi(Build.VERSION_CODES.R)
class EmbeddedNostrAppController(
    private val appContext: Context,
    private val params: Bundle,
) : EmbeddedSurfaceController,
    EmbeddedImeBridge,
    EmbeddedMagnifierProbe,
    ConsoleBridge,
    FindBridge {
    private val incoming = Messenger(Handler(Looper.getMainLooper(), ::onServiceMessage))
    private var serviceMessenger: Messenger? = null
    private var bound = false

    private var sandboxedSdkView: SandboxedSdkView? = null

    /**
     * Set for an app running inside a note: it never opens the picker or the camera. A run in the feed
     * asks the user nothing (the broker refuses its prompts the same way); opening the app answers.
     */
    var refusesFileChooser: Boolean = false
    private var pendingAdapter: SandboxedUiAdapter? = null

    /**
     * True once this controller's adapter has actually been handed to a [SandboxedSdkView]. An adapter can
     * only ever serve ONE view: when that view is disposed, privacysandbox closes the remote session and the
     * sandbox destroys its WebView, so the adapter is dead. See [attachView].
     */
    private var adapterDelivered = false

    // A single NappletHostService instance serves every embedded napplet tab, so each controller stamps
    // its own id on every message; the provider uses it to route controls/state/IME to this tab.
    // Re-minted whenever the remote session is re-created (see [attachView]), so a late close() from the
    // previous view can never reap the replacement.
    private var sessionId: String = newSessionId()

    // What the provider was last told (see [syncPageState]). A parked tab can be hidden before the service
    // even binds, when the message is dropped (no messenger yet), so both are replayed right after each
    // session is created — otherwise an applet that was never shown comes up running, and acting, unwatched.
    private var wantPaused = false
    private var wantAttended = false

    // The app is on screen / has been in the background long enough to pause even the visible tab.
    private var appVisible = true
    private var backgroundIdle = false

    /** (canGoBack) — drives the in-tab back gesture. */
    var onStateChanged: ((Boolean) -> Unit)? = null

    private var hasLoadedReal = false

    // Brings the tab back when its sandbox-side surface dies (see [onSurfaceLost]).
    private val recovery = EmbeddedAutoRecovery(SystemClock::elapsedRealtime)

    // The remote session behind the current view errored out: only a brand-new session can repaint it.
    private var sessionDead = false

    // Set after the first connection, so a later onServiceConnected is recognised as `:napplet` coming back.
    private var everConnected = false

    // Set by [unbind]: nothing that arrives afterwards may act.
    private var tornDown = false

    // A `:napplet` restart found this tab hidden: its session is re-created when it is next shown.
    private var createOnShow = false

    // A create is in flight: the view's old session erroring out now is the one being replaced, not news.
    private var awaitingReady = false

    // The current session's surface has shown in the view at least once (see [retry]).
    private var uiDisplayed = false

    // Whether `:napplet` routes through Tor right now: another surface that needs Tor puts every page on it.
    private val routedOverTor = mutableStateOf(false)

    /** This nSite is set to the open web but goes through Tor anyway, because another open page needs Tor. */
    val isTorForced: Boolean get() = !params.getBoolean(NappletHostContract.EXTRA_USE_TOR, true) && routedOverTor.value

    /** Last known main-frame load state, so the tab layer renders the right overlay immediately. */
    override var loadStatus: EmbeddedLoadStatus = EmbeddedLoadStatus()
        private set

    /** Notified on the main thread whenever [loadStatus] changes. */
    override var onLoadStatusChanged: ((EmbeddedLoadStatus) -> Unit)? = null

    /** Notified on the main thread with how far the download the page waits on has come; null when it ended. */
    var onDownloadChanged: ((NappletDownloadProgress?) -> Unit)? = null

    override var onImeEvent: ((ImeEvent) -> Unit)? = null

    override var onMagnifierFrame: ((MagnifierFrame) -> Unit)? = null

    /** The app's console output, capped at [MAX_CONSOLE_LOGS] entries. */
    private val console = ConsoleBuffer(MAX_CONSOLE_LOGS)
    override val consoleLogs get() = console.lines
    override val consoleErrorCount get() = console.errorCount

    override fun clearConsoleLogs() = console.clear()

    // The user's text zoom. The provider forgets it whenever the session is re-created (a `:napplet`
    // restart, a rearm), so it is re-sent with every create.
    private var textZoom = BrowserChrome.DEFAULT_TEXT_ZOOM

    /** The user's text zoom as last set, for a screen coming back to this tab. */
    val currentTextZoom: Int get() = textZoom

    // Whether the app can go back, kept here rather than in the tab's screen (which leaves composition on
    // every bottom-bar switch), so coming back keeps Back working inside the app.
    var lastCanGoBack = false
        private set

    private val _findResult = mutableStateOf<FindResult?>(null)
    override val findResult: State<FindResult?> = _findResult

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                serviceMessenger = Messenger(service)
                if (everConnected) {
                    // `:napplet` died and was restarted. Re-creating the session IS the recovery (a fresh
                    // process has no session under any id), so nothing else is left pending; cover the
                    // surface until the new page paints. Only the visible tab rebuilds now: every warm tab
                    // reconnects at once, and rebuilding them all right after the OS reclaimed that memory
                    // would just push it back up. The rest re-create when next shown.
                    recovery.clearPending()
                    sessionDead = false
                    showRecovering()
                    everConnected = true
                    if (recovery.isShown) sendCreateSession() else createOnShow = true
                    return
                }
                everConnected = true
                sendCreateSession()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // `:napplet` died (the OS reclaimed it, or it crashed). Its WebViews went with it; the
                // system restarts the bound service and [onServiceConnected] re-creates the session.
                serviceMessenger = null
                showRecovering()
            }
        }

    fun bind() {
        val intent = Intent().setClassName(appContext, NappletEmbedContract.SERVICE_CLASS)
        bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        // Tell the provider to drop this tab's session now: one created for a view that was disposed before
        // it attached never gets the surface close that would otherwise clean it up.
        send(NappletEmbedContract.MSG_CLOSE_SESSION)
        tornDown = true
        if (bound) {
            runCatching { appContext.unbindService(connection) }
            bound = false
        }
        // This controller's launch token dies with it (every session it re-creates reuses the token, so it
        // can't be given back any earlier). Left registered, dead tokens crowd live ones out of the registry.
        NappletLaunchRegistry.unregister(params.getString(NappletHostContract.EXTRA_LAUNCH_TOKEN))
        // Drop refs so an evicted controller doesn't pin the surface view or the remote messenger.
        serviceMessenger = null
        sandboxedSdkView?.setEventListener(null)
        sandboxedSdkView = null
        pendingAdapter = null
        adapterDelivered = false
        onStateChanged = null
        onImeEvent = null
        onMagnifierFrame = null
        onLoadStatusChanged = null
        onDownloadChanged = null
    }

    /**
     * Hands the surface view to the controller; applies the adapter if it already arrived, and re-arms the
     * remote session when this controller is being re-used by a *second* view.
     *
     * A warm controller outlives the composition (it lives in the process-scoped
     * [com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabHost]), but its [SandboxedSdkView]
     * does not: an account switch rebuilds the whole logged-in subtree, disposing every surface. That
     * disposal makes privacysandbox close the remote session and the sandbox destroy its WebView, so the
     * adapter already handed out is dead and cannot serve the fresh view. A [SandboxedSdkView] with no
     * adapter never builds a ContentView/SurfaceView and paints nothing but its background colour, forever.
     *
     * So when a new view attaches after the adapter was already delivered, ask the sandbox for a brand new
     * session; the reply arms this view. [sendCreateSession] re-stamps the CURRENT account's storage
     * profile, so re-arming can never resurrect the previous account's jar.
     */
    override fun attachView(view: SandboxedSdkView) {
        sandboxedSdkView = view
        // Paint the surface placeholder in the app's theme background so there's no white flash before
        // the remote WebView delivers its first frame.
        view.setBackgroundColor(params.getInt(NappletHostContract.EXTRA_BG_COLOR, android.graphics.Color.WHITE))
        view.setEventListener(surfaceListener(view))
        val adapter = pendingAdapter
        when {
            adapter != null -> {
                pendingAdapter = null
                adapterDelivered = true
                view.setAdapter(adapter)
            }
            // No adapter in hand and one was already spent on a previous (now disposed) view: the session
            // behind it is gone, so this view would stay blank forever. Re-create it.
            adapterDelivered -> rearmSession()
            // else: the first session is still in flight; MSG_SESSION_READY will arm this view.
        }
    }

    override fun detachView(view: SandboxedSdkView) {
        if (sandboxedSdkView !== view) return
        view.setEventListener(null)
        sandboxedSdkView = null
    }

    /**
     * Asks the sandbox for a brand-new session; the [NappletEmbedContract.MSG_SESSION_READY] reply arms the
     * current view with its adapter.
     *
     * Mints a FRESH session id: a disposed view's Session.close() reaches the sandbox asynchronously and can
     * land AFTER the create. Reusing the id would let that late close reap the session we just asked for,
     * leaving the surface black.
     */
    private fun rearmSession() {
        // The session being replaced may never have opened a surface (its view went away first), in which
        // case no surface close will ever reach the provider for it.
        send(NappletEmbedContract.MSG_CLOSE_SESSION)
        sessionId = newSessionId()
        // This create IS the re-creation a `:napplet` restart deferred to the next show.
        createOnShow = false
        adapterDelivered = false
        sessionDead = false
        _findResult.value = null
        sendCreateSession()
    }

    /**
     * Watches [view]'s remote session. A session that errors out (its provider failed, or `:napplet` died)
     * leaves the view holding a dead client that never reopens: it paints nothing, forever, until it is
     * handed a NEW adapter.
     */
    private fun surfaceListener(view: SandboxedSdkView) =
        object : SandboxedSdkViewEventListener {
            override fun onUiDisplayed() {
                // The load state reports when the page itself paints; this only says the surface opened.
                if (sandboxedSdkView === view) uiDisplayed = true
            }

            override fun onUiError(error: Throwable) {
                // A view this controller has since moved past (disposed, replaced) is not ours to revive, and an
                // error landing while a new session is on its way is the old one dying: that create already
                // is the rebuild.
                if (sandboxedSdkView === view && !awaitingReady) onSurfaceLost(sessionDead = true)
            }

            override fun onUiClosed() {
                // Nothing to do: closes are ours (a view disposed, or an adapter replaced on purpose).
            }
        }

    /**
     * The tab's page is gone: its WebView's renderer died ([sessionDead] false — the session lives on, and a
     * MSG_RELOAD rebuilds the WebView inside it), or the whole remote session errored out ([sessionDead]
     * true — only a new session can repaint the view). Either way the surface is a black rectangle that would
     * stay that way, so rebuild it, as [EmbeddedAutoRecovery] allows.
     */
    private fun onSurfaceLost(sessionDead: Boolean) {
        if (sessionDead) this.sessionDead = true
        hasLoadedReal = false
        // `:napplet` itself is down: its restart re-creates the session (see [onServiceConnected]).
        if (serviceMessenger?.binder?.isBinderAlive != true) {
            showRecovering()
            return
        }
        when (recovery.onLost()) {
            EmbeddedAutoRecovery.Decision.RECOVER_NOW -> recover()
            EmbeddedAutoRecovery.Decision.DEFERRED -> showRecovering()
            EmbeddedAutoRecovery.Decision.GIVE_UP -> publishLoadStatus(EmbeddedLoadStatus(failed = true))
        }
    }

    private fun recover() {
        showRecovering()
        if (sessionDead) rearmSession() else reload()
    }

    /** Covers the surface with the loading spinner until the rebuilt page paints. */
    private fun showRecovering() {
        hasLoadedReal = false
        publishLoadStatus(EmbeddedLoadStatus(isLoading = true))
    }

    override fun onShown() {
        val deferredRecovery = recovery.onShown()
        syncPageState()
        if (createOnShow) {
            createOnShow = false
            sendCreateSession()
        } else if (deferredRecovery) {
            recover()
        }
    }

    override fun onHidden() {
        recovery.onHidden()
        syncPageState()
    }

    override fun onAppVisibility(visible: Boolean) {
        appVisible = visible
        syncPageState()
    }

    override fun onBackgroundIdle(idle: Boolean) {
        backgroundIdle = idle
        syncPageState()
    }

    /**
     * Tells the provider what the applet may do now — the same schedule as a website tab:
     * - paused (JS-driven animations, media, geolocation) while parked off-screen, or once the app has sat in
     *   the background as long as the relays get (EmbeddedTabHost.BACKGROUND_PAUSE_MS), so a quick trip to
     *   another app doesn't interrupt it;
     * - attended only while it's the visible tab AND the app is on screen. The provider holds its requests
     *   that act for the user (publish, pay, upload…) while it isn't — at once, not after the grace: even an
     *   "allow always" napplet can't act on the user's behalf while they aren't looking.
     */
    private fun syncPageState() {
        val pause = !recovery.isShown || backgroundIdle
        if (pause != wantPaused) {
            wantPaused = pause
            send(if (pause) NappletEmbedContract.MSG_PAUSE else NappletEmbedContract.MSG_RESUME)
        }
        val attended = recovery.isShown && appVisible
        if (attended != wantAttended) {
            wantAttended = attended
            send(NappletEmbedContract.MSG_SET_ATTENDED) { putBoolean(NappletEmbedContract.KEY_ATTENDED, attended) }
        }
    }

    override fun teardown() = unbind()

    private fun sendCreateSession() {
        awaitingReady = true
        uiDisplayed = false
        val msg =
            Message.obtain(null, NappletEmbedContract.MSG_CREATE_SESSION).apply {
                replyTo = incoming
                data =
                    Bundle(params).apply {
                        putString(NappletEmbedContract.KEY_SESSION_ID, sessionId)
                        // Re-stamp the storage partition at SEND time rather than trusting the one baked
                        // into [params] at construction: a session re-created for a new view (see
                        // [attachView]) must land in the CURRENT account's jar, never the one this
                        // controller was originally built for.
                        putString(NappletHostContract.EXTRA_WEBVIEW_PROFILE, NappletWebViewProfiles.current())
                        // Likewise Tor's port: it may have come up (or moved) since [params] were minted.
                        putInt(NappletHostContract.EXTRA_PROXY_PORT, EmbeddedTabFactory.currentTorPort())
                    }
            }
        runCatching { serviceMessenger?.send(msg) }
        // Replay a pause / the attended state decided before we had a messenger to send it on
        // (parked-before-bound), so a never-shown applet doesn't start running or acting. Messenger preserves
        // order, so these land after CREATE in the host.
        if (wantPaused) send(NappletEmbedContract.MSG_PAUSE)
        // Always: a new session starts unattended, so a tab created in view must say it is being watched.
        send(NappletEmbedContract.MSG_SET_ATTENDED) { putBoolean(NappletEmbedContract.KEY_ATTENDED, wantAttended) }
        if (textZoom != BrowserChrome.DEFAULT_TEXT_ZOOM) setTextZoom(textZoom)
    }

    private fun onServiceMessage(msg: Message): Boolean {
        // Nothing may act on a torn-down tab (a late file-chooser request would still open a picker), nor on
        // what a session this controller has since replaced still had in flight — a stale SESSION_READY
        // would re-arm the view with that dead session's adapter.
        if (tornDown) return true
        val from = msg.data?.getString(NappletEmbedContract.KEY_SESSION_ID)
        if (from != null && from != sessionId) return true
        when (msg.what) {
            NappletEmbedContract.MSG_SESSION_READY -> {
                val coreLibInfo = msg.data?.getBundle(NappletEmbedContract.KEY_CORE_LIB_INFO) ?: return true
                awaitingReady = false
                val adapter = SandboxedUiAdapterFactory.createFromCoreLibInfo(coreLibInfo)
                val view = sandboxedSdkView
                if (view != null) {
                    adapterDelivered = true
                    view.setAdapter(adapter)
                } else {
                    pendingAdapter = adapter
                }
            }
            NappletEmbedContract.MSG_STATE -> {
                val canGoBack = msg.data?.getBoolean(NappletEmbedContract.KEY_CAN_GO_BACK, false) ?: false
                lastCanGoBack = canGoBack
                onStateChanged?.invoke(canGoBack)
            }
            NappletEmbedContract.MSG_NOTICE -> {
                val notice = msg.data?.getString(NappletEmbedContract.KEY_NOTICE) ?: return true
                showNotice(notice)
            }
            NappletEmbedContract.MSG_IME_EVENT -> {
                val payload = msg.data?.getString(NappletEmbedContract.KEY_IME_PAYLOAD) ?: return true
                parseImeEvent(payload)?.let { event -> onImeEvent?.invoke(event) }
            }
            NappletEmbedContract.MSG_LOAD_STATE -> {
                val isLoading = msg.data?.getBoolean(NappletEmbedContract.KEY_IS_LOADING, false) ?: false
                val failed = msg.data?.getBoolean(NappletEmbedContract.KEY_LOAD_FAILED, false) ?: false
                if (msg.data?.getBoolean(NappletEmbedContract.KEY_RENDERER_GONE, false) == true) {
                    onSurfaceLost(sessionDead = false)
                } else {
                    onLoadState(isLoading, failed)
                }
            }
            NappletEmbedContract.MSG_FILE_CHOOSER_REQUEST -> {
                val data = msg.data ?: return true
                val requestId = data.getLong(NappletEmbedContract.KEY_FILE_CHOOSER_ID)
                if (refusesFileChooser) {
                    send(NappletEmbedContract.MSG_FILE_CHOOSER_RESULT) { putLong(NappletEmbedContract.KEY_FILE_CHOOSER_ID, requestId) }
                    return true
                }
                // The sandbox has no Activity to run a picker from, so the main process runs it here and
                // ships the URIs back. The reply is sent on every outcome (a cancel included) — the page's
                // file input stays busy until it hears something.
                WebFileChooserCoordinator.request(
                    context = appContext,
                    acceptTypes = data.getStringArray(NappletEmbedContract.KEY_FILE_CHOOSER_ACCEPT)?.toList().orEmpty(),
                    allowMultiple = data.getBoolean(NappletEmbedContract.KEY_FILE_CHOOSER_MULTIPLE, false),
                    captureEnabled = data.getBoolean(NappletEmbedContract.KEY_FILE_CHOOSER_CAPTURE, false),
                    pageTitle = data.getString(NappletEmbedContract.KEY_FILE_CHOOSER_TITLE),
                ) { uris ->
                    send(NappletEmbedContract.MSG_FILE_CHOOSER_RESULT) {
                        putLong(NappletEmbedContract.KEY_FILE_CHOOSER_ID, requestId)
                        uris?.let { putStringArray(NappletEmbedContract.KEY_FILE_CHOOSER_URIS, it) }
                    }
                }
            }
            NappletEmbedContract.MSG_ROUTE -> routedOverTor.value = msg.data?.getBoolean(NappletEmbedContract.KEY_ROUTE_TOR, false) ?: false
            NappletEmbedContract.MSG_DOWNLOAD -> {
                val data = msg.data
                val download =
                    if (data != null && data.containsKey(NappletEmbedContract.KEY_DOWNLOAD_BYTES)) {
                        NappletDownloadProgress(
                            data.getLong(NappletEmbedContract.KEY_DOWNLOAD_BYTES),
                            data.getLong(NappletEmbedContract.KEY_DOWNLOAD_TOTAL, -1).takeIf { it > 0 },
                        )
                    } else {
                        null
                    }
                onDownloadChanged?.invoke(download)
            }
            NappletEmbedContract.MSG_FIND_RESULT -> {
                val data = msg.data ?: return true
                _findResult.value = FindResult(data.getInt(NappletEmbedContract.KEY_FIND_ACTIVE), data.getInt(NappletEmbedContract.KEY_FIND_TOTAL))
            }
            NappletEmbedContract.MSG_CONSOLE_LOG -> {
                val data = msg.data ?: return true
                console.add(
                    ConsoleLine(
                        consoleLevelOf(data.getString(NappletEmbedContract.KEY_CONSOLE_LEVEL).orEmpty()),
                        data.getString(NappletEmbedContract.KEY_CONSOLE_MESSAGE).orEmpty(),
                        data.getString(NappletEmbedContract.KEY_CONSOLE_SOURCE).orEmpty(),
                        data.getInt(NappletEmbedContract.KEY_CONSOLE_LINE, 0),
                    ),
                )
            }
            NappletEmbedContract.MSG_MAGNIFIER_FRAME -> {
                val data = msg.data ?: return true
                val bytes = data.getByteArray(NappletEmbedContract.KEY_MAG_BYTES) ?: return true
                onMagnifierFrame?.invoke(
                    MagnifierFrame(
                        bytes = bytes,
                        width = data.getInt(NappletEmbedContract.KEY_MAG_W),
                        height = data.getInt(NappletEmbedContract.KEY_MAG_H),
                        captureMs = data.getDouble(NappletEmbedContract.KEY_MAG_CAPTURE_MS),
                        requestStampNanos = data.getLong(NappletEmbedContract.KEY_MAG_REQ_T),
                    ),
                )
            }
            else -> return false
        }
        return true
    }

    override fun sendImeOp(json: String) = send(NappletEmbedContract.MSG_IME_OP) { putString(NappletEmbedContract.KEY_IME_PAYLOAD, json) }

    override fun requestMagnifier(
        surfaceX: Float,
        surfaceY: Float,
        boxWidthPx: Int,
        boxHeightPx: Int,
        zoom: Float,
    ) = send(NappletEmbedContract.MSG_MAGNIFIER_REQUEST) {
        putFloat(NappletEmbedContract.KEY_MAG_X, surfaceX)
        putFloat(NappletEmbedContract.KEY_MAG_Y, surfaceY)
        putInt(NappletEmbedContract.KEY_MAG_BOX_W, boxWidthPx)
        putInt(NappletEmbedContract.KEY_MAG_BOX_H, boxHeightPx)
        putFloat(NappletEmbedContract.KEY_MAG_ZOOM, zoom)
        putLong(NappletEmbedContract.KEY_MAG_REQ_T, SystemClock.elapsedRealtimeNanos())
    }

    fun back() = send(NappletEmbedContract.MSG_BACK)

    fun reload() = send(NappletEmbedContract.MSG_RELOAD) { putInt(NappletHostContract.EXTRA_PROXY_PORT, EmbeddedTabFactory.currentTorPort()) }

    override fun find(query: String) {
        if (query.isEmpty()) _findResult.value = null
        send(NappletEmbedContract.MSG_FIND) { putString(NappletEmbedContract.KEY_FIND_QUERY, query) }
    }

    override fun findNext(forward: Boolean) = send(NappletEmbedContract.MSG_FIND_NEXT) { putBoolean(NappletEmbedContract.KEY_FIND_FORWARD, forward) }

    fun setTextZoom(percent: Int) {
        textZoom = percent
        send(NappletEmbedContract.MSG_SET_TEXT_ZOOM) { putInt(NappletEmbedContract.KEY_TEXT_ZOOM, percent) }
    }

    /** User-triggered recovery for a stuck or failed session: reload the verified content from scratch. */
    override fun retry() {
        recovery.clearPending()
        showRecovering()
        // A surface that never opened has nothing to reload: only a new session can paint it.
        if (sessionDead || (sandboxedSdkView != null && !uiDisplayed)) rearmSession() else reload()
    }

    private fun onLoadState(
        isLoading: Boolean,
        failed: Boolean,
    ) {
        if (!isLoading && !failed) hasLoadedReal = true
        publishLoadStatus(EmbeddedLoadStatus(isLoading = isLoading, failed = failed, hasLoadedReal = hasLoadedReal))
    }

    private fun publishLoadStatus(status: EmbeddedLoadStatus) {
        loadStatus = status
        onLoadStatusChanged?.invoke(status)
    }

    /**
     * A granted "allow always" sensitive op just ran (one of NappletEmbedContract.NOTICE_*): tell the user.
     * Shown from here, on the app's own scope, rather than by the tab's screen: the op can complete after
     * the user has left the tab, when that screen — and the coroutine scope it would have toasted from — is
     * already gone, and the notice was silently dropped.
     */
    private fun showNotice(notice: String) {
        val res =
            when (notice) {
                NappletEmbedContract.NOTICE_PUBLISHED -> Res.string.favorite_notice_published
                NappletEmbedContract.NOTICE_UPLOADED -> Res.string.favorite_notice_uploaded
                NappletEmbedContract.NOTICE_PAID -> Res.string.favorite_notice_paid
                else -> return
            }
        Amethyst.instance.applicationIOScope.launch {
            val text = loadStringRes(res)
            withContext(Dispatchers.Main) { Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show() }
        }
    }

    private inline fun send(
        what: Int,
        crossinline block: Bundle.() -> Unit = {},
    ) {
        val msg =
            Message.obtain(null, what).apply {
                data =
                    Bundle().apply {
                        putString(NappletEmbedContract.KEY_SESSION_ID, sessionId)
                        block()
                    }
            }
        runCatching { serviceMessenger?.send(msg) }
    }

    private companion object {
        private val SESSION_SEQ = AtomicLong()

        // The provider outlives this process's restarts (and this counter with them): without a per-process
        // nonce a fresh main process would hand out ids a still-running `:napplet` already holds.
        private val PROCESS_NONCE = UUID.randomUUID().toString().take(8)

        private fun newSessionId() = "napplet-$PROCESS_NONCE-${SESSION_SEQ.incrementAndGet()}"

        private const val MAX_CONSOLE_LOGS = 200
    }
}
