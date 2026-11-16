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

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.privacysandbox.ui.provider.toCoreLibInfo
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.browser.DownloadCooldown
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
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

/**
 * Provider for the **embedded** in-app browser. Runs in the keyless `:napplet` process: it hosts the
 * live-URL WebView and exposes it to the main app as a `SandboxedUiAdapter` (the main app renders it
 * inside a `SandboxedSdkView`, so only pixels + input cross the process boundary — never the WebView's
 * JS context, cookies, or the NIP-07 bridge). Keys still live only in the main process; every NIP-07
 * `window.nostr` call is brokered and consent-gated there, per visited origin.
 *
 * Shares the keyless-sandbox trust model of [NappletHostActivity] (the verified-blob nSite/napplet
 * host), but as a windowless Service rendering an arbitrary live URL, so the surface can be embedded in
 * the main activity rather than taking over the screen. Requires API 30+ (SurfaceControlViewHost); the
 * feature is hidden below that.
 */
@RequiresApi(Build.VERSION_CODES.R)
class NappletBrowserService : Service() {
    private val incoming = Messenger(Handler(Looper.getMainLooper(), ::onClientMessage))

    /**
     * Everything that belongs to ONE embedded browser surface. A single service instance is shared by
     * every tab (they bind the same Intent), so all per-surface state is collected here and keyed by
     * [sessionId] in [tabs]. Each tab gets its own [replyMessenger]: the broker echoes `replyTo` on both
     * responses and unsolicited relay pushes, so this is what scopes NIP-07 traffic back to the right
     * surface without any id rewriting.
     */
    private inner class BrowserTab(
        val sessionId: String,
        var clientMessenger: Messenger?,
        val url: String,
        var proxyPort: Int,
        var useTor: Boolean,
        val bgColor: Int,
        val themeType: String,
        // Opaque per-account WebView storage-profile name (see NappletWebViewProfile).
        val webViewProfile: String?,
        // SearchEngines id the main process chose; handed to popups, whose address bar resolves here.
        val searchEngine: String?,
    ) {
        var webView: WebView? = null

        // The page the renderer was showing when it died, so the rebuild lands back where the user was.
        var recoverUrl: String? = null

        // The client's last pause/resume. A parked tab can be paused before its WebView exists (the WebView
        // is only built when the surface opens), so the flag is applied to every WebView built for the tab.
        var paused = false

        // Whether the user is looking at this tab (see NappletBrowserContract.MSG_SET_ATTENDED), and the page's
        // requests that act for the user held while they aren't: (origin, request), sent when they're back.
        // Unattended until the client says otherwise: it sends its state right after every create.
        var attended = false
        val heldWhileAway = NappletHeldRequests<Pair<String, Message>>(SystemClock::elapsedRealtime)

        // The session's root view (holds the WebView, and the page's fullscreen view when it has one).
        var container: FrameLayout? = null
        var customView: View? = null
        var customViewCallback: WebChromeClient.CustomViewCallback? = null

        // Page-originated JS dialogs and permission requests waiting on the main process's answer.
        val jsDialogs = mutableMapOf<Long, JsResult>()
        var jsDialogsOnPage = 0
        var jsDialogsBlocked = false
        val permissionRequests = mutableMapOf<Long, (Set<BrowserSitePermission>) -> Unit>()
        var textZoom = BrowserChrome.DEFAULT_TEXT_ZOOM
        var desktopSite = false

        // The page on screen's bridge reply proxy, and which document each broker reply belongs to: a
        // reply for a page the tab has navigated away from must never land in the next one.
        val bridge = NappletBridgeDocuments<JavaScriptReplyProxy>()
        var fireSeq = 0

        // The in-flight `<input type="file">` pick for this surface. The picker itself runs in the main
        // process (this provider is windowless), so the callback waits here for MSG_FILE_CHOOSER_RESULT.
        val fileChooser = PendingFileChooser()

        // Last main-frame error state, pushed to the client so it can show an error/retry overlay over
        // the surface (the embedded surface has no error page of its own).
        var loadFailed = false

        // Host whose favicon this tab already relayed. A page fires the icon callbacks several times, and
        // both capture paths (WebView raster + declared-icon sniff) can land for the same visit, so this
        // keeps it to one relay per host per visit. Re-armed when the host changes.
        var lastIconHost: String? = null

        // Per visited origin: its broker-minted launch token, the requests queued until it arrives, and
        // the origins a mint is already in flight for — so NIP-07 consent is scoped per site, per tab.
        val originTokens = mutableMapOf<String, String>()
        val pendingByOrigin = mutableMapOf<String, MutableList<Message>>()
        val mintInFlight = mutableSetOf<String>()

        // Page-initiated downloads: how often each site may ask, and whether an offer is being prepared
        // (decoded) — one at a time, so a page can't queue up decodes.
        val downloadCooldown = DownloadCooldown()
        var preparingDownload = false

        val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { onBrokerReply(this, it) })

