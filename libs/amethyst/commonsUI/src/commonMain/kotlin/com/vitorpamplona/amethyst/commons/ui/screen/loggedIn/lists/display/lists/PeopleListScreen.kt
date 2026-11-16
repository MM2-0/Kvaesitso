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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.display.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.follow_set_broadcast
import com.vitorpamplona.amethyst.commons.resources.follow_set_delete
import com.vitorpamplona.amethyst.commons.resources.follow_set_edit_list_metadata
import com.vitorpamplona.amethyst.commons.resources.list_actions_dialog_title
import com.vitorpamplona.amethyst.commons.resources.private_members
import com.vitorpamplona.amethyst.commons.resources.private_members_count
import com.vitorpamplona.amethyst.commons.resources.public_members
import com.vitorpamplona.amethyst.commons.resources.public_members_count
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.resources.quick_action_share_browser_link
import com.vitorpamplona.amethyst.commons.resources.search_and_add_a_user
import com.vitorpamplona.amethyst.commons.ui.components.ClickableBox
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.ClearTextIcon
import com.vitorpamplona.amethyst.commons.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.commons.ui.note.externalLinkForNote
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.display.DrawUser
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.display.PeopleListView
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.display.RenderAddUserFieldAndSuggestions
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.HalfVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.PopupUpEffect
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdPadding
import com.vitorpamplona.amethyst.commons.ui.theme.TabRowHeight
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleListScreen(
    selectedDTag: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: PeopleListViewModel = rememberViewModel { PeopleListViewModel() }
    viewModel.init(accountViewModel, selectedDTag)

    val pagerState = rememberPagerState { 2 }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        TitleAndDescription(viewModel)
                    },
                    navigationIcon = {
                        if (nav.canPop()) {
                            IconButton(nav::popBack) {
                                ArrowBackIcon()
                            }
                        }
                    },
                    actions = {
                        ListActionsMenuButton(viewModel, accountViewModel, nav)
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
                TopAppTabs(viewModel, pagerState)
            }
        },
    ) { padding ->
        ListViewAndEditColumn(
            viewModel = viewModel,
            pagerState = pagerState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding(),
                    ).consumeWindowInsets(padding)
                    .imePaddingSafe(),
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun TopAppTabs(
    viewModel: PeopleListViewModel,
    pagerState: PagerState,
) {
    SecondaryTabRow(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        selectedTabIndex = pagerState.currentPage,
        modifier = TabRowHeight,
    ) {
        val scope = rememberCoroutineScope()
        Tab(
            selected = pagerState.currentPage == 0,
            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
            text = {
                val list = viewModel.selectedList.collectAsStateWithLifecycle()
                val labelPublic =
                    list.value?.let {
                        stringRes(Res.string.public_members_count, it.publicMembers.size)
                    } ?: stringRes(Res.string.public_members)
                Text(labelPublic)
            },
        )
        Tab(
            selected = pagerState.currentPage == 1,
            onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
            text = {
                val list = viewModel.selectedList.collectAsStateWithLifecycle()
                val labelPrivate =
                    list.value?.let {
                        stringRes(Res.string.private_members_count, it.privateMembersList.size)
                    } ?: stringRes(Res.string.private_members)
                Text(labelPrivate)
            },
        )
    }
}

@Composable
private fun TitleAndDescription(viewModel: PeopleListViewModel) {
    val selectedSetState = viewModel.selectedList.collectAsStateWithLifecycle()
    selectedSetState.value?.let { selectedSet ->
        Text(
            text = selectedSet.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ListViewAndEditColumn(
    viewModel: PeopleListViewModel,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(modifier = modifier) {
        PeopleListPager(
            viewModel = viewModel,
            pagerState = pagerState,
            modifier = Modifier.weight(1f),
            onDeleteUser = { user, isPrivate ->
                accountViewModel.launchSigner {
                    viewModel.removeUserFromSet(user, isPrivate)
                }
            },
            accountViewModel = accountViewModel,
            nav = nav,
        )

        RenderAddUserFieldAndSuggestions(viewModel, pagerState, accountViewModel)
    }
}

@Composable
private fun RenderAddUserFieldAndSuggestions(
    viewModel: PeopleListViewModel,
    pagerState: PagerState,
    accountViewModel: AccountViewModel,
) {
    RenderAddUserFieldAndSuggestions(
        viewModel.userSuggestions,
        hasUserFlow = { user ->
            viewModel.hasUserFlow(user, pagerState.currentPage == 1)
        },
        addUserToSet = { user ->
            accountViewModel.launchSigner {
                viewModel.addUserToSet(user, pagerState.currentPage == 1)
            }
        },
        removeUserFromSet = { user ->
            accountViewModel.launchSigner {
                viewModel.removeUserFromSet(user, pagerState.currentPage == 1)
            }
        },
        accountViewModel = accountViewModel,
    )
}

@Composable
private fun PeopleListPager(
    viewModel: PeopleListViewModel,
    pagerState: PagerState,
    modifier: Modifier,
    onDeleteUser: (User, Boolean) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val selectedSetState = viewModel.selectedList.collectAsStateWithLifecycle()
    selectedSetState.value?.let { selectedSet ->
        HorizontalPager(state = pagerState, modifier) { page ->
            when (page) {
                0 -> {
                    PeopleListView(
                        memberList = selectedSet.publicMembersList,
                        onDeleteUser = { user ->
                            onDeleteUser(user, false)
                        },
                        modifier = Modifier.fillMaxSize(),
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                1 -> {
                    PeopleListView(
                        memberList = selectedSet.privateMembersList,
                        onDeleteUser = { user ->
                            onDeleteUser(user, true)
                        },
                        modifier = Modifier.fillMaxSize(),
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
    }
}

@Composable
@Preview(device = "spec:width=2160px,height=2940px,dpi=440")
private fun PeopleListViewPreview() {
    val accountViewModel = mockAccountViewModel()

    val user1: User = LocalCache.getOrCreateUser("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")
    val user2: User = LocalCache.getOrCreateUser("ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b")
    val user3: User = LocalCache.getOrCreateUser("7eb29c126b3628077e2e3d863b917a56b74293aa9d8a9abc26a40ba3f2866baf")

    ThemeComparisonRow {
        Column {
            PeopleListView(
                memberList = persistentListOf(user1, user2, user3),
                onDeleteUser = { _ -> },
                accountViewModel = accountViewModel,
                nav = EmptyNav(),
            )

            Spacer(HalfVertSpacer)

            var userName by remember { mutableStateOf("") }
            OutlinedTextField(
                label = { Text(text = stringRes(Res.string.search_and_add_a_user)) },
                modifier =
                    Modifier
                        .padding(horizontal = Size10dp)
                        .fillMaxWidth(),
                value = userName,
                onValueChange = {
                    userName = it
                },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {},
                    ) {
                        ClearTextIcon()
                    }
                },
            )

            Card(
                modifier = Modifier.padding(horizontal = 10.dp),
                elevation = cardElevation(5.dp),
                shape = PopupUpEffect,
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(top = 10.dp),
                    modifier = Modifier.heightIn(0.dp, 200.dp),
                ) {
                    itemsIndexed(persistentListOf(user1, user2, user3), key = { _, item -> item.pubkeyHex }) { _, baseUser ->
                        DrawUser(
                            baseUser,
                            { MutableStateFlow(false) },
                            {},
                            {},
                            accountViewModel,
                        )

                        HorizontalDivider(
                            thickness = DividerThickness,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListActionsMenuButton(
    viewModel: PeopleListViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ListActionsMenuButton(
        note = viewModel::selectedNote,
        onEditList = {
            nav.nav { Route.PeopleListMetadataEdit(viewModel.selectedDTag.value) }
        },
        onBroadcastList = {
            accountViewModel.launchSigner {
                viewModel.loadNote()?.let { updatedSetNote ->
                    accountViewModel.broadcast(updatedSetNote)
                }
            }
        },
        onDeleteList = {
            accountViewModel.launchSigner {
                viewModel.deleteFollowSet()
            }
            nav.popBack()
        },
    )
}

@Composable
private fun ListActionsMenuButton(
    note: () -> AddressableNote,
    onEditList: () -> Unit,
    onBroadcastList: () -> Unit,
    onDeleteList: () -> Unit,
) {
    val quickActionShareBrowserLinkStr = stringRes(Res.string.quick_action_share_browser_link)
    val quickActionShareStr = stringRes(Res.string.quick_action_share)
    val isActionListOpen = remember { mutableStateOf(false) }

    ClickableBox(
        modifier =
            StdPadding
                .size(30.dp)
                .border(
                    width = Dp.Hairline,
                    color = ButtonDefaults.filledTonalButtonColors().containerColor,
                    shape = ButtonBorder,
                ).background(
                    color = ButtonDefaults.filledTonalButtonColors().containerColor,
                    shape = ButtonBorder,
                ),
        onClick = { isActionListOpen.value = true },
    ) {
        VerticalDotsIcon()
    }

    if (isActionListOpen.value) {
        val textSharer = rememberTextSharer()
        M3ActionDialog(
            title = stringRes(Res.string.list_actions_dialog_title),
            onDismiss = { isActionListOpen.value = false },
        ) {
            M3ActionSection {
                M3ActionRow(icon = MaterialSymbols.Share, text = stringRes(Res.string.quick_action_share)) {
                    textSharer.share(externalLinkForNote(note()), quickActionShareBrowserLinkStr, quickActionShareStr)
                    isActionListOpen.value = false
                }
                M3ActionRow(icon = MaterialSymbols.Edit, text = stringRes(Res.string.follow_set_edit_list_metadata)) {
                    onEditList()
                    isActionListOpen.value = false
                }
                M3ActionRow(icon = MaterialSymbols.CellTower, text = stringRes(Res.string.follow_set_broadcast)) {
                    onBroadcastList()
                    isActionListOpen.value = false
                }
            }
            M3ActionSection {
                M3ActionRow(icon = MaterialSymbols.Delete, text = stringRes(Res.string.follow_set_delete), isDestructive = true) {
                    onDeleteList()
                    isActionListOpen.value = false
                }
            }
        }
    }
}
