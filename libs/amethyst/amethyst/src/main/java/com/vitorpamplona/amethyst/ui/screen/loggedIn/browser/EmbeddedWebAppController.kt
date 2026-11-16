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

package com.vitorpamplona.amethyst.ui.screen.loggedIn.browser

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
import androidx.annotation.RequiresApi
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.privacysandbox.ui.client.SandboxedUiAdapterFactory
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import androidx.privacysandbox.ui.client.view.SandboxedSdkViewEventListener
import androidx.privacysandbox.ui.core.SandboxedUiAdapter
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.browser.ui.pill.CertificateInfo
import com.vitorpamplona.amethyst.commons.browser.ui.pill.ConsoleLine
import com.vitorpamplona.amethyst.commons.browser.ui.pill.PageDialogType
import com.vitorpamplona.amethyst.napplet.NappletWebViewProfiles
import com.vitorpamplona.amethyst.napplet.WebFileChooserCoordinator
import com.vitorpamplona.amethyst.napplethost.NappletBrowserContract
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.ConsoleBridge
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.ConsoleBuffer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedAutoRecovery
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedImeBridge
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedLoadStatus
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedMagnifierProbe
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedSurfaceController
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.FindBridge
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.FindResult
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.ImeEvent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.MagnifierFrame
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.consoleLevelOf
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.parseImeEvent
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Client-side handle to the embedded browser. Binds [NappletBrowserService] (in the keyless `:napplet`
 * process), hands its `SandboxedUiAdapter` to a [SandboxedSdkView] so the remote WebView renders inside
 * the main activity, and relays chrome controls (navigate/reload/back/Tor) while receiving URL updates
 * that drive the trusted, main-process address bar.
 */
