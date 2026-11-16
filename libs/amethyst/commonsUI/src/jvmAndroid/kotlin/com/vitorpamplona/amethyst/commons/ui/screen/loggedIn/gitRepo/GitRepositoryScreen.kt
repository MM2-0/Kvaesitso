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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.gitRepo.dal.RepositoryIssuesFeedViewModel
import com.vitorpamplona.amethyst.commons.gitRepo.dal.RepositoryPatchesFeedViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip34Git.GitBrowseState
import com.vitorpamplona.amethyst.commons.nip34Git.GitPrivateRepositoryAuth
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepoSnapshotCache
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepositoryBrowserViewModel
import com.vitorpamplona.amethyst.commons.nip34Git.status.GitStatuses
import com.vitorpamplona.amethyst.commons.nip34Git.title.GitItemTitles
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitShape
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitCard
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitRepositoryActivity
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitSignedState
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.gitRepo.LoadGitRepositoryAnnouncements
import com.vitorpamplona.amethyst.commons.relayClient.gitRepo.RepositoryFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_issue_search
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_button
import com.vitorpamplona.amethyst.commons.resources.git_pr_new_title
import com.vitorpamplona.amethyst.commons.resources.git_repo_filter_closed
import com.vitorpamplona.amethyst.commons.resources.git_repo_filter_open
import com.vitorpamplona.amethyst.commons.resources.git_repo_follow
import com.vitorpamplona.amethyst.commons.resources.git_repo_label_all
import com.vitorpamplona.amethyst.commons.resources.git_repo_labels
import com.vitorpamplona.amethyst.commons.resources.git_repo_readme
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_title
import com.vitorpamplona.amethyst.commons.resources.git_repo_tab_code
import com.vitorpamplona.amethyst.commons.resources.git_repo_tab_issues
import com.vitorpamplona.amethyst.commons.resources.git_repo_tab_patches
import com.vitorpamplona.amethyst.commons.resources.git_status_draft
import com.vitorpamplona.amethyst.commons.resources.loading_feed
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TitleIconModifier
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.commons.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.GitItemFeedLoaded
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.GitRepoSettingsDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoActivityPulse
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoExternalNotice
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoHero
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoLanguageBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoLastCommit
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoPushFirstCommit
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoSocialRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoStatTiles
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.RepoTitleBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.code.GitCodeTab
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.code.GitReadmeSection
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.computeLanguageBreakdown
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.gitLabelsOf
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.repoHasFetchableClone
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.FeedViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.git.GitHttpAuth
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import org.jetbrains.compose.resources.StringResource
import kotlin.reflect.KClass

// ---------------------------------------------------------------------------
// Project Home + drill-in screens.
//
// The repository is presented as a scrollable "project home" (facts + README +
// navigation cards) rather than a tab bar. Code, Issues and Pull Requests are
// dedicated screens, each owning its own disappearing top bar — so per-section
// headers (branch/search selectors, status filters) track the bar correctly and
// heavy content (the code browser, the feeds) only loads when navigated to.
// ---------------------------------------------------------------------------

@Composable
fun GitRepositoryScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { note ->
        note?.let {
            GitRepositoryHome(
                note = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun GitRepositoryCodeScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { note ->
        note?.let { GitRepositoryCode(it, accountViewModel, nav) }
    }
}

@Composable
fun GitRepositoryIssuesScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { note ->
        note?.let { GitRepositoryIssues(it, accountViewModel, nav) }
    }
}

@Composable
fun GitRepositoryPullsScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { note ->
        note?.let { GitRepositoryPulls(it, accountViewModel, nav) }
    }
}

/** Builds the [GitRepositoryBrowserViewModel] with the HTTP client its clones and fetches go through. */
class GitRepositoryBrowserViewModelFactory(
    private val okHttpClient: (String) -> OkHttpClient,
    private val auth: GitHttpAuth? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: KClass<T>,
        extras: CreationExtras,
    ): T = GitRepositoryBrowserViewModel(okHttpClient, auth) as T
}

