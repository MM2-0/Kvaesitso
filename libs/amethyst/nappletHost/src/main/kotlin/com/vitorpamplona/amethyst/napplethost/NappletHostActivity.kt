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
package com.vitorpamplona.amethyst.napplethost

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPillEvent
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPillUi
import com.vitorpamplona.amethyst.commons.browser.ui.pill.ConsoleLine
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletActingRequests
import com.vitorpamplona.amethyst.commons.napplet.NappletDownloadProgress
import com.vitorpamplona.amethyst.commons.napplet.NappletHeldRequests
import com.vitorpamplona.amethyst.commons.napplet.NappletProxyClaims
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.NappletWebResources
import com.vitorpamplona.amethyst.commons.napplet.WebxdcLaunch
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.util.booleanOrNull
import com.vitorpamplona.amethyst.commons.util.parseJsonObjectOrNull
import com.vitorpamplona.amethyst.commons.util.stringOrNull
import com.vitorpamplona.amethyst.commons.util.withString
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.lang.ref.WeakReference
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * Hosts a napplet/nsite WebView in the isolated `:napplet` process — a process that holds **no**
 * account state, signer, or keys. It:
 *
 * 1. serves the trusted shell page and the manifest's **already-verified** blobs through
 *    [WebViewClient.shouldInterceptRequest] (no `file://`/`content://`, default-deny CSP with
 *    `connect-src 'none'` so the applet has no direct network), and
 * 2. relays the applet's `window.napplet.*` capability calls — applet → shell (postMessage) →
 *    native (origin-restricted [WebViewCompat.addWebMessageListener]) → main-process broker
 *    (Messenger) → back — without ever interpreting them itself.
 *
 * Even a full WebView/renderer escape into this process yields no secret: the keys live only in
 * the main process, and every brokered operation is still gated by user consent there.
 */
class NappletHostActivity : ComponentActivity() {
    private lateinit var webView: WebView

    // ---- HTML file input (`<input type="file">`) ----
    // The applet picks the file through the system picker, so the user names exactly which file crosses
    // into the sandbox — the same user-mediated grant a browser gives a web page. Nothing extra is
    // brokered: the applet gets the bytes of the one file that was chosen and no path around it.
    // Registered as a field so it exists before onCreate returns (registerForActivityResult's contract).
    private val pendingFileChooser = PendingFileChooser()

    private val fileChooserLauncher = WebFileChooserLauncher(this) { uris -> pendingFileChooser.deliver(uris) }

    private val paths = mutableListOf<PathTag>()
    private val servers = mutableListOf<String>()
    private var author: String = ""

    // The applet's stable identifier (the manifest `d` tag; empty for a root/replaceable applet).
    // Combined with [author] it gives the applet's per-launch-stable identity, used to derive its
    // own sandbox origin so its storage persists across launches and stays isolated from other applets.
    private var identifier: String = ""

    // Opaque token the broker resolves to this launch's trusted identity + declared capabilities.
    // The sandbox never carries its own coordinate, so a compromised :napplet process can't forge one.
    private var launchToken: String = ""

    // The host posture: a WEBSITE nSite (NIP-07 window.nostr + normal network) vs a locked NAPPLET.
    private var profile: HostProfile = HostProfile.NAPPLET

    // Per-site network choice: route this site's traffic through Tor (default) or over the open web.
    // Applied to both the WebView proxy and the blob-fetch client; toggled from the top-bar onion.
    private var useTor: Boolean = true

    // NAP domain strings the shell advertises to the applet in the shell.init handshake.
    private var declaredDomains: List<String> = emptyList()

    private var themeType: String = "SYSTEM"

    // Opaque per-account WebView storage-profile name (see NappletWebViewProfile). Null/blank when the
    // launcher didn't scope one, which lands on the shared default jar.
    private var webViewProfile: String? = null

    // Pre-localized capability labels for the "what it can access" sheet (resolved by the launcher).
    private var capabilityLabels: List<String> = emptyList()

    // Correlation id for id-less fire-and-forget messages so they still reach the broker.
    private var fireSeq = 0

    private var proxyPort: Int = -1

    /** Whether the process route is Tor right now, whatever this nSite asked for. */
    private var routedOverTor = false

    // The resource edge (shell + verified blobs); built in onCreate once the manifest is parsed.
    private lateinit var contentServer: NappletContentServer

    // Messenger to the main-process broker, bound lazily; requests queue until connected.
    private var brokerMessenger: Messenger? = null

    /**
     * Reply channel handed to the broker. It MUST NOT hold this Activity strongly — a [Messenger] sent
     * over IPC is a binder, so while the main process holds it ART keeps a JNI global reference to the
     * backing [Handler] here in `:napplet`. With a bound method reference as the handler callback, that
     * one retained binder pins Activity → window → WebView past `onDestroy`, reclaimable only by killing
     * the process. See [NappletBrowserActivity.replyMessenger] for the measured case.
     */
    private val replyMessenger = Messenger(WeakBrokerReplyHandler(this))