@RequiresApi(Build.VERSION_CODES.R)
class EmbeddedWebAppController(
    private val appContext: Context,
    // Read on every create and load rather than once: Tor may still be starting when the tab is made, and a
    // Tor page loads nothing (fails closed) until its port is known.
    private val proxyPort: () -> Int,
    private val initialUseTor: Boolean,
    private val backgroundColor: Int,
    private val themeType: String = "SYSTEM",
    // Read on every create, so a session made after the user changes engines picks the new one up.
    private val searchEngine: () -> String? = { null },
) : EmbeddedSurfaceController,
    EmbeddedImeBridge,
    EmbeddedMagnifierProbe,
    ConsoleBridge,
    FindBridge {
    private val incoming = Messenger(Handler(Looper.getMainLooper(), ::onServiceMessage))
    private var serviceMessenger: Messenger? = null
    private var bound = false

    private var sandboxedSdkView: SandboxedSdkView? = null
    private var pendingAdapter: SandboxedUiAdapter? = null

    /**
     * True once this controller's adapter has actually been handed to a [SandboxedSdkView]. An adapter can
     * only ever serve ONE view: when that view is disposed, privacysandbox closes the remote session and the
     * sandbox destroys its WebView, so the adapter is dead. See [attachView] for why this matters.
     */
    private var adapterDelivered = false
    private var startUrl: String = BLANK_PAGE

    private var hasLoadedReal = false
    private var blankRecovered = false

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

    // What the provider was last told (see [syncPageState]). Remembered so both are replayed right after each
    // session is created: a parked tab can be hidden before the service even binds.
    private var wantPaused = false
    private var wantAttended = false

    // The app is on screen / has been in the background long enough to pause even the visible tab.
    private var appVisible = true
    private var backgroundIdle = false

    /** Last known main-frame load state, so the tab layer renders the right overlay immediately. */
    override var loadStatus: EmbeddedLoadStatus = EmbeddedLoadStatus()
        private set

    /** Notified on the main thread whenever [loadStatus] changes. */
    override var onLoadStatusChanged: ((EmbeddedLoadStatus) -> Unit)? = null

    /** JavaScript console output received from the embedded WebView, capped at [MAX_CONSOLE_LOGS] entries. */
    private val console = ConsoleBuffer(MAX_CONSOLE_LOGS)
    override val consoleLogs get() = console.lines
    override val consoleErrorCount get() = console.errorCount

    override fun clearConsoleLogs() = console.clear()

    // The user's per-tab page settings. The provider forgets them whenever the session is re-created (a
    // `:napplet` restart, a rearm), so they are re-sent with every create — otherwise a site the user
    // switched onto Tor would silently come back over clearnet while the pill still said Tor.
    private var useTor = initialUseTor
    private var textZoom = BrowserChrome.DEFAULT_TEXT_ZOOM
    private var desktopSite = false

    // The page on screen, kept here rather than in the tab's screen: the screen leaves composition whenever
    // the user switches bottom-bar tabs, and coming back must show where they were (the right address for
    // share / favorite / site settings, and a Back that goes back in the page instead of leaving the tab).
    var lastUrl: String? = null
        private set
    var lastTitle: String? = null
        private set
    var lastCanGoBack = false
        private set
    var lastCanGoForward = false
        private set

    /** A tab's page and per-tab settings, carried to the controller that replaces this one on a rebuild. */
    class PageSnapshot(
        val url: String?,
        val useTor: Boolean,
        val textZoom: Int,
        val desktopSite: Boolean,
    )

    fun snapshot() = PageSnapshot(lastUrl, useTor, textZoom, desktopSite)

    /** Takes over a torn-down predecessor's page and settings; call before [bind], which creates the session. */
    fun restore(snapshot: PageSnapshot) {
        lastUrl = snapshot.url
        useTor = snapshot.useTor
        textZoom = snapshot.textZoom
        desktopSite = snapshot.desktopSite
    }

    /** The user's per-tab settings as last set, for a screen coming back to this tab. */
    val isTorOn: Boolean get() = useTor

    // Whether `:napplet` routes through Tor right now: another surface that needs Tor puts every page on it.
    private val routedOverTor = mutableStateOf(false)

    /** This page is set to the open web but goes through Tor anyway, because another open page needs Tor. */
    val isTorForced: Boolean get() = !useTor && routedOverTor.value
    val isDesktopSite: Boolean get() = desktopSite
    val currentTextZoom: Int get() = textZoom

    // A single NappletBrowserService instance serves every embedded browser tab, so each controller
    // stamps its own id on every message; the provider uses it to route controls/updates to this tab.
    // Re-minted whenever the remote session is re-created (see [attachView]), so a late close() from the
    // previous view can never reap the replacement.
    private var sessionId: String = newSessionId()

    /** Invoked on the main thread when the page navigates or retitles: (url, title or null, canGoBack, canGoForward). */
    var onUrlChanged: ((String, String?, Boolean, Boolean) -> Unit)? = null

    // ---- page-initiated UI, drawn by the main process (the provider has no window) ----

    private val _findResult = mutableStateOf<FindResult?>(null)
    override val findResult: State<FindResult?> = _findResult

    /** The JS dialog the page is waiting on, if any. */
    val pendingDialog = mutableStateOf<EmbeddedJsDialog?>(null)

    /** The camera / microphone / location request the page is waiting on, if any. */
    val pendingPermission = mutableStateOf<EmbeddedPermissionRequest?>(null)

    /** The download the page started that awaits the user's consent, if any. */
    val pendingDownload = mutableStateOf<EmbeddedDownloadRequest?>(null)

    /** The certificate of the page on screen, once page info asked for it (null for none, or not yet). */
    val pageCertificate = mutableStateOf<CertificateInfo?>(null)

    /** A main-frame load is in flight (the pill's reload button becomes stop). */
    val isLoading = mutableStateOf(false)

    /** The page is showing HTML fullscreen (a video) inside the surface. */
    val isFullscreen = mutableStateOf(false)

    override var onImeEvent: ((ImeEvent) -> Unit)? = null

    // SPIKE (magnifier #4, option B): provider-side capture round-trip. Removed once the loupe lands.
    override var onMagnifierFrame: ((MagnifierFrame) -> Unit)? = null

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
                resetPageState()
                showRecovering()
            }
        }

    fun bind(startUrl: String) {
        this.startUrl = startUrl
        val intent = Intent().setClassName(appContext, NappletBrowserContract.BROWSER_SERVICE_CLASS)
        bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        // Tell the provider to drop this tab's session now: one created for a view that was disposed before
        // it attached never gets the surface close that would otherwise clean it up.
        send(NappletBrowserContract.MSG_CLOSE_SESSION) {}
        tornDown = true
        if (bound) {
            runCatching { appContext.unbindService(connection) }
            bound = false
        }
        // Drop refs so an evicted controller doesn't pin the surface view or the remote messenger.
        serviceMessenger = null
        sandboxedSdkView?.setEventListener(null)
        sandboxedSdkView = null
        pendingAdapter = null
        adapterDelivered = false
        onUrlChanged = null
        onImeEvent = null
        onMagnifierFrame = null
        onLoadStatusChanged = null
        console.clear()
        resetPageState()
    }

    /**
     * Drops UI state that belongs to the page on screen: once that page is gone (renderer death, session
     * lost, `:napplet` restart) nothing will ever close it. A stale fullscreen flag swallowed every Back
     * press, and a stale dialog or permission prompt auto-refused every new one from the rebuilt page.
     */
    private fun resetPageState() {
        pendingDialog.value = null
        pendingPermission.value = null
        pendingDownload.value = null
        isFullscreen.value = false
        _findResult.value = null
    }

    override fun teardown() = unbind()

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
     * Tells the provider what the page may do now:
     * - paused while parked off-screen, or once the app has sat in the background as long as the relays get
     *   (EmbeddedTabHost.BACKGROUND_PAUSE_MS) — no animations, media or geolocation keep running;
     * - attended only while it's the visible tab AND the app is on screen. The provider holds the page's
     *   NIP-07 sign / encrypt / decrypt while it isn't, so a parked or backgrounded site can't sign (even with
     *   "allow always") while nobody is looking. That one applies at once: it's about who is watching, not
     *   about saving work.
     */
    private fun syncPageState() {
        val pause = !recovery.isShown || backgroundIdle
        if (pause != wantPaused) {
            wantPaused = pause
            send(if (pause) NappletBrowserContract.MSG_PAUSE else NappletBrowserContract.MSG_RESUME) {}
        }
        val attended = recovery.isShown && appVisible
        if (attended != wantAttended) {
            wantAttended = attended
            send(NappletBrowserContract.MSG_SET_ATTENDED) { putBoolean(NappletBrowserContract.KEY_ENABLED, attended) }
        }
    }

    /**
     * Hands the surface view to the controller; applies the adapter if it already arrived, and re-arms the
     * remote session when this controller is being re-used by a *second* view.
     *
     * A warm controller outlives the composition (it lives in the process-scoped [EmbeddedTabHost]), but its
     * [SandboxedSdkView] does not: an account switch rebuilds the whole logged-in subtree, disposing every
     * surface. That disposal makes privacysandbox close the remote session, which destroys the sandbox's
     * WebView — so the adapter this controller already handed out is dead and cannot be given to the fresh
     * view. A [SandboxedSdkView] with no adapter never builds a ContentView/SurfaceView and paints nothing
     * but its background colour, forever (the load overlay's retry can't help — it re-navigates a WebView
     * that no longer exists).
     *
     * So when a new view attaches after the adapter was already delivered, ask the sandbox for a brand new
     * session; the [NappletBrowserContract.MSG_SESSION_READY] reply arms this view. The sandbox stamps the
     * CURRENT account's storage profile on that new session (see [sendCreateSession]), so re-arming can
     * never resurrect the previous account's cookie jar.
     */
    override fun attachView(view: SandboxedSdkView) {
        sandboxedSdkView = view
        // Paint the surface placeholder in the app's theme background so there's no white flash before
        // the remote WebView delivers its first frame.
        view.setBackgroundColor(backgroundColor)
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
     * Asks the sandbox for a brand-new session; the [NappletBrowserContract.MSG_SESSION_READY] reply arms
     * the current view with its adapter.
     *
     * Mints a FRESH session id. A disposed view's Session.close() reaches the sandbox asynchronously (it
     * posts to the sandbox's main thread) and was measured landing ~1 s AFTER the create: reusing the id let
     * that late close reap the session we had just asked for — a new WebView was built, destroyed, and the
     * surface stayed black. A new id makes the stale close target only the corpse it belongs to.
     */
    private fun rearmSession() {
        // The session being replaced may never have opened a surface (its view went away first), in which
        // case no surface close will ever reach the provider for it.
        send(NappletBrowserContract.MSG_CLOSE_SESSION) {}
        sessionId = newSessionId()
        // This create IS the re-creation a `:napplet` restart deferred to the next show.
        createOnShow = false
        adapterDelivered = false
        sessionDead = false
        resetPageState()
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
        resetPageState()
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
        blankRecovered = false
        publishLoadStatus(EmbeddedLoadStatus(isLoading = true))
    }

    // Set by the user's Retry: the next session starts over at [startUrl]. Every other re-creation (a crashed
    // renderer, a `:napplet` restart, a memory-trim rebuild) resumes the page the user was on.
    private var restartAtStart = false

    /** Where a new session opens: the page on screen before it was lost, else the tab's own [startUrl]. */
    private fun sessionUrl(): String {
        val resume = lastUrl?.takeUnless { restartAtStart || it.isBlankPage() }
        restartAtStart = false
        return resume ?: startUrl
    }

    private fun sendCreateSession() {
        awaitingReady = true
        uiDisplayed = false
        val msg =
            Message.obtain(null, NappletBrowserContract.MSG_CREATE_SESSION).apply {
                replyTo = incoming
                data =
                    Bundle().apply {
                        putString(NappletBrowserContract.KEY_SESSION_ID, sessionId)
                        putString(NappletBrowserContract.KEY_URL, sessionUrl())
                        putInt(NappletBrowserContract.KEY_PROXY_PORT, proxyPort())
                        putBoolean(NappletBrowserContract.KEY_USE_TOR, useTor)
                        putInt(NappletBrowserContract.KEY_BG_COLOR, backgroundColor)
                        putString(NappletBrowserContract.KEY_THEME, themeType)
                        putString(NappletBrowserContract.KEY_SEARCH_ENGINE, searchEngine())
                        // Opaque per-account storage partition, so an embedded site can't carry one
                        // npub's session into another. Derived here (the sandbox never sees the pubkey).
                        putString(NappletBrowserContract.KEY_WEBVIEW_PROFILE, NappletWebViewProfiles.current())
                    }
            }
        runCatching { serviceMessenger?.send(msg) }
        // Messenger keeps order, so these land after the CREATE and are stored on the new tab.
        if (textZoom != BrowserChrome.DEFAULT_TEXT_ZOOM) setTextZoom(textZoom)
        if (desktopSite) setDesktopSite(true)
        if (wantPaused) send(NappletBrowserContract.MSG_PAUSE) {}
        // Always: a new session starts unattended, so a tab created in view must say it is being watched.
        send(NappletBrowserContract.MSG_SET_ATTENDED) { putBoolean(NappletBrowserContract.KEY_ENABLED, wantAttended) }
    }

    private fun onServiceMessage(msg: Message): Boolean {
        // Nothing may act on a torn-down tab (a late file-chooser request would still open a picker), nor on
        // what a session this controller has since replaced still had in flight — a stale SESSION_READY
        // would re-arm the view with that dead session's adapter.
        if (tornDown) return true
        val from = msg.data?.getString(NappletBrowserContract.KEY_SESSION_ID)
        if (from != null && from != sessionId) return true
        when (msg.what) {
            NappletBrowserContract.MSG_SESSION_READY -> {
                val coreLibInfo = msg.data?.getBundle(NappletBrowserContract.KEY_CORE_LIB_INFO) ?: return true
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
            NappletBrowserContract.MSG_URL_CHANGED -> {
                val url = msg.data?.getString(NappletBrowserContract.KEY_URL).orEmpty()
                val canGoBack = msg.data?.getBoolean(NappletBrowserContract.KEY_CAN_GO_BACK, false) ?: false
                val canGoForward = msg.data?.getBoolean(NappletBrowserContract.KEY_CAN_GO_FORWARD, false) ?: false
                val title = msg.data?.getString(NappletBrowserContract.KEY_TITLE)
                if (url != BLANK_PAGE) {
                    lastUrl = url
                    lastTitle = title
                }
                lastCanGoBack = canGoBack
                lastCanGoForward = canGoForward
                onUrlChanged?.invoke(url, title, canGoBack, canGoForward)
            }
            NappletBrowserContract.MSG_IME_EVENT -> {
                val payload = msg.data?.getString(NappletBrowserContract.KEY_IME_PAYLOAD) ?: return true
                parseImeEvent(payload)?.let { event -> onImeEvent?.invoke(event) }
            }
            NappletBrowserContract.MSG_LOAD_STATE -> {
                val isLoading = msg.data?.getBoolean(NappletBrowserContract.KEY_IS_LOADING, false) ?: false
                val failed = msg.data?.getBoolean(NappletBrowserContract.KEY_LOAD_FAILED, false) ?: false
                val loadedUrl = msg.data?.getString(NappletBrowserContract.KEY_URL).orEmpty()
                if (msg.data?.getBoolean(NappletBrowserContract.KEY_RENDERER_GONE, false) == true) {
                    onSurfaceLost(sessionDead = false)
                } else {
                    onLoadState(isLoading, failed, loadedUrl)
                }
            }
            NappletBrowserContract.MSG_CONSOLE_LOG -> {
                val level = msg.data?.getString(NappletBrowserContract.KEY_CONSOLE_LEVEL) ?: "LOG"
                val message = msg.data?.getString(NappletBrowserContract.KEY_CONSOLE_MESSAGE).orEmpty()
                val source = msg.data?.getString(NappletBrowserContract.KEY_CONSOLE_SOURCE).orEmpty()
                val line = msg.data?.getInt(NappletBrowserContract.KEY_CONSOLE_LINE, 0) ?: 0
                console.add(ConsoleLine(consoleLevelOf(level), message, source, line))
            }
            NappletBrowserContract.MSG_FILE_CHOOSER_REQUEST -> {
                val data = msg.data ?: return true
                val requestId = data.getLong(NappletBrowserContract.KEY_FILE_CHOOSER_ID)
                // The sandbox has no Activity to run a picker from, so the main process runs it here and
                // ships the URIs back. The reply is sent on every outcome (a cancel included) — the page's
                // file input stays busy until it hears something.
                WebFileChooserCoordinator.request(
                    context = appContext,
                    acceptTypes = data.getStringArray(NappletBrowserContract.KEY_FILE_CHOOSER_ACCEPT)?.toList().orEmpty(),
                    allowMultiple = data.getBoolean(NappletBrowserContract.KEY_FILE_CHOOSER_MULTIPLE, false),
                    captureEnabled = data.getBoolean(NappletBrowserContract.KEY_FILE_CHOOSER_CAPTURE, false),
                    pageTitle = data.getString(NappletBrowserContract.KEY_FILE_CHOOSER_TITLE),
                ) { uris ->
                    send(NappletBrowserContract.MSG_FILE_CHOOSER_RESULT) {
                        putLong(NappletBrowserContract.KEY_FILE_CHOOSER_ID, requestId)
                        uris?.let { putStringArray(NappletBrowserContract.KEY_FILE_CHOOSER_URIS, it) }
                    }
                }
            }
            NappletBrowserContract.MSG_FIND_RESULT -> {
                val data = msg.data ?: return true
                _findResult.value = FindResult(data.getInt(NappletBrowserContract.KEY_FIND_ACTIVE), data.getInt(NappletBrowserContract.KEY_FIND_TOTAL))
            }
            NappletBrowserContract.MSG_PAGE_INFO -> {
                val data = msg.data ?: return true
                pageCertificate.value =
                    data.getString(NappletBrowserContract.KEY_CERT_ISSUED_TO)?.let { issuedTo ->
                        CertificateInfo(
                            issuedTo = issuedTo,
                            issuedBy = data.getString(NappletBrowserContract.KEY_CERT_ISSUED_BY).orEmpty(),
                            validUntil = data.getString(NappletBrowserContract.KEY_CERT_VALID_UNTIL).orEmpty(),
                        )
                    }
            }
            NappletBrowserContract.MSG_JS_DIALOG -> {
                val data = msg.data ?: return true
                val id = data.getLong(NappletBrowserContract.KEY_DIALOG_ID)
                // One page, one dialog at a time; if another is somehow still up, the newcomer is refused.
                if (pendingDialog.value != null) {
                    answerDialog(id, confirmed = false)
                    return true
                }
                pendingDialog.value =
                    EmbeddedJsDialog(
                        id = id,
                        type =
                            when (data.getString(NappletBrowserContract.KEY_DIALOG_TYPE)) {
                                "confirm" -> PageDialogType.CONFIRM
                                "prompt" -> PageDialogType.PROMPT
                                "beforeunload" -> PageDialogType.BEFORE_UNLOAD
                                else -> PageDialogType.ALERT
                            },
                        url = data.getString(NappletBrowserContract.KEY_URL),
                        message = data.getString(NappletBrowserContract.KEY_DIALOG_MESSAGE).orEmpty(),
                        defaultValue = data.getString(NappletBrowserContract.KEY_DIALOG_DEFAULT).orEmpty(),
                        offerBlock = data.getBoolean(NappletBrowserContract.KEY_DIALOG_OFFER_BLOCK, false),
                    )
            }
            NappletBrowserContract.MSG_PERMISSION_REQUEST -> {
                val data = msg.data ?: return true
                val id = data.getLong(NappletBrowserContract.KEY_PERMISSION_ID)
                val origin = data.getString(NappletBrowserContract.KEY_BROWSER_ORIGIN)
                val wanted =
                    data
                        .getStringArray(NappletBrowserContract.KEY_PERMISSIONS)
                        .orEmpty()
                        .mapNotNull(BrowserSitePermission::fromKey)
                        .toSet()
                if (origin == null || wanted.isEmpty() || pendingPermission.value != null) {
                    answerPermission(id, emptySet())
                } else {
                    pendingPermission.value = EmbeddedPermissionRequest(id, origin, wanted)
                }
            }
            NappletBrowserContract.MSG_PERMISSION_CANCEL -> {
                val id = msg.data?.getLong(NappletBrowserContract.KEY_PERMISSION_ID)
                if (pendingPermission.value?.id == id) pendingPermission.value = null
            }
            NappletBrowserContract.MSG_ROUTE -> routedOverTor.value = msg.data?.getBoolean(NappletBrowserContract.KEY_USE_TOR, false) ?: false
            NappletBrowserContract.MSG_DOWNLOAD_CONSENT -> {
                val data = msg.data ?: return true
                val id = data.getLong(NappletBrowserContract.KEY_DOWNLOAD_ID)
                val origin = data.getString(NappletBrowserContract.KEY_BROWSER_ORIGIN)
                val name = data.getString(NappletBrowserContract.KEY_DOWNLOAD_NAME).orEmpty()
                // An unnamed/absent origin means the sandbox couldn't even state who is asking: refuse.
                if (origin == null || name.isEmpty() || pendingDownload.value != null || pendingDialog.value != null || pendingPermission.value != null) {
                    answerDownload(id, allowed = false)
                    return true
                }
                pendingDownload.value =
                    EmbeddedDownloadRequest(
                        id = id,
                        origin = origin,
                        fileName = name,
                        sizeBytes = data.getLong(NappletBrowserContract.KEY_DOWNLOAD_SIZE, -1L),
                        sourceHost = data.getString(NappletBrowserContract.KEY_DOWNLOAD_SOURCE),
                        risky = data.getBoolean(NappletBrowserContract.KEY_DOWNLOAD_RISKY, false),
                    )
            }
            NappletBrowserContract.MSG_DOWNLOAD_CANCEL -> {
                val id = msg.data?.getLong(NappletBrowserContract.KEY_DOWNLOAD_ID)
                if (pendingDownload.value?.id == id) pendingDownload.value = null
            }
            NappletBrowserContract.MSG_FULLSCREEN -> isFullscreen.value = msg.data?.getBoolean(NappletBrowserContract.KEY_ENABLED, false) ?: false
            NappletBrowserContract.MSG_MAGNIFIER_FRAME -> {
                val data = msg.data ?: return true
                val bytes = data.getByteArray(NappletBrowserContract.KEY_MAG_BYTES) ?: return true
                onMagnifierFrame?.invoke(
                    MagnifierFrame(
                        bytes = bytes,
                        width = data.getInt(NappletBrowserContract.KEY_MAG_W),
                        height = data.getInt(NappletBrowserContract.KEY_MAG_H),
                        captureMs = data.getDouble(NappletBrowserContract.KEY_MAG_CAPTURE_MS),
                        requestStampNanos = data.getLong(NappletBrowserContract.KEY_MAG_REQ_T),
                    ),
                )
            }
            else -> return false
        }
        return true
    }

    fun navigate(url: String) =
        send(NappletBrowserContract.MSG_NAVIGATE) {
            putString(NappletBrowserContract.KEY_URL, url)
            putInt(NappletBrowserContract.KEY_PROXY_PORT, proxyPort())
        }

    fun reload() = send(NappletBrowserContract.MSG_RELOAD) { putInt(NappletBrowserContract.KEY_PROXY_PORT, proxyPort()) }

    /**
     * User-triggered recovery for a stuck, blank, or failed session: reload the canonical [startUrl] from
     * scratch. Unlike [reload] (which re-fetches whatever the WebView currently shows — `about:blank` for a
     * session that never got its URL), this re-navigates to the favorite's real URL.
     */
    override fun retry() {
        recovery.clearPending()
        showRecovering()
        // A surface that never opened has nothing to navigate: only a new session can paint it.
        if (sessionDead || (sandboxedSdkView != null && !uiDisplayed)) {
            restartAtStart = true
            rearmSession()
        } else {
            navigate(startUrl)
        }
    }

    private fun onLoadState(
        isLoading: Boolean,
        failed: Boolean,
        loadedUrl: String,
    ) {
        // A favorite whose session settled on about:blank never received its real URL (a warm session built
        // before the URL was wired through). Re-navigate once to the canonical URL — reload() can't fix this
        // because it would just reload about:blank. Scoped to a real startUrl, so the generic browser's
        // intentional about:blank new-tab page is left alone.
        if (!isLoading && !failed && loadedUrl.isBlankPage() && !startUrl.isBlankPage() && !blankRecovered) {
            blankRecovered = true
            publishLoadStatus(EmbeddedLoadStatus(isLoading = true))
            navigate(startUrl)
            return
        }
        if (!isLoading && !failed && !loadedUrl.isBlankPage()) hasLoadedReal = true
        publishLoadStatus(EmbeddedLoadStatus(isLoading = isLoading, failed = failed, hasLoadedReal = hasLoadedReal))
    }

    private fun publishLoadStatus(status: EmbeddedLoadStatus) {
        loadStatus = status
        isLoading.value = status.isLoading
        onLoadStatusChanged?.invoke(status)
    }

    private fun String.isBlankPage() = isEmpty() || this == BLANK_PAGE

    fun back() = send(NappletBrowserContract.MSG_BACK) {}

    fun forward() = send(NappletBrowserContract.MSG_FORWARD) {}

    fun stop() = send(NappletBrowserContract.MSG_STOP) {}

    override fun find(query: String) {
        if (query.isEmpty()) _findResult.value = null
        send(NappletBrowserContract.MSG_FIND) { putString(NappletBrowserContract.KEY_FIND_QUERY, query) }
    }

    override fun findNext(forward: Boolean) = send(NappletBrowserContract.MSG_FIND_NEXT) { putBoolean(NappletBrowserContract.KEY_FIND_FORWARD, forward) }

    fun setDesktopSite(enabled: Boolean) {
        desktopSite = enabled
        send(NappletBrowserContract.MSG_SET_DESKTOP) { putBoolean(NappletBrowserContract.KEY_ENABLED, enabled) }
    }

    fun setTextZoom(percent: Int) {
        textZoom = percent
        send(NappletBrowserContract.MSG_SET_TEXT_ZOOM) { putInt(NappletBrowserContract.KEY_TEXT_ZOOM, percent) }
    }

    /** Back to the app's home origin ([homeUrl]), Chrome's out-of-scope ✕. */
    fun backToScope(homeUrl: String) = send(NappletBrowserContract.MSG_BACK_TO_SCOPE) { putString(NappletBrowserContract.KEY_URL, homeUrl) }

    fun clearSiteData() = send(NappletBrowserContract.MSG_CLEAR_SITE_DATA) {}

    fun clearAllSiteData() = send(NappletBrowserContract.MSG_CLEAR_ALL_SITE_DATA) {}

    fun requestPageInfo() {
        pageCertificate.value = null
        send(NappletBrowserContract.MSG_PAGE_INFO_REQUEST) {}
    }

    fun exitFullscreen() = send(NappletBrowserContract.MSG_EXIT_FULLSCREEN) {}

    /** Answers the page's JS dialog [id]; [block] suppresses its further dialogs until it navigates. */
    fun answerDialog(
        id: Long,
        confirmed: Boolean,
        text: String? = null,
        block: Boolean = false,
    ) {
        if (pendingDialog.value?.id == id) pendingDialog.value = null
        send(NappletBrowserContract.MSG_JS_DIALOG_RESULT) {
            putLong(NappletBrowserContract.KEY_DIALOG_ID, id)
            putBoolean(NappletBrowserContract.KEY_DIALOG_CONFIRMED, confirmed)
            text?.let { putString(NappletBrowserContract.KEY_DIALOG_TEXT, it) }
            putBoolean(NappletBrowserContract.KEY_DIALOG_BLOCK, block)
        }
    }

    /** Grants [granted] (possibly nothing) for the page's permission request [id]. */
    fun answerPermission(
        id: Long,
        granted: Set<BrowserSitePermission>,
    ) {
        if (pendingPermission.value?.id == id) pendingPermission.value = null
        send(NappletBrowserContract.MSG_PERMISSION_RESULT) {
            putLong(NappletBrowserContract.KEY_PERMISSION_ID, id)
            putStringArray(NappletBrowserContract.KEY_PERMISSIONS, granted.map { it.key }.toTypedArray())
        }
    }

    /** Answers the download-consent card for [id]: true lets the sandbox fetch or write the file it described. */
    fun answerDownload(
        id: Long,
        allowed: Boolean,
    ) {
        if (pendingDownload.value?.id == id) pendingDownload.value = null
        send(NappletBrowserContract.MSG_DOWNLOAD_CONSENT_RESULT) {
            putLong(NappletBrowserContract.KEY_DOWNLOAD_ID, id)
            putBoolean(NappletBrowserContract.KEY_DOWNLOAD_ALLOWED, allowed)
        }
    }

    fun setTor(useTor: Boolean) {
        this.useTor = useTor
        send(NappletBrowserContract.MSG_SET_TOR) {
            putBoolean(NappletBrowserContract.KEY_USE_TOR, useTor)
            putInt(NappletBrowserContract.KEY_PROXY_PORT, proxyPort())
        }
    }

    override fun sendImeOp(json: String) = send(NappletBrowserContract.MSG_IME_OP) { putString(NappletBrowserContract.KEY_IME_PAYLOAD, json) }

    // Stamp the client send time so the reply can be matched / stale frames dropped (same-process clock).
    override fun requestMagnifier(
        surfaceX: Float,
        surfaceY: Float,
        boxWidthPx: Int,
        boxHeightPx: Int,
        zoom: Float,
    ) = send(NappletBrowserContract.MSG_MAGNIFIER_REQUEST) {
        putFloat(NappletBrowserContract.KEY_MAG_X, surfaceX)
        putFloat(NappletBrowserContract.KEY_MAG_Y, surfaceY)
        putInt(NappletBrowserContract.KEY_MAG_BOX_W, boxWidthPx)
        putInt(NappletBrowserContract.KEY_MAG_BOX_H, boxHeightPx)
        putFloat(NappletBrowserContract.KEY_MAG_ZOOM, zoom)
        putLong(NappletBrowserContract.KEY_MAG_REQ_T, SystemClock.elapsedRealtimeNanos())
    }

    private inline fun send(
        what: Int,
        crossinline block: Bundle.() -> Unit,
    ) {
        val msg =
            Message.obtain(null, what).apply {
                data =
                    Bundle().apply {
                        putString(NappletBrowserContract.KEY_SESSION_ID, sessionId)
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
        private const val MAX_CONSOLE_LOGS = 200
        private const val BLANK_PAGE = "about:blank"

        private fun newSessionId() = "browser-$PROCESS_NONCE-${SESSION_SEQ.incrementAndGet()}"
    }
}