@Composable
private fun rememberRepoBrowser(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
): GitRepositoryBrowserViewModel =
    viewModel(
        key = note.idHex + "GitRepoBrowser",
        factory = GitRepositoryBrowserViewModelFactory(accountViewModel.httpClientBuilder::okHttpClientForPreview, GitPrivateRepositoryAuth(accountViewModel.account.signer)),
    )

/**
 * Subscribes to the repository's issues/patches/status events while [event] is loaded.
 *
 * RepositoryContentSubAssembler.updateFilter reads note.event and bails out if it isn't a
 * GitRepositoryEvent yet. The compose subscription manager doesn't re-run updateFilter when
 * note.event later mutates, so subscribing before the event arrives (cold-start / deep-link)
 * leaves an empty filter forever. Gating on event presence makes the subscription composable
 * enter composition only once the repo event is loaded.
 */
@Composable
private fun RepoContentSubscription(
    note: AddressableNote,
    event: GitRepositoryEvent?,
    accountViewModel: AccountViewModel,
) {
    if (event != null) {
        RepositoryFilterAssemblerSubscription(note, event, accountViewModel.dataSources().gitRepository)
    }
    // Its co-maintainers' announcements, which its membership (and so its lists) depend on.
    LoadGitRepositoryAnnouncements(remember(note) { listOf(note.address) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitRepositoryHome(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val browserViewModel = rememberRepoBrowser(note, accountViewModel)
    val event by observeNoteEvent<GitRepositoryEvent>(note, accountViewModel)
    val cacheKey = remember(note) { note.address.toValue() }

    // Start the smart-HTTP browser as soon as the announcement arrives, so the README renders.
    LaunchedEffect(event) {
        event?.let { browserViewModel.loadOnce(it.clones(), cacheKey) }
    }
    // The maintainers' signed tips decide which server's code is shown.
    val signedState = observeGitSignedState(note.address, accountViewModel)
    LaunchedEffect(signedState) { browserViewModel.updateSignedState(signedState) }
    val browserState by browserViewModel.state.collectAsStateWithLifecycle()

    RepoContentSubscription(note, event, accountViewModel)

    // Feed view models power the issue/PR counts on the nav cards and the recent-activity pulse.
    val openIssues: RepositoryIssuesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoIssuesOpen",
            factory = RepositoryIssuesFeedViewModel.Factory(note, accountViewModel.account, showClosed = false),
        )
    val closedIssues: RepositoryIssuesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoIssuesClosed",
            factory = RepositoryIssuesFeedViewModel.Factory(note, accountViewModel.account, showClosed = true),
        )
    val openPatches: RepositoryPatchesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoPatchesOpen",
            factory = RepositoryPatchesFeedViewModel.Factory(note, accountViewModel.account, showClosed = false),
        )
    val closedPatches: RepositoryPatchesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoPatchesClosed",
            factory = RepositoryPatchesFeedViewModel.Factory(note, accountViewModel.account, showClosed = true),
        )
    WatchLifecycleAndUpdateModel(openIssues)
    WatchLifecycleAndUpdateModel(closedIssues)
    WatchLifecycleAndUpdateModel(openPatches)
    WatchLifecycleAndUpdateModel(closedPatches)

    val openIssueItems = rememberGitFeedItems(openIssues)
    val closedIssueItems = rememberGitFeedItems(closedIssues)
    val openPatchItems = rememberGitFeedItems(openPatches)
    val closedPatchItems = rememberGitFeedItems(closedPatches)

    val snapshot = (browserState as? GitBrowseState.Loaded)?.snapshot ?: remember(cacheKey) { GitRepoSnapshotCache.get(cacheKey) }
    val fileNames = remember(snapshot) { snapshot?.walkFileNames() ?: emptyList() }
    val languageSlices = remember(fileNames) { computeLanguageBreakdown(fileNames) }
    val activity =
        remember(openIssueItems, closedIssueItems, openPatchItems, closedPatchItems) {
            (openIssueItems + closedIssueItems + openPatchItems + closedPatchItems)
                .sortedByDescending { it.createdAt() ?: 0L }
                .take(6)
        }

    // Nav-card badges count only the OPEN issues/PRs, recounted when any item's state changes.
    val repositoryActivity = observeGitRepositoryActivity(note.address.dTag)
    val openIssueCount =
        remember(openIssueItems, closedIssueItems, repositoryActivity) {
            (openIssueItems + closedIssueItems).distinctBy { it.idHex }.count { !GitStatuses.isClosedOrResolved(it, LocalCache.gitAuthority) }
        }
    val openPullCount =
        remember(openPatchItems, closedPatchItems, repositoryActivity) {
            // Drafts aren't counted: the badge is what awaits review, as gitworkshop's PR tab shows it.
            (openPatchItems + closedPatchItems).distinctBy { it.idHex }.count {
                val status = GitStatuses.latest(it, LocalCache.gitAuthority)
                !GitStatuses.isClosedOrResolved(status) && status !is GitStatusDraftEvent
            }
        }

    var showSettings by rememberSaveable(note.idHex) { mutableStateOf(false) }
    val currentEventForSettings = event
    if (showSettings && currentEventForSettings != null) {
        GitRepoSettingsDialog(currentEventForSettings, signedState, accountViewModel) { showSettings = false }
    }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            ShorterTopAppBar(
                title = { RepoTitleBar(event = event, fallback = note.dTag(), accountViewModel = accountViewModel, nav = nav) },
                navigationIcon = {
                    // No arrow in a tab or a window of its own: nothing to go back to.
                    if (nav.canPop()) {
                        Row(TitleIconModifier, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = nav::popBack) { ArrowBackIcon() }
                        }
                    }
                },
                actions = {
                    val bookmarkedSet by accountViewModel.account.gitRepositoryListState.repositoryAddressSet
                        .collectAsStateWithLifecycle()
                    val isBookmarked = remember(bookmarkedSet, note) { bookmarkedSet.contains(note.address) }
                    IconButton(onClick = { accountViewModel.toggleRepositoryBookmark(note, isBookmarked) }) {
                        // Following is the kind-10018 list, which gitworkshop calls "Followed repositories".
                        Icon(
                            symbol = if (isBookmarked) MaterialSymbols.Visibility else MaterialSymbols.VisibilityOff,
                            contentDescription = stringRes(Res.string.git_repo_follow),
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (event != null && accountViewModel.isLoggedUser(event?.pubKey)) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(MaterialSymbols.Edit, contentDescription = stringRes(Res.string.git_repo_settings_title))
                        }
                    }
                    Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        MoreOptionsButton(note, accountViewModel = accountViewModel, nav = nav)
                    }
                },
            )
        },
        accountViewModel = accountViewModel,
    ) {
        val scaffoldPadding = LocalDisappearingScaffoldPadding.current
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(scaffoldPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val currentEvent = event
            if (currentEvent != null) {
                RepoHero(currentEvent, accountViewModel, nav)
            }

            if (snapshot != null) {
                RepoStatTiles(
                    branches = snapshot.branches.size,
                    tags = snapshot.tags.size,
                    files = fileNames.size,
                    updatedEpochSec = snapshot.tipCommit?.authorTimeSec,
                )
                if (languageSlices.isNotEmpty()) {
                    RepoLanguageBar(languageSlices)
                }
                snapshot.tipCommit?.let { commit ->
                    RepoLastCommit(commit) { nav.nav(Route.GitRepositoryCode(note.address)) }
                }
            } else if (currentEvent != null && !repoHasFetchableClone(currentEvent)) {
                RepoExternalNotice(currentEvent)
            } else if (currentEvent != null && (browserState as? GitBrowseState.Error)?.isEmptyRepository == true && accountViewModel.isLoggedUser(currentEvent.pubKey)) {
                RepoPushFirstCommit(currentEvent)
            }

            RepoSections(note, openIssueCount, openPullCount, nav)

            RepoActivityPulse(activity, accountViewModel, nav)

            if (currentEvent != null) {
                RepoReadmeCard {
                    GitReadmeSection(browserState, browserViewModel, currentEvent, accountViewModel, nav)
                }
                RepoSocialRow(note, accountViewModel, nav)
            } else {
                EmptyMessage(stringRes(Res.string.loading_feed))
            }
        }
    }
}

