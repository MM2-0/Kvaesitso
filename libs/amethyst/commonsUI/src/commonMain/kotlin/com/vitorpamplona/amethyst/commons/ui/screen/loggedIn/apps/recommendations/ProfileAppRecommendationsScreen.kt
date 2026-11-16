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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.apps.recommendations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_definition_handles
import com.vitorpamplona.amethyst.commons.resources.app_definition_no_supported_kinds
import com.vitorpamplona.amethyst.commons.resources.app_definition_untitled
import com.vitorpamplona.amethyst.commons.resources.profile_app_recommendations_description
import com.vitorpamplona.amethyst.commons.resources.profile_app_recommendations_empty
import com.vitorpamplona.amethyst.commons.resources.profile_app_recommendations_filter_empty
import com.vitorpamplona.amethyst.commons.resources.profile_app_recommendations_search
import com.vitorpamplona.amethyst.commons.resources.profile_app_recommendations_search_empty
import com.vitorpamplona.amethyst.commons.resources.select_list_to_filter
import com.vitorpamplona.amethyst.commons.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.FeedFilterSpinner
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.ClearTextIcon
import com.vitorpamplona.amethyst.commons.ui.note.SearchIcon
import com.vitorpamplona.amethyst.commons.ui.note.types.ByAuthorChip
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.apps.recommendations.datasource.ProfileAppRecommendationsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindDisplayName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.kinds.KindNames
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.flowOn

