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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.browser.BrowserHistoryEntry
import com.vitorpamplona.amethyst.commons.browser.DefaultWebClients
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.browser.OmniboxSuggestions
import com.vitorpamplona.amethyst.commons.browser.SearchEngines
import com.vitorpamplona.amethyst.commons.browser.SuggestedWebApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.favorites.PreloadFavoriteNostrApps
import com.vitorpamplona.amethyst.commons.favorites.favoriteCoordinateOf
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.icons.symbols.rememberMaterialSymbolPainter
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.napplet.isLaunchable
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_address_hint
import com.vitorpamplona.amethyst.commons.resources.browser_clear
import com.vitorpamplona.amethyst.commons.resources.browser_discover_napplets
import com.vitorpamplona.amethyst.commons.resources.browser_discover_nsites
import com.vitorpamplona.amethyst.commons.resources.browser_favorites
import com.vitorpamplona.amethyst.commons.resources.browser_go
import com.vitorpamplona.amethyst.commons.resources.browser_napplet_default_label
import com.vitorpamplona.amethyst.commons.resources.browser_nsite_default_label
import com.vitorpamplona.amethyst.commons.resources.browser_omnibox_go_to
import com.vitorpamplona.amethyst.commons.resources.browser_omnibox_search_for
import com.vitorpamplona.amethyst.commons.resources.browser_pill_fill_in
import com.vitorpamplona.amethyst.commons.resources.browser_pill_paste_go
import com.vitorpamplona.amethyst.commons.resources.browser_recent_options
import com.vitorpamplona.amethyst.commons.resources.browser_recent_remove
import com.vitorpamplona.amethyst.commons.resources.browser_suggested
import com.vitorpamplona.amethyst.commons.resources.favorite_app_add
import com.vitorpamplona.amethyst.commons.resources.favorite_app_recent
import com.vitorpamplona.amethyst.commons.resources.favorite_app_remove
import com.vitorpamplona.amethyst.commons.resources.favorite_app_still_loading
import com.vitorpamplona.amethyst.commons.ui.components.util.getText
import com.vitorpamplona.amethyst.commons.ui.components.util.hasText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.platform.rememberAppLauncher
import com.vitorpamplona.amethyst.commons.ui.platform.rememberNappletIconModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.favorites.favoriteAppItems
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.napplets.datasource.NappletsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nsites.datasource.NsitesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource

/** How many of the most recent history entries the idle browser home surfaces under "Recent". */
private const val RECENTS_LIMIT = 12

/**
 * The Browser tab — a **launcher**, not a content surface. The user types a URL here and each opened
 * site lands in its own full-screen
 * [NappletBrowserActivity][com.vitorpamplona.amethyst.napplethost.NappletBrowserActivity] (its own
 * task/recents entry), so apps are swapped the normal Android way and a running app never carries an
 * editable address bar.
 *
 * Idle, the body is the [BrowserHome]: pinned favorites on top, then recent visits. As the user types it
 * becomes a grouped omnibox suggestion list (favorites first + highlighted, then recents) with inline
 * ghost-text completion. Both decorate sites with the favicon captured when they were last opened.
 *
 * Works on any API level: each site loads in a full-screen direct-WebView activity, never the
 * cross-process SurfaceControlViewHost surface that the *embedded* favorite-app tabs require (API 30+).
 */
@Composable
fun BrowserScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    BrowserLauncher(accountViewModel, nav)
}

