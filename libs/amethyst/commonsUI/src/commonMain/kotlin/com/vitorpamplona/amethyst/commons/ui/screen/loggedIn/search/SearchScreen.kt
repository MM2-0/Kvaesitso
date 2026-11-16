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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.LocalEmojiSearchTerms
import com.vitorpamplona.amethyst.commons.relayClient.searchCommand.TextSearchDataSourceSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ephemeral_relay_chat
import com.vitorpamplona.amethyst.commons.resources.npub_hex_username
import com.vitorpamplona.amethyst.commons.resources.search_by_hashtag
import com.vitorpamplona.amethyst.commons.resources.search_filters_open
import com.vitorpamplona.amethyst.commons.resources.search_filters_reset
import com.vitorpamplona.amethyst.commons.resources.search_filters_section_sort
import com.vitorpamplona.amethyst.commons.resources.search_filters_section_source
import com.vitorpamplona.amethyst.commons.resources.search_filters_title
import com.vitorpamplona.amethyst.commons.resources.search_follows_only
import com.vitorpamplona.amethyst.commons.resources.search_no_results
import com.vitorpamplona.amethyst.commons.resources.search_no_results_explainer
import com.vitorpamplona.amethyst.commons.resources.search_recent
import com.vitorpamplona.amethyst.commons.resources.search_recent_clear
import com.vitorpamplona.amethyst.commons.resources.search_scope_all
import com.vitorpamplona.amethyst.commons.resources.search_scope_notes
import com.vitorpamplona.amethyst.commons.resources.search_scope_people
import com.vitorpamplona.amethyst.commons.resources.search_sort_newest
import com.vitorpamplona.amethyst.commons.resources.search_sort_oldest
import com.vitorpamplona.amethyst.commons.resources.search_sort_popular
import com.vitorpamplona.amethyst.commons.resources.search_sort_relevance
import com.vitorpamplona.amethyst.commons.resources.search_source_local
import com.vitorpamplona.amethyst.commons.resources.search_source_relays
import com.vitorpamplona.amethyst.commons.resources.search_type_to_begin
import com.vitorpamplona.amethyst.commons.resources.search_type_to_begin_explainer
import com.vitorpamplona.amethyst.commons.resources.search_waiting_on_relays
import com.vitorpamplona.amethyst.commons.search.QuerySerializer
import com.vitorpamplona.amethyst.commons.search.SearchScope
import com.vitorpamplona.amethyst.commons.search.SearchSortOrder
import com.vitorpamplona.amethyst.commons.search.SearchSource
import com.vitorpamplona.amethyst.commons.search.searchHistoryStorage
import com.vitorpamplona.amethyst.commons.ui.components.namecoin.NamecoinResolutionRow
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClearTextIcon
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.SearchIcon
import com.vitorpamplona.amethyst.commons.ui.note.UserCompose
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.ChannelName
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.BasicRelaySetupInfoClickableRow
import com.vitorpamplona.amethyst.commons.ui.search.GroupCandidate
import com.vitorpamplona.amethyst.commons.ui.search.SEARCH_PICKER_LIMIT
import com.vitorpamplona.amethyst.commons.ui.search.SearchFieldState
import com.vitorpamplona.amethyst.commons.ui.search.TokenizedSearchField
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdTopPadding
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The search screen, opened either bare or with the calling screen's own filter already in the
 * box — see [Route.Search.of].
 *
 * [initialQuery] is seeded straight into the field rather than held beside it, so the tokens it
 * contains draw as chips through the same path as anything typed, and a backspace drops them.
 */
@Composable
fun SearchScreen(
    initialQuery: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val appStores = LocalAppServices.current.appStores
    val namecoinResolver = LocalAppServices.current.namecoinResolver
    val historyStorage = remember { appStores.searchHistoryStorage() }
    val searchBarViewModel: SearchBarViewModel =
        rememberViewModel(
            // Keyed on the seed: navigating from one screen's search button to another's has to
            // build a model holding that screen's filter, not hand back the previous one.
            key = "SearchBarViewModel${initialQuery?.let { " $it" }.orEmpty()}",
            factory =
                SearchBarViewModel.Factory(
                    accountViewModel.account,
                    accountViewModel.nip05ClientBuilder(),
                    namecoinResolver,
                    historyStorage,
                    initialQuery,
                ),
        )

    SearchScreen(searchBarViewModel, accountViewModel, nav)
}