@Composable
fun ProfileAppRecommendationsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Pull my kind 31989 events plus recent kind 31990 app definitions from
    // relays so the list below has candidates while this screen is open.
    ProfileAppRecommendationsFilterAssemblerSubscription(accountViewModel)

    // Kind 31990 app definitions, kept live. observeNotes seeds with the notes
    // already cached and re-emits as new definitions are inserted, so the
    // candidate list below derives straight from these notes — no manual tick or
    // full-cache rescan.
    //
    // Caveat: observeNotes does NOT re-emit when an already-listed addressable is
    // replaced in place, so the list isn't re-filtered/re-sorted on a kind-31990
    // update (a blank app that later gains a name, or a newer createdAt changing
    // the tier order). That's acceptable here: membership rarely flips on an
    // update, each row's own content stays live via AppRow's observeNoteEvent, and
    // the list recomputes anyway as the recommendation/follow lists stream in.
    //
    // The initial value is the current cache snapshot so the first frame matches
    // the seeded emission instead of flashing empty.
    val cachedAppDefinitions =
        remember {
            LocalCache.addressables
                .filterIntoSet(AppDefinitionEvent.KIND) { _, _ -> true }
                .toList()
        }
    val appDefinitionNotes by remember {
        LocalCache
            .observeNotes(Filter(kinds = listOf(AppDefinitionEvent.KIND)))
            .flowOn(Dispatchers.IO)
    }.collectAsStateWithLifecycle(initialValue = cachedAppDefinitions)

    val myRecommendationEvents by accountViewModel.account.appRecommendations.flow
        .collectAsStateWithLifecycle()

    val recommendedAddresses =
        remember(myRecommendationEvents) {
            myRecommendationEvents.flatMapTo(mutableSetOf()) { event -> event.recommendations().map { it.address } }
        }

    // The recommended set used for ORDERING only. It tracks recommendedAddresses
    // while my 31989s stream in from relays, but freezes at the first toggle so
    // rows don't jump around mid-edit. The next visit re-sorts with fresh data.
    // Both pins start from the data already in cache (not empty) so the first
    // frame after returning to this screen sorts the same as the last one;
    // otherwise the restored LazyListState anchors to a key that then jumps
    // down the list when the tiers kick in, dragging the viewport with it.
    var userHasEdited by remember { mutableStateOf(false) }
    var pinnedRecommended by remember { mutableStateOf(recommendedAddresses) }
    LaunchedEffect(recommendedAddresses) {
        if (!userHasEdited) pinnedRecommended = recommendedAddresses
    }

    // Tracks the follow list as it loads (it may not be ready when the screen
    // opens), then freezes with the first toggle like pinnedRecommended.
    val followsState by accountViewModel.account.kind3FollowList.flow
        .collectAsStateWithLifecycle()
    var pinnedFollows by remember { mutableStateOf(accountViewModel.account.kind3FollowList.flow.value.authors) }
    LaunchedEffect(followsState) {
        if (!userHasEdited) pinnedFollows = followsState.authors
    }

    val apps =
        remember(appDefinitionNotes, pinnedRecommended, pinnedFollows) {
            appDefinitionNotes
                .filterIsInstance<AddressableNote>()
                .filter { note ->
                    val event = note.event as? AppDefinitionEvent ?: return@filter false
                    // Unnamed apps are poor recommendation candidates; keep them
                    // only when already recommended, so they can be turned off.
                    note.address in pinnedRecommended ||
                        event
                            .appMetaData()
                            ?.anyName()
                            ?.isNotBlank() == true
                }.sortedWith(
                    // Apps I recommend on top, then apps authored by people I
                    // follow, then the rest; most recent first within each tier.
                    compareByDescending<AddressableNote> { it.address in pinnedRecommended }
                        .thenByDescending { it.address.pubKeyHex in pinnedFollows }
                        .thenByDescending { it.event?.createdAt ?: 0 },
                )
        }

    // Apps I recommend whose kind 31990 definition hasn't arrived yet: still
    // listed so they can be turned off, while EventFinder fetches the details.
    // Derived from the pinned set so a deselected row stays visible (switched
    // off) instead of vanishing mid-edit.
    val missingRecommended =
        remember(pinnedRecommended, apps) {
            val known = apps.mapTo(mutableSetOf()) { it.address }
            pinnedRecommended
                .filterNot { it in known }
                .map { LocalCache.getOrCreateAddressableNote(it) }
        }

    // The shared top-nav feed filter, resolved to an author/tag matcher. Only the
    // author dimension is meaningful for app definitions, so the matchAuthor side
    // does the work (Follows lists narrow to apps by those authors); the
    // hashtag/relay/community variants leave matchAuthor == true and act as no-ops.
    // Applied to discovered apps only — apps I already recommend stay listed so I
    // can always turn them off.
    val navFilter by accountViewModel.account.liveAppRecommendationsFollowLists
        .collectAsStateWithLifecycle()
    val authorFilteredApps =
        remember(apps, navFilter) {
            apps.filter { navFilter.matchAuthor(it.address.pubKeyHex) }
        }

    // Full candidate list in display order; the search box filters this view.
    val allApps =
        remember(missingRecommended, authorFilteredApps) {
            missingRecommended + authorFilteredApps
        }

    var searchQuery by remember { mutableStateOf("") }

    // Matches on the app's name; rows whose definition hasn't arrived yet have no
    // name to match, so they only show when the search box is empty.
    val visibleApps =
        remember(allApps, searchQuery) {
            val query = searchQuery.trim()
            if (query.isEmpty()) {
                allApps
            } else {
                allApps.filter { note ->
                    val event = note.event as? AppDefinitionEvent
                    event?.appMetaData()?.anyName()?.contains(query, ignoreCase = true) == true
                }
            }
        }

    Scaffold(
        topBar = {
            AppRecommendationsTopBar(accountViewModel, nav)
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(
                text = stringRes(Res.string.profile_app_recommendations_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            if (allApps.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.profile_app_recommendations_search),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    leadingIcon = { SearchIcon(modifier = Size20Modifier, MaterialTheme.colorScheme.placeholderText) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                ClearTextIcon()
                            }
                        }
                    },
                    singleLine = true,
                )
            }

            HorizontalDivider()

            if (allApps.isEmpty()) {
                // Apps exist in cache but the author filter hid them all, vs.
                // nothing discovered yet.
                val emptyMessage =
                    if (apps.isNotEmpty()) {
                        stringRes(Res.string.profile_app_recommendations_filter_empty)
                    } else {
                        stringRes(Res.string.profile_app_recommendations_empty)
                    }
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else if (visibleApps.isEmpty()) {
                Text(
                    text = stringRes(Res.string.profile_app_recommendations_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = visibleApps,
                        key = { it.idHex },
                    ) { appNote ->
                        AppRow(
                            appNote = appNote,
                            isRecommended = appNote.address in recommendedAddresses,
                            onUserEdited = { userHasEdited = true },
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRecommendationsTopBar(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ShorterTopAppBar(
        navigationIcon = {
            if (nav.canPop()) {
                IconButton(nav::popBack) {
                    ArrowBackIcon()
                }
            }
        },
        title = {
            val listName by accountViewModel.account.settings.defaultAppRecommendationsFollowList
                .collectAsStateWithLifecycle()
            val options by accountViewModel.feedStates.feedListOptions.kind3GlobalPeople
                .collectAsStateWithLifecycle()

            FeedFilterSpinner(
                placeholderCode = listName,
                explainer = stringRes(Res.string.select_list_to_filter),
                options = options,
                onSelect = { accountViewModel.account.settings.changeDefaultAppRecommendationsFollowList(it.code) },
                accountViewModel = accountViewModel,
            )
        },
    )
}

@Composable
private fun AppRow(
    appNote: AddressableNote,
    isRecommended: Boolean,
    onUserEdited: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Ask relays for the definition only if we don't have it yet, then watch.
    // Subscribing for every visible row floods relays for nothing.
    if (appNote.event == null) {
        EventFinderFilterAssemblerSubscription(appNote, accountViewModel)
    }
    val definition by observeNoteEvent<AppDefinitionEvent>(appNote, accountViewModel)

    val metadata = definition?.appMetaData()
    val supportedKinds = definition?.supportedKinds() ?: emptyList()
    val canToggle = definition != null && (isRecommended || supportedKinds.isNotEmpty())

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    routeFor(appNote, accountViewModel.account)?.let { nav.nav(it) }
                }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(definition)

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metadata?.anyName()?.ifBlank { null } ?: stringRes(Res.string.app_definition_untitled),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)) {
                ByAuthorChip(appNote.address.pubKeyHex, accountViewModel, nav)
            }
            metadata?.about?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (definition != null && supportedKinds.isEmpty()) {
                Text(
                    text = stringRes(Res.string.app_definition_no_supported_kinds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (supportedKinds.isNotEmpty()) {
                Text(
                    text = supportedKindsLabel(supportedKinds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.size(12.dp))

        Switch(
            checked = isRecommended,
            enabled = canToggle,
            onCheckedChange = { checked ->
                onUserEdited()
                accountViewModel.launchSigner {
                    val account = accountViewModel.account
                    if (checked) {
                        val event = definition ?: return@launchSigner
                        account.appRecommendations.recommendApp(event, appNote.relayHintUrl(), account)
                    } else {
                        account.appRecommendations.unrecommendApp(appNote.address, account)
                    }
                }
            },
        )
    }
}

private const val VISIBLE_KIND_NAMES = 3

@Composable
private fun supportedKindsLabel(kinds: List<Int>): String {
    val names =
        kinds.take(VISIBLE_KIND_NAMES).map { kind ->
            val nameRes = kindDisplayName(kind)
            if (nameRes != null) stringRes(nameRes) else (KindNames.nameFor(kind) ?: "k$kind")
        }
    val overflow = kinds.size - VISIBLE_KIND_NAMES
    val suffix = if (overflow > 0) " +$overflow" else ""
    return stringRes(Res.string.app_definition_handles) + ": " + names.joinToString(" · ") + suffix
}

@Composable
private fun AppLogo(definition: AppDefinitionEvent?) {
    val imageUrl = definition?.appMetaData()?.profilePicture()?.ifBlank { null }
    val logoModifier = Modifier.size(48.dp).clip(CircleShape)

    if (imageUrl.isNullOrBlank()) {
        RobohashAsyncImage(
            robot = definition?.id ?: "appnotfound",
            contentDescription = null,
            modifier = logoModifier,
            loadRobohash = true,
        )
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = logoModifier,
            contentScale = ContentScale.Crop,
        )
    }
}
