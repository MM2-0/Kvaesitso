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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.communities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.communities.dal.CommunityFeedViewModel
import com.vitorpamplona.amethyst.commons.communities.dal.CommunityModerationFeedViewModel
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.relayClient.communities.CommunityFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.feed
import com.vitorpamplona.amethyst.commons.resources.mod_queue
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TitleIconModifier
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.nip22Comments.LocalCurrentExternalScope
import com.vitorpamplona.amethyst.commons.ui.note.types.LongCommunityHeader
import com.vitorpamplona.amethyst.commons.ui.note.types.ShortCommunityActionOptions
import com.vitorpamplona.amethyst.commons.ui.note.types.ShortCommunityHeaderNoActions
import com.vitorpamplona.amethyst.commons.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlinx.coroutines.launch

@Composable
fun CommunityScreen(
    aTagHex: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(aTagHex) {
        it?.let {
            PrepareViewModelsCommunityScreen(
                note = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun PrepareViewModelsCommunityScreen(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val followsFeedViewModel: CommunityFeedViewModel =
        rememberViewModel(
            key = note.idHex + "CommunityFeedViewModel",
            factory =
                CommunityFeedViewModel.Factory(
                    note,
                    accountViewModel.account,
                ),
        )

    val modFeedViewModel: CommunityModerationFeedViewModel =
        rememberViewModel(
            key = note.idHex + "CommunityModerationFeedViewModel",
            factory =
                CommunityModerationFeedViewModel.Factory(
                    note,
                    accountViewModel.account,
                ),
        )

    CommunityScreen(note, followsFeedViewModel, modFeedViewModel, accountViewModel, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    note: AddressableNote,
    feedViewModel: CommunityFeedViewModel,
    modFeedViewModel: CommunityModerationFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedViewModel)
    WatchLifecycleAndUpdateModel(modFeedViewModel)
    CommunityFilterAssemblerSubscription(note, accountViewModel.dataSources().community)

    val pagerState = rememberForeverPagerState(note.idHex + "CommunityScreenPagerState") { 2 }
    val expanded = remember { mutableStateOf(false) }

    DisappearingScaffold(
        isInvertedLayout = false,
        isActive = { !expanded.value },
        topBar = {
            Column(
                Modifier.clickable { expanded.value = !expanded.value },
            ) {
                ShorterTopAppBar(
                    title = {
                        ShortCommunityHeaderNoActions(note, accountViewModel, nav)
                    },
                    navigationIcon = {
                        Row(TitleIconModifier, verticalAlignment = Alignment.CenterVertically) {
                            if (nav.canPop()) {
                                IconButton(onClick = nav::popBack) { ArrowBackIcon() }
                            }
                        }
                    },
                    actions = {
                        Row(
                            modifier =
                                Modifier
                                    .height(Size35dp)
                                    .padding(start = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ShortCommunityActionOptions(note, accountViewModel, nav)
                        }
                    },
                )

                if (expanded.value) {
                    val elevation = if (MaterialTheme.colorScheme.isLight) 1.dp else 10.dp
                    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = elevation) {
                        LongCommunityHeader(baseNote = note, accountViewModel = accountViewModel, nav = nav)
                    }
                }
            }

            SecondaryTabRow(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                modifier = TabRowHeight,
                selectedTabIndex = pagerState.currentPage,
            ) {
                val coroutineScope = rememberCoroutineScope()
                Tab(
                    selected = pagerState.currentPage == 0,
                    text = { Text(text = stringRes(Res.string.feed)) },
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    text = { Text(text = stringRes(Res.string.mod_queue)) },
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                )
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                NewCommunityNoteButton(note.idHex, accountViewModel, nav)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        // Every row here is a post in this one community, which the top bar already names. Telling
        // the rows which scope they are inside stops each of them from repeating the same
        // community card as its parent context.
        CompositionLocalProvider(LocalCurrentExternalScope provides note.idHex) {
            HorizontalPager(
                state = pagerState,
            ) { page ->
                when (page) {
                    0 -> {
                        RefresheableFeedView(
                            feedViewModel,
                            null,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }

                    1 -> {
                        RefresheableFeedView(
                            modFeedViewModel,
                            null,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }
        }
    }
}