@Composable
private fun GitRepositoryCode(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val browserViewModel = rememberRepoBrowser(note, accountViewModel)
    val event by observeNoteEvent<GitRepositoryEvent>(note, accountViewModel)
    val cacheKey = remember(note) { note.address.toValue() }
    LaunchedEffect(event) {
        event?.let { browserViewModel.loadOnce(it.clones(), cacheKey) }
    }
    // The maintainers' signed tips decide which server's code is shown.
    val signedState = observeGitSignedState(note.address, accountViewModel)
    LaunchedEffect(signedState) { browserViewModel.updateSignedState(signedState) }
    val browserState by browserViewModel.state.collectAsStateWithLifecycle()
    RepoContentSubscription(note, event, accountViewModel)

    GitRepoSubScreenScaffold(event, note.dTag(), stringRes(Res.string.git_repo_tab_code), accountViewModel, nav) {
        val ev = event
        if (ev != null && !repoHasFetchableClone(ev)) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(LocalDisappearingScaffoldPadding.current)
                        .padding(12.dp),
            ) {
                RepoExternalNotice(ev)
            }
        } else {
            GitCodeTab(browserState, browserViewModel, note, accountViewModel, nav)
        }
    }
}

@Composable
private fun GitRepositoryIssues(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val openViewModel: RepositoryIssuesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoIssuesOpen",
            factory = RepositoryIssuesFeedViewModel.Factory(note, accountViewModel.account, showClosed = false),
        )
    val closedViewModel: RepositoryIssuesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoIssuesClosed",
            factory = RepositoryIssuesFeedViewModel.Factory(note, accountViewModel.account, showClosed = true),
        )
    WatchLifecycleAndUpdateModel(openViewModel)
    WatchLifecycleAndUpdateModel(closedViewModel)

    val event by observeNoteEvent<GitRepositoryEvent>(note, accountViewModel)
    RepoContentSubscription(note, event, accountViewModel)

    StatusFeedScreen(
        persistKey = note.idHex + "GitRepoIssuesStatus",
        event = event,
        fallbackTitle = note.dTag(),
        section = stringRes(Res.string.git_repo_tab_issues),
        openViewModel = openViewModel,
        closedViewModel = closedViewModel,
        accountViewModel = accountViewModel,
        nav = nav,
        floatingButton = if (event != null) ({ NewIssueFab { nav.nav(Route.GitRepositoryNewIssue(note.address)) } }) else null,
    )
}

