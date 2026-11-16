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

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.TypedValue
import android.view.ContextMenu
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isEmpty
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.Action
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission.Decision
import com.vitorpamplona.amethyst.commons.browser.DownloadCooldown
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.browser.PageLoadFailure
import com.vitorpamplona.amethyst.commons.browser.SearchEngines
import com.vitorpamplona.amethyst.commons.browser.ui.PageLoadError
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserChromeTheme
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPillEvent
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPillUi
import com.vitorpamplona.amethyst.commons.browser.ui.pill.ConsoleLine
import com.vitorpamplona.amethyst.commons.browser.ui.pill.PageDialogType
import com.vitorpamplona.amethyst.commons.napplet.BrowserOriginPolicy
import com.vitorpamplona.amethyst.commons.napplet.NappletActingRequests
import com.vitorpamplona.amethyst.commons.napplet.NappletBridgeDocuments
import com.vitorpamplona.amethyst.commons.napplet.NappletHeldRequests
import com.vitorpamplona.amethyst.commons.napplet.NappletProxyClaims
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.NappletWebResources
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.util.parseJsonObjectOrNull
import com.vitorpamplona.amethyst.commons.util.stringOrNull
import com.vitorpamplona.amethyst.commons.util.withString
import com.vitorpamplona.quartz.utils.Log
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * Full-screen **direct-WebView** browser for an arbitrary URL, running in the keyless `:napplet`
 * process — Amethyst's equivalent of an installed Chrome PWA window. Unlike the embedded browser
 * ([NappletBrowserService], which streams its surface to the main app through SurfaceControlViewHost — a
 * path that, on current Android, forwards taps but drops scroll/zoom/keyboard gestures), this hosts the
 * WebView **directly** in its own window, so scrolling, pinch zoom, and the soft keyboard all work natively
 * — the window insets the content for the IME itself (see [applyFullScreenHostInsets]). It stays just as
 * keyless: the page JS runs here, every NIP-07 `window.nostr` call is brokered + consent-gated in the main
 * process per origin, and the keys never leave it.
 *
 * PWA behaviours, beyond the page itself: its own task in Recents titled, iconed and coloured after the
 * site ([updateTaskDescription]); system bars tinted with the page's `theme-color`; the top pill
 * ([BrowserChromeHost], laid out by [BrowserChrome]); JS dialogs; new windows (`_blank` / `window.open`)
 * as new browser windows with `opener` intact ([BrowserPopups]); downloads; HTML fullscreen video;
 * camera / microphone / location behind a per-site prompt; find in page; long-press link and image menus;
 * Web Share; and recovery from a renderer crash.
 */
class NappletBrowserActivity : ComponentActivity() {
    private var webView: WebView? = null

    private var startUrl: String = BLANK_PAGE
    private var proxyPort: Int = -1
    private var useTor: Boolean = true
    private var themeType: String = "SYSTEM"
    private var webViewProfile: String? = null

    // SearchEngines id from the main process (the sandbox can't read the user's settings itself).
    private var searchEngine: String? = null

    // Key for this window's foreground lease with the broker; stable for the Activity's life.
    private var leaseKey: String = ""

    private val contentFrame by lazy { FrameLayout(this) }
    private var root: FrameLayout? = null
    private var loadingView: View? = null
    private var crashView: View? = null

    /** Our own page over the WebView's built-in one after a main-frame load failure; see [showLoadError]. */
    private var loadErrorView: View? = null
    private var loadError by mutableStateOf<LoadErrorState?>(null)

    private data class LoadErrorState(
        val failure: PageLoadFailure,
        val host: String,
        val detail: String?,
        val viaTor: Boolean,
        /** Whether flipping this page's Tor choice could change the outcome; see [canSwitchTor]. */
        val canSwitchTor: Boolean,
        val retrying: Boolean = false,
    )

    private var resumed = false

    // The back-stack slot the last history record came from. A page that finishes in the same slot of the
    // same WebView replaced that page in place (a JS redirect, a rewritten query), so it is the same visit.
    private var lastRecordedView: WebView? = null
    private var lastRecordedIndex = -1
    private var lastRecordedUrl: String? = null

    // The pill, find, console and page dialogs — the shared Compose chrome (see BrowserChromeHost).
    private var chrome: BrowserChromeHost? = null

    // The NIP-07 shim + browser extras, injected at document start in this window and in its popups.
    private var shimJs: String = ""

    // Visit-history gating: only a clean main-frame load (no error) is recorded, so a misspelled/
    // unresolved address never enters history. Reset on each main-frame page start.
    private var pendingMainFrameUrl: String? = null
    private var mainFrameLoadFailed = false
    private var lastIconHost: String? = null

    // The last URL whose pin state was asked of the broker, so the several page callbacks that report the
    // same address don't each trigger a round-trip.
    private var lastFavoriteQueryUrl: String? = null

    /** The origin whose site permissions the pill is already showing. */
    private var lastSitePermissionOrigin: String? = null

    // What Recents shows for this task: the page's title, favicon and theme colour.
    private var pageTitle: String? = null
    private var pageIcon: Bitmap? = null
    private var themeColor: Int? = null

    // HTML fullscreen (a video's fullscreen button): the view WebView hands us, drawn over the whole window.
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Page-originated alert/confirm/prompt/beforeunload: from the second dialog on a page, the user may block
    // the rest until the next main-frame navigation (Chrome's rule), so a looping alert() can't trap them.
    private var jsDialogsOnPage = 0
    private var jsDialogsBlocked = false

    // ---- HTML file input (`<input type="file">`) ----
    // Registered as a field so it is in place before onCreate returns, which is what
    // registerForActivityResult requires. This activity hosts its WebView directly, so it can run the
    // picker itself; the embedded surfaces have no Activity and route theirs through the main process.
    private val pendingFileChooser = PendingFileChooser()

    private val fileChooserLauncher = WebFileChooserLauncher(this) { uris -> pendingFileChooser.deliver(uris) }

