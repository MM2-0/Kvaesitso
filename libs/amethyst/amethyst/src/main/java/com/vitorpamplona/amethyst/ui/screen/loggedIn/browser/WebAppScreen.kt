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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.browser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.browser.OmniboxSuggestions
import com.vitorpamplona.amethyst.commons.browser.SearchEngines
import com.vitorpamplona.amethyst.commons.browser.ui.pill.AddressSuggestion
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPillEvent
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPillUi
import com.vitorpamplona.amethyst.commons.browser.ui.pill.DownloadPromptCard
import com.vitorpamplona.amethyst.commons.browser.ui.pill.PageDialogCard
import com.vitorpamplona.amethyst.commons.browser.ui.pill.PageInfoSheet
import com.vitorpamplona.amethyst.commons.browser.ui.pill.PermissionPromptCard
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.favoriteIds
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_unsupported
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher
import com.vitorpamplona.amethyst.favorites.WebShortcuts
import com.vitorpamplona.amethyst.napplet.WebAppNetworkRegistry
import com.vitorpamplona.amethyst.napplet.WebSitePermissionRegistry
import com.vitorpamplona.amethyst.napplethost.BrowserWebTools
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabChrome
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabFactory
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabHost
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * A **Web app** (a Nostr web client, reached by [url]) rendered as an **in-app tab** — for *any* URL,
 * favorited or not; favoriting is just the star toggle in the chrome. The embedded `:napplet` browser surface is drawn
 * by the persistent [EmbeddedTabHost]/[com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabLayer]
 * layer, which keeps the session warm across tab swaps. This screen owns only the chrome — it publishes
 * the controls as an [EmbeddedTabChrome] and the layer draws a top pull-down sheet over the (z-below)
 * surface. No title bar or editable address bar.
 *
 * Only bottom-row favorites stay warm; if this URL isn't a bottom-bar favorite, its session is evicted
 * (restarted) when the screen leaves. Requires API 30+ for the cross-process surface.
 */