@Composable
private fun GitRepositoryPulls(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val openViewModel: RepositoryPatchesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoPatchesOpen",
            factory = RepositoryPatchesFeedViewModel.Factory(note, accountViewModel.account, showClosed = false),
        )
    val closedViewModel: RepositoryPatchesFeedViewModel =
        viewModel(
            key = note.idHex + "GitRepoPatchesClosed",
            factory = RepositoryPatchesFeedViewModel.Factory(note, accountViewModel.account, showClosed = true),
        )
    WatchLifecycleAndUpdateModel(openViewModel)
    WatchLifecycleAndUpdateModel(closedViewModel)

    val event by observeNoteEvent<GitRepositoryEvent>(note, accountViewModel)
    RepoContentSubscription(note, event, accountViewModel)

    StatusFeedScreen(
        persistKey = note.idHex + "GitRepoPatchesStatus",
        event = event,
        fallbackTitle = note.dTag(),
        section = stringRes(Res.string.git_repo_tab_patches),
        openViewModel = openViewModel,
        closedViewModel = closedViewModel,
        accountViewModel = accountViewModel,
        nav = nav,
        floatingButton =
            if (event != null && accountViewModel.account.isWriteable()) {
                { NewIssueFab(Res.string.git_pr_new_title) { nav.nav(Route.GitRepositoryNewPullRequest(note.address)) } }
            } else {
                null
            },
    )
}

