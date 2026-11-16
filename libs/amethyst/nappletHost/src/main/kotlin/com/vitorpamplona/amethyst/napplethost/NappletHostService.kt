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
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.privacysandbox.ui.provider.toCoreLibInfo
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletActingRequests
import com.vitorpamplona.amethyst.commons.napplet.NappletDownloadProgress
import com.vitorpamplona.amethyst.commons.napplet.NappletHeldRequests
import com.vitorpamplona.amethyst.commons.napplet.NappletSiteServer
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
import kotlinx.serialization.json.JsonObject
import kotlin.concurrent.thread

/**
 * Provider for an **embedded** nsite/napplet tab — the in-app-tab counterpart of [NappletHostActivity].
 * Runs in the keyless `:napplet` process: it serves the trusted shell + the manifest's already-verified
 * blobs (via [NappletContentServer]), relays the applet's `window.napplet.*` calls to the main-process
 * broker (with this launch's single token), and exposes the WebView to the main app as a
 * `SandboxedUiAdapter` so only pixels + input cross the process boundary — never the keys, which live
 * only in the main process where every brokered op is still consent-gated.
 *
 * Shares [NappletHostActivity]'s trust model and resource edge; differs only in being a windowless
 * Service whose surface the main app embeds (vs. an Activity that owns the screen). The trusted chrome
 * is drawn by the main process around the surface. Requires API 30+ (SurfaceControlViewHost).
 */
@RequiresApi(Build.VERSION_CODES.R)
class NappletHostService : Service() {
    private val incoming = Messenger(Handler(Looper.getMainLooper(), ::onClientMessage))

    /**
     * Per-embedded-tab state. A single NappletHostService instance is shared by every embedded
     * napplet/nSite tab (they bind the same Intent), so each tab's config, content server, WebView, and
     * broker bridge live here, keyed by [sessionId]. Each tab has its OWN [replyMessenger] so broker
     * responses AND unsolicited pushes come back tagged to the right tab (the broker echoes replyTo).
     */
    private inner class NappletTab(
        val sessionId: String,
        var clientMessenger: Messenger?,
        val paths: List<PathTag>,
        val servers: List<String>,
        val author: String,
        val identifier: String,
        val launchToken: String,
        val profile: HostProfile,
        val useTor: Boolean,
        var proxyPort: Int,
        val bgColor: Int,
        val themeType: String,
        // Opaque per-account WebView storage-profile name (see NappletWebViewProfile).
        val webViewProfile: String?,
        val declaredDomains: List<String>,
        // Set for a webxdc app: its archive and who runs it.
        val webxdc: WebxdcLaunch? = null,
        // Set for a front page: the one checked document it serves.
        val paper: ByteArray? = null,
    ) {
        // Read on the WebView worker thread in shouldInterceptRequest; written on the main thread in
        // createHostWebView — @Volatile gives the happens-before so the worker never sees a stale null.
        @Volatile var contentServer: NappletContentServer? = null
        var webView: WebView? = null

        // The session's root view; a WebView lost to a renderer crash is rebuilt inside it on retry.
        var container: FrameLayout? = null

        // The client's last pause/resume. A parked tab is paused before its WebView exists (the WebView is
        // only built when the surface opens), so the flag is applied to every WebView built for the tab.
        var paused = false

        // The applet wasn't built because its route can't be honored (Tor wanted, no port): a retry rebuilds it.
        var routeBlocked = false

        // Whether the user is looking at this napplet (NappletEmbedContract.MSG_SET_ATTENDED). Requests that act
        // for the user (publish, pay, upload…) made while they aren't — or while the page is paused — are held
        // here and sent when they're back: pausing the WebView doesn't stop JavaScript. Unattended until the
        // client says otherwise: it sends its state right after every create.
        var attended = false
        val heldWhilePaused = NappletHeldRequests<Message>(SystemClock::elapsedRealtime)

        val mayAct: Boolean get() = attended && !paused
        var bridgeReplyProxy: JavaScriptReplyProxy? = null
        var fireSeq = 0

        // The in-flight `<input type="file">` pick for this surface. The picker itself runs in the main
        // process (this provider is windowless), so the callback waits here for MSG_FILE_CHOOSER_RESULT.
        val fileChooser = PendingFileChooser()

        // Last main-frame error state, pushed to the client so it can show an error/retry overlay over the
        // surface (the embedded surface has no error page of its own).
        var loadFailed = false

        // The user's text size, re-applied when a renderer crash forces a fresh WebView.
        var textZoom = BrowserChrome.DEFAULT_TEXT_ZOOM
        val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { onBrokerReply(this, it) })

