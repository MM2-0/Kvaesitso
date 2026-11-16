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

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.model.ThemeType
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcApp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nsite_open_failed
import com.vitorpamplona.amethyst.commons.resources.webxdc_open_failed
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.napplethost.NappletHostActivity
import com.vitorpamplona.amethyst.napplethost.NappletHostContract
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens a napplet or nSite in the sandboxed [NappletHostActivity] (the `:napplet` process). The
 * checks are the shared [NappletLaunch]'s; this adds what only the main process can: the launch
 * token, the Tor route, capability labels and the theme. Only the verified data the host needs to
 * render and broker for the app crosses into the sandbox process; no account state does.
 */
object NappletLauncher {
    /** Opens a NIP-5D napplet, refused unless [manifest] is signed by [authorPubKey] and named [identifier]. */
    fun launch(
        context: Context,
        manifest: NappletManifest,
        authorPubKey: HexKey,
        identifier: String,
    ) {
        buildLaunchParams(context, manifest, authorPubKey, identifier)?.let { openHost(context, it) }
    }

    /**
     * Opens a NIP-5A nSite ([com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent] or
     * `NamedSiteEvent`). A refused site says so, instead of the tap doing nothing.
     */
    fun launchSite(
        context: Context,
        event: Event,
    ) {
        val params = buildSiteLaunchParams(context, event)
        if (params != null) {
            openHost(context, params)
        } else {
            val app = context.applicationContext
            Amethyst.instance.applicationIOScope.launch {
                val message = loadStringRes(Res.string.nsite_open_failed)
                withContext(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    /**
     * Runs the webxdc [app] attached to post [postId] by [poster], as the logged-in user. A refused
     * launch (nobody logged in) says so.
     */
    fun launchWebxdc(
        context: Context,
        app: WebxdcApp,
        postId: HexKey,
        poster: HexKey,
    ) {
        val account = Amethyst.instance.sessionManager.loggedInAccount()
        if (account != null) {
            val selfName = account.userProfile().toBestDisplayName()
            openHost(context, buildLaunchParams(context, NappletLaunch.fromWebxdc(app, postId, poster, account.pubKey, selfName)))
        } else {
            val appContext = context.applicationContext
            Amethyst.instance.applicationIOScope.launch {
                val message = loadStringRes(Res.string.webxdc_open_failed)
                withContext(Dispatchers.Main) { Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun openHost(
        context: Context,
        params: Bundle,
    ) {
        val intent =
            Intent(context, NappletHostActivity::class.java).apply {
                putExtras(params)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }

    /**
     * The launch parameters for a NIP-5D napplet, as a [Bundle] keyed by the [NappletHostContract]
     * EXTRA_* names, or null when the manifest is refused. Used both for the activity intent and for
     * the embedded [com.vitorpamplona.amethyst.napplethost.NappletHostService] session (carried over
     * Messenger), so the two host paths launch from identical, main-process-minted parameters.
     */
    fun buildLaunchParams(
        context: Context,
        manifest: NappletManifest,
        authorPubKey: HexKey,
        identifier: String,
    ): Bundle? =
        NappletLaunch
            .fromManifest(manifest, authorPubKey, identifier)
            .onFailure { Log.w(TAG, "Refusing NIP-5D launch", it) }
            .getOrNull()
            ?.let { buildLaunchParams(context, it) }

    /**
     * The launch parameters for running [manifest] inside a note as instance [instanceId]
     * ([NappletLaunch.inline]): the broker never asks the user anything for it. Null when refused.
     */
    fun buildInlineLaunchParams(
        context: Context,
        manifest: NappletManifest,
        instanceId: String,
    ): Bundle? =
        NappletLaunch
            .fromManifest(manifest)
            .onFailure { Log.w(TAG, "Refusing inline NIP-5D launch", it) }
            .getOrNull()
            ?.let { buildLaunchParams(context, it.inline(instanceId)) }

    /**
     * The launch parameters for running the webxdc [app] posted in [postId] by [poster] inside its
     * note as instance [instanceId], or null when nobody is logged in.
     */
    fun buildInlineWebxdcParams(
        context: Context,
        app: WebxdcApp,
        postId: HexKey,
        poster: HexKey,
        instanceId: String,
    ): Bundle? {
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return null
        val launch = NappletLaunch.fromWebxdc(app, postId, poster, account.pubKey, account.userProfile().toBestDisplayName())
        return buildLaunchParams(context, launch.inline(instanceId))
    }

    /**
     * The launch parameters for showing the reader's front page [html] (already written, resolved and
     * checked in this process) as instance [instanceId], or null when nobody is logged in or the page
     * is too large to hand to the sandbox in one message.
     */
    fun buildInlinePaperParams(
        context: Context,
        html: String,
        title: String,
        instanceId: String,
    ): Bundle? {
        val account = Amethyst.instance.sessionManager.loggedInAccount() ?: return null
        val launch = NappletLaunch.forPaper(account.pubKey, title, html)
        // One binder transaction carries the whole launch; a page this size is not a front page.
        if ((launch.paper?.size ?: 0) > MAX_PAPER_BYTES) {
            Log.w(TAG) { "Refusing a ${launch.paper?.size}-byte front page" }
            return null
        }
        return buildLaunchParams(context, launch.inline(instanceId))
    }

    /** The launch parameters for a NIP-5A nSite, or null when the event is refused. */
    fun buildSiteLaunchParams(
        context: Context,
        event: Event,
    ): Bundle? =
        NappletLaunch
            .fromSite(event)
            .onFailure { Log.w(TAG, "Refusing nSite launch", it) }
            .getOrNull()
            ?.let { buildLaunchParams(context, it) }

    private fun buildLaunchParams(
        context: Context,
        launch: NappletLaunch,
    ): Bundle {
        val proxyPort = Amethyst.instance.torManager.activePortOrNull.value ?: -1

        // Mint the launch token in the (trusted) main process: the broker resolves the sandbox's
        // requests back to THIS identity + declared set, regardless of anything the sandbox sends.
        val identity = launch.identity
        val declared = launch.declared
        val profile = launch.profile
        // Bound to the account launching it, so the surface keeps signing as that account even if the
        // user switches while it is open (an embedded surface is rebuilt on a switch and re-mints).
        // An empty key can never match a loaded account, so a launch with nobody signed in fails
        // closed at the broker rather than falling back to whoever signs in later.
        val launchAccountPubKey =
            Amethyst.instance.sessionManager
                .loggedInAccount()
                ?.pubKey
                .orEmpty()
        val launchToken = NappletLaunchRegistry.register(identity, declared, launchAccountPubKey)

        // Resolve the per-site network choice. An nSite follows the user's choice for it, else the Tor
        // settings' "Applets and Websites" switch; a locked napplet's blob fetches follow that switch.
        NsiteNetworkRegistry.preload()
        // Whether Tor is ON, not whether its port is known yet: with Tor on and no port the host refuses to
        // fetch anything (fails closed) instead of going out directly while Tor is still starting.
        val torPrefs = Amethyst.instance.torPrefs
        val torEnabled = torPrefs.torType.value != TorType.OFF
        val useTor =
            torEnabled &&
                if (profile.exposesNetwork) NsiteNetworkRegistry.useTor(identity.coordinate) else torPrefs.value.appletsAndWebsitesViaTor.value

        // Resolve capability labels here (the app has the resources) so the sandbox module needs none.
        val capLabels = declared.map { stringRes(context, it.labelResId()) }

        val themeType = Amethyst.instance.uiPrefs.value.theme.value
        val theme =
            when (themeType) {
                ThemeType.DARK -> "DARK"
                ThemeType.LIGHT -> "LIGHT"
                ThemeType.SYSTEM -> {
                    val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (nightMask == Configuration.UI_MODE_NIGHT_YES) "DARK" else "LIGHT"
                }
            }

        return Bundle().apply {
            putStringArrayList(NappletHostContract.EXTRA_PATHS, ArrayList(launch.paths.map { it.path }))
            putStringArrayList(NappletHostContract.EXTRA_HASHES, ArrayList(launch.paths.map { it.hash }))
            putStringArrayList(NappletHostContract.EXTRA_SERVERS, ArrayList(launch.servers))
            putString(NappletHostContract.EXTRA_AUTHOR, identity.authorPubKey)
            putString(NappletHostContract.EXTRA_IDENTIFIER, identity.identifier)
            putString(NappletHostContract.EXTRA_AGGREGATE_HASH, identity.aggregateHash)
            putString(NappletHostContract.EXTRA_TITLE, launch.title)
            putStringArrayList(NappletHostContract.EXTRA_REQUIRES, ArrayList(launch.requires))
            putStringArrayList(NappletHostContract.EXTRA_CAP_LABELS, ArrayList(capLabels))
            putString(NappletHostContract.EXTRA_LAUNCH_TOKEN, launchToken)
            putInt(NappletHostContract.EXTRA_PROXY_PORT, proxyPort)
            putString(NappletHostContract.EXTRA_HOST_PROFILE, profile.name)
            putBoolean(NappletHostContract.EXTRA_USE_TOR, useTor)
            putString(NappletHostContract.EXTRA_THEME, theme)
            // Opaque per-account storage partition, so a napplet/nSite can't carry one npub's cookies
            // and localStorage into another. Derived here (the sandbox never sees the pubkey).
            putString(NappletHostContract.EXTRA_WEBVIEW_PROFILE, NappletWebViewProfiles.current())
            launch.webxdc?.let { NappletHostContract.putWebxdc(this, it) }
            launch.paper?.let { putByteArray(NappletHostContract.EXTRA_PAPER_HTML, it) }
        }
    }

    /**
     * The largest page handed to the sandbox. The whole launch is one binder transaction, whose
     * buffer (1 MB) is shared by everything the process has in flight: 600 KB left too little.
     */
    const val MAX_PAPER_BYTES = 400_000

    private const val TAG = "NappletLauncher"
}