/**
 * Shared scaffold for the Code / Issues / Pull Requests drill-in screens: a back arrow and the
 * repo title. [belowBar] (e.g. status-filter chips) is rendered inside the disappearing top bar
 * so it hides with it instead of leaving a static band, and [floatingButton] feeds the scaffold's
 * FAB slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitRepoSubScreenScaffold(
    event: GitRepositoryEvent?,
    fallbackTitle: String,
    section: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
    belowBar: (@Composable () -> Unit)? = null,
    floatingButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                ShorterTopAppBar(
                    title = { TopBarTitle(event = event, fallback = fallbackTitle, section = section) },
                    navigationIcon = {
                        // No arrow in a tab or a window of its own: nothing to go back to.
                        if (nav.canPop()) {
                            Row(TitleIconModifier, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = nav::popBack) { ArrowBackIcon() }
                            }
                        }
                    },
                )
                belowBar?.invoke()
            }
        },
        floatingButton = floatingButton,
        accountViewModel = accountViewModel,
    ) {
        content()
    }
}

/**
 * The Code / Issues / Pull Requests entry points on the project home: one bordered bar of three
 * sections, each with its open count, as a forge's tab strip.
 */
@Composable
private fun RepoSections(
    note: AddressableNote,
    openIssues: Int,
    openPulls: Int,
    nav: INav,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).gitCard(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Code has no count: it takes its own width and leaves the rest to the two lists.
        RepoSection(MaterialSymbols.Code, stringRes(Res.string.git_repo_tab_code), null, grow = false) {
            nav.nav(Route.GitRepositoryCode(note.address))
        }
        VerticalDivider(thickness = DividerThickness, color = MaterialTheme.colorScheme.subtleBorder)
        RepoSection(MaterialSymbols.RadioButtonChecked, stringRes(Res.string.git_repo_tab_issues), openIssues) {
            nav.nav(Route.GitRepositoryIssues(note.address))
        }
        VerticalDivider(thickness = DividerThickness, color = MaterialTheme.colorScheme.subtleBorder)
        RepoSection(MaterialSymbols.CallMerge, stringRes(Res.string.git_repo_tab_patches), openPulls) {
            nav.nav(Route.GitRepositoryPulls(note.address))
        }
    }
}

@Composable
private fun RowScope.RepoSection(
    symbol: MaterialSymbol,
    title: String,
    count: Int?,
    grow: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .let { if (grow) it.weight(1f) else it }
                .fillMaxHeight()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
    ) {
        Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.grayText)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (count != null && count > 0) {
            GitCount(count)
        }
    }
}

/** A count beside a section name, quieter than the name. */
@Composable
private fun GitCount(count: Int) {
    Text(
        text = if (count > 999) "999+" else count.toString(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.grayText,
        maxLines = 1,
    )
}

/** The README, in a bordered panel with its file name as the header, as forges show it. */
@Composable
private fun RepoReadmeCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().gitCard()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(symbol = MaterialSymbols.Description, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.grayText)
            Text(text = stringRes(Res.string.git_repo_readme), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(thickness = DividerThickness, color = MaterialTheme.colorScheme.subtleBorder)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            content()
        }
    }
}