@Composable
fun SearchScreen(
    searchBarViewModel: SearchBarViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(searchBarViewModel)

    LaunchedEffect(searchBarViewModel.focusRequester) {
        if (searchBarViewModel.listState.firstVisibleItemIndex == 0) {
            searchBarViewModel.focusRequester.requestFocus()
        }
    }

    // A picker is a scrollable living inside the top bar, and the scaffold moves its bars on any
    // scroll in its subtree. Pin them while one is open, or paging through the kinds drags the
    // whole chrome up with it.
    val pickerOpen by searchBarViewModel.pickerOpen.collectAsStateWithLifecycle()

    DisappearingScaffold(
        isInvertedLayout = false,
        allowBarHide = !pickerOpen,
        topBar = {
            SearchBar(searchBarViewModel, accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.Search(), nav, accountViewModel) { route ->
                nav.navBottomBar(route)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        DisplaySearchResults(
            searchBarViewModel = searchBarViewModel,
            headerContent = { ObserveRelayListForSearchAndDisplayIfNotFound(accountViewModel, nav) },
            nav = nav,
            accountViewModel = accountViewModel,
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun SearchBar(
    searchBarViewModel: SearchBarViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    TextSearchDataSourceSubscription(searchBarViewModel, accountViewModel)

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect {
                if (searchBarViewModel.isSearchingFun()) {
                    searchBarViewModel.invalidateData()
                }
            }
        }

        launch(Dispatchers.IO) {
            LocalCache.live.deletedEventBundles.collect {
                if (searchBarViewModel.isSearchingFun()) {
                    searchBarViewModel.invalidateData()
                }
            }
        }

        // Invite links only. A pasted nip19 code used to navigate straight off this screen,
        // which the token language made unusable: `from:npub1…` contains an npub, so typing an
        // author filter opened that profile mid-query. Codes now resolve into the results list.
        launch {
            searchBarViewModel.directRouteResolver.filterNotNull().collect { route ->
                nav.nav(route)
                searchBarViewModel.clear()
            }
        }
    }

    Column(
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                // A custom bar (not a Material3 TopAppBar) must apply the top system insets
                // itself. systemBars — not just statusBars — so the caption bar of a desktop
                // window (Waydroid/DeX freeform) is respected too.
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
    ) {
        SearchTextField(searchBarViewModel, accountViewModel, Modifier)
        // Inline Namecoin lookup feedback for the global search field.
        // Mirrors the wiring in OnchainZapSendDialog: the local prefix
        // search can race ahead of the on-chain resolution and show a
        // cached sibling profile (e.g. "m@testls.bit") before the bare
        // ".bit" host resolves to its `_@host` profile. Surfaces the
        // in-flight state, the eventual on-chain match, and any failure
        // explicitly. Tapping the resolved row navigates to the user and
        // clears the search field, matching the invite-link behaviour in
        // `SearchBarViewModel.directRouteResolver`.
        NamecoinResolutionRow(
            searchInput = searchBarViewModel.searchValue,
            accountViewModel = accountViewModel,
            onUserResolved = { user ->
                nav.nav(routeFor(user))
                searchBarViewModel.clear()
            },
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        SearchFilterRow(searchBarViewModel)
    }
}

// Must match SearchBarViewModel.source's initial value.
private val DEFAULT_SOURCE = SearchSource.RELAYS

private fun hasNonDefaultFilters(
    scope: SearchScope,
    source: SearchSource,
    followsOnly: Boolean,
    sort: SearchSortOrder,
): Boolean {
    if (source != DEFAULT_SOURCE) return true
    if (followsOnly) return true
    if (scope != SearchScope.PEOPLE && sort != SearchSortOrder.EVENT_DEFAULT) return true
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilterRow(searchBarViewModel: SearchBarViewModel) {
    val currentScope by searchBarViewModel.scope.collectAsStateWithLifecycle()
    val pinnedToNotes by searchBarViewModel.scopePinnedToNotes.collectAsStateWithLifecycle()
    val currentSource by searchBarViewModel.source.collectAsStateWithLifecycle()
    val currentFollowsOnly by searchBarViewModel.followsOnly.collectAsStateWithLifecycle()
    val currentSort by searchBarViewModel.sortOrder.collectAsStateWithLifecycle()

    var sheetOpen by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            val scopes = listOf(SearchScope.ALL, SearchScope.PEOPLE, SearchScope.NOTES)
            scopes.forEachIndexed { index, s ->
                SegmentedButton(
                    selected = currentScope == s,
                    onClick = { searchBarViewModel.updateScope(s) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = scopes.size),
                    // Any token in the box beyond its free text is something the people search
                    // cannot be asked, so All and People are greyed rather than merely
                    // unselected: they would answer a narrower question than the one on screen,
                    // silently dropping the chips the reader can still see.
                    enabled = !pinnedToNotes || s == SearchScope.NOTES,
                ) {
                    Text(
                        text =
                            when (s) {
                                SearchScope.ALL -> stringRes(Res.string.search_scope_all)
                                SearchScope.PEOPLE -> stringRes(Res.string.search_scope_people)
                                SearchScope.NOTES -> stringRes(Res.string.search_scope_notes)
                            },
                    )
                }
            }
        }

        FilterIconButton(
            hasBadge =
                hasNonDefaultFilters(
                    scope = currentScope,
                    source = currentSource,
                    followsOnly = currentFollowsOnly,
                    sort = currentSort,
                ),
            onClick = { sheetOpen = true },
        )
    }

    if (sheetOpen) {
        SearchFiltersSheet(
            scope = currentScope,
            source = currentSource,
            followsOnly = currentFollowsOnly,
            sort = currentSort,
            onSourceChange = searchBarViewModel::updateSource,
            onFollowsOnlyChange = searchBarViewModel::updateFollowsOnly,
            onSortChange = searchBarViewModel::updateSortOrder,
            onReset = {
                searchBarViewModel.updateSource(DEFAULT_SOURCE)
                searchBarViewModel.updateFollowsOnly(false)
                searchBarViewModel.updateSortOrder(SearchSortOrder.EVENT_DEFAULT)
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun FilterIconButton(
    hasBadge: Boolean,
    onClick: () -> Unit,
) {
    Box {
        IconButton(onClick = onClick) {
            Icon(
                symbol = MaterialSymbols.Tune,
                contentDescription = stringRes(Res.string.search_filters_open),
            )
        }
        if (hasBadge) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-10).dp, y = 10.dp)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFiltersSheet(
    scope: SearchScope,
    source: SearchSource,
    followsOnly: Boolean,
    sort: SearchSortOrder,
    onSourceChange: (SearchSource) -> Unit,
    onFollowsOnlyChange: (Boolean) -> Unit,
    onSortChange: (SearchSortOrder) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringRes(Res.string.search_filters_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringRes(Res.string.search_filters_section_source),
                    style = MaterialTheme.typography.labelLarge,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val sources = listOf(SearchSource.LOCAL, SearchSource.RELAYS)
                    sources.forEachIndexed { index, s ->
                        SegmentedButton(
                            selected = source == s,
                            onClick = { onSourceChange(s) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = sources.size),
                        ) {
                            Text(
                                text =
                                    when (s) {
                                        SearchSource.LOCAL -> stringRes(Res.string.search_source_local)
                                        SearchSource.RELAYS -> stringRes(Res.string.search_source_relays)
                                    },
                            )
                        }
                    }
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onFollowsOnlyChange(!followsOnly) }
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(Res.string.search_follows_only),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = followsOnly,
                    onCheckedChange = onFollowsOnlyChange,
                )
            }

            if (scope != SearchScope.PEOPLE) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringRes(Res.string.search_filters_section_sort),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    SearchSortOrder.EVENT_OPTIONS.forEach { opt ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = sort == opt,
                                        onClick = { onSortChange(opt) },
                                    ).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = sort == opt,
                                onClick = { onSortChange(opt) },
                            )
                            Text(
                                text = sortLabel(opt),
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            TextButton(onClick = onReset) {
                Text(stringRes(Res.string.search_filters_reset))
            }
        }
    }
}

@Composable
private fun sortLabel(opt: SearchSortOrder): String =
    when (opt) {
        SearchSortOrder.NEWEST -> stringRes(Res.string.search_sort_newest)
        SearchSortOrder.RELEVANCE -> stringRes(Res.string.search_sort_relevance)
        SearchSortOrder.POPULAR -> stringRes(Res.string.search_sort_popular)
        SearchSortOrder.OLDEST -> stringRes(Res.string.search_sort_oldest)
        SearchSortOrder.NAME_AZ, SearchSortOrder.NAME_ZA -> opt.label
    }

/**
 * What the results area says when it has nothing to show.
 *
 * The main search box was the only search surface in the app without one — settings, git
 * repositories, the location picker and app recommendations all say something — and it is the one
 * that needs it most, because a seeded query legitimately returns nothing until a word is typed.
 */
@Composable
private fun SearchEmptyState(
    title: String,
    explainer: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = explainer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.placeholderText,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SearchTextField(
    searchBarViewModel: SearchBarViewModel,
    accountViewModel: AccountViewModel,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.padding(10.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The tokenized field: `from:`/`to:`, `since:`/`until:`, `#tag`, `label:`, `group:` and
        // the NIP-73 scopes settle into chips as the caret leaves them, and become NIP-01 filter
        // fields rather than search terms. The value stays the plain text, so a query can still
        // be copied out and pasted back.
        val fieldState = remember { SearchFieldState(searchBarViewModel.searchValue) }
        val appServices = LocalAppServices.current

        // Reported up rather than read down: the scaffold that has to stop moving is composed
        // above this field, and it is the only thing that can keep a picker's scroll off the bars.
        val hasPicker = fieldState.activePicker != null
        LaunchedEffect(hasPicker) { searchBarViewModel.pickerOpen.value = hasPicker }
        val interactionSource = remember { MutableInteractionSource() }

        // The composer's own mention picker, reused verbatim: it already resolves NIP-05, asks
        // the search and indexer relays through SearchQueryState, and ranks follows first. A
        // second, thinner people search living only in the search field would answer `from:` with
        // less than `@` answers in a post.
        val userSuggestions =
            remember(accountViewModel) {
                UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
            }

        // The rooms the reader can actually reach, for the `group:` picker and for naming the
        // chips it writes. An id typed from memory is unverifiable; a name is not.
        var groupQuery by remember { mutableStateOf("") }
        var groupInvalidations by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            LocalCache.live.newEventBundles.collect { groupInvalidations++ }
        }
        // Keyed on the cache's own invalidation counter: a room joined while this screen is
        // open has to reach the picker, and a keyless remember froze both it and the names.
        val groupChannels = remember(groupInvalidations) { LocalCache.allRelayGroupChannels() }
        val groupNames = remember(groupChannels) { groupChannels.associate { it.groupId.id to it.toBestDisplayName() } }

        val groupCandidates =
            remember(groupChannels, groupQuery) {
                groupChannels
                    .asSequence()
                    // A group id is opaque and case-exact, but a reader types the name they know.
                    .filter { groupQuery.isBlank() || it.groupId.id.startsWith(groupQuery, true) || it.toBestDisplayName().contains(groupQuery, true) }
                    .take(SEARCH_PICKER_LIMIT)
                    .map { channel ->
                        GroupCandidate(
                            id = channel.groupId.id,
                            name = channel.toBestDisplayName(),
                            subtitle = channel.groupId.relayUrl.displayUrl(),
                            // The same id on two relays is two rooms, and a `#h` filter cannot tell them apart.
                            ambiguous = groupChannels.count { it.groupId.id == channel.groupId.id } > 1,
                        )
                    }.toList()
                    .toImmutableList()
            }

        LaunchedEffect(fieldState.text) { searchBarViewModel.updateSearchValue(fieldState.text) }
        // The one direction that is not the field's own: a clear, or a query loaded from elsewhere.
        LaunchedEffect(searchBarViewModel.searchValue) {
            if (fieldState.text != searchBarViewModel.searchValue) fieldState.setText(searchBarViewModel.searchValue)
        }

        TokenizedSearchField(
            state = fieldState,
            modifier = Modifier.weight(1f, true),
            fieldModifier =
                Modifier
                    .defaultMinSize(minHeight = 20.dp)
                    .focusRequester(searchBarViewModel.focusRequester),
            // A key already in the cache draws as its owner's name; one that has not arrived yet
            // stays a short npub rather than being given an invented name.
            displayName = { LocalCache.getUserIfExists(it)?.toBestDisplayName() },
            groupName = { groupNames[it] },
            // The same reverse-geocode cache the feed spinner and thread view already read
            // through LoadCityName; null until it resolves, which leaves the geohash showing.
            scopeName = { field, value -> if (field == "geo") appServices.cachedPlaceName(value) else null },
            groups = groupCandidates,
            onPeopleQuery = { userSuggestions.processCurrentWord(it) },
            onGroupQuery = { partial -> groupQuery = partial },
            // The Enter key is what says a search was meant, rather than passed through on the way
            // to a longer word. Recording every pause in typing would fill the list with prefixes.
            onSubmit = { searchBarViewModel.remember() },
            peoplePicker = { _, onPick ->
                ShowUserSuggestionList(
                    userSuggestions = userSuggestions,
                    onSelect = { onPick(it.pubkeyHex) },
                    accountViewModel = accountViewModel,
                    contentPadding = PaddingValues(0.dp),
                )
            },
            textStyle = LocalTextStyle.current,
            decorationBox = { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = fieldState.text,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(25.dp),
                    leadingIcon = { SearchIcon(modifier = Size20Modifier, MaterialTheme.colorScheme.placeholderText) },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.npub_hex_username),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    trailingIcon = {
                        if (searchBarViewModel.hasQuery.value) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Android had no way of saying results were still coming, so an
                                // empty list part-way through a search looked like the answer.
                                // Beside the clear button rather than in place of it: a search
                                // that is still running is exactly when a reader wants to abandon
                                // it.
                                if (searchBarViewModel.isRefreshing.value) {
                                    RelayWaitProgress(searchBarViewModel)
                                }
                                IconButton(onClick = { searchBarViewModel.clear() }) {
                                    ClearTextIcon()
                                }
                            }
                        }
                    },
                    colors =
                        TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                )
            },
        )
    }
}