        /**
         * Sends [msg] to this tab's client stamped with the session it belongs to, so the client can drop what
         * a session it has since replaced still had in flight (a late file-chooser request, a stale
         * SESSION_READY that would re-arm its view with a dead adapter). Returns whether it was delivered.
         */
        fun toClient(msg: Message): Boolean {
            val client = clientMessenger ?: return false
            msg.data.putString(NappletBrowserContract.KEY_SESSION_ID, sessionId)
            return runCatching { client.send(msg) }.isSuccess
        }
    }

    private val tabs = mutableMapOf<String, BrowserTab>()

    // WebView's PermissionRequest → our relay id, so a page's cancellation can withdraw the prompt.
    private val pendingWebPermissions = mutableMapOf<PermissionRequest, Long>()

    // Download consent cards the main process is showing, by relay id, until the client answers or the
    // tab closes. The offer holds the decoded bytes (or the URL to fetch) the card describes.
    private class PendingDownload(
        val sessionId: String,
        val origin: String,
        val offer: BrowserDownloads.DownloadOffer,
    )

    private val pendingDownloads = mutableMapOf<Long, PendingDownload>()
    private var downloadSeq = 0L

    // The shim never changes; read+decode it once instead of per tab on the main thread.
    private val shimJs: String by lazy { readContractAsset(NappletWebResources.SHIM_JS_PATH).decodeToString() }

    // ---- broker bridge: ONE binding shared by all tabs; per-message replyTo scopes the routing ----
    private var brokerMessenger: Messenger? = null
    private var brokerBound = false
    private val pendingBrokerRequests = mutableListOf<Message>()

    private val brokerConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                brokerMessenger = Messenger(service)
                pendingBrokerRequests.forEach { sendToBroker(it) }
                pendingBrokerRequests.clear()
                // A broker that restarted knows nothing about who is watching.
                tabs.values.forEach { if (it.attended) reportAttended(it) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                brokerMessenger = null
            }
        }

    override fun onBind(intent: Intent?): IBinder = incoming.binder

    override fun onDestroy() {
        // Release before unbinding, while the broker can still hear it.
        tabs.values.forEach {
            it.fileChooser.cancel()
            cancelPending(it)
            releasePage(it, closing = true)
            WebViewProxyPolicy.release(it)
            it.webView?.destroy()
        }
        tabs.clear()
        if (brokerBound) {
            runCatching { unbindService(brokerConnection) }
            brokerBound = false
        }
        super.onDestroy()
    }

    /** The client re-reads Tor's port on every load it asks for: Tor may have come up (or moved) since the tab was made. */
    private fun refreshProxyPort(
        tab: BrowserTab,
        msg: Message,
    ) {
        msg.data
            ?.getInt(NappletBrowserContract.KEY_PROXY_PORT, 0)
            ?.takeIf { it != 0 }
            ?.let { tab.proxyPort = it }
    }

    private fun tabFor(msg: Message): BrowserTab? = msg.data?.getString(NappletBrowserContract.KEY_SESSION_ID)?.let { tabs[it] }

    private fun onClientMessage(msg: Message): Boolean {
        when (msg.what) {
            NappletBrowserContract.MSG_CREATE_SESSION -> {
                val data = msg.data ?: return true
                val sessionId = data.getString(NappletBrowserContract.KEY_SESSION_ID) ?: return true
                // A re-sent create for an id that is still live (a client that lost track of it) must not
                // strand the old tab's WebView, broker state and proxy claim with nothing left to close them.
                tabs[sessionId]?.let(::closeTab)
                val tab =
                    BrowserTab(
                        sessionId = sessionId,
                        clientMessenger = msg.replyTo,
                        url = data.getString(NappletBrowserContract.KEY_URL)?.ifBlank { ABOUT_BLANK } ?: ABOUT_BLANK,
                        proxyPort = data.getInt(NappletBrowserContract.KEY_PROXY_PORT, -1),
                        useTor = data.getBoolean(NappletBrowserContract.KEY_USE_TOR, false),
                        bgColor = data.getInt(NappletBrowserContract.KEY_BG_COLOR, Color.WHITE),
                        themeType = data.getString(NappletBrowserContract.KEY_THEME).orEmpty().ifBlank { "SYSTEM" },
                        webViewProfile = data.getString(NappletBrowserContract.KEY_WEBVIEW_PROFILE),
                        searchEngine = data.getString(NappletBrowserContract.KEY_SEARCH_ENGINE),
                    )
                tabs[sessionId] = tab
                // Bind the broker once; a re-sent MSG_CREATE_SESSION (e.g. client reconnect) must not
                // leak a second binding.
                if (!brokerBound) {
                    brokerBound = bindService(Intent().setClassName(this, NappletHostContract.BROKER_SERVICE_CLASS), brokerConnection, BIND_AUTO_CREATE)
                }
                replyWithAdapter(tab)
            }
            NappletBrowserContract.MSG_NAVIGATE -> {
                val tab = tabFor(msg) ?: return true
                refreshProxyPort(tab, msg)
                val url = normalizeUrl(msg.data?.getString(NappletBrowserContract.KEY_URL).orEmpty())
                // A renderer crash destroyed this tab's WebView; the user's retry builds a fresh one.
                val wv = tab.webView
                if (wv == null) {
                    rebuildWebView(tab, url)
                } else {
                    // Right after a Tor toggle the new route may still be applying; don't let this load race it
                    // (nor go out at all when Tor is wanted but unavailable).
                    claimRoute(tab) { if (tab.webView === wv) wv.loadUrl(url) }
                }
            }
            NappletBrowserContract.MSG_CLOSE_SESSION -> tabFor(msg)?.let(::closeTab)
            NappletBrowserContract.MSG_SET_ATTENDED -> {
                val tab = tabFor(msg) ?: return true
                tab.attended = msg.data?.getBoolean(NappletBrowserContract.KEY_ENABLED, false) ?: false
                reportAttended(tab)
                if (tab.attended) {
                    val held = tab.heldWhileAway.drain()
                    held.fail.forEach { (_, request) -> tab.bridge.failRequest(request, NappletHeldRequests.EXPIRED) }
                    held.send.forEach { (origin, request) -> dispatchToBroker(tab, origin, request) }
                }
            }
            NappletBrowserContract.MSG_PAUSE ->
                tabFor(msg)?.let {
                    it.paused = true
                    it.webView?.onPause()
                }
            NappletBrowserContract.MSG_RESUME ->
                tabFor(msg)?.let {
                    it.paused = false
                    it.webView?.onResume()
                }
            NappletBrowserContract.MSG_FORWARD -> tabFor(msg)?.webView?.let { if (it.canGoForward()) it.goForward() }
            NappletBrowserContract.MSG_STOP -> tabFor(msg)?.webView?.stopLoading()
            NappletBrowserContract.MSG_FIND -> {
                val tab = tabFor(msg) ?: return true
                val wv = tab.webView ?: return true
                val query = msg.data?.getString(NappletBrowserContract.KEY_FIND_QUERY).orEmpty()
                if (query.isEmpty()) {
                    wv.clearMatches()
                    wv.setFindListener(null)
                } else {
                    wv.setFindListener { active, total, _ -> pushFindResult(tab, active, total) }
                    wv.findAllAsync(query)
                }
            }
            NappletBrowserContract.MSG_FIND_NEXT -> tabFor(msg)?.webView?.findNext(msg.data?.getBoolean(NappletBrowserContract.KEY_FIND_FORWARD, true) ?: true)
            NappletBrowserContract.MSG_SET_DESKTOP -> {
                val tab = tabFor(msg) ?: return true
                tab.desktopSite = msg.data?.getBoolean(NappletBrowserContract.KEY_ENABLED, false) ?: false
                tab.webView?.let { BrowserWebTools.setDesktopMode(it, tab.desktopSite) }
            }
            NappletBrowserContract.MSG_SET_TEXT_ZOOM -> {
                val tab = tabFor(msg) ?: return true
                tab.textZoom = msg.data?.getInt(NappletBrowserContract.KEY_TEXT_ZOOM, BrowserChrome.DEFAULT_TEXT_ZOOM) ?: BrowserChrome.DEFAULT_TEXT_ZOOM
                tab.webView?.let { BrowserWebTools.setTextZoom(it, tab.textZoom) }
            }
            NappletBrowserContract.MSG_BACK_TO_SCOPE -> {
                val tab = tabFor(msg) ?: return true
                tab.webView?.let { BrowserWebTools.backToScope(it, msg.data?.getString(NappletBrowserContract.KEY_URL) ?: tab.url) }
            }
            NappletBrowserContract.MSG_CLEAR_SITE_DATA -> {
                val tab = tabFor(msg) ?: return true
                tab.webView?.let { wv -> wv.url?.let { BrowserWebTools.clearSiteData(this, wv, it) } }
            }
            NappletBrowserContract.MSG_CLEAR_ALL_SITE_DATA -> {
                val tab = tabFor(msg) ?: return true
                tab.webView?.let { BrowserWebTools.clearAllSiteData(this, it) }
            }
            NappletBrowserContract.MSG_PAGE_INFO_REQUEST -> {
                val tab = tabFor(msg) ?: return true
                val wv = tab.webView ?: return true
                val certificate = BrowserWebTools.certificateInfo(wv)
                sendToClient(tab, NappletBrowserContract.MSG_PAGE_INFO) {
                    certificate?.let {
                        putString(NappletBrowserContract.KEY_CERT_ISSUED_TO, it.issuedTo)
                        putString(NappletBrowserContract.KEY_CERT_ISSUED_BY, it.issuedBy)
                        putString(NappletBrowserContract.KEY_CERT_VALID_UNTIL, it.validUntil)
                    }
                }
            }
            NappletBrowserContract.MSG_JS_DIALOG_RESULT -> {
                val tab = tabFor(msg) ?: return true
                val data = msg.data ?: return true
                val result = tab.jsDialogs.remove(data.getLong(NappletBrowserContract.KEY_DIALOG_ID)) ?: return true
                if (data.getBoolean(NappletBrowserContract.KEY_DIALOG_BLOCK, false)) tab.jsDialogsBlocked = true
                val confirmed = data.getBoolean(NappletBrowserContract.KEY_DIALOG_CONFIRMED, false)
                when {
                    !confirmed -> result.cancel()
                    result is JsPromptResult -> result.confirm(data.getString(NappletBrowserContract.KEY_DIALOG_TEXT).orEmpty())
                    else -> result.confirm()
                }
            }
            NappletBrowserContract.MSG_PERMISSION_RESULT -> {
                val tab = tabFor(msg) ?: return true
                val data = msg.data ?: return true
                val answer = tab.permissionRequests.remove(data.getLong(NappletBrowserContract.KEY_PERMISSION_ID)) ?: return true
                answer(
                    data
                        .getStringArray(NappletBrowserContract.KEY_PERMISSIONS)
                        .orEmpty()
                        .mapNotNull(BrowserSitePermission::fromKey)
                        .toSet(),
                )
            }
            NappletBrowserContract.MSG_DOWNLOAD_CONSENT_RESULT -> {
                val data = msg.data ?: return true
                val pending = pendingDownloads.remove(data.getLong(NappletBrowserContract.KEY_DOWNLOAD_ID)) ?: return true
                val tab = tabs[pending.sessionId] ?: return true
                val allowed = data.getBoolean(NappletBrowserContract.KEY_DOWNLOAD_ALLOWED, false)
                tab.downloadCooldown.answered(pending.origin, allowed)
                if (allowed) pending.offer.save(this)
            }
            NappletBrowserContract.MSG_EXIT_FULLSCREEN -> tabFor(msg)?.let { exitFullscreen(it) }
            NappletBrowserContract.MSG_RELOAD -> {
                val tab = tabFor(msg) ?: return true
                refreshProxyPort(tab, msg)
                // A renderer death destroyed this tab's WebView: rebuild it on the page it was showing.
                if (tab.webView == null) rebuildWebView(tab, tab.recoverUrl ?: tab.url) else claimRoute(tab) { tab.webView?.reload() }
            }
            NappletBrowserContract.MSG_BACK -> tabFor(msg)?.webView?.let { if (it.canGoBack()) it.goBack() }
            NappletBrowserContract.MSG_IME_OP -> {
                val tab = tabFor(msg) ?: return true
                val payload = msg.data?.getString(NappletBrowserContract.KEY_IME_PAYLOAD) ?: return true
                runCatching { tab.bridge.currentProxy?.postMessage(payload) }
            }
            NappletBrowserContract.MSG_SET_TOR -> {
                val tab = tabFor(msg) ?: return true
                tab.useTor = msg.data?.getBoolean(NappletBrowserContract.KEY_USE_TOR, false) ?: false
                tab.webView?.let { NappletWebRtcGuard.sync(it, tab.useTor) }
                refreshProxyPort(tab, msg)
                // Reload only after the route actually applies — the override is async, so reloading
                // immediately would re-fetch through the old route.
                claimRoute(tab) { tab.webView?.reload() }
            }
            NappletBrowserContract.MSG_MAGNIFIER_REQUEST -> onMagnifierRequest(msg)
            NappletBrowserContract.MSG_FILE_CHOOSER_RESULT -> {
                val tab = tabFor(msg) ?: return true
                val data = msg.data ?: return true
                val id = data.getLong(NappletBrowserContract.KEY_FILE_CHOOSER_ID)
                // Absent array = the user cancelled; PendingFileChooser turns that into the null the page
                // needs to see so its file input becomes tappable again.
                val uris = data.getStringArray(NappletBrowserContract.KEY_FILE_CHOOSER_URIS)?.map(String::toUri)
                tab.fileChooser.deliver(id, uris?.toTypedArray())
            }
            else -> return false
        }
        return true
    }

    // Source rect is in view px (== surface px, the SCVH is 1:1). See MagnifierCapture for the threading.
    private fun onMagnifierRequest(msg: Message) {
        val tab = tabFor(msg) ?: return
        val wv = tab.webView ?: return
        val data = msg.data ?: return
        val reqT = data.getLong(NappletBrowserContract.KEY_MAG_REQ_T)
        MagnifierCapture.capture(
            webView = wv,
            bgColor = tab.bgColor,
            cx = data.getFloat(NappletBrowserContract.KEY_MAG_X),
            cy = data.getFloat(NappletBrowserContract.KEY_MAG_Y),
            boxW = data.getInt(NappletBrowserContract.KEY_MAG_BOX_W, 150).coerceIn(16, 1024),
            boxH = data.getInt(NappletBrowserContract.KEY_MAG_BOX_H, 84).coerceIn(16, 1024),
            zoom = data.getFloat(NappletBrowserContract.KEY_MAG_ZOOM, 1.6f).coerceIn(1f, 4f),
        ) { bytes, outW, outH, captureMs ->
            val reply =
                Message.obtain(null, NappletBrowserContract.MSG_MAGNIFIER_FRAME).apply {
                    this.data =
                        Bundle().apply {
                            putByteArray(NappletBrowserContract.KEY_MAG_BYTES, bytes)
                            putInt(NappletBrowserContract.KEY_MAG_W, outW)
                            putInt(NappletBrowserContract.KEY_MAG_H, outH)
                            putDouble(NappletBrowserContract.KEY_MAG_CAPTURE_MS, captureMs)
                            putLong(NappletBrowserContract.KEY_MAG_REQ_T, reqT)
                        }
                }
            tab.toClient(reply)
        }
    }

    /** Builds the SandboxedUiAdapter for [tab] and ships its cross-process handle (coreLibInfo) to the client. */
    @Suppress("DEPRECATION") // androidx.privacysandbox.ui toCoreLibInfo is deprecated with no successor.
    private fun replyWithAdapter(tab: BrowserTab) {
        val adapter = NappletBrowserUiAdapter(this, tab.sessionId)
        val coreLibInfo = adapter.toCoreLibInfo(this)
        val reply =
            Message.obtain(null, NappletBrowserContract.MSG_SESSION_READY).apply {
                data = Bundle().apply { putBundle(NappletBrowserContract.KEY_CORE_LIB_INFO, coreLibInfo) }
            }
        tab.toClient(reply)
    }

    /**
     * Builds and configures [sessionId]'s WebView (called by the adapter on the main thread when the
     * client attaches the surface). Injects the same NIP-07 shim [NappletHostActivity] uses, at document
     * start for every origin, reaching the broker directly (no shell). Loads the tab's URL.
     */
    fun createBrowserWebView(
        context: Context,
        sessionId: String,
        container: FrameLayout,
    ): WebView {
        // The session may have been closed between MSG_CREATE_SESSION and this posted call — fail rather
        // than build a WebView that no tab tracks (it would leak).
        val tab = tabs[sessionId] ?: error("No browser tab for session $sessionId")
        // A session re-opened on this tab (the client's view detached and re-attached) before the old one's
        // close landed: that session's WebView is still here. Destroy it now — its close will be ignored
        // (see onSessionClosed), and overwriting it would leak it.
        tab.webView?.let { stale ->
            (stale.parent as? ViewGroup)?.removeView(stale)
            stale.destroy()
            tab.webView = null
        }
        tab.container = container
        val wv = buildTabWebView(context, tab)
        claimRoute(tab) { if (tab.webView === wv) wv.loadUrl(tab.url) }
        return wv
    }

    /**
     * Files [tab]'s Tor / open-web choice with the process-wide [WebViewProxyPolicy] and runs [onReady] (the
     * load) once the shared route is in effect. Fails closed: a tab that wants Tor while Tor has no port yet
     * doesn't claim or load at all, and a route that can't be applied blocks the load too — either way the
     * client hears [NappletBrowserContract.KEY_ROUTE_BLOCKED] and offers Retry. Also keeps the client told
     * which route is really in effect ([NappletBrowserContract.MSG_ROUTE]).
     */
    private fun claimRoute(
        tab: BrowserTab,
        onReady: () -> Unit = {},
    ) {
        WebViewProxyPolicy.observeRoute(tab) { usesTor ->
            if (tabs[tab.sessionId] === tab) sendToClient(tab, NappletBrowserContract.MSG_ROUTE) { putBoolean(NappletBrowserContract.KEY_USE_TOR, usesTor) }
        }
        // On Tor without a guard against WebRTC (an old WebView), a page would learn the real address
        // around the proxy: nothing loads, as when Tor's port is unknown.
        if (tab.useTor && (tab.proxyPort <= 0 || !NappletWebRtcGuard.isSupported())) {
            reportRouteBlocked(tab)
            return
        }
        WebViewProxyPolicy.claim(
            owner = tab,
            torPort = if (tab.useTor) tab.proxyPort else NappletProxyClaims.NO_PROXY,
            onFailed = { reportRouteBlocked(tab) },
            onReady = onReady,
        )
    }

    /** The page wasn't loaded because its route can't be honored: tell the client, which shows the error. */
    private fun reportRouteBlocked(tab: BrowserTab) {
        if (tabs[tab.sessionId] !== tab) return
        tab.loadFailed = true
        sendToClient(tab, NappletBrowserContract.MSG_LOAD_STATE) {
            putBoolean(NappletBrowserContract.KEY_IS_LOADING, false)
            putBoolean(NappletBrowserContract.KEY_LOAD_FAILED, true)
            putBoolean(NappletBrowserContract.KEY_ROUTE_BLOCKED, true)
            putString(NappletBrowserContract.KEY_URL, tab.webView?.url ?: tab.url)
        }
    }

    /** Builds [tab]'s WebView with every client, bridge and script wired, without loading anything. */
    private fun buildTabWebView(
        context: Context,
        tab: BrowserTab,
    ): WebView {
        val wv = WebView(nightThemedContext(context, tab.themeType))
        // FIRST touch after construction: setProfile throws once the WebView has loaded content (or its
        // profile has otherwise been used), so the storage partition must be chosen before the
        // configure/bridge setup and the loadUrl below.
        NappletWebViewProfile.apply(context, wv, tab.webViewProfile)
        configureWebView(wv, tab)
        // Theme the pre-load background so a blank/loading page shows Amethyst's background, not white.
        wv.setBackgroundColor(tab.bgColor)
        wv.dropSystemBarInsets()
        WebViewCompat.addWebMessageListener(wv, NappletWebContract.BRIDGE_NAME, setOf("*")) { view, message, sourceOrigin, isMainFrame, replyProxy ->
            onBridgeMessage(tab, view, message, sourceOrigin, isMainFrame, replyProxy)
        }
        // imeProxy: this is the EMBEDDED surface (no native keyboard), so install the IME agent that relays
        // the focused field to the host's keyboard. The full-screen browser activity sets the direct bridge
        // but NOT this flag (it has a real WebView window with a native keyboard).
        WebViewCompat.addDocumentStartJavaScript(wv, BrowserWebTools.browserStartScript(shimJs, imeProxy = true), setOf("*"))
        BrowserWebTools.setTextZoom(wv, tab.textZoom)
        if (tab.desktopSite) BrowserWebTools.setDesktopMode(wv, true)
        tab.webView = wv
        if (tab.paused) wv.onPause()
        return wv
    }

    /** After a renderer crash: a fresh WebView in the same surface, loading [url]. */
    private fun rebuildWebView(
        tab: BrowserTab,
        url: String,
    ) {
        val container = tab.container ?: return
        val wv = buildTabWebView(container.context, tab)
        container.addView(wv, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        claimRoute(tab) { if (tab.webView === wv) wv.loadUrl(url) }
    }

    /** A session closed: drop the tab and destroy its own WebView (never a sibling's). */
    fun onSessionClosed(
        sessionId: String,
        container: FrameLayout,
    ) {
        // Only the session that currently owns the tab may close it. A late close from a session that was
        // already replaced by a re-open would otherwise reap the live one — its WebView destroyed under a
        // client that had just been told the session opened, leaving the surface black for good.
        tabs[sessionId]?.takeIf { it.container === container }?.let(::closeTab)
    }

    /** Drops [tab] and everything it holds (its WebView, broker state, proxy claim). */
    private fun closeTab(tab: BrowserTab) {
        // By identity: a replaced tab closing late must not take its replacement (same id) with it.
        tabs.remove(tab.sessionId, tab)
        WebViewProxyPolicy.release(tab)
        releasePage(tab, closing = true)
        tab.bridge.clear()
        // Release a picker still waiting on this surface before its WebView goes away.
        tab.fileChooser.cancel()
        cancelPending(tab)
        // An unanswered download card dies with its tab: nothing is saved, its bytes are freed, and the
        // client is told so its card doesn't linger with a Save that does nothing.
        pendingDownloads.entries.filter { it.value.sessionId == tab.sessionId }.forEach { (id, _) ->
            pendingDownloads.remove(id)
            sendToClient(tab, NappletBrowserContract.MSG_DOWNLOAD_CANCEL) { putLong(NappletBrowserContract.KEY_DOWNLOAD_ID, id) }
        }
        tab.customViewCallback?.onCustomViewHidden()
        tab.customViewCallback = null
        tab.customView = null
        tab.container = null
        tab.webView?.destroy()
        tab.webView = null
    }

    private fun configureWebView(
        wv: WebView,
        tab: BrowserTab?,
    ) {
        BrowserWebTools.applyBrowserSettings(wv)
        if (tab != null) NappletWebRtcGuard.sync(wv, tab.useTor)
        wv.webViewClient = BrowserClient(tab)
        wv.webChromeClient = BrowserChromeClient(tab)
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            if (tab == null) return@setDownloadListener
            // The page's origin; a fresh popup still on about:blank has none, so name the file's own.
            val origin = wv.url?.let(BrowserChrome::originOf) ?: BrowserChrome.originOf(url) ?: return@setDownloadListener
            if (!canOfferDownload(tab, origin)) return@setDownloadListener
            // Read on the main thread: the cookie jar is the tab's own per-account WebView profile.
            val cookie = BrowserWebTools.cookieManager(wv).getCookie(url)
            prepareDownloadOffer(tab, origin) { ready ->
                BrowserDownloads.offerListenerDownload(url, userAgent, contentDisposition, mimeType, contentLength, cookie, if (tab.useTor) tab.proxyPort else -1, ready)
            }
        }
    }

    /** Answers everything [tab] still has outstanding, so no page stays blocked on a torn-down surface. */
    private fun cancelPending(tab: BrowserTab) {
        tab.jsDialogs.values.forEach { it.cancel() }
        tab.jsDialogs.clear()
        pendingWebPermissions.values.removeAll(tab.permissionRequests.keys)
        tab.permissionRequests.values.forEach { it(emptySet()) }
        tab.permissionRequests.clear()
    }

    private inline fun sendToClient(
        tab: BrowserTab,
        what: Int,
        crossinline block: Bundle.() -> Unit,
    ): Boolean {
        val message = Message.obtain(null, what).apply { data = Bundle().apply(block) }
        return tab.toClient(message)
    }

    private fun pushFindResult(
        tab: BrowserTab,
        active: Int,
        total: Int,
    ) {
        sendToClient(tab, NappletBrowserContract.MSG_FIND_RESULT) {
            putInt(NappletBrowserContract.KEY_FIND_ACTIVE, active)
            putInt(NappletBrowserContract.KEY_FIND_TOTAL, total)
        }
    }

    private var dialogSeq = 0L

    /**
     * Relays a page's JS dialog to the main process, which draws it over the tab (this provider has no
     * window). Answers at once when dialogs are blocked for this page or no client can show one.
     */
    private fun relayJsDialog(
        tab: BrowserTab?,
        type: String,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsResult,
    ): Boolean {
        if (tab == null) {
            result.cancel()
            return true
        }
        if (tab.jsDialogsBlocked) {
            // A blocked page may no longer hold the user on it: leaving is allowed, everything else cancels.
            if (type == "beforeunload") result.confirm() else result.cancel()
            return true
        }
        val id = ++dialogSeq
        tab.jsDialogs[id] = result
        tab.jsDialogsOnPage++
        val sent =
            sendToClient(tab, NappletBrowserContract.MSG_JS_DIALOG) {
                putLong(NappletBrowserContract.KEY_DIALOG_ID, id)
                putString(NappletBrowserContract.KEY_DIALOG_TYPE, type)
                putString(NappletBrowserContract.KEY_URL, url)
                putString(NappletBrowserContract.KEY_DIALOG_MESSAGE, message)
                putString(NappletBrowserContract.KEY_DIALOG_DEFAULT, defaultValue)
                putBoolean(NappletBrowserContract.KEY_DIALOG_OFFER_BLOCK, tab.jsDialogsOnPage > 1)
            }
        if (!sent) tab.jsDialogs.remove(id)?.cancel()
        return true
    }

    private var permissionSeq = 0L

    /** Relays a camera / microphone / location request to the main process, which owns the prompt. */
    private fun relayPermissionRequest(
        tab: BrowserTab?,
        origin: String?,
        wanted: Set<BrowserSitePermission>,
        answer: (Set<BrowserSitePermission>) -> Unit,
    ): Long? {
        if (tab == null || origin == null || wanted.isEmpty()) {
            answer(emptySet())
            return null
        }
        val id = ++permissionSeq
        tab.permissionRequests[id] = answer
        val sent =
            sendToClient(tab, NappletBrowserContract.MSG_PERMISSION_REQUEST) {
                putLong(NappletBrowserContract.KEY_PERMISSION_ID, id)
                putString(NappletBrowserContract.KEY_BROWSER_ORIGIN, origin)
                putStringArray(NappletBrowserContract.KEY_PERMISSIONS, wanted.map { it.key }.toTypedArray())
            }
        if (!sent) tab.permissionRequests.remove(id)?.invoke(emptySet())
        return id
    }

    private fun sitePermissionFor(resource: String): BrowserSitePermission? =
        when (resource) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> BrowserSitePermission.CAMERA
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> BrowserSitePermission.MICROPHONE
            else -> null
        }

    /** HTML fullscreen inside the surface: the page's view covers the tab; back (from the client) leaves it. */
    private fun enterFullscreen(
        tab: BrowserTab?,
        view: View,
        callback: WebChromeClient.CustomViewCallback,
    ) {
        val container = tab?.container
        if (tab == null || container == null || tab.customView != null) {
            callback.onCustomViewHidden()
            return
        }
        tab.customView = view
        tab.customViewCallback = callback
        view.setBackgroundColor(Color.BLACK)
        container.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        sendToClient(tab, NappletBrowserContract.MSG_FULLSCREEN) { putBoolean(NappletBrowserContract.KEY_ENABLED, true) }
    }

    private fun exitFullscreen(tab: BrowserTab) {
        val view = tab.customView ?: return
        tab.customView = null
        tab.container?.removeView(view)
        val callback = tab.customViewCallback
        tab.customViewCallback = null
        callback?.onCustomViewHidden()
        sendToClient(tab, NappletBrowserContract.MSG_FULLSCREEN) { putBoolean(NappletBrowserContract.KEY_ENABLED, false) }
    }

    private inner class BrowserChromeClient(
        private val tab: BrowserTab?,
    ) : WebChromeClient() {
        /**
         * Hands the page's `<input type="file">` to the main process, which owns the only Activity that
         * can run a picker. Always returns true: the callback is ours now, and [requestFileChooser]
         * answers it on every path — otherwise the input would stay dead for the life of the page.
         */
        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean = requestFileChooser(tab, filePathCallback, fileChooserParams)

        /**
         * The embedded surface captures favicons just like the full-screen browser does — a site pinned
         * to a tab but never opened full-screen would otherwise never contribute an icon at all.
         */
        override fun onReceivedIcon(
            view: WebView,
            icon: Bitmap?,
        ) {
            val tab = tab ?: return
            if (icon == null || tab.loadFailed) return
            val host = OmniboxInput.hostOf(view.url ?: return) ?: return
            if (host == tab.lastIconHost) return
            tab.lastIconHost = host
            recordIcon(host, icon)
        }

        /** Relays the page's `<title>` so the tab's top sheet names the page, not just its host. */
        override fun onReceivedTitle(
            view: WebView,
            title: String?,
        ) = pushUrl(tab, view)

        // This WebView is built from a Service context, so the framework can't show JS dialogs itself
        // (it needs an Activity); the main process draws them over the tab instead.
        override fun onJsAlert(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean = relayJsDialog(tab, "alert", url, message, null, result)

        override fun onJsConfirm(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean = relayJsDialog(tab, "confirm", url, message, null, result)

        override fun onJsPrompt(
            view: WebView,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean = relayJsDialog(tab, "prompt", url, message, defaultValue, result)

        override fun onJsBeforeUnload(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean = relayJsDialog(tab, "beforeunload", url, message, null, result)

        /** `_blank` / user-initiated `window.open()`: a new full-screen browser window, `opener` intact. */
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            val tab = tab ?: return false
            if (!isUserGesture) return false
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val (token, child) = BrowserPopups.create(this@NappletBrowserService, shimJs, tab.proxyPort, tab.useTor, tab.themeType, tab.webViewProfile, tab.searchEngine)
            transport.webView = child
            resultMsg.sendToTarget()
            runCatching { startActivity(NappletBrowserActivity.popupIntent(this@NappletBrowserService, token)) }
                .onFailure { Log.w(TAG, "Could not open the new window", it) }
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val wanted = request.resources.mapNotNull(::sitePermissionFor).toSet()
            val id =
                relayPermissionRequest(tab, BrowserChrome.originOf(request.origin.toString()), wanted) { granted ->
                    // Answered: nothing left for a cancellation to withdraw.
                    pendingWebPermissions.remove(request)
                    val resources = request.resources.filter { sitePermissionFor(it) in granted }.toTypedArray()
                    if (resources.isEmpty()) request.deny() else request.grant(resources)
                }
            // Only track it while it is still waiting on the user — it may already have been answered inline
            // (nothing to ask, or the client unreachable), and an entry kept after that would never be removed.
            if (id != null && tab?.permissionRequests?.containsKey(id) == true) pendingWebPermissions[request] = id
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            val id = pendingWebPermissions.remove(request) ?: return
            val tab = tab ?: return
            tab.permissionRequests.remove(id)
            sendToClient(tab, NappletBrowserContract.MSG_PERMISSION_CANCEL) { putLong(NappletBrowserContract.KEY_PERMISSION_ID, id) }
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback,
        ) {
            relayPermissionRequest(tab, BrowserChrome.originOf(origin), setOf(BrowserSitePermission.LOCATION)) { granted ->
                // Never let WebView remember it: the answer lives in the main-process registry.
                callback.invoke(origin, BrowserSitePermission.LOCATION in granted, false)
            }
        }

        override fun onShowCustomView(
            view: View,
            callback: CustomViewCallback,
        ) = enterFullscreen(tab, view, callback)

        override fun onHideCustomView() {
            tab?.let { exitFullscreen(it) }
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            if (tab == null) return false
            pushConsoleLog(
                tab,
                consoleMessage.messageLevel().name,
                consoleMessage.message(),
                consoleMessage.sourceId(),
                consoleMessage.lineNumber(),
            )
            return true
        }
    }

    /**
     * Asks the client (main process) to run the picker for [tab] and remembers the callback until the
     * matching [NappletBrowserContract.MSG_FILE_CHOOSER_RESULT] comes back. A surface with no client to
     * ask — a torn-down tab, a dead Messenger — releases the input immediately instead of leaving it
     * stuck waiting for a reply that can never arrive.
     */
    private fun requestFileChooser(
        tab: BrowserTab?,
        filePathCallback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        val client = tab?.clientMessenger
        if (client == null) {
            filePathCallback.onReceiveValue(null)
            return true
        }
        val id = tab.fileChooser.start(filePathCallback)
        val msg =
            Message.obtain(null, NappletBrowserContract.MSG_FILE_CHOOSER_REQUEST).apply {
                data =
                    Bundle().apply {
                        putLong(NappletBrowserContract.KEY_FILE_CHOOSER_ID, id)
                        putStringArray(NappletBrowserContract.KEY_FILE_CHOOSER_ACCEPT, params.acceptTypes ?: emptyArray())
                        putBoolean(NappletBrowserContract.KEY_FILE_CHOOSER_MULTIPLE, NappletFileChooser.allowsMultiple(params.mode))
                        putBoolean(NappletBrowserContract.KEY_FILE_CHOOSER_CAPTURE, params.isCaptureEnabled)
                        putString(NappletBrowserContract.KEY_FILE_CHOOSER_TITLE, params.title?.toString())
                    }
            }
        if (!tab.toClient(msg)) tab.fileChooser.cancel()
        return true
    }

    private fun pushConsoleLog(
        tab: BrowserTab,
        level: String,
        message: String,
        source: String,
        line: Int,
    ) {
        val msg =
            Message.obtain(null, NappletBrowserContract.MSG_CONSOLE_LOG).apply {
                data =
                    Bundle().apply {
                        putString(NappletBrowserContract.KEY_CONSOLE_LEVEL, level)
                        putString(NappletBrowserContract.KEY_CONSOLE_MESSAGE, message)
                        putString(NappletBrowserContract.KEY_CONSOLE_SOURCE, source)
                        putInt(NappletBrowserContract.KEY_CONSOLE_LINE, line)
                    }
            }
        tab.toClient(msg)
    }

    /** Loads live web pages in-WebView (http/https) and hands other schemes to the system on a user tap. */
    private inner class BrowserClient(
        private val tab: BrowserTab?,
    ) : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return false
            return BrowserWebTools.openExternal(this@NappletBrowserService, uri, request.hasGesture()) { view.loadUrl(it) }
        }

        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: Bitmap?,
        ) {
            // A new main-frame navigation cleared any prior error, and lifts "block this page's dialogs".
            tab?.loadFailed = false
            // The page is being replaced: nothing it asked for (replies, subscription pushes) may reach the next
            // one, even a next one that never talks to the bridge.
            if (tab != null && tab.bridge.onNavigation()) releasePage(tab)
            tab?.jsDialogsOnPage = 0
            tab?.jsDialogsBlocked = false
            // Re-arm favicon capture when the host changes, so a same-host in-page nav doesn't re-send.
            if (tab != null && OmniboxInput.hostOf(url) != tab.lastIconHost) tab.lastIconHost = null
            // view.title still names the page being left; the new one's arrives via onReceivedTitle.
            pushUrl(tab, view, includeTitle = false)
            pushLoadState(tab, view, isLoading = true)
        }

        override fun doUpdateVisitedHistory(
            view: WebView,
            url: String,
            isReload: Boolean,
        ) = pushUrl(tab, view)

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            pushUrl(tab, view)
            pushLoadState(tab, view, isLoading = false)
            if (url.startsWith("https://") || url.startsWith("http://")) scheduleFaviconSniff(tab, view, url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            // Only a main-frame failure blanks the page; sub-resource errors (a missing image, a blocked
            // tracker) are irrelevant to whether the app opened.
            if (!request.isForMainFrame) return
            tab?.loadFailed = true
            pushLoadState(tab, view, isLoading = false)
        }

        /**
         * The renderer died. It is shared by every WebView in `:napplet`, and an unhandled crash kills the
         * whole process — every other tab included. Drop just this tab's WebView and report it gone
         * ([NappletBrowserContract.KEY_RENDERER_GONE]); the client's MSG_RELOAD (or a MSG_NAVIGATE) builds
         * a fresh WebView in the same surface.
         */
        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean {
            Log.w(TAG) { "Renderer gone (crashed=${detail.didCrash()}) for an embedded tab" }
            val lastUrl = view.url
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            val tab = tab ?: return true
            if (tab.webView === view) {
                tab.webView = null
                tab.recoverUrl = lastUrl?.takeIf { it.isNotBlank() && it != ABOUT_BLANK } ?: tab.recoverUrl
                // The page died with its renderer: its broker subscriptions and pick have no one to serve.
                releasePage(tab)
                tab.bridge.clear()
                tab.fileChooser.cancel()
                tab.customView?.let { tab.container?.removeView(it) }
                tab.customView = null
                tab.customViewCallback = null
                cancelPending(tab)
                tab.loadFailed = true
                sendToClient(tab, NappletBrowserContract.MSG_LOAD_STATE) {
                    putBoolean(NappletBrowserContract.KEY_IS_LOADING, false)
                    putBoolean(NappletBrowserContract.KEY_LOAD_FAILED, true)
                    putBoolean(NappletBrowserContract.KEY_RENDERER_GONE, true)
                    putString(NappletBrowserContract.KEY_URL, tab.url)
                }
            }
            return true
        }
    }

    /** Tells the client whether a main-frame load is in flight and whether it failed, so it can overlay a spinner/retry. */
    private fun pushLoadState(
        tab: BrowserTab?,
        view: WebView,
        isLoading: Boolean,
    ) {
        val message =
            Message.obtain(null, NappletBrowserContract.MSG_LOAD_STATE).apply {
                data =
                    Bundle().apply {
                        putBoolean(NappletBrowserContract.KEY_IS_LOADING, isLoading)
                        putBoolean(NappletBrowserContract.KEY_LOAD_FAILED, tab?.loadFailed ?: false)
                        putString(NappletBrowserContract.KEY_URL, view.url.orEmpty())
                    }
            }
        tab?.toClient(message)
    }

    private fun pushUrl(
        tab: BrowserTab?,
        view: WebView,
        includeTitle: Boolean = true,
    ) {
        val url = view.url ?: return
        // WebView reports the URL itself as the title of a document that has none yet; that is no title.
        val title = view.title?.trim()?.takeIf { includeTitle && it.isNotEmpty() && it != url }
        val message =
            Message.obtain(null, NappletBrowserContract.MSG_URL_CHANGED).apply {
                data =
                    Bundle().apply {
                        putString(NappletBrowserContract.KEY_URL, url)
                        putBoolean(NappletBrowserContract.KEY_CAN_GO_BACK, view.canGoBack())
                        putBoolean(NappletBrowserContract.KEY_CAN_GO_FORWARD, view.canGoForward())
                        title?.let { putString(NappletBrowserContract.KEY_TITLE, it) }
                    }
            }
        tab?.toClient(message)
    }

    /**
     * A top-frame NIP-07 call from [sourceOrigin]. The origin is the trusted value the WebView reports
     * (the page can't forge it), so it keys consent; each origin uses its own broker-minted launch token.
     */
    private fun onBridgeMessage(
        tab: BrowserTab,
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (!isMainFrame) return
        // A new document replaced the page: whatever the old one had open with the broker is dead.
        if (tab.bridge.onMessage(replyProxy)) releasePage(tab)
        val raw = message.data ?: return
        val envelope = parseJsonObjectOrNull(raw) ?: return

        // The origin the WebView reports, which the page can't forge, keys every decision below.
        val origin = trustedOrigin(sourceOrigin) ?: return

        // Browser conveniences (share, blob downloads) are handled here, never brokered; a download still
        // asks the user first ([offerInlineDownload]), since any top-frame script can post one. The theme
        // colour only matters to a window with system bars, which an embedded tab doesn't own.
        when (envelope.stringOrNull("type")) {
            "browser.share" -> {
                // Only while the user is looking at this tab: a parked one can't pop share sheets.
                if (tab.attended) BrowserWebTools.share(this, envelope.stringOrNull("title"), envelope.stringOrNull("text"), envelope.stringOrNull("url"))
                return
            }
            "browser.themeColor" -> return
            "browser.download" -> {
                offerInlineDownload(tab, origin, envelope)
                return
            }
        }

        // IME events aren't brokered — the main app hosts the keyboard. Relay the envelope to the client.
        if (envelope.stringOrNull("type").orEmpty().startsWith("ime.")) {
            val reply =
                Message.obtain(null, NappletBrowserContract.MSG_IME_EVENT).apply {
                    data = Bundle().apply { putString(NappletBrowserContract.KEY_IME_PAYLOAD, raw) }
                }
            tab.toClient(reply)
            return
        }

        val pageId = envelope.stringOrNull("id").orEmpty().ifEmpty { "fire-${tab.fireSeq++}" }
        val id = tab.bridge.brokerIdFor(pageId)
        // A relay subscription is named by the page, and its pushes (decrypted events included) come back
        // under that name: stamp this document on it so the next page can never receive them.
        val outgoing = tab.bridge.stampSubscription(envelope)?.toString() ?: raw
        val msg =
            Message.obtain(null, NappletIpc.MSG_REQUEST).apply {
                replyTo = tab.replyMessenger
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_REQUEST_ID, id)
                        putString(NappletIpc.KEY_PAYLOAD, outgoing)
                    }
            }
        // A page on plain http could have had its script put there by anyone on the path, and a grant is kept
        // per origin: only an origin whose transport can't be tampered with gets window.nostr.
        if (!BrowserOriginPolicy.offersNostr(origin)) {
            tab.bridge.failRequest(msg, "This page isn't secure (http): use its https address to sign in.")
            return
        }

        // Nobody is looking at this tab (it's parked, or the app is in the background): a sign / encrypt /
        // decrypt waits until they are, even when "allow always" would let it through without a prompt.
        if (!tab.attended && NappletActingRequests.actsForUser(runCatching { NappletProtocolJson.readType(raw) }.getOrNull())) {
            val refused = tab.heldWhileAway.hold(origin to msg)
            if (refused != null) {
                tab.bridge.failRequest(refused.second, NappletHeldRequests.TOO_MANY)
            } else {
                // Settle it with an error if nobody comes back for it, so the page isn't left waiting forever.
                heldExpiry.postDelayed({ expireHeld(tab) }, NappletHeldRequests.MAX_AGE_MS)
            }
            return
        }
        dispatchToBroker(tab, origin, msg)
    }

    private val heldExpiry = Handler(Looper.getMainLooper())

    private fun expireHeld(tab: BrowserTab) {
        if (tabs[tab.sessionId] !== tab) return
        tab.heldWhileAway.expire().forEach { (_, request) -> tab.bridge.failRequest(request, NappletHeldRequests.EXPIRED) }
    }

    /** Sends [msg] with [origin]'s launch token, minting the token first if the origin has none yet. */
    private fun dispatchToBroker(
        tab: BrowserTab,
        origin: String,
        msg: Message,
    ) {
        val token = tab.originTokens[origin]
        if (token != null) {
            msg.data.putString(NappletIpc.KEY_LAUNCH_TOKEN, token)
            if (brokerMessenger == null) pendingBrokerRequests.add(msg) else sendToBroker(msg)
        } else {
            tab.pendingByOrigin.getOrPut(origin) { mutableListOf() }.add(msg)
            requestBrowserToken(tab, origin)
        }
    }

    private val mintTimeouts = Handler(Looper.getMainLooper())

    /** A token for [origin] won't come: answer each call queued behind it with a failure, and allow a retry. */
    private fun failMint(
        tab: BrowserTab,
        origin: String,
        reason: String,
    ) {
        tab.mintInFlight.remove(origin)
        tab.pendingByOrigin.remove(origin)?.forEach { queued -> tab.bridge.failRequest(queued, reason) }
    }

    /**
     * The page on [tab] is gone (navigated away, renderer died, session closed): drop its requests still
     * waiting for a token or the broker, and have the broker close the live relay / inc subscriptions it
     * opened — otherwise their events would keep streaming into whatever page comes next.
     *
     * [closing]: the whole tab is going away, so its per-origin launch tokens are given back too (a closed
     * tab never re-uses them; a new session mints its own). Otherwise they'd sit in the broker's registry
     * until evicted, pushing live tabs' tokens out first.
     */
    private fun releasePage(
        tab: BrowserTab,
        closing: Boolean = false,
    ) {
        tab.pendingByOrigin.clear()
        tab.heldWhileAway.clear()
        // A mint the broker never answered (none is sent while logged out) would otherwise block the
        // origin for the tab's life; the next page asks again.
        tab.mintInFlight.clear()
        pendingBrokerRequests.removeAll { it.what == NappletIpc.MSG_REQUEST && it.replyTo == tab.replyMessenger }
        val release =
            Message.obtain(null, NappletIpc.MSG_RELEASE_CLIENT).apply {
                replyTo = tab.replyMessenger
                if (closing && tab.originTokens.isNotEmpty()) {
                    data = Bundle().apply { putStringArray(NappletIpc.KEY_RELEASED_TOKENS, tab.originTokens.values.toTypedArray()) }
                }
            }
        if (closing) tab.originTokens.clear()
        if (brokerMessenger != null) sendToBroker(release)
        // The release forgets the tab's attendance along with the rest; the next page is watched just the same.
        if (!closing && tab.attended) reportAttended(tab)
    }

    /** Tells the broker whether [tab] is being looked at, which gates decrypting its relay reads. */
    private fun reportAttended(tab: BrowserTab) {
        val msg =
            Message.obtain(null, NappletIpc.MSG_SET_ATTENDED).apply {
                replyTo = tab.replyMessenger
                data = Bundle().apply { putBoolean(NappletIpc.KEY_ATTENDED, tab.attended) }
            }
        if (brokerMessenger == null) pendingBrokerRequests.add(msg) else sendToBroker(msg)
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
        tab: BrowserTab,
        origin: String,
        envelope: JsonObject,
    ) {
        if (!canOfferDownload(tab, origin)) return
        val data = envelope.stringOrNull("data") ?: return
        prepareDownloadOffer(tab, origin) { ready -> BrowserDownloads.offerInline(data, envelope.stringOrNull("name"), ready) }
    }

    /**
     * Whether [origin] may put a download card up in [tab] now: never over another of the tab's page prompts
     * (so a page can't swap the name under the user's finger, or pop the card where a dialog's button just
     * was), never while an offer is still being prepared (so a page can't queue decodes), and not within
     * its cooldown.
     */
    private fun canOfferDownload(
        tab: BrowserTab,
        origin: String,
    ) = !tab.preparingDownload &&
        tab.jsDialogs.isEmpty() &&
        tab.permissionRequests.isEmpty() &&
        pendingDownloads.values.none { it.sessionId == tab.sessionId } &&
        tab.downloadCooldown.allows(origin)

    /** Runs [prepare] (which answers exactly once, on the main thread) and relays the offer it produces. */
    private fun prepareDownloadOffer(
        tab: BrowserTab,
        origin: String,
        prepare: ((BrowserDownloads.DownloadOffer?) -> Unit) -> Unit,
    ) {
        tab.preparingDownload = true
        prepare { offer ->
            tab.preparingDownload = false
            if (offer != null) showDownloadOffer(tab, origin, offer)
        }
    }

    /**
     * Asks the main process to show [offer]'s consent card (this provider has no window of its own); the
     * file is fetched or written only if the user taps Save there.
     */
    private fun showDownloadOffer(
        tab: BrowserTab,
        origin: String,
        offer: BrowserDownloads.DownloadOffer,
    ) {
        if (tabs[tab.sessionId] !== tab || !canOfferDownload(tab, origin)) return
        val id = ++downloadSeq
        val sent =
            sendToClient(tab, NappletBrowserContract.MSG_DOWNLOAD_CONSENT) {
                putLong(NappletBrowserContract.KEY_DOWNLOAD_ID, id)
                putString(NappletBrowserContract.KEY_BROWSER_ORIGIN, origin)
                putString(NappletBrowserContract.KEY_DOWNLOAD_NAME, offer.fileName)
                putLong(NappletBrowserContract.KEY_DOWNLOAD_SIZE, offer.sizeBytes)
                putString(NappletBrowserContract.KEY_DOWNLOAD_SOURCE, offer.sourceHost)
                putBoolean(NappletBrowserContract.KEY_DOWNLOAD_RISKY, offer.risky)
            }
        if (sent) pendingDownloads[id] = PendingDownload(tab.sessionId, origin, offer)
    }

    private fun requestBrowserToken(
        tab: BrowserTab,
        origin: String,
    ) {
        if (!tab.mintInFlight.add(origin)) return
        // The broker may never answer (it died mid-mint): fail the origin's queued calls rather than let the
        // page wait forever.
        mintTimeouts.postDelayed({
            if (tabs[tab.sessionId] === tab && origin in tab.mintInFlight) failMint(tab, origin, MINT_TIMED_OUT)
        }, NappletIpc.MINT_TIMEOUT_MS)
        val msg =
            Message.obtain(null, NappletIpc.MSG_MINT_BROWSER_TOKEN).apply {
                replyTo = tab.replyMessenger
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_BROWSER_ORIGIN, origin)
                        putString(NappletIpc.KEY_WEBVIEW_PROFILE, tab.webViewProfile)
                    }
            }
        if (brokerMessenger == null) pendingBrokerRequests.add(msg) else sendToBroker(msg)
    }

    /**
     * Second-chance favicon capture for pages `onReceivedIcon` never fires for (SVG-only declarations —
     * WebView does not rasterize those into the callback). Delayed so the WebView's own raster path,
     * which usually lands just after page-finish, gets first claim on the host.
     */
    private fun scheduleFaviconSniff(
        tab: BrowserTab?,
        view: WebView,
        url: String,
    ) {
        if (tab == null) return
        val host = OmniboxInput.hostOf(url) ?: return
        view.postDelayed({
            if (tabs[tab.sessionId] !== tab || tab.loadFailed || host == tab.lastIconHost || view.url != url) return@postDelayed
            NappletFaviconSniffer.capture(view) { sniffedHost, bytes ->
                if (sniffedHost == tab.lastIconHost) return@capture
                tab.lastIconHost = sniffedHost
                recordIconBytes(sniffedHost, bytes)
            }
        }, FAVICON_SNIFF_DELAY_MS)
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
        if (brokerMessenger == null) pendingBrokerRequests.add(msg) else sendToBroker(msg)
    }

    private fun sendToBroker(msg: Message) {
        try {
            brokerMessenger?.send(msg)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deliver request to broker", e)
        }
    }

    /** Broker reply for [tab] — delivered to the tab's own reply Messenger, so it's already scoped. */
    private fun onBrokerReply(
        tab: BrowserTab,
        msg: Message,
    ): Boolean {
        // The reply may arrive after the tab closed (its WebView/page is gone) — drop it so we never
        // postMessage to a dead JavaScriptReplyProxy.
        if (tabs[tab.sessionId] !== tab) return true
        val data = msg.data ?: return true
        when (msg.what) {
            NappletIpc.MSG_RESPONSE -> {
                val brokerId = data.getString(NappletIpc.KEY_REQUEST_ID) ?: return true
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                // Null when the page that asked has been navigated away from: drop it rather than hand
                // one site's answer (a signature, a decryption) to the next.
                val (pageId, proxy) = tab.bridge.resolve(brokerId) ?: return true
                val result = (parseJsonObjectOrNull(payload) ?: JsonObject(emptyMap())).withString("id", pageId)
                runCatching { proxy.postMessage(result.toString()) }
            }
            NappletIpc.MSG_PUSH -> {
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                // Dropped when it is for a subscription a replaced document opened.
                val push = parseJsonObjectOrNull(payload)?.let { tab.bridge.resolvePush(it) } ?: return true
                runCatching { tab.bridge.currentProxy?.postMessage(push.toString()) }
            }
            NappletIpc.MSG_TOKEN_UNKNOWN -> {
                // The broker no longer knows this token (evicted): forget it so the origin re-mints.
                val token = data.getString(NappletIpc.KEY_LAUNCH_TOKEN) ?: return true
                tab.originTokens.values.removeAll { it == token }
            }
            NappletIpc.MSG_BROWSER_TOKEN -> {
                val origin = data.getString(NappletIpc.KEY_BROWSER_ORIGIN) ?: return true
                val token = data.getString(NappletIpc.KEY_LAUNCH_TOKEN)
                if (token == null) {
                    // Refused (no account signed in): the page's calls fail now instead of hanging.
                    failMint(tab, origin, NOT_SIGNED_IN)
                    return true
                }
                tab.originTokens[origin] = token
                tab.mintInFlight.remove(origin)
                tab.pendingByOrigin.remove(origin)?.forEach { queued ->
                    queued.data.putString(NappletIpc.KEY_LAUNCH_TOKEN, token)
                    sendToBroker(queued)
                }
            }
            else -> return false
        }
        return true
    }

    private fun readContractAsset(path: String): ByteArray = assets.open(NappletWebResources.RESOURCE_ASSET_ROOT + path).use { it.readBytes() }

    /** Address-bar text → URL via the shared [OmniboxInput] rules (bare domain → https, else search). */
    private fun normalizeUrl(input: String): String = OmniboxInput.resolve(input)?.url ?: ABOUT_BLANK

    private companion object {
        private const val TAG = "NappletBrowserService"
        private const val NOT_SIGNED_IN = "Sign in to Amethyst to use this site's Nostr features."
        private const val MINT_TIMED_OUT = "Amethyst didn't answer. Reload the page to try again."
        private const val ABOUT_BLANK = "about:blank"

        /** Max favicon edge (px) before sending over IPC — keeps the PNG tiny, well under the Binder limit. */
        private const val ICON_MAX_PX = 96

        /** Grace period after page-finish before the declared-icon sniff runs, so `onReceivedIcon` wins first. */
        private const val FAVICON_SNIFF_DELAY_MS = 1_200L
    }
}
