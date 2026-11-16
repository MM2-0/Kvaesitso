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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.interestSets.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip51Lists.ui.InterestSetItem
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.interest_set_create_btn_label
import com.vitorpamplona.amethyst.commons.resources.interest_sets_empty
import com.vitorpamplona.amethyst.commons.resources.interest_sets_title
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.fabBottomBarPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch

@Composable
fun ListOfInterestSetsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val sets by accountViewModel.account.interestSets.listFeedFlow
        .collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopBarWithBackButton(caption = stringRes(Res.string.interest_sets_title), nav)
        },
        bottomBar = {
            AppBottomBar(Route.InterestSets, nav, accountViewModel) { route ->
                if (route == Route.InterestSets) {
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingActionButton = {
            InterestSetFab(
                onAdd = { nav.nav(Route.InterestSetMetadataEdit()) },
                modifier = Modifier.fabBottomBarPadding(nav),
            )
        },
    ) { paddingValues ->
        Column(
            Modifier
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                ).fillMaxHeight(),
        ) {
            if (sets.isEmpty()) {
                EmptyInterestSets()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = FeedPadding,
                ) {
                    itemsIndexed(sets, key = { _, it -> it.identifier }) { _, set ->
                        InterestSetItem(
                            modifier = Modifier.fillMaxSize().animateItem(),
                            interestSet = set,
                            onClick = { nav.nav(Route.InterestSetView(set.identifier)) },
                            onRename = { nav.nav(Route.InterestSetMetadataEdit(set.identifier)) },
                            onClone = {
                                accountViewModel.launchSigner {
                                    accountViewModel.account.interestSets.cloneInterestSet(
                                        source = set,
                                        customName = null,
                                        account = accountViewModel.account,
                                    )
                                }
                            },
                            onDelete = {
                                accountViewModel.launchSigner {
                                    accountViewModel.account.interestSets.deleteInterestSet(
                                        identifier = set.identifier,
                                        account = accountViewModel.account,
                                    )
                                }
                            },
                        )
                        HorizontalDivider(thickness = DividerThickness)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyInterestSets() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            symbol = MaterialSymbols.Tag,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp),
        )
        Text(
            text = stringRes(Res.string.interest_sets_empty),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}

@Composable
fun InterestSetFab(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        text = {
            Text(text = stringRes(Res.string.interest_set_create_btn_label))
        },
        icon = {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.PlaylistAdd,
                contentDescription = null,
            )
        },
        onClick = onAdd,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}