/**
 * A drill-in screen showing an Open / Closed &amp; Resolved status feed. The status + label
 * filter chips live inside the disappearing top bar (so they hide with it and the feed scrolls
 * cleanly under them), while the feed body uses the scaffold's normal content padding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusFeedScreen(
    persistKey: String,
    event: GitRepositoryEvent?,
    fallbackTitle: String,
    section: String,
    openViewModel: FeedViewModel,
    closedViewModel: FeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    floatingButton: (@Composable () -> Unit)? = null,
) {
    var bucket by rememberSaveable(persistKey) { mutableStateOf(StatusBucket.OPEN) }
    var selectedLabel by rememberSaveable(persistKey) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(persistKey) { mutableStateOf("") }

    val openFeedItems = rememberGitFeedItems(openViewModel)
    val closedItems = rememberGitFeedItems(closedViewModel)

    // The open feed holds drafts too (they aren't closed); split them out as gitworkshop does, so
    // "Open" counts what is ready for review and "Draft" what isn't.
    val repositoryActivity = observeGitRepositoryActivity(event?.dTag() ?: persistKey)
    val isDraft: (Note) -> Boolean = remember(repositoryActivity) { { note -> GitStatuses.latest(note, LocalCache.gitAuthority) is GitStatusDraftEvent } }
    val draftItems = remember(openFeedItems, isDraft) { openFeedItems.filter(isDraft) }
    val openItems = remember(openFeedItems, isDraft) { openFeedItems.filterNot(isDraft) }

    val activeItems =
        when (bucket) {
            StatusBucket.OPEN -> openItems
            StatusBucket.DRAFT -> draftItems
            StatusBucket.CLOSED -> closedItems
        }
    val labels =
        remember(activeItems) {
            activeItems.flatMap { gitLabelsOf(it) }.distinct().sorted()
        }

    // A label selected under one status may not exist under the other; drop it when it's gone.
    LaunchedEffect(labels) {
        if (selectedLabel != null && selectedLabel !in labels) selectedLabel = null
    }

    GitRepoSubScreenScaffold(
        event = event,
        fallbackTitle = fallbackTitle,
        section = section,
        accountViewModel = accountViewModel,
        nav = nav,
        belowBar = {
            StatusFilterChips(
                bucket = bucket,
                onBucket = { bucket = it },
                openCount = openItems.size,
                draftCount = draftItems.size,
                closedCount = closedItems.size,
                selectedLabel = selectedLabel,
                onSelectLabel = { selectedLabel = it },
                labels = labels,
                query = query,
                onQuery = { query = it },
            )
        },
        floatingButton = floatingButton,
    ) {
        RefresheableFeedView(
            viewModel = if (bucket == StatusBucket.CLOSED) closedViewModel else openViewModel,
            routeForLastRead = null,
            accountViewModel = accountViewModel,
            nav = nav,
            onLoaded = { loaded, listState ->
                val statusFilter: ((Note) -> Boolean)? =
                    when (bucket) {
                        StatusBucket.OPEN -> { note -> !isDraft(note) }
                        StatusBucket.DRAFT -> isDraft
                        StatusBucket.CLOSED -> null
                    }
                val needle = query.trim().lowercase()
                val itemFilter: ((Note) -> Boolean)? =
                    if (needle.isEmpty()) {
                        statusFilter
                    } else {
                        { note -> (statusFilter?.invoke(note) ?: true) && matchesSearch(note, needle) }
                    }
                GitItemFeedLoaded(loaded, listState, accountViewModel, nav, labelFilter = selectedLabel, itemFilter = itemFilter)
            },
        )
    }
}

/** Which status a repository's issue or PR list shows. */
private enum class StatusBucket { OPEN, DRAFT, CLOSED }

/**
 * The Open / Draft / Closed toggles and the label menu, on one line inside the top bar, as a forge
 * heads its issue list. "Draft" appears only while there are drafts (issues have none).
 */