@Composable
private fun DisplaySearchResults(
    searchBarViewModel: SearchBarViewModel,
    headerContent: @Composable () -> Unit,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    val hasQuery by searchBarViewModel.hasQuery
    val hashTags by searchBarViewModel.hashtagResults.collectAsStateWithLifecycle()
    val relays by searchBarViewModel.relayResults.collectAsStateWithLifecycle()
    val users by searchBarViewModel.searchResultsUsers.collectAsStateWithLifecycle()
    val publicChatChannels by searchBarViewModel.searchResultsPublicChatChannels.collectAsStateWithLifecycle()
    val ephemeralChannels by searchBarViewModel.searchResultsEphemeralChannels.collectAsStateWithLifecycle()
    val liveActivityChannels by searchBarViewModel.searchResultsLiveActivityChannels.collectAsStateWithLifecycle()
    val notes by searchBarViewModel.searchResultsNotes.collectAsStateWithLifecycle()
    val emojis by searchBarViewModel.emojiResults.collectAsStateWithLifecycle()
    val emojiTerms by searchBarViewModel.emojiSearchTerms.collectAsStateWithLifecycle()
    val asksNothing by searchBarViewModel.queryAsksNothing.collectAsStateWithLifecycle()
    val settled by searchBarViewModel.searchSettled.collectAsStateWithLifecycle()
    val recent by searchBarViewModel.history.recent.collectAsStateWithLifecycle()

    // Serialized once per change of the list rather than twice per row per recomposition: the row
    // draws it, and the lazy list keys on it.
    val recentText = remember(recent) { recent.map { QuerySerializer.serialize(it) } }

    // Pack cards in the results lead with, and ring, the emojis these terms matched.
    CompositionLocalProvider(LocalEmojiSearchTerms provides emojiTerms) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            contentPadding = rememberFeedContentPadding(FeedPadding),
            state = searchBarViewModel.listState,
        ) {
            item(key = "scaffold-header") { headerContent() }

            if (!hasQuery) {
                // An empty box used to render nothing at all. What a reader searched for before is
                // the one thing worth offering there, and it is one tap from being re-run because a
                // history entry is stored as the same text the box holds.
                if (recent.isNotEmpty()) {
                    item(key = "recent-header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringRes(Res.string.search_recent),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                            TextButton(onClick = { searchBarViewModel.history.clearRecent() }) {
                                Text(stringRes(Res.string.search_recent_clear))
                            }
                        }
                    }
                    itemsIndexed(recentText, key = { _, item -> "recent-$item" }) { _, asText ->
                        Text(
                            text = asText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { searchBarViewModel.updateSearchValue(asText) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        HorizontalDivider(thickness = DividerThickness)
                    }
                }
                return@LazyColumn
            }

            // Every result list is empty. Which of the two things that means is not cosmetic: a box
            // holding only a seeded `kind:` never asked a relay anything, and telling that reader
            // "no results" would blame the network for a search they have not written yet.
            val foundNothing =
                hashTags.isEmpty() && relays.isEmpty() && users.isEmpty() && notes.isEmpty() && emojis.isEmpty() &&
                    publicChatChannels.isEmpty() && ephemeralChannels.isEmpty() && liveActivityChannels.isEmpty()

            if (foundNothing && (asksNothing || settled)) {
                item(key = "empty-state") {
                    SearchEmptyState(
                        title = stringRes(if (asksNothing) Res.string.search_type_to_begin else Res.string.search_no_results),
                        explainer = stringRes(if (asksNothing) Res.string.search_type_to_begin_explainer else Res.string.search_no_results_explainer),
                    )
                }
                return@LazyColumn
            }

            itemsIndexed(
                hashTags,
                key = { _, item -> "#$item" },
            ) { _, item ->
                HashtagLine(item.lowercase()) { nav.nav(Route.Hashtag(item.lowercase())) }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 10.dp),
                    thickness = DividerThickness,
                )
            }

            itemsIndexed(
                users,
                key = { _, item -> "u" + item.pubkeyHex },
            ) { _, item ->
                UserCompose(item, accountViewModel = accountViewModel, nav = nav)

                HorizontalDivider(
                    thickness = DividerThickness,
                )
            }

            if (emojis.isNotEmpty()) {
                item(key = "emoji-results") {
                    EmojiSearchResults(emojis, accountViewModel, nav)
                    HorizontalDivider(thickness = DividerThickness)
                }
            }

            itemsIndexed(
                relays,
                key = { _, item -> "relay${item.relay.url}" },
            ) { _, relayInfo ->
                BasicRelaySetupInfoClickableRow(
                    item = relayInfo,
                    loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                    loadRobohash = LocalDisplaySettings.current.loadRobohash,
                    onClick = { nav.nav(Route.RelayInfo(relayInfo.relay.url)) },
                    onDelete = null,
                    nip11CachedRetriever = LocalCache.appHost.nip11Cache,
                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 10.dp),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            itemsIndexed(
                publicChatChannels,
                key = { _, item -> "public" + item.idHex },
            ) { _, item ->
                ChannelName(
                    channelIdHex = item.idHex,
                    channelPicture = item.profilePicture(),
                    channelTitle = {
                        Text(
                            item.toBestDisplayName(),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    channelLastTime = null,
                    channelLastContent = item.summary(),
                    hasNewMessages = false,
                    loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                    loadRobohash = LocalDisplaySettings.current.loadRobohash,
                    autoPlayGif =
                        accountViewModel.settings.autoPlayVideosFlow
                            .collectAsStateWithLifecycle()
                            .value,
                    onClick = { nav.nav(routeFor(item)) },
                )

                HorizontalDivider(
                    modifier = StdTopPadding,
                    thickness = DividerThickness,
                )
            }

            itemsIndexed(
                ephemeralChannels,
                key = { _, item -> "ephem" + item.roomId.toKey() },
            ) { _, item ->
                val relayInfo by loadRelayInfo(item.roomId.relayUrl)

                ChannelName(
                    channelIdHex = item.roomId.toKey(),
                    channelPicture = relayInfo.icon,
                    channelTitle = {
                        Text(
                            item.toBestDisplayName(),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    channelLastTime = null,
                    channelLastContent = stringRes(Res.string.ephemeral_relay_chat),
                    hasNewMessages = false,
                    loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                    loadRobohash = LocalDisplaySettings.current.loadRobohash,
                    autoPlayGif =
                        accountViewModel.settings.autoPlayVideosFlow
                            .collectAsStateWithLifecycle()
                            .value,
                    onClick = { nav.nav(routeFor(item)) },
                )

                HorizontalDivider(
                    modifier = StdTopPadding,
                    thickness = DividerThickness,
                )
            }

            itemsIndexed(
                liveActivityChannels,
                key = { _, item -> "live" + item.address.toValue() },
            ) { _, item ->
                ChannelName(
                    channelIdHex = item.address.toValue(),
                    channelPicture = item.profilePicture(),
                    channelTitle = {
                        Text(
                            item.toBestDisplayName(),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    channelLastTime = null,
                    channelLastContent = item.summary(),
                    hasNewMessages = false,
                    loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                    loadRobohash = LocalDisplaySettings.current.loadRobohash,
                    autoPlayGif =
                        accountViewModel.settings.autoPlayVideosFlow
                            .collectAsStateWithLifecycle()
                            .value,
                    onClick = { nav.nav(routeFor(item)) },
                )

                HorizontalDivider(
                    modifier = StdTopPadding,
                    thickness = DividerThickness,
                )
            }

            itemsIndexed(
                notes,
                key = { _, item -> "n" + item.idHex },
            ) { _, item ->
                NoteCompose(
                    item,
                    quotesLeft = 1,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )

                HorizontalDivider(
                    thickness = DividerThickness,
                )
            }
        }
    }
}

@Composable
fun HashtagLine(
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringRes(Res.string.search_by_hashtag, tag),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The search spinner, and what it is waiting on.
 *
 * The spinner alone can only say "still going", and it says that off a timer -- there is no EOSE
 * on this path, so `settled` means "long enough since you typed", not "the relays answered". The
 * sub-assemblers do know which relays were asked and which have sent EOSE, so tapping the spinner
 * shows exactly that, and a search that looks stuck names the relay it is stuck on.
 *
 * Relays that never send EOSE stay in the waiting list, which is the true answer rather than a
 * tidy one.
 */
@Composable
private fun RelayWaitProgress(viewModel: SearchBarViewModel) {
    var showing by remember { mutableStateOf(false) }

    val waiting = viewModel.relaysWaiting.value
    val asked by viewModel.relaysAsked.collectAsStateWithLifecycle()
    val answered by viewModel.relaysAnswered.collectAsStateWithLifecycle()

    Box {
        CircularProgressIndicator(
            modifier = Size20Modifier.clickable { showing = true },
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.placeholderText,
        )

        DropdownMenu(expanded = showing, onDismissRequest = { showing = false }) {
            Text(
                text = stringRes(Res.string.search_waiting_on_relays, waiting.size, asked.size),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = Size10dp, vertical = Size5dp),
            )

            waiting.sortedBy { it.url }.forEach { relay ->
                DropdownMenuItem(
                    text = { Text(relay.displayUrl(), style = MaterialTheme.typography.bodySmall) },
                    onClick = { showing = false },
                )
            }

            // The ones already in, dimmed: "three of eight" is easier to read against the list it
            // came from than on its own.
            (asked intersect answered).sortedBy { it.url }.forEach { relay ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = relay.displayUrl(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    onClick = { showing = false },
                )
            }
        }
    }
}