@Composable
private fun BrowserLauncher(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val appServices = LocalAppServices.current
    val appLauncher = rememberAppLauncher()
    val appStillLoadingStr = stringRes(Res.string.favorite_app_still_loading)
    val apps by appServices.favoriteApps.favorites
        .collectAsStateWithLifecycle()
    val history by appServices.browserHistory.history
        .collectAsStateWithLifecycle()
    val iconKeys by appServices.browserIcons.keys
        .collectAsStateWithLifecycle()

    // Fetch favorited nsite/napplet manifests up front so tapping one launches immediately instead of
    // showing "isn't loaded yet" until the user happens to visit the nsite/napplet feed.
    PreloadFavoriteNostrApps(apps, accountViewModel)

    val searchEngineId by accountViewModel.settings.uiSettingsFlow.searchEngine
        .collectAsStateWithLifecycle()
    val searchEngine = SearchEngines.byId(searchEngineId)

    var field by remember { mutableStateOf(TextFieldValue("")) }

    // The selection an inline completion produced. While the field's selection is still exactly this, the
    // highlighted suffix is ghost text the user didn't type; any other selection (select-all, a shift-select)
    // is the user's own.
    var ghost by remember { mutableStateOf<TextRange?>(null) }
    val ghostShowing = ghost != null && field.selection == ghost

    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // Checked from the clipboard's metadata whenever the field gains focus or the window comes back (the
    // user may have left to copy a link), so "Paste and go" is only offered when there is something to
    // paste; the contents are read only when the user taps it.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var clipboardHasText by remember { mutableStateOf(false) }
    LaunchedEffect(focused, windowFocused) { clipboardHasText = focused && windowFocused && clipboard.hasText() }

    // Favorites + visit history + the hardcoded Discover apps, flattened into the neutral candidate shape
    // the ranker consumes — so typing the omnibox finds a suggested app even before its first visit. The
    // ranker dedupes by host, and favorites/history outscore a plain default, so a default that the user
    // already pinned or visited collapses into that higher-ranked row instead of showing twice.
    val candidates =
        remember(apps, history) {
            buildList {
                apps.forEach { if (it is FavoriteApp.WebApp) add(OmniboxSuggestions.Candidate(it.url, it.label, isFavorite = true)) }
                history.forEach {
                    add(
                        OmniboxSuggestions.Candidate(
                            url = it.url,
                            label = it.title.ifBlank { it.host },
                            isFavorite = false,
                            visitCount = it.visitCount,
                            lastVisitedAt = it.lastVisitedAt,
                        ),
                    )
                }
                DefaultWebClients.list.forEach { add(OmniboxSuggestions.Candidate(it.app.url, it.app.label, isFavorite = false)) }
            }
        }

    // Visited URLs, so the suggestion list can tell a Recent result from a Discover one (and only the
    // former offers "Remove from history").
    val historyUrls = remember(history) { history.mapTo(HashSet()) { it.url } }

    // Discover nsites & napplets — the NIP-5A sites and NIP-5D apps published by the people the user
    // follows. Same feed + follow-list filter the dedicated nSites/nApplets screens use (set those to
    // "All Follows" for a pure follows list): the subscriptions pull manifests into LocalCache while the
    // Browser tab is open, and we observe the addressable store and keep the matching authors'.
    NsitesFilterAssemblerSubscription(accountViewModel)
    NappletsFilterAssemblerSubscription(accountViewModel)

    val nsiteNotes by remember {
        LocalCache.observeNotes(Filter(kinds = listOf(RootSiteEvent.KIND, NamedSiteEvent.KIND)))
    }.collectAsStateWithLifecycle(emptyList())
    val nappletNotes by remember {
        LocalCache.observeNotes(Filter(kinds = listOf(RootNappletEvent.KIND, NamedNappletEvent.KIND)))
    }.collectAsStateWithLifecycle(emptyList())

    val nsiteFollows by accountViewModel.account.liveNsitesFollowLists.collectAsStateWithLifecycle()
    val nappletFollows by accountViewModel.account.liveNappletsFollowLists.collectAsStateWithLifecycle()

    // Drop ones already pinned — they show under Favorites, not twice.
    val favoriteCoordinates = remember(apps) { apps.filterIsInstance<FavoriteApp.NostrApp>().mapTo(HashSet()) { it.coordinate } }
    // Built off the main thread: these walk every cached nsite/napplet note, and the note lists re-emit as
    // relays deliver.
    val followedNsites by produceState(emptyList<DiscoverNostrApp>(), nsiteNotes, nsiteFollows, favoriteCoordinates) {
        value = withContext(Dispatchers.IO) { nsiteNotes.toDiscoverApps(nsiteFollows::matchAuthor, favoriteCoordinates) }
    }
    val followedNapplets by produceState(emptyList<DiscoverNostrApp>(), nappletNotes, nappletFollows, favoriteCoordinates) {
        value = withContext(Dispatchers.IO) { nappletNotes.toDiscoverApps(nappletFollows::matchAuthor, favoriteCoordinates) }
    }

    // What the user actually typed, excluding the ghost-completion suffix while one is showing.
    val typed = ghost?.takeIf { ghostShowing }?.let { field.text.take(it.start) } ?: field.text
    // One ranking per typed text: an appended character is ranked in onValueChange (for the inline
    // completion) and then again for this list on the recomposition that follows — keep the last one.
    val lastRanking = remember(candidates) { arrayOfNulls<Pair<String, List<OmniboxSuggestions.Suggestion>>>(1) }

    fun ranked(text: String): List<OmniboxSuggestions.Suggestion> =
        lastRanking[0]?.takeIf { it.first == text }?.second
            ?: OmniboxSuggestions.rank(text, candidates, limit = 12).also { lastRanking[0] = text to it }

    val suggestions = remember(typed, candidates) { ranked(typed) }

    fun open(text: String) {
        val target = OmniboxInput.resolve(text, searchEngine.queryPrefix) ?: return
        appLauncher.launchUrl(target.url, target.forceTor, nav)
        // The site opens in its own window: coming back should land on a fresh launcher, not on the
        // half-typed address and its suggestions.
        field = TextFieldValue("")
        ghost = null
        focusManager.clearFocus()
    }

    // Pin/unpin a plain web URL by its favorite id. Shared by the suggestion list and the Recent rows.
    fun toggleFavorite(
        url: String,
        label: String,
    ) {
        val id = "url:$url"
        if (appServices.favoriteApps.isFavorite(id)) {
            appServices.favoriteApps.remove(id)
        } else {
            appServices.favoriteApps.add(FavoriteApp.WebApp(url, label.ifBlank { OmniboxInput.hostOf(url) ?: url }, TimeUtils.nowMillis()))
        }
    }

    // Inline autocomplete: when the user appends a character, offer the top host as selected ghost text so
    // the next keystroke replaces it. On deletion or mid-string edits, leave the value untouched.
    fun onValueChange(new: TextFieldValue) {
        // What is left once the selection (a ghost suffix, or anything the user selected) is typed over.
        val kept = field.text.removeRange(field.selection.min, field.selection.max)
        val newText = new.text
        val appended =
            new.selection.collapsed &&
                new.selection.start == newText.length &&
                newText.length > kept.length &&
                newText.startsWith(kept)
        if (appended) {
            // The completion has always looked at the top 8 (rank's default limit).
            val completion = OmniboxSuggestions.completion(newText, ranked(newText).take(8))
            if (completion != null) {
                // Keep the user's own casing for the typed prefix; append only the remaining suffix.
                val full = newText + completion.substring(newText.length)
                val suffix = TextRange(newText.length, full.length)
                field = TextFieldValue(full, suffix)
                ghost = suffix
                return
            }
        }
        if (new.text != field.text) ghost = null
        field = new
    }

    Scaffold(
        topBar = {
            OmniBar(
                nav = nav,
                field = field,
                focused = focused,
                onFocusChange = { focused = it },
                onValueChange = ::onValueChange,
                hasCompletion = ghostShowing,
                onAcceptCompletion = {
                    field = field.copy(selection = TextRange(field.text.length))
                    ghost = null
                },
                onClear = {
                    field = TextFieldValue("")
                    ghost = null
                },
                onOpen = { open(field.text) },
                onPasteAndGo =
                    if (focused && field.text.isEmpty() && clipboardHasText) {
                        { scope.launch { clipboard.getText()?.let(::open) } }
                    } else {
                        null
                    },
            )
        },
        bottomBar = {
            AppBottomBar(Route.Browser, nav, accountViewModel) { route -> nav.navBottomBar(route) }
        },
    ) { padding ->
        val contentModifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
        when {
            typed.isNotBlank() ->
                SuggestionGrid(
                    entered = field.text,
                    searchEngine = searchEngine,
                    suggestions = suggestions,
                    iconKeys = iconKeys,
                    historyUrls = historyUrls,
                    onOpenEntered = { open(field.text) },
                    onOpen = { open(it.url) },
                    onFillIn = { field = TextFieldValue(it, TextRange(it.length)) },
                    onToggleFavorite = { toggleFavorite(it.url, it.label) },
                    onRemoveFromHistory = { appServices.browserHistory.remove(it) },
                    modifier = contentModifier,
                )
            else -> {
                val favoriteUrls = remember(apps) { apps.filterIsInstance<FavoriteApp.WebApp>().mapTo(HashSet()) { it.url } }
                // Hardcoded starter web apps, minus any the user already pinned (those show under Favorites).
                val suggested = remember(favoriteUrls) { DefaultWebClients.list.filter { it.app.url !in favoriteUrls } }
                BrowserHome(
                    apps = apps,
                    history = history,
                    iconKeys = iconKeys,
                    favoriteUrls = favoriteUrls,
                    suggested = suggested,
                    nsites = followedNsites,
                    napplets = followedNapplets,
                    onOpenApp = { appLauncher.launch(it, appStillLoadingStr, nav) },
                    onRemoveApp = { appServices.favoriteApps.remove(it.id) },
                    onAddApp = { appServices.favoriteApps.add(it) },
                    onOpenUrl = { open(it) },
                    onToggleRecentFavorite = { entry -> toggleFavorite(entry.url, entry.title.ifBlank { entry.host }) },
                    onRemoveRecent = { appServices.browserHistory.remove(it) },
                    modifier = contentModifier,
                )
            }
        }
    }
}

