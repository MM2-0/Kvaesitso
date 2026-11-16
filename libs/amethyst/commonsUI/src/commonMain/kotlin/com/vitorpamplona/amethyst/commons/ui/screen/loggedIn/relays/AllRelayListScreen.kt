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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.defaults.DefaultDMRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.commons.defaults.DefaultSearchRelayList
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.relays.ui.rememberRelayDragState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.blocked_section
import com.vitorpamplona.amethyst.commons.resources.blocked_section_explainer
import com.vitorpamplona.amethyst.commons.resources.broadcast_section
import com.vitorpamplona.amethyst.commons.resources.broadcast_section_explainer
import com.vitorpamplona.amethyst.commons.resources.connected_section
import com.vitorpamplona.amethyst.commons.resources.connected_section_description
import com.vitorpamplona.amethyst.commons.resources.default_relays
import com.vitorpamplona.amethyst.commons.resources.export_actions_dialog_title
import com.vitorpamplona.amethyst.commons.resources.export_as_text
import com.vitorpamplona.amethyst.commons.resources.export_as_zip
import com.vitorpamplona.amethyst.commons.resources.export_relay_settings
import com.vitorpamplona.amethyst.commons.resources.favorite_section
import com.vitorpamplona.amethyst.commons.resources.favorite_section_explainer
import com.vitorpamplona.amethyst.commons.resources.indexer_section
import com.vitorpamplona.amethyst.commons.resources.indexer_section_explainer
import com.vitorpamplona.amethyst.commons.resources.keypackage_section
import com.vitorpamplona.amethyst.commons.resources.keypackage_section_explainer
import com.vitorpamplona.amethyst.commons.resources.local_section
import com.vitorpamplona.amethyst.commons.resources.local_section_explainer
import com.vitorpamplona.amethyst.commons.resources.private_inbox_section
import com.vitorpamplona.amethyst.commons.resources.private_inbox_section_explainer
import com.vitorpamplona.amethyst.commons.resources.private_outbox_section
import com.vitorpamplona.amethyst.commons.resources.private_outbox_section_explainer
import com.vitorpamplona.amethyst.commons.resources.proxy_section
import com.vitorpamplona.amethyst.commons.resources.proxy_section_explainer
import com.vitorpamplona.amethyst.commons.resources.public_home_section
import com.vitorpamplona.amethyst.commons.resources.public_home_section_explainer
import com.vitorpamplona.amethyst.commons.resources.public_notif_section
import com.vitorpamplona.amethyst.commons.resources.public_notif_section_explainer
import com.vitorpamplona.amethyst.commons.resources.relay_settings
import com.vitorpamplona.amethyst.commons.resources.search_section
import com.vitorpamplona.amethyst.commons.resources.search_section_explainer
import com.vitorpamplona.amethyst.commons.resources.trusted_section
import com.vitorpamplona.amethyst.commons.resources.trusted_section_explainer
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.components.rememberFileSharer
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.blocked.BlockedRelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.blocked.renderBlockedItems
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.broadcast.BroadcastRelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.broadcast.renderBroadcastItems
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.RelayExporter
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.RelayListCollection
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.RelayZipExporter
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.connected.ConnectedRelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.connected.renderConnectedItems
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.dm.DMRelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.dm.renderDMItems
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.feeds.FavoriteRelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.feeds.renderRelayFeedsItems
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.indexer.IndexerRelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.indexer.renderIndexerItems
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.keyPackage.KeyPackageRelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.keyPackage.renderKeyPackageItems
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.local.LocalRelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.local.renderLocalItems
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.nip37.PrivateOutboxRelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.nip37.renderPrivateOutboxItems
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.nip65.Nip65RelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.nip65.renderNip65HomeItems
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.nip65.renderNip65NotifItems
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.proxy.ProxyRelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.proxy.renderProxyItems
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.search.SearchRelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.search.renderSearchItems
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.trusted.TrustedRelayListViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.trusted.renderTrustedItems
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.commons.ui.theme.SettingsCategoryFirstWithHorzBorderModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SettingsCategorySpacingWithHorzBorderModifier
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