    // ---- site permissions (camera / microphone / location) ----
    private val sitePermissionQueries = mutableMapOf<Long, (Map<BrowserSitePermission, Decision>) -> Unit>()
    private var sitePermissionSeq = 0L
    private var pendingRuntimeGrant: ((Map<String, Boolean>) -> Unit)? = null
    private val runtimePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            pendingRuntimeGrant?.invoke(result)
            pendingRuntimeGrant = null
        }

    // ---- broker bridge (per-origin NIP-07 tokens; identical to NappletBrowserService) ----
    private var brokerMessenger: Messenger? = null

    /**
     * Reply channel handed to the broker. It MUST NOT hold this Activity strongly.
     *
     * A [Messenger] sent over IPC is a binder: while the main process holds it, ART keeps a **JNI global
     * reference** to the backing [Handler] here in `:napplet`. A `Handler(looper, ::onBrokerReply)` makes
     * the Activity the handler's `mCallback` (a bound method reference captures `this`), so that one
     * retained binder pinned Activity → PhoneWindow → DecorView → WebView past `onDestroy`, and no GC in
     * this process could ever reclaim it — only killing the process could. Measured: one full-screen page
     * opened and closed left a destroyed Activity plus its WebView alive through repeated forced GCs.
     *
     * Holding the Activity weakly severs that chain at the source, so even a broker that never processes
     * [NappletIpc.MSG_RELEASE_CLIENT] (see [onDestroy]) cannot leak a surface. Messages arriving after
     * destruction are dropped, which is correct: there is nothing left to deliver them to.
     */
    private val replyMessenger = Messenger(WeakBrokerReplyHandler(this))

    private class WeakBrokerReplyHandler(
        activity: NappletBrowserActivity,
    ) : Handler(Looper.getMainLooper()) {
        private val ref = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            ref.get()?.onBrokerReply(msg)
        }
    }

    private val pendingBrokerRequests = mutableListOf<Message>()

    // The page on screen's bridge reply proxy, and which document each broker reply belongs to: a reply
    // for a page the user has navigated away from must never land in the next one.
    private val bridge = NappletBridgeDocuments<JavaScriptReplyProxy>()
    private var fireSeq = 0
    private val originTokens = mutableMapOf<String, String>()
    private val pendingByOrigin = mutableMapOf<String, MutableList<Message>>()
    private val mintInFlight = mutableSetOf<String>()
    private val downloadCooldown = DownloadCooldown()
    private var preparingDownload = false

    // The page's requests that act for the user (NIP-07 sign / encrypt / decrypt) made while this window was in
    // the background, as (origin, request): sent on the next resume, so a site can't sign — even with
    // "allow always" — while nobody is looking at it.
    private val heldWhileAway = NappletHeldRequests<Pair<String, Message>>(SystemClock::elapsedRealtime)

    /**
     * Back walks out of fullscreen video, then the find bar, then the page's history, then leaves. Enabled
     * only while one of those applies, so the system back (and its predictive animation) otherwise acts
     * on the window itself.
     */
    private val backCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val wv = webView
                when {
                    customView != null -> exitFullscreen()
                    chrome?.handleBack() == true -> Unit
                    wv != null && wv.canGoBack() -> wv.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
                syncBackState()
            }
        }

    private fun syncBackState() {
        backCallback.isEnabled = customView != null || chrome?.wantsBack == true || webView?.canGoBack() == true
    }

    private val brokerConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                brokerMessenger = Messenger(service)
                pendingBrokerRequests.forEach { sendToBroker(it) }
                pendingBrokerRequests.clear()
                if (resumed) setBrokerForeground(true)
                reportAttended()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                brokerMessenger = null
            }
        }

    // The chrome sees touches first, so a swipe down on its handle opens it over the page.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = chrome?.dispatchTouchEvent(ev) { super.dispatchTouchEvent(it) } ?: super.dispatchTouchEvent(ev)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SandboxComposeResources.ensure(this)

        // A new window a page opened: its WebView already exists (built inside the opener's onCreateWindow).
        val popupToken = intent.getStringExtra(EXTRA_POPUP_TOKEN)
        val popup = BrowserPopups.take(popupToken)
        if (popupToken != null && popup == null) {
            finish()
            return
        }

        if (popup != null) {
            proxyPort = popup.proxyPort
            useTor = popup.useTor
            themeType = popup.themeType
            webViewProfile = popup.webViewProfile
            searchEngine = popup.searchEngine
            leaseKey = "popup:$popupToken"
        } else {
            startUrl = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() } ?: run {
                finish()
                return
            }
            proxyPort = intent.getIntExtra(EXTRA_PROXY_PORT, -1)
            useTor = intent.getBooleanExtra(EXTRA_USE_TOR, true)
            themeType = intent.getStringExtra(EXTRA_THEME).orEmpty().ifBlank { "SYSTEM" }
            webViewProfile = intent.getStringExtra(NappletHostContract.EXTRA_WEBVIEW_PROFILE)
            searchEngine = intent.getStringExtra(EXTRA_SEARCH_ENGINE)
            leaseKey = startUrl
        }
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        // Fail closed: Tor is on but its port isn't known yet (still starting). This window can't learn it
        // later, so refuse now rather than sit blank — or go out on the open web.
        if (popup == null && useTor && proxyPort <= 0) {
            Toast.makeText(this, R.string.napplet_route_blocked, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            Toast.makeText(this, getString(R.string.napplet_webview_too_old), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        shimJs = readContractAsset(NappletWebResources.SHIM_JS_PATH).decodeToString()
        // Which route is really in effect: an open-web page goes through Tor while another page needs it.
        WebViewProxyPolicy.observeRoute(this) {
            routedOverTor = it
            updateChromeState { copy(torForced = !useTor && it) }
        }
        // A popup's WebView is already loading: its route is claimed now, not before a load.
        if (popup != null) claimRoute()

        bindService(Intent().setClassName(this, NappletHostContract.BROKER_SERVICE_CLASS), brokerConnection, BIND_AUTO_CREATE)
        onBackPressedDispatcher.addCallback(this, backCallback)

        val root =
            FrameLayout(this).apply {
                setBackgroundColor(resolveThemeColor(android.R.attr.colorBackground))
                addView(contentFrame, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
        this.root = root
        chrome = buildChrome().also { it.attach(root) }
        setContentView(root)
        // Pad by the system bars + cutout AND the IME: on an edge-to-edge window (enforced for targetSdk
        // 35+ on Android 15+) windowSoftInputMode=adjustResize no longer shrinks the window, so without
        // this the keyboard covers the bottom of the page. See applyFullScreenHostInsets. The root's own
        // background shows through that padding, which is how the page's theme-color tints the bars.
        root.applyFullScreenHostInsets()

        val wv = buildWebView(popup)
        contentFrame.addView(wv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (popup == null) {
            if (!guardOnion(wv, startUrl, isMainFrame = true)) {
                loadingView = buildLoadingView().also { contentFrame.addView(it) }
                // Wait for this page's route to be in effect before the first request leaves.
                claimRoute { if (webView === wv) wv.loadUrl(startUrl) }
            }
        } else {
            wv.url?.let { if (it.isNotBlank() && it != BLANK_PAGE) startUrl = it }
        }
        updateTaskDescription()
    }

    /**
     * Builds (or, for a popup, adopts) this window's WebView and wires every client and bridge. Also used
     * to rebuild after a renderer crash.
     */
    private fun buildWebView(popup: BrowserPopups.Pending? = null): WebView {
        val wv: WebView
        if (popup != null) {
            wv = popup.webView
            // The popup was built on a placeholder context; point it at this Activity now.
            popup.context.baseContext = nightThemedContext(this, themeType)
        } else {
            // Build the WebView from a context forced to the app theme so its content follows DARK/LIGHT
            // even when the device theme differs (WebView reads the context's theme, not the window's).
            wv = WebView(nightThemedContext(this, themeType))
            // FIRST touch after construction: setProfile throws once the WebView has loaded content (or
            // its profile has otherwise been used), so the storage partition must be chosen first.
            NappletWebViewProfile.apply(this, wv, webViewProfile)
        }
        BrowserWebTools.applyBrowserSettings(wv)
        NappletWebRtcGuard.sync(wv, useTor)
        wv.webViewClient = BrowserClient()
        wv.webChromeClient = BrowserChromeClient()
        wv.setFindListener { active, total, _ -> chrome?.setFindResult(active, total) }
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            // The page's origin; a fresh popup still on about:blank has none, so name the file's own.
            val origin =
                chrome
                    ?.ui
                    ?.chrome
                    ?.url
                    ?.let(BrowserChrome::originOf) ?: BrowserChrome.originOf(url) ?: return@setDownloadListener
            if (!canOfferDownload(origin)) return@setDownloadListener
            // Read on the main thread: the cookie jar is the tab's own per-account WebView profile.
            val cookie = BrowserWebTools.cookieManager(wv).getCookie(url)
            prepareDownloadOffer(origin) { ready ->
                BrowserDownloads.offerListenerDownload(url, userAgent, contentDisposition, mimeType, contentLength, cookie, if (useTor) proxyPort else -1, ready)
            }
        }
        wv.setBackgroundColor(resolveThemeColor(android.R.attr.colorBackground))
        wv.dropSystemBarInsets()
        registerForContextMenu(wv)

        if (popup != null) {
            popup.adopt(::onBridgeMessage)
        } else {
            // NIP-07 over the direct bridge (no shell): the shim talks to native at document start for
            // every origin; the broker scopes consent per visited origin.
            WebViewCompat.addWebMessageListener(wv, NappletWebContract.BRIDGE_NAME, setOf("*"), ::onBridgeMessage)
            WebViewCompat.addDocumentStartJavaScript(wv, BrowserWebTools.browserStartScript(shimJs, imeProxy = false), setOf("*"))
        }
        webView = wv
        return wv
    }

    // Renews the broker's foreground lease while resumed; without it the broker's watchdog would reap the
    // lease (tearing down Tor/relays) while the browser is still genuinely foreground.
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeat =
        object : Runnable {
            override fun run() {
                setBrokerForeground(true)
                heartbeatHandler.postDelayed(this, FOREGROUND_HEARTBEAT_MS)
            }
        }

    private val expireHeld =
        Runnable {
            if (!isDestroyed) heldWhileAway.expire().forEach { (_, request) -> bridge.failRequest(request, NappletHeldRequests.EXPIRED) }
        }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        resumed = true
        reportAttended()
        val held = heldWhileAway.drain()
        held.fail.forEach { (_, request) -> bridge.failRequest(request, NappletHeldRequests.EXPIRED) }
        held.send.forEach { (origin, request) -> dispatchToBroker(origin, request) }
        heartbeatHandler.removeCallbacks(heartbeat)
        heartbeat.run()
    }

    override fun onStart() {
        super.onStart()
        // Back within the grace: the page was never paused.
        heartbeatHandler.removeCallbacks(backgroundPause)
    }

    override fun onStop() {
        // Out of sight: pause the page after the same grace the rest of the app gets
        // (NappletHostContract.BACKGROUND_PAUSE_MS), so a quick trip to another app doesn't interrupt it.
        // NIP-07 sign / encrypt / decrypt is already held while not resumed (see [heldWhileAway]).
        heartbeatHandler.postDelayed(backgroundPause, NappletHostContract.BACKGROUND_PAUSE_MS)
        super.onStop()
    }

    // Only pause THIS activity's WebView (onPause is per-WebView). Do NOT call pauseTimers(): it is
    // process-global — it freezes JS/layout/parsing timers for EVERY WebView in `:napplet`, including the
    // embedded ones in NappletBrowserService, which have no resume of their own. That left the embed frozen
    // (dead page/connection) after returning from a full-screen excursion.
    private val backgroundPause = Runnable { if (!isDestroyed) webView?.onPause() }

    override fun onPause() {
        resumed = false
        reportAttended()
        heartbeatHandler.removeCallbacks(heartbeat)
        setBrokerForeground(false)
        super.onPause()
    }

    override fun onDestroy() {
        heartbeatHandler.removeCallbacks(backgroundPause)
        heartbeatHandler.removeCallbacks(expireHeld)
        // Tell the broker to drop every reference to our reply Messenger BEFORE unbinding — a retained
        // Messenger is a binder, and it would pin this Activity (and its WebView) in `:napplet` for the
        // life of the process. `unbindService` alone does not release it. See [replyMessenger].
        releaseFromBroker()
        WebViewProxyPolicy.release(this)
        runCatching { unbindService(brokerConnection) }
        // A picker still up when the browser is torn down would otherwise leave its callback unanswered.
        pendingFileChooser.cancel()
        fileChooserLauncher.teardown()
        // A dialog still up would leak its window and leave the page's JS blocked on an unanswered result.
        chrome?.dialog?.answer?.invoke(false, null, false)
        chrome?.permissionPrompt?.answer?.invoke(false, false)
        chrome?.downloadPrompt?.answer?.invoke(false)
        customViewCallback?.onCustomViewHidden()
        destroyWebView()
        super.onDestroy()
    }

    /**
     * Detaches, then destroys, the WebView. Destroying a WebView while it is still attached to the window
     * corrupts the SHARED multiprocess renderer/network state, which then breaks the OTHER (embedded)
     * WebViews living in this `:napplet` process: dead DNS (ERR_NAME_NOT_RESOLVED), DOM reads returning
     * empty, dead selection-highlight paint, and broken IME — all after a full-screen excursion returns to
     * an embed. (`destroy()` requires the view to be removed from the hierarchy first.)
     */
    private fun destroyWebView() {
        val wv = webView ?: return
        webView = null
        unregisterForContextMenu(wv)
        wv.stopLoading()
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.destroy()
    }

    /** Reports foreground state to the broker so the main process stays resumed (Tor/relays/AUTH). */
    private fun setBrokerForeground(foreground: Boolean) {
        val msg =
            Message.obtain(null, NappletIpc.MSG_SET_FOREGROUND).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, leaseKey)
                        putBoolean(NappletIpc.KEY_FOREGROUND, foreground)
                    }
            }
        if (brokerMessenger != null) sendToBroker(msg)
    }

    /**
     * Asks the broker to drop every reference it holds to [replyMessenger] (inc-bus subscriptions and this
     * surface's foreground lease). Sent directly rather than through [sendToBroker] because that queues
     * when the broker is unbound — and we are being destroyed, so a queued release would never be sent.
     */
    private fun releaseFromBroker() {
        val broker = brokerMessenger ?: return
        val msg =
            Message.obtain(null, NappletIpc.MSG_RELEASE_CLIENT).apply {
                replyTo = replyMessenger
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, leaseKey)
                        // This activity's per-origin tokens die with it (a recreated activity mints its own).
                        if (originTokens.isNotEmpty()) putStringArray(NappletIpc.KEY_RELEASED_TOKENS, originTokens.values.toTypedArray())
                    }
            }
        runCatching { broker.send(msg) }
    }

    private fun currentUrl(): String =
        webView?.url?.takeIf { it.isNotBlank() } ?: chrome
            ?.ui
            ?.chrome
            ?.url
            ?.takeIf { it.isNotBlank() } ?: startUrl

    /** Updates what the chrome shows. */
    private inline fun updateUi(block: BrowserPillUi.() -> BrowserPillUi) {
        chrome?.let { it.ui = it.ui.block() }
    }

    private inline fun updateChromeState(crossinline block: BrowserChrome.State.() -> BrowserChrome.State) = updateUi { copy(chrome = chrome.block()) }

    /** Captures favicon, title and console output, and hosts every page-initiated UI. */
    private inner class BrowserChromeClient : WebChromeClient() {
        /**
         * Without this override the base implementation returns false and WebView shows nothing at all, so
         * every `<input type="file">` in the browser is a dead tap. Always returns true: we take ownership
         * of the callback, and [showFileChooser] guarantees it is answered on every path.
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

        override fun onReceivedTitle(
            view: WebView,
            title: String?,
        ) {
            pageTitle = title?.trim()?.takeIf { it.isNotEmpty() && it != view.url }
            updateUi { copy(title = pageTitle ?: BrowserChrome.displayHost(chrome.url)) }
            updateTaskDescription()
        }

        override fun onReceivedIcon(
            view: WebView,
            icon: Bitmap?,
        ) {
            if (icon == null || mainFrameLoadFailed) return
            pageIcon = icon
            updateTaskDescription()
            val host = OmniboxInput.hostOf(view.url ?: return) ?: return
            // De-dupe: a page can fire this several times — store once per host per visit.
            if (host == lastIconHost) return
            lastIconHost = host
            recordIcon(host, icon)
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            val host = chrome ?: return false
            host.appendConsole(
                ConsoleLine(BrowserChromeHost.levelOf(consoleMessage.messageLevel()), consoleMessage.message(), consoleMessage.sourceId(), consoleMessage.lineNumber()),
            )
            return true
        }

        // The framework's own JS dialogs only appear when the WebView's context IS an Activity
        // (`JsDialogHelper.canShowAlertDialog`), and this one is built from [nightThemedContext] — a
        // configuration context, not the Activity — so without these overrides every alert() was silently
        // dismissed, confirm() always answered false and prompt() null. The chrome shows them itself.
        override fun onJsAlert(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean = showJsDialog(PageDialogType.ALERT, url, message, null, result)

        override fun onJsConfirm(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean = showJsDialog(PageDialogType.CONFIRM, url, message, null, result)

        override fun onJsPrompt(
            view: WebView,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean = showJsDialog(PageDialogType.PROMPT, url, message, defaultValue, result)

        override fun onJsBeforeUnload(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean = showJsDialog(PageDialogType.BEFORE_UNLOAD, url, message, null, result)

        /**
         * A `_blank` link or a user-initiated `window.open()`: open it as a new browser window, like a new
         * Chrome tab. Without a user gesture it's refused (Chrome's popup blocker).
         */
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            if (!isUserGesture) return false
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val (token, child) = BrowserPopups.create(this@NappletBrowserActivity, shimJs, proxyPort, useTor, themeType, webViewProfile, searchEngine)
            transport.webView = child
            resultMsg.sendToTarget()
            startActivity(popupIntent(this@NappletBrowserActivity, token))
            return true
        }

        /** `window.close()` from a window a page opened: close this window. */
        override fun onCloseWindow(window: WebView) {
            if (window === webView) finish()
        }

        override fun onPermissionRequest(request: PermissionRequest) = handlePermissionRequest(request)

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            chrome?.permissionPrompt = null
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback,
        ) {
            val siteOrigin = BrowserChrome.originOf(origin) ?: return callback.invoke(origin, false, false)
            resolveSitePermissions(siteOrigin, listOf(BrowserSitePermission.LOCATION)) { granted ->
                // Never let WebView remember it: the answer lives in the main-process registry.
                callback.invoke(origin, BrowserSitePermission.LOCATION in granted, false)
            }
        }

        override fun onGeolocationPermissionsHidePrompt() {
            chrome?.permissionPrompt = null
        }

        override fun onShowCustomView(
            view: View,
            callback: CustomViewCallback,
        ) = enterFullscreen(view, callback)

        override fun onHideCustomView() = exitFullscreen()
    }

    /**
     * Opens the system picker for a page's file input and routes the pick back to it. Returns true
     * unconditionally: the callback is ours from here on, and it is delivered on every path — a real
     * pick, a cancel, or a device with no app that can return a file (the input is released with null so
     * the user can tap it again after installing one).
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

    private inner class BrowserClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return guardOnion(view, uri.toString(), request.isForMainFrame)
            return BrowserWebTools.openExternal(this@NappletBrowserActivity, uri, request.hasGesture()) { view.loadUrl(it) }
        }

        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: Bitmap?,
        ) {
            // A fresh main-frame navigation: arm history gating and show the new address.
            pendingMainFrameUrl = url
            mainFrameLoadFailed = false
            // Keep the error page up while the next attempt loads: under it is the WebView's own error page.
            loadError?.let { loadError = it.copy(retrying = true) }
            // The page is being replaced: nothing it asked for (replies, subscription pushes) may reach the next
            // one, even a next one that never talks to the bridge.
            if (bridge.onNavigation()) releasePage()
            // A window a page opened takes its first real page as its home ("scope").
            if (startUrl == BLANK_PAGE && url.startsWith("http")) startUrl = url
            // Re-arm favicon capture when the host changes, so a same-host in-page nav doesn't re-send.
            if (OmniboxInput.hostOf(url) != lastIconHost) {
                lastIconHost = null
                pageIcon = null
                pageTitle = null
                themeColor = null
                applyThemeColor(null)
            }
            // Chrome scopes "block this page's dialogs" to the page: a new main-frame load lifts it.
            jsDialogsOnPage = 0
            jsDialogsBlocked = false
            updateChromeState { copy(isLoading = true) }
            showUrl(url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            // A main-frame failure (DNS miss on a misspelled host, no connection, …) disqualifies this
            // navigation from history. Sub-resource errors are irrelevant to whether the page opened.
            if (request.isForMainFrame) {
                mainFrameLoadFailed = true
                val description = error.description?.toString()
                val failedUrl = request.url?.toString() ?: view.url.orEmpty()
                PageLoadFailure.forPage(error.errorCode, description, failedUrl, routedOverTor)?.let { failure ->
                    showLoadError(failedUrl, failure, description)
                }
            }
            logConsoleError(request, getString(R.string.napplet_console_load_error, error.errorCode, error.description?.toString().orEmpty()))
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            logConsoleError(request, getString(R.string.napplet_console_http_error, errorResponse.statusCode, errorResponse.reasonPhrase.orEmpty()))
        }

        override fun onPageCommitVisible(
            view: WebView,
            url: String,
        ) {
            // The page has painted its first frame — drop the loading screen.
            loadingView?.let { contentFrame.removeView(it) }
            loadingView = null
            if (!mainFrameLoadFailed) hideLoadError()
            showUrl(url)
        }

        override fun doUpdateVisitedHistory(
            view: WebView,
            url: String,
            isReload: Boolean,
        ) {
            syncNavigation(view)
            showUrl(url)
        }

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            syncNavigation(view)
            updateChromeState { copy(isLoading = false) }
            showUrl(url)
            if (!mainFrameLoadFailed) hideLoadError()
            // Record only a clean http(s) main-frame load — never a typed-but-failed address.
            if (!mainFrameLoadFailed && (url.startsWith("https://") || url.startsWith("http://"))) {
                recordHistory(view, url, view.title)
                scheduleFaviconSniff(view, url)
            }
        }

        /**
         * The renderer died (crashed or was killed for memory). Every WebView in `:napplet` shares one
         * renderer, and returning false here would kill the whole process — taking every embedded tab with
         * it. So drop just this WebView and offer a reload, like Chrome's "Aw, Snap!".
         */
        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean {
            if (view !== webView) {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                return true
            }
            val lastUrl = view.url?.takeIf { it.startsWith("http") } ?: startUrl
            Log.w(TAG) { "Renderer gone (crashed=${detail.didCrash()}); offering a reload of $lastUrl" }
            exitFullscreen()
            destroyWebView()
            // The page died with its renderer: nothing is left to receive its replies or pushes.
            releasePage()
            bridge.clear()
            showCrashView(lastUrl)
            return true
        }
    }

    private fun syncNavigation(view: WebView) {
        syncBackState()
        updateChromeState { copy(canGoBack = view.canGoBack(), canGoForward = view.canGoForward()) }
    }

    /**
     * Pushes the displayed [url] into the chrome and, when it is a different page, asks the broker whether
     * it is pinned — the registry lives in the main process, so the star can't know on its own.
     */
    private fun showUrl(url: String) {
        updateUi {
            if (url == chrome.url) {
                this
            } else {
                // Another site: its host names it until its title arrives; its pin state is unknown until the
                // broker answers (the star toggle sends an explicit target, so a tap meanwhile can only add).
                val newSite = BrowserChrome.displayHost(url) != BrowserChrome.displayHost(chrome.url)
                copy(chrome = chrome.copy(url = url), title = if (newSite) BrowserChrome.displayHost(url) else title, isFavorite = false)
            }
        }
        // The pill's "Site settings" line summarises this origin's camera/mic/location answers, and the
        // registry that holds them lives in the main process. Until this was asked for on navigation, the
        // only thing that ever filled it was opening page info — so a window said "Nothing allowed yet"
        // about a site it had already been granted the camera on, and only stopped lying once you opened
        // the sheet that made it ask.
        BrowserChrome.originOf(url)?.let { origin ->
            if (origin != lastSitePermissionOrigin) {
                lastSitePermissionOrigin = origin
                querySitePermissions(origin) { decisions ->
                    updateUi { copy(sitePermissions = decisions.filterValues { it != Decision.ASK }) }
                }
            }
        }

        if (url == lastFavoriteQueryUrl) return
        lastFavoriteQueryUrl = url
        val msg =
            Message.obtain(null, NappletIpc.MSG_QUERY_WEB_FAVORITE).apply {
                replyTo = replyMessenger
                data = Bundle().apply { putString(NappletIpc.KEY_FAVORITE_URL, url) }
            }
        queueToBroker(msg)
    }

    /**
     * Second-chance favicon capture for pages `onReceivedIcon` never fires for (SVG-only declarations —
     * WebView does not rasterize those into the callback). Deliberately delayed so the WebView's own
     * raster path, which usually lands shortly after the page finishes, gets first claim on the host;
     * if it did, [lastIconHost] is already set and we skip out entirely.
     */
    private fun scheduleFaviconSniff(
        view: WebView,
        url: String,
    ) {
        val host = OmniboxInput.hostOf(url) ?: return
        view.postDelayed({
            if (view !== webView || mainFrameLoadFailed || host == lastIconHost || view.url != url) return@postDelayed
            NappletFaviconSniffer.capture(view) { sniffedHost, bytes ->
                if (sniffedHost == lastIconHost) return@capture
                lastIconHost = sniffedHost
                recordIconBytes(sniffedHost, bytes)
            }
        }, FAVICON_SNIFF_DELAY_MS)
    }

    /** Relays a successfully loaded page to the main-process broker for the device-local visit history. */
    private fun recordHistory(
        view: WebView,
        url: String,
        title: String?,
    ) {
        val index = view.copyBackForwardList().currentIndex
        val replaces = lastRecordedUrl?.takeIf { view === lastRecordedView && index == lastRecordedIndex && it != url }
        lastRecordedView = view
        lastRecordedIndex = index
        lastRecordedUrl = url

        val msg =
            Message.obtain(null, NappletIpc.MSG_RECORD_HISTORY).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_HISTORY_URL, url)
                        putString(NappletIpc.KEY_HISTORY_TITLE, title.orEmpty())
                        if (replaces != null) putString(NappletIpc.KEY_HISTORY_REPLACES, replaces)
                    }
            }
        queueToBroker(msg)
    }

    /** Scales [icon] down and relays it to the broker as the favicon for [host] (PNG bytes over IPC). */
    private fun recordIcon(
        host: String,
        icon: Bitmap,
    ) {
        val bytes =
            runCatching {
                val scaled = if (icon.width > ICON_MAX_PX || icon.height > ICON_MAX_PX) icon.scale(ICON_MAX_PX, ICON_MAX_PX) else icon
                ByteArrayOutputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray()
                }
            }.getOrNull() ?: return
        recordIconBytes(host, bytes)
    }

    /** Relays already-encoded icon bytes (PNG/ICO/… or SVG source) to the broker as [host]'s favicon. */
    private fun recordIconBytes(
        host: String,
        bytes: ByteArray,
    ) {
        val msg =
            Message.obtain(null, NappletIpc.MSG_RECORD_ICON).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_ICON_HOST, host)
                        putByteArray(NappletIpc.KEY_ICON_BYTES, bytes)
                    }
            }
        queueToBroker(msg)
    }

    /** Loads a user-typed address from "Edit address", forcing Tor for `.onion` when available. */
    private fun loadAddress(text: String) {
        val resolved = OmniboxInput.resolve(text, SearchEngines.byId(searchEngine).queryPrefix) ?: return
        if (resolved.forceTor && !useTor) {
            useTor = true
            webView?.let { NappletWebRtcGuard.sync(it, true) }
            updateChromeState { copy(torOn = true, torForced = false) }
        }
        // An onion must not leave before the Tor route it just claimed is in place.
        claimRoute { webView?.loadUrl(resolved.url) }
    }

    // ---- bridge: page <-> native (mirror of NappletBrowserService.onBridgeMessage) ----

    private fun onBridgeMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (!isMainFrame) return
        // A new document replaced the page: whatever the old one had open with the broker is dead.
        if (bridge.onMessage(replyProxy)) releasePage()
        val raw = message.data ?: return
        val envelope = parseJsonObjectOrNull(raw) ?: return

        // The origin the WebView reports, which the page can't forge, keys every decision below.
        val origin = trustedOrigin(sourceOrigin) ?: return

        // Browser conveniences (share, theme colour, blob downloads) are handled here, never brokered.
        // A download still asks the user first ([offerInlineDownload]): any top-frame script can post one.
        when (envelope.stringOrNull("type")) {
            "browser.share" -> {
                if (resumed) BrowserWebTools.share(this, envelope.stringOrNull("title"), envelope.stringOrNull("text"), envelope.stringOrNull("url"))
                return
            }
            "browser.themeColor" -> {
                themeColor = BrowserChrome.parseCssRgb(envelope.stringOrNull("color"))
                applyThemeColor(themeColor)
                updateTaskDescription()
                return
            }
            "browser.download" -> {
                offerInlineDownload(origin, envelope)
                return
            }
        }

        val pageId = envelope.stringOrNull("id").orEmpty().ifEmpty { "fire-${fireSeq++}" }
        val id = bridge.brokerIdFor(pageId)
        // A relay subscription is named by the page, and its pushes (decrypted events included) come back
        // under that name: stamp this document on it so the next page can never receive them.
        val outgoing = bridge.stampSubscription(envelope)?.toString() ?: raw
        val msg =
            Message.obtain(null, NappletIpc.MSG_REQUEST).apply {
                replyTo = replyMessenger
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_REQUEST_ID, id)
                        putString(NappletIpc.KEY_PAYLOAD, outgoing)
                    }
            }
        // A page on plain http could have had its script put there by anyone on the path, and a grant is kept
        // per origin: only an origin whose transport can't be tampered with gets window.nostr.
        if (!BrowserOriginPolicy.offersNostr(origin)) {
            bridge.failRequest(msg, "This page isn't secure (http): use its https address to sign in.")
            return
        }

        // In the background: a sign / encrypt / decrypt waits until the user is back on this window.
        if (!resumed && NappletActingRequests.actsForUser(runCatching { NappletProtocolJson.readType(raw) }.getOrNull())) {
            val refused = heldWhileAway.hold(origin to msg)
            if (refused != null) {
                bridge.failRequest(refused.second, NappletHeldRequests.TOO_MANY)
            } else {
                // Settle it with an error if nobody comes back for it, so the page isn't left waiting forever.
                heartbeatHandler.postDelayed(expireHeld, NappletHeldRequests.MAX_AGE_MS)
            }
            return
        }
        dispatchToBroker(origin, msg)
    }

    /** Sends [msg] with [origin]'s launch token, minting the token first if the origin has none yet. */
    private fun dispatchToBroker(
        origin: String,
        msg: Message,
    ) {
        val token = originTokens[origin]
        if (token != null) {
            msg.data.putString(NappletIpc.KEY_LAUNCH_TOKEN, token)
            queueToBroker(msg)
        } else {
            pendingByOrigin.getOrPut(origin) { mutableListOf() }.add(msg)
            requestBrowserToken(origin)
        }
    }

    /** `scheme://host[:port]` of the WebView-reported origin, or null when it has no usable one. */
    private fun trustedOrigin(sourceOrigin: Uri): String? {
        val scheme = sourceOrigin.scheme ?: return null
        val host = sourceOrigin.host ?: return null
        return "$scheme://$host" + if (sourceOrigin.port > 0) ":${sourceOrigin.port}" else ""
    }

    /**
     * A `browser.download` envelope: the bytes a page wants saved (a `blob:` download the extras script
     * read, or one a script forged). Keyed on the WebView-reported [origin], never an envelope field.
     */
    private fun offerInlineDownload(
        origin: String,
        envelope: JsonObject,
    ) {
        if (!canOfferDownload(origin)) return
        val data = envelope.stringOrNull("data") ?: return
        prepareDownloadOffer(origin) { ready -> BrowserDownloads.offerInline(data, envelope.stringOrNull("name"), ready) }
    }

    /**
     * Whether [origin] may put a download card up now: never over another page prompt (so a page can't swap
     * the name under the user's finger, or pop the card where a dialog's button just was), never while an
     * offer is still being prepared (so a page can't queue decodes), and not within its cooldown.
     */
    private fun canOfferDownload(origin: String): Boolean {
        val host = chrome ?: return false
        return host.downloadPrompt == null && host.dialog == null && host.permissionPrompt == null && !preparingDownload && downloadCooldown.allows(origin)
    }

    /** Runs [prepare] (which answers exactly once, on the main thread) and shows the offer it produces. */
    private fun prepareDownloadOffer(
        origin: String,
        prepare: ((BrowserDownloads.DownloadOffer?) -> Unit) -> Unit,
    ) {
        preparingDownload = true
        prepare { offer ->
            preparingDownload = false
            if (offer != null) showDownloadOffer(origin, offer)
        }
    }

    /** Shows [offer]'s consent card; the file is fetched or written only if the user taps Save. */
    private fun showDownloadOffer(
        origin: String,
        offer: BrowserDownloads.DownloadOffer,
    ) {
        val host = chrome ?: return
        if (isDestroyed || !canOfferDownload(origin)) return
        host.downloadPrompt =
            BrowserChromeHost.PendingDownload(
                host = BrowserChrome.displayHost(origin),
                security = BrowserChrome.security(host.ui.chrome),
                fileName = offer.fileName,
                sizeBytes = offer.sizeBytes,
                sourceHost = offer.sourceHost,
                risky = offer.risky,
            ) { allowed ->
                downloadCooldown.answered(origin, allowed)
                if (allowed) offer.save(this)
            }
    }

    private fun requestBrowserToken(origin: String) {
        if (!mintInFlight.add(origin)) return
        // The broker may never answer (it died mid-mint): fail the origin's queued calls rather than let the
        // page wait forever.
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDestroyed && origin in mintInFlight) failMint(origin, MINT_TIMED_OUT)
        }, NappletIpc.MINT_TIMEOUT_MS)
        val msg =
            Message.obtain(null, NappletIpc.MSG_MINT_BROWSER_TOKEN).apply {
                replyTo = replyMessenger
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_BROWSER_ORIGIN, origin)
                        putString(NappletIpc.KEY_WEBVIEW_PROFILE, webViewProfile)
                    }
            }
        queueToBroker(msg)
    }

    /**
     * The page is gone (navigated away, or its renderer died): drop its requests still waiting for a token
     * or the broker, and have the broker close the live relay / inc subscriptions it opened — otherwise
     * their events would keep streaming into whatever page comes next. Unlike [releaseFromBroker] this keeps
     * the surface's foreground lease: the activity itself is still up.
     */
    private fun releasePage() {
        pendingByOrigin.clear()
        heldWhileAway.clear()
        // A mint the broker never answered (none is sent while logged out) would otherwise block the
        // origin for good; the next page asks again.
        mintInFlight.clear()
        pendingBrokerRequests.removeAll { it.what == NappletIpc.MSG_REQUEST }
        val broker = brokerMessenger ?: return
        runCatching { broker.send(Message.obtain(null, NappletIpc.MSG_RELEASE_CLIENT).apply { replyTo = replyMessenger }) }
        // The release forgets this window's attendance along with the rest; the next page is watched the same.
        if (resumed) reportAttended()
    }

    /** Tells the broker whether this window is being looked at, which gates decrypting its relay reads. */
    private fun reportAttended() {
        queueToBroker(
            Message.obtain(null, NappletIpc.MSG_SET_ATTENDED).apply {
                replyTo = replyMessenger
                data = Bundle().apply { putBoolean(NappletIpc.KEY_ATTENDED, resumed) }
            },
        )
    }

    /** A token for [origin] won't come: answer each call queued behind it with a failure, and allow a retry. */
    private fun failMint(
        origin: String,
        reason: String,
    ) {
        mintInFlight.remove(origin)
        pendingByOrigin.remove(origin)?.forEach { queued -> bridge.failRequest(queued, reason) }
    }

    /** Sends now when the broker is bound, else queues until it is. */
    private fun queueToBroker(msg: Message) {
        if (brokerMessenger != null) sendToBroker(msg) else pendingBrokerRequests.add(msg)
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
            NappletIpc.MSG_TOKEN_UNKNOWN -> {
                // The broker no longer knows this token (evicted): forget it so the origin re-mints.
                val token = data.getString(NappletIpc.KEY_LAUNCH_TOKEN) ?: return true
                originTokens.values.removeAll { it == token }
            }
            NappletIpc.MSG_RESPONSE -> {
                val brokerId = data.getString(NappletIpc.KEY_REQUEST_ID) ?: return true
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                // Null when the page that asked has been navigated away from: drop it rather than hand
                // one site's answer (a signature, a decryption) to the next.
                val (pageId, proxy) = bridge.resolve(brokerId) ?: return true
                val result = (parseJsonObjectOrNull(payload) ?: JsonObject(emptyMap())).withString("id", pageId)
                runCatching { proxy.postMessage(result.toString()) }
            }
            NappletIpc.MSG_PUSH -> {
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                // Dropped when it is for a subscription a replaced document opened.
                val push = parseJsonObjectOrNull(payload)?.let { bridge.resolvePush(it) } ?: return true
                runCatching { bridge.currentProxy?.postMessage(push.toString()) }
            }
            NappletIpc.MSG_WEB_FAVORITE_STATE -> {
                val url = data.getString(NappletIpc.KEY_FAVORITE_URL) ?: return true
                val favorite = data.getBoolean(NappletIpc.KEY_FAVORITE_IS_FAVORITE, false)
                updateUi { if (url == chrome.url) copy(isFavorite = favorite) else this }
            }
            NappletIpc.MSG_SITE_PERMISSIONS -> {
                val callback = sitePermissionQueries.remove(data.getLong(NappletIpc.KEY_REQUEST_ID)) ?: return true
                callback(
                    BrowserSitePermission.entries.associateWith { permission ->
                        runCatching { Decision.valueOf(data.getString(NappletIpc.KEY_SITE_PERMISSION_PREFIX + permission.key).orEmpty()) }.getOrDefault(Decision.ASK)
                    },
                )
            }
            NappletIpc.MSG_BROWSER_TOKEN -> {
                val origin = data.getString(NappletIpc.KEY_BROWSER_ORIGIN) ?: return true
                val token = data.getString(NappletIpc.KEY_LAUNCH_TOKEN)
                if (token == null) {
                    // Refused (no account signed in): the page's calls fail now instead of hanging.
                    failMint(origin, NOT_SIGNED_IN)
                    return true
                }
                originTokens[origin] = token
                mintInFlight.remove(origin)
                pendingByOrigin.remove(origin)?.forEach { queued ->
                    queued.data.putString(NappletIpc.KEY_LAUNCH_TOKEN, token)
                    sendToBroker(queued)
                }
            }
            else -> return false
        }
        return true
    }

    // ---- site permissions ----

    private fun handlePermissionRequest(request: PermissionRequest) {
        val origin = BrowserChrome.originOf(request.origin.toString()) ?: return request.deny()
        val wanted = request.resources.mapNotNull(::sitePermissionFor).distinct()
        if (wanted.isEmpty()) return request.deny()
        resolveSitePermissions(origin, wanted) { granted ->
            val resources = request.resources.filter { sitePermissionFor(it) in granted }.toTypedArray()
            if (resources.isEmpty()) request.deny() else request.grant(resources)
        }
    }

    private fun sitePermissionFor(resource: String): BrowserSitePermission? =
        when (resource) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> BrowserSitePermission.CAMERA
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> BrowserSitePermission.MICROPHONE
            else -> null
        }

    /**
     * Decides [wanted] for [origin]: the user's remembered answers first (kept per origin in the main
     * process — the same on Tor and the open web), a prompt for anything never answered, and finally
     * Android's own runtime permission for whatever was allowed. [done] receives what is granted.
     */
    private fun resolveSitePermissions(
        origin: String,
        wanted: List<BrowserSitePermission>,
        done: (Set<BrowserSitePermission>) -> Unit,
    ) {
        querySitePermissions(origin) { decisions ->
            val allowed = wanted.filter { decisions[it] == Decision.ALLOW }.toSet()
            val ask = wanted.filter { decisions[it] == Decision.ASK }
            if (ask.isEmpty()) {
                ensureRuntimePermissions(allowed, done)
            } else {
                showPermissionPrompt(origin, ask) { remembered, grantNow ->
                    // remembered: true/false = Allow while visiting / Don't allow; null = only this time or dismissed.
                    if (remembered != null) ask.forEach { rememberSitePermission(origin, it, if (remembered) Decision.ALLOW else Decision.BLOCK) }
                    ensureRuntimePermissions(if (grantNow) allowed + ask else allowed, done)
                }
            }
        }
    }

    private fun querySitePermissions(
        origin: String,
        callback: (Map<BrowserSitePermission, Decision>) -> Unit,
    ) {
        val id = ++sitePermissionSeq
        sitePermissionQueries[id] = callback
        val msg =
            Message.obtain(null, NappletIpc.MSG_QUERY_SITE_PERMISSIONS).apply {
                replyTo = replyMessenger
                data =
                    Bundle().apply {
                        putLong(NappletIpc.KEY_REQUEST_ID, id)
                        putString(NappletIpc.KEY_BROWSER_ORIGIN, origin)
                    }
            }
        queueToBroker(msg)
    }

    private fun rememberSitePermission(
        origin: String,
        permission: BrowserSitePermission,
        decision: Decision,
    ) {
        val msg =
            Message.obtain(null, NappletIpc.MSG_SET_SITE_PERMISSION).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_BROWSER_ORIGIN, origin)
                        putString(NappletIpc.KEY_SITE_PERMISSION, permission.key)
                        putString(NappletIpc.KEY_SITE_DECISION, decision.name)
                    }
            }
        queueToBroker(msg)
    }

    /**
     * The permission prompt ([com.vitorpamplona.amethyst.commons.browser.ui.pill.PermissionPromptCard]):
     * [answer] gets true (allow, remembered), false (block, remembered), or null (allow only this time, or
     * dismissed — nothing remembered; a dismissal also denies).
     */
    private fun showPermissionPrompt(
        origin: String,
        permissions: List<BrowserSitePermission>,
        answer: (allow: Boolean?, grantNow: Boolean) -> Unit,
    ) {
        val host = chrome
        if (host == null || isFinishing || isDestroyed || host.permissionPrompt != null) {
            answer(null, false)
            return
        }
        host.permissionPrompt =
            BrowserChromeHost.PendingPermission(
                host = BrowserChrome.displayHost(origin),
                security = BrowserChrome.security(host.ui.chrome),
                permissions = permissions.toSet(),
            ) { allow, remember ->
                when {
                    allow && remember -> answer(true, true)
                    allow -> answer(null, true)
                    remember -> answer(false, false)
                    else -> answer(null, false)
                }
            }
    }

    /** Requests Android's runtime permission for each allowed site permission that lacks it. */
    private fun ensureRuntimePermissions(
        allowed: Set<BrowserSitePermission>,
        done: (Set<BrowserSitePermission>) -> Unit,
    ) {
        val needed = allowed.associateWith(::androidPermissionFor)
        val missing = needed.values.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.distinct()
        if (missing.isEmpty()) return done(allowed)
        if (pendingRuntimeGrant != null) return done(allowed - needed.filterValues { it in missing }.keys)
        pendingRuntimeGrant = { result ->
            val granted = allowed.filter { ContextCompat.checkSelfPermission(this, needed.getValue(it)) == PackageManager.PERMISSION_GRANTED }.toSet()
            if (granted.size < allowed.size) Toast.makeText(this, CommonsR.string.browser_permission_system_denied, Toast.LENGTH_LONG).show()
            done(granted)
        }
        runtimePermissionLauncher.launch(missing.toTypedArray())
    }

    private fun androidPermissionFor(permission: BrowserSitePermission): String =
        when (permission) {
            BrowserSitePermission.CAMERA -> Manifest.permission.CAMERA
            BrowserSitePermission.MICROPHONE -> Manifest.permission.RECORD_AUDIO
            BrowserSitePermission.LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
        }

    // ---- fullscreen video ----

    private fun enterFullscreen(
        view: View,
        callback: WebChromeClient.CustomViewCallback,
    ) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        view.setBackgroundColor(Color.BLACK)
        // Over the whole window, outside the inset root, so the video really covers the screen.
        (window.decorView as? FrameLayout)?.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        // The page now covers the address bar and everything else that says whose it is: name the site, as
        // Chrome does, so a full-screen page can't pass itself off as Amethyst asking for the user's key.
        val site = runCatching { currentUrl().toUri().host }.getOrNull()?.takeIf { it.isNotBlank() } ?: barTitle()
        Toast.makeText(this, getString(CommonsR.string.browser_fullscreen_notice, site), Toast.LENGTH_LONG).show()
        syncBackState()
    }

    private fun exitFullscreen() {
        val view = customView ?: return
        customView = null
        (window.decorView as? FrameLayout)?.removeView(view)
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        val callback = customViewCallback
        customViewCallback = null
        callback?.onCustomViewHidden()
        syncBackState()
    }

    // ---- theme colour + Recents ----

    /**
     * Tints the system-bar areas with the page's `theme-color` (the root's padding shows through behind
     * the transparent bars), and flips the bar icons to stay legible on it. Null restores the app theme.
     */
    private fun applyThemeColor(color: Int?) {
        val root = root ?: return
        val background = color ?: resolveThemeColor(android.R.attr.colorBackground)
        root.setBackgroundColor(background)
        val light = ColorUtils.calculateLuminance(background) > 0.5
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    /** Makes this task look like an installed app in Recents: the site's title, icon and colour. */
    @Suppress("DEPRECATION")
    private fun updateTaskDescription() {
        val label = pageTitle ?: title.ifBlank { null } ?: BrowserChrome.displayHost(currentUrl())
        val color = themeColor ?: 0
        // The Builder's `setIcon` that takes an `Icon` is API 37. We compile
        // against 37, so it resolves, and the old guard was `TIRAMISU` — which
        // meant every device from 33 to 36 called a method its framework does
        // not have and died with NoSuchMethodError the moment a page delivered
        // a favicon. Lint's NewApi did not flag it. The deprecated constructor
        // takes the same three things and carries the bitmap, so it stays the
        // path for everything below 37.
        val description =
            if (Build.VERSION.SDK_INT >= ICON_BUILDER_SDK) {
                ActivityManager.TaskDescription
                    .Builder()
                    .setLabel(label)
                    .apply { pageIcon?.let { setIcon(Icon.createWithBitmap(it)) } }
                    .apply { if (color != 0) setPrimaryColor(color) }
                    .build()
            } else {
                ActivityManager.TaskDescription(label, pageIcon, color)
            }
        runCatching { setTaskDescription(description) }
    }

    // ---- long-press menu ----

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?,
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val wv = v as? WebView ?: return
        val hit = wv.hitTestResult
        val extra = hit.extra?.takeIf { it.isNotBlank() } ?: return
        when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> addLinkItems(menu, extra)
            WebView.HitTestResult.IMAGE_TYPE -> addImageItems(menu, extra)
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // An image inside a link: the hit gives the image; ask the page for the link's href.
                val href = Handler(Looper.getMainLooper()).obtainMessage()
                wv.requestFocusNodeHref(href)
                href.data
                    .getString("url")
                    ?.takeIf { it.startsWith("http") }
                    ?.let { addLinkItems(menu, it) }
                addImageItems(menu, extra)
            }
        }
    }

    private fun addLinkItems(
        menu: ContextMenu,
        link: String,
    ) {
        if (menu.isEmpty()) menu.setHeaderTitle(link)
        if (link.startsWith("http")) {
            menu.add(CommonsR.string.browser_ctx_open_new_window).setOnMenuItemClickListener {
                startActivity(newWindowIntent(link))
                true
            }
        }
        menu.add(CommonsR.string.browser_ctx_copy_link).setOnMenuItemClickListener {
            BrowserWebTools.copyToClipboard(this, link)
            true
        }
        menu.add(CommonsR.string.browser_ctx_share_link).setOnMenuItemClickListener {
            BrowserWebTools.share(this, null, null, link)
            true
        }
    }

    private fun addImageItems(
        menu: ContextMenu,
        image: String,
    ) {
        if (menu.isEmpty()) menu.setHeaderTitle(image.take(80))
        if (image.startsWith("http") || image.startsWith("data:")) {
            menu.add(CommonsR.string.browser_ctx_download_image).setOnMenuItemClickListener {
                val wv = webView
                val cookie = wv?.let { BrowserWebTools.cookieManager(it).getCookie(image) }
                BrowserDownloads.download(this, image, wv?.settings?.userAgentString, null, null, cookie, if (useTor) proxyPort else -1)
                true
            }
        }
        if (image.startsWith("http")) {
            menu.add(CommonsR.string.browser_ctx_copy_image_link).setOnMenuItemClickListener {
                BrowserWebTools.copyToClipboard(this, image)
                true
            }
        }
    }

    /** A plain new browser window for [url], sharing this one's route, theme and account storage. */
    private fun newWindowIntent(url: String): Intent = intent(this, url, proxyPort, useTor, theme = themeType, webViewProfile = webViewProfile).addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

    // ---- network ----

    /** Whether the process route is Tor right now, whatever this page asked for. */
    private var routedOverTor = false

    /**
     * Files this page's Tor / open-web choice with the process-wide [WebViewProxyPolicy] (the override is
     * shared by every WebView in `:napplet`, so no surface sets it directly); [onReady] runs once the shared
     * route is in effect. Fails closed: a page that wants Tor loads nothing until Tor's port is known and the
     * route is really applied.
     */
    private fun claimRoute(onReady: () -> Unit = {}) {
        // On Tor without a guard against WebRTC (an old WebView), a page would learn the real address
        // around the proxy: nothing loads, as when Tor's port is unknown.
        if (useTor && (proxyPort <= 0 || !NappletWebRtcGuard.isSupported())) {
            showRouteBlocked()
            return
        }
        WebViewProxyPolicy.claim(
            owner = this,
            torPort = if (useTor) proxyPort else NappletProxyClaims.NO_PROXY,
            onFailed = { showRouteBlocked() },
            onReady = onReady,
        )
    }

    private fun showRouteBlocked() {
        if (!isDestroyed) Toast.makeText(this, R.string.napplet_route_blocked, Toast.LENGTH_LONG).show()
    }

    /** Persists the per-host Tor choice in the main process and re-applies it to the live WebView. */
    private fun setNetworkMode(newUseTor: Boolean) {
        useTor = newUseTor
        webView?.let { NappletWebRtcGuard.sync(it, useTor) }
        // Reload only once the new route is in effect, or the reload would go out the old way.
        claimRoute { webView?.reload() }
        updateChromeState { copy(torOn = useTor, torForced = !useTor && routedOverTor) }
        // Key the persisted choice on the host actually displayed (which may differ from startUrl after
        // in-page navigation), so the preference sticks to the right site.
        val host = runCatching { currentUrl().toUri().host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        val msg =
            Message.obtain(null, NappletIpc.MSG_SET_WEB_TOR).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_WEB_HOST, host)
                        putBoolean(NappletIpc.KEY_NETWORK_USE_TOR, newUseTor)
                    }
            }
        if (brokerMessenger != null) sendToBroker(msg)
    }

    // ---- trusted chrome + loading ----

    private var title: String = ""

    private fun barTitle(): String = title.ifBlank { runCatching { startUrl.toUri().host }.getOrNull() ?: getString(CommonsR.string.napplet_untitled) }

    /**
     * The window's chrome: the pull-down pill at the top (out of the corner where a site shows its own avatar),
     * find and the console at the bottom, and the page's dialogs — the shared Compose components.
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
                            surface = BrowserChrome.Surface.WEB,
                            presentation = BrowserChrome.Presentation.FULL_SCREEN,
                            url = startUrl,
                            startUrl = startUrl,
                            torOn = if (proxyPort > 0) useTor else null,
                            torForced = !useTor && routedOverTor,
                        ),
                    isFavorite = intent.getBooleanExtra(EXTRA_IS_FAVORITE, false),
                    defaultBrowserName = DefaultBrowser.label(this),
                ),
            listener = chromeListener,
        )

    private fun isDarkTheme(): Boolean =
        when (themeType) {
            "DARK" -> true
            "LIGHT" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

    private val chromeListener =
        object : BrowserChromeHost.Listener {
            override fun onPillEvent(event: BrowserPillEvent) {
                when (event) {
                    is BrowserPillEvent.Action -> onAction(event.action)
                    is BrowserPillEvent.Navigate -> loadAddress(event.input)
                    is BrowserPillEvent.TextZoom -> {
                        webView?.let { BrowserWebTools.setTextZoom(it, event.percent) }
                        updateUi { copy(textZoom = event.percent) }
                    }
                    BrowserPillEvent.CopyOrigin -> BrowserWebTools.copyToClipboard(this@NappletBrowserActivity, currentUrl())
                    BrowserPillEvent.PageInfo -> showPageInfo()
                    BrowserPillEvent.Close -> finish()
                }
            }

            override fun onFind(query: String) {
                val wv = webView ?: return
                if (query.isEmpty()) wv.clearMatches() else wv.findAllAsync(query)
            }

            override fun onFindNext(forward: Boolean) {
                webView?.findNext(forward)
            }

            override fun onFindClosed() {
                webView?.clearMatches()
            }

            override fun onPermissionChange(
                permission: BrowserSitePermission,
                decision: Decision,
            ) {
                BrowserChrome.originOf(currentUrl())?.let { rememberSitePermission(it, permission, decision) }
                // Keep the pill's summary in step with the sheet the change was made in. The registry
                // is authoritative and lives in the other process, but it does not call back, so an
                // edit made here would otherwise not show up until the next navigation.
                updateUi {
                    val next = sitePermissions.toMutableMap()
                    if (decision == Decision.ASK) next.remove(permission) else next[permission] = decision
                    copy(sitePermissions = next)
                }
            }

            override fun onClearSiteData() {
                webView?.let { BrowserWebTools.clearSiteData(this@NappletBrowserActivity, it, currentUrl()) }
            }

            override fun onClearAllSiteData() {
                webView?.let { BrowserWebTools.clearAllSiteData(this@NappletBrowserActivity, it) }
            }

            override fun onPanelsChanged() = syncBackState()
        }

    private fun onAction(action: Action) {
        val wv = webView
        when (action) {
            Action.BACK -> wv?.goBack()
            Action.FORWARD -> wv?.goForward()
            Action.RELOAD -> wv?.reload()
            Action.STOP -> wv?.stopLoading()
            // The star flips the shown state; the toggle sends that target explicitly.
            Action.FAVORITE -> sendFavoriteToggle(currentUrl(), chrome?.ui?.isFavorite != true)
            Action.SHARE -> BrowserWebTools.share(this, pageTitle, null, currentUrl())
            Action.BACK_TO_APP -> wv?.let { BrowserWebTools.backToScope(it, startUrl) }
            Action.COPY_LINK -> BrowserWebTools.copyToClipboard(this, currentUrl())
            Action.DESKTOP_SITE ->
                wv?.let {
                    val desktop = !BrowserWebTools.isDesktopMode(it)
                    BrowserWebTools.setDesktopMode(it, desktop)
                    updateUi { copy(desktopSite = desktop) }
                }
            Action.ADD_TO_HOME_SCREEN -> addToHomeScreen()
            Action.OPEN_IN_BROWSER_APP -> BrowserWebTools.openInOtherBrowser(this, currentUrl())
            Action.TOR -> setNetworkMode(!useTor)
            Action.SITE_SETTINGS -> openPermissions()
            else -> Unit
        }
    }

    /**
     * Shows a page's JS dialog in the chrome, titled with the page's host. Answers at once when dialogs are
     * blocked for this page, another is already up, or the window is going away.
     */
    private fun showJsDialog(
        type: PageDialogType,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsResult,
    ): Boolean {
        val host = chrome
        if (jsDialogsBlocked) {
            // A blocked page may no longer hold the user on it: leaving is allowed, everything else cancels.
            if (type == PageDialogType.BEFORE_UNLOAD) result.confirm() else result.cancel()
            return true
        }
        if (host == null || host.dialog != null || isFinishing || isDestroyed) {
            result.cancel()
            return true
        }
        jsDialogsOnPage++
        host.dialog =
            BrowserChromeHost.PendingDialog(
                type = type,
                host = url?.let(BrowserChrome::originOf)?.let(BrowserChrome::displayHost),
                security = BrowserChrome.security(host.ui.chrome),
                message = message.orEmpty(),
                defaultValue = defaultValue.orEmpty(),
                offerBlock = jsDialogsOnPage > 1,
            ) { confirmed, text, block ->
                if (block) jsDialogsBlocked = true
                when {
                    !confirmed -> result.cancel()
                    result is JsPromptResult -> result.confirm(text.orEmpty())
                    else -> result.confirm()
                }
            }
        return true
    }

    /**
     * Chrome's page info: connection, route and certificate, then this site's camera / microphone / location
     * answers (asked of the broker first, so the sheet shows the truth) and its data.
     */
    private fun showPageInfo() {
        val wv = webView ?: return
        val origin = BrowserChrome.originOf(currentUrl())
        val certificate = BrowserWebTools.certificateInfo(wv)
        if (origin == null) {
            chrome?.showPageInfo(certificate)
            return
        }
        querySitePermissions(origin) { decisions ->
            updateUi { copy(sitePermissions = decisions.filterValues { it != Decision.ASK }) }
            chrome?.showPageInfo(certificate)
        }
    }

    /** Asks the main process to pin a launcher shortcut that reopens this page in Amethyst's browser. */
    private fun addToHomeScreen() {
        val url = currentUrl()
        val msg =
            Message.obtain(null, NappletIpc.MSG_ADD_TO_HOME_SCREEN).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_FAVORITE_URL, url)
                        putString(NappletIpc.KEY_FAVORITE_LABEL, pageTitle ?: BrowserChrome.displayHost(url))
                    }
            }
        queueToBroker(msg)
    }

    /**
     * Ask the broker to open the editable permission screen for the site currently displayed. NIP-07 grants
     * for a plain browser are keyed per visited origin (`browser:<origin>`), so we send the live origin and
     * the broker launches the main activity at that Connected Apps detail.
     */
    private fun openPermissions() {
        val uri = runCatching { currentUrl().toUri() }.getOrNull() ?: return
        val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return
        val origin = "$scheme://$host" + if (uri.port > 0) ":${uri.port}" else ""
        val msg =
            Message.obtain(null, NappletIpc.MSG_OPEN_PERMISSIONS).apply {
                data = Bundle().apply { putString(NappletIpc.KEY_BROWSER_ORIGIN, origin) }
            }
        queueToBroker(msg)
    }

    /**
     * Pins or unpins [url] in the main-process registry. Sends the state the user asked for rather than a
     * flip, and takes the broker's confirmed state back through [NappletIpc.MSG_WEB_FAVORITE_STATE].
     */
    private fun sendFavoriteToggle(
        url: String,
        isFavorite: Boolean,
    ) {
        val label = pageTitle ?: runCatching { url.toUri().host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
        val msg =
            Message.obtain(null, NappletIpc.MSG_TOGGLE_WEB_FAVORITE).apply {
                replyTo = replyMessenger
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_FAVORITE_URL, url)
                        putString(NappletIpc.KEY_FAVORITE_LABEL, label)
                        putBoolean(NappletIpc.KEY_FAVORITE_IS_FAVORITE, isFavorite)
                    }
            }
        queueToBroker(msg)
    }

    private fun buildLoadingView(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(resolveThemeColor(android.R.attr.colorBackground))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            addView(
                TextView(this@NappletBrowserActivity).apply {
                    text = barTitle()
                    setTextColor(resolveThemeColor(android.R.attr.textColorPrimary))
                    textSize = 18f
                    gravity = Gravity.CENTER
                },
            )
            addView(View(this@NappletBrowserActivity).apply { layoutParams = LinearLayout.LayoutParams(1, dp(20)) })
            addView(ProgressBar(this@NappletBrowserActivity))
        }

    /**
     * Covers the WebView's built-in error page (an Android robot over "net::ERR_…" text) with one that says
     * what went wrong in words and offers a retry. It sits in [contentFrame] under the pill, so the address
     * can still be edited. It stays up through the next attempt (showing it is retrying) and goes away only
     * once a page really paints, so a retry never flashes the built-in page underneath.
     */
    private fun showLoadError(
        url: String,
        failure: PageLoadFailure,
        detail: String?,
    ) {
        loadError = LoadErrorState(failure, BrowserChrome.displayHost(url).ifBlank { url }, detail, routedOverTor, canSwitchTor(failure, url))
        if (loadErrorView != null) return
        val dark = isDarkTheme()
        loadErrorView =
            ComposeView(this)
                .apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        val state = loadError ?: return@setContent
                        BrowserChromeTheme(dark) {
                            PageLoadError(
                                failure = state.failure,
                                host = state.host,
                                detail = state.detail,
                                viaTor = state.viaTor,
                                retrying = state.retrying,
                                onRetry = { webView?.reload() },
                                onSwitchTor = if (state.canSwitchTor) ({ setNetworkMode(!useTor) }) else null,
                            )
                        }
                    }
                }.also { contentFrame.addView(it, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)) }
    }

    /**
     * Whether the error page may offer to flip this page's Tor choice. An onion off Tor can be opened with
     * Tor when Tor is running. Otherwise only a reachability failure while this page asked for Tor (some
     * sites drop Tor exits), never for an onion, and only when no other open surface also needs Tor — the
     * route is shared and Tor wins, so the open-web retry would go through Tor again anyway.
     */
    private fun canSwitchTor(
        failure: PageLoadFailure,
        url: String,
    ): Boolean =
        when {
            proxyPort <= 0 -> false
            failure == PageLoadFailure.ONION_NEEDS_TOR -> !useTor
            else -> useTor && PageLoadFailure.mayBeTorBlocked(failure) && !OmniboxInput.isOnion(url) && !WebViewProxyPolicy.torWantedByOthers(this)
        }

    /**
     * Keeps a `.onion` link from leaving on the open web, where its name would be looked up by the regular
     * resolver and fail as "not found": a main-frame navigation to an onion on a page with Tor off switches
     * this page to Tor first (as a typed onion address does), or, with no Tor to switch to, shows why it
     * can't open. Returns whether the navigation was taken over.
     */
    private fun guardOnion(
        view: WebView,
        url: String,
        isMainFrame: Boolean,
    ): Boolean {
        if (!isMainFrame || useTor || !OmniboxInput.isOnion(url)) return false
        if (proxyPort > 0) {
            useTor = true
            NappletWebRtcGuard.sync(view, true)
            updateChromeState { copy(torOn = true, torForced = false) }
            claimRoute { if (webView === view) view.loadUrl(url) }
        } else {
            showLoadError(url, PageLoadFailure.ONION_NEEDS_TOR, null)
        }
        return true
    }

    private fun hideLoadError() {
        loadError = null
        loadErrorView?.let { contentFrame.removeView(it) }
        loadErrorView = null
    }

    /** Chrome's "Aw, Snap!": the page's renderer died; offer to load [url] again in a fresh WebView. */
    private fun showCrashView(url: String) {
        crashView?.let { contentFrame.removeView(it) }
        crashView =
            LinearLayout(this)
                .apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundColor(resolveThemeColor(android.R.attr.colorBackground))
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    addView(
                        TextView(this@NappletBrowserActivity).apply {
                            text = getString(CommonsR.string.browser_renderer_gone)
                            setTextColor(resolveThemeColor(android.R.attr.textColorPrimary))
                            textSize = 18f
                            gravity = Gravity.CENTER
                        },
                    )
                    addView(
                        Button(this@NappletBrowserActivity).apply {
                            text = getString(CommonsR.string.browser_renderer_gone_reload)
                            setOnClickListener {
                                crashView?.let { contentFrame.removeView(it) }
                                crashView = null
                                val wv = buildWebView()
                                contentFrame.addView(wv, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                                claimRoute { if (webView === wv) wv.loadUrl(url) }
                            }
                        },
                    )
                }.also { contentFrame.addView(it) }
        syncBackState()
    }

    /**
     * Feeds the page's load progress to the pill, which draws it under the grabber (and as a ring around
     * Stop when open); cleared at 100%.
     */
    private fun updateLoadProgress(progress: Int) {
        if (progress >= 100) {
            updateUi { copy(loadProgress = null, chrome = chrome.copy(isLoading = false)) }
        } else {
            updateUi { copy(loadProgress = progress / 100f) }
        }
    }

    /** Appends a single ERROR line to the console panel and refreshes the chrome's unread count. */
    private fun logConsoleError(
        request: WebResourceRequest,
        message: String,
    ) {
        chrome?.appendConsole(ConsoleLine(ConsoleLine.Level.ERROR, message, request.url?.toString().orEmpty(), 0))
    }

    private fun resolveThemeColor(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun readContractAsset(path: String): ByteArray = assets.open(NappletWebResources.RESOURCE_ASSET_ROOT + path).use { it.readBytes() }

    companion object {
        private const val TAG = "NappletBrowserActivity"
        private const val BLANK_PAGE = "about:blank"
        private const val NOT_SIGNED_IN = "Sign in to Amethyst to use this site's Nostr features."
        private const val MINT_TIMED_OUT = "Amethyst didn't answer. Reload the page to try again."
        private const val ACTIVITY_CLASS = "com.vitorpamplona.amethyst.napplethost.NappletBrowserActivity"

        /** How often a resumed browser renews its foreground lease (well under the broker's 90s TTL). */
        private const val FOREGROUND_HEARTBEAT_MS = 30_000L

        /** Max favicon edge (px) before sending over IPC — keeps the PNG tiny, well under the Binder limit. */
        private const val ICON_MAX_PX = 96

        /** Grace period after page-finish before the declared-icon sniff runs, so `onReceivedIcon` wins first. */
        private const val FAVICON_SNIFF_DELAY_MS = 1_200L

        private const val EXTRA_URL = "url"
        private const val EXTRA_PROXY_PORT = "proxyPort"
        private const val EXTRA_USE_TOR = "useTor"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_THEME = "theme"
        private const val EXTRA_IS_FAVORITE = "isFavorite"
        private const val EXTRA_POPUP_TOKEN = "popupToken"
        private const val EXTRA_SEARCH_ENGINE = "searchEngine"

        /** `TaskDescription.Builder.setIcon(Icon)` exists from this SDK on. */
        private const val ICON_BUILDER_SDK = 37

        fun intent(
            context: Context,
            url: String,
            proxyPort: Int,
            useTor: Boolean,
            title: String = "",
            theme: String = "SYSTEM",
            isFavorite: Boolean = false,
            webViewProfile: String? = null,
            searchEngine: String? = null,
        ): Intent =
            Intent()
                .setClassName(context, ACTIVITY_CLASS)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_PROXY_PORT, proxyPort)
                .putExtra(EXTRA_USE_TOR, useTor)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_THEME, theme)
                .putExtra(EXTRA_IS_FAVORITE, isFavorite)
                // Opaque per-account storage partition; shares the host contract's key so there is one
                // name for the concept across every WebView creation site.
                .putExtra(NappletHostContract.EXTRA_WEBVIEW_PROFILE, webViewProfile)
                .putExtra(EXTRA_SEARCH_ENGINE, searchEngine)
                // Distinct task identity per URL for documentLaunchMode=intoExisting.
                .setData(url.toUri())

        /**
         * Opens, as its own task, a window a page asked for; [token] names the popup WebView parked in
         * [BrowserPopups]. Usable from the embedded browser's Service as well as from an Activity.
         */
        fun popupIntent(
            context: Context,
            token: String,
        ): Intent =
            Intent()
                .setClassName(context, ACTIVITY_CLASS)
                .putExtra(EXTRA_POPUP_TOKEN, token)
                .setData("amethyst-window://$token".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    }
}