    private class WeakBrokerReplyHandler(
        activity: NappletHostActivity,
    ) : Handler(Looper.getMainLooper()) {
        private val ref = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            ref.get()?.onBrokerReply(msg)
        }
    }

    private val pendingRequests = mutableListOf<Message>()
    private var bridgeReplyProxy: JavaScriptReplyProxy? = null

    // Keyboard/command actions the applet bound via keys.registerAction; matched in dispatchKeyEvent.
    private val keyActions = NappletKeyActions()

    // Swaps between the loading screen, the applet WebView, and the "unavailable" screen.
    private val contentFrame by lazy { FrameLayout(this) }
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // The loading splash (monogram + spinner). Kept on top of the mounted WebView and removed only on
    // first paint, so there's never a blank/dark gap between the index probe and the shell's first frame.
    private var loadingView: View? = null

    // The splash's spinner, and the bar and sizes that take its place while a webxdc archive downloads.
    private var loadingSpinner: View? = null
    private var downloadBar: ProgressBar? = null
    private var downloadLabel: TextView? = null

    // The trusted pull-down pill, find and the developer console — the shared Compose chrome.
    private var chrome: BrowserChromeHost? = null

    // Set when the renderer died and the WebView was destroyed, so teardown doesn't touch it again.
    private var webViewGone = false

    // Set once the WebView has begun loading the shell, so a retry doesn't reload it.
    private var started = false

    // Back goes "page back" inside the applet's WebView history first; only when it can't go back
    // further does this disable itself and let the system back exit the sandbox to Amethyst.
    private val backCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                // The chrome first (open pill, find), then the applet's own history.
                if (chrome?.handleBack() == true) return
                if (this@NappletHostActivity::webView.isInitialized && !webViewGone && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

    /** Keep the in-WebView back gesture enabled exactly while the applet has history to pop. */
    private fun syncBackState() {
        val canGoBack = this::webView.isInitialized && !webViewGone && webView.canGoBack()
        backCallback.isEnabled = canGoBack || chrome?.wantsBack == true
    }

    // True between onResume and onPause. Sent to the broker (foreground hold) on connect too, in case
    // the broker binds after this surface is already resumed (bindService is async).
    private var resumed = false

    // Requests that act for the user (publish, pay, upload…) made while this napplet was in the background.
    // Pausing the WebView doesn't stop JavaScript, so they are held here and sent on the next resume.
    private val heldWhilePaused = NappletHeldRequests<Message>(SystemClock::elapsedRealtime)

    private val expireHeld =
        Runnable {
            if (!isDestroyed) heldWhilePaused.expire().forEach { bridgeReplyProxy?.failRequest(it, NappletHeldRequests.EXPIRED) }
        }

    // Renews the broker's foreground lease while resumed. If this process dies, the heartbeat stops and
    // the broker reaps the stale lease, so a crash can't pin the main process's network up forever.
    private var foregroundHeartbeat: Job? = null

    private val brokerConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                brokerMessenger = Messenger(service)
                pendingRequests.forEach { sendToBroker(it) }
                pendingRequests.clear()
                // If we're already foreground by the time the broker binds, report it now so the
                // main-process resource hold is acquired for this session.
                if (resumed) setBrokerForeground(true)
                reportAttended()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                brokerMessenger = null
            }
        }

    /**
     * This activity is `singleTask`, so opening a napplet while another is still in the background
     * arrives here instead of starting a new one. The same napplet comes back as it was; a different
     * one rebuilds the screen around the new intent, since everything here is set up in onCreate.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isSameLaunch(intent)) return
        setIntent(intent)
        recreate()
    }

    private fun isSameLaunch(other: Intent): Boolean =
        other.getStringExtra(NappletHostContract.EXTRA_AUTHOR).orEmpty() == author &&
            other.getStringExtra(NappletHostContract.EXTRA_IDENTIFIER).orEmpty() == identifier &&
            HostProfile.fromName(other.getStringExtra(NappletHostContract.EXTRA_HOST_PROFILE)) == profile &&
            other.getStringExtra(NappletHostContract.EXTRA_WEBVIEW_PROFILE) == webViewProfile &&
            other.getBooleanExtra(NappletHostContract.EXTRA_USE_TOR, true) == useTor &&
            NappletHostContract.webxdcOf(other.extras).let { it?.url == webxdc?.url && it?.hash == webxdc?.hash }

    // The chrome sees touches first, so a swipe down on its handle opens it over the page.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = chrome?.dispatchTouchEvent(ev) { super.dispatchTouchEvent(it) } ?: super.dispatchTouchEvent(ev)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SandboxComposeResources.ensure(this)

        if (!readManifestExtras()) {
            Toast.makeText(this, getString(R.string.napplet_invalid), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // Fail closed: Tor is on but its port isn't known yet, so nothing (blobs or web traffic) may go out.
        if (useTor && proxyPort <= 0) {
            Toast.makeText(this, R.string.napplet_route_blocked, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // A napplet or webxdc app (or a site on Tor) must lose WebRTC in every frame; a WebView that can't
        // do that would hand it a network around the proxy, so it doesn't run here at all.
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) || (NappletWebRtcGuard.isNeeded(profile, useTor) && !NappletWebRtcGuard.isSupported())) {
            Toast.makeText(this, getString(R.string.napplet_webview_too_old), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // The shell page + shim are the shared web contract (commons composeResources); load them
        // once up front so the WebView worker threads that serve them never block on resource I/O.
        // This isolated `:napplet` process skips app init (to stay key-free), so the compose-resources
        // Android context is never set here and `Res.readBytes` would crash — read the contract bytes
        // straight from the APK assets, where compose-resources packages them.
        fun readContractAsset(path: String): ByteArray = assets.open(NappletWebResources.RESOURCE_ASSET_ROOT + path).use { it.readBytes() }
        val shellHtml = readContractAsset(NappletWebResources.SHELL_HTML_PATH)
        val shim = readContractAsset(NappletWebResources.SHIM_JS_PATH).decodeToString()
        val appOrigin = NappletWebContract.appOrigin(NappletWebContract.appId(author, identifier, profile = profile), profile)
        // "Open web" for a site makes everything direct — both its blob fetches (here) and its live
        // web traffic (the WebView proxy, below). Tor (the default) routes both through the SOCKS port.
        val effectiveProxy = if (useTor) proxyPort else -1
        val cachePartition = NappletBlobCache.partitionOf(webViewProfile.orEmpty(), author, identifier)
        contentServer = NappletContentServer(paths, servers, effectiveProxy, cacheDir, cachePartition, shellHtml, shim, appOrigin, profile, declaredDomains, webxdc = webxdc)

        // Create + warm the WebView NOW so its (slow, first-in-process) Chromium init runs on the main
        // thread concurrently with the index probe below (which runs on IO) — instead of serially after
        // it. Binding the broker early overlaps too. The WebView is attached once the probe succeeds.
        // Built from a context forced to the app theme so its content follows DARK/LIGHT regardless of the
        // device theme (WebView reads the context's theme, not the window's — see nightThemedContext).
        webView = WebView(nightThemedContext(this, themeType))
        // FIRST touch after construction: setProfile throws once the WebView has loaded content (or its
        // profile has otherwise been used), so the storage partition must be chosen before anything else.
        NappletWebViewProfile.apply(this, webView, webViewProfile)
        hardenWebView(webView)
        webView.setFindListener { active, total, _ -> chrome?.setFindResult(active, total) }
        // Theme the WebView's pre-paint background to the app's so it doesn't flash white when the shell
        // mounts. This activity has a themed context, so it resolves the color locally (no IPC needed).
        webView.setBackgroundColor(resolveThemeColor(android.R.attr.colorBackground))
        // Route the WebView's own (off-origin) traffic through Tor for an nSite, unless this site was
        // opted out to the open web. Set process-wide before any page navigation; the shell + blobs are
        // served from cache via shouldInterceptRequest, so only the site's external requests hit this.
        if (profile.exposesNetwork) {
            // An open-web nSite still goes through Tor while another surface needs it: say so in the chrome.
            WebViewProxyPolicy.observeRoute(this) {
                routedOverTor = it
                chrome?.let { c -> c.ui = c.ui.copy(chrome = c.ui.chrome.copy(torForced = !useTor && it)) }
            }
            // Start applying now, overlapping the index probe; the load itself waits in mountWebView.
            WebViewProxyPolicy.claim(this, effectiveProxy)
        }
        // Origin-restricted bridge: only the trusted shell page (main frame) can reach native.
        WebViewCompat.addWebMessageListener(
            webView,
            NappletWebContract.BRIDGE_NAME,
            setOf(NappletWebContract.ORIGIN),
            ::onShellMessage,
        )
        // Bind the main-process broker by explicit class name (same APK) rather than a compile-time
        // class reference, so this sandbox module needs no dependency on :amethyst.
        bindService(Intent().setClassName(this, NappletHostContract.BROKER_SERVICE_CLASS), brokerConnection, BIND_AUTO_CREATE)

        // Route the back gesture into the WebView's history first (see backCallback).
        onBackPressedDispatcher.addCallback(this, backCallback)

        // The applet titles itself, so instead of a full-width bar we hang a trusted top pull-down sheet
        // (the anti-phishing shield the applet can't draw over) over the content frame, which shows a
        // loading screen → the applet's WebView, or an "unavailable" screen.
        val root =
            FrameLayout(this).apply {
                addView(contentFrame, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
        chrome = buildChrome().also { it.attach(root) }
        setContentView(root)
        // Activities are edge-to-edge by default on recent Android; pad by the system bar, display-cutout
        // and IME insets so neither the chrome nor the applet draws under the system bars or the soft
        // keyboard (adjustResize no longer resizes an edge-to-edge window). See applyFullScreenHostInsets.
        root.applyFullScreenHostInsets()

        probeAndMount()
    }

    /**
     * Probe whether the app/site's index resolves (downloading + verifying it, which also warms the
     * cache) — on IO, concurrently with the WebView init kicked off in [onCreate] — before showing the
     * WebView. The user sees a loading screen, then either the running app or a clear "unavailable"
     * screen with Retry, instead of a blank/white WebView.
     */
    private fun probeAndMount() {
        contentFrame.removeAllViews()
        loadingView = buildLoadingView().also { contentFrame.addView(it) }
        uiScope.launch {
            val available = withContext(Dispatchers.IO) { contentServer.isAvailable("/") { runOnUiThread { showDownload(it) } } }
            // The watch stops with the probe and may miss the download's end: the spinner returns
            // while the page loads.
            showDownload(null)
            if (available) {
                mountWebView()
            } else {
                contentFrame.removeAllViews()
                loadingView = null
                contentFrame.addView(buildErrorView { probeAndMount() })
            }
        }
    }

    private fun mountWebView() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        // Mount the WebView UNDER the loading splash (index 0) instead of replacing it: the shell + applet
        // bundle still take time to paint (seconds over Tor), and the WebView shows only its dark
        // colorBackground until then. The splash stays until the first frame paints (onPageCommitVisible),
        // so the user never sees a blank/black screen with no sign that anything is loading.
        contentFrame.addView(webView, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (!started) {
            started = true
            // Wait for the route to be in effect: a Tor nSite's first off-origin request must not leave early,
            // and must not leave at all if the route can't be applied.
            if (profile.exposesNetwork) {
                WebViewProxyPolicy.claim(
                    owner = this,
                    torPort = if (useTor) proxyPort else NappletProxyClaims.NO_PROXY,
                    onFailed = { if (!isDestroyed) Toast.makeText(this, R.string.napplet_route_blocked, Toast.LENGTH_LONG).show() },
                ) { if (!isDestroyed) webView.loadUrl(NappletWebContract.SHELL_URL) }
            } else {
                webView.loadUrl(NappletWebContract.SHELL_URL)
            }
        }
    }

    private val backgroundPauseHandler = Handler(Looper.getMainLooper())

    // webView.onPause() pauses THIS WebView (animations, media, geolocation). Do NOT call pauseTimers(): it's
    // process-global and freezes EVERY WebView in `:napplet`, including the embedded browser/napplet surfaces,
    // which never resume.
    private val backgroundPause =
        Runnable {
            if (!isDestroyed && this::webView.isInitialized && !webViewGone) webView.onPause()
        }

    override fun onResume() {
        super.onResume()
        if (this::webView.isInitialized && !webViewGone) {
            webView.onResume()
        }
        // Launching this :napplet-process surface backgrounded the main process; tell the broker to
        // hold the main process resumed (Tor/relays/AUTH) while this napplet/nSite is in front, and
        // keep renewing that lease so a crash here can't pin the network up forever.
        resumed = true
        reportAttended()
        startForegroundHeartbeat()
        val held = heldWhilePaused.drain()
        held.fail.forEach { bridgeReplyProxy?.failRequest(it, NappletHeldRequests.EXPIRED) }
        held.send.forEach { if (brokerMessenger == null) pendingRequests.add(it) else sendToBroker(it) }
    }

    override fun onStart() {
        super.onStart()
        // Back within the grace: the page was never paused.
        backgroundPauseHandler.removeCallbacks(backgroundPause)
    }

    override fun onStop() {
        // Out of sight: pause the page after the same grace the rest of the app gets
        // (NappletHostContract.BACKGROUND_PAUSE_MS), so a quick trip to another app doesn't interrupt it.
        // Anything that acts for the user is already held (see [resumed]).
        backgroundPauseHandler.postDelayed(backgroundPause, NappletHostContract.BACKGROUND_PAUSE_MS)
        super.onStop()
    }

    override fun onPause() {
        // Foreground-only for requests: while not resumed, the applet's requests that act for the user
        // (sign/publish/pay…) are held until the user is back, so their consent prompt can't surface over
        // (and be confused with) Amethyst's own UI and an "allow always" napplet can't act unwatched. The page
        // itself keeps running until onStop's grace runs out.
        resumed = false
        reportAttended()
        stopForegroundHeartbeat()
        setBrokerForeground(false)
        super.onPause()
    }

    /** Reports foreground=true immediately and then re-reports on a heartbeat to renew the broker lease. */
    private fun startForegroundHeartbeat() {
        foregroundHeartbeat?.cancel()
        foregroundHeartbeat =
            uiScope.launch {
                while (true) {
                    setBrokerForeground(true)
                    delay(FOREGROUND_HEARTBEAT_MS)
                }
            }
    }

    private fun stopForegroundHeartbeat() {
        foregroundHeartbeat?.cancel()
        foregroundHeartbeat = null
    }

    /**
     * Asks the broker to drop every reference it holds to [replyMessenger] (inc-bus subscriptions and this
     * surface's foreground lease). Sent directly rather than through [sendToBroker], which queues while
     * unbound — we are being destroyed, so a queued release would never leave.
     */
    private fun releaseFromBroker() {
        val broker = brokerMessenger ?: return
        val msg =
            Message.obtain(null, NappletIpc.MSG_RELEASE_CLIENT).apply {
                replyTo = replyMessenger
                data = Bundle().apply { putString(NappletIpc.KEY_LAUNCH_TOKEN, launchToken) }
            }
        runCatching { broker.send(msg) }
    }

    /** Tells the broker whether this napplet is being looked at, which gates decrypting its relay reads. */
    private fun reportAttended() {
        val msg =
            Message.obtain(null, NappletIpc.MSG_SET_ATTENDED).apply {
                replyTo = replyMessenger
                data =
                    Bundle().apply {
                        putBoolean(NappletIpc.KEY_ATTENDED, resumed)
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, launchToken)
                    }
            }
        if (brokerMessenger == null) pendingRequests.add(msg) else sendToBroker(msg)
    }

    /** Reports this surface's foreground state to the broker so it can hold the main process resumed. */

    private fun setBrokerForeground(foreground: Boolean) {
        val msg =
            Message.obtain(null, NappletIpc.MSG_SET_FOREGROUND).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, launchToken)
                        putBoolean(NappletIpc.KEY_FOREGROUND, foreground)
                    }
            }
        // Before the broker binds, the surface isn't really up yet; the matching onPause(false) is a
        // no-op on the broker's empty map, so dropping a pre-bind report is harmless — the heartbeat
        // re-reports once connected (and onServiceConnected seeds it too).
        if (brokerMessenger != null) sendToBroker(msg)
    }

    override fun onDestroy() {
        backgroundPauseHandler.removeCallbacks(backgroundPause)
        backgroundPauseHandler.removeCallbacks(expireHeld)
        uiScope.cancel()
        // Drop the broker's references to our reply Messenger BEFORE unbinding — a retained Messenger is a
        // binder and would pin this Activity (and its WebView) for the life of the `:napplet` process.
        releaseFromBroker()
        WebViewProxyPolicy.release(this)
        // Its own HTTP client: threads and keep-alive connections that would outlive the screen.
        if (this::contentServer.isInitialized) contentServer.close()
        // unbind is in runCatching: if the index never resolved we never bound the broker.
        runCatching { unbindService(brokerConnection) }
        keyActions.clear()
        // A picker still up when the applet is torn down would otherwise leave its callback unanswered.
        pendingFileChooser.cancel()
        fileChooserLauncher.teardown()
        if (this::webView.isInitialized && !webViewGone) {
            // Detach before destroy(): destroying an attached WebView corrupts the shared multiprocess
            // renderer/network state and breaks the other (embedded) WebViews in this `:napplet` process
            // (dead DNS, empty DOM reads, dead selection paint, broken IME). See NappletBrowserActivity.
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    /**
     * Intercepts hardware-key combos the applet bound via `keys.registerAction` and turns them into a
     * `keys.action` push — the applet never sees raw key events, only its own named action. Unmatched
     * keys fall through to the WebView (so the applet's own text inputs still work normally).
     *
     * RestrictedApi is a false positive: `Activity.dispatchKeyEvent` is a public framework
     * hook; lint flags it only because androidx.core's intermediate override carries a
     * library-group `@RestrictTo`. Unmatched keys still reach `super`, so androidx's
     * KeyEventDispatcher routing is preserved.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val actionId = keyActions.actionFor(event)
        if (actionId != null) {
            runCatching { bridgeReplyProxy?.postMessage(NappletProtocolJson.encodeKeysAction(actionId)) }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun readManifestExtras(): Boolean {
        val pathList = intent.getStringArrayListExtra(NappletHostContract.EXTRA_PATHS) ?: return false
        val hashList = intent.getStringArrayListExtra(NappletHostContract.EXTRA_HASHES) ?: return false
        webxdc = NappletHostContract.webxdcOf(intent.extras)
        // A webxdc app's one file is its archive, which comes as its own extra.
        if (pathList.size != hashList.size || (pathList.isEmpty() && webxdc == null)) return false

        for (i in pathList.indices) paths.add(PathTag(pathList[i], hashList[i]))
        servers.addAll(intent.getStringArrayListExtra(NappletHostContract.EXTRA_SERVERS) ?: emptyList())
        author = intent.getStringExtra(NappletHostContract.EXTRA_AUTHOR).orEmpty()
        identifier = intent.getStringExtra(NappletHostContract.EXTRA_IDENTIFIER).orEmpty()
        profile = HostProfile.fromName(intent.getStringExtra(NappletHostContract.EXTRA_HOST_PROFILE))
        useTor = intent.getBooleanExtra(NappletHostContract.EXTRA_USE_TOR, true)
        title = intent.getStringExtra(NappletHostContract.EXTRA_TITLE).orEmpty()
        proxyPort = intent.getIntExtra(NappletHostContract.EXTRA_PROXY_PORT, -1)
        launchToken = intent.getStringExtra(NappletHostContract.EXTRA_LAUNCH_TOKEN).orEmpty()
        capabilityLabels = intent.getStringArrayListExtra(NappletHostContract.EXTRA_CAP_LABELS) ?: emptyList()
        themeType = intent.getStringExtra(NappletHostContract.EXTRA_THEME).orEmpty().ifBlank { "SYSTEM" }
        webViewProfile = intent.getStringExtra(NappletHostContract.EXTRA_WEBVIEW_PROFILE)

        val requires = intent.getStringArrayListExtra(NappletHostContract.EXTRA_REQUIRES) ?: emptyList()
        val resolved = profile.declaredCapabilities(requires)
        // Domain-object presence is the NIP-5D availability signal. The broker still enforces the
        // authoritative set minted into the launch token in the main process.
        declaredDomains = resolved.map { it.name.lowercase() }.distinct()

        return author.isNotEmpty() && launchToken.isNotEmpty()
    }

    private var title: String = ""

    // Set for a webxdc app: its archive and who runs it.
    private var webxdc: WebxdcLaunch? = null

    @Suppress("SetJavaScriptEnabled")
    private fun hardenWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true // the applet needs JS; isolation comes from process + CSP + sandbox
            // DOM storage (localStorage/sessionStorage) is on because the applet runs on its OWN real,
            // per-applet origin (a napplet.local subdomain) — storage is scoped to that origin, isolated
            // from other applets and from the shell. SPAs need it at boot; without it they crash-loop.
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                safeBrowsingEnabled = true
            }
        }
        // Disable the overscroll stretch/glow: forcing a scroll past the content edge stretched the
        // WebView's output and exposed the shell document's background behind the applet iframe at the
        // seam (a stray white band at the bottom). The applet's own content still scrolls normally.
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        WebView.setWebContentsDebuggingEnabled(false)
        if (NappletWebRtcGuard.isNeeded(profile, useTor)) NappletWebRtcGuard.install(webView)
        NappletWebRtcGuard.installCookieGuard(webView)
        webView.webViewClient = NappletWebViewClient()
        webView.webChromeClient = NappletWebChromeClient()
    }

    /** Serves only the trusted shell and the manifest's verified blobs; everything else 404s. */
    private inner class NappletWebViewClient : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = contentServer.serve(request)

        // The applet's in-page / in-iframe navigations (links + history.pushState) change the
        // WebView's back/forward list; keep the back gesture enabled exactly while it can pop.
        override fun doUpdateVisitedHistory(
            view: WebView,
            url: String,
            isReload: Boolean,
        ) {
            syncBackState()
        }

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            syncBackState()
        }

        // The shell has painted its first frame — drop the loading splash so the running app shows
        // through. Null-safe so a later in-app navigation/reload (splash already gone) is a no-op.
        override fun onPageCommitVisible(
            view: WebView,
            url: String,
        ) {
            loadingView?.let { contentFrame.removeView(it) }
            loadingView = null
        }

        // Surface failed resource fetches (a missing blob, a verify miss, an off-origin request the
        // default-deny CSP blocked) in the console so an nsite/napplet developer can see what broke.
        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            logConsoleError(request, getString(R.string.napplet_console_load_error, error.errorCode, error.description?.toString().orEmpty()))
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            logConsoleError(request, getString(R.string.napplet_console_http_error, errorResponse.statusCode, errorResponse.reasonPhrase.orEmpty()))
        }

        /**
         * The renderer died. Every WebView in `:napplet` shares one renderer and an unhandled crash kills
         * the whole process (every embedded tab too), so drop only this WebView and offer to start over.
         */
        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean {
            Log.w(TAG) { "Renderer gone (crashed=${detail.didCrash()}); offering a restart" }
            webViewGone = true
            chrome?.closeFind()
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            loadingView?.let { contentFrame.removeView(it) }
            loadingView = null
            contentFrame.addView(buildErrorView { recreate() })
            syncBackState()
            return true
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val uri = request.url
            // Internal hosts (the shell host and this applet's own per-applet subdomain): let the
            // WebView load them — they go through shouldInterceptRequest and are served from cache.
            if (NappletWebContract.isInternalHost(uri.host)) return false
            // An external link the user actually tapped is handed to the system browser. A user
            // gesture is required so a hostile site can't auto-redirect to spam-open the browser,
            // and only http(s) is honored so it can't fire arbitrary intent schemes.
            if (profile.opensLinks && request.hasGesture() && (uri.scheme == "https" || uri.scheme == "http")) {
                runCatching { startActivity(linkIntent(profile, uri)) }
            }
            // Never navigate the sandbox WebView away from our internal origin.
            return true
        }
    }

    /** Feeds load progress to the pill, opens the file picker, and forwards `console.*` output to the panel. */
    private inner class NappletWebChromeClient : WebChromeClient() {
        /**
         * Without this override WebView's base implementation returns false and shows no picker at all, so
         * every `<input type="file">` in an applet or nSite is a dead tap. Always returns true: the
         * callback is ours, and [showFileChooser] answers it on every path.
         */
        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean = showFileChooser(filePathCallback, fileChooserParams)

        override fun onProgressChanged(
            view: WebView,
            newProgress: Int,
        ) {
            updateLoadProgress(newProgress)
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            val host = chrome ?: return false
            host.appendConsole(
                ConsoleLine(BrowserChromeHost.levelOf(consoleMessage.messageLevel()), consoleMessage.message(), consoleMessage.sourceId(), consoleMessage.lineNumber()),
            )
            return true
        }
    }

    /**
     * Opens the system picker for the applet's file input and routes the pick back to it. Returns true
     * unconditionally: the callback is ours from here on, and it is answered on every path — a real pick,
     * a cancel, or a device with no app that can return a file.
     */
    private fun showFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        pendingFileChooser.start(filePathCallback)
        fileChooserLauncher.launch(
            acceptTypes = params.acceptTypes?.toList().orEmpty(),
            allowMultiple = NappletFileChooser.allowsMultiple(params.mode),
            captureEnabled = params.isCaptureEnabled,
            pageTitle = params.title,
        )
        return true
    }

    /**
     * Feeds the page's load progress to the pill, which draws it under the grabber (and as a ring around
     * Stop when open); cleared at 100%.
     */
    private fun updateLoadProgress(progress: Int) {
        if (progress >= 100) {
            chrome?.let { it.ui = it.ui.copy(loadProgress = null, chrome = it.ui.chrome.copy(isLoading = false)) }
        } else {
            chrome?.let { it.ui = it.ui.copy(loadProgress = progress / 100f, chrome = it.ui.chrome.copy(isLoading = true)) }
        }
    }

    /** Appends a single ERROR line to the console panel and refreshes the chrome's unread count. */
    private fun logConsoleError(
        request: WebResourceRequest,
        message: String,
    ) {
        chrome?.appendConsole(ConsoleLine(ConsoleLine.Level.ERROR, message, request.url?.toString().orEmpty(), 0))
    }

    // ---- bridge: shell <-> native ----

    private fun onShellMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (!isMainFrame) return // only the trusted shell, never a sub-frame
        bridgeReplyProxy = replyProxy

        val raw = message.data ?: return
        // The applet sends a full upstream envelope {type, id, ...}; we forward it verbatim and
        // correlate on its id. The broker reads `type` to decode and to build the .result reply.
        val envelope = parseJsonObjectOrNull(raw) ?: return

        // Unbind a keyboard action as soon as the applet drops it (the broker's Done reply carries no
        // actionId, so the binding is removed here from the envelope itself).
        if (envelope.stringOrNull("type") == "keys.unregisterAction") {
            envelope.stringOrNull("actionId")?.takeIf { it.isNotEmpty() }?.let { keyActions.unregister(it) }
        }

        // Fire-and-forget messages (inc.emit, keys.unregisterAction) have no id; synthesize one so
        // they still reach the broker. Any reply is harmless — the applet has nothing to correlate.
        val id = envelope.stringOrNull("id").orEmpty().ifEmpty { "fire-${fireSeq++}" }

        val msg =
            Message.obtain(null, NappletIpc.MSG_REQUEST).apply {
                replyTo = replyMessenger
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_REQUEST_ID, id)
                        putString(NappletIpc.KEY_PAYLOAD, raw)
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, launchToken)
                    }
            }

        // In the background: an act on the user's behalf waits until they're looking at this napplet again.
        if (!resumed && NappletActingRequests.actsForUser(runCatching { NappletProtocolJson.readType(raw) }.getOrNull())) {
            val refused = heldWhilePaused.hold(msg)
            if (refused != null) {
                bridgeReplyProxy?.failRequest(refused, NappletHeldRequests.TOO_MANY)
            } else {
                // Settle it with an error if nobody comes back for it, so the applet isn't left waiting forever.
                backgroundPauseHandler.postDelayed(expireHeld, NappletHeldRequests.MAX_AGE_MS)
            }
            return
        }
        val messenger = brokerMessenger
        if (messenger == null) {
            pendingRequests.add(msg)
        } else {
            sendToBroker(msg)
        }
    }

    private fun sendToBroker(msg: Message) {
        try {
            brokerMessenger?.send(msg)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deliver request to broker", e)
        }
    }

    private fun onBrokerReply(msg: Message): Boolean {
        val data = msg.data ?: return true
        when (msg.what) {
            NappletIpc.MSG_RESPONSE -> {
                val id = data.getString(NappletIpc.KEY_REQUEST_ID) ?: return true
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true

                // payload is the broker's {type:"...result", ok, ...}; inject the correlation id for the shim.
                val result = (parseJsonObjectOrNull(payload) ?: JsonObject(emptyMap())).withString("id", id)
                // The broker authorized a keyboard action: bind the honored key combo so dispatchKeyEvent
                // can fire it. Only ok'd registrations bind (a denied KEYS request never reaches here).
                if (result.stringOrNull("type") == "keys.registerAction.result" && result.booleanOrNull("ok") == true) {
                    val actionId = result.stringOrNull("actionId").orEmpty()
                    if (actionId.isNotEmpty()) keyActions.register(actionId, result.stringOrNull("binding")?.ifEmpty { null })
                }
                notifyIfSensitive(result)
                bridgeReplyProxy?.postMessage(result.toString())
            }
            // A subscription push (relay.event/relay.eose) is keyed by subId, not a request id; forward verbatim.
            NappletIpc.MSG_PUSH -> {
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                bridgeReplyProxy?.postMessage(payload)
            }
            else -> return false
        }
        return true
    }

    // ---- loading / unavailable screens ----

    /** A monogram tile (first letter of the title on a colored rounded square), matching the card. */
    private fun monogram(sizeDp: Int): TextView =
        TextView(this).apply {
            text = barTitle().trim().take(1).uppercase()
            setTextColor(resolveThemeColor(android.R.attr.textColorPrimaryInverse))
            textSize = (sizeDp / 2.4f)
            gravity = Gravity.CENTER
            val bg =
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(resolveThemeColor(android.R.attr.colorPrimary))
                }
            background = bg
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }

    private fun centeredColumn(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            // Opaque so the splash/error screen fully covers the WebView it now overlays (mounted beneath
            // it until first paint) instead of letting the dark, not-yet-painted page show through.
            setBackgroundColor(resolveThemeColor(android.R.attr.colorBackground))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

    private fun buildLoadingView(): View =
        centeredColumn().apply {
            addView(monogram(72))
            addView(spacer(dp(20)))
            addView(
                TextView(this@NappletHostActivity).apply {
                    text = barTitle()
                    setTextColor(resolveThemeColor(android.R.attr.textColorPrimary))
                    textSize = 20f
                    gravity = Gravity.CENTER
                },
            )
            addView(spacer(dp(20)))
            addView(ProgressBar(this@NappletHostActivity).also { loadingSpinner = it })
            addView(
                ProgressBar(this@NappletHostActivity, null, android.R.attr.progressBarStyleHorizontal)
                    .apply {
                        max = DOWNLOAD_BAR_MAX
                        visibility = View.GONE
                        layoutParams = LinearLayout.LayoutParams(dp(240), LinearLayout.LayoutParams.WRAP_CONTENT)
                    }.also { downloadBar = it },
            )
            addView(
                TextView(this@NappletHostActivity)
                    .apply {
                        setTextColor(resolveThemeColor(android.R.attr.textColorSecondary))
                        textSize = 13f
                        gravity = Gravity.CENTER
                        visibility = View.GONE
                        setPadding(0, dp(8), 0, 0)
                    }.also { downloadLabel = it },
            )
        }

    /** The bar and its sizes while a download runs; the spinner before and after it. */
    private fun showDownload(download: NappletDownloadProgress?) {
        val bar = downloadBar ?: return
        val fraction = download?.fraction
        loadingSpinner?.visibility = if (download == null) View.VISIBLE else View.GONE
        bar.visibility = if (download == null) View.GONE else View.VISIBLE
        // No size from the server: the bar still moves, and the label counts the bytes.
        bar.isIndeterminate = download != null && fraction == null
        if (fraction != null) bar.progress = (fraction * DOWNLOAD_BAR_MAX).toInt()
        downloadLabel?.let {
            it.visibility = if (download == null) View.GONE else View.VISIBLE
            it.text = download?.label()
        }
    }

    private fun buildErrorView(onRetry: () -> Unit): View =
        centeredColumn().apply {
            addView(
                TextView(this@NappletHostActivity).apply {
                    text = "⚠"
                    textSize = 44f
                    gravity = Gravity.CENTER
                },
            )
            addView(spacer(dp(12)))
            addView(
                TextView(this@NappletHostActivity).apply {
                    text = getString(R.string.napplet_unavailable_title, barTitle())
                    setTextColor(resolveThemeColor(android.R.attr.textColorPrimary))
                    textSize = 18f
                    gravity = Gravity.CENTER
                },
            )
            addView(spacer(dp(8)))
            addView(
                TextView(this@NappletHostActivity).apply {
                    text = getString(R.string.napplet_unavailable_subtitle)
                    setTextColor(resolveThemeColor(android.R.attr.textColorSecondary))
                    textSize = 14f
                    gravity = Gravity.CENTER
                },
            )
            addView(spacer(dp(20)))
            addView(
                Button(this@NappletHostActivity).apply {
                    text = getString(R.string.napplet_unavailable_retry)
                    setOnClickListener { onRetry() }
                },
            )
        }

    private fun spacer(heightPx: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, heightPx) }

    // ---- trusted sandbox chrome ----

    private fun barTitle(): String = title.ifBlank { getString(CommonsR.string.napplet_untitled) }

    /**
     * The trusted pull-down pill: a small grabber at the top edge (out of the corner where the app shows its
     * own avatar) that expands to the sandbox **shield**, the nSite network/Tor row (website mode only, taps
     * through to a relaunch), reload, find, text size and "what it can access". The applet can't draw over
     * it. The same Compose components as the embedded tabs and the web browser.
     */
    private fun buildChrome(): BrowserChromeHost =
        BrowserChromeHost(
            activity = this,
            dark = isDarkTheme(),
            initial =
                BrowserPillUi(
                    title = barTitle(),
                    chrome =
                        BrowserChrome.State(
                            surface = if (profile == HostProfile.WEBSITE) BrowserChrome.Surface.NSITE else BrowserChrome.Surface.NAPPLET,
                            presentation = BrowserChrome.Presentation.FULL_SCREEN,
                            url = "",
                            startUrl = "",
                            // Website-mode nSites can re-route over Tor; switching rebuilds the session, so the
                            // row taps through to a full relaunch rather than toggling inline.
                            torOn = if (profile.exposesNetwork && proxyPort > 0) useTor else null,
                            torForced = !useTor && routedOverTor,
                            canFavorite = false,
                            hasAccessInfo = true,
                        ),
                ),
            listener = chromeListener,
        )

    private fun isDarkTheme(): Boolean =
        when (themeType) {
            "DARK" -> true
            "LIGHT" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

    private fun liveWebView(): WebView? = if (this::webView.isInitialized && !webViewGone) webView else null

    private val chromeListener =
        object : BrowserChromeHost.Listener {
            override fun onPillEvent(event: BrowserPillEvent) {
                when (event) {
                    is BrowserPillEvent.Action ->
                        when (event.action) {
                            BrowserChrome.Action.RELOAD -> liveWebView()?.reload()
                            BrowserChrome.Action.STOP -> liveWebView()?.stopLoading()
                            BrowserChrome.Action.TOR -> setNetworkMode(!useTor)
                            BrowserChrome.Action.ACCESS_INFO -> showAccessDialog()
                            BrowserChrome.Action.SITE_SETTINGS -> openPermissions()
                            else -> Unit
                        }
                    is BrowserPillEvent.TextZoom -> {
                        liveWebView()?.let { BrowserWebTools.setTextZoom(it, event.percent) }
                        chrome?.let { it.ui = it.ui.copy(textZoom = event.percent) }
                    }
                    BrowserPillEvent.PageInfo -> showAccessDialog()
                    BrowserPillEvent.Close -> finish()
                    else -> Unit
                }
            }

            override fun onFind(query: String) {
                val wv = liveWebView() ?: return
                if (query.isEmpty()) wv.clearMatches() else wv.findAllAsync(query)
            }

            override fun onFindNext(forward: Boolean) {
                liveWebView()?.findNext(forward)
            }

            override fun onFindClosed() {
                liveWebView()?.clearMatches()
            }

            override fun onPermissionChange(
                permission: BrowserSitePermission,
                decision: BrowserSitePermission.Decision,
            ) = Unit

            override fun onClearSiteData() = Unit

            override fun onPanelsChanged() = syncBackState()
        }

    /**
     * Ask the broker to open this napplet's editable permission screen. The sandbox can't state its own
     * coordinate, so we send only the launch token; the broker resolves it to the trusted coordinate and
     * launches the main activity at the Connected Apps detail.
     */
    private fun openPermissions() {
        val msg =
            Message.obtain(null, NappletIpc.MSG_OPEN_PERMISSIONS).apply {
                data = Bundle().apply { putString(NappletIpc.KEY_LAUNCH_TOKEN, launchToken) }
            }
        if (brokerMessenger != null) sendToBroker(msg)
    }

    /** Persists the new routing choice in the main process, then relaunches this screen to apply it. */
    private fun setNetworkMode(newUseTor: Boolean) {
        val msg =
            Message.obtain(null, NappletIpc.MSG_SET_NETWORK_MODE).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, launchToken)
                        putBoolean(NappletIpc.KEY_NETWORK_USE_TOR, newUseTor)
                    }
            }
        sendToBroker(msg)
        useTor = newUseTor
        intent.putExtra(NappletHostContract.EXTRA_USE_TOR, newUseTor)
        recreate()
    }

    /** Lists, in plain language, exactly which capabilities this napplet was launched with. */
    private fun showAccessDialog() {
        chrome?.showAccessInfo(
            BrowserChromeHost.AccessInfo(
                title = barTitle(),
                isWebsite = profile == HostProfile.WEBSITE,
                capabilities = capabilityLabels,
                torOn = if (profile.exposesNetwork && proxyPort > 0) useTor else null,
                onManagePermissions = if (brokerMessenger != null) ::openPermissions else null,
            ),
        )
    }

    /**
     * Surfaces an "allow always" capability acting on the user's behalf, so a granted RELAY/UPLOAD/VALUE
     * op can never run completely silently. Read-only ops (identity/storage/resource) don't toast.
     */
    private fun notifyIfSensitive(result: JsonObject) {
        if (result.booleanOrNull("ok") != true) return
        val message =
            when (result.stringOrNull("type")) {
                "relay.publish.result", "relay.publishEncrypted.result" -> getString(R.string.napplet_action_published, barTitle())
                "upload.upload.result" -> getString(R.string.napplet_action_uploaded, barTitle())
                "value.payInvoice.result" -> getString(R.string.napplet_action_paid, barTitle())
                else -> return
            }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun resolveThemeColor(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "NappletHostActivity"

        // The bar's resolution: a step per 0.1%.
        private const val DOWNLOAD_BAR_MAX = 1000

        /**
         * How often a resumed host renews its foreground lease with the broker. Comfortably shorter than
         * the broker's lease TTL so a couple of dropped/delayed beats don't expire a still-foreground
         * surface, while a dead process (heartbeat stopped) is reaped within the TTL.
         */
        const val FOREGROUND_HEARTBEAT_MS = 30_000L
    }
}