@Composable
private fun StatusFilterChips(
    bucket: StatusBucket,
    onBucket: (StatusBucket) -> Unit,
    openCount: Int,
    draftCount: Int,
    closedCount: Int,
    selectedLabel: String?,
    onSelectLabel: (String?) -> Unit,
    labels: List<String>,
    query: String,
    onQuery: (String) -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            StatusToggle(bucket == StatusBucket.OPEN, MaterialSymbols.RadioButtonChecked, openCount, stringRes(Res.string.git_repo_filter_open)) { onBucket(StatusBucket.OPEN) }
            if (draftCount > 0 || bucket == StatusBucket.DRAFT) {
                StatusToggle(bucket == StatusBucket.DRAFT, MaterialSymbols.EditNote, draftCount, stringRes(Res.string.git_status_draft)) { onBucket(StatusBucket.DRAFT) }
            }
            StatusToggle(bucket == StatusBucket.CLOSED, MaterialSymbols.Check, closedCount, stringRes(Res.string.git_repo_filter_closed)) { onBucket(StatusBucket.CLOSED) }
            Spacer(Modifier.weight(1f))
            if (labels.isNotEmpty() || selectedLabel != null) {
                LabelMenu(selectedLabel, labels, onSelectLabel)
            }
            IconButton(onClick = {
                searching = !searching
                if (!searching) onQuery("")
            }) {
                Icon(MaterialSymbols.Search, contentDescription = stringRes(Res.string.git_issue_search), modifier = Modifier.size(18.dp))
            }
        }
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text(stringRes(Res.string.git_issue_search)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider(thickness = DividerThickness)
    }
}

@Composable
private fun StatusToggle(
    selected: Boolean,
    symbol: MaterialSymbol,
    count: Int,
    label: String,
    onClick: () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.grayText
    Row(
        modifier =
            Modifier
                .clip(GitShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
        Text(
            text = if (count > 0) "$count $label" else label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
            maxLines = 1,
        )
    }
}

/** "Labels ▾": picks one label to narrow the list to, or all of them. */
@Composable
private fun LabelMenu(
    selectedLabel: String?,
    labels: List<String>,
    onSelectLabel: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier =
                Modifier
                    .clip(GitShape)
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.Label,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selectedLabel != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.grayText,
            )
            Text(
                text = selectedLabel ?: stringRes(Res.string.git_repo_labels),
                style = MaterialTheme.typography.labelLarge,
                color = if (selectedLabel != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.grayText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Icon(symbol = MaterialSymbols.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.grayText)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.git_repo_label_all)) },
                onClick = {
                    onSelectLabel(null)
                    expanded = false
                },
            )
            labels.forEach { label ->
                DropdownMenuItem(
                    text = { Text(label, fontWeight = if (label == selectedLabel) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = {
                        onSelectLabel(if (label == selectedLabel) null else label)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NewIssueFab(
    label: StringResource = Res.string.git_new_issue_button,
    onClick: () -> Unit,
) {
    // The app theme overrides shapes.large (the extended-FAB default) to 0.dp, which makes the
    // FAB square — so pin an explicit rounded shape here.
    ExtendedFloatingActionButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        icon = { Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(20.dp)) },
        text = { Text(stringRes(label)) },
    )
}

/**
 * Mirrors the active feed list out of a [FeedViewModel] so the status row can show item counts
 * and derive the available label set. Emits an empty list while the feed is loading or empty.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun rememberGitFeedItems(viewModel: FeedViewModel): List<Note> {
    val flow =
        remember(viewModel) {
            viewModel.feedState.feedContent.flatMapLatest { state ->
                if (state is FeedState.Loaded) state.feed.map { it.list } else flowOf(emptyList())
            }
        }
    val items by flow.collectAsStateWithLifecycle(emptyList())
    return items
}

/** "ngit / Issues": the project, then the section the screen is. */
@Composable
private fun TopBarTitle(
    event: GitRepositoryEvent?,
    fallback: String,
    section: String?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = event?.name() ?: fallback,
            color = if (section != null) MaterialTheme.colorScheme.grayText else Color.Unspecified,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (section != null) {
            Text(text = " / ", color = MaterialTheme.colorScheme.grayText, maxLines = 1)
            Text(text = section, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

/** An issue or PR whose title, labels or text contain [needle] (lowercase). */
private fun matchesSearch(
    note: Note,
    needle: String,
): Boolean =
    GitItemTitles.titleOf(note)?.lowercase()?.contains(needle) == true ||
        gitLabelsOf(note).any { it.contains(needle) } ||
        note.event
            ?.content
            ?.lowercase()
            ?.contains(needle) == true
