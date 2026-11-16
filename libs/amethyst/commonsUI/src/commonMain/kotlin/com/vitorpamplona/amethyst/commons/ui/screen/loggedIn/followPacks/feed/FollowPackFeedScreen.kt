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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.followPacks.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.followPacks.feed.dal.FollowPackFeedConversationsFeedViewModel
import com.vitorpamplona.amethyst.commons.followPacks.feed.dal.FollowPackFeedNewThreadFeedViewModel
import com.vitorpamplona.amethyst.commons.followPacks.feed.dal.FollowPackMembersUserFeedViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.conversations
import com.vitorpamplona.amethyst.commons.resources.members
import com.vitorpamplona.amethyst.commons.resources.new_threads
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.feeds.rememberForeverPagerState
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TitleIconModifier
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarSize
import com.vitorpamplona.amethyst.commons.ui.note.LikeReaction
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.ReplyReaction
import com.vitorpamplona.amethyst.commons.ui.note.ZapReaction
import com.vitorpamplona.amethyst.commons.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.commons.ui.screen.UserFeedView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.followPacks.feed.datasource.FollowPackFeedFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy2dp
import com.vitorpamplona.amethyst.commons.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowPackFeedScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) {
        it?.let {
            PrepareViewModelsFollowPackScreen(
                note = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Suppress("StateFlowValueCalledInComposition")
@Composable
fun PrepareViewModelsFollowPackScreen(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val conversationsFeedViewModel: FollowPackFeedConversationsFeedViewModel =
        rememberViewModel(
            key = note.idHex + "ConversationsFeedViewModel",
            factory =
                FollowPackFeedConversationsFeedViewModel.Factory(
                    note,
                    accountViewModel.account,
                ),
        )

    val newThreadFeedViewModel: FollowPackFeedNewThreadFeedViewModel =
        rememberViewModel(
            key = note.idHex + "NewThreadFeedViewModel",
            factory =
                FollowPackFeedNewThreadFeedViewModel.Factory(
                    note,
                    accountViewModel.account,
                ),
        )

    val membersFeedViewModel: FollowPackMembersUserFeedViewModel =
        rememberViewModel(
            key = note.idHex + "MembersFeedViewModel",
            factory =
                FollowPackMembersUserFeedViewModel.Factory(
                    note,
                    accountViewModel.account,
                ),
        )

    FollowPackFeedScreen(note, newThreadFeedViewModel, conversationsFeedViewModel, membersFeedViewModel, accountViewModel, nav)
}

@Composable
fun FollowPackFeedScreen(
    note: AddressableNote,
    newThreadFeedViewModel: FollowPackFeedNewThreadFeedViewModel,
    conversationsFeedViewModel: FollowPackFeedConversationsFeedViewModel,
    membersFeedViewModel: FollowPackMembersUserFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(newThreadFeedViewModel)
    WatchLifecycleAndUpdateModel(conversationsFeedViewModel)
    WatchLifecycleAndUpdateModel(membersFeedViewModel)

    FollowPackFeedFilterAssemblerSubscription(note, accountViewModel)

    val pagerState = rememberForeverPagerState(note.idHex + "FollowPackScreenPagerState") { 3 }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            FollowPackFeedTopBar(
                note,
                pagerState,
                accountViewModel,
                nav,
            )
        },
        accountViewModel = accountViewModel,
    ) {
        HorizontalPager(
            state = pagerState,
        ) { page ->
            when (page) {
                0 -> {
                    RefresheableFeedView(
                        newThreadFeedViewModel,
                        null,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                1 -> {
                    RefresheableFeedView(
                        conversationsFeedViewModel,
                        null,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                2 -> {
                    UserFeedView(
                        membersFeedViewModel,
                        accountViewModel,
                        nav,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowPackFeedTopBar(
    note: AddressableNote,
    pagerState: PagerState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column {
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val modifier = Modifier.fillMaxWidth().height(TopBarSize + statusBarHeight)
        Box(
            modifier = modifier, // Adjust height as needed for your banner
        ) {
            DisplayBanner(note, Modifier.fillMaxSize(), accountViewModel)

            ShorterTopAppBar(
                title = {
                    FollowPackHeader(note, accountViewModel, nav)
                },
                navigationIcon = {
                    Row(TitleIconModifier, verticalAlignment = Alignment.CenterVertically) {
                        if (nav.canPop()) {
                            IconButton(
                                onClick = nav::popBack,
                            ) {
                                Icon(
                                    symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                                    contentDescription = stringRes(Res.string.back),
                                )
                            }
                        }
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = SpacedBy2dp) {
                        ReplyReaction(
                            baseNote = note,
                            grayTint = MaterialTheme.colorScheme.onBackground,
                            accountViewModel = accountViewModel,
                            iconSizeModifier = Size18Modifier,
                        ) {
                            nav.nav {
                                Route.Note(note.idHex)
                            }
                        }
                        Spacer(modifier = HalfHorzSpacer)
                        LikeReaction(
                            baseNote = note,
                            grayTint = MaterialTheme.colorScheme.onBackground,
                            accountViewModel = accountViewModel,
                            nav,
                        )
                        Spacer(modifier = HalfHorzSpacer)
                        ZapReaction(
                            baseNote = note,
                            grayTint = MaterialTheme.colorScheme.onBackground,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                        Spacer(modifier = HalfHorzSpacer)
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.6f), // Make TopAppBar background transparent
                    ),
            )
        }

        SecondaryTabRow(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = TabRowHeight,
            selectedTabIndex = pagerState.currentPage,
        ) {
            val coroutineScope = rememberCoroutineScope()
            Tab(
                selected = pagerState.currentPage == 0,
                text = { Text(text = stringRes(Res.string.new_threads)) },
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
            )
            Tab(
                selected = pagerState.currentPage == 1,
                text = { Text(text = stringRes(Res.string.conversations)) },
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
            )
            Tab(
                selected = pagerState.currentPage == 2,
                text = { Text(text = stringRes(Res.string.members)) },
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
            )
        }
    }
}

@Composable
private fun DisplayBanner(
    baseNote: AddressableNote,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
) {
    val noteEvent by observeNoteEvent<StarterPackEvent>(baseNote, accountViewModel)

    noteEvent?.image()?.let {
        AsyncImage(
            model = it,
            contentDescription = stringRes(Res.string.preview_card_image_for, it),
            contentScale = ContentScale.Crop,
            modifier = Modifier,
        )
    }
}

@Composable
fun FollowPackHeader(
    baseNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent by observeNoteEvent<StarterPackEvent>(baseNote, accountViewModel)

    Text(
        text = noteEvent?.title() ?: baseNote.dTag(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
