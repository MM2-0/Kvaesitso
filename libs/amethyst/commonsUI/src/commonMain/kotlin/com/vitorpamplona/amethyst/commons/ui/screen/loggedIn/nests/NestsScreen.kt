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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nest_create_fab
import com.vitorpamplona.amethyst.commons.resources.nest_no_server_body
import com.vitorpamplona.amethyst.commons.resources.nest_no_server_cancel
import com.vitorpamplona.amethyst.commons.resources.nest_no_server_save_failed
import com.vitorpamplona.amethyst.commons.resources.nest_no_server_title
import com.vitorpamplona.amethyst.commons.resources.nest_no_server_use_default
import com.vitorpamplona.amethyst.commons.resources.nests
import com.vitorpamplona.amethyst.commons.ui.feeds.RefresheableBox
import com.vitorpamplona.amethyst.commons.ui.feeds.RenderFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.SaveableFeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.commons.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.create.CreateNestSheet
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.create.CreateNestViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.datasource.NestsFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun NestsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NestsScreen(
        nestsFeedContentState = accountViewModel.feedStates.nestsFeed,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun NestsScreen(
    nestsFeedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(nestsFeedContentState)
    WatchAccountForNestsScreen(nestsFeedState = nestsFeedContentState, accountViewModel = accountViewModel)
    NestsFilterAssemblerSubscription(accountViewModel)

    val nestsServers by accountViewModel.account.nestsServers.flow
        .collectAsStateWithLifecycle()
    var showCreateSheet by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(false) }

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            NestsTopBar(accountViewModel, nav)
        },
        bottomBar = {
            AppBottomBar(Route.Nests, nav, accountViewModel) { route ->
                if (route == Route.Nests) {
                    nestsFeedContentState.sendToTop()
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
        floatingButton = {
            FabBottomBarPadded(nav) {
                FloatingActionButton(
                    onClick = {
                        if (nestsServers.any { it.relay.startsWith("http") && it.auth.startsWith("http") }) {
                            showCreateSheet = true
                        } else {
                            showSetupDialog = true
                        }
                    },
                    shape = CircleShape,
                ) {
                    Icon(
                        symbol = MaterialSymbols.Add,
                        contentDescription = stringRes(Res.string.nest_create_fab),
                    )
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        RefresheableBox(nestsFeedContentState, true) {
            SaveableFeedContentState(nestsFeedContentState, scrollStateKey = ScrollStateKeys.NESTS_SCREEN) { listState ->
                RenderFeedContentState(
                    feedContentState = nestsFeedContentState,
                    accountViewModel = accountViewModel,
                    listState = listState,
                    nav = nav,
                    routeForLastRead = "NestsFeed",
                    onLoaded = { loaded ->
                        NestsFeedLoaded(
                            loaded = loaded,
                            listState = listState,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    },
                )
            }
        }
    }

    if (showSetupDialog) {
        SetUpAudioServerDialog(
            defaultUrl = CreateNestViewModel.DEFAULT_ENDPOINT_URL,
            onDismiss = { showSetupDialog = false },
            onConfirm = {
                showSetupDialog = false
                accountViewModel.launchSigner {
                    try {
                        // Seed the kind-10112 list with the public nostrnests
                        // (relay, auth) pair. We can't derive the auth host
                        // from the relay URL reliably for community-run
                        // deployments, so the dialog hardcodes the public
                        // pair; users with their own deployment use the
                        // Settings → Audio-room servers screen instead.
                        accountViewModel.account.sendNestsServersList(
                            listOf(
                                com.vitorpamplona.quartz.nip53LiveActivities.nestsServers
                                    .NestsServer(
                                        relay = CreateNestViewModel.DEFAULT_ENDPOINT_URL,
                                        auth = CreateNestViewModel.DEFAULT_SERVICE_URL,
                                    ),
                            ),
                        )
                        showCreateSheet = true
                    } catch (_: Throwable) {
                        accountViewModel.toastManager.toast(
                            Res.string.nests,
                            Res.string.nest_no_server_save_failed,
                        )
                    }
                }
            },
        )
    }

    if (showCreateSheet) {
        CreateNestSheet(
            accountViewModel = accountViewModel,
            onDismiss = { showCreateSheet = false },
        )
    }
}

@Composable
private fun SetUpAudioServerDialog(
    defaultUrl: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.nest_no_server_title)) },
        text = { Text(stringRes(Res.string.nest_no_server_body, defaultUrl)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(Res.string.nest_no_server_use_default))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.nest_no_server_cancel))
            }
        },
    )
}

@Composable
fun WatchAccountForNestsScreen(
    nestsFeedState: FeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by accountViewModel.account.liveNestsFollowLists.collectAsStateWithLifecycle()
    // Use `by` to unwrap the StateFlow's value into the LaunchedEffect
    // key — without it, the State<T> object is reference-stable across
    // recompositions and the effect never re-fires when the user
    // mutes someone, leaving the rooms feed stale until a manual
    // refresh.
    val hiddenUsers by accountViewModel.account.hiddenUsers.flow
        .collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState, hiddenUsers) {
        nestsFeedState.checkKeysInvalidateDataAndSendToTop()
    }
}