@Composable
fun WebAppScreen(
    url: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        EmbeddedWebAppTab(url, accountViewModel, nav)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringRes(Res.string.browser_unsupported),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun EmbeddedWebAppTab(
    url: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    // Matches FavoriteApp.WebApp.id, so warm-keep membership lines up with the bottom-bar favorites.
    val id = "url:$url"

    var showPageInfo by remember { mutableStateOf(false) }

    // Tor is ON (its port may still be coming up: a Tor page then waits, it never falls back to the open web).
    val proxyAvailable = remember { Amethyst.instance.torPrefs.torType.value != TorType.OFF }

    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

    // Keyed on the theme epoch too: when the app theme flips, the warm session is torn down and this
    // re-acquires a freshly-themed one (the embed WebView's theme is fixed at construction).
    val controller =
        remember(id, EmbeddedTabHost.rebuildEpoch) {
            EmbeddedTabFactory.acquireWebApp(context, url, backgroundColor)
        }

    // Seeded from the controller, which outlives this screen: switching bottom-bar tabs disposes the screen
    // but keeps the page, so coming back must show where the page is — not the start URL with no history.
    var currentUrl by remember(controller) { mutableStateOf(controller.lastUrl ?: url) }
    // The page's own <title>; null until the current document reports one (the sheet shows the host).
    var pageTitle by remember(controller) { mutableStateOf(controller.lastTitle) }
    var canGoBack by remember(controller) { mutableStateOf(controller.lastCanGoBack) }
    var canGoForward by remember(controller) { mutableStateOf(controller.lastCanGoForward) }
    var desktopSite by remember(controller) { mutableStateOf(controller.isDesktopSite) }
    var textZoom by remember(controller) { mutableIntStateOf(controller.currentTextZoom) }
    // The controller starts from this site's remembered Tor choice (some sites' servers reject Tor exits, so
    // the user can opt one out and it must stick). Only meaningful when Tor is actually available.
    var torOn by remember(controller) { mutableStateOf(proxyAvailable && controller.isTorOn) }

    val apps by Amethyst.instance.favoriteApps.favorites
        .collectAsStateWithLifecycle()
    val isFavorite = remember(apps, currentUrl) { apps.any { it is FavoriteApp.WebApp && it.url == currentUrl } }
    val isLoading by controller.isLoading

    // Keep the URL/back callback fresh (cheap, needs the latest closure).
    SideEffect {
        controller.onUrlChanged = { newUrl, title, back, forward ->
            if (newUrl != "about:blank") {
                currentUrl = newUrl
                pageTitle = title
            }
            canGoBack = back
            canGoForward = forward
        }
    }

    fun toggleFavorite() {
        val favId = "url:$currentUrl"
        val favorites = Amethyst.instance.favoriteApps
        if (favorites.isFavorite(favId)) {
            favorites.remove(favId)
        } else {
            favorites.add(FavoriteApp.WebApp(currentUrl, pageTitle ?: hostLabel(currentUrl), System.currentTimeMillis()))
        }
    }

    // NIP-07 grants for a plain web client are keyed per visited origin as `browser:<origin>` (see
    // NappletBrokerService.BROWSER_IDENTITY_AUTHOR); site settings jump straight to that detail screen.
    fun openSiteSettings() {
        browserOrigin(currentUrl)?.let { origin -> nav.nav(Route.ConnectedAppDetail("browser:$origin")) }
    }

    fun onAction(action: BrowserChrome.Action) {
        when (action) {
            BrowserChrome.Action.BACK -> controller.back()
            BrowserChrome.Action.FORWARD -> controller.forward()
            BrowserChrome.Action.RELOAD -> controller.reload()
            BrowserChrome.Action.STOP -> controller.stop()
            BrowserChrome.Action.FAVORITE -> toggleFavorite()
            BrowserChrome.Action.SHARE -> BrowserWebTools.share(context, pageTitle, null, currentUrl)
            BrowserChrome.Action.BACK_TO_APP -> controller.backToScope(url)
            BrowserChrome.Action.COPY_LINK -> BrowserWebTools.copyToClipboard(context, currentUrl)
            BrowserChrome.Action.DESKTOP_SITE -> {
                desktopSite = !desktopSite
                controller.setDesktopSite(desktopSite)
            }
            BrowserChrome.Action.ADD_TO_HOME_SCREEN -> WebShortcuts.requestPin(context, currentUrl, pageTitle ?: hostLabel(currentUrl))
            BrowserChrome.Action.OPEN_IN_BROWSER_APP -> BrowserWebTools.openInOtherBrowser(context, currentUrl)
            // The page the user is looking at, not the one the tab was pinned with.
            BrowserChrome.Action.OPEN_FULL_SCREEN -> FavoriteAppLauncher.launchUrl(context, currentUrl)
            BrowserChrome.Action.TOR -> {
                torOn = !torOn
                controller.setTor(torOn)
                WebAppNetworkRegistry.set(currentUrl, torOn)
            }
            BrowserChrome.Action.SITE_SETTINGS -> openSiteSettings()
            else -> Unit
        }
    }

    fun onNavigate(text: String) {
        val engine = SearchEngines.byId(Amethyst.instance.uiPrefs.value.searchEngine.value)
        val resolved = OmniboxInput.resolve(text, engine.queryPrefix) ?: return
        // .onion only resolves over Tor.
        if (resolved.forceTor && proxyAvailable && !torOn) {
            torOn = true
            controller.setTor(true)
        }
        controller.navigate(resolved.url)
    }

    // Favorites first, then history: what the address editor offers for what the user has typed.
    val history by Amethyst.instance.browserHistory.history
        .collectAsStateWithLifecycle()
    val candidates =
        remember(apps, history) {
            buildList {
                apps.forEach { if (it is FavoriteApp.WebApp) add(OmniboxSuggestions.Candidate(it.url, it.label, isFavorite = true)) }
                history.forEach {
                    add(OmniboxSuggestions.Candidate(it.url, it.title.ifBlank { it.host }, isFavorite = false, visitCount = it.visitCount, lastVisitedAt = it.lastVisitedAt))
                }
            }
        }

    val siteDecisions by WebSitePermissionRegistry.decisions.collectAsStateWithLifecycle()
    val sitePermissions = remember(siteDecisions, currentUrl) { browserOrigin(currentUrl)?.let { siteDecisions[it] }.orEmpty() }

    // Off for this site, yet on Tor because another open page needs it (Tor always wins in `:napplet`).
    val torForced = controller.isTorForced

    // Rebuilt only when a displayed value changes, so the tab layer isn't recomposed every frame.
    val chrome =
        remember(currentUrl, pageTitle, canGoBack, canGoForward, isLoading, torOn, torForced, proxyAvailable, isFavorite, desktopSite, textZoom, sitePermissions, candidates, controller) {
            EmbeddedTabChrome(
                ui =
                    BrowserPillUi(
                        title = pageTitle ?: hostLabel(currentUrl),
                        chrome =
                            BrowserChrome.State(
                                surface = BrowserChrome.Surface.WEB,
                                presentation = BrowserChrome.Presentation.EMBEDDED,
                                url = currentUrl,
                                startUrl = url,
                                canGoBack = canGoBack,
                                canGoForward = canGoForward,
                                isLoading = isLoading,
                                torOn = if (proxyAvailable) torOn else null,
                                torForced = torForced,
                                hasSiteSettings = browserOrigin(currentUrl) != null,
                            ),
                        isFavorite = isFavorite,
                        desktopSite = desktopSite,
                        textZoom = textZoom,
                        sitePermissions = sitePermissions,
                    ),
                onEvent = { event ->
                    when (event) {
                        is BrowserPillEvent.Action -> onAction(event.action)
                        is BrowserPillEvent.Navigate -> onNavigate(event.input)
                        is BrowserPillEvent.TextZoom -> {
                            textZoom = event.percent
                            controller.setTextZoom(event.percent)
                        }
                        BrowserPillEvent.CopyOrigin -> BrowserWebTools.copyToClipboard(context, currentUrl)
                        BrowserPillEvent.PageInfo -> {
                            controller.requestPageInfo()
                            showPageInfo = true
                        }
                        BrowserPillEvent.Close -> Unit
                    }
                },
                suggestionsFor = { typed ->
                    OmniboxSuggestions.rank(typed, candidates, limit = 5).map { AddressSuggestion(it.label, it.url, it.isFavorite) }
                },
            )
        }
    // Publish the top-sheet controls to the tab layer (which draws them over the z-below surface). In a
    // SideEffect so it runs after [setActive]; the host short-circuits the identical remembered instance.
    SideEffect { EmbeddedTabHost.setActiveChrome(id, chrome) }

    val bottomBarFlow = accountViewModel.account.settings.syncedSettings.navigation.bottomBarItems
    val entryLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(id) {
        val token = EmbeddedTabHost.setActive(id)
        EmbeddedTabHost.hold(id)
        onDispose {
            EmbeddedTabHost.clearActiveIfOwner(token)
            EmbeddedTabHost.clearActiveChrome(id)
            // Only bottom-row apps stay warm; anything else restarts once the user actually leaves it — not
            // when a screen is merely pushed on top, and not when a re-navigation to this same tab already
            // composed a new screen on the same session.
            if (EmbeddedTabHost.release(id)) {
                EmbeddedTabHost.releaseWhenGone(id, entryLifecycle) { id in bottomBarFlow.value.favoriteIds() }
            }
        }
    }

    val isFullscreen by controller.isFullscreen
    PlatformBackHandler(enabled = canGoBack && !isFullscreen) { controller.back() }
    // A fullscreen video inside the tab: back leaves fullscreen first, as in Chrome.
    PlatformBackHandler(enabled = isFullscreen) { controller.exitFullscreen() }

    EmbeddedPageUi(controller, chrome.ui, showPageInfo, onPageInfoDismiss = { showPageInfo = false })

    Scaffold(
        bottomBar = {
            AppBottomBar(Route.WebApp(url), nav, accountViewModel) { route -> nav.navBottomBar(route) }
        },
    ) { padding ->
        // Reserve the full content area; the warm surface, its top sheet, and the loading/error overlay
        // are all drawn over these bounds by the tab layer.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { EmbeddedTabHost.reportBounds(it.boundsInWindow()) },
        )
    }
}