@Composable
fun AllRelayListScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val dmViewModel: DMRelayListViewModel = rememberViewModel { DMRelayListViewModel() }
    val keyPackageViewModel: KeyPackageRelayListViewModel = rememberViewModel { KeyPackageRelayListViewModel() }
    val nip65ViewModel: Nip65RelayListViewModel = rememberViewModel { Nip65RelayListViewModel() }
    val privateOutboxViewModel: PrivateOutboxRelayListViewModel = rememberViewModel { PrivateOutboxRelayListViewModel() }
    val searchViewModel: SearchRelayListViewModel = rememberViewModel { SearchRelayListViewModel() }
    val blockedViewModel: BlockedRelayListViewModel = rememberViewModel { BlockedRelayListViewModel() }
    val trustedViewModel: TrustedRelayListViewModel = rememberViewModel { TrustedRelayListViewModel() }
    val localViewModel: LocalRelayListViewModel = rememberViewModel { LocalRelayListViewModel() }
    val connectedViewModel: ConnectedRelayListViewModel = rememberViewModel { ConnectedRelayListViewModel() }
    val broadcastViewModel: BroadcastRelayListViewModel = rememberViewModel { BroadcastRelayListViewModel() }
    val indexerViewModel: IndexerRelayListViewModel = rememberViewModel { IndexerRelayListViewModel() }
    val proxyViewModel: ProxyRelayListViewModel = rememberViewModel { ProxyRelayListViewModel() }
    val relayFeedsViewModel: FavoriteRelayListViewModel = rememberViewModel { FavoriteRelayListViewModel() }

    dmViewModel.init(accountViewModel, autoSave = true)
    keyPackageViewModel.init(accountViewModel, autoSave = true)
    nip65ViewModel.init(accountViewModel, autoSave = true)
    searchViewModel.init(accountViewModel, autoSave = true)
    localViewModel.init(accountViewModel, autoSave = true)
    privateOutboxViewModel.init(accountViewModel, autoSave = true)
    connectedViewModel.init(accountViewModel)
    blockedViewModel.init(accountViewModel, autoSave = true)
    trustedViewModel.init(accountViewModel, autoSave = true)
    broadcastViewModel.init(accountViewModel, autoSave = true)
    indexerViewModel.init(accountViewModel, autoSave = true)
    proxyViewModel.init(accountViewModel, autoSave = true)
    relayFeedsViewModel.init(accountViewModel, autoSave = true)

    LaunchedEffect(accountViewModel) {
        dmViewModel.load()
        keyPackageViewModel.load()
        nip65ViewModel.load()
        searchViewModel.load()
        localViewModel.load()
        privateOutboxViewModel.load()
        connectedViewModel.load()
        blockedViewModel.load()
        trustedViewModel.load()
        broadcastViewModel.load()
        indexerViewModel.load()
        proxyViewModel.load()
        relayFeedsViewModel.load()
    }

    // Every edit is published after a short debounce; leaving the screen publishes the last one now.
    DisposableEffect(Unit) {
        onDispose {
            dmViewModel.flushAutoSave()
            keyPackageViewModel.flushAutoSave()
            nip65ViewModel.flushAutoSave()
            searchViewModel.flushAutoSave()
            localViewModel.flushAutoSave()
            privateOutboxViewModel.flushAutoSave()
            blockedViewModel.flushAutoSave()
            trustedViewModel.flushAutoSave()
            broadcastViewModel.flushAutoSave()
            indexerViewModel.flushAutoSave()
            proxyViewModel.flushAutoSave()
            relayFeedsViewModel.flushAutoSave()
        }
    }

    MappedAllRelayListView(
        dmViewModel,
        keyPackageViewModel,
        nip65ViewModel,
        searchViewModel,
        localViewModel,
        privateOutboxViewModel,
        connectedViewModel,
        blockedViewModel,
        trustedViewModel,
        broadcastViewModel,
        indexerViewModel,
        proxyViewModel,
        relayFeedsViewModel,
        accountViewModel,
        nav,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappedAllRelayListView(
    dmViewModel: DMRelayListViewModel,
    keyPackageViewModel: KeyPackageRelayListViewModel,
    nip65ViewModel: Nip65RelayListViewModel,
    searchViewModel: SearchRelayListViewModel,
    localViewModel: LocalRelayListViewModel,
    privateOutboxViewModel: PrivateOutboxRelayListViewModel,
    connectedViewModel: ConnectedRelayListViewModel,
    blockedViewModel: BlockedRelayListViewModel,
    trustedViewModel: TrustedRelayListViewModel,
    broadcastViewModel: BroadcastRelayListViewModel,
    indexerViewModel: IndexerRelayListViewModel,
    proxyViewModel: ProxyRelayListViewModel,
    relayFeedsViewModel: FavoriteRelayListViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val dmFeedState by dmViewModel.relays.collectAsStateWithLifecycle()
    val keyPackageFeedState by keyPackageViewModel.relays.collectAsStateWithLifecycle()
    val homeFeedState by nip65ViewModel.homeRelays.collectAsStateWithLifecycle()
    val notifFeedState by nip65ViewModel.notificationRelays.collectAsStateWithLifecycle()
    val privateOutboxFeedState by privateOutboxViewModel.relays.collectAsStateWithLifecycle()
    val searchFeedState by searchViewModel.relays.collectAsStateWithLifecycle()
    val blockedFeedState by blockedViewModel.relays.collectAsStateWithLifecycle()
    val trustedFeedState by trustedViewModel.relays.collectAsStateWithLifecycle()
    val localFeedState by localViewModel.relays.collectAsStateWithLifecycle()
    val connectedRelays by connectedViewModel.relays.collectAsStateWithLifecycle()
    val broadcastRelays by broadcastViewModel.relays.collectAsStateWithLifecycle()
    val indexerRelays by indexerViewModel.relays.collectAsStateWithLifecycle()
    val proxyRelays by proxyViewModel.relays.collectAsStateWithLifecycle()
    val relayFeedsFeedState by relayFeedsViewModel.relays.collectAsStateWithLifecycle()

    val outboxCounts by nip65ViewModel.homeCountResults.collectAsStateWithLifecycle()
    val inboxCounts by nip65ViewModel.notifCountResults.collectAsStateWithLifecycle()
    val dmCounts by dmViewModel.countResults.collectAsStateWithLifecycle()
    val keyPackageCounts by keyPackageViewModel.countResults.collectAsStateWithLifecycle()
    val privateHomeCounts by privateOutboxViewModel.countResults.collectAsStateWithLifecycle()
    val proxyCounts by proxyViewModel.countResults.collectAsStateWithLifecycle()
    val indexerCounts by indexerViewModel.countResults.collectAsStateWithLifecycle()
    val searchCounts by searchViewModel.countResults.collectAsStateWithLifecycle()

    val homeDragState =
        rememberRelayDragState(
            onMove = { from, to -> nip65ViewModel.moveHomeRelay(from, to) },
            itemCount = { homeFeedState.size },
        )
    val notifDragState =
        rememberRelayDragState(
            onMove = { from, to -> nip65ViewModel.moveNotifRelay(from, to) },
            itemCount = { notifFeedState.size },
        )
    val dmDragState =
        rememberRelayDragState(
            onMove = { from, to -> dmViewModel.moveRelay(from, to) },
            itemCount = { dmFeedState.size },
        )
    val keyPackageDragState =
        rememberRelayDragState(
            onMove = { from, to -> keyPackageViewModel.moveRelay(from, to) },
            itemCount = { keyPackageFeedState.size },
        )
    val privateOutboxDragState =
        rememberRelayDragState(
            onMove = { from, to -> privateOutboxViewModel.moveRelay(from, to) },
            itemCount = { privateOutboxFeedState.size },
        )
    val proxyDragState =
        rememberRelayDragState(
            onMove = { from, to -> proxyViewModel.moveRelay(from, to) },
            itemCount = { proxyRelays.size },
        )
    val broadcastDragState =
        rememberRelayDragState(
            onMove = { from, to -> broadcastViewModel.moveRelay(from, to) },
            itemCount = { broadcastRelays.size },
        )
    val indexerDragState =
        rememberRelayDragState(
            onMove = { from, to -> indexerViewModel.moveRelay(from, to) },
            itemCount = { indexerRelays.size },
        )
    val searchDragState =
        rememberRelayDragState(
            onMove = { from, to -> searchViewModel.moveRelay(from, to) },
            itemCount = { searchFeedState.size },
        )
    val localDragState =
        rememberRelayDragState(
            onMove = { from, to -> localViewModel.moveRelay(from, to) },
            itemCount = { localFeedState.size },
        )
    val trustedDragState =
        rememberRelayDragState(
            onMove = { from, to -> trustedViewModel.moveRelay(from, to) },
            itemCount = { trustedFeedState.size },
        )
    val feedsDragState =
        rememberRelayDragState(
            onMove = { from, to -> relayFeedsViewModel.moveRelay(from, to) },
            itemCount = { relayFeedsFeedState.size },
        )
    val blockedDragState =
        rememberRelayDragState(
            onMove = { from, to -> blockedViewModel.moveRelay(from, to) },
            itemCount = { blockedFeedState.size },
        )

    Scaffold(
        topBar = {
            TopBarWithBackButton(
                caption = stringRes(Res.string.relay_settings),
                nav = nav,
            ) {
                ExportDropdownMenu {
                    RelayListCollection(
                        homeRelays = homeFeedState,
                        notifRelays = notifFeedState,
                        dmRelays = dmFeedState,
                        privateOutboxRelays = privateOutboxFeedState,
                        proxyRelays = proxyRelays,
                        broadcastRelays = broadcastRelays,
                        indexerRelays = indexerRelays,
                        searchRelays = searchFeedState,
                        localRelays = localFeedState,
                        trustedRelays = trustedFeedState,
                        favoriteRelays = relayFeedsFeedState,
                        blockedRelays = blockedFeedState,
                    )
                }
            }
        },
    ) { pad ->
        val anyDragging =
            homeDragState.isDragging || notifDragState.isDragging ||
                dmDragState.isDragging || keyPackageDragState.isDragging ||
                privateOutboxDragState.isDragging ||
                proxyDragState.isDragging || broadcastDragState.isDragging ||
                indexerDragState.isDragging || searchDragState.isDragging ||
                localDragState.isDragging || trustedDragState.isDragging ||
                feedsDragState.isDragging || blockedDragState.isDragging

        LazyColumn(
            contentPadding = FeedPadding,
            userScrollEnabled = !anyDragging,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = pad.calculateTopPadding(),
                        bottom = pad.calculateBottomPadding(),
                    ).consumeWindowInsets(pad)
                    .imePaddingSafe(),
        ) {
            item {
                SettingsCategory(
                    Res.string.public_home_section,
                    Res.string.public_home_section_explainer,
                    SettingsCategoryFirstWithHorzBorderModifier,
                )
            }
            renderNip65HomeItems(homeFeedState, nip65ViewModel, accountViewModel, nav, outboxCounts, homeDragState)

            item {
                SettingsCategory(
                    Res.string.public_notif_section,
                    Res.string.public_notif_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderNip65NotifItems(notifFeedState, nip65ViewModel, accountViewModel, nav, inboxCounts, notifDragState)

            item {
                SettingsCategoryWithButton(
                    Res.string.private_inbox_section,
                    Res.string.private_inbox_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                    action = {
                        ResetDMRelays(dmViewModel)
                    },
                )
            }
            renderDMItems(dmFeedState, dmViewModel, accountViewModel, nav, dmCounts, dmDragState)

            item {
                SettingsCategory(
                    Res.string.keypackage_section,
                    Res.string.keypackage_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderKeyPackageItems(keyPackageFeedState, keyPackageViewModel, accountViewModel, nav, keyPackageCounts, keyPackageDragState)

            item {
                SettingsCategory(
                    Res.string.private_outbox_section,
                    Res.string.private_outbox_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderPrivateOutboxItems(privateOutboxFeedState, privateOutboxViewModel, accountViewModel, nav, privateHomeCounts, privateOutboxDragState)

            item {
                SettingsCategory(
                    Res.string.proxy_section,
                    Res.string.proxy_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderProxyItems(proxyRelays, proxyViewModel, accountViewModel, nav, proxyCounts, proxyDragState)

            item {
                SettingsCategory(
                    Res.string.broadcast_section,
                    Res.string.broadcast_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderBroadcastItems(broadcastRelays, broadcastViewModel, accountViewModel, nav, broadcastDragState)

            item {
                SettingsCategoryWithButton(
                    Res.string.indexer_section,
                    Res.string.indexer_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                ) {
                    ResetIndexerRelays(indexerViewModel)
                }
            }
            renderIndexerItems(indexerRelays, indexerViewModel, accountViewModel, nav, indexerCounts, indexerDragState)

            item {
                SettingsCategoryWithButton(
                    Res.string.search_section,
                    Res.string.search_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                ) {
                    ResetSearchRelays(searchViewModel)
                }
            }
            renderSearchItems(searchFeedState, searchViewModel, accountViewModel, nav, searchCounts, searchDragState)

            item {
                SettingsCategory(
                    Res.string.local_section,
                    Res.string.local_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderLocalItems(localFeedState, localViewModel, accountViewModel, nav, localDragState)

            item {
                SettingsCategory(
                    Res.string.trusted_section,
                    Res.string.trusted_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderTrustedItems(trustedFeedState, trustedViewModel, accountViewModel, nav, trustedDragState)

            item {
                SettingsCategory(
                    Res.string.favorite_section,
                    Res.string.favorite_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderRelayFeedsItems(relayFeedsFeedState, relayFeedsViewModel, accountViewModel, nav, feedsDragState)

            item {
                SettingsCategory(
                    Res.string.blocked_section,
                    Res.string.blocked_section_explainer,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderBlockedItems(blockedFeedState, blockedViewModel, accountViewModel, nav, blockedDragState)

            item {
                SettingsCategory(
                    Res.string.connected_section,
                    Res.string.connected_section_description,
                    SettingsCategorySpacingWithHorzBorderModifier,
                )
            }
            renderConnectedItems(connectedRelays, connectedViewModel, accountViewModel, nav)
        }
    }
}

@Composable
fun ResetSearchRelays(postViewModel: SearchRelayListViewModel) {
    OutlinedButton(
        onClick = {
            postViewModel.deleteAll()
            DefaultSearchRelayList.forEach {
                postViewModel.addRelay(
                    relaySetupInfoBuilder(it),
                )
            }
            postViewModel.loadRelayDocuments()
        },
    ) {
        Text(stringRes(Res.string.default_relays))
    }
}

@Composable
fun ResetIndexerRelays(postViewModel: IndexerRelayListViewModel) {
    OutlinedButton(
        onClick = {
            postViewModel.deleteAll()
            DefaultIndexerRelayList.forEach {
                postViewModel.addRelay(
                    relaySetupInfoBuilder(it),
                )
            }
            postViewModel.loadRelayDocuments()
        },
    ) {
        Text(stringRes(Res.string.default_relays))
    }
}

@Composable
fun ResetDMRelays(postViewModel: DMRelayListViewModel) {
    OutlinedButton(
        onClick = {
            postViewModel.deleteAll()
            DefaultDMRelayList.forEach {
                postViewModel.addRelay(relaySetupInfoBuilder(it))
            }
            postViewModel.loadRelayDocuments()
        },
    ) {
        Text(stringRes(Res.string.default_relays))
    }
}

@Composable
fun SettingsCategory(
    title: StringResource,
    description: StringResource? = null,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(
            text = stringRes(title),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
        )
        if (description != null) {
            Text(
                text = stringRes(description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

@Composable
fun SettingsCategoryWithButton(
    title: StringResource,
    description: StringResource? = null,
    modifier: Modifier,
    action: @Composable () -> Unit,
) {
    Row(modifier, horizontalArrangement = RowColSpacing) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringRes(title),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
            )
            if (description != null) {
                Text(
                    text = stringRes(description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }

        action()
    }
}

@Composable
fun ExportDropdownMenu(collection: () -> RelayListCollection) {
    var expanded by remember { mutableStateOf(false) }
    val textSharer = rememberTextSharer()
    val fileSharer = rememberFileSharer()
    val scope = rememberCoroutineScope()

    IconButton(onClick = { expanded = true }) {
        Icon(
            symbol = MaterialSymbols.Share,
            contentDescription = stringRes(Res.string.export_relay_settings),
        )
    }

    if (expanded) {
        M3ActionDialog(
            title = stringRes(Res.string.export_actions_dialog_title),
            onDismiss = { expanded = false },
        ) {
            M3ActionSection {
                M3ActionRow(
                    icon = MaterialSymbols.Description,
                    text = stringRes(Res.string.export_as_text),
                ) {
                    expanded = false
                    scope.launch { RelayExporter(textSharer).export(collection()) }
                }
                M3ActionRow(
                    icon = MaterialSymbols.FolderZip,
                    text = stringRes(Res.string.export_as_zip),
                ) {
                    expanded = false
                    scope.launch { RelayZipExporter(fileSharer).export(collection()) }
                }
            }
        }
    }
}
