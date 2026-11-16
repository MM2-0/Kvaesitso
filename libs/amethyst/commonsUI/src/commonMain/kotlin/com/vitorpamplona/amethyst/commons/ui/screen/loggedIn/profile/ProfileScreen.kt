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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.profile.apps.dal.UserProfileAppsFeedViewModel
import com.vitorpamplona.amethyst.commons.profile.bookmarks.dal.UserProfileBookmarksFeedViewModel
import com.vitorpamplona.amethyst.commons.profile.conversations.dal.UserProfileConversationsFeedViewModel
import com.vitorpamplona.amethyst.commons.profile.followers.dal.UserProfileFollowersUserFeedViewModel
import com.vitorpamplona.amethyst.commons.profile.follows.dal.UserProfileFollowsUserFeedViewModel
import com.vitorpamplona.amethyst.commons.profile.gallery.dal.UserProfileGalleryFeedViewModel
import com.vitorpamplona.amethyst.commons.profile.gitrepos.dal.UserProfileGitReposFeedViewModel
import com.vitorpamplona.amethyst.commons.profile.header.apps.UserAppRecommendationsFeedViewModel
import com.vitorpamplona.amethyst.commons.profile.mutual.dal.UserProfileMutualFeedViewModel
import com.vitorpamplona.amethyst.commons.profile.newthreads.dal.UserProfileNewThreadsFeedViewModel
import com.vitorpamplona.amethyst.commons.profile.pinnedNotes.dal.UserProfilePinnedNotesFeedViewModel
import com.vitorpamplona.amethyst.commons.profile.reports.dal.UserProfileReportFeedViewModel
import com.vitorpamplona.amethyst.commons.profile.ui.RelaysTabHeader
import com.vitorpamplona.amethyst.commons.profile.zaps.dal.UserProfileZapsViewModel
import com.vitorpamplona.amethyst.commons.relayClient.profile.UserProfileFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.observeAccountIsHiddenUser
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.gallery
import com.vitorpamplona.amethyst.commons.resources.mutual
import com.vitorpamplona.amethyst.commons.resources.notes
import com.vitorpamplona.amethyst.commons.resources.profile_tab_apps
import com.vitorpamplona.amethyst.commons.resources.profile_tab_repos
import com.vitorpamplona.amethyst.commons.resources.replies
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.BackToTopButton
import com.vitorpamplona.amethyst.commons.ui.feeds.BackToTopState
import com.vitorpamplona.amethyst.commons.ui.feeds.LocalBackToTop
import com.vitorpamplona.amethyst.commons.ui.feeds.UserBlockedFeed
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.apps.TabApps
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.bookmarks.BookmarkTabHeader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.bookmarks.TabBookmarks
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.conversations.TabNotesConversations
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.followers.FollowersTabHeader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.followers.TabFollowers
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.follows.FollowTabHeader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.follows.TabFollows
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.gallery.TabGallery
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.gitrepos.TabGitRepos
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.hashtags.FollowedTagsTabHeader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.hashtags.TabFollowedTags
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.ProfileHeader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.ProfileTopBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.mutual.TabMutualConversations
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.newthreads.TabNotesNewThreads
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.relays.TabRelays
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.reports.ReportsTabHeader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.reports.TabReports
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.zaps.TabReceivedZaps
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.zaps.ZapTabHeader
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Size8dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.UserExternalIdentitiesViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    userId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (userId == null) return

    var userBase by remember { mutableStateOf(LocalCache.getUserIfExists(userId)) }

    if (userBase == null) {
        LaunchedEffect(userId) {
            val newUserBase = LocalCache.checkGetOrCreateUser(userId)
            if (newUserBase != userBase) {
                userBase = newUserBase
            }
        }
    }

    userBase?.let {
        PrepareViewModels(
            baseUser = it,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun PrepareViewModels(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val followsFeedViewModel: UserProfileFollowsUserFeedViewModel =
        rememberViewModel(
            key = baseUser.pubkeyHex + "UserProfileFollowsUserFeedViewModel",
            factory =
                UserProfileFollowsUserFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val galleryFeedViewModel: UserProfileGalleryFeedViewModel =
        rememberViewModel(
            key = baseUser.pubkeyHex + "UserGalleryFeedViewModel",
            factory =
                UserProfileGalleryFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val gitReposFeedViewModel: UserProfileGitReposFeedViewModel =
        rememberViewModel(
            key = baseUser.pubkeyHex + "UserProfileGitReposFeedViewModel",
            factory =
                UserProfileGitReposFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val followersFeedViewModel: UserProfileFollowersUserFeedViewModel =
        rememberViewModel(
            key = baseUser.pubkeyHex + "UserProfileFollowersUserFeedViewModel",
            factory =
                UserProfileFollowersUserFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val appRecommendations: UserAppRecommendationsFeedViewModel =
        rememberViewModel(
            key = baseUser.pubkeyHex + "UserAppRecommendationsFeedViewModel",
            factory = UserAppRecommendationsFeedViewModel.Factory(baseUser),
        )

    val externalIdentities: UserExternalIdentitiesViewModel =
        rememberViewModel(
            key = baseUser.pubkeyHex + "UserExternalIdentitiesViewModel",
            factory = UserExternalIdentitiesViewModel.Factory(baseUser),
        )

    val zapFeedViewModel: UserProfileZapsViewModel =
        rememberViewModel(
            key = baseUser.pubkeyHex + "UserProfileZapsFeedViewModel",
            factory =
                UserProfileZapsViewModel.Factory(baseUser, accountViewModel.account),
        )

    val threadsViewModel: UserProfileNewThreadsFeedViewModel =
        rememberViewModel(
            key = baseUser.pubkeyHex + "UserProfileNewThreadsFeedViewModel",
            factory =
                UserProfileNewThreadsFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val repliesViewModel: UserProfileConversationsFeedViewModel =
        rememberViewModel(
            key = baseUser.pubkeyHex + "UserProfileConversationsFeedViewModel",
            factory =
                UserProfileConversationsFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val mutualViewModel: UserProfileMutualFeedViewModel =
        rememberViewModel(
            key = baseUser.pubkeyHex + "UserProfileMutualFeedViewModel",
            factory =
                UserProfileMutualFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val bookmarksFeedViewModel: UserProfileBookmarksFeedViewModel =
        rememberViewModel(
            key = baseUser.pubkeyHex + "UserProfileBookmarksFeedViewModel",
            factory =
                UserProfileBookmarksFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val pinnedNotesFeedViewModel: UserProfilePinnedNotesFeedViewModel =
        rememberViewModel(
            key = baseUser.pubkeyHex + "UserProfilePinnedNotesFeedViewModel",
            factory =
                UserProfilePinnedNotesFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val reportsFeedViewModel: UserProfileReportFeedViewModel =
        rememberViewModel(
            key = baseUser.pubkeyHex + "UserProfileReportFeedViewModel",
            factory =
                UserProfileReportFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val appsFeedViewModel: UserProfileAppsFeedViewModel =
        rememberViewModel(
            key = baseUser.pubkeyHex + "UserProfileAppsFeedViewModel",
            factory =
                UserProfileAppsFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    ProfileScreen(
        baseUser = baseUser,
        threadsViewModel,
        repliesViewModel,
        mutualViewModel,
        followsFeedViewModel,
        followersFeedViewModel,
        appRecommendations,
        externalIdentities,
        zapFeedViewModel,
        bookmarksFeedViewModel,
        pinnedNotesFeedViewModel,
        galleryFeedViewModel,
        gitReposFeedViewModel,
        reportsFeedViewModel,
        appsFeedViewModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun ProfileScreen(
    baseUser: User,
    threadsViewModel: UserProfileNewThreadsFeedViewModel,
    repliesViewModel: UserProfileConversationsFeedViewModel,
    mutualViewModel: UserProfileMutualFeedViewModel,
    followsFeedViewModel: UserProfileFollowsUserFeedViewModel,
    followersFeedViewModel: UserProfileFollowersUserFeedViewModel,
    appRecommendations: UserAppRecommendationsFeedViewModel,
    externalIdentities: UserExternalIdentitiesViewModel,
    zapFeedViewModel: UserProfileZapsViewModel,
    bookmarksFeedViewModel: UserProfileBookmarksFeedViewModel,
    pinnedNotesFeedViewModel: UserProfilePinnedNotesFeedViewModel,
    galleryFeedViewModel: UserProfileGalleryFeedViewModel,
    gitReposFeedViewModel: UserProfileGitReposFeedViewModel,
    reportsFeedViewModel: UserProfileReportFeedViewModel,
    appsFeedViewModel: UserProfileAppsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val ui = accountViewModel.settings.uiSettingsFlow
    val showAppRecommendations by ui.showProfileAppRecommendations.collectAsStateWithLifecycle()
    val showFollowersFeed by ui.showProfileFollowersFeed.collectAsStateWithLifecycle()
    val showZapReceivedFeed by ui.showProfileZapReceivedFeed.collectAsStateWithLifecycle()

    WatchLifecycleAndUpdateModel(threadsViewModel)
    WatchLifecycleAndUpdateModel(repliesViewModel)
    WatchLifecycleAndUpdateModel(mutualViewModel)
    if (showAppRecommendations) {
        WatchLifecycleAndUpdateModel(appRecommendations)
    }
    WatchLifecycleAndUpdateModel(bookmarksFeedViewModel)
    WatchLifecycleAndUpdateModel(pinnedNotesFeedViewModel)
    WatchLifecycleAndUpdateModel(galleryFeedViewModel)
    WatchLifecycleAndUpdateModel(gitReposFeedViewModel)
    WatchLifecycleAndUpdateModel(appsFeedViewModel)

    UserProfileFilterAssemblerSubscription(
        user = baseUser,
        loadFollowers = showFollowersFeed,
        loadZapsReceived = showZapReceivedFeed,
        assembler = accountViewModel.dataSources().profile,
    )

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // The tabs' feeds report here. Going back to the top also brings the profile header back.
    val backToTop = remember { BackToTopState { coroutineScope.launch { scrollState.animateScrollTo(0) } } }

    Scaffold(
        topBar = {
            ProfileTopBar(baseUser, accountViewModel, nav)
        },
        // Status-bar handling is done inside RenderSurface, but the bottom inset must stay:
        // when AppBottomBar renders nothing (pushed entries on phones, all large-screen
        // tiers) this inset is the only thing keeping content clear of the system nav bar.
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            AppBottomBar(
                Route.Profile(accountViewModel.userProfile().pubkeyHex),
                nav,
                accountViewModel,
            ) { route ->
                if (route is Route.Profile) {
                    coroutineScope.launch { scrollState.animateScrollTo(0) }
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingActionButton = { BackToTopButton(backToTop) },
    ) { padding ->
        Box(modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            RenderSurface(scrollState) { tabRowModifier: Modifier, pagerModifier: Modifier ->
                CompositionLocalProvider(LocalBackToTop provides backToTop) {
                    RenderScreen(
                        baseUser,
                        tabRowModifier,
                        pagerModifier,
                        threadsViewModel,
                        repliesViewModel,
                        mutualViewModel,
                        appRecommendations,
                        externalIdentities,
                        followsFeedViewModel,
                        followersFeedViewModel,
                        zapFeedViewModel,
                        bookmarksFeedViewModel,
                        pinnedNotesFeedViewModel,
                        galleryFeedViewModel,
                        gitReposFeedViewModel,
                        reportsFeedViewModel,
                        appsFeedViewModel,
                        accountViewModel,
                        nav,
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderSurface(
    scrollState: ScrollState,
    content: @Composable (tabRowModifier: Modifier, pagerModifier: Modifier) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        var columnSize by remember { mutableStateOf(IntSize.Zero) }
        var tabsSize by remember { mutableStateOf(IntSize.Zero) }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { columnSize = it },
        ) {
            val coroutineScope = rememberCoroutineScope()

            val tabRowModifier = remember { Modifier.onSizeChanged { tabsSize = it } }

            val pagerModifier =
                with(LocalDensity.current) { Modifier.height((columnSize.height - tabsSize.height).toDp()) }

            val starting =
                with(LocalDensity.current) { WindowInsets.statusBars.getTop(this) }

            Box(
                modifier =
                    remember {
                        Modifier
                            .verticalScroll(scrollState)
                            .nestedScroll(
                                object : NestedScrollConnection {
                                    override fun onPreScroll(
                                        available: Offset,
                                        source: NestedScrollSource,
                                    ): Offset {
                                        val borderLimit = scrollState.maxValue - starting
                                        val finalValue = scrollState.value - available.y

                                        return if (available.y >= 0) {
                                            Offset.Zero
                                        } else {
                                            // When scrolling vertically, scroll the container first.

                                            // if it doesn't go over the max
                                            if (finalValue < borderLimit) {
                                                coroutineScope.launch { scrollState.scrollBy(-available.y) }
                                                Offset(0f, available.y)
                                            } else {
                                                // if it's already over the max
                                                if (scrollState.value >= borderLimit) {
                                                    Offset.Zero
                                                } else {
                                                    // move to the max
                                                    val newY = (borderLimit - scrollState.value).toFloat()
                                                    coroutineScope.launch { scrollState.scrollBy(newY) }
                                                    Offset(0f, -newY)
                                                }
                                            }
                                        }
                                    }
                                },
                            ).fillMaxHeight()
                    },
            ) {
                content(tabRowModifier, pagerModifier)
            }
        }
    }
}

@Composable
private fun RenderScreen(
    baseUser: User,
    tabRowModifier: Modifier,
    pagerModifier: Modifier,
    threadsViewModel: UserProfileNewThreadsFeedViewModel,
    repliesViewModel: UserProfileConversationsFeedViewModel,
    mutualViewModel: UserProfileMutualFeedViewModel,
    appRecommendations: UserAppRecommendationsFeedViewModel,
    externalIdentities: UserExternalIdentitiesViewModel,
    followsFeedViewModel: UserProfileFollowsUserFeedViewModel,
    followersFeedViewModel: UserProfileFollowersUserFeedViewModel,
    zapFeedViewModel: UserProfileZapsViewModel,
    bookmarksFeedViewModel: UserProfileBookmarksFeedViewModel,
    pinnedNotesFeedViewModel: UserProfilePinnedNotesFeedViewModel,
    galleryFeedViewModel: UserProfileGalleryFeedViewModel,
    gitReposFeedViewModel: UserProfileGitReposFeedViewModel,
    reportsFeedViewModel: UserProfileReportFeedViewModel,
    appsFeedViewModel: UserProfileAppsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val ui = accountViewModel.settings.uiSettingsFlow
    val showFollowersTab by ui.showProfileFollowersFeed.collectAsStateWithLifecycle()
    val showZapsTab by ui.showProfileZapReceivedFeed.collectAsStateWithLifecycle()

    // Most profiles have no repos: the tab only appears once the feed confirms any exist.
    val gitReposFeedState by gitReposFeedViewModel.feedState.feedContent.collectAsStateWithLifecycle()
    val loadedGitRepos = (gitReposFeedState as? FeedState.Loaded)?.feed?.collectAsStateWithLifecycle()?.value
    val showGitReposTab = !loadedGitRepos?.list.isNullOrEmpty()

    val visibleTabs =
        remember(showFollowersTab, showZapsTab, showGitReposTab) {
            buildList {
                add(ProfileTab.Notes)
                add(ProfileTab.Replies)
                add(ProfileTab.Mutual)
                add(ProfileTab.Gallery)
                add(ProfileTab.Apps)
                if (showGitReposTab) add(ProfileTab.GitRepos)
                add(ProfileTab.Follows)
                if (showFollowersTab) add(ProfileTab.Followers)
                if (showZapsTab) add(ProfileTab.Zaps)
                add(ProfileTab.Bookmarks)
                add(ProfileTab.FollowedTags)
                add(ProfileTab.Reports)
                add(ProfileTab.Relays)
            }
        }

    // One pager for the screen's lifetime. Tabs come and go (settings toggles, the Repos tab
    // once the profile's repositories arrive), so pages are keyed by tab: the pager stays on the
    // tab the user is reading when one is inserted or removed before it, and neither the header
    // nor the pages' scroll positions are rebuilt.
    val pagerState = rememberPagerState(pageCount = { visibleTabs.size })

    Column {
        ProfileHeader(baseUser, appRecommendations, externalIdentities, nav, accountViewModel)
        SecondaryScrollableTabRow(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            selectedTabIndex = pagerState.currentPage,
            edgePadding = Size8dp,
            modifier = tabRowModifier,
            divider = { HorizontalDivider(thickness = DividerThickness) },
        ) {
            CreateAndRenderTabs(
                baseUser,
                pagerState,
                visibleTabs,
                threadsViewModel,
                repliesViewModel,
                mutualViewModel,
                followsFeedViewModel,
                followersFeedViewModel,
                zapFeedViewModel,
                bookmarksFeedViewModel,
                galleryFeedViewModel,
                reportsFeedViewModel,
                accountViewModel,
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = pagerModifier,
            key = { page -> visibleTabs[page].name },
        ) { page ->
            CreateAndRenderPages(
                visibleTabs[page],
                baseUser,
                threadsViewModel,
                repliesViewModel,
                mutualViewModel,
                followsFeedViewModel,
                followersFeedViewModel,
                zapFeedViewModel,
                bookmarksFeedViewModel,
                pinnedNotesFeedViewModel,
                galleryFeedViewModel,
                gitReposFeedViewModel,
                reportsFeedViewModel,
                appsFeedViewModel,
                accountViewModel,
                nav,
            )
        }
    }
}

private enum class ProfileTab {
    Notes,
    Replies,
    Mutual,
    Gallery,
    Apps,
    GitRepos,
    Follows,
    Followers,
    Zaps,
    Bookmarks,
    FollowedTags,
    Reports,
    Relays,
}

@Composable
private fun CreateAndRenderPages(
    tab: ProfileTab,
    baseUser: User,
    threadsViewModel: UserProfileNewThreadsFeedViewModel,
    repliesViewModel: UserProfileConversationsFeedViewModel,
    mutualViewModel: UserProfileMutualFeedViewModel,
    followsFeedViewModel: UserProfileFollowsUserFeedViewModel,
    followersFeedViewModel: UserProfileFollowersUserFeedViewModel,
    zapFeedViewModel: UserProfileZapsViewModel,
    bookmarksFeedViewModel: UserProfileBookmarksFeedViewModel,
    pinnedNotesFeedViewModel: UserProfilePinnedNotesFeedViewModel,
    galleryFeedViewModel: UserProfileGalleryFeedViewModel,
    gitReposFeedViewModel: UserProfileGitReposFeedViewModel,
    reportsFeedViewModel: UserProfileReportFeedViewModel,
    appsFeedViewModel: UserProfileAppsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    UpdateThreadsAndRepliesWhenBlockUnblock(
        baseUser,
        threadsViewModel,
        repliesViewModel,
        accountViewModel,
    )

    val isHidden by observeAccountIsHiddenUser(accountViewModel.account, baseUser)

    when (tab) {
        ProfileTab.Notes ->
            if (isHidden) {
                UserBlockedFeed { accountViewModel.show(baseUser) }
            } else {
                TabNotesNewThreads(threadsViewModel, pinnedNotesFeedViewModel, accountViewModel, nav)
            }
        ProfileTab.Replies ->
            if (isHidden) {
                UserBlockedFeed { accountViewModel.show(baseUser) }
            } else {
                TabNotesConversations(repliesViewModel, accountViewModel, nav)
            }
        ProfileTab.Mutual -> TabMutualConversations(mutualViewModel, accountViewModel, nav)
        ProfileTab.Gallery -> TabGallery(galleryFeedViewModel, accountViewModel, nav)
        ProfileTab.Apps -> TabApps(appsFeedViewModel, accountViewModel, nav)
        ProfileTab.GitRepos -> TabGitRepos(gitReposFeedViewModel, accountViewModel, nav)
        ProfileTab.Follows -> TabFollows(followsFeedViewModel, accountViewModel, nav)
        ProfileTab.Followers -> TabFollowers(followersFeedViewModel, accountViewModel, nav)
        ProfileTab.Zaps -> TabReceivedZaps(baseUser, zapFeedViewModel, accountViewModel, nav)
        ProfileTab.Bookmarks -> TabBookmarks(bookmarksFeedViewModel, accountViewModel, nav)
        ProfileTab.FollowedTags -> TabFollowedTags(baseUser, accountViewModel, nav)
        ProfileTab.Reports -> TabReports(baseUser, reportsFeedViewModel, accountViewModel, nav)
        ProfileTab.Relays -> TabRelays(baseUser, accountViewModel, nav)
    }
}

@Composable
fun UpdateThreadsAndRepliesWhenBlockUnblock(
    baseUser: User,
    threadsViewModel: UserProfileNewThreadsFeedViewModel,
    repliesViewModel: UserProfileConversationsFeedViewModel,
    accountViewModel: AccountViewModel,
) {
    val isHidden by observeAccountIsHiddenUser(accountViewModel.account, baseUser)

    LaunchedEffect(key1 = isHidden) {
        threadsViewModel.invalidateData()
        repliesViewModel.invalidateData()
    }
}

@Composable
private fun CreateAndRenderTabs(
    baseUser: User,
    pagerState: PagerState,
    visibleTabs: List<ProfileTab>,
    threadsViewModel: UserProfileNewThreadsFeedViewModel,
    repliesViewModel: UserProfileConversationsFeedViewModel,
    mutualViewModel: UserProfileMutualFeedViewModel,
    followsFeedViewModel: UserProfileFollowsUserFeedViewModel,
    followersFeedViewModel: UserProfileFollowersUserFeedViewModel,
    zapFeedViewModel: UserProfileZapsViewModel,
    bookmarksFeedViewModel: UserProfileBookmarksFeedViewModel,
    galleryFeedViewModel: UserProfileGalleryFeedViewModel,
    reportsFeedViewModel: UserProfileReportFeedViewModel,
    accountViewModel: AccountViewModel,
) {
    val coroutineScope = rememberCoroutineScope()

    visibleTabs.forEachIndexed { index, tab ->
        Tab(
            selected = pagerState.currentPage == index,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
            text = {
                when (tab) {
                    ProfileTab.Notes -> Text(text = stringRes(Res.string.notes))
                    ProfileTab.Replies -> Text(text = stringRes(Res.string.replies))
                    ProfileTab.Mutual -> Text(text = stringRes(Res.string.mutual))
                    ProfileTab.Gallery -> Text(text = stringRes(Res.string.gallery))
                    ProfileTab.Apps -> Text(text = stringRes(Res.string.profile_tab_apps))
                    ProfileTab.GitRepos -> Text(text = stringRes(Res.string.profile_tab_repos))
                    ProfileTab.Follows -> FollowTabHeader(followsFeedViewModel, accountViewModel)
                    ProfileTab.Followers -> FollowersTabHeader(baseUser, followersFeedViewModel, accountViewModel)
                    ProfileTab.Zaps -> ZapTabHeader(zapFeedViewModel, accountViewModel)
                    ProfileTab.Bookmarks -> BookmarkTabHeader(baseUser, accountViewModel)
                    ProfileTab.FollowedTags -> FollowedTagsTabHeader(baseUser, accountViewModel)
                    ProfileTab.Reports -> ReportsTabHeader(baseUser, reportsFeedViewModel, accountViewModel)
                    ProfileTab.Relays -> RelaysTabHeader()
                }
            },
        )
    }
}
