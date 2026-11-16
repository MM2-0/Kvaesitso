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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.UserFeedState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.num_selected
import com.vitorpamplona.amethyst.commons.resources.unblock
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedError
import com.vitorpamplona.amethyst.commons.ui.feeds.LoadingFeed
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.ShowUserButton
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.buttons.CloseButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size15dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.UserFeedViewModel
import org.jetbrains.compose.resources.StringResource

/**
 * Re-runs [invalidate] whenever the user's block/mute state changes. `hiddenUsers.flow`
 * already combines transient spammers, so subscribing to that alone covers both.
 */
@Composable
internal fun InvalidateOnBlockListChange(
    accountViewModel: AccountViewModel,
    invalidate: () -> Unit,
) {
    val blockListState by accountViewModel.account.hiddenUsers.flow
        .collectAsStateWithLifecycle()

    LaunchedEffect(blockListState) { invalidate() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlockListTopBar(
    title: StringResource,
    selectedCount: Int,
    onCancel: () -> Unit,
    onUnblock: () -> Unit,
    nav: INav,
) {
    if (selectedCount == 0) {
        TopBarWithBackButton(stringRes(id = title), nav)
    } else {
        ShorterTopAppBar(
            title = {
                Text(
                    text = stringRes(Res.string.num_selected, selectedCount),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            },
            navigationIcon = {
                CloseButton(modifier = HalfHorzPadding, onPress = onCancel)
            },
            actions = {
                Button(modifier = HalfHorzPadding, onClick = onUnblock) {
                    Text(text = stringRes(Res.string.unblock))
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SelectableUserList(
    modifier: Modifier = Modifier,
    viewModel: UserFeedViewModel,
    emptyMessage: StringResource,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
    enablePullRefresh: Boolean = true,
) {
    // Outer Box applies caller padding (Scaffold insets) so the Loading/Error
    // states inside RefresheableBox don't render under the top bar.
    Box(modifier.fillMaxSize()) {
        RefresheableBox(viewModel, enablePullRefresh) {
            val feedState by viewModel.feedContent.collectAsStateWithLifecycle()
            val selectionMode = selected.isNotEmpty()

            when (val state = feedState) {
                is UserFeedState.Loaded -> {
                    val items by state.feed.collectAsStateWithLifecycle()
                    val listState = rememberLazyListState()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = rememberFeedContentPadding(FeedPadding),
                        state = listState,
                    ) {
                        items(items, key = { it.pubkeyHex }) { user ->
                            val isSelected = user.pubkeyHex in selected
                            val rowModifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (selectionMode) {
                                                onToggle(user.pubkeyHex)
                                            } else {
                                                nav.nav(routeFor(user))
                                            }
                                        },
                                        onLongClick = { onToggle(user.pubkeyHex) },
                                    ).let {
                                        if (isSelected) {
                                            it.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        } else {
                                            it
                                        }
                                    }

                            Row(
                                modifier = rowModifier.padding(horizontal = Size15dp, vertical = Size10dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UserPicture(user, Size55dp, accountViewModel = accountViewModel, nav = nav)
                                Column(
                                    modifier =
                                        Modifier
                                            .padding(start = 10.dp)
                                            .weight(1f),
                                ) {
                                    UsernameDisplay(user, accountViewModel = accountViewModel)
                                }
                                if (selectionMode) {
                                    Checkbox(checked = isSelected, onCheckedChange = { onToggle(user.pubkeyHex) })
                                } else {
                                    ShowUserButton { accountViewModel.show(user) }
                                }
                            }
                            HorizontalDivider(thickness = DividerThickness)
                        }
                    }
                }

                is UserFeedState.Empty -> {
                    EmptyState(emptyMessage)
                }

                is UserFeedState.Loading -> {
                    LoadingFeed()
                }

                is UserFeedState.FeedError -> {
                    FeedError(state.errorMessage) { viewModel.invalidateData() }
                }
            }
        }
    }
}

@Composable
fun EmptyState(message: StringResource) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp).padding(bottom = 16.dp),
        )
        Text(
            text = stringRes(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
