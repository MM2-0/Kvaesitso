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
package com.vitorpamplona.amethyst.napplet

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.SystemClock
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.DataStoreNappletStorage
import com.vitorpamplona.amethyst.commons.napplet.NappletActingRequests
import com.vitorpamplona.amethyst.commons.napplet.NappletAttendance
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletHeldRequests
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentityWatch
import com.vitorpamplona.amethyst.commons.napplet.NappletIncBus
import com.vitorpamplona.amethyst.commons.napplet.NappletIntentCatalog
import com.vitorpamplona.amethyst.commons.napplet.NappletIntentDispatcher
import com.vitorpamplona.amethyst.commons.napplet.NappletLiveSubscriptions
import com.vitorpamplona.amethyst.commons.napplet.NappletRequestRouter
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsSession
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.amethyst.favorites.WebShortcuts
import com.vitorpamplona.amethyst.napplet.gateways.AndroidNappletFilePort
import com.vitorpamplona.amethyst.napplet.gateways.AndroidNappletGateways
import com.vitorpamplona.amethyst.napplethost.NappletHostActivity
import com.vitorpamplona.amethyst.napplethost.NappletHostContract
import com.vitorpamplona.amethyst.napplethost.NappletIpc
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * The trust boundary's main-process endpoint. The untrusted `:napplet` process binds this
 * service and sends requests as JSON over a [Messenger]; this is the only side that holds the
 * signer, the relays, and the permission ledger. It runs each request through the shared
 * [NappletBroker] (built per account by [AndroidNappletGateways]) via the host-agnostic
 * [NappletRequestRouter], which gates everything on consent and never returns key material.
 *
 * This class is intentionally thin: it owns the Messenger transport, the per-account broker cache,
 * and the live-subscription registry, and delegates all decode/policy/execution to the shared core.
 *
 * `exported=false` in the manifest restricts binding to this app's own UID, so no other
 * installed app can reach the broker. A renderer escape that reaches the `:napplet` process
 * could still bind here — but it gains only what the user explicitly consents to, per
 * capability, and never the private key (the broker's contract).
 */
class NappletBrokerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Persistent grants on disk, session grants in RAM. Shared app-wide (see AppModules) so the
    // Connected Apps screens revoke the very grants this broker consults; session grants are dropped
    // in onDestroy, which is the "all applet surfaces closed" boundary.
    private val ledger get() = Amethyst.instance.nappletPermissionLedger

    // Per-app internal-signer permission ledger (policy + per-op overrides). Lazy so it's only
    // instantiated in the main process where the signer lives; never touched from :napplet.
    private val signerLedger by lazy { NostrSignerPermissionLedger(Amethyst.instance.signerPermissionStore) }

    // Per-applet sandboxed key-value store (namespaced by account + coordinate inside the impl).
    private val storage by lazy { DataStoreNappletStorage(Amethyst.instance.appStores.getDataStore("napplet_storage"), Amethyst.instance.nappletAccountScope) }

    private val incoming by lazy { Messenger(Handler(Looper.getMainLooper(), ::handleMessage)) }

    // Live relay subscriptions, keyed by the requesting surface plus the applet's subId. The account comes per-open from the
    // requesting surface's launch token, so a surface's REQs always target the account it acts as.
    // Which surfaces the user is looking at: relay reads are decrypted for a page only while it is.
    private val attendance = NappletAttendance<Messenger>()
    private val liveSubscriptions = NappletLiveSubscriptions(scope, attendance)

    // NAP-RESOURCE cancellation is keyed by the trusted launch token plus the caller's request id.
    // Cancelling removes the job before it can emit a late terminal envelope to the sandbox.
    private val resourceRequests = ConcurrentHashMap<String, Job>()

    // NAP-FS: what the user picked for each running surface, keyed by its launch token. Picks belong to
    // that one surface, so they go when it gives its token back (or when this service ends).
    private val files = ConcurrentHashMap<String, NappletFsSession>()
    private val filePort by lazy { AndroidNappletFilePort(applicationContext) }

    private fun filesFor(
        launchToken: String?,
        declared: Set<NappletCapability>,
    ): NappletFsSession? {
        if (launchToken == null || NappletCapability.FS !in declared) return null
        return files.getOrPut(launchToken) { NappletFsSession(filePort) }
    }

    private fun dropFiles(launchToken: String) {
        files.remove(launchToken)?.close()
    }

    // NAP-INC between the live napplet surfaces. A surface is its reply Messenger plus the launch token
    // it runs under, so a tab that loads another napplet becomes a new endpoint, not the old one.
    private val incBus = NappletIncBus<IncEndpoint>()

    private data class IncEndpoint(
        val replyTo: Messenger,
        val launchToken: String,
    )

    // NAP-INTENT: a handler is one of the napplets the user pinned, opened full screen. Keyed by the
    // launch token the target runs under, which is what its `intent.ready` arrives with.
    private val intents by lazy {
        NappletIntentDispatcher(
            catalog = { NappletIntentCatalog.of(Amethyst.instance.favoriteApps) },
            launch = { handler -> withContext(Dispatchers.Main) { launchHandler(handler.manifest) } },
        )
    }

    // Opens [manifest] in the sandbox host and returns the launch token it runs under, or null when refused.
    private fun launchHandler(manifest: NappletManifest): String? {
        val identifier = (manifest as? NamedNappletEvent)?.identifier() ?: ""
        val params = NappletLauncher.buildLaunchParams(this, manifest, (manifest as Event).pubKey, identifier) ?: return null
        val token = params.getString(NappletHostContract.EXTRA_LAUNCH_TOKEN) ?: return null
        val intent =
            Intent(this, NappletHostActivity::class.java).apply {
                putExtras(params)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return runCatching { startActivity(intent) }.map { token }.getOrElse {
            Log.w("NappletBrokerService", "Could not open an intent handler", it)
            null
        }
    }

    // Streams identity.changed to a watching applet. Bound to the surface's LAUNCH account, not the
    // app's active one: a surface acts as the account that opened it for its whole life, so switching
    // accounts elsewhere is not an identity change *for it*. Announcing the newly-active pubkey here
    // would tell a page it had become someone else while its signatures still came back as the
    // original — the same desync the launch binding exists to prevent. What this does still report is
    // that account going away (logout/removal), which emits "".
    private val identityWatch =
        NappletIdentityWatch(scope) { boundPubKey ->
            Amethyst.instance.accountsCache.accounts
                .map { loaded -> if (loaded.containsKey(boundPubKey)) boundPubKey else "" }
        }

    private val themeRecipients = mutableMapOf<Messenger, String>()
    private var lastTheme: String? = null

    override fun onCreate() {
        super.onCreate()
        // A token that the registry's cap pushed out was never given back: its surface is gone.
        NappletLaunchRegistry.onEvicted = { token ->
            dropFiles(token)
            incBus.removeAll { endpoint -> endpoint.launchToken == token }
            intents.remove(token)
        }
        scope.launch(Dispatchers.Main.immediate) {
            Amethyst.instance.uiPrefs.value.theme
                .collect { broadcastTheme() }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        broadcastTheme()
    }

    private fun broadcastTheme() {
        val colors = currentNappletThemeColors(this)
        val payload = NappletProtocolJson.encodeThemeChanged(colors.background, colors.text, colors.primary)
        if (payload == lastTheme) return
        lastTheme = payload
        themeRecipients.entries.removeAll { (_, token) ->
            NappletLaunchRegistry.resolve(token)?.declared?.contains(NappletCapability.THEME) != true
        }
        themeRecipients.keys.toList().forEach { push(it, payload) }
    }

    // Binding is restricted to our own UID by exported=false in the manifest, enforced by the OS.
    // A UID check here would be useless: onBind() runs once (the binder is cached and reused for all
    // later clients) and outside any binder transaction, so getCallingUid() returns our own UID anyway.
    override fun onBind(intent: Intent?): IBinder? = incoming.binder

    override fun onDestroy() {
        liveSubscriptions.closeAll()
        incBus.removeAll { true }
        identityWatch.stopAll()
        NappletLaunchRegistry.onEvicted = null
        files.keys.toList().forEach(::dropFiles)
        themeRecipients.clear()
        // Every applet/browser surface has unbound, so the "session" the user granted for is over.
        // The ledger and the broker cache are now app-wide singletons that outlive this service, so
        // their in-memory session grants have to be dropped explicitly here — that keeps the lifetime
        // the consent dialog promises ("allow for this session") instead of letting it become
        // "allow until the app process dies".
        dropCachedBroker()
        Amethyst.instance.nappletPermissionLedger.endSession()
        // Drop any foreground holds this broker still owns so they don't leak past the service.
        synchronized(foregroundLeases) {
            repeat(foregroundLeases.size) { SandboxForegroundHold.release() }
            foregroundLeases.clear()
        }
        scope.cancel()
        super.onDestroy()
    }

    // Sandbox surfaces (full-screen :napplet hosts) currently reporting themselves foreground, mapped to
    // the last time each renewed its lease (monotonic elapsedRealtime). While this map is non-empty the
    // main process is held resumed (Tor/relays/AUTH up) via SandboxForegroundHold — the napplet host
    // lives in :napplet and can't touch that lifecycle itself, so it signals over IPC.
    //
    // The host re-sends its foreground report on a heartbeat while genuinely resumed. We key on the
    // launch token, so a repeated report just refreshes the timestamp (idempotent, no double-acquire).
    // If a host's process dies while foreground it can't send its onPause "false", so the heartbeats
    // simply stop and [foregroundLeaseWatchdog] reaps the stale lease — bounding any such leak to one
    // [FOREGROUND_LEASE_TTL_MS] window instead of holding the network up forever.
    private val foregroundLeases = HashMap<String, Long>()
    private var foregroundLeaseWatchdog: Job? = null

    private fun handleMessage(msg: Message): Boolean {
        // A sandbox surface is being destroyed: drop every reference we hold to its Messenger. Holding a
        // client's Messenger keeps a binder alive, which pins that surface's whole Activity (and its
        // WebView) in the `:napplet` process past onDestroy — reclaimable only by killing the process.
        if (msg.what == NappletIpc.MSG_RELEASE_CLIENT) {
            msg.replyTo?.let {
                incBus.removeAll { endpoint -> endpoint.replyTo == it }
                liveSubscriptions.closeAllFor(it)
                attendance.forget(it)
                themeRecipients.remove(it)
            }
            // Tokens the surface will never use again: drop their sessions and whatever runs under them.
            // Only the surface that minted a token holds it (tokens are unguessable), so it can only ever
            // give back its own.
            msg.data?.getStringArray(NappletIpc.KEY_RELEASED_TOKENS)?.forEach { token ->
                NappletLaunchRegistry.unregister(token)
                incBus.removeAll { endpoint -> endpoint.launchToken == token }
                intents.remove(token)
                identityWatch.stop(token)
                dropFiles(token)
                val prefix = "$token\u0000"
                resourceRequests.keys.filter { it.startsWith(prefix) }.forEach { key -> resourceRequests.remove(key)?.cancel() }
            }
            // Release its foreground lease too; otherwise a destroyed surface keeps the main process
            // pinned resumed until the lease watchdog expires it.
            msg.data?.getString(NappletIpc.KEY_LAUNCH_TOKEN)?.let { token ->
                synchronized(foregroundLeases) {
                    if (foregroundLeases.remove(token) != null) SandboxForegroundHold.release()
                }
            }
            return true
        }

        // A sandbox surface (full-screen :napplet host) entered, renewed, or left the foreground. Hold the
        // main process resumed while at least one is foreground, so opening it doesn't tear down Tor/relays.
        if (msg.what == NappletIpc.MSG_SET_ATTENDED) {
            val owner = msg.replyTo ?: return true
            val attended = msg.data?.getBoolean(NappletIpc.KEY_ATTENDED, false) ?: false
            attendance.set(owner, attended)
            val token = msg.data?.getString(NappletIpc.KEY_LAUNCH_TOKEN)
            if (token != null && NappletLaunchRegistry.resolve(token)?.declared?.contains(NappletCapability.THEME) == true) {
                themeRecipients[owner] = token
            } else {
                themeRecipients.remove(owner)
            }
            // Encrypted events its subscriptions received meanwhile can be decrypted and delivered now.
            if (attended) liveSubscriptions.onAttended(owner)
            return true
        }

        if (msg.what == NappletIpc.MSG_SET_FOREGROUND) {
            val data = msg.data ?: return true
            val token = data.getString(NappletIpc.KEY_LAUNCH_TOKEN) ?: return true
            val foreground = data.getBoolean(NappletIpc.KEY_FOREGROUND, false)
            synchronized(foregroundLeases) {
                if (foreground) {
                    val firstReport = !foregroundLeases.containsKey(token)
                    // A foreground lease just pins Tor/relays (no key access), but a misbehaving sandbox
                    // could still spam distinct keys to keep the network up. Bound the damage: refuse new
                    // lease keys past the cap. Real usage holds only a handful of foreground surfaces.
                    if (firstReport && foregroundLeases.size >= MAX_FOREGROUND_LEASES) {
                        Log.w("NappletBrokerService") { "Foreground lease cap reached; ignoring new lease $token" }
                        return true
                    }
                    foregroundLeases[token] = SystemClock.elapsedRealtime()
                    if (firstReport) {
                        SandboxForegroundHold.acquire()
                        ensureForegroundLeaseWatchdog()
                    }
                } else if (foregroundLeases.remove(token) != null) {
                    SandboxForegroundHold.release()
                }
            }
            return true
        }

        // The direct-WebView browser relays a successfully loaded page; record it in the visit history
        // (main process only). Only clean page-finishes reach here, so misspellings never get recorded.
        if (msg.what == NappletIpc.MSG_RECORD_HISTORY) {
            val data = msg.data ?: return true
            val url = data.getString(NappletIpc.KEY_HISTORY_URL)?.takeIf { it.isNotBlank() } ?: return true
            val history = Amethyst.instance.browserHistory
            history.init()
            history.record(
                url = url,
                title = data.getString(NappletIpc.KEY_HISTORY_TITLE).orEmpty(),
                replaces = data.getString(NappletIpc.KEY_HISTORY_REPLACES),
            )
            return true
        }

        // The direct-WebView browser relays a favicon captured from the loaded page; store it by host.
        if (msg.what == NappletIpc.MSG_RECORD_ICON) {
            val data = msg.data ?: return true
            val host = data.getString(NappletIpc.KEY_ICON_HOST)?.takeIf { it.isNotBlank() } ?: return true
            val bytes = data.getByteArray(NappletIpc.KEY_ICON_BYTES) ?: return true
            val icons = Amethyst.instance.browserIcons
            icons.init()
            icons.record(host, bytes)
            return true
        }

        // The direct-WebView browser pins/unpins the page it shows (main process only).
        if (msg.what == NappletIpc.MSG_TOGGLE_WEB_FAVORITE) {
            val data = msg.data ?: return true
            val url = data.getString(NappletIpc.KEY_FAVORITE_URL)?.takeIf { it.isNotBlank() } ?: return true
            val label = data.getString(NappletIpc.KEY_FAVORITE_LABEL).orEmpty().ifBlank { url }
            val favorites = Amethyst.instance.favoriteApps
            favorites.init()
            val id = "url:$url"
            // The star sends the state it wants (it flips what it shows); an older client sends none: toggle.
            val target =
                if (data.containsKey(NappletIpc.KEY_FAVORITE_IS_FAVORITE)) {
                    data.getBoolean(NappletIpc.KEY_FAVORITE_IS_FAVORITE)
                } else {
                    !favorites.isFavorite(id)
                }
            if (target) {
                favorites.add(FavoriteApp.WebApp(url, label, System.currentTimeMillis()))
            } else {
                favorites.remove(id)
            }
            msg.replyTo?.let { replyWebFavoriteState(it, url) }
            return true
        }

        // The direct-WebView browser asks whether the page it now shows is pinned, so its star is right.
        if (msg.what == NappletIpc.MSG_QUERY_WEB_FAVORITE) {
            val replyTo = msg.replyTo ?: return true
            val url = msg.data?.getString(NappletIpc.KEY_FAVORITE_URL)?.takeIf { it.isNotBlank() } ?: return true
            Amethyst.instance.favoriteApps.init()
            replyWebFavoriteState(replyTo, url)
            return true
        }

        // The browser asks what the user already answered for a site's camera/microphone/location.
        if (msg.what == NappletIpc.MSG_QUERY_SITE_PERMISSIONS) {
            val replyTo = msg.replyTo ?: return true
            val data = msg.data ?: return true
            val origin = data.getString(NappletIpc.KEY_BROWSER_ORIGIN)?.takeIf { it.isNotBlank() } ?: return true
            WebSitePermissionRegistry.init(applicationContext)
            val reply =
                Message.obtain(null, NappletIpc.MSG_SITE_PERMISSIONS).apply {
                    this.data =
                        Bundle().apply {
                            putLong(NappletIpc.KEY_REQUEST_ID, data.getLong(NappletIpc.KEY_REQUEST_ID))
                            putString(NappletIpc.KEY_BROWSER_ORIGIN, origin)
                            BrowserSitePermission.entries.forEach { permission ->
                                putString(NappletIpc.KEY_SITE_PERMISSION_PREFIX + permission.key, WebSitePermissionRegistry.decision(origin, permission).name)
                            }
                        }
                }
            runCatching { replyTo.send(reply) }
            return true
        }

        // The browser relays the user's answer to a site's permission prompt; remember it per origin.
        if (msg.what == NappletIpc.MSG_SET_SITE_PERMISSION) {
            val data = msg.data ?: return true
            val origin = data.getString(NappletIpc.KEY_BROWSER_ORIGIN)?.takeIf { it.isNotBlank() } ?: return true
            val permission = BrowserSitePermission.fromKey(data.getString(NappletIpc.KEY_SITE_PERMISSION)) ?: return true
            val decision = runCatching { BrowserSitePermission.Decision.valueOf(data.getString(NappletIpc.KEY_SITE_DECISION).orEmpty()) }.getOrNull() ?: return true
            WebSitePermissionRegistry.init(applicationContext)
            WebSitePermissionRegistry.set(origin, permission, decision)
            return true
        }

        // The full-screen browser's "Add to Home screen": pin a shortcut that reopens it in Amethyst.
        if (msg.what == NappletIpc.MSG_ADD_TO_HOME_SCREEN) {
            val data = msg.data ?: return true
            val url = data.getString(NappletIpc.KEY_FAVORITE_URL)?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return true
            Amethyst.instance.browserIcons.init()
            WebShortcuts.requestPin(applicationContext, url, data.getString(NappletIpc.KEY_FAVORITE_LABEL).orEmpty())
            return true
        }

        // The direct-WebView browser relays its per-host Tor choice; persist it (main process only).
        if (msg.what == NappletIpc.MSG_SET_WEB_TOR) {
            val data = msg.data ?: return true
            val host = data.getString(NappletIpc.KEY_WEB_HOST)?.takeIf { it.isNotBlank() } ?: return true
            WebAppNetworkRegistry.init(applicationContext)
            WebAppNetworkRegistry.set(host, data.getBoolean(NappletIpc.KEY_NETWORK_USE_TOR, true))
            return true
        }

        // The sandbox relays the user's per-site network choice; persist it against the trusted
        // coordinate the launch token resolves to (the sandbox can't state its own coordinate).
        if (msg.what == NappletIpc.MSG_SET_NETWORK_MODE) {
            val data = msg.data ?: return true
            val session = NappletLaunchRegistry.resolve(data.getString(NappletIpc.KEY_LAUNCH_TOKEN)) ?: return true
            NsiteNetworkRegistry.preload()
            NsiteNetworkRegistry.set(session.identity.coordinate, data.getBoolean(NappletIpc.KEY_NETWORK_USE_TOR, true))
            return true
        }

        // A running full-screen sandbox surface asks to open its editable permission screen. The sandbox
        // can't state its own coordinate, so a napplet/nsite sends its launch token (resolved here to the
        // trusted coordinate) and a browser sends its visited origin (keyed as `browser:<origin>`). Open
        // the main activity at that Connected Apps detail.
        if (msg.what == NappletIpc.MSG_OPEN_PERMISSIONS) {
            val data = msg.data ?: return true
            val coordinate =
                data.getString(NappletIpc.KEY_LAUNCH_TOKEN)?.let { NappletLaunchRegistry.resolve(it)?.identity?.coordinate }
                    ?: data.getString(NappletIpc.KEY_BROWSER_ORIGIN)?.takeIf { it.isNotBlank() }?.let { "browser:$it" }
                    ?: return true
            openConnectedAppDetail(coordinate)
            return true
        }

        // Browser mode mints a fresh launch token per visited origin, so NIP-07 consent is scoped to the
        // one site the request came from. The origin is the trusted source origin the WebView reported
        // (the sandbox can't forge it), and the synthetic identity keys the permission ledger per host.
        if (msg.what == NappletIpc.MSG_MINT_BROWSER_TOKEN) {
            val data = msg.data ?: return true
            val replyTo = msg.replyTo ?: return true
            val origin = data.getString(NappletIpc.KEY_BROWSER_ORIGIN)?.takeIf { it.isNotBlank() } ?: return true
            val identity = NappletIdentity(authorPubKey = BROWSER_IDENTITY_AUTHOR, identifier = origin)
            // Bind to the account active at mint time: a browser token minted for one account must
            // never sign as another if the user switches while the page is still open.
            val mintAccount = Amethyst.instance.sessionManager.loggedInAccount()
            // The surface names the storage jar it runs in: a page left open across an account switch (its
            // cookies, its session, belong to the previous account) must not be re-minted a token that acts
            // as the new one — its token is evicted or released, and it asks again from the old jar.
            val surfaceProfile = data.getString(NappletIpc.KEY_WEBVIEW_PROFILE)
            if (mintAccount == null || surfaceProfile != NappletWebViewProfiles.forPubKey(mintAccount.pubKey)) {
                // No one to act as: answer anyway (with no token), so the page's queued calls fail right away
                // instead of waiting forever for a token that will never come.
                val refusal =
                    Message.obtain(null, NappletIpc.MSG_BROWSER_TOKEN).apply {
                        this.data = Bundle().apply { putString(NappletIpc.KEY_BROWSER_ORIGIN, origin) }
                    }
                runCatching { replyTo.send(refusal) }
                return true
            }
            val token = NappletLaunchRegistry.register(identity, NappletCapability.WEBSITE_CAPABILITIES, mintAccount.pubKey, browserOrigin = true)
            val response =
                Message.obtain(null, NappletIpc.MSG_BROWSER_TOKEN).apply {
                    this.data =
                        Bundle().apply {
                            putString(NappletIpc.KEY_BROWSER_ORIGIN, origin)
                            putString(NappletIpc.KEY_LAUNCH_TOKEN, token)
                        }
                }
            runCatching { replyTo.send(response) }
            return true
        }

        if (msg.what != NappletIpc.MSG_REQUEST) return false

        val data = msg.data ?: return true
        val replyTo = msg.replyTo ?: return true
        val requestId = data.getString(NappletIpc.KEY_REQUEST_ID) ?: return true
        val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true

        val requestType = runCatching { NappletProtocolJson.readType(payload) }.getOrNull() ?: "napplet"
        // Stamped now, before anything suspends: orders this request against a later close of its subscription.
        val arrivedAt = liveSubscriptions.arrival()

        // Resolve the launch token to the trusted identity + declared set. The sandbox never states
        // its own coordinate, so a compromised :napplet process can only ever act as the napplet it
        // was launched as (it holds only its own token). An unknown token = no session; refuse.
        val launchToken = data.getString(NappletIpc.KEY_LAUNCH_TOKEN)
        val session = NappletLaunchRegistry.resolve(launchToken)
        if (session == null) {
            reply(replyTo, requestId, NappletProtocolJson.encodeResponse(requestType, NappletResponse.Failed("Unknown napplet session.")))
            // Tell the surface its token is gone, so a browser tab re-mints instead of failing every call.
            if (launchToken != null) {
                val unknown =
                    Message.obtain(null, NappletIpc.MSG_TOKEN_UNKNOWN).apply {
                        this.data = Bundle().apply { putString(NappletIpc.KEY_LAUNCH_TOKEN, launchToken) }
                    }
                runCatching { replyTo.send(unknown) }
            }
            return true
        }
        val identity = session.identity
        val declared = session.declared
        val resourceRequestKey = "$launchToken\u0000$requestId"
        if (requestType == "resource.cancel") {
            resourceRequests.remove(resourceRequestKey)?.cancel()
            return true
        }
        // NAP-INC is routed here, on the thread the message came in on, so a napplet's messages reach
        // the bus in the order it sent them (the request coroutines below run on a thread pool).
        // The bus only connects surfaces launched as the same account.
        if (launchToken != null && requestType.startsWith("inc.")) {
            when (val outcome = NappletRequestRouter.routeInc(declared, payload, requestType)) {
                null -> {}
                is NappletRequestRouter.Outcome.Inc -> {
                    incBus.handle(IncEndpoint(replyTo, launchToken), session.accountPubKey, { push(replyTo, it) }, outcome.request)?.let { reply(replyTo, requestId, it) }
                    return true
                }
                is NappletRequestRouter.Outcome.Reply -> {
                    reply(replyTo, requestId, outcome.payload)
                    return true
                }
                else -> return true
            }
        }
        // NAP-INTENT: an invoke may open the handler, which waits on the main thread, so it runs in a coroutine.
        if (launchToken != null && requestType.startsWith("intent.")) {
            when (val outcome = NappletRequestRouter.routeIntent(declared, payload, requestType)) {
                null -> {}
                is NappletRequestRouter.Outcome.Intent -> {
                    scope.launch { intents.handle(launchToken, identity, { push(replyTo, it) }, outcome.request)?.let { reply(replyTo, requestId, it) } }
                    return true
                }
                is NappletRequestRouter.Outcome.Reply -> {
                    reply(replyTo, requestId, outcome.payload)
                    return true
                }
                else -> return true
            }
        }
        val tracksResourceRequest = requestType == "resource.bytes" || requestType == "resource.bytesMany"

        val requestJob =
            scope.launch(start = if (tracksResourceRequest) CoroutineStart.LAZY else CoroutineStart.DEFAULT) {
                // The shared, host-agnostic router owns decode → broker → encode and the subscribe-vs-reply
                // decision (it stays wire-identical with the future desktop host). This service only supplies
                // the broker, the Messenger transport, and the live relay subscription each Outcome implies.
                // The launch token decides whose key signs — not the active account. A surface opened by
                // one account can never be handed another's signer, even while it stays open across a switch.
                val broker = brokerFor(session.accountPubKey)
                if (broker == null) {
                    reply(replyTo, requestId, NappletProtocolJson.encodeResponse(requestType, NappletResponse.Failed("That account is no longer signed in.")))
                    return@launch
                }
                // A query's results are decrypted with the user's key: while nobody is looking at the page it
                // waits (as its sign / decrypt requests do), and gives up like them.
                if (NappletActingRequests.readsForUser(requestType) && !attendance.awaitAttended(replyTo, NappletHeldRequests.MAX_AGE_MS)) {
                    reply(replyTo, requestId, NappletProtocolJson.encodeResponse(requestType, NappletResponse.Failed(NappletHeldRequests.EXPIRED)))
                    return@launch
                }
                when (val outcome = NappletRequestRouter.route(broker, identity, declared, payload, filesFor(launchToken, declared))) {
                    is NappletRequestRouter.Outcome.Ignore -> {}
                    is NappletRequestRouter.Outcome.Reply -> {
                        reply(replyTo, requestId, outcome.payload)
                        // NAP-IDENTITY has no watch/unwatch request. Once the consent-gated startup
                        // snapshot succeeds, the runtime owns identity.changed delivery for this
                        // trusted launch token until the broker service closes.
                        if (requestType == "identity.getPublicKey" && outcome.response is NappletResponse.PublicKey && launchToken != null) {
                            identityWatch.start(launchToken, session.accountPubKey) { push(replyTo, it) }
                        }
                    }
                    is NappletRequestRouter.Outcome.OpenSubscription ->
                        liveSubscriptions.open(replyTo, outcome.subId, outcome.filters, accountFor(session.accountPubKey), arrivedAt, outcome.relays, outcome.outbox, outcome.mayDecrypt) { push(replyTo, it) }
                    is NappletRequestRouter.Outcome.CloseSubscription -> liveSubscriptions.close(replyTo, outcome.subId, arrivedAt)
                    is NappletRequestRouter.Outcome.Push -> outcome.payloads.forEach { push(replyTo, it) }
                    // Routed above, before this coroutine started.
                    is NappletRequestRouter.Outcome.Inc, is NappletRequestRouter.Outcome.Intent -> {}
                }
            }
        if (tracksResourceRequest) {
            resourceRequests.put(resourceRequestKey, requestJob)?.cancel()
            requestJob.invokeOnCompletion { resourceRequests.remove(resourceRequestKey, requestJob) }
            requestJob.start()
        }
        return true
    }

    /**
     * Periodically reaps foreground leases that stopped renewing — the signature of a `:napplet` host
     * process that died (or was killed) while foreground, so it never sent its onPause "false". Each
     * reaped lease releases its [SandboxForegroundHold] so the network isn't held up forever by a host
     * that's already gone. Runs only while at least one lease exists; cancelled with [scope] on destroy.
     */
    private fun ensureForegroundLeaseWatchdog() {
        if (foregroundLeaseWatchdog != null) return
        foregroundLeaseWatchdog =
            scope.launch {
                while (true) {
                    delay(FOREGROUND_LEASE_CHECK_MS)
                    synchronized(foregroundLeases) {
                        val now = SystemClock.elapsedRealtime()
                        val iterator = foregroundLeases.entries.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (now - entry.value > FOREGROUND_LEASE_TTL_MS) {
                                Log.w("NappletBrokerService") { "Foreground lease ${entry.key} expired (host process gone?); releasing hold" }
                                iterator.remove()
                                SandboxForegroundHold.release()
                            }
                        }
                    }
                }
            }
    }

    /** The launched-as account, or null once it is no longer loaded. */
    private fun accountFor(accountPubKey: HexKey): Account? = Amethyst.instance.accountsCache.accounts.value[accountPubKey]

    /**
     * The broker for the account a surface was LAUNCHED as — [NappletLaunchRegistry.Session.accountPubKey],
     * never whichever account is active right now.
     *
     * Resolving live was wrong in a way that defeated per-account isolation: a full-screen host is a
     * separate activity that an account switch does not tear down, so its WebView kept account A's
     * cookies while requests were signed by B. The page displayed one identity while another signed,
     * and B's session was written into A's storage jar — after which even the embedded tab, which is
     * rebuilt correctly, showed the wrong account.
     *
     * Binding to the launch account satisfies both halves of the rule with no extra machinery:
     * embedded surfaces are torn down and re-minted on a switch, so they follow the active account,
     * while a full-screen surface stays on the account it was opened with.
     *
     * Returns null when that account is no longer loaded (logged out), so requests fail closed
     * rather than silently falling back to someone else's key.
     */
    private fun brokerFor(accountPubKey: HexKey): NappletBroker? {
        val account = accountFor(accountPubKey) ?: return null
        synchronized(brokerLock) {
            cachedBroker?.let { (acc, broker) -> if (acc === account) return broker }
            val broker =
                AndroidNappletGateways(
                    account = account,
                    context = applicationContext,
                    // The launch account's grants and storage, not whoever is logged in now.
                    ledger = ledger.forAccount(account.pubKey),
                    storage = storage.forAccount(account.pubKey),
                    // Per-applet Tor decision (see NappletResourceFetcher): the shared manager routes
                    // through Tor when asked + active, and falls back to clearnet otherwise.
                    httpClient = { useProxy -> Amethyst.instance.okHttpClients.getHttpClient(useProxy) },
                    signerLedger = signerLedger,
                ).broker()
            cachedBroker = account to broker
            return broker
        }
    }

    /**
     * Brings the main activity (a `singleInstance`) forward at the Connected Apps detail for [coordinate],
     * via the in-process `connectedapp?coordinate=` deep link that [com.vitorpamplona.amethyst.commons.model.navigation.uri.uriToRoute]
     * resolves. Used when a full-screen sandbox surface taps "Manage permissions".
     */
    private fun openConnectedAppDetail(coordinate: String) {
        val intent =
            Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                data = ("nostr:connectedapp?coordinate=" + Uri.encode(coordinate)).toUri()
            }
        runCatching { applicationContext.startActivity(intent) }
            .onFailure { Log.w("NappletBrokerService", "Could not open Connected Apps detail", it) }
    }

    private fun reply(
        replyTo: Messenger,
        requestId: String,
        payload: String,
    ) {
        val response =
            Message.obtain(null, NappletIpc.MSG_RESPONSE).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_REQUEST_ID, requestId)
                        putString(NappletIpc.KEY_PAYLOAD, payload)
                    }
            }
        try {
            replyTo.send(response)
        } catch (e: RemoteException) {
            Log.w("NappletBrokerService", "Applet host went away before reply could be delivered", e)
        }
    }

    /** Tells a browser surface whether [url] is currently pinned, so its star shows the registry's truth. */
    private fun replyWebFavoriteState(
        replyTo: Messenger,
        url: String,
    ) {
        val message =
            Message.obtain(null, NappletIpc.MSG_WEB_FAVORITE_STATE).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_FAVORITE_URL, url)
                        putBoolean(NappletIpc.KEY_FAVORITE_IS_FAVORITE, Amethyst.instance.favoriteApps.isFavorite("url:$url"))
                    }
            }
        try {
            replyTo.send(message)
        } catch (e: RemoteException) {
            Log.w("NappletBrokerService", "Browser went away before the favorite state could be delivered", e)
        }
    }

    /** Sends an unsolicited push (a `relay.event`/`relay.eose` envelope) for the host to forward verbatim. */
    private fun push(
        replyTo: Messenger,
        payload: String,
    ) {
        val message =
            Message.obtain(null, NappletIpc.MSG_PUSH).apply {
                data = Bundle().apply { putString(NappletIpc.KEY_PAYLOAD, payload) }
            }
        try {
            replyTo.send(message)
        } catch (e: RemoteException) {
            Log.w("NappletBrokerService", "Applet host went away before push could be delivered", e)
        }
    }

    companion object {
        /**
         * Guards [cachedBroker]. Both live on the companion rather than the service instance so the
         * Connected Apps UI can reach the running broker to revoke its live session grants — the
         * screens are plain composables with no binder to this service, and the broker is the only
         * holder of the in-memory "allow for this session" signer grants.
         *
         * Main-process only, like the sibling `Napplet*Registry` objects: the `:napplet` process gets
         * its own (unused, empty) copy of these statics and must never touch them.
         */
        private val brokerLock = Any()

        // The broker for the current account, rebuilt only on account switch (see brokerFor()).
        private var cachedBroker: Pair<Account, NappletBroker>? = null

        /**
         * Drops the live "allow for this session" signer grants the running broker holds for
         * [coordinate] (the bare app coordinate). Called when the user revokes or forgets an app in
         * Connected Apps: without it the persisted grants are cleared but the in-memory session ones
         * keep authorizing signatures until the broker dies, so a revoked app goes on signing.
         *
         * No-op when no broker has been built yet (no applet has run this process).
         */
        suspend fun revokeSessionGrants(coordinate: String) {
            val broker = synchronized(brokerLock) { cachedBroker?.second } ?: return
            broker.revokeSessionGrants(coordinate)
        }

        /** Forgets the cached broker, dropping every session grant it holds. */
        private fun dropCachedBroker() {
            synchronized(brokerLock) { cachedBroker = null }
        }

        /**
         * Sentinel "author" for a browser-mode per-origin identity. The real key is the visited origin,
         * carried in the identity's identifier (which the consent dialog shows); this constant only fills
         * the coordinate's author slot so each origin keys the permission ledger separately as
         * `browser:<origin>`. It is never treated as a real pubkey.
         */
        private const val BROWSER_IDENTITY_AUTHOR = "browser"

        /**
         * How long a foreground lease stays valid without a renewing heartbeat. A live host re-reports
         * every [NappletHostActivity.FOREGROUND_HEARTBEAT_MS][com.vitorpamplona.amethyst.napplethost.NappletHostActivity]
         * (well under this), so only a host that's actually gone lets its lease age past it. Sized to
         * tolerate a couple of missed/delayed heartbeats while still reaping a dead host's hold promptly.
         */
        private const val FOREGROUND_LEASE_TTL_MS = 90_000L

        /** How often [ensureForegroundLeaseWatchdog] sweeps for stale leases. */
        private const val FOREGROUND_LEASE_CHECK_MS = 20_000L

        /**
         * Cap on concurrent foreground leases. A foreground lease only keeps Tor/relays up (no key
         * access), but this bounds how long a misbehaving sandbox can pin the network by spamming
         * distinct lease keys. Comfortably above any realistic number of foreground sandbox surfaces.
         */
        private const val MAX_FOREGROUND_LEASES = 8
    }
}