/**
 * The launcher's address field: a rounded pill with the same anatomy as the in-site
 * [AddressEditor][com.vitorpamplona.amethyst.commons.browser.ui.pill.AddressEditor] —
 * a leading glyph that says what Go will do (search vs. open an address), the field, clear, and a filled Go
 * once there is something to go to. Focused and empty, that trailing slot is a paste-and-go button
 * instead, when the clipboard holds text. Tab (or →) accepts the inline completion; Escape leaves the field.
 */
@Composable
private fun OmniBar(
    nav: INav,
    field: TextFieldValue,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    hasCompletion: Boolean,
    onAcceptCompletion: () -> Unit,
    onClear: () -> Unit,
    onOpen: () -> Unit,
    onPasteAndGo: (() -> Unit)?,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val isAddress = OmniboxInput.isAddress(field.text)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // The omnibox is a plain Row in the topBar slot (not a Material3 TopAppBar), so it must
                // apply the top system insets itself — systemBars rather than just statusBars, so the
                // caption bar of a desktop window (Waydroid/DeX freeform) is respected too.
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                .padding(start = if (nav.canPop()) 4.dp else 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // When reached from the drawer (pushed onto the back stack) rather than as a bottom-bar tab, show
        // a back arrow — same rule as the other launcher/feed top bars (see NappletsTopBar).
        if (nav.canPop()) {
            IconButton(onClick = nav::popBack) { ArrowBackIcon() }
        }
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(CircleShape)
                .background(if (focused) colors.surfaceContainerHighest else colors.surfaceContainerHigh)
                .border(if (focused) 2.dp else 1.dp, if (focused) colors.primary else colors.outlineVariant, CircleShape)
                // The whole pill is the target, not just the text line inside it.
                .clickable(interactionSource = null, indication = null) { focusRequester.requestFocus() }
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isAddress) MaterialSymbols.Language else MaterialSymbols.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (focused) colors.primary else colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = field,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                    keyboardActions = KeyboardActions(onGo = { onOpen() }),
                    // The placeholder lives inside the field's decoration so it is part of the field's
                    // semantics: a screen reader announces it as the field's hint, as Material's TextField does.
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (field.text.isEmpty()) {
                                Text(
                                    stringRes(Res.string.browser_address_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { onFocusChange(it.isFocused) }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    // Tab takes the ghost completion instead of moving focus away.
                                    Key.Tab ->
                                        if (hasCompletion) {
                                            onAcceptCompletion()
                                            true
                                        } else {
                                            false
                                        }
                                    Key.Escape -> {
                                        focusManager.clearFocus()
                                        true
                                    }
                                    else -> false
                                }
                            },
                )
            }
            if (field.text.isNotEmpty()) {
                IconButton(onClick = {
                    onClear()
                    focusRequester.requestFocus()
                }) {
                    Icon(MaterialSymbols.Cancel, contentDescription = stringRes(Res.string.browser_clear), modifier = Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                }
            }
            if (field.text.isNotBlank()) {
                FilledIconButton(onClick = onOpen, modifier = Modifier.size(44.dp)) {
                    Icon(MaterialSymbols.AutoMirrored.ArrowForward, contentDescription = stringRes(Res.string.browser_go), modifier = Modifier.size(22.dp))
                }
            } else if (onPasteAndGo != null) {
                IconButton(onClick = onPasteAndGo) {
                    Icon(MaterialSymbols.ContentPasteGo, contentDescription = stringRes(Res.string.browser_pill_paste_go), modifier = Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * The first row while typing: exactly what Go / Enter will do with the [entered] text — open it as an
 * address, or search for it — so the user never has to guess which one they're about to get.
 */
@Composable
private fun EnteredRow(
    entered: String,
    searchEngine: SearchEngines.SearchEngine,
    onClick: () -> Unit,
) {
    val target = OmniboxInput.resolve(entered, searchEngine.queryPrefix) ?: return
    val isAddress = !target.isSearch
    val title =
        if (isAddress) {
            stringRes(Res.string.browser_omnibox_go_to, target.url.removePrefix("https://"))
        } else {
            stringRes(Res.string.browser_omnibox_search_for, entered.trim())
        }
    val subtitle = if (isAddress) null else searchEngine.name
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isAddress) MaterialSymbols.Language else MaterialSymbols.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Icon(
            MaterialSymbols.AutoMirrored.ArrowForward,
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 12.dp).size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The typed-state body: what Go will do with the [entered] text, then ranked suggestions split into a
 * highlighted Favorites group, then Recent (visited sites), then Discover (the hardcoded web apps the user
 * hasn't pinned or visited yet). Every row carries a ↖ that puts its address in the field to keep typing
 * from, and the same 3-dot menu as the idle Recent cards (pin/unpin; plus remove-from-history for visited
 * sites).
 */
@Composable
private fun SuggestionGrid(
    entered: String,
    searchEngine: SearchEngines.SearchEngine,
    suggestions: List<OmniboxSuggestions.Suggestion>,
    iconKeys: Set<String>,
    historyUrls: Set<String>,
    onOpenEntered: () -> Unit,
    onOpen: (OmniboxSuggestions.Suggestion) -> Unit,
    onFillIn: (String) -> Unit,
    onToggleFavorite: (OmniboxSuggestions.Suggestion) -> Unit,
    onRemoveFromHistory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val favorites = suggestions.filter { it.isFavorite }
    val recent = suggestions.filter { !it.isFavorite && it.url in historyUrls }
    val discover = suggestions.filter { !it.isFavorite && it.url !in historyUrls }

    fun LazyGridScope.section(
        keyPrefix: String,
        title: StringResource,
        rows: List<OmniboxSuggestions.Suggestion>,
        highlighted: Boolean,
    ) {
        if (rows.isEmpty()) return
        item(key = "h-$keyPrefix") { SectionHeader(stringRes(title)) }
        items(rows, key = { "$keyPrefix:" + it.url }) { suggestion ->
            SuggestionRow(
                suggestion = suggestion,
                iconKeys = iconKeys,
                highlighted = highlighted,
                removableFromHistory = suggestion.url in historyUrls,
                onClick = { onOpen(suggestion) },
                onFillIn = { onFillIn(suggestion.url.removePrefix("https://")) },
                onToggleFavorite = { onToggleFavorite(suggestion) },
                onRemoveFromHistory = { onRemoveFromHistory(suggestion.url) },
            )
        }
    }

    LazyVerticalGrid(columns = GridCells.Fixed(1), modifier = modifier) {
        item(key = "entered") { EnteredRow(entered, searchEngine, onOpenEntered) }
        section("f", Res.string.browser_favorites, favorites, highlighted = true)
        section("o", Res.string.favorite_app_recent, recent, highlighted = false)
        section("s", Res.string.browser_suggested, discover, highlighted = false)
    }
}

@Composable
private fun SuggestionRow(
    suggestion: OmniboxSuggestions.Suggestion,
    iconKeys: Set<String>,
    highlighted: Boolean,
    removableFromHistory: Boolean,
    onClick: () -> Unit,
    onFillIn: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveFromHistory: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(suggestion.host, suggestion.isFavorite, iconKeys, Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                suggestion.host,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (suggestion.label.isNotBlank() && !suggestion.label.equals(suggestion.host, ignoreCase = true)) {
                Text(
                    suggestion.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onFillIn) {
            Icon(MaterialSymbols.NorthWest, contentDescription = stringRes(Res.string.browser_pill_fill_in), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(MaterialSymbols.MoreVert, contentDescription = stringRes(Res.string.browser_recent_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringRes(if (suggestion.isFavorite) Res.string.favorite_app_remove else Res.string.favorite_app_add)) },
                    leadingIcon = {
                        Icon(if (suggestion.isFavorite) MaterialSymbols.Star else MaterialSymbols.StarBorder, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        onToggleFavorite()
                    },
                )
                if (removableFromHistory) {
                    DropdownMenuItem(
                        text = { Text(stringRes(Res.string.browser_recent_remove)) },
                        leadingIcon = { Icon(MaterialSymbols.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRemoveFromHistory()
                        },
                    )
                }
            }
        }
    }
}

/** The idle body: favorites grid on top, then recent visits — in one grid so they scroll together. */
@Composable
private fun BrowserHome(
    apps: List<FavoriteApp>,
    history: List<BrowserHistoryEntry>,
    iconKeys: Set<String>,
    favoriteUrls: Set<String>,
    suggested: List<SuggestedWebApp>,
    nsites: List<DiscoverNostrApp>,
    napplets: List<DiscoverNostrApp>,
    onOpenApp: (FavoriteApp) -> Unit,
    onRemoveApp: (FavoriteApp) -> Unit,
    onAddApp: (FavoriteApp) -> Unit,
    onOpenUrl: (String) -> Unit,
    onToggleRecentFavorite: (BrowserHistoryEntry) -> Unit,
    onRemoveRecent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recents = remember(history) { history.take(RECENTS_LIMIT) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(96.dp),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (apps.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-fav") { SectionHeader(stringRes(Res.string.browser_favorites)) }
            favoriteAppItems(apps, onOpenApp, onRemoveApp)
        }
        if (recents.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-rec") { SectionHeader(stringRes(Res.string.favorite_app_recent)) }
            items(recents, span = { GridItemSpan(maxLineSpan) }, key = { "r:" + it.url }) { entry ->
                RecentRow(
                    entry = entry,
                    iconKeys = iconKeys,
                    isFavorited = entry.url in favoriteUrls,
                    onClick = { onOpenUrl(entry.url) },
                    onToggleFavorite = { onToggleRecentFavorite(entry) },
                    onRemove = { onRemoveRecent(entry.url) },
                )
            }
        }
        if (nsites.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-nsite") { SectionHeader(stringRes(Res.string.browser_discover_nsites)) }
            items(nsites, span = { GridItemSpan(maxLineSpan) }, key = { "ns:" + it.app.coordinate }) { entry ->
                NostrAppRow(entry, onClick = onOpenApp, onAddFavorite = onAddApp)
            }
        }
        if (napplets.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-napp") { SectionHeader(stringRes(Res.string.browser_discover_napplets)) }
            items(napplets, span = { GridItemSpan(maxLineSpan) }, key = { "np:" + it.app.coordinate }) { entry ->
                NostrAppRow(entry, onClick = onOpenApp, onAddFavorite = onAddApp)
            }
        }
        if (suggested.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-sug") { SectionHeader(stringRes(Res.string.browser_suggested)) }
            items(suggested, span = { GridItemSpan(maxLineSpan) }, key = { "s:" + it.app.url }) { entry ->
                SuggestedRow(
                    entry = entry,
                    iconKeys = iconKeys,
                    onClick = { onOpenApp(entry.app) },
                    onAddFavorite = { onAddApp(entry.app) },
                )
            }
        }
    }
}

/** A Discover row: the app's own icon, its name, and a one-line description, with a star to pin it. */
@Composable
private fun SuggestedRow(
    entry: SuggestedWebApp,
    iconKeys: Set<String>,
    onClick: () -> Unit,
    onAddFavorite: () -> Unit,
) {
    val appServices = LocalAppServices.current
    val iconModel = remember(entry, iconKeys) { OmniboxInput.hostOf(entry.app.url)?.let(appServices.browserIcons::iconModelFor) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FavoriteAppIcon(
            app = entry.app,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
            iconModel = iconModel,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onAddFavorite) {
            Icon(MaterialSymbols.StarBorder, contentDescription = stringRes(Res.string.favorite_app_add))
        }
    }
}

/** A followed nsite/napplet, resolved into its launchable [app] plus the manifest [description]. */
private data class DiscoverNostrApp(
    val app: FavoriteApp.NostrApp,
    val description: String?,
    val fallbackLabel: StringResource,
)

/** How many followed nsites/napplets each Discover section surfaces, so the launcher stays tidy. */
private const val DISCOVER_NOSTR_LIMIT = 12

/**
 * Keeps the [Note]s authored by the followed set, drops ones already pinned, maps each to its
 * launchable [DiscoverNostrApp], and caps the result. Covers the "Mine" selection too: the shared
 * matcher now resolves Mine to the user's own pubkey, so [matchAuthor] already narrows to the user.
 */
private fun List<Note>.toDiscoverApps(
    matchAuthor: (String) -> Boolean,
    excludeCoordinates: Set<String>,
): List<DiscoverNostrApp> =
    asSequence()
        .filter { note ->
            val author = note.event?.pubKey ?: return@filter false
            matchAuthor(author)
        }.mapNotNull { it.toDiscoverNostrApp() }
        .filter { it.app.coordinate !in excludeCoordinates }
        // The observed store can surface the same addressable manifest twice (a replaceable note whose
        // new version mutates its sort key inside the backing set), so collapse by coordinate to keep the
        // grid keys unique — otherwise LazyVerticalGrid throws on the duplicate "ns:"/"np:" key.
        .distinctBy { it.app.coordinate }
        .take(DISCOVER_NOSTR_LIMIT)
        .toList()

/** Resolve a cached nsite/napplet manifest note into a launchable favorite + its description, or null. */
private fun Note.toDiscoverNostrApp(): DiscoverNostrApp? {
    val event = event ?: return null
    // Another app's kind 35129 format, an invalid manifest, or one needing a NAP domain this shell
    // doesn't implement: nothing this launcher grid could open.
    if (event is NappletManifest && !event.isLaunchable()) return null
    val coordinate = favoriteCoordinateOf(event)
    val label: String
    val fallback: StringResource
    val description: String?
    when (event) {
        is RootSiteEvent -> {
            // Blank until composition, where NostrAppRow words it with [fallback].
            label = event.title()?.ifBlank { null } ?: ""
            fallback = Res.string.browser_nsite_default_label
            description = event.description()
        }
        is NamedSiteEvent -> {
            label = event.title()?.ifBlank { null } ?: event.identifier()
            fallback = Res.string.browser_nsite_default_label
            description = event.description()
        }
        is RootNappletEvent -> {
            label = event.title()?.ifBlank { null } ?: ""
            fallback = Res.string.browser_napplet_default_label
            description = event.content
        }
        is NamedNappletEvent -> {
            label = event.title()?.ifBlank { null } ?: event.identifier()
            fallback = Res.string.browser_napplet_default_label
            description = event.content
        }
        else -> return null
    }
    return DiscoverNostrApp(FavoriteApp.NostrApp(coordinate, label, 0L), description, fallback)
}

/** A Discover row for a followed nsite/napplet: its manifest icon, name, and description, with a pin star. */
@Composable
private fun NostrAppRow(
    discover: DiscoverNostrApp,
    onClick: (FavoriteApp) -> Unit,
    onAddFavorite: (FavoriteApp) -> Unit,
) {
    // An untitled nsite/napplet opens and pins under the localized "Website"/"App" fallback.
    val fallbackLabel = stringRes(discover.fallbackLabel)
    val app = remember(discover.app, fallbackLabel) { discover.app.copy(label = discover.app.label.ifBlank { fallbackLabel }) }
    val iconModel = rememberNappletIconModel(app.coordinate)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = { onClick(app) })
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FavoriteAppIcon(
            app = app,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
            iconModel = iconModel,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!discover.description.isNullOrBlank()) {
                Text(
                    discover.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { onAddFavorite(app) }) {
            Icon(MaterialSymbols.StarBorder, contentDescription = stringRes(Res.string.favorite_app_add))
        }
    }
}

@Composable
private fun RecentRow(
    entry: BrowserHistoryEntry,
    iconKeys: Set<String>,
    isFavorited: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(entry.host, isFavorite = isFavorited, iconKeys = iconKeys, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title.ifBlank { entry.host },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // Host and path, not just the host: two pages of one site usually carry the
                // same <title>, so `primal.net/home` and `primal.net` arrived as two rows
                // reading "Primal / primal.net" and there was no way to tell them apart or
                // to know which one a tap would open.
                remember(entry.url, entry.host) { recentSubtitle(entry.url, entry.host) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(MaterialSymbols.MoreVert, contentDescription = stringRes(Res.string.browser_recent_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringRes(if (isFavorited) Res.string.favorite_app_remove else Res.string.favorite_app_add)) },
                    leadingIcon = {
                        Icon(if (isFavorited) MaterialSymbols.Star else MaterialSymbols.StarBorder, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        onToggleFavorite()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringRes(Res.string.browser_recent_remove)) },
                    leadingIcon = { Icon(MaterialSymbols.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A site's captured favicon, falling back to a glyph (a star for favorites, the globe otherwise). */
@Composable
private fun SiteIcon(
    host: String,
    isFavorite: Boolean,
    iconKeys: Set<String>,
    modifier: Modifier = Modifier,
) {
    val appServices = LocalAppServices.current
    val model = remember(host, iconKeys) { appServices.browserIcons.iconModelFor(host) }
    val symbol = if (isFavorite) MaterialSymbols.Star else MaterialSymbols.Public
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    if (model == null) {
        Icon(symbol, contentDescription = null, modifier = modifier, tint = tint)
    } else {
        val glyph = rememberMaterialSymbolPainter(symbol, tint)
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(6.dp)),
            placeholder = glyph,
            error = glyph,
            fallback = glyph,
        )
    }
}

/** The host of [url] for a favorite's default label, falling back to the raw string. */
private fun hostOf(url: String): String = OmniboxInput.hostOf(url) ?: url

/**
 * The second line of a Recent row: what a reader needs to tell two rows of one site apart.
 *
 * The scheme and a bare trailing slash are noise at this size, so they go; everything after the
 * host stays, because that is the part that differs. Falls back to the host when the URL has
 * nothing more to say.
 */
fun recentSubtitle(
    url: String,
    host: String,
): String {
    val schemeEnd = url.indexOf("://")
    val afterScheme = if (schemeEnd > 0) url.substring(schemeEnd + 3) else url
    val trimmed = afterScheme.removeSuffix("/")
    return trimmed.ifBlank { host }
}