/**
 * Everything an embedded page asks the user for, drawn by the main process because the provider has no
 * window: JS dialogs, camera / microphone / location prompts (remembered per origin in
 * [WebSitePermissionRegistry], then Android's own runtime permission), inline-download consent, and
 * page info — the shared [PageDialogCard], [PermissionPromptCard], [DownloadPromptCard] and
 * [PageInfoSheet].
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun EmbeddedPageUi(
    controller: EmbeddedWebAppController,
    ui: BrowserPillUi,
    showPageInfo: Boolean,
    onPageInfoDismiss: () -> Unit,
) {
    val context = LocalContext.current

    val dialog by controller.pendingDialog
    dialog?.let { d ->
        Dialog(onDismissRequest = { controller.answerDialog(d.id, confirmed = false) }) {
            PageDialogCard(
                type = d.type,
                host = d.url?.let(::browserOrigin)?.let(::hostLabel),
                security = ui.security,
                message = d.message,
                defaultValue = d.defaultValue,
                offerBlock = d.offerBlock,
                onResult = { confirmed, text, block -> controller.answerDialog(d.id, confirmed, text, block) },
            )
        }
    }

    // Android's runtime permission, asked only for what the user allowed the site to use.
    var runtimeRequest by remember { mutableStateOf<Pair<Long, Set<BrowserSitePermission>>?>(null) }
    val runtimeLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val (requestId, allowed) = runtimeRequest ?: return@rememberLauncherForActivityResult
            runtimeRequest = null
            val granted = allowed.filter { ContextCompat.checkSelfPermission(context, androidPermissionFor(it)) == PackageManager.PERMISSION_GRANTED }.toSet()
            if (granted.size < allowed.size) Toast.makeText(context, CommonsR.string.browser_permission_system_denied, Toast.LENGTH_LONG).show()
            controller.answerPermission(requestId, granted)
        }

    fun grant(
        requestId: Long,
        allowed: Set<BrowserSitePermission>,
    ) {
        val missing = allowed.map(::androidPermissionFor).filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty() || runtimeRequest != null) {
            controller.answerPermission(requestId, if (missing.isEmpty()) allowed else emptySet())
        } else {
            runtimeRequest = requestId to allowed
            runtimeLauncher.launch(missing.toTypedArray())
        }
    }

    val permissionRequest by controller.pendingPermission
    permissionRequest?.let { request ->
        val decisions = remember(request.id) { request.permissions.associateWith { WebSitePermissionRegistry.decision(request.origin, it) } }
        val allowed = decisions.filterValues { it == BrowserSitePermission.Decision.ALLOW }.keys
        val ask = decisions.filterValues { it == BrowserSitePermission.Decision.ASK }.keys
        if (ask.isEmpty()) {
            LaunchedEffect(request.id) { grant(request.id, allowed) }
        } else {
            // allow: grant now; remember: store the answer for the site ("Only this time" and a dismissal don't).
            fun answer(
                allow: Boolean,
                remember: Boolean,
            ) {
                if (remember) {
                    val decision = if (allow) BrowserSitePermission.Decision.ALLOW else BrowserSitePermission.Decision.BLOCK
                    ask.forEach { WebSitePermissionRegistry.set(request.origin, it, decision) }
                }
                grant(request.id, if (allow) allowed + ask else allowed)
            }
            Dialog(onDismissRequest = { answer(allow = false, remember = false) }) {
                PermissionPromptCard(
                    host = hostLabel(request.origin),
                    security = ui.security,
                    permissions = ask,
                    onAllow = { answer(allow = true, remember = true) },
                    onAllowOnce = { answer(allow = true, remember = false) },
                    onDeny = { answer(allow = false, remember = true) },
                )
            }
        }
    }

    // A download the page started: nothing is fetched or saved until this is answered. The card shows the
    // file name that would be saved, its size and source host, headed by the WebView-reported origin (never
    // a page-supplied field), with a warning for names that can be installed or run ("invoice.apk").
    val downloadRequest by controller.pendingDownload
    downloadRequest?.let { request ->
        Dialog(onDismissRequest = { controller.answerDownload(request.id, allowed = false) }) {
            DownloadPromptCard(
                host = hostLabel(request.origin),
                security = ui.security,
                fileName = request.fileName,
                sizeBytes = request.sizeBytes,
                sourceHost = request.sourceHost,
                risky = request.risky,
                onAllow = { controller.answerDownload(request.id, allowed = true) },
                onDeny = { controller.answerDownload(request.id, allowed = false) },
            )
        }
    }

    if (showPageInfo) {
        val certificate by controller.pageCertificate
        val origin = browserOrigin(ui.chrome.url)
        Dialog(onDismissRequest = onPageInfoDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                PageInfoSheet(
                    ui = ui,
                    certificate = certificate,
                    onPermissionChange = { permission, decision -> origin?.let { WebSitePermissionRegistry.set(it, permission, decision) } },
                    onClearSiteData = {
                        onPageInfoDismiss()
                        controller.clearSiteData()
                    },
                    onClearAllSiteData = {
                        onPageInfoDismiss()
                        controller.clearAllSiteData()
                    },
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
        }
    }
}

private fun androidPermissionFor(permission: BrowserSitePermission): String =
    when (permission) {
        BrowserSitePermission.CAMERA -> Manifest.permission.CAMERA
        BrowserSitePermission.MICROPHONE -> Manifest.permission.RECORD_AUDIO
        BrowserSitePermission.LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
    }

/** The host of [url] for the tab title, falling back to the raw string. */
internal fun hostLabel(url: String): String = runCatching { url.toUri().host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

/**
 * The `scheme://host[:port]` origin of [url] — the exact form the sandbox reports for NIP-07 consent, so
 * it matches the `browser:<origin>` permission-ledger key. Null when [url] has no usable scheme/host.
 */
internal fun browserOrigin(url: String): String? {
    val uri = runCatching { url.toUri() }.getOrNull() ?: return null
    val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return null
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    return "$scheme://$host" + if (uri.port > 0) ":${uri.port}" else ""
}