        /**
         * Sends [msg] to this tab's client stamped with the session it belongs to, so the client can drop what
         * a session it has since replaced still had in flight (a late file-chooser request, a stale
         * SESSION_READY that would re-arm its view with a dead adapter). Returns whether it was delivered.
         */
        fun toClient(msg: Message): Boolean {
            val client = clientMessenger ?: return false
            msg.data.putString(NappletEmbedContract.KEY_SESSION_ID, sessionId)
            return runCatching { client.send(msg) }.isSuccess
        }
    }

    private val tabs = mutableMapOf<String, NappletTab>()

    // The shell HTML and shim never change; read+decode them once instead of per tab on the main thread.
    private val shellHtml: ByteArray by lazy { readContractAsset(NappletWebResources.SHELL_HTML_PATH) }
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
            WebViewProxyPolicy.release(it)
            releaseFromBroker(it)
            it.fileChooser.cancel()
            it.contentServer?.close()
            it.webView?.destroy()
        }
        tabs.clear()
        if (brokerBound) {
            runCatching { unbindService(brokerConnection) }
            brokerBound = false
        }
        super.onDestroy()
    }

    private fun tabFor(msg: Message): NappletTab? = msg.data?.getString(NappletEmbedContract.KEY_SESSION_ID)?.let { tabs[it] }

    private fun tabFor(view: WebView): NappletTab? = tabs.values.firstOrNull { it.webView === view }

    private fun onClientMessage(msg: Message): Boolean {
        when (msg.what) {
            NappletEmbedContract.MSG_CREATE_SESSION -> {
                val tab = buildTab(msg) ?: return true
                // A re-sent create for an id that is still live must not strand the old tab's WebView, content
                // server and broker state with nothing left to close them.
                tabs[tab.sessionId]?.let(::closeTab)
                tabs[tab.sessionId] = tab
                // Bind the broker once; a re-sent MSG_CREATE_SESSION must not leak a second binding.
                if (!brokerBound) {
                    brokerBound = bindService(Intent().setClassName(this, NappletHostContract.BROKER_SERVICE_CLASS), brokerConnection, BIND_AUTO_CREATE)
                }
                replyWithAdapter(tab)
            }
            NappletEmbedContract.MSG_BACK -> tabFor(msg)?.webView?.let { if (it.canGoBack()) it.goBack() }
            NappletEmbedContract.MSG_RELOAD -> {
                val tab = tabFor(msg) ?: return true
                // The client re-reads Tor's port on a retry: it may have come up (or moved) since the tab was made.
                msg.data
                    ?.getInt(NappletHostContract.EXTRA_PROXY_PORT, 0)
                    ?.takeIf { it != 0 }
                    ?.let { tab.proxyPort = it }
                val container = tab.container
                // After a renderer crash the tab has no WebView, and a tab blocked on its route has only a blank
                // placeholder: the retry builds a fresh one.
                if ((tab.webView == null || tab.routeBlocked) && container != null) {
                    val wv = createHostWebView(container.context, tab.sessionId, container)
                    container.addView(wv, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                } else {
                    tab.webView?.reload()
                }
            }
            // onPause()/onResume() are per-WebView (pause/resume THIS surface's JS/DOM). Do NOT call
            // pauseTimers()/resumeTimers(): they are process-global and would freeze/thaw every WebView in
            // `:napplet` (the browser embed + other napplets), whose lifecycles are independent of this one.
            NappletEmbedContract.MSG_CLOSE_SESSION -> tabFor(msg)?.let(::closeTab)
            NappletEmbedContract.MSG_PAUSE ->
                tabFor(msg)?.let {
                    it.paused = true
                    it.webView?.onPause()
                }
            NappletEmbedContract.MSG_RESUME ->
                tabFor(msg)?.let {
                    it.paused = false
                    it.webView?.onResume()
                    releaseHeld(it)
                }
            NappletEmbedContract.MSG_SET_ATTENDED ->
                tabFor(msg)?.let {
                    it.attended = msg.data?.getBoolean(NappletEmbedContract.KEY_ATTENDED, false) ?: false
                    reportAttended(it)
                    releaseHeld(it)
                }
            NappletEmbedContract.MSG_IME_OP -> {
                val tab = tabFor(msg) ?: return true
                val payload = msg.data?.getString(NappletEmbedContract.KEY_IME_PAYLOAD) ?: return true
                // The proxy can belong to a page that has already gone away; that must not crash the sandbox.
                runCatching { tab.bridgeReplyProxy?.postMessage(payload) }
            }
            NappletEmbedContract.MSG_MAGNIFIER_REQUEST -> onMagnifierRequest(msg)
            NappletEmbedContract.MSG_FIND -> {
                val wv = tabFor(msg)?.webView ?: return true
                val query = msg.data?.getString(NappletEmbedContract.KEY_FIND_QUERY).orEmpty()
                if (query.isEmpty()) wv.clearMatches() else wv.findAllAsync(query)
            }
            NappletEmbedContract.MSG_FIND_NEXT -> tabFor(msg)?.webView?.findNext(msg.data?.getBoolean(NappletEmbedContract.KEY_FIND_FORWARD, true) ?: true)
            NappletEmbedContract.MSG_SET_TEXT_ZOOM -> {
                val tab = tabFor(msg) ?: return true
                tab.textZoom = msg.data?.getInt(NappletEmbedContract.KEY_TEXT_ZOOM, tab.textZoom) ?: tab.textZoom
                tab.webView?.let { BrowserWebTools.setTextZoom(it, tab.textZoom) }
            }
            NappletEmbedContract.MSG_FILE_CHOOSER_RESULT -> {
                val tab = tabFor(msg) ?: return true
                val data = msg.data ?: return true
                val id = data.getLong(NappletEmbedContract.KEY_FILE_CHOOSER_ID)
                // Absent array = the user cancelled; PendingFileChooser turns that into the null the page
                // needs to see so its file input becomes tappable again.
                val uris = data.getStringArray(NappletEmbedContract.KEY_FILE_CHOOSER_URIS)?.map(String::toUri)
                tab.fileChooser.deliver(id, uris?.toTypedArray())
            }
            else -> return false
        }
        return true
    }

    private fun buildTab(msg: Message): NappletTab? {
        val data = msg.data ?: return null
        val sessionId = data.getString(NappletEmbedContract.KEY_SESSION_ID) ?: return null
        val pathList = data.getStringArrayList(NappletHostContract.EXTRA_PATHS) ?: return null
        val hashList = data.getStringArrayList(NappletHostContract.EXTRA_HASHES) ?: return null
        val webxdc = NappletHostContract.webxdcOf(data)
        val profileName = data.getString(NappletHostContract.EXTRA_HOST_PROFILE)
        // Only a PAPER launch may carry a page of its own; anything else serves its listed files.
        val paper = NappletHostContract.paperOf(data)?.takeIf { HostProfile.fromName(profileName) == HostProfile.PAPER }
        // A webxdc app's one file is its archive, and a front page's is its page: each its own extra.
        if (pathList.size != hashList.size || (pathList.isEmpty() && webxdc == null && paper == null)) return null
        val author = data.getString(NappletHostContract.EXTRA_AUTHOR).orEmpty()
        val launchToken = data.getString(NappletHostContract.EXTRA_LAUNCH_TOKEN).orEmpty()
        if (author.isEmpty() || launchToken.isEmpty()) return null

        val requires = data.getStringArrayList(NappletHostContract.EXTRA_REQUIRES) ?: emptyList()
        val profile = HostProfile.fromName(data.getString(NappletHostContract.EXTRA_HOST_PROFILE))
        val declaredDomains = profile.declaredCapabilities(requires).map { it.name.lowercase() }.distinct()

        val tab =
            NappletTab(
                sessionId = sessionId,
                clientMessenger = msg.replyTo,
                paths = pathList.indices.map { PathTag(pathList[it], hashList[it]) },
                servers = data.getStringArrayList(NappletHostContract.EXTRA_SERVERS) ?: emptyList(),
                author = author,
                identifier = data.getString(NappletHostContract.EXTRA_IDENTIFIER).orEmpty(),
                launchToken = launchToken,
                profile = profile,
                useTor = data.getBoolean(NappletHostContract.EXTRA_USE_TOR, true),
                proxyPort = data.getInt(NappletHostContract.EXTRA_PROXY_PORT, -1),
                bgColor = data.getInt(NappletHostContract.EXTRA_BG_COLOR, android.graphics.Color.WHITE),
                themeType = data.getString(NappletHostContract.EXTRA_THEME).orEmpty().ifBlank { "SYSTEM" },
                webViewProfile = data.getString(NappletHostContract.EXTRA_WEBVIEW_PROFILE),
                declaredDomains = declaredDomains,
                webxdc = webxdc,
                paper = paper,
            )
        return tab
    }

    /**
     * Draw a zoomed slice of the live WebView (the loupe content) and ship it back as a PNG. The WebView is a
     * real in-window view here in the provider, so its software draw renders real DOM pixels — unlike host-side
     * `PixelCopy` of the sandbox surface. Mirror of the browser path. Runs on the main looper (`WebView.draw`).
     */
    private fun onMagnifierRequest(msg: Message) {
        val tab = tabFor(msg) ?: return
        val wv = tab.webView ?: return
        val data = msg.data ?: return
        val reqT = data.getLong(NappletEmbedContract.KEY_MAG_REQ_T)
        MagnifierCapture.capture(
            webView = wv,
            bgColor = tab.bgColor,
            cx = data.getFloat(NappletEmbedContract.KEY_MAG_X),
            cy = data.getFloat(NappletEmbedContract.KEY_MAG_Y),
            boxW = data.getInt(NappletEmbedContract.KEY_MAG_BOX_W, 150).coerceIn(16, 1024),
            boxH = data.getInt(NappletEmbedContract.KEY_MAG_BOX_H, 84).coerceIn(16, 1024),
            zoom = data.getFloat(NappletEmbedContract.KEY_MAG_ZOOM, 1.6f).coerceIn(1f, 4f),
        ) { bytes, outW, outH, captureMs ->
            val reply =
                Message.obtain(null, NappletEmbedContract.MSG_MAGNIFIER_FRAME).apply {
                    this.data =
                        Bundle().apply {
                            putByteArray(NappletEmbedContract.KEY_MAG_BYTES, bytes)
                            putInt(NappletEmbedContract.KEY_MAG_W, outW)
                            putInt(NappletEmbedContract.KEY_MAG_H, outH)
                            putDouble(NappletEmbedContract.KEY_MAG_CAPTURE_MS, captureMs)
                            putLong(NappletEmbedContract.KEY_MAG_REQ_T, reqT)
                        }
                }
            tab.toClient(reply)
        }
    }

    /** Builds the SandboxedUiAdapter for [tab] and ships its cross-process handle (coreLibInfo) to the client. */
    @Suppress("DEPRECATION") // androidx.privacysandbox.ui toCoreLibInfo is deprecated with no successor.
    private fun replyWithAdapter(tab: NappletTab) {
        val adapter = NappletHostUiAdapter(this, tab.sessionId)
        val coreLibInfo = adapter.toCoreLibInfo(this)
        val reply =
            Message.obtain(null, NappletEmbedContract.MSG_SESSION_READY).apply {
                data = Bundle().apply { putBundle(NappletEmbedContract.KEY_CORE_LIB_INFO, coreLibInfo) }
            }
        tab.toClient(reply)
    }

    /**
     * Builds the session WebView (called by the adapter on the main thread when the client attaches the
     * surface). Mirrors [NappletHostActivity]: serves the shell + verified blobs through the content
     * server, installs the origin-restricted shell bridge, loads the trusted shell URL.
     */
    fun createHostWebView(
        context: Context,
        sessionId: String,
        container: FrameLayout,
    ): WebView {
        // The session may have been closed between MSG_CREATE_SESSION and this posted call — fail rather
        // than build a WebView that no tab tracks (it would leak).
        val tab = tabs[sessionId] ?: error("No napplet tab for session $sessionId")
        // A session re-opened on this tab (the client's view detached and re-attached) before the old one's
        // close landed: that session's WebView is still here. Destroy it now — its close will be ignored
        // (see onSessionClosed), and overwriting it would leak it.
        tab.webView?.let { stale ->
            (stale.parent as? ViewGroup)?.removeView(stale)
            stale.destroy()
            tab.webView = null
            tab.bridgeReplyProxy = null
        }
        tab.container = container
        // A rebuild after a renderer crash: release the previous content server first.
        tab.contentServer?.close()
        tab.contentServer = null
        // Fail closed: Tor is wanted but has no port yet. Nothing may be fetched — not the applet's blobs (the
        // content server would fetch them directly) and not its own traffic — so leave a blank placeholder and
        // tell the client, whose Retry sends the port once Tor is up.
        // The same when WebRTC must be taken away and this WebView can't: its UDP would be a network the
        // app must not have (a napplet, a webxdc app) or go around Tor.
        if ((tab.useTor && tab.proxyPort <= 0) || (NappletWebRtcGuard.isNeeded(tab.profile, tab.useTor) && !NappletWebRtcGuard.isSupported())) {
            val blank = WebView(nightThemedContext(context, tab.themeType)).apply { setBackgroundColor(tab.bgColor) }
            tab.webView = blank
            reportRouteBlocked(tab)
            return blank
        }
        tab.routeBlocked = false
        val wv = WebView(nightThemedContext(context, tab.themeType))
        // FIRST touch after construction: setProfile throws once the WebView has loaded content (or its
        // profile has otherwise been used), so the storage partition must be chosen before the
        // hardening/bridge setup and the loadUrl below.
        NappletWebViewProfile.apply(context, wv, tab.webViewProfile)
        val appOrigin = NappletWebContract.appOrigin(NappletWebContract.appId(tab.author, tab.identifier, profile = tab.profile), tab.profile)
        val effectiveProxy = if (tab.useTor) tab.proxyPort else -1
        tab.contentServer =
            NappletContentServer(
                tab.paths,
                tab.servers,
                effectiveProxy,
                cacheDir,
                NappletBlobCache.partitionOf(tab.webViewProfile.orEmpty(), tab.author, tab.identifier),
                shellHtml,
                shimJs,
                appOrigin,
                tab.profile,
                tab.declaredDomains,
                imeProxy = true,
                webxdc = tab.webxdc,
                paper = tab.paper,
            )

        hardenWebView(wv, tab)
        // Theme the pre-load background so the shell/app loading shows Amethyst's background, not white.
        wv.setBackgroundColor(tab.bgColor)
        wv.dropSystemBarInsets()
        WebViewCompat.addWebMessageListener(wv, NappletWebContract.BRIDGE_NAME, setOf(NappletWebContract.ORIGIN), ::onShellMessage)
        wv.setFindListener { active, total, _ -> pushFindResult(tab, active, total) }
        if (tab.textZoom != BrowserChrome.DEFAULT_TEXT_ZOOM) BrowserWebTools.setTextZoom(wv, tab.textZoom)
        tab.webView = wv
        if (tab.paused) wv.onPause()
        if (tab.profile.exposesNetwork) {
            // The site's own off-origin traffic follows the process-wide route; load once it's in place so
            // a Tor nSite's first request can't leave over the open web — and not at all if it can't be.
            WebViewProxyPolicy.observeRoute(tab) { usesTor ->
                if (tabs[tab.sessionId] === tab) {
                    tab.toClient(Message.obtain(null, NappletEmbedContract.MSG_ROUTE).apply { data = Bundle().apply { putBoolean(NappletEmbedContract.KEY_ROUTE_TOR, usesTor) } })
                }
            }
            WebViewProxyPolicy.claim(
                owner = tab,
                torPort = effectiveProxy,
                onFailed = { reportRouteBlocked(tab) },
            ) { if (tab.webView === wv) wv.loadUrl(NappletWebContract.SHELL_URL) }
        } else if (tab.webxdc != null) {
            // A webxdc archive downloads on first use, inside the page's own request, where a failure
            // is only a 404 in an empty frame. Fetch it first, as the activity does, so a miss shows
            // the retry instead.
            val server = tab.contentServer
            pushLoadState(tab, isLoading = true)
            thread(name = "webxdc-probe", isDaemon = true) {
                val available =
                    server?.isAvailable(NappletSiteServer.INDEX_PATH) { download ->
                        // This probe's WebView only: a rebuilt tab's newer probe owns the bar now.
                        probeResults.post { if (tabs[tab.sessionId] === tab && tab.webView === wv) pushDownload(tab, download) }
                    } == true
                probeResults.post {
                    if (tabs[tab.sessionId] !== tab || tab.webView !== wv) return@post
                    // The watch stops with the probe and may miss the download's end.
                    pushDownload(tab, null)
                    if (available) {
                        wv.loadUrl(NappletWebContract.SHELL_URL)
                    } else {
                        // Dropped, so the client's retry builds the tab again, with a new download.
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        wv.destroy()
                        tab.webView = null
                        tab.bridgeReplyProxy = null
                        tab.loadFailed = true
                        pushLoadState(tab, isLoading = false)
                    }
                }
            }
        } else {
            wv.loadUrl(NappletWebContract.SHELL_URL)
        }
        return wv
    }

    /** A session closed: drop the tab, release its resources, and destroy its own WebView (never a sibling's). */
    fun onSessionClosed(
        sessionId: String,
        container: FrameLayout,
    ) {
        // Only the session that currently owns the tab may close it. A late close from a session that was
        // already replaced by a re-open would otherwise reap the live one — its WebView destroyed under a
        // client that had just been told the session opened, leaving the surface black for good.
        tabs[sessionId]?.takeIf { it.container === container }?.let(::closeTab)
    }

    /** Drops [tab] and everything it holds (its WebView, content server, broker state, proxy claim). */
    private fun closeTab(tab: NappletTab) {
        // By identity: a replaced tab closing late must not take its replacement (same id) with it.
        tabs.remove(tab.sessionId, tab)
        tab.bridgeReplyProxy = null
        WebViewProxyPolicy.release(tab)
        releaseFromBroker(tab)
        // Release a picker still waiting on this surface before its WebView goes away.
        tab.fileChooser.cancel()
        tab.contentServer?.close()
        tab.contentServer = null
        tab.webView?.destroy()
        tab.webView = null
        tab.container = null
    }

    @Suppress("SetJavaScriptEnabled")
    private fun hardenWebView(
        wv: WebView,
        tab: NappletTab,
    ) {
        wv.settings.apply {
            javaScriptEnabled = true
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
        wv.overScrollMode = View.OVER_SCROLL_NEVER
        WebView.setWebContentsDebuggingEnabled(false)
        if (NappletWebRtcGuard.isNeeded(tab.profile, tab.useTor)) NappletWebRtcGuard.install(wv)
        NappletWebRtcGuard.installCookieGuard(wv)
        wv.webViewClient = HostClient(tab)
        wv.webChromeClient = HostChromeClient(tab)
    }

    /**
     * The applet's file picker. This surface has no other need for a chrome client — console output and
     * the loading bar are drawn by the main process from [NappletEmbedContract.MSG_LOAD_STATE] — but
     * without one WebView silently ignores every `<input type="file">`.
     */
    private inner class HostChromeClient(
        private val tab: NappletTab,
    ) : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean = requestFileChooser(tab, filePathCallback, fileChooserParams)

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            pushConsoleLog(tab, consoleMessage.messageLevel().name, consoleMessage.message(), consoleMessage.sourceId(), consoleMessage.lineNumber())
            return true
        }

        // Setting a chrome client at all is what opts this WebView into the default JS-dialog handling,
        // and this one is built from a Service context — there is no window token to attach a dialog to,
        // and an applet's alert() must not be able to draw over the main app's trusted chrome anyway.
        // Dismiss all three so the page's JS resumes instead of blocking on a dialog that never appears.
        override fun onJsAlert(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean {
            result.cancel()
            return true
        }

        override fun onJsConfirm(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean {
            result.cancel()
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean {
            result.cancel()
            return true
        }
    }

    /**
     * Asks the client (main process) to run the picker for [tab] and holds the callback until the
     * matching [NappletEmbedContract.MSG_FILE_CHOOSER_RESULT] arrives. A surface with no client to ask
     * releases the input immediately rather than leaving it stuck on a reply that can never come.
     *
     * The user names the one file that crosses into the sandbox by picking it, so this needs no
     * capability of its own — same user-mediated grant a browser gives a page.
     */
    private fun requestFileChooser(
        tab: NappletTab,
        filePathCallback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        val client = tab.clientMessenger
        if (client == null) {
            filePathCallback.onReceiveValue(null)
            return true
        }
        val id = tab.fileChooser.start(filePathCallback)
        val msg =
            Message.obtain(null, NappletEmbedContract.MSG_FILE_CHOOSER_REQUEST).apply {
                data =
                    Bundle().apply {
                        putLong(NappletEmbedContract.KEY_FILE_CHOOSER_ID, id)
                        putStringArray(NappletEmbedContract.KEY_FILE_CHOOSER_ACCEPT, params.acceptTypes ?: emptyArray())
                        putBoolean(NappletEmbedContract.KEY_FILE_CHOOSER_MULTIPLE, NappletFileChooser.allowsMultiple(params.mode))
                        putBoolean(NappletEmbedContract.KEY_FILE_CHOOSER_CAPTURE, params.isCaptureEnabled)
                        putString(NappletEmbedContract.KEY_FILE_CHOOSER_TITLE, params.title?.toString())
                    }
            }
        if (!tab.toClient(msg)) tab.fileChooser.cancel()
        return true
    }

    /** Serves only the trusted shell and the manifest's verified blobs; external links go to the system. */
    private inner class HostClient(
        private val tab: NappletTab,
    ) : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = tab.contentServer?.serve(request)

        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: Bitmap?,
        ) {
            // A new main-frame navigation cleared any prior error.
            tab.loadFailed = false
            pushLoadState(tab, isLoading = true)
        }

        override fun doUpdateVisitedHistory(
            view: WebView,
            url: String,
            isReload: Boolean,
        ) = pushState(tab, view)

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            pushState(tab, view)
            pushLoadState(tab, isLoading = false)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            pushConsoleLog(tab, ConsoleMessage.MessageLevel.ERROR.name, getString(R.string.napplet_console_load_error, error.errorCode, error.description?.toString().orEmpty()), request.url?.toString().orEmpty(), 0)
            // Only a main-frame failure blanks the applet; a missing sub-resource is irrelevant to whether
            // it opened.
            if (!request.isForMainFrame) return
            tab.loadFailed = true
            pushLoadState(tab, isLoading = false)
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            pushConsoleLog(tab, ConsoleMessage.MessageLevel.ERROR.name, getString(R.string.napplet_console_http_error, errorResponse.statusCode, errorResponse.reasonPhrase.orEmpty()), request.url?.toString().orEmpty(), 0)
        }

        /**
         * The renderer died. It is shared by every WebView in `:napplet`, and an unhandled crash kills the
         * whole process — every other tab included. Drop just this tab's WebView and report it gone
         * ([NappletEmbedContract.KEY_RENDERER_GONE]); the client's MSG_RELOAD rebuilds it in the same surface.
         */
        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean {
            Log.w(TAG) { "Renderer gone (crashed=${detail.didCrash()}) for an embedded napplet/nsite" }
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            if (tab.webView === view) {
                tab.webView = null
                tab.bridgeReplyProxy = null
                tab.loadFailed = true
                pushLoadState(tab, isLoading = false, rendererGone = true)
            }
            return true
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val uri = request.url
            if (NappletWebContract.isInternalHost(uri.host)) return false
            if (tab.profile.opensLinks && request.hasGesture() && (uri.scheme == "https" || uri.scheme == "http")) {
                runCatching { startActivity(linkIntent(tab.profile, uri)) }
            }
            return true
        }
    }

    private fun pushState(
        tab: NappletTab,
        view: WebView,
    ) {
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_STATE).apply {
                data = Bundle().apply { putBoolean(NappletEmbedContract.KEY_CAN_GO_BACK, view.canGoBack()) }
            }
        tab.toClient(message)
    }

    private fun pushFindResult(
        tab: NappletTab,
        active: Int,
        total: Int,
    ) {
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_FIND_RESULT).apply {
                data =
                    Bundle().apply {
                        putInt(NappletEmbedContract.KEY_FIND_ACTIVE, active)
                        putInt(NappletEmbedContract.KEY_FIND_TOTAL, total)
                    }
            }
        tab.toClient(message)
    }

    private fun pushConsoleLog(
        tab: NappletTab,
        level: String,
        text: String,
        source: String,
        line: Int,
    ) {
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_CONSOLE_LOG).apply {
                data =
                    Bundle().apply {
                        putString(NappletEmbedContract.KEY_CONSOLE_LEVEL, level)
                        putString(NappletEmbedContract.KEY_CONSOLE_MESSAGE, text)
                        putString(NappletEmbedContract.KEY_CONSOLE_SOURCE, source)
                        putInt(NappletEmbedContract.KEY_CONSOLE_LINE, line)
                    }
            }
        tab.toClient(message)
    }

    /** Tells the client whether a main-frame load is in flight and whether it failed, so it can overlay a spinner/retry. */
    private fun pushLoadState(
        tab: NappletTab,
        isLoading: Boolean,
        rendererGone: Boolean = false,
        routeBlocked: Boolean = false,
    ) {
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_LOAD_STATE).apply {
                data =
                    Bundle().apply {
                        putBoolean(NappletEmbedContract.KEY_IS_LOADING, isLoading)
                        putBoolean(NappletEmbedContract.KEY_LOAD_FAILED, tab.loadFailed)
                        putBoolean(NappletEmbedContract.KEY_RENDERER_GONE, rendererGone)
                        putBoolean(NappletEmbedContract.KEY_ROUTE_BLOCKED, routeBlocked)
                    }
            }
        tab.toClient(message)
    }

    /** Tells the client how far the tab's download has come, or (null) that it ended, for its loading bar. */
    private fun pushDownload(
        tab: NappletTab,
        download: NappletDownloadProgress?,
    ) {
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_DOWNLOAD).apply {
                data =
                    Bundle().apply {
                        if (download != null) {
                            putLong(NappletEmbedContract.KEY_DOWNLOAD_BYTES, download.bytes)
                            putLong(NappletEmbedContract.KEY_DOWNLOAD_TOTAL, download.total ?: -1)
                        }
                    }
            }
        tab.toClient(message)
    }

    /** The applet wasn't loaded because its route can't be honored: tell the client, which shows the error. */
    private fun reportRouteBlocked(tab: NappletTab) {
        if (tabs[tab.sessionId] !== tab) return
        // Whatever WebView the tab has never loaded the applet: the retry must rebuild it, not reload it.
        tab.routeBlocked = true
        tab.loadFailed = true
        pushLoadState(tab, isLoading = false, routeBlocked = true)
    }

    // ---- bridge: shell <-> native (mirror of NappletHostActivity.onShellMessage) ----

    private fun onShellMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (!isMainFrame) return
        val tab = tabFor(view) ?: return
        tab.bridgeReplyProxy = replyProxy

        val raw = message.data ?: return
        val envelope = parseJsonObjectOrNull(raw) ?: return

        // IME events aren't brokered — the main app hosts the keyboard. Relay the envelope to the client.
        if (envelope.stringOrNull("type").orEmpty().startsWith("ime.")) {
            val reply =
                Message.obtain(null, NappletEmbedContract.MSG_IME_EVENT).apply {
                    data = Bundle().apply { putString(NappletEmbedContract.KEY_IME_PAYLOAD, raw) }
                }
            tab.toClient(reply)
            return
        }

        val id = envelope.stringOrNull("id").orEmpty().ifEmpty { "fire-${tab.fireSeq++}" }
        val msg =
            Message.obtain(null, NappletIpc.MSG_REQUEST).apply {
                replyTo = tab.replyMessenger
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_REQUEST_ID, id)
                        putString(NappletIpc.KEY_PAYLOAD, raw)
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, tab.launchToken)
                    }
            }
        // Nobody is looking (parked off-screen, or the app is in the background): an act on the user's behalf
        // waits until they're looking at this napplet again.
        if (!tab.mayAct && NappletActingRequests.actsForUser(runCatching { NappletProtocolJson.readType(raw) }.getOrNull())) {
            val refused = tab.heldWhilePaused.hold(msg)
            if (refused != null) {
                tab.bridgeReplyProxy?.failRequest(refused, NappletHeldRequests.TOO_MANY)
            } else {
                // Settle it with an error if nobody comes back for it, so the applet isn't left waiting forever.
                heldExpiry.postDelayed({ expireHeld(tab) }, NappletHeldRequests.MAX_AGE_MS)
            }
            return
        }
        if (brokerMessenger == null) pendingBrokerRequests.add(msg) else sendToBroker(msg)
    }

    /** Sends [tab]'s held acting requests once it may act again (attended and not paused). */
    private fun releaseHeld(tab: NappletTab) {
        if (!tab.mayAct) return
        val held = tab.heldWhilePaused.drain()
        held.fail.forEach { tab.bridgeReplyProxy?.failRequest(it, NappletHeldRequests.EXPIRED) }
        held.send.forEach { request -> if (brokerMessenger == null) pendingBrokerRequests.add(request) else sendToBroker(request) }
    }

    private val heldExpiry = Handler(Looper.getMainLooper())

    // Where a webxdc tab's availability probe hands its answer back to the main thread.
    private val probeResults = Handler(Looper.getMainLooper())

    /** Tells the broker whether [tab] is being looked at, which gates decrypting its relay reads. */
    private fun reportAttended(tab: NappletTab) {
        val msg =
            Message.obtain(null, NappletIpc.MSG_SET_ATTENDED).apply {
                replyTo = tab.replyMessenger
                data =
                    Bundle().apply {
                        putBoolean(NappletIpc.KEY_ATTENDED, tab.attended)
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, tab.launchToken)
                    }
            }
        if (brokerMessenger == null) pendingBrokerRequests.add(msg) else sendToBroker(msg)
    }

    private fun expireHeld(tab: NappletTab) {
        if (tabs[tab.sessionId] !== tab) return
        tab.heldWhilePaused.expire().forEach { tab.bridgeReplyProxy?.failRequest(it, NappletHeldRequests.EXPIRED) }
    }

    /**
     * [tab] is gone: have the broker close the live relay / inc subscriptions it opened and drop its reply
     * Messenger (a binder the main process would otherwise hold, keeping the tab alive). The launch token is
     * NOT given back: the main-process controller re-creates sessions with the same token, and gives it
     * back itself when it is torn down.
     */
    private fun releaseFromBroker(tab: NappletTab) {
        tab.heldWhilePaused.clear()
        pendingBrokerRequests.removeAll { it.replyTo == tab.replyMessenger }
        if (brokerMessenger == null) return
        sendToBroker(Message.obtain(null, NappletIpc.MSG_RELEASE_CLIENT).apply { replyTo = tab.replyMessenger })
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
        tab: NappletTab,
        msg: Message,
    ): Boolean {
        // The reply may arrive after the tab closed (its WebView/page is gone) — drop it so we never
        // postMessage to a dead JavaScriptReplyProxy.
        if (tabs[tab.sessionId] !== tab) return true
        val data = msg.data ?: return true
        when (msg.what) {
            NappletIpc.MSG_RESPONSE -> {
                val id = data.getString(NappletIpc.KEY_REQUEST_ID) ?: return true
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                val result = (parseJsonObjectOrNull(payload) ?: JsonObject(emptyMap())).withString("id", id)
                notifyIfSensitive(tab, result)
                runCatching { tab.bridgeReplyProxy?.postMessage(result.toString()) }
            }
            NappletIpc.MSG_PUSH -> {
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                runCatching { tab.bridgeReplyProxy?.postMessage(payload) }
            }
            else -> return false
        }
        return true
    }

    /** Pushes a notice to the main process for a granted "allow always" sensitive op, so it can toast. */
    private fun notifyIfSensitive(
        tab: NappletTab,
        result: JsonObject,
    ) {
        if (result.booleanOrNull("ok") != true) return
        val notice =
            when (result.stringOrNull("type")) {
                "relay.publish.result", "relay.publishEncrypted.result" -> NappletEmbedContract.NOTICE_PUBLISHED
                "upload.upload.result" -> NappletEmbedContract.NOTICE_UPLOADED
                "value.payInvoice.result" -> NappletEmbedContract.NOTICE_PAID
                else -> return
            }
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_NOTICE).apply {
                data = Bundle().apply { putString(NappletEmbedContract.KEY_NOTICE, notice) }
            }
        tab.toClient(message)
    }

    private fun readContractAsset(path: String): ByteArray = assets.open(NappletWebResources.RESOURCE_ASSET_ROOT + path).use { it.readBytes() }

    private companion object {
        private const val TAG = "NappletHostService"
    }
}
